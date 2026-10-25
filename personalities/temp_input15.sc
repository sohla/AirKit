var inputBus = 0;
var m = ~model;
var dev = ~device;
var group, trig, buffer;
var hitKey = ('hit_' ++ m.ptn).asSymbol;
// 
var samplePath = "~/Downloads/melSamples/mel_sing_dry-005.wav";
// var samplePath = "~/Downloads/nicSamples/twoNote/nic_46_f4g4.wav";

var sampleRoot = 60;
var pitches = [55, 58, 60, 63, 65, 67, 70];

var imu = {
	var tilt = dev.sensors.gyroEvent.y / pi.half;
	var turn = dev.sensors.gyroEvent.z / pi;
	var roll = dev.sensors.gyroEvent.x / pi;
	(
		rate: (pitches.clipAt(tilt.linlin(-1, 1, 0, pitches.size).asInteger) - sampleRoot).midiratio,
		pos: turn.linlin(-1, 1, 0, 1),
		grainDur: roll.linexp(-1, 1, 0.01, 0.4)
	)
};

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------

SynthDef(\inputTrigger15, { |inputBus = 0, ratio = 3.0, floorDb = -48,
	deadtime = 0.10, slowAtk = 0.300, slowRel = 0.150, inGain = 0.7,
	fullScale = 0.35, curve = 1.5, window = 0.005|

	var in   = SoundIn.ar(inputBus) * inGain;
	var fast = Amplitude.kr(in, 0.001, 0.05);
	var slow = LagUD.kr(fast, slowAtk, slowRel);
	var over = fast > ((slow * ratio) + floorDb.dbamp);
	var edge = over > Delay1.kr(over);
	var trg  = Trig1.kr(edge, deadtime);

	var peak   = RunningMax.kr(fast, trg);
	var report = TDelay.kr(trg, window);

	var vel = Latch.kr(peak, report).linlin(0.0, fullScale, 0.0, 1.0).clip(0, 1).pow(curve);

	SendReply.kr(report, '/akHit', [vel]);
}).add;

//------------------------------------------------------------

SynthDef(\grainCloud15, { |out = 0, buf = 0, amp = 0.3, vel = 1,
	numGrains = 40, tail = 2, tailCurve = -4,
	rate = 1, pos = 0.5, posSpread = 0.02, grainDur = 0.1, panSpread = 0.6|

	var density = EnvGen.ar(Env([1, 0], [tail]));
	var trg     = Dust.ar(2 * numGrains / tail * density);
	var level   = EnvGen.ar(Env([0, 1, 0], [0.002, tail], [0, tailCurve]), doneAction: 2);

	var grainPos = (Lag.kr(pos, 0.1) + TRand.ar(posSpread.neg, posSpread, trg)).wrap(0, 1);
	var grainPan = TRand.ar(panSpread.neg, panSpread, trg);

	var sig = GrainBuf.ar(2, trg, Lag.kr(grainDur, 0.1), buf, Lag.kr(rate, 0.02), grainPos, 2, grainPan);

	Out.ar(out, sig * level * vel * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;

	buffer = Buffer.readChannel(s, PathName(samplePath).fullPath, channels: [0], action: { |b|
		postf("buffer alloc [%] \n", b);
	});

	trig = Synth(\inputTrigger15, [\inputBus, inputBus, \ratio, 0.3, \slowRel, 0.25, \floorDb, -52,
		\fullScale, 0.7, \curve, 0.5, \deadtime, 0.02], group);

	OSCdef(hitKey, { |msg|
		if (msg[1] == trig.nodeID) {
			var vel = msg[3];
			var now = imu.();

			Synth(\grainCloud15, [
				\buf, buffer,
				\amp, dev.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1) ,
				\vel, vel,
				\numGrains, vel.linlin(0, 1, 10, 100).round,
				\tail, vel.linexp(0, 1, 0.1, 1.0),
				\rate, now.rate,
				\pos, now.pos,
				\grainDur, 0.1,//now.grainDur
			], group);
		};
	}, '/akHit', s.addr);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;
	var dyingBuffer = buffer;

	OSCdef(hitKey) !? (_.free);
	trig !? (_.free);
	trig = nil;
	group = nil;
	buffer = nil;

	fork {
		8.5.wait;
		dying !? (_.free);
		s.sync;
		dyingBuffer !? { postf("buffer dealloc [%] \n", dyingBuffer); dyingBuffer.free };
	};
};

//------------------------------------------------------------
~next = {|d|
	var now = imu.();

	group !? { group.set(\rate, now.rate, \pos, now.pos, \grainDur, now.grainDur) };
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
	[(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];
	// [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
};
