var m = ~model;
var synth;
var synth2;
var drone;
var group;

var lastTime=0;
// var notes = [0,0,7,0,0,0,7,0,7,5,0,0,0,7,0,0,7,5,0,0,0,-2,7,-2,7,5,-2,-2,7,5,-2,-2,-2,-4,7,5,-4,-4,7,5,-2,-2,7,5,-2,7,5];
// var notes = [0,0,7,0,0,0,7,0,7,5,0,0,0,7,0,0,7,5,0,0,0];
var notes = [0];
// var mr = [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-4,-4,-4,-4,-4,-4,-4,-2,-2,-2,-2,-2,-2,-2].stutter(2);
var mr = [0].stutter(2);
var roots = [12].stutter(47*2);
var currentNote = notes[0];
var currentRoot = roots[0];

var armed = false;
var quietFor = 0;
var lastNow;
var swingNotes = [36,33,28,31].stutter(4);

var swingBase = swingNotes[0];

m.accelMassFilteredAttack = 0.6;
m.accelMassFilteredDecay = 0.2;
// m.accelMassFilteredAttack = 0.2;
// m.accelMassFilteredDecay = 0.99;

//------------------------------------------------------------
SynthDef(\timSwingWind2Bell, { |out, freq=111, gate=0, amp = 0.3, pchx=0|
	var env = EnvGen.ar(Env.asr(0.007,1.0,5.0), gate, doneAction:Done.freeSelf);
	var follow = Amplitude.kr(amp, 0.03, 0.03);
	var trig = PinkNoise.ar(0.01) * env * follow.lag(2);
	var sig =  DynKlank.ar(`[[freq, freq*1.007].lag(3), [1,0.4,0.3], [2, 1, 1, 1]], trig);
  	var tone = SinOsc.ar([freq * 2, freq * 0.5] * LFNoise2.ar(12,0.02,1), LFNoise2.ar(3,6),[0.1,0.12 ]* env * 6);
	var dly = DelayC.ar(sig + tone,0.03,[0.02,0.027]);
	var eq = BLowShelf.ar(dly,1000,0.4, -7);
	Out.ar(out, eq * amp);
}).add;

SynthDef(\timSwingWind2Drone, {|out,freq = 1000, amp = 0.5, att = 0.02, dec = 0.3, sus = 1, rel = 5, gate = 1, fb = 1.2, ch=10|
	var snd, env;
	env = EnvGen.kr(Env.adsr(att, dec, sus, rel), gate: gate, doneAction: 2);

	snd = SinOsc.ar(freq,
		LocalIn.ar(2) * LFNoise1.ar(0.1,1),
		LFNoise1.ar(ch.lag(0.3),7)
		// LFNoise1.ar(MouseY.kr(0.2,19),MouseX.kr(0.1,4.1))
	).tanh * amp.lag(0.3);
	2.do{
		snd = AllpassL.ar(snd,0.3,{0.1.rand+0.03}!2,5)
	};
	Out.ar(out, snd.tanh * env);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;
	drone = Synth(\timSwingWind2Drone, [\freq, swingNotes[0].midicps, \gate, 1, \amp, 0], group);
};

~deinit = ~deinit <> {
	var oldGroup = group;
	group = nil;
	synth !? { synth.set(\gate,0) };
	synth = nil;
	drone !? { drone.set(\gate,0) };
	drone = nil;
	fork {
		5.5.wait;
		if (oldGroup.notNil) {
			s.bind { oldGroup.freeAll };
			s.sync;
			oldGroup.free;
		};
	};
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var move = m.accelMassFiltered.linlin(0, 6 * sens,0,1.5);
	var a = m.accelMassFiltered * 0.5;
	var amp = m.accelMassFiltered.lincurve(0, 3 * sens,0.02,0.3,-5);
	var gap = m.accelMassFiltered.lincurve(0, 3 * sens,0.8,0.1,-5);
	var oct = d.sensors.gyroEvent.y.linlin(-1,1,1,5).floor * 12;
	var av = d.sensors.gyroEvent.y.lincurve(-1,1,1,0.1,-2);
	var swingMr = swingNotes[0] - swingBase;

	var quietThresh = 0.2;
	var quietSecs = 0.35;
	var now = Main.elapsedTime;
	var swingA = m.accelMassFiltered.linlin(0,1,0.0,0.6);
	var ch = m.accelMassFiltered.linlin(0.0,2.5,0.02,44);
	// var pchs = [0,12,24,36,48];
	// var i = (d.sensors.gyroEvent.y.abs / pi) * (pchs.size);

	if(a<0.02,{a=0});
	// if(a>0.9,{a=1});
	if((move > 0.02) and: { group.notNil }, {
    // synth.set(\amp, amp);
  	if(TempoClock.beats > (lastTime + gap),{
			lastTime = TempoClock.beats;
			notes = notes.rotate(-1);
			currentNote = notes[0];
			roots = roots.rotate(-1);
			// mr = mr.rotate(-1);
			currentRoot = roots[0];
			// m.com.root = currentRoot + swingNotes - 24;
			m.com.root = currentRoot + swingMr;
      // synth.set(\freq, (36 + currentNote).midicps);
			synth = Synth(\timSwingWind2Bell, [
				\freq, (36 + currentNote + currentRoot + swingMr + oct).midicps,
				\gate, 1,
				\amp, amp * 0.4 * av * vol,
			], group);
			synth.server.sendBundle(0.3,[\n_set, synth.nodeID, \gate, 0]);
		});
	});

	if(swingA<0.01,{swingA=0});
	if(swingA>0.9,{swingA=1.0});
	drone !? { drone.set(\amp, swingA * 0.3, \ch, ch, \freq, (swingNotes[0] + currentRoot).midicps) };

	quietFor = if(m.accelMassFiltered > quietThresh, { armed = true; 0 }, { quietFor + (now - (lastNow ? now)) });
	lastNow = now;
	if(armed and: { quietFor >= quietSecs }, { swingNotes = swingNotes.rotate(-1); armed = false });
	// synth.set(\freq,(notes[0] + m.com.root).midicps);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	// [m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered,d.sensors.gyroEvent.y * 0.1];
	// [d.sensors.rotateEvent.y];
	// [d.sensors.rotateEvent.x, d.sensors.rotateEvent.y, d.sensors.rotateEvent.z];
	[m.accelMassFiltered * 0.25];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};
