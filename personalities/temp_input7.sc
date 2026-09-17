var m = ~model;
var dev = ~device;
var group, trig;
var hitKey = ('hit_' ++ m.ptn).asSymbol;
var relTime = 0.8;

var notes = [0, 16, 4, 7, 14, 19, 11];
var roots = [50,53,48].stutter(16) - 12;

var step = 0;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------

SynthDef(\inputTrigger, { |ratio = 3.0, floorDb = -48,
	deadtime = 0.10, slowAtk = 0.300, slowRel = 0.150, inGain = 0.7,
	fullScale = 0.35, curve = 1.5, window = 0.005|

	var in   = SoundIn.ar(0) * inGain;
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

SynthDef(\warmPadVoice, {
	|out=0, gate=1, freq=440, amp=0.3, vel=1.0, dur=0.25,
	atk=0.03, dec=0.2, sus=0.8, rel=1.0,
	filtMin=500, filtMax=5000, filtSpeed=0.5,
	detuneAmount = 0.001,chorusRate=0.5, chorusDepth=0.01,
	pan=0, spread=0.2|

	var g = gate * Trig1.kr(Impulse.kr(0), dur);
	var sig, env, filt, chorus, numVoices=4, sub;

	env = EnvGen.kr(
		Env.adsr(atk, dec, sus, rel),
		g,
		doneAction: 2
	);

	sig = Array.fill(numVoices, { |i|
		var detune = i * detuneAmount;
		var oscillator = SinOsc.ar(freq * (1 + detune)) +
						Saw.ar(freq * (1 + detune), pi/2 * i) * 0.3;
		Pan2.ar(oscillator, pan + detune)
	}).sum;

	filt = SinOsc.kr(filtSpeed).range(filtMin, filtMax);
	sig = RLPF.ar(sig, filt, 0.5);

	chorus = Array.fill(2, {
		var maxDelay = 0.05;
		var delayTime = SinOsc.kr(
			chorusRate + rand(0.1),
			rrand(0, 2pi)
		).range(0, chorusDepth);

		AllpassC.ar(
			sig,
			maxDelay,
			delayTime + (chorusDepth * 0.1),
			0.1
		)
	});
	sub = LFTri.ar(freq, pi, 0.2).tanh;
	sig = Mix([sig, chorus.sum]) / (numVoices + 2);
	sig = sig * env * amp * vel;

	sig = Splay.ar(sig, spread);

	Out.ar(out, sig + (sub * env * vel));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;
	step = 0;

	trig = Synth(\inputTrigger, [\ratio, 0.3, \slowRel, 0.25, \floorDb, -42,
		\fullScale, 0.7, \curve, 1.5, \deadtime, 0.02], group);

	// trig = Synth(\inputTrigger, [\ratio, 0.3, \slowRel, 0.25, \floorDb, -32,
	// 	\fullScale, 0.7, \curve, 0.5, \deadtime, 0.001], group);


	OSCdef(hitKey, { |msg|
		if (msg[1] == trig.nodeID) {
			// var roots = [60].stutter(16) - 12;
			// var ri = (dev.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,0.0,roots.size,0).asInteger;
			var note = roots.wrapAt(step) + notes.wrapAt(step);

			step = step + 1;

			Synth(\warmPadVoice, [
				\freq, note.midicps,
				\vel, msg[3],
				\amp, 0.5,
				\dur, 0.25,
				\atk, 0.02,
				\dec, 0.1,
				\sus, 0.5,
				\rel, relTime,
				\filtMin, 200,
				\filtMax, 2600,
				\filtSpeed, 0.1,
				\chorusRate, 0.05,
				\chorusDepth, 0.02,
				\detuneAmount, 0.0008,
				\spread, 0.6
			], group);
		};
	}, '/akHit', s.addr);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;

	OSCdef(hitKey) !? (_.free);
	trig !? (_.free);
	trig = nil;
	group = nil;

	if (dying.notNil) {
		fork {
			dying.set(\gate, 0);
			(relTime + 0.5).wait;
			dying.free;
		};
	};
};

//------------------------------------------------------------
~next = {|d|
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
