var m = ~model;
var synth;
var bsynth;
var note = 48 + 4 - 12;
var lastTime = 0;
// The device, captured lexically in ~init. It CANNOT be read as ~device
// from inside the vdef: a draw func runs in drawCanvas's context, not the
// personality Environment, so ~device there resolves against the wrong
// currentEnvironment and comes back nil. m works only because it is a
// lexical var too. This is a reference, not a copied value, so
// dev.sensors is live at frame rate.
var dev;

// visual only : the eased alpha of the airstick. An accumulator over
// frames — see the slew in the ~vdef for why it exists.
var stickA = 0;

// SHARED ACROSS THE QUARTET. trainBass2 writes m.com.root; this voice
// reads it, so a harmony change turns every hue in the ensemble together.
var rootHue = { (m.com.root ? 0).linlin(-2, 3, -0.06, 0.06) };


m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;


// add another trigger that adds impulse to the synth, with offset freq's

SynthDef(\warmPadMove2, {
	|out=0, gate=1, freq=440, amp=0.1,atk=0.03, dec=0.2, sus=0.8, rel=1.0,filtMin=500, filtMax=5000, filtSpeed=0.5,
	detuneAmount = 1.001,chorusRate=0.5, chorusDepth=0.01,pan=0, spread=0.2, lfoFreq=1, excAttack=0.01, excRelease=0.1, trig=1|

    var sig, env, filt, chorus, numVoices=8, sub;
		var pulse = LFCub.ar(lfoFreq,pi,0.5,0.5);
		var exciter = EnvGen.kr(Env.perc(excAttack, excRelease), trig)+1;
		freq = freq.lag(3);


    // Main envelope
    env = EnvGen.kr(
        Env.adsr(atk, dec, sus, rel),
        gate,
        doneAction: 2
    );

    // Multiple slightly detuned oscillators for warmth
    sig = Array.fill(numVoices, { |i|
        var detune = i * detuneAmount;
        var oscillator = SinOsc.ar(freq * (1 + detune)) +
                        Saw.ar(freq * (1 + detune), pi/2 * i) * 0.3;
        Pan2.ar(oscillator, pan + detune)
    }).sum;


    // Chorus effect
    // Anti-aliased chorus using all-pass filter
    chorus = Array.fill(2, {
        var maxDelay = 0.05;
        var delayTime = SinOsc.kr(
            chorusRate + rand(0.1),
            rrand(0, 2pi)
        ).range(0, chorusDepth);

        AllpassC.ar(
            sig,
            maxDelay,
            delayTime + (chorusDepth * 0.1),
            0.1  // Shorter decay time for cleaner sound
        )
    });    // Final processing
	sub = LFTri.ar(freq * (3/2), pi, 3).tanh * 0.8;
	sig = Mix([sig, chorus.sum, sub]) / (numVoices + 3);

	    // Filter sweep
    filt = SinOsc.kr(filtSpeed.lag(1.0)).range(filtMin, filtMax);
    sig = RLPF.ar(sig, filt.lag(0.3), 0.5);

  // sig = sig * env * amp;

    // Output with stereo spread
    sig = Splay.ar(sig, spread);
		// sig = GVerb.ar(sig.tanh * 0.2,4,0.1);
	Out.ar(out, sig * Amplitude.kr(amp,0.1,0.5) * env * exciter);
}).add;


//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {|d|

	// visual : the tunnel itself.
	//
	// This voice is one long-lived pad, not a pattern, so it is the only
	// thing here that does not travel. That is the right job for a drone:
	// the other three are traffic, this is the structure they move
	// through. It draws the perspective lines running from the vanishing
	// point out to the edges — the walls, the thing that tells you there
	// IS a tunnel and where its centre is. Without it the other voices
	// are just marks flying outward; with it they are inside something.
	//
	// ONE held event, fired here, never re-laid. duration: inf means the
	// cull never expires it, so there is no pulse of its own to beat
	// against the music — the walls simply exist. It also means normTime
	// stays pinned at 0, so the core's start->end blending never advances:
	// every end* and *Env key would be inert, and they are left off rather
	// than written and quietly ignored. What you set below is what you
	// see, which is what makes it adjustable in one place.
	//
	// All the motion therefore has to come from this draw func reading
	// live state each frame, which is the metal1 \sheetFrame idiom.
	// Nothing is relayed: lfoFreq is recomputed from m.gyroYFiltered here,
	// so tilting sets how fast the shimmer travels round the tunnel. Every
	// other tunable is a ? fallback on \modulation, so the single event is
	// the one place to adjust the whole thing.
	//
	// clearEvents in personalityController removes it on unload — without
	// that a held event would outlive its vdef and degrade into a stray
	// fallback circle.
	//
	// Lineage: circuit / apparatus, atlas grammar G12 — the score as a
	// diagram of the space rather than of the sounds — crossed with the
	// radial G8 the other three fly along. Palette from atlas §0.5,
	// saturated primaries on black; this voice takes the coolest, darkest
	// end so it reads as architecture, never as an event.
	//
	//   gyro x/y/z -> the stick's orientation, same angles as threeDeeView
	//   accel      -> how far the whiskers reach out
	//   \modulation -> box proportions, camera, segment count, reach, alphas
	dev = d;

	// The airstick itself, wireframed and projected by hand.
	//
	// This is threeDeeView's cube, rebuilt so it can live on the visual
	// canvas: Canvas3D is a View class and cannot be used inside a draw
	// func, so the rotation and the perspective divide are done here. The
	// three Euler angles are lifted verbatim from threeDeeView so the
	// stick on the canvas reads at the same attitude as the one on the
	// device panel — same wraps, same negations, same X then Y then Z
	// order. Body proportions are its mScale(1.0, 0.4, 0.5), and the
	// camera is its distance 3.5 / perspective 0.75.
	//
	// dev is read here, not ~device — see the note at the top of the file.
	//
	// The whiskers are the part to play with. Every vertex can shoot a
	// line on outward from the body along its own direction, length from
	// \reach scaled by accel, so at rest you get a clean wireframe stick
	// and on movement it throws lines out into the tunnel. Set reach to 0
	// on the event for the stick alone. They are drawn as segmented
	// polylines rather than 2-point spans so \modulation can bend them
	// later — a 2-point path is all endpoints and never moves.

	~curveAbove = { |in, thresh = 0.2, outMin = 0.3, outMax = 1.0, curve = -4|
    if (in < thresh) { 0 } {
        in.lincurve(thresh, 1.0, outMin, outMax, curve)
    }
};
	~vdef.(\airstick, { |ev, c|
		var mod   = ev[\modulation] ? ();
		var g     = dev.sensors.gyroEvent;
		var rx    = (g.x + pi.half).wrap(-pi, pi);
		var ry    = (g.y).wrap(-pi.half, pi.half).neg;
		var rz    = pi + (g.z - pi.half).wrap(-pi, pi).neg;
		var bw    = (mod[\bw] ? 1.0);
		var bh    = (mod[\bh] ? 0.4);
		var bd    = (mod[\bd] ? 0.5);
		var dist  = mod[\dist] ? 3.5;
		var persp = mod[\persp] ? 0.75;
		var seg   = (mod[\seg] ? 8).max(2);
		var edgeA, rayA;
		var lit   = m.accelMassFiltered.linlin(0, mod[\litFull] ? 1.2, 0, 1);
		var rise  = mod[\rise] ? 0.15;
		var fall  = mod[\fall] ? 0.02;
		var reach = (mod[\reach] ? 0.0)
			* m.accelMassFiltered.linlin(0, 2.0, 0, 1);
		var rot, proj, verts, edges;

		// The alpha was a comparator — if(accel > 0.01, {1}, {0}) — which
		// can only ever be 1 or 0, so it could not do anything but snap.
		// Now it is a continuous 0..1 from accel, eased by a one-pole slew
		// run once per frame (the canvas animates at 60fps).
		//
		// The slew has to live here rather than in the model:
		// m.accelMassFiltered is deliberately fast (attack 0.99 / decay 0.8)
		// because it drives the synth, and those are model filter
		// coefficients — not ours to retune for a picture. stickA is a
		// genuine accumulator over frames, which cannot be recomputed from
		// m, and that is the one case a file-level var is justified.
		//
		// Asymmetric on purpose: rise 0.15 lights it in about 110 ms so it
		// still catches a gesture, fall 0.02 lets it die over about 800 ms
		// so it settles instead of flickering between gestures.
		stickA = stickA + ((lit - stickA) * if(lit > stickA, { rise }, { fall }));
		edgeA  = (mod[\edgeAlpha] ? 1.0) * stickA;
		rayA   = (mod[\rayAlpha] ? 0.45) * stickA;

		rot = { |v|
			var x = v[0], y = v[1], z = v[2], t;
			t = y; y = (t * cos(rx)) - (z * sin(rx)); z = (t * sin(rx)) + (z * cos(rx));
			t = x; x = (t * cos(ry)) + (z * sin(ry)); z = (t * sin(ry)).neg + (z * cos(ry));
			t = x; x = (t * cos(rz)) - (y * sin(rz)); y = (t * sin(rz)) + (y * cos(rz));
			[x, y, z]
		};
		proj = { |v|
			var k = persp / (dist - v[2]).max(0.05) * c[\size];
			c[\pos] + ((v[0] * k) @ (v[1] * k))
		};

		verts = [
			[-1,-1,-1], [1,-1,-1], [1,1,-1], [-1,1,-1],
			[-1,-1, 1], [1,-1, 1], [1,1, 1], [-1,1, 1]
		].collect({ |v| rot.([v[0] * bw, v[1] * bh, v[2] * bd]) });

		edges = [ [0,1],[1,2],[2,3],[3,0], [4,5],[5,6],[6,7],[7,4],
		          [0,4],[1,5],[2,6],[3,7] ];

		edges.do({ |e|
			var p0 = verts[e[0]];
			var p1 = verts[e[1]];
			c[\render].(
				Array.fill(seg, { |j| proj.(p0 + ((p1 - p0) * (j / (seg - 1)))) }),
				1, edgeA, false);
		});

		if(reach > 0.001, {
			verts.do({ |v|
				var far = v * (1 + reach);
				c[\render].(
					Array.fill(seg, { |j| proj.(v + ((far - v) * (j / (seg - 1)))) }),
					0.6, rayA, false);
			});
		});
		nil
	});

	// The hit, fired per strike from ~next. The stick's four LONG rails,
	// drawn again and extended along their own axis.
	//
	// The box's long axis is bw, so the four edges running along it are
	// [0,1] [2,3] [4,5] [6,7] — the rails, sitting at the four (±bh, ±bd)
	// corners. Each line here spans its whole rail, from -bw to +bw, so it
	// is COLLINEAR with the rail rather than crossing it.
	//
	// c[\size] is a RADIAL MULTIPLIER on how far each line sits off the
	// stick's long axis. At 1 the four lines are exactly the four rails.
	// At 2 they are twice as far out, so they move apart from each other
	// while staying parallel — the stick opens up. They keep the rail's
	// length throughout; only the separation grows.
	//
	// The angles come from \modulation, NOT from dev. \modulation is fixed
	// when the event fires, so each set of lines keeps the attitude it was
	// struck at and hangs there while the stick turns on underneath.
	// Reading the gyro live here — as \airstick does, correctly, because it
	// IS the stick — would drag every line in flight around with the wrist.
	// That is the whole difference between the two vdefs.
	//
	// \proj MUST match the stick event's \startSize or the lines will not
	// lie on the rails. That is the one coupling between the two events.
	//
	// Here c[\size] is not a projection scale, it is the REACH — how far
	// past the tip the line has got, in model units. So \startSize ->
	// \endSize is the extending, and the core drives it through the event's
	// -3 curve: out fast, then decelerate, because a hit has already
	// happened.
	//
	// The near clamp is 0.5 rather than the stick's 0.05: an extended rail
	// can pass the camera plane, and a tighter clamp would send it to a
	// huge coordinate as it crossed. At 0.5 it simply leaves the frame,
	// which is what shooting past you should look like.
	~vdef.(\hitRays, { |ev, c|
		var mod   = ev[\modulation] ? ();
		var rx    = mod[\rx] ? 0;
		var ry    = mod[\ry] ? 0;
		var rz    = mod[\rz] ? 0;
		var bw    = mod[\bw] ? 1.0;
		var bh    = mod[\bh] ? 0.4;
		var bd    = mod[\bd] ? 0.5;
		var dist  = mod[\dist] ? 3.5;
		var persp = mod[\persp] ? 0.75;
		var proj  = mod[\proj] ? 600;
		var seg   = (mod[\seg] ? 12).max(2);
		var spread = c[\size];
		var rot, project, corners;

		rot = { |v|
			var x = v[0], y = v[1], z = v[2], t;
			t = y; y = (t * cos(rx)) - (z * sin(rx)); z = (t * sin(rx)) + (z * cos(rx));
			t = x; x = (t * cos(ry)) + (z * sin(ry)); z = (t * sin(ry)).neg + (z * cos(ry));
			t = x; x = (t * cos(rz)) - (y * sin(rz)); y = (t * sin(rz)) + (y * cos(rz));
			[x, y, z]
		};
		project = { |v|
			var k = persp / (dist - v[2]).max(0.5) * proj;
			c[\pos] + ((v[0] * k) @ (v[1] * k))
		};

		// corners = [ [bh.neg, bd.neg], [bh, bd.neg],
		//             [bh.neg, bd],     [bh, bd] ];
		corners = [ [bh.neg, bd.neg]];

		corners.do({ |yz|
			var off = [yz[0] * spread, yz[1] * spread];
			var a = rot.([bw.neg, off[0], off[1]]);
			var b = rot.([bw,     off[0], off[1]]);
			c[\render].(
				Array.fill(seg, { |j| project.(a + ((b - a) * (j / (seg - 1)))) }),
				1, 1, false);
		});
		nil
	});

	// THE single event. Adjust the stick here and nowhere else.
	(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port,
		shape: \airstick,
		sx: -0.0, sy: 0, ex: 0, ey: 0,
		startSize: 1500,
		startWidth: 12.6,
		startColor: d.color,
		closed: false,
		duration: inf,
		modulation: (
			amp: 0,
			bw: 1.0, bh: 0.4, bd: 0.5,
			dist: 3.5, persp: 0.75,
			seg: 8,
			reach: 2.5,
			litFull: 1.2,     // accel that counts as fully lit
			rise: 0.15,       // ~110 ms to light
			fall: 0.02,       // ~800 ms to fade
			edgeAlpha: 1.0,
			rayAlpha: 0.45
		)
	).play;

	synth = Synth(\warmPadMove2, [
		\freq, note.midicps, 
		\amp, 0,
		\gate, 1,
		\atk, 1.02,
		\rel, 1.8,
		// \filtMin, 800,
		// \filtMax, 8000,
		\filtSpeed, 0.1,
		\chorusRate, 0.001,
		\chorusDepth, 0.0001,
		\detuneAmount, 0.0004
	]);
};


//------------------------------------------------------------
// triggers
//------------------------------------------------------------
~deinit = ~deinit <> {
	// Pdef(m.ptn).remove;
	// synth.free;
    synth.set(\gate, 0);
    bsynth.set(\gate, 0);

	synth.free;
	bsynth.free;

};

// example feeding the community
~onEvent = {|e|
	if(e.root != m.com.root,{
		// "key change".postln;
		synth.set(\freq, (note + e.root).midicps);
	});
	m.com.root = e.root;
	m.com.dur = e.dur;
};

//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);

	var dur = 0.5 * 2.pow(m.accelMassFiltered.linlin(0,3 * sens,0,2).floor).reciprocal;
	var a = m.accelMassFiltered.lincurve(0,1.5 * sens,0,1,-2);
	var filtSpeed = m.accelMassFiltered.lincurve(0,2.5 * sens,0.1,30,3);
	var lfoFreq = m.accelMassFiltered.lincurve(0,2.5,0.1,8,-1);
	var fmin = (m.gyroZFiltered.fold(-0.5,0.5) * 2).linexp(-1.0,1.0,200,500);
	var fmax = (m.gyroZFiltered.fold(-0.5,0.5) * 2).linexp(-1.0,1.0,200,8000);
	var notes = [-36,-12-5,-5,-2,7,10,12,14,16];
	var idx = m.gyroYFiltered.lincurve(-0.4,0.4,0.0,notes.size-1,-2).asInteger;
	var hue = (note + m.com.root+ notes[idx]).linlin(40, 66, 0, 1);

	if(a<0.03,{a=0});
	if(a>0.9,{a=0.9});


	// aa = a;

    synth.set(\freq, (note + m.com.root).midicps);
	synth.set(\amp, a * 1 * vol);
	synth.set(\filtSpeed, filtSpeed);
	synth.set(\lfoFreq, lfoFreq);
	synth.set(\filtMin, fmin);
	synth.set(\filtMax, fmax);


	if(d.sensors.accelEvent.x > (1.0 * sens), {

		if(TempoClock.beats > (lastTime + 0.055),{
			// same expression the bsynth below takes for its \amp, so the
			// ring is the strength of this hit rather than a guess at it.
			var chorus = m.accelMassFiltered.lincurve(0,2.5,0.02,0.2,-1);

			// The stick's attitude SAMPLED NOW, at the strike, and handed to
			// the visual on \modulation. \modulation is fixed at fire time,
			// so the lines keep the angle they were struck at while the
			// stick itself carries on turning underneath them. Reading the
			// gyro live inside the vdef instead would drag every line in
			// flight around with the wrist.
			var hg = d.sensors.gyroEvent;
			var hrx = (hg.x + pi.half).wrap(-pi, pi);
			var hry = (hg.y).wrap(-pi.half, pi.half).neg;
			var hrz = (hg.z - pi.half).wrap(-pi, pi).neg;

			lastTime = TempoClock.beats;

			bsynth = Synth(\warmPadMove2, [
				\freq, (note + m.com.root+ notes[idx]).midicps * 4, 
				\amp, m.accelMassFiltered.lincurve(0,2.5 * sens,0.003,0.1,1) * vol,
				\gate, 1,
				\atk, m.accelMassFiltered.lincurve(0,2.5 * sens,0.08,0.02,1),
				\rel, m.accelMassFiltered.lincurve(0.5,2.5 * sens,0.2,8.03,-1),
				\filtMin, 8000,
				\filtMax, 12000,
				\filtSpeed, 0.1 * chorus,
				\chorusRate, 0.1 * chorus,
				\chorusDepth, 0.01 * chorus,
				\detuneAmount, 0.0004 * chorus
			]);
			// mod 

			s.bind { bsynth.set(\gate, 0) };

			// visual : the hit. Four lines off the stick's four long rails.
			//
			// sx/sy MUST be 0 so this shares the stick's centre — the lines
			// are drawn in the stick's own model space, so any offset here
			// would start them somewhere the stick is not.
			//
			// startSize/endSize are a REACH in model units, not pixels: 0 is
			// flush against the rail and endSize is how far out the line has
			// grown. The -3 curve is the same one the ring used, and for the
			// same reason — everything else in the tunnel accelerates toward
			// you on +3 because it is approaching, but a hit has already
			// happened, so it goes out fast and decelerates.
			//
			//   accel -> how far the lines reach, and their weight
			(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port,
				shape: \hitRays,
				// MUST match the \airstick event's sx/sy above: these lines
				// are the stick's own rails, so if the stick is offset they
				// are offset with it, or they start where it is not.
				sx: -0.0, sy: 0, ex: -0.0, ey: 0,
				// A RADIAL MULTIPLIER, not a distance. 1 = exactly on the
				// rails, 2 = twice as far off the axis. So the lines start
				// as the stick's own edges and move apart from there.
				startSize: 1.0,
				endSize: m.accelMassFiltered.lincurve(0.0,2.5,1.0,4.0,-2),
				sizeEnv: Env([0, 1], [1], -3),
				startWidth: 5.5,
				endWidth: m.accelMassFiltered.lincurve(0.0,2.5,1,0,-2),
				widthEnv: Env([0, 1], [1], -3),
				startColor: Color.hsv(hue, 0.85, 1.0, 0.95),
				endColor: Color.hsv(hue, 0.60, 1.0, 0.0),
				closed: true,
				duration: m.accelMassFiltered.lincurve(0.5,2.5,0.2,8.03,-1),
				modulation: (
					amp: 0,
					rx: hrx, ry: hry, rz: hrz,
					bw: 2.0, bh: 0.4, bd: 0.5,
					dist: 3.5, persp: 0.75,
					proj: 500,   // = the \airstick event's startSize
					seg: 12
				)
			).play;

		},{
		});
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
	// [m.rrateMassFiltered];
	// [d.sensors.accelEvent.x.abs];
		// [m.gyroYFiltered.lincurve(-0.5,0.5,0.0,4.0,-2).asInteger / 4.0];
[(m.gyroZFiltered.fold(-0.5,0.5) * 2)];
	// [m.rrateMassFiltered, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

