(

var staker;
var machine = Require("machine.scd");
var personalityController = Require("personalityController.scd");
var oscController = Require("oscController.scd");
var visualCore = Require("visualCore.scd");
var screens = Require("screens.scd");
var hdmiRect;
var specsView;

var midiCC;
var labels;
var z;

// The one surface lives in ONE of these two hosts at a time - never both.
var visualHost;		// the visual tab page
var hdmiHost;		// inside the second-screen window
var setVisualTarget;
var ensureHdmi;

// Whichever host is not holding the surface gets this instead. A
// StaticText has no draw loop, so the idle side costs nothing.
var placeholder = { |host, text|
	host.removeAll;
	host.layout_(VLayout(
		StaticText()
			.string_(text)
			.align_(\center)
			.font_(Font(size: 20))
			.stringColor_(Color.gray(0.4))
	).margins_(0));
};

// The visual tab is built unconditionally, second screen or not. That is
// what lets the surface move at runtime : the tab set never changes shape,
// so plugging HDMI in re-parents the surface instead of rebuilding the UI.
var stack = {
	var deviceView = Require("deviceView.scd");
	var controlView = Require("controlView.scd");
	var systemView = Require("systemView.scd");
	var visualView = Require("visualView.scd");
	var pages = [deviceView.()];

	labels = ["device"];

	visualHost = visualView.();
	pages = pages.add(visualHost);
	labels = labels.add("visual");

	pages = pages ++ [controlView.()];
	labels = labels ++ ["control"];

	pages = pages ++ [systemView.()];
	labels = labels ++ ["system"];

	View().layout_(staker = StackLayout(*pages));
};

var tabButton = {|i|
	UserView()
	.background_( if(i==0,Color.black.lighten(0.25),Color.black))
	.mouseDownAction_({|but|
		but.parent.children.do({|ab,i|
			if(but != ab, {
				ab.backColor_(Color.black);
			},{
				ab.backColor_(Color.black.lighten(0.25));
				staker.index = i;
			});
		},{});
	})
	.drawFunc_({|v|Pen.stringAtPoint(labels[i], (v.bounds.width-45/2)-40@25, Font(size:30), Color.white.darken(0.75))})
	.animate_(false)
};

var stackView = stack.();

var tabs = {
	View().layout_(HLayout(*labels.size.collect(tabButton)).spacing_(2).margins_(0)).maxHeight_(100);
};

var mainView = VLayout(
		tabs.(),
		stackView
).spacing_(4).margins_(0);

var shutdown = {
	midiCC.free;
	s.quit({
		0.exit;
	});
};

var initGUI = {

	QtGUI.palette = QPalette.dark;

	w = Window("AirKit", border: machine[\border])
		.bounds_(Rect(0,0,1280,800))
		.layout_(mainView)
		.front
		.background_(Color.black.lighten(0.25));

	if(machine[\fullScreen], { w.fullScreen });

	w.onClose = {
		shutdown.();
	};

	// Boot asks for HDMI and lets ensureHdmi decide - so startup and the
	// button press are the same path, and there is only one place that
	// knows how to build the second-screen window.
	setVisualTarget.(\hdmi);

	CmdPeriod.doOnce({
		w.close;
		if(z.notNil, { z.close });
	});
};


"SC_JACK_DEFAULT_INPUTS".setenv("system");
"SC_JACK_DEFAULT_OUTPUTS".setenv("system");

s.volume = -2; // db
s.options.blockSize = 128;
s.options.numBuffers = 2048;  
s.options.memSize = 65536;   
s.options.numOutputBusChannels = 2; 
s.options.numInputBusChannels = 1;
s.latency = machine[\latency]; 



MIDIIn.connectAll;

s.waitForBoot({

	midiCC = MIDIFunc.cc({|...args|
		// args[1].postln;
		NetAddr.new("127.0.0.1", 57120).sendMsg(format("/airkit/cc/%",args[1]), args[0]);
	});

	visualCore.latency = s.latency;

	["local port:", NetAddr.localAddr.port].postln;
	NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/startOSCListening", 57120);

	// Which screen the surface is on. Asking for \hdmi RE-PROBES first, so
	// a cable plugged in after boot is picked up here - the button press is
	// the detection. If there is still no second screen the request resolves
	// back to \tab, which is what makes the button "do nothing" without the
	// button itself knowing anything about screens.
	//
	// The resulting target is echoed on /airkit/visualTargetIs so the button
	// snaps back to whatever actually happened.
	setVisualTarget = { |name|
		var target = name.asSymbol;

		if((target == \hdmi) and: { ensureHdmi.().not }, { target = \tab });

		if(target == \hdmi, {
			visualCore.moveSurface(hdmiHost);
			placeholder.(visualHost, "visuals on HDMI");
		},{
			visualCore.moveSurface(visualHost);
			if(hdmiHost.notNil, { placeholder.(hdmiHost, "visuals on panel") });
		});

		NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/visualTargetIs", target.asString);
		target
	};

	// Re-probe the screens and make the second-screen window match what is
	// actually out there. Returns true if there is somewhere to put the
	// surface. Builds the window on first connect and reuses it after, so
	// replugging does not leak a Window per cable event.
	//
	// This is the ONLY place screens are detected after boot. Pressing the
	// HDMI button is the re-detect - deliberately, rather than a timer: the
	// probe shells out to xrandr, and unixCmdGetStdOut blocks the same
	// language thread that runs ~next at 33Hz.
	ensureHdmi = {
		var rect = screens.();
		hdmiRect = rect;
		if(rect.notNil, {
			if(z.isNil, {
				hdmiHost = View().background_(Color.black);
				z = Window("AirKit Visuals", border: false)
					.layout_(VLayout(hdmiHost).margins_(0).spacing_(0))
					.background_(Color.black);
				z.userCanClose = false;
			});
			// setTopLeftBounds bypasses Window.flipY (which measures against
			// the PRIMARY screen height and would put it in the wrong place).
			z.setTopLeftBounds(rect, 0);
			z.front;
			if(w.notNil, { w.front });	// keep the panel focused on the DSI
		},{
			// the cable went away : drop a stale window rather than leave
			// the surface stranded on a screen that is no longer there.
			if(z.notNil, { z.close; z = nil });
			hdmiHost = nil;
		});
		hdmiHost.notNil
	};

	initGUI.();

	// OSCdef, not OSCFunc : re-running main.sc replaces this rather than
	// stacking a second responder on the same address.
	OSCdef(\airkitVisualTarget, { |msg|
		{ setVisualTarget.(msg[1]) }.defer;
	}, '/airkit/visualTarget');

	ShutDown.add({"shut down...".postln});

});


)


