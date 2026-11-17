var m = ~model;
var bi = 0;

~buffers;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.2;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.9;

//------------------------------------------------------------
SynthDef(\grobt, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=400, rq=1, subFreq=145|

	var lr = rate * BufRateScale.kr(bufnum) ;//* (freq/440.0);
	var cd = BufDur.kr(bufnum);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: cd * 2, doneAction: 2);
		  var kick = SinOsc.ar(XLine.kr(subFreq*2, subFreq, 0.04),0,0.2) * EnvGen.ar(Env.perc(0.01, 0.3), gate);

	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.0], startPos: start * BufFrames.kr(bufnum), loop: 0) * 1.7;
    sig = RHPF.ar(sig, cutoff, rq);
		sig = Compander.ar(sig, sig,
        thresh: -33.dbamp,
        slopeBelow: 1,
        slopeAbove: 0.5,
        clampTime:  0.01,
        relaxTime:  0.01
		);
		sig = Mix.ar([sig]);
    sig = Pan2.ar(sig, pan);
    Out.ar(out, sig * amp * env);
}).add;

SynthDef(\miniMoogModel, { |freq = 440, amp = 0.5, gate = 1, pan = 0,
	attack = 0.002, decay = 0.1, sustain = 0.2, release = 0.3, detune = 0.005,
    osc1Mix = 0.33, osc2Mix = 0.13, osc3Mix = 0.93,
    noiseMix = 0.1, lfoRate = 0.5, lfoAmount = 0.5, filterFreq = 3000, filterRes = 0.5|

    var env, osc1, osc2, osc3, noise, filter, lfo, modulatedSignal, mix;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
    osc1 = Saw.ar(freq) * osc1Mix;
	osc2 = Pulse.ar([freq, freq * (1 + detune)], 0.5) * osc2Mix;
	osc3 = SinOsc.ar([freq * (1 - detune), freq]) * osc3Mix;
    noise = WhiteNoise.ar() * noiseMix;
    mix = (osc1 + osc2 + osc3 + noise) * env;
    filter = LPF.ar(mix, filterFreq, filterRes);
    lfo = SinOsc.kr(lfoRate).range(1 - lfoAmount, 1 + lfoAmount);
    modulatedSignal = filter * lfo;
    Out.ar(0, Pan2.ar(modulatedSignal * amp, pan));
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var folder  = PathName("~/Downloads/yourDNASamples/robt");
	postf("loading samples : % \n", folder);

	~buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
			if(folder.entries.size - 1 == i,{
				"samples loaded".postln;
			});
		});
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \grobt,
			\bufnum, Pfunc{
				bi = bi + 1;
				if(bi >= (~buffers.size-1),{bi=0});
				~buffers[bi];
			},
			\octave, 5,//Pseq([3,4].stutter(24), inf),
			\dur, Pseq([0.25,0.25+0.125,0.125,0.25,0.25, 0.125,0.125], inf),
			// \rate, 0.5,//Pseq([0,-3,-5,4,7,9,12,0].midiratio, inf),
			\amp, Pseq([1, 2, 2, 0.9,0.7,0.6 ,0.5 ,1] * 1, inf),
			\subFreq,Pseq([45,55,440,80,45,70], inf),
			\start, 0,
			\legato, 0.3,
			\note, Pseq([33], inf),
		 \pan,Pseq([-1],inf),
			\attack, 0.02,
			\release,0.2,
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:0.2);

	Pdef(\mm,
		Pbind(
			\instrument, \miniMoogModel,
			\octave, Pseq([3,4], inf),
			\amp, 0.2,
			\dur, 0.2,
			\root, Pseq([0,3,-2,-5].stutter(16), inf),
			\note, Pseq([0,5,4,2].stutter(8), inf),
			\args, #[],
		)
	);
	// Pdef(\mm).play(quant:0.2);

};

~deinit = ~deinit <> {

	Pdef(m.ptn).remove;
	// Pdef(\mm).remove;

	~buffers.do({|buf|
		buf.free;
		s.sync;
		postf("buffer dealloc [%] \n", buf);
	});
};


//------------------------------------------------------------
~next = {|d|


	var sub = 2.pow(m.rrateMassFiltered.lincurve(0,0.2,0,4,-2).floor).reciprocal;
	var rate = ((d.sensors.gyroEvent.y / pi).fold(-0.5,0.5) * 2.0).linlin(-1, 1, 0.25,6);

	// Pdef(m.ptn).set(\dur, 0.25);
	Pdef(m.ptn).set(\rate, rate);

  // if(d.sensors.digiInEvent[0] == 1, {
	if(m.rrateMassFiltered > 0.022,{

		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.25);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});


	if(m.rrateMassFiltered > 0.022,{
	},{
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.rrateMass * 0.1, m.rrateMassFiltered];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
Buffer.cachedBuffersDo(s, {|b|b.postln})