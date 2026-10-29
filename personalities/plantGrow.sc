var m = ~model;
var dev = ~device;
var group, synth, buffer;

var samplePath = "~/Downloads/yourDNASamples/plant/plant grow_2.wav";
// var samplePath = "~/Downloads/yourDNASamples/planetRadiation/The Planets_RADIATION_16.wav";
// var samplePath = "~/Downloads/yourDNASamples/planetSong/The Planets_magnetic storm song_8.wav";

var pushRate = 0.25;
var sinkRate = 0.03;
var grow = 0;
var lastNow;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.1;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\plantGrow, { |out = 0, buf = 0, amp = 0, gate = 1,
	lo = 0, hi = 0.6, scanRate = 0.3, grainDur = 0.2, overlap = 3,
	rate = 1, cutoff = 400, rq = 0.9, panSpread = 0.3|

	var env  = EnvGen.kr(Env.asr(1.5, 1, 2.5), gate, doneAction: 2);
	var dur  = Lag.kr(grainDur, 0.2);
	var trg  = Impulse.ar(overlap / dur);
	var span = Lag.kr([lo, hi], 0.5);
	var pos  = span[0] + (LFSaw.kr(scanRate).range(0, 1) * (span[1] - span[0]));
	var pan  = TRand.ar(panSpread.neg, panSpread, trg);

	var sig = GrainBuf.ar(2, trg, dur, buf, Lag.kr(rate, 0.3), pos, 2, pan) * 3;
	// sig = RLPF.ar(sig, Lag.kr(cutoff, 0.2).clip(1000, 12000), rq) * 2;

	Out.ar(out, sig * env * Lag.kr(amp, 0.1));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;

	buffer = Buffer.readChannel(s, PathName(samplePath).fullPath, channels: [0], action: { |b|
		postf("buffer alloc [%] \n", b);
	});
	s.sync;

	if (group.notNil) {
		synth = Synth(\plantGrow, [\buf, buffer], group);
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
		3.wait;
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

	var now = Main.elapsedTime;
	var dt = (now - (lastNow ? now)).clip(0, 0.25);

	var tilt = d.sensors.gyroEvent.y / pi.half;
	var roll = d.sensors.gyroEvent.x / pi;

	var amp = energy.lincurve(0, 2.0 * sens, -40, -1, -2);
	var cutoff = energy.lincurve(0, 2.5 * sens, 250, 7000, 3);
	var rate = energy.lincurve(0, 2.5 * sens, 0.3, 0.34, 0);

	var width = tilt.linlin(-1, 1, 0.05, 0.4);
	var lo, hi;

	lastNow = now;
	grow = (grow + (energy * pushRate * dt) - (sinkRate * dt)).clip(0, 1);
	lo = grow * (1 - width);
	hi = lo + width;

	if(amp < -38, { amp = -110 });

	synth !? { synth.set(
		\amp, amp.dbamp * vol,
		\cutoff, cutoff,
		\rate, rate,
		\lo, lo,
		\hi, hi,
		\grainDur, roll.linlin(-1, 1, 0.1, 0.3)
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
	// [m.accelMass, m.accelMassFiltered];

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right

	// COMPUTED values : this file's own ~next, recomputed
	// [m.accelMassFiltered.lincurve(0, 2.0, -40, -6, -2).dbamp];//amp
	// [m.accelMassFiltered.lincurve(0, 2.5, 250, 7000, 3) / 7000];//cutoff

	[m.accelMassFiltered, grow];
};
