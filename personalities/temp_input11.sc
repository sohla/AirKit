// IMU controls chords and root. 

var inputBus = 0;
var m = ~model;
var dev = ~device;
var group, trig, pad;
var samplesLib;
var hitKey = ('hit_' ++ m.ptn).asSymbol;

var kitFolder = "~/Downloads/cotf_samples/African Marimba";
var sampleFilter = "Marimba ln f l1x";

// The note pool. Up/down tilt picks an index into it, and a trigger plays
// subdivLen notes from there - so the run is a rotation of the pool and
// the tilt decides which note it starts on.
var notes = [0, 3, 5, 2,7,10,9] - 12;

// Mid to high for the marimba; the pad sits an octave below it and its
// SynthDef adds a sub an octave below that again.
var baseNote = 72-12;
var padBase = 84-12;

// How many notes a trigger fires, and how fast. Both fixed on purpose -
// a trigger always plays subdivLen notes at beat / subdiv seconds apart,
// whatever the IMU is doing.
//
// subdivLen is independent of notes.size: the run reads through the pool
// with wrapAt, so a pool of 3 played 4 long wraps back to the first note,
// and a long pool played 3 long only ever sounds 3 of its notes per hit.
var beat = 1;
var subdiv = 4;
var subdivLen = 1;

// Motion values owned by ~next. The OSCdef and the sequence routine read
// them rather than computing anything themselves.
var idxNow = 0;
var ampNow = 0.0;
var padAmpNow = 0.0;
var cutoffNow = 800;
var blendNow = 0.5;

var step = 0;

// Speakers. Each stereo voice writes its left to outBus and its right to
// outBus + spread, wrapping at numChans - so 0 and 3 is front left and
// back left on a quad rig, and outBus 3 with spread 1 is 3 and 0.
//
// Other ways to place a voice, kept here for reference:
//  - rotate when the SynthDef is built:
//      Out.ar(0, (sig ++ (0 ! (numChans - 2))).rotate(offset));
//    wraps, but offset and numChans are fixed at build time, not per note.
//  - pad with silent channels: Out.ar(0, (0 ! offset) ++ sig);
//    adjacent channels only, and never wraps.
//  - PanAz.ar(numChans, mono, pos) : a continuous position round a ring of
//    speakers. Wraps naturally, mono in, suits IMU-driven movement.
var outBus = 0;
var spread = 3;
var numChans = s.options.numOutputBusChannels;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
// Right input. Named \inputTriggerR rather than \inputTrigger because
// every other trigger personality defines a \inputTrigger of its own and
// the last one loaded wins on the server - see the note in my reply.
SynthDef(\inputTriggerR, { |inputBus = 1, ratio = 3.0, floorDb = -48,
	deadtime = 0.10, slowAtk = 0.300, slowRel = 0.150, inGain = 0.7,
	fullScale = 0.35, curve = 1.5, window = 0.005|

	var in   = SoundIn.ar(inputBus) * inGain;
	var fast = Amplitude.kr(in, 0.001, 0.05);
	var slow = LagUD.kr(fast, slowAtk, slowRel);
	var over = fast > ((slow * ratio) + floorDb.dbamp);
	var edge = over > Delay1.kr(over);
	var trg  = Trig1.kr(edge, deadtime);

	var peak   = RunningMax.kr(fast, trg);
	var report = TDelay.kr(trg, window);

	var vel = Latch.kr(peak, report).linlin(0.0, fullScale, 0.0, 1.0).clip(0, 1).pow(curve);

	SendReply.kr(report, '/akHit', [vel]);
}).add;

//------------------------------------------------------------
// Both voices are verbatim copies from personalities/miniMarimba.sc under
// local names, so that personality's \miniMarimba and \miniMarimbaPad are
// left exactly as they are.
SynthDef(\marimbaVoice11, { |out = 0, spread = 1, numChans = 2, bufnum = 0, amp = 0.5, rate = 1, start = 0, pan = 0,
	attack = 0.002, sustain = 0.04, release = 1.2|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig[0]);
	Out.ar((out + spread) % numChans, sig[1]);
}).add;

SynthDef(\mellowPad11, { |out = 0, spread = 1, numChans = 2, freq = 130, amp = 0, gate = 1,
	cutoff = 800, blend = 0.5, glide = 0.25|
	var f = freq.lag(glide);
	var env = EnvGen.kr(Env.asr(2.0, 1, 2.5), gate, doneAction: 2);
	var drift = LFNoise2.kr([0.13, 0.17, 0.11]).range(0.997, 1.003);
	var warm = VarSaw.ar(f * drift, 0, blend.lag(0.4).linlin(0, 1, 0.5, 0.05), 0.25).sum;
	var sub = SinOsc.ar(f * 0.5, 0, 0.5);
	var sig = MoogFF.ar(warm + sub, cutoff.lag(0.4).clip(120, 6000), 1.4);
	sig = Splay.ar([sig, DelayC.ar(sig, 0.03, 0.013)], 0.6) * env * amp.lagud(0.4, 1.8);
	Out.ar(out, sig[0]);
	Out.ar((out + spread) % numChans, sig[1]);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var folder = PathName(kitFolder);

	// Sample loading and nearest-sample lookup, both from
	// personalities/miniMarimba.sc. The library is sparse, so almost every
	// note is reached by resampling the closest one.
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
	step = 0;

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
		"[temp_input11] no samples matched '%' in % - the marimba will stay silent"
			.format(sampleFilter, kitFolder).warn;
	});

	~vdef.(\padBlock, { |ev, c|
		var mod = ev[\modulation] ? ();
		var n = ((ev[\numPoints] ? 64) / 2).floor.max(2);
		var breadth = mod[\breadth] ? 60;
		var fMin = mod[\filtMin] ? 200;
		var fMax = mod[\filtMax] ? 2600;
		var fSpeed = mod[\filtSpeed] ? 0.1;
		var filt = sin(c[\elapsed] * fSpeed * 2pi).linlin(-1, 1, fMin, fMax);
		var half = c[\size] * filt / ((fMin + fMax) * 0.5);
		var mid = c[\pos];

		Array.fill(n, { |i| mid + ((((i / (n - 1)) - 0.5) * 2 * breadth) @ half.neg) })
		++ Array.fill(n, { |i| mid + (((0.5 - (i / (n - 1))) * 2 * breadth) @ half) })
	});

	// The mono drone. One instance for the life of the personality, opened
	// at silence. glide is shortened from the SynthDef's 0.25 because the
	// run's notes are 0.25s apart - at the default the pitch lag never
	// arrives and the four notes smear into one slide.
	pad = Synth.tail(group, \mellowPad11, [\out, outBus, \spread, spread, \numChans, numChans, \freq, padBase.midicps, \amp, 0, \glide, 0.08]);

	trig = Synth(\inputTriggerR, [\inputBus, inputBus, \ratio, 0.3, \slowRel, 0.25, \floorDb, -22,
		\fullScale, 0.7, \curve, 0.5, \deadtime, 0.02], group);


	OSCdef(hitKey, { |msg|
		if (msg[1] == trig.nodeID) {
			// The start note is latched at the hit so the run stays a
			// coherent rotation. Its timing is not motion-dependent at all.
			var idxAtHit = idxNow;
			var vel = msg[3];

			{
				subdivLen.do({ |i|
					var note, found, x, y;

					if (group.notNil and: { samplesLib.size > 0 }, {
						note = baseNote + notes.wrapAt(idxAtHit + i);
						found = findClosestSample.(note);

						Synth(\marimbaVoice11, [
							\out, outBus,
							\spread, spread,
							\numChans, numChans,
							\bufnum, found[\buffer],
							\rate, found[\rate],
							\amp, ampNow * vel,
							\attack, 0.02,
							\sustain, 0.04,
							\release, 1.2,
							// spread the run left to right, whatever its length
							\pan, ((i / (subdivLen - 1).max(1)) * 2 - 1) * 0.4
						], group);

						// The drone takes the same note, in its own register.
						// Set here rather than in ~next so it tracks the run
						// note for note; between runs it holds the last one.
						if (pad.notNil, {
							pad.set(\freq, (padBase + notes.wrapAt(idxAtHit + i)).midicps);
						});

						step = step + 1;
						x = ((((step - 1) % 16) + 0.5) / 8) - 1;
						y = note.linlin(60, 96, 0.85, -0.85);

						(
							type: \customVisualEvent,
							amp: 0,
							dur: 0.01,
							viewID: dev.port,

							shape: \padBlock,
							fill: true,
							numPoints: 64,

							sx: x,
							ex: x + 0.06,
							sy: y,
							ey: y,

							startSize: vel.linlin(0, 1, 18, 110),
							endSize: vel.linlin(0, 1, 18, 110),

							// hue by position in the run, so the notes read as
							// separate colours rather than one block
							startColor: Color.hsv([0.13, 0.08, 0.04, 0.0].wrapAt(i), 0.85, 1.0, 0.0),
							endColor: Color.hsv([0.13, 0.08, 0.04, 0.0].wrapAt(i), 0.85, 1.0, 0.85),
							colorEnv: Env([0, 1, 0.5, 0.5, 0], [0.02, 0.1, 0.13, 1.2] / 1.45),

							duration: 1.45,

							modulation: (
								type: \radial,
								freq: 0.6,
								amp: m.accelMassFiltered.clip(0, 1).linlin(0, 1, 2, 30),
								harmonics: 4,
								breadth: 58,
								filtMin: 200,
								filtMax: 2600,
								filtSpeed: 0.1
							)
						).play;
					});

					(beat / subdiv).wait;
				});
			}.fork(SystemClock);
		};
	}, '/akHit', s.addr);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;

	OSCdef(hitKey) !? (_.free);
	trig !? (_.free);
	trig = nil;
	if (pad.notNil) { pad.set(\gate, 0) };
	group = nil;

	fork {
		2.6.wait;
		if (dying.notNil) {
			s.bind { dying.freeAll };
			s.sync;
			dying.free;
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
// All the motion mapping lives here. The sequence routine and the drone
// both read what this leaves behind, so a gesture keeps working on notes
// that have already started and on the note still to come.
~next = {|d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var e = m.accelMassFiltered;

	// up/down picks which of the four notes the run starts on, and the
	// note the drone holds
	var ud = m.gyroYFiltered.clip(-0.5, 0.5).lincurve(-0.5, 0.5, 0, notes.size - 1, 0);

	// temp_input10's mapping, unchanged.
	var amp = e.lincurve(0, 3 * sens, -15, -1, -1);

	// Positive curve, so the drone stays down until there is real energy in
	// the stick instead of coming most of the way up on a small move.
	var padAmp = e.lincurve(0.0, 1.5 * sens, -60, -14, 2);

	idxNow = ud.round.asInteger.clip(0, notes.size - 1);

	// Floor to actual silence. The curve bottoms out at -60dB, which is
	// still audible on a sustained drone, so a still stick never went
	// quiet. Same idiom as miniMarimba.sc and hollowWood1L - drop below
	// the threshold and slam to -120dB. The pad's own amp.lagud(0.4, 1.8)
	// turns that into a 1.8s fade rather than a cut.
	if (padAmp < 22.neg, { padAmp = 120.neg });

	ampNow = amp.dbamp * vol;
	padAmpNow = padAmp.dbamp * vol * 3;
	cutoffNow = m.rrateMassFiltered.linexp(0.0, 2.0 * sens, 250, 4500);
	blendNow = (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0, 1);

	// No \freq here - the sequence routine owns the drone's pitch, so that
	// it follows the marimba. At this rate ~next would overwrite the note
	// within a control tick of it being set.
	if (pad.notNil) {
		pad.set(
			\amp, padAmpNow,
			\cutoff, cutoffNow,
			\blend, blendNow
		);
	};
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// note select, then the pad's blend
	[(d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.x / pi)];
};
