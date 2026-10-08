(
SynthDef(\whipbird, {
    |out=0, pan=0, amp=0.3, gate=1, swoopDelay=0.03, gliss=0.01,pitchRand=1,
        reverbMix=0.3, reverbTime=2.0, reverbSize=0.8|

    var whipEnv, whistleEnv, whipOsc, whistleOsc, sig, mainEnv;
    var numCombs = 7;
    var numAllpass = 4;
    var room = reverbSize.clip(0.1, 0.9);

    // Main envelope for the whole sound
    mainEnv = EnvGen.kr(
        Env.asr(0.01, 1, 0.5),
        gate
		// doneAction:2
    );

    // Whip crack envelope
    whipEnv = EnvGen.ar(
        Env.perc(0.001, 0.03, curve: -8),
        gate
    );

    // Rising whistle envelope with adjustable delay
    whistleEnv = EnvGen.ar(
        Env(
            [0, 0, 1, 0],
            [gliss, 0.15, 0.2],
            curve: [0, 2, -4]
        ),
        gate
    );

    // Whip crack sound - enhanced with slight resonance
    whipOsc = WhiteNoise.ar *
        BPF.ar(
            PinkNoise.ar,
            freq: XLine.kr(12000, 2000, 0.03),
            rq: 0.05,
        ) +
        Resonz.ar(
            PinkNoise.ar,
            XLine.kr(9000, 1500, 0.04),
            0.1,
            0.2
        );

    // Rising whistle with more character
    whistleOsc = SinOsc.ar(
        freq: Env(
			1000 + [2000, 2000, 4000, 3800] * pitchRand,
            [swoopDelay, 0.15, 0.15],
            [\step, \sine, -3]
        ).kr
    ) * whistleEnv;

    // Combine both sounds
    sig = (whipOsc * whipEnv * 0.2) + (whistleOsc * 0.2);
	sig = Pan2.ar(sig, pan);
    // Forest-like reverb using feedback delay network
    sig = FreeVerb2.ar(
		sig[0], sig[1],
        mix: reverbMix,
        room: room,
        damp: 0.3
    );

    // Additional early reflections for forest feel
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
)
(
// Pattern to play the whipbird with forest-like variations
Pbindef(\whipPattern,
    \instrument, \whipbird,
    \dur, Pwhite(0.8, 3.8),         // Random timing
    \pan, Pwhite(-1, 1),        // Spatial distribution
    \amp, Pwhite(0.8, 0.7),         // Dynamic variation
    \swoopDelay, Pwhite(0.01, 0.03),// Varied swoop timing
	\gliss, Pwhite(0.07, 0.2),
	\pitchRand, Pwhite(0.6,0.9),
    \reverbMix, 0.5,                // Consistent reverb level
    \reverbTime, 3.0,               // Forest-like decay
    \reverbSize, 0.7                // Large space simulation
).play;
)


// To play:
Pbindef(\whipPattern).play;

// To stop:
Pbindef(\whipPattern).stop;

// For a single test:
Synth(\whipbird, [\amp, 0.9, \swoopDelay, 0.5, \gliss, 0.34, \pitchRand, 0.6.rrand(0.8),\reverbMix, 0.3, \gate,1]);
s.meter


