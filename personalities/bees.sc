var m = ~model;
var synth;
var srr = 0.5.rrand(1.7);

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;

//------------------------------------------------------------
SynthDef(\beeSynth1, { |out=0, rr=0.1, amp = 0.0, gate = 1, release = 2, af=264, bf=398|

	var ampa = Lag.ar(K2A.ar(rr), 0.7) * 2.0 * amp;

	var lrr = Lag.ar(K2A.ar(rr), 0.7);
	var pitch = LinLin.ar(lrr, 0.0, 1.0, af, bf);
	var ffrq = LinLin.ar(lrr, 0.0, 1.0, 811, 2398);
	var ramp = LinLin.ar(lrr, 0.0, 1.0, 5, 22);

	var sig = HPF.ar(
		LFSaw.ar(
			StandardL.ar(11, ramp) * 14 + pitch,
			1,
			0.9
		),
	ffrq)!2;

	var sub = SinOsc.ar(pitch * 0.5,0,0.4);
	sig = (sub) * EnvGen.kr(Env.adsr(0.1, 0.1, 7, release), gate: gate, doneAction: Done.freeSelf) * ampa.lag(0.1);
	Out.ar(out, sig);

}).add;

~init = ~init <> {
	synth = Synth(\beeSynth1, [\af, 600.rrand(300), \bf, 50.rrand(440)]);
	
};

~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------`------------------------
~next = {|d|

	var amp = m.accelMassFiltered.linlin(0,1,0,1.9);
	var rate = m.accelMassFiltered.lincurve(0.0,2.5 * srr,0.3,1.2,4 * srr);
	var rr = m.rrateMassFiltered.linlin(0,1,1,2.1);
	synth.set(\amp, amp * 0.4);
	synth.set(\rr, rate * rr);
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

