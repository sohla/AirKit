var m = ~model;
var synth;
var buffer;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.9;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;


//------------------------------------------------------------
SynthDef(\monoSampler, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.0, release=0.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], Lag.kr(pan, 3),  env);
		sig = Compander.ar(sig, sig,
						thresh: -32.dbamp,
						slopeBelow: 1,
						slopeAbove: 0.5,
						clampTime:  0.02,
						relaxTime:  0.01
				);
    Out.ar(out, sig * amp);
}).add;

SynthDef(\pullstretchMonoQ, {|out, amp = 1, buffer = 0, envbuf = -1, pch = 1.0, div=1, speed = 0.01, splay = 0.4 ,pan=0, rate=1|
	var pos;
	// var mx,my;
	var sp;
	var mas;
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1,0.5,0.5);
	// my = MouseY.kr(0.01,1,1.0);//splay


	sp = Splay.arFill(4,
		{ |i| Warp1.ar(1, buffer, lfo.linlin(0,1,0.05,0.95), rate,splay, envbuf, 8, 0.3, 4)  },
			1,
			1,
			0
	) ;

	mas = HPF.ar(sp,245);

  mas = FreeVerb.ar(mas,0.5);

	Out.ar(out,Pan2.ar(mas[0],pan.lag(1))* amp.lag(1));
}).add;
//------------------------------------------------------------
~init = ~init <> {
	// var path = PathName("~/Downloads/yourDNASamples/HK laughing2-glued.wav");
	var path = PathName("~/Downloads/yourDNASamples/violin/Violin_04.wav");

	// var path = PathName("~/Downloads/yourDNASamples/HK lots of teddies.wav");
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMonoQ,[\buffer,buf,\pch,-12.midiratio, \amp,0.4, \div, 10]);
	});
};

~deinit = ~deinit <> {
	synth.free;
	buffer.free;
};


//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity;
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.accelMass.linlin(0, 1 * sens,0.00001,1);
	var speed= m.accelMassFiltered.lincurve(0.1, 1 * sens,0.01,2,-2);
	var rate = m.gyroYFiltered.linlin(-1,1,1,2).asInteger;
	var pan = m.gyroZFiltered.linlin(-1,1,-1,1);

	if(amp < 0.01, {amp = 0});

	synth.set(\rate, rate);
	synth.set(\speed, speed);
	synth.set(\amp, amp * 12 * vol);
	synth.set(\pan, pan);
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
