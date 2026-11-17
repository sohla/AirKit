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
    sig = RHPF.ar(sig, cutoff, rq);
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
			\instrument, \blobblob2,
			\bufnum, Pfunc{
				bi = bi + 1;
				if(bi >= 4,{bi=0});
				// bi = 6;
				buffers[bi];
			},
			\dur, Pseq([0.2,0.2,0.2,Rest(0.2),0.2,Rest(0.1),0.1,0.2] * 1, inf),
			\octave, 3,
			\rate, Pseq([1,1,1.12,1.12,1.07,1.67,1.39], inf),//1,1,1.12,1.12,,1.07,1.67,1.39
			\legato, 0.35,
			\start, Pwhite(0, 0.1),
			\note, Pseq([33].stutter(8), inf),
			// \amp, 2,
			\attack, 0.002,
			\release,0.17,
			\pan, Pwhite(-0.3,0.3),
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

	var amp = m.accelMassFiltered.linexp(0,1.5,1,3);
	Pdef(m.ptn).set(\amp, amp*1);

	//   if(d.sensors.digiInEvent[0] == 1, {
	// 	if( Pdef(m.ptn).isPlaying.not,{
	// 		Pdef(m.ptn).resume(quant:0);
	// 	});
	// },{
	// 	if( Pdef(m.ptn).isPlaying,{
	// 		Pdef(m.ptn).pause();
	// 	});
	// });

	if(m.accelMassFiltered > 0.07,{
		if( Pdef(~model.ptn).isPlaying.not,{
			Pdef(~model.ptn).resume(quant:0.25);
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