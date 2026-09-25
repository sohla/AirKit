var m = ~model;
var synth;
var buffer;
var stretchDiv = 10;
var grainWindow = 0.4;
var sweep = 0;
var ampSmooth = 0;
var lastNow = 0;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.9;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\pullstretchMonoQm2, {|out, amp = 1, buffer = 0, envbuf = -1, pch = 1.0, div=1, speed = 0.01, splay = 0.4 ,pan=0, gate=1|
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1).range(0.0,0.7);
	var sp = Splay.arFill(4,
		{ |i| Warp1.ar(1, buffer, lfo.linlin(0,1,0.05,0.95), pch * (0.25/2) * (2*(i+1)),splay, envbuf, 8, 0.1 * (i+1), 4)  },
			1,
			1,
			0
	) ;
	var env = EnvGen.ar(Env.adsr(0.4,0.1,0.9,2.0), gate, doneAction:2);
	var mas = HPF.ar(sp,45);
	var sig = Pan2.ar(mas,pan)* amp.lag(1) * env;
	Out.ar(out, ((0)!0 ++ sig));
}).add;

//------------------------------------------------------------
~init = ~init <> {|d|
	var path = PathName("~/Downloads/melSamples/mel_sing_dry-004.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMonoQm2,[\buffer,buf,\pch,0.midiratio, \amp,0.4, \div, 10]);
	});

	~vdef.(\stretchArc, { |ev, c|
		var mod = ev[\modulation] ? ();
		var voice = mod[\voice] ? 0;
		var ampLag = mod[\ampLag] ? 1;
		var sweepLo = mod[\sweepLo] ? 0.05;
		var sweepHi = mod[\sweepHi] ? 0.95;
		var sweepRange = mod[\sweepRange] ? 0.7;
		var n = ev[\numPoints] ? 48;
		var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
		var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
		var amp = m.accelMass.linlin(0, 4 * sens, 0, 1);
		var speed = m.accelMassFiltered.lincurve(0.5, 5 * sens, 0.01, 1, -2);
		var pch = m.gyroZFiltered.linlin(-1, 1, 0, 1).round * 12;
		var freqScale = (pch - 4).midiratio * 0.25 * (voice + 1);
		var frames = buffer !? { buffer.numFrames };
		var bufDur = if(frames.notNil and: { frames > 0 }, { frames / buffer.sampleRate }, { 4 });
		var dt = (c[\now] - lastNow).clip(0, 0.25);
		var head, span, radius, pts;

		if(amp < 0.01, { amp = 0 });
		if(dt > 0, {
			lastNow = c[\now];
			sweep = (sweep + (dt * speed * stretchDiv / bufDur)).wrap(0, 1);
			ampSmooth = ampSmooth + (((amp * vol) - ampSmooth) * (1 - exp(dt.neg / ampLag)));
		});

		head = sweep.linlin(0, 1, 0, sweepRange).linlin(0, 1, sweepLo, sweepHi) * 2pi;
		span = (grainWindow * freqScale / bufDur * 2pi).clip(0.05, 2pi);
		radius = c[\size] * freqScale * 4;
		pts = Array.fill(n, { |i|
			c[\pos] + Polar(radius, head - (span * (1 - (i / (n - 1))))).asPoint
		});
		c[\render].(pts, ampSmooth, ampSmooth, false);
		nil
	});

	[
		Color(1.0, 0.41, 0.71),
		Color(1.0, 0.55, 0.82),
		Color(0.93, 0.62, 1.0),
		Color(0.78, 0.66, 1.0)
	].do { |col, i|
		(
			type: \customVisualEvent,
			amp: 0,
			dur: 0.01,
			viewID: d.port,
			shape: \stretchArc,
			duration: inf,
			numPoints: 64,
			fill: false,
			closed: false,
			sx: 0, sy: 0, ex: 0, ey: 0,
			startSize: 95,
			endSize: 95,
			startWidth: 34 - (i * 6),
			endWidth: 34 - (i * 6),
			rotation: -0.5pi,
			startColor: col.alpha_(0.9),
			endColor: col.alpha_(0.9),
			modulation: (
				voice: i,
				ampLag: 1,
				sweepLo: 0.05,
				sweepHi: 0.95,
				sweepRange: 0.7,
				type: \noise,
				amp: 3 * (i + 1),
				freq: 2,
				harmonics: 3
			)
		).play;
	};
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
	var amp = m.accelMass.linlin(0, 4 * sens,0.00001,1);
	var speed= m.accelMassFiltered.lincurve(0.5, 5 * sens,0.01,1,-2);
	var rate = m.accelMassFiltered.linlin(0, 2 * sens,0.9,1.4);
	var pch = m.gyroZFiltered.linlin(-1,1,0,1).round * 12;

	if(amp < 0.01, {amp = 0});

	synth.set(\pch, (pch + -4).midiratio);
	synth.set(\speed, speed);
	synth.set(\amp, amp * 0.8 * vol);
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];

};
