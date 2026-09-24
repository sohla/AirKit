var m = ~model;
var dev = ~device;
var group, trig;
var hitKey = ('hit_' ++ m.ptn).asSymbol;
var relTime = 1.5;

var lastHit = 0;
var lastIoi = 0.5;
var avgIoi = 0.5;
var steadiness = 0.5;
var hitCount = 0;

var ioiMin = 0.04;
var ioiMax = 2.0;
var smooth = 0.99;

var notes = [0, 7, 10, 12, 15,19,24] + 12;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.3;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------

SynthDef(\inputTrigger, { |ratio = 3.0, floorDb = -68,
	deadtime = 0.01, slowAtk = 0.100, slowRel = 0.150, inGain = 0.7,
	fullScale = 0.35, curve = 1.5, window = 0.003|

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

SynthDef(\hollowWood, { |out = 0, freq = 220, amp = 0.3, vel = 1.0,
	decay = 1.5, coef = 0.3, body = 0.6, tone = 4000,
	gate = 1, rel = 1.5, pan = 0|

	var fire = Impulse.ar(0);
	var exc  = LPF.ar(WhiteNoise.ar(1), (tone * vel).clip(200, 18000));

	var plk = Pluck.ar(exc, fire, 0.05, freq.reciprocal.clip(0.0001, 0.05) * 0.5,
		decay, coef.clip(0.0, 0.95));

	var knock = Decay2.ar(fire, 0.0005, 0.008) * PinkNoise.ar(1);
	var bod = DynKlank.ar(`[
		[freq * 1, freq * 1.34, freq * 2.71],
		[0.5, 0.25, 0.12],
		[0.30, 0.18, 0.10] * body
	], knock);

	var sig = (plk + bod*0.5) * vel;
	var env = EnvGen.kr(Env.cutoff(rel, 1, \sin), gate, doneAction: 2);

	DetectSilence.ar(sig, 0.0001, 0.2, doneAction: 2);

	Out.ar(out, Pan2.ar(sig * env * amp, pan).tanh);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;

	lastHit = SystemClock.seconds;
	lastIoi = 0.1;
	avgIoi = 0.5;
	steadiness = 0.5;
	hitCount = 0;

	trig = Synth(\inputTrigger, [\ratio, 0.3, \slowRel, 0.25, \floorDb, -32,
		\fullScale, 0.7, \curve, 0.5, \deadtime, 0.001], group);

	OSCdef(hitKey, { |msg|
		if (msg[1] == trig.nodeID) {
			var vel = msg[3];
			var now = SystemClock.seconds;
			var ioi = (now - lastHit).clip(ioiMin, ioiMax);
			var rate, dec, cf, bod, rt, note;
			// var ang = (dev.sensors.gyroEvent.y / pi.half).linlin(0,1,0,1);
			var ang = m.accelMassFiltered.lincurve(0.0, 0.5, 0.0, 1.0, -1);

			if (hitCount > 0, {
				var drift = ((ioi - avgIoi).abs / avgIoi).clip(0.0, 1.0);
				avgIoi = (avgIoi * (1 - smooth)) + (ioi * smooth);
				steadiness = (steadiness * (1 - smooth)) + ((1 - drift) * smooth);
				lastIoi = ioi;
			});

			lastHit = now;
			hitCount = hitCount + 1;
			
			/*
			rate        -> decay   2.5 .. 0.2s   fast playing goes dry and clipped, slow rings
			steadiness  -> coef    0.12 .. 0.45  even playing damps the string toward woodblock
			rate        -> body    1.0 .. 0.25   the hollow box backs off as you speed up
			*/
			rate = avgIoi.reciprocal;
			dec  = rate.linexp(0.5, 12.0, 12.5, 0.01);
			cf   = steadiness.linlin(0.0, 1.0, 0.82, 0.25);
			bod  = rate.linlin(0.5, 12.0, 2.0, 0.8);

			// rt = (dev.sensors.gyroEvent.y / pi.half).lincurve(0.2, 0.9, 0.0, notes.size, 0).asInteger;
			// rt = rate.linlin(0.5, 12.0, 0.2, notes.size).asInteger;	
			// rt = rate.linlin(0.5, 15.0, 25, 200);	
			rt = ang.linlin(0.0, 1.0, 0, notes.size - 1).asInteger;	
			note = notes.clipAt(rt) + 45;
			Synth(\hollowWood, [
				\freq, note.midicps,
				\vel, vel,
				\amp, 0.5 * ang,
				\decay, ang * 5,
				\coef, cf,
				\body, ang * 5,
				\rel, ang * 5,
				\pan, (dev.sensors.gyroEvent.z / pi).clip(-1, 1) * 0.5
			], group);

			SystemClock.sched(0.2, {
			Synth(\hollowWood, [
				\freq, note.midicps,
				\vel, vel,
				\amp, 0.5 * ang,
				\decay, ang * 5,
				\coef, cf,
				\body, ang * 5,
				\rel, ang * 5,
				\pan, (dev.sensors.gyroEvent.z / pi).clip(-1, 1) * 0.5
			], group);
			});

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
	var sens = d.params.sensitivity;
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var since = SystemClock.seconds - lastHit;

	if (since > (avgIoi * 3), {
		avgIoi = min(ioiMax, avgIoi * 1.02);
		steadiness = steadiness * 0.995;
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, cyan , magenta]??

	// TRACKER : rate, steadiness, time since last hit
	// [
	// 	avgIoi.reciprocal.explin(0.5, 12.0, 0.0, 1.0),
	// 	steadiness,
	// 	(SystemClock.seconds - lastHit).linlin(0.0, ioiMax, 0.0, 1.0)
	// ];

	// [avgIoi.linlin(ioiMin, ioiMax, 0.0, 1.0), lastIoi.linlin(ioiMin, ioiMax, 0.0, 1.0)];
	// [hitCount % 16 / 16];

	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// [m.accelMass, m.accelMassFiltered];

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	[(d.sensors.gyroEvent.y / pi.half).linlin(0,1,0,1)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];
  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
};
