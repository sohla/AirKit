var m = ~model;
var synth;
var buffer;
var notes = [-12,-8,-3,1,0];
var note = 0;
var trig = false;
var warpVoices = 8;
var warpWindow = 0.3;
var warpDiv = 10;
var warpSpeed = 0.01;
var bornAt;

m.accelMassFilteredAttack = 0.98;
m.accelMassFilteredDecay = 0.3;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\pullstretchMonoQN, {|out, amp = 1, buffer = 0, envbuf = -1, pch = 1, div=1, speed = 0.01, splay = 0.3,pan=0, gate=1, delta=0|
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1).range(0.0,0.99);
	var sp = Splay.arFill(8,
		{ |i| Warp1.ar(1, buffer, lfo.linlin(0,1,0.01,0.99), pch * (1 / ((i*delta)+1)) ,splay, envbuf, 8, 0.1 * (i+1), 4)  },
			1,
			1,
			0
	) ;
	var env = EnvGen.ar(Env.adsr(0.4,0.1,0.9,2.0), gate, doneAction:2);
	var mas = HPF.ar(sp,45) * amp.lag(0.2);
	var sig = Compander.ar(mas, mas,
			thresh: -32.dbamp,
			slopeBelow: 1,
			slopeAbove: 0.5,
			clampTime:  0.02,
			relaxTime:  0.01
	);

	sig = Pan2.ar(sig,pan) * env;
	Out.ar(out, (0)!0 ++ sig);
}).add;

//------------------------------------------------------------
~init = ~init <> { |d|
	var path = PathName("~/Downloads/nicSamples/4_Dreams/2.wav");
	var pointerAt = { |now|
		var len = if(buffer.notNil and: { buffer.numFrames.notNil }, { buffer.duration }, { 10 });
		bornAt = bornAt ? now;
		((now - bornAt) * warpSpeed * warpDiv / len.max(0.1)).frac.linlin(0, 1, 0.01, 0.99)
	};
	var drive = {
		var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
		var amp = m.rrateMassFiltered.lincurve(0, 1 * sens, 0.0, 1, 1);
		if(amp < 0.002, { 0 }, { amp.clip(0, 1) })
	};

	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMonoQN,[\buffer,buf, \amp,0.4, \div, 10]);
	});

	~vdef.(\subFan, { |ev, c|
		var mod = ev[\modulation] ? ();
		var n = ev[\numPoints] ? 24;
		var zoom = mod[\zoom] ? 6;
		var jitterPx = mod[\jitterPx] ? 18;
		var shimmer = mod[\shimmer] ? 7;
		var octavePx = c[\size];
		var a = c[\posStart];
		var b = c[\posEnd];
		var now = c[\now];
		var len = if(buffer.notNil and: { buffer.numFrames.notNil }, { buffer.duration }, { 10 });
		var head = a.blend(b, pointerAt.(now));
		var amp = drive.();
		var delta = m.gyroYFiltered.linlin(-1,1,10,-10).lcurve;

		if(amp > 0, {
			warpVoices.do { |i|
				var ratio = 1 / ((i * delta) + 1);
				var reach = (b.x - a.x) * warpWindow * ratio / len.max(0.1) * zoom;
				var drop = ratio.log2.neg * octavePx;
				var rand = 0.1 * (i + 1);
				c[\render].(
					Array.fill(n, { |j|
						var t = j / (n - 1);
						var wob = sin((j * 12.9898) + (now * shimmer * (i + 1))) * jitterPx * rand * t;
						(head.x - (reach * t)) @ (head.y + drop + wob)
					}),
					1 + ((1 - ratio) * 2), amp, false
				);
			};
		});
		nil
	});

	~vdef.(\readHead, { |ev, c|
		var mod = ev[\modulation] ? ();
		var rest = mod[\restAlpha] ? 0.15;
		var a = c[\posStart];
		var b = c[\posEnd];
		var reach = c[\size];
		var amp = drive.();
		var x = a.blend(b, pointerAt.(c[\now])).x;
		c[\render].([ x @ (a.y - reach), x @ (a.y + reach) ], 1, rest + ((1 - rest) * amp), false);
		c[\draw].(\circle, (pos: x @ a.y, size: 3 + (amp * (mod[\beadPx] ? 12))), 1, rest + amp);
		nil
	});

	(
		type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port,
		shape: \subFan,
		sx: -1, ex: 1, sy: -0.6, ey: -0.6,
		startSize: 150, endSize: 150,
		startWidth: 2.5, endWidth: 2.5,
		startColor: Color.new(0.21, 0.95, 0.9, 0.85),
		endColor: Color.new(0.21, 0.95, 0.9, 0.85),
		numPoints: 24, fill: false, closed: false,
		duration: inf,
		modulation: (amp: 0, zoom: 6, jitterPx: 18, shimmer: 7)
	).play;

	(
		type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port,
		shape: \readHead,
		sx: -1, ex: 1, sy: -0.6, ey: -0.6,
		startSize: 2000, endSize: 2000,
		startWidth: 1, endWidth: 1,
		startColor: Color.new(0.92, 0.97, 1.0, 0.7),
		endColor: Color.new(0.92, 0.97, 1.0, 0.7),
		numPoints: 24, fill: false, closed: false,
		duration: inf,
		modulation: (amp: 0, restAlpha: 0.15, beadPx: 12)
	).play;
};

~deinit = ~deinit <> {
	synth.onFree({
		postf("buffer dealloc [%] \n", buffer);
		buffer.free;
	});	
	synth.set(\gate, 0);
};


//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var delta = m.gyroYFiltered.linlin(-1,1,10,-10).lcurve;
	var amp = m.rrateMassFiltered.lincurve(0, 1 * sens,0.0,1,1);
	var speed= m.gyroXFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0.1,0.001,-1);


	// if(amp < 0.01, {
	// 	amp = 0;
	// });

	if(amp<0.002,{
		amp=0;
		// synth.set(\lag,0.8);
		if(trig, {
				trig = false;
		});
	},{
		if(trig.not, {
			trig = true;
			// notes = notes.rotate(-1);
			// note = notes[0];
		});
		synth.set(\pch, note.midiratio);
		// synth.set(\lag,0.01);
	});

	// synth.set(\speed, speed);
	synth.set(\amp, amp * 1 * vol);
	synth.set(\delta, delta);
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// [m.accelMass, m.accelMassFiltered];

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];


	// [m.gyroXFiltered.fold(-0.5,0.5)];
	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [m.gyroYFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,1,-1)];
	[m.gyroXFiltered.fold(-0.5,0.5).linlin(-0.5,0.5,-10,10).lcurve];
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
