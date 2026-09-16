var m = ~model;
var isPlaying = false;
var synth;
var notes = 58 + [4,-10,4,8-12,-10,6-12,4];
var lastTime=0;
m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.9;
// smooth
//------------------------------------------------------------

SynthDef("woiworung1", {|out,freq = 1000, amp = 0.5, att = 2.02, dec = 0.3, sus = 1, rel = 4, gate = 1, fb = 1.2, ch=10|
	var snd, env;
	env = EnvGen.kr(Env.adsr(att, dec, sus, rel), gate: gate, doneAction: 2);

	snd = SinOsc.ar(freq,
		LocalIn.ar(2) * LFNoise1.ar(0.1,2),
		LFNoise2.ar(ch.lag(0.3),1.7)
	).tanh * amp.lagud(0.1,2.3) * freq.linlin(50,800,1,0.007);
	2.do{
		snd = AllpassL.ar(snd,0.3,{0.1.rand+0.03}!2,5)
	};
	Out.ar(out, snd.tanh * env);
}).add;

~init = ~init <> {
	synth = Synth(\woiworung1, [\freq, (60+4).midicps, \gate,1, \amp, 0]);
};

~deinit = ~deinit <> {
	synth.set(\gate,0);
	// synth.free; // for now
};

//------------------------------------------------------------
~next = {|d|

	var a = m.accelMassFiltered.lincurve(0,2,0.0,0.15,-2);
	var ch = (m.accelMassFiltered * 0.25).linlin(0.0,1.0,0.1,10);
	// var pchs = [0,12,24,36,48];
	// var i = (d.sensors.gyroEvent.y.abs / pi) * (pchs.size);
	if(a<0.1,{a=0});
	if(a>0.9,{a=1.0});
	synth.set(\amp, a * 0.25);
	synth.set(\ch, ch);

	a = m.accelMassFiltered * 0.1;
	if(a < 0.003, {
		if(isPlaying.not,{
			isPlaying = true;
			// notes[0].postln;
		})
	},{
		if(isPlaying,{
			isPlaying = false;
			notes = notes.rotate(-1);
			synth.set(\freq,notes[0].midicps);
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	// [m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
		[m.accelMassFiltered * 0.25];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
