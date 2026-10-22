/*
gestures:    [tilt, rotate, shake, wrist]
description: A duet for two people and a shared memory. The trigger gives up two numbers per stroke and nothing else - when, and how hard - so that is the material. Every stroke sounds immediately and is also written into a rolling sixteen-stroke phrase. An echo routine walks that phrase forever, replaying the drummer's own timing and dynamics back at them; the airstick decides whether the echo is audible, how fast it runs against the drummer's live playing, what key it is in and how bright it is. Dynamics are melody here: how hard a stroke was played picks its note, so the drummer writes a tune out of a shape they can feel but not hear until it comes back. The phrase is a rolling window, so the echo is always chasing what was just played. The picture is the memory itself - the remembered contour, and the head reading it.
sound:       struck bars, bright for the live stroke and darker for the remembered one
pitch:       pentatonic, chosen by how hard each stroke was hit, transposed by tilt
rhythm:      the drummer's own, replayed at the mover's tempo
instruments: [Template]
*/

var inputBus = 1;
var m = ~model;
var dev = ~device;
var group, trig, echo;
var hitKey = ('hit_' ++ m.ptn).asSymbol;

// The shared memory. A rolling window, not a committed take - the echo
// always reads the last phraseLen strokes, so as the drummer plays the
// echo gradually becomes the new material and is always chasing them.
var phraseLen = 16;
var phrase;
var loopIdx = 0;

// Dynamics are melody. A stroke's velocity picks its note out of this, so
// the drummer's dynamic shape is the melodic shape.
var notes = [0, 3, 5, 7, 10];
var baseNote = 60;

// The drummer, as the instrument understands them.
var lastHit = 0;
var avgIoi = 0.5;
var steadiness = 0.5;
var hitCount = 0;

var ioiMin = 0.05;
var ioiMax = 2.0;

// A real average, not the 0.99 used elsewhere in the kit - that weights
// the newest interval at 99% and so tracks the last stroke rather than
// the tempo. The echo needs the tempo.
var smooth = 0.25;

// Mover values owned by ~next.
var stretchNow = 1.0;
var transposeNow = 0;
var echoAmpNow = 0.0;
var brightNow = 0.5;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
// The trigger. Unchanged from temp_input11 and 12 apart from the name, so
// the drummer's feel carries straight over. The input is only ever read as
// an onset and a velocity - nothing downstream touches the signal itself.
SynthDef(\inputTrigger13, { |inputBus = 1, ratio = 3.0, floorDb = -48,
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
// One struck-bar voice for both layers. The live stroke and the remembered
// one are the same instrument at different settings - bright and short
// for now, darker and longer for then - so the echo reads as the same
// thing displaced in time rather than as a second instrument.
//
// DynKlank rather than Klank because decay has to be an arg; Klank bakes
// its spec at build time.
SynthDef(\strike13, { |out = 0, freq = 440, amp = 0.3, vel = 1.0,
	decay = 0.8, tone = 3000, bright = 0.5, pan = 0|

	var fire = Impulse.ar(0);
	var knock = Decay.ar(fire, 0.004)
		* LPF.ar(WhiteNoise.ar(1), (tone * vel).clip(200, 18000));

	var body = DynKlank.ar(`[
		[1, 2.76, 5.40, 8.93],
		[1, 0.6, 0.3, 0.15],
		[decay, decay * 0.6, decay * 0.4, decay * 0.25]
	], knock, freq);

	var env = EnvGen.kr(Env.perc(0.001, decay * 1.5, 1, -4), doneAction: 2);
	var sig = (body * (0.3 + bright)).tanh * vel * env * amp;

	DetectSilence.ar(sig, 0.0001, 0.2, doneAction: 2);
	Out.ar(out, Pan2.ar(sig, pan));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	// velocity -> note. Shared by both layers so the live stroke and its
	// echo agree about what a given dynamic means, and both take the
	// mover's transposition.
	var noteFor = { |vel|
		var i = vel.clip(0, 1).lincurve(0, 1, 0, notes.size - 1, -1).round.asInteger;
		baseNote + notes.clipAt(i + transposeNow)
	};

	phrase = [];
	loopIdx = 0;
	lastHit = 0;
	avgIoi = 0.5;
	steadiness = 0.5;
	hitCount = 0;

	group = Group.new;

	//------------------------------------------------------------
	// visual : the memory, drawn as itself. x is position in the phrase,
	// y is the velocity of the stroke remembered there - which is also its
	// note, so the contour is the tune. The head shows where the echo is
	// reading. Both are single duration:inf marks whose vdefs read the
	// phrase out of scope and are called once per frame, so they update
	// continuously instead of being redrawn per stroke.
	//
	//   phrase index -> x
	//   velocity     -> y          (and therefore pitch)
	//   loop index   -> head x
	//   stretch      -> head height

	~vdef.(\phraseLine, { |ev, c|
		var mid = c[\pos];
		var halfW = c[\bounds].width * 0.5 * 0.85;
		var halfH = c[\size];
		var n = (phrase ? []).size;

		if(n < 2, {
			// nothing remembered yet - a flat line, so the empty tape is
			// legible rather than just absent
			[ mid + (halfW.neg @ 0), mid + (halfW @ 0) ]
		}, {
			Array.fill(n, { |i|
				var x = ((i / (phraseLen - 1)) * 2 - 1) * halfW;
				var y = (phrase[i][\vel] ? 0).clip(0, 1).linlin(0, 1, halfH, halfH.neg);
				mid + (x @ y)
			})
		})
	});

	~vdef.(\loopHead, { |ev, c|
		var mid = c[\pos];
		var halfW = c[\bounds].width * 0.5 * 0.85;
		var n = (phrase ? []).size.max(1);
		var x = (((loopIdx % n) / (phraseLen - 1)) * 2 - 1) * halfW;
		var h = c[\size] * stretchNow.explin(0.25, 4.0, 1.4, 0.5);
		[ mid + (x @ h.neg), mid + (x @ h) ]
	});

	trig = Synth(\inputTrigger13, [\inputBus, inputBus, \ratio, 0.3, \slowRel, 0.25, \floorDb, -82,
		\fullScale, 0.7, \curve, 0.5, \deadtime, 0.02], group);

	// the remembered contour
	(
		type: \customVisualEvent,
		amp: 0, dur: 0.01,
		viewID: dev.port,
		shape: \phraseLine,
		fill: false,
		sx: 0, sy: 0, ex: 0, ey: 0,
		startSize: 300,
		startWidth: 2, endWidth: 2,
		startColor: Color.new(1.0, 0.72, 0.25, 0.85),
		endColor: Color.new(1.0, 0.72, 0.25, 0.85),
		duration: inf
	).play;

	// the echo's reading head
	(
		type: \customVisualEvent,
		amp: 0, dur: 0.01,
		viewID: dev.port,
		shape: \loopHead,
		fill: false,
		sx: 0, sy: 0, ex: 0, ey: 0,
		startSize: 340,
		startWidth: 2, endWidth: 2,
		startColor: Color.new(0.45, 0.85, 1.0, 0.8),
		endColor: Color.new(0.45, 0.85, 1.0, 0.8),
		duration: inf
	).play;

	//------------------------------------------------------------
	// The drummer. A stroke sounds at once and is written to the phrase.
	// Its level is the drummer's alone - the mover can silence the echo
	// but never the player holding the sticks.
	OSCdef(hitKey, { |msg|
		if (msg[1] == trig.nodeID) {
			var vel = msg[3];
			var now = SystemClock.seconds;
			var ioi = if(hitCount > 0, { (now - lastHit).clip(ioiMin, ioiMax) }, { 0.5 });
			var note = noteFor.(vel);
			var x;

			if (hitCount > 0, {
				var drift = ((ioi - avgIoi).abs / avgIoi).clip(0.0, 1.0);
				avgIoi = (avgIoi * (1 - smooth)) + (ioi * smooth);
				steadiness = (steadiness * (1 - smooth)) + ((1 - drift) * smooth);
			});

			lastHit = now;
			hitCount = hitCount + 1;

			// bright and short : this is "now"
			Synth(\strike13, [
				\freq, note.midicps,
				\vel, vel,
				\amp, 0.5,
				\decay, 0.35,
				\tone, 6000,
				\bright, brightNow,
				\pan, -0.25
			], group);

			phrase = phrase.add((ioi: ioi, vel: vel));
			if (phrase.size > phraseLen, { phrase = phrase.drop(phrase.size - phraseLen) });

			// a flash on the contour where the stroke just landed
			x = (((phrase.size - 1) / (phraseLen - 1)) * 2 - 1) * 0.85;
			(
				type: \customVisualEvent,
				amp: 0, dur: 0.01,
				viewID: dev.port,
				shape: \circle,
				fill: true,
				sx: x, ex: x,
				sy: vel.clip(0, 1).linlin(0, 1, 0.55, -0.55),
				ey: vel.clip(0, 1).linlin(0, 1, 0.55, -0.55),
				startSize: vel.linlin(0, 1, 10, 46),
				endSize: 2,
				startColor: Color.new(1.0, 0.95, 0.7, 0.95),
				endColor: Color.new(1.0, 0.5, 0.1, 0.0),
				duration: 0.5
			).play;
		};
	}, '/akHit', s.addr);

	//------------------------------------------------------------
	// The echo. Walks the phrase forever at the mover's tempo, replaying
	// the drummer's intervals and dynamics. Reading the ioi out of the
	// phrase rather than off a grid is what makes it inherit the feel -
	// a drummer who swings gets a swung echo for free, with no mapping.
	echo = {
		loop {
			var slot, note;

			if (group.notNil and: { (phrase ? []).size > 1 }, {
				slot = phrase.wrapAt(loopIdx);
				note = noteFor.(slot[\vel]);

				// darker and longer : this is "then". Silent unless the
				// mover is asking for it.
				if (echoAmpNow > 0.0001, {
					Synth(\strike13, [
						\freq, note.midicps,
						\vel, slot[\vel],
						\amp, echoAmpNow,
						\decay, 1.6,
						\tone, 1800,
						\bright, brightNow,
						\pan, 0.3
					], group);
				});

				loopIdx = loopIdx + 1;
				(slot[\ioi] * stretchNow).clip(0.02, 6).wait;
			}, {
				// nothing remembered yet, or torn down
				0.1.wait;
			});
		};
	}.fork(SystemClock);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;

	OSCdef(hitKey) !? (_.free);
	echo !? (_.stop);
	echo = nil;
	trig !? (_.free);
	trig = nil;
	group = nil;
	phrase = nil;

	if (dying.notNil) {
		fork {
			2.4.wait;
			s.bind { dying.freeAll };
			s.sync;
			dying.free;
		};
	};
};

//------------------------------------------------------------
// Where the duet happens.
//
//   MOVER                                  DRUMMER
//   shake  -> is the echo audible          timing     -> the echo's rhythm
//   tilt   -> what key it is in            dynamics   -> the melody
//   rotate -> how fast it runs             steadiness -> how tight it is
//   wrist  -> how bright both layers are
//
// Neither side carries it alone. With an empty phrase the echo has nothing
// to walk and waits; with a still stick the gate holds the echo at silence
// and only the live strokes sound. The drummer owns all the material and
// none of the playback; the mover owns all the playback and none of the
// material.
~next = {|d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var e = m.accelMassFiltered;

	// tilt transposes the whole memory through the pool. Integer steps, so
	// it lands on notes rather than sliding between them.
	var tr = (d.sensors.gyroEvent.y / pi.half).clip(-1, 1)
		.linlin(-1, 1, (notes.size - 1).neg, notes.size - 1);

	// rotation runs the echo against the drummer. 1.0 is exactly as
	// played; below that it outruns them, above it drags behind.
	var stretch = m.rrateMassFiltered.linexp(0, 0.5 * sens, 1.0, 0.3);

	// wrist : dull to ringing, applied to both layers so the two stay one
	// instrument
	var bright = (d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.0, 1.4);

	var amp = e.lincurve(0, 3 * sens, -30, -8, -1);

	// A steady drummer gets a tighter echo - the stretch is pulled back
	// toward 1.0, so a locked groove comes back in time with itself while
	// a loose one is free to drift.
	var tight = steadiness.clip(0.0, 1.0);

	// Silence gate, the idiom from miniMarimba and hollowWood1L. Without
	// it the curve floors at -30dB and a still stick would still be
	// echoing audibly.
	if (amp < 26.neg, { amp = 120.neg });

	transposeNow = tr.round.asInteger;
	stretchNow = stretch.blend(1.0, tight * 0.6).clip(0.25, 4.0);
	brightNow = bright;
	echoAmpNow = amp.dbamp * vol * 0.8;
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// one trace per player, then the thing they share :
	// the drummer's tempo, the drummer's steadiness, the echo's stretch
	[
		avgIoi.reciprocal.explin(0.5, 20, 0.0, 1.0),
		steadiness,
		stretchNow.explin(0.25, 4.0, -1.0, 1.0)
	];
};
