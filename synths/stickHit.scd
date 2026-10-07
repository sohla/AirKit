/*
=============================================================================
  STICKS HIT TOGETHER — SuperCollider SynthDef
=============================================================================

  INSTRUMENT
  ──────────
  Two hardwood sticks struck together: claves, rhythm sticks, dowels.
  Organological class: CONCUSSION IDIOPHONE — the sticks ARE the resonators,
  with no membrane, string, or air column. Sound comes entirely from the
  material vibrating in its structural modes.

  PHYSICS REFERENCES
  ──────────────────
  • Euler-Bernoulli free-free bar (classical mechanics)
      Bilbao (2009) "Sound Synthesis and Physical Modeling", Cambridge UP
      Euphonics.org §3.2.1 "Bending Beams and Free-Free Modes" (J. Woodhouse, Cambridge)

      For a uniform bar with BOTH ENDS FREE, bending mode frequency ratios are:
        f1 : f2 : f3 : f4 : f5 = 1 : 2.756 : 5.404 : 8.933 : 13.34
      These are solutions to the characteristic equation: cosh(kL)·cos(kL) = 1
      They are STRONGLY INHARMONIC — none are integer multiples of f1.
      This inharmonicity is the defining acoustic fingerprint of wood.
      (A tube has harmonic modes 1:2:3:4. A string has 1:2:3:4. Wood does not.)

      Longitudinal modes of the same bar ARE harmonically spaced (v/2L, 2v/2L, ...)
      but occur at much higher frequencies (~5-20kHz for clave-length sticks) and
      are usually excited only by very hard blows.

  • Klatzky, Pai & Krotkov (2000) "Perception of material from contact sounds"
      Presence: Teleoperators and Virtual Environments 9(4)
      The CONTACT CLICK is the primary perceptual cue for material hardness.
      Listeners identify wood vs. rubber vs. metal from the click duration/spectrum
      before the resonant ring begins. This makes the excitation signal —
      not just the filter bank — perceptually critical.

  • van den Doel & Pai (1998) "The Sounds of Physical Shapes" — Presence journal
  • van den Doel et al. (2001) "Modal Synthesis for Vibrating Objects"
      Modal synthesis framework: broadband excitation pulse → parallel Resonz bank.
      Each Resonz = one vibrational mode: (centre freq, amplitude, decay time).
      Contact-point selectivity: striking at a nodal position of mode n → that
      mode receives zero energy; striking at an anti-node → maximum energy.

  • Hall (1986/1987) "Piano string excitation" — JASA 81(2)
  • Hertz contact mechanics:
      Contact force pulse ≈ half-sine of duration τ ∝ 1/√K
      where K is contact stiffness (material + geometry).
      Harder wood → higher K → shorter τ → richer in high frequencies.
      This models why: rubber mallet = soft thud, hardwood = razor-sharp crack.

  • Anderson & Peterson (2012) "The song of the singing rod" — JASA 131(3)
      Confirms measured bending mode ratios in real rods match Euler-Bernoulli theory.
      Hard strikes excite both bending and longitudinal mode families.

  RESONZ DECAY FORMULA
  ────────────────────
  Resonz.ar(input, freq, rq):
    BW    = rq × freq                 (3dB bandwidth in Hz)
    A(t)  = exp(−π × BW × t)         (exponential envelope)
    t60   = 6.9078 / (π × BW)        (time to reach −60 dB)
    ∴ rq  = 6.9078 / (π × t60 × freq)

  This formula is used for every modal filter below. All decay times are t60.

  PHYSICAL TIMELINE
  ─────────────────
    t = 0 ms        CONTACT — sticks meet at contact point
    t = 0–1 ms      CLICK — Hertz contact pulse (material identity cue)
    t = 0–10 ms     BENDING WAVES — propagate outward from contact point
    t = 0 ms+       BOTH STICKS RING — at their respective bending mode frequencies
    t = 0 ms+       The two slightly-different spectra beat against each other

  SOUND COMPONENTS
  ────────────────
    A.  Contact click         Hertz pulse through BPF. The material voice.
    B.  Stick A bending ring  Receiver ("la hembra"). 4 Euler-Bernoulli modes.
    C.  Stick B bending ring  Striker ("el macho"). Same model, shorter decay.
    D.  Body thump            Sub-fundamental low energy. Heavy sticks only.

  KEY PHYSICAL INSIGHT — TWO RESONATORS
  ──────────────────────────────────────
  Both sticks vibrate simultaneously after impact. The sound is a superposition
  of two inharmonic spectra at slightly different fundamentals (freq vs. freq2).
  The beating between these two slightly-detuned spectra is what gives claves
  their characteristic "alive" quality. Setting freq == freq2 collapses this
  to a single dead-sounding blob.

  TYPICAL FREQUENCY VALUES (bending fundamental)
  ───────────────────────────────────────────────
    Rosewood claves (20cm × Ø2.5cm)    1800–2500 Hz
    Birch rhythm sticks (30cm)          900–1400 Hz
    Bamboo sticks (40cm)                600–1100 Hz
    Large hardwood dowel (50cm)         300–600 Hz
    Pencil / chopstick                  2500–4000 Hz

  ARGS
  ────
    out           0      output bus
    amp           0.7    master amplitude
    pan           0.0    stereo position (-1 … +1)
    freq          2200   Hz: bending fundamental of receiver stick (stick A)
    freq2         2100   Hz: bending fundamental of striker stick (stick B)
                           set freq2 slightly different from freq for liveness
    decay         0.8    sec: t60 of receiver fundamental
                           0.05=very muted  0.3=soft wood  1.5=dense hardwood
    strikeForce   0.7    0-1: light tap to hard blow
                           scales amplitude; also shapes excitation spectrum
    strikePos     0.28   0-1: contact point as fraction of stick length
                           0 = end-of-stick, 0.5 = centre, 0.28 = typical clave
                           modulates amplitude ratio between high and low modes
    woodHardness  0.8    0-1: soft/light wood to dense hardwood (rosewood/ebony)
                           0.3 = balsa / pine, 0.6 = birch, 0.8 = oak, 1.0 = ebony
    muting        0.0    0-1: free ring to hand-damped / wet contact

=============================================================================
*/

(
SynthDef(\stickHit, {

    |out=0, amp=0.7, pan=0,
     freq=2200,
     freq2=2100,
     decay=0.8,
     strikeForce=0.7,
     strikePos=0.28,
     woodHardness=0.8,
     muting=0.0|

    // ── All vars declared at top of scope ──────────────────────────────────

    // Derived scaling
    var forceScale, effectiveDecay, exciteDur, hardScale, posScale;

    // Single raw impulse — fans out into all Decay.ar lines
    var rawImpulse;

    // A — Contact click
    var clickExcite, clickFilt, clickSnd;

    // Shared modal excitation (drives all Resonz banks)
    var excite;

    // B — Stick A (receiver, "la hembra") — 4 bending modes
    var fA1, fA2, fA3, fA4;
    var dA1, dA2, dA3, dA4;
    var rqA1, rqA2, rqA3, rqA4;
    var aA1, aA2, aA3, aA4;
    var mA1, mA2, mA3, mA4, ringA;

    // C — Stick B (striker, "el macho") — 4 bending modes
    var fB1, fB2, fB3, fB4;
    var dB1, dB2, dB3, dB4;
    var rqB1, rqB2, rqB3, rqB4;
    var aB1, aB2, aB3, aB4;
    var mB1, mB2, mB3, mB4, ringB;

    // D — Body thump
    var thumpExcite, thumpSnd;

    // Mix + output
    var dry, masterEnv;


    // ════════════════════════════════════════════════════════════════════════
    // DERIVED SCALING FACTORS
    // ════════════════════════════════════════════════════════════════════════

    forceScale     = strikeForce.linexp(0, 1, 0.1, 1.0);

    // muting scales down ring time — models hand damping or wet surface
    effectiveDecay = decay * (1.0 - muting * 0.93).max(0.02);

    // Contact duration (Hertz mechanics): harder / faster → shorter → more HF.
    // Hard dry wood at full force: ~0.2ms. Soft wood, light tap: ~4ms.
    exciteDur      = (woodHardness * strikeForce).linexp(0, 1, 0.004, 0.0002);

    // hardScale: upper modes need harder strikes to be clearly audible
    hardScale      = woodHardness.linexp(0, 1, 0.3, 1.0);

    // posScale: simplified contact-point brightness proxy.
    // posScale = 1 at ends (strikePos=0,1), = 0 at centre (strikePos=0.5).
    // Physical basis: striking near a bending anti-node excites that mode maximally.
    // Mode 3 is suppressed at the centre (centre is a nodal line for mode 3
    // of a free-free bar); end-striking excites all modes. This is a perceptual
    // approximation — the full calculation requires eigenfunction evaluation at
    // each contact position for each mode.
    posScale       = (strikePos * 2.0 - 1.0).abs;   // 1=ends, 0=centre

    // Single impulse fires once at t=0; all Decay.ar units fan out from it
    rawImpulse = Impulse.ar(0);


    // ════════════════════════════════════════════════════════════════════════
    // A. CONTACT CLICK  (t = 0, duration = exciteDur)
    //
    //    Physics (Klatzky 2000, Hertz contact mechanics):
    //      When two hard surfaces collide, the contact force pulse is
    //      approximately a half-sine of duration τ ≈ 1/√(contact stiffness).
    //      Hard material (high K): short τ → wideband spectrum → bright click.
    //      Soft material (low K): long τ → low-pass spectrum → dull thud.
    //
    //      This click is the PRIMARY perceptual cue for material hardness.
    //      Human listeners can identify material type from the click alone,
    //      before the resonant ring has started.
    //
    //    Model:
    //      Decay.ar(rawImpulse, exciteDur)     exponential pulse (contact shape)
    //      → WhiteNoise shaped by BPF          surface micro-roughness noise
    //      BPF centre scales with hardness: 2kHz (soft) → 8kHz (hard ebony)
    // ════════════════════════════════════════════════════════════════════════

    clickExcite = Decay.ar(rawImpulse, exciteDur);
    clickFilt   = BPF.ar(WhiteNoise.ar, 2000 + (woodHardness * 6000), 0.9);
    clickSnd    = clickFilt * clickExcite * forceScale * 0.85;


    // ════════════════════════════════════════════════════════════════════════
    // SHARED MODAL EXCITATION
    //
    //    The same short Decay pulse (slightly longer than click) drives all
    //    Resonz filter banks. This is the standard modal synthesis source:
    //    broadband impulse → parallel resonator bank (van den Doel 2001).
    //
    //    Longer exciteDur = lower-frequency content in excitation = softer
    //    striking or less dense material. Upper modes receive less energy.
    //    The 1.5× factor makes the modal excitation slightly smoother than
    //    the raw contact click — matching the transition from contact to ring.
    // ════════════════════════════════════════════════════════════════════════

    excite = Decay.ar(rawImpulse, exciteDur * 1.5);


    // ════════════════════════════════════════════════════════════════════════
    // B. STICK A — RECEIVER ("la hembra")  BENDING MODES
    //
    //    Physics (Euler-Bernoulli beam theory, Bilbao 2009, Euphonics 3.2.1):
    //
    //    FREE-FREE BAR BENDING MODE RATIOS:
    //      f1 : f2 : f3 : f4 = 1 : 2.756 : 5.404 : 8.933
    //    Eigenvalues of:  cosh(βnL) · cos(βnL) = 1
    //    These are INHARMONIC — none are integer ratios.
    //    Verified experimentally by Anderson & Peterson (2012 JASA).
    //
    //    MODAL DECAY TIMES (frequency-dependent damping):
    //      For wood, energy loss has two main contributions:
    //        (1) Internal / viscoelastic damping (proportional to frequency)
    //        (2) Radiation damping (increases with frequency for small bodies)
    //      Result: higher modes decay faster.
    //      Approximation used here:   t60_n = t60_1 / sqrt(ratio_n)
    //      This is a perceptual proxy (not exact) but gives correct qualitative
    //      decay balance. True values need material Q measurements.
    //
    //    RESONZ rq PARAMETER:
    //      rq = 6.9078 / (π × t60 × freq)    [derived in header above]
    //
    //    MODE AMPLITUDES:
    //      Shaped by two factors:
    //        (1) strikeForce: harder blow → more energy in all modes
    //        (2) posScale: end-strikes are brighter (more high modes);
    //                      centre-strikes are more fundamental-heavy
    //      Upper modes (3, 4) additionally require harder wood to be audible
    //      (harder material → shorter excitation → more HF → more upper modes).
    //
    //    RECEIVER HOLD: cupped hand with fingers loose → minimal hand damping
    //    → longest possible ring time. This is how claves are played: one stick
    //    rests in the cups formed by the fingers, never gripped.
    // ════════════════════════════════════════════════════════════════════════

    // Euler-Bernoulli free-free bar mode frequencies
    fA1 = freq;
    fA2 = freq * 2.756;
    fA3 = freq * 5.404;
    fA4 = freq * 8.933;

    // Modal decay times: t60_n = t60_1 / sqrt(ratio_n)
    dA1 = effectiveDecay;
    dA2 = effectiveDecay / 2.756.sqrt;    // ÷ 1.660
    dA3 = effectiveDecay / 5.404.sqrt;    // ÷ 2.325
    dA4 = effectiveDecay / 8.933.sqrt;    // ÷ 2.989

    // Resonz rq: rq = 6.9078 / (π × t60 × freq)
    rqA1 = 6.9078 / (pi * dA1 * fA1);
    rqA2 = 6.9078 / (pi * dA2 * fA2);
    rqA3 = 6.9078 / (pi * dA3 * fA3);
    rqA4 = 6.9078 / (pi * dA4 * fA4);

    // Mode amplitudes — contact-position selectivity (simplified proxy)
    // Mode 1: always dominant (anti-node at ends and centre for free-free mode 1)
    aA1 = forceScale * 1.0;
    // Mode 2: slightly more excited at end-strikes (posScale high) than centre
    aA2 = forceScale * (0.32 + posScale * 0.14);
    // Modes 3 & 4: scale up with end-strikes and with harder material
    aA3 = forceScale * (0.12 + posScale * 0.10) * hardScale;
    aA4 = forceScale * (0.04 + posScale * 0.05) * hardScale;

    // The four bending mode resonators
    mA1 = Resonz.ar(excite, fA1.clip(20, 20000), rqA1.clip(0.0001, 0.9)) * aA1;
    mA2 = Resonz.ar(excite, fA2.clip(20, 20000), rqA2.clip(0.0001, 0.9)) * aA2;
    mA3 = Resonz.ar(excite, fA3.clip(20, 20000), rqA3.clip(0.0001, 0.9)) * aA3;
    mA4 = Resonz.ar(excite, fA4.clip(20, 20000), rqA4.clip(0.0001, 0.9)) * aA4;

    ringA = mA1 + mA2 + mA3 + mA4;


    // ════════════════════════════════════════════════════════════════════════
    // C. STICK B — STRIKER ("el macho") BENDING MODES
    //
    //    Physics: Same Euler-Bernoulli model. Two differences:
    //
    //    (1) DIFFERENT FUNDAMENTAL (freq2 vs freq):
    //        Real sticks are never perfectly matched. The slight frequency
    //        difference causes inter-stick beating — the beating produces the
    //        "live", "lively" quality of well-matched claves. The beating period
    //        is 1/(freq - freq2); for a 100Hz difference: ~10ms = perceptible shimmer.
    //        For patterns, vary freq2 independently from freq.
    //
    //    (2) SHORTER DECAY (~50% of receiver):
    //        The striker is held between thumb and forefinger. This firm grip
    //        provides significant contact damping on the stick's lower end.
    //        Hand mass also loads the stick, lowering Q of all modes.
    //        Represented here as effectiveDecay * 0.5.
    //
    //    (3) LOWER AMPLITUDE (~60% of receiver):
    //        Some kinetic energy goes into the striking motion itself.
    //        The receiver has more time to build up oscillation amplitude
    //        from its free support condition.
    // ════════════════════════════════════════════════════════════════════════

    fB1 = freq2;
    fB2 = freq2 * 2.756;
    fB3 = freq2 * 5.404;
    fB4 = freq2 * 8.933;

    // Striker: ~50% of receiver decay time (firm grip = more damping)
    dB1 = effectiveDecay * 0.5;
    dB2 = dB1 / 2.756.sqrt;
    dB3 = dB1 / 5.404.sqrt;
    dB4 = dB1 / 8.933.sqrt;

    rqB1 = 6.9078 / (pi * dB1 * fB1);
    rqB2 = 6.9078 / (pi * dB2 * fB2);
    rqB3 = 6.9078 / (pi * dB3 * fB3);
    rqB4 = 6.9078 / (pi * dB4 * fB4);

    // Striker amplitudes: ~60% of receiver, fewer upper modes (less free end)
    aB1 = forceScale * 0.60;
    aB2 = forceScale * (0.20 + posScale * 0.10);
    aB3 = forceScale * 0.08 * hardScale;
    aB4 = forceScale * 0.03 * hardScale;

    mB1 = Resonz.ar(excite, fB1.clip(20, 20000), rqB1.clip(0.0001, 0.9)) * aB1;
    mB2 = Resonz.ar(excite, fB2.clip(20, 20000), rqB2.clip(0.0001, 0.9)) * aB2;
    mB3 = Resonz.ar(excite, fB3.clip(20, 20000), rqB3.clip(0.0001, 0.9)) * aB3;
    mB4 = Resonz.ar(excite, fB4.clip(20, 20000), rqB4.clip(0.0001, 0.9)) * aB4;

    ringB = mB1 + mB2 + mB3 + mB4;


    // ════════════════════════════════════════════════════════════════════════
    // D. BODY THUMP  (sub-fundamental, low-frequency)
    //
    //    Physics: At the moment of impact, the two sticks briefly compress
    //    each other. The recoil energy partly excites very-low-frequency
    //    structural vibration (below the first bending mode). In heavy sticks
    //    this manifests as a perceptible low "thud" underlying the ring.
    //    In thin claves it is nearly inaudible.
    //
    //    Modelled as: a single low Resonz at ~freq/8 with moderate Q.
    //    Amplitude scales proportionally with strikeForce and inversely with
    //    woodHardness (softer = more mass-spring-like, more thump; harder =
    //    more instantaneous, more click and ring, less thump).
    //
    //    exciteDur is longer here (12ms base + more for soft wood) to model
    //    the slower energy transfer of sub-structural motion.
    // ════════════════════════════════════════════════════════════════════════

    thumpExcite = Decay.ar(rawImpulse, 0.012 + ((1.0 - woodHardness) * 0.02));
    thumpSnd    = Resonz.ar(
        thumpExcite,
        (freq * 0.12).clip(30, 400),
        0.55
    ) * forceScale * (1.0 - woodHardness * 0.78) * 0.30;


    // ════════════════════════════════════════════════════════════════════════
    // MIX AND OUTPUT
    // ════════════════════════════════════════════════════════════════════════

    dry = clickSnd + ringA + ringB + thumpSnd;

    // tanh soft-limits without hard clipping — preserves transient character
    dry = dry.tanh;

    // Master lifetime: governed by receiver fundamental (the longest ring).
    // Using 1.1× effectiveDecay as hold plateau, then 0.25× decay as tail.
    // doneAction:2 frees the node when envelope completes.
    masterEnv = EnvGen.kr(
        Env([1, 1, 0], [effectiveDecay * 1.1, effectiveDecay * 0.25], [0, -4]),
        doneAction: 2
    );

    Out.ar(out, Pan2.ar(dry * amp * masterEnv, pan));

}).add;
)


// =============================================================================
//  QUICK TESTS — evaluate s.boot first, then each block individually
// =============================================================================

// s.boot;

// Classic rosewood claves — tight and bright
Synth(\stickHit, [freq: 2200, freq2: 2080, decay: 0.9, strikeForce: 0.75,
                  strikePos: 0.28, woodHardness: 0.9, muting: 0.0, amp: 0.7]);

// Birch rhythm sticks — warmer, lower
Synth(\stickHit, [freq: 1100, freq2: 1050, decay: 0.6, strikeForce: 0.65,
                  strikePos: 0.30, woodHardness: 0.65, muting: 0.0, amp: 0.7]);

// Heavy hardwood dowels — deep and resonant
Synth(\stickHit, [freq: 450, freq2: 440, decay: 1.2, strikeForce: 0.8,
                  strikePos: 0.25, woodHardness: 0.75, muting: 0.0, amp: 0.6]);

// Light tap on thin pencil
Synth(\stickHit, [freq: 3200, freq2: 3100, decay: 0.4, strikeForce: 0.25,
                  strikePos: 0.35, woodHardness: 0.5, muting: 0.1, amp: 0.6]);

// Dead muted click (heavy muting, short ring)
Synth(\stickHit, [freq: 440.rrand(900), freq2: 744.rrand(1000), decay: 0.2.rrand(0.4), strikeForce: 0.5, strikePos: 0.5, woodHardness: 0.1.rrand(0.5), muting: 0.06.rrand(0.08), amp: 1.8]);

// Sweep through freq (different stick sizes)
// (5, 3 .. -10).do { |i|
//     var f = 2200 * (2 ** (i / 12));
//     SystemClock.sched((5 - i) * 0.6, {
//         Synth(\stickHit, [freq: f, freq2: f * 0.97, decay: 0.7,
//                            strikeForce: 0.7, woodHardness: 0.8, amp: 0.6]);
//         nil
//     });
// };

// Sweep woodHardness (rubber to ebony)
// (0.0, 0.1 .. 1.0).do { |h, i|
//     SystemClock.sched(i * 0.7, {
//         Synth(\stickHit, [freq: 2200, freq2: 2100, decay: 0.6,
//                            strikeForce: 0.7, woodHardness: h, amp: 0.6]);
//         nil
//     });
// };


// =============================================================================
//  PATTERN NOTES  (implementation deferred)
// =============================================================================
(
    Pbind(
        \instrument,   \stickHit,
        \dur,          Pwhite(0.125, 0.25),
        \amp,          Pwhite(0.5, 0.85),
        \pan,          Pwhite(-0.35, 0.35),

        // Pitch and ring character — vary together (larger stick = lower + longer)
        \freq,         Pwhite(100, 800),
        \freq2,        Pkey(\freq) * Pwhite(0.53, 2.07),  // slight detuning from freq
        \decay,        Pwhite(0.2, 0.9),

        // Strike variation — mimics human performance variability
        \strikeForce,  Pwhite(0.30, 0.95),
        \strikePos,    Pwhite(0.20, 0.40),   // keep in clave playing zone

        // Material/environment — can stay fixed or vary
        \woodHardness, Prand([0.60, 0.75, 0.85, 0.92], inf),
        \muting,       Pwhite(0.1, 0.75),    // subtle hand variation
    ).play;
)
    Natural correlations worth exploiting:
      freq  ↔ decay        (larger/denser sticks ring longer)
      freq  ↔ woodHardness (dense wood gives higher frequency for same size)
      freq  ↔ freq2        (freq2 should be a small ratio deviation from freq)
      strikeForce ↔ amp    (hard hit = louder — can be driven from one param)

    For realistic clave patterns, freq / freq2 should stay nearly constant
    across events (same two sticks) while strikeForce / strikePos vary.
    Reserve freq / woodHardness changes for transitions between sections.
*/
