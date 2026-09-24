var m = ~model;
var synth;
var buffer;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.9;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\pullstretchMonoQm2, {|out, amp = 1, buffer = 0, envbuf = -1, pch = 1.0, div=1, speed = 0.01, splay = 0.4 ,pan=0, gate=1|
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1).range(0.0,0.7);
	var sp = Splay.arFill(4,
		{ |i| Warp1.ar(1, buffer, lfo.linlin(0,1,0.05,0.95), pch * (0.25/2) * (2*(i+1)),splay, envbuf, 8, 0.1 * (i+1), 4)  },
			1,
			1,
			0
	) ;
	var env = EnvGen.ar(Env.adsr(0.4,0.1,0.9,2.0), gate, doneAction:2);
	var mas = HPF.ar(sp,45);
	var sig = Pan2.ar(mas,pan)* amp.lag(1) * env;
	Out.ar(out, ((0)!0 ++ sig));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/melSamples/mel_sing_dry-004.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMonoQm2,[\buffer,buf,\pch,0.midiratio, \amp,0.4, \div, 10]);
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
	var sens = d.params.sensitivity;
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.accelMass.linlin(0, 4 * sens,0.00001,1);
	var speed= m.accelMassFiltered.lincurve(0.5, 5 * sens,0.01,1,-2);
	var rate = m.accelMassFiltered.linlin(0, 2 * sens,0.9,1.4);
	var pch = m.gyroZFiltered.linlin(-1,1,0,1).round * 12;

	if(amp < 0.01, {amp = 0});

	synth.set(\pch, (pch + -4).midiratio);
	synth.set(\speed, speed);
	synth.set(\amp, amp * 0.8 * vol);
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];

};
