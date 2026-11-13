var m = ~model;
var group;
var fxBus;
var verbSynth;
var buffer;
var wind;

var samplePath = "~/Downloads/biddyBirds/currawong2eq.wav".standardizePath;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.3;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
SynthDef(\currawong2, { |out = 0, bufnum = 0, amp = 0.3, rate = 1, vibRate = 5, vibDepth = 0, attack = 0.005|
	var vib = (SinOsc.kr(vibRate) * vibDepth).midiratio;
	var env = EnvGen.kr(Env.asr(attack, 1, 0.1));
	var sig = PlayBuf.ar(2, bufnum, rate * vib * BufRateScale.kr(bufnum), doneAction: 2);
	Out.ar(out, (sig[0] + sig[1]) * 0.5 * env * amp);
}).add;

SynthDef(\currawong2Verb, { |in = 0, out = 0, mix = 0.75, room = 2.8, damp = 0.4, pos = 0, width = 2, amp = 1, gate = 1|
	var numChans = s.options.numOutputBusChannels;
	var sig = In.ar(in, 1);
	var env = EnvGen.kr(Env.asr(0.01, 1, 0.5), gate, doneAction: 2);
	sig = FreeVerb.ar(sig, mix, room, damp);
	sig = LeakDC.ar(sig);
	Out.ar(out, PanAz.ar(numChans, sig, pos.lag(0.3), amp * env, width.lag(0.3), 0));
}).add;

SynthDef(\currawong2Wind, {

	|out=0, amp=0.5, density=0.5, strength=0.5,
     filterFreq=1000, filterQ=0.5,
     reverbMix=0.6, reverbRoom=0.5, reverbDamp=0.2, gate=0, my=0.5, mx=1x|

    var wind, filtered, reverbed;
    var densityMod, strengthMod;
	var env = EnvGen.ar(Env.asr(1.3,1.0,5.0), gate, doneAction:Done.freeSelf);

    // Modulate density and strength
	densityMod = LFNoise2.kr(0.1).range(0.8, 1.2) * mx.lag(5);
	strengthMod = LFNoise2.kr(0.2).range(0.8, 1.2) * my.lag(3.5);

    // Base wind sound
	wind = [WhiteNoise.ar(1),WhiteNoise.ar(1)] * 1;

    // Apply density control (affects graininess of the wind)
    wind = Dust.ar(densityMod * 10000) * wind * [1, pi];	

    // Apply strength control (affects amplitude and filter)
    wind = wind * strengthMod;

    // Apply resonant filter
	filtered = RLPF.ar(wind, filterFreq.lag(3)  * strengthMod.linexp(0, 1, 0.5, 2), filterQ);

    // Apply reverb
    reverbed = FreeVerb.ar(filtered, reverbMix, reverbRoom, reverbDamp);


	Out.ar(out, reverbed * amp.lag(2) * env);

}).add;

//------------------------------------------------------------
~init = ~init <> { |d|
	group = Group.new;
	fxBus = Bus.audio(s, 1);
	buffer = Buffer.read(s, samplePath, action: { |buf| postf("buffer alloc [%] \n", buf) });
	wind = Synth.head(group, \currawong2Wind, [\gate, 1]);
	verbSynth = Synth.tail(group, \currawong2Verb, [\in, fxBus, \out, 0, \mix, 0.45, \room, 0.8, \damp, 0.4, \pos, 0, \width, 2]);

	Pdef(m.ptn,
		Pbind(
			\instrument, \currawong2,
			\group, group,
			\out, fxBus,
			\bufnum, buffer,
			\note, Pseq([0,-2,5].stutter(3), inf),
			\dur, Pwhite(1.9,3.2),
			\rate, Pfunc({ |e| e[\note].value.midiratio }),
			\vibRate, 6,
			\vibDepth, 0.1,
		);
	);

	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).quant = 0;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;
	var verb = verbSynth;
	var dyingBus = fxBus;
	var dyingBuf = buffer;
	var tail = 4.0;
	wind !? { wind.set(\gate, 0) };
	wind = nil;
	group = nil;
	verbSynth = nil;
	fxBus = nil;
	buffer = nil;

	Pdef(m.ptn).remove;

	fork {
		tail.wait;
		verb !? { verb.set(\gate, 0) };
		1.0.wait;
		if (dying.notNil) {
			s.bind { dying.freeAll };
			s.sync;
			dying.free;
		};
		dyingBus !? { dyingBus.free };
		dyingBuf !? { postf("buffer dealloc [%] \n", dyingBuf); dyingBuf.free };
	};
};

//------------------------------------------------------------
~next = { |d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var startAt = 0.2 * sens;
	var stopAt = 0.025 * sens;
	var pos = d.sensors.gyroEvent.z / pi;
	var windAmp = m.accelMassFiltered.lincurve(0, 6 * sens,0,5,-6);
	var windMy = m.accelMassFiltered.linexp(0, 2 * sens,0.1,0.9);
	var windMx = m.rrateMassFiltered.linlin(0, 2 * sens,0.8,1.0);
	var windFreq = ((d.sensors.gyroEvent.y / 2pi) + 0.5).fold(0,0.5) * 2;

	if(Pdef(m.ptn).isPlaying.not and: { m.accelMassFiltered > startAt }, { Pdef(m.ptn).play });
	if(Pdef(m.ptn).isPlaying and: { m.accelMassFiltered < stopAt }, { Pdef(m.ptn).pause });

	Pdef(m.ptn).set(\amp, 1.5);
	verbSynth !? { verbSynth.set(\pos, pos) };

	windFreq = windFreq.linexp(0,1,600,1600);
	if(windAmp<0.03,{windAmp=0});
	if(windAmp>0.9,{windAmp=0.9});
	wind !? {
		wind.set(\amp, windAmp * 1.3 * vol);
		wind.set(\my, windMy);
		wind.set(\mx, windMx);
		wind.set(\filterFreq, windFreq);
	};
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d, p|

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;

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

	// COMPUTED values : this file's own ~next, recomputed
	// [m.accelMassFiltered, 0.025, 0.0125];//start / stop thresholds at sensitivity 0.5

	[m.accelMass, m.accelMassFiltered];
};
