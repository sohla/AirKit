var m = ~model;
var dur = 0.22/2;

// SHARED ACROSS THE QUARTET. trainBass2 writes m.com.root; this voice
// reads it, so a harmony change turns every hue in the ensemble together.
// This voice is held near-white, so the root only tints it.
var rootHue = { (m.com.root ? 0).linlin(-2, 3, -0.06, 0.06) };

// the note loop is 5 long, and that length is the additive cell.
var cell = 5;

//------------------------------------------------------------

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.99;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\chooka, {
    |out=0, freq = 440, gate = 1, amp = 0.8, filtFreq = 2000, filtRes = 0.5, envAtk = 0.01, envDec = 0.1, envSus = 0.7, envRel = 0.2, 
	pan = 0.0, subFreq = 90, subDecay = 0.2, trig = 1, seq = 1|

    var env = EnvGen.ar(Env.adsr(envAtk, envDec, envSus, envRel), gate, doneAction: Done.freeSelf);
    var osc1 = WhiteNoise.ar(0.1);
    var osc2 = BrownNoise.ar(0.2);
	var kick = SinOsc.ar(XLine.kr(subFreq*2, subFreq, 0.3)) *
           EnvGen.ar(Env.perc(0.01, subDecay), gate) * seq;
    var osc3 = Pulse.ar(freq * 0.25, LFCub.ar(10,0,1,1), 0.5) * 0.8;
    var output = Mix([osc1, osc2, kick]) * env * amp;
    var filter = RLPF.ar(output, filtFreq, filtRes);
    Out.ar(out, Pan2.ar(filter,pan));
}).add;

//------------------------------------------------------------
// visual : the additive cell. Nothing here depicts steam — the pulse is
// drawn as process, not as weather.
//
// The note loop is 5 long, so each pass lays five hard-edged bars, all
// pivoting off one fixed left margin, each one unit longer than the last:
// 1 2 3 4 5, then back to 1. That is the figure Glass builds Einstein
// out of, and it was already in this file as Pseq([12,14,10,7,0]-1) —
// the bar length simply counts the position in that cell. The fifth bar
// fills solid, so the cycle closes with an accent and the traversal is
// legible (atlas §3.4) without a cursor.
//
// The strangeness is that the rank is not level and never settles.
// Every completed cell adds 0.045 rad, so the whole fan pivots about its
// margin at about one turn every 77 seconds — far too slow to watch,
// impossible to miss after a minute. Rigid geometry quietly off-axis.
// Rotation is derived from the step count, so it is exact and needs no
// stored state.
//
// Lineage: block / register-band, atlas grammar G3-G4 — flat rectangles,
// bandwidth as height, no modelling, no taper, hard edges. Wilson's light
// bars by way of the atlas's data / test-pattern palette (§0.5): white on
// black, one accent, no gradient, modulation amp pinned to 0 so nothing
// wobbles.
//
// REDONE. This voice fires about nine times a second, so anything that
// is large or bright by default becomes a wall — the bars did. The rule
// here now is that the mark is TINY unless the player does something,
// and the only two things that make it grow are the two gestures ~next
// actually reads: accel and the y axis.
//
// So: circles, all the same size, streaming out through the tunnel. The
// ONE thing that varies between them is how white each one is. At rest a
// dot is dark grey and the screen reads as fine drifting texture; on an
// accel accent it comes in white. accel drives amp on a -1 curve in
// ~next, which rises fast off zero, so the accents are already sharp
// before the visual sees them.
//
// Size deliberately says nothing now. When both size and brightness
// carried the accent the two channels doubled up and every hit became a
// big bright shape — which is what made this voice a wall. One variable,
// one meaning: a field of identical circles, some of them lit.
//
// Whiteness sits in \startColor, not \endColor, because \colorEnv runs a
// +3 curve — a dot spends most of its life near its start colour and only
// blends to the end as it leaves. So it is born at its accent brightness
// and fades out; the accent is legible the moment it appears rather than
// arriving just as the dot goes transparent.
//
// The y axis gets two cues, because it does two things to the sound.
// It sets \octave (8..4), which here lifts or drops the point the whole
// stream pours out of — tilt and the tunnel tips. It also sets filtFreq
// (80..14000), which here sets how FAR each speck travels, so opening
// the filter throws the grain further and faster past you. Tilting
// therefore moves the source and changes the speed at once, which is
// what it does to the noise.
//
// The note picks one of five directions round the tunnel, jittered so
// the streams read as spread rather than as five spokes — the loop is
// still in there, it is just texture now, not architecture.
//
// Lineage: field / constellation, atlas grammar G1 — accreting, drifting
// grain, meaning carried by density rather than by any one mark — set in
// the shared radial tunnel. Palette still the atlas §0.5 data / test
// pattern row: near-white on black, brightness doing the work, no hue
// drama, modulation amp pinned to 0 so nothing wobbles.
//
//   accel      -> how WHITE the circle is      (the accent, and only this)
//   y axis     -> origin height (\octave) and travel distance (filtFreq)
//   note       -> which of five directions, jittered
//   distance   -> \startSize -> \endSize through a +3 curve
//   silence    -> size 0, nothing drawn
~init = ~init <> {

	// a dot: a small circle riding at the rim. Its radius is a RATIO of
	// how far out it has travelled, so it grows with perspective like
	// everything else in the tunnel — but the ratio is now a CONSTANT, so
	// every dot is the same size at the same depth. Size is no longer
	// saying anything; whiteness is.
	//
	// It needs to be a vdef rather than the library \circle because the
	// travel is polar: \startSize -> \endSize is the distance out along
	// \rotation, and the library circle would read that as its own radius
	// and draw one expanding ring centred on the canvas. \sx/\sy cannot
	// place it either — they normalise against half-width and half-height
	// separately, so they cannot describe a circle.
	~vdef.(\dot, { |ev, c|
		var mod = ev[\modulation] ? ();
		var at  = c[\pos] + (c[\size] @ 0);
		var r   = c[\size] * (mod[\dotRatio] ? 0.10);
		var n   = ev[\numPoints] ? 16;
		Array.fill(n, { |i| at + Polar(r, i / n * 2pi).asPoint })
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \chooka,
			\note, Pseq([12,14,10,7,0]-1, inf),
			\envSus, 0.0,
			\pan, Pseq([-0.3,0.3], inf),
    		\filtRes, 0.8,

			\type, \customVisualEvent,
			\shape, \line,
			\numPoints, Pfunc({ |e| (e[\note] ? 0).linlin(-1, 13, 3, 7) }),
			\fill, true,
			\vstep, Pseries(0, 0.0, inf),
			\sx, 0,
			\ex, 0,
			\sy, 0,
			\ey, 1.0,
			\rotation, 0,
			\startSize, 1,
			\endSize, 200,
			\sizeEnv, Pfunc({ Env([0, 1], [1], 0) }),
			\startWidth, Pfunc({ |e| 
				var a = (e[\amp] ? 0);
				if(a < 0.1, { 0 }, { a });
				a.lincurve(0, 1, 0, 9, 1);
			 }),
			\endWidth, Pkey(\startWidth) * 8,
			\colorEnv, Pfunc({ Env([0, 1], [1], 3) }),
			\duration, 0.3,
			\modulation, Pfunc({ |e| (amp: 0, dotRatio: 0.3) }),

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

	var oct = m.gyroYFiltered.linlin(-1,1,8,4).floor;
  	var envRel = m.accelMassFiltered.lincurve(0,2.5,0.4,1.1,1);
	var amp = m.accelMassFiltered.lincurve(0,2.1,0.001,1.0,-1);
	var ff = (d.sensors.gyroEvent.y / pi.half).linexp(-1,1,80,14000,-2);
	var atk = m.accelMassFiltered.lincurve(0,2.5,0.02,0.001,-1);
	var dcy = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1,1,0.001,0.4,-2);
	var lff = (m.gyroZFiltered.fold(-0.5,0.5) * 2).linlin(-1,1,110.0,800);
	var hff = (m.gyroZFiltered.fold(-0.5,0.5) * 2).linexp(-1,1,250.0,8000);
	var hh = m.accelMassFiltered.lincurve(0,2.5,0,1.0,1);
	var notes = [28,35,40,47,52,59,64] + 12 + m.com.root;
	var ni = (d.sensors.gyroEvent.y / pi.half).lincurve(-0.8,0.8,0,notes.size-1,-2).floor;
	var kd = (d.sensors.gyroEvent.y / pi.half).lincurve(-0.8,0.8,0.1,2,-2);
	var ca = m.accelMassFiltered.lincurve(0,2.5,0.0,1.0,-12);

	// dur = (m.rrateMassFiltered).lincurve(0.0,2.1,0.2,0.01,-1);


	if(amp < 0.02) { amp = 0.0 };
	if(amp > 0.85, { 
		// Pdef(m.ptn).set(\subFreq, notes[0].midicps);
		Pdef(m.ptn).set(\subFreq, notes[ni].midicps);
		Pdef(m.ptn).set(\seq, amp * 0.5);
		Pdef(m.ptn).set(\subDecay, kd);
	}, { 
		Pdef(m.ptn).set(\seq, 0) ;
	});

	if(m.gyroYFiltered > -0.1, {
		if(m.gyroYFiltered < 0.1, {
				// amp = rrand(0.2, 0.35);
				// Pdef(m.ptn).set(\filtFreq, rrand(lff, hff));
				// Pdef(m.ptn).set(\envAtk, rrand(0.04, 0.05));
				// Pdef(m.ptn).set(\envRel,rrand(0.2,0.5));
				// Pdef(m.ptn).set(\envDec, rrand(0.1,0.3));

		},{
			Pdef(m.ptn).set(\filtFreq, ff);
			Pdef(m.ptn).set(\envAtk,atk);
			Pdef(m.ptn).set(\envRel,envRel);
			Pdef(m.ptn).set(\envDec, dcy);
			
		}); 
	});

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\octave,oct);


/*
			\startColor, Pfunc({ |e|
				Color.hsv((0.55 + rootHue.()).wrap(0, 1), 0.96,
					(e[\amp] ? 0).linexp(0, 1, 0.3, 1.0), 1.0)
			}),
			\endColor, Pfunc({ |e|
				Color.hsv((0.55 + rootHue.()).wrap(0, 1), 0.96,
					(e[\amp] ? 0).linexp(0, 1, 0.05, 1.0) * 0.4, 0.0)
			}),
*/

	
	Pdef(m.ptn).set(\startColor, Color.hsv((hh + rootHue.()).wrap(0, 1), 0.96,
					ca, 1.0));

	Pdef(m.ptn).set(\endColor, Color.hsv((hh + rootHue.()).wrap(0, 1), 0.96,
					ca, 0.0));	


};


//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	[m.rrateMassFiltered*30];
	// [m.accelMass * 0.1, m.accelMassFiltered * 0.1];
		// [((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2) ];
	// [m.gyroZFiltered.lincurve(-1.0,1.0,0.0,1.0,-2)];
	// [(m.gyroZFiltered/pi.half).linlin(-1.0,1.0,0.0,1.0)];
	// [(m.gyroZFiltered.fold(-0.5,0.5) * 2)];
	// [(d.sensors.gyroEvent.y / pi.half)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

