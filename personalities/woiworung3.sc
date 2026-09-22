var m = ~model;
var isPlaying = false;
var synth;
var notes = [43];
var lastTime=0;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.99;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.99;
// smooth
//------------------------------------------------------------

SynthDef("simple", {|out,freq = 1000, amp = 0.5, att = 2.02, dec = 0.3, sus = 1, rel = 4, gate = 1, fb = 1.2, ch=10|
	var snd, env;
	env = EnvGen.kr(Env.adsr(att, dec, sus, rel), gate: gate, doneAction: 2);

	snd = SinOsc.ar(freq, 0, 1);
	Out.ar(out, snd.tanh * env * amp);
}).add;


SynthDef("woiworung1", {|out,freq = 1000, amp = 0.5, att = 2.02, dec = 0.3, sus = 1, rel = 4, gate = 1, fb = 1.2, ch=10|
	var snd, env;
	env = EnvGen.kr(Env.adsr(att, dec, sus, rel), gate: gate, doneAction: 2);

	snd = SinOsc.ar(freq,
		LocalIn.ar(2) * LFNoise1.ar(0.1,2),
		LFNoise2.ar(ch.lag(0.3),1.7)
	).tanh * amp.lagud(0.1,0.1) * freq.linlin(50,800,1,0.007);
	2.do{
		snd = AllpassL.ar(snd,0.3,{0.1.rand+0.03}!2,5)
	};
	Out.ar(out, snd.tanh * env);
}).add;

~init = ~init <> {
	synth = Synth(\woiworung1, [\freq, 300, \gate,1, \amp, 0]);
};

~deinit = ~deinit <> {
	synth.set(\gate,0);
	// synth.free; // for now
};

//------------------------------------------------------------
~next = {|d|

	// var amp = m.accelMassFiltered.lincurve(0,2,-20,-1,-2);
	var amp = m.rrateMassFiltered.lincurve(0,0.5,-20,-1,1);
	var ch = (m.accelMassFiltered).linlin(0.0,2.0,0.1,30);

	if(amp<19.neg,{amp=100.neg});

	synth.set(\amp, amp.dbamp);
	synth.set(\ch, ch);
	synth.set(\fb,10);

	synth.set(\freq, 200);

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	// [m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
		// [m.accelMassFiltered * 0.25];

			// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half).fold(-0.5,0.5) + 0.5];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
[m.rrateMassFiltered.linlin(0,0.4,0,1)];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
