var m = ~model;
var buffers;
var synths=[];
var group;
var dead = false;
var releaseTime = 10;
var lastTime=0;

m.accelMassFilteredAttack = 0.01;
m.accelMassFilteredDecay = 0.03;

SynthDef(\rainSampler, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0,
    attack=0.01, decay=0.1, sustain=0.3, release=0.9, gate=1,cutoff=20, rq=1|

	  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: 1, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: rate, startPos: start * BufFrames.kr(bufnum), loop: 1);
    sig = HPF.ar(sig, cutoff);
    sig = Balance2.ar(sig[0],sig[1], pan.lag(2), amp * env);
    Out.ar(out, sig);
}).add;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> { |d|

	var folder  = PathName("~/Downloads/yourDNASamples/rain");
	postf("loading samples : % \n", folder);

	group = Group.new;

	~vdef.(\drop, { |ev, c|
		var n = ev[\numPoints] ? 32;
		Array.fill(n, { |i| var t = i / n * 2pi; c[\pos] + ((c[\size] * 0.6 * sin(t) * sin(t / 2)) @ (c[\size] * cos(t).neg)) })
	});

	buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
			// postf("buffer alloc [%] \n", buf.path.basename.splitext[0]);
			if(dead.not, {
				synths = synths.add(Synth(\rainSampler, [
					\rate, 1.1,
					\gate, 1,
					\amp, 0,
					\pan,-1,
					\release, releaseTime,
					\bufnum, buf
				], group));
			});
			if(folder.entries.size - 1 == i,{
				"samples loaded".postln;
			});
		});
	});

};

~deinit = ~deinit <> {
	var dying = group;
	var dyingBuffers = buffers;

	dead = true;
	synths.do({|synth| synth.set(\gate, 0) });
	synths = [];
	group = nil;
	buffers = nil;

	fork {
		(releaseTime + 0.5).wait;
		if(dying.notNil, {
			s.bind { dying.freeAll };
			s.sync;
			dying.free;
		});
		dyingBuffers.do({|buf|
			postf("buffer dealloc [%] \n", buf);
			buf.free;
			s.sync;
		});
	};

	// buffers.do({|buf|
	// 	buf.free;
	// 	s.sync;
	// 	postf("buffer dealloc [%] \n", buf);
	// });
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var levels = [	
		m.accelMassFiltered.clip2(0.8 * sens).linlin(0, 0.8 * sens,0,1),
		m.accelMassFiltered.clip2(1.6 * sens).linlin(0.8 * sens, 1.6 * sens,0,1),
		m.accelMassFiltered.clip2(2.8 * sens).linlin(1.6 * sens, 2.8 * sens,0,1),
		m.accelMassFiltered.clip2(6.0 * sens).linlin(2.8 * sens, 6.0 * sens,0,1)];

	var mix = [0.6,1,1,1.5] * 4;
	var cutoff = m.accelMassFiltered.lincurve(0, 3 * sens,600,20,-2);
	var pan = d.sensors.gyroEvent.z.linlin(-1,1,-0.3,0.3);
	var drive = levels.sum / 4;
	var now = Main.elapsedTime;
	var gap = 0.1;//drive.linlin(0, 1, 2.0, 0.3);
	var size = rrand(8, 18);
	var col = Color.hsv([rrand(0.55, 0.66), rrand(0.40, 0.48)].wchoose([0.8, 0.2]), 0.6, 0.9, drive.linlin(0, 1, 0.4, 0.9));
	var x = rrand(-0.9, 0.9);

	synths.do({|synth, i|
		synth.set(\amp, levels[i] * mix[i] * vol);
		synth.set(\cutoff, cutoff);
		synth.set(\pan, pan);
	});

	if(((drive * vol) > 0.02) and: { now > (lastTime + gap) }, {
		lastTime = now;
		(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port, shape: \drop, numPoints: 32, fill: true, sx: x, ex: x, sy: -1.1, ey: 1.1, startSize: size, endSize: size, startWidth: 1, endWidth: 1, startColor: col, endColor: col.copy.alpha_(0), duration: rrand(2.0, 6.0)).play;
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




