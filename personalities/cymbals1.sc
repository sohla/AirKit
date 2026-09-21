var m = ~model;
var bi = 0;
var dur = 0.18;
var buffers;
var lastTime = 0;

m.accelMassFilteredAttack = 0.9999;
m.accelMassFilteredDecay = 0.999;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;


//------------------------------------------------------------
SynthDef(\drumkit, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=0.02, gate=1,cutoff=50, rq=0.01|
	var lr = rate * BufRateScale.kr(bufnum);
	var cd = BufDur.kr(bufnum);
  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.0], startPos: start * BufFrames.kr(bufnum), loop: 0) ;
    sig = RHPF.ar(sig, cutoff, rq);
		sig = Compander.ar(sig, sig,
        thresh: -15.dbamp,
        slopeBelow: 1,
        slopeAbove: 0.5,
        clampTime:  0.01,
        relaxTime:  0.01
		) ;
		sig = Mix.ar([sig]);
    sig = Balance2.ar(sig[0],sig[1], pan);
    Out.ar(out, sig * amp * env);
}).add;
//--------------------------------------
~init = ~init <> {

	var folder  = PathName("~/Downloads/openLabSamples/kit");

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
			\instrument, \drumkit,			
			\bufnum, Pfunc{
				if(bi >= (buffers.size-1),{bi=0});
				buffers[bi];
			},
			\octave, Pseq([5].stutter(24), inf),
			\start, 0,
			\note, Pseq([40], inf),
			\pan, Pwhite(-0.3,0.3),
			\attack, 0.01,
			\release,1.3,
			\args, #[],

  		\type, \customVisualEvent,
			\shape, \square,
			\sx, Pwhite(-0.02,0.02),
			\sy, Pwhite(-0.02,0.02),
			\ex, 0,
			\ey, 0,
			\rotation, pi / Pwhite(1.7,2.3),
			\fill, true,
			\endWidth, 0.1,
      \duration, 0.3,
		)
	);

	Pdef(m.ptn).play(TempoClock,quant:dur);
	Pdef(m.ptn).set(\bufnum, buffers[0]);

};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

	buffers.do({|buf|
		// s.sync;
		postf("buffer dealloc [%] \n", buf);
		buf.free;
	});
};


//------------------------------------------------------------
~next = {|d|

	var rate = m.rrateMassFiltered.linlin(0,0.3,0.2,10.4);
	var amp = m.accelMassFiltered.lincurve(0,0.3,0.0,2, 2);
	var roll = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0,1.0,0.5,1.0,0);
	var thr = (d.sensors.accelEvent.y.abs).lincurve(0,0.5,0.0,1.0,-2).asInteger;
	var ff = ((d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2).lincurve(-1.0,1.0,500,50.0,1);

	Pdef(m.ptn).set(\amp, amp*1);
	Pdef(m.ptn).set(\rate, roll);
	Pdef(m.ptn).set(\cutoff, ff);

	Pdef(m.ptn).set(\viewID, d.port);
  	Pdef(m.ptn).set(\startSize, 70 * amp);
  	Pdef(m.ptn).set(\endSize, 130 + (40 * amp));
  	Pdef(m.ptn).set(\startWidth, (2.pow(amp)));

	Pdef(m.ptn).set(\modulation, (
			type: \radial,
			freq: 1 ,
			amp: 1 + (30 * amp),
			harmonics: 2
	));

	bi = (d.sensors.gyroEvent.y / pi.half).linlin(-1.0,1.0,0,buffers.size-1);
	bi = bi.asInteger;

	Pdef(m.ptn).set(\startColor, Color.hsv(bi/buffers.size,1,1.0,0.5));
	Pdef(m.ptn).set(\endColor, Color.hsv(bi/buffers.size,1,1.0,0.1));

	if(TempoClock.beats >= (lastTime + 0.18),{
		if(m.accelMassFiltered > 0.3,{
			lastTime = TempoClock.beats;
			dur = 0.18/2;
		},{
			dur = 0.18;			
		});
	});
	Pdef(m.ptn).set(\dur, 0.18);

	if(m.accelMassFiltered > 0.01,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.18);
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
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	[d.sensors.gyroEvent.y / pi.half, (d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2,d.sensors.accelEvent.y.abs,(d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
// Buffer.cachedBuffersDo(s, {|b|b.postln})