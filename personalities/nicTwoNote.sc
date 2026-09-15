var m = ~model;

var samplesLib;

//------------------------------------------------------------
// The two recordings are each a RISING WHOLE TONE, not a single note:
//
//   nic_47_d#4f4.wav   D#4 -> F4    anchor 63
//   nic_46_f4g4.wav    F4  -> G4    anchor 65
//
// So the pitch a sample is asked for is the pitch its gesture STARTS on.
// The filename's last underscore field carries both notes; the first of
// them is the anchor.
//
// The two anchors are a whole tone apart, which leaves the odd semitone
// in the middle. Transposing either by a single semitone fills it - and
// because they sit either side of it, E can be reached by pitching f4g4
// DOWN or d#4f4 UP. pickSample alternates between those two answers, so
// the recordings interleave in both directions rather than one always
// winning.
//
// The cell is D# E F F# G, and inside a register no note is more than two
// semitones from a real recording:
//
//   D#  d#4f4  +0      E   alternates -1 / +1      F   f4g4  +0
//   F#  f4g4   +1      G   f4g4       +2
//
// Octaves ride the same rate, so -1 plays at 0.5 and +1 at 2.0 : a 2.1s
// gesture becomes 4.2s low and 1.0s high. That stretch is most of the
// register character and is deliberate.
//------------------------------------------------------------

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

// "nic_47_d#4f4" -> "D#4" : the first note token of the last field
var firstNoteOf = { |stem|
	var hits = stem.split($_).last.toUpper.findRegexp("[A-G]#?[0-9]");
	if(hits.size < 1, { Error("no note in name: %".format(stem)).throw });
	hits[0][1]
};

var folder = PathName("~/Downloads/nicSamples/twoNote");

var cellBase = 63;					
var cell = [2,0,-2,-12,-24] - 3 + 12;
var octaves = [0,-1];			


//------------------------------------------------------------
// harp1's filter tuning, unchanged
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\nicTwoNoteSampler, {|bufnum=0, out=0, amp=1, rate=1, start=0, pan=0, freq=440,
	attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate,doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	var penv = EnvGen.kr(Env.new([0, 2], [0.27], '\hold'), gate);
	var li = LocalIn.ar(2);
	var tone = LFTri.ar((freq.cpsmidi+penv).midicps + 0.5, 0, 0.1) + li;
	var fil = RLPF.ar(sig, freq, 0.1) + tone; 
	var verb = FreeVerb.ar(fil + sig, mix: 0.4, room: 0.9, damp: 0.2);
	LocalOut.ar(PitchShift.ar(verb,0.3,7.midiratio,0,0.3,1));

	Out.ar(out, verb * amp * env);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	// STRICT ALTERNATION. Going up from cellBase the recordings swap on
	// every semitone : 0 -> sample1, 1 -> sample2, 2 -> sample1 ...
	//
	// samplesLib is sorted by anchor, so sample1 is always d#4f4 (63) and
	// sample2 always f4g4 (65) - not whatever order the folder happened to
	// list. That matters : nearest-neighbour selection used to hand the
	// tie at E to whichever file sorted first, so renaming the files could
	// have flipped it.
	//
	// The cost of alternating rather than choosing the nearest is a bigger
	// stretch on some notes - up to +4 semitones on G, where nearest would
	// have used +2. That is the trade for hearing both recordings.
	var pickSample = { |n|
		var smp = samplesLib[(n - cellBase).mod(samplesLib.size)];
		var shift = n - smp.midiNote;
		(buffer: smp.buffer, rate: shift.midiratio, tag: smp.tag)
	};

	// the sounding midi note : \note is an offset in the cell, \root is the
	// cell base, \octave picks the register - same shape as harp1
	var midiOf = { |e| (e[\note] + e[\root] + (12 * (e[\octave] ? 0))).asInteger };

	samplesLib = folder.entries.collect({ |path|
		var stem = path.fileNameWithoutExtension;
		var buffer = Buffer.read(s, path.fullPath, action:{ |buf|
			postf("buffer alloc [%] \n", buf);
		});
		var anchor = noteToMidi.(firstNoteOf.(stem));
		postf("loading sample : %  (gesture starts on midi %)\n", stem, anchor);
		(name: stem, buffer: buffer, midiNote: anchor, tag: stem.split($_).last)
	});

	// low anchor first, so index 0 is always the lower recording
	samplesLib = samplesLib.sort({ |a, b| b.midiNote < a.midiNote });

	// A plain \note event. No custom event type, nothing registered on a
	// class-level dict, no Group, no out bus, no global environment - the
	// buffer and rate are resolved as ordinary Pbind keys.
	//
	// \octave and \range are deliberately NOT Pbind keys - a Pbind key of
	// the same name overrides whatever Pdef.set put in the envir, so ~next
	// could never move the register. \root carries the cell base rather
	// than adding to the Pslide, which is how harp1 does it.
	Pdef(m.ptn,
		Pbind(
			\instrument, \nicTwoNoteSampler,
			\note, Pslide(cell, inf, Pkey(\range), 0, 0),
			\root, cellBase,
			\bufnum, Pfunc({ |e| pickSample.(midiOf.(e))[\buffer] }),
			\rate, Pfunc({ |e| pickSample.(midiOf.(e))[\rate] }),
			// the pitch the gesture STARTS on, in Hz. Set explicitly rather
			// than left to the default note/root/octave chain, so it can
			// never drift from the midi note pickSample was handed.
			\freq, Pfunc({ |e| midiOf.(e).midicps }),
			// \start, 0.0,
			// \dur, 0.25,
			\pan, Pwhite(-0.5, 0.5),
			\attack, 0.02,
			\decay, 0.1,
			\sustain, 0.01,
			\release, 1.8,

			\type, \customVisualEvent,
			\shape, \circle,
			\startSize, 330,
			\endSize, 600,
			\startWidth, 3,
			\endWidth, 100,
			\duration, 1.2,
			\fill, false,
			\sx, 0,
			\sy, 0,
			\ex, 0,
			\ey, 0,
			// \startColor, Color.hsv(0.55, 0.85, 1.0, 0.8),
			// \endColor, Color.hsv(0.55, 0.85, 1.0, 0.0),

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
	Pdef(m.ptn).remove;

	// delay so in-flight synths finish before their buffers go
	fork {
		1.0.yield;
		if(samplesLib.notNil, {
			samplesLib.do({|sample|
				postf("buffer dealloc [%] \n", sample.buffer);
				sample.buffer.free;
				s.sync;
			});
			samplesLib = nil;
		});
	};
};

//------------------------------------------------------------
~onEvent = {|e| };

//------------------------------------------------------------
// harp1's mappings, unchanged apart from what they drive:
//   accel  -> how far the run slides, and amp
//   gyro x -> which of the three registers
~next = {|d|

	var move = m.accelMassFiltered.lincurve(0, 0.1, 1, cell.size, 1);
	var amp = m.accelMassFiltered.lincurve(0, 0.1, -60, -8, -1);
	var step = m.gyroXFiltered.linlin(-0.8, 0.8, 0, octaves.size - 0.001).floor;
	var start = m.accelMassFiltered.lincurve(0, 0.5, 0.0, 0.1,0);
	var dur = m.accelMassFiltered.lincurve(0, 1.5, 0.4, 0.1,0);

	if(amp < -58, { amp = -90; });

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\range, move.asInteger);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, octaves[step.asInteger]);
	Pdef(m.ptn).set(\start, start);
	
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
	[m.accelMassFiltered.linlin(0, 2, 0, 1), (d.sensors.gyroEvent.x / pi)];
};
