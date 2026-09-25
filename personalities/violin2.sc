var m = ~model;
var synth;
var buffer;
var scan = 0.5;
var lastNow = 0;
var level = 0;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.9;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;


//------------------------------------------------------------
SynthDef(\monoSampler, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.0, release=0.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], Lag.kr(pan, 3),  env);
		sig = Compander.ar(sig, sig,
						thresh: -32.dbamp,
						slopeBelow: 1,
						slopeAbove: 0.5,
						clampTime:  0.02,
						relaxTime:  0.01
				);
    Out.ar(out, sig * amp);
}).add;

SynthDef(\pullstretchMonoQ, {|out, amp = 1, buffer = 0, envbuf = -1, pch = 1.0, div=1, speed = 0.01, splay = 0.4 ,pan=0, rate=1|
	var pos;
	// var mx,my;
	var sp;
	var mas;
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1,0.5,0.5);
	// my = MouseY.kr(0.01,1,1.0);//splay


	sp = Splay.arFill(4,
		{ |i| Warp1.ar(1, buffer, lfo.linlin(0,1,0.05,0.95), rate,splay, envbuf, 8, 0.3, 4)  },
			1,
			1,
			0
	) ;

	mas = HPF.ar(sp,245);

  mas = FreeVerb.ar(mas,0.5);

	Out.ar(out,Pan2.ar(mas[0],pan.lag(1))* amp.lag(1));
}).add;
//------------------------------------------------------------
~init = ~init <> {|d|
	// var path = PathName("~/Downloads/yourDNASamples/HK laughing2-glued.wav");
	var path = PathName("~/Downloads/yourDNASamples/violin/Violin_04.wav");

	// var path = PathName("~/Downloads/yourDNASamples/HK lots of teddies.wav");
	postf("loading sample : % \n", path.fileName);

	~vdef.(\grainScan, { |ev, c|
		var mod = ev[\modulation] ? ();
		var div = mod[\div] ? 10;
		var winSize = mod[\windowSize] ? 0.4;
		var overlaps = mod[\overlaps] ? 8;
		var randRatio = mod[\randRatio] ? 0.3;
		var voices = mod[\voices] ? 4;
		var lo = mod[\lo] ? 0.05;
		var hi = mod[\hi] ? 0.95;
		var ripples = mod[\ripples] ? 3;
		var lagTime = mod[\lag] ? 1;
		var lane = mod[\lane] ? 36;
		var drift = mod[\drift] ? 0.6;
		var headReach = mod[\headReach] ? 1.8;
		var headAlpha = mod[\headAlpha] ? 0.15;
		var n = ev[\numPoints] ? 24;
		var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
		var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
		var amp = m.accelMass.linlin(0, 1 * sens, 0.00001, 1);
		var speed = m.accelMassFiltered.lincurve(0.1, 1 * sens, 0.01, 2, -2);
		var rate = m.gyroYFiltered.linlin(-1, 1, 1, 2).asInteger;
		var pan = m.gyroZFiltered.linlin(-1, 1, -1, 1);
		var frames = buffer !? { buffer.numFrames };
		var bufDur = if(frames.notNil, { frames / buffer.sampleRate }, { 10 });
		var now = c[\now];
		var dt = (now - lastNow).clip(0, 0.25);
		var a = c[\posStart];
		var b = c[\posEnd];
		var tall = c[\size];
		var hop = winSize / overlaps;
		var span = winSize * rate / bufDur;
		var head, centre;

		lastNow = now;
		if(amp < 0.01, { amp = 0 });
		level = level + (((amp * vol) - level) * (dt * 6.9 / lagTime).clip(0, 1));
		scan = (scan + (dt * speed * div / bufDur)).wrap(0, 1);
		head = a.blend(b, scan.linlin(0, 1, lo, hi));
		centre = head + (0 @ (pan * tall * drift));

		c[\render].([head - (0 @ (tall * headReach)), head + (0 @ (tall * headReach))], 0.5, headAlpha, false);

		if(level > 0.005, {
			voices.do { |v|
				var gain = cos((v / (voices - 1) * 2) * 0.25pi);
				var y = centre + (0 @ ((v - ((voices - 1) / 2)) * lane));
				overlaps.do { |g|
					var serial = (now / hop).floor - g;
					var age = (now - (serial * hop)) / winSize;
					var jitter = (sin((serial * 7.31) + (v * 101.7)) * 43758.5453).frac * 2 - 1;
					var wide = span * (1 + (jitter * randRatio)) * (b - a).x / (hi - lo);
					var height = tall * level * gain * sin(age.clip(0, 1) * pi).squared;
					var pts = Array.fill(n, { |k|
						var u = k / (n - 1);
						y + (((u - 0.5) * wide) @ (sin(u * pi).squared * cos(2pi * ripples * rate * u) * height.neg))
					});
					if(height > 0.5, { c[\render].(pts, gain, gain, false) });
				};
			};
		});
		nil
	});

	(
		type: \customVisualEvent,
		amp: 0,
		dur: 0.01,
		viewID: d.port,
		shape: \grainScan,
		numPoints: 24,
		closed: false,
		fill: false,
		sx: -1, sy: 0,
		ex: 1, ey: 0,
		startSize: 220,
		endSize: 220,
		startWidth: 2,
		endWidth: 2,
		startColor: Color.new(0.1, 0.66, 0.62, 0.85),
		endColor: Color.new(0.1, 0.66, 0.62, 0.85),
		duration: inf,
		modulation: (
			div: 10,
			windowSize: 0.4,
			overlaps: 8,
			randRatio: 0.3,
			voices: 4,
			lo: 0.05,
			hi: 0.95,
			ripples: 3,
			lag: 1,
			lane: 36,
			drift: 0.6,
			headReach: 1.8,
			headAlpha: 0.15,
			amp: 0
		)
	).play;

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMonoQ,[\buffer,buf,\pch,-12.midiratio, \amp,0.4, \div, 10]);
	});
};

~deinit = ~deinit <> {
	synth.free;
	buffer.free;
};


//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.accelMass.linlin(0, 1 * sens,0.00001,1);
	var speed= m.accelMassFiltered.lincurve(0.1, 1 * sens,0.01,2,-2);
	var rate = m.gyroYFiltered.linlin(-1,1,1,2).asInteger;
	var pan = m.gyroZFiltered.linlin(-1,1,-1,1);

	if(amp < 0.01, {amp = 0});

	synth.set(\rate, rate);
	synth.set(\speed, speed);
	synth.set(\amp, amp * 12 * vol);
	synth.set(\pan, pan);
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};
