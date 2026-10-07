// Ocean Wave Synth Engine
// Five independent spectral layers — no buses, no groups.
// Evaluate Section 0 first, then each layer block as needed.
// Attach controls via .set on the ~ globals.

// ============================================================
// === SECTION 0: BOOT ===
// ============================================================
(
s.options.blockSize = 64;
s.options.numOutputBusChannels = 2;
s.boot;
"Boot initiated. Evaluate layer sections after server is running.".postln;
)


// ============================================================
// === SECTION 1: SUB-BASS SWELL  \oceanSubBass ===
// ============================================================
// 20–120 Hz bulk displacement, slow LFO swell.
// Two-LFO swell + slow noise wobble → PinkNoise through BPF → HPF → Pan2.
(
// ~subBass.free;

SynthDef(\oceanSubBass, { |amp = 0.2,
    swellRate = 0.08, swellDepth = 0.5,
    freqLo = 30, freqHi = 80, rq = 0.8,
    hpfFreq = 20, pan = 0.0, out = 0|

    var swellLFO, freq, sig;

    swellLFO = SinOsc.kr(swellRate) * 0.6
             + SinOsc.kr(swellRate * 1.37) * 0.3
             + LFNoise1.kr(0.03) * 0.1;
    swellLFO = swellLFO.range(1 - swellDepth, 1.0);

    freq = LFNoise1.kr(0.07).range(freqLo, freqHi);
    sig  = BPF.ar(PinkNoise.ar, freq, rq);
    sig  = sig * swellLFO * amp;
    sig  = HPF.ar(sig, hpfFreq);
    sig  = Pan2.ar(sig, pan + LFNoise1.kr(0.04).range(-0.06, 0.06));
    Out.ar(out, sig);
}).add;

~subBass = Synth(\oceanSubBass);
"Sub-bass swell running.".postln;
)

// Spot checks:
~subBass.set(\amp, 0.8)
~subBass.set(\swellRate, 0.2)
~subBass.set(\swellDepth, 1.0)
~subBass.set(\freqLo, 10, \freqHi, 90)


// ============================================================
// === SECTION 2: BODY / BUBBLE CLOUD  \oceanBody ===
// ============================================================
// 120–600 Hz collective bubble resonances, BPF + extra LPF rolloff.
(
// ~body.free;

SynthDef(\oceanBody, { |amp = 0.3,
    swellRate = 0.08, swellDepth = 0.5,
    freqLo = 150, freqHi = 250, rq = 0.6,
    lpfFreq = 800, pan = 0.0, out = 0|

    var swellLFO, freq, sig;

    swellLFO = SinOsc.kr(swellRate) * 0.6
             + SinOsc.kr(swellRate * 1.37) * 0.3
             + LFNoise1.kr(0.03) * 0.1;
    swellLFO = swellLFO.range(1 - swellDepth, 1.0);

    freq = LFNoise1.kr(0.05).range(freqLo, freqHi);
    sig  = BPF.ar(PinkNoise.ar, freq, rq);
    sig  = LPF.ar(sig, lpfFreq);
    sig  = sig * swellLFO * amp;
    sig  = Pan2.ar(sig, pan + LFNoise1.kr(0.04).range(-0.06, 0.06));
    Out.ar(out, sig);
}).add;

~body = Synth(\oceanBody);
"Body / bubble cloud running.".postln;
)

// Spot checks:
//   ~body.set(\amp, 0.6)
~body.set(\freqHi, 400)


// ============================================================
// === SECTION 3: BREAK NOISE  \oceanBreakNoise ===
// ============================================================
// 400 Hz–4 kHz individual Minnaert bubble resonances.
// Default amp = 0 — user brings in.
(
// ~breakNoise.free;

SynthDef(\oceanBreakNoise, { |amp = 0.07,
    swellRate = 20.41, swellDepth = 0.9,
    freqLo = 500, freqHi = 9000, rq = 0.8,
    cutoffLo = 2000, cutoffHi = 4000,
    pan = 0.0, out = 0|

    var swellLFO, freq, cutoff, sig;

    swellLFO = SinOsc.kr(swellRate) * 0.6
             + SinOsc.kr(swellRate * 1.43) * 0.3
             + LFNoise1.kr(0.03) * 0.1;
    swellLFO = swellLFO.range(1 - swellDepth, 1.0);

    freq   = LFNoise1.kr(0.11).range(freqLo, freqHi);
    cutoff = LFNoise1.kr(0.17).range(cutoffLo, cutoffHi);
    sig    = BPF.ar(PinkNoise.ar, freq, rq);
    sig    = LPF.ar(sig, cutoff);
    sig    = sig * swellLFO * amp;
    sig    = Pan2.ar(sig, pan + LFNoise1.kr(0.05).range(-0.48, 0.58));
    Out.ar(out, sig);
}).add;

~breakNoise = Synth(\oceanBreakNoise);
"Break noise running (amp=0 by default).".postln;
)

// Spot checks:
~breakNoise.set(\amp, 0.5)
//   ~breakNoise.set(\amp, 0)


// ============================================================
// === SECTION 4: SPRAY / HISS  \oceanSpray ===
// ============================================================
// 2–10 kHz droplet impacts and foam.
// brightness (0–1) maps HPF cutoff: 0 → 2 kHz, 1 → 6 kHz.
// Default amp = 0 — user brings in.
(
// ~spray.free;

SynthDef(\oceanSpray, { |amp = 0.005,
    swellRate = 10.1, swellDepth = 0.99,
    brightness = 0.9, lpfFreq = 11000,
    pan = 0.0, out = 0|

    var swellLFO, hpfCutoff, sig;

    swellLFO = SinOsc.kr(swellRate) * 0.6
             + SinOsc.kr(swellRate * 1.37) * 0.3
             + LFNoise1.kr(0.23) * 0.1;
    swellLFO = swellLFO.range(1 - swellDepth, 1.0);

	hpfCutoff = brightness.linexp(0.001, 1, 9000, 13000) * LFNoise2.ar(0.3,0.4,0.5);
    sig = WhiteNoise.ar;
    sig = HPF.ar(sig, hpfCutoff);
    sig = LPF.ar(sig, lpfFreq);
    sig = sig * swellLFO * amp;
    sig = Pan2.ar(sig, pan + LFNoise1.kr(0.16).range(-0.08, 0.08));
    Out.ar(out, sig);
}).add;

~spray = Synth(\oceanSpray);
"Spray / hiss running (amp=0 by default).".postln;
)

// Spot checks:
//   ~spray.set(\amp, 0.3)
//   ~spray.set(\brightness, 0.9)


// ============================================================
// === SECTION 5: WAVE BREAK (TRIGGERED)  \oceanWaveBreak ===
// ============================================================
// Persistent synth — stays alive and idles silently between triggers.
// Trigger: ~waveBreak.set(\gate, 1); ~waveBreak.set(\gate, 0)
//
// gate → Trig.kr fires 1ms pulse → 4 per-layer EnvGens (doneAction:0)
// violence (0–1): scales level + spectral centroid (harder = brighter)
// scale  (0–1): stretches all envelope times
// Random pan offset sampled via Latch+LFNoise0 per trigger.
//
// Spectral centroid rises during decay:
//   sub (fastest) → body → bubble → hiss (longest tail)
(
// ~waveBreak.free;

SynthDef(\oceanWaveBreak, { |gate = 0,
    violence = 0.5, scale = 0.5,
    pan = 0.0, panSpread = 0.3,
    out = 0|

    var trig, v, panPos;
    var envSub, envBody, envBubble, envHiss;
    var subLayer, bodyLayer, bubbleLayer, hissLayer;
    var sig;

    trig   = Trig.kr(gate, 0.001);
    // Clamp violence for safe linexp input (must be > 0)
    v      = violence.clip(0.001, 1);
    // Random pan sampled at each trigger
    panPos = Latch.kr(LFNoise0.kr(1), trig) * panSpread + pan;

    // Per-layer envelopes — sub decays fastest, hiss lingers longest
    envSub = EnvGen.kr(
        Env([0, 1, 0.001], [0.08, 5.2], [\lin, \exp]),
        trig,
        levelScale: v,
        timeScale:  scale.linlin(0, 1, 0.5, 2.0),
        doneAction: 0
    );
    envBody = EnvGen.kr(
        Env([0, 1, 0.3, 0.001], [0.1, 1.5, 2.0], [\lin, \exp, \exp]),
        trig,
        levelScale: v,
        timeScale:  scale.linlin(0, 1, 0.6, 2.5),
        doneAction: 0
    );
    envBubble = EnvGen.kr(
        Env([0, 0.8, 0.4, 0.001], [0.12, 2.0, 2.5], [\lin, \exp, \exp]),
        trig,
        levelScale: v,
        timeScale:  scale.linlin(0, 1, 0.7, 3.0),
        doneAction: 0
    );
    envHiss = EnvGen.kr(
        Env([0, 0.3, 0.15, 0.001], [0.15, 1.5, 3.5], [\lin, \exp, \exp]),
        trig,
        levelScale: (v * 0.8),
        timeScale:  scale.linlin(0, 1, 0.9, 4.0),
        doneAction: 0
    );

    // Sub-bass thud: 30–90 Hz, violence → higher freq on harder impact
    subLayer = BPF.ar(PinkNoise.ar, v.linexp(0.001, 1, 40, 90), 0.6);
    subLayer = subLayer * envSub * 2.8;

    // Body crunch: 200–800 Hz, bubble cloud collective resonance
    bodyLayer = BPF.ar(PinkNoise.ar, v.linexp(0.001, 1, 250, 700), 0.5);
    bodyLayer = LPF.ar(bodyLayer, 1500) * envBody * 0.6;

    // Bubble noise: 500 Hz–3 kHz, individual Minnaert resonances
    bubbleLayer = BPF.ar(
        PinkNoise.ar + WhiteNoise.ar(0.3),
        v.linexp(0.001, 1, 600, 2500),
        0.7
    );
    bubbleLayer = LPF.ar(bubbleLayer, v.linexp(0.001, 1, 2000, 5000));
    bubbleLayer = bubbleLayer * envBubble * 0.5;

    // Spray/hiss: 3–10 kHz, droplet impacts and foam (longest tail)
    hissLayer = HPF.ar(WhiteNoise.ar, v.linexp(0.001, 1, 2000, 6000));
    hissLayer = LPF.ar(hissLayer, 17000) * envHiss * 0.35;

    sig = (subLayer + bodyLayer + bubbleLayer + hissLayer);
    sig = Pan2.ar(sig, panPos);
    Out.ar(out, sig);
}).add;

~waveBreak = Synth(\oceanWaveBreak);
"Wave break ready. Trigger: ~waveBreak.set(\\gate, 1); ~waveBreak.set(\\gate, 0)".postln;
)

// Spot checks:
~waveBreak.set(\violence, 0.4, \scale, 0.2)
~waveBreak.set(\gate, 1)
~waveBreak.set(\gate, 0)

//   // → hear crash; spectral centroid rises as sub dies first, hiss lingers
(
{
	var env = EnvGen.kr(Env([0.7, 0.9, 0.2, 0.001], [0.002, 0.49, 1.5], [\lin, \exp, \exp]), timeScale:1);
	BPF.ar(PinkNoise.ar(0.2), env.linlin(0,1,4000,13000), LFNoise1.ar(900).range(0.2,0.9))!2;

}.play
)

