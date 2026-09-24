var m = ~model;
var dur = 0.22/2;

// SHARED ACROSS THE QUARTET. trainBass2 writes m.com.root; this voice
// reads it, so a harmony change turns every hue in the ensemble together.
var rootHue = { (m.com.root ? 0).linlin(-2, 3, -0.06, 0.06) };

// Nothing sweeps any more — bearing is read off the sounding pitch.

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\funMelody, {
    |out=0, freq = 440, gate = 1, amp = 0.8, filtFreq = 2000, filtRes = 0.3, envAtk = 0.01, envDec = 0.1, envSus = 0.7, envRel = 0.2, pan = 0.0|
    var osc1, osc2, osc3, env, filter, output;
    env = EnvGen.ar(Env.adsr(envAtk, envDec, envSus, envRel), gate, doneAction: Done.freeSelf);
    osc1 = Saw.ar(freq, 1.0);
    osc2 = Pulse.ar(freq * 0.99, 0.5, 0.5);
    osc3 = SinOsc.ar(freq * 1.01, 0, 1.0);
    output = Mix([osc1, osc2, osc3]) * env * amp;
    filter = RLPF.ar(output, filtFreq, filtRes).tanh;
		// filter = ([filter, DelayN.ar(filter, 0.5, 0.5)+filter] * 2).tanh;

    Out.ar(out, Pan2.ar(filter,pan));
}).add;


//------------------------------------------------------------
~init = ~init <> {

	// visual : the near lights, the ones that arrive quickest.
	//
	// Same tunnel as trainBass2 and the same vanishing point at the canvas
	// centre — this voice just runs the near lane. The arpeggio is a 5
	// note loop, so a mark is mounted at one of five angles round the
	// tunnel and flies out along it, growing through a +3 curve so it
	// creeps while distant and rushes as it passes.
	//
	// The concentric relationship survives the change of viewpoint: the
	// bass turns its ring once per root hold while this turns five times
	// faster and travels quicker, so the melody still visibly laps the
	// harmony — but now as two rates of traffic down one tunnel rather
	// than two rings on a disc.
	//
	// Lineage: cyclic/radial notation, atlas grammar G8, read down the
	// axis. Concentric rates are the orrery verb from the same section.
	// Palette from atlas §0.5, saturated primaries on black.
	//
	// The mark is a small arc lying ON the ring, tangent to it, not a
	// blob sitting at a point on it.
	//
	// Bearing is the SOUNDING PITCH, read as a clock face. Screen y runs
	// DOWNWARD, so 12 o'clock is -pi/2 and 6 o'clock is +pi/2, and midi
	// 36..96 maps across that half circle — high at the top, low at the
	// bottom.
	//
	// It has to be the midi note, not \note. The pitch here is
	// note + root + 12*octave, and \octave is live off gyro y (6..3) while
	// \root moves with the harmony. \note alone is only the innermost of
	// those three, so using it threw away the whole vertical gesture and
	// pinned the fan in place. Now the 5 note figure sits as a compact fan
	// about an hour and a half wide, and tilting slides that whole fan
	// round the clock — a bar and a half of travel from octave 3 to
	// octave 6. The harmony nudges it too, since root is in the sum.
	//
	// Recomputed here rather than read from \midinote: that key is a
	// Function in the parent event and is not resolved until the event
	// plays, so at Pbind-evaluation time it is not a number yet.
	//
	// Which HALF of the clock is gyro x. The transform is a reflection
	// through the vertical axis, pi - a, and that is the only one that
	// keeps a note at the same HEIGHT on both sides — a rotation would
	// send the high note to 6 o'clock. So tilting throws the whole fan
	// left or right while 12 stays 12 and 6 stays 6.
	//
	// The flip is on the sign of m.gyroXFiltered, so it is a switch, not
	// a slide. That is deliberate: any continuous version has to pass
	// through the vertical axis, where every note would collapse onto 12
	// and 6 and the pitch spread would vanish. Because an arc keeps the
	// bearing it was fired with, crossing over migrates the fan across as
	// old arcs fade rather than snapping the picture.
	//
	// filtFreq sets the arc's ANGULAR span, so opening the filter closes
	// the ring up: narrow arcs read as separate dashes, wide ones fuse
	// into a continuous band. That is the filter doing something
	// structural to the drawing rather than just resizing a dot.
	//
	//   midi note  -> bearing, high at 12 o'clock, low at 6 (\rotation)
	//                 = \note + \root + 12*\octave, so gyro y (octave) and
	//                   the harmony (root) both move the fan
	//   gyro x     -> which half of the clock, by reflection
	//                 (currently commented out in the Pfunc)
	//   distance   -> \startSize -> \endSize through a +3 curve
	//   filtFreq   -> angular span of the arc  (\modulation span)
	//   envRel     -> how long it stays in the tunnel, clipped so the
	//                 perspective stays coherent
	//   m.com.root -> hue, shared with all four voices
	//
	// NB \closed must be false on the event — a points func gets the
	// event's \closed, which defaults to true, and that would draw a
	// chord straight back across the arc.
	~vdef.(\arpMark, { |ev, c|
		var mod  = ev[\modulation] ? ();
		var span = mod[\span] ? 0.3;
		var n    = ev[\numPoints] ? 16;
		Array.fill(n, { |i|
			c[\pos] + Polar(c[\size], ((i / (n - 1)) - 0.5) * span).asPoint
		})
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \funMelody,
			\note, Pseq([12,14,10,7,0]-1, inf),
			\envAtk, Pwhite(0.002,0.04, inf),
			// \envDec, Pwhite(0.2, 0.1, inf),
			// \root, Pseq([0].stutter(16), inf),
			\envSus, 0.0,
			\pan, Pseq([-0.3,0.3], inf),
    		// \filtRes, 0.9,

			\type, \customVisualEvent,
			\shape, \arpMark,
			\numPoints, 16,
			\closed, false,
			\sx, Pwhite(-0.0, 0.0, inf),
			\ex, Pkey(\sx),
			\sy, Pwhite(-0.0, 0.0, inf),
			\ey, Pkey(\sy),
			\rotation, Pfunc({ |e|
				var midi = (e[\note] ? 6) + (e[\root] ? 0) + (12 * (e[\octave] ? 5));
				var a = midi.linlin(36, 96, 0.5pi, -0.5pi);
				if(m.gyroXFiltered < 0, { pi - a }, { a })
				// a - pi.half
			}),
			\startSize, Pfunc({ |e| (e[\amp] ? 0.2).linlin(0, 1.0, 0, 40) }) * 5,
			\endSize, Pkey(\startSize) * 10,//250,//Pfunc({ |e| (e[\octave] ? 5).linlin(3, 6, 920, 640) }),
			\sizeEnv, Pfunc({ Env([0, 1], [1], 3) }),
			\startWidth, 2,
			\endWidth, 220,//Pfunc({ |e| (e[\amp] ? 0.2).linlin(0, 0.8, 12, 26) }),
			\widthEnv, Pfunc({ Env([0, 1], [1], 3) }),
			\startColor, Pfunc({ |e| Color.hsv((0.13 + rootHue.()).wrap(0, 1), 1.0, 0.72, 0.95) }),
			\endColor, Pfunc({ |e| Color.hsv((0.13 + rootHue.()).wrap(0, 1), 0.72, 0.2, 0.35) }),
			\colorEnv, Pfunc({ Env([0, 1], [1], 3) }),
			\duration, Pfunc({ |e| (e[\envRel] ? 1.2).clip(0.8, 1.8) }),
			\modulation, Pfunc({ |e|
				(amp: 0, span: (e[\filtFreq] ? 2000).explin(50, 14000, 0.3, 0.5))
			}),

			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:dur);
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

};

//------------------------------------------------------------

~onEvent = {|e|
	Pdef(m.ptn).set(\root, m.com.root+5);
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var oct = m.gyroYFiltered.linlin(-1,1,6,3).floor;
  	var envRel = m.accelMassFiltered.lincurve(0, 2 * sens,0.1,2.6,2);
	var envDec = m.accelMassFiltered.lincurve(0, 2 * sens,0.05,0.2,-2);

	var amp = m.accelMassFiltered.lincurve(0, 5 * sens,0.001,0.1,-2);
	var ff = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).linexp(-1,1,50,14000);
	var rf = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).linexp(-1,1,0.9,0.2);

	if(amp<0.02,{amp=0.0;});
	
	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\filtFreq, ff);
	Pdef(m.ptn).set(\filtRes, rf);
	Pdef(m.ptn).set(\amp, amp * 2 * vol);
	Pdef(m.ptn).set(\octave,oct);
	Pdef(m.ptn).set(\envRel,envRel);
	Pdef(m.ptn).set(\envDec,envDec);

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	// [m.accelMass * 0.1, m.accelMassFiltered * 0.1];
	// [((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2) ];
	[m.gyroYFiltered.lincurve(-1.0,1.0,0.0,1.0,-2)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

