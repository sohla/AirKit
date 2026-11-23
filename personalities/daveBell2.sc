var m = ~model;
var group;
var fxBus;
var verbSynth;
var buffer;
var harpLib;

var samplePath = "~/Downloads/yourDNASamples/ding A.wav";
var harpFolder = PathName("~/Downloads/openLabSamples/harp");
var baseNote = 68;
var partialRatios = [1, 3.01, 5.98];
var partialAmps = [1, 0.44, 0.12];

var melody = [ 0,4, 7, 11,12, ] + 4;
var beats  = [ 2, 1,  1,  2,  1, 1, Rest(2), 1,  3,  1,  2, 2 ] * 0.25;

var noteToMidi = { |noteName|
	var noteNames = "C C# D D# E F F# G G# A A# B";
	var parts = noteName.findRegexp("([A-G](#|b)?)([0-9])");
	var note, octave;
	if(parts.size < 3, { Error("Invalid note format: %".format(noteName)).throw });
	note = parts[1][1];
	octave = parts[3][1].asInteger;
	note = note.replace("Cb", "B").replace("Db", "C#").replace("Eb", "D#").replace("Fb", "E").replace("Gb", "F#").replace("Ab", "G#").replace("Bb", "A#");
	(octave + 1) * 12 + noteNames.split($ ).find([note]);
};

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.6;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay  = 0.3;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
SynthDef(\daveBell2, { |out = 0, bufnum = 0, freq = 207, basefreq = 207, amp = 0.2, pan = 0,
	attack = 0.003, decay = 0.4, susLevel = 0.6, release = 1.4, gate = 1,
	cutoff = 9000, rq = 0.8|
	var rate = (freq / basefreq) * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.adsr(attack, decay, susLevel, release), gate, doneAction: Done.freeSelf);
	var sig = PlayBuf.ar(2, bufnum, rate, loop: 0);
	sig = RLPF.ar(sig, cutoff, rq);
	sig = Balance2.ar(sig[0], sig[1], pan);
	Out.ar(out, sig * amp * env * 0.8);
}).add;

SynthDef(\daveBell2Harp, { |bufnum = 0, out = 0, amp = 1, rate = 1, start = 0, pan = 0, attack = 0.2, sustain = 0.3, release = 1.2|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.new([0, 0, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017] * 0.25, startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig);
}).add;

SynthDef(\daveBell2Verb, {
    |in=0, out=0, mix=0.25, room=2.83, damp=0.2, amp=1, gate=1, release=0.5|
	var sig = In.ar(in, 2);
	var env = EnvGen.kr(Env.asr(0.01, 1, release), gate, doneAction: 2);
	sig = FreeVerb2.ar(sig[0], sig[1], mix, room.clip(0, 0.95), damp);
	sig = LeakDC.ar(sig);
	Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> { |d|
	var path = PathName(samplePath);
	var closestHarp = { |midi| harpLib.minItem({ |smp| (smp.midiNote - midi).abs }) };

	group = Group.new;
	fxBus = Bus.audio(s, 2);
	verbSynth = Synth.tail(group, \daveBell2Verb, [\in, fxBus, \out, 0]);

	~vdef.(\dingBand, { |ev, c|
		var mod = ev[\modulation] ? ();
		var rest = mod[\rest] ? false;
		var semiPx = mod[\semiPx] ? 5;
		var ring = mod[\ring] ? 1.4;
		var strikeRing = mod[\strikeRing] ? 0.14;
		var n = (ev[\numPoints] ? 16).max(2);
		var onset = c[\pos];
		var reach = c[\size];
		var t = c[\elapsed];
		var head = onset.x + (reach * c[\normTime]);
		var crown = onset.x @ (onset.y - (12 * log2(partialRatios.last) * semiPx));

		if(rest.not, {
			c[\render].(
				Array.fill(n, { |j| onset.blend(crown, j / (n - 1)) }),
				0.6, exp(t.neg / strikeRing), false
			);

			partialRatios.do({ |ratio, i|
				var y = onset.y - (12 * log2(ratio) * semiPx);
				var a = partialAmps[i] * exp(t.neg / (ring / ratio));
				c[\render].(
					Array.fill(n, { |j| (onset.x @ y).blend(head @ y, j / (n - 1)) }),
					partialAmps[i], a, false
				);
			});
		});
		nil
	});

	harpLib = harpFolder.entries.select({ |p| p.extension == "wav" }).collect({ |p|
		(midiNote: noteToMidi.(p.fileNameWithoutExtension.split($_).last), buffer: Buffer.read(s, p.fullPath))
	});
	postf("loading harp : % samples \n", harpLib.size);

	postf("loading sample : % \n", path.fileName);


	buffer = Buffer.read(s, path.fullPath, action: { |buf|
		postf("buffer alloc [%] \n", buf);

		if (group.notNil) {
			var notes = Pbind(
				\col, Pseq((0..melody.size - 1), inf),
				\tone, Pseq(melody, inf),
				\beat, Pseq(beats, inf),
				\dur, Pkey(\beat) * Pkey(\pulse),
				\root, Pseq([0,3,-3].stutter(40), inf), //0 
				\midinote, Pkey(\tone) + baseNote + Pkey(\root) + Pfunc({ |e| (e[\oct] ? 0) * 12 }),
				\octave, 4,
				\legato, 0.9,
			);

			Pdef(m.ptn, Ppar([
				Pbind(
					\instrument, \daveBell2,
					\group, group,
					\out, fxBus,
					\bufnum, buf,
					\basefreq, baseNote.midicps,
					\release, 1.4,
					\rq, 0.8,
					\pan, Pfunc({ |e| (e[\col] / (melody.size - 1)).linlin(0, 1, -0.6, 0.6) }),
					\args, #[],

					\type, \customVisualEvent,
					\shape, \dingBand,
					\numPoints, 16,
					\fill, false,
					\closed, false,
					\sx, Pfunc({ |e| (e[\col] / (melody.size - 1)).linlin(0, 1, -0.9, 0.45) }),
					\ex, Pkey(\sx),
					\sy, Pfunc({ |e|
						(e[\midinote] + (e[\ctranspose] ? 0)).linlin(baseNote - 12, baseNote + 36, 0.8, -0.35)
					}),
					\ey, Pkey(\sy),
					\duration, Pfunc({ |e| ((e[\dur] ? 0.3).value * e[\legato]) + e[\release] }),
					\startSize, Pkey(\duration) * 60,
					\endSize, Pkey(\startSize),
					\startWidth, Pfunc({ |e| ((e[\amp] ? 0.1) * 11) + 1.2 }),
					\endWidth, 0.5,
					\startColor, Color.new(0.486, 1.0, 0.627, 0.9),
					\endColor, Color.new(0.486, 1.0, 0.627, 0.0),
					\rotation, 0,
					\modulation, Pfunc({ |e| (
						rest: e.isRest,
						amp: 0,
						semiPx: 5,
						ring: e[\release],
						strikeRing: 0.14,
					) }),
				) <> notes,
				Pbind(
					\instrument, \daveBell2Harp,
					\group, group,
					\out, fxBus,
					\harp, Pfunc({ |e| closestHarp.(e[\midinote]) }),
					\bufnum, Pfunc({ |e| e[\harp][\buffer] }),
					\rate, Pfunc({ |e| (e[\midinote] - e[\harp][\midiNote]).midiratio }),
					\amp, Pfunc({ |e| (e[\amp] ? 0) * 1.0 }),
					\attack, 0.005,
					\release, 1.2,
					\pan, Pfunc({ |e| (e[\col] / (melody.size - 1)).linlin(0, 1, -0.6, 0.6) }),
				) <> notes
			]));

			Pdef(m.ptn).set(\amp, 0);
			Pdef(m.ptn).set(\pulse, 0.22);
			// Pdef(m.ptn).set(\ctranspose, 4);
			Pdef(m.ptn).set(\cutoff, 9000);

			Pdef(m.ptn).play(quant: 0.1);
		};
	});
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var oldGroup = group;
	var oldVerb = verbSynth;
	var oldBus = fxBus;
	var oldBuffer = buffer;
	var oldHarp = harpLib;
	group = nil;
	harpLib = nil;
	verbSynth = nil;
	fxBus = nil;
	buffer = nil;
	Pdef(m.ptn).remove;
	fork {
		2.0.wait;
		oldVerb !? { oldVerb.set(\gate, 0) };
		0.7.wait;
		if (oldGroup.notNil) {
			s.bind { oldGroup.freeAll };
			s.sync;
			oldGroup.free;
		};
		oldBus !? { oldBus.free };
		if (oldBuffer.notNil) {
			postf("buffer dealloc [%] \n", oldBuffer);
			oldBuffer.free;
			s.sync;
		};
		oldHarp.do({ |smp| smp.buffer.free });
	};
};

//------------------------------------------------------------
~next = { |d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.accelMassFiltered.lincurve(0, 2.8 * sens, -50, -6, -1);
	var shift = (d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, -12, 12).round;
	var pulse = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 0.34, 0.10, 1);
	var cutoff = m.accelMassFiltered.lincurve(0, 2.8 * sens, 1200, 16000, 2);
	var oct = ((d.sensors.gyroEvent.y / pi).fold(-0.5, 0.5) * 2.0).linlin(-1, 1, -1, 2).round.asInteger;

	if(amp < 44.neg, { amp = 90.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol * 0.2);
	// Pdef(m.ptn).set(\ctranspose, shift);
	Pdef(m.ptn).set(\pulse, pulse);
	Pdef(m.ptn).set(\cutoff, cutoff);
	Pdef(m.ptn).set(\oct, oct);
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
	// [m.accelMassFiltered.lincurve(0, 1.4, -50, -6, -1).dbamp];//amp
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, -12, 12).round / 12];//shift
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 0.34, 0.10, 1) / 0.34];//pulse
	// [m.accelMassFiltered.lincurve(0, 1.4, 1200, 16000, 2) / 16000];//cutoff

	[m.accelMass, m.accelMassFiltered];
};
