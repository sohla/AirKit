var m = ~model;
var synth;
var lastTime=0;
var notes = 10 + [0,8,3,9,5,6,14,2,8,4,9,11,3,6,2] * 3;
var roots = [0,0,9,8,8].dupEach(24);
// var notes = [0,1,4,5,7,8,11,12,14] + 24;
// var roots = [0].dupEach(18);
var currentNote = notes[0];
var currentRoot = roots[0];

m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.999;
m.rrateMassFilteredAttack = 0.2;
m.rrateMassFilteredDecay = 0.08;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;


SynthDef(\bambooComplex, {
    arg out=0, freq=440, pan=0, amp=0.1,
        att=0.001, rel=3.0,
        strikePos=0.1, // Position of strike (affects resonance)
        resonance=0.9, // Amount of resonant body sound
        bambooMoisture=0.1, // Affects damping and resonance
        model=0, // Model selecto
        width=0.8; // Stereo width

    var exciter, klank, env, noiseSig, bodyResonance;
    var freqs, amps, times;
    var output, numResonators=5;

    // Initial strike - gentler, more wooden
    noiseSig = Mix([
        // Main body of the strike
        HPF.ar(PinkNoise.ar, 1000) * 0.6,

        // Bamboo "hollow" characteristic
        BPF.ar(
            BrownNoise.ar,
            freq * [2.7, 4.2],
            0.1
        ).sum * 0.4,

        // High-end detail
        HPF.ar(WhiteNoise.ar, 8000) * 0.1
    ]);

    // Shape the strike
    exciter = noiseSig * Env.perc(att, 0.05).ar(0) * 0.5;

    // Model-specific tunings
    freqs = Select.kr(model, [
        // Original bamboo - emphasized hollow resonances
        freq * [1.0, 2.82, 4.97, 6.15, 8.92],
        // Large bamboo
        freq * [1.0, 2.31, 3.89, 5.12, 7.54],
        // Thin bamboo
        freq * [1.0, 3.12, 5.89, 7.93, 10.12],
        // Short bamboo
        freq * [1.0, 2.92, 4.23, 6.47, 9.32],
        // Wet bamboo (more pronounced lows)
        freq * [1.0, 2.15, 3.89, 5.64, 8.12],
        // Aged bamboo (sparse partials)
        freq * [1.0, 3.35, 5.67, 8.21, 11.42]
    ]);

    // Damping based on bamboo moisture
    times = Array.fill(numResonators, { |i|
        // Lower frequencies decay slower
        var baseTime = (numResonators - i) * 0.8;
        baseTime * bambooMoisture.linlin(0, 1, 0.5, 2.0)
    });

    // Amplitude relationships with strike position influence
    amps = Array.fill(numResonators, { |i|
        var baseAmp = (numResonators - i) / numResonators;
        baseAmp * (1 - (strikePos * (i / numResonators)))
    });

    // Main resonant body - dual Klank for stereo
    bodyResonance = Array.fill(2, {
        Klank.ar(
            `[
                freqs * LFNoise1.kr(0.1!numResonators).range(0.999, 1.001),
                amps,
                times
            ],
            exciter,
            freqscale: 1,
            decayscale: resonance
        )
    });

    // Additional tube resonance
   bodyResonance = bodyResonance + (
        DynKlank.ar(
            `[
                freqs * [1, 1.01],  // Slight detuning
                amps * 0.1,
                times * 1.2
            ],
            exciter * 0.3
        ).dup
    );
    // Overall envelope
    env = EnvGen.kr(
        Env.perc(
            att,
            rel * bambooMoisture.linlin(0, 1, 0.8, 2.2),
            curve: -4
        ),
        doneAction: 2
    );

    // Mix and position in stereo field
    output = Mix([
        Pan2.ar(bodyResonance[0], pan - (width/2)),
        Pan2.ar(bodyResonance[1], pan + (width/2))
    ]);

    // Final shaping
    output = LPF.ar(output, 12000); // Remove any harsh highs
    output = output * env * amp;
    output = LeakDC.ar(output);
    output = Limiter.ar(output, 0.95);

    Out.ar(out, Pan2.ar(Mix(output), -1));
}).add;


~init = ~init <> {

	//------------------------------------------------------------
	// visual : a triangle whose three edges break apart and tumble.
	// Registered here so it reloads with the file (loadPersonality
	// calls clearVdefs before re-interpreting us).
	//
	// Needs a vdef rather than shape: \triangle because the library
	// triangle is one connected outline - it has no notion of the edges
	// separating from each other.
	~vdef.(\shards, { |ev, c|
		var mod = ev[\modulation] ? ();
		var spread = mod[\spread] ? 1.2;
		var spin = mod[\spin] ? 1.0;
		var seed = mod[\phase] ? 0;
		var sz = c[\size];
		var pos = c[\pos];
		var brk = c[\normTime].pow(mod[\hold] ? 2);
		var verts = Array.fill(3, { |i|
			pos + Polar(sz, (i / 3 * 2pi) - 0.5pi).asPoint
		});

		3.do { |i|
			var pa = verts[i];
			var pb = verts[(i + 1) % 3];
			var mid = (pa + pb) / 2;
			var drift = (mid - pos) * (brk * spread);
			var ang = brk * spin * (1 + sin(seed + i)) * if(i.even, 1, -1);
			var swing = { |p|
				var v = p - mid;
				mid + Polar(v.rho, v.theta + ang).asPoint + drift
			};
			c[\render].([ swing.(pa), swing.(pb) ], 1, 1, false);
		};
	});
};

~deinit = ~deinit <> {
};

//------------------------------------------------------------
~onEvent = {|e|
	m.com.root = e.root;
	m.com.dur = e.dur;
};
//------------------------------------------------------------
~next = {|d|

	var move = m.accelMassFiltered.linlin(0,1,0,1);
	var att = m.accelMassFiltered.lincurve(0,2.5,0.1,0.001,-8);
	var amp = m.accelMassFiltered.linexp(0,2.5,0.08,1);
	var noteIndex = m.accelMassFiltered.linlin(0,2,0.0001,notes.size).floor;
	var space = m.accelMassFiltered.lincurve(0,0.3,0.25,0.02,-1);
	if(noteIndex>=notes.size,{noteIndex=notes.size-1});
	if(move > 0.02, {
		if(TempoClock.beats > (lastTime + space),{
			lastTime = TempoClock.beats;
			notes = notes.rotate(-1);
			currentNote = notes[0];
			roots = roots.rotate(-1);
			currentRoot = roots[0];
			m.com.root = currentRoot;
			synth = Synth(\bambooComplex, [
				\freq, (2 + notes[noteIndex] + currentRoot).midicps,
				\gate, 1,
				\att, 0.03,
				\amp, 0.3 * amp,
				\strikePos, 1.0.rand, // Position of strike (affects resonance)
				\resonance, 0.06, // Amount of resonant body sound
				\bambooMoisture, 1.0.rand, // Affects damping and resonance
				\model, 6.rand.floor, // Model selecto

			]);
			synth.server.sendBundle(0.1,[\n_set, synth.nodeID, \gate, 0]);

			// visual : one triangle per strike, breaks apart as it rises.
			// amp:0 / dur - the event type re-types this to \note and plays
			// it, so the silent \default note is kept short. The bell above
			// is the sound.
			(
				type: \customVisualEvent,
				amp: 0,
				dur: 0.05,
				viewID: d.port,
				shape: \shards,
				startSize: 200 + (50 * amp),
				endSize: 10 + (50 * amp),
				startWidth: 3.3,
				endWidth: 30.3,
				sx: rrand(-0.02, 0.02),
				sy: 0+ rrand(-0.01,0.01),
				ex: rrand(-0.1, 0.1),
				ey: rrand(-0.1, 0.1),
				yEnv: Env([0, 1], [1], \sin),
				startColor: Color.hsv(noteIndex / notes.size, 0.45, 1.0, 1),
				endColor: Color.hsv(noteIndex / notes.size, 0.9, 0.6, 0.0),
				rotation: 2pi.rand,
				duration: m.accelMassFiltered.lincurve(0,2.5,1.0,3,-1),
				modulation: (
					spread: rrand(7.0, 7.0) * 0.3,
					spin: rrand(0.5, 2.0) * 0.1,
					phase: 2pi.rand,
					amp: 0,
					hold: 0.3
				)
			).play;
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	[m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
