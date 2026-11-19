var m = ~model;
var buffers;
var synth;
var bi = 0;
var armed = true;
var phase = 0;
var lastNow = 0;
var noteOn = { |amp = 0, rate = 1|
	synth !? { synth.set(\gate, 0) };
	synth = buffers !? { Synth(\nicPrrr2Loop, [\bufnum, buffers[bi], \amp, amp, \rate, rate, \gate, 1]) };
};

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.97;
m.accelMassFilteredDecay = 0.25;
m.rrateMassFilteredAttack = 0.8;
m.rrateMassFilteredDecay = 0.3;

//------------------------------------------------------------
SynthDef(\nicPrrr2Loop, { |out = 0, bufnum = 0, amp = 0, rate = 1, gate = 1,
	thresh = 0.3, slopeAbove = 0.1|
	var env = EnvGen.kr(Env.asr(0.3, 1, 0.6), gate, doneAction: Done.freeSelf);
	var sig = PlayBuf.ar(2, bufnum,
		rate.lag(1.3) * BufRateScale.kr(bufnum) * [1,1.008],
		1, 0, loop: 1);
	sig = LeakDC.ar(sig);
	sig = BLowShelf.ar(sig, 70, 0.97,11);

	sig = Compander.ar(sig, sig,
		thresh: thresh,
		slopeBelow: 1,
		slopeAbove: slopeAbove,
		clampTime: 0.01,
		relaxTime: 0.1
	);
	Out.ar(0, sig * env * amp.lag(0.12));
}).add;

//------------------------------------------------------------
~init = ~init <> { |d|

	var folder = PathName("~/Downloads/nicSamples/prrr");
	postf("loading samples : % \n", folder);

	buffers = folder.files.select({ |p| p.extension.toLower == "wav" }).collect({ |p|
		Buffer.read(s, p.fullPath)
	});

	~vdef.(\purrHair, { |ev, c|
		var mod = ev[\modulation] ? ();
		var modes = mod[\modes] ? 4;
		var depth = mod[\depth] ? 0.12;
		var vibHz = mod[\vibHz] ? 3;
		var restAlpha = mod[\restAlpha] ? 0.15;
		var spread = mod[\spread] ? 0.5;

		var drive = m.accelMassFiltered;
		var amp = drive.lincurve(0, 1.6, 0, 1, 2);
		var rate = drive.linlin(0, 1.6, 1, 1.22);
		var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
		var sound = drive.lincurve(0, 0.6 * d.params.sensitivity.linexp(0, 1, 4, 0.25), 0, 1, 2) * vol;

		var a = c[\posStart];
		var b = c[\posEnd];
		var now = c[\now];
		var n = ev[\numPoints] ? 24;
		var alpha = amp.linlin(0, 1, restAlpha, 1);

		var buf = buffers !? { buffers[bi] };
		var loop = if(buf.notNil and: { buf.numFrames.notNil }, { buf.duration }, { 1 });
		var dt = (now - lastNow).clip(0, 0.25);

		lastNow = now;
		phase = (phase + (rate * dt / loop.max(0.1))).wrap(0, 1);

		if(sound > -80.dbamp, {
			Pen.width = c[\width];
			(mod[\hairs] ? []).do { |h|
				var root = a.x.blend(b.x, h[0]) @ a.y;
				var len = (a.y - b.y) * (1 + (spread * (h[1] * 2 - 1))) * amp;
				var col = ev[\startColor].blend(ev[\endColor], h[2]);
				Pen.strokeColor = col.alpha_(col.alpha * alpha);
				Pen.moveTo(root);
				(1..(n - 1)).do { |i|
					var t = i / (n - 1);
					var x = 0;
					modes.do { |k|
						var kk = k + 2;
						x = x + (sin((kk * t * pi) + ((phase + h[0]) * 2pi * (k + 1))) * cos(2pi * vibHz * rate * (1 + (k * 0.3)) * (now + h[0])) / kk);
					};
					Pen.lineTo(root + ((len * depth * amp * x * t) @ (len * t).neg));
				};
				Pen.stroke;
			};
		});
		nil
	});

	(
		type: \customVisualEvent,
		amp: 0,
		dur: 0.01,
		viewID: d.port,
		shape: \purrHair,
		numPoints: 24,
		sx: -1, ex: 1,
		sy: 1, ey: 0.7,
		startWidth: 2,
		startColor: Color.white,
		endColor: Color.red,
		duration: inf,
		modulation: (
			hairs: Array.fill(40, { [1.0.rand, 1.0.rand, 1.0.rand] }),
			spread: 0.5,
			modes: 6,
			depth: 0.15,
			vibHz: 7,
			restAlpha: 0.5,
			amp: 0
		)
	).play;

	fork {
		s.sync;
		if(synth.isNil, { noteOn.() });
		postf("samples loaded : % \n", buffers.size);
	};
	m.com.bgColor = Color.black;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var old = buffers;
	buffers = nil;
	noteOn.();
	fork {
		1.0.wait;
		old.do({ |buf| buf.free });
	};
};

//------------------------------------------------------------
~next = { |d|

	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var sens = d.params.sensitivity.linexp(0, 1, 4, 0.25);
	var drive = m.accelMassFiltered;
	var amp = drive.lincurve(0, 0.6 * sens, 0, 1, 2);
	var rate = ((d.sensors.gyroEvent.y / pi).fold(-0.5,0.5) * 2.0).linlin(-1, 1, 0.5, 1.23);

	synth !? { synth.set(\amp, amp * vol, \rate, rate) };

	if(armed and: { buffers.notNil } and: { m.accelMass > (0.25 * sens) }, {
		armed = false;
		bi = (bi + 1) % buffers.size;
		noteOn.(amp * vol, 1);

		// (
		// 	type: \customVisualEvent,
		// 	amp: 0,
		// 	dur: 0.01,
		// 	viewID: d.port,
		// 	shape: \purrRing,
		// 	numPoints: 48,
		// 	fill: false,
		// 	sx: 0, sy: 0, ex: 0, ey: 0,
		// 	startSize: 64,
		// 	endSize: 100,
		// 	startWidth: 6,
		// 	endWidth: 10.4,
		// 	startColor: Color.black,
		// 	endColor: Color.black,
		// 	duration: 0.9,
		// 	modulation: (
		// 		modes: 24,
		// 		depth: 1.22,
		// 		vibHz: 7,
		// 		restAlpha: 0.5,
		// 		amp: 0
		// 	)
		// ).play;
	});

	if(m.accelMass < (0.15 * sens), { armed = true });
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
	// [m.accelMassFiltered.lincurve(0, 1.6, 0, 1, 2)];//amp
	// [m.accelMassFiltered.linlin(0, 1.6, 1, 1.22) - 1];//rate

	[m.accelMass * 0.5, m.accelMassFiltered * 0.5];
};
