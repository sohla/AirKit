var m = ~model;
var group;
var fxBus;
var verb;
var pads;

var notes = [66,62,64,57,57,64,66,62] + 21 - 12;
var cols = 16;
var divs = [1, 2, 4, 8 ];
var bar = 0.8;
// var chords = [[0, 7, 14, 16], [-3, 4, 7, 12], [-7, 0, 4, 9], [-5, 2, 7, 12]] + 47 + 24;
// var chords = [[0, 7, 14, 16]] + 47 + 24;
var chords = [[0, 4, 7]] + 47 + 12;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay  = 0.3;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
SynthDef(\bleepBloop3, { |out = 0, freq = 1000, amp = 0.2, sustain = 0.08, bend = 0, bendTime = 0.04, fmRatio = 2, fmIndex = 0, wave = 0, pw = 0.5, cutoff = 6000, pan = 0, fx = 0, send = 0|
	var env = EnvGen.ar(Env.linen(0.002, sustain, 0.01), doneAction: Done.freeSelf);
	var f = freq * EnvGen.ar(Env([bend.midiratio, 1], [bendTime], \exp));
	var mod = SinOsc.ar(f * fmRatio) * f * fmIndex;
	var sig = SelectX.ar(wave.clip(0, 1), [SinOsc.ar(f + mod), Pulse.ar(f + mod, pw)]);
	sig = LPF.ar(sig, cutoff.clip(200, 18000));
	sig = Pan2.ar(sig * env * amp, pan);
	Out.ar(out, sig);
	Out.ar(fx, sig * send);
}).add;

SynthDef(\bleepBloop3Chord, { |out = 0, freq = 220, amp = 0.1, gate = 1, attack = 0.4, release = 1.5, cutoff = 2500, 
	lowcut = 150, detune = 0.003, breath = 0.5, breathRq = 0.06, flutterRate = 0.4, phaseRate = 0.15, 
	phaseDepth = 0.5, pan = 0, fx = 0, send = 0, glide = 0.3, ampLag = 0.9|
	var env = EnvGen.kr(Env.asr(attack, 1, release), gate, doneAction: Done.freeSelf);
	var f = freq.lag(glide);
	var flutter = LFNoise2.kr(flutterRate).range(0.5, 1);
	var tone = Mix(LFTri.ar(f * [1 - detune, 1, 1 + detune])) * 0.25 + (Pulse.ar(f, 0.5) * 0.05);
	var air = Mix(BPF.ar(PinkNoise.ar, f * [1, 3], breathRq) * [1, 0.5]) * breath * 22 * flutter;
	var sweep = SinOsc.kr(phaseRate, Rand(0, 2pi)).exprange(400, 2500);
	var sig = HPF.ar(LPF.ar(tone + air, cutoff.clip(200, 18000)), lowcut);
	var phased = sig;
	3.do { phased = BAllPass.ar(phased, sweep, 1) };
	sig = (sig + (phased * phaseDepth)) * 0.7;
	sig = Pan2.ar(sig * env * amp.lagud(ampLag,ampLag*2), pan);
	Out.ar(out, sig);
	Out.ar(fx, sig * send);
}).add;

SynthDef(\bleepBloop3Verb, { |in = 0, out = 0, mix = 1, room = 0.8, damp = 0.4, lowcut = 200, hicut = 3000, amp = 1, gate = 1|
	var env = EnvGen.kr(Env.asr(0.01, 1, 0.5), gate, doneAction: 2);
	var wet = In.ar(in, 2);
	wet = FreeVerb2.ar(wet[0], wet[1], mix, room, damp);
	wet = LPF.ar(HPF.ar(wet, lowcut), hicut);
	Out.ar(out, wet * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> { |d|
	group = Group.new;
	fxBus = Bus.audio(s, 2);
	verb = Synth.tail(group, \bleepBloop3Verb, [\in, fxBus, \out, 0, \room, 0.9, \damp, 0.8, \mix, 0.5]);
	pads = chords[0].collect({ |n, i| Synth(\bleepBloop3Chord, [\freq, (n + (m.com.root ? 0)).midicps, \amp, 0, \pan, [-0.4, -0.1, 0.1, 0.4].wrapAt(i), \attack, 0.4, \release, 1.5, \cutoff, 2500, \phaseRate, 0.15, \phaseDepth, 0.5, \fx, fxBus.index, \send, 0.8], group) });

	Pdef(m.ptn, Ppar([
		Pbind(
			\instrument, \bleepBloop3,
			\group, group,
			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }), Pkey(\divIdx)),
			\dur,  Pkey(\div).reciprocal * bar,
			\midinote, Pfunc({ |e| notes.choose + (e[\root] ? 0) }),
			\sustain, Pwrand([0.04, 0.08, 0.2], [0.5, 0.35, 0.15], inf),
			\bend, Pwrand([0, 2, -2, -1], [0.55, 0.15, 0.2, 0.1], inf),
			\bendTime, Pwhite(0.02, 0.08),
			\fmRatio, Prand([1, 1.5, 2, 3.01], inf),
			\pan, Pwhite(-0.5, 0.5),
			\fx, fxBus.index,
			\send, 0.4,
			\args, #[],

			\type, \customVisualEvent,
			\shape, \square,
			\fill, true,
			\col, Pseries(0, 1, inf) % cols,
			\sx, Pfunc({ |e| ((e[\col] + 0.5) / cols * 1.8) - 0.9 }),
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e| (e[\midinote] - (e[\root] ? 0)).linlin(notes.first, notes.last, -0.7, 0.7) }),
			\ey, Pfunc({ |e| e[\sy] - (e[\bend] / 48) }),
			\startSize, Pfunc({ |e| e[\sustain] * 250 }),
			\endSize, Pfunc({ |e| e[\startSize] * 0.6 }),
			\startWidth, 1,
			\endWidth, 1,
			\startColor, Pfunc({ |e| Color.new(0.3, 1.0, 0.45, 0.95).blend(Color.new(1.0, 0.7, 0.0, 0.95), (e[\wave] ? 0).clip(0, 1)) }),
			\endColor, Color.new(0.3, 1.0, 0.45, 0.0),
			\duration, Pfunc({ |e| e[\sustain] * 4 }),
			\modulation, (amp: 0),
		),
		Pbind(
			\type, \set,
			\id, Pfunc({ pads.collect(_.nodeID) }),
			\args, #[\freq],
			\dur, bar * 1,
			\chord, Pseq(chords, inf),
			\freq, Pfunc({ |e| (e[\chord] + (e[\root] ? 0)).midicps }),
		)
	]));

	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).play(quant: bar);
	m.com.bgColor = Color.black;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var rv = verb;
	var bus = fxBus;
	var g = group;
	var tail = 3.0;
	var dyingPads = pads;
	pads = nil;
	group = nil;
	verb = nil;
	fxBus = nil;
	Pdef(m.ptn).remove;
	fork {
		rv !? { rv.set(\gate, 0) };
		dyingPads.do({ |x| x.set(\gate, 0) });
		tail.wait;
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
		bus !? { bus.free };
	};
};

//------------------------------------------------------------
~next = { |d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var tilt = (d.sensors.gyroEvent.y / pi.half);
	var amp = m.accelMassFiltered.lincurve(0, 1.4 * sens, -30, -10, -1);
	var idx = m.accelMassFiltered.lincurve(0, 2.5 * sens, 0, divs.size - 1, 3).round.asInteger.clip(0, divs.size - 1);
	var fmIndex = m.rrateMassFiltered.lincurve(0, 1.2, 0, 1, 2);
	var cutoff = tilt.linexp(-1.0, 1.0, 900, 12000);
	var wave = tilt.linlin(-0.5, 0.8, 0, 0.1);
	var pw = tilt.linlin(-1.0, 1.0, 0.1, 0.1);
	var padAmp = m.accelMassFiltered.lincurve(0, 2.0 * sens, -40, -10, -1);

	if(amp < 25.neg, { amp = 110.neg });
	if(padAmp < 35.neg, { padAmp = 110.neg });


	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\root, m.com.root ? 0);
	Pdef(m.ptn).set(\fmIndex, fmIndex);
	Pdef(m.ptn).set(\cutoff, cutoff);
	Pdef(m.ptn).set(\wave, wave);
	Pdef(m.ptn).set(\pw, pw);
	pads.do({ |x| x.set(\amp, padAmp.dbamp * vol) });
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
	// [m.accelMassFiltered.lincurve(0, 1.4, -50, -10, -1).dbamp];//amp
	// [m.accelMassFiltered.lincurve(0, 1.5, 0, divs.size - 1, 1).round / (divs.size - 1)];//divIdx
	// [m.rrateMassFiltered.lincurve(0, 1.2, 0, 4, 2) / 4];//fmIndex
	// [(d.sensors.gyroEvent.y / pi.half).linlin(-0.5, 0.8, 0, 1)];//wave

	[m.accelMass, m.accelMassFiltered];
};
