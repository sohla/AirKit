var m = ~model;
var note = 60;

// THE SLOT. trainBass2's event is now a SLOT, and every sequence in the
// Pbind — dur, note, octave, root — advances one step per slot, never
// per event. A slot is always 0.22 long. What accel changes is how many
// events fill it: one at rest, two above doubleThresh. Two rungs only,
// subdiv 1 and 0.5.
//
// THE PAIR, latched per slot — the multiBeat mechanism. Each entry of
// reps is embedded as Pn(n, n), a FINITE pattern of length n, so the
// Pswitch reads \rateIdx once and then cannot read it again until the
// whole slot has been handed out. That is what makes double time
// arrive as a pair: accel falling away mid-slot cannot strand a single
// 0.11 event, because the rung was latched before the slot began. Drop
// the latch and the grid drifts by a half-slot the first time the stick
// wavers, and never recovers.
//
// Everything else follows \rep rather than \rateIdx, through Pdup —
// one value pulled off the source sequence per slot, repeated across
// the slot's events. So a subdivided slot fires the SAME note at the
// SAME octave twice: at slot level the riff still stutters 2 against an
// alternating [3,4], so in double time the note stutters 4 and the
// octave 2, exactly the trainBass2 figure struck twice per position.
// Rest(0.22) / 2 is Rest(0.11), so a subdivided rest stays a rest.
var reps = [1, 2];
var doubleThresh = 0.5;

// THE ONE SHOT. The pattern is defined in ~init but NOT played there.
// The first time accel crosses startThresh the pattern starts, and the
// latch is never reset — it is a file-level var, so reloading the p-file
// re-arms it. quant: 0 so the sequence starts on the gesture rather than
// on the next grid line; quant: 0.22 would grid-lock it to the rest of
// the quartet instead.
var startThresh = 1.0;
var started = false;

// The slot rung, latched. Every other sequence reads \rep, not this.
var repPat = Pswitch(reps.collect({ |n| Pn(n, n) }), Pkey(\rateIdx));


// SHARED ACROSS THE QUARTET. All four voices read m.com.root, so a
// harmony change turns every hue together — this file is the one that
// WRITES it (see ~onEvent), the other three follow. Each voice keeps its
// own base hue; the root only nudges it, so the ensemble shifts as one
// without losing channel identity.
var rootHue = { (m.com.root ? 0).linlin(-2, 3, -0.06, 0.06) };

// Nothing rotates here any more, so there is no turn counter. Angle is
// read straight off the note instead — see the ~vdef comment in ~init.

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.9999;
m.accelMassFilteredDecay = 0.9999;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.99;
m.gyroFilteredDecay = 0.99;

//------------------------------------------------------------
SynthDef(\versatilePerc, {
    |out=0, freq=50, tension=0.1, decay=0.5, clickLevel=0.5, amp=0.5, dist = 5, filtFreq = 20, filtRes = 0.8,pan =0|
    var pitch_contour, drum_osc, click_osc, drum_env, click_env, sig, pch;

	var sub = SinOsc.ar(freq * 0.5, pi, 1) * 1;
    // Pitch envelope
    pitch_contour = Line.kr(1, 0, 0.02);

    // Drum oscillator

	pch = freq * (1 + (pitch_contour * tension));
	drum_osc = SinOsc.ar([pch,pch*1.007], LFNoise2.ar([4,5],10,-10),1.8);

    // Click oscillator
    click_osc = LPF.ar(WhiteNoise.ar(1), 1500);

    // Drum envelope
    drum_env = EnvGen.ar(
        Env.perc(attackTime: 0.005, releaseTime: decay, curve: -4),
        doneAction: 2
    );

    // Click envelope
    click_env = EnvGen.ar(
        Env.perc(attackTime: 0.001, releaseTime: 0.01),
        levelScale: clickLevel
    );
	sig = (drum_osc * drum_env) + (click_osc * click_env);
	sig = (sig * dist).tanh.distort;
	sig = HPF.ar(sig, filtFreq)+ (sub * drum_env);
    // Mix and output
    Out.ar(out, Balance2.ar(sig[0], sig[1],pan,amp))
}).add;

//------------------------------------------------------------
~init = ~init <> {|d|

	// visual : the lamps of the tunnel, coming at us.
	//
	// All four voices share one vanishing point at the canvas centre.
	// Nothing here is placed — every mark is BORN at the vanishing point,
	// tiny and almost dark, and travels outward until it leaves the frame.
	// The travel is the \startSize -> \endSize sweep read through a curve
	// of +3, which is what makes it read as depth rather than as a zoom:
	// a lamp far down the tunnel barely moves, then blows past in the last
	// fifth of its life. Linear would look like an expanding circle;
	// curved looks like approach.
	//
	// Nothing rotates. The angle is not a counter creeping round, it is
	// read straight off the note: pitch class maps to the full circle, so
	// a given note ALWAYS fires down the same bearing. Repeats of a note
	// stack on the same line and the riff becomes a fixed set of bearings
	// rather than a sweep — a constellation you learn, not a clock.
	//
	// The pair is the point. At slot level \note still stutters 2 against
	// an alternating \octave, so every note is played twice, once in each
	// octave. The upper octave adds pi, which puts it diametrically
	// opposite its own lower octave. So each note arrives as two lines
	// firing out from the centre on exactly opposite bearings — the
	// octave swap read as symmetry across the tunnel instead of as a
	// change of size. Both halves travel the same distance, so the pair
	// stays balanced.
	//
	// Double time is not a new bearing, it is the SAME bearing struck
	// twice: a subdivided slot fires the same note at the same octave
	// 0.11 apart, so the second ray leaves the vanishing point while the
	// first is still travelling and the line reads as thickened and
	// stuttering outward rather than as a new direction. The constellation
	// of bearings is fixed; accel only decides which of them are doubled.
	//
	// trail is 1.0, so the line is anchored at the middle and grows
	// outward rather than detaching as a comet. It is a ray leaving the
	// vanishing point, with the head riding its tip.
	//
	// The Rest in the dur cycle falls on every third event, so now and
	// then only one half of a pair is drawn — the symmetry breaks for a
	// beat. That is the rest made visible, and it stops the figure
	// becoming a static cross.
	//
	// Lineage: cyclic/radial notation, atlas grammar G8 — but with the
	// traversal removed, which the atlas allows: when no cursor moves,
	// meaning comes from the field reconfiguring itself (§0.2). Palette
	// from §0.5, saturated primaries on black.
	//
	//   note        -> bearing round the tunnel, by pitch class
	//   octave      -> which side of the circle, 3 vs 4 is opposite
	//   distance    -> \startSize -> \endSize through a +3 curve
	//   amp         -> ray weight as it nears     (\widthEnv)
	//   m.com.root  -> hue, shared with all four voices
	//   accel       -> whether a slot fires one ray or two on one bearing
	//   Rest        -> nothing drawn, so a pair goes half-lit
	~vdef.(\driveSpoke, { |ev, c|
		var mod   = ev[\modulation] ? ();
		var hub   = c[\pos];
		var rim   = hub + (c[\size] @ 0);
		var head  = c[\size] * (mod[\headRatio] ? 0.07);
		var trail = mod[\trail] ? 0.34;
		var tail  = hub.blend(rim, 1 - trail);
		if((mod[\rest] ? false).not, {
			c[\render].(Array.fill(12, { |j| tail.blend(rim, j / 11) }), 0.6, 0.55, false);
			c[\draw].(\circle, (pos: rim, size: head), 1, 1);
		});
		nil
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \versatilePerc,
			\rep, repPat,
			\note, 8,//Pdup(Pkey(\rep), Pseq([0,10,5,4,7,7,2,5,4,4,-2,2,0,0,0,0].stutter(2) + 4, inf)),
    		\slotDur, Pdup(Pkey(\rep), Pseq([0.22,0.22,Rest(0.22),0.22,0.22,0.22] - 0.04, inf)),
			\dur, Pkey(\slotDur) / Pkey(\rep),
			\octave,Pdup(Pkey(\rep), Pseq([4,3],inf)),
			\root, Pdup(Pkey(\rep), Pseq([0,0,-2,0,3].stutter(32), inf)),
			\decay,Pkey(\octave).squared * 0.05,
   			\pan, Pxrand([-0.5,0.5], inf),
   			\filtRes, 1.0,//Pwhite(0.4,0.7),

			\type, \customVisualEvent,
			\shape, \driveSpoke,
			\rotation,
			// Pfunc({ |e|
			// 	(((e[\note] ? 4) % 12) / 12 * pi.half) - 0.525
			// 		+ if((e[\octave] ? 3) > 3.5, { pi }, { 0 })
			// }),

			Pfunc({ |e|
				var midi = (e[\note] ? 6) + (e[\root] ? 0) + (12 * (e[\octave] ? 5));
				var a = midi.linlin(36, 96, 0, 2pi);

				if((e[\octave] ? 3) > 3.5, {
					a + 0.95// (((e[\note] ? 4) % 12) / 12 * pi.half) - 0.525
				}, {
					a.neg + 0.95// (((e[\note] ? 4) % 12) / 12 * pi.half) - 0.525
				})

			}),

			\startSize, Pfunc({ |e|
				var a = e[\amp];
				if(a<0.03,{a=0});(a ? 0.3).linlin(0, 1, 0, 400)
				}),
			\endSize, Pkey(\startSize) * 2,
			\sizeEnv, Pfunc({ Env([0, 1], [1], 3) }),
			\startWidth, 1,
			\endWidth, Pfunc({ |e| (e[\amp] ? 0.3).linlin(0, 1, 3, 11) }),
			\widthEnv, Pfunc({ Env([0, 1], [1], 3) }),
			// \startColor, Pfunc({ |e| Color.hsv((0.03 + rootHue.()).wrap(0, 1), 1.0, 0.8, 0.95) }),
			// \endColor, Pfunc({ |e| Color.hsv((0.03 + rootHue.()).wrap(0, 1), 0.70, 1.0, 0.05) }),

			\startColor, d.color,
			\endColor, d.color,

			\colorEnv, Pfunc({ Env([0, 1], [1], 3) }),
			\duration, 1.7,
			\modulation, Pfunc({ |e|
				(rest: e.isRest, amp: 0, headRatio: 0.07, trail: 1.0)
			}),

			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		)
	);

	// Seed the envir. The one shot means ~next has run before the first
	// event fires, so this is belt and braces — but a nil \rateIdx would
	// reach Pswitch.wrapAt as nil.
	Pdef(m.ptn).set(\rateIdx, 0);
	// Pdef(m.ptn).play(quant: 0);

};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
~onEvent = {|e|

	m.com.root = e.root;
	m.com.dur = e.dur;

};

//------------------------------------------------------------
~next = {|d|

	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var sens = d.params.sensitivity;
	var a = m.accelMassFiltered.lincurve(0,2.2 * sens,0.0,1,-1);
	var filtSpeed = m.accelMassFiltered.lincurve(0,2.5 * sens,0.1,20,3);
	var ff = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).linexp(-1.0,1.0,700,1000);
	var dist = m.accelMassFiltered.lincurve(0,2.5 * sens,1,2,1);
	var tension = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0,1.0,0.01,1,2);

	if(a<0.02,{a=0});

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, a * 1.0 * vol);
	Pdef(m.ptn).set(\filtFreq, ff);
	Pdef(m.ptn).set(\dist, dist);
	Pdef(m.ptn).set(\tension, tension);

	// \rateIdx is deliberately NOT a Pbind key — a Pbind key would
	// override the envir and defeat this .set. The slot decides when to
	// read it, so this can flip as often as it likes.
	// Pdef(m.ptn).set(\rateIdx, if(m.accelMassFiltered > doubleThresh, 2, 0));
	// Pdef(m.ptn).set(\rateIdx,  ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0,1,0,3,-2).asInteger);
	Pdef(m.ptn).set(\rateIdx, m.accelMassFiltered.lincurve(0.0,2.5 * sens,0,1,6).asInteger);
	if(started.not and: { m.accelMass > doubleThresh }, {
		started = true;
		Pdef(m.ptn).play(quant:0.18);
	});

};


//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	// [m.accelMass * 0.1, m.accelMassFiltered * 0.1];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.z];
	[((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2)];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
