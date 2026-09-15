// ============================================================================
//
//   ONE MICROPHONE, MANY INSTRUMENTS
//   Triggering and analysis of an acoustic drum in SuperCollider
//
//   Mic on input 0 throughout.
//
// ----------------------------------------------------------------------------
//   HOW TO USE THIS FILE
//
//   Every evaluable unit is wrapped in ( ... ). Put the cursor anywhere
//   inside a block and press Cmd+Enter (Ctrl+Enter on Windows/Linux) to
//   evaluate the whole block. Single lines evaluate on their own.
//
//   Each section follows the same three-step shape:
//
//     (  ...SynthDef + OSCdef...  )      <- evaluate to define
//     ~run.(\name);                      <- evaluate to hear/see it
//     ~stop.(\name);                     <- evaluate to stop it
//
//   ~run replaces any previous instance of the same SynthDef, so you can
//   re-evaluate freely without stacking synths. ~stop.() with no
//   argument stops everything. Cmd+Period is still the panic button.
//
//   Run sections in order the first time. Part 6 depends on Part 4.
//
// ----------------------------------------------------------------------------
//   CONTENTS
//
//   PART 0  SETUP AND FIRST PRINCIPLES
//     0.1  Server and helpers        0.3  Capture and look at a hit
//     0.2  Verify the input          0.4  What is in the signal
//
//   PART 1  THE AMPLITUDE DOMAIN
//     1.1  Envelope following        1.5  Velocity, done properly
//     1.2  The naive threshold       1.6  FluidAmpSlice
//     1.3  Debounce                  1.7  FluidAmpGate (duration)
//     1.4  Adaptive threshold
//
//   PART 2  THE SPECTRAL DOMAIN
//     2.1  What an ODF is            2.5  FluidOnsetSlice
//     2.2  Onsets.kr                 2.6  The metric bank
//     2.3  PV_ detectors             2.7  Unthresholded features
//     2.4  Third-party detectors
//
//   PART 3  OTHER FRAMINGS OF "EVENT"
//     3.1  Segment on change         3.4  Density and rolls
//     3.2  Time-domain transients    3.5  IOI and microtiming
//     3.3  Phrase endings            3.6  Accumulated work
//
//   PART 4  DESCRIPTION
//     4.1  Vanilla descriptors       4.3  Early and late
//     4.2  FluCoMa descriptors       4.4  The full packet
//
//   PART 5  DECOMPOSITION
//     5.1  Multi-band                5.3  Transients and Sines
//     5.2  Transient / body split    5.4  HPSS
//
//   PART 6  LEARNED BEHAVIOUR
//     6.1  NMF gesture bases         6.4  Regression
//     6.2  Building a dataset        6.5  Corpus matching
//     6.3  Classification            6.6  The unfamiliar
//
//   PART 7  RESPONSE
//     7.1  Trigger to sample         7.3  Continuous mapping
//     7.2  Excitation
//
//   PART 8  GOTCHAS
//
// ============================================================================


// ############################################################################
//  PART 0  ::  SETUP AND FIRST PRINCIPLES
// ############################################################################

// ----------------------------------------------------------------------------
// 0.1  Server and helpers        EVALUATE THIS FIRST
//
//      64-sample blocks are ~1.45ms at 44.1k. Everything downstream
//      inherits that resolution, and for percussion timing it is
//      already at the edge of what you can hear.
// ----------------------------------------------------------------------------
(
s.options.numInputBusChannels  = 2;
s.options.numOutputBusChannels = 2;
s.options.blockSize            = 64;
s.options.hardwareBufferSize   = 128;
s.options.memSize              = 2**18;
s.reboot;
)

// run/stop helpers -- evaluate once, after the server has booted
(
~synths = IdentityDictionary.new;

~run = { |name, args|
    ~synths[name] !? (_.free);
    ~synths[name] = Synth(name, args ? []);
    ("RUN    %".format(name)).postln;
    ~synths[name];
};

~stop = { |name|
    if (name.isNil) {
        ~synths.do(_.free);
        ~synths.clear;
        "STOP   all".postln;
    } {
        ~synths[name] !? (_.free);
        ~synths.removeAt(name);
        ("STOP   %".format(name)).postln;
    };
};

~hasFluid = try { FluidOnsetSlice.class; true } { false };
("FluCoMa: " ++ if (~hasFluid)
    { "present" }
    { "NOT INSTALLED -- 1.6+, 2.5+, 3.1, 3.2, 5.3+, 6 unavailable" }).postln;
)


// ----------------------------------------------------------------------------
// 0.2  Verify the input
//
//      Do this before anything else. Almost every "my trigger doesn't
//      work" turns out to be a gain problem.
//
//      Want: hits peaking -12 to -6 dBFS, noise floor below -50. A high
//      noise floor makes every threshold you set later a compromise.
// ----------------------------------------------------------------------------

// Scope the INPUT. Audio input buses start AFTER the output buses, so
// input 0 is bus index numOutputBusChannels -- NOT 0.
s.scope(1, s.options.numOutputBusChannels);
s.meter;

(
SynthDef(\levelcheck, {
    var in = SoundIn.ar(0);
    SendReply.kr(Impulse.kr(4), '/level', [
        Amplitude.kr(in, 0.001, 0.5).ampdb,
        RunningMin.kr(Amplitude.kr(in, 0.2, 0.2), Impulse.kr(0.2)).ampdb
    ]);
}).add;

OSCdef(\level, { |msg|
    ("peak % dB     floor % dB".format(
        msg[3].round(0.1), msg[4].round(0.1))).postln;
}, '/level');
)

~run.(\levelcheck);
~stop.(\levelcheck);


// ----------------------------------------------------------------------------
// 0.3  Capture and look at a single hit
//
//      Before writing a detector, look at what you are detecting.
// ----------------------------------------------------------------------------
(
~oneHit = Buffer.alloc(s, s.sampleRate * 2, 1);

~grab = {
    "hit once, now".postln;
    { RecordBuf.ar(SoundIn.ar(0), ~oneHit, loop: 0, doneAction: 2); Silent.ar }.play;
};
)

~grab.value;
~oneHit.plot;

// zoom in on the first 30ms -- this is what detectors actually see
(
~oneHit.loadToFloatArray(action: { |a|
    defer { a[0 .. (s.sampleRate * 0.03).asInteger].plot("first 30ms") };
});
)


// ----------------------------------------------------------------------------
// 0.4  What is actually in the signal
//
//      A struck drum produces three overlapping things, and nearly
//      every technique here is a way of separating them:
//
//      1. THE STRIKE   a few milliseconds, near-wideband. The sound of
//                      stick meeting head. Carries the implement and
//                      the force.
//
//      2. THE MODES    the head's resonances, inharmonic, decaying over
//                      hundreds of milliseconds. Relative amplitudes
//                      encode WHERE you hit. Decay rate encodes damping.
//
//      3. THE NOISE    snare buzz, shell rattle, air, room bleed.
//
//      A piezo collapses all three into one number. A microphone does
//      not. That is the whole opportunity.
//
//      Three consequences to hold onto:
//
//      * Amplitude detection sees mostly (1) and is FAST.
//      * Spectral detection sees (1) and (2), is slower by at least one
//        FFT window, and can tell gestures apart.
//      * Anything about (2) requires WAITING. Damping cannot be known
//        at the instant of the hit, by anyone, ever. Design around that
//        rather than fighting it -- see 4.3.
// ----------------------------------------------------------------------------


// ############################################################################
//  PART 1  ::  THE AMPLITUDE DOMAIN
//
//  No FFT. Lowest possible latency. Cannot tell a rimshot from a centre
//  hit, but knows SOMETHING happened faster than anything else here.
// ############################################################################

// ----------------------------------------------------------------------------
// 1.1  Envelope following
//
//      Amplitude.kr(in, attackTime, releaseTime) is the foundation,
//      but only releaseTime earns its name. attackTime does NOT slow
//      the rise -- see the trap in 1.4 before you rely on it. These
//      three differ in how they fall, not in how fast they catch a
//      transient.
//
//      A short release also under-reads peaks: it drops into the
//      troughs between waveform peaks faster than the 1.45ms control
//      rate samples it, so it catches the envelope low. Measured on one
//      noise burst, the 0.05 release read roughly twice the 0.008 one.
// ----------------------------------------------------------------------------
(
SynthDef(\env, {
    var in   = SoundIn.ar(0);
    var fast = Amplitude.kr(in, 0.0005, 0.010);    // resets fastest, reads lowest
    var mid  = Amplitude.kr(in, 0.005,  0.100);    // gesture-level
    var slow = Amplitude.kr(in, 0.100,  0.500);    // smoothest, slowest to reset
    SendReply.kr(Impulse.kr(20), '/env', [fast, mid, slow]);
}).add;

OSCdef(\env, { |msg|
    ("fast %    mid %    slow %".format(
        msg[3].round(0.001), msg[4].round(0.001), msg[5].round(0.001))).postln;
}, '/env');
)

~run.(\env);
~stop.(\env);


// ----------------------------------------------------------------------------
// 1.2  The naive threshold, and why it fails
//
//      The simplest trigger that can exist. Run it and watch it
//      misbehave -- understanding HOW it fails motivates everything
//      after it.
//
//      Hit once and count the messages. Usually three to ten.
//
//      Failure modes in the order you meet them:
//        * one hit fires many times (the envelope crosses back and
//          forth across the threshold during decay)
//        * quiet hits missed entirely
//        * a loud passage makes it fire continuously
//        * moving the mic 10cm breaks your threshold
// ----------------------------------------------------------------------------
(
SynthDef(\naive, {
    var in   = SoundIn.ar(0);
    var env  = Amplitude.kr(in, 0.001, 0.05);
    var trig = env > \thresh.kr(0.05);
    SendReply.kr(trig, '/naive', [env]);
}).add;

OSCdef(\naive, { |msg| ("HIT %".format(msg[3].round(0.001))).postln }, '/naive');
)

~run.(\naive);
~run.(\naive, [\thresh, 0.02]);        // lower it and watch it get worse
~stop.(\naive);


// ----------------------------------------------------------------------------
// 1.3  Debounce
//
//      Fixes multiple-firing. Trig1 fires once, then refuses for a
//      fixed time.
//
//      The dead time is a musical decision, not a technicality. 30ms
//      caps you at 33 strokes per second (fine for most playing). 10ms
//      lets buzz rolls through as individual events, and also lets
//      double-triggers through.
// ----------------------------------------------------------------------------
(
SynthDef(\debounced, {
    var in   = SoundIn.ar(0);
    var env  = Amplitude.kr(in, 0.001, 0.03);
    var over = env > \thresh.kr(0.05);
    var trig = Trig1.kr(over, \deadtime.kr(0.030));
    SendReply.kr(trig, '/hit1', [Latch.kr(env, trig)]);
}).add;

OSCdef(\hit1, { |msg| ("hit %".format(msg[3].round(0.001))).postln }, '/hit1');
)

~run.(\debounced);
~run.(\debounced, [\deadtime, 0.010]);     // now try a buzz roll
~stop.(\debounced);


// ----------------------------------------------------------------------------
// 1.4  Adaptive threshold
//
//      Fixes the fixed-threshold problem. Compare a FAST envelope
//      against a SLOW one rather than against a constant. The slow
//      envelope is the local background level, so the detector follows
//      your dynamics automatically.
//
//      This one trick is most of what commercial drum triggers do, and
//      it is the biggest robustness gain in Part 1.
//
//      THE TRAP -- and it is silent. You cannot build the slow envelope
//      by handing Amplitude.kr a long attackTime. attackTime does not
//      slow the rise. Measured on a step, Amplitude.kr(in, 0.300, 0.150)
//      and Amplitude.kr(in, 0.001, 0.05) BOTH reach 95% in one control
//      block. Release is the only one of the two that shapes anything.
//
//      So a slow follower built that way rises WITH the strike, and
//      fast > (slow * ratio) compares a value against a multiple of
//      itself. It cannot fire on an attack. What it can still do is
//      fire on ripples partway down the decay, once slow has released
//      and fast has risen again -- so it triggers on the tail and on
//      rebounds while missing the hit that caused them.
//
//      That failure does not read as a threshold problem, because
//      lowering the floor never helps: the floor was never the binding
//      term. Verified on a drum mic -- the slow follower's window peak
//      tracked at ~74% of the fast one's.
//
//      Lag the rise explicitly instead. LagUD's up-time is real.
//
//      ratio  how much louder than background counts as an event.
//             2-4 expressive, 5+ for noisy stages.
//      floor  absolute minimum, stops it firing on silence.
// ----------------------------------------------------------------------------
(
SynthDef(\adaptive, {
    var in   = SoundIn.ar(0);
    var fast = Amplitude.kr(in, 0.001, 0.05);
    var slow = LagUD.kr(fast, \slowUp.kr(0.300), \slowDown.kr(0.150));
    var over = fast > ((slow * \ratio.kr(3.0)) + \floor.kr(0.004));
    var trig = Trig1.kr(over, \deadtime.kr(0.025));
    SendReply.kr(trig, '/hit2', [Latch.kr(fast, trig), Latch.kr(slow, trig)]);
}).add;

OSCdef(\hit2, { |msg|
    ("hit %    (background %)".format(
        msg[3].round(0.001), msg[4].round(0.001))).postln;
}, '/hit2');
)

// play quietly, then loudly. It should keep up with both.
~run.(\adaptive);

// slowUp 0 removes the rise lag, reproducing the trap described above.
// Hit once: it fires on the decay and the rebound, not on the strike.
~run.(\adaptive, [\slowUp, 0]);

~stop.(\adaptive);


// ----------------------------------------------------------------------------
// 1.5  Velocity, done properly
//
//      Latching the envelope AT the trigger gives a value from partway
//      up the attack, so quiet fast-attack hits read as loud and the
//      dynamic range compresses.
//
//      Correct: trigger, track the PEAK over a short window, then
//      report. Costs 5ms and returns a velocity that corresponds to how
//      hard you actually hit.
//
//      The exponent at the end is a feel curve. 0.6 opens up the quiet
//      end, usually what you want for hands and brushes. Be aware it
//      lifts false triggers disproportionately too -- a rebound at a
//      third the level comes back at half the velocity.
//
//      fullScale is the one number here that is NOT portable. Measure
//      your own loudest hit with 0.2 and set it from that, or every hit
//      clips to 1.0 and the dynamics vanish. The slow follower uses
//      LagUD for the reason given in 1.4.
// ----------------------------------------------------------------------------
(
SynthDef(\velocity, {
    var in     = SoundIn.ar(0);
    var fast   = Amplitude.kr(in, 0.001, 0.05);
    var slow   = LagUD.kr(fast, 0.300, 0.150);
    var over   = fast > ((slow * \ratio.kr(3.0)) + \floor.kr(0.004));
    var trig   = Trig1.kr(over, \deadtime.kr(0.025));

    var peak   = RunningMax.kr(fast, trig);            // resets each trigger
    var report = TDelay.kr(trig, \window.kr(0.005));
    var vel    = Latch.kr(peak, report);

    var norm   = (vel / \fullScale.kr(0.35)).clip(0, 1);
    var curved = norm.pow(\curve.kr(0.6));

    SendReply.kr(report, '/vel', [curved, vel]);
}).add;

OSCdef(\vel, { |msg|
    ("vel %    (raw %)".format(msg[3].round(0.01), msg[4].round(0.001))).postln;
}, '/vel');
)

// play a crescendo. The normalised value should sweep 0 to 1 smoothly.
~run.(\velocity);
~run.(\velocity, [\curve, 1.0]);       // linear, for comparison
~stop.(\velocity);


// ----------------------------------------------------------------------------
// 1.6  [FLUCOMA]  FluidAmpSlice
//
//      1.4 and 1.5 productised and better tuned. Works in dBFS, which
//      is far easier to reason about than linear amplitude, and has a
//      proper two-envelope architecture.
//
//      Times in SAMPLES. onThreshold/offThreshold in dB ABOVE the slow
//      envelope. floor is an absolute dBFS gate.
// ----------------------------------------------------------------------------
(
SynthDef(\ampslice, {
    var in   = SoundIn.ar(0);
    var trig = FluidAmpSlice.ar(in,
        fastRampUp:     10,
        fastRampDown:   200,
        slowRampUp:     2205,        // 50ms at 44.1k
        slowRampDown:   2205,
        onThreshold:    \on.kr(10),
        offThreshold:   \off.kr(5),
        floor:          \floor.kr(-50),
        minSliceLength: 440,         // ~10ms debounce
        highPassFreq:   85);

    // T2K, not A2K -- single-sample impulses. See Part 8.
    SendReply.kr(T2K.kr(trig), '/ampslice');
}).add;

OSCdef(\ampslice, { "hit".postln }, '/ampslice');
)

~run.(\ampslice);
~stop.(\ampslice);


// ----------------------------------------------------------------------------
// 1.7  [FLUCOMA]  FluidAmpGate -- events with duration
//
//      A conceptual jump, not just a better detector.
//
//      Everything above produces an INSTANT. A gate produces a start,
//      an end and a LENGTH. A brush sweep becomes one event instead of
//      forty. A dead stroke and a let-ring become measurably different
//      objects rather than two identical triggers.
//
//      If you take one thing from Part 1: not every musical event is a
//      point in time.
//
//      lookBack/lookAhead buy edge accuracy at the cost of latency.
//      Start at 0/0 for live playing.
// ----------------------------------------------------------------------------
(
SynthDef(\ampgate, {
    var in   = SoundIn.ar(0);
    var gate = FluidAmpGate.ar(in,
        rampUp:           5,
        rampDown:         80,
        onThreshold:      \on.kr(-27),      // dBFS
        offThreshold:     \off.kr(-33),
        minSliceLength:   400,
        minSilenceLength: 400,
        minLengthAbove:   100,
        minLengthBelow:   100,
        lookBack:         0,
        lookAhead:        0,
        highPassFreq:     40,
        maxSize:          16384);

    var prev = Delay1.ar(gate);
    var on   = gate > prev;
    var off  = gate < prev;
    var len  = Sweep.ar(on);                       // seconds since gate opened
    var peak = RunningMax.ar(Amplitude.ar(in, 0.001, 0.01), on);

    SendReply.kr(T2K.kr(on),  '/gateOn');
    SendReply.kr(T2K.kr(off), '/gateOff', [Latch.ar(len, off), Latch.ar(peak, off)]);
}).add;

OSCdef(\gateOn, { "[".post }, '/gateOn');

OSCdef(\gateOff, { |msg|
    (" %s  peak % ]".format(msg[3].round(0.001), msg[4].round(0.001))).postln;
}, '/gateOff');
)

// compare a single stick hit against a long brush sweep
~run.(\ampgate);
~stop.(\ampgate);


// ############################################################################
//  PART 2  ::  THE SPECTRAL DOMAIN
//
//  Costs at least one FFT window (512 samples ~ 12ms at 44.1k). Buys
//  the ability to tell gestures apart, which amplitude detection
//  fundamentally cannot do.
// ############################################################################

// ----------------------------------------------------------------------------
// 2.1  What an onset detection function is
//
//      Take the spectrum now. Take it a moment ago. Measure how
//      different they are. That difference over time is an onset
//      detection function (ODF). Peaks in it are onsets.
//
//      The only real question is what "different" means, and every
//      named detector is a different answer:
//
//        power / energy   did it get louder? (nearly amplitude detection)
//        HFC              did HIGH frequency energy increase? Biased
//                         toward sharp attacks, ignores soft ones.
//        spectral flux    did any bin increase? General purpose.
//        MKL              Kullback-Leibler-ish. Sensitive to soft and
//                         noisy onsets that energy methods miss entirely.
//        phase deviation  did phase advance unpredictably? Catches
//                         onsets with no amplitude change at all.
//        complex domain   magnitude and phase together. Usually the best
//                         single general-purpose choice.
//
//      No metric is best. Different metrics hear different gestures,
//      which is the whole basis of 2.6.
// ----------------------------------------------------------------------------


// ----------------------------------------------------------------------------
// 2.2  [SC]  Onsets.kr
//
//      SuperCollider's built-in. Needs an explicit FFT chain. Good,
//      fast, well-tested. odftype: \power \magsum \complex \rcomplex
//      \phase \wphase \mkl
//
//      mingap and medianspan are in FFT FRAMES, not seconds. With a
//      512-point window at default hop, one frame is ~5.8ms.
// ----------------------------------------------------------------------------
(
SynthDef(\onsets, {
    var in    = SoundIn.ar(0);
    var chain = FFT(LocalBuf(512), in);
    var trig  = Onsets.kr(chain,
        threshold:  \thresh.kr(0.5),
        odftype:    \rcomplex,
        relaxtime:  1,
        floor:      0.1,
        mingap:     5,
        medianspan: 11);
    SendReply.kr(trig, '/onset', [Latch.kr(Amplitude.kr(in, 0.0005, 0.02), trig)]);
}).add;

OSCdef(\onset, { |msg| ("onset %".format(msg[3].round(0.001))).postln }, '/onset');
)

~run.(\onsets);
~run.(\onsets, [\thresh, 0.3]);
~stop.(\onsets);


// ----------------------------------------------------------------------------
// 2.3  [SC]  PV_HainsworthFoote and PV_JensenAndersen
//
//      Two older FFT detectors with a different character. Both output
//      an audio-rate trigger directly.
//
//      HainsworthFoote weights a probabilistic measure (proph) against
//      spectral flux (propf). High proph makes it respond to
//      statistical surprise rather than energy -- on a drum that means
//      it fires on unusual hits more than loud ones.
//
//      JensenAndersen weights four features: centroid, high frequency
//      energy, high frequency content, spectral flux. More reliable on
//      noisy material.
// ----------------------------------------------------------------------------
(
SynthDef(\pvdetect, {
    var in    = SoundIn.ar(0);
    var chain = FFT(LocalBuf(512), in);

    var hf = PV_HainsworthFoote.ar(chain,
        proph: \proph.kr(1.0), propf: \propf.kr(0.0),
        threshold: \hfthresh.kr(0.7), waittime: 0.04);

    var ja = PV_JensenAndersen.ar(chain,
        propsc: 0.25, prophfe: 0.25, prophfc: 0.25, propsf: 0.25,
        threshold: \jathresh.kr(1.0), waittime: 0.04);

    SendReply.kr(T2K.kr(hf), '/pv', [0]);
    SendReply.kr(T2K.kr(ja), '/pv', [1]);
}).add;

OSCdef(\pv, { |msg|
    ([\hainsworthFoote, \jensenAndersen][msg[3].asInteger]).postln;
}, '/pv');
)

~run.(\pvdetect);
~stop.(\pvdetect);


// ----------------------------------------------------------------------------
// 2.4  [SC]  Third-party detectors (sc3-plugins)
//
//      Each has a distinct personality. Argument orders vary between
//      plugin versions -- check the help files rather than trusting
//      anything written here verbatim.
//
//        Coyote          amplitude-tracking, time domain, very low
//                        latency, handles wide dynamics well
//        AttackSlope     reports an onset AND how steep the attack was
//                        -- stick vs mallet vs hand from one object
//        SLOnset         time-domain, statistical. Different failure
//                        modes to spectral methods, so a useful second
//                        opinion
//        OnsetStatistics takes an onset stream, reports density, mean
//                        IOI and variance. Sections 3.4/3.5 done for you
//
//      A genuinely useful pattern: run Coyote (fast, time domain) for
//      TIMING and a spectral detector for IDENTITY. Fire the note on
//      the fast one, decide what it was with the slow one a few
//      milliseconds later.
// ----------------------------------------------------------------------------
(
// uncomment if sc3-plugins is installed, and check the help file first
/*
SynthDef(\coyote, {
    var in   = SoundIn.ar(0);
    var trig = Coyote.kr(in,
        trackFall: 0.2, slowLag: 0.2, fastMul: 0.5,
        thresh: 0.05, minDur: 0.02);
    SendReply.kr(trig, '/coyote');
}).add;

OSCdef(\coyote, { "coyote".postln }, '/coyote');
*/
)

// ~run.(\coyote);
// ~stop.(\coyote);


// ----------------------------------------------------------------------------
// 2.5  [FLUCOMA]  FluidOnsetSlice
//
//      Ten metrics selectable by symbol, no manual FFT chain:
//
//        \power \hfc \flux \mkl \is \cosine \phase \wphase \complex \rcomplex
//
//      minSliceLength and filterSize are in HOPS. Thresholds are
//      metric-dependent and NOT comparable between metrics.
// ----------------------------------------------------------------------------
(
SynthDef(\fluidonset, {
    var in   = SoundIn.ar(0);
    var trig = FluidOnsetSlice.ar(in,
        metric:         \metric.kr(9),      // 9 = rcomplex
        threshold:      \thresh.kr(0.5),
        minSliceLength: 4,
        filterSize:     5,
        windowSize:     512,
        hopSize:        128);
    SendReply.kr(T2K.kr(trig), '/fluidonset');
}).add;

OSCdef(\fluidonset, { "onset".postln }, '/fluidonset');
)

~run.(\fluidonset);
~run.(\fluidonset, [\metric, 3, \thresh, 1.0]);     // 3 = mkl, now try brushes
~stop.(\fluidonset);


// ----------------------------------------------------------------------------
// 2.6  [FLUCOMA]  The metric bank -- gesture fingerprints
//
//      The payoff of 2.1. Run all ten metrics at once. Any gesture
//      fires a characteristic SUBSET. That subset is a gesture
//      identifier available with zero training.
//
//      A stick hit fires nearly everything. A brush stroke fires \mkl
//      and little else. A fingertip may fire only the phase methods.
//      Those patterns are stable and you can switch on them.
//
//      Thresholds must be tuned per metric. The values below are a
//      starting point, not an answer.
// ----------------------------------------------------------------------------
(
~metricNames = [\power, \hfc, \flux, \mkl, \is,
                \cosine, \phase, \wphase, \complex, \rcomplex];

SynthDef(\metricbank, {
    var in     = SoundIn.ar(0);
    var thresh = \thresh.kr([0.7, 0.5, 0.4, 1.0, 0.6,
                             0.3, 0.6, 0.6, 0.5, 0.5]);

    ~metricNames.do { |m, i|
        var trig = FluidOnsetSlice.ar(in, m,
            threshold:      thresh[i],
            minSliceLength: 4,
            filterSize:     5,
            windowSize:     512,
            hopSize:        128);
        SendReply.kr(T2K.kr(trig), '/metric', [i]);
    };
}).add;

~fp     = Set[];
~fpTask = nil;

OSCdef(\metric, { |msg|
    ~fp = ~fp.add(msg[3].asInteger);
    ~fpTask !? (_.stop);
    ~fpTask = Routine({
        0.03.wait;
        ("fingerprint:   " ++
            ~fp.asArray.sort.collect { |i| ~metricNames[i] }.join(" ")).postln;
        ~fp = Set[];
    }).play(AppClock);
}, '/metric');
)

// Play every technique you have. Write down the fingerprints.
~run.(\metricbank);
~stop.(\metricbank);


// ----------------------------------------------------------------------------
// 2.7  [FLUCOMA]  Unthresholded detection functions
//
//      Do not threshold at all. Use the raw ODF as a continuous control
//      signal -- "onsetness" as a modulation source.
//
//      Frequently the most musical use of these algorithms. You get the
//      SHAPE of the gesture rather than a binary decision about it, and
//      no threshold means no missed hits and no false positives.
// ----------------------------------------------------------------------------
(
SynthDef(\odfcontinuous, {
    var in = SoundIn.ar(0);
    var o  = LagUD.kr(FluidOnsetFeature.kr(in, \rcomplex,
                filterSize: 5, windowSize: 512, hopSize: 128), 0.001, 0.15);
    var n  = LagUD.kr(FluidNoveltyFeature.kr(in, \mfcc, kernelSize: 41), 0.01, 0.30);
    SendReply.kr(Impulse.kr(15), '/odf', [o, n]);
}).add;

OSCdef(\odf, { |msg|
    ("onsetness %    novelty %".format(
        msg[3].round(0.01), msg[4].round(0.01))).postln;
}, '/odf');
)

~run.(\odfcontinuous);
~stop.(\odfcontinuous);


// ############################################################################
//  PART 3  ::  OTHER FRAMINGS OF "EVENT"
//
//  Everything so far answers "did something start?". These ask
//  different questions.
// ############################################################################

// ----------------------------------------------------------------------------
// 3.1  [FLUCOMA]  Segment on change, not energy
//
//      FluidNoveltySlice fires when the CHARACTER of the sound shifts,
//      not when a hit occurs. Switch from sticks to brushes mid-phrase
//      and it fires ONCE, at the switch.
//
//      It segments your playing into technique-regions. Nothing in
//      Parts 1 or 2 can do this.
//
//      algorithm: \spectrum \mfcc \chroma \pitch \loudness
//      kernelSize (odd) sets the timescale of "change".
// ----------------------------------------------------------------------------
(
SynthDef(\novelty, {
    var in = SoundIn.ar(0);

    var timbre  = FluidNoveltySlice.ar(in, \mfcc,
        kernelSize: 41, threshold: \tThresh.kr(0.45), minSliceLength: 20);

    var dynamic = FluidNoveltySlice.ar(in, \loudness,
        kernelSize: 61, threshold: \dThresh.kr(0.35), minSliceLength: 30);

    var tuning  = FluidNoveltySlice.ar(in, \pitch,
        kernelSize: 31, threshold: \pThresh.kr(0.50), minSliceLength: 20);

    SendReply.kr(T2K.kr(timbre),  '/nov', [0]);
    SendReply.kr(T2K.kr(dynamic), '/nov', [1]);
    SendReply.kr(T2K.kr(tuning),  '/nov', [2]);
}).add;

OSCdef(\nov, { |msg|
    ("CHANGE: " ++ [\timbre, \dynamic, \tuning][msg[3].asInteger]).postln;
}, '/nov');
)

// play sticks for a while, then switch to brushes. One message at the switch.
~run.(\novelty);
~stop.(\novelty);


// ----------------------------------------------------------------------------
// 3.2  [FLUCOMA]  Time-domain transients
//
//      FluidTransientSlice models the signal as predictable-plus-click
//      and finds the clicks. Not spectral, so much tighter timing.
//      Catches stick-on-stick, rim taps and fingernails that spectral
//      difference smears across frames.
//
//      More false positives than spectral methods. Worth it when timing
//      precision is the priority.
// ----------------------------------------------------------------------------
(
SynthDef(\transientslice, {
    var in   = SoundIn.ar(0);
    var trig = FluidTransientSlice.ar(in,
        order:          40,
        blockSize:      512,
        padSize:        256,
        skew:           \skew.kr(0),        // -10..10 sensitivity bias
        threshFwd:      \fwd.kr(3.0),
        threshBack:     \back.kr(1.1),
        windowSize:     15,
        clumpLength:    30,
        minSliceLength: 1000);
    SendReply.kr(T2K.kr(trig), '/click');
}).add;

OSCdef(\click, { "click".postln }, '/click');
)

~run.(\transientslice);
~run.(\transientslice, [\fwd, 2.0]);       // more sensitive
~stop.(\transientslice);


// ----------------------------------------------------------------------------
// 3.3  Phrase endings
//
//      Trigger on the moment you STOP. Most systems only listen for
//      attacks; listening for departures is underused and reads very
//      musically -- a reverb that blooms when you stop rather than when
//      you start.
// ----------------------------------------------------------------------------
(
SynthDef(\phrase, {
    var in    = SoundIn.ar(0);
    var quiet = DetectSilence.ar(in, \floor.kr(0.008), \hold.kr(0.45));
    SendReply.kr(T2K.kr(Trig1.ar(quiet, 1.0)), '/phraseEnd');
}).add;

OSCdef(\phraseEnd, { "-- phrase ended --".postln }, '/phraseEnd');
)

~run.(\phrase);
~stop.(\phrase);


// ----------------------------------------------------------------------------
// 3.4  Density and rolls
//
//      Leaky-integrate the onset stream. Below a threshold you are
//      playing notes; above it you are playing a texture.
//
//      Let the system cross into a sustained or granular behaviour when
//      you buzz-roll and back out when you return to single strokes.
//      The instrument changes what KIND of instrument it is based on how
//      you play it -- one of the most rewarding mappings here.
// ----------------------------------------------------------------------------
(
SynthDef(\density, {
    var in   = SoundIn.ar(0);
    var trig = T2K.kr(FluidOnsetSlice.ar(in, \rcomplex, 0.5, minSliceLength: 2));
    var dens = Integrator.kr(trig, 0.985).clip(0, 20);
    var mode = dens > \switchAt.kr(4.0);
    SendReply.kr(Impulse.kr(10), '/dens', [dens]);
    SendReply.kr(Changed.kr(mode), '/mode', [mode]);
}).add;

OSCdef(\dens, { |msg| ("density %".format(msg[3].round(0.1))).postln }, '/dens');

OSCdef(\mode, { |msg|
    if (msg[3] > 0) { "==> TEXTURE".postln } { "==> NOTES".postln };
}, '/mode');
)

~run.(\density);
~stop.(\density);


// ----------------------------------------------------------------------------
// 3.5  Inter-onset interval and microtiming
//
//      The rhythm's derivative. Compare each interval to a running
//      median: positive deviation is dragging, negative is rushing.
//
//      Map your push and pull to something rather than quantising it
//      away. Feed it to the swing of a generated layer and the machine
//      follows your feel instead of the other way round.
// ----------------------------------------------------------------------------
(
SynthDef(\timing, {
    var in   = SoundIn.ar(0);
    var trig = T2K.kr(FluidOnsetSlice.ar(in, \rcomplex, 0.5, minSliceLength: 4));
    var ioi  = Timer.kr(trig);
    var med  = Median.kr(5, Latch.kr(ioi, trig));
    SendReply.kr(trig, '/timing', [ioi, med, ioi - med]);
}).add;

OSCdef(\timing, { |msg|
    ("ioi %s    dev %ms    (%)".format(
        msg[3].round(0.001),
        (msg[5] * 1000).round(0.1),
        if (msg[5] > 0) { "drag" } { "rush" })).postln;
}, '/timing');
)

// play a steady pulse, then deliberately rush and drag
~run.(\timing);
~stop.(\timing);


// ----------------------------------------------------------------------------
// 3.6  Accumulated work
//
//      Integrate squared amplitude with a very slow leak. Total
//      mechanical energy put into the drum across the performance -- a
//      long-form arc genuinely tied to physical effort.
//
//      For structural change over minutes, not notes.
// ----------------------------------------------------------------------------
(
SynthDef(\work, {
    var in  = SoundIn.ar(0);
    var acc = Integrator.kr(Amplitude.kr(in, 0.001, 0.05).squared * 0.01, 0.99995);
    SendReply.kr(Impulse.kr(2), '/work', [acc]);
}).add;

OSCdef(\work, { |msg| ("work %".format(msg[3].round(0.01))).postln }, '/work');
)

~run.(\work);
~stop.(\work);


// ############################################################################
//  PART 4  ::  DESCRIPTION -- what KIND of energy
// ############################################################################

// ----------------------------------------------------------------------------
// 4.1  [SC]  Vanilla descriptors
//
//      No FluCoMa required. Cruder than 4.2, but free when you already
//      have an FFT chain.
//
//        SpecCentroid  brightness -- stick vs mallet, edge vs centre
//        SpecFlatness  noisiness -- brush and scrape vs tonal hit
//        SpecPcile     where the energy sits
//        Pitch         the dominant mode
//        Slope         on the envelope, gives attack steepness
// ----------------------------------------------------------------------------
(
SynthDef(\descSC, {
    var in    = SoundIn.ar(0);
    var chain = FFT(LocalBuf(1024), in);
    var trig  = Onsets.kr(chain, 0.4, \rcomplex, mingap: 5);
    var at    = TDelay.kr(trig, 0.015);

    SendReply.kr(at, '/descSC', [
        Latch.kr(SpecCentroid.kr(chain), at),
        Latch.kr(SpecFlatness.kr(chain), at),
        Latch.kr(SpecPcile.kr(chain, 0.90), at),
        Latch.kr(Pitch.kr(in, minFreq: 40, maxFreq: 2000)[0], at),
        Latch.kr(Slope.kr(Amplitude.kr(in, 0.0002, 0.002)) * 0.001,
                 TDelay.kr(trig, 0.003))
    ]);
}).add;

OSCdef(\descSC, { |msg|
    ("centroid %   flatness %   p90 %   f0 %   slope %".format(
        msg[3].round(1), msg[4].round(0.001), msg[5].round(1),
        msg[6].round(0.1), msg[7].round(0.01))).postln;
}, '/descSC');
)

~run.(\descSC);
~stop.(\descSC);


// ----------------------------------------------------------------------------
// 4.2  [FLUCOMA]  The descriptor set
//
//      Better conditioned, perceptually motivated, directly usable as
//      input to the machine learning in Part 6.
//
//      TWO THINGS THAT WILL TRIP YOU UP:
//
//      (a) These return features in their OWN FIXED ORDER, regardless
//          of the order you pass to `select`:
//            FluidSpectralShape  centroid spread skewness kurtosis
//                                rolloff flatness crest
//            FluidLoudness       loudness peak
//            FluidPitch          pitch confidence
//
//      (b) A MultiOutUGen asked for exactly ONE channel returns a BARE
//          UGEN, not an array -- so indexing it with [0] fails. Calling
//          .asArray normalises both cases, which is why it appears on
//          every descriptor call below. Get into the habit.
// ----------------------------------------------------------------------------
(
SynthDef(\descFluid, {
    var in    = SoundIn.ar(0);
    var trig  = T2K.kr(FluidOnsetSlice.ar(in, \rcomplex, \thresh.kr(0.5),
                    minSliceLength: 4, windowSize: 512, hopSize: 128));
    var at    = TDelay.kr(trig, 0.012);

    var loud  = FluidLoudness.kr(in, [\loudness, \peak],
                    windowSize: 256, hopSize: 64).asArray;
    var shape = FluidSpectralShape.kr(in,
                    [\centroid, \spread, \rolloff, \flatness, \crest],
                    unit: 1).asArray;                     // 1 = MIDI units
    var pitch = FluidPitch.kr(in, [\pitch, \confidence],
                    algorithm: 2, minFreq: 40, maxFreq: 4000, unit: 1).asArray;
    var mfcc  = FluidMFCC.kr(in, numCoeffs: 13, numBands: 40,
                    startCoeff: 1).asArray;

    SendReply.kr(at, '/descF',
        (loud ++ shape ++ pitch).collect { |x| Latch.kr(x, at) });
    SendReply.kr(at, '/descMFCC',
        mfcc.collect { |x| Latch.kr(x, at) });
}).add;

OSCdef(\descF, { |msg|
    ("loud %   centroid %   flat %   crest %   pitch %   conf %".format(
        msg[3].round(0.1),  msg[5].round(0.1), msg[8].round(0.001),
        msg[9].round(0.1), msg[10].round(0.1), msg[11].round(0.01))).postln;
}, '/descF');
)

~run.(\descFluid);
~stop.(\descFluid);


// ----------------------------------------------------------------------------
// 4.3  Early and late descriptors
//
//      A real constraint worth designing around rather than fighting.
//
//      EARLY (within ~15ms): loudness, centroid, flatness, attack
//      slope, MFCC. Everything about the STRIKE. These can gate or
//      select the voice you trigger.
//
//      LATE (needs 100ms+): damping, decay shape, whether the note was
//      choked, how long the gesture lasted. Everything about the MODES.
//      These cannot possibly influence WHICH note fires -- but they can
//      MODULATE a voice that is already sounding.
//
//      So: trigger on early descriptors, modulate on late ones. Hit the
//      drum and choke it with your hand -- the synth voice follows.
// ----------------------------------------------------------------------------
(
~dampBus = Bus.control(s, 1);

SynthDef(\earlylate, {
    var in    = SoundIn.ar(0);
    var trig  = T2K.kr(FluidOnsetSlice.ar(in, \rcomplex, 0.5, minSliceLength: 8));
    var env   = Amplitude.kr(in, 0.001, 0.01);

    var peak  = Latch.kr(env, TDelay.kr(trig, 0.006));
    var mid   = Latch.kr(env, TDelay.kr(trig, 0.060));
    var tail  = Latch.kr(env, TDelay.kr(trig, 0.130));

    var decay = (tail / (peak + 1e-6)).clip(0, 1);   // 0 dead, 1 ringing
    var curve = (mid  / (peak + 1e-6)).clip(0, 1);

    SendReply.kr(TDelay.kr(trig, 0.012), '/early', [peak]);
    SendReply.kr(TDelay.kr(trig, 0.135), '/late',  [decay, curve]);

    Out.kr(\dampBus.kr(0), Lag.kr(decay, 0.05));
}).add;

OSCdef(\early, { "  hit".post }, '/early');

OSCdef(\late, { |msg|
    ("    damping %   (0 muted, 1 open)".format(msg[3].round(0.01))).postln;
}, '/late');
)

// hit open, then hit and immediately choke with your hand. Compare.
~run.(\earlylate, [\dampBus, ~dampBus.index]);
~stop.(\earlylate);


// ----------------------------------------------------------------------------
// 4.4  The full packet
//
//      Everything in 4.2 and 4.3 in one place. Three OSC messages per
//      hit: core descriptors, MFCC timbre vector, late damping.
//
//      This is the SynthDef Part 6 builds on. The core packet is NINE
//      values -- that number matters in 6.2.
// ----------------------------------------------------------------------------
(
SynthDef(\analyse, {
    var in    = SoundIn.ar(0);
    var trig  = T2K.kr(FluidOnsetSlice.ar(in, \rcomplex, \thresh.kr(0.5),
                    minSliceLength: 4, windowSize: 512, hopSize: 128));
    var at    = TDelay.kr(trig, 0.012);
    var late  = TDelay.kr(trig, 0.130);

    var loud  = FluidLoudness.kr(in, [\loudness, \peak],
                    windowSize: 256, hopSize: 64).asArray;
    var shape = FluidSpectralShape.kr(in,
                    [\centroid, \spread, \rolloff, \flatness, \crest],
                    unit: 1).asArray;
    var pitch = FluidPitch.kr(in, [\pitch, \confidence], unit: 1).asArray;
    var mfcc  = FluidMFCC.kr(in, numCoeffs: 13, numBands: 40,
                    startCoeff: 1).asArray;

    var env   = Amplitude.kr(in, 0.0005, 0.02);
    var peakE = Latch.kr(env, TDelay.kr(trig, 0.005));
    var tailE = Latch.kr(env, late);

    SendReply.kr(at, '/hit',
        (loud ++ shape ++ pitch).collect { |x| Latch.kr(x, at) });    // 9 values
    SendReply.kr(at, '/hitMFCC',
        mfcc.collect { |x| Latch.kr(x, at) });
    SendReply.kr(late, '/hitLate',
        [(tailE / (peakE + 1e-6)).clip(0, 1)]);
}).add;

OSCdef(\hit, { |msg|
    ~last = (
        loudness:  msg[3],  peak:      msg[4],
        centroid:  msg[5],  spread:    msg[6],  rolloff: msg[7],
        flatness:  msg[8],  crest:     msg[9],
        pitch:     msg[10], pitchConf: msg[11]
    );
    ~last.postln;
}, '/hit');
)

~run.(\analyse);
~stop.(\analyse);


// ############################################################################
//  PART 5  ::  DECOMPOSITION -- many signals from one mic
//
//  The most direct answer to "how do I get more than one input". Do not
//  add microphones; split the one you have.
// ############################################################################

// Allocate private buses properly rather than hardcoding indices, and
// define a monitor so you can actually HEAR each separated stream.
(
~clickBus   = Bus.audio(s, 1);
~partialBus = Bus.audio(s, 1);
~noiseBus   = Bus.audio(s, 1);
~harmBus    = Bus.audio(s, 1);
~percBus    = Bus.audio(s, 1);
~resBus     = Bus.audio(s, 1);

SynthDef(\monitor, {
    Out.ar(0, In.ar(\bus.kr(0), 1).dup * \amp.kr(0.7));
}).add;
)


// ----------------------------------------------------------------------------
// 5.1  [SC]  Multi-band detection
//
//      Crude, effective, dependency-free. Four bands, four independent
//      onset detectors. A rimshot lights the top two, a centre hit the
//      bottom two, a stick click only band 2.
//
//      Combinations of bands behave like chords from a mono source.
// ----------------------------------------------------------------------------
(
SynthDef(\bandtrig, {
    var in = SoundIn.ar(0);
    var bands = [
        LPF.ar(in, 160),             // 0  shell thump
        BPF.ar(in, 450, 1.4),        // 1  body
        BPF.ar(in, 2200, 1.2),       // 2  attack / stick
        HPF.ar(in, 6000)             // 3  air / buzz
    ];
    bands.do { |b, i|
        var chain = FFT(LocalBuf(512), b);
        var trig  = Onsets.kr(chain, \thresh.kr(0.4), \rcomplex, mingap: 5);
        SendReply.kr(trig, '/band',
            [i, Latch.kr(Amplitude.kr(b, 0.001, 0.05), trig)]);
    };
}).add;

OSCdef(\band, { |msg|
    ("band %   (%)".format(msg[3].asInteger, msg[4].round(0.001))).postln;
}, '/band');
)

~run.(\bandtrig);
~stop.(\bandtrig);


// ----------------------------------------------------------------------------
// 5.2  [SC]  Transient / body split
//
//      Gate the first 12ms into one stream and everything after into
//      another. Stream A is the stick -- sharp, wideband, good for
//      triggering. Stream B is the ringing membrane -- pitched,
//      sustained, good as an EXCITER (see 7.2).
//
//      A poor man's 5.3, but it costs nothing and the idea is the same.
//
//      Headphones -- the monitor puts the mic back out through the
//      speakers.
// ----------------------------------------------------------------------------
(
SynthDef(\split, {
    var in   = SoundIn.ar(0);
    var trig = Onsets.kr(FFT(LocalBuf(512), in), 0.4, \rcomplex, mingap: 5);
    var gate = EnvGen.ar(Env([1, 1, 0], [0.012, 0.002]), trig);

    Out.ar(\strikeBus.kr(0), in * gate);
    Out.ar(\bodyBus.kr(0),   in * (1 - gate));
}).add;
)

(
~run.(\split, [\strikeBus, ~clickBus.index, \bodyBus, ~partialBus.index]);
~synths[\monA] = Synth.after(~synths[\split], \monitor, [\bus, ~clickBus.index]);
)

// swap the monitor to hear the other stream
~synths[\monA].set(\bus, ~partialBus.index);
~synths[\monA].set(\bus, ~clickBus.index);

(
~stop.(\split);
~stop.(\monA);
)


// ----------------------------------------------------------------------------
// 5.3  [FLUCOMA]  FluidTransients and FluidSines
//
//      Proper decomposition rather than gating.
//
//        FluidTransients -> [clicks, body]
//        FluidSines      -> [tracked partials, residual noise]
//
//      Chain them for three genuinely independent signals: the strike,
//      the modal ring, and the breath of the drum.
//
//      Detecting onsets on the PARTIALS stream is quietly useful -- it
//      catches sympathetic resonance and bleed from other drums as
//      events distinct from your own strikes.
// ----------------------------------------------------------------------------
(
SynthDef(\splitstreams, {
    var in = SoundIn.ar(0);
    var clicks, body, partials, noise;

    # clicks, body = FluidTransients.ar(in,
        order: 40, blockSize: 512, padSize: 256,
        skew: 0, threshFwd: 3.0, threshBack: 1.1);

    # partials, noise = FluidSines.ar(body,
        bandwidth: 76, detectionThreshold: -96,
        birthLowThreshold: -24, birthHighThreshold: -60,
        minTrackLen: 15);

    Out.ar(\clickBus.kr(0),   clicks);      // the strike alone
    Out.ar(\partialBus.kr(0), partials);    // the modal ring alone
    Out.ar(\noiseBus.kr(0),   noise);       // the noise floor alone

    SendReply.kr(
        T2K.kr(FluidOnsetSlice.ar(partials, \mkl, 1.2, minSliceLength: 20)),
        '/ringEvent');
}).add;

OSCdef(\ringEvent, { "ring event (not a strike)".postln }, '/ringEvent');
)

(
~run.(\splitstreams, [
    \clickBus,   ~clickBus.index,
    \partialBus, ~partialBus.index,
    \noiseBus,   ~noiseBus.index
]);
~synths[\monB] = Synth.after(~synths[\splitstreams], \monitor,
    [\bus, ~clickBus.index]);
)

~synths[\monB].set(\bus, ~partialBus.index);
~synths[\monB].set(\bus, ~noiseBus.index);

(
~stop.(\splitstreams);
~stop.(\monB);
)


// ----------------------------------------------------------------------------
// 5.4  [FLUCOMA]  FluidHPSS
//
//      Three streams from one object:
//        harmonic    the sustained modal ring
//        percussive  the strike, stick noise, snare buzz
//        residual    whatever the masks reject
//
//      Give each its own detector. A hit that fires the percussive
//      detector but not the harmonic one is a dead stroke; one that
//      fires both is an open hit. That distinction becomes a fact about
//      the signal rather than a guess.
//
//      maskingMode: 0 soft, 1 binary, 2 double-threshold.
// ----------------------------------------------------------------------------
(
SynthDef(\hpss, {
    var in = SoundIn.ar(0);
    var h, p, r;
    # h, p, r = FluidHPSS.ar(in,
        harmFilterSize: 17, percFilterSize: 31,
        maskingMode: 1, windowSize: 1024);

    [h, p, r].do { |sig, i|
        var t = FluidOnsetSlice.ar(sig, \rcomplex, 0.5, minSliceLength: 4);
        SendReply.kr(T2K.kr(t), '/hpss',
            [i, Latch.ar(Amplitude.ar(sig, 0.001, 0.05), t)]);
    };

    Out.ar(\harmBus.kr(0), h);
    Out.ar(\percBus.kr(0), p);
    Out.ar(\resBus.kr(0),  r);
}).add;

OSCdef(\hpss, { |msg|
    ("%   %".format([\harmonic, \percussive, \residual][msg[3].asInteger],
        msg[4].round(0.001))).postln;
}, '/hpss');
)

(
~run.(\hpss, [
    \harmBus, ~harmBus.index,
    \percBus, ~percBus.index,
    \resBus,  ~resBus.index
]);
~synths[\monC] = Synth.after(~synths[\hpss], \monitor, [\bus, ~harmBus.index]);
)

~synths[\monC].set(\bus, ~percBus.index);
~synths[\monC].set(\bus, ~resBus.index);

(
~stop.(\hpss);
~stop.(\monC);
)


// ############################################################################
//  PART 6  ::  LEARNED BEHAVIOUR
//
//  Everything above was hand-designed. These sections let the drum, the
//  mic and the room teach the system what your gestures are.
//
//  All of Part 6 is specific to one mic position on one drum. Move the
//  mic, retrain.
// ############################################################################

// ----------------------------------------------------------------------------
// 6.1  NMF gesture bases
//
//      Record a few seconds of ONE gesture at a time. Learn a single NMF
//      basis from each. Stack them. FluidNMFMatch then reports,
//      continuously, how much of each learned gesture is present.
//
//      Not a classification -- a BLEND. "0.7 centre, 0.3 rimshot" is a
//      meaningful and playable state, and it interpolates smoothly as
//      you move around the head.
//
//      The highest-value section in this file.
// ----------------------------------------------------------------------------
(
~fftSize  = 1024;
~nBins    = (~fftSize / 2 + 1).asInteger;
~gestures = [\centre, \edge, \rimshot, \brush];

~recBufs  = ~gestures.collect { Buffer.alloc(s, s.sampleRate * 5, 1) };
~oneBasis = ~gestures.collect { Buffer.alloc(s, ~nBins, 1) };
~bases    = Buffer.alloc(s, ~nBins, ~gestures.size);

~recGesture = { |i|
    ("RECORDING % -- play ONLY this gesture for 5 seconds".format(~gestures[i])).postln;
    { RecordBuf.ar(SoundIn.ar(0), ~recBufs[i], loop: 0, doneAction: 2); Silent.ar }.play;
    Routine({ 5.2.wait; ("  captured %".format(~gestures[i])).postln }).play(AppClock);
};
)

// evaluate one line at a time, playing that gesture for five seconds
~recGesture.(0);    // centre
~recGesture.(1);    // edge
~recGesture.(2);    // rimshot
~recGesture.(3);    // brush

// learn one basis per gesture, compose them into one buffer
(
~learnBases = {
    var step;
    step = { |i|
        if (i < ~gestures.size) {
            FluidBufNMF.process(s, ~recBufs[i],
                bases: ~oneBasis[i], components: 1, iterations: 200,
                windowSize: ~fftSize, fftSize: ~fftSize,
                action: {
                    FluidBufCompose.process(s, ~oneBasis[i],
                        destination: ~bases, destStartChan: i, destGain: 1,
                        action: {
                            ("basis %   (%)".format(i, ~gestures[i])).postln;
                            step.(i + 1);
                        });
                });
        } {
            "-- all bases learned --".postln;
        };
    };
    step.(0);
};
~learnBases.value;
)

// realtime matching
(
SynthDef(\nmfmatch, {
    var in   = SoundIn.ar(0);
    var act  = FluidNMFMatch.kr(in, \bases.kr(0),
                   maxComponents: 4, iterations: 20,
                   windowSize: 1024, fftSize: 1024).asArray;
    var sm   = act.collect { |a| LagUD.kr(a, 0.002, 0.10) };
    var trig = T2K.kr(FluidOnsetSlice.ar(in, \rcomplex, 0.5, minSliceLength: 4));

    SendReply.kr(TDelay.kr(trig, 0.022), '/nmfHit',
        sm.collect { |a| Latch.kr(a, TDelay.kr(trig, 0.020)) });
}).add;

OSCdef(\nmfHit, { |msg|
    var v   = msg[3 .. (2 + ~gestures.size)];
    var sum = v.sum + 1e-9;
    ("blend:   " ++ ~gestures.collect { |g, i|
        "%:%".format(g, (v[i] / sum).round(0.01)) }.join("   ")).postln;
}, '/nmfHit');
)

~run.(\nmfmatch, [\bases, ~bases]);
~stop.(\nmfmatch);


// ----------------------------------------------------------------------------
// 6.2  Building a dataset
//
//      Descriptors go into a FluidDataSet at each onset, tagged with an
//      id. You attach labels or targets in sclang as you play.
//
//      This dataset feeds 6.3, 6.4 and 6.6.
//
//      NOTE: FluCoMa object ids are plain integers, so datasets and
//      models CAN be passed through NamedControls (as ~ds.id below).
//      Referencing the objects directly is simpler but resolves at
//      SynthDef build time -- which is why the prediction SynthDefs in
//      6.3 and 6.4 must be built AFTER fitting.
// ----------------------------------------------------------------------------
(
~nFeat    = 9;                          // matches the \hit core packet in 4.4
~ds       = FluidDataSet(s);
~dsNorm   = FluidDataSet(s);
~labelSet = FluidLabelSet(s);
~featBuf  = Buffer.alloc(s, ~nFeat);
~outBuf   = Buffer.alloc(s, 1);
~norm     = FluidNormalize(s);
~classes  = [\centre, \edge, \rimshot, \brush];
~currentLabel = 0;
~hitCount = 0;

SynthDef(\capture, {
    var in    = SoundIn.ar(0);
    var trig  = T2K.kr(FluidOnsetSlice.ar(in, \rcomplex, \thresh.kr(0.5),
                    minSliceLength: 4, windowSize: 512, hopSize: 128));
    var at    = TDelay.kr(trig, 0.012);
    var count = PulseCount.kr(at);

    var loud  = FluidLoudness.kr(in, [\loudness, \peak],
                    windowSize: 256, hopSize: 64).asArray;
    var shape = FluidSpectralShape.kr(in,
                    [\centroid, \spread, \rolloff, \flatness, \crest],
                    unit: 1).asArray;
    var pitch = FluidPitch.kr(in, [\pitch, \confidence], unit: 1).asArray;
    var feat  = (loud ++ shape ++ pitch).collect { |x| Latch.kr(x, at) };

    FluidKrToBuf.kr(feat, \featBuf.kr(0));
    FluidDataSetWr.kr(\ds.kr(0), idPrefix: "hit-", idNumber: count,
        buf: \featBuf.kr(0), trig: TDelay.kr(at, 0.001));

    SendReply.kr(TDelay.kr(at, 0.002), '/captured', [count]);
}).add;
)


// ----------------------------------------------------------------------------
// 6.3  Classification
//
//      Label hits as you play, train a KNN, and it names every hit in
//      realtime.
//
//      IMPORTANT: for realtime prediction the labels must be NUMERIC
//      STRINGS ("0", "1", "2") -- the kr output buffer carries a number,
//      not a symbol.
//
//      Aim for 30+ examples per class, played the way you actually play,
//      including the sloppy ones.
// ----------------------------------------------------------------------------
(
~knn = FluidKNNClassifier(s);

OSCdef(\captured, { |msg|
    var id = "hit-" ++ msg[3].asInteger;
    ~labelSet.addLabel(id, ~currentLabel.asString);
    ~hitCount = ~hitCount + 1;
    ("%   ->   %   (% total)".format(id, ~classes[~currentLabel], ~hitCount)).postln;
}, '/captured');
)

~run.(\capture, [\ds, ~ds.id, \featBuf, ~featBuf]);

// set the label, then play ~30 of that gesture
~currentLabel = 0;      // centre
~currentLabel = 1;      // edge
~currentLabel = 2;      // rimshot
~currentLabel = 3;      // brush

~stop.(\capture);

// normalise, then fit
(
~norm.fitTransform(~ds, ~dsNorm, action: {
    ~knn.fit(~dsNorm, ~labelSet, action: { "-- classifier trained --".postln });
});
)

// Build this AFTER training -- the model objects resolve at build time.
(
SynthDef(\classify, {
    var in    = SoundIn.ar(0);
    var trig  = T2K.kr(FluidOnsetSlice.ar(in, \rcomplex, \thresh.kr(0.5),
                    minSliceLength: 4, windowSize: 512, hopSize: 128));
    var at    = TDelay.kr(trig, 0.012);

    var loud  = FluidLoudness.kr(in, [\loudness, \peak],
                    windowSize: 256, hopSize: 64).asArray;
    var shape = FluidSpectralShape.kr(in,
                    [\centroid, \spread, \rolloff, \flatness, \crest],
                    unit: 1).asArray;
    var pitch = FluidPitch.kr(in, [\pitch, \confidence], unit: 1).asArray;
    var feat  = (loud ++ shape ++ pitch).collect { |x| Latch.kr(x, at) };

    FluidKrToBuf.kr(feat, ~featBuf);
    ~norm.kr(TDelay.kr(at, 0.001), ~featBuf, ~featBuf);
    ~knn.kr(TDelay.kr(at, 0.002), ~featBuf, ~outBuf);

    SendReply.kr(TDelay.kr(at, 0.004), '/class',
        [FluidBufToKr.kr(~outBuf, numFrames: 1)]);
}).add;

OSCdef(\class, { |msg|
    ("->   " ++ ~classes[msg[3].asInteger.clip(0, ~classes.size - 1)]).postln;
}, '/class');
)

~run.(\classify);
~stop.(\classify);


// ----------------------------------------------------------------------------
// 6.4  Regression -- continuous control by demonstration
//
//      Classification gives categories. Regression gives a continuous,
//      playable parameter.
//
//      You DEMONSTRATE: hit at the centre and declare 0.0, hit at the
//      edge and declare 1.0, hit halfway and declare 0.5. The network
//      learns descriptors -> position and interpolates to positions you
//      never demonstrated.
//
//      This is the honest way to get strike position from one mic.
//      Deriving it by hand from modal amplitude ratios is possible but
//      brittle -- it needs recalibrating for every drum, tuning and mic
//      placement, and twenty minutes of demonstration beats it.
//
//      The target need not be position. Demonstrate anything you can
//      produce consistently: stick angle, pressure, distance from rim,
//      how choked the note is.
// ----------------------------------------------------------------------------
(
~posDS  = FluidDataSet(s);
~posBuf = Buffer.alloc(s, 1);
~mlp    = FluidMLPRegressor(s,
    hiddenLayers:     [8, 8],
    activation:       2,        // relu
    outputActivation: 1,        // sigmoid -> bounded 0..1
    maxIter:          2000,
    learnRate:        0.05,
    momentum:         0.9,
    batchSize:        16,
    validation:       0.2);
~declaredPos = 0.0;

// REPLACES the labelling OSCdef from 6.3. Run 6.3 or 6.4, not both.
OSCdef(\captured, { |msg|
    var id = "hit-" ++ msg[3].asInteger;
    ~posBuf.set(0, ~declaredPos);
    ~posDS.addPoint(id, ~posBuf);
    ("%   @   %".format(id, ~declaredPos)).postln;
}, '/captured');
)

~run.(\capture, [\ds, ~ds.id, \featBuf, ~featBuf]);

~declaredPos = 0.0;     // dead centre, ~20 hits
~declaredPos = 0.33;
~declaredPos = 0.66;
~declaredPos = 1.0;     // right at the edge

~stop.(\capture);

(
~norm.fitTransform(~ds, ~dsNorm, action: {
    ~mlp.fit(~dsNorm, ~posDS, action: { |loss|
        ("-- MLP trained, loss % --".format(loss)).postln;
    });
});
)

// build AFTER training
(
SynthDef(\predictPos, {
    var in    = SoundIn.ar(0);
    var trig  = T2K.kr(FluidOnsetSlice.ar(in, \rcomplex, \thresh.kr(0.5),
                    minSliceLength: 4, windowSize: 512, hopSize: 128));
    var at    = TDelay.kr(trig, 0.012);

    var loud  = FluidLoudness.kr(in, [\loudness, \peak],
                    windowSize: 256, hopSize: 64).asArray;
    var shape = FluidSpectralShape.kr(in,
                    [\centroid, \spread, \rolloff, \flatness, \crest],
                    unit: 1).asArray;
    var pitch = FluidPitch.kr(in, [\pitch, \confidence], unit: 1).asArray;
    var feat  = (loud ++ shape ++ pitch).collect { |x| Latch.kr(x, at) };

    FluidKrToBuf.kr(feat, ~featBuf);
    ~norm.kr(TDelay.kr(at, 0.001), ~featBuf, ~featBuf);
    ~mlp.kr(TDelay.kr(at, 0.002), ~featBuf, ~posBuf);

    SendReply.kr(TDelay.kr(at, 0.004), '/pos',
        [FluidBufToKr.kr(~posBuf, numFrames: 1)]);
}).add;

OSCdef(\pos, { |msg| ("position   %".format(msg[3].round(0.01))).postln }, '/pos');
)

// now play anywhere on the head, including places you never demonstrated
~run.(\predictPos);
~stop.(\predictPos);


// ----------------------------------------------------------------------------
// 6.5  Corpus matching
//
//      Analyse a folder of samples into MFCC means. Fit a KD-tree. At
//      every hit, query the tree with the hit's own MFCC mean and play
//      the nearest sample.
//
//      The drum stops being a trigger and becomes a way of NAVIGATING a
//      sound library by timbre. A brighter hit finds a brighter sample.
//
//      The trick for realtime: a KD-tree cannot return a string id
//      through a buffer, so build a second dataset mapping each slice id
//      to its [startFrame, numFrames] and pass it as lookupDataSet. The
//      query then returns numbers you can play directly.
// ----------------------------------------------------------------------------
(
~corpusPath = "~/Music/corpus".standardizePath;     // <-- YOUR FOLDER

~corpusDS  = FluidDataSet(s);      // id -> 13 MFCC means
~lookupDS  = FluidDataSet(s);      // id -> [startFrame, numFrames]
~tree      = FluidKDTree(s, numNeighbours: 1);

~mfccBufs  = 4.collect { Buffer.new };
~statsBufs = 4.collect { Buffer.new };
~flatBufs  = 4.collect { Buffer.new };
~lookupBuf = Buffer.alloc(s, 2);
~queryOut  = Buffer.alloc(s, 2);
~mfccQuery = Buffer.alloc(s, 13);
)

// 1. load and slice the corpus
(
~loader = FluidLoadFolder(~corpusPath);
~loader.play(s, action: {
    "corpus loaded".postln;
    ~slicer = FluidSliceCorpus({ |src, start, num, dest|
        FluidBufOnsetSlice.kr(src, start, num, indices: dest,
            metric: 9, threshold: 0.5, blocking: 1);
    });
    ~slicer.play(s, ~loader.buffer, ~loader.index, action: {
        ("corpus sliced: % slices".format(~slicer.index.size)).postln;
    });
});
)

// 2. analyse each slice into the dataset
(
~extractor = FluidProcessSlices({ |src, start, num, data|
    var mfcc, stats, flat, i, id;
    i  = data.value[\voice];
    id = data.key;
    mfcc  = FluidBufMFCC.kr(src, start, num, features: ~mfccBufs[i],
                numCoeffs: 13, numBands: 40, startCoeff: 1, trig: 1);
    stats = FluidBufStats.kr(~mfccBufs[i], stats: ~statsBufs[i],
                select: [\mean], trig: Done.kr(mfcc));
    flat  = FluidBufFlatten.kr(~statsBufs[i], ~flatBufs[i], trig: Done.kr(stats));
    FluidDataSetWr.kr(~corpusDS, id, nil, ~flatBufs[i], trig: Done.kr(flat));
});
~extractor.play(s, ~loader.buffer, ~slicer.index, { "features extracted".postln });
)

// 3. build the id -> [start, length] lookup, then fit the tree
(
Routine({
    ~slicer.index.asSortedArray.do { |pair|
        var bounds = pair[1][\bounds];
        ~lookupBuf.setn(0, [bounds[0], bounds[1] - bounds[0]]);
        s.sync;
        ~lookupDS.addPoint(pair[0], ~lookupBuf);
    };
    s.sync;
    ~tree.fit(~corpusDS, action: { "-- tree fitted --".postln });
}).play;
)

// 4. realtime: every hit plays its nearest corpus slice
(
SynthDef(\concat, {
    var in    = SoundIn.ar(0);
    var trig  = T2K.kr(FluidOnsetSlice.ar(in, \rcomplex, \thresh.kr(0.5),
                    minSliceLength: 4, windowSize: 512, hopSize: 128));
    var at    = TDelay.kr(trig, 0.012);
    var fire  = TDelay.kr(at, 0.003);
    var mfcc  = FluidMFCC.kr(in, numCoeffs: 13, numBands: 40,
                    startCoeff: 1).asArray;
    var start, env, play;

    FluidKrToBuf.kr(mfcc.collect { |x| Latch.kr(x, at) }, ~mfccQuery);
    ~tree.kr(TDelay.kr(at, 0.001), ~mfccQuery, ~queryOut,
        numNeighbours: 1, lookupDataSet: ~lookupDS);

    start = Latch.kr(FluidBufToKr.kr(~queryOut, 0, 1), TDelay.kr(at, 0.002));
    env   = EnvGen.ar(Env.perc(0.001, \dur.kr(0.4)), fire);
    play  = PlayBuf.ar(1, \corpus.kr(0),
                rate:     BufRateScale.kr(\corpus.kr(0)),
                trigger:  T2A.ar(fire),
                startPos: start,
                loop:     0);

    Out.ar(0, (play * env * \amp.kr(0.5)).dup);
}).add;
)

~run.(\concat, [\corpus, ~loader.buffer]);
~stop.(\concat);


// ----------------------------------------------------------------------------
// 6.6  Triggering on the unfamiliar
//
//      Keep running statistics over your descriptor history. Fire only
//      when a hit is statistically unlike the last hundred.
//
//      The instrument responds to NOVELTY rather than to attack. Play
//      steadily and nothing happens; do something unusual and it
//      answers. Inverts the normal relationship between player and
//      machine in a way that is genuinely different to play.
//
//      Done in sclang so the statistics are per-HIT. On the server,
//      FluidStats.kr would weight by how long each value was held,
//      biasing the result by inter-onset interval.
//
//      This REPLACES the \hit OSCdef from 4.4 and needs \analyse running.
// ----------------------------------------------------------------------------
(
~history = List[];
~histLen = 100;
~zThresh = 2.2;
~minHist = 25;

OSCdef(\hit, { |msg|
    var v = msg[3..].asArray;

    if (~history.size >= ~minHist) {
        var n     = ~history.size;
        var means = v.size.collect { |i| ~history.sum { |h| h[i] } / n };
        var devs  = v.size.collect { |i|
            (~history.sum { |h| (h[i] - means[i]).squared } / n).sqrt + 1e-6
        };
        var z = v.size.collect { |i| ((v[i] - means[i]) / devs[i]).abs }.sum / v.size;

        if (z > ~zThresh) {
            ("!!!   UNFAMILIAR    z = %".format(z.round(0.01))).postln;
            // fire whatever you like here
        };
    };

    ~history.add(v);
    if (~history.size > ~histLen) { ~history.removeAt(0) };
}, '/hit');
)

// play steadily for thirty seconds, then do something strange
~run.(\analyse);
~stop.(\analyse);


// ############################################################################
//  PART 7  ::  RESPONSE -- what to do with all this
// ############################################################################

// ----------------------------------------------------------------------------
// 7.1  Trigger to sample
//
//      The obvious one, included for completeness. Velocity scales the
//      output; the analysis from Part 4 can select the bank.
// ----------------------------------------------------------------------------
(
~sample = Buffer.read(s, Platform.resourceDir +/+ "sounds/a11wlk01.wav");

SynthDef(\sampler, {
    var in   = SoundIn.ar(0);
    var trig = T2K.kr(FluidOnsetSlice.ar(in, \rcomplex, \thresh.kr(0.5),
                   minSliceLength: 4, windowSize: 512, hopSize: 128));
    var fire = TDelay.kr(trig, 0.002);
    var vel  = Latch.kr(Amplitude.kr(in, 0.0005, 0.02), trig);
    var env  = EnvGen.ar(Env.perc(0.001, \dur.kr(0.4)), fire);
    var sig  = PlayBuf.ar(1, \buf.kr(0), BufRateScale.kr(\buf.kr(0)),
                   trigger: T2A.ar(fire), loop: 0);
    Out.ar(0, (sig * env * vel.linlin(0, 0.3, 0, 1).clip(0, 1) * \amp.kr(1)).dup);
}).add;
)

~run.(\sampler, [\buf, ~sample]);
~stop.(\sampler);


// ----------------------------------------------------------------------------
// 7.2  Excitation, not triggering
//
//      Instead of onset -> sample playback, feed the ACTUAL mic signal
//      into a resonator bank. You keep every micro-detail of the hit --
//      the scrape, the double bounce, the buzz -- because you never
//      reduced it to a number.
//
//      Analysis then retunes the resonators per hit rather than
//      selecting a sample. Consistently feels more alive than
//      trigger-and-sample, and degrades gracefully: a missed onset just
//      means the resonators were not retuned, not silence.
//
//      Headphones, or this will feed back.
// ----------------------------------------------------------------------------
(
SynthDef(\exciter, {
    var in    = SoundIn.ar(0);
    var trig  = T2K.kr(FluidOnsetSlice.ar(in, \rcomplex, 0.5, minSliceLength: 4));
    var at    = TDelay.kr(trig, 0.012);

    // ONE feature -> bare UGen, so .asArray then index. See 4.2 (b).
    var cent  = Latch.kr(
                    FluidSpectralShape.kr(in, [\centroid], unit: 0).asArray[0],
                    at).clip(200, 8000);
    var tune  = Lag.kr(cent.explin(200, 8000, 0.6, 2.4), 0.01);

    var exc   = HPF.ar(in, 300) * EnvGen.ar(Env.perc(0.0005, 0.03), T2A.ar(trig));

    var freqs = [1, 2.37, 3.41, 4.62, 6.13] * \base.kr(180) * tune;
    var amps  = [1, 0.7, 0.5, 0.35, 0.2];
    var rings = [1.8, 1.2, 0.9, 0.6, 0.4];

    var sig   = DynKlank.ar(`[freqs, amps, rings], exc * \drive.kr(1.0));

    Out.ar(0, Limiter.ar(LeakDC.ar(sig) * \amp.kr(0.3)).dup);
}).add;
)

~run.(\exciter, [\base, 180, \drive, 2]);
~run.(\exciter, [\base, 90,  \drive, 3]);
~stop.(\exciter);


// ----------------------------------------------------------------------------
// 7.3  Continuous mapping
//
//      No triggering at all. Descriptor streams drive synthesis
//      parameters directly. Combine with 2.7 and 6.1 for an instrument
//      that has no notes, only states.
//
//      Headphones.
// ----------------------------------------------------------------------------
(
SynthDef(\continuous, {
    var in    = SoundIn.ar(0);

    // ONE feature -> bare UGen. .asArray makes the indexing safe.
    var loud  = FluidLoudness.kr(in, [\loudness],
                    windowSize: 256, hopSize: 64).asArray[0];
    var shape = FluidSpectralShape.kr(in, [\centroid, \flatness], unit: 1).asArray;
    var onset = FluidOnsetFeature.kr(in, \rcomplex);

    var amp   = Lag.kr(loud.clip(-60, 0).linlin(-60, 0, 0, 1), 0.01);
    var cent  = Lag.kr(shape[0].clip(30, 110), 0.02);
    var flat  = Lag.kr(shape[1].clip(0, 1), 0.05);

    var sig = Mix([
        SinOsc.ar(cent.midicps)                        * (1 - flat),
        PinkNoise.ar                                   * flat,
        Dust.ar(onset.clip(0, 1).linexp(0, 1, 1, 200)) * 0.3
    ]) * amp;

    Out.ar(0, Limiter.ar(sig * \amp.kr(0.2)).dup);
}).add;
)

~run.(\continuous);
~stop.(\continuous);


// ############################################################################
//  STOP EVERYTHING
// ############################################################################
~stop.();


// ############################################################################
//  PART 8  ::  GOTCHAS
// ############################################################################
//
//  EVALUATION
//    Multi-line code must be inside ( ). Cmd+Enter evaluates the
//    enclosing parenthesised block, or the current LINE if there is
//    none -- so a multi-line OSCdef sitting outside parens is a syntax
//    error, not merely untidy.
//
//  TRIGGER CONVERSION
//    Use T2K, not A2K, on audio-rate triggers from FluCoMa's slicers.
//    They emit single-sample impulses; A2K samples only the first frame
//    of each control block and silently drops hits. This is the most
//    common cause of "it works but misses some". T2A goes the other way.
//
//  SINGLE-CHANNEL MULTIOUT
//    MultiOutUGen:initOutputs returns channels[0] when there is exactly
//    one channel -- so FluidLoudness.kr(in, [\loudness]) returns a BARE
//    UGEN and indexing it with [0] fails, while
//    FluidLoudness.kr(in, [\loudness, \peak]) returns an array. Call
//    .asArray on every descriptor and the problem disappears.
//
//  FIXED RETURN ORDERS
//    FluidSpectralShape returns centroid spread skewness kurtosis
//    rolloff flatness crest in that order regardless of what you pass
//    to `select`. Same for FluidLoudness (loudness, peak) and
//    FluidPitch (pitch, confidence).
//
//  Amplitude.kr ATTACK TIME DOES NOT SLOW THE RISE
//    Measured on a step, Amplitude.kr(in, 0.300, 0.150) reaches 95% in
//    one control block -- identical to Amplitude.kr(in, 0.001, 0.05).
//    Release is the only one of the two that shapes anything.
//    So you cannot build a slow background follower by lengthening the
//    attack. It rises with the strike, and fast > (slow * ratio) becomes
//    a value compared against a multiple of itself, which can never fire
//    on an attack. It fires on decay ripples and rebounds instead.
//    Symptom: "triggers on the tail and the slap-back but not on the
//    hit", and lowering the floor never helps, because the floor was
//    never the binding term. Worse, the adaptive term can drive the
//    threshold above 1.0, where triggering is arithmetically impossible.
//    Use LagUD.kr(fast, up, down) -- its up-time is real. See 1.4.
//
//  A SHORT RELEASE UNDER-READS PEAKS
//    Amplitude.kr is sampled once per control block (1.45ms at 64/44.1k).
//    A release much shorter than that falls into the troughs between
//    waveform peaks between samples, so it reads the envelope low. On one
//    noise burst the 0.05 release read about twice the 0.008 one. If a
//    "faster" follower reads lower than a slower one, this is why.
//
//  UNITS
//    FluidAmpSlice / FluidAmpGate   thresholds dBFS, times SAMPLES
//    FluidOnsetSlice / Novelty      times in HOPS
//    Onsets.kr                      mingap/medianspan in FFT FRAMES
//    Amplitude.kr                   times in SECONDS
//    Easy to mix up and the failure is silent.
//
//  THRESHOLDS ARE NOT PORTABLE
//    FluidOnsetSlice thresholds are not comparable between metrics, and
//    nothing is comparable between drums, rooms or mic positions.
//    Recalibrate rather than reusing numbers.
//
//  MFCC COEFFICIENT 0
//    Tracks loudness, not timbre. startCoeff: 1 makes the description
//    amplitude-invariant, which is what you want for classification.
//    Leave it in only if you want loud and quiet hits classified apart.
//
//  NORMALISE BEFORE FITTING
//    Loudness in dB and centroid in MIDI have very different ranges, so
//    an untreated distance metric is dominated by whichever is largest.
//    FluidNormalize or FluidStandardize first, always, and apply the
//    SAME transform at prediction time.
//
//  MODEL OBJECTS IN SYNTHDEFS
//    FluCoMa ids are integers, so models can go through NamedControls.
//    Referencing the objects directly is simpler but resolves at build
//    time -- build prediction SynthDefs AFTER fitting, and rebuild
//    after refitting.
//
//  BUSES
//    Allocate with Bus.audio / Bus.control rather than hardcoding
//    indices. Hardcoded private bus numbers collide with whatever else
//    is running, and the symptom is silence or someone else's audio.
//
//  SCOPING THE INPUT
//    Audio input buses start after the output buses, so s.scope(1, 0)
//    shows you output 0, not your microphone. Use
//    s.scope(1, s.options.numOutputBusChannels).
//
//  LATENCY BUDGET
//    amplitude domain        ~1-3 ms
//    spectral, 512 window    ~12 ms
//    spectral, 1024 window   ~23 ms
//    + lookAhead if used
//    + your declared descriptor delay (12ms in most of Part 4)
//    Useful pattern: fire the note on a fast time-domain detector,
//    decide what it WAS on a slower spectral one a few ms later, and
//    modulate the already-sounding voice.
//
//  MIC PLACEMENT CHANGES EVERYTHING
//    above the head    good position and brightness discrimination
//    inside the shell  strong pitch and damping, poor stick detail
//    snare side        excellent buzz and roll detection
//    A contact mic on the shell alongside the air mic gives a genuinely
//    independent second stream -- structure-borne, barely hears the
//    head, mostly rim and shell. Far better separation than two mics in
//    air, and the cheapest real second input you can add.
//
//  RETRAIN AFTER MOVING ANYTHING
//    All of Part 6 is specific to one mic position, one drum, one
//    tuning, one room. Treat trained models as part of the instrument
//    setup, not as software.
//
// ############################################################################
