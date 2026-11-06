var m = ~model;

var group;
var clock;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.15;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay  = 0.6;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
SynthDef(\whipbird, {
    |out=0, pan=0, amp=0.3, gate=1, swoopDelay=0.03, gliss=0.01,pitchRand=1, freq = 440,
        reverbMix=0.3, reverbTime=2.0, reverbSize=0.8|

    var whipEnv, whistleEnv, whipOsc, whistleOsc, sig, mainEnv;
    var numCombs = 7;
    var numAllpass = 4;
    var room = reverbSize.clip(0.1, 0.9);

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
            [0, 0, 1, 0],
            [gliss, 0.15, 0.2],
            curve: [0, 2, -4]
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
			[freq, freq * 2, freq * 1.9],
            [swoopDelay, 0.15, 0.15],
            [\sine, \sine, -3]
        ).kr
    ) * whistleEnv;

    sig = (whipOsc * whipEnv * 0.2) + (whistleOsc * 0.2);
	sig = Pan2.ar(sig * 0.5, pan);
    sig = FreeVerb2.ar(
		sig[0], sig[1],
        mix: reverbMix,
        room: room,
        damp: 0.3
    );

    sig = sig + DelayN.ar(
		sig,
        0.1,
        [0.033, 0.039, 0.045].collect({ |t|
            sig * LFNoise2.kr(0.1).range(0.01, 0.02) * DelayC.ar(sig, 0.1, t)
        }).sum
    );

	DetectSilence.ar(sig, time:0.3, doneAction:2);
    Out.ar(out, sig * amp * mainEnv);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	clock = TempoClock(2);
	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \whipbird,
			\group, group,
			\pan, Pwhite(-1.0, 1.0),
			
			// \dur, Pseq([2,1,1,2,1,1], inf),
			\sustain, 2,
			\sendGate, false,
			\reverbMix, 0.5,
			\reverbTime, 3.0,
			\reverbSize, 0.7,

			\type, \customVisualEvent,
			\shape, \circle,
			\sx, Pkey(\pan),
			\ex, Pkey(\pan),
			\sy, Pfunc({ |e| (e[\freq] ? 440).explin(200, 1200, 0.5, -0.5) }),
			\ey, Pkey(\sy) - 0.2,
			\startSize, Pfunc({ |e| (e[\amp] ? 0).linlin(0, 1, 0, 60) }),
			\endSize, Pfunc({ |e| (e[\amp] ? 0).linlin(0, 1, 0, 140) }),
			\startColor, Color.white,
			\endColor, Color(1, 1, 1, 0),
			\duration, 0.6
		);
	);

	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).play(clock);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;
	var c = clock;
	group = nil;
	clock = nil;

	Pdef(m.ptn).remove;
	c !? { c.stop };

	fork {
		if (dying.notNil) {
			4.wait;
			s.bind { dying.freeAll };
			s.sync;
			dying.free;
		};
	};
};

//------------------------------------------------------------
~next = { |d|
	var baseShift  = 61 + 0;
	var idleNotes  = [0,4,7,11,14];
	var gain = 4;
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.accelMassFiltered.lincurve(0, 2.6 * sens, -40, -5, -2);
	var dur = m.accelMassFiltered.lincurve(0, 2.6 * sens, 1, 0.2, -2);

	if(amp < -38, { amp = -110 });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, (amp + gain).dbamp * vol);
	Pdef(m.ptn).set(\freq, (baseShift + idleNotes.choose).midicps);
	Pdef(m.ptn).set(\swoopDelay, 0.0);
	Pdef(m.ptn).set(\gliss, 0.07);

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

	[m.accelMassFiltered, m.gyroYFiltered];
};
