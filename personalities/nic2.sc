var m = ~model;
var bi = 0;
var dur = 0.3 ;
var localRoot = 0;
var buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.2;

//------------------------------------------------------------
SynthDef(\drumkitNN, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=0.3, gate=1,cutoff=10, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);
	var cd = BufDur.kr(bufnum);
  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr,lr], startPos: start * BufFrames.kr(bufnum), loop: 0) ;
    sig = RHPF.ar(sig, cutoff, rq);
		sig = Compander.ar(sig, sig,
        thresh: -15.dbamp,
        slopeBelow: 1,
        slopeAbove: 0.5,
        clampTime:  0.01,
        relaxTime:  0.01
	);
	sig = Mix.ar([sig]);
    sig = Balance2.ar(sig[0],sig[1], pan) * amp * env;
    Out.ar(out, ((0)!0 ++ sig));
}).add;

//--------------------------------------
~init = ~init <> {

	var folder = PathName("~/Downloads/nicSamples/3_Bites/Percussive");
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
			\instrument, \drumkitNN,			
			\bufnum, Pfunc{
				bi = bi + 1;
				if(bi >= (9),{bi=0});
				buffers[bi];
			},
			\start, 0.03,
			\pan, Pwhite(-0.1,0.1),
			\root, Pseq([0, -2].stutter(64), inf),
			\func, Pfunc({|e|localRoot=e.root}),
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:dur);
	Pdef(m.ptn).set(\bufnum, buffers[0]);

};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	{
	buffers.do({|buf|
		buf.free;
		// s.sync;
		postf("buffer dealloc [%] \n", buf);
	});
	}.defer(0.3);
};


//------------------------------------------------------------
~next = {|d|

	var rate = m.rrateMassFiltered.linlin(0,1,0.6,3);
	var amp = m.accelMassFiltered.lincurve(0,2.5,0.4,1, -2);
	var notes = [0,7,5,10] + localRoot + 5;
	var amps = [2,1,1,1] * 0.2;
	var index = m.gyroYFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,notes.size,-1).asInteger;
	var attack = m.accelMassFiltered.lincurve(0.0,1.5,0.1,0.002,-1);
	var release = m.accelMassFiltered.lincurve(0.0,1.5,4.3,0.001,-1);

	dur = m.accelMassFiltered.lincurve(0,0.6,0.2,0.06, -1);

	Pdef(m.ptn).set(\amp, amp * amps[index]);
	Pdef(m.ptn).set(\rate, (notes[index]).midiratio );
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\attack, attack);
	Pdef(m.ptn).set(\release, release);

	if(m.accelMassFiltered > 0.1,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [m.gyroXFiltered.fold(-0.5,0.5).linlin(-0.5,0.5,-10,10).lcurve];

	[m.accelMassFiltered];

};
