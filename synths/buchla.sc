(
// Buchla-inspired synth
SynthDef(\buchlaInspired, {
    |out=0, freq=440, amp=0.5, pan=0, gate=1,
    attack=0.01, decay=0.3, sustain=0.5, release=1,
    osc1Waveform=0, osc2Waveform=0,
    osc1Ratio=1, osc2Ratio=1,
    osc1Index=1, osc2Index=1,
    osc1Level=1, osc2Level=1,
    lpgDecay=0.5, lpgSustain=0,
    complexOscFold=1, complexOscWarp=0.5,
    modOscFreq=1, modOscAmount=0,
    randomFreq=10, randomAmount=0,
    lowpassCutoff=1000, lowpassResonance=0.5,
    reverbMix=0.3, reverbRoom=0.5, reverbDamp=0.5|

    var sig, osc1, osc2, complexOsc, modOsc, rand, lpg, verb;
	var buf = [-1,0,1].as(LocalBuf);

    modOsc = SinOsc.ar(modOscFreq, 0, modOscAmount);
    rand = LFNoise2.kr(randomFreq).bipolar(randomAmount);

    osc1 = Select.ar(osc1Waveform, [
        SinOsc.ar(freq * osc1Ratio + modOsc + rand),
        Saw.ar(freq * osc1Ratio + modOsc + rand),
        Pulse.ar(freq * osc1Ratio + modOsc + rand, 0.5)
    ]);

    osc2 = Select.ar(osc2Waveform, [
        SinOsc.ar(freq * osc2Ratio + modOsc + rand),
        Saw.ar(freq * osc2Ratio + modOsc + rand),
        Pulse.ar(freq * osc2Ratio + modOsc + rand, 0.5)
    ]);

    complexOsc = SinOscFB.ar(freq + modOsc + rand, complexOscFold);
    complexOsc = Shaper.ar(
        buf,
        complexOsc
    );

    sig = (osc1 * osc1Level * osc1Index) + (osc2 * osc2Level * osc2Index) + complexOsc;

    // Low Pass Gate (LPG) simulation
    lpg = EnvGen.ar(
        Env.new([0, 1, lpgSustain, 0], [attack, lpgDecay, release], [2, -4, -4], 2),
        gate,
        doneAction: 2
    );
    sig = sig * lpg;

    // Lowpass filter
    sig = RLPF.ar(sig, lowpassCutoff, lowpassResonance);

    // Spatialization
    sig = PanAz.ar(4, sig, pan);

    // Reverb
    verb = FreeVerb.ar(sig, reverbMix, reverbRoom, reverbDamp);

    Out.ar(out, verb * amp);

}).add;
)
// 10 Example Patches

// 1. Complex Oscillator Drone
(
Synth(\buchlaInspired, [
    \freq, 55,
    \complexOscFold, 2.5, \complexOscWarp, 0.7,
    \modOscFreq, 0.1, \modOscAmount, 10,
    \lpgDecay, 2.0, \lpgSustain, 0.8,
    \lowpassCutoff, 2000,
	\lowpassResonance, 0.3,
    \attack, 0.5, \decay, 1.0, \sustain, 0.8, \release, 2.0,
    \reverbMix, 0.4, \reverbRoom, 0.8, \reverbDamp, 0.2
]);
)

// 2. Metallic Percussion
(
// s.record;

Synth(\buchlaInspired, [
    \freq,440,
    \osc1Waveform, 1, \osc1Ratio, 2.7, \osc1Index, 0.8,
    \osc2Waveform, 0, \osc2Ratio, 4.5, \osc2Index, 0.7,
    \complexOscFold, 1.5, \complexOscWarp, 0.3,
    \lpgDecay, 0.1, \lpgSustain, 0,
    \lowpassCutoff, 5000, \lowpassResonance, 0.1,
    \attack, 0.001, \decay, 0.1, \sustain, 0.1, \release, 0.5,
    \reverbMix, 0.2, \reverbRoom, 0.3, \reverbDamp, 0.5
]);
)
// 3. Buchla Bongo
(
Synth(\buchlaInspired, [
    \freq, 110,
    \osc1Waveform, 0, \osc1Ratio, 1, \osc1Index, 1,
    \osc2Waveform, 0, \osc2Ratio, 1.7, \osc2Index, 0.5,
    \complexOscFold, 1.2, \complexOscWarp, 0.6,
    \lpgDecay, 0.1, \lpgSustain, 0,
    \lowpassCutoff, 800, \lowpassResonance, 0.2,
    \attack, 0.001, \decay, 0.1, \sustain, 0, \release, 0.1,
    \reverbMix, 0.1, \reverbRoom, 0.2, \reverbDamp, 0.7
]);
)
// 4. Alien Texture
(
Synth(\buchlaInspired, [
    \freq, 220,
    \osc1Waveform, 2, \osc1Ratio, 0.5, \osc1Index, 0.7,
    \osc2Waveform, 1, \osc2Ratio, 0.25, \osc2Index, 0.8,
    \complexOscFold, 3, \complexOscWarp, 0.8,
    \modOscFreq, 0.2, \modOscAmount, 50,
    \randomFreq, 2, \randomAmount, 20,
    \lpgDecay, 1.5, \lpgSustain, 0.5,
    \lowpassCutoff, 3000, \lowpassResonance, 0.4,
    \attack, 0.2, \decay, 0.5, \sustain, 0.7, \release, 1.0,
    \reverbMix, 0.5, \reverbRoom, 0.9, \reverbDamp, 0.1
]);
)
// 5. West Coast Bass
(
Synth(\buchlaInspired, [
    \freq, 55,
    \osc1Waveform, 1, \osc1Ratio, 1, \osc1Index, 1,
    \osc2Waveform, 1, \osc2Ratio, 2, \osc2Index, 0.5,
    \complexOscFold, 2, \complexOscWarp, 0.3,
    \lpgDecay, 0.3, \lpgSustain, 0.2,
    \lowpassCutoff, 600, \lowpassResonance, 0.7,
    \attack, 0.01, \decay, 0.1, \sustain, 0.8, \release, 0.2,
    \reverbMix, 0.1, \reverbRoom, 0.2, \reverbDamp, 0.8
]);
)
// 6. Buchla Bells
(
Synth(\buchlaInspired, [
    \freq, 880,
    \osc1Waveform, 0, \osc1Ratio, 1, \osc1Index, 1,
    \osc2Waveform, 0, \osc2Ratio, 2.7, \osc2Index, 0.3,
    \complexOscFold, 1.5, \complexOscWarp, 0.5,
    \modOscFreq, 5, \modOscAmount, 10,
    \lpgDecay, 1.0, \lpgSustain, 0,
    \lowpassCutoff, 5000, \lowpassResonance, 0.1,
    \attack, 0.001, \decay, 2.0, \sustain, 0, \release, 2.0,
    \reverbMix, 0.4, \reverbRoom, 0.8, \reverbDamp, 0.2
]);
)
// 7. Cosmic Wind
(
Synth(\buchlaInspired, [
    \freq, 440,
    \osc1Waveform, 1, \osc1Ratio, 0.5, \osc1Index, 0.7,
    \osc2Waveform, 2, \osc2Ratio, 0.25, \osc2Index, 0.6,
    \complexOscFold, 2.5, \complexOscWarp, 0.7,
    \modOscFreq, 0.1, \modOscAmount, 100,
    \randomFreq, 0.5, \randomAmount, 50,
    \lpgDecay, 2.0, \lpgSustain, 0.5,
    \lowpassCutoff, 2000, \lowpassResonance, 0.3,
    \attack, 1.0, \decay, 1.0, \sustain, 0.8, \release, 3.0,
    \reverbMix, 0.6, \reverbRoom, 0.9, \reverbDamp, 0.1
]);
)
// 8. Plucked String
(
Synth(\buchlaInspired, [
    \freq, 220,
    \osc1Waveform, 1, \osc1Ratio, 1, \osc1Index, 1,
    \osc2Waveform, 1, \osc2Ratio, 2, \osc2Index, 0.5,
    \complexOscFold, 1.2, \complexOscWarp, 0.3,
    \lpgDecay, 0.1, \lpgSustain, 0,
    \lowpassCutoff, 3000, \lowpassResonance, 0.2,
    \attack, 0.001, \decay, 0.5, \sustain, 0, \release, 0.5,
    \reverbMix, 0.2, \reverbRoom, 0.4, \reverbDamp, 0.5
]);
)
(
// 9. Buchla Keyboard
Synth(\buchlaInspired, [
    \freq, 440,
    \osc1Waveform, 2, \osc1Ratio, 1, \osc1Index, 1,
    \osc2Waveform, 1, \osc2Ratio, 2, \osc2Index, 0.5,
    \complexOscFold, 1.5, \complexOscWarp, 0.5,
    \modOscFreq, 6, \modOscAmount, 5,
    \lpgDecay, 0.2, \lpgSustain, 0.5,
    \lowpassCutoff, 2000, \lowpassResonance, 0.3,
    \attack, 0.01, \decay, 0.1, \sustain, 0.8, \release, 0.3,
    \reverbMix, 0.2, \reverbRoom, 0.3, \reverbDamp, 0.5
]);
)
(
// 10. Chaos Patch
Synth(\buchlaInspired, [
    \freq, 110,
    \osc1Waveform, 2, \osc1Ratio, 0.33, \osc1Index, 1,
    \osc2Waveform, 1, \osc2Ratio, 2.7, \osc2Index, 0.8,
    \complexOscFold, 3, \complexOscWarp, 0.9,
    \modOscFreq, 0.3, \modOscAmount, 200,
    \randomFreq, 20, \randomAmount, 100,
    \lpgDecay, 0.5, \lpgSustain, 0.5,
    \lowpassCutoff, 4000, \lowpassResonance, 0.6,
    \attack, 0.05, \decay, 0.2, \sustain, 0.7, \release, 1.0,
    \reverbMix, 0.4, \reverbRoom, 0.7, \reverbDamp, 0.3
]);)