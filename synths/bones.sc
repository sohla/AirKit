// bone-on-bone impacts. three approaches:
//   \boneHit   modal free-free bar, a pair of them clacking   <- the main one
//   \boneTube  hollow long bone as a lossy waveguide
//   \boneRoll  a handful tumbling, one synth per handful
//
// cortical bone: E ~18 GPa, rho ~1900 kg/m3, so sqrt(E/rho) ~3080 m/s.
// slower than hardwood along the grain, which is why bone clacks lower
// than a woodblock of the same size.
// loss factor 0.01-0.02 dry, 0.035-0.1 hydrated -> that is the dryness arg.
// attenuation climbs steeply with frequency (Haversian scattering) -> porosity.


//-------------------------------------------------- \boneHit

(
SynthDef(\boneHit, {
    |out = 0, pan = 0, amp = 0.5,
     freq = 700, dryness = 0.6, porosity = 0.5, hardness = 0.6,
     inharm = 0.15, scatter = 0.25, grip = 0.3, strike = 0.08,
     detune = 0.035, flam = 0.002, pairBalance = 0.4,
     hollow = 0.25, cavity = 1.7|

    var barRatios = [1, 2.756, 5.404, 8.933, 13.344, 18.638, 24.597];
    var nModes = barRatios.size;
    var stretch = 1 + (inharm * 0.3);
    var tilt = porosity * 0.6;
    var baseDecay = dryness.linexp(0, 1, 0.014, 0.40) * (1 - (grip * 0.6));
    var contact = hardness.linexp(0, 1, 0.0045, 0.00035);
    var bite = hardness.linexp(0, 1, 2500, 16000);

    // free-free mode n has an antinode at each end and nodes where
    // cos((n+1)*pi*x) crosses zero. strike 0 = tip, 0.5 = mid-shaft.
    var strikeGain = barRatios.collect { |r, i| ((i + 2) * pi * strike).cos.abs };
    // fingers at the hold point take the low modes first
    var gripGain = barRatios.collect { |r, i|
        1 - (grip * (1 - (i / (nModes - 1))).pow(0.7))
    };

    var pulse, exciter, bodyA, bodyB, air, sig, bone;

    bone = { |f0|
        var freqs = barRatios.collect { |r, i|
            (f0 * r.pow(stretch)
                * (1 + (scatter * 0.12 * Rand(-1, 1) * (i + 1) / nModes))).clip(20, 17000)
        };
        var decays = freqs.collect { |f| (baseDecay * (tilt.neg * f / 1000).exp).max(0.0015) };
        var amps = barRatios.collect { |r, i| strikeGain[i] * gripGain[i] / r.pow(0.8) };
        DynKlank.ar(`[freqs, amps, decays], exciter)
    };

    pulse = EnvGen.ar(Env.perc(0.00004, contact, 1, -6));
    exciter = ((WhiteNoise.ar(1) * 0.9) + 0.35) * pulse;
    exciter = HPF.ar(LPF.ar(exciter, bite), 120) * 0.3;

    bodyA = bone.(freq);
    bodyB = bone.(freq * (1 + detune));
    bodyB = DelayC.ar(bodyB, 0.02, flam.clip(0, 0.02));

    air = Ringz.ar(exciter, (freq * cavity).clip(40, 6000), baseDecay * 0.5) * hollow * 0.5;

    sig = (bodyA * (1 - pairBalance)) + (bodyB * pairBalance) + air;
    sig = HPF.ar(sig, 55) * 0.6;
    sig = sig * EnvGen.ar(Env([1, 1, 0], [baseDecay * 7, baseDecay * 2], \sin), doneAction: 2);

    Out.ar(out, Pan2.ar(sig, pan, amp));
}).add;
)

// a pair of ribs, held, clacked near the tips
Synth(\boneHit, [\freq, 969, \dryness, 0.75, \grip, 0.45, \hardness, 0.7]);

// the same bones, fresh and greasy
Synth(\boneHit, [\freq, 969, \dryness, 0.1, \grip, 0.45, \hardness, 0.7]);

// femur, hollow, loose in the hand
Synth(\boneHit, [\freq, 198, \dryness, 0.8, \grip, 0.1, \hollow, 0.6, \hardness, 0.4]);

// toe bones, a dry tick
Synth(\boneHit, [\freq, 6332, \dryness, 0.9, \porosity, 0.8, \hardness, 0.9, \hollow, 0.0]);

// struck mid-shaft: even modes vanish, the clack hollows out
Synth(\boneHit, [\freq, 684, \strike, 0.5, \dryness, 0.8, \grip, 0.05]);

// porosity sweep, same bone
(
[0.1, 0.3, 0.6, 1.0].do { |p, i|
    SystemClock.sched(i * 0.6, { Synth(\boneHit, [\freq, 969, \porosity, p, \dryness, 0.85]); nil });
};
)


//-------------------------------------------------- \boneTube

(
SynthDef(\boneTube, {
    |out = 0, pan = 0, amp = 0.5,
     freq = 260, bore = 0.5, wall = 0.4, marrow = 0.25,
     dryness = 0.6, hardness = 0.6, grip = 0.2, flare = 0.3|

    var contact = hardness.linexp(0, 1, 0.005, 0.0004);
    var bite = hardness.linexp(0, 1, 2000, 14000);
    var loopLoss = bore.linexp(0, 1, 2000, 9000);
    var ring = dryness.linexp(0, 1, 0.05, 0.9) * (1 - (grip * 0.6));
    var delay = freq.reciprocal.clip(0.00008, 0.05);
    var feedback = 0.001.pow(delay / ring);
    var pulse, exciter, tube, walls, dull, sig;

    pulse = EnvGen.ar(Env.perc(0.00005, contact, 1, -6));
    exciter = ((PinkNoise.ar(1) * 1.2) + 0.3) * pulse;
    exciter = LPF.ar(exciter, bite) * 0.4;

    tube = LocalIn.ar(1);
    tube = DelayC.ar(exciter + (tube * feedback), 0.05, delay);
    tube = LPF.ar(tube, loopLoss);
    tube = OnePole.ar(tube, flare * 0.8);
    LocalOut.ar(tube);

    walls = [1, 2.756, 5.404, 8.933].collect { |r, i|
        Ringz.ar(exciter, (freq * 3.2 * r.pow(1.05)).clip(40, 16000),
            (ring * 0.25 * (0.55 ** i)).max(0.002)) / r.pow(0.8)
    }.sum * wall;

    dull = LPF.ar(exciter, (freq * 2).clip(80, 900)) * marrow * 6;

    sig = tube + walls + dull;
    sig = LeakDC.ar(sig) * 0.5;
    sig = sig * EnvGen.ar(Env([1, 1, 0], [ring * 2.5, ring], \sin), doneAction: 2);

    Out.ar(out, Pan2.ar(sig, pan, amp));
}).add;
)

Synth(\boneTube, [\freq, 198, \bore, 0.6, \wall, 0.5, \dryness, 0.8]);
Synth(\boneTube, [\freq, 375, \bore, 0.3, \wall, 0.2, \marrow, 0.6, \dryness, 0.3]);
Synth(\boneTube, [\freq, 120, \bore, 0.9, \wall, 0.7, \marrow, 0.05, \dryness, 0.95, \hardness, 0.8]);


//-------------------------------------------------- \boneRoll

// a rolling body hits a number of asperities per second set by its size, and
// the rate falls away as it settles. so: an impact series, thinning, clumped.

(
SynthDef(\boneRoll, {
    |out = 0, pan = 0, amp = 0.4,
     freq = 500, sizeSpread = 1.4, density = 30, settle = 0.6,
     clump = 0.5, dryness = 0.7, porosity = 0.5, hardness = 0.55,
     dur = 2.5|

    var ratios = [1, 2.756, 5.404, 8.933];
    var nBones = 6;
    var tilt = porosity * 0.6;
    var baseDecay = dryness.linexp(0, 1, 0.012, 0.18);
    var contact = hardness.linexp(0, 1, 0.004, 0.0004);
    var bite = hardness.linexp(0, 1, 2500, 15000);
    var pace, bank, sig;

    pace = density * XLine.kr(1, settle.linexp(0, 1, 0.5, 0.02), dur);
    pace = pace * LFNoise1.kr(2.5).range(1 - clump, 1 + clump);
    pace = (pace / nBones).max(0.3);

    bank = Array.fill(nBones, { |b|
        var f0 = freq * (2 ** Rand(sizeSpread.neg, sizeSpread));
        var trig = Dust.ar(pace);
        var exc = WhiteNoise.ar(1) * Decay.ar(trig * TExpRand.ar(0.2, 1.0, trig), contact);
        exc = LPF.ar(exc, bite);
        ratios.collect { |r, i|
            var f = (f0 * r.pow(1.05)).clip(30, 16000);
            Ringz.ar(exc, f, (baseDecay * (tilt.neg * f / 1000).exp).max(0.0015)) / r.pow(0.8)
        }.sum
    });

    sig = Splay.ar(bank, 0.9) * 0.25;
    sig = HPF.ar(sig, 55);
    sig = sig * EnvGen.ar(Env([1, 1, 0], [dur * 0.85, dur * 0.15], \sin), doneAction: 2);

    Out.ar(out, Balance2.ar(sig[0], sig[1], pan, amp));
}).add;
)

// tipped out of a bag and left to settle
Synth(\boneRoll, [\freq, 500, \density, 40, \settle, 0.7, \dur, 3]);

// a slow turn of the bag, no settling
Synth(\boneRoll, [\freq, 420, \density, 14, \settle, 0.05, \clump, 0.8, \dur, 6]);

// small bones only, shaken hard
Synth(\boneRoll, [\freq, 2400, \sizeSpread, 0.7, \density, 70, \settle, 0.3,
    \dryness, 0.9, \porosity, 0.8, \dur, 2]);


//-------------------------------------------------- the bag

// bar fundamentals from real bone dimensions. f1 = 3166 * thickness / length^2
// for sqrt(E/rho) = 3080. an ideal solid bar, so these run low: a real bone is
// a hollow tube and stiffer for its mass. and the grip usually kills f1, so
// the clack you hear sits a mode or two above these.

(
~boneSpeed = 3080;
~boneBar = { |lengthM, thickM|
    (4.730.squared / (2pi * lengthM.squared)) * (thickM / 12.sqrt) * ~boneSpeed
};
~boneBag = [
    [0.40, 0.010],   // femur
    [0.26, 0.008],   // tibia
    [0.18, 0.007],   // humerus
    [0.14, 0.006],   // rib
    [0.09, 0.006],   // metacarpal
    [0.07, 0.007],   // vertebra fragment
    [0.05, 0.005]    // phalanx
].collect { |d| ~boneBar.(d[0], d[1]) };
~boneBag.round(1).postln;
)


// every bone in the bag, once, low to high
(
Pdef(\bagScan,
    Pbind(
        \instrument, \boneHit,
        \freq, Pseq(~boneBag, 1),
        \dur, 0.5,
        \dryness, 0.8,
        \grip, 0.2,
        \amp, 0.4
    )
).play;
)


// the bag turned over: poisson-ish arrivals, bones recurring, size correlated
// with loudness, dullness and hollowness
(
Pdef(\bagRoll,
    Pbind(
        \instrument, \boneHit,
        \freq, Prand(~boneBag, inf) * Pwhite(0.97, 1.03),
        \dur, Pexprand(0.025, 0.45),
        \amp, Pkey(\freq).explin(198, 6332, 0.55, 0.14) * Pexprand(0.5, 1.0),
        \dryness, Pkey(\freq).explin(198, 6332, 0.45, 0.85),
        \hollow, Pkey(\freq).explin(198, 6332, 0.55, 0.04),
        \porosity, Pwhite(0.35, 0.8),
        \hardness, Pexprand(0.25, 0.95),
        \grip, Pwhite(0.0, 0.35),
        \strike, Pwhite(0.0, 0.3),
        \detune, Pwhite(0.01, 0.07),
        \flam, Pexprand(0.0008, 0.006),
        \scatter, Pwhite(0.15, 0.5),
        \pan, Pwhite(-0.7, 0.7)
    )
).play(quant: 0);
)

Pdef(\bagRoll).stop;


// bursts: a clatter of 3-9, then a gap. the gaps are the piece
(
Pdef(\bagBursts,
    Pbind(
        \instrument, \boneHit,
        \freq, Prand(~boneBag, inf),
        \dur, Pseq([
            Pn(Pexprand(0.02, 0.07, 1), { rrand(3, 9) }),
            Pexprand(0.3, 1.6, 1)
        ], inf),
        \amp, Pkey(\freq).explin(198, 6332, 0.5, 0.12) * Pexprand(0.4, 1.0),
        \dryness, Pkey(\freq).explin(198, 6332, 0.4, 0.85),
        \hollow, Pkey(\freq).explin(198, 6332, 0.5, 0.03),
        \porosity, Pwhite(0.4, 0.9),
        \hardness, Pexprand(0.3, 1.0),
        \grip, Pwhite(0.1, 0.5),
        \pan, Pwhite(-0.8, 0.8)
    )
).play(quant: 0);
)

Pdef(\bagBursts).stop;


// handfuls dropped, a few at a time
(
Pdef(\handfuls,
    Pbind(
        \instrument, \boneRoll,
        \dur, Pexprand(1.5, 5),
        \freq, Prand(~boneBag[1..4], inf),
        \sizeSpread, Pwhite(0.8, 1.8),
        \density, Pexprand(10, 60),
        \settle, Pwhite(0.1, 0.9),
        \clump, Pwhite(0.2, 0.9),
        \dryness, Pwhite(0.5, 0.95),
        \porosity, Pwhite(0.4, 0.8),
        \hardness, Pwhite(0.3, 0.8),
        \amp, Pwhite(0.25, 0.45),
        \pan, Pwhite(-0.5, 0.5)
    )
).play(quant: 0);
)

Pdef(\handfuls).stop;


// the big bones underneath the small ones
(
Pdef(\bagLow,
    Pbind(
        \instrument, \boneTube,
        \freq, Prand(~boneBag[0..2], inf),
        \dur, Pexprand(0.2, 1.4),
        \bore, Pwhite(0.3, 0.9),
        \wall, Pwhite(0.15, 0.6),
        \marrow, Pwhite(0.05, 0.5),
        \dryness, Pwhite(0.4, 0.9),
        \hardness, Pwhite(0.3, 0.9),
        \grip, Pwhite(0.05, 0.4),
        \amp, Pwhite(0.3, 0.5),
        \pan, Pwhite(-0.4, 0.4)
    )
).play(quant: 0);
)

Pdef(\bagLow).stop;


(
Pdef(\bagRoll).play(quant: 0);
Pdef(\bagLow).play(quant: 0);
)

(
Pdef(\bagRoll).stop;
Pdef(\bagBursts).stop;
Pdef(\handfuls).stop;
Pdef(\bagLow).stop;
)
