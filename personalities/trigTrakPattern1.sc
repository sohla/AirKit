var m = ~model;
var dev = ~device;
var group, trig;
var hitKey = ('hit_' ++ m.ptn).asSymbol;
var relTime = 0.5;

var lastHit = 0;
var lastIoi = 0.5;
var avgIoi = 0.5;
var steadiness = 0.5;
var hitCount = 0;

var ioiMin = 0.04;
var ioiMax = 1.0;
var smooth = 0.8;
var patDiv = 1;

var notes = [0, 3, 7, 10];
var baseNote = 45;

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

SynthDef(\trigTone, { |out = 0, freq = 220, amp = 0.3, vel = 1.0,
	atk = 0.003, rel = 0.45, gate = 1, pan = 0|

	var env = EnvGen.kr(Env.perc(atk, rel, 1, -4), gate, doneAction: 2);
	var sig = SinOsc.ar(freq) + (Saw.ar(freq * 1.002) * 0.25);

	sig = LPF.ar(sig, (freq * 8).clip(200, 9500));

	Out.ar(out, Pan2.ar(sig * env * amp * vel, pan));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;

	lastHit = SystemClock.seconds;
	lastIoi = 0.5;
	avgIoi = 0.5;
	steadiness = 0.5;
	hitCount = 0;

	~vdef.(\toneColumn, { |ev, c|
		var mod = ev[\modulation] ? ();
		var n = ev[\numPoints] ? 110;
		var cycles = mod[\cycles] ? 6;
		var harm = mod[\harm] ? 8;
		var sawMix = mod[\sawMix] ? 0.25;
		var atk = mod[\atk] ? 0.003;
		var rel = mod[\rel] ? 0.45;
		var reach = mod[\reach] ? 700;
		var scroll = mod[\scroll] ? 0.5;
		var t = c[\elapsed];
		var x = ((t - atk) / rel).clip(0, 1);
		var env = if(t < atk, { t / atk }, { 1 - ((1 - exp(-4 * x)) / (1 - exp(-4))) });
		var sway = m.accelMassFiltered.clip(0, 2) * (mod[\sway] ? 0);
		var mid = c[\pos];

		Array.fill(n, { |i|
			var u = i / (n - 1);
			var ph = ((u * cycles) + (t * scroll)) * 2pi;
			var saw = Array.fill(harm, { |k| sin(ph * (k + 1)) / (k + 1) }).sum;
			var wave = sin(ph) + (saw * sawMix);
			mid + (((wave * c[\size] * env) + (sway * sin(u * pi))) @ ((u - 0.5) * 2 * reach))
		})
	});

	trig = Synth(\inputTrigger, [\ratio, 0.3, \slowRel, 0.25, \floorDb, -32,
		\fullScale, 0.7, \curve, 0.5, \deadtime, 0.001], group);

	OSCdef(hitKey, { |msg|
		if (msg[1] == trig.nodeID) {
			var vol = dev.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
			var vel = msg[3];
			var now = SystemClock.seconds;
			var ioi = (now - lastHit).clip(ioiMin, ioiMax);
			var rt, note;

			if (hitCount > 0, {
				var drift = ((ioi - avgIoi).abs / avgIoi).clip(0.0, 1.0);
				avgIoi = (avgIoi * (1 - smooth)) + (ioi * smooth);
				steadiness = (steadiness * (1 - smooth)) + ((1 - drift) * smooth);
				lastIoi = ioi;
			});

			lastHit = now;
			hitCount = hitCount + 1;

			rt = (dev.sensors.gyroEvent.y / pi.half).lincurve(-1.0, 1.0, 0.0, notes.size, 0).asInteger;
			note = notes.clipAt(rt) + baseNote;

			Synth(\trigTone, [
				\freq, note.midicps,
				\vel, vel,
				\amp, 0.35 * vol,
				\atk, 0.003,
				\rel, relTime,
				\pan, (dev.sensors.gyroEvent.z / pi).clip(-1, 1) * 0.4
			], group);

			(
				type: \customVisualEvent,
				amp: 0,
				dur: 0.01,
				viewID: dev.port,

				shape: \toneColumn,
				numPoints: 110,
				closed: false,

				sx: (dev.sensors.gyroEvent.z / pi).clip(-1, 1) * 0.9,
				ex: (dev.sensors.gyroEvent.z / pi).clip(-1, 1) * 0.9,

				startSize: vel.linlin(0, 1, 30, 150),
				endSize: vel.linlin(0, 1, 30, 150),

				startWidth: vel.linlin(0, 1, 1.5, 7),
				endWidth: vel.linlin(0, 1, 1, 3),

				startColor: Color.white,
				endColor: Color.hsv(0.88, 0.95, 1.0, 0.0),
				colorEnv: Env([0, 1], [1], -3),

				duration: 0.003 + relTime,

				modulation: (
					amp: 0,
					cycles: note.midicps / 25,
					harm: ((note.midicps * 8).clip(200, 9500) / note.midicps).floor.asInteger,
					sawMix: 0.25,
					atk: 0.003,
					rel: relTime,
					reach: 700,
					scroll: 0.4,
					sway: 120
				)
			).play;
		};
	}, '/akHit', s.addr);

	Pdef(m.ptn,
		Pbind(
			\instrument, \trigTone,
			\group, group,
			\midinote, Pseq(notes + baseNote + 12, inf),
			\vel, Pseq([1.0, 0.55, 0.7, 0.55], inf),
			\atk, 0.002,
			\rel, 0.25,
			\amp, Pfunc({ 0.7 * dev.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1) }),
			\pan, Pseq([-0.3, 0.3], inf),
			\args, #[],

			\type, \customVisualEvent,
			\shape, \square,
			\fill, true,
			\numPoints, 8,
			\col, Pseq((0..15), inf),
			\lane, Pfunc({ |e| notes.indexOf(e[\midinote] - baseNote - 12) ? 0 }),
			\sx, Pfunc({ |e| ((e[\col] + 0.5) / 8) - 1 }),
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e| 0.93 - (e[\lane] * 0.07) }),
			\ey, Pkey(\sy),
			\rotation, Pfunc({ |e| e[\pan] * 0.5pi }),
			\startSize, Pfunc({ |e| e[\vel] * 16 }),
			\endSize, 2,
			\sizeEnv, Pfunc({ Env([0, 1], [1], -4) }),
			\startColor, Color.hsv(0.165, 0.9, 1.0, 0.9),
			\endColor, Color.hsv(0.165, 0.9, 1.0, 0.0),
			\duration, Pfunc({ |e| (e[\atk] ? 0.002) + (e[\rel] ? 0.25) })
		);
	);

	Pdef(m.ptn).set(\dur, avgIoi);
	Pdef(m.ptn).set(\amp, 0.12);

	Pdef(m.ptn).play(quant: 0);
	// Pdef(m.ptn).pause;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;

	Pdef(m.ptn).remove;
	OSCdef(hitKey) !? (_.free);
	trig !? (_.free);
	trig = nil;
	group = nil;

	if (dying.notNil) {
		fork {
			(relTime + 0.5).wait;
			dying.freeAll;
			s.sync;
			dying.free;
		};
	};
};

//------------------------------------------------------------
~next = {|d|
	var since = SystemClock.seconds - lastHit;

	if (since > (avgIoi * 3), {
		avgIoi = min(ioiMax, avgIoi * 1.02);
		steadiness = steadiness * 0.995;
	});

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\dur, (avgIoi / patDiv).clip(ioiMin, ioiMax));
	// Pdef(m.ptn).set(\amp, m.accelMassFiltered.lincurve(0.0, 3 * sens, -34, -4, -2).dbamp);

	// if (since < (ioiMax * 1.5), {
	// 	if (Pdef(m.ptn).isPlaying.not, { Pdef(m.ptn).resume(quant: 0) });
	// }, {
	// 	if (Pdef(m.ptn).isPlaying, { Pdef(m.ptn).pause });
	// });
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, cyan , magenta]??

	// TRACKER : pattern dur, rate, steadiness
	[
		(avgIoi / patDiv).linlin(ioiMin, ioiMax, 0.0, 1.0),
		avgIoi.reciprocal.explin(0.5, 12.0, 0.0, 1.0),
		steadiness
	];

	// [(SystemClock.seconds - lastHit).linlin(0.0, ioiMax, 0.0, 1.0)];
	// [hitCount % 16 / 16];

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
  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
};
