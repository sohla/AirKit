var m  = ~model;

var root   = 0;
var scale  = [0, 2, 4, 7, 9];
var midiLo = 86, midiHi = 100;

var group;
var clock;
var eventTypeName = (\birdsong_ ++ m.ptn).asSymbol;

var energy    = 0;
var prevEnergy = 0;
var twist     = 0;
var tiltOct   = 0;
var restFloor = 0.25;
var urgent    = false;
var lastFlick = 0;
var muted     = false;
var vol       = 1;

var pcs       = (scale + root) % 12;
var ampLoDb   = -16, ampHiDb = -2;
var gapMin    = 2, gapMax = 16;
var maxNotes  = 8;
var allowShrill = true;

var queue   = [];
var cur     = nil;
var gapLeft = 0;
var lastMidi = midiHi - 5;

var freqComp, scaleNotes, snapToScale, unitBeats, mkNote, shrillRun, buildPhrase, gapFor, step, sense;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.95;
m.accelMassFilteredDecay  = 0.1;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay  = 0.2;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay  = 0.7;

//------------------------------------------------------------
SynthDef(\birdsongChirp, { |out=0, freq=2000, g0=0.9, g1=1.0, secs=0.15, amp=0.1, pan=0,
	trill=0.005, trate=7, bright=0.15, air=0.04, verb=0.22|
	var life  = secs + 0.6;
	var fenv  = EnvGen.kr(Env([g0, 1, 1, g1], [0.22, 0.38, 0.4] * secs, \exp));
	var aenv  = EnvGen.kr(Env([0, 1, 0.85, 0], [0.12, 0.55, 0.33] * secs, [-2, 0, -3]));
	var f     = freq * fenv * (1 + (trill * SinOsc.kr(trate, Rand(0, 2pi))));
	var tone  = SinOsc.ar(f) + (SinOsc.ar(f * 2) * bright * 0.6) + (SinOsc.ar(f * 3) * bright * 0.2);
	var breath = BPF.ar(WhiteNoise.ar, f, 0.08) * air * 4;
	var sig   = (tone + breath) * aenv * amp;
	var st    = Pan2.ar(sig, pan);
	var wet   = FreeVerb2.ar(st[0], st[1], verb, 0.55, 0.6);
	Line.kr(0, 1, life, doneAction: Done.freeSelf);
	Out.ar(out, wet);
}).add;

//------------------------------------------------------------
freqComp = { |freq| (2000 / freq).sqrt.clip(0.45, 1.3) };

scaleNotes = {
	var lo = midiLo + tiltOct, hi = midiHi + tiltOct;
	var notes = [];
	(lo..hi).do { |n| if (pcs.includes(n % 12)) { notes = notes.add(n) } };
	if (notes.isEmpty) { notes = [lo + 9] };
	notes
};

unitBeats = {
	var tempo = clock !? { |c| c.tempo } ? 4;
	((0.12 * tempo) / 0.25).round.max(1) * 0.25
};

mkNote = { |midi, beats, g0, g1, trill, trate, bright, air, ampScale, pan|
	(note: midi, beats: beats, g0: g0, g1: g1, trill: trill, trate: trate,
		bright: bright, air: air, ampScale: ampScale, pan: pan)
};

snapToScale = { |midi, dir|
	(0..11).collect({ |i| midi + (i * dir) }).detect({ |n| pcs.includes(n % 12) }) ? midi
};

shrillRun = { |fromMidi, k|
	var u = unitBeats.();
	var sb = (u * 0.5).max(0.25);
	var base = (fromMidi + 12 + 5.rand).clip(midiHi, midiHi + 12);
	k.collect { |i|
		var up = i.even;
		var midi = (base + (if (up) { rrand(1, 4) } { rrand(-4, -1) })).clip(midiHi - 2, midiHi + 14);
		midi = snapToScale.(midi, if (up) { 1 } { -1 });
		mkNote.(midi, sb,
			if (up) { rrand(0.72, 0.85) } { rrand(1.18, 1.35) },
			if (up) { rrand(1.04, 1.15) } { rrand(0.86, 0.95) },
			rrand(0.035, 0.07), rrand(55, 110), 0.35, 0.12,
			0.45 * (1 - (i * 0.04)), rrand(-0.5, 0.5))
	}
};

buildPhrase = {
	var notes = scaleNotes.();
	var u = unitBeats.();
	var n, idx, out = [];
	var e = energy.clip(restFloor, 1.5);
	n = (2 + (e.linlin(restFloor, 1.5, 0, 5)).round + 2.rand).clip(1, maxNotes).asInteger;
	idx = notes.indexOfGreaterThan(lastMidi - 1) ? (notes.size - 1);
	idx = (idx + [-1, 0, 0, 1].choose).clip(0, notes.size - 1);
	n.do { |i|
		var step = [-3, -2, -1, -1, 0, 1, 1, 2, 3].choose;
		var beats, long;
		if (i == 0) { step = [-2, 0, 1, 2].choose };
		idx = if (notes.size > 1) { (idx + step).fold(0, notes.size - 1) } { 0 };
		long = (i == (n - 1)) and: { 0.45.coin };
		beats = if (long) { u * [2, 3].choose } { u * [1, 1, 1, 1.5, 2].choose };
		out = out.add(mkNote.(notes[idx], beats,
			[0.82, 0.88, 0.94, 1.0, 1.0, 1.08].choose,
			[1.0, 1.0, 1.0, 0.96, 1.04].choose,
			if (long) { rrand(0.008, 0.014) } { rrand(0.003, 0.007) },
			rrand(5.5, 9), rrand(0.1, 0.2), rrand(0.03, 0.06),
			1.0, rrand(-0.3, 0.3)));
		lastMidi = notes[idx];
	};
	if (allowShrill and: { (twist > 0.35) or: { 0.3.coin } }) {
		out = out ++ shrillRun.(lastMidi, rrand(4, 8));
	};
	out
};

gapFor = {
	energy.clip(restFloor, 1.3).linlin(restFloor, 1.3, gapMax, gapMin).round.asInteger
};

step = {
	var beats = 0.5;
	cur = nil;
	if (muted.not and: { group.notNil }) {
		if (queue.isEmpty) {
			if ((energy > restFloor) and: { urgent or: { gapLeft <= 0 } }) {
				queue = buildPhrase.();
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
			beats = cur[\beats];
			if (queue.isEmpty) { gapLeft = gapFor.() };
		};
	};
	beats
};

sense = { |sens|
	prevEnergy = energy;
	energy = m.accelMassFiltered * (0.5 / sens);
	twist  = m.rrateMassFiltered;
	if ((energy > (restFloor + 0.1)) and: { prevEnergy <= (restFloor + 0.1) }) { urgent = true };
	tiltOct = switch(tiltOct,
		0,   { if (m.gyroYFiltered > 0.4) { 12 } { if (m.gyroYFiltered < -0.4) { -12 } { 0 } } },
		12,  { if (m.gyroYFiltered < 0.25) { 0 } { 12 } },
		-12, { if (m.gyroYFiltered > -0.25) { 0 } { -12 } }
	);
	if (allowShrill and: { twist > 0.8 } and: { TempoClock.beats > (lastFlick + 1.2) }
		and: { energy > restFloor }) {
		lastFlick = TempoClock.beats;
		queue = shrillRun.(lastMidi, rrand(3, 6)) ++ queue;
	};
};

//------------------------------------------------------------
~init = ~init <> { |d|
	var port = d.port;
	clock = TempoClock(2);
	group = Group.new;

	Event.addEventType(eventTypeName, { |e|
		var n = cur;
		if (n.notNil and: { group.notNil } and: { muted.not }) {
			var tempo = clock !? { |c| c.tempo } ? 4;
			var midi  = n[\note];
			var freq  = midi.midicps;
			var db    = energy.clip(restFloor, 1.5).lincurve(restFloor, 1.5, ampLoDb, ampHiDb, -1.5);
			var vy    = midi.linlin(midiLo, midiHi + 12, 0.6, -0.6);
			var vsize = db.linlin(ampLoDb, ampHiDb, 8, 40);
			~instrument = \birdsongChirp;
			~out    = 0;
			~group  = group;
			~freq   = freq;
			~g0     = n[\g0];
			~g1     = n[\g1];
			~secs   = (n[\beats] / tempo).clip(0.035, 0.6);
			~amp    = db.dbamp * n[\ampScale] * freqComp.(freq) * vol;
			~pan    = n[\pan];
			~trill  = n[\trill];
			~trate  = n[\trate];
			~bright = n[\bright];
			~air    = n[\air];
			~sustain = ~secs;
			~type = \note;
			currentEnvironment.play;
			(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: port, shape: \circle, sx: n[\pan], ex: n[\pan], sy: vy, ey: vy, startSize: vsize, endSize: vsize * 2, startColor: Color.white, endColor: Color(1, 1, 1, 0), duration: 0.5).play;
		};
	});

	Pdef(m.ptn, Pbind(
		\type, eventTypeName,
		\dur,  Pfunc { step.() }
	));
	Pdef(m.ptn).play(clock);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var g = group;
	var c = clock;
	group = nil;
	clock = nil;
	muted = true;
	queue = []; cur = nil;
	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);
	c !? { c.stop };
	if (g.notNil) {
		fork {
			1.5.wait;
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
	};
};

//------------------------------------------------------------
~next = { |d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	sense.(sens);
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
