var m = ~model;
var bi = 0;
var dur = 0.14 * 1;
var limit = 8;
var step = 1;
var lastTime=0;
var buffers;

// ~buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\drumkit2, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=0.3, gate=1,cutoff=40, rq=0.1, octave = 0|
	var lr = rate * BufRateScale.kr(bufnum);
	var cd = BufDur.kr(bufnum);
  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1] * (octave * 12).midiratio, startPos: start * BufFrames.kr(bufnum), loop: 0) * 10;
    sig = RHPF.ar(sig, cutoff, rq);
		sig = Compander.ar(sig, sig,
        thresh: -5.dbamp,
        slopeBelow: 1,
        slopeAbove: 0.5,
        clampTime:  0.01,
        relaxTime:  0.01
	);
	sig = Mix.ar([sig]);
    sig = Balance2.ar(sig[0],sig[1], -1);
    Out.ar(out, ((0)!0 ++ sig) * amp * env);

}).add;
//--------------------------------------
~init = ~init <> {

	var folder  = PathName("~/Downloads/melSamples/melbb");
	var shapes = [\circle,\circle,\hexagon,\hexagon,\hexagon,\circle,\hexagon];
	postf("loading samples : % \n", folder);

	buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			buf.normalize(0.8);
			postf("buffer alloc [%] \n", buf);
			if(folder.entries.size - 1 == i,{
				"samples loaded".postln;
			});
		});
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \drumkit2,			
			\bufnum, Pfunc{
        bi = bi + step;
				if(bi >= (7),{bi=0});
				buffers[bi];
				bi.asInteger.postln;
			},
			\octave, Pseq([0].stutter(8), inf),
			\start, 0.05,
			\note, Pseq([30], inf),
			// \dur, dur,
			\pan, Pwhite(-0.4,0.4),
			\attack, 0.02,

			\type, \customVisualEvent,
			\shape, Pfunc{shapes[bi.asInteger]},
			
			// \sx, 0,
			// \sy, 0.4,
			// \ex, 0,
			// \ey, 0.4,
			// \xEnv: ~xEnv ? defaultEnv,
			// \yEnv: ~yEnv ? defaultEnv,			

			\startWidth, Pfunc{|e| e.dur.squared * 100},
			\endWidth, 1,
			// \widthEnv: ~sizeEnv ? defaultEnv,

			\startSize, 300,
			\endSize, 60,
			// \sizeEnv: ~sizeEnv ? defaultEnv,

			\startColor, Color.yellow,
			\endColor, Color.red.alpha_(0.0),
			// \colorEnv: ~colorEnv ? defaultEnv,

			\rotation,pi.half,

			\duration, 0.7,
			// \envelope: ~envelope ? defaultEnv,

			// \alphaEnv: ~alphaEnv ? Env([1, 1, 0], [0.0, 1], \sin),

			// \fill, true,

			// \closed: ~closed ? true,  // Whether to close the shape
			// \modulation: ~modulation,  // Optional modulation settings

			\args, #[],
		)
	);


	Pdef(\shaker,
		Pbind(
			\instrument, \drumkit2,
			\bi, Pxrand([0,1,3,5], inf),			
			\bufnum, Pfunc{|e|
				buffers[e.bi];
			},
			\octave, Pseq([0,1,2].stutter(1), inf),
			\start, Pwhite(0.15,0.3),
			\note, Pseq([30], inf),
			\rate, Pxrand([5,6], inf),
			\dur, dur,
			\cnt, Pseries(0,1, inf),
			\clk, Pfunc({TempoClock.beats}),
			\pan, Pwhite(-0.4,0.4),
			\attack, Pwhite(0.07,0.14),
			\legato, 0.2,
			\release, Pwhite(0.1,0.9),

			\type, \customVisualEvent,
			\shape, \line,
			\sx, Pfunc{|e| ((e.bi / 3) - 1) * 0.02},
			\sy, 0,
			\ex, Pkey(\sx),
			\ey, 0,
			\startWidth, 1,
			\endWidth, 10,
			// \widthEnv: ~sizeEnv ? defaultEnv,

			\startSize, Pfunc{|e| 300 - ((e.octave + 1) * 30)},
			\endSize, 10,
			// \sizeEnv: ~sizeEnv ? defaultEnv,

			\startColor, Color.red,
			\endColor, Color.yellow.alpha_(0.0),
			// \colorEnv: ~colorEnv ? defaultEnv,

			\rotation,pi.half,

			\duration, 0.3,

			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		)
	);


	Pdef(m.ptn).play(quant:dur);
	Pdef(m.ptn).set(\bufnum, buffers[0]);

	Pdef(\shaker).play(quant:dur);


};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	Pdef(\shaker).remove;

	buffers.do({|buf|
		postf("buffer dealloc [%] \n", buf);
		buf.free;
		s.sync;
	});
};

//------------------------------------------------------------
~onEvent = {|e|
	
	if(e.clk > (lastTime + (0.14 * 2)),{
		lastTime = e.clk;
		if(m.accelMassFiltered > 1.5,{
				dur = 0.14/2;
		},{
				dur = 0.14
		});

	});
  true
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity;
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var rel = m.gyroYFiltered.clip(-0.5,0.5).lincurve(-0.5,0.5,0.3,0.01,3);
	var amp = m.accelMassFiltered.lincurve(0, 0.6 * sens,0.0,0.8, -1);
	var roll = m.gyroXFiltered.lincurve(-0.2,0.4,1,4,-2) * 0.5;
	var sa = m.rrateMassFiltered.lincurve(0, 0.6 * sens,0.1,0.35, -1);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(\shaker).set(\viewID, d.port);

	Pdef(m.ptn).set(\amp, amp * 0.3 * vol);
	Pdef(m.ptn).set(\release, rel);
	Pdef(m.ptn).set(\rate, roll);
	step = 2.pow(m.accelMassFiltered.lincurve(0, 2 * sens,-1,0, -1));

	Pdef(\shaker).set(\amp, sa*1 * vol);	
	Pdef(m.ptn).set(\dur, dur);	


	if(m.accelMassFiltered > 0.07,{
		if( Pdef(m.ptn).isPlaying.not,{
      // bi = 8;
			Pdef(m.ptn).play(quant:dur*1);
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

	// [yellow, cyan , magenta]??

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	[m.accelMass, m.accelMassFiltered];

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];


};
// Buffer.cachedBuffersDo(s, {|b|b.postln})