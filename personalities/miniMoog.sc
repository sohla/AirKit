
var m = ~model;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;

//------------------------------------------------------------
SynthDef(\miniMoog, {
	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0|

    var env, osc, filt, sig;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 0.005],0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
    filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
    sig = filt * env * amp * 0.5;
    sig = Pan2.ar(sig, pan);
    Out.ar(0, sig.tanh);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	//------------------------------------------------------------
	// visual : a superellipse. The exponent is what makes it round or
	// square - 2 is a true circle, and the larger it gets the flatter the
	// sides and the tighter the corners, until it reads as a rectangle.
	// modulation[\sharp] (0-1, set per note from accelMassFiltered) picks
	// the point on that scale.
	//
	// Returns its points rather than drawing, so the core still applies
	// \modulation, \closed and \fill for us.
	~vdef.(\squircle, { |ev, c|
		var n = ev[\numPoints] ? 64;
		var mod = ev[\modulation] ? ();
		var sharp = (mod[\sharp] ? 0).clip(0, 1);
		var expo = 2 / sharp.linexp(0, 1, mod[\round] ? 2, mod[\corner] ? 14);
		var sz = c[\size];
		var pos = c[\pos];
		var pts = Array.fill(n, { |i|
			var ang = i / n * 2pi;
			var ca = cos(ang) * 3;
			var sa = sin(ang) * 1 ;
			pos + (
				(ca.sign * ca.abs.pow(expo) * sz)
				@ (sa.sign * sa.abs.pow(expo) * sz)
			)
		});
		pts
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \miniMoog,
            \note, Pseq([0,5,2,9,4], inf),
            \octave, Pseq([1,2,3].stutter(2) + 2, inf),
            \root, Pseq([0,4,8].stutter(60), inf),
            // \amp, 0.3,
            \decay, 0.1,
            \sustain, 0.1,
            \release, Pwhite(0.1,2.4),
            \fq, 0.3,
            \pan, Pseq([-0.3, 0.3], inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],

			\type, \customVisualEvent,
			\shape, \squircle,
			\numPoints, 64,
			\fill, true,
			\duration, Pkey(\release),
			\sx, 0,
			\ex, 0.0 - (Pkey(\release) * 0.5),
			\sy, (Pkey(\note) + (Pkey(\octave) * 12)).linlin(36, 69, 0.55, -0.55),
			\ey, (Pkey(\note) + (Pkey(\octave) * 12)).linlin(36, 69, 0.55, -0.55),
			\startSize, Pkey(\amp).lincurve(0.018, 0.89, 8, 30, -1),
			\endSize, Pkey(\amp).lincurve(0.018, 0.89, 1, 20, -1),
			\startWidth, 2,
			\endWidth, 0.5,
			\startColor, Pfunc{|e|Color.hsv( (e.root/9).fold(0.1,1), 0.9, 0.3, 1.0)},
			\endColor, Color.hsv(0.4, 0.8, 0.3, 0.0),
			\modulation, Pfunc({ (
				type: \normal,
				freq: m.accelMassFiltered.lincurve(0, 2.0, 1, 12, -1),
				amp: m.accelMassFiltered.lincurve(0, 2.0, 0.0, 12, 2),
				phase: 2pi.rand,
				harmonics: 3,
				sharp: m.accelMassFiltered.lincurve(0, 2.0, 0, 1,-1),
				round: 3,
				corner: 1
			) })

			
		);
	);
	Pdef(m.ptn).play(quant:0.1);
	Pdef(m.ptn).pause;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
//------------------------------------------------------------
~onEvent = {|e|
	m.com.root = e.root;
};


//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity;
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var dur = 0.2;
	
	// var ff = (d.sensors.gyroEvent.x/pi).linexp(-0.5,0.5,300,10000); 
	// var oct = (d.sensors.gyroEvent.y/pi).linlin(-0.4,0.4,3.0,8.0); //left right

	var amp = m.accelMassFiltered.lincurve(0, 3 * sens,-15,-1,-1);
	var atk = m.accelMassFiltered.lincurve(0, 3 * sens,0.1,0.0001,-3);
	var rel = m.accelMassFiltered.lincurve(0, 5 * sens,0.01,1.0,-1);
	// var ff = m.accelMassFiltered.linexp(0, 1 * sens,60,18000);
	var ff = m.rrateMassFiltered.linexp(0, 0.4 * sens,60,15000);

	// tells the visual router which device these shapes came from
	Pdef(m.ptn).set(\viewID, d.port);

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol);
	Pdef(m.ptn).set(\attack, atk);
	Pdef(m.ptn).set(\rel, rel);
    Pdef(m.ptn).set(\filterFreq, ff);

	if(m.accelMassFiltered > 0.02,{
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

	// ACCEL
	// [m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];
	
	// ROTATE
	[m.rrateMass, m.rrateMassFiltered.linlin(0,0.2,0,1)];

	// X axis
	// [d.sensors.gyroEvent.x/pi]; // norm
	
	// Y axis
	// [d.sensors.gyroEvent.y/pi]; // norm

	// Z axis
	// [d.sensors.gyroEvent.z/(pi/2)]; // norm

	// device [I• ]
	// [(d.sensors.gyroEvent.x/pi).linlin(-0.5,0.5,0.9,-0.9)]  //up down
	// [(d.sensors.gyroEvent.y/pi).linlin(-0.4,0.4,0.9,-0.9)]  //left right
	// [(d.sensors.gyroEvent.z/(pi/2)).linlin(-0.3,1.0,-0.9,0.9)]  //wrist rotate


};




// •Pitch + Duration 
// •Pitch + Timbre 
// •Pitch + Dynamics 
// Duration + Timbre 
// Duration + Dynamics 
// Timbre + Dynamics
