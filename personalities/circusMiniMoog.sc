
var m = ~model;
var group;
var fxBus;
var verb;

var pools = [
	[0,5,2,9,4] - 1,
	[0,7,2,11,12] - 3,
	[0,5,2,9,4] + 4,
	[0,7,2,11,12] + 2,
	[0,7,2,11,12] - 5,
	[0,7,4,11,12] - 6,
] - 4;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;

//mel conducts bass player

//------------------------------------------------------------
SynthDef(\circusMiniMoogOld, {
	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0, fx = 0, send = 0|

    var env, osc, filt, sig;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 1.005] * 1,0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
    filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
    sig = filt * env * amp * 0.5;
    sig = Pan2.ar(sig, pan);
    Out.ar(0, sig.tanh);
    Out.ar(fx, sig.tanh * send);
}).add;


SynthDef(\circusMiniMoog, {
	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0|

    var env, osc, filt, sig, sub;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 1.005] * 1,0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
    filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
	sub = SinOsc.ar(freq * 0.5, 0, 0.3 * amp);
    sig = filt * env * amp * 0.5;
	sig = sig + (sub * env);
    sig = Pan2.ar(sig, pan);
    Out.ar(0, sig.tanh);
}).add;
SynthDef(\circusMiniMoogVerb, { |in = 0, out = 0, room = 0.95, damp = 0.1, lowcut = 250, hicut = 14000, amp = 1, gate = 1|
	var env = EnvGen.kr(Env.asr(0.01, 1, 6), gate, doneAction: 2);
	var wet = In.ar(in, 2);
	wet = FreeVerb2.ar(wet[0], wet[1], 1, room.lag(0.5), damp.lag(0.5));
	wet = LPF.ar(HPF.ar(wet, lowcut.lag(0.5)), hicut.lag(0.5));
	Out.ar(out, wet * env * amp.lag(0.5));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;
	fxBus = Bus.audio(s, 2);
	verb = Synth.tail(group, \circusMiniMoogVerb, [\in, fxBus, \out, 0]);

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
			\instrument, \circusMiniMoog,
			\group, group,
			\fx, fxBus.index,
            // \note, Pseq([0,4,2,9,4], inf),
			// \note, Pseq([0,4,2,9,7], inf),
			\note, Pn(Plazy({ Pseq(pools.wrapAt(m.com.scaleIdx ? 0), 1) })),
            \octave, Pseq([1,2,3].stutter(2) + 2, inf),
            // \root, Pseq([0,4,-4,2,8].stutter(60), inf),
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
			\sy, 0.8,
			\ey, 0.0 - (Pkey(\release) * 0.5),
			\sx, (Pkey(\note) + (Pkey(\octave) * 12)).linlin(36, 69, 0.55, -0.55),
			\ex, (Pkey(\note) + (Pkey(\octave) * 12)).linlin(36, 69, 0.55, -0.55),
			\startSize, Pkey(\amp).lincurve(0.018, 0.89, 8, 30, -1),
			\endSize, Pkey(\amp).lincurve(0.018, 0.89, 1, 20, -1),
			\startWidth, 2,
			\endWidth, 0.5,
			// \startColor, Pfunc{|e|Color.hsv( (e.root/9).fold(0.1,1), 0.9, 0.3, 1.0)},
			// \endColor, Color.hsv(0.4, 0.8, 0.3, 0.0),
			\startColor, Pfunc({ |e| Color.hsv((e[\filterFreq] ? 800).explin(60, 15000, 0.28, 0.4), (e[\fq] ? 0.3).linlin(0.05, 1.0, 0.85, 0.35), 1.0, 1.0) }),
			\endColor, Pfunc({ |e| e[\startColor].copy.alpha_(0) }),
			\rotation, pi/2,
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
	Pdef(m.ptn).play(quant:0.8);
	// Pdef(m.ptn).pause;
	m.com.bgColor = Color.black;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var rv = verb;
	var bus = fxBus;
	var g = group;
	var tail = 8.0;
	group = nil;
	verb = nil;
	fxBus = nil;
	Pdef(m.ptn).remove;
	fork {
		tail.wait;
		rv !? { rv.set(\gate, 0) };
		6.wait;
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
		bus !? { bus.free };
	};
};

//------------------------------------------------------------
~onEvent = {|e|
	// m.com.root = e.root;
	e
};


//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var dur = 0.2;
	var amp = m.accelMassFiltered.lincurve(0, 2 * sens,-20,-1,-1);
	var atk = m.accelMassFiltered.lincurve(0, 2 * sens,0.1,0.001,-3);
	var rel = m.accelMassFiltered.lincurve(0, 2 * sens,0.001, 8.0,-1);
	var ff = m.rrateMassFiltered.linexp(0, 0.4 * sens,60,15000);
	var send = m.accelMassFiltered.lincurve(0, 6 * sens, 0.0001, 1.0, 4);
	var verbRoom = 0.85;
	var verbDamp = 0.1;
	var verbLowcut = 250;
	var verbHicut = 14000;
	var verbAmp = 1;

	if(amp < 18.neg, {amp = 110.neg});

	// tells the visual router which device these shapes came from
	Pdef(m.ptn).set(\viewID, d.port);

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol);
	Pdef(m.ptn).set(\attack, atk);
	Pdef(m.ptn).set(\rel, rel);
    Pdef(m.ptn).set(\filterFreq, ff);
	Pdef(m.ptn).set(\send, send);
	verb !? { |x| x.set(\room, verbRoom, \damp, verbDamp, \lowcut, verbLowcut, \hicut, verbHicut, \amp, verbAmp) };

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
