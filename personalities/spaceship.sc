var m = ~model;
var dev = ~device;
var group, synth, buffer;

var samplePath = "~/Downloads/yourDNASamples/planets/The Planets__10.wav";

var pitchRange = 7;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.06;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\spaceship, { |out = 0, buf = 0, amp = 0, gate = 1,
	loopStart = 1.0, loopEnd = 8.5, xfade = 2, rate = 1,
	shelfFreq = 90, shelfRs = 0.5, shelfDb = 0|

	var env = EnvGen.kr(Env.asr(2, 1, 3), gate, doneAction: 2);
	var sr = BufSampleRate.kr(buf);
	var startFrame = loopStart * sr;
	var fadeFrames = xfade * sr;
	var loopFrames = ((loopEnd - loopStart) * sr) - fadeFrames;

	var p = Phasor.ar(0, BufRateScale.kr(buf) * Lag.kr(rate, 0.3), 0, loopFrames);
	var mainPos = startFrame + fadeFrames + p;
	var fadePos = mainPos - loopFrames;
	var x = ((p - (loopFrames - fadeFrames)) / fadeFrames).clip(0, 1) * 0.5pi;

	var sig = (BufRd.ar(2, buf, mainPos, 0, 4) * x.cos) + (BufRd.ar(2, buf, fadePos.max(startFrame), 0, 4) * x.sin);
	sig = BLowShelf.ar(sig, Lag.kr(shelfFreq, 0.4), shelfRs, Lag.kr(shelfDb, 0.4));

	Out.ar(out, sig * env * Lag.kr(amp, 0.2) * 4);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;

	buffer = Buffer.read(s, PathName(samplePath).fullPath, action: { |b|
		postf("buffer alloc [%] \n", b);
	});
	s.sync;

	if (group.notNil) {
		synth = Synth(\spaceship, [\buf, buffer], group);
	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;
	var dyingBuffer = buffer;

	synth !? (_.set(\gate, 0));
	synth = nil;
	group = nil;
	buffer = nil;

	fork {
		3.5.wait;
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
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var energy = m.accelMassFiltered;
	var tilt = d.sensors.gyroEvent.y / pi.half;

	var amp = energy.lincurve(0, 2.0 * sens, -20, -1, -2);
	var shelfDb = energy.lincurve(0, 2.5 * sens, 0, 4, -1);
	var shelfFreq = energy.lincurve(0, 2.5 * sens, 160, 3420, 0);
	var rate = (tilt.clip(-1, 1) * pitchRange).midiratio;

	if(amp < -18, { amp = -110 });

	synth !? { synth.set(
		\amp, amp.dbamp * vol,
		\shelfDb, shelfDb,
		\shelfFreq, shelfFreq,
		\rate, 0.5
	) };
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
	// [(d.sensors.gyroEvent.y / pi.half).clip(-1, 1)];//pitch, ±pitchRange semitones

	[m.accelMass, m.accelMassFiltered];
};
