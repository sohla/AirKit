var m = ~model;
var group;
var samplesLib;

// The voices do not reach the hardware directly any more : they play into
// fxBus, and the reverb sitting at the TAIL of the group reads that bus
// and writes to 0. Order is the whole mechanism — a Pbind's synths are
// added with addToHead, so every voice lands in front of the tail synth
// however many of them are sounding.
var fxBus;
var verb;

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

var folder = PathName("~/Downloads/cotf_samples/Celtic Hammered Dulcimer");
var sampleFilter = "Dlcmr-hrd";

var beat = 0.5;
var divs = [2, 4, 6];
// var pool = [0, 11, 7, 4, 2, -5];
var pool = [0, 11, 7, 4, 2, -5];

//------------------------------------------------------------
var noteToMidi = { |noteName|
	var pattern = "([A-G](#|b)?)([0-9])";
	var noteNames = "C C# D D# E F F# G G# A A# B";
	var parts, note, octave, noteIndex;
	parts = noteName.findRegexp(pattern);
	if(parts.size < 3, { Error("Invalid note format: %".format(noteName)).throw });
	note = parts[1][1];
	octave = parts[3][1].asInteger;
	note = note.replace("Cb", "B").replace("Db", "C#").replace("Eb", "D#")
		.replace("Fb", "E").replace("Gb", "F#").replace("Ab", "G#").replace("Bb", "A#");
	noteIndex = noteNames.split($ ).find([note]);
	(octave + 1) * 12 + noteIndex;
};

var parseDulcimerNote = { |fileStem|
	var parts = fileStem.findRegexp("([A-G](#|b)?[0-9])[a-z]?$");
	if(parts.size < 2, { Error("Invalid dulcimer note format: %".format(fileStem)).throw });
	noteToMidi.(parts[1][1]);
};

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;

//------------------------------------------------------------
SynthDef(\dulcimerVoice, {|out=0, bufnum=0, amp=0.2, rate=1, start=0, pan=0,
    attack=0.002, sustain=0.2, release=1.2, freq=440|
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum,
		rate: rate * BufRateScale.kr(bufnum) * [1, 1.007],
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	var tone = LFTri.ar(freq * [1,1.02] * 1 * LFCub.ar(9 * amp,0, 0.1 * amp,2), 0, 0.02);
	var mix = (sig + tone) * amp * env;
	Out.ar(out, mix);
}).add;

// The tail of the group. FreeVerb2 carries its own dry signal, so mix is
// the wet/dry blend and nothing has to be routed around it. Gated, because
// a hard free on a reverb cuts the tail off mid-air.
SynthDef(\dulcimerVerb, {|in=0, out=0, mix=0.1, room=1.12, damp=0.2, amp=1,
    gate=1, release=1.4|
	var sig = In.ar(in, 2);
	var env = EnvGen.kr(Env.asr(0.01, 1, release), gate, doneAction: 2);
	sig = FreeVerb2.ar(sig[0], sig[1], mix, room, damp);
	sig = LeakDC.ar(sig);
	Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------
// (machine-legible), on the phosphor palette from §0.5.
~init = ~init <> {

		var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({ |sample| (sample.midiNote - targetMidi).abs });
		(buffer: closest.buffer, rate: (targetMidi - closest.midiNote).midiratio)
	};

	samplesLib = folder.entries
		.select({ |path| path.fileName.contains(sampleFilter) })
		.collect({ |path|
			var midiNote = parseDulcimerNote.(path.fileNameWithoutExtension);
			var buffer = Buffer.read(s, path.fullPath, action: { |buf|
				postf("buffer alloc [%] \n", buf);
			});
			(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: midiNote)
		});
	postf("loading dulcimer : % samples \n", samplesLib.size);

	Event.addEventType(eventTypeName, { |e|
		var target = ~note + ~root + (12 * ~octave);
		var found = findClosestSample.(target);
		~bufnum = found.buffer;
		~rate = found.rate;
		~type = \customVisualEvent;
		currentEnvironment.play;
	});

	group = Group.new;
	fxBus = Bus.audio(s, 2);
	verb = Synth.tail(group, \dulcimerVerb, [\in, fxBus, \out, 0]);

	Pdef(m.ptn,
		Pbind(
			\instrument, \dulcimerVoice,
			\group, group,
			\out, fxBus,
			\type, eventTypeName,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx)),
			\note, Pswitch(divs.collect({ |n| Pseq(pool.keep(n), 1) }), Pkey(\divIdx)),
			// \root, 0,//Pseq([0, 2,-2,0].stutter(24), inf),
			\octave, Pseq([2, 4, 3, 5].stutter(2), inf),
			\dur,  Pkey(\div).reciprocal * beat,
			\legato, 0.8,
			\attack, 0.01,
			\release, 2,
			\pan, Pwhite(-0.3, 0.3),

			\shape, \line,
			\rotation, pi.half,
			\sx, (Pkey(\step) / Pkey(\div) * 1.5) - 0.75,
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e| ((e[\note] ? 0) + (e[\root] ? 0)).linlin(-4, 14, 0.6, -0.6) }),
			\ey, Pkey(\sy).neg,
			\startSize, Pkey(\amp) * 490,
			\endSize, Pkey(\amp) * 90,
			\startColor, Color.new(1.0, 0.0, 0.0, 0.9),
			\endColor, Color.new(0.0, 1.0, 0.0, 0.0),
			\startWidth, Pfunc({ |e| ((e[\amp] ? 0.2) * 22) + 1 }),
			\endWidth, 0.4,
			\duration, Pkey(\dur) * 6,
			// \func, Pfunc({ |e| ~onEvent.(e) }),

			\args, #[],
		);
	);

	Pdef(m.ptn).play(quant: 1);
	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\amp, 0);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);
	if (verb.notNil) { verb.set(\gate, 0) };

	// Synths first, then buffers, then the bus. Freeing a buffer a PlayBuf
	// is still reading crashes the server; the wait lets the reverb tail
	// out rather than being cut off by the freeAll.
	fork {
		0.6.wait;
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
			verb = nil;
		};
		if (samplesLib.notNil) {
			samplesLib.do({ |sample|
				postf("buffer dealloc [%] \n", sample.buffer);
				sample.buffer.free;
				s.sync;
			});
			samplesLib = nil;
		};
		if (fxBus.notNil) {
			postf("bus dealloc [%] \n", fxBus);
			fxBus.free;
			fxBus = nil;
		};
	};
};

//------------------------------------------------------------
// ~onEvent = {|e|

// 	m.com.root = e.root;
// 	m.com.dur = e.dur;
// };

//------------------------------------------------------------
~next = {|d|
	var idx = m.accelMassFiltered.lincurve(0, 1.0, 0, divs.size - 1, 2).round.asInteger.clip(0, divs.size - 1);
	var amp = m.accelMassFiltered.lincurve(0, 1.0, -40, -6, -2);

	if(amp < 39.neg, { amp = 120.neg});

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\root, m.com.root ? 0);

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
	// [m.accelMassFiltered.lincurve(0, 2.5, 0, divs.size - 1, 2).round / (divs.size - 1)];//divIdx
	// [m.accelMassFiltered.lincurve(0, 2.5, -40, -5, -2).dbamp];//amp

	[m.accelMass, m.accelMassFiltered];
};
