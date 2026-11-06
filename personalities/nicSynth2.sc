var m = ~model;

var samplesLib;
var group;
var fxBus;
var verbSynth;

var noteToMidi = { |noteName|
	var pattern = "([A-G](#|b)?)([0-9])";
	var noteNames = "C C# D D# E F F# G G# A A# B";
	var parts, note, octave, noteIndex;
	parts = noteName.toUpper.findRegexp(pattern);
	if(parts.size < 3, { Error("Invalid note format: %".format(noteName)).throw});
	note = parts[1][1];
	octave = parts[3][1].asInteger;
	note = note.replace("Cb", "B").replace("Db", "C#").replace("Eb", "D#")
	           .replace("Fb", "E").replace("Gb", "F#").replace("Ab", "G#").replace("Bb", "A#");
	noteIndex = noteNames.split($ ).find([note]);
	(octave + 1) * 12 + noteIndex;
};

var folder = PathName("~/Downloads/cotf_samples/chronicPiano");

var cellBase = 60 - 0;					
var noteSets = [
	[0,7,5,2],
	[2,5,7],

	[1,5,8,0],
	[0,5,-3],
	[2,3,5,7],
];


// var noteSets = [[0]];

var octaves = [-2,-1,0,1];			


//------------------------------------------------------------
// harp1's filter tuning, unchanged
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\nicSynth2Piano, {|out=0, bufnum=0, amp=0.2, rate=1, start=0, pan=0,
    attack=0.004, sustain=0.3, release=0.2, mr=1, moda=0.0, modf=0.1, suba=1.0|
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum,
		rate: rate * BufRateScale.kr(bufnum),
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	var sub = PlayBuf.ar(2, bufnum,
		rate: rate * BufRateScale.kr(bufnum) * mr * LFCub.ar(modf, 1, moda,1),
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = BRF.ar(sig, 160, 0.98);
	sig = sig + (sub * suba * 0.5).tanh;

	Out.ar(out, Balance2.ar(sig[0], sig[1], pan, amp * env));
}).add;

SynthDef(\nicSynth2Verb, {
    |in=0, out=0, mix=0.1, room=2.83, damp=0.3, amp=1, gate=1, release=0.5|
	var sig = In.ar(in, 2);
	var env = EnvGen.kr(Env.asr(0.01, 1, release), gate, doneAction: 2);
	sig = FreeVerb2.ar(sig[0], sig[1], mix, room.clip(0, 0.95), damp);
	sig = LeakDC.ar(sig);
	Out.ar(out, sig * env * amp);
	Out.ar(0, sig * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({ |sample| (sample.midiNote - targetMidi).abs });
		(buffer: closest.buffer, rate: (targetMidi - closest.midiNote).midiratio)
	};

	var midiOf = { |e| (e[\note] + e[\root] + (12 * (e[\octave] ? 0))).asInteger };

	group = Group.new;
	fxBus = Bus.audio(s, 2);
	verbSynth = Synth.tail(group, \nicSynth2Verb, [\in, fxBus, \out, 0, \mix, 0.2, \room, 2.83, \damp, 0.3, \amp, 1]);

	m.com.scaleIdx = 0;

	samplesLib = folder.entries.collect({ |path|
		var midiNote = noteToMidi.(path.fileNameWithoutExtension.split($_).last);
		var buffer = Buffer.read(s, path.fullPath, action: { |buf|
			postf("buffer alloc [%] \n", buf);
		});
		postf("loading piano : % (midi %) \n", path.fileNameWithoutExtension, midiNote);
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: midiNote)
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \nicSynth2Piano,
			\group, group,
			\out, fxBus,
			\note, Pn(Plazy({ |e| var cell = noteSets.wrapAt(m.com.scaleIdx ? 0); Pseq(Array.fill(e[\range] ? 1, { |i| cell.wrapAt(i) }), 1) })),
			\root, cellBase,
			\bufnum, Pfunc({ |e| findClosestSample.(midiOf.(e))[\buffer] }),
			\rate, Pfunc({ |e| findClosestSample.(midiOf.(e))[\rate] }),
			\freq, Pfunc({ |e| midiOf.(e).midicps }),
			// \start, 0.0,
			// \dur, 0.25,
			\pan, Pwhite(-0.5, 0.5),
			\attack, 0.02,
			\decay, 0.1,
			\sustain, 0.01,
			// \release, 1.8,

			\type, \customVisualEvent,
			\shape, \circle,
			\startSize, 0,
			\endSize, 0,
			\startWidth, 0,
			\endWidth, 0,
			\duration, 0.1,
			\fill, false,
			\sx, 0,
			\sy, 0,
			\ex, 0,
			\ey, 0,
			// \startColor, Color.hsv(0.55, 0.85, 1.0, 0.8),
			// \endColor, Color.hsv(0.55, 0.85, 1.0, 0.0),
			\startColor, Color.hsv(0.55, 0.85, 1.0, 0.8),
			\endColor, Color.hsv(0.55, 0.85, 1.0, 0.0),

			// \func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]
		);
	);

	// ~next has not run yet, so seed everything it supplies. An unset
	// \range hands Pslide a nil length, which THROWS on the first event -
	// and a throw inside a pattern stream kills the Pdef without ever
	// reaching the console. Silence, no error.
	Pdef(m.ptn).set(\range, 2);
	Pdef(m.ptn).set(\octave, 0);
	Pdef(m.ptn).set(\amp, 0.1);

	Pdef(m.ptn).play(quant: 0.1);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dyingLib = samplesLib;
	var dyingGroup = group;
	var dyingBus = fxBus;
	var dyingVerb = verbSynth;
	samplesLib = nil;
	group = nil;
	fxBus = nil;
	verbSynth = nil;
	Pdef(m.ptn).remove;

	fork {
		3.7.yield;
		dyingVerb !? { dyingVerb.set(\gate, 0) };
		0.6.yield;
		dyingGroup !? {
			s.bind { dyingGroup.freeAll };
			s.sync;
			dyingGroup.free;
		};
		dyingLib.do({|sample|
			postf("buffer dealloc [%] \n", sample.buffer);
			sample.buffer.free;
			s.sync;
		});
		dyingBus !? { dyingBus.free };
	};
};

//------------------------------------------------------------
~onEvent = {|e| };

//------------------------------------------------------------
// harp1's mappings, unchanged apart from what they drive:
//   accel  -> how far the run slides, and amp
//   gyro x -> which of the three registers
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var move = m.accelMassFiltered.lincurve(0, 1.5 * sens, 1, noteSets.wrapAt(m.com.scaleIdx ? 0).size, 1);
	var amp = m.accelMassFiltered.lincurve(0, 1.5 * sens, -60, -6, -1);
	var step = m.gyroXFiltered.fold(-0.5,0.5).linlin(-0.5, 0.5, 0, octaves.size-1).floor;
	var rel = m.accelMassFiltered.lincurve(0, 3 * sens, 3.1, 0.4, -2);

	var start = m.accelMassFiltered.lincurve(0, 2 * sens, 0.0, 0.1,0);
	var dur = m.accelMassFiltered.lincurve(0, 3 * sens, 0.6, 0.1,-2);

	if(amp < -58, { amp = -90; });

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\range, move.asInteger);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol);
	Pdef(m.ptn).set(\octave, octaves[step.asInteger]);
	Pdef(m.ptn).set(\start, start);
	Pdef(m.ptn).set(\release, rel);
	
	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\startColor, Color.hsv(0.55, 0.85, 1.0, amp.linlin(-60, 1, 0.0, 0.8)));
	Pdef(m.ptn).set(\endColor, Color.hsv(0.55, 0.85, 1.0, 0.0));	
	
	// if(m.accelMassFiltered > 0.05, {
	// 	if(Pdef(m.ptn).isPlaying.not, {
	// 		Pdef(m.ptn).resume(quant: 1);
	// 	});
	// },{
	// 	if(Pdef(m.ptn).isPlaying, {
	// 		Pdef(m.ptn).pause();
	// 	});
	// });
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMassFiltered.linlin(0, 2, 0, 1), m.gyroXFiltered.fold(-0.5,0.5).linlin(-0.5, 0.5, 0, 1)];
};
