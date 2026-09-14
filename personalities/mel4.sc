var m = ~model;
var synth;
var buffer;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.2;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\bufGrainM, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=5.2, gate=1,cutoff=20000, rq=1, rezf=200|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction:2);
	var sig = Splay.arFill(2,{|i|
			Warp1.ar(2, bufnum, start, rate * (i+1) , 0.3, -1, 8, windowRandRatio:0.3)
	},1,1,0);
  	sig = Compander.ar(sig, sig,
        thresh: 0.01,
        slopeBelow: 1,
        slopeAbove: 0.1,
        clampTime:  0.01,
        relaxTime:  0.01
    );
	sig = sig[0] * env * amp;
    Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/melSamples/mel_mouth2.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\bufGrainM,[\bufnum,buf, \rate, 1, \gate, 1 ]);
	});
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.onFree({
		postf("buffer dealloc [%] \n", buffer);
		buffer.free;
	});	
	synth.set(\gate, 0);
};
//------------------------------------------------------------
~next = {|d|
	var amp = m.accelMassFiltered.linlin(0,2,0.00001,1);
	var rate =  m.gyroYFiltered.lincurve(-1.0,1.0,0.1,2.0,0);
	var start = m.gyroZFiltered.lincurve(-1.0,1.0,0.0,1.0,0);

	if(amp < 0.01, {amp = 0});

	synth.set(\start, start);
	synth.set(\rate, rate);
	synth.set(\amp, amp * 20);

};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	[d.sensors.gyroEvent.x/pi];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};
