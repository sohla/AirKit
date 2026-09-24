var m = ~model;
var bi = 0;
var buffers;
var group;

var beat = 0.8;
var layers = [
	[1] / 2,
	[1, 2, 1] / 4,
	[2, 1, 1, 3, 2, 1] / 8
];

var layerFloors = [0.5, 0.7];

m.accelMassFilteredAttack = 0.9999;
m.accelMassFilteredDecay = 0.999;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;


//------------------------------------------------------------
SynthDef(\cymbals2, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
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

	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \cymbals2,
			\group, group,

			\step, Pswitch(layers.collect({ |l| Pseries(0, 1, l.size) }), Pkey(\layer)),
			\dur,  Pswitch(layers.collect({ |l| Pseq(l, 1) }),            Pkey(\layer)) * beat,
			\accent, Pfunc({ |e| if(e[\step] == 0, { 1.0 }, { 0.55 }) }),

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
			\amp, Pfunc({ |e| (e[\energy] ? 0) * e[\accent] }),
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

	Pdef(m.ptn).set(\layer, 0);
	Pdef(m.ptn).set(\energy, 0);

	Pdef(m.ptn).play(TempoClock,quant:beat);
	Pdef(m.ptn).set(\bufnum, buffers[0]);

};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
		buffers.do({|buf|
			postf("buffer dealloc [%] \n", buf);
			buf.free;
		});
	};
};


//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity;
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var rate = m.rrateMassFiltered.linlin(0, 0.6 * sens,0.2,10.4);
	var amp = m.accelMassFiltered.lincurve(0, 0.6 * sens,0.0,1, 2);
	var layer = layerFloors.count({ |f| m.accelMassFiltered > (f * sens) });
	var roll = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0,1.0,0.5,1.0,0);
	var thr = (d.sensors.accelEvent.y.abs).lincurve(0,0.5,0.0,1.0,-2).asInteger;
	var ff = ((d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2).lincurve(-1.0,1.0,500,50.0,1);

	Pdef(m.ptn).set(\energy, amp*1 * vol);
	Pdef(m.ptn).set(\layer, layer);
	Pdef(m.ptn).set(\rate, roll);
	Pdef(m.ptn).set(\cutoff, ff);

	Pdef(m.ptn).set(\viewID, d.port);
  	Pdef(m.ptn).set(\startSize, 70 * amp);
  	Pdef(m.ptn).set(\endSize, 130 + (40 * amp));
  	Pdef(m.ptn).set(\startWidth, (2.pow(amp)));

	Pdef(m.ptn).set(\modulation, (
			type: \radial,
			freq: 1 ,
			amp: 1 + (1 * amp),
			harmonics: 2
	));

	bi = (d.sensors.gyroEvent.y / pi.half).linlin(-1.0,1.0,0,buffers.size-1);
	bi = bi.asInteger;

	Pdef(m.ptn).set(\startColor, Color.hsv(bi/buffers.size,1,1.0,0.5));
	Pdef(m.ptn).set(\endColor, Color.hsv(bi/buffers.size,1,1.0,0.1));

	if(m.accelMassFiltered > 0.01,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:beat);
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
	// [d.sensors.gyroEvent.y / pi.half, (d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2,d.sensors.accelEvent.y.abs,(d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];
	[(d.sensors.gyroEvent.y / pi.half)];
	// [m.accelMassFiltered.lincurve(0, 0.6, 0, layers.size - 1, 1).round / (layers.size - 1)];//layer
	// [m.accelMassFiltered / d.params.sensitivity] ++ layerFloors;//energy against the layer floors
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
