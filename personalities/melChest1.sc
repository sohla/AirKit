var m = ~model;
var synth;
var delayTaps = [0.02, 0.027];
var followSmooth = 0;
var lastNow = 0;

m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.1;

//------------------------------------------------------------
SynthDef(\treeWind, { |out, frq=111, gate=0, amp = 0, pchx=0|
	var env = EnvGen.ar(Env.asr(1.3,1.0,8.0), gate, doneAction:Done.freeSelf);
	var follow = Amplitude.kr(amp, 0.3, 0.5).lag(2);
	var tone = Saw.ar([60 + 7 - 12 + pchx.lagud(0.03,0.1)].midicps,0.002 * env * amp.lagud(0.01,0.05)).tanh;
	var trig = PinkNoise.ar(0.01) * env * follow + tone;
	var sig =  DynKlank.ar(`[[60 + 7 - 12 + pchx.lagud(0.02,0.08) - 24].midicps, nil, [2, 1, 1, 1]], trig);
	var dly = DelayC.ar(sig,0.03,[0.02,0.027]);
	Out.ar(out, dly * 0.5);
}).add;

~init = ~init <> {|d|
	synth = Synth(\treeWind, [\frq, 140.rrand(80), \gate, 1]);

	~vdef.(\windStrand, { |ev, c|
		var mod = ev[\modulation] ? ();
		var channel = mod[\channel] ? 0;
		var inward = mod[\inward] ? 1;
		var followLag = mod[\followLag] ? 2;
		var vibScale = mod[\vibScale] ? 0.05;
		var toothScale = mod[\toothScale] ? 0.1;
		var toothPx = mod[\toothPx] ? 16;
		var tapScale = mod[\tapScale] ? 20;
		var n = ev[\numPoints] ? 120;
		var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
		var a = m.accelMass;
		var pchs = [0,2,4,5,7,9,10,12] - 7;
		var pchx = pchs.clipAt((d.sensors.gyroEvent.x.abs / pi).lincurve(0.0, 1.0, 0.0, pchs.size).floor);
		var bodyHz = (60 + 7 - 12 + pchx - 24).midicps;
		var sawHz = (60 + 7 - 12 + pchx).midicps;
		var now = c[\now];
		var lag = now - (delayTaps[channel] * tapScale);
		var dt = (now - lastNow).clip(0, 0.25);
		var p0 = c[\posStart];
		var p1 = c[\posEnd];
		var sway, pts;

		if(a < 0.02, { a = 0 });
		if(a > 0.9, { a = 0.9 });
		a = a * vol;
		if(dt > 0, {
			lastNow = now;
			followSmooth = followSmooth + ((a - followSmooth) * (1 - exp(dt.neg / followLag)));
		});

		sway = cos(2pi * bodyHz * vibScale * lag) * followSmooth * c[\size];
		pts = Array.fill(n, { |i|
			var t = i / (n - 1);
			var tooth = (((t * sawHz * toothScale) - (lag * sawHz * vibScale)).frac * 2 - 1) * a * toothPx;
			p0.blend(p1, t) + ((((sin(pi * t) * sway) + tooth) * inward) @ 0)
		});
		c[\render].(pts, 0.3 + followSmooth, followSmooth.max(a), false);
		nil
	});

	[
		[-0.9, 1, Color(1.0, 0.55, 0.1)],
		[0.9, -1, Color(1.0, 0.93, 0.3)]
	].do { |side, i|
		(
			type: \customVisualEvent,
			amp: 0,
			dur: 0.01,
			viewID: d.port,
			shape: \windStrand,
			duration: inf,
			numPoints: 120,
			fill: false,
			closed: false,
			sx: side[0], sy: -1.05, ex: side[0], ey: 1.05,
			startSize: 380,
			endSize: 380,
			startWidth: 12,
			endWidth: 12,
			startColor: side[2].alpha_(0.9),
			endColor: side[2].alpha_(0.9),
			modulation: (
				channel: i,
				inward: side[1],
				followLag: 2,
				vibScale: 0.05,
				toothScale: 0.1,
				toothPx: 16,
				tapScale: 20,
				type: \noise,
				amp: 5,
				freq: 0.7,
				harmonics: 5
			)
		).play;
	};
};

~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var a = m.accelMass * 1;
	var f = 50 + (m.accelMassFiltered * 100);
	var pchs = [0,2,4,5,7,9,10,12] - 7;
	var i = (d.sensors.gyroEvent.x.abs / pi).lincurve(0.0,1.0,0.0, pchs.size);
	// pchs[i.floor].postln;
	if(a<0.02,{a=0});
	if(a>0.9,{a=0.9});
	synth.set(\amp, a * 1 * vol);
	synth.set(\pchx,pchs[i.floor]);
  // synth.set(\pchx, m.com.root);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	// [m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
	[d.sensors.gyroEvent.x];


};
