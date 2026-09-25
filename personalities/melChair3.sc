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
	var sig = SinOsc.ar(freq, li * 1,0.5)!2;
	LocalOut.ar(sig);
    Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	~vdef.(\paperTone, { |ev, c|
		var n = ev[\numPoints] ? 64;
		var mod = ev[\modulation] ? ();
		var cycles = mod[\cycles] ? 6;
		var index = mod[\index] ? 0.5;
		var drift = mod[\drift] ? 1.5;
		var shake = c[\size] * (1 + (m.rrateMassFiltered * (mod[\shake] ? 1)));
		var a = c[\posStart];
		var b = c[\posEnd];
		var normal = Polar(1, (b - a).theta + 0.5pi).asPoint;
		var phase = c[\now] * drift;

		Array.fill(n, { |i|
			var t = i / (n - 1);
			var ph = 2pi * ((t * cycles) - phase);
			var y = 0;
			3.do { y = sin(ph + (index * y)) };
			a.blend(b, t) + (normal * y * shake * sin(t * pi))
		})
	});

  Pdef(m.ptn,
    Pbind(
      \instrument, \simple,
      \octave, 3,
	  \root, Pseq([3].stutter(16), inf),
      \note, Pseq([0,4,7,11,12,11,7,4].stutter(4), inf),
      \attack,0.03,
      \decay, 0.1,
      \sustain,0.1,
      \release,1.04,
	//   \amp,0.5,
      \args, #[],

      \type, \customVisualEvent,
      \shape, \paperTone,
      \closed, false,
      \fill, false,
      \numPoints, 80,
      \paper, 4,
      \sx, Pfunc({ |e| ((thisThread.beats / e[\paper]) % 1).linlin(0, 1, -0.96, 0.96) }),
      \ex, Pfunc({ |e| e[\sx] + ((e[\dur] ? 0.2) / e[\paper] * 1.92) }),
      \sy, Pfunc({ |e| (e[\note] ? 0).linlin(0, 12, 0.72, -0.72) }),
      \ey, Pkey(\sy),
      \duration, Pfunc({ |e| (e[\sustain] ? 0.1) + (e[\release] ? 1) }),
      \sizeEnv, Pfunc({ |e|
        var att = e[\attack] ? 0.03;
        var gate = (e[\sustain] ? 0.1) - att;
        Env([0, 1, 0.3, 0], [att, gate.max(0.001), e[\release] ? 1].normalizeSum, -4)
      }),
      \widthEnv, Pfunc({ |e| e[\sizeEnv] }),
      \startSize, 0,
      \endSize, 34,
      \startWidth, 0.8,
      \endWidth, 7,
      \startColor, Color.new255(52, 92, 214, 255),
      \endColor, Color.new255(232, 158, 150, 0),
      \modulation, Pfunc({ |e| (
        cycles: e.use { ~freq.value } * (e[\dur] ? 0.2) * 0.25,
        index: 0.5,
        drift: 1.5,
        shake: 1,
        amp: 0
      ) })
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

  var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
  var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
  var dur = m.rrateMassFiltered.lincurve(0, 2 * sens,0.4,0.04,-3);

  Pdef(m.ptn).set(\dur, dur);
  Pdef(m.ptn).set(\amp, vol * 0.8);
  Pdef(m.ptn).set(\viewID, d.port);

 	if(m.rrateMassFiltered > 0.1,{
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
