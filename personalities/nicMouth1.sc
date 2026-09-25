var m = ~model;
var synth;
var buffer;
var mouthVoices = 3;
var squeezeAt = 0.4;
var squeezeSlope = 0.1;
var chew = 0;
var lastNow;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.2;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\nicMouthGrain, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=5.2, gate=1,cutoff=20000, rq=1, rezf=200|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction:2);
	var sig = Splay.arFill(3,{|i|
			Warp1.ar(2, bufnum, start, rate / (i+1) , 0.3, -1, 3, windowRandRatio:0.3) * (0.2 + (i * 0.4))
	},1,1,0);
  	sig = Compander.ar(sig, sig,
        thresh: 0.4,
        slopeBelow: 1,
        slopeAbove: 0.1,
        clampTime:  0.01,
        relaxTime:  0.01
    );
	sig = sig[0] * env * amp;
    Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> { |d|
	var path = PathName("~/Downloads/nicSamples/nic_disgusting.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\nicMouthGrain,[\bufnum,buf, \rate, 1, \gate, 1 ]);
	});

	~vdef.(\mouth, { |ev, c|
		var mod = ev[\modulation] ? ();
		var n = ev[\numPoints] ? 40;
		var peakWidth = mod[\peakWidth] ? 0.18;
		var lipPx = mod[\lipPx] ? 14;
		var ripples = mod[\ripples] ? 3;
		var chewHz = mod[\chewHz] ? 1.5;
		var rest = mod[\restAlpha] ? 0.15;
		var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
		var amp = m.accelMassFiltered.linlin(0,2.3 * sens,0.00001,1);
		var rate = m.gyroYFiltered.lincurve(-1.0,1.0,0.1,2.0,0);
		var start = m.gyroZFiltered.lincurve(-1.0,1.0,0.0,1.0,0);
		var a = c[\posStart];
		var b = c[\posEnd];
		var now = c[\now];
		var dt = (now - (lastNow ? now)).clip(0, 0.25);
		var open;

		lastNow = now;
		chew = chew + (dt * rate * chewHz);
		if(amp < 0.01, { amp = 0 });
		open = if(amp > squeezeAt, { squeezeAt + ((amp - squeezeAt) * squeezeSlope) }, { amp });
		open = open / (squeezeAt + ((1 - squeezeAt) * squeezeSlope));

		mouthVoices.do { |i|
			var gain = 0.2 + (i * 0.4);
			var gap = open * gain * c[\size];
			var lip = { |t, side|
				var shape = sin(pi * t).pow(0.7) * (0.35 + (0.65 * exp(((t - start) / peakWidth).squared.neg)));
				var wob = sin(2pi * ((t * ripples) - (chew / (i + 1)))) * lipPx * (0.2 + open) * sin(pi * t);
				a.blend(b, t) + (0 @ ((gap * shape * side) + wob))
			};
			c[\render].(
				Array.fill(n, { |j| lip.(j / (n - 1), -1) })
				++ Array.fill(n, { |j| lip.(1 - (j / (n - 1)), 1) }),
				0.5 + gain, rest + ((1 - rest) * gain * open.sqrt), true
			);
		};
		nil
	});

	(
		type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port,
		shape: \mouth,
		sx: -1, ex: 1, sy: 0.1, ey: 0.1,
		startSize: 420, endSize: 420,
		startWidth: 3, endWidth: 3,
		startColor: Color.new(1.0, 0.42, 0.68, 0.9),
		endColor: Color.new(1.0, 0.42, 0.68, 0.9),
		numPoints: 40, fill: false, closed: true,
		duration: inf,
		modulation: (amp: 0, peakWidth: 0.18, lipPx: 14, ripples: 3, chewHz: 1.5, restAlpha: 0.15)
	).play;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.onFree({
		postf("buffer dealloc [%] \n", buffer);
		buffer.free;
	});	
	synth.set(\gate, 0);
};
//------------------------------------------------------------
~next = {|d|
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var amp = m.accelMassFiltered.linlin(0,2.3 * sens,0.00001,1);
	var rate =  m.gyroYFiltered.lincurve(-1.0,1.0,0.1,2.0,0);
	var start = m.gyroZFiltered.lincurve(-1.0,1.0,0.0,1.0,0);

	if(amp < 0.01, {amp = 0});

	synth.set(\start, start);
	synth.set(\rate, rate);
	synth.set(\amp, amp * 2 * vol);

};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	[d.sensors.gyroEvent.x/pi];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};
