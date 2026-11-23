var m = ~model;
var group;
var fxBus;
var plate;
var samplesLib;
var lanes;

var folder = PathName("~/Downloads/nicSamples/mwp");

var beat = 0.085;
var pool = [0];

var laneSplits = [55, 72];
var laneY = [0.5, 0, -0.5];
var lanePan = [0, -0.3, 0.3];

var grids = [
	[
		[1,0,0,0, 0,0,0,0, 1,0,0,0, 0,0,0,0],
		[1,0,0,0, 0,0,0,0, 1,0,0,0, 0,0,0,0],
		[1,0,0,0, 0,0,0,0, 1,0,0,0, 0,0,0,0],
		[1,0,0,0, 0,0,0,0, 1,0,0,0, 0,0,0,0],
	],
	[
		[0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0],
		[0,0,0,0, 1,0,0,0, 0,0,0,0, 1,0,0,0],
		[0,0,0,0, 1,0,0,0.5, 0,0,0,0, 1,0,0,0],
		[0,0,0.5,0, 1,0,0,0.5, 0,0.5,0,0, 1,0,0.5,0.5]
	],
	[
		[0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0],
		[0,0,0,0, 0,0,0,0, 0,0,1,0, 0,0,0,0],
		[0,0,1,0, 0,0,0,0, 0,0,1,0, 0,0,0,0],
		[0,0.5,1,0, 0,0,0,0, 0,0,1,0, 0,0,0,0],
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
SynthDef(\nicMouthPerc, { |out = 0, bufnum = 0, amp = 0.3, vel = 1, rate = 1, start = 0.03, pan = 0,
	attack = 0.002, decay=0.02, sustain = 0.1, release = 0.0003, gate=1, fx = 0, send = 0|
	// var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);////~~!!!!
  	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate * BufRateScale.kr(bufnum) * [1,1.0], startPos: start * BufFrames.kr(bufnum));
	sig = HPF.ar(sig, 30);

	// sig = RHPF.ar(sig, 6400.clip(20, 8000), 0.02, 1) + sig	;

	sig = sig * amp * vel * env;
	Out.ar(out, sig);
	Out.ar(fx, sig * send);
}).add;

SynthDef(\nicMouthPercPlate, { |in = 0, out = 0, predelay = 0.01, room = 0.2, damp = 0.3, lowcut = 30, hicut = 900, amp = 1, gate = 1|
	var env = EnvGen.kr(Env.asr(0.01, 1, 0.5), gate, doneAction: 2);
	var wet = DelayN.ar(In.ar(in, 2), 0.2, predelay);
	wet = 4.collect({ |i| AllpassN.ar(_, 0.05, [0.0047, 0.0053] * (i + 1) * 1.3, 0.08) }).inject(wet, { |sig, ap| ap.(sig) });
	wet = FreeVerb2.ar(wet[0], wet[1], 1, room, damp);
	wet = LPF.ar(HPF.ar(wet, lowcut), hicut);
	Out.ar(out, wet * env * amp);
}).add;

//------------------------------------------------------------
~vdef.(\splotch, { |ev, c|
	var mod = ev[\modulation] ? ();
	var lumps = mod[\lumps] ? [];
	var drops = mod[\drops] ? [];
	var bleed = mod[\bleed] ? 2;
	var bleedGrow = mod[\bleedGrow] ? 0.15;
	var bleedAlpha = mod[\bleedAlpha] ? 0.3;
	var n = ev[\numPoints] ? 48;
	var blob = { |pos, r| Array.fill(n, { |i| var a = i / n * 2pi; pos + Polar(r * (1 + lumps.sum({ |l| l[1] * sin((a * l[0]) + l[2]) })), a).asPoint }) };
	bleed.do({ |j| c[\render].(blob.(c[\pos], c[\size] * (1 + ((bleed - j) * bleedGrow))), 1, bleedAlpha / (bleed - j)) });
	c[\render].(blob.(c[\pos], c[\size]), 1, 1);
	drops.do({ |d| c[\render].(blob.(c[\pos] + Polar(c[\size] * d[1], d[0]).asPoint, c[\size] * d[2]), 1, 1) });
	nil
});

//------------------------------------------------------------
~init = ~init <> {
	var solo = nil;
	var looks = (
		'nic_07_g2':  (hue: 0.98, sat: 0.85, val: 0.70, alpha: 0.85, scale: 1.5, spread: 1.25, lump: 0.25, drops: 2, bleed: 3),
		'nic_09_g2':  (hue: 0.03, sat: 0.90, val: 0.80, alpha: 0.85, scale: 1.4, spread: 1.30, lump: 0.30, drops: 3, bleed: 3),
		'nic_11_b2':  (hue: 0.08, sat: 0.90, val: 0.90, alpha: 0.80, scale: 1.3, spread: 1.20, lump: 0.20, drops: 1, bleed: 2),
		'nic_10_c4':  (hue: 0.13, sat: 0.85, val: 0.90, alpha: 0.80, scale: 1.0, spread: 1.20, lump: 0.25, drops: 3, bleed: 2),
		'nic_12_c#4': (hue: 0.30, sat: 0.70, val: 0.70, alpha: 0.80, scale: 1.0, spread: 1.15, lump: 0.15, drops: 2, bleed: 2),
		'nic_13_e4':  (hue: 0.45, sat: 0.80, val: 0.70, alpha: 0.80, scale: 0.9, spread: 1.25, lump: 0.30, drops: 4, bleed: 2),
		'nic_14_f#4': (hue: 0.55, sat: 0.85, val: 0.85, alpha: 0.80, scale: 0.9, spread: 1.20, lump: 0.20, drops: 2, bleed: 2),
		'nic_15_c5':  (hue: 0.62, sat: 0.85, val: 0.85, alpha: 0.75, scale: 0.8, spread: 1.30, lump: 0.25, drops: 5, bleed: 1),
		'nic_17_d#5': (hue: 0.70, sat: 0.75, val: 0.80, alpha: 0.75, scale: 0.8, spread: 1.20, lump: 0.15, drops: 3, bleed: 1),
		'nic_08_f5':  (hue: 0.78, sat: 0.70, val: 0.85, alpha: 0.75, scale: 0.7, spread: 1.25, lump: 0.30, drops: 4, bleed: 1),
		'nic_06_f#5': (hue: 0.85, sat: 0.80, val: 0.90, alpha: 0.75, scale: 0.7, spread: 1.20, lump: 0.20, drops: 6, bleed: 1),
		'nic_05_d6':  (hue: 0.92, sat: 0.65, val: 0.95, alpha: 0.70, scale: 0.6, spread: 1.30, lump: 0.25, drops: 5, bleed: 1),
		'default':    (hue: 0.00, sat: 0.00, val: 0.30, alpha: 0.80, scale: 1.0, spread: 1.20, lump: 0.20, drops: 2, bleed: 2)
	);

	group = Group.new;
	fxBus = Bus.audio(s, 2);
	plate = Synth.tail(group, \nicMouthPercPlate, [\in, fxBus, \out, 0, \predelay, 0.004, \room, 0.9, \damp, 0.4]);

	samplesLib = folder.entries.select({ |p| p.extension == "wav" and: { solo.isNil or: { p.fileNameWithoutExtension.asSymbol == solo } } }).collect({ |path|
		var stem = path.fileNameWithoutExtension;
		var midiNote = noteToMidi.(stem.split($_).last.toUpper);
		var buffer = Buffer.read(s, path.fullPath, action: { |buf| postf("buffer alloc [%] \n", buf) });
		postf("loading mouth perc : % (midi %) \n", stem, midiNote);
		(name: stem, buffer: buffer, midiNote: midiNote, look: looks[stem.asSymbol] ?? { looks[\default] })
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
				\rate, 1,//.(e[\smp].midiNote) - e[\smp].midiNote).midiratio }),
				\pan, lanePan[lane],
				\fx, fxBus.index,
				\send, 0.3,
				\decay, 0.4,
				\sustain, 0.1,
				\release, 0.01,
				\legato, 0.1,

				\type, \customVisualEvent,
				\shape, \splotch,
				\fill, false,
				\look, Pfunc({ |e| e[\smp][\look] }),
				\sx, Pfunc({ |e| ((e[\step] + 0.5) / 16 * 1.8) - 0.9 + 0.03.rand2 }),
				\ex, Pkey(\sx),
				\sy, Pfunc({ laneY[lane] + 0.08.rand2 }),
				\ey, Pkey(\sy),
				\startSize, Pfunc({ |e| e[\vel].value * 40 * e[\look][\scale] }),
				\endSize, Pfunc({ |e| e[\startSize] * e[\look][\spread] }),
				\startWidth, 1,
				\endWidth, 1,
				// \startColor, Pfunc({ |e| var k = e[\look]; Color.hsv(k[\hue], k[\sat], k[\val], k[\alpha]) }),
				// \endColor, Pfunc({ |e| var k = e[\look]; Color.hsv(k[\hue], k[\sat], k[\val] * 0.7, 0.0) }),
				\startColor, Color.white,
				\endColor, Color.black.alpha_(0),
				\modulation, Pfunc({ |e| var k = e[\look]; (amp: 0, bleed: k[\bleed], lumps: Array.fill(3, { |i| [i + 2, rrand(0.0, k[\lump]), 2pi.rand] }), drops: Array.fill(rrand(0, k[\drops]), { [2pi.rand, rrand(1.2, 2.2), rrand(0.08, 0.25)] })) }),
				\duration, beat * 2,

				\args, #[],
			)
		}).select({ |ptn, lane| lanes[lane].notEmpty }))
	);

	Pdef(m.ptn).set(\level, 0);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).play(quant: beat);
	m.com.bgColor = Color.black;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var verb = plate;
	var bus = fxBus;
	var g = group;
	var tail = 2.0;
	group = nil;
	plate = nil;
	fxBus = nil;
	Pdef(m.ptn).remove;
	fork {
		verb !? { verb.set(\gate, 0) };
		tail.wait;
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
		bus !? { bus.free };
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
	var amp = m.accelMassFiltered.lincurve(0, 1.0 * sens, -35, -8, -1);

	if(amp < 33.neg, { amp = 110.neg });

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
