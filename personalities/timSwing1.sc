var m = ~model;
var isPlaying = false;
var synth;
var notes = [36,43,48];
m.accelMassFilteredAttack = 0.6;
m.accelMassFilteredDecay = 0.2;

//------------------------------------------------------------
SynthDef("woiworung2", {|out,freq = 1000, amp = 0.5, att = 0.02, dec = 0.3, sus = 1, rel = 5, gate = 1, fb = 1.2, ch=10|
	var snd, env;
	env = EnvGen.kr(Env.adsr(att, dec, sus, rel), gate: gate, doneAction: 2);

	snd = SinOsc.ar(freq,
		LocalIn.ar(2) * LFNoise1.ar(0.1,1),
		LFNoise1.ar(ch.lag(0.3),7)
		// LFNoise1.ar(MouseY.kr(0.2,19),MouseX.kr(0.1,4.1))
	).tanh * amp.lag(0.3);
	2.do{
		snd = AllpassL.ar(snd,0.3,{0.1.rand+0.03}!2,5)
	};
	Out.ar(out, snd.tanh * env);
}).add;

~init = ~init <> {
	synth = Synth(\woiworung2, [\freq, notes[0].midicps, \gate, 1, \amp, 0]);
};

~deinit = ~deinit <> {
	synth.set(\gate,0);
	// synth.free; // for now
};

//------------------------------------------------------------
~next = {|d|

	var a = m.accelMassFiltered.linlin(0,1,0.0,0.6);
	var ch = m.accelMassFiltered.linlin(0.0,2.5,0.02,44);
	// var pchs = [0,12,24,36,48];
	// var i = (d.sensors.gyroEvent.y.abs / pi) * (pchs.size);
	if(a<0.01,{a=0});
	if(a>0.9,{a=1.0});
	synth.set(\amp, a * 0.8);
	synth.set(\ch, ch);
	synth.set(\freq,(notes[0] + m.com.root).midicps);
	a = m.accelMassFiltered * 0.1;
	if(a < 0.02, {
		if(isPlaying.not,{
			isPlaying = true;
			notes = notes.rotate(-1);
			// synth.set(\freq,(notes[0] + m.com.root).midicps);
		})
	},{
		if(isPlaying,{
			isPlaying = false;
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
