var m = ~model;
var group;
var pad;
var samplesLib;

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;
var kitFolder = "~/Downloads/cotf_samples/African Marimba";
var sampleFilter = "Marimba ln f l1x";

var beat = 4;

var bassLine = [0, 7, 12];

var divs = [6, 12];
var run = [0];

var interval = 12;
var transpose = 2;

var runUp = true;

m.accelMassFilteredAttack = 0.95;
m.accelMassFilteredDecay = 0.9;
m.rrateMassFilteredAttack = 0.8;
m.rrateMassFilteredDecay = 0.4;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\miniMarimba, { |out = 0, bufnum = 0, amp = 0.5, rate = 1, start = 0, pan = 0,
	attack = 0.002, sustain = 0.04, release = 1.2|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig);
}).add;

SynthDef(\miniMarimbaPad, { |out = 0, freq = 130, amp = 0, gate = 1,
	cutoff = 800, blend = 0.5, glide = 0.25|
	var f = freq.lag(glide);
	var env = EnvGen.kr(Env.asr(2.0, 1, 2.5), gate, doneAction: 2);
	var drift = LFNoise2.kr([0.13, 0.17, 0.11]).range(0.997, 1.003);
	var warm = VarSaw.ar(f * drift, 0, blend.lag(0.4).linlin(0, 1, 0.5, 0.05), 0.25).sum;
	var sub = SinOsc.ar(f * 0.5, 0, 0.5);
	var sig = MoogFF.ar(warm + sub, cutoff.lag(0.4).clip(120, 6000), 1.4);
	sig = Splay.ar([sig, DelayC.ar(sig, 0.03, 0.013)], 0.6);
	Out.ar(out, sig * env * amp.lagud(0.4, 1.8));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var folder = PathName(kitFolder);

	var parseMarimbaNote = { |fileStem|
		var noteNames = "C C# D D# E F F# G G# A A# B".split($ );
		var parts = fileStem.findRegexp("([0-9])[ ]*([a-g])$");
		var octave = parts[1][1].asInteger;
		((octave + 1) * 12) + noteNames.indexOfEqual(parts[2][1].toUpper)
	};

	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({ |sample| (sample[\midiNote] - targetMidi).abs });
		(buffer: closest[\buffer], rate: (targetMidi - closest[\midiNote]).midiratio)
	};

	group = Group.new;

	samplesLib = folder.entries
		.select({ |path| path.fileName.contains(sampleFilter) })
		.collect({ |path|
			var midiNote = parseMarimbaNote.(path.fileNameWithoutExtension);
			postf("loading sample : % (midi %) \n", path.fileNameWithoutExtension, midiNote);
			(
				midiNote: midiNote,
				buffer: Buffer.read(s, path.fullPath, action: { |buf|
					postf("buffer alloc [%] \n", buf);
				})
			)
		});

	if(samplesLib.size == 0, {
		"[miniMarimba] no samples matched '%' in % - the pattern will stay silent"
			.format(sampleFilter, kitFolder).warn;
	});

	pad = Synth.tail(group, \miniMarimbaPad, [\freq, 48.midicps, \amp, 0]);

	Event.addEventType(eventTypeName, {
		var midi, found;
		if(samplesLib.size > 0 and: { currentEnvironment.isRest.not }, {
			midi = (currentEnvironment.use { ~midinote.value } + transpose).round.asInteger;
			found = midi.asArray.collect({ |n| findClosestSample.(n) });
			~bufnum = found.collect({ |f| f[\buffer] }).unbubble;
			~rate = found.collect({ |f| f[\rate] }).unbubble;
			if(~layer == \bass, {
				s.makeBundle(s.latency, { pad.set(\freq, (midi.asArray.first).midicps) });
			});
			~type = \customVisualEvent;
			currentEnvironment.play;
		});
	});

	Pdef(m.ptn,
		Ppar([
			Pbind(
				\instrument, \miniMarimba,
				\group, group,
				\type, eventTypeName,
				\layer, \bass,

				\dur, beat / 3,
				\count, Pseries(0, 1, inf),
				\step, Pkey(\count) % 3,
				\note, Pfunc({ |e| bassLine.wrapAt(e[\count]) }),
				\octave, 3,
				\amp, Pkey(\bassAmp),
				\release, 1.6,
				\pan, -0.15,

				\shape, \triangle,
				\rotation, Pfunc({ |e| (e[\step] ? 0) / 3 * 2pi }),
				\startSize, Pfunc({ |e| (e[\amp] ? 0.1).explin(0.003, 0.5, 40, 220) }),
				\endSize, Pfunc({ |e| (e[\amp] ? 0.1).explin(0.003, 0.5, 60, 300) }),
				\startWidth, 3,
				\endWidth, 0.5,
				\startColor, Pfunc({ |e|
					var warm = (e[\cutoff] ? 800).explin(200, 5000, 0.0, 0.12);
					Color.hsv(warm, 0.85, 0.95, 0.9)
				}),
				\endColor, Color.new(0.4, 0.05, 0.0, 0.0),
				\duration, beat,
				\modulation, Pfunc({ (amp: 0) }),
			),

			Pbind(
				\instrument, \miniMarimba,
				\group, group,
				\type, eventTypeName,
				\layer, \run,

				\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),         Pkey(\divIdx)),
				\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }), Pkey(\divIdx)),
				\dur,  Pkey(\div).reciprocal * beat,
				\note, Pfunc({ |e|
					var line = run.keep(e[\div] ? 6);
					var n;
					if(e[\step] == 0, { runUp = runUp.not });
					if(runUp.not, { line = line.reverse });
					n = line.clipAt(e[\step] ? 0);
					[n, n + (e[\interval] ? interval)]
				}),
				\octave, 5,
				\amp, Pfunc({ |e| (e[\runAmp] ? 0) * if(e[\step] == 0, { 1.0 }, { 0.7 }) }),
				\release, Pfunc({ |e| (e[\dur] ? 0.2) * 4 }),
				\pan, Pfunc({ |e| ((e[\step] ? 0) / (e[\div] ? 6) * 2 - 1) * 0.5 }),

				\shape, \line,
				\rotation, pi.half,
				\sx, Pfunc({ |e| ((e[\step] ? 0) / (e[\div] ? 6) * 1.6) - 0.8 }),
				\ex, Pkey(\sx),
				\sy, Pfunc({ |e|
					var lo = (e[\note].asArray.first ? 0) + 60;
					(lo + ((e[\interval] ? interval) / 2)).linlin(48, 96, 0.7, -0.7)
				}),
				\ey, Pkey(\sy),
				\startSize, Pfunc({ |e| (e[\interval] ? interval) * 6 + 4 }),
				\endSize, Pkey(\startSize),
				\startWidth, Pfunc({ |e| (e[\amp] ? 0.1).explin(0.003, 0.5, 1, 8) }),
				\endWidth, 0.5,
				\startColor, Pfunc({ |e|
					Color.new(1.0, 0.85, 0.35, (e[\amp] ? 0.1).explin(0.003, 0.5, 0.2, 1.0))
				}),
				\endColor, Color.new(1.0, 0.5, 0.1, 0.0),
				\duration, Pfunc({ |e| (e[\dur] ? 0.2) * 5 }),
				\modulation, Pfunc({ (amp: 0) }),
			)
		])
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\bassAmp, 0);
	Pdef(m.ptn).set(\runAmp, 0);
	Pdef(m.ptn).set(\interval, interval);
	Pdef(m.ptn).set(\cutoff, 800);
	Pdef(m.ptn).set(\root, 0);

	Pdef(m.ptn).play(quant: beat);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);
	if (pad.notNil) { pad.set(\gate, 0) };

	fork {
		2.6.wait;
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
			pad = nil;
		};
		if (samplesLib.notNil) {
			samplesLib.do({ |sample|
				postf("buffer dealloc [%] \n", sample[\buffer]);
				sample[\buffer].free;
				s.sync;
			});
			samplesLib = nil;
		};
	};
};

//------------------------------------------------------------
~next = { |d|
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, -2);
	var e = m.accelMassFiltered;

	var idx = e.lincurve(0, 1.5 * sens, 0, divs.size - 1, 1).round.asInteger.clip(0, divs.size - 1);
	var bassAmp = e.lincurve(0.0, 1.0 * sens, -40, -8, -2);
	var runAmp = e.lincurve(0.2 * sens, 2.0 * sens, -30, -4, -2);
	var padAmp = e.lincurve(0.0, 1.5 * sens, -60, -14, -2);

	var cutoff = m.rrateMassFiltered.linexp(0.0, 2.0 * sens, 250, 4500);
	var blend = (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0, 1);

	if(bassAmp < 39.neg, { bassAmp = 120.neg });
	if(runAmp < 29.neg, { runAmp = 120.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\bassAmp, bassAmp.dbamp * vol);
	Pdef(m.ptn).set(\runAmp, runAmp.dbamp * vol * 0.06);
	Pdef(m.ptn).set(\cutoff, cutoff);
	Pdef(m.ptn).set(\root, m.com.root ? 0);

	if (pad.notNil) {
		pad.set(\amp, padAmp.dbamp * vol * 3, \cutoff, cutoff, \blend, blend);
	};
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMassFiltered, m.rrateMassFiltered];
};
