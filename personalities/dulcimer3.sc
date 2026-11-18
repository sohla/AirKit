/*
gestures:    [shake, tilt]
description: Hammered dulcimer in two layers — a low pedal note that subdivides faster the harder you play, and a tilt-picked melody on top that slides between notes
internals:   Ported from Airsticks-RPI personalities/dulcimer3.sc. Two Pdefs on ~beatClock (pedal + "piano" melody) firing \dulcimer3Voice sampler synths (PlayBuf + SinOsc sub, XLine glide from the previous note) into a dedicated Group, through one \dulcimer3Verb placed after the Group on a private bus. SynthDefs and Pbind material verbatim; visual keys dropped (no views on COTF hosts). RPI's 1 s beat = one ~beatClock bar (8 events), so the pedal divs land on quarter/eighth/sixteenth notes. Samples: ~/Music/cotf_samples/Celtic Hammered Dulcimer (hrd). Patch trim not set — verify at bench.
sound:       hard-mallet dulcimer pedal with a soft sine sub under it, a gliding dulcimer line over the top, small room reverb
pitch:       pedal = pools[0] (A) in octaves 4,4,3,3,2,2; melody = scales[0] (A G F# E D) picked by y tilt, octave 3-6 from y tilt. Idle: root 0 (RPI default). Tuning: root 0, melody pinned to A4. Piece: \root moves both layers so the pedal sits on the lowest note of ctx.voicePool. Curtain holds the last root.
rhythm:      pedal subdivides 2 / 4 / 8 per bar with accel; melody dur from accel (0.2 -> 0.025 s RPI-equivalent). Tuning: pedal at 2 per bar, melody slower. Curtain: pedal at 2 per bar, both from rotation only.
*/

var m = ~model;
var ob = ~outBus ? 0;
var group;
var samplesLib;
var loading = false;

var fxBus;
var verb;

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;
var pianoPtn = (m.ptn ++ "_piano").asSymbol;
var pianoEventTypeName = (\customEvent_ ++ m.ptn ++ "_piano").asSymbol;

var folder = PathName("~/Music/cotf_samples/Celtic Hammered Dulcimer");
var sampleFilter = "Dlcmr-hrd";

var beat = 8;   // ~beatClock events per RPI-second (one bar)
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

SynthDef(\dulcimer3Verb, {|in=0, out=0, mix=0.2, room=1.12, damp=0.2, amp=1,
    gate=1, release=1.4|
	var sig = In.ar(in, 2);
	var env = EnvGen.kr(Env.asr(0.01, 1, release), gate, doneAction: 2);
	sig = FreeVerb2.ar(sig[0], sig[1], mix, room, damp);
	sig = LeakDC.ar(sig);
	Out.ar(out, sig * env * amp);
	// Out.ar(2, sig * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({ |sample| (sample.midiNote - targetMidi).abs });
		(buffer: closest.buffer, rate: (targetMidi - closest.midiNote).midiratio, midiNote: closest.midiNote, idx: samplesLib.indexOf(closest))
	};
	var lastTarget, pianoLastTarget;

	loading = true;

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

	s.sync;

	if(loading.not or: { samplesLib.isNil }, {
		postf("dulcimer3: load cancelled — unloaded while samples were loading \n");
	},{
		samplesLib.do({ |sample|
			if(sample.buffer.numFrames.isNil or: { sample.buffer.numFrames == 0 }, {
				postf("dulcimer3: sample failed to load : % \n", sample.name);
			});
		});

		Event.addEventType(eventTypeName, { |e|
			var target = ~note + ~root + (12 * ~octave);
			var found = findClosestSample.(target);
			var from = lastTarget ? target;
			~bufnum = found.buffer;
			~rate = found.rate;
			~fromRate = (from - found.midiNote).midiratio;
			~fromFreq = from.midicps;
			lastTarget = target;
			~type = \note;
			currentEnvironment.play;
		});

		Event.addEventType(pianoEventTypeName, { |e|
			var target = ~note + ~root + (12 * ~octave);
			var found = findClosestSample.(target);
			var from = pianoLastTarget ? target;
			~bufnum = found.buffer;
			~rate = found.rate;
			~fromRate = (from - found.midiNote).midiratio;
			~fromFreq = from.midicps;
			pianoLastTarget = target;
			~type = \note;
			currentEnvironment.play;
		});

		topEnvironment.use {
			group = Group.new;
			fxBus = Bus.audio(s, 2);
			// after the Group, not inside it: ~onResync's freeAll must not take the reverb
			verb = Synth.after(group, \dulcimer3Verb, [\in, fxBus, \out, ob]);

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
					\args, #[],
				);
			);

			Pdef(m.ptn).set(\divIdx, 0, \amp, 0, \root, 0);
			Pdef(pianoPtn).set(\amp, 0, \root, 0, \dur, 0.2 * beat);
			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			Pdef(pianoPtn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		};

		~onResync = { |idx|
			topEnvironment.use {
				Pdef(m.ptn).stop;
				Pdef(pianoPtn).stop;
				s.bind { group.freeAll };
				Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
				Pdef(pianoPtn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			};
		};
	});
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var g = group;
	var v = verb;
	var lib = samplesLib;
	var bus = fxBus;
	var voiceTail = 3.0;
	var verbTail = 1.5;
	loading = false;
	group = nil;
	verb = nil;
	samplesLib = nil;
	fxBus = nil;

	Pdef(m.ptn).remove;
	Pdef(pianoPtn).remove;
	Event.eventTypes.removeAt(eventTypeName);
	Event.eventTypes.removeAt(pianoEventTypeName);

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
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  { },
		\piece,   { },
		\curtain, { },
		\silent,  {
			Pdef(m.ptn).set(\amp, 0);
			Pdef(pianoPtn).set(\amp, 0);
		}
	);
};

//------------------------------------------------------------
~idleNext = {|d, ctx|
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

	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol);
	Pdef(m.ptn).set(\subamp, subamp.dbamp * vol);
	Pdef(m.ptn).set(\root, 0);

	Pdef(pianoPtn).set(\amp, pianoAmp.dbamp * vol * 0.25);
	Pdef(pianoPtn).set(\dur, pianoDur * beat);
	Pdef(pianoPtn).set(\attack, attack);
	Pdef(pianoPtn).set(\root, 0);
	Pdef(pianoPtn).set(\octave, pianoOct);
	Pdef(pianoPtn).set(\tilt, pianoTilt);
};

~tuningNext = {|d, ctx|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.accelMassFiltered.lincurve(0, 2.0 * sens, -30, -16, -4);
	var subamp = m.accelMassFiltered.lincurve(0, 2.5 * sens, -20, -10, -4);
	var pianoAmp = m.accelMassFiltered.lincurve(0, 0.2 * sens, -34, -12, -4);
	var pianoDur = m.accelMassFiltered.lincurve(0, 2.0 * sens, 0.4, 0.1, -4);

	if(amp < 25.neg, { amp = 140.neg});
	if(pianoAmp < 29.neg, { pianoAmp = 120.neg});

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol);
	Pdef(m.ptn).set(\subamp, subamp.dbamp * vol);
	Pdef(m.ptn).set(\root, 0);

	Pdef(pianoPtn).set(\amp, pianoAmp.dbamp * vol * 0.25);
	Pdef(pianoPtn).set(\dur, pianoDur * beat);
	Pdef(pianoPtn).set(\attack, 0.2);
	Pdef(pianoPtn).set(\root, 0);
	Pdef(pianoPtn).set(\octave, 6);
	Pdef(pianoPtn).set(\tilt, 0);
};

~pieceNext = {|d, ctx|
	var pool = (ctx !? { ctx.voicePool }) ? [69];
	var loud = ((ctx !? { ctx.loudness }) ? 1.0).linlin(0, 1, 0.3, 1.0);
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
	var root;

	if(pool.isEmpty, { pool = [69] });
	root = (pool.minItem.asInteger - 69).wrap(-6, 5);   // pedal (A at root 0) onto the pool's lowest note

	if(amp < 25.neg, { amp = 140.neg});
	if(pianoAmp < 29.neg, { pianoAmp = 120.neg});

	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol * loud);
	Pdef(m.ptn).set(\subamp, subamp.dbamp * vol);
	Pdef(m.ptn).set(\root, root);

	Pdef(pianoPtn).set(\amp, pianoAmp.dbamp * vol * 0.25 * loud);
	Pdef(pianoPtn).set(\dur, pianoDur * beat);
	Pdef(pianoPtn).set(\attack, attack);
	Pdef(pianoPtn).set(\root, root);
	Pdef(pianoPtn).set(\octave, pianoOct);
	Pdef(pianoPtn).set(\tilt, pianoTilt);
};

~curtainNext = {|d, ctx|
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = (m.rrateMassFiltered * 2.0).lincurve(0, 1.0, -80, -35, -4);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol);
	Pdef(pianoPtn).set(\amp, amp.dbamp * vol * 0.25);
	Pdef(pianoPtn).set(\dur, 0.4 * beat);
};

//------------------------------------------------------------
~onTick    = {|ctx| };
~onHalf    = {|ctx| };
~onBeat    = {|ctx| };
~onBar     = {|ctx| };
~onPhrase  = {|ctx| };
~onSection = {|ctx| };
~onChord   = {|ctx| };
~onKey     = {|ctx| };
~onScale   = {|ctx| };

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
