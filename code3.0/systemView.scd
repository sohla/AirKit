var bh = 100;

// The panel was written for the RPI; procfs/sysfs and `hostname -I` don't
// exist on macOS, so each probe picks its own command per platform.
var isOSX = thisProcess.platform.name == \osx;
var cores = if(isOSX, {"sysctl -n hw.ncpu"}, {"nproc"}).unixCmdGetStdOut.asInteger.max(1);

// ask the routing table which interface is actually up rather than
// assuming en0 (Wi-Fi on most Macs, but wrong when we're on ethernet)
var hosts = if(isOSX,
	{"ipconfig getifaddr $(route -n get default 2>/dev/null | awk '/interface:/{print $2}')"},
	{"hostname -I"}).unixCmdGetStdOut.stripWhiteSpace.replace($ ," | ");

var getSCLoad = {
	var pidLine, sumLine, raw, norm;
    pidLine = "pgrep -f sclang | tr '\n' ',' | sed 's/,$//'".unixCmdGetStdOut.stripWhiteSpace;
    if (pidLine.notEmpty) {
		// `-o %cpu=` suppresses the header on both BSD and GNU ps
        sumLine = ("ps -p " ++ pidLine ++ " -o %cpu= | awk '{s+=$1} END{print s}'")
            .unixCmdGetStdOut.stripWhiteSpace;
        raw  = sumLine.asFloat;
        norm = (raw / cores).round(0.1);
        [raw, norm]
    } {
        [0.0, 0.0]  // sclang not found
    }
};

var getSystemLoad = {
    var result = if(isOSX,
		{"sysctl -n vm.loadavg".unixCmdGetStdOut.replace("{","").replace("}","").stripWhiteSpace},
		{"cat /proc/loadavg".unixCmdGetStdOut});
    var load = result.split($ )[0].asFloat * 100 / cores;
	load.round(0.1)
};

// no unprivileged temp/fan source on macOS (powermetrics needs sudo)
var getTemp = {
	if(isOSX, { nil }, {
		var result = "cat /sys/class/thermal/thermal_zone0/temp".unixCmdGetStdOut;
		result.asInteger / 1000.0 // Convert millidegrees to degrees
	})
};

var getFan = {
	if(isOSX, { nil }, {
		"cat /sys/class/thermal/cooling_device0/cur_state".unixCmdGetStdOut.asInteger
	})
};

var thermalString = {
	var temp = getTemp.();
	if(temp.isNil,
		{ "Temp: n/a.   Fan: n/a" },
		{ "Temp: "++temp.asStringPrec(2)++"°C"++".   Fan: "++getFan.() })
};

var cpuView = UserView()
	.minWidth_(60)
	.maxHeight_(30)
	.frameRate_(10)
	.animate_(true)
	.drawFunc_({|uv|
			("System Load: "++getSystemLoad.().asStringPrec(2)++"%").drawAtPoint(10@4, Font.default, Color.yellow);
			("SC Load: "++getSCLoad.()[1].asStringPrec(2)++"%").drawAtPoint(200@4, Font.default, Color.yellow);
			thermalString.().drawAtPoint(300@4, Font.default, Color.yellow);
});

var serverView = UserView()
    .minWidth_(60)
    .maxHeight_(30)
	.frameRate_(10)
    .animate_(true)
    .drawFunc_({|uv|
			("Server: "++s.peakCPU.asStringPrec(3)++"%").drawAtPoint(10@4, Font.default, Color.yellow);
			("UGens: "++s.numUGens).drawAtPoint(250@4, Font.default, Color.yellow);
			("Groups: "++s.numGroups).drawAtPoint(350@4, Font.default, Color.yellow);
			("Synths: "++s.numSynths).drawAtPoint(450@4, Font.default, Color.yellow);
			("Hosting: "++hosts).drawAtPoint(550@4, Font.default, Color.yellow);
			("Port: "++NetAddr.	localAddr.port).drawAtPoint(1000@4, Font.default, Color.yellow);
});

var meterView = View()
	.minWidth_(150)
	.maxWidth_(150);

var nodeTree = View()
	.background_(Color.black(0.25))
	.minWidth_(700)
	.maxHeight_(800);

var turnOffButton = Button()
		.font_(Font(size:18, bold:true))
		// .maxWidth_(140)
		.maxHeight_(bh)
		.states_([["TURN OFF",Color.black,Color.red(1)]])
		.action_({
				"sudo shutdown now".unixCmdGetStdOut;
		});

var quitButton = Button()
		.font_(Font(size:18, bold:false))
		// .maxWidth_(140)
		.maxHeight_(bh)
		.states_([["QUIT",Color.black,Color.white.alpha_(0.75)]])
		.action_({
				s.quit({
					0.exit;
				});
		});

var restartButton = Button()
		.font_(Font(size:18, bold:false))
		.maxHeight_(bh)
		.states_([["Restart",Color.black,Color.white.alpha_(0.75)]])
		.action_({
			// "sudo reboot".unixCmd;
		});


var oscThruButton = Button()
		.font_(Font(size:18, bold:false))
		// .maxWidth_(140)
		.maxHeight_(bh)
		.states_([
			["OSC Thru",Color.black,Color.gray(0.5)],
			["OSC Thru",Color.white(),Color.green(0.25)]
			])
		.action_({|b|
			NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/oscThru", b.value.asBoolean);
		});

var networkReset = Button()
	.font_(Font(size:18, bold:false))
	// .maxWidth_(140)
	.maxHeight_(bh)
	.states_([["Network",Color.black,Color.gray(0.5)]])
	.action_({
			("sudo systemctl restart NetworkManager").unixCmdGetStdOut();
	});

var freeAllNodes = Button()
	.font_(Font(size:18, bold:false))
	// .maxWidth_(140)
	.maxHeight_(bh)
	.states_([["Free All Nodes",Color.black,Color.gray(0.5)]])
	.action_({
		s.freeAll;
	});

var freeAllBuffers = Button()
	.font_(Font(size:18, bold:false))
	// .maxWidth_(140)
	.maxHeight_(bh)
	.states_([["Free All buffers",Color.black,Color.gray(0.5)]])
	.action_({
		Buffer.freeAll(s);
	});
	
var version = File.readAllString(Require("machine.scd")[\versionPath]).stripWhiteSpace;

var versionView = StaticText()
	.font_(Font(size:14, bold:false))
	.string_("Version : "++version)
	.minWidth_(140)
	.maxHeight_(bh);


// visual scene : overlay (all devices composited on one canvas)
//             vs grid (one canvas per device)
var sceneButton = Button()
	.font_(Font(size:18, bold:false))
	// .maxWidth_(140)
	.maxHeight_(bh)
	.states_([
		["Overlay",Color.black,Color.gray(0.5)],
		["Grid",Color.white(),Color.blue(0.5)]
		])
	.action_({|b|
		NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/visualScene",
			if(b.value == 0, "overlay", "grid"));
	});

// keep the button in sync when the scene is changed over OSC
var sceneSync = OSCFunc({ |msg|
	{ sceneButton.value_(if(msg[1].asSymbol == \grid, 1, 0)) }.defer;
},'/airkit/visualScene');


// which screen the visual surface lives on : this panel, or the second
// screen. With no HDMI attached main.sc resolves the request back to
// \tab and echoes that, so the button simply snaps back and nothing
// happens - it needs no knowledge of screens itself.
var displayButton = Button()
	.font_(Font(size:18, bold:false))
	.maxHeight_(bh)
	.states_([
		["Panel",Color.black,Color.gray(0.5)],
		["HDMI",Color.white(),Color.blue(0.5)]
		])
	.action_({|b|
		NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/visualTarget",
			if(b.value == 0, "tab", "hdmi"));
	});

var displaySync = OSCFunc({ |msg|
	{ displayButton.value_(if(msg[1].asSymbol == \hdmi, 1, 0)) }.defer;
},'/airkit/visualTargetIs');

var systemView = {
	View().layout_(VLayout(
		HLayout(View().maxWidth_(140), cpuView),
		HLayout(View().maxWidth_(140), serverView),
		HLayout(meterView, nodeTree),
		HLayout(
			versionView,
			sceneButton,
			displayButton,
			Platform.case(\linux, {networkReset},{View()}),
			oscThruButton,
			freeAllBuffers,
			freeAllNodes,
			Platform.case(\linux, {quitButton},{View().maxHeight_(bh)}),
			View(),
			Platform.case(\linux, {turnOffButton},{View().maxHeight_(bh)}),
		)
	))
};

s.plotTreeView(0.5, nodeTree);

ServerMeterView.new(s, meterView, 0@0, 2, 4);

OSCFunc({ |msg, time, addr, recvPort|
	s.quit({
		0.exit;
	});
},'/airkit/quit'); //* not having an NetAddr makes this public ie. external control

systemView;