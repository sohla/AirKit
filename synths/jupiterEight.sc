(// Roland Jupiter-8 inspired synth
SynthDef(\jupiter8, {
    |out=0, freq=440, amp=0.5, pan=0, gate=1,
    attack=0.01, decay=0.3, sustain=0.5, release=1,
    cutoff=1000, resonance=0.5,
    osc1Level=1, osc2Level=1,
    osc1Waveform=0, osc2Waveform=0,
    osc1Tune=0, osc2Tune=0,
    osc1PulseWidth=0.5, osc2PulseWidth=0.5,
    lfoRate=1, lfoDepth=0, lfoPitchMod=0, lfoFilterMod=0, lfoPWMod=0,
    filterEnvAmount=0, filterAttack=0.01, filterDecay=0.3, filterSustain=0.5, filterRelease=1,
    highpassCutoff=20,
    chorusLevel=0, chorusRate=0.3, chorusDepth=0.3|

    var sig, env, filterEnv, lfo, osc1, osc2, chorus;

    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
    filterEnv = EnvGen.kr(Env.adsr(filterAttack, filterDecay, filterSustain, filterRelease), gate);

    lfo = SinOsc.kr(lfoRate);

    osc1 = Select.ar(osc1Waveform, [
        Saw.ar(freq * (1 + osc1Tune) + (lfo * lfoPitchMod)),
        Pulse.ar(freq * (1 + osc1Tune) + (lfo * lfoPitchMod),
                 osc1PulseWidth + (lfo * lfoPWMod * 0.1))
    ]) * osc1Level;

    osc2 = Select.ar(osc2Waveform, [
        Saw.ar(freq * (1 + osc2Tune) + (lfo * lfoPitchMod)),
        Pulse.ar(freq * (1 + osc2Tune) + (lfo * lfoPitchMod),
                 osc2PulseWidth + (lfo * lfoPWMod * 0.1))
    ]) * osc2Level;

    sig = (osc1 + osc2) * 0.5;

    sig = RLPF.ar(
        sig,
        cutoff + (filterEnv * filterEnvAmount) + (lfo * lfoFilterMod * 1000),
        resonance.linexp(0, 1, 1, 0.05)
    );

    sig = HPF.ar(sig, highpassCutoff);

    chorus = DelayC.ar(sig, 0.1, SinOsc.kr(chorusRate, 0, chorusDepth, chorusDepth + 0.005));
    sig = XFade2.ar(sig, chorus, chorusLevel * 2 - 1);

    sig = sig * env * amp;
    sig = Pan2.ar(sig, pan);
    Out.ar(out, sig);
}).add;
)
// 10 Example Patches

// 1. Classic Brass
Synth(\jupiter8, [
    \freq, 220,
    \osc1Waveform, 1, \osc1Level, 1, \osc1PulseWidth, 0.3,
    \osc2Waveform, 1, \osc2Level, 0.7, \osc2Tune, 0.01, \osc2PulseWidth, 0.5,
    \cutoff, 2000, \resonance, 0.3,
    \attack, 0.05, \decay, 0.1, \sustain, 0.8, \release, 0.3,
    \filterEnvAmount, 2000, \filterAttack, 0.01, \filterDecay, 0.2, \filterSustain, 0.6, \filterRelease, 0.2,
    \chorusLevel, 0.3
]);

// 2. Lush Pad
Synth(\jupiter8, [
    \freq, 110,
    \osc1Waveform, 0, \osc1Level, 1,
    \osc2Waveform, 0, \osc2Level, 1, \osc2Tune, 0.02,
    \cutoff, 1200, \resonance, 0.2,
    \attack, 0.8, \decay, 0.5, \sustain, 0.7, \release, 1.5,
    \lfoRate, 0.2, \lfoFilterMod, 0.3,
    \chorusLevel, 0.5, \chorusRate, 0.2, \chorusDepth, 0.5
]);

// 3. Punchy Bass
Synth(\jupiter8, [
    \freq, 55,
    \osc1Waveform, 0, \osc1Level, 1,
    \osc2Waveform, 1, \osc2Level, 0.6, \osc2Tune, 0.01, \osc2PulseWidth, 0.2,
    \cutoff, 500, \resonance, 0.7,
    \attack, 0.01, \decay, 0.2, \sustain, 0.4, \release, 0.2,
    \filterEnvAmount, 3000, \filterAttack, 0.01, \filterDecay, 0.1, \filterSustain, 0.2, \filterRelease, 0.1
]);

// 4. Bright Lead
Synth(\jupiter8, [
    \freq, 440,
    \osc1Waveform, 1, \osc1Level, 1, \osc1PulseWidth, 0.6,
    \osc2Waveform, 0, \osc2Level, 0.7, \osc2Tune, 0.01,
    \cutoff, 3000, \resonance, 0.4,
    \attack, 0.01, \decay, 0.2, \sustain, 0.6, \release, 0.3,
    \lfoRate, 5, \lfoPitchMod, 0.02, \lfoPWMod, 0.1
]);

// 5. Soft Strings
Synth(\jupiter8, [
    \freq, 220,
    \osc1Waveform, 1, \osc1Level, 1, \osc1PulseWidth, 0.7,
    \osc2Waveform, 1, \osc2Level, 0.8, \osc2Tune, -0.01, \osc2PulseWidth, 0.6,
    \cutoff, 1500, \resonance, 0.2,
    \attack, 0.3, \decay, 0.4, \sustain, 0.8, \release, 1.0,
    \chorusLevel, 0.7, \chorusRate, 0.3, \chorusDepth, 0.4
]);

// 6. Percussive Synth
Synth(\jupiter8, [
    \freq, 330,
    \osc1Waveform, 1, \osc1Level, 1, \osc1PulseWidth, 0.2,
    \osc2Waveform, 0, \osc2Level, 0.5, \osc2Tune, 0.01,
    \cutoff, 2000, \resonance, 0.6,
    \attack, 0.001, \decay, 0.1, \sustain, 0.2, \release, 0.2,
    \filterEnvAmount, 4000, \filterAttack, 0.001, \filterDecay, 0.05, \filterSustain, 0.1, \filterRelease, 0.1
]);

// 7. Sweeping Pad
Synth(\jupiter8, [
    \freq, 165,
    \osc1Waveform, 0, \osc1Level, 1,
    \osc2Waveform, 1, \osc2Level, 0.7, \osc2Tune, 0.02, \osc2PulseWidth, 0.6,
    \cutoff, 1000, \resonance, 0.3,
    \attack, 1.0, \decay, 0.5, \sustain, 0.7, \release, 2.0,
    \lfoRate, 0.1, \lfoFilterMod, 0.5,
    \chorusLevel, 0.4
]);

// 8. Funky Clav
Synth(\jupiter8, [
    \freq, 220,
    \osc1Waveform, 1, \osc1Level, 1, \osc1PulseWidth, 0.1,
    \osc2Waveform, 1, \osc2Level, 0.5, \osc2Tune, 0.01, \osc2PulseWidth, 0.2,
    \cutoff, 3000, \resonance, 0.7,
    \attack, 0.01, \decay, 0.3, \sustain, 0.4, \release, 0.2,
    \filterEnvAmount, 2000, \filterAttack, 0.01, \filterDecay, 0.1, \filterSustain, 0.3, \filterRelease, 0.1
]);

// 9. Ethereal Atmosphere
Synth(\jupiter8, [
    \freq, 55,
    \osc1Waveform, 0, \osc1Level, 1,
    \osc2Waveform, 1, \osc2Level, 0.6, \osc2Tune, 0.007, \osc2PulseWidth, 0.8,
    \cutoff, 800, \resonance, 0.2,
    \attack, 2.0, \decay, 1.0, \sustain, 0.8, \release, 3.0,
    \lfoRate, 0.2, \lfoFilterMod, 0.3, \lfoPWMod, 0.2,
    \chorusLevel, 0.6, \chorusRate, 0.1, \chorusDepth, 0.7
]);

// 10. Rhythmic Pulse
Synth(\jupiter8, [
    \freq, 440,
    \osc1Waveform, 1, \osc1Level, 1, \osc1PulseWidth, 0.5,
    \osc2Waveform, 1, \osc2Level, 0.7, \osc2Tune, 0.01, \osc2PulseWidth, 0.3,
    \cutoff, 2000, \resonance, 0.5,
    \attack, 0.01, \decay, 0.1, \sustain, 0.5, \release, 0.2,
    \lfoRate, 8, \lfoFilterMod, 0.4, \lfoPWMod, 0.2,
    \filterEnvAmount, 1000, \filterAttack, 0.01, \filterDecay, 0.1, \filterSustain, 0.3, \filterRelease, 0.1
]);



(
// s.record;

Pdef(\a,
Pbind(
    \instrument, \jupiter8,
		\dur,0.25,
		\note, Pseq([0,5,2,9,4], inf),
		\octave, Pseq([2,3,4,5].stutter(2)+1, 4),
		\amp,0.5,
    \osc1Waveform, 0,
	\osc1Level, 1,
		\osc2Waveform, 1,
	\osc2Level, 1,
	\osc2Tune, 0.003,
		\osc2PulseWidth, 0.003,
    \cutoff, 3198,
	\resonance, 0.2,
    \attack, 0.02,
	\decay, 0.1,
	\sustain, 0.3,
	\release, 1.8,
    \lfoRate, 9.2,
	\lfoFilterMod, 0.9,
	\lfoPWMod, 0.01,
    \chorusLevel, 0.2,
	\chorusRate, 0.4,
	\chorusDepth, 0.007

);
).play(quant:0.0);
)