var m = ~model;
var synth;
var buffer;
var lastTime = 0;
var notes = [0,-5]-1;
var noteHold = 7;
var filterRq = 1;
var ampSmooth = 0;
var lastNow = 0;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.07;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\bufGrainM, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=5.2, gate=1,cutoff=20000, rq=1, rezf=200|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction:2);
	var sub = LFTri.ar(66*rate*2,0,0.3).tanh;
	var sig = Splay.arFill(2,{|i|
		Warp1.ar(2, bufnum, start , lr * 2 , 0.3, windowRandRatio:0.3)},
	1,1,0);
    sig = RLPF.ar(sig, cutoff, rq);
		// sig = Resonz.ar(sig, rezf.lag(0.4), 0.05, 5)* amp.lag(0.9);
		// sig = AllpassN.ar(sig, 0.1, [0.09, 0.08], 8);
		// sig = JPverb.ar(sig,1, modDepth: 0.1, modFreq: 4.0, low: 1.0);
	sig = sig * env * amp;
    Out.ar(out, ((0)!0 ++ sig));
}).add;

//------------------------------------------------------------
~init = ~init <> {|d|
	// var path = PathName("~/Downloads/yourDNASamples/brenton/BrentonVoice_09.wav");
	// var path = PathName("~/Downloads/melSamples/mel_sing_dry-005.wav");
	var path = PathName("~/Downloads/melSamples/mel_sing_wet-007.wav");

	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\bufGrainM,[\bufnum,buf, \rate, 1, \gate, 1 ]);
	});

	~vdef.(\cutoffRings, { |ev, c|
		var mod = ev[\modulation] ? ();
		var baseHz = mod[\baseHz] ? 240;
		var partials = mod[\partials] ? 24;
		var octavePx = mod[\octavePx] ? 120;
		var ampLag = mod[\ampLag] ? 0.6;
		var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
		var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
		var amp = m.accelMassFiltered.linlin(0, 4 * sens, 0, 1);
		var cutoff = m.gyroZFiltered.fold(-0.5, 0.5).lincurve(-0.5, 0.5, 1000, 18000, 0);
		var f0 = baseHz * notes[0].midiratio * 2;
		var dt = (c[\now] - lastNow).clip(0, 0.25);

		if(amp < 0.01, { amp = 0 });
		if(dt > 0, {
			lastNow = c[\now];
			ampSmooth = ampSmooth + (((amp * vol) - ampSmooth) * (1 - exp(dt.neg / ampLag)));
		});

		partials.do { |k|
			var h = k + 1;
			var x = f0 * h / cutoff;
			var gain = (((1 - x.squared).squared + (x * filterRq).squared).sqrt.reciprocal).min(1);
			var alpha = gain * ampSmooth * h.reciprocal.sqrt;
			if(alpha > 0.01, {
				c[\draw].(\circle, (size: c[\size] + (h.log2 * octavePx)), gain, alpha);
			});
		};
		nil
	});

	~vdef.(\noteClock, { |ev, c|
		var mod = ev[\modulation] ? ();
		var idle = mod[\idle] ? 0.2;
		var n = ev[\numPoints] ? 48;
		var progress = ((TempoClock.beats - lastTime) / noteHold).clip(0.001, 1);
		var span = progress * 2pi;
		var pts = Array.fill(n, { |i|
			c[\pos] + Polar(c[\size], i / (n - 1) * span).asPoint
		});
		c[\render].(pts, 1, idle + ((1 - idle) * ampSmooth), false);
		nil
	});

	(
		type: \customVisualEvent,
		amp: 0,
		dur: 0.01,
		viewID: d.port,
		shape: \cutoffRings,
		duration: inf,
		numPoints: 40,
		fill: false,
		closed: true,
		sx: 0, sy: 0, ex: 0, ey: 0,
		startSize: 70,
		endSize: 70,
		startWidth: 7,
		endWidth: 7,
		startColor: Color(0.35, 0.78, 1.0, 0.9),
		endColor: Color(0.35, 0.78, 1.0, 0.9),
		modulation: (
			baseHz: 240,
			partials: 24,
			octavePx: 120,
			ampLag: 0.6,
			type: \radial,
			amp: 4,
			freq: 0.3,
			harmonics: 4
		)
	).play;

	(
		type: \customVisualEvent,
		amp: 0,
		dur: 0.01,
		viewID: d.port,
		shape: \noteClock,
		duration: inf,
		numPoints: 48,
		fill: false,
		closed: false,
		sx: 0, sy: 0, ex: 0, ey: 0,
		startSize: 34,
		endSize: 34,
		startWidth: 12,
		endWidth: 12,
		rotation: -0.5pi,
		startColor: Color(1.0, 0.95, 0.35, 0.9),
		endColor: Color(1.0, 0.95, 0.35, 0.9),
		modulation: (idle: 0.2, amp: 0)
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
	var start = m.gyroYFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0.0,1.0,0);
	var cutoff = m.gyroZFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,1000,18000,0);

	if(amp < 0.01, {
		amp = 0;
	});

	if(TempoClock.beats > (lastTime + 7),{
			notes = notes.rotate(-1);
		lastTime = TempoClock.beats;
	});


	synth.set(\cutoff, cutoff);
	synth.set(\start, start);
	synth.set(\amp, amp * 2 * vol);
	synth.set(\rate, notes[0].midiratio);




};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	[m.accelMass];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};
