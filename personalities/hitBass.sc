var inputBus = 0;
var m = ~model;
var dev = ~device;
var group, trig, hold;
var hitKey = ('hit_' ++ m.ptn).asSymbol;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;


// tempo tracking
// beat compare as input

//------------------------------------------------------------

SynthDef(\inputTriggerHitBass, { |inputBus = 0, ratio = 3.0, floorDb = -68,
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

SynthDef(\miniMoogVoiceHitBass, {
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

//------------------------------------------------------------

SynthDef(\woiworungHitBass, {|out,freq = 1000, amp = 0.5, att = 0.02, dec = 0.3, sus = 1, rel = 4, gate = 1, fb = 1.2, ch=10|
	var snd, env;
	env = EnvGen.kr(Env.adsr(att, dec, sus, rel), gate: gate, doneAction: 2);

	snd = SinOsc.ar(freq,
		LocalIn.ar(2) * LFNoise1.ar(0.1,2),
		LFNoise2.ar(ch.lag(0.3),1.7)
	).tanh * amp.lagud(0.1,2.3) * freq.linlin(50,800,1,0.007);
	2.do{
		snd = AllpassL.ar(snd,0.3,{0.1.rand+0.03}!2,5)
	};
	Out.ar(out, snd.tanh * env);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var notes = [-1,0,3,4,6,7,9,11,12,14] + 40;
	var interval = [-12,36];
	group = Group.new;

	hold = Synth(\woiworungHitBass, [\freq, (notes[0] + interval[0]).midicps, \gate, 1, \amp, 0]);

	trig = Synth(\inputTriggerHitBass, [\inputBus, inputBus, \ratio, 0.3, \slowRel, 0.25, \floorDb, -30,
		\fullScale, 0.7, \curve, 0.3, \deadtime, 0.001], group);

	OSCdef(hitKey, { |msg|
		if (msg[1] == trig.nodeID) {
			var sens = dev.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
			var vol = dev.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
			var vel = msg[3];
			var ff = m.accelMassFiltered.lincurve(0.0, 2.0 * sens, 200, 12000, -2).clip(200, 12000);
			var rt = (dev.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,0.0,notes.size,0).asInteger.clip(0, notes.size - 1);
			Synth(\miniMoogVoiceHitBass, [
				\freq, (notes[rt] + [0, 12, 0, 0].choose).midicps,
				\amp, vel.linlin(0.0, 1.0, 0.05, 0.2) * vol,
				\attack, vel.linexp(0.0, 1.0, 0.05, 0.001),
				\filterFreq, ff,
				\attack, 0.001,//vel.lincurve(0.0, 1.0, 1.01, 0.002,-2),
				\decay, 0.12,
				\sustain, 0.1,
				\release, vel.lincurve(0.0, 1.0, 2.2, 0.2,-2),
				\fq, 0.4,
				\dur, 0.2
			], group);

			interval = interval.rotate(-1);
			hold !? { hold.set(\freq, (notes[rt] + interval[0]).midicps) };

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
	hold !? { hold.set(\gate, 0) };
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
	var a = m.accelMassFiltered.lincurve(0,2 * sens,0.0,0.2,1);
	var ch = (m.accelMassFiltered * 0.25).linlin(0.0,1.0,0.1,10);
	if(a<0.01,{a=0});
	if(a>0.9,{a=1.0});
	hold !? { hold.set(\amp, a * vol, \ch, ch) };
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
