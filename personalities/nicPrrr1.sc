var m = ~model;
var buffers;
var synth;
var bi = 0;
var armed = true;
var phase = 0;
var lastNow = 0;
var noteOn = { |amp = 0, rate = 1|
	synth !? { synth.set(\gate, 0) };
	synth = buffers !? { Synth(\nicPrrrLoop, [\bufnum, buffers[bi], \amp, amp, \rate, rate, \gate, 1]) };
};

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.97;
m.accelMassFilteredDecay = 0.25;
m.rrateMassFilteredAttack = 0.8;
m.rrateMassFilteredDecay = 0.3;

//------------------------------------------------------------
SynthDef(\nicPrrrLoop, { |out = 0, bufnum = 0, amp = 0, rate = 1, gate = 1|
	var env = EnvGen.kr(Env.asr(0.3, 1, 0.6), gate, doneAction: Done.freeSelf);
	var sig = PlayBuf.ar(2, bufnum,
		rate.lag(0.25) * BufRateScale.kr(bufnum),
		1, 0, loop: 1);
	sig = LeakDC.ar(sig);
	Out.ar(out, sig * env * amp.lag(0.12));
}).add;

//------------------------------------------------------------
~init = ~init <> { |d|

	var folder = PathName("~/Downloads/nicSamples/prrr");
	postf("loading samples : % \n", folder);

	buffers = folder.files.select({ |p| p.extension.toLower == "wav" }).collect({ |p|
		Buffer.read(s, p.fullPath)
	});

	~vdef.(\prrrRing, { |ev, c|
		var mod = ev[\modulation] ? ();
		var swarm = mod[\swarm] ? 12;
		var spread = mod[\spread] ? 0.35;
		var dotMin = mod[\dotMin] ? 3;
		var dotMax = mod[\dotMax] ? 12;
		var tailMin = mod[\tailMin] ? 0.04;
		var tailMax = mod[\tailMax] ? 0.45;
		var wobble = mod[\wobble] ? 0.13;
		var wingHz = mod[\wingHz] ? 9;
		var ringAlpha = mod[\ringAlpha] ? 0.3;

		var drive = m.accelMassFiltered;
		var amp = drive.lincurve(0, 1.6, 0, 1, 2);
		var rate = drive.linlin(0, 1.6, 1, 1.22);

		var mid = c[\pos];
		var ring = c[\size];
		var now = c[\now];
		var n = ev[\numPoints] ? 48;

		var buf = buffers !? { buffers[bi] };
		var loop = if(buf.notNil and: { buf.numFrames.notNil }, { buf.duration }, { 1 });
		var dt = (now - lastNow).clip(0, 0.25);
		var head, tail, flies;

		lastNow = now;
		phase = (phase + (rate * dt / loop.max(0.1))).wrap(0, 1);

		head = (phase * 2pi) - 0.5pi;
		tail = amp.linlin(0, 1, tailMin, tailMax) * 2pi;

		// c[\draw].(\circle, (pos: mid, size: ring), 0.25, ringAlpha);

		// c[\render].(
		// 	Array.fill(n, { |i|
		// 		mid + Polar(ring, head - (tail * (1 - (i / (n - 1))))).asPoint
		// 	}),
		// 	1, amp.linlin(0, 1, 0.12, 1), false
		// );

		flies = amp.linlin(0, 1, 1, swarm).round.asInteger.max(1);
		flies.do { |i|
			var beat = sin((now * wingHz * rate * (1 + (i * 0.11))) + (i * 1.7));
			var a = head - (spread * beat.abs * ((i + 1) / flies));
			var r = ring * (1 + (wobble * beat * ((i % 3) - 1)));
			var dot = dotMin.blend(dotMax, amp) * (1 - (i / (flies + 1)));
			c[\draw].(\circle, (pos: mid + Polar(r, a).asPoint, size: dot), 1, amp);
		};

		nil
	});

	(
		type: \customVisualEvent,
		amp: 0,
		dur: 0.01,
		viewID: d.port,
		shape: \prrrRing,
		numPoints: 48,
		fill: false,
		closed: false,
		sx: 0, sy: 0, ex: 0, ey: 0,
		startSize: 240,
		endSize: 240,
		startWidth: 3,
		endWidth: 3,
		startColor: Color.new(0.486, 1.0, 0.627, 0.85),
		endColor: Color.new(0.486, 1.0, 0.627, 0.0),
		duration: inf,
		modulation: (
			swarm: 12,
			spread: 0.35,
			dotMin: 3,
			dotMax: 12,
			tailMin: 0.04,
			tailMax: 0.45,
			wobble: 0.13,
			wingHz: 9,
			ringAlpha: 0.3,
			amp: 0
		)
	).play;

	fork {
		s.sync;
		if(synth.isNil, { noteOn.() });
		postf("samples loaded : % \n", buffers.size);
	};
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
	var rate = drive.linlin(0, 0.6 * sens, 1, 1.22);

	synth !? { synth.set(\amp, amp * vol, \rate, rate) };

	if(armed and: { buffers.notNil } and: { m.accelMass > (0.55 * sens) }, {
		armed = false;
		bi = (bi + 1) % buffers.size;
		noteOn.(amp * vol, 1);

		(
			type: \customVisualEvent,
			amp: 0,
			dur: 0.01,
			viewID: d.port,
			shape: \circle,
			numPoints: 48,
			fill: false,
			sx: 0, sy: 0, ex: 0, ey: 0,
			startSize: 24,
			endSize: 320,
			startWidth: 6,
			endWidth: 0.4,
			startColor: Color.new(0.486, 1.0, 0.627, 0.9),
			endColor: Color.new(0.486, 1.0, 0.627, 0.0),
			duration: 0.9,
			modulation: (amp: 0)
		).play;
	});

	if(m.accelMass < (0.25 * sens), { armed = true });
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
