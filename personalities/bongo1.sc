var m = ~model;
var synth;

// Mirrors the DynKlank in \bongo1 below : four stretched membrane modes,
// their relative amplitudes, and their relative decay times.
var modeRatios = [1, 1.7, 2.3, 2.9];
var modeAmps   = [1, 0.6, 0.3, 0.2];
var modeDecays = [0.3, 0.2, 0.15, 0.1];
// position around the cycle ring. State, not a tunable - the cycle LENGTH
// is set on the event, next to everything else.
var step = 0;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.99;


//------------------------------------------------------------
SynthDef(\bongo1, {
    arg out=0, freq=200, amp=0.5, pan=0,
    tension=0.7,  // affects brightness/pitch bend
    damp=0.3,    // affects decay time
    pos=0.3;     // affects harmonic content

    var exciter, membrane, env, earlyRefs, sound;

    // Exciter (strike impulse with noise)
    exciter = Impulse.ar(0) * PinkNoise.ar(1);

    // Membrane simulation using resonant filters
    membrane = DynKlank.ar(
        `[
            // Frequencies are harmonically related but stretched
            [freq, freq*1.7, freq*2.3, freq*2.9],
            // Amplitudes decrease for higher modes
            [1, 0.6, 0.3, 0.2] * pos.squared,
            // Decay times shorter for higher modes
            [damp*0.3, damp*0.2, damp*0.15, damp*0.1]
        ],
        exciter
    );

    // Add initial pitch bend for attack
    membrane = membrane + (
        SinOsc.ar(freq * Line.kr(1.5, 1, 0.02))
        * EnvGen.kr(Env.perc(0.001, 0.09))
        * tension
    );

    // Basic envelope
    env = EnvGen.kr(Env.perc(0.001, damp ));

    // Body filter for overall tone
    sound = BPF.ar(
        membrane,
        freq * [1, 2.1],
        0.3
    ).sum;

    // Early reflections
    earlyRefs = DelayN.ar(
        sound,
        0.02,
        [0.01, 0.012, 0.013]
    ).sum * 0.3;


    // Room reverb
    sound = FreeVerb.ar(
        sound + earlyRefs,
        mix: 0.2,
        room: 0.5,
        damp: 0.4
    );

    sound = Pan2.ar(sound * env * amp, pan);
    DetectSilence.ar(sound, doneAction: Done.freeSelf);
    Out.ar(out, sound);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	//------------------------------------------------------------
	// visual : a cyclic score. Hits are laid round a ring by their position
	// in a 128 step cycle, which runs long against the 14 step \dur pattern
	// so the rhythm precesses round the ring rather than stamping the same
	// figure each lap. Rests draw nothing, so the gaps are real.
	//
	// Lineage: the cyclic notations in the atlas (gong-point rings, nested
	// cycles) crossed with Chladni/cymatic plate figures for the mark
	// itself. Atlas grammars G8 / G7.
	//
	//   cycle    -> angle round the ring       (\cyc -> \rotation)
	//   pitch    -> radius                     (\rad -> \startSize)
	//   amp      -> head size                  (\startSize)
	//   damp     -> how long the mark lives    (\duration)
	//   tension  -> attack overshoot           (\modulation)
	//
	// A draw func, because concentric rings are several subpaths. It knows
	// no geometry of its own : the event is anchored at the canvas centre
	// (\sx \sy default 0), \rotation carries the angle, and \startSize is
	// the ring radius in pixels. The draw func steps out along +x and asks
	// c[\draw] for library shapes, so it never touches Pen.
	~vdef.(\membrane, { |ev, c|
		var mod = ev[\modulation] ? ();
		var rest = mod[\rest] ? false;
		var chirpAmt = mod[\chirp] ? 0.5;
		var chirpTime = mod[\chirpTime] ? 0.04;
		var arcAlpha = mod[\arcAlpha] ? 0.22;
		var head = mod[\head] ? 30;
		var t = c[\normTime];
		var mid = c[\pos];
		var ring = c[\size];
		var tension = (mod[\tension] ? 0.5).clip(0, 1);
		var chirp = 1 + (chirpAmt * tension * exp(t.neg / chirpTime));
		var pos = mid + (ring @ 0);

		if(rest.not, {

			c[\draw].(\arc, (pos: mid, size: ring), 1, arcAlpha, false);

			modeRatios.do { |ratio, i|
				var a = modeAmps[i] * exp(t.neg / modeDecays[i]);
				var r = head / ratio * chirp;
				if(a > 0.01, {
					c[\draw].(\circle, (pos: pos, size: r), a, a);
				});
			};
		});
		nil
	});

	Pdef(m.ptn,
		Pbind(
		    \instrument, \bongo1,
			\dur, Pseq([Pseq([Rest(0.25), 0.25], 5) ,0.6666,0.6666,0.666, 0.25]* 0.5 , inf),
			\octave, Pseq([2,3,4] + 2, inf),
		    \note, Pseq([0,5,9,2,7,4,3], inf),
			// \amp, Pseq([0.9, 0.6, 0.8], inf),
		    \tension, Pwhite(0.001, 1),
			// \damp, 4,//Pwhite(0.5,2),
            // \dur, 0.125 * 0.5,
		    \pos, 0.4,//Pwhite(0.1, 0.9),
		    \pan, Pwhite(-0.3, 0.3),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],

			\type, \customVisualEvent,
			\shape, \membrane,
			\cyc, Pfunc({
				var s = 128;
				var ph = (step % s) / s;
				step = step + 1;
				ph
			}),
			\rad, Pfunc({ |e|
				((e[\note] ? 0) + ((e[\octave] ? 5) * 12)).linlin(48, 81, 0.34, 0.78)
			}),
			\rotation, Pfunc({ |e| (e[\cyc] * 2pi) - 0.5pi }),
			\startSize, Pfunc({ |e| e[\rad] * 400 }),
			\endSize, Pkey(\startSize),
			\startWidth, 23,
			\endWidth, 0.8,
			\startColor, Color.new(1.0, 0.42, 0.12, 0.95),
			\endColor, Color.new(1.0, 0.42, 0.12, 0.0),
			\duration, Pfunc({ |e|
				(e[\damp] ? 1).clip(0.001, 14).explin(0.001, 14, 0.3, 3.2)
			}),
			\modulation, Pfunc({ |e| (
				rest: e.isRest,
				tension: e[\tension] ? 0.5,
				chirp: 0.5,
				chirpTime: 0.04,
				arcSpan: 0.65,
				amp: 0,
				arcAlpha: 0.22,
				head: (e[\amp] ? 1).clip(0.02, 3).linexp(0.02, 3, 12, 70)
			) })
		);
	);

	Pdef(m.ptn).play(quant:0.125);
    Pdef(m.ptn).pause;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
~onEvent = {|e|
	if(e.root != m.com.root,{
		// "key change".postln;
		Pdef(m.ptn).reset;
	});
	Pdef(m.ptn).set(\root, m.com.root);
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity;
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.accelMassFiltered.lincurve(0, 2 * sens,0.02,2,3);
	var damp = m.accelMassFiltered.lincurve(0, 2 * sens,0.001,14,5);
	// tells the visual router which device these shapes came from
	Pdef(m.ptn).set(\viewID, d.port);

	Pdef(m.ptn).set(\amp, amp * vol);
	Pdef(m.ptn).set(\damp, damp);

	if(m.accelMassFiltered > 0.004,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.125);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
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




