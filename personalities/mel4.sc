var m = ~model;
var synth;
var buffer;
var grainWindow = 0.3;
var grainOverlaps = 8;
var grainRand = 0.3;
var compThresh = 0.01;
var compSlope = 0.1;
var ampSmooth = 0;
var lastNow = 0;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.2;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\bufGrainM, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=5.2, gate=1,cutoff=20000, rq=1, rezf=200|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction:2);
	var sig = Splay.arFill(2,{|i|
			Warp1.ar(2, bufnum, start, rate * (i+1) , 0.3, -1, 8, windowRandRatio:0.3)
	},1,1,0);
  	sig = Compander.ar(sig, sig,
        thresh: 0.01,
        slopeBelow: 1,
        slopeAbove: 0.1,
        clampTime:  0.01,
        relaxTime:  0.01
    );
	sig = sig[0] * env * amp;
    Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {|d|
	var path = PathName("~/Downloads/melSamples/mel_mouth2.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\bufGrainM,[\bufnum,buf, \rate, 1, \gate, 1 ]);
	});

	~vdef.(\mouthGrain, { |ev, c|
		var mod = ev[\modulation] ? ();
		var slot = mod[\slot] ? 0;
		var stretch = mod[\stretch] ? 1;
		var halfWidth = mod[\halfWidth] ? 16;
		var twin = mod[\twin] ? 0.25;
		var ampLag = mod[\ampLag] ? 0.1;
		var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
		var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
		var amp = m.accelMassFiltered.linlin(0, 0.6 * sens, 0, 1);
		var rate = m.gyroYFiltered.lincurve(-1.0, 1.0, 0.1, 2.0, 0);
		var start = m.gyroZFiltered.lincurve(-1.0, 1.0, 0.0, 1.0, 0);
		var frames = buffer !? { buffer.numFrames };
		var bufDur = if(frames.notNil and: { frames > 0 }, { frames / buffer.sampleRate }, { 2 });
		var window = grainWindow * stretch;
		var phase = ((c[\now] / window) + (slot / grainOverlaps)).frac;
		var hann = sin(pi * phase).squared;
		var a = c[\posStart];
		var b = c[\posEnd];
		var dt = (c[\now] - lastNow).clip(0, 0.25);
		var squash, bar;

		if(amp < 0.01, { amp = 0 });
		if(dt > 0, {
			lastNow = c[\now];
			ampSmooth = ampSmooth + (((amp * vol) - ampSmooth) * (1 - exp(dt.neg / ampLag)));
		});
		squash = if(ampSmooth > compThresh, {
			compThresh * ((ampSmooth / compThresh) ** compSlope)
		},{
			ampSmooth
		}) / (compThresh * (compThresh.reciprocal ** compSlope));

		bar = { |voice, edge, reach, alpha|
			var travel = phase * window * rate * (voice + 1) / bufDur;
			var x = a.x.blend(b.x, (start + travel).wrap(0, 1));
			var tip = edge + (reach * hann * squash);
			if(hann * squash > 0.01, {
				c[\render].([
					(x - halfWidth) @ edge,
					(x + halfWidth) @ edge,
					(x + halfWidth) @ tip,
					(x - halfWidth) @ tip
				], 1, alpha, true);
			});
		};
		bar.(0, a.y, c[\size].neg, 1);
		bar.(1, b.y, c[\size], twin);
		nil
	});

	grainOverlaps.do { |i|
		var col = [Color(0.95, 0.1, 0.15), Color(1.0, 0.85, 0.0), Color(0.1, 0.35, 1.0)].wrapAt(i);
		(
			type: \customVisualEvent,
			amp: 0,
			dur: 0.01,
			viewID: d.port,
			shape: \mouthGrain,
			duration: inf,
			fill: true,
			closed: true,
			sx: -1, sy: 1, ex: 1, ey: -1,
			startSize: 260,
			endSize: 260,
			startWidth: 1,
			endWidth: 1,
			startColor: col.alpha_(0.85),
			endColor: col.alpha_(0.85),
			modulation: (
				slot: i,
				stretch: 1 + grainRand.rand2,
				halfWidth: 16,
				twin: 0.25,
				ampLag: 0.1,
				amp: 0
			)
		).play;
	};
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
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.accelMassFiltered.linlin(0, 0.6 * sens,0.00001,1);
	var rate =  m.gyroYFiltered.lincurve(-1.0,1.0,0.1,2.0,0);
	var start = m.gyroZFiltered.lincurve(-1.0,1.0,0.0,1.0,0);

	if(amp < 0.01, {amp = 0});

	synth.set(\start, start);
	synth.set(\rate, rate);
	synth.set(\amp, amp * 3s0 * vol);

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
