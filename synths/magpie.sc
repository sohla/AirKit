(
SynthDef(\magpie, {
    |out =0,freq = 2000, warbleSpeed = 8, warbleDepth = 100,
        chirpRate = 1, chirpDur = 0.1, noiseMix = 0.2,
        amp = 0.3, pan = 0, gate=1|

    var warble, chirp, noise, sig;

    // Warbling whistle
    warble = SinOsc.ar(
        freq: freq + (SinOsc.kr(warbleSpeed) * warbleDepth),
        mul: LFPulse.kr(chirpRate, width: chirpDur)
    );

    // Chirp envelope
    chirp = EnvGen.ar(
        Env.perc(0.01, 0.1),
        gate: gate
    );

    // Noise component for harsh sounds
    noise = PinkNoise.ar(noiseMix) * chirp;

    // Combine warble and noise
    sig = (warble + noise) * chirp;

    // Apply overall envelope and panning
    sig = sig * EnvGen.kr(Env.asr(0.17, 1, 0.05), gate, doneAction:2);
    sig = Pan2.ar(sig, pan);

    sig * amp;
	Out.ar(out, sig);
}).add;
)

(
Pbindef(\magpiePattern,
    \instrument, \magpie,
    \dur, 0.2,
    \freq, Pexprand(740, 900, inf),
    \amp, Pexprand(0.05, 0.15, inf),  // Random amplitude
    \pan, Pwhite(-1.0, 1.0, inf),    // Random panning
	\chirpRate, 0.9
).play;
)
