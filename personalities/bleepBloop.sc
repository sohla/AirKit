var m = ~model;
var group;

// var notes = [72, 74, 79, 81, 84, 86, 91, 93, 96] - 24;
var notes = [66,62,64,57,57,64,66,62] + 24;
var cols = 16;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay  = 0.3;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
SynthDef(\bleepBloop, { |out = 0, freq = 1000, amp = 0.2, sustain = 0.08, bend = 0, bendTime = 0.04, fmRatio = 2, fmIndex = 0, wave = 0, pw = 0.5, cutoff = 6000, pan = 0|
	var env = EnvGen.ar(Env.linen(0.002, sustain, 0.01), doneAction: Done.freeSelf);
	var f = freq * EnvGen.ar(Env([bend.midiratio, 1], [bendTime], \exp));
	var mod = SinOsc.ar(f * fmRatio) * f * fmIndex;
	var sig = SelectX.ar(wave.clip(0, 1), [SinOsc.ar(f + mod), Pulse.ar(f + mod, pw)]);
	sig = LPF.ar(sig, cutoff.clip(200, 18000));
	Out.ar(out, Pan2.ar(sig * env * amp, pan));
}).add;

//------------------------------------------------------------
~init = ~init <> { |d|
	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \bleepBloop,
			\group, group,
			\midinote, Pfunc({ notes.choose }),
			\sustain, Pwrand([0.04, 0.08, 0.2], [0.5, 0.35, 0.15], inf),
			\bend, Pwrand([0, 12, -12, -24], [0.55, 0.15, 0.2, 0.1], inf),
			\bendTime, Pwhite(0.02, 0.08),
			\fmRatio, Prand([1, 1.5, 2, 3.01], inf),
			\pan, Pwhite(-0.5, 0.5),
			\args, #[],

			\type, \customVisualEvent,
			\shape, \square,
			\fill, true,
			\col, Pseries(0, 1, inf) % cols,
			\sx, Pfunc({ |e| ((e[\col] + 0.5) / cols * 1.8) - 0.9 }),
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e| e[\midinote].linlin(notes.first, notes.last, -0.7, 0.7) }),
			\ey, Pfunc({ |e| e[\sy] - (e[\bend] / 48) }),
			\startSize, Pfunc({ |e| e[\sustain] * 250 }),
			\endSize, Pfunc({ |e| e[\startSize] * 0.6 }),
			\startWidth, 1,
			\endWidth, 1,
			\startColor, Pfunc({ |e| Color.new(0.3, 1.0, 0.45, 0.95).blend(Color.new(1.0, 0.7, 0.0, 0.95), (e[\wave] ? 0).clip(0, 1)) }),
			\endColor, Color.new(0.3, 1.0, 0.45, 0.0),
			\duration, Pfunc({ |e| e[\sustain] * 4 }),
			\modulation, (amp: 0),
		);
	);

	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\dur, 0.25);
	Pdef(m.ptn).play(quant: 0.1);
	m.com.bgColor = Color.black;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
	};
};

//------------------------------------------------------------
~next = { |d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var tilt = (d.sensors.gyroEvent.y / pi.half);
	var amp = m.accelMassFiltered.lincurve(0, 1.4 * sens, -50, -10, -1);
	var dur = m.accelMassFiltered.lincurve(0, 2.0 * sens, 0.3, 0.06, -2);
	var fmIndex = m.rrateMassFiltered.lincurve(0, 1.2, 0, 4, 2);
	var cutoff = tilt.linexp(-1.0, 1.0, 900, 12000);
	var wave = tilt.linlin(-0.5, 0.8, 0, 1);
	var pw = tilt.linlin(-1.0, 1.0, 0.5, 0.1);

	if(amp < 45.neg, { amp = 110.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol);
	Pdef(m.ptn).set(\dur, 0.2);
	Pdef(m.ptn).set(\fmIndex, fmIndex);
	Pdef(m.ptn).set(\cutoff, cutoff);
	Pdef(m.ptn).set(\wave, wave);
	Pdef(m.ptn).set(\pw, pw);
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
	// [m.accelMassFiltered.lincurve(0, 2.0, 0.3, 0.06, -2)];//dur
	// [m.rrateMassFiltered.lincurve(0, 1.2, 0, 4, 2) / 4];//fmIndex
	// [(d.sensors.gyroEvent.y / pi.half).linlin(-0.5, 0.8, 0, 1)];//wave

	[m.accelMass, m.accelMassFiltered];
};
