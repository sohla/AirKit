var m = ~model;
var group, synth, trig;
var hitKey = ('hit_' ++ m.ptn).asSymbol;
var relTime = 0.6;

var notes = [40,47,52,56];
var step = 0;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.95;
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

SynthDef(\simpleHit, {|out=0, amp=0.0, freq=120, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1,
	t_hit=0, vel=1.0, hitAtk=0.004, hitDec=0.4, hitCurve=(-4), hitAmp=0.7, duckAmt=0.7|

	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var accel = amp.lagud(0.01,1.1);
	var shape = EnvGen.kr(Env.perc(hitAtk, hitDec, 1, hitCurve), t_hit) * vel;
	var duck = 1 - (shape * duckAmt);
	var sig = SinOsc.ar(freq * 0.5,0,0.5)!2 + Saw.ar(freq,0.1) ;
    Out.ar(out, sig * env * ((accel * duck) + (shape * hitAmp)));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;
	step = 0;

	trig = Synth(\inputTrigger, [\ratio, 0.3, \slowRel, 0.25, \floorDb, -52,
		\fullScale, 0.7, \curve, 1.5, \deadtime, 0.02], group);

	synth = Synth(\simpleHit, [\hitAmp, 0.7, \duckAmt, 0.7, \hitDec, 0.4], group, \addToTail);

	OSCdef(hitKey, { |msg|
		if (msg[1] == trig.nodeID) {
			var note = notes.wrapAt(step);

			step = step + 1;

			synth.set(\freq, 220, \vel, msg[3], \t_hit, 1);
		};
	}, '/akHit', s.addr);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;

	OSCdef(hitKey) !? (_.free);
	trig !? (_.free);
	trig = nil;
	synth = nil;
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
	var amp = m.accelMassFiltered.lincurve(0.0, 4.6 * sens,-50,-8,-1);

  	synth !? (_.set(\amp, amp.dbamp * vol));

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
