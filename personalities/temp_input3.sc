var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------

SynthDef(\warmBass, { |out = 0, amp = 0.3, freq = 65, thresh = -26, full = 0.25,
	attack = 0.006, release = 0.2, fdecay = 0.28, width = 0.45,
	fmul = 2.5, fdepth = 4000, res = 1.6|

	var in = SoundIn.ar(0);
	var follower = Amplitude.kr(in, 0.001, 0.05);
	var trig = follower > thresh.dbamp;
	var vel = Latch.kr(follower, trig).linlin(0.0, full, 0.25, 1.0).clip(0.25, 1.0);

	var ampEnv = EnvGen.kr(Env.perc(attack, release, 1, [2, -4]), trig);
	var cutEnv = EnvGen.kr(Env.perc(attack, fdecay, 1, [2, -4]), trig);

	var f = freq.lag(0.003);
	var osc = VarSaw.ar(f * [0.996, 1.004], 0, width).sum * 0.4
		+ SinOsc.ar(f * 0.5, 0, 0.7);
	var cutoff = (f * fmul) + (cutEnv * vel * fdepth);
	var sig = MoogFF.ar(osc, cutoff.clip(40, 9000), res);

	Out.ar(out, (sig * ampEnv * vel * amp).tanh ! 2);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	synth = Synth(\warmBass, [\amp, 8, \thresh, -6]);

};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.free;
};

//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity;
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var notes = [0,3,5,10,12] + 36;
	var rt = (d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,0.0,notes.size,0).asInteger;

	synth.set(\freq, notes[rt].midicps);
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, cyan , magenta]??

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;

	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// [m.accelMass, m.accelMassFiltered];
	// [d.sensors.accelEvent.x.abs * d.sensors.accelEvent.y.abs *  m.accelMassFiltered] * 0.5;

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	[(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];
  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
