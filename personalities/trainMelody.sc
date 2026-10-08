var m = ~model;
var dur = 0.22/2;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\funMelody, {
    |out=0, freq = 440, gate = 1, amp = 0.8, filtFreq = 2000, filtRes = 0.5, envAtk = 0.01, envDec = 0.1, envSus = 0.7, envRel = 0.2, pan = 0.0|
    var osc1, osc2, osc3, env, filter, output;
    env = EnvGen.ar(Env.adsr(envAtk, envDec, envSus, envRel), gate, doneAction: Done.freeSelf);
    osc1 = Saw.ar(freq, 1.0);
    osc2 = Pulse.ar(freq * 0.99, 0.5, 0.5);
    osc3 = SinOsc.ar(freq * 1.01, 0, 1.0);
    output = Mix([osc1, osc2, osc3]) * env * amp;
    filter = RLPF.ar(output, filtFreq, filtRes).tanh;
		// filter = ([filter, DelayN.ar(filter, 0.5, 0.5)+filter] * 2).tanh;

    Out.ar(out, Pan2.ar(filter,pan));
}).add;


//------------------------------------------------------------
~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \funMelody,
			\note, Pseq([12,14,10,7,0]-1, inf),
			// \octave,Pseq([5,6].stutter(2),inf),
			// \root, Pseq([0].stutter(32), inf),
			\envAtk, Pwhite(0.002,0.04, inf),
			\envDec, Pwhite(0.2, 0.1, inf),
			\envSus, 0.0,
			// \envRel,Pkey(\octave) * 0.4,
    		// \amp, Pkey(\octave).reciprocal * 0.13,
			\pan, Pseq([-0.3,0.3], inf),
    		\filtRes, 0.8,
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:dur);
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

};

//------------------------------------------------------------

~onEvent = {|e|
	Pdef(m.ptn).set(\root, m.com.root+5);
};

//------------------------------------------------------------
~next = {|d|

	// var dur = 0.5 * 2.pow(m.accelMassFiltered.linexp(0,3,0,5).floor).reciprocal;
	// var dur = 0.5 * 2.pow(m.accelMassFiltered.lincurve(0,2.5,0,3,-1).floor).reciprocal;
	var oct = m.gyroYFiltered.linlin(-1,1,6,3).floor;
  	var envRel = m.accelMassFiltered.lincurve(0,1,0.5,0.6,2);
	var amp = m.accelMassFiltered.lincurve(0,2,0.001,0.5,-2);

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\filtFreq, m.accelMassFiltered.linexp(0,1.5,180,800));
	Pdef(m.ptn).set(\amp, amp*0.7);
	Pdef(m.ptn).set(\octave,oct);
	Pdef(m.ptn).set(\envRel,envRel);

	if(m.accelMass > 0.12,{
		if( Pdef(~model.ptn).isPlaying.not,{
			Pdef(~model.ptn).resume(quant:dur);
		});
	},{
		if( Pdef(~model.ptn).isPlaying,{
			Pdef(~model.ptn).pause();
		});
	});
};

~nextMidiOut = {|d|
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	[m.accelMass * 0.1, m.accelMassFiltered * 0.1];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

