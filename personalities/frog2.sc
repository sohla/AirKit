var m = ~model;
var bi = 0;
var buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;

SynthDef(\blobblob2, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var cd = BufDur.kr(bufnum);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: cd * 2, doneAction: 2);
		var hat = HPF.ar(WhiteNoise.ar, 13000) * EnvGen.ar(Env.perc(0.001, 	0.04), gate);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RHPF.ar(sig + (hat * 0.1), cutoff, rq);
    sig = Balance2.ar(sig[0], sig[0], pan);
		sig = Compander.ar(sig, sig,
						thresh: -20.dbamp,
						slopeBelow: 1,
						slopeAbove: 0.5,
						clampTime:  0.02,
						relaxTime:  0.01
				);
    Out.ar(out, sig * amp * env);
}).add;
SynthDef(\miniMoogModel, { |freq = 440, amp = 0.5, gate = 1, pan = 0,
	attack = 0.002, decay = 0.1, sustain = 0.2, release = 0.3, detune = 0.005,
    osc1Mix = 0.33, osc2Mix = 0.13, osc3Mix = 0.93,
    noiseMix = 0.1, lfoRate = 0.5, lfoAmount = 0.5, filterFreq = 3000, filterRes = 0.5|

    var env, osc1, osc2, osc3, noise, filter, lfo, modulatedSignal, mix;
		freq = freq * 0.5;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
    osc1 = Saw.ar(freq) * osc1Mix;
	osc2 = Pulse.ar([freq, freq * (1 + detune)], 0.5) * osc2Mix;
	osc3 = SinOsc.ar([freq * (1 - detune), freq]) * osc3Mix;
    noise = WhiteNoise.ar() * noiseMix;
    mix = (osc1 + osc2 + osc3 + noise) * env;
    filter = LPF.ar(mix, filterFreq, filterRes);
    lfo = SinOsc.kr(lfoRate).range(1 - lfoAmount, 1 + lfoAmount);
    modulatedSignal = filter * lfo;
    Out.ar(0, Pan2.ar(modulatedSignal * amp * 0.15, pan));
}).add;
//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {

	var folder  = PathName("~/Downloads/yourDNASamples/blobblob");
	postf("loading samples : % \n", folder);

	buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
			if(folder.entries.size - 1 == i,{
				"samples loaded".postln;
			});
		});
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, Pseq([\blobblob2], inf),
			\bufnum, Pfunc{
				bi = bi + 1;
				if(bi >= (buffers.size-1),{bi=0});
				buffers[bi];
			},
			\dur, Pseq([0.2,Rest(0.2),0.2,0.2,Rest(0.2),0.1,0.1,0.2]*1, inf),
			\octave, Pseq([1,3].stutter(4), inf),
			\rate, Pseq([0,4,7].midiratio, inf),
			\legato, Prand([0.35,0.65], inf),
			\start, Pwhite(0, 0.2),
			\note, Pseq([33].stutter(8), inf),
			// \amp, 2,
			\attack, 0.002,
			\release,0.17,
			\pan, Pseq([-0.2,0.2], inf),
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:0.25);
};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

	buffers.do({|buf|
		buf.free;
		s.sync;
		postf("buffer dealloc [%] \n", buf);
	});
};

//------------------------------------------------------------
~next = {|d|

	var amp = m.accelMassFiltered.linexp(0,1.5,1,4);
	Pdef(m.ptn).set(\amp, amp);

	if(m.accelMassFiltered > 0.07,{
		if( Pdef(~model.ptn).isPlaying.not,{
			Pdef(~model.ptn).resume(quant:0.2);
		});
	},{
		if( Pdef(~model.ptn).isPlaying,{
			Pdef(~model.ptn).pause();
		});
	});
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