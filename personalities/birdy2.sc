var m  = ~model;

var group;
var clock;
var fxBus;
var verbSynth;
var birdType = (\birdy2_ ++ m.ptn).asSymbol;

var energy     = 0;
var prevEnergy = 0;
var twist      = 0;
var tiltOct    = 0;
var restFloor  = 0.25;
var urgent     = false;
var lastFlick  = 0;
var flickPending = false;
var muted      = false;
var birdPan    = 0;

var queue    = [];
var cur      = nil;
var gapLeft  = 0;
var lastMidi = 82;

var freqComp, scaleNotes, shrillRun, whipRun, buildPhrase, gapFor, step, sense;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.95;
m.accelMassFilteredDecay  = 0.1;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay  = 0.2;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay  = 0.7;

//------------------------------------------------------------
SynthDef(\birdy2, { |out=0, freq=1000, amp=0.1, pan=0, g0=0.9, g1=1.0,
	glide=0.03, hold=0.05, sweep=0.06, tail=0.05,
	trill=0.005, trate=3, bright=0.15, air=0.04, crack=0|
	var body  = glide + hold + sweep;
	var fenv  = EnvGen.kr(Env([g0, 1, 1, g1], [glide, hold, sweep], [\exp, 0, \sine]));
	var aenv  = EnvGen.kr(Env([0, 1, 0.85, 0], [0.12 * body, 0.88 * body, tail], [-2, 0, -3]));
	var f     = freq * fenv * (1 + (trill * SinOsc.kr(trate, Rand(0, 2pi))));
	var tone  = SinOsc.ar(f) + (SinOsc.ar(f * 2) * bright * 0.6) + (SinOsc.ar(f * 3) * bright * 0.2);
	var breath = BPF.ar(WhiteNoise.ar, f, 0.08) * air * 4;
	var snap  = BPF.ar(WhiteNoise.ar, f * 3, 0.3) * EnvGen.ar(Env([0, 0, 1, 0], [glide + hold, 0.001, 0.03], -8)) * crack;
	var sig   = ((tone + breath) * aenv) + snap;
	Line.kr(0, 1, body + tail + 0.05, doneAction: Done.freeSelf);
	Out.ar(out, Pan2.ar(sig * amp, pan));
}).add;

SynthDef(\birdy2Verb, { |in=0, out=0, mix=0.3, room=0.6|
	var sig = In.ar(in, 2);
	Out.ar(out, FreeVerb2.ar(sig[0], sig[1], mix, room, 0.5));
}).add;

//------------------------------------------------------------
freqComp = { |freq| (2000 / freq).sqrt.clip(0.45, 1.3) };

scaleNotes = { |pcs|
	var lo = 74 + tiltOct, hi = 88 + tiltOct;
	var notes = [];
	(lo..hi).do { |n| if (pcs.includes(n % 12)) { notes = notes.add(n) } };
	if (notes.isEmpty) { notes = [lo + 9] };
	notes
};

shrillRun = { |fromMidi, k|
	var sb = 0.5 * rrand(0.5, 1.5);
	var base = (fromMidi + 12 + 5.rand).clip(88, 100);
	k.collect { |i|
		var up = i.even;
		(
			kind:     \chirp,
			note:     (base + (if (up) { rrand(1, 4) } { rrand(-4, -1) })).clip(86, 102),
			beats:    sb,
			g0:       if (up) { rrand(0.72, 0.85) } { rrand(1.18, 1.35) },
			g1:       if (up) { rrand(1.04, 1.15) } { rrand(0.86, 0.95) },
			trill:    rrand(0.035, 0.07),
			trate:    rrand(7.5, 15),
			bright:   0.25,
			air:      0.32,
			crack:    0,
			ampScale: 0.45 * (1 - (i * 0.04)),
			pan:      rrand(-0.5, 0.5)
		)
	}
};

whipRun = { |toMidi, k, interval, hold, sweep|
	k.collect {
		(
			kind:     \whip,
			note:     toMidi - interval,
			beats:    1,
			g0:       1,
			g1:       interval.midiratio,
			hold:     hold,
			sweep:    sweep,
			trill:    0.002,
			trate:    5,
			bright:   0.15,
			air:      0.04,
			crack:    0.6,
			ampScale: 1.0,
			pan:      rrand(-0.3, 0.3)
		)
	}
};

buildPhrase = { |e|
	var notes = scaleNotes.(e[\pcs] ? [0]);
	var maxN = e[\notes] ? 8;
	var whipAt = e[\whipAt] ? 1;
	var n, idx, out = [];
	var en = energy.clip(restFloor, 1.5);
	n = (2 + (en.linlin(restFloor, 1.5, 0, 5)).round + 2.rand).clip(1, maxN).asInteger;
	idx = notes.indexOfGreaterThan(lastMidi - 1) ? (notes.size - 1);
	idx = (idx + [-1, 0, 0, 1].choose).clip(0, notes.size - 1);
	n.do { |i|
		var stepBy = [-3, -2, -1, -1, 0, 1, 1, 2, 3].choose;
		var long;
		if (i == 0) { stepBy = [-2, 0, 1, 2].choose };
		idx = if (notes.size > 1) { (idx + stepBy).fold(0, notes.size - 1) } { 0 };
		long = (i == (n - 1)) and: { 0.45.coin };
		out = out.add((
			kind:     \chirp,
			note:     notes[idx],
			beats:    if (long) { [2, 3].choose } { [1, 1, 1, 1.5, 2].choose },
			g0:       [0.82, 0.88, 0.94, 1.0, 1.0, 1.08].choose,
			g1:       [1.0, 1.0, 1.0, 0.96, 1.04].choose,
			trill:    if (long) { rrand(0.008, 0.014) } { rrand(0.003, 0.007) },
			trate:    rrand(2.75, 4.5),
			bright:   rrand(0.1, 0.2),
			air:      rrand(0.03, 0.06),
			crack:    0,
			ampScale: 1.0,
			pan:      rrand(-0.3, 0.3)
		));
		lastMidi = notes[idx];
	};
	if (twist > 0.35) {
		out = out ++ shrillRun.(lastMidi, rrand(4, 8));
	};
	if (energy > whipAt) {
		out = out ++ whipRun.(lastMidi, energy.linlin(whipAt, whipAt + 0.6, 1, 3).round.asInteger,
			e[\whipInterval] ? 12, e[\hold] ? 0.1, e[\sweep] ? 0.05);
	};
	out
};

gapFor = { |gMin, gMax|
	energy.clip(restFloor, 1.3).linlin(restFloor, 1.3, gMax, gMin).round.asInteger
};

step = { |e|
	var ampFloor = -60.dbamp;
	var beats = 0.5;
	cur = nil;
	if (muted.not and: { group.notNil } and: { (e[\amp] ? 0) > ampFloor }) {
		if (flickPending) {
			flickPending = false;
			if (energy > restFloor) { queue = shrillRun.(lastMidi, rrand(3, 6)) ++ queue };
		};
		if (queue.isEmpty) {
			if ((energy > restFloor) and: { urgent or: { gapLeft <= 0 } }) {
				queue = buildPhrase.(e);
				urgent = false;
			} {
				gapLeft = gapLeft - 1;
			};
		} {
			if ((energy < (restFloor * 0.8)) and: { queue.size > 1 }) {
				queue = queue.keep(1);
			};
		};
		if (queue.notEmpty) {
			cur = queue.removeAt(0);
			cur[\beats] = cur[\beats] * (e[\dur] ? 0.25);
			beats = cur[\beats];
			if (queue.isEmpty) { gapLeft = gapFor.(e[\gapMin] ? 2, e[\gapMax] ? 16) };
		};
	} {
		queue = [];
	};
	beats
};

sense = {
	prevEnergy = energy;
	energy = m.accelMassFiltered;
	twist  = m.rrateMassFiltered;
	if ((energy > (restFloor + 0.1)) and: { prevEnergy <= (restFloor + 0.1) }) { urgent = true };
	tiltOct = switch(tiltOct,
		0,   { if (m.gyroYFiltered > 0.4) { 12 } { if (m.gyroYFiltered < -0.4) { -12 } { 0 } } },
		12,  { if (m.gyroYFiltered < 0.25) { 0 } { 12 } },
		-12, { if (m.gyroYFiltered > -0.25) { 0 } { -12 } }
	);
	if ((twist > 0.8) and: { TempoClock.beats > (lastFlick + 1.2) } and: { energy > restFloor }) {
		lastFlick = TempoClock.beats;
		flickPending = true;
	};
};

//------------------------------------------------------------
~init = ~init <> {
	clock = TempoClock(2);
	group = Group.new;
	fxBus = Bus.audio(s, 2);
	verbSynth = Synth.tail(group, \birdy2Verb, [\in, fxBus, \out, 0, \mix, 0.3, \room, 0.6]);

	Event.addEventType(birdType, { |e|
		var n = cur;
		if (n.notNil and: { group.notNil } and: { muted.not }) {
			var tempo = clock !? { |c| c.tempo } ? 4;
			var freq  = n[\note].midicps;
			var secs  = (n[\beats] / tempo).clip(0.035, 0.6);
			var times = if (n[\kind] == \whip) { [0.01, n[\hold], n[\sweep], 0.15] } { [0.22, 0.38, 0.4, 0.33] * secs };
			var spread = 0.3;
			~instrument = \birdy2;
			~out    = fxBus;
			~group  = group;
			~freq   = freq;
			~amp    = (~amp ? 0) * n[\ampScale] * freqComp.(freq);
			~pan    = (birdPan + (n[\pan] * spread)).clip(-1, 1);
			~g0     = n[\g0];
			~g1     = n[\g1];
			~glide  = times[0];
			~hold   = times[1];
			~sweep  = times[2];
			~tail   = times[3];
			~trill  = n[\trill];
			~trate  = n[\trate];
			~bright = n[\bright];
			~air    = n[\air];
			~crack  = n[\crack];
			~sendGate = false;
			~type = \note;
			currentEnvironment.play;
		};
	});

	Pdef(m.ptn, Pbind(
		\type, birdType,
		\dur,  Pfunc { |e| step.(e) }
	));
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).play(clock);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var g = group;
	var b = fxBus;
	var c = clock;
	group = nil;
	clock = nil;
	fxBus = nil;
	verbSynth = nil;
	muted = true;
	queue = []; cur = nil;
	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(birdType);
	c !? { c.stop };
	fork {
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
		b !? { b.free };
	};
};

//------------------------------------------------------------
~next = { |d|
	var pcs = [0, 2, 5, 7];
	var gain = -8;
	var whipInterval = 12;
	var holdTime = 0.1;
	var whipTime = 0.05;
	var panDrift = 0.01;
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol  = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var full = restFloor + (0.6 * sens);
	var db   = m.accelMassFiltered.lincurve(restFloor, full, -30, 0, -2);
	var dur  = m.accelMassFiltered.lincurve(0.0, 2.6 * sens, 2, 0.2, -1);

	sense.();
	birdPan = (birdPan + panDrift.rand2).fold(-0.8, 0.8);

	if(m.accelMassFiltered <= restFloor, { db = -110 });

	Pdef(m.ptn).set(\amp, (db + gain).dbamp * vol);
	Pdef(m.ptn).set(\pcs, pcs, \notes, 8, \gapMin, 2, \gapMax, 16);
	Pdef(m.ptn).set(\whipAt, full, \whipInterval, whipInterval, \hold, holdTime, \sweep, whipTime);
	Pdef(m.ptn).set(\dur, dur);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 2;
~plot = { |d, p|

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;

	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// [m.accelMass, m.accelMassFiltered];

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right

	[energy, twist, m.gyroYFiltered];
};
