var m = ~model;
var dev = ~device;
var group, buffer;


m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\spaceship2, { |out = 0, buf = 0, amp = 0, gate = 1, start = 1.0, rate = 1, attack = 0.005, release = 0.08, shelfFreq = 90, shelfRs = 0.5, shelfDb = 0|
	var env = EnvGen.kr(Env.asr(attack, 1, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, buf, BufRateScale.kr(buf) * rate, 1, start * BufSampleRate.kr(buf));
	sig = BLowShelf.ar(sig, shelfFreq, shelfRs, shelfDb);
	Out.ar(out, sig * env * amp * 4);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var samplePath = "~/Downloads/yourDNASamples/planets/The Planets__14.wav";

	group = Group.new;

	buffer = Buffer.read(s, PathName(samplePath).fullPath, action: { |b|
		postf("buffer alloc [%] \n", b);
	});
	s.sync;

	Pdef(m.ptn,
		Pbind(
			\instrument, \spaceship2,
			\group, group,
			\buf, buffer,
			// \dur, Pfunc({ |e| e[\step] ? 0.5 }),
			\slice, Pseq((0..15) / 16, inf),
			\start, Pkey(\slice).linlin(0, 1, 1.0, 8.5),
			\rate, 0.25,
			\legato, 0.7,
			\attack, 0.0002,
			\release, 0.02,
		)
	);

	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).play(quant: 1);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;
	var dyingBuffer = buffer;

	group = nil;
	buffer = nil;

	Pdef(m.ptn).remove;

	fork {
		1.0.wait;
		if (dying.notNil) {
			s.bind { dying.freeAll };
			s.sync;
			dying.free;
		};
		dyingBuffer !? {
			postf("buffer dealloc [%] \n", dyingBuffer);
			dyingBuffer.free;
		};
	};
};

//------------------------------------------------------------
~next = { |d|
	var steps = [1, 0.5, 0.25, 0.125, 0.0625];
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var energy = m.accelMassFiltered;

	var amp = energy.lincurve(0, 2.0 * sens, -30, -12, -2);
	var shelfDb = energy.lincurve(0, 2.5 * sens, 0, 4, -1);
	var shelfFreq = energy.lincurve(0, 2.5 * sens, 160, 3420, 0);
	var stepIdx = energy.lincurve(0, 2.5 * sens, 0, steps.size - 1, 0).round.asInteger.clip(0, steps.size - 1);
	var dur = energy.lincurve(0, 2.5 * sens, 0.4, 0.1, 0);


	if(amp < -28, { amp = -110 });

	Pdef(m.ptn).set(\amp, amp.dbamp * vol * 0.3);
	Pdef(m.ptn).set(\shelfDb, shelfDb);
	Pdef(m.ptn).set(\shelfFreq, shelfFreq);
	// Pdef(m.ptn).set(\step, steps[stepIdx]);
	Pdef(m.ptn).set(\dur, 0.2);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;

	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right

	// COMPUTED values : this file's own ~next, recomputed
	// [m.accelMassFiltered.lincurve(0, 2.0, -40, -6, -2).dbamp];//amp
	// [m.accelMassFiltered.lincurve(0, 2.5, 0, 6, -1) / 6];//shelfDb
	// [m.accelMassFiltered.lincurve(0, 2.5, 0, 4, 0).round / 4];//speed step index

	[m.accelMass, m.accelMassFiltered];
};
