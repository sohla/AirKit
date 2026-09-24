var m = ~model;
var group;

m.accelMassFilteredAttack = 0.8;
m.accelMassFilteredDecay = 0.15;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------

SynthDef(\movingBeast, { |out = 0, freq = 45, amp = 0.2, gate = 1,
	atk = 0.08, dec = 0.3, sus = 0.7, rel = 1.2, crv = -2,
	harm = 3, growl = 0.6, harmLag = 1.4, growlLag = 0.08,
	detune = 1.003, fbDepth = 2,
	ffreq = 900, rq = 0.4, pan = 0|

	var env = EnvGen.kr(Env.adsr(atk, dec, sus, rel, 1, crv), gate, doneAction: 2);
	var h   = harm.clip(1, 24).round.lag(harmLag);
	var g   = growl.lag(growlLag);

	var in  = LocalIn.ar(2);
	var sig = LFTri.ar(freq * [1.0, detune], 0,
		SinOsc.ar(freq * h, in * g, fbDepth));

	LocalOut.ar(sig);

	sig = RLPF.ar(sig, ffreq.clip(40, 18000).lag(0.05), rq);
	sig = Balance2.ar(sig[0], sig[1], pan, env * amp);

	Out.ar(out, sig.tanh);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \movingBeast,
			\group, group,
			// \dur, Pseq([1.5, 0.5, 0.5, 1, 2.5] * 0.2, inf),
			\legato, Pseq([0.9, 0.6, 0.6, 0.8, 1.0], inf),
			\scale, Scale.minor,
			\root, 0,
			\octave, Pseq([1, 3, 2, 4] + 1, inf),
			\note, Pseq([0, 0, 3, 5, 3, 0, -2], inf),
			\atk, 0.003,
			\dec, 0.2,
			\sus, 0.1,
			// \rel, 0.2,
			\detune, 1.003,
			\rq, 0.4,
			\pan, Pwhite(-0.35, 0.35),
			\args, #[],

		);
	);

	Pdef(m.ptn).play(quant: 0.5);
	Pdef(m.ptn).pause;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

	fork {
		if (group.notNil) {
			group.set(\gate, 0);
			1.8.wait;
			group.free;
			group = nil;
		};
	};
};

//------------------------------------------------------------
~next = {|d|
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var sens = d.params.sensitivity;
	var dur = m.accelMassFiltered.lincurve(0, 2.5 * sens, 0.4, 0.1, -1);
	var amp = m.accelMassFiltered.lincurve(0.0, 2.0 * sens, -40, -10, -2);
	var ff  = m.rrateMassFiltered.linexp(0.0, 2.5 * sens, 180, 900);
	var rel  = m.rrateMassFiltered.lincurve(0.0, 2.5 * sens, 0.2, 3.2,-2);
	var fb  = (d.sensors.gyroEvent.y / pi.half).lincurve(-1.0, 1.0, 0.1, 2.0, -1);
	var harm  = (d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 1, 3);
	var growl = (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).lincurve(-0.5, 0.5, 1, 4, 2);

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol); 
	Pdef(m.ptn).set(\fbDepth, fb);
	Pdef(m.ptn).set(\harm, harm);
	Pdef(m.ptn).set(\growl, growl);
	Pdef(m.ptn).set(\rel, rel * 0.9);

	if (m.accelMassFiltered > 0.05, {
		if (Pdef(m.ptn).isPlaying.not, { Pdef(m.ptn).resume(quant: 0.5) });
	}, {
		if (Pdef(m.ptn).isPlaying, { Pdef(m.ptn).pause });
	});
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
