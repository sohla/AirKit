var inputBus = 0;
var m = ~model;
var dev = ~device;
var group, trig, voice;
var hitKey = ('hit_' ++ m.ptn).asSymbol;

var root = 38 - 12;
var notes = [0, 3, 7, 10, 12];
var filterSteps = [1, 2.5, 0.6, 4, 1.5, 0.8, 3, 1.2];

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------

SynthDef(\inputTrigger14, { |inputBus = 0, ratio = 3.0, floorDb = -48,
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

SynthDef(\evolver14, { |out = 0, gate = 1, amp = 0.3, t_hit = 0, vel = 1,
	evolveTime = 5, stepTime = 0.25, stepGlide = 1.8,
	filterLo = 150, filterHi = 2500, rq = 0.3,
	detuneLo = 0.001, detuneHi = 0.015,
	note = 0, vibRate = 5.5, vibDepth = 0, drive = 1,
	strikeAtk = 0.002, strikeDecay = 0.15, strikeCurve = -2, bodyLevel = 0.35, accent = 4|

	var rise   = EnvGen.kr(Env([0, 0, 1], [0.01, evolveTime], [0, 2]), t_hit);

	var strike = EnvGen.ar(Env([0, 1, 0], [strikeAtk, strikeDecay], [0, strikeCurve]), t_hit);
	var body   = EnvGen.ar(Env([0, 1, 0], [strikeAtk, evolveTime], [0, -3]), t_hit);
	var level  = (strike * (1 - bodyLevel)) + (body * bodyLevel);

	var filterStep = Lag.kr(Duty.kr(stepTime, t_hit, Dseq(filterSteps, inf)), stepGlide);
	var cutoff = (rise.linexp(0, 1, filterLo, filterHi) * filterStep * (1 + (A2K.kr(strike) * accent))).clip(40, 18000);
	var detune = rise.linlin(0, 1, detuneLo, detuneHi);

	var vibrato = SinOsc.kr(vibRate, 0, Lag.kr(vibDepth, 0.1));
	var freq    = (root + Lag.kr(note, 0.02) + vibrato).midicps;

	var sig = Splay.ar(Saw.ar(freq * [1 - detune, 1, 1 + detune]));
	sig = RLPF.ar(sig, cutoff, rq);
	sig = (sig * Lag.kr(drive, 0.1)).tanh;

	sig = sig * level * vel * Lag.kr(amp, 0.05);
	sig = sig * EnvGen.kr(Env.asr(0.01, 1, 0.1), gate, doneAction: 2);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;

	voice = Synth(\evolver14, [\amp, 0], group);

	trig = Synth(\inputTrigger14, [\inputBus, inputBus, \ratio, 0.3, \slowRel, 0.25, \floorDb, -32,
		\fullScale, 0.7, \curve, 0.5, \deadtime, 0.02], group);

	OSCdef(hitKey, { |msg|
		if (msg[1] == trig.nodeID) {
			voice.set(\vel, msg[3], \t_hit, 1);
		};
	}, '/akHit', s.addr);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;

	OSCdef(hitKey) !? (_.free);
	trig !? (_.free);
	trig = nil;
	voice = nil;
	group = nil;

	if (dying.notNil) {
		fork {
			dying.set(\gate, 0);
			1.5.wait;
			dying.free;
		};
	};
};

//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);

	var tilt = d.sensors.gyroEvent.y / pi.half;
	var note = notes.clipAt(tilt.linlin(-1, 1, 0, notes.size).asInteger);

	var vibDepth = m.rrateMassFiltered.lincurve(0, 0.4 * sens, 0, 0.6, -2);
	var drive = m.accelMassFiltered.lincurve(0, 2 * sens, 1, 12, -2);

	voice !? { voice.set(\amp, vol * 0.4, \note, note, \vibDepth, vibDepth, \drive, drive) };
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;

	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	[m.accelMass, m.accelMassFiltered];

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
};
