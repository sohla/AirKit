var m = ~model;
var group;
var samplesLib;
var lanes;

var folder = PathName("~/Downloads/nicSamples/mwp");

var beat = 0.125;
var pool = [0, 3, 5, 7, 10];

var laneSplits = [55, 72];
var laneY = [0.5, 0, -0.5];
var lanePan = [0, -0.3, 0.3];
var laneHue = [0.02, 0.12, 0.5];

var grids = [
	[
		[1,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0],
		[1,0,0,0, 0,0,0,0, 1,0,0,0, 0,0,0,0],
		[1,0,0,0.5, 0,0,1,0, 1,0,0,0, 0,0,0.5,0],
		[1,0,0.5,1, 0,0,1,0, 1,0,0.5,0, 0,1,1,0]
	],
	[
		[0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0],
		[0,0,0,0, 1,0,0,0, 0,0,0,0, 1,0,0,0],
		[0,0,0,0, 1,0,0,0.5, 0,0,0,0, 1,0,0,0],
		[0,0,0.5,0, 1,0,0,0.5, 0,0.5,0,0, 1,0,0.5,0.5]
	],
	[
		[0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0],
		[0,0,1,0, 0,0,1,0, 0,0,1,0, 0,0,1,0],
		[1,0,1,0, 1,0,1,0, 1,0,1,0, 1,0,1,0.5],
		[1,0.5,1,0.5, 1,0.5,1,0.5, 1,0.5,1,0.5, 1,0.5,1,1]
	]
];

var noteToMidi = { |noteName|
	var noteNames = "C C# D D# E F F# G G# A A# B";
	var parts = noteName.findRegexp("([A-G](#|b)?)([0-9])");
	var note, octave;
	if(parts.size < 3, { Error("Invalid note format: %".format(noteName)).throw });
	note = parts[1][1];
	octave = parts[3][1].asInteger;
	note = note.replace("Cb", "B").replace("Db", "C#").replace("Eb", "D#")
		.replace("Fb", "E").replace("Gb", "F#").replace("Ab", "G#").replace("Bb", "A#");
	(octave + 1) * 12 + noteNames.split($ ).find([note]);
};

var snapToPool = { |midi|
	pool.collect({ |p| p + (12 * ((midi - p) / 12).round) }).minItem({ |t| (t - midi).abs })
};

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\nicMouthPerc, { |out = 0, bufnum = 0, amp = 0.3, vel = 1, rate = 1, start = 0, pan = 0,
	attack = 0.002, sustain = 0.1, release = 0.0003|
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);////~~!!!!
	var sig = PlayBuf.ar(2, bufnum, rate * BufRateScale.kr(bufnum), startPos: start * BufFrames.kr(bufnum));
	Out.ar(out, Balance2.ar(sig[0], sig[1], pan, amp * vel * env));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;

	samplesLib = folder.entries.select({ |p| p.extension == "wav" }).collect({ |path|
		var stem = path.fileNameWithoutExtension;
		var midiNote = noteToMidi.(stem.split($_).last.toUpper);
		var buffer = Buffer.read(s, path.fullPath, action: { |buf| postf("buffer alloc [%] \n", buf) });
		postf("loading mouth perc : % (midi %) \n", stem, midiNote);
		(name: stem, buffer: buffer, midiNote: midiNote)
	});

	lanes = [
		samplesLib.select({ |smp| smp.midiNote < laneSplits[0] }),
		samplesLib.select({ |smp| smp.midiNote >= laneSplits[0] and: { smp.midiNote < laneSplits[1] } }),
		samplesLib.select({ |smp| smp.midiNote >= laneSplits[1] })
	];

	Pdef(m.ptn,
		Ppar(lanes.collect({ |laneSamples, lane|
			Pbind(
				\instrument, \nicMouthPerc,
				\group, group,
				\dur, beat,

				\step, Pseries(0, 1, inf) % 16,
				\vel, Pfunc({ |e| var v = grids[lane][e[\level] ? 0][e[\step]]; if(v > 0, { v }, { Rest(0) }) }),
				\smp, Pfunc({ laneSamples.choose }),
				\bufnum, Pfunc({ |e| e[\smp].buffer }),
				\rate, Pfunc({ |e| (snapToPool.(e[\smp].midiNote) - e[\smp].midiNote).midiratio }),
				\pan, lanePan[lane],
				\sustain, 2,

				\type, \customVisualEvent,
				\shape, \circle,
				\fill, true,
				\sx, Pfunc({ |e| ((e[\step] + 0.5) / 16 * 1.8) - 0.9 }),
				\ex, Pkey(\sx),
				\sy, laneY[lane],
				\ey, laneY[lane],
				\startSize, Pfunc({ |e| e[\vel].value * 40 }),
				\endSize, 3,
				\startColor, Color.hsv(laneHue[lane], 0.8, 1.0, 0.9),
				\endColor, Color.hsv(laneHue[lane], 0.8, 0.4, 0.0),
				\duration, beat * 6,

				\args, #[],
			)
		}))
	);

	Pdef(m.ptn).set(\level, 0);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).play(quant: 1);
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
		if (samplesLib.notNil) {
			samplesLib.do({ |smp|
				postf("buffer dealloc [%] \n", smp.buffer);
				smp.buffer.free;
				s.sync;
			});
			samplesLib = nil;
		};
	};
};

//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var level = m.accelMassFiltered.lincurve(0, 2.5 * sens, 0, 3, 7).round.asInteger.clip(0, 3);
	var amp = m.accelMassFiltered.lincurve(0, 2.0 * sens, -25, -6, -1);

	if(amp < 23.neg, { amp = 110.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\level, level);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;

	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	[m.accelMass, m.accelMassFiltered];

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right

	// COMPUTED values : this file's own ~next, recomputed
	// [m.accelMassFiltered.lincurve(0, 1.25, 0, 3, 7).round / 3];//level
};
