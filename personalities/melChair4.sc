var m = ~model;
var bl=false;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.7;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\simple, {|out=0, amp=0.0, freq=440, attack=0.001, decay=0.03, sustain=0.8, release=2.59, gate=1|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var li = LocalIn.ar(2);
	var sig = SinOsc.ar(freq * [1,1.02], HPF.ar(li,40,1,0.5),1);
	LocalOut.ar(sig);
    Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	~vdef.(\beatRings, { |ev, c|
		var mod = ev[\modulation] ? ();
		var ratio = mod[\ratio] ? 1.02;
		var beat = mod[\beat] ? 1;
		var depth = mod[\depth] ? 0.6;
		var sway = (mod[\swing] ? 14) * (1 + (m.accelMassFiltered * (mod[\push] ? 1)));
		var ring = c[\size];
		var slide = sin(pi * beat * c[\elapsed]) * sway;
		var pulse = 1 - depth + (depth * cos(pi * beat * c[\elapsed]).abs);

		c[\draw].(\circle, (pos: c[\pos] - (slide @ 0), size: ring), 1, pulse);
		c[\draw].(\circle, (pos: c[\pos] + (slide @ 0), size: ring / ratio), 1, pulse);
		nil
	});

  Pdef(m.ptn,
    Pbind(
      \instrument, \simple,
      \octave, Pseq([4,5], inf),
	  \root, Pseq([0,3,-2,2].stutter(22), inf),
      \note, Pseq([11,4,7,0,4,7,11,12,11,7,4], inf),
      \attack,0.1,
      \decay, 0.1,
      \sustain,0.1,
      \release,2.04,
      \args, #[],

      \type, \customVisualEvent,
      \shape, \beatRings,
      \numPoints, 48,
      \fill, false,
      \ringMidi, Pfunc({ |e| (e[\note] ? 0) + (e[\root] ? 0) + ((e[\octave] ? 4) * 12) }),
      \sx, Pseq((0..10).linlin(0, 10, -0.85, 0.85), inf),
      \ex, Pkey(\sx),
      \sy, Pfunc({ |e| e[\ringMidi].linlin(46, 76, 0.85, -0.85) }),
      \ey, Pfunc({ |e| e[\sy] - 0.08 }),
      \startSize, Pfunc({ |e| e[\ringMidi].linexp(46, 76, 180, 36) }),
      \endSize, Pfunc({ |e| e[\startSize] * 1.3 }),
      \startWidth, 3,
      \endWidth, 0.5,
      \startColor, Pfunc({ |e|
        if((e[\octave] ? 4) > 4, { Color.new255(40, 180, 160, 240) }, { Color.new255(242, 178, 38, 240) })
      }),
      \endColor, Pfunc({ |e| e[\startColor].copy.alpha_(0) }),
      \colorEnv, Pfunc({ Env([0, 1], [1], -4) }),
      \duration, Pfunc({ |e| (e[\sustain] ? 0.1) + ((e[\release] ? 2) * 0.55) }),
      \modulation, Pfunc({ |e|
        var beat = e.use { ~freq.value } * 0.02 * 0.1;
        (
          ratio: 1.02,
          beat: beat,
          depth: 0.6,
          swing: 14,
          push: 1.5,
          type: \radial,
          freq: beat,
          harmonics: 4,
          amp: (m.accelMassFiltered * 8).clip(0, 10)
        )
      })
    )
  );
  Pdef(m.ptn).play(quant:0.2);  
};

//------------------------------------------------------------
~deinit = ~deinit <> {
  Pdef(m.ptn).remove;

};

//------------------------------------------------------------
~next = {|d|

	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
//   var dur = m.rrateMassFiltered.lincurve(0,0.1,0.4,0.04,-1);
	var dur = m.accelMassFiltered.lincurve(0,2.5 * sens,4,12,-10).reciprocal;

  Pdef(m.ptn).set(\dur, dur);
  Pdef(m.ptn).set(\amp, vol * 0.2);
  Pdef(m.ptn).set(\viewID, d.port);
 	if(m.rrateMassFiltered > (0.01 + (0.2 * sens)),{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:dur);
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

	// [yellow, cyan , magenta]??

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// [m.accelMass, m.accelMassFiltered];
	// [d.sensors.accelEvent.x.abs * d.sensors.accelEvent.y.abs *  m.accelMassFiltered] * 0.5;

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	[(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
