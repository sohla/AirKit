var inputBus = 1;
var m = ~model;
var dev = ~device;
var group, trig, hold;
var hitKey = ('hit_' ++ m.ptn).asSymbol;


var notes = [-5,-4,-1,0,1,4,5,7] + 33 + 24;
var interval = 12;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;


// tempo tracking
// beat compare as input

//------------------------------------------------------------

SynthDef(\inputTrigger6R_2, { |inputBus = 0, ratio = 3.0, floorDb = -68,
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

SynthDef(\miniMoogVoice6R_2, {
	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.7, pan = 0, dur = 0.2|

	var g = gate * Trig1.kr(Impulse.kr(0), dur);
    var env, osc, filt, sig;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), g, doneAction: 2);
	osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 0.005],0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
    filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
    sig = filt * env * amp * 0.5;
    sig = Pan2.ar(sig, pan);
    Out.ar(0, sig.tanh);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var rel = m.accelMassFiltered.lincurve(0, 2, 0.02, 4, -1);
	// var notes = [0,1,4,5,7,8,10,12] + 45;
	// var interval = 12;

	group = Group.new;

	hold = Synth(\miniMoogVoice6R_2, [
		\freq, (notes[0] + interval).midicps,
		\amp, 0,
		\filterFreq, 100,
		\attack, 0.5,
		\decay, 0.1,
		\sustain, 1,
		\release, 2,
		\fq, 0.3,
		\dur, 86400
	], group);

	trig = Synth(\inputTrigger6R_2, [\inputBus, inputBus, \ratio, 0.9, \slowAtk, 0.1,\slowRel, 0.01, \floorDb, -24,
		\fullScale, 0.7, \curve, 0.5, \deadtime, 0.06], group);

	OSCdef(hitKey, { |msg|
		if (msg[1] == trig.nodeID) {
			var sens = dev.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
			var vol = dev.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
			var vel = msg[3];
			var ff = m.accelMassFiltered.lincurve(0.0, 2.0 * sens, 200, 2000, -2).clip(200, 12000);
			var rt = (dev.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,0.0,notes.size,0).asInteger.clip(0, notes.size - 1);

			Synth(\miniMoogVoice6R_2, [
				\freq, (notes[rt]).midicps,
				\amp, vel.linlin(0.0, 1.0, 0.01, 0.6) * vol,
				\filterFreq, ff,
				\attack, rel * 0.2,//vel.lincurve(0.0, 1.0, 1.01, 0.002,-2),
				\decay, 0.12,
				\sustain, 0.1,
				\release, 0.4,
				\fq, 0.3,
				\dur, 0.2
			], group);

			// hold !? { hold.set(\freq, (notes[rt] + interval).midicps * 2) };

			(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: dev.port, shape: \circle,
				startSize: vel.linlin(0, 1, 20, 160), endSize: vel.linlin(0, 1, 40, 240),
				startColor: Color.white.alpha_(0.8), endColor: Color.white.alpha_(0), duration: 2.2).play;
		};
	}, '/akHit', s.addr);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;
	var tail = 6.5;

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
	var amp = m.accelMassFiltered.lincurve(0, 2 * sens, 0, 0.09, -2);
	var ff = m.rrateMassFiltered.linexp(0, 0.4 * sens, 290, 3000);
	var rt = (dev.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,0.0,notes.size,0).asInteger.clip(0, notes.size - 1);

	if(amp < 0.005, { amp = 0 });

	hold !? { hold.set(\freq, (notes[rt] + interval).midicps * 2, \amp, amp * vol, \filterFreq, ff) };

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
