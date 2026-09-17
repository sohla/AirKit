(// ARP Odyssey inspired synth
SynthDef(\arpOdyssey, {
    |out=0, freq=440, amp=0.5, pan=0, gate=1,
    attack=0.01, decay=0.3, sustain=0.5, release=1,
    cutoff=1000, resonance=0.5,
    osc1Waveform=0, osc2Waveform=0,
    osc1Octave=0, osc2Octave=0,
    osc1Detune=0, osc2Detune=0,
    osc1Level=1, osc2Level=1,
    oscSync=0,
    pwm=0.5, pwmRate=1,
    fmAmount=0,
    noiseLevel=0,
    lfoRate=1, lfoWaveform=0, lfoAmount=0, lfoDestination=0,
    filterEnvAmount=0, filterAttack=0.01, filterDecay=0.3, filterSustain=0.5, filterRelease=1,
    sampleHold=0, sampleHoldRate=10|

    var sig, env, filterEnv, lfo, osc1, osc2, noise, pwmOsc, sh;

    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
    filterEnv = EnvGen.kr(Env.adsr(filterAttack, filterDecay, filterSustain, filterRelease), gate);

    lfo = Select.ar(lfoWaveform, [
        SinOsc.ar(lfoRate),
        LFSaw.ar(lfoRate),
        LFPulse.ar(lfoRate, width: 0.5),
        LFNoise0.ar(lfoRate)
    ]);

    sh = Latch.kr(WhiteNoise.kr, Impulse.kr(sampleHoldRate));
    lfo = Select.kr(sampleHold, [lfo, sh]);

    pwmOsc = LFPulse.kr(pwmRate);

    osc1 = Select.ar(osc1Waveform, [
        Saw.ar(freq * (2 ** osc1Octave) * (1 + osc1Detune)),
        Pulse.ar(freq * (2 ** osc1Octave) * (1 + osc1Detune), pwm + (pwmOsc * 0.4)),
        SinOsc.ar(freq * (2 ** osc1Octave) * (1 + osc1Detune))
    ]);

    osc2 = Select.ar(osc2Waveform, [
        Saw.ar(freq * (2 ** osc2Octave) * (1 + osc2Detune) * (1 + (osc1 * fmAmount))),
        Pulse.ar(freq * (2 ** osc2Octave) * (1 + osc2Detune) * (1 + (osc1 * fmAmount)), pwm + (pwmOsc * 0.4)),
        SinOsc.ar(freq * (2 ** osc2Octave) * (1 + osc2Detune) * (1 + (osc1 * fmAmount)))
    ]);

    osc2 = Select.ar(oscSync, [osc2, SyncSaw.ar(osc2, osc1)]);

    noise = WhiteNoise.ar(noiseLevel);

    sig = (osc1 * osc1Level) + (osc2 * osc2Level) + noise;

    sig = Select.ar(lfoDestination, [
        sig,
        sig * (1 + (lfo * lfoAmount)),
        sig
    ]);

    sig = RLPF.ar(
        sig,
        Select.kr(lfoDestination, [
            cutoff + (filterEnv * filterEnvAmount),
            cutoff + (filterEnv * filterEnvAmount),
            cutoff + (filterEnv * filterEnvAmount) + (lfo * lfoAmount * 1000)
        ]),
        resonance.linexp(0, 1, 1, 0.05)
    );

    sig = sig * env * amp;
    sig = Pan2.ar(sig, pan);
    Out.ar(out, sig);
}).add;
)
// 10 Example Patches

// 1. Classic Bass
Synth(\arpOdyssey, [
    \freq, 55,
    \osc1Waveform, 0, \osc1Level, 1,
    \osc2Waveform, 1, \osc2Level, 0.7, \osc2Detune, 0.01,
    \cutoff, 800, \resonance, 0.6,
    \attack, 0.01, \decay, 0.2, \sustain, 0.4, \release, 0.2,
    \filterEnvAmount, 3000, \filterAttack, 0.01, \filterDecay, 0.1, \filterSustain, 0.2, \filterRelease, 0.1
]);

// 2. Screaming Lead
Synth(\arpOdyssey, [
    \freq, 440,
    \osc1Waveform, 1, \osc1Level, 1,
    \osc2Waveform, 0, \osc2Level, 0.8, \osc2Detune, 0.02,
    \oscSync, 1,
    \cutoff, 3000, \resonance, 0.7,
    \attack, 0.05, \decay, 0.1, \sustain, 0.8, \release, 0.3,
    \filterEnvAmount, 2000, \filterAttack, 0.01, \filterDecay, 0.1, \filterSustain, 0.7, \filterRelease, 0.2,
    \lfoRate, 5, \lfoAmount, 0.2, \lfoDestination, 1
]);

// 3. Funky Clav
Synth(\arpOdyssey, [
    \freq, 220,
    \osc1Waveform, 1, \osc1Level, 1,
    \osc2Waveform, 1, \osc2Level, 0.5, \osc2Detune, 0.01,
    \pwm, 0.2, \pwmRate, 0.5,
    \cutoff, 2000, \resonance, 0.8,
    \attack, 0.01, \decay, 0.3, \sustain, 0.4, \release, 0.1,
    \filterEnvAmount, 3000, \filterAttack, 0.01, \filterDecay, 0.1, \filterSustain, 0.3, \filterRelease, 0.1
]);

// 4. Atmospheric Pad
Synth(\arpOdyssey, [
    \freq, 110,
    \osc1Waveform, 2, \osc1Level, 1,
    \osc2Waveform, 2, \osc2Level, 1, \osc2Detune, 0.02,
    \cutoff, 1000, \resonance, 0.3,
    \attack, 1.5, \decay, 0.5, \sustain, 0.7, \release, 2.0,
    \lfoRate, 0.2, \lfoAmount, 0.3, \lfoDestination, 2,
    \noiseLevel, 0.05
]);

// 5. Percussive Synth
Synth(\arpOdyssey, [
    \freq, 330,
    \osc1Waveform, 0, \osc1Level, 1,
    \osc2Waveform, 1, \osc2Level, 0.7, \osc2Octave, 1,
    \cutoff, 5000, \resonance, 0.4,
    \attack, 0.001, \decay, 0.1, \sustain, 0.1, \release, 0.1,
    \filterEnvAmount, 5000, \filterAttack, 0.001, \filterDecay, 0.05, \filterSustain, 0.1, \filterRelease, 0.05
]);

// 6. Sample & Hold Madness
Synth(\arpOdyssey, [
    \freq, 220,
    \osc1Waveform, 0, \osc1Level, 1,
    \osc2Waveform, 0, \osc2Level, 1, \osc2Detune, 0.05,
    \cutoff, 2000, \resonance, 0.7,
    \attack, 0.01, \decay, 0.1, \sustain, 0.5, \release, 0.5,
    \lfoRate, 10, \lfoAmount, 0.5, \lfoDestination, 2,
    \sampleHold, 1, \sampleHoldRate, 20
]);

// 7. FM Bell
Synth(\arpOdyssey, [
    \freq, 440,
    \osc1Waveform, 2, \osc1Level, 1,
    \osc2Waveform, 2, \osc2Level, 0.5, \osc2Octave, 2,
    \fmAmount, 0.2,
    \cutoff, 3000, \resonance, 0.2,
    \attack, 0.01, \decay, 1.0, \sustain, 0.3, \release, 1.0,
    \filterEnvAmount, 1000, \filterAttack, 0.01, \filterDecay, 0.5, \filterSustain, 0.3, \filterRelease, 0.5
]);

// 8. Sweeping Bass
Synth(\arpOdyssey, [
    \freq, 55,
    \osc1Waveform, 0, \osc1Level, 1,
    \osc2Waveform, 0, \osc2Level, 1, \osc2Detune, 0.01,
    \cutoff, 500, \resonance, 0.7,
    \attack, 0.01, \decay, 0.2, \sustain, 0.6, \release, 0.3,
    \filterEnvAmount, 3000, \filterAttack, 0.01, \filterDecay, 0.3, \filterSustain, 0.4, \filterRelease, 0.2,
    \lfoRate, 0.2, \lfoAmount, 0.5, \lfoDestination, 2
]);

// 9. Noisy Texture
Synth(\arpOdyssey, [
    \freq, 110,
    \osc1Waveform, 1, \osc1Level, 0.7,
    \osc2Waveform, 0, \osc2Level, 0.7, \osc2Detune, 0.05,
    \noiseLevel, 0.3,
    \cutoff, 2000, \resonance, 0.5,
    \attack, 0.5, \decay, 0.3, \sustain, 0.7, \release, 1.0,
    \lfoRate, 3, \lfoAmount, 0.3, \lfoDestination, 2,
    \lfoWaveform, 3
]);

// 10. PWM Strings
Synth(\arpOdyssey, [
    \freq, 220,
    \osc1Waveform, 1, \osc1Level, 1,
    \osc2Waveform, 1, \osc2Level, 1, \osc2Detune, 0.01,
    \pwm, 0.5, \pwmRate, 0.3,
    \cutoff, 1500, \resonance, 0.3,
    \attack, 0.2, \decay, 0.3, \sustain, 0.6, \release, 0.8,
    \filterEnvAmount, 1000, \filterAttack, 0.1, \filterDecay, 0.3, \filterSustain, 0.5, \filterRelease, 0.5
]);




(

Pdef(\a,
Pbind(
    \instrument, \arpOdyssey,
		\dur,0.25,
		\note, Pseq([0,5,2,9,4], inf),
		\octave, Pseq([2,3,4,5].stutter(2)+1, 4),
		\amp,0.1,

    \osc1Waveform, 2, \osc1Level, 1.0,
    \osc2Waveform, 0, \osc2Level, 1, \osc2Detune, 0.007,
    \cutoff, 500, \resonance, 0.2,
		    \pwm, 0.9, \pwmRate, 0.3,

    \attack, 0.2, \decay, 0.2, \sustain, 0.6, \release, 0.3,
    \filterEnvAmount,1000, \filterAttack, 0.1, \filterDecay, 0.3, \filterSustain, 0.4, \filterRelease, 0.5,
    \lfoRate, 0.2, \lfoAmount, 0.9, \lfoDestination, 2

);
).play(quant:0.0);
)