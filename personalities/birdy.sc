var m  = ~model;

var group;
var clock;
var eventTypeName = (\birdy_ ++ m.ptn).asSymbol;
var whipPtn       = (m.ptn ++ "whip").asSymbol;

var energy    = 0;
var prevEnergy = 0;
var twist     = 0;
var tiltOct   = 0;
var restFloor = 0.25;
var urgent    = false;
var lastFlick = 0;
var muted     = false;

var idlePcs   = [0, 2, 4, 7, 9];
var flickPending = false;
var ampFloor  = -60.dbamp;

var whipBase      = 61+12;
var whipIdleNotes = [0, 4, 7, 11, 14];
var whipGap       = 8;


var queue   = [];
var cur     = nil;
var gapLeft = 0;
var lastMidi = 95;

var midiLo = 86, midiHi = 100;

var motifTrillLo     = 0.003, motifTrillHi     = 0.007;
var motifTrillLongLo = 0.008, motifTrillLongHi = 0.014;
var motifTrateLo     = 2.75,  motifTrateHi     = 4.5;

var shrillTrillLo    = 0.035, shrillTrillHi    = 0.07;
var shrillTrateLo    = 7.5,  shrillTrateHi    = 15;

var freqComp, scaleNotes, unitBeats, shrillRun, buildPhrase, gapFor, step, sense;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.95;
m.accelMassFilteredDecay  = 0.1;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay  = 0.2;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay  = 0.7;

//------------------------------------------------------------
SynthDef(\birdyChirp, { |out=0, freq=2000, g0=0.9, g1=1.0, secs=0.15, amp=0.1, pan=0,
	trill=0.005, trate=1, bright=0.15, air=0.04, verb=0.22|
	var life  = secs + 0.6;
	var fenv  = EnvGen.kr(Env([g0, 1, 1, g1], [0.22, 0.38, 0.4] * secs, \exp));
	var aenv  = EnvGen.kr(Env([0, 1, 0.85, 0], [0.12, 0.55, 0.33] * secs, [-2, 0, -3]));
	var f     = freq * fenv * (1 + (trill * SinOsc.kr(trate, Rand(0, 2pi)))) * 0.5;
	var tone  = SinOsc.ar(f) + (SinOsc.ar(f * 2) * bright * 0.6) + (SinOsc.ar(f * 3) * bright * 0.2);
	var breath = BPF.ar(WhiteNoise.ar, f, 0.08) * air * 4;
	var sig   = (tone + breath) * aenv * amp;
	var st    = Pan2.ar(sig, pan);
	var wet   = FreeVerb2.ar(st[0], st[1], verb, 0.55, 0.6);
	Line.kr(0, 1, life, doneAction: Done.freeSelf);
	Out.ar(out, wet);
}).add;

SynthDef(\birdyWhip, {
    |out=0, pan=0, amp=0.3, gate=1, swoopDelay=0.03, gliss=0.01,pitchRand=1, freq = 440,
        reverbMix=0.3, reverbTime=2.0, reverbSize=0.8|

    var whipEnv, whistleEnv, whipOsc, whistleOsc, sig, mainEnv;
    var numCombs = 7;
    var numAllpass = 4;
    var room = reverbSize.clip(0.1, 0.9);

    mainEnv = EnvGen.kr(
        Env.asr(0.02, 1, 0.8),
        gate
    );
    whipEnv = EnvGen.ar(
        Env.perc(0.001, 0.03, curve: -8),
        gate
    );
    whistleEnv = EnvGen.ar(
        Env(
            [0, 0, 1, 0],
            [gliss, 0.15, 0.2],
            curve: [0, 2, -4]
        ),
        gate
    );
    whipOsc = WhiteNoise.ar *
        BPF.ar(
            PinkNoise.ar,
            freq: XLine.kr(12000, 2000, 0.03),
            rq: 0.05,
        ) +
        Resonz.ar(
            PinkNoise.ar,
            XLine.kr(9000, 1500, 0.03),
            0.1,
            0.2
        );

    whistleOsc = SinOsc.ar(
        freq: Env(
			[freq, freq * 2, freq * 1.9],
            [swoopDelay, 0.15, 0.15],
            [\sine, \sine, -3]
        ).kr
    ) * whistleEnv;

    sig = (whipOsc * whipEnv * 0.2) + (whistleOsc * 0.2);
	sig = Pan2.ar(sig * 0.5, pan);
    sig = FreeVerb2.ar(
		sig[0], sig[1],
        mix: reverbMix,
        room: room,
        damp: 0.3
    );
    sig = sig + DelayN.ar(
		sig,
        0.1,
        [0.033, 0.039, 0.045].collect({ |t|
            sig * LFNoise2.kr(0.1).range(0.01, 0.02) * DelayC.ar(sig, 0.1, t)
        }).sum
    );
	DetectSilence.ar(sig, time:0.3, doneAction:2);
    Out.ar(out, sig * amp * mainEnv);
}).add;

//------------------------------------------------------------
freqComp = { |freq| (2000 / freq).sqrt.clip(0.45, 1.3) };

scaleNotes = { |pcsIn|
	var lo = midiLo + tiltOct, hi = midiHi + tiltOct;
	var use = if (pcsIn.isNil or: { pcsIn.isEmpty }) { idlePcs } { pcsIn };
	var notes = [];
	(lo..hi).do { |n| if (use.includes(n % 12)) { notes = notes.add(n) } };
	if (notes.isEmpty) { notes = [lo + 9] };
	notes
};

unitBeats = {
	var tempo = clock !? { |c| c.tempo } ? 4;
	((0.12 * tempo) / 0.25).round.max(1) * 0.25
};

shrillRun = { |fromMidi, k|
	var u = unitBeats.();
	var sb = (u * 0.5).max(0.25) * rrand(0.5, 1.5);
	var base = (fromMidi + 12 + 5.rand).clip(100, 112);
	k.collect { |i|
		var up = i.even;
		var midi = (base + (if (up) { rrand(1, 4) } { rrand(-4, -1) })).clip(98, 114);
		(
			note:     midi,
			beats:    sb,
			g0:       if (up) { rrand(0.72, 0.85) } { rrand(1.18, 1.35) },
			g1:       if (up) { rrand(1.04, 1.15) } { rrand(0.86, 0.95) },
			trill:    rrand(shrillTrillLo, shrillTrillHi),
			trate:    rrand(shrillTrateLo, shrillTrateHi),
			bright:   0.25,
			air:      0.32,
			ampScale: 0.45 * (1 - (i * 0.04)),
			pan:      rrand(-0.5, 0.5)
		)
	}
};

buildPhrase = { |pcsIn, maxN, shrillOK|
	var notes = scaleNotes.(pcsIn);
	var u = unitBeats.();
	var n, idx, out = [];
	var e = energy.clip(restFloor, 1.5);
	n = (2 + (e.linlin(restFloor, 1.5, 0, 5)).round + 2.rand).clip(1, maxN).asInteger;
	idx = notes.indexOfGreaterThan(lastMidi - 1) ? (notes.size - 1);
	idx = (idx + [-1, 0, 0, 1].choose).clip(0, notes.size - 1);
	n.do { |i|
		var step = [-3, -2, -1, -1, 0, 1, 1, 2, 3].choose;
		var beats, long;
		if (i == 0) { step = [-2, 0, 1, 2].choose };
		idx = if (notes.size > 1) { (idx + step).fold(0, notes.size - 1) } { 0 };
		long = (i == (n - 1)) and: { 0.45.coin };
		beats = if (long) { u * [2, 3].choose } { u * [1, 1, 1, 1.5, 2].choose };
		out = out.add((
			note:     notes[idx],
			beats:    beats,
			g0:       [0.82, 0.88, 0.94, 1.0, 1.0, 1.08].choose,
			g1:       [1.0, 1.0, 1.0, 0.96, 1.04].choose,
			trill:    if (long) { rrand(motifTrillLongLo, motifTrillLongHi) }
			                    { rrand(motifTrillLo,     motifTrillHi)     },
			trate:    rrand(motifTrateLo, motifTrateHi),
			bright:   rrand(0.1, 0.2),
			air:      rrand(0.03, 0.06),
			ampScale: 1.0,
			pan:      rrand(-0.3, 0.3)
		));
		lastMidi = notes[idx];
	};
	if (shrillOK and: { (twist > 0.35) or: { 0.3.coin } }) {
		out = out ++ shrillRun.(lastMidi, rrand(4, 8));
	};
	out
};

gapFor = { |gMin, gMax|
	energy.clip(restFloor, 1.3).linlin(restFloor, 1.3, gMax, gMin).round.asInteger
};

step = { |e|
	var beats   = 0.5;
	var amp     = (e[\amp] ? 0);
	var gMin    = (e[\gapMin] ? 2);
	var gMax    = (e[\gapMax] ? 16);
	var maxN    = (e[\notes]  ? 8);
	var shrillOK = (e[\shrill] ? 1) > 0;
	cur = nil;
	if (muted.not and: { group.notNil } and: { amp > ampFloor }) {
		if (flickPending) {
			flickPending = false;
			if (shrillOK and: { energy > restFloor }) {
				queue = shrillRun.(lastMidi, rrand(3, 6)) ++ queue;
			};
		};
		if (queue.isEmpty) {
			if ((energy > restFloor) and: { urgent or: { gapLeft <= 0 } }) {
				queue = buildPhrase.(e[\pcs], maxN, shrillOK);
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
			if (queue.isEmpty) { gapLeft = gapFor.(gMin, gMax) };
		};
	} {
		queue = [];
	};
	beats
};

sense = { |d|
	prevEnergy = energy;
	energy = m.accelMassFiltered;
	twist  = m.rrateMassFiltered;
	if ((energy > (restFloor + 0.1)) and: { prevEnergy <= (restFloor + 0.1) }) { urgent = true };
	tiltOct = switch(tiltOct,
		0,   { if (m.gyroYFiltered > 0.4) { 12 } { if (m.gyroYFiltered < -0.4) { -12 } { 0 } } },
		12,  { if (m.gyroYFiltered < 0.25) { 0 } { 12 } },
		-12, { if (m.gyroYFiltered > -0.25) { 0 } { -12 } }
	);
	if ((twist > 0.8) and: { TempoClock.beats > (lastFlick + 1.2) }
		and: { energy > restFloor }) {
		lastFlick = TempoClock.beats;
		flickPending = true;
	};
};

//------------------------------------------------------------
~init = ~init <> {
	clock = TempoClock(2);
	group = Group.new;

	Event.addEventType(eventTypeName, { |e|
		var n = cur;
		if (n.notNil and: { group.notNil } and: { muted.not }) {
			var tempo = clock !? { |c| c.tempo } ? 4;
			var midi  = n[\note];
			var freq  = midi.midicps;
			var gain  = (~amp ? 0);
			~instrument = \birdyChirp;
			~out    = 0;
			~group  = group;
			~freq   = freq;
			~g0     = n[\g0];
			~g1     = n[\g1];
			~secs   = (n[\beats] / tempo).clip(0.035, 0.6);
			~amp    = gain * n[\ampScale] * freqComp.(freq);
			~pan    = n[\pan];
			~trill  = n[\trill];
			~trate  = n[\trate];
			~bright = n[\bright];
			~air    = n[\air];
			~sustain = ~secs;
			~type = \note;
			currentEnvironment.play;
		};
	});

	Pdef(m.ptn, Pbind(
		\type, eventTypeName,
		\dur,  Pfunc { |e| step.(e) }
	));
	Pdef(m.ptn).set(\amp, 0, \pcs, idlePcs, \notes, 8,
		\gapMin, 2, \gapMax, 16, \shrill, 1);
	Pdef(m.ptn).play(clock);

	Pdef(whipPtn,
		Pbind(
			\instrument, \birdyWhip,
			\group, group,
			\pan, Pwhite(-1.0, 1.0),
			\dur, Pfunc { whipGap },
			\sustain, 2,
			\reverbMix, 0.5,
			\reverbTime, 3.0,
			\reverbSize, 0.7
		);
	);
	Pdef(whipPtn).set(\amp, 0, \freq, whipBase.midicps);
	Pdef(whipPtn).play(clock);
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
	Pdef(whipPtn).remove;
	Event.eventTypes.removeAt(eventTypeName);
	c !? { c.stop };
	if (g.notNil) {
		fork {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
	};
};

//------------------------------------------------------------
~next = { |d|
	var chirpGain = -8;
	var whipGain  = -2;
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol  = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var wAmp = m.accelMassFiltered.lincurve(0, 0.6 * sens, -40, 0, -2);
	var wN   = m.gyroYFiltered.lincurve(-1.0, 1.0, 0, whipIdleNotes.size, -1)
		.asInteger.clip(0, whipIdleNotes.size - 1);
	var bAmp = m.accelMassFiltered.lincurve(restFloor, restFloor + (0.6 * sens), -30, 0, -2);

	sense.(d);

	if(wAmp < -38, { wAmp = -110 });
	if(m.accelMassFiltered <= restFloor, { bAmp = -110 });

	Pdef(m.ptn).set(\amp, (bAmp + chirpGain).dbamp * vol);
	Pdef(m.ptn).set(\pcs, idlePcs);
	Pdef(m.ptn).set(\notes, 8, \gapMin, 2, \gapMax, 16, \shrill, 0.2);

	whipGap = 8;
	Pdef(whipPtn).set(\amp, (wAmp + whipGain).dbamp * vol);
	Pdef(whipPtn).set(\freq, (whipBase + whipIdleNotes[wN]).midicps);
	Pdef(whipPtn).set(\swoopDelay, 0.0);
	Pdef(whipPtn).set(\gliss, 0.07);
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
