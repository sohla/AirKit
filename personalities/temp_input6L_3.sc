var inputBus = 0;
var m = ~model;
var dev = ~device;
var group, trig, hold;
var hitKey = ('hit_' ++ m.ptn).asSymbol;
var notes = [-5,-4,0,7,8,12] + 33;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;


// tempo tracking
// beat compare as input

//------------------------------------------------------------

SynthDef(\inputTrigger6L, { |inputBus = 0, ratio = 3.0, floorDb = -68,
	deadtime = 0.01, slowAtk = 0.100, slowRel = 0.150, inGain = 0.7,
	fullScale = 0.35, curve = 1.5, window = 0.003|

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

SynthDef(\miniMoogVoice6L, {
	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0, dur = 0.2|

	var g = gate * Trig1.kr(Impulse.kr(0), dur);
    var env, osc, filt, sig, sub;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), g, doneAction: 2);
	osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 0.005],0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
    filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
	sub = SinOsc.ar(freq * 0.5, 0.0, 2);
    sig = (filt + sub) * env * amp * 0.5;
    sig = Pan2.ar(sig, pan);
    Out.ar(0, sig.tanh);
}).add;

SynthDef(\holdVoice6L, { |out = 0, freq = 55, amp = 0, drive = 0, atk = 0.4, rel = 3, slide = 0.3, detune = 0.08, lo = 1, hi = 8, gate = 1|
	var f = freq.lag(slide);
	var env = drive.lagud(atk, rel);
	var tone = Saw.ar(f * detune.neg.midiratio) + Saw.ar(f * detune.midiratio);
	var release = EnvGen.kr(Env.asr(0.01, 1, 2), gate, doneAction: 2);
	var sig = MoogFF.ar(tone, (f * env.linexp(0, 1, lo, hi)).clip(20, 18000), 1.2);
	Out.ar(out, Pan2.ar(sig * env * amp * release * 0.3, 0).tanh);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;
	hold = Synth(\holdVoice6L, [\amp, 0, \atk, 0.4, \rel, 3, \slide, 0.01, \detune, 0.08, \lo, 1, \hi, 16], group);

	trig = Synth(\inputTrigger6L, [\inputBus, inputBus, \ratio, 0.3, \slowRel, 0.25, \floorDb, -26,
		\fullScale, 0.7, \curve, 0.3, \deadtime, 0.001], group);

	OSCdef(hitKey, { |msg|
		if (msg[1] == trig.nodeID) {
			var sens = dev.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
			var vol = dev.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
			var vel = msg[3];
			// var notes = [0,-5,-4,5,1] + 33;
			var ff = m.accelMassFiltered.lincurve(0.0, 2.0 * sens, 40, 800, -2).clip(200, 12000);
			var rt = (dev.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,0.0,notes.size,0).asInteger.clip(0, notes.size - 1);
			Synth(\miniMoogVoice6L, [
				\freq, (notes[rt] + [0, 12, 0, 0].choose).midicps,
				\amp, vel.linlin(0.0, 1.0, 0.01, 0.1) * vol,
				\attack, vel.linexp(0.0, 1.0, 0.05, 0.001),
				\filterFreq, ff,
				\attack, 0.08,//vel.lincurve(0.0, 1.0, 1.01, 0.002,-2),
				\decay, 0.12,
				\sustain, 0.1,
				\release, vel.lincurve(0.0, 1.0, 2.2, 0.2,-2),
				\fq, 0.4,
				\dur, 0.2
			], group);

			// hold !? { hold.set(\freq, (notes[rt] + 12).midicps) };

			(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: dev.port, shape: \circle,
				startSize: vel.linlin(0, 1, 20, 160), endSize: vel.linlin(0, 1, 40, 240),
				startColor: Color.white.alpha_(0.8), endColor: Color.white.alpha_(0), duration: 2.4).play;
		};
	}, '/akHit', s.addr);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;
	var tail = 3.0;

	OSCdef(hitKey) !? (_.free);
	trig !? (_.free);
	trig = nil;
	hold = nil;
	group = nil;

	if (dying.notNil) {
		fork {
			dying.set(\gate, 0);
			tail.wait;
			dying.free;
		};
	};
};

//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var drive = m.accelMassFiltered.lincurve(0.0, 2.0 * sens, 0.0, 1.0, -1);
	var rt = (dev.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,0.0,notes.size,0).asInteger.clip(0, notes.size - 1);

	hold !? { hold.set(\freq, (notes[rt]).midicps, \drive, drive, \amp, vol, \lo, 0.1, \hi, 8) };
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
