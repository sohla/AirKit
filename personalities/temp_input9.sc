var m = ~model;
var dev = ~device;
var group, trig;
var hitKey = ('hit_' ++ m.ptn).asSymbol;
var relTime = 0.8;

// Chord sets, one per section. Motion picks which note of the current set
// sounds, and the set moves on once it has been exhausted `reps` times.
//
// PLACEHOLDER - these are temp_input7's own note set transposed, NOT the
// Deuce Ambience intro. Nothing named deuce/duece exists in the project,
// so there were no voicings to read. Swap this array and nothing else.
var noteSets = [
	[0, 16, 4, 7, 14, 19, 11],
	[0, 16, 4, 7, 14, 19, 11] + 5,
	[0, 16, 4, 7, 14, 19, 11] - 2
];

// A pass is one set's worth of notes, so a set lasts reps * set.size hits.
var reps = 2;

// 50 - 12, temp_input7's first root - keeps the register of the template.
var base = 38;

var step = 0;
var setIdx = 0;
var hitsInSet = 0;

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
// The miniMoog voice, copied from personalities/miniMoog.sc so that the
// shared \miniMoog stays exactly as it is. Two things differ, both forced
// by being triggered as a bare Synth from the OSCdef rather than by a
// Pbind: the gate has to release itself after dur, and vel has to be an
// arg so the trigger's velocity reaches the envelope.
SynthDef(\miniMoogVoice, {
	|out = 0, freq = 440, amp = 0.5, vel = 1.0, dur = 0.25,
	attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3,
	gate = 1, filterFreq = 800, fq = 0.5, pan = 0|

	var g = gate * Trig1.kr(Impulse.kr(0), dur);
	var env, osc, filt, sig;

	env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), g, doneAction: 2);
	osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 1.005] * 1,0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
	filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
	sig = filt * env * amp * vel * 0.5;
	sig = Pan2.ar(sig, pan);
	Out.ar(out, sig.tanh);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;
	step = 0;
	setIdx = 0;
	hitsInSet = 0;

	~vdef.(\padBlock, { |ev, c|
		var mod = ev[\modulation] ? ();
		var n = ((ev[\numPoints] ? 64) / 2).floor.max(2);
		var breadth = mod[\breadth] ? 60;
		var fMin = mod[\filtMin] ? 200;
		var fMax = mod[\filtMax] ? 2600;
		var fSpeed = mod[\filtSpeed] ? 0.1;
		var filt = sin(c[\elapsed] * fSpeed * 2pi).linlin(-1, 1, fMin, fMax);
		var half = c[\size] * filt / ((fMin + fMax) * 0.5);
		var mid = c[\pos];

		Array.fill(n, { |i| mid + ((((i / (n - 1)) - 0.5) * 2 * breadth) @ half.neg) })
		++ Array.fill(n, { |i| mid + (((0.5 - (i / (n - 1))) * 2 * breadth) @ half) })
	});

	// trig = Synth(\inputTrigger, [\ratio, 0.3, \slowRel, 0.25, \floorDb, -42,
	// 	\fullScale, 0.7, \curve, 1.5, \deadtime, 0.02], group);
	trig = Synth(\inputTrigger, [\ratio, 0.3, \slowRel, 0.25, \floorDb, -32,
		\fullScale, 0.7, \curve, 0.5, \deadtime, 0.02], group);


	OSCdef(hitKey, { |msg|
		if (msg[1] == trig.nodeID) {
			var set = noteSets.wrapAt(setIdx);
			var band = setIdx;
			var sens = dev.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
			var vol = dev.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);

			// up/down tilt picks which note of the current set sounds.
			// clipAt, so the extremes hold the top and bottom of the set
			// instead of wrapping back round.
			var tilt = dev.sensors.gyroEvent.y / pi.half;
			var idx = tilt.lincurve(-1.0, 1.0, 0.0, set.size, 0).asInteger;
			var note = base + set.clipAt(idx);

			// filter mapping lifted from personalities/miniMoog.sc - the
			// voice is dull at the SynthDef default, and rotation is the
			// one gesture the note index is not already using.
			var ff = m.rrateMassFiltered.linexp(0, 0.4 * sens, 60, 15000);

			step = step + 1;
			hitsInSet = hitsInSet + 1;

			if (hitsInSet >= (set.size * reps), {
				setIdx = setIdx + 1;
				hitsInSet = 0;
			});

			Synth(\miniMoogVoice, [
				\freq, note.midicps,
				\vel, msg[3],
				\amp, 0.9 * vol,
				\dur, 0.25,
				\attack, 0.02,
				\decay, 0.1,
				\sustain, 0.5,
				\release, relTime,
				\filterFreq, ff,
				\fq, 0.3,
				\pan, 0
			], group);

			(
				type: \customVisualEvent,
				amp: 0,
				dur: 0.01,
				viewID: dev.port,

				shape: \padBlock,
				fill: true,
				numPoints: 64,

				sx: ((((step - 1) % 16) + 0.5) / 8) - 1,
				ex: ((((step - 1) % 16) + 0.5) / 8) - 1 + 0.06,
				sy: note.linlin(36, 60, 0.85, -0.85),
				ey: note.linlin(36, 60, 0.85, -0.85),

				startSize: msg[3].linlin(0, 1, 18, 110),
				endSize: msg[3].linlin(0, 1, 18, 110),

				startColor: Color.hsv([0.93, 0.015, 0.075].wrapAt(band), 0.9, 1.0, 0.0),
				endColor: Color.hsv([0.93, 0.015, 0.075].wrapAt(band), 0.9, 1.0, 0.85),
				colorEnv: Env([0, 1, 0.5, 0.5, 0], [0.02, 0.1, 0.13, relTime] / (0.25 + relTime)),

				duration: 0.25 + relTime,

				modulation: (
					type: \radial,
					freq: 0.6,
					amp: m.accelMassFiltered.clip(0, 1).linlin(0, 1, 2, 30),
					harmonics: 4,
					breadth: 58,
					filtMin: 200,
					filtMax: 2600,
					filtSpeed: 0.1
				)
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
