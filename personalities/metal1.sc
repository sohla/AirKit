var m = ~model;
var synth;
// the one-pole accumulator the draw func carries between frames. The drive
// itself is not held here - the draw func reads m and computes it.
var droneAmpSmooth = 0;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.4;
m.rrateMassFilteredAttack = 0.2;
m.rrateMassFilteredDecay = 0.08;
//------------------------------------------------------------

SynthDef(\bambooComplex1, {
  arg out=0, freq=440, pan=0, amp=0.5,
      att=0.001, rel=3.0,
      strikePos=0.3, // Position of strike (affects resonance)
      resonance=0.7, // Amount of resonant body sound
      bambooMoisture=0.5, // Affects damping and resonance
      model=0, // Model selector
      width=0.8; // Stereo width

  var exciter, klank, env, noiseSig, bodyResonance;
  var freqs, amps, times;
  var output, numResonators=5;

  // Initial strike - gentler, more wooden
  noiseSig = Mix([
      // Main body of the strike
      HPF.ar(PinkNoise.ar, 1000) * 0.2,

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
          rel * bambooMoisture.linlin(0, 1, 0.8, 1.2),
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

  Out.ar(out, output);
}).add;

SynthDef(\sheet2, { |out, frq=111, gate=0, amp = 0, pchx=0|
	var env = EnvGen.ar(Env.asr(0.3,1.0,8.0), gate, doneAction:Done.freeSelf);
	var follow = Amplitude.kr(amp.lag(3), 0.3, 0.5);
	// var sig = Saw.ar(frq.lag(2),0.3 * env * amp.lag(1));
	var trig = LPF.ar(PinkNoise.ar(0.01),600) * env * follow;
	var sig =  DynKlank.ar(`[([30,42,54] + pchx.lag(3)).midicps, nil, [2, 1, 1, 1]], trig);
	var dly = DelayC.ar(sig,0.03,[0.02,0.027]);
	Out.ar(out, Pan2.ar(Mix(dly),1));
}).add;

//------------------------------------------------------------
~init = ~init <> {|d|

	//------------------------------------------------------------
	// visual : the \sheet2 drone gets ONE held event, not a stream of them.
	//
	// duration: inf means the cull never expires it and normTime stays
	// pinned at 0, so the core's own start->end blending never advances.
	// This draw func does that blending instead, using the drone amp
	// (0-1) as the blend position in place of normTime. So start* is how
	// the frame looks silent, end* is how it looks at full drone, and
	// EVERYTHING that defines it lives in the event below - nothing here
	// invents a size, a colour or a scale factor of its own.
	//
	// clearEvents (personality unload) is what removes it.
	//
	// One of the few legitimate Pen cases : it re-derives COLOUR from amp,
	// not just alpha, and c[\render] can only scale the event's own colour.
	// It is a single path, so nothing is lost by drawing it directly.
	~vdef.(\sheetFrame, { |ev, c|
		var mod = ev[\modulation] ? ();
		var atk = mod[\attack] ? 0.007;
		var rel = mod[\release] ? 0.7;
		var fade = mod[\fade] ? -8;
		var drive = m.accelMass * 0.5;
		var amp, size, col;

		if(drive < 0.02, { drive = 0.0 });
		if(drive > 0.9, { drive = 0.3 });
		drive = drive.linlin(0, 0.9, 0, 1);

		droneAmpSmooth = if(drive > droneAmpSmooth, {
			droneAmpSmooth + ((drive - droneAmpSmooth) * atk)
		},{
			droneAmpSmooth + ((drive - droneAmpSmooth) * rel)
		});
		amp = droneAmpSmooth.clip(0, 1);

		size = ev[\startSize].blend(ev[\endSize], amp);
		col = ev[\startColor].blend(ev[\endColor], amp);
		col.alpha = col.alpha * amp.lincurve(0, 1, 0, 1, fade);

		Pen.width = ev[\startWidth].blend(ev[\endWidth], amp);
		Pen.addArc(c[\pos], size, 0, 2pi);
		if(ev[\fill] ? false, {
			Pen.fillColor = col;
			Pen.fill;
		},{
			Pen.strokeColor = col;
			Pen.stroke;
		});
	});

	// Fired once, then held. start* = silent, end* = full drone; the draw
	// func above blends between them on amp. Sizes are half-extents in
	// pixels, so endSize 320 is a 640px square.
	(
		type: \customVisualEvent,
		amp: 0,
		dur: 0.01,
		viewID: d.port,
		shape: \sheetFrame,
		fill: false,
		startSize: 60,
		endSize: 600,
		startWidth: 180,
		endWidth: 10,
		rotation: pi.half,
		startColor: Color.hsv(0.45, 0.95, 1.0, 0.4),
		endColor: Color.hsv(0.55, 0.25, 1.0, 0.1),
		sx: 0, sy: 0, ex: 0, ey: 0,
		duration: inf,
		modulation: (
			attack: 0.01,
			release: 0.015,
			fade: -5,
		),
	).play;

	//------------------------------------------------------------
	// visual : each note is a ring that flattens into a line, with a
	// wobble riding on top. Registered here so it reloads with the file
	// (loadPersonality calls clearVdefs before re-interpreting us).
	//
	// A vdef rather than shape: \circle because this wobbles the ring
	// per-point from \modulation values the library circle cannot see.
	~vdef.(\morphLine, { |ev, c|
		var n = ev[\numPoints] ? 48;
		var mod = ev[\modulation] ? ();
		var wobFreq = mod[\freq] ? 6;
		var wobHarm = mod[\harmonics] ? 3;
		var wobPhase = mod[\phase] ? 0;
		var ease = mod[\ease] ? 0.7;
		var sz = c[\size];
		var wobAmp = (mod[\wob] ? 4) * (1 - (c[\normTime] * ease));
		var pts = Array.fill(n, { |i|
			var angle = i / n * 2pi;
			var wob = cos((angle * wobHarm) + (2pi * wobFreq * c[\now]) + wobPhase) * wobAmp;
			c[\pos] + (
				(sin(angle) * sz)
				@ (((cos(angle) * sz) ) + wob)
			)
		});

		pts
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \bambooComplex1,
			\octave, Pseq([6,8,7] - 2, inf),
			// \dur, Pseq([0.4,Rest(0.2),0.2] * 0.5, inf),
			\degree, Pseq([0,2,7], inf),
			\root, Pseq([0,3,-2,0,-5,3,5,2].stutter(30), inf),
			\amp, 0.1,
			\pan, Pwhite(-0.6, 0.6),
			\model, 1,//Prand([0, 1, 2,3,4,5,6], inf),
			\strikePos, Pwhite(0.1, 0.9),
			\resonance, Pwhite(0.1, 0.9),
			\bambooMoisture, Pwhite(0.1,0.9),
			\rel, 1.9,
	        \func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],

			\type, \customVisualEvent,
			\shape, \morphLine,
            \fill, false,
			\numPoints, 48,
			\sx, Pwhite(-0.02, 0.02),
			\sy, Pwhite(-0.02, 0.02),
			\ex, 0,
			\ey, 0,
			\startSize, Pkey(\root).linlin(0,127,100,300),
			\endSize, 200,
			\startWidth, 13.5,
			\endWidth, 0.4,
			\rotation, Pwhite(0, 0.02),
			\startColor, Color.hsv(0.55, 0.85, 1.0, 0.9),
			\endColor, Color.hsv(0.2, 0.9, 0.5, 0.0),
			\duration, 0.8,
			\modulation, Pfunc({ (
				freq: rrand(4.0, 9.0) * 0.5,
				wob: rrand(2.0, 4.0),
				amp: 0,
				phase: 2pi.rand,
				harmonics: [2, 3, 4].choose,
				ease: 0.7
			) }),
		)
	);

	Pdef(m.ptn).play(quant:0.1);
	synth = Synth(\sheet2, [\frq, 10.midicps, \gate, 1]);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
  synth.set(\gate, 0);
};

//------------------------------------------------------------
~onEvent = {|e|
	m.com.root = e.root;
	m.com.dur = e.dur;

  synth.set(\pchx,4 + m.com.root + 14 -12);
};

//------------------------------------------------------------
~next = {|d|

	var move = m.accelMassFiltered.linlin(0,0.4,0,1);
	var oct = m.accelMassFiltered.linlin(0,3,2,5).floor;
	var dur = m.accelMassFiltered.lincurve(0,1.0,0.3,0.05,-3);
	
  	var a = m.accelMass * 0.5;
	var f = 50 + (m.accelMassFiltered * 100);
	var pchs = [0,12,24,36,48];
	var i = (d.sensors.gyroEvent.y.abs / pi) * (pchs.size);
	// pchs[i.floor].postln;
	if(a<0.02,{a=0.0});
	if(a>0.9,{a=0.3});
	synth.set(\amp, a * 1);

	// tells the visual router which device these shapes came from
	Pdef(m.ptn).set(\viewID, d.port);

 	Pdef(m.ptn).set(\dur, dur);
	// Pdef(m.ptn).set(\octave, 4 + oct);
	// Pdef(m.ptn).set(\amp, oct.linlin(2,5,0.1,0.9));
	if(move > 0.05, {
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.4);
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
	[m.accelMass * 0.1, m.accelMassFiltered.linlin(0,5,0,1)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};




