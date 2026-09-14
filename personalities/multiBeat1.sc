var m = ~model;
var group;

var divs = [1, 2, 4] * 2;
var pool = [0, 4, 7, 11, 12, 11, 7, 2];

var divPat  = Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx));
var stepPat = Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx));
var notePat = Pswitch(divs.collect({ |n| Pseq(pool.keep(n), 1) }), Pkey(\divIdx));

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\multiBeatVoice, {|out=0, freq=440, amp=0.2, pan=0,
    attack=0.005, release=0.4, ffreq=3000|
	var env = EnvGen.kr(Env.perc(attack, release), doneAction: 2);
	var sig = Pulse.ar(freq, 0.3, 0.5) + SinOsc.ar(freq / 2, 0, 0.6);
	sig = RLPF.ar(sig, ffreq.clip(80, 12000), 0.4);
	Out.ar(out, Pan2.ar(sig, pan, amp * env));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \multiBeatVoice,
			\group, group,

			\div,  divPat,
			\step, stepPat,
			\note, notePat,
			\root, Pseq([0,3,-2,1].stutter(32), inf),
			\octave, Prand([4,5,6], inf),
			\dur,  Pkey(\div).reciprocal * 0.5,
			\pan, Pwhite(-0.2, 0.2),

			\type, \customVisualEvent,
			\shape, \circle,
			\sx, (Pkey(\step) / Pkey(\div) * 1.6) - 0.8,
			\ex, Pkey(\sx),
			\startSize, Pkey(\amp) * 400,
			\endSize, Pkey(\amp) * 8,
			\startColor, Color.new(0.6, 1.0, 0.8),
			\endColor, Color.new(0.1, 0.4, 0.6).alpha_(0.0),
			\startWidth, 3,
			\endWidth, 0.4,
			\duration, Pkey(\dur) * 8,
			\func, Pfunc({|e| ~onEvent.(e)}),

			\args, #[],
		)
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\ffreq, 2000);

	Pdef(m.ptn).play(quant: 1);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
	};
};

//------------------------------------------------------------
~onEvent = {|e|

	m.com.root = e.root;
	m.com.dur = e.dur;
};

//------------------------------------------------------------
~next = {|d|
	var idx = m.accelMassFiltered.lincurve(0, 2.5, 0, divs.size - 1, 1)
		.round.asInteger.clip(0, divs.size - 1);
	var amp = m.accelMassFiltered.lincurve(0, 0.2, -60, -16, -1);
	var ffreq = m.accelMassFiltered.lincurve(0, 1.0, 700, 6000, 2);
	var rel = m.accelMassFiltered.lincurve(0, 1.0, 3.1,0.2, 2);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\ffreq, ffreq);
	Pdef(m.ptn).set(\attack, 0.0003);
	Pdef(m.ptn).set(\release, rel * 0.5);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, magenta, cyan]

	// RAW values
	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// Gyro
	// [(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];//left right

	// MODEL values
	// Acceleration
	// [m.accelMass, m.accelMassFiltered].lincurve(0.0,5.0,0.0,1.0,0);
	// Rotation Rate
	// [m.rrateMass, m.rrateMassFiltered].lincurve(0.0,1.0,0.0,1.0,0);

	// COMPUTED values : this file's own ~next, recomputed
	// [m.accelMassFiltered.lincurve(0, 1.5, 0, divs.size - 1, 1).round / (divs.size - 1)];//divIdx
	// [m.accelMassFiltered.lincurve(0, 1.0, -60, -12, -1).dbamp];//amp
	// [m.accelMassFiltered.lincurve(0, 1.0, 700, 6000, 2) / 6000];//ffreq
	// [m.accelMassFiltered.lincurve(0, 1.0, 0.1, 3.2, 2) * 0.1];//release

	[m.accelMass, m.accelMassFiltered];
};
