/*
gestures:    [shake]
description: A test bench for the chronicPiano library. Six samples, one per octave from C0 to C5, so nearest-sample lookup has to shift almost every note it plays — up to six semitones either way. The pattern is a plain C major scale climbing four octaves and starting again, and the picture is a deviation plot: pitch runs left to right, the shift applied to reach it runs up and down against a fixed zero line, and hue says which of the six samples is doing the work. Each note also posts its own lookup, so the mapping can be read as well as heard.
sound:       one piano sample per note, resampled to pitch; the further a note sits from its sample, the more it stretches
pitch:       C major, MIDI 24 to 71
rhythm:      one note every half second
instruments: [Template]
*/

var m = ~model;
var group;
var samplesLib;

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

var folder = PathName("~/Downloads/cotf_samples/chronicPiano");

var scale = [0,4,7,11];
var octaves = [2, 3, 4, 5, 6];

//------------------------------------------------------------
// note-name -> MIDI. The stems end in their note: "..._C4.aif".
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

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;

//------------------------------------------------------------
SynthDef(\pianoVoice, {|out=0, bufnum=0, amp=0.2, rate=1, start=0, pan=0,
    attack=0.002, sustain=0.3, release=0.2|
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum,
		rate: rate * BufRateScale.kr(bufnum),
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	Out.ar(out, Balance2.ar(sig[0], sig[1], pan, amp * env));
}).add;

//------------------------------------------------------------
// visual : a deviation plot. Pitch is x, the semitone shift the lookup had
// to apply is y against a fixed zero line, and hue is which sample was
// picked — so the six sample zones read as six coloured bands sloping
// through zero, and any mapping error shows up as a mark in the wrong band.
//
// Lineage: piano-roll and machine-legible plotting. Atlas grammars G3 / G10.
//
//   sounding pitch -> x        (set in the event type, where it is known)
//   shift          -> y
//   sample index   -> hue
//   amp            -> size
~init = ~init <> {|d|

	// Nearest sample, and the semitone shift needed to reach the target
	// from it. Octave-spaced library, so the shift runs to ±6.
	var findClosestSample = { |targetMidi|
		var idx = (0 .. samplesLib.size - 1).minItem({ |i|
			(samplesLib[i].midiNote - targetMidi).abs
		});
		var shift = targetMidi - samplesLib[idx].midiNote;
		(buffer: samplesLib[idx].buffer, rate: shift.midiratio,
			shift: shift, idx: idx, name: samplesLib[idx].name)
	};

	samplesLib = folder.entries.collect({ |path|
		var midiNote = noteToMidi.(path.fileNameWithoutExtension.split($_).last);
		var buffer = Buffer.read(s, path.fullPath, action: { |buf|
			postf("buffer alloc [%] \n", buf);
		});
		postf("loading piano : % (midi %) \n", path.fileNameWithoutExtension, midiNote);
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: midiNote)
	});

	// The lookup happens here, so this is where the numbers to draw and to
	// post exist. \sx and \sy are set from them and the rest of the mark is
	// on the Pbind below.
	Event.addEventType(eventTypeName, { |e|
		var target = ~note + ~root + (12 * ~octave);
		var found = findClosestSample.(target);
		~bufnum = found.buffer;
		~rate = found.rate;

		~sx = target.linlin(24, 71, -0.85, 0.85);
		~ex = ~sx;
		~sy = found.shift.linlin(-6, 6, 0.7, -0.7);
		~ey = ~sy;
		~startColor = Color.hsv(found.idx / samplesLib.size, 0.9, 1.0, 0.9);
		~endColor = Color.hsv(found.idx / samplesLib.size, 0.9, 0.5, 0.0);

		// postf("midi % -> % (shift %) \n", target, found.name, found.shift);

		~type = \customVisualEvent;
		currentEnvironment.play;
	});

	group = Group.new;

	// The zero line, held for the life of the personality, so the shift
	// axis has something to be read against.
	(
		type: \customVisualEvent,
		amp: 0, dur: 0.01,
		viewID: d.port,
		shape: \line,
		sx: 0, sy: 0, ex: 0, ey: 0,
		startSize: 900,
		startWidth: 1, endWidth: 1,
		startColor: Color.new(0.4, 0.4, 0.4, 0.5),
		endColor: Color.new(0.4, 0.4, 0.4, 0.5),
		duration: inf
	).play;

	Pdef(m.ptn,
		Pbind(
			\instrument, \pianoVoice,
			\group, group,
			\type, eventTypeName,

			\note, Pseq(scale, inf),
			\octave, Pseq(octaves.stutter(scale.size), inf),
			// \root, 0,
			// \dur, 0.2,
			\legato, 1.6,
			\release, 2.1,
			\pan, 0,

			\shape, \circle,
			\fill, true,
			\startSize, Pfunc({ |e| ((e[\amp] ? 0.2) * 90) + 14 }),
			\endSize, 6,
			\startWidth, 3,
			\endWidth, 0.4,
			\duration, 1.2,

			\args, #[],
		);
	);

	Pdef(m.ptn).play(quant: 1);
	Pdef(m.ptn).set(\amp, 0.2);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);

	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
		if (samplesLib.notNil) {
			samplesLib.do({ |sample|
				postf("buffer dealloc [%] \n", sample.buffer);
				sample.buffer.free;
				s.sync;
			});
			samplesLib = nil;
		};
	};
};

//------------------------------------------------------------
// Shake sets the level, with a floor — a test bench has to keep sounding
// when the stick is put down.
~next = {|d|
	var amp = m.accelMassFiltered.lincurve(0, 0.2, -34, -6, -1);
	var dur = m.accelMassFiltered.lincurve(0, 1.0, 0.4, 0.05, -1);

	if(amp < 29.neg, { amp = 120.neg});

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\root, m.com.root ? 0);

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMass, m.accelMassFiltered];
};
