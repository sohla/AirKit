var m = ~model;
var group, synth, trig;
var hitKey = ('hit_' ++ m.ptn).asSymbol;
var relTime = 0.6;

var notes = [40,47,52,56];
var step = 0;

var hitEnv = Env.perc(0.004, 0.4, 1, -4);
var duckAmt = 0.7;
var hitAmp = 0.7;
var hitAt = -100;
var hitVel = 0;
var droneLvl = 0;
var lastNow = 0;

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
~init = ~init <> {|d|
	group = Group.new;
	step = 0;

	~vdef.(\rail, { |ev, c|
		var mod = ev[\modulation] ? ();
		var n = ev[\numPoints] ? 120;
		var cycles = (mod[\cycles] ? 5) * (mod[\ratio] ? 1);
		var scroll = mod[\scroll] ? 0.5;
		var lift = mod[\lift] ? 80;
		var saw = (mod[\wave] ? \sine) == \saw;
		var rise = mod[\rise] ? 0.01;
		var fall = mod[\fall] ? 1.1;
		var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
		var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
		var target = m.accelMassFiltered.lincurve(0.0, 4.6 * sens, -50, -8, -1).linlin(-50, -8, 0, 1) * vol;
		var now = c[\now];
		var dt = (now - lastNow).clip(0, 0.25);
		var duck = 1 - (hitEnv.at(now - hitAt) * hitVel * duckAmt);
		var half = c[\size];
		var mid = c[\pos];
		var h;

		lastNow = now;
		droneLvl = droneLvl + ((target - droneLvl) * (1 - exp(dt.neg / if(target > droneLvl, rise, fall))));
		h = lift * droneLvl * duck;

		c[\render].(Array.fill(n, { |i|
			var t = i / (n - 1);
			var ph = (t * cycles) - (now * scroll);
			var w = if(saw, { (ph.wrap(0, 1) * 2) - 1 }, { sin(ph * 2pi) });
			(mid.x + (half * ((t * 2) - 1))) @ (mid.y + (w * h))
		}), 0.4 + (droneLvl * duck), 0.12 + (0.88 * droneLvl * duck), false);
		nil
	});

	~vdef.(\bolt, { |ev, c|
		var mod = ev[\modulation] ? ();
		var n = ev[\numPoints] ? 64;
		var teeth = mod[\teeth] ? 11;
		var jag = mod[\jag] ? 50;
		var a = c[\posStart];
		var b = c[\posEnd];
		Array.fill(n, { |i|
			var t = i / (n - 1);
			a.blend(b, t) + ((((t * teeth).wrap(0, 1) * 2) - 1) * jag @ 0)
		})
	});

	[
		[-0.88, \saw, 1, Color.new(0.30, 0.42, 1.0, 0.95)],
		[0.88, \sine, 0.5, Color.new(0.16, 0.20, 0.95, 0.95)]
	].do { |rail|
		(
			type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port,
			shape: \rail, duration: inf, numPoints: 120,
			sx: 0, sy: rail[0], ex: 0, ey: rail[0],
			startSize: 1100, endSize: 1100,
			startWidth: 3, endWidth: 3,
			startColor: rail[3], endColor: rail[3],
			modulation: (wave: rail[1], ratio: rail[2], cycles: 10, scroll: 0.35,
				lift: 90, rise: 0.01, fall: 1.1, amp: 0)
		).play;
	};

	trig = Synth(\inputTrigger, [\ratio, 0.3, \slowRel, 0.25, \floorDb, -52,
		\fullScale, 0.7, \curve, 1.5, \deadtime, 0.02], group);

	synth = Synth(\simpleHit, [\hitAmp, 0.7, \duckAmt, 0.7, \hitDec, 0.4], group, \addToTail);

	OSCdef(hitKey, { |msg|
		if (msg[1] == trig.nodeID) {
			var note = notes.wrapAt(step);

			step = step + 1;

			synth.set(\freq, 220, \vel, msg[3], \t_hit, 1);

			hitAt = thisThread.seconds;
			hitVel = msg[3];

			(
				type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port, vLatency: 0,
				shape: \bolt, numPoints: 64, closed: false,
				sx: (d.sensors.gyroEvent.z / pi).clip(-1, 1) * 0.85, sy: -0.88,
				ex: (d.sensors.gyroEvent.z / pi).clip(-1, 1) * 0.85, ey: 0.88,
				startWidth: 2 + (msg[3] * hitAmp * 16), endWidth: 1,
				startColor: Color.new(1.0, 0.36, 0.28, 0.95),
				endColor: Color.new(1.0, 0.36, 0.28, 0.0),
				colorEnv: Env([1, 0, 1], hitEnv.times.normalizeSum, hitEnv.curves),
				widthEnv: Env([1, 0, 1], hitEnv.times.normalizeSum, hitEnv.curves),
				duration: hitEnv.duration,
				modulation: (teeth: 11, jag: 20 + (msg[3] * hitAmp * 90), amp: 0)
			).play;
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
