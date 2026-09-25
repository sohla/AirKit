var m = ~model;
var synth;
var buffer;
var lastTime = 0;
var roots = [0,4,-2,2];
var ringVoices = 5;
var ringSpread = 0.125;
var subHz = 66;
var ampSmooth = 0;
var lastNow = 0;


m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.07;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\bufGrain, {|bufnum=0, out=0, amp=0.0, rate=1, start=0, pan=0, freq=440,
    attack=0.3, decay=0.1, sustain=0.8, release=5.2, gate=1, rezf=500|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction:2);
	var sub = LFTri.ar(66*rate*2,0,0.1).tanh;
	var sig = Splay.arFill(5,{|i|
		Ringz.ar(
			Warp1.ar(2, bufnum, start, rate * (i+1) , 0.3, windowRandRatio:0.3),
			rezf.lag(1) + (i * (rezf.lag(1) * 0.125)),
			0.13,
			0.05)
	},1,1,0);
	sig = sig.tanh + sub;
	sig = Greyhole.ar(sig[0], 0.3,0.3);
	sig = sig * env * amp.lag(0.4);
    Out.ar(out, ((0)!0 ++ sig));
}).add;

//------------------------------------------------------------
~init = ~init <> {|d|
	var path = PathName("~/Downloads/melSamples/mel_sing_dry-005.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\bufGrain,[\bufnum,buf, \rate, -8.midiratio, \gate, 1 ]);
	});

	~vdef.(\ringzBand, { |ev, c|
		var mod = ev[\modulation] ? ();
		var voice = mod[\voice] ? 0;
		var attackLag = mod[\attackLag] ? 0.4;
		var tailLag = mod[\tailLag] ? 2;
		var lowHz = mod[\lowHz] ? 150;
		var highHz = mod[\highHz] ? 1600;
		var ripple = mod[\ripple] ? 70;
		var reach = mod[\reach] ? 0.14;
		var waves = mod[\waves] ? 0.02;
		var drift = mod[\drift] ? 1.5;
		var ghost = mod[\ghost] ? 0.05;
		var n = ev[\numPoints] ? 96;
		var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
		var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
		var amp = m.accelMassFiltered.linlin(0, 4 * sens, 0, 1);
		var start = m.gyroYFiltered.lincurve(-1.0, 1.0, 0.0, 1.0, 0);
		var rezf = (d.sensors.gyroEvent.y / pi).lincurve(-0.5, 0.5, 200, 1000, 0);
		var hz = rezf * (1 + (voice * ringSpread));
		var rate = 0.5 * roots[0].midiratio * (voice + 1);
		var lift = hz.explin(lowHz, highHz, 1, -1) * c[\size];
		var cycles = hz * waves;
		var a = c[\posStart];
		var b = c[\posEnd];
		var now = c[\now];
		var dt = (now - lastNow).clip(0, 0.25);
		var target, pts;

		if(amp < 0.001, { amp = 0 });
		target = amp * vol;
		if(dt > 0, {
			lastNow = now;
			ampSmooth = ampSmooth + ((target - ampSmooth) * (1 - exp(dt.neg / if(target > ampSmooth, attackLag, tailLag))));
		});

		pts = Array.fill(n, { |i|
			var t = i / (n - 1);
			var bell = exp(((t - start) / reach).squared.neg);
			var y = sin((t * cycles * 2pi) - (now * rate * drift * 2pi)) * bell * ripple * ampSmooth;
			a.blend(b, t) + (0 @ (lift + y))
		});
		c[\render].(pts, 0.2 + ampSmooth, ghost + ((1 - ghost) * ampSmooth), false);
		nil
	});

	~vdef.(\subTri, { |ev, c|
		var mod = ev[\modulation] ? ();
		var waves = mod[\waves] ? 0.06;
		var drift = mod[\drift] ? 0.02;
		var n = ev[\numPoints] ? 96;
		var rate = 0.5 * roots[0].midiratio;
		var cycles = subHz * rate * 2 * waves;
		var a = c[\posStart];
		var b = c[\posEnd];
		var pts = Array.fill(n, { |i|
			var t = i / (n - 1);
			var ph = ((t * cycles) - (c[\now] * subHz * rate * 2 * drift)).frac;
			var tri = 1 - (4 * (ph - 0.5).abs);
			a.blend(b, t) + (0 @ (tri * c[\size] * ampSmooth))
		});
		c[\render].(pts, 0.2 + ampSmooth, ampSmooth, false);
		nil
	});

	~vdef.(\grainCursor, { |ev, c|
		var start = m.gyroYFiltered.lincurve(-1.0, 1.0, 0.0, 1.0, 0);
		var a = c[\posStart];
		var b = c[\posEnd];
		var x = a.x.blend(b.x, start);
		c[\render].([ x @ a.y, x @ b.y ], 1, 0.15 + (0.85 * ampSmooth), false);
		nil
	});

	ringVoices.do { |i|
		(
			type: \customVisualEvent,
			amp: 0,
			dur: 0.01,
			viewID: d.port,
			shape: \ringzBand,
			duration: inf,
			numPoints: 110,
			fill: false,
			closed: false,
			sx: -1, sy: 0, ex: 1, ey: 0,
			startSize: 230,
			endSize: 230,
			startWidth: 9 - i,
			endWidth: 9 - i,
			startColor: Color(0.45, 1.0, 0.78 - (i * 0.06), 0.9),
			endColor: Color(0.45, 1.0, 0.78 - (i * 0.06), 0.9),
			modulation: (
				voice: i,
				attackLag: 0.4,
				tailLag: 2,
				lowHz: 150,
				highHz: 1600,
				ripple: 70,
				reach: 0.14,
				waves: 0.02,
				drift: 1.5,
				ghost: 0.05,
				amp: 0
			)
		).play;
	};

	(
		type: \customVisualEvent,
		amp: 0,
		dur: 0.01,
		viewID: d.port,
		shape: \subTri,
		duration: inf,
		numPoints: 96,
		fill: false,
		closed: false,
		sx: -1, sy: 0.95, ex: 1, ey: 0.95,
		startSize: -60,
		endSize: -60,
		startWidth: 14,
		endWidth: 14,
		startColor: Color(0.2, 0.85, 0.7, 0.8),
		endColor: Color(0.2, 0.85, 0.7, 0.8),
		modulation: (waves: 0.06, drift: 0.02, amp: 0)
	).play;

	(
		type: \customVisualEvent,
		amp: 0,
		dur: 0.01,
		viewID: d.port,
		shape: \grainCursor,
		duration: inf,
		fill: false,
		closed: false,
		sx: -1, sy: -1, ex: 1, ey: 1,
		startWidth: 3,
		endWidth: 3,
		startColor: Color(1.0, 0.45, 0.75, 0.9),
		endColor: Color(1.0, 0.45, 0.75, 0.9)
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
	var amp = m.accelMassFiltered.linlin(0, 4 * sens,0.00001,1);
	var start = m.gyroYFiltered.lincurve(-1.0,1.0,0.0,1.0,0);
	// var rezf = m.gyroZFiltered.lincurve(-1.0,1.0,100,1200,0);
	//(d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2
	var rezf = ((d.sensors.gyroEvent.y / pi)).lincurve(-0.5,0.5,200,1000,0);

	if(amp < 0.001, {amp = 0});

	if(m.accelMassFiltered<0.2,{
		if(TempoClock.beats > (lastTime + 0.3),{
			roots = roots.rotate(-1);
			// roots[0].postln;
		});
			lastTime = TempoClock.beats;
	});

	synth.set(\rezf, rezf);
	synth.set(\start, start);
	synth.set(\amp, amp * 0.4 * vol);
	synth.set(\rate, 0.5 * ((roots[0]).midiratio));

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
};
