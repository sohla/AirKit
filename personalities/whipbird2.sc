var m = ~model;

var group;
var clock;
var fxBus;
var verbSynth;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.15;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay  = 0.6;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
SynthDef(\whipbird2, {
    |out=0, pan=0, amp=0.3, gate=1, hold=0, whip=0.03, gliss=0.01,pitchRand=1, freq = 440, swoop = 2|

    var whipEnv, whistleEnv, whipOsc, whistleOsc, sig, mainEnv;
    var numCombs = 7;
    var numAllpass = 4;

    mainEnv = EnvGen.kr(
        Env.asr(0.02, 1, 0.8),
        gate
		// doneAction:2
    );

    whipEnv = EnvGen.ar(
        Env.perc(0.001, 0.03, curve: -8),
        gate
    );

    whistleEnv = EnvGen.ar(
        Env(
            [0, 0, 1, 1, 0],
            [gliss, 0.15, hold + whip, 0.2],
            curve: [0, 2, 0, -4]
        ),
        gate
    );

    whipOsc = WhiteNoise.ar *
        BPF.ar(
            PinkNoise.ar,
            freq: XLine.kr(12000, 2000, 0.03),
            rq: 0.05,
        ) +
        Resonz.ar(
            PinkNoise.ar,
            XLine.kr(9000, 1500, 0.03),
            0.1,
            0.2
        );

    whistleOsc = SinOsc.ar(
        freq: Env(
			[freq, freq, freq * swoop, freq * swoop * 0.95],
            [hold, whip, 0.15],
            [0, \sine, -3]
        ).kr
    ) * whistleEnv;

    sig = (whipOsc * whipEnv * 0.2) + (whistleOsc * 0.2);
	sig = Pan2.ar(sig * 0.5, pan);

	DetectSilence.ar(sig, time:0.3, doneAction:2);
    Out.ar(out, sig * amp * mainEnv);
}).add;

SynthDef(\whipbird2Verb, {
    |in=0, out=0, reverbMix=0.3, reverbSize=0.8|
    var room = reverbSize.clip(0.1, 0.9);
    var sig = In.ar(in, 2);

    sig = FreeVerb2.ar(
		sig[0], sig[1],
        mix: reverbMix,
        room: room,
        damp: 0.3
    );

    sig = sig + [0.033, 0.039, 0.045].collect({ |t|
        DelayC.ar(sig, 0.1, t) * LFNoise2.kr(0.1).range(0.01, 0.02)
    }).sum;

    Out.ar(out, sig * 2);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	clock = TempoClock(2);
	group = Group.new;
	fxBus = Bus.audio(s, 2);
	verbSynth = Synth.tail(group, \whipbird2Verb,
		[\in, fxBus, \out, 0, \reverbMix, 0.5, \reverbSize, 0.7]);

	Pdef(m.ptn,
		Pbind(
			\instrument, \whipbird2,
			\out, fxBus,
			\group, group,
			\pan, Pwhite(-1.0, 1.0),
			
			// \dur, Pseq([2,1,1,2,1,1], inf),
			\sustain, 2,
			\sendGate, false
		);
	);

	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).play(clock);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;
	var dyingBus = fxBus;
	var c = clock;
	group = nil;
	clock = nil;
	fxBus = nil;
	verbSynth = nil;

	Pdef(m.ptn).remove;
	c !? { c.stop };

	fork {
		if (dying.notNil) {
			s.bind { dying.freeAll };
			s.sync;
			dying.free;
		};
		dyingBus !? { dyingBus.free };
	};
};

//------------------------------------------------------------
~next = { |d|
	var baseShift  = 61 + 0;
	var idleNotes  = [0,4,7,11,14];
	var gain = 4;
	var whipInterval = 36;
	var holdTime = 0.1;
	var whipTime = 0.05;
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.accelMassFiltered.lincurve(0, 2.6 * sens, -40, 0, -2);
	var dur = m.accelMassFiltered.lincurve(0, 2.6 * sens, 1, 0.2, -2);
	var swoop = m.accelMassFiltered.lincurve(1.3 * sens, 2.6 * sens, 12, whipInterval, 0).midiratio;

	if(amp < -38, { amp = -110 });

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, (amp + gain).dbamp * vol);
	Pdef(m.ptn).set(\freq, (baseShift + idleNotes.choose).midicps);
	Pdef(m.ptn).set(\hold, holdTime, \whip, whipTime);
	Pdef(m.ptn).set(\gliss, 0.07);
	Pdef(m.ptn).set(\swoop, swoop);

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 2;
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
	// [m.accelMassFiltered.lincurve(0, 0.3, -40, 0, -2).dbamp];//amp at sensitivity 0.5
	// [m.accelMassFiltered.lincurve(0.65, 1.3, 0, 1, 0)];//whip widening at sensitivity 0.5

	[m.accelMassFiltered, m.gyroYFiltered];
};
