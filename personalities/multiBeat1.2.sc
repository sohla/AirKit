var m = ~model;
var group;
var drone;
var patternBus, droneBus;
var patternVerb, droneVerb;

var droneNote = 60 - 12;   // multiBeatSynth1's pool note + its offsets, at root 0
var divs = [1, 2, 3, 4, 8];
var pool = [0, 4, 7, 11, 12, 11, 7, 2] - 12;

var divPat  = Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx));
var stepPat = Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx));
var notePat = Pswitch(divs.collect({ |n| Pseq(pool.keep(n), 1) }), Pkey(\divIdx));

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\multiBeatVoice, {|out=0, freq=440, amp=0.2, pan=0,
    attack=0.005, release=0.4, ffreq=3000|
	var env = EnvGen.kr(Env.perc(attack, release), doneAction: 2);
	var sig = Pulse.ar(freq * [1,1.006], SinOsc.ar(1,0,1,1), 0.5) + SinOsc.ar(freq * 0.5, 0, 1.1);
	sig = RLPF.ar(sig, ffreq.clip(30, 12000), 0.3).tanh;
	Out.ar(out, Pan2.ar(sig, pan, amp * env));
}).add;

SynthDef(\multiBeatDrone, {|out=0, freq=110, amp=0.05, pan=0,
    attack=1.0, release=0.8, ffreq=1200, gate=1, lag=4.1|
	var env = EnvGen.kr(Env.asr(attack, 1, release, \sin), gate, doneAction: 2);
	var f = freq.lag(lag);
	var sig = Pulse.ar(f, SinOsc.ar(0.3,0,1,1), 0.3) + SinOsc.ar(f / 4, 0, 0.6);
	sig = RLPF.ar(sig, ffreq.lagud(1.9,1.3).clip(80, 12000), 0.4);
	Out.ar(out, Pan2.ar(sig, pan, amp.lagud(0.7,1.9) * env));
}).add;

SynthDef(\multiBeatVerb, {|in=0, out=0, mix=0.3, room=0.8, damp=0.4, gate=1, release=1.4|
	var sig = In.ar(in, 2);
	var env = EnvGen.kr(Env.asr(0.01, 1, release), gate, doneAction: 2);
	sig = FreeVerb2.ar(sig[0], sig[1], mix, room, damp);
	sig = LeakDC.ar(sig);
	Out.ar(out, sig * env);
}).add;

//------------------------------------------------------------
~vdef.(\orbitCircle, { |ev, c|
	var orbit = (ev[\modulation] ? ())[\orbit] ? 0;
	c[\draw].(\square, (pos: c[\pos] + (orbit @ 0)));
	nil
});

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;
	patternBus = Bus.audio(s, 2);
	droneBus = Bus.audio(s, 2);

	drone = Synth(\multiBeatDrone, [
		\out, droneBus, \amp, 0.025, \freq, droneNote.midicps, \ffreq, 500, \gate, 1
	], group);

	patternVerb = Synth.tail(group, \multiBeatVerb, [\in, patternBus, \out, 0, \mix, 0.2]);
	droneVerb = Synth.tail(group, \multiBeatVerb, [\in, droneBus, \out, 0, \mix, 0.4]);

	Pdef(m.ptn,
		Pbind(
			\instrument, \multiBeatVoice,
			\group, group,
			\out, patternBus.index,

			\div,  divPat,
			\step, stepPat,
			\note, notePat,
			\root, Pseq([0,0,0,0,3,3,3,-2,-2,1,0,0,0,3,3,-2,-4,-4].stutter(16), inf),
			// \octave, Prand([4,5,6,7], inf), // z?
			\dur,  Pkey(\div).reciprocal * 0.5,
			\pan, Pwhite(-0.2, 0.2),

			\type, \customVisualEvent,
			\shape, \orbitCircle,
			\fill, true,
			\sy, 0.1,
			\ey, Pkey(\sy),
			\cyc, Plazy({ var ph = 0; Pfunc({ |e| ph = (ph + (e[\dur] * (e[\orbitSpeed] ? 0.25))).wrap(0, 1) }) }),
			\rotation, Pfunc({ |e| (e[\cyc] * 2pi) - 0.5pi }),
			\modulation, Pfunc({ |e| (orbit: (e[\orbitSize] ? 400) * 0.5, amp: 0) }),
			\startSize, Pkey(\amp) * 200,
			\endSize, Pkey(\amp) * 400,
			\startColor, Color.new(0.6, 1.0, 0.8),
			\endColor, Color.new(0.1, 0.4, 0.6).alpha_(0.0),
			\startWidth, 3,
			\endWidth, 0.4,
			\duration, Pkey(\dur) * 4,
			\func, Pfunc({|e| ~onEvent.(e)}),

			\args, #[],
		)
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\ffreq, 2000);

	Pdef(m.ptn).play(quant: 0.25);
	m.com.bgColor = Color.black;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var g = group;
	var verbs = [patternVerb, droneVerb];
	var buses = [patternBus, droneBus];
	var tail = 1.8;
	group = nil;
	patternVerb = nil;
	droneVerb = nil;
	patternBus = nil;
	droneBus = nil;

	Pdef(m.ptn).remove;
	if (drone.notNil) { drone.set(\gate, 0) };
	drone = nil;
	fork {
		tail.wait;
		verbs.do { |v| v !? { v.set(\gate, 0) } };
		1.5.wait;
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
		buses.do { |b| b !? { b.free } };
	};
};

//------------------------------------------------------------
~onEvent = {|e|

	m.com.root = e.root;
	m.com.dur = e.dur;

	if (drone.notNil) { drone.set(\freq, (droneNote + (e[\root] ? 0)).midicps) };
};

//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var idx = m.accelMassFiltered.lincurve(0, 7.5 * sens, 0, divs.size - 1, -2).round.asInteger.clip(0, divs.size - 1);
	var amp = m.accelMassFiltered.lincurve(0, 2.0 * sens, -35, -5, -1);
	var octave = ((d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5) * 2).lincurve(-1.0, 1.0, 6, 9, 1).asInteger;

	var ffreq = m.accelMassFiltered.lincurve(0, 2.0 * sens, 200, 1200, -2);
	var rel = m.accelMassFiltered.lincurve(0, 2.5 * sens, 3.1,0.1, 1);
	var droneAmp = m.accelMassFiltered.lincurve(0, 2.0 * sens, -32, -5, -1);
	var droneFfreq = m.accelMassFiltered.lincurve(0, 1.0 * sens, 500, 3000, 2);
	var orbitSize = 800;
	var orbitSpeed = 0.1;

	if(amp < 33.neg, { amp = 110.neg });
	amp = amp - (idx * 3);
	if(droneAmp < 30.neg, { droneAmp = 120.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\octave, octave);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol);
	Pdef(m.ptn).set(\ffreq, ffreq);
	Pdef(m.ptn).set(\attack, 0.0003);
	Pdef(m.ptn).set(\release, rel * 0.5);
	Pdef(m.ptn).set(\orbitSize, orbitSize);
	Pdef(m.ptn).set(\orbitSpeed, orbitSpeed);

	if (drone.notNil) {
		drone.set(
			\amp, droneAmp.dbamp * vol,
			\ffreq, droneFfreq
		);
	};
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, magenta, cyan]

	// RAW values
	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// Gyro
	// [(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];//left right

	// MODEL values
	// Acceleration
	// [m.accelMass, m.accelMassFiltered].lincurve(0.0,5.0,0.0,1.0,0);
	// Rotation Rate
	// [m.rrateMass, m.rrateMassFiltered].lincurve(0.0,1.0,0.0,1.0,0);

	// COMPUTED values : this file's own ~next, recomputed
	// [m.accelMassFiltered.lincurve(0, 1.5, 0, divs.size - 1, 1).round / (divs.size - 1)];//divIdx
	// [m.accelMassFiltered.lincurve(0, 1.0, -60, -12, -1).dbamp];//amp
	// [m.accelMassFiltered.lincurve(0, 1.0, 700, 6000, 2) / 6000];//ffreq
	// [m.accelMassFiltered.lincurve(0, 1.0, 0.1, 3.2, 2) * 0.1];//release
	// [m.accelMassFiltered.lincurve(0, 1.0, 500, 3000, 2) / 3000];//droneFfreq
	// [m.accelMassFiltered.lincurve(0, 2.0, -32, -10, -1).dbamp];//droneAmp

	[m.accelMass, m.accelMassFiltered];
};
