var m = ~model;
var synth;
var srr = 0.5.rrand(1.7);

// State, not tunables : the flight cannot be recomputed from m alone because
// it is an orbit - where the bee is now depends on every step it has taken.
// beeTheta/beeMom are the standard map's own two variables, the same pair
// StandardL integrates inside \beeSynth1.
var beeTheta = 0.7;
var beeMom = 0.1;
var beePos = 0 @ 0;
var trail = [];

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\beeSynth1, { |out=0, rr=0.1, amp = 0.0, gate = 1, release = 2, af=264, bf=398|

	var ampa = Lag.ar(K2A.ar(rr), 0.7) * 2.0 * amp;

	var lrr = Lag.ar(K2A.ar(rr), 0.7);
	var pitch = LinLin.ar(lrr, 0.0, 1.0, af, bf);
	var ffrq = LinLin.ar(lrr, 0.0, 1.0, 811, 2398);
	var ramp = LinLin.ar(lrr, 0.0, 1.0, 5, 22);

	var sig = HPF.ar(
		LFSaw.ar(
			StandardL.ar(11, ramp) * 14 + pitch,
			1,
			0.9
		),
	ffrq)!2;

	sig = sig * EnvGen.kr(Env.adsr(0.1, 0.1, 7, release), gate: gate, doneAction: Done.freeSelf) * ampa.lagud(0.1,0.1);
	Out.ar(out, sig);

}).add;

~init = ~init <> {|d|

	//--------------------------------------------------------
	// visual : the flight path, drawn by the same chaos that makes the sound.
	//
	// \beeSynth1's character comes from StandardL.ar(11, ramp) - the Chirikov
	// standard map - perturbing the saw's frequency. This draw func iterates
	// that same map,
	//
	//     mom   = mom + (k * sin(theta))
	//     theta = theta + mom
	//
	// and flies the bee along theta. So k IS the synth's ramp, scaled to put
	// the map's chaos threshold (K = 0.97) in the middle of the gesture: at
	// rest k is 0.5 and the orbit is a regular curve, by full drive k is 2.4
	// and it diffuses. Hovering to frantic, from one number, shared with the
	// audio rather than imitating it.
	//
	// A draw func because the trail tapers - each band of it carries its own
	// alpha and width, which a single point list cannot express.
	//
	// Lineage: G2 trajectory - "leave decaying trails" - under traversal 4,
	// free ranging, which the atlas says to implement as an agent with
	// steering behaviour. That is literally a bee.
	//
	//   rr drive  -> how chaotic the flight is   (k, live from m)
	//   rr drive  -> how far it ranges           (startSize..endSize)
	//   rr drive  -> brightness of the trace     (alpha)
	//   momentum  -> how fast it darts           (step length)
	//
	// Held event : normTime is pinned at 0, so every start*->end* blend
	// resolves to start*. The draw func does the blending itself on drive,
	// exactly as metal1's \sheetFrame does on amp.
	~vdef.(\beeFlight, { |ev, c|
		var mod = ev[\modulation] ? ();
		var chaos = mod[\chaos] ? 0.05;
		var speed = mod[\speed] ? 2.2;
		var span = mod[\trail] ? 42;
		var bands = mod[\bands] ? 6;
		var steps = mod[\steps] ? 2;
		var body = mod[\body] ? 0.09;
		var fade = mod[\fade] ? -3;
		var headWob = mod[\headWob] ? 0.07;
		var headHarm = mod[\headHarm] ? 3;
		var headSpin = mod[\headSpin] ? 1.6;
		var headPts = mod[\headPts] ? 28;
		var rate = m.accelMassFiltered.lincurve(0.0, 2.5 * srr, 0.3, 1.2, 4 * srr);
		var rr = m.rrateMassFiltered.linlin(0, 1, 1, 2.1);
		var lrr = rate * rr;
		var k = lrr.linlin(0, 1, 5, 22) * chaos;
		var drive = lrr.linlin(0.3, 2.5, 0, 1).clip(0, 1);
		var amp = m.accelMassFiltered.linlin(0, 1, 0, 0.4);
		var vis = amp.lincurve(0, 0.4, 0, 1, fade).clip(0, 1);
		var terr = ev[\startSize].blend(ev[\endSize], drive);
		var head, ring;

		if(vis < 0.01, { trail = [] });

		steps.do {
			beeMom = (beeMom + (k * sin(beeTheta))).wrap(-pi, pi);
			beeTheta = (beeTheta + beeMom).wrap(-pi, pi);
			beePos = beePos + Polar(speed * (0.3 + beeMom.abs), beeTheta).asPoint;
			if(beePos.rho > terr, {
				beeTheta = (beeTheta + pi).wrap(-pi, pi);
				beePos = Polar(terr * 0.97, beePos.theta).asPoint;
			});
		};

		head = c[\pos] + beePos;
		trail = [head] ++ trail;
		if(trail.size > span, { trail = trail.keep(span) });

		if(trail.size > 2, {
			bands.do { |b|
				var lo = (b / bands * (trail.size - 1)).floor.asInteger;
				var hi = ((b + 1) / bands * (trail.size - 1)).ceil.asInteger;
				var seg = trail.copyRange(lo, hi.min(trail.size - 1));
				var taper = 1 - (b / bands);
				if(seg.size > 1, {
					c[\render].(seg, taper, taper * taper * vis, false);
				});
			};
		});

		ring = terr * body;
		c[\render].(
			Array.fill(headPts, { |i|
				var a = i / headPts * 2pi;
				head + Polar(
					ring * (1 + (headWob * sin((a * headHarm) + (c[\now] * headSpin)))),
					a
				).asPoint
			}),
			1.6, vis, true
		);
		nil
	});

	// Fired once and held. start* is the bee at rest, end* is the bee at full
	// drive; the draw func blends between them on the same rr the synth gets.
	(
		type: \customVisualEvent,
		amp: 0,
		dur: 0.01,
		viewID: d.port,
		shape: \beeFlight,
		fill: false,
		closed: false,
		sx: 0, sy: 0, ex: 0, ey: 0,
		startSize: 560,
		endSize: 1200,
		startWidth: 10.4,
		endWidth: 10.4,
		startColor: Color.hsv(0.11, 0.85, 1.0, 0.85),
		endColor: Color.hsv(0.11, 0.85, 1.0, 0.85),
		duration: inf,
		modulation: (
			chaos: 0.03,
			speed: 8.8,
			trail: 42,
			bands: 6,
			steps: 2,
			body: 0.09,
			fade: -3,
			headWob: 0.07,
			headHarm: 3,
			headSpin: 1.6,
			headPts: 28,
			amp: 0
		)
	).play;

	synth = Synth(\beeSynth1, [\af, 130.rrand(290), \bf, 250.rrand(340)]);
};

~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------`------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.accelMassFiltered.linlin(0, 2 * sens,0,0.4);
	var rate = m.accelMassFiltered.lincurve(0.0,2.5 * srr,0.3,4.2,4 * srr);
	var rr = m.rrateMassFiltered.linlin(0, 2 * sens,1,2.1);
	synth.set(\amp, amp * 0.05 * vol);
	synth.set(\rr, rate * rr);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [m.rrateMass, m.rrateMassFiltered];
	[m.accelMass, m.accelMassFiltered];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};

