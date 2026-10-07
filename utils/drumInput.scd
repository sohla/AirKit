// ----------------------------------------------------------------------------
//  DRUM INPUT
//
//  A virtual drummer for the personalities that trigger off SoundIn.ar(0) --
//  temp_input*.sc, hollowWood*.sc. Pair it with PyOSCCam: the camera drives
//  the motion data, this drives the hit.
//
//  Loaded by main.sc at boot on any kit whose machine file sets
//  drumInput: true (machines/desktop.scd). The window opens with AirKit and
//  its posts go to the AirKit console, because it is the same process.
//
//  It has to be that process. scsynth is booted with maxLogins 1, so a second
//  sclang cannot log in, and its node and buffer allocators would start where
//  AirKit's already are. One process, one client.
//
//  Kick and snare each get their own 16-step row. A step is off / normal /
//  accent -- accents matter because those p-files map the detected velocity
//  onto pitch, decay and tone, so a flat velocity parks them at one point.
//
//  Kick is on input channel 0, snare on channel 1 -- SoundIn.ar(0) and
//  SoundIn.ar(1). The kit's machine file must declare numInputs: 2, which also
//  stops Bus.audio handing channel 1 out as a private bus. Note that
//  temp_input*.sc and hollowWood*.sc read SoundIn.ar(0) only, so as written
//  they hear the KICK alone.
//
//  Nothing reaches the speakers. The drums are written into the input buses
//  from a group placed immediately BEFORE the default group, where every
//  personality's own Group.new lives. So \inputTrigger reads them in the same
//  control block it would read a microphone, and no p-file, machine file or
//  main.sc knows this exists. Check it in s.plotTree : drumInput above group 1.
//
//  While PLAY is on, the mic is silenced at the head of that group, so the
//  drummer is the only thing in the input bus -- Out.ar sums, so otherwise the
//  room triggers the p-files right alongside the drums. STOP gives it back.
//
//  The bar beside PLAY is the input as the PERSONALITIES see it. Do not use
//  systemView's s.meter to judge this: ServerMeter plays its input synth at
//  the head of the RootNode, ahead of this group, so it shows the raw mic
//  whatever happens here. The mark on the bar is -32 dB, the floorDb those
//  p-files run.
//
//  "free all nodes" in systemView takes the group with it -- press PLAY again
//  and it rebuilds. "free all buffers" needs a re-run of main.sc.
// ----------------------------------------------------------------------------
(
var folder = "~/Music/cotf_samples/drums".standardizePath;
var kickPath = (folder +/+ "BD*.wav").pathMatch.first;
var snarePath = (folder +/+ "SD*.wav").pathMatch.first;

var steps = 16;
var stepDur = 0.25;
var vels = [0, 0.55, 1.0];

var kickRow  = [2,0,0,0, 0,0,1,0, 2,0,0,0, 0,0,1,0];
var snareRow = [0,0,0,0, 2,0,0,1, 0,0,0,0, 2,0,1,0];

var bpm = 112;
var level = 0.6;
var step = -1;

var kickBus = 0;
var snareBus = 1;

var floorDbMark = -32;
var peak = [0, 0];

var kickBuf, snareBuf;
var group, mute, probe, seq, clock, cmdPeriod;
var win, playhead, playBtn, bpmText, levelText, meter;
var ensureGroup, ensureProbe, fire, start, stop, cells, labelled;

SynthDef(\drumInputVoice, { |bufnum = 0, amp = 0.5, inBus = 0|
	var sig = PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum), doneAction: 2);
	Out.ar(NumOutputBuses.ir + inBus, sig * amp);
}).add;

// Out.ar SUMS, so without this the live mic stays in the bus and the room
// keeps triggering the p-files alongside the drummer. Both channels, since the
// kick and the snare own one each.
SynthDef(\drumInputMute, {
	ReplaceOut.ar(NumOutputBuses.ir, Silent.ar(2));
}).add;

// systemView's ServerMeter reads at the head of the RootNode, ahead of this
// group, so it can only ever show the raw mic. This one lives in the default
// group - the same side of the drums as a personality's \inputTrigger - so it
// shows what the p-files actually get.
SynthDef(\drumInputProbe, { |rate = 20|
	SendPeakRMS.kr(SoundIn.ar([0, 1]), rate, 3, '/drumInputLevel');
}).add;

ensureGroup = { if(group.isNil, { group = Group.new(s.defaultGroup, \addBefore) }); group };

ensureProbe = { if(probe.isNil, { probe = Synth(\drumInputProbe, [], s.defaultGroup, \addToHead) }); probe };

// addToTail, NOT the default addToHead : the voice has to run after the mute
// or the mute wipes it and the input bus goes silent.
fire = { |row, buf, inBus|
	var v = row[step];
	if(v > 0, { Synth(\drumInputVoice, [\bufnum, buf, \amp, vels[v] * level, \inBus, inBus], group, \addToTail) });
};

start = {
	ensureGroup.value;
	ensureProbe.value;
	seq !? (_.stop);
	mute !? (_.free);
	mute = Synth(\drumInputMute, [], group);
	step = -1;
	clock.tempo = bpm / 60;
	seq = Routine({ loop({ step = (step + 1) % steps; fire.(kickRow, kickBuf, kickBus); fire.(snareRow, snareBuf, snareBus); { playhead !? (_.refresh) }.defer; stepDur.wait }) }).play(clock, quant: 0);
};

stop = {
	seq !? (_.stop);
	mute !? (_.free);
	mute = nil;
	step = -1;
	{ playhead !? (_.refresh) }.defer;
};

// a step button owns its own cell : click cycles off -> normal -> accent and
// writes straight back into the row the sequencer reads.
cells = { |row|
	steps.collect({ |i|
		Button()
			.fixedSize_(26 @ 26)
			.states_([
				["", Color.grey(0.5), Color.grey(0.16)],
				["", Color.black, Color.grey(0.5)],
				["", Color.black, Color.white]
			])
			.value_(row[i])
			.action_({ |b| row.put(i, b.value) })
	})
};

labelled = { |name, items| HLayout(*([StaticText().string_(name).fixedWidth_(56)] ++ items ++ [nil])).spacing_(2) };

~drumInputOpen = {
	if(s.serverRunning, {
		~drumInputClose.value;

		kickBuf = Buffer.read(s, kickPath);
		snareBuf = Buffer.read(s, snarePath);

		clock = TempoClock(bpm / 60);
		ensureGroup.value;
		ensureProbe.value;

		OSCdef(\drumInputLevel, { |msg|
			if(probe.notNil and: { msg[1] == probe.nodeID }, {
				peak = [msg[3], msg[5]];
				{ meter !? (_.refresh) }.defer;
			});
		}, '/drumInputLevel', s.addr);

		playhead = UserView().fixedWidth_(446).fixedHeight_(10).drawFunc_({ |v|
			steps.do({ |i|
				Pen.fillColor = if(i == step, { Color.white }, { Color.grey(0.2) });
				Pen.addRect(Rect(i * 28, 0, 26, 10));
				Pen.fill;
			});
		});

		playBtn = Button()
			.fixedSize_(90 @ 30)
			.states_([
				["PLAY", Color.white, Color.grey(0.25)],
				["STOP", Color.black, Color.new(0.9, 0.35, 0.2)]
			])
			.action_({ |b| if(b.value == 1, start, stop) });

		// top bar = kick on channel 0, bottom bar = snare on channel 1
		meter = UserView().fixedWidth_(220).fixedHeight_(22).drawFunc_({ |v|
			var w = v.bounds.width;
			var h = v.bounds.height;
			var markX = floorDbMark.linlin(-60, 0, 0, 1) * w;
			Pen.fillColor = Color.grey(0.16);
			Pen.addRect(Rect(0, 0, w, h));
			Pen.fill;
			Pen.fillColor = Color.new(0.4, 0.9, 0.5);
			peak.do({ |p, i|
				Pen.addRect(Rect(0, i * (h / 2), p.ampdb.linlin(-60, 0, 0, 1) * w, (h / 2) - 2));
			});
			Pen.fill;
			Pen.strokeColor = Color.new(0.9, 0.35, 0.2);
			Pen.line(markX @ 0, markX @ h);
			Pen.stroke;
		});

		bpmText = StaticText().string_(bpm.asInteger.asString ++ " bpm").fixedWidth_(70);
		levelText = StaticText().string_(level.round(0.01).asString).fixedWidth_(70);

		win = Window("dr🥁m b🤖t", Rect(120, 420, 580, 230))
			.front
			.background_(Color.grey(0.1));

		win.layout_(VLayout(
			labelled.("", [playhead]),
			labelled.("KICK", cells.(kickRow)),
			labelled.("SNARE", cells.(snareRow)),
			labelled.("", [
				StaticText().string_("tempo").fixedWidth_(50),
				Slider().orientation_(\horizontal).fixedHeight_(22).fixedWidth_(220).value_(bpm.linlin(40, 200, 0, 1)).action_({ |sl| bpm = sl.value.linlin(0, 1, 40, 200).round(1); clock.tempo = bpm / 60; bpmText.string_(bpm.asInteger.asString ++ " bpm") }),
				bpmText
			]),
			labelled.("", [
				StaticText().string_("level").fixedWidth_(50),
				Slider().orientation_(\horizontal).fixedHeight_(22).fixedWidth_(220).value_(level).action_({ |sl| level = sl.value; levelText.string_(level.round(0.01).asString) }),
				levelText
			]),
			labelled.("", [playBtn, meter])
		).margins_(12).spacing_(6));

		cmdPeriod = { group = nil; mute = nil; probe = nil; peak = [0, 0]; seq !? (_.stop); step = -1; { if(win.notNil, { playBtn.value_(0); playhead.refresh }) }.defer };
		CmdPeriod.add(cmdPeriod);

		win.onClose = { ~drumInputClose.value };

		"[drumInput] group % : writing input bus 0, before group %".format(group.nodeID, s.defaultGroup.nodeID).postln;
		"[drumInput] ~drumInputClose.value; to shut it down".postln;
		win
	}, {
		"[drumInput] no server. Load this into the sclang running AirKit, not a new one.".warn;
	});
};

~drumInputClose = {
	cmdPeriod !? { CmdPeriod.remove(cmdPeriod) };
	cmdPeriod = nil;
	OSCdef(\drumInputLevel) !? (_.free);
	probe !? (_.free);
	probe = nil;
	peak = [0, 0];
	seq !? (_.stop);
	seq = nil;
	clock !? (_.stop);
	clock = nil;
	mute !? (_.free);
	mute = nil;
	group !? (_.free);
	group = nil;
	kickBuf !? (_.free);
	snareBuf !? (_.free);
	kickBuf = nil;
	snareBuf = nil;
	step = -1;
	playhead = nil;
	playBtn = nil;
	meter = nil;
	bpmText = nil;
	levelText = nil;
	if(win.notNil, { var dying = win; win = nil; dying.onClose = nil; dying.close });
};

~drumInputOpen.value;
)
