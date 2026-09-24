var m = ~model;
var dev = ~device;
var group, trig;
var hitKey = ('hit_' ++ m.ptn).asSymbol;
var relTime = 3.0;

var dblDelay = 0.3;
var dblVel = 0.9;
var dblNote = 4;
var dblDecay = 0.1;

var ratios = [1, 2.756, 5.404, 7.933, 9.34, 11.64];
var amps   = [0.5, 0.3, 0.2, 0.12, 0.08, 0.05];
var rings  = [1.0, 0.8, 0.5, 0.35, 0.25, 0.18];

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.8;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------


SynthDef(\inputTrigger, { |ratio = 3.0, floorDb = -68,
	deadtime = 0.01, slowAtk = 0.100, slowRel = 0.150, inGain = 0.5,
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

SynthDef(\bellVoice, { |out = 0, amp = 0.3, freq = 523, vel = 1.0,
	decay = 3.0, tone = 5000, gate = 1, rel = 3.0|

	var strike = Decay2.ar(Impulse.ar(0), 0.0002, 0.004)
		* LPF.ar(PinkNoise.ar(1), (tone * vel).clip(200, 18000));

	var bell = DynKlank.ar(`[ratios * freq, amps, rings * decay], strike * vel);
	var env  = EnvGen.kr(Env.cutoff(rel, 1, \sin), gate, doneAction: 2);

	DetectSilence.ar(bell, 0.0001, 0.2, doneAction: 2);

	Out.ar(out, (bell * env * amp).softclip ! 2);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;

	trig = Synth(\inputTrigger, [\ratio, 0.3, \slowRel, 0.25, \floorDb, -32,
		\fullScale, 0.7, \curve, 0.5, \deadtime, 0.001], group);

	OSCdef(hitKey, { |msg|

		if (msg[1] == trig.nodeID) {
			var notes = [0,7,12,16] + 52;
			var rt = (dev.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,0.0,notes.size,0).asInteger;
			var rrate = [dev.sensors.rrateEvent.x, dev.sensors.rrateEvent.y, dev.sensors.rrateEvent.z].sumabs.lincurve(0.0, 1.0, 0.0, 1.0, -5);
			var decay = m.accelMassFiltered.lincurve(0.0, 1.0, 0.0, 6.0, -5);
			var updown = (dev.sensors.gyroEvent.y / pi.half).lincurve(0.0,1.0,0.0,6.0,0);
			var note = notes[rt];
			var vel = msg[3];

			Synth(\bellVoice, [\freq, note.midicps, \vel, vel,
				\amp, 0.1, \decay, decay, \rel, relTime], group);

			// SystemClock.sched(dblDelay, {
			// 	if (group.notNil, {
			// 		Synth(\bellVoice, [\freq, (note + dblNote).midicps, \vel, vel * dblVel,
			// 			\amp, 0.3, \decay, decay * dblDecay, \rel, relTime], group);
			// 	});
			// 	nil
			// });
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

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
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

	[
		[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs.lincurve(0.0, 1.0, 0.0, 1.0, -5),
		m.accelMassFiltered.lincurve(0.0, 1.0, 0.0, 1.0, -5),
		(d.sensors.gyroEvent.y / pi.half).lincurve(0.0,1.0,0.0,1.0,0)
	];
	
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];
  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];

	// [(d.sensors.gyroEvent.y / pi.half)];

};
