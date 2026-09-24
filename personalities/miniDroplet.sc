var m = ~model;
var group;
var fxBus;
var verbSynth;

var beat = 0.8;
var divs = [1, 4, 6,8];
var notes = [2,4,0,-1,-3].stutter(4);

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\miniDroplet, {
    |out=0, freq=1000, amp=0.8, pan=0, gate=1, attack=0.001, decay=0.05,
	filterFreq=3000, filterRQ=1, wobble=10|

	  var sig, env;
    env = EnvGen.ar(Env.perc(attack, decay), gate);
    sig = SinOsc.ar(freq * LFPar.ar(wobble,pi/2, 0.2,1)) * env;
    sig = BPF.ar(sig, filterFreq, filterRQ);
	// sig = BLowShelf.ar(sig, 90, 0.3, );
	sig = Pan2.ar(sig, pan);
	Out.ar(out, sig * amp);
  	DetectSilence.ar(sig, doneAction: 2);
    Out.ar(out, PanAz.ar(2, sig, pan, 1, 2, 0.8) * amp);
}).add;

SynthDef(\dropletVerb, {
    |in=0, out=0, mix=0.2, room=0.83, damp=0.5, amp=1, gate=1, release=0.5|
	var sig = In.ar(in, 2);
	var env = EnvGen.kr(Env.asr(0.01, 1, release), gate, doneAction: 2);
	sig = FreeVerb2.ar(sig[0], sig[1], mix, room.clip(0, 0.95), damp);
	sig = LeakDC.ar(sig);
	Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;
	fxBus = Bus.audio(s, 2);
	verbSynth = Synth.tail(group, \dropletVerb, [\in, fxBus, \out, 0, \mix, 0.2, \room, 0.3, \damp, 0.5]);

	Pdef(m.ptn,
		Pbind(
			\instrument, \miniDroplet,
			\group, group,
			\out, fxBus,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),             Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),     Pkey(\divIdx)),
			\dur,  Pkey(\div).reciprocal * beat,

			\accent, Pfunc({ |e|
				var st = e[\step] ? 0, dv = e[\div] ? 1;
				if (st == 0, { 1.0 }, {
					if ((dv > 3) and: { st == (dv div: 2) }, { 0.7 }, { 0.45 })
				})
			}),

			\scale, Scale.minor,
			\pick, Pfunc({ |e| if(e[\step] == 0, { 0 }, { (e[\pal] ? 1).rand }) }),
			\note, Pfunc({ |e| notes[e[\pick].clip(0, notes.size - 1)] }),
			\octave, Pfunc({ |e| if(e[\step] == 0, { 3 }, { [3,5,6].choose }) }),

			\attack, 0.001,
			\filterRQ, Pwhite(0.5, 1.5),
			\pan, Pwhite(-0.8, 0.8),

			\amp, Pfunc({ |e| (e[\gestAmp] ? 0.12) * (e[\accent] ? 1.0) })
		);
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\pal, 1);
	Pdef(m.ptn).set(\gestAmp, 0.22);
	Pdef(m.ptn).set(\decay, 0.3);
	Pdef(m.ptn).set(\filterFreq, 1500);
	Pdef(m.ptn).set(\wobble, 10);

	Pdef(m.ptn).play(quant: beat);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	if (verbSynth.notNil) { verbSynth.set(\gate, 0) };

	fork {
		0.7.wait;
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
			verbSynth = nil;
		};
		if (fxBus.notNil) {
			fxBus.free;
			fxBus = nil;
		};
	};
};

//------------------------------------------------------------
~next = {|d|
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var sens = d.params.sensitivity;
	var e = m.accelMassFiltered;
	var idx = e.lincurve(0, 2.0 * sens, 0, divs.size - 1, 1).round.asInteger.clip(0, divs.size - 1);
	var pal = e.lincurve(0, 2.0 * sens, 1, notes.size, 1).round.asInteger.clip(1, notes.size);
	var amp = e.lincurve(0.0, 2.0 * sens, -50, -1, -2);
	var wob = ((d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5) * 2).lincurve(-1.0, 1.0, 0.01, 14000.0, -2);
	var cut = m.rrateMassFiltered.linexp(0.0, 2.0 * sens, 180, 4000);
	var ring = (d.sensors.gyroEvent.y / pi.half).lincurve(-1.0, 1.0, 0.05, 1.9, 2);

	if(amp < 49.neg, { amp = 120.neg });

	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\pal, pal);
	Pdef(m.ptn).set(\gestAmp, amp.dbamp * vol);
	Pdef(m.ptn).set(\filterFreq, cut);
	Pdef(m.ptn).set(\decay, ring);
	Pdef(m.ptn).set(\wobble, wob);
	Pdef(m.ptn).set(\root, m.com.root);
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
	// [(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2];//wobble
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];
	// [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
