/*
gestures:    [shake]
description: Bamboo and metal — a held resonant drone that swells with movement, and a bamboo arpeggio that only plays while you are moving, faster the harder you shake
internals:   Ported from Airsticks-RPI personalities/metal1.sc. Mixed engine: one long-lived \sheet2 synth (pink-noise-excited DynKlank drone, Amplitude-followed, gated) held for the life of the seat, plus a Pdef on ~beatClock firing self-freeing \bambooComplex1 synths (noise exciter into dual Klank + DynKlank tube) into a dedicated Group. The Pdef pauses when the stick is still and resumes on movement. Each bamboo note retunes the drone to its root (~onEvent). SynthDefs and Pbind material verbatim; visual keys and m.com writes dropped. RPI dur (seconds) is scaled by beat = 8 ~beatClock events. A Pbindf over the verbatim Pbind lets tuning/piece replace the root walk. No samples. Patch trim not set — verify at bench.
sound:       a breathing metallic drone underneath, hollow bamboo knocks over it in rising octave figures
pitch:       bamboo degrees [0,2,7] (major) in octaves 4,6,5 over \root; drone sits on the same root (C + root, three octaves). Idle: the Pbind's own root walk [0,3,-2,0,-5,3,5,2], 30 notes each. Tuning: bamboo paused, drone on A. Piece: root replaced by the lowest note of ctx.voicePool, so drone and bamboo follow the score's bass. Curtain holds the last root.
rhythm:      bamboo dur from accel (0.3 -> 0.05 s RPI-equivalent), only while moving; drone continuous. Tuning and curtain: drone only.
*/

var m = ~model;
var ob = ~outBus ? 0;
var synth;
var group;
var beat = 8;      // ~beatClock events per RPI-second (one bar)
var scoreRoot;     // nil = the Pbind's own root walk

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
	Out.ar(out, dly);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var onEvent = ~onEvent;   // the Pdef's stream runs under topEnvironment, where ~onEvent is nil

	topEnvironment.use {
		group = Group.new;
		// after the Group, not inside it: ~onResync's freeAll must not take the drone
		synth = Synth.after(group, \sheet2, [\out, ob, \frq, 10.midicps, \gate, 1]);

		Pdef(m.ptn,
			Pbindf(
				Pbind(
					\instrument, \bambooComplex1,
					\out, ob,
					\group, group,
					\octave, Pseq([6,8,7] - 2, inf),
					// \dur, Pseq([0.4,Rest(0.2),0.2] * 0.5, inf),
					\degree, Pseq([0,2,7], inf),
					\root, Pseq([0,3,-2,0,-5,3,5,2].stutter(30), inf),
					// \amp, 0.1,
					\pan, Pwhite(-0.6, 0.6),
					\model, 1,//Prand([0, 1, 2,3,4,5,6], inf),
					\strikePos, Pwhite(0.1, 0.9),
					\resonance, Pwhite(0.1, 0.9),
					\bambooMoisture, Pwhite(0.1,0.9),
					\rel, 1.9,
					\args, #[],
				),
				\root, Pfunc({ |e| scoreRoot ? e[\root] }),
				\func, Pfunc({ |e| onEvent.(e) })
			)
		);

		Pdef(m.ptn).set(\amp, 0, \dur, 0.3 * beat);
		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
	};

	~onResync = { |idx|
		topEnvironment.use {
			Pdef(m.ptn).stop;
			s.bind { group.freeAll };
			if ([\tuning, \curtain, \silent].includes(~roomState).not) {
				Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			};
		};
	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var g = group;
	group = nil;

	Pdef(m.ptn).remove;
	if(synth.notNil, { synth.set(\gate, 0) });
	synth = nil;

	fork {
		2.5.wait;
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
	};
};

//------------------------------------------------------------
~onEvent = {|e|
  synth.set(\pchx,4 + e.root + 14 -12);
};

//------------------------------------------------------------
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  { },
		\piece,   { },
		\curtain, { },
		\silent,  {
			Pdef(m.ptn).pause;
			synth !? { synth.set(\amp, 0) };
		}
	);
};

//------------------------------------------------------------
~idleNext = {|d, ctx|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var move = m.accelMassFiltered.linlin(0, 0.8 * sens,0,1);
	var oct = m.accelMassFiltered.linlin(0, 6 * sens,2,5).floor;
	var dur = m.accelMassFiltered.lincurve(0, 2 * sens,0.3,0.05,-3);

  	var a = m.accelMass * 0.5;
	var f = 50 + (m.accelMassFiltered * 100);
	var pchs = [0,12,24,36,48];
	var i = (d.sensors.gyroEvent.y.abs / pi) * (pchs.size);
	// pchs[i.floor].postln;
	if(a<0.02,{a=0.0});
	if(a>0.9,{a=0.3});
	synth.set(\amp, a * 0.5 * vol);

	scoreRoot = nil;
	Pdef(m.ptn).set(\amp, 0.1 * vol);

 	Pdef(m.ptn).set(\dur, dur * beat);
	// Pdef(m.ptn).set(\octave, 4 + oct);
	// Pdef(m.ptn).set(\amp, oct.linlin(2,5,0.1,0.9));
	if(move > 0.05, {
		if( Pdef(m.ptn).isPlaying.not,{
			topEnvironment.use { Pdef(m.ptn).resume(~beatClock, quant: 0.4 * beat) };
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});

};

~tuningNext = {|d, ctx|
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
  	var a = m.accelMass * 0.5;
	if(a<0.02,{a=0.0});
	if(a>0.9,{a=0.3});
	synth.set(\amp, a * 0.5 * vol);
	synth.set(\pchx, 4 + 9 + 14 - 12);   // ~onEvent's mapping at root 9 = A

	if( Pdef(m.ptn).isPlaying,{ Pdef(m.ptn).pause() });
};

~pieceNext = {|d, ctx|
	var pool = (ctx !? { ctx.voicePool }) ? [69];
	var loud = ((ctx !? { ctx.loudness }) ? 1.0).linlin(0, 1, 0.3, 1.0);
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var move = m.accelMassFiltered.linlin(0, 0.8 * sens,0,1);
	var dur = m.accelMassFiltered.lincurve(0, 2 * sens,0.3,0.05,-3);
  	var a = m.accelMass * 0.5;
	if(a<0.02,{a=0.0});
	if(a>0.9,{a=0.3});
	if(pool.isEmpty, { pool = [69] });

	synth.set(\amp, a * 0.5 * vol * loud);

	scoreRoot = pool.minItem.asInteger.wrap(-6, 5);
	Pdef(m.ptn).set(\amp, 0.1 * vol * loud);
 	Pdef(m.ptn).set(\dur, dur * beat);

	if(move > 0.05, {
		if( Pdef(m.ptn).isPlaying.not,{
			topEnvironment.use { Pdef(m.ptn).resume(~beatClock, quant: 0.4 * beat) };
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});
};

~curtainNext = {|d, ctx|
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var a = (m.rrateMassFiltered * 2.0).lincurve(0, 1.0, 0, 0.3, -4);
	synth.set(\amp, a * 0.5 * vol);

	if( Pdef(m.ptn).isPlaying,{ Pdef(m.ptn).pause() });
};

//------------------------------------------------------------
~onTick    = {|ctx| };
~onHalf    = {|ctx| };
~onBeat    = {|ctx| };
~onBar     = {|ctx| };
~onPhrase  = {|ctx| };
~onSection = {|ctx| };
~onChord   = {|ctx| };
~onKey     = {|ctx| };
~onScale   = {|ctx| };

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
