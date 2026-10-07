/*
=============================================================================
  ROCK SPLASH SYNTHESIZER — SuperCollider SynthDef
=============================================================================

  PHYSICS REFERENCES
  ──────────────────
  • Minnaert (1933) "On musical air-bubbles and the sounds of running water"
      f_m ≈ 3.3 / R   (Hz, R in metres, atmospheric pressure)
      This formula defines every bubble pitch in the cloud.

  • van den Doel (2005) "Physically based models for liquid sounds" — ACM TAP
      Bubbles are the PRIMARY acoustic mechanism in water sounds.
      Each bubble ≈ decaying sinusoid at its Minnaert frequency.
      Log-normal distribution of bubble radii.

  • Zheng & James (2009) "Harmonic Fluids" — ACM SIGGRAPH
      Superposition of many bubble oscillators → convincing water sound.

  • Moss et al. (2010) "Sounding Liquids" — ACM TOG
      Bubble generation from fluid velocity + surface curvature.

  • ScienceDirect (2019) "Liquid-solid interaction sound synthesis"
      Splits LSIS into: (1) bubble sound + (2) impact sound.

  • Gekle et al. (2009) Physical Review Letters
      Cavity forms behind sinking object; hydrostatic pressure pinches it
      at the midpoint (hourglass shape) → delayed pressure pop.

  PHYSICAL TIMELINE
  ─────────────────
    t = 0 ms        IMPACT — rock pierces surface
    t = 0–5 ms      CROWN SPLASH — water sheet ejected around rim
    t = 0–50 ms     CAVITY FORMATION — air cavity trails rock downward
    t = 5–15 ms     SURFACE BUBBLE — large air pocket at surface
    t = 50–200 ms   CAVITY PINCH-OFF — hourglass collapse → sharp pop
    t = 50 ms–2 s   BUBBLE CLOUD — many Minnaert oscillators
    t = ongoing     SURFACE TURBULENCE — drip-backs, spray, ripple

  SOUND COMPONENTS
  ────────────────
    A. Impact transient     broadband crack + body thud       (0–15 ms)
    B. Hollow cavity tone   Helmholtz resonator, freq arc     (0–250 ms)
    C. Cavity collapse pop  delayed pressure pulse            (~80–200 ms)
    D. Bubble cloud         12 Minnaert oscillators, staggered
    E. Turbulent splash     layered noise (spray/gurgle/rumble)

  ARGS
  ────
    out             0       output bus
    amp             0.5     master amplitude
    pan             0.0     stereo position (-1 … +1)
    rockSize        0.3     0-1: pebble → boulder
                              scales timing, freq range, bubble sizes
    impactVelocity  0.6     0-1: gentle plop → hard throw
                              scales amplitude of impact + pop
    waterDepth      0.4     0-1: shallow pool → deep lake
                              scales reverb room size, cavity resonance Q
    bubblyness      0.7     0-1: sparse → dense bubble cloud
    splashiness     0.5     0-1: quiet entry → explosive crown splash
    reverbMix       0.25    0-1: dry → wet
    envScale        1.0     global time stretch (useful from Patterns)

=============================================================================
*/

(
SynthDef(\rockSplash, {

    |out=0, amp=0.5, pan=0,
     rockSize=0.3,
     impactVelocity=0.6,
     waterDepth=0.4,
     bubblyness=0.7,
     splashiness=0.5,
     reverbMix=0.25,
     envScale=1.0|

    // ── All vars declared at top of scope ──────────────────────────────────

    // Derived scaling
    var sizeScale, freqScale, velAmp;

    // A — Impact transient
    var impactEnv, impactNoise, impactHigh, impactLow, impactSnd;

    // B — Hollow cavity tone
    var cavEnv, cavFreqBase, cavFreq, cavQ, cavNoise, cavResonant, cavSine, cavSnd;

    // C — Cavity collapse pop
    var popDelay, popTrig, popEnv, popFreq, popSnd;

    // D — Bubble cloud
    var bubbleFreqLo, bubbleFreqHi, bubbleSnd;

    // E — Turbulent splash noise
    var splashEnv, splashNoise, splashHigh, splashMid, splashLow, splashSnd;

    // Mix + output
    var dry, revRoomSize, rev, wet, totalDur, masterEnv;


    // ════════════════════════════════════════════════════════════════════════
    // DERIVED SCALING FACTORS
    // ════════════════════════════════════════════════════════════════════════

    sizeScale = rockSize.linexp(0, 1, 0.3, 5.0);  // bigger rock → slower, lower
    freqScale = rockSize.linexp(0, 1, 2.5, 0.2);  // bigger rock → lower freqs
    velAmp    = impactVelocity.linexp(0, 1, 0.15, 1.0);


    // ════════════════════════════════════════════════════════════════════════
    // A. IMPACT TRANSIENT  (t ≈ 0 ms,  dur ≈ 0–15 ms)
    //
    //    Physics: Rapid energy transfer at liquid-solid collision.
    //    Creates a broadband surface crack (shockwave through water sheet)
    //    plus a low-frequency body thud (inertia of displaced water mass).
    //
    //    WhiteNoise → BPF    surface crack, mid-to-high band
    //    WhiteNoise → Resonz  body thud, low resonant peak
    // ════════════════════════════════════════════════════════════════════════

    impactEnv   = EnvGen.kr(Env.perc(0.0004, 0.014 * sizeScale, 1, -7), doneAction: 0);
    impactNoise = WhiteNoise.ar;
    impactHigh  = BPF.ar(impactNoise, 2500 * freqScale, 1.0) * 0.65;
    impactLow   = Resonz.ar(impactNoise, 110 * freqScale, 0.35) * 0.9;
    impactSnd   = (impactHigh + impactLow) * impactEnv * velAmp;


    // ════════════════════════════════════════════════════════════════════════
    // B. HOLLOW CAVITY TONE  (t ≈ 0–250 ms)
    //
    //    Physics: The trailing air column behind the sinking rock behaves as
    //    a Helmholtz resonator. Volume grows (→ freq drops) then collapses
    //    (→ freq briefly rises). This is the hollow "plop" character.
    //
    //    LFNoise1 → Resonz   resonant filtered noise, hollow character
    //    SinOsc              pitched centre for body
    //    Freq envelope arc   mirrors the cavity volume arc (hi → dip → rise)
    // ════════════════════════════════════════════════════════════════════════

    cavEnv = EnvGen.kr(
        Env(
            [0, 0.95, 0.55, 0.70, 0],
            [0.004, 0.07 * sizeScale, 0.04 * sizeScale, 0.18 * sizeScale],
            [-1, -3, 2, -6]
        ),
        doneAction: 0
    );

    cavFreqBase = 260 * freqScale;

    cavFreq = EnvGen.kr(
        Env(
            [cavFreqBase * 1.5, cavFreqBase * 0.55, cavFreqBase * 1.1],
            [0.09 * sizeScale,  0.09 * sizeScale],
            \sine
        ),
        doneAction: 0
    );

    cavQ        = waterDepth.linlin(0, 1, 0.08, 0.20);
    cavNoise    = LFNoise1.ar(9000);
    cavResonant = Resonz.ar(cavNoise, cavFreq, cavQ);
    cavSine     = SinOsc.ar(cavFreq * 0.78) * 0.28;
    cavSnd      = (cavResonant * 0.72 + cavSine * 0.28) * cavEnv * velAmp;


    // ════════════════════════════════════════════════════════════════════════
    // C. CAVITY COLLAPSE POP  (delayed trigger, t ≈ 80–200 ms)
    //
    //    Physics (Gekle et al. 2009): Hydrostatic pressure pinches cavity
    //    at its midpoint into an hourglass. The pinch singularity concentrates
    //    energy and radiates a sharp upward pressure pulse.
    //    Larger rocks → cavity grows longer before pinching → later pop.
    //
    //    TDelay.kr(Impulse.kr(0), delay)  one-shot delayed trigger
    //    SinOsc + XLine downward glide    pressure wave dispersing outward
    // ════════════════════════════════════════════════════════════════════════

    popDelay = (0.07 * sizeScale) + (0.05 * impactVelocity);
    popTrig  = TDelay.kr(Impulse.kr(0), popDelay);

    popEnv  = EnvGen.kr(
        Env.perc(0.0006, 0.022 * sizeScale.sqrt, 1, -9),
        gate: popTrig,
        doneAction: 0
    );

    popFreq = XLine.ar(650 * freqScale, 180 * freqScale, 0.022 * sizeScale.sqrt);
    popSnd  = SinOsc.ar(popFreq) * popEnv * velAmp * 0.65;


    // ════════════════════════════════════════════════════════════════════════
    // D. BUBBLE CLOUD  (t ≈ 50 ms – 1500 ms)
    //
    //    Physics (Minnaert 1933, van den Doel 2005):
    //      f ≈ 3.3 / R    (R in metres, atmospheric pressure)
    //      Bubble radii are log-normally distributed. Each bubble is a
    //      decaying sinusoid at its Minnaert frequency.
    //      As a bubble contracts, R decreases → f rises slightly.
    //      Lower-frequency (larger) bubbles decay more slowly.
    //
    //    Minnaert examples (scaled by freqScale for rock size):
    //      R = 1 mm  → 3300 Hz   (tiny surface bubbles)
    //      R = 5 mm  →  660 Hz   (small cloud bubbles)
    //      R = 20mm  →  165 Hz   (large initial cavity bubble)
    //
    //    FM (modulator at 2× carrier): adds shimmer from non-spherical mode
    //    mixing — real bubbles are never perfectly spherical (Moss 2010).
    //
    //    Mix.fill(12) — 12 independent oscillators, vars defined inside
    //    the closure so each iteration gets its own random values.
    // ════════════════════════════════════════════════════════════════════════

    bubbleFreqLo = (165  * freqScale).clip(60,  600);
    bubbleFreqHi = (3300 * freqScale).clip(400, 7000);

    bubbleSnd = Mix.fill(12, {
        var bFreq, bDecay, bOnset, bFreqGlide, bMod, bCarrier, bTrig, bEnv;

        bFreq      = ExpRand(bubbleFreqLo, bubbleFreqHi);
        bDecay     = (5.0 / bFreq).linexp(1/7000, 1/60, 0.04, 0.9)
                      * sizeScale * ExpRand(0.5, 2.0);
        bOnset     = ExpRand(0.04, 0.55) * sizeScale;
        bFreqGlide = Line.kr(bFreq, bFreq * ExpRand(1.05, 1.25), bDecay);
        bMod       = SinOsc.ar(bFreqGlide * 2.0) * (bFreq * Rand(0.01, 0.06));
        bCarrier   = SinOsc.ar(bFreqGlide + bMod);
        bTrig      = TDelay.kr(Impulse.kr(0), bOnset.max(0.005));
        bEnv       = EnvGen.ar(
            Env.perc(0.0008, bDecay, Rand(0.4, 1.0), -5),
            gate: bTrig,
            doneAction: 0
        );

        bCarrier * bEnv * ExpRand(0.3, 1.0)
    });

    bubbleSnd = bubbleSnd * 0.11 * bubblyness;


    // ════════════════════════════════════════════════════════════════════════
    // E. TURBULENT SPLASH NOISE  (t ≈ 5 ms – 450 ms)
    //
    //    Physics (Franz 1959, "Splashes as sources of sound in liquids"):
    //      Crown splash ejects thin water sheet; its breakup and droplet
    //      re-entry creates broadband noise across three bands.
    //
    //    High  HPF 2.5k    airborne spray / hiss
    //    Mid   BPF 700     water turbulence and gurgle
    //    Low   LPF 280     body displacement / slosh
    // ════════════════════════════════════════════════════════════════════════

    splashEnv = EnvGen.kr(
        Env(
            [0, 1.0, 0.35, 0],
            [0.006, 0.10 * sizeScale, 1.40 * sizeScale],
            [3, -2, -5]
        ),
        doneAction: 0
    );

    splashNoise = WhiteNoise.ar;
    splashHigh  = HPF.ar(LPF.ar(splashNoise, 18000), 9500) * 0.95;
    splashMid   = BPF.ar(splashNoise, 700 * freqScale, 2.2) * 0.40;
    splashLow   = LPF.ar(splashNoise, 280 * freqScale) * 0.30;
    splashSnd   = (splashHigh + splashMid + splashLow)
                   * splashEnv * velAmp * splashiness;


    // ════════════════════════════════════════════════════════════════════════
    // MIX, SOFT LIMIT, REVERB, OUTPUT
    // ════════════════════════════════════════════════════════════════════════

    dry = impactSnd + cavSnd + popSnd + bubbleSnd + splashSnd;
    // tanh soft limiting — preserves transient character, prevents clipping
    dry = dry.tanh;

    // Deeper water → larger acoustic space
    revRoomSize = waterDepth.linlin(0, 1, 0.15, 0.85);
    rev         = FreeVerb.ar(dry, reverbMix, revRoomSize, 0.35);
    wet         = (dry * (1.0 - reverbMix * 0.4)) + (rev * reverbMix);

    // Master lifetime covers the full bubble cloud duration
    // max onset (0.55 * sizeScale) + max decay (0.9 * sizeScale) + margin
    totalDur  = (1.8 * sizeScale * envScale).max(0.5);
    masterEnv = EnvGen.kr(
        Env([1, 1, 0], [totalDur * 0.82, totalDur * 0.18], [0, -3]),
        doneAction: 2
    );

    Out.ar(out, Pan2.ar(wet * amp * masterEnv, pan));

}).add;
)


// =============================================================================
//  QUICK TEST — evaluate s.boot first, then run individual Synth calls
// =============================================================================

// s.boot;

// Pebble, gentle toss
Synth(\rockSplash, [rockSize: 0.09, impactVelocity: 0.1, bubblyness: 0.7,splashiness: 0.3, waterDepth: 0.3, amp: 0.6]);

// Medium rock
Synth(\rockSplash, [rockSize: 0.35, impactVelocity: 0.6, bubblyness: 0.7,
                    splashiness: 0.5, waterDepth: 0.4, amp: 0.6]);

// Large rock, hard throw
Synth(\rockSplash, [rockSize: 0.8, impactVelocity: 0.9, bubblyness: 0.9,
                    splashiness: 0.8, waterDepth: 0.6, reverbMix: 0.4, amp: 0.5]);

// Boulder, deep lake
Synth(\rockSplash, [rockSize: 1.0, impactVelocity: 1.0, bubblyness: 1.0,
                    splashiness: 1.0, waterDepth: 0.9, reverbMix: 0.5, amp: 0.4]);

// Sweep through sizes
(0.0, 0.12 .. 1.0).do { |rs, i|
    SystemClock.sched(i * 2.5, {
        Synth(\rockSplash, [rockSize: rs, impactVelocity: rs + 0.3,
                             bubblyness: 0.7, splashiness: 0.5, amp: 0.5]);
        nil
    });
};


// =============================================================================
//  PATTERN NOTES  (implementation deferred)
// =============================================================================
(

    Pbind(
        \instrument,     \rockSplash,
        \dur,            Pwhite(0.1, 0.3),
        \amp,            Pwhite(0.3, 0.7),
        \pan,            Pwhite(-0.6, 0.6),
        \rockSize,       Pwhite(0.001, 0.1),
        \impactVelocity, Pwhite(0.1, 0.25),
        \bubblyness,     Pwhite(0.5, 1.0),
        \splashiness,    Pwhite(0.1, 0.2),
        \waterDepth,     Prand([0.2, 0.4, 0.6, 0.8], inf),
        \reverbMix,      Pwhite(0.2, 0.3),
        \envScale,       1.0,
    ).play;
)
// Natural correlations worth exploiting:
// rockSize    <-> impactVelocity  (bigger rock = harder throw)
// rockSize    <-> splashiness     (larger rock = bigger crown splash)
