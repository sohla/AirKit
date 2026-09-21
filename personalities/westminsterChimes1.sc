var m = ~model;
var bi = 0;
var dur = 0.25;

~buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;

//------------------------------------------------------------
SynthDef(\drumkit, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=0.3, gate=1,cutoff=10, rq=1|

	var lr = rate * BufRateScale.kr(bufnum);// * (freq/440.0);
	var cd = BufDur.kr(bufnum);
  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);

	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0], startPos: start * BufFrames.kr(bufnum), loop: 0) ;
    sig = RHPF.ar(sig, cutoff, rq);
		sig = Compander.ar(sig, sig,
        thresh: -15.dbamp,
        slopeBelow: 1,
        slopeAbove: 0.5,
        clampTime:  0.01,
        relaxTime:  0.01
		) ;
		sig = Mix.ar([sig]);
    // sig = Pan2.ar(Mix(sig), pan);
    Out.ar(out, sig * amp * env);
}).add;


//------------------------------------------------------------
~init = ~init <> {

	var folder  = PathName("~/Downloads/westminsterChimes");
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
			\instrument, \drumkit,
			\bufnum, Pfunc{
				bi = rrand(1,~buffers.size-1);
				~buffers[bi];
			},
			\rate, 1,
			\start, Pwhite(0,0.1),
			\note, Pseq([40], inf),
			\pan,Pwhite(-0.6,0.6),
			\cutoff, 500,
			\attack, 0.03,
			\release, 1.9,
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:dur);
	Pdef(m.ptn).set(\bufnum, ~buffers[0]);
};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

	~buffers.do({|buf|
		postf("buffer dealloc [%] \n", buf);
		s.sync;
		buf.free;
	});
	// synth.free;
};


//------------------------------------------------------------
~next = {|d|

	var amp = m.accelMassFiltered.lincurve(0,1.5,-20,-10,-1);
  	var notes = [1,2,3,4];
	var index = (d.sensors.gyroEvent.y/pi).linlin(-0.5,0.5,0.0,notes.size); 
	var dur = m.accelMassFiltered.lincurve(0,1.5,0.4,0.1,-1);
	
	if(amp < -19, {amp = -90;});

	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\dur, dur);
	
	// dur = 0.125;
	// bi = notes[index.asInteger.clip(0, notes.size-1)];

	// if(m.accelMassFiltered > 0.05,{
	// 	if( Pdef(m.ptn).isPlaying.not,{
	// 		// Pdef(m.ptn).resume(quant:dur);
	// 		Pdef(m.ptn).play(quant:dur);
	// 	});
	// },{
	// 	if( Pdef(m.ptn).isPlaying,{
	// 		// Pdef(m.ptn).pause();
	// 		Pdef(m.ptn).stop();
	// 		Pdef(m.ptn).reset();
	// 	});
	// });
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMassFiltered * 0.15];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered * 4]
	[d.sensors.gyroEvent.y/pi];
	// [m.accelMassFiltered]
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
Buffer.cachedBuffersDo(s, {|b|b.postln})