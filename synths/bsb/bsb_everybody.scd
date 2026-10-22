// Backstreet Boys - Everybody, driven from MIDI : drums -> samples, bass -> miniMoog, pad -> warmPad
// 1. run the setup block   2. ~bsb.run(\A)   3. ~bsb.off()

//------------------------------------------------------------ setup
(
~bsb !? { ~bsb.off };
~bsb = ();
~bsb.dir = thisProcess.nowExecutingPath.dirname +/+ "midi";
~bsb.drumDir = "~/Downloads/yourDNASamples/drums".standardizePath;
~bsb.extraSamples = [
	"~/Develop/SuperCollider/Projects/supersonic/supersonic/samples/drum_cowbell.flac",
	"~/Develop/SuperCollider/Projects/supersonic/supersonic/samples/elec_triangle.flac"
].collect(_.standardizePath);

// MIDI channels are 1-16. A and K are the same arrangement (multi-track vs single-track export).
~bsb.versions = (
	A: (file: "everybody_backstreets_back.mid", drums: 10, moog: 2, chords: 9),   // melody is ch 4
	K: (file: "bsb_everybody_K.mid",            drums: 10, moog: 2, chords: 9),   // melody is ch 4
	B: (file: "bsb_everybody.mid",              drums: 10, moog: 3, chords: 2)    // melody is ch 8
);

// GM drum note -> [sample, rate, amp]. Anything not listed is skipped and posted at load.
~bsb.drumMap = (
	36: [\BD02X07, 1, 1],
	38: [\SD02X05, 1, 0.9],
	40: [\SD02X05, 1, 0.9],
	39: [\SD02X15, 1.1, 0.7],
	42: [\HH11X01, 1, 0.5],
	44: [\HH11X01, 0.9, 0.35],
	46: [\HH11X09, 1, 0.5],
	54: [\HH11X05, 1.5, 0.3],
	49: [\HH11X09, 0.6, 0.6],
	57: [\HH11X09, 0.55, 0.6],
	41: [\TTLO02X07, 1, 0.8],
	43: [\TTLO02X12, 1, 0.8],
	45: [\TTMI02X18, 1, 0.8],
	47: [\TTMI02X23, 1, 0.8],
	48: [\TTHI02X07, 1, 0.8],
	50: [\TTHI02X15, 1, 0.8],
	56: [\drum_cowbell, 1, 0.4],
	80: [\elec_triangle, 1, 0.3]
);

~bsb.gain = (drums: 1, moog: 1, chords: 1);
~bsb.speed = 1;

//------------------------------------------------------------ synths
SynthDef(\miniMoog, {
	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sus = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0|

    var env, osc, filt, sig;
    env = EnvGen.kr(Env.adsr(attack, decay, sus, release), gate, doneAction: 2);
	osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 1.005] * 1,0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
    filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
    sig = filt * env * amp * 0.5;
    sig = Pan2.ar(sig, pan);
    Out.ar(0, sig.tanh);
}).add;

SynthDef(\drumkit, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=0.3, gate=1,cutoff=10, rq=1|

	var lr = rate * BufRateScale.kr(bufnum);// * (freq/440.0);
	var cd = BufDur.kr(bufnum);
  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);

	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.0], startPos: start * BufFrames.kr(bufnum), loop: 0) ;
    sig = RHPF.ar(sig, cutoff, rq);
		sig = Compander.ar(sig, sig,
        thresh: -15.dbamp,
        slopeBelow: 1,
        slopeAbove: 0.5,
        clampTime:  0.01,
        relaxTime:  0.01
		) ;
		sig = Mix.ar([sig]);
    sig = Balance2.ar(sig[0],sig[1], pan);
    Out.ar(out, sig * amp * env);
}).add;

SynthDef(\warmPad, {
    |out=0, gate=1, freq=440, amp=0.3,
    atk=0.03, dec=0.2, sus=0.8, rel=1.0,
    filtMin=500, filtMax=5000, filtSpeed=0.5,
    detuneAmount = 0.001,chorusRate=0.5, chorusDepth=0.01,
	pan=0, spread=0.2|

    var sig, env, filt, chorus, numVoices=4, sub;

    env = EnvGen.kr(
        Env.adsr(atk, dec, sus, rel),
        gate,
        doneAction: 2
    );

    sig = Array.fill(numVoices, { |i|
        var detune = i * detuneAmount;
        var oscillator = SinOsc.ar(freq * (1 + detune)) +
                        Saw.ar(freq * (1 + detune), pi/2 * i) * 0.3;
        Pan2.ar(oscillator, pan + detune)
    }).sum;

	filt = SinOsc.kr(filtSpeed).range(filtMin, filtMax);
	sig = RLPF.ar(sig, filt, 0.5);

    chorus = Array.fill(2, {
        var maxDelay = 0.05;
        var delayTime = SinOsc.kr(
            chorusRate + rand(0.1),
            rrand(0, 2pi)
        ).range(0, chorusDepth);

        AllpassC.ar(
            sig,
            maxDelay,
            delayTime + (chorusDepth * 0.1),
            0.1
        )
    });
	sub = LFTri.ar(freq, pi, 0.2).tanh;
	sig = Mix([sig, chorus.sum]) / (numVoices + 2);
    sig = sig * env * amp;

    sig = Splay.ar(sig, spread);

	Out.ar(out, sig + (sub * env * amp));
}).add;

//------------------------------------------------------------ midi -> notes
~bsb.notes = { |self, mf, ch|
	mf.noteSustainEvents(ch - 1)
		.sort({ |a, b| a[1] < b[1] })
		.collect({ |e| (time: e[1], note: e[4], velo: e[5], len: e[6]) })
};

~bsb.layer = { |self, notes, keys|
	var times = notes.collect(_.time);
	var deltas = times.differentiate.drop(1) ++ [notes.last.len];
	[times.first, Pbind(*([
		\dur, Pseq(deltas),
		\midinote, Pseq(notes.collect(_.note)),
		\velo, Pseq(notes.collect(_.velo)),
		\len, Pseq(notes.collect(_.len))
	] ++ keys))]
};

~bsb.build = { |self, name|
	var v = ~bsb.versions[name];
	var mf = SimpleMIDIFile.read(~bsb.dir +/+ v.file).timeMode_(\seconds);
	var drums = ~bsb.notes(mf, v.drums);
	var skipped = drums.reject({ |n| ~bsb.drumMap[n.note].notNil }).collect(_.note).asBag;
	var hits = drums.select({ |n| ~bsb.drumMap[n.note].notNil });
	var cells = hits.collect({ |n| ~bsb.drumMap[n.note] });
	var layers, lead;

	postf("% : % drum hits, % bass notes, % chord notes\n", v.file, hits.size, ~bsb.notes(mf, v.moog).size, ~bsb.notes(mf, v.chords).size);
	skipped.contents.keys.asArray.sort.do({ |k| postf("  unmapped drum note % x%\n", k, skipped.occurrencesOf(k)) });

	layers = (
		~bsb.layer(hits, [
			\instrument, \drumkit,
			\bufnum, Pseq(cells.collect({ |c| ~bsb.buffers[c[0]] })),
			\rate, Pseq(cells.collect(_[1])),
			\cellAmp, Pseq(cells.collect(_[2])),
			\amp, Pfunc({ |e| e.cellAmp * (e.velo / 127) * ~bsb.gain.drums }),
			\sustain, 0.8,
			\attack, 0.001,
			\release, 0.3
		])
		++ ~bsb.layer(~bsb.notes(mf, v.moog), [
			\instrument, \miniMoog,
			\amp, Pfunc({ |e| (e.velo / 127) * ~bsb.gain.moog }),
			\sustain, Pkey(\len),
			\attack, 0.005,
			\decay, 0.15,
			\release, 0.15,
			\filterFreq, 1500,
			\fq, 0.3
		])
		++ ~bsb.layer(~bsb.notes(mf, v.chords), [
			\instrument, \warmPad,
			\amp, Pfunc({ |e| (e.velo / 127) * 0.4 * ~bsb.gain.chords }),
			\sustain, Pkey(\len),
			\atk, 0.03,
			\dec, 0.3,
			\sus, 0.7,
			\rel, 0.8,
			\filtMin, 600,
			\filtMax, 3000,
			\filtSpeed, 0.1
		])
	).clump(2);
	lead = layers.collect(_[0]).minItem;

	Ptpar(layers.collect({ |l| [l[0] - lead, l[1]] }).flatten(1))
};

~bsb.run = { |self, name|
	~bsb.off();
	~bsb.clock = TempoClock(~bsb.speed);
	~bsb.player = ~bsb.build(name).play(~bsb.clock);
};

~bsb.off = { |self|
	~bsb.player !? { ~bsb.player.stop };
	~bsb.clock !? { ~bsb.clock.stop };
};

s.waitForBoot({
	~bsb.buffers = ();
	(PathName(~bsb.drumDir).files ++ ~bsb.extraSamples.collect(PathName(_))).do({ |p|
		~bsb.buffers[p.fileNameWithoutExtension.asSymbol] = Buffer.read(s, p.fullPath);
	});
	s.sync;
	postf("bsb ready : % drum samples\n", ~bsb.buffers.size);
});
)

//------------------------------------------------------------ play
~bsb.run(\A);
~bsb.run(\K);
~bsb.run(\B);
~bsb.off();

~bsb.gain.drums = 0;
~bsb.gain.moog = 0;
~bsb.gain.chords = 0;
~bsb.gain = (drums: 1, moog: 1, chords: 1);

~bsb.speed = 0.9; ~bsb.clock !? { ~bsb.clock.tempo = ~bsb.speed };
