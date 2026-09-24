var m = ~model;
var synth;
var buffer;
var lastTime = 0;
var roots = [0,4,-2,2];


m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.07;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\bufGrain, {|bufnum=0, out=0, amp=0.0, rate=1, start=0, pan=0, freq=440,
    attack=0.3, decay=0.1, sustain=0.8, release=5.2, gate=1, rezf=500|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction:2);
	var sub = LFTri.ar(66*rate*2,0,0.1).tanh;
	var sig = Splay.arFill(5,{|i|
		Ringz.ar(
			Warp1.ar(2, bufnum, start, rate * (i+1) , 0.3, windowRandRatio:0.3),
			rezf.lag(1) + (i * (rezf.lag(1) * 0.125)),
			0.13,
			0.05)
	},1,1,0);
	sig = sig.tanh + sub;
	sig = Greyhole.ar(sig[0], 0.3,0.3);
	sig = sig * env * amp.lag(0.4);
    Out.ar(out, ((0)!0 ++ sig));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/melSamples/mel_sing_dry-005.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\bufGrain,[\bufnum,buf, \rate, -8.midiratio, \gate, 1 ]);
	});
};

~deinit = ~deinit <> {
	synth.onFree({
		postf("buffer dealloc [%] \n", buffer);
		buffer.free;
	});	
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.accelMassFiltered.linlin(0, 4 * sens,0.00001,1);
	var start = m.gyroYFiltered.lincurve(-1.0,1.0,0.0,1.0,0);
	// var rezf = m.gyroZFiltered.lincurve(-1.0,1.0,100,1200,0);
	//(d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2
	var rezf = ((d.sensors.gyroEvent.y / pi)).lincurve(-0.5,0.5,200,1000,0);

	if(amp < 0.001, {amp = 0});

	if(m.accelMassFiltered<0.2,{
		if(TempoClock.beats > (lastTime + 0.3),{
			roots = roots.rotate(-1);
			// roots[0].postln;
		});
			lastTime = TempoClock.beats;
	});

	synth.set(\rezf, rezf);
	synth.set(\start, start);
	synth.set(\amp, amp * 0.4 * vol);
	synth.set(\rate, 0.5 * ((roots[0]).midiratio));

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
};
