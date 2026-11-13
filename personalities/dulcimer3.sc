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
var pianoPtn = (m.ptn ++ "_piano").asSymbol;
var pianoEventTypeName = (\customEvent_ ++ m.ptn ++ "_piano").asSymbol;

var folder = PathName("~/Downloads/cotf_samples/Celtic Hammered Dulcimer");
var sampleFilter = "Dlcmr-hrd";

var beat = 1;
var divs = [2, 4, 8];

var pools = [
	[0] - 3,
	[-5] + 5,
	[0] + 2,
	[0] - 2,
	[0-5] + 3,
	[0] + 2
] ;


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

//------------------------------------------------------------
// harp1's filter tuning, unchanged
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\dulcimer3Voice, {|out=0, bufnum=0, amp=0.2, rate=1, fromRate=1, start=0, pan=0,
    attack=0.002, sustain=0.2, release=1.2, freq=440, fromFreq=440, slide=0.1, subamp = 0.2|
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var glideRate = XLine.kr(fromRate, rate, slide);
	var glideFreq = XLine.kr(fromFreq, freq, slide);
	var sig = PlayBuf.ar(2, bufnum,
		rate: glideRate * BufRateScale.kr(bufnum) * [1, 1.007],
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	var tone = SinOsc.ar(glideFreq * [1,1.006] * 0.5 * LFCub.ar(1 * amp * 0.5,0, 0.1 * amp, 2), 0, subamp);
	// var ex = HPF.ar(BrownNoise.ar(0.03), 5250);
	// var klan = Klank.ar(`[[32, 16, 8] * freq, [0.04, 0.2, 0.3, 0.4], 0.3], ex);
	var mix = (sig + tone) * amp * env * 0.75;
	Out.ar(out, mix);
}).add;

// The tail of the group. FreeVerb2 carries its own dry signal, so mix is
// the wet/dry blend and nothing has to be routed around it. Gated, because
// a hard free on a reverb cuts the tail off mid-air.
SynthDef(\dulcimer3Verb, {|in=0, out=0, mix=0.2, room=1.12, damp=0.2, amp=1,
    gate=1, release=1.4|
	var sig = In.ar(in, 2);
	var env = EnvGen.kr(Env.asr(0.01, 1, release), gate, doneAction: 2);
	sig = FreeVerb2.ar(sig[0], sig[1], mix, room, damp);
	sig = LeakDC.ar(sig);
	// Out.ar(out, sig * env * amp);
	Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------
// (machine-legible), on the phosphor palette from §0.5.
~init = ~init <> {

		var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({ |sample| (sample.midiNote - targetMidi).abs });
		(buffer: closest.buffer, rate: (targetMidi - closest.midiNote).midiratio, midiNote: closest.midiNote, idx: samplesLib.indexOf(closest))
	};
	var lastTarget, pianoLastTarget;

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
		var from = lastTarget ? target;
		~bufnum = found.buffer;
		~rate = found.rate;
		~fromRate = (from - found.midiNote).midiratio;
		~fromFreq = from.midicps;
		lastTarget = target;
		~type = \customVisualEvent;
		currentEnvironment.play;
	});

	Event.addEventType(pianoEventTypeName, { |e|
		var target = ~note + ~root + (12 * ~octave);
		var found = findClosestSample.(target);
		var from = pianoLastTarget ? target;
		var shift = target - found.midiNote;
		~bufnum = found.buffer;
		~rate = found.rate;
		~fromRate = (from - found.midiNote).midiratio;
		~fromFreq = from.midicps;
		pianoLastTarget = target;

		~sx = target.linlin(24, 71, -0.85, 0.85);
		~ex = ~sx;
		~sy = shift.linlin(-6, 6, 0.7, -0.7);
		~ey = ~sy;
		~startColor = Color.hsv(found.idx / samplesLib.size, 0.9, 1.0, 0.9);
		~endColor = ~startColor.copy.alpha_(0.0);

		~type = \customVisualEvent;
		currentEnvironment.play;
	});

	group = Group.new;
	fxBus = Bus.audio(s, 2);
	verb = Synth.tail(group, \dulcimer3Verb, [\in, fxBus, \out, 0]);

	Pdef(m.ptn,
		Pbind(
			\instrument, \dulcimer3Voice,
			\group, group,
			\out, fxBus,
			\type, eventTypeName,
			\slide, 0.0,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx)),
			\note, Pswitch(divs.collect({ |n| Plazy({ Pseq(pools.wrapAt(m.com.scaleIdx ? 0).keep(n), 1) }) }), Pkey(\divIdx)),
			// \root, 0,//Pseq([0, 2,-2,0].stutter(24), inf),
			\octave, Pseq([4,3,2].stutter(2), inf),
			\dur,  Pkey(\div).reciprocal * beat,
			\legato, 0.8,
			\attack, 0.4,
			\release, 2,
			\pan, Pwhite(-0.3, 0.3),

			\shape, \line,
			\rotation, pi.half,
			\sx, (Pkey(\step) / Pkey(\div) * 0.1) ,
			\ex, Pkey(\sx),
			\ey, 0,//Pfunc({ |e| ((e[\note] ? 0) + (e[\root] ? 0)).linlin(-4, 14, 0.6, -0.6) }),
			\sy, 0,
			\startSize, Pkey(\amp) * 990,
			\endSize, 300,//Pkey(\amp) * 90,
			\startColor, Color.new(1.0, 0.0, 0.0, 0.9),
			\endColor, Color.new(0.0, 1.0, 0.0, 0.0),
			\startWidth, Pfunc({ |e| ((e[\amp] ? 40.2) * 40) + 31 }),
			\endWidth, 10.4,
			\duration, Pkey(\dur) * 6,
			// \func, Pfunc({ |e| ~onEvent.(e) }),
			\modulation, (
				type: \normal,
				freq: 8,
				amp: 2,
				harmonics: 4
			),
			\args, #[],
		);
	);

	Pdef(pianoPtn,
		Pbind(
			\instrument, \dulcimer3Voice,
			\group, group,
			\out, fxBus,
			\type, pianoEventTypeName,
			\slide, 3,
			\scales, [
				[0,-2,-3,-5,-7] - 3,
				[-5,-6,-8,-10] + 5,
				[0,-2,-3,-5] + 2,
				[0,-1,-3,-5] - 2,
				[2,0,-3,-5] - 2,
				[0,-1,-3,-5] + 2
			],

			// \scales, [
			// 	[7,9,14,15] - 3,
			// 	[2,5,7,11] + 5,
			// 	[5,7,10,14] + 2,
			// 	[4,6,7,9] - 2,
			// 	[4,6,7,9] + 3,
			// 	[2,4,7,9] + 2
			// ],
			\scaleIdx, Pfunc({ |e| (m.com.scaleIdx ? 0) % e[\scales].size }),
			\note, Pfunc({ |e| var set = e[\scales][e[\scaleIdx]]; set[((e[\tilt] ? 0) * set.size).floor.asInteger.clip(0, set.size - 1)] }),
			\start, Pwhite(0.0, 0.04, inf),
			\legato, 0.8,
			// \octave, 4,
			// \attack, 0.002,
			\release, 0.4,
			\pan, 0,

			\shape, \circle,
			\fill, true,
			\startSize, 0,
			\endSize, 0,
			\startWidth, 3,
			\endWidth, 0.4,
			\duration, 1.2,

			\args, #[],
		);
	);

	Pdef(m.ptn).play(quant: 1);
	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(pianoPtn).play(quant: 1);
	Pdef(pianoPtn).set(\amp, 0);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var g = group;
	var v = verb;
	var lib = samplesLib;
	var bus = fxBus;
	var voiceTail = 3.0;
	var verbTail = 1.5;
	group = nil;
	verb = nil;
	samplesLib = nil;
	fxBus = nil;

	Pdef(m.ptn).remove;
	Pdef(pianoPtn).remove;
	Event.eventTypes.removeAt(eventTypeName);
	Event.eventTypes.removeAt(pianoEventTypeName);

	// Synths first, then buffers, then the bus. Freeing a buffer a PlayBuf
	// is still reading crashes the server; the wait lets the reverb tail
	// out rather than being cut off by the freeAll.
	fork {
		voiceTail.wait;
		if (v.notNil) { v.set(\gate, 0) };
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
		if (bus.notNil) {
			postf("bus dealloc [%] \n", bus);
			bus.free;
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
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var idx = m.accelMassFiltered.lincurve(0, 2.0 * sens, 0, divs.size - 1, -1).round.asInteger.clip(0, divs.size - 1);
	var amp = m.accelMassFiltered.lincurve(0, 2.0 * sens, -30, -10, -2);
	var subamp = m.accelMassFiltered.lincurve(0, 2.5 * sens, -20, -4, -2);
	var pianoAmp = m.accelMassFiltered.lincurve(0, 0.2 * sens, -34, -6, -1);
	var pianoDur = m.accelMassFiltered.lincurve(0, 2.0 * sens, 0.2, 0.025, -1);
	var pianoOct = ((d.sensors.gyroEvent.y / pi).fold(-0.5,0.5) * 2.0).linlin(-1, 1, 3, 6).round.asInteger;
	var pianoTilt = ((d.sensors.gyroEvent.y / pi).fold(-0.5,0.5) * 2.0).linlin(-1, 1, 0, 1);
	var attack = m.accelMassFiltered.lincurve(0, 2.0 * sens, 0.2, 0.02, -3);

	if(amp < 25.neg, { amp = 140.neg});
	if(pianoAmp < 29.neg, { pianoAmp = 120.neg});

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol);
	Pdef(m.ptn).set(\subamp, subamp.dbamp * vol);
	Pdef(m.ptn).set(\root, m.com.root ? 0);

	Pdef(pianoPtn).set(\viewID, d.port);
	Pdef(pianoPtn).set(\amp, pianoAmp.dbamp * vol * 0.25);
	Pdef(pianoPtn).set(\dur, pianoDur);
	Pdef(pianoPtn).set(\attack, attack);

	Pdef(pianoPtn).set(\root, m.com.root ? 0);
	Pdef(pianoPtn).set(\octave, pianoOct);
	Pdef(pianoPtn).set(\tilt, pianoTilt);

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
	// [(m.com.scaleIdx ? 0) / 5];//band set

	[m.accelMass, m.accelMassFiltered];
};
