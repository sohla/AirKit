var m = ~model;
var buffer;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;


//------------------------------------------------------------
SynthDef(\stereoSampler1, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: 1,doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.013], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    // sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig * amp * env);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var path = PathName("~/Downloads/yourDNASamples/violin/Violin_04.wav");
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		Pdef(m.ptn,
			Pbind(
				\instrument, \stereoSampler1,
				\bufnum, buf,
				// \octave, Pxrand([3], inf),
				// \note, Pxrand([33,35,37], inf),
				\note, Pxrand([33,38,45,52].stutter(4), inf),
				\decay, 0.2,
				\sustain,0.1,
				\release,3.2,
				\rate, 0.25,
				// \dur, Pseq([0.25], inf),
				\args, #[],
			)
		);
		Pdef(m.ptn).play(quant:0.125);
	});
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	postf("buffer dealloc [%] \n", buffer);
	buffer.free;
};

//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.linlin(0,1,0.5,0.03);
	var start = m.gyroXFiltered.lincurve(0.0,1.0,0.02,0.4,-2);
	var amp = m.accelMassFiltered.lincurve(0,0.3,0,1,-2);
	var rate= m.accelMass.linlin(0,1,0,2);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,2,7,3).floor;

	if(amp < 0.03, {amp = 0});

	Pdef(m.ptn).set(\octave, 2 + oct);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, amp * 15);
 	Pdef(m.ptn).set(\start, start.linlin(0,1,0.02,0.09));

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	[m.accelMass * 0.2, m.rrateMass, (m.rrateMass + m.accelMass) * 0.2];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMass, m.rrateMassFiltered, d.sensors.rrateEvent.x];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
