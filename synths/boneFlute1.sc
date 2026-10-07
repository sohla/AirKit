// boneFlute1 : the two bone-pipe engines as one instrument.
//
//   windBone     -> the tone. the stretched partial bank, the resonated breath and the mouth noise. this is what the note is.
//   windBoneAir  -> the attack. the edge tone and the pipe, the breath before it has become tone.
//
// the attack has its own envelope and does not pass through the ADSR's attack stage, because it IS the attack.
// the ADSR carries the tone in behind it. so att shapes how the note arrives, accentDec shapes how it is struck,
// and the two are independent. with a long accentDec and a low accent the strike stops being a strike and
// becomes a breath that the tone grows out of.
//
// see windBone.sc for where the numbers come from: Hohle Fels griffon vulture radius, Jiahu red-crowned crane ulnae,
// and Atema's Divje Babe replicas.


//-------------------------------------------------- \boneFlute1

(
SynthDef(\boneFlute1, {
    |out = 0, pan = 0, amp = 0.3, gate = 1,
     freq = 740, register = 1, breath = 0.55, wall = 0.6, noise = 0.35,
     att = 0.04, dec = 0.18, sus = 0.75, rel = 0.35, curve = -2,
     accent = 0.8, accentAtt = 0.001, accentDec = 0.25, accentCurve = -4,
     chiff = 0.5, bore = 0.55, edge = 0.5, hiss = 2000,
     body = 1.0, jet = 0.0, dispersion = 0.45, cutoff = 4200,
     bend = 0, slide = 0.04, wobble = 0.25, wobRate = 4.6, vib = 0.12, vibRate = 5.2|
    var boreProfile = [0, 11, -8, 23, -17, 29, -6, 38, -21, 14, 45, -28];
    var nPart = boreProfile.size;
    var env, accentEnv, reg, regSq, f, roll, wallRing, fs, amps, lattice, flutter, rings;
    var tone, resAir, mouth, toneLayer, pipeDecay, air, pipe, attackLayer, sig;

    env = EnvGen.kr(Env.adsr(att, dec, sus, rel, 1, [curve.neg, curve, curve]), gate, doneAction: 2);
    accentEnv = EnvGen.ar(Env.perc(accentAtt, accentDec, 1, accentCurve));
    reg = register.max(1);
    regSq = reg.sqrt;
    f = Lag.kr(freq, slide) * bend.midiratio * (1 + (vib * 0.012 * SinOsc.kr(vibRate))) * (1 + (wobble * 0.008 * LFNoise2.kr(wobRate))) * (1 + ((breath - 0.5) * 0.02));

    // ---- the tone : windBone

    roll = breath.linlin(0, 1, 0.10, 0.60) / regSq;
    wallRing = wall.linexp(0, 1, 0.003, 0.055);
    fs = Array.fill(nPart, { |i| f * (i + 1) * (boreProfile[i] * dispersion * regSq / 100).midiratio });
    amps = Array.fill(nPart, { |i| roll.pow(i) * if((i + 1).even) { 1 + jet } { 1 - (jet * 0.4) } });
    lattice = 1 / (1 + (fs / cutoff.max(200)).squared);
    amps = amps * lattice;
    flutter = Array.fill(nPart, { 1 + (noise * 0.25 * LFNoise2.kr(exprand(3.0, 11.0))) });
    rings = fs.collect { |pf| (wallRing * 400 / pf.max(50)).clip(0.0015, 0.25) };
    tone = (SinOsc.ar(fs, Rand(0, 2pi)) * amps * flutter).sum;
    resAir = DynKlank.ar(`[fs, amps * 0.5, rings], PinkNoise.ar(1) * 0.06 * noise);
    mouth = HPF.ar(WhiteNoise.ar(1), (f * 2).clip(200, 9000)) * noise * 0.1;
    toneLayer = (((tone + resAir) * breath.sqrt) + mouth) * 0.3 * body;

    // ---- the attack : windBoneAir

    pipeDecay = wall.linexp(0, 1, 0.02, 0.5);
    air = BPF.ar(WhiteNoise.ar(1), (f * edge.linexp(0, 1, 1.2, 4.5)).clip(80, 16000), 1.2) * 3;
    air = air + (HPF.ar(WhiteNoise.ar(1), hiss.clip(200, 15000)) * 0.35);
    air = air * breath;
    pipe = CombC.ar(air, 0.05, f.reciprocal.clip(0.00008, 0.05), pipeDecay);
    pipe = LPF.ar(pipe, bore.linexp(0, 1, 900, 11000));
    attackLayer = (pipe * 0.8) + (air * chiff * 0.6);
    attackLayer = attackLayer * 1.6 * accent * accentEnv;

    // ---- the tone rides the ADSR, the attack does not

    sig = (toneLayer * env) + attackLayer;
    sig = LeakDC.ar(sig) * amp;
    Out.ar(out, Pan2.ar(sig, pan));
}).add;
)


Synth(\boneFlute1, [\freq, 346, \accent, 0.3, \accentDec, 0.3, \chiff, 0.8, \att, 0.5, \rel, 0.6]);

// accentCurve decides whether accentDec means anything. -6 is gone in 50ms however long accentDec is.
Synth(\boneFlute1, [\freq, 346, \accent, 0.6, \accentDec, 0.3, \accentCurve, -6, \chiff, 0.8, \att, 0.5, \rel, 0.6]);
Synth(\boneFlute1, [\freq, 346, \accent, 0.6, \accentDec, 0.3, \accentCurve, -3, \chiff, 0.8, \att, 0.5, \rel, 0.6]);
Synth(\boneFlute1, [\freq, 346, \accent, 0.6, \accentDec, 0.3, \accentCurve, -1, \chiff, 0.8, \att, 0.5, \rel, 0.6]);
Synth(\boneFlute1, [\freq, 346, \accent, 0.6, \accentDec, 0.3, \accentCurve, 0, \chiff, 0.8, \att, 0.5, \rel, 0.6]);

// and how loud it sits against the tone
Synth(\boneFlute1, [\freq, 346, \accent, 0.3, \accentDec, 0.3, \chiff, 0.8, \att, 0.5, \rel, 0.6]);
Synth(\boneFlute1, [\freq, 346, \accent, 0.6, \accentDec, 0.3, \chiff, 0.8, \att, 0.5, \rel, 0.6]);
Synth(\boneFlute1, [\freq, 346, \accent, 1.0, \accentDec, 0.3, \chiff, 0.8, \att, 0.5, \rel, 0.6]);
Synth(\boneFlute1, [\freq, 346, \accent, 1.0, \accentDec, 0.6, \chiff, 0.9, \att, 0.5, \rel, 0.6]);
Synth(\boneFlute1, [\freq, 346, \accent, 0.3, \accentDec, 0.6, \chiff, 0.9, \att, 0.9, \rel, 1.2, \breath, 0.3]);
Synth(\boneFlute1, [\freq, 173, \accent, 0.4, \accentDec, 0.5, \chiff, 0.9, \att, 0.7, \rel, 1.0, \wall, 0.85]);
Synth(\boneFlute1, [\freq, 692, \accent, 0.25, \accentDec, 0.25, \chiff, 0.7, \att, 0.4, \rel, 0.5]);
Synth(\boneFlute1, [\freq, 346, \accent, 1.0, \accentDec, 0.05, \chiff, 0.8, \att, 0.01, \rel, 0.3]);
Synth(\boneFlute1, [\freq, 346, \accent, 0, \att, 0.6, \rel, 0.8, \breath, 0.3]);


//-------------------------------------------------- expression


// AIRS. the setting above, played. long soft strikes that the tone grows out of, and a lot of space between them.
(
Pdef(\airs,
    Pbind(
        \instrument, \boneFlute1,
        \degree, Prand([0, 2, 3, 5, 7], inf),
        \octave, Pwrand([4, 4, 5], [0.5, 0.3, 0.2], inf),
        \dur, Pseq([2.5, 1.5, Rest(1.0), 3.0, 2.0, Rest(1.5), 1.5, 4.0, Rest(2.0)] * 0.2, inf),
        \legato, 0.9,
        \att, Pwhite(0.35, 0.8),
        \dec, Pwhite(0.4, 1.0),
        \sus, Pwhite(0.65, 0.9),
        \rel, Pwhite(0.5, 1.3),
        \accent, Pwhite(0.1, 0.2),
        \accentDec, Pwhite(0.9, 0.95),
        \chiff, Pwhite(0.7, 0.95),
        \breath, Pwhite(0.25, 0.45),
        \noise, Pwhite(0.4, 0.6),
        \vib, Pwhite(0.05, 0.22),
        \vibRate, Pwhite(4.0, 5.5),
        \wobble, Pwhite(0.25, 0.5),
        \slide, Pexprand(0.1, 0.5),
        \wall, Pwhite(0.7, 0.9),
        \dispersion, 0.5,
        \amp, 0.3,
        \pan, Pwhite(-0.4, 0.4)
    )
).play(quant: 0);
)

Pdef(\airs).stop;


// BREATHING. one long arc of blowing. breath drives the colour and everything else follows it, so the swell
// is not a volume change, it is the instrument opening up and closing again.
(
Pdef(\breathing,
    Pbind(
        \instrument, \boneFlute1,
        \degree, Pseq([0, 2, 3, 5, 3, 2, 0, -2], inf),
        \octave, 4,
        \dur, 0.7,
        \legato, 0.95,
        \breath, Pseq([0.16, 0.24, 0.34, 0.46, 0.58, 0.72, 0.86, 0.95, 0.88, 0.74, 0.6, 0.47, 0.36, 0.27, 0.2], inf),
        \amp, Pkey(\breath).linlin(0.16, 0.95, 0.14, 0.34),
        \att, Pkey(\breath).explin(0.16, 0.95, 0.6, 0.02),
        \dec, 0.4,
        \sus, 0.85,
        \rel, Pkey(\breath).explin(0.16, 0.95, 0.9, 0.3),
        \accent, Pkey(\breath).linlin(0.16, 0.95, 0.15, 0.8),
        \accentDec, Pkey(\breath).explin(0.16, 0.95, 0.5, 0.08),
        \chiff, Pkey(\breath).linlin(0.16, 0.95, 0.95, 0.5),
        \noise, Pkey(\breath).linlin(0.16, 0.95, 0.6, 0.25),
        \vib, Pkey(\breath).linlin(0.16, 0.95, 0.04, 0.26),
        \wall, 0.8,
        \pan, Pwhite(-0.25, 0.25)
    )
).play(quant: 0);
)

Pdef(\breathing).stop;


// CALLS. short figures thrown out and then left alone. the bends are the expression, not the notes.
(
Pdef(\calls,
    Pbind(
        \instrument, \boneFlute1,
        \degree, Pseq([Pseq([0, 4, 7], 1), Pseq([7, 5], 1), Prand([0, 2, 4, 5, 7, 9], 3), Pseq([9, 7, 4], 1)], inf),
        \octave, Pwrand([4, 5, 5], [0.3, 0.45, 0.25], inf),
        \dur, Pseq([0.18, 0.18, 0.5, Rest(1.2), 0.22, 0.6, Rest(0.8), 0.15, 0.15, 0.15, 0.7, Rest(1.6)], inf),
        \legato, Pwhite(0.6, 1.0),
        \att, Pexprand(0.01, 0.1),
        \dec, Pwhite(0.15, 0.4),
        \sus, Pwhite(0.6, 0.85),
        \rel, Pwhite(0.2, 0.6),
        \accent, Pwhite(0.5, 0.95),
        \accentDec, Pwhite(0.08, 0.3),
        \chiff, Pwhite(0.5, 0.9),
        \bend, Pwrand([0, Pwhite(-0.8, 0.8, 1), Pwhite(-2.0, 2.0, 1)], [0.5, 0.35, 0.15], inf),
        \slide, Pexprand(0.03, 0.25),
        \breath, Pwhite(0.4, 0.85),
        \noise, Pwhite(0.25, 0.5),
        \wobble, Pwhite(0.2, 0.6),
        \vib, Pwhite(0.0, 0.25),
        \wall, 0.78,
        \dispersion, 0.5,
        \amp, 0.3,
        \pan, Pwhite(-0.5, 0.5)
    )
).play(quant: 0);
)

Pdef(\calls).stop;


// LEGATO. one synth, gate held open, so the envelope never retriggers and slide does all the joining.
(
Pdef(\legatoLine,
    Pmono(\boneFlute1,
        \degree, Pseq([0, 2, 3, 5, 4, 2, 0, -2, 0, 4, 5, 7, 5, 4, 2, 0], inf),
        \octave, 4,
        \dur, Pseq([0.6, 0.3, 0.3, 0.9, 0.3, 0.6, 1.2, 0.6], inf),
        \slide, Pexprand(0.08, 0.3),
        \att, 0.6,
        \dec, 0.6,
        \sus, 0.9,
        \rel, 1.6,
        \curve, -1,
        \accent, 0,
        \breath, Pseq([0.3, 0.38, 0.46, 0.56, 0.62, 0.56, 0.46, 0.38], inf),
        \noise, 0.45,
        \vib, Pseq([0.05, 0.1, 0.18, 0.28, 0.28, 0.18, 0.1, 0.05], inf),
        \vibRate, 4.8,
        \wobble, 0.3,
        \wall, 0.82,
        \amp, 0.32
    )
).play(quant: 0);
)

Pdef(\legatoLine).stop;


// LEAPS. \harmonic multiplies the pitch so register 2 and 3 are the overblown octave and twelfth of the same
// pipe, and register also thins the spectrum, the way a real pipe gets purer the higher you push it.
(
Pdef(\leaps,
    Pbind(
        \instrument, \boneFlute1,
        \degree, Pseq([0, 2, 4, 2, 0, 4, 5, 2], inf),
        \octave, 4,
        \register, Pwrand([1, 2, 3], [0.5, 0.33, 0.17], inf),
        \harmonic, Pkey(\register),
        \dur, Pseq([0.4, 0.4, 0.8, 0.4, 0.4, 0.4, 1.2, 0.8], inf),
        \legato, Pwhite(0.6, 0.95),
        \breath, Pkey(\register).linlin(1, 3, 0.45, 0.95),
        \att, Pkey(\register).explin(1, 3, 0.12, 0.01),
        \dec, 0.25,
        \sus, 0.75,
        \rel, 0.45,
        \accent, Pkey(\register).linlin(1, 3, 0.35, 0.9),
        \accentDec, Pkey(\register).explin(1, 3, 0.35, 0.07),
        \chiff, Pwhite(0.6, 0.9),
        \noise, 0.3,
        \vib, Pwhite(0.05, 0.2),
        \dispersion, 0.5,
        \wall, 0.78,
        \amp, 0.3,
        \pan, Pwhite(-0.35, 0.35)
    )
).play(quant: 0);
)

Pdef(\leaps).stop;


// PHRASE. everything hung off note length, the way a player's articulation is. short notes are struck and dry,
// long ones are breathed into, held open and given vibrato.
(
Pdef(\phrase,
    Pbind(
        \instrument, \boneFlute1,
        \degree, Pseq([Pseq([0, 2, 4, 5, 7, 5, 4, 2], 1), Prand([0, 2, 4, 5, 7, 9], 6), Pseq([9, 7, 5, 4, 2, 0], 1)], inf),
        \octave, Pwrand([4, 4, 5], [0.45, 0.35, 0.2], inf),
        \dur, Pseq([0.25, 0.25, 0.25, 0.75, 0.25, 0.5, 1.5, 0.5], inf),
        \legato, Pkey(\dur).linlin(0.25, 1.5, 0.5, 1.0),
        \att, Pkey(\dur).explin(0.25, 1.5, 0.01, 0.55),
        \dec, Pkey(\dur) * 0.35,
        \sus, Pkey(\dur).linlin(0.25, 1.5, 0.55, 0.9),
        \rel, Pkey(\dur) * 0.6,
        \curve, Pkey(\dur).linlin(0.25, 1.5, -5, -1),
        \accent, Pkey(\dur).explin(0.25, 1.5, 0.95, 0.2),
        \accentDec, Pkey(\dur).explin(0.25, 1.5, 0.06, 0.45),
        \chiff, Pkey(\dur).linlin(0.25, 1.5, 0.5, 0.95),
        \breath, Pseq([0.4, 0.5, 0.62, 0.74, 0.86, 0.7, 0.55, 0.42], inf),
        \noise, Pkey(\breath).linlin(0.4, 0.86, 0.5, 0.25),
        \vib, Pkey(\dur).explin(0.25, 1.5, 0.02, 0.3),
        \slide, Pkey(\dur) * 0.08,
        \bend, Pwrand([0, Pwhite(-0.4, 0.4, 1)], [0.8, 0.2], inf),
        \wobble, Pwhite(0.2, 0.45),
        \dispersion, 0.5,
        \wall, 0.78,
        \amp, 0.3,
        \pan, Pwhite(-0.35, 0.35)
    )
).play(quant: 0);
)

Pdef(\phrase).stop;


// TWO FLUTES. a low one holding and a high one answering it. the register gap is most of the expression.
(
Pdef(\twoFlutes,
    Ppar([
        Pbind(
            \instrument, \boneFlute1,
            \degree, Pseq([0, -3, 0, 2, -2, 0], inf),
            \octave, 4,
            \dur, Pseq([3.0, 2.0, 4.0, 2.5, 3.5, 2.0], inf),
            \legato, 1.0,
            \att, Pwhite(0.5, 1.0),
            \dec, 0.8,
            \sus, 0.88,
            \rel, Pwhite(0.8, 1.6),
            \accent, Pwhite(0.15, 0.3),
            \accentDec, Pwhite(0.35, 0.65),
            \chiff, Pwhite(0.8, 0.95),
            \breath, Pwhite(0.25, 0.4),
            \noise, Pwhite(0.45, 0.6),
            \vib, Pwhite(0.06, 0.18),
            \wobble, Pwhite(0.3, 0.55),
            \slide, Pexprand(0.2, 0.7),
            \wall, 0.85,
            \amp, 0.3,
            \pan, Pwhite(-0.7, -0.3)
        ),
        Pbind(
            \instrument, \boneFlute1,
            \degree, Pseq([Rest(), Rest(), Pseq([7, 9, 7, 5], 1), Rest(), Prand([4, 5, 7, 9, 11], 3), Rest()], inf),
            \octave, 5,
            \dur, Pseq([1.5, 1.0, 0.4, 0.4, 0.4, 0.8, 1.2, 0.3, 0.3, 0.6, 2.0], inf),
            \legato, Pwhite(0.5, 0.9),
            \att, Pexprand(0.015, 0.2),
            \dec, Pwhite(0.2, 0.5),
            \sus, Pwhite(0.6, 0.85),
            \rel, Pwhite(0.3, 0.8),
            \accent, Pwhite(0.45, 0.85),
            \accentDec, Pwhite(0.1, 0.35),
            \chiff, Pwhite(0.55, 0.9),
            \breath, Pwhite(0.45, 0.8),
            \noise, Pwhite(0.25, 0.45),
            \vib, Pwhite(0.08, 0.28),
            \bend, Pwrand([0, Pwhite(-0.6, 0.6, 1)], [0.7, 0.3], inf),
            \slide, Pexprand(0.04, 0.2),
            \wall, 0.75,
            \amp, 0.26,
            \pan, Pwhite(0.3, 0.7)
        )
    ])
).play(quant: 0);
)

Pdef(\twoFlutes).stop;


(
Pdef(\airs).stop;
Pdef(\breathing).stop;
Pdef(\calls).stop;
Pdef(\legatoLine).stop;
Pdef(\leaps).stop;
Pdef(\phrase).stop;
Pdef(\twoFlutes).stop;
)
