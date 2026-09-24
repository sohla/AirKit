var m = ~model;
var synth;

// The only file-level state : accumulators the draw func carries between
// frames. Everything the band needs to KNOW it reads from m, the same model
// ~next reads - no values are relayed through vars.
var sincePluck = 0;
var lastNow = 0;

m.rrateMassFilteredAttack = 0.8;
m.accelMassFilteredAttack = 0.8;
m.rrateMassFilteredDecay = 0.6;
m.accelMassFilteredDecay = 0.04;


//------------------------------------------------------------
SynthDef(\pluck1, { |out=0, amp=0, pch=30, frq=30, gate=0 |
	var env = EnvGen.ar(Env.asr(0.1,1.0,3.3), gate, doneAction:2);
	var sig = Impulse.ar(pch.linlin(30,300,1,30));
	var dly = Decay.ar(sig, 0.01, BrownNoise.ar(0.1));
	var plk = Pluck.ar(WhiteNoise.ar, sig, frq.reciprocal, frq.reciprocal, 8, 0.9, 0.7);
	var ton = SinOsc.ar([1,1.03] * pch,0,((plk*1)+(dly*0.1))*2);
	Out.ar(out, ton+(dly*0.01) * amp.lag(0.2) * env);
}).add;


//------------------------------------------------------------
~init = ~init <> {|d|

	//------------------------------------------------------------
	// visual : the rubber band. One held event, always on screen, at rest
	// when silent and struck whenever the synth's Impulse fires.
	//
	// The band spans its two ANCHORS - \sx \sy to \ex \ey - and is displaced
	// perpendicular to that span, so moving it is a matter of moving the
	// anchors. \startSize says how far a pluck throws it. Size is how big,
	// never where.
	//
	// Lineage: Lucier's Music on a Long Thin Wire and Fullman's Long String
	// Instrument - the apparatus is the piece, and the physics performs it.
	// Atlas grammar G2.
	//
	// The draw func reads m directly and recomputes pch and frq with the same
	// expressions ~next hands the synth, so the two cannot drift apart and
	// nothing has to be relayed through a var.
	//
	//   pch  -> how often it is plucked      (= the Impulse.ar rate)
	//   pch  -> how many modes ring          (modeMin..modeMax)
	//   frq  -> how fast it vibrates         (scaled for 60fps)
	//   size -> how far a pluck throws it    (\startSize)
	//
	// A points func : a band is one polyline, so the core draws it and it
	// keeps \modulation, \closed and \fill.
	~vdef.(\rubberBand, { |ev, c|
		var n = ev[\numPoints] ? 64;
		var mod = ev[\modulation] ? ();
		var decay = mod[\decay] ? 4;
		var vibScale = mod[\vibScale] ? 0.05;
		var modeMin = mod[\modeMin] ? 1;
		var modeMax = mod[\modeMax] ? 10;
		var pch = 40 + (m.accelMass * 150);
		var frq = 110 + (m.accelMassFiltered * 100);
		var pluckRate = pch.linlin(30, 300, 1, 30);
		var modes = pch.linlin(40, 190, modeMin, modeMax).round.asInteger.clip(1, 16);
		var gate = m.accelMassFiltered * 1.3;
		var a = c[\posStart];
		var b = c[\posEnd];
		var normal = Polar(1, (b - a).theta + 0.5pi).asPoint;
		var throwPx = c[\size];
		var now = c[\now];
		var vibHz = frq * vibScale;
		var dt = (now - lastNow).clip(0, 0.25);
		var env;

		lastNow = now;
		sincePluck = sincePluck + dt;
		if(sincePluck >= pluckRate.max(0.01).reciprocal, { sincePluck = 0 });
		env = exp(sincePluck.neg * decay) * gate * throwPx;

		Array.fill(n, { |i|
			var t = i / (n - 1);
			var y = 0;
			modes.do { |k|
				var kk = k + 1;
				y = y + (sin(kk * pi * t) * cos(2pi * kk * vibHz * now) / kk);
			};
			a.blend(b, t) + (normal * y * env)
		})
	});

	// Fired once and held. The anchors are normalised, so the band fits
	// whatever canvas it lands on - overlay, HDMI or a grid column.
	(
		type: \customVisualEvent,
		amp: 0,
		dur: 0.01,
		viewID: d.port,
		shape: \rubberBand,
		numPoints: 14,
		fill: false,
		closed: false,
		sx: -0.85, sy: 0,
		ex: 0.85, ey: 0,
		startSize: 50,
		endSize: 50,
		startWidth: 10,
		endWidth: 1,
		startColor: Color.green.alpha_(0.8),
		endColor: d.color,
		duration: inf,
		modulation: (
			decay: 0.03,
			vibScale: 0.02,
			modeMin: 1,
			modeMax: 10,
			amp: 0
		)
	).play;

	synth = Synth(\pluck1, [\frq, 140.rrand(80), \gate, 1]);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.set(\gate,0);
};

//------------------------------------------------------------
~next = {|d|

	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var sens = d.params.sensitivity;
	var amp = m.accelMassFiltered.linlin(0,2.5 * sens,0.07,1);

	var pch = 40 + ((m.accelMass * 150) * sens.linlin(0.0,1.0,1,0.0));
	var frq= 110 + ((m.accelMassFiltered * 100) * sens.linlin(0.0,1.0,1,0.0));
	synth.set(\pch,pch);
	synth.set(\frq,frq);

	if(m.accelMass < 0.1,{
		synth.set(\amp,0);
	},{
		synth.set(\amp,amp * vol);
	});

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	[m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x * d.sensors.gyroEvent.y * d.sensors.gyroEvent.z * 0.1];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
