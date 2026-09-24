var m = ~model;
var buffers;
var synth;
var lastTime=0;
var index = 0;

m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.9;

SynthDef(\thunderSampler, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum);
	var cd = BufDur.kr(bufnum);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: cd * 2, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Pan2.ar(sig, pan, amp * env);
    Out.ar(out, sig);
}).add;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {

	var folder  = PathName("~/Downloads/yourDNASamples/thunder");
	postf("loading samples : % \n", folder);

	buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
			if(folder.entries.size - 1 == i,{
				"samples loaded".postln;
			});
		});
	});

};

~deinit = ~deinit <> {

	// if(synth.notNil, {
	// 	synth.onFree({
	// 		"all synths on free".postln;
	// 		buffers.do({|buf|
	// 			{
	// 				postf("buffer dealloc [%] \n", buf);
	// 				buf.free;
	// 				s.sync;
	// 			}.fork;
	// 		});
	// 	});
	// 	synth.set(\gate, 0);	
	// });
	// synth.sendMsg(\gate, 0);
	synth.free;
	buffers.do({|buf|
		buf.free;
		s.sync;
		postf("buffer dealloc [%] \n", buf);
	});

};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var move = m.accelMassFiltered.linlin(0, 6 * sens,0,1);
	var amp = m.accelMassFiltered.lincurve(0, 5 * sens,0.05,0.9,-2);

	if(move > 0.1, {
		if(TempoClock.beats > (lastTime + 0.35),{
			lastTime = TempoClock.beats;
			synth = Synth(\thunderSampler, [
				\rate, 1,
				\gate, 1,
				\amp, amp * vol,
        \bufnum, buffers[index]
			]);
			synth.server.sendBundle(0.3,[\n_set, synth.nodeID, \gate, 0]);
			index = index + 1;
      if(index >= buffers.size,{ index = 3});
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