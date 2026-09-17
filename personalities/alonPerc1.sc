var m = ~model;
var dev = ~device;
var group, trig, buffers;
var hitKey = ('hit_' ++ m.ptn).asSymbol;
var relTime = 0.4;

var kitFolder = "~/Music/cotf_samples/drums";
var kitNames = ["HH11X05", "SD02X05", "BD02X07"];

var kit = [
	(idx: 0, lo: 0.00, hi: 0.8, lvl: 0.55, rate: 1.0,  cut: 16000),
	// (idx: 1, lo: 0.5, hi: 0.80, lvl: 0.70, rate: 1.0,  cut: 12000),
	(idx: 2, lo: 0.50, hi: 1.01, lvl: 0.95, rate: 1.0,  cut:  6000)
];

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.2;
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

SynthDef(\alonKit, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.001, release=0.02, gate=1, cutoff=14000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.asr(attack, 1, release), gate, doneAction: Done.freeSelf);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.0], startPos: start * BufFrames.kr(bufnum),
		loop: 0, doneAction: Done.freeSelf) * env;
	sig = RLPF.ar(sig, cutoff.clip(200, 18000), rq);
	sig = Compander.ar(sig, sig,
		thresh: -15.dbamp,
		slopeBelow: 1,
		slopeAbove: 0.5,
		clampTime:  0.01,
		relaxTime:  0.01
	);
	sig = FreeVerb.ar(sig, 0.1, 1.1, 0.4);
	Out.ar(out, sig * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var folder = kitFolder.standardizePath;

	group = Group.new;

	buffers = kitNames.collect({ |n|
		Buffer.read(s, folder +/+ (n ++ ".wav"), action: { |b|
			postf("buffer alloc [%] % \n", b, n);
		});
	});

	trig = Synth(\inputTrigger, [\ratio, 0.3, \slowRel, 0.25, \floorDb, -32,
		\fullScale, 0.7, \curve, 0.5, \deadtime, 0.001], group);

	OSCdef(hitKey, { |msg|
		if (msg[1] == trig.nodeID) {
			var vel = msg[3];
			var ff = m.accelMassFiltered.lincurve(0.0, 1.0, 0.35, 1.6, -2);
			var rt = (dev.sensors.gyroEvent.y / pi.half).lincurve(-1.0, 1.0, 0.1, 4, 0);

			if (buffers.notNil, {
				var lay = kit.detect({ |l| (vel >= l[\lo]) and: { vel < l[\hi] } }) ? kit.last;
				var b = buffers[lay[\idx]];

				if (b.notNil, {
					Synth(\alonKit, [
						\bufnum, b,
						\amp, lay[\lvl] * vel.linlin(lay[\lo], lay[\hi], 0.6, 1.0),
						\rate,1,
						\cutoff, lay[\cut] * ff,
						\rq, 1,
						\attack, 0.001,
						\release, 0.02
					], group);
				});
			});
		};
	}, '/akHit', s.addr);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group, dyingBufs = buffers;

	OSCdef(hitKey) !? (_.free);
	trig !? (_.free);
	trig = nil;
	group = nil;
	buffers = nil;

	if (dying.notNil, {
		fork {
			dying.set(\gate, 0);
			(relTime + 0.5).wait;
			dying.freeAll;
			s.sync;
			dying.free;
			s.sync;
			dyingBufs !? { |bs|
				bs.do({ |b|
					postf("buffer dealloc [%] \n", b);
					b.free;
					s.sync;
				});
			};
		};
	});
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
