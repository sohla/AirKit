// a bone as a flute. the oldest instruments we have are hollow bird bones.
//
//   \windBone     an irregular bone bore driven by a jet. the main one.
//   \windBoneAir  stage one: a hollow bone blown across, no finger holes
//
// the bones that were actually used are wing bones, and they are narrow:
// Hohle Fels is a griffon vulture radius, 21.8 cm preserved of an original
// ~34 cm, bore ~8 mm, five finger holes, two V-notches cut at the blowing end.
// Jiahu are red-crowned crane ulnae, 18-25 cm, five to eight holes, end-blown.
// L/d of about 30 means these instruments sound HIGH. the measured Jiahu pipe
// tones sit around F#5-A#5 and the fingered notes run up past A6.
//
// three things separate a bone pipe from a wooden one:
//   the walls are thin, dense and hard, so the resonances are high-Q and the
//   tone goes bell-like. Atema's Divje Babe replica is "sweet and clear and
//   bell-like"; a fresh bone version of the same flute was "dull and rough".
//   -> wall
//
//   the bore is a bird's wing, not a drilled tube. it tapers, it curves, it has
//   ridges and a nutrient foramen. so the upper resonances are not integer
//   multiples of the first. short wavelengths are thrown off most.
//   -> dispersion, and boreProfile below
//
//   it is end-blown across a notch, so a lot of the air never becomes tone.
//   -> noise, chiff
//
// an open pipe has every harmonic, 1f 2f 3f 4f, and overblowing climbs them.
// the Geissenklosterle swan-radius flute gives "four basic notes" and "three
// additional overtones by blowing more sharply".
// -> register


//-------------------------------------------------- \windBone

(
SynthDef(\windBone, {
    |out = 0, pan = 0, amp = 0.3, gate = 1,
     freq = 740, register = 1, breath = 0.55, jet = 0.0,
     noise = 0.35, chiff = 0.5, dispersion = 0.45, wall = 0.6,
     cutoff = 4200, bend = 0, slide = 0.04,
     wobble = 0.25, wobRate = 4.6, vib = 0.12, vibRate = 5.2,
     att = 0.05, rel = 0.25|

    // how far each partial is thrown off the harmonic series, in cents.
    // it grows with partial number because the bore's irregularities are
    // small compared to the long wavelengths and not to the short ones.
    var boreProfile = [0, 11, -8, 23, -17, 29, -6, 38, -21, 14, 45, -28];
    var nPart = boreProfile.size;

    var env = EnvGen.kr(Env.asr(att, 1, rel, \sin), gate, doneAction: 2);
    var reg = register.max(1);
    var regSq = reg.sqrt;

    // blowing harder sharpens the note, as it does on any flute
    var f = Lag.kr(freq, slide)
        * bend.midiratio
        * (1 + (vib * 0.012 * SinOsc.kr(vibRate)))
        * (1 + (wobble * 0.008 * LFNoise2.kr(wobRate)))
        * (1 + ((breath - 0.5) * 0.02));

    // soft is near a sine, loud is rich, and the upper registers are purer
    // whatever you do with them
    var roll = breath.linlin(0, 1, 0.10, 0.60) / regSq;
    var wallRing = wall.linexp(0, 1, 0.003, 0.055);

    var fs = Array.fill(nPart, { |i|
        f * (i + 1) * (boreProfile[i] * dispersion * regSq / 100).midiratio
    });

    // jet offset trades the even harmonics against the odd
    var amps = Array.fill(nPart, { |i|
        var k = i + 1;
        roll.pow(i) * if(k.even) { 1 + jet } { 1 - (jet * 0.4) }
    });

    // the open tone holes stop reflecting above the lattice cutoff
    var lattice = 1 / (1 + (fs / cutoff.max(200)).squared);

    // turbulence pushing the partials around, the flute's live quality
    var flutter = Array.fill(nPart, {
        1 + (noise * 0.25 * LFNoise2.kr(exprand(3.0, 11.0)))
    });

    var rings = fs.collect { |pf| (wallRing * 400 / pf.max(50)).clip(0.0015, 0.25) };

    var tone, resAir, mouth, edge, sig;

    amps = amps * lattice;

    tone = (SinOsc.ar(fs, Rand(0, 2pi)) * amps * flutter).sum;

    // breath noise heard through the bore rather than past it
    resAir = DynKlank.ar(`[fs, amps * 0.5, rings], PinkNoise.ar(1) * 0.06 * noise);

    mouth = HPF.ar(WhiteNoise.ar(1), (f * 2).clip(200, 9000)) * noise * 0.1;

    edge = BPF.ar(WhiteNoise.ar(1), (f * 2.5).clip(100, 16000), 0.4)
        * EnvGen.ar(Env.perc(0.004, 0.09)) * chiff * 1.5;

    sig = ((tone + resAir) * breath.sqrt) + mouth + edge;
    sig = LeakDC.ar(sig) * 0.3;
    sig = sig * env * amp;

    Out.ar(out, Pan2.ar(sig, pan));
}).add;
)

// the Hohle Fels pipe tone, as near as a 34 cm vulture radius gets
Synth(\windBone, [\freq, 505, \breath, 0.5, \dispersion, 0.5, \wall, 0.6]);

// Divje Babe, the cave bear femur. partly fossilised, so hard and bell-like
Synth(\windBone, [\freq, 587, \breath, 0.35, \wall, 0.95, \dispersion, 0.3,
    \noise, 0.18, \cutoff, 7000]);

// the same bone fresh, which Atema reports as dull and rough
Synth(\windBone, [\freq, 587, \breath, 0.35, \wall, 0.1, \dispersion, 0.55,
    \noise, 0.6, \cutoff, 1800]);

// a crane ulna, blown gently and then hard
Synth(\windBone, [\freq, 736, \breath, 0.2, \noise, 0.5]);
Synth(\windBone, [\freq, 736, \breath, 0.95, \noise, 0.5]);

// the three registers of one pipe: fundamental, octave, twelfth
(
[[505, 1], [1010, 2], [1515, 3]].do { |p, i|
    SystemClock.sched(i * 0.9, {
        Synth(\windBone, [\freq, p[0], \register, p[1], \breath, 0.45 + (i * 0.2),
            \rel, 0.4]);
        nil
    });
};
)

// what dispersion is doing. 0 is a drilled tube, 1 is a wing
(
[0, 0.35, 0.7, 1.0].do { |d, i|
    SystemClock.sched(i * 0.9, {
        Synth(\windBone, [\freq, 736, \dispersion, d, \breath, 0.8, \wall, 0.85,
            \rel, 0.5]);
        nil
    });
};
)


//-------------------------------------------------- \windBoneAir

// Atema's stage 1a, the found sound: a hollow bone with nothing done to it,
// blown across one open end. one pitch, bent by air speed and angle alone.

(
SynthDef(\windBoneAir, {
    |out = 0, pan = 0, amp = 0.3, gate = 1,
     freq = 520, breath = 0.5, bore = 0.55, edge = 0.5, wall = 0.6,
     hiss = 2000, bend = 0, slide = 0.08,
     wobble = 0.3, wobRate = 3.5, att = 0.12, rel = 0.4|

    var env = EnvGen.kr(Env.asr(att, 1, rel, \sin), gate, doneAction: 2);
    var f = Lag.kr(freq, slide) * bend.midiratio
        * (1 + (wobble * 0.01 * LFNoise2.kr(wobRate)));
    var decay = wall.linexp(0, 1, 0.02, 0.5);
    var air, pipe, sig;

    // the edge tone, riding up with air speed
    air = BPF.ar(WhiteNoise.ar(1), (f * edge.linexp(0, 1, 1.2, 4.5)).clip(80, 16000), 1.2) * 3;
    air = air + (HPF.ar(WhiteNoise.ar(1), hiss.clip(200, 15000)) * 0.35);
    air = air * breath * env;

    pipe = CombC.ar(air, 0.05, f.reciprocal.clip(0.00008, 0.05), decay);
    pipe = LPF.ar(pipe, bore.linexp(0, 1, 900, 11000));

    sig = (pipe * 0.8) + (air * 0.25);
    sig = LeakDC.ar(sig) * 0.4 * env * amp;

    Out.ar(out, Pan2.ar(sig, pan));
}).add;
)

Synth(\windBoneAir, [\freq, 505, \breath, 0.5, \wall, 0.7]);
Synth(\windBoneAir, [\freq, 780, \breath, 0.8, \edge, 0.8, \wall, 0.9, \bore, 0.8]);
Synth(\windBoneAir, [\freq, 300, \breath, 0.3, \edge, 0.2, \wall, 0.3, \bore, 0.2]);


//-------------------------------------------------- the measured flutes

// Zhang, Xiao & Lee 2004, Stroboconn readings off five Jiahu flutes.
// MIDI note plus the published cent deviation, so the numbers are the paper's.
// first entry of each is the pipe alone, every hole covered.

(
~jiahuM341a = [79-0.05, 82+0.10, 84+0.35, 85+0.17, 91+0.10, 96-0.07];
~jiahuM341b = [82+0.05, 84+0.08, 86+0.05, 89+0.09, 91+0.10, 94+0.05, 98+0.10];
~jiahuM282a = [78-0.10, 81-0.60, 83-0.82, 84-0.30, 86-0.30, 88-0.30, 90-0.30, 93-0.30];
~jiahuM282b = [78+0.05, 81-0.31, 81+0.35, 84+0.12, 86-0.32, 89+0.15, 90+0.50, 93-0.33];
~jiahuM253  = [78-0.05, 81-0.20, 82+0.35, 84-0.50, 85-0.32, 86-0.08, 87+0.38, 89+0.15, 91+0.15];

"M341:1  5 holes, c.7000 BC, four tones  : ".post; ~jiahuM341a.midicps.round(0.1).postln;
"M341:2  6 holes, complete pentatonic    : ".post; ~jiahuM341b.midicps.round(0.1).postln;
"M282:20 7 holes, 23.6 cm, keynote D6-30 : ".post; ~jiahuM282a.midicps.round(0.1).postln;
"M282:21 7 holes, 22.7 cm, its copy      : ".post; ~jiahuM282b.midicps.round(0.1).postln;
"M253:4  8 holes, full seven-tone        : ".post; ~jiahuM253.midicps.round(0.1).postln;
)


// every hole of M282:20, low to high, the way it was tested
(
Pdef(\holeByHole,
    Pbind(
        \instrument, \windBone,
        \midinote, Pseq(~jiahuM282a, 1),
        \dur, 0.8,
        \legato, 0.85,
        \breath, 0.5,
        \dispersion, 0.45,
        \wall, 0.7,
        \amp, 0.3
    )
).play;
)


// M253:4 is the eight-holed Phase 3 flute, the one that divides the octave
// into seven. the paper lists three keynotes it can be played from.
(
Pdef(\sevenTone,
    Pbind(
        \instrument, \windBone,
        \midinote, Pseq([
            Pseq(~jiahuM253[1..], 1),
            Pseq(~jiahuM253[1..].reverse, 1),
            Prand(~jiahuM253[1..], 12)
        ], inf),
        \dur, Pseq([0.3, 0.3, 0.6, 0.3, 0.3, 0.3, 0.9] * 0.7, inf),
        \legato, Pwhite(0.55, 0.95),
        \breath, Pwhite(0.35, 0.75),
        \noise, Pwhite(0.25, 0.5),
        \chiff, Pwhite(0.3, 0.8),
        \dispersion, 0.45,
        \wall, 0.7,
        \wobble, Pwhite(0.15, 0.4),
        \vib, Pwhite(0.0, 0.25),
        \amp, 0.28,
        \pan, Pwhite(-0.3, 0.3)
    )
).play(quant: 0);
)

Pdef(\sevenTone).stop;


// the pentatonic one, played quietly, which is where a narrow bore is sweetest
(
Pdef(\pentatonic,
    Pbind(
        \instrument, \windBone,
        \midinote, Prand(~jiahuM341b[1..], inf),
        \dur, Pseq([0.5, 0.25, 0.25, 0.5, 0.5, 1.0], inf),
        \legato, 0.9,
        \breath, Pwhite(0.2, 0.45),
        \noise, Pwhite(0.3, 0.55),
        \chiff, Pwhite(0.2, 0.5),
        \dispersion, 0.35,
        \wall, 0.85,
        \cutoff, 6000,
        \vib, Pwhite(0.05, 0.3),
        \att, Pwhite(0.04, 0.12),
        \rel, Pwhite(0.2, 0.5),
        \amp, 0.26
    )
).play(quant: 0);
)

Pdef(\pentatonic).stop;


// Atema's point, and the one most easily lost: these are not scale
// instruments. fingers slide, blowing bends, and the whole range is available
// as a glissando. the scale is where the holes are, not where the music is.
(
Pdef(\sliding,
    Pbind(
        \instrument, \windBone,
        \midinote, Prand(~jiahuM282a[1..], inf),
        \dur, Pexprand(0.4, 2.2),
        \legato, 1.0,
        \slide, Pexprand(0.08, 0.5),
        \bend, Pseq([
            Pseq([0], 3),
            Pwhite(-1.5, 1.5, 1)
        ], inf),
        \breath, Pwhite(0.3, 0.8),
        \noise, Pwhite(0.3, 0.6),
        \dispersion, 0.5,
        \wall, 0.75,
        \wobble, Pwhite(0.2, 0.6),
        \wobRate, Pexprand(2.0, 7.0),
        \att, Pwhite(0.08, 0.3),
        \rel, Pwhite(0.3, 0.8),
        \amp, 0.26,
        \pan, Pwhite(-0.4, 0.4)
    )
).play(quant: 0);
)

Pdef(\sliding).stop;


// overblowing. the pipe is M282:20's, the registers are 1 2 3 and the breath
// has to come up to hold each one
(
Pdef(\registers,
    Pbind(
        \instrument, \windBone,
        \register, Pdup(4, Pseq([1, 2, 3], inf)),
        \midinote, Pseq(~jiahuM282a[1..4], inf) + (Pkey(\register).log2 * 12),
        \breath, Pkey(\register).linlin(1, 3, 0.4, 0.95),
        \dur, 0.45,
        \legato, 0.9,
        \noise, 0.35,
        \dispersion, 0.5,
        \wall, 0.7,
        \amp, 0.25
    )
).play(quant: 0);
)

Pdef(\registers).stop;


// the hole-less bone underneath the fingered one
(
Pdef(\drone,
    Pbind(
        \instrument, \windBoneAir,
        \midinote, Prand([~jiahuM282a[0], ~jiahuM282a[0] - 12], inf),
        \dur, Pexprand(2, 7),
        \legato, 0.95,
        \breath, Pwhite(0.25, 0.55),
        \edge, Pwhite(0.2, 0.7),
        \bore, Pwhite(0.3, 0.7),
        \wall, Pwhite(0.5, 0.85),
        \bend, Pwhite(-0.6, 0.6),
        \slide, Pexprand(0.2, 1.5),
        \wobble, Pwhite(0.2, 0.6),
        \att, Pwhite(0.3, 1.2),
        \rel, Pwhite(0.5, 2.0),
        \amp, 0.18,
        \pan, Pwhite(-0.6, 0.6)
    )
).play(quant: 0);
)

Pdef(\drone).stop;


(
Pdef(\sliding).play(quant: 0);
Pdef(\drone).play(quant: 0);
)

(
Pdef(\sevenTone).stop;
Pdef(\pentatonic).stop;
Pdef(\sliding).stop;
Pdef(\registers).stop;
Pdef(\drone).stop;
)
