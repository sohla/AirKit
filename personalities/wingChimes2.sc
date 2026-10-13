var m = ~model;


	SynthDef(\wingChimes2, {|freq = 1000, pulseFreq = 10, amp = 0, rq = 0.001, att = 0.23, dec = 1.3, sus = 0, rel = 3, gate = 1, numHarms = 200|
		var snd, env;
		env = EnvGen.kr(Env.adsr(att, dec, sus, rel), gate: gate, doneAction: 2);
		snd = BPF.ar(
			in: WhiteNoise.ar(Select.ar(0,
				[
					Blip.ar(pulseFreq, numHarms, 0.5),
					LFPulse.ar(pulseFreq,0,0.5) * 0.1
				]
			)),
			freq: [freq, freq + 5] ,
			rq: Lag.kr(rq, 1));
		snd = snd * env * Lag.kr(amp, 1) * 20;
		// snd = Clip.ar(snd, -0.5, 0.5);
		// snd = BLowShelf.ar(snd,200,1,4);
		Out.ar(0, snd);
	}).add;

//------------------------------------------------------------
~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \wingChimes2,
			\note, Prand([0,7,14,21], inf),
			\octave, Pseq([3,4], inf),
			\pulseFreq, Pwhite(1, 3),
			\numHarms, 30,
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);
	Pdef(m.ptn).play(quant:[0.1]);
};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
~onEvent = {|e|
	Pdef(m.ptn).set(\root, m.com.root);
};

//------------------------------------------------------------
~next = {|d|

	var dur = 0.3;// * 2.pow(m.accelMassFiltered.linlin(0,4,0,4).floor).reciprocal;
	// var rq = m.accelMassFiltered.linexp(0,4,0.01,0.0001);
	var amp = m.accelMassFiltered.linexp(0,2,1,30);

	Pdef(m.ptn).set(\dur, dur);
	// Pdef(m.ptn).set(\rq, rq);
	Pdef(m.ptn).set(\amp, amp * 4);

	if(m.accelMass > 0.1,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).play(quant:[0.1,0,0,0]);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).stop();
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	// [m.accelMass * 0.1, m.accelMassFiltered * 0.1];
	[m.rrateMass];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};
