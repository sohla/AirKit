var m = ~model;
var synth;
var nvoices = 8;

m.accelMassFilteredAttack = 0.95;
m.accelMassFilteredDecay = 0.7;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.8;
m.gyroFilteredDecay = 0.8;

//------------------------------------------------------------

SynthDef(\gendyDrone, { |out = 0, amp = 0.0, gate = 1,
	freq = 45, detune = 0.12, ampdist = 1, durdist = 0,
	adparam = 0.001, ddparam = 1, ampscale = 0, durscale = 0.0,
	atk = 2.0, rel = 6.0, glide = 0.6,
	cutoff = 6000, roomsize = 1, revtime = 0.1, revmix = 0.35,
	shelfFreq = 250, shelfDb = -2|

	var env = EnvGen.kr(Env.asr(atk, 1, rel), gate, doneAction: Done.freeSelf);
	var f   = freq.lag(glide);
	var det = detune.lag(glide);

	var dry = Splay.ar({
		Gendy1.ar(ampdist, durdist, adparam, ddparam,
			f, f + (f * det), ampscale, durscale, mul: 1)
	} ! nvoices).softclip * 3;

	var wet = GVerb.ar(dry.sum * 0.5, roomsize, revtime);
	var sig = (dry * (1 - revmix)) + (wet * revmix);

	sig = LPF.ar(sig, cutoff.clip(120, 18000).lag(glide));
	sig = BLowShelf.ar(sig, shelfFreq, db: shelfDb);

	Out.ar(out, (sig * env * amp.lag(glide)).softclip);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	synth = Synth(\gendyDrone, [\gate, 1, \amp, 0]);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth !? { |x| x.set(\rel, 4.0); x.set(\gate, 0) };
	synth = nil;
};

//------------------------------------------------------------
~next = {|d|
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
 
	var sens = d.params.sensitivity;
	var amp    = m.accelMassFiltered.lincurve(0.0, 1.0 * sens, -50, -5, -2);
	var freq   = (d.sensors.gyroEvent.y / pi.half).linexp(-1.0, 1.0, 20, 90);
	var detune = m.rrateMassFiltered.lincurve(0.0, 1.2, 0.10, 0.45, -1);
	var cutoff = m.accelMassFiltered.linexp(0.0, 2.0, 700, 9000);

	synth !? { |x|
		x.set(\amp, amp.dbamp * vol);
		x.set(\freq, freq);
		x.set(\detune, detune);
		x.set(\cutoff, cutoff);
	};
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
	[m.accelMass, m.accelMassFiltered];
	// [d.sensors.accelEvent.x.abs * d.sensors.accelEvent.y.abs *  m.accelMassFiltered] * 0.5;

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];
  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
