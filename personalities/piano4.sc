
var m = ~model;
var group;
var fxBus;
var verbSynth;
var samplesLib;

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

var folder = PathName("~/Downloads/cotf_samples/chronicPiano");

// var scale = [0,9,4,11,19,14,28,24];
// var scale = [0,9,4,11,19-12,14-12,28-12,24-12];
// var scale = [0,9,4,11,7,2,16,12] - 3;


// list - played in order, each set exhausted `reps` times before the
// next one starts, then the whole cycle repeats forever.
// var scales = [
// 	[0,9,4,11,7,2,16,12],
// 	[0,7,4,11,7,2,16,12] + 5,
// 	[0,9,3,10,7,2,15,12] + 2,
// 	[0,7,4,11,7,2,16,12] - 2,
// 	[0,7,4,11,7,2,16,12] + 3,
// 	[0,7,4,11,7,2,16,12] + 2
// ];

// The set changes once a gesture has finished: energy above quietThresh, then below it for quietSecs.
var quietThresh = 0.02;
var quietSecs = 0.75;

var setIdx = 0;
var step = 0;
var quietFor = 0;
var armed = false;
var lastNow;





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
SynthDef(\piano4Voice, {|out=0, fx=0, send=0.3, spk=0, width=2, bufnum=0, amp=0.2, rate=1, start=0,
    attack=0.004, sustain=0.3, release=0.2, mr=1, moda=0.0, modf=0.1, suba=1.0|
	var numChans = s.options.numOutputBusChannels;
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum,
		rate: rate * BufRateScale.kr(bufnum),
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	var sub = PlayBuf.ar(2, bufnum,
		rate: rate * BufRateScale.kr(bufnum) * mr * LFCub.ar(modf, 1, moda,1),
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = BRF.ar(sig, 160, 0.98);
	sig = sig + (sub * suba * 0.5).tanh;
	sig = (sig[0] + sig[1]) * amp * env;
	Out.ar(out, PanAz.ar(numChans, sig, spk * 2 / numChans, 1, width, 0));
	Out.ar(fx, sig * send);
}).add;

SynthDef(\piano4Verb, {
    |in=0, out=0, mix=1, room=2.83, damp=0.3, amp=1, gate=1, release=0.5|
	var numChans = s.options.numOutputBusChannels;
	var sig = In.ar(in, 1);
	var env = EnvGen.kr(Env.asr(0.01, 1, release), gate, doneAction: 2);
	sig = Array.fill((numChans / 2).ceil, { |i| FreeVerb2.ar(sig, sig, mix, (room - (i * 0.08)).clip(0, 0.95), damp + (i * 0.15)) }).flop.flat.keep(numChans);
	sig = LeakDC.ar(sig);
	Out.ar(out, sig * env * amp);
	// Out.ar(2, sig * env * amp * 2);
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
	fxBus = Bus.audio(s, 1);
	verbSynth = Synth.tail(group, \piano4Verb, [\in, fxBus, \out, 0, \mix, 1, \room, 2.83, \damp, 0.3, \amp, 0.3]);

	
	// The zero line, held for the life of the personality, so the shift
	// axis has something to be read against.
	~vdef.(\zeroLine, { |ev, c|
		var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
		var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
		var amp = m.accelMassFiltered.lincurve(0, 0.2 * sens, -34, -6, -1);
		if((amp >= -29) and: { vol > 0.001 }, { c[\draw].(\line, ()) });
		nil
	});
	(
		type: \customVisualEvent,
		amp: 0, dur: 0.01,
		viewID: d.port,
		shape: \zeroLine,
		sx: 0, sy: 0, ex: 0, ey: 0,
		startSize: 900,
		startWidth: 1, endWidth: 1,
		startColor: Color.new(0.4, 0.4, 0.4, 0.5),
		endColor: Color.new(0.4, 0.4, 0.4, 0.5),
		duration: inf
	).play;

	Pdef(m.ptn,
		Pbind(
			\instrument, \piano4Voice,
			\group, group,
			\out, 0,
			\fx, fxBus,
			\type, eventTypeName,

			\scales, [
				[3,12,7,14,10,5,19,15] - 3,// A- (f#)
				[4,12,7,14,11,7,2,16,12] + 5,// F 
				[3,12,7,14,10,2,15,12] + 2,// D- (b)
				[4,12,7,14,11,7,2,16,12] - 2,//Bb (e)
				[4,12,7,14,11,7,2,16,0] + 3,//Eb (a)
				[4,0,7,14,11,7,2,16,12] + 2//D
			],
			\scaleIdx, Pfunc({ |e| setIdx % e[\scales].size }),
			// \scaleIdx, 5,
			\note, Pfunc({ |e| var n = e[\scales][e[\scaleIdx]].wrapAt(step); step = step + 1; n }),
			\spk, Pseq((0, 0.1 .. 3.9), inf),
			\send, 0.3,
			\func, Pfunc({ |e| ~onEvent.(e) }),
			\start, Pwhite(0.0, 0.04, inf),
			// \root, 0,
			// \dur, 0.2,
			\legato, 1.6,
			\release, 2.1,

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
	var g = group;
	var verb = verbSynth;
	var lib = samplesLib;
	var bus = fxBus;
	var voiceTail = 3.0;
	var verbTail = 0.6;
	group = nil;
	verbSynth = nil;
	samplesLib = nil;
	fxBus = nil;

	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);

	fork {
		voiceTail.wait;
		if (verb.notNil) { verb.set(\gate, 0) };
		verbTail.wait;
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
		if (lib.notNil) {
			lib.do({ |sample|
				postf("buffer dealloc [%] \n", sample.buffer);
				sample.buffer.free;
				s.sync;
			});
		};
		if (bus.notNil) { bus.free };
	};
};

//------------------------------------------------------------
~onEvent = {|e|
	m.com.scaleIdx = e[\scaleIdx];
};

//------------------------------------------------------------
// Shake sets the level, with a floor — a test bench has to keep sounding
// when the stick is put down.
~next = {|d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.accelMassFiltered.lincurve(0, 0.2 * sens, -34, -6, -1);
	var dur = m.accelMassFiltered.lincurve(0, 2.0 * sens, 0.5, 0.05, -3);
	var oct = ((d.sensors.gyroEvent.y / pi).fold(-0.5,0.5) * 2.0).linlin(-1, 1, 3, 7).round.asInteger;
	var mr = ((d.sensors.gyroEvent.y / pi).fold(-0.5,0.5) * 2.0).linlin(-1, 1, 2, -1).round.asInteger;
	var now = Main.elapsedTime;

	quietFor = if(m.accelMassFiltered > quietThresh, { armed = true; 0 }, { quietFor + (now - (lastNow ? now)) });
	lastNow = now;
	if(armed and: { quietFor >= quietSecs }, { setIdx = setIdx + 1; step = 0; armed = false });

	mr = 1.0 / 2.pow(mr);
	if(amp < 29.neg, { amp = 120.neg});

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\root, m.com.root ? 0);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\mr, mr);


};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[((d.sensors.gyroEvent.y / pi).fold(-0.5,0.5) * 2.0)];
};
