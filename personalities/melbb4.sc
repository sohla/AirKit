var m = ~model;
var bi = [5,14].choose;
var dur = 0.085;
var beat = dur * 8;
var divs = [2,2,3,4];
var limit = 0;
var beatType = (\customBeatEvent_ ++ m.ptn).asSymbol;
var marimbaLib;

// var marimbaNotes = [0,1,4,5,8,11,13,16] + 50;
// var marimbaNotes = [0,2,5,7,8,11,12] + 50;
var marimbaNotes = [0,7,11,12,17,20,26] + 50 - 12;

var marimbaFolder = "~/Downloads/cotf_samples/African Marimba";
var marimbaFilter = "Marimba ln f l1x";


	var ev = (
		type: \customVisualEvent,
		amp: 0,
		viewID: ~device.port,
		shape: \circle,
		fill: true,
		rotate: 0,
		startSize: 10,
		endSize: 20,
		duration: 1.0,
		dur: 0.01,
		startColor: Color.yellow.lighten(0.2).alpha_(0.8),
		endColor: Color.green.darken(0.8).alpha_(0.1),
		startWidth: 0.0,
		endWidth: 1.0,
		sx: 0,
		sy: 0,
		ex: 0.2,
		ey: -0.3, 
	);

~buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.3;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\drumkit3, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=0.3, gate=1,cutoff=40, rq=0.1, poff=5|
	var lr = rate * BufRateScale.kr(bufnum);
	var cd = BufDur.kr(bufnum);
  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, poff.midiratio], startPos: start * BufFrames.kr(bufnum), loop: 0) * 10;
    sig = RHPF.ar(sig, cutoff, rq);
		sig = Compander.ar(sig, sig,
        thresh: -15.dbamp,
        slopeBelow: 1,
        slopeAbove: 0.5,
        clampTime:  0.008,
        relaxTime:  0.1
	);
	sig = Mix.ar([sig]);
    // sig = Balance2.ar(sig[0],sig[1], pan);
    Out.ar(out, ((0)!0 ++ sig) * amp * env);
}).add;

SynthDef(\melbb4Marimba, { |out = 0, bufnum = 0, amp = 0.5, rate = 1, start = 0, pan = 0,
	attack = 0.004, sustain = 0.04, release = 0.03|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env([0, 1, 0.0, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr * SinOsc.ar([9,14],0,0.02,1), startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = sig * amp * env;
	Out.ar(out, sig);
}).add;
//--------------------------------------
~init = ~init <> {


	var folder  = PathName("~/Downloads/melSamples/melbb");

	var parseMarimbaNote = { |fileStem|
		var noteNames = "C C# D D# E F F# G G# A A# B".split($ );
		var parts = fileStem.findRegexp("([0-9])[ ]*([a-g])$");
		var octave = parts[1][1].asInteger;
		((octave + 1) * 12) + noteNames.indexOfEqual(parts[2][1].toUpper)
	};

	var findClosestMarimba = { |targetMidi|
		var closest = marimbaLib.minItem({ |sample| (sample[\midiNote] - targetMidi).abs });
		(buffer: closest[\buffer], rate: (targetMidi - closest[\midiNote]).midiratio)
	};

	postf("loading samples : % \n", folder);

	~buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
			if(folder.entries.size - 1 == i,{
				"samples loaded".postln;
			});
		});
	});

	marimbaLib = PathName(marimbaFolder).entries
		.select({ |path| path.fileName.contains(marimbaFilter) })
		.collect({ |path|
			(
				midiNote: parseMarimbaNote.(path.fileNameWithoutExtension),
				buffer: Buffer.read(s, path.fullPath, action: { |buf| postf("buffer alloc [%] \n", buf) })
			)
		});

  	Event.addEventType(beatType, {
		var found;
		~type = \note;
		currentEnvironment.play;
		if(marimbaLib.size > 0 and: { ~marimbaNote.notNil }, {
			found = findClosestMarimba.(~marimbaNote);
			(instrument: \melbb4Marimba, sendGate: false, bufnum: found[\buffer], rate: found[\rate], amp: ~amp * (~marimbaAmp ? 1), pan: ~pan).play;
		});
		ev.play;
  	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \drumkit3,			
      		\type, beatType,
			\div, Pswitch(divs.collect({ |n| Pn(n, n) }), Pkey(\divIdx)),
			\dur, Pkey(\div).reciprocal * beat,
			\bufnum, Pfunc{ |e|
				bi = (bi + (8 / e[\div]).asInteger) % (~buffers.size - 1);
				~buffers[bi];
			},
			\start, 0.01,
			\note, Pseq([0].stutter(4), inf),
			
			\poff, Pstutter(Pkey(\div), Pseq([0,7,5,4,2], inf)),
			\marimbaNote, Prand(marimbaNotes, inf),
			\marimbaAmp, 1,
			\pan, Pwhite(-0.4,0.4),
			\attack, 0.02,
      		\release, 0.1,
      		\func, Pfunc({|e| ~onEvent.(e)}),
			// \vis, Pfunc({|e| 
			// 	// ev.play;
			// 	true
			// }),

			\args, #[],
		)
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).play(quant:dur);
	Pdef(m.ptn).set(\bufnum, ~buffers[0]);

	m.com.bgColor = Color.black;
};

~deinit = ~deinit <> {
	var dying = (~buffers ? []) ++ (marimbaLib ? []).collect({ |smp| smp[\buffer] });
	~buffers = nil;
	marimbaLib = nil;
	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(beatType);

	fork {
		2.0.wait;
		dying.do({|buf|
			postf("buffer dealloc [%] \n", buf);
			buf.free;
			s.sync;
		});
	};
};

//------------------------------------------------------------
~onEvent = {|e|

  true
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var ud = m.accelMassFiltered.lincurve(0, 2.5 * sens,0,5,1).round;
	var amp = m.accelMassFiltered.lincurve(0, 2.5 * sens,0,0.4,1);
	var rate = m.gyroYFiltered.clip(-0.5,0.5).lincurve(-0.5,0.5,1,-1,-1).floor;
	var time = TempoClock.beats;

	Pdef(m.ptn).set(\divIdx, ud.asInteger.clip(0, divs.size - 1));
	Pdef(m.ptn).set(\amp, 0.2 * vol);
	Pdef(m.ptn).set(\rate, 2.pow(rate));
	Pdef(m.ptn).set(\octave, 7);

	if(m.accelMassFiltered > (0.1 * sens),{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:dur);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});
	ev = (
		type: \customVisualEvent,
		amp: 0,
		viewID: ~device.port,
		shape: \hexagon,
		fill: false,
		rotate: 0,
		startSize: 0,
		endSize: 180,
		duration: 0.3,
		dur: 0.01,
		startColor: Color.blue.lighten(0.8).alpha_(1),
		endColor: Color.red.darken(0.9).alpha_(0.1),
		startWidth: 1.0,
		endWidth: ud.reciprocal * 30,
		sx: 0,
		sy: 0,
		ex: sin(time * 2) * 0.1,
		ey: cos(time * 2) * 0.1, 
	);

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
	[m.accelMass, m.accelMassFiltered];

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];


};
// Buffer.cachedBuffersDo(s, {|b|b.postln})