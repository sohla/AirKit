var m = ~model;
var buffer;

//------------------------------------------------------------
m.rrateMassFilteredAttack = 0.8;
m.rrateMassFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\stereoSamplerAM, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1, ts=1, cutoff=20000, rq=0.3|
	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: ts, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Pan2.ar(sig, pan.lag(2), amp * env);
    Out.ar(out, sig[0]);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var path = PathName("~/Downloads/yourDNASamples/STE-1004.wav");
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);

		Pdef(m.ptn,
			Pbind(
				\instrument, \stereoSamplerAM,
				\bufnum, buf,
				\octave, Pxrand([3], inf),
				\rate, Pwhite(1.3,1.6),
				\start, Pseq([0.04,0.1,0.28,0.525,0.7,0.75,0.86,0.9], inf),
				\note, Pseq([33].stutter(64*2), inf),
				\attack, 0.07,
				\release, 0.2,
				\legato, 1,
				\args, #[],
			)
		);
		Pdef(m.ptn).play(quant:0.04);
	});

};

~deinit = ~deinit <> {
	Pdef(m.ptn).stop;
	buffer.free;
	s.sync;
	Pdef(m.ptn).remove;
	postf("buffer dealloc [%] \n", buffer);
};

//------------------------------------------------------------
~next = {|d|

	// var dur = m.accelMassFiltered.lincurve(0,1,0.30,0.04,-1).lag(0.2);
	var leg= m.accelMassFiltered.linlin(0,1,0.6,0.2);
	var amp = m.gyroYFiltered.lincurve(-1.0,1.0,0.5,1,-2);

	var start = m.accelMass.linlin(0,0.5,0.5,0.8);
	// var amp = m.accelMass.lincurve(0,2.5,0,1.5,-5);
	var co = (d.sensors.gyroEvent.y / pi).linexp(-1,1,5540,14000);
	var pan = d.sensors.gyroEvent.z.linlin(-1,1,-1,1);
  var dur = m.gyroYFiltered.lincurve(-1.0,1.0,0.9,0.075);

	if(amp < 0.2, {amp = 0}, { amp = 3});

	// if(d.sensors.digiInEvent[0] == 1, {
	if(m.rrateMassFiltered > 0.032,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});

	Pdef(m.ptn).set(\amp, amp*1);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\cutoff, co);
	Pdef(m.ptn).set(\ts, leg);
	Pdef(m.ptn).set(\pan, pan);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [m.rrateMass, m.rrateMassFiltered];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	[d.sensors.gyroEvent.x / pi, d.sensors.gyroEvent.y / pi, d.sensors.gyroEvent.z / pi];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};