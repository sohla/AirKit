
var m = ~model;
var lastTime = 0;
var frame = 0;

var synth;
// var notes = [0,2,5,7,9,11,12,14,12,11] + 0;
var notes = [0,7,12,14,12,7,5,2] + 0;
var offset = 0;
var root = 0;
var octave = 3;
//------------------------------------------------------------

var noteToMidi = { |noteName|
		var pattern = "([A-G](#|b)?)([0-9])";
		var noteNames = "C C# D D# E F F# G G# A A# B";
		var parts, note, octave, noteIndex;
		parts = noteName.findRegexp(pattern);
		if(parts.size < 3, { Error("Invalid note format: %".format(noteName)).throw});
		note = parts[1][1];
		octave = parts[3][1].asInteger;
		note = note.replace("Cb", "B").replace("Db", "C#").replace("Eb", "D#")
								.replace("Fb", "E").replace("Gb", "F#").replace("Ab", "G#").replace("Bb", "A#");
	noteIndex = noteNames.split($ ).find([note]);
		(octave + 1) * 12 + noteIndex;
};

var folder = PathName("~/Downloads/openLabSamples/harp");
var samplesLib;

//------------------------------------------------------------

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\stereoSamplerH, {|bufnum=0, out=0, amp=1, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1,cutoff=20000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);// * (freq/440.0);
    var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	// sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig);
}).add;


//------------------------------------------------------------
~init = ~init <> {

	var result;

	var findSampleBuffer = {|note|
		var bufnum;
		samplesLib.do({|sample|
			if(sample.midiNote == note,{
				bufnum = sample.buffer;
			});
		});
		bufnum
	};

	samplesLib = folder.entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buffer = Buffer.read(s, path.fullPath, action:{ |buf|
			postf("buffer alloc [%] \n", buf);
		});
		postf("loading sample : % \n", path.fileNameWithoutExtension);
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
	});

	// ~playNote = {|note,root,octave, amp=0.1|
	// 		var n = note + root + (12 * octave);
	// 		var bufnum,rate;

	// 		if(n.odd,{
	// 			bufnum = findSampleBuffer.(n-1);
	// 			rate = 1.midiratio;
	// 		},{
	// 			bufnum = findSampleBuffer.(n);
	// 			rate = 1;
	// 		});
	// 		synth = Synth(\stereoSampler, [
	// 			\bufnum, bufnum,
	// 			\rate,rate,
	// 			\freq, n.midicps,
	// 			\amp,amp*1
	// 		]);
	// 		synth.server.sendBundle(0.3,[\n_set, synth.nodeID, \gate, 0]);

	// };

	Event.addEventType(\customEvent, {|e|
		~note = ~note + ~root + (12 * ~octave);
		if(~note.odd,{
			~bufnum = findSampleBuffer.(~note-1);
				~rate = 1.midiratio;
		},{
			~bufnum = findSampleBuffer.(~note);
				~rate = 1;
		});
			// ~instrument = \stereoSampler;
			~type = \customVisualEvent;
			// ~type = \note;
			currentEnvironment.play;
		 	// ~bufnum.postln;
	});

	Pdef(m.ptn,
		Pbind(
      \type, \customEvent,
			\instrument, \stereoSamplerH,
      // \root,0,
			// \dur, Pslide([dur,dur,dur,dur,dur,dur,dur,dur,dur,dur], inf, Pkey(\range), 0, 0),
			\note, Pslide(notes, inf, Pkey(\range), 0, offset),
			\sx, (Pkey(\note) * 0.1) - 0.8,
			\sy, 0,
			\ex, Pkey(\sx),
			\ey, 0,
			// \octave, octave,
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]

		);
	);
	Pdef(m.ptn).play(quant:0.1);


};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	// hack a delay to ensure the Pdef is removed before the samples are freed
	fork{
		1.0.yield;
		samplesLib.do({|sample|
			postf("buffer dealloc [%] \n", sample.buffer);
			sample.buffer.free;
			// s.sync;
		});
	};
	
};

//------------------------------------------------------------
~onEvent = {|e|
  root = m.com.root;
	frame = frame + 1;
};


//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.lincurve(0,2.5,0.5,0.05,-3);
	var move = m.accelMassFiltered.lincurve(0,1.5,1,notes.size,-1);
	var amp = m.accelMassFiltered.lincurve(0,1.5,-40,-2,-1);
	octave = m.gyroYFiltered.lincurve(-1.0,1.0,3,8,-1).asInteger;
	
	if(amp < -38, {amp = -100});
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\range, move.floor);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\root, root-2);
	Pdef(m.ptn).set(\octave, octave);

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
[m.gyroYFiltered];
	// X axis
	// [d.sensors.gyroEvent.x/pi]; // norm

	// Y axis
	// [d.sensors.gyroEvent.y/pi]; // norm

	// Z axis
	// [d.sensors.gyroEvent.z/(pi/2)]; // norm

	// device [I• ]
	// [(d.sensors.gyroEvent.x/pi).linlin(-0.8,0.8,0.9,-0.9)]  //up down
	// [(d.sensors.gyroEvent.y/pi).linlin(-0.4,0.4,0.9,-0.9)]  //left right
	// [(d.sensors.gyroEvent.z/(pi/2)).linlin(-0.3,1.0,-0.9,0.9)]  //wrist rotate


};




