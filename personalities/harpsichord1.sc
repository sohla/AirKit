var m = ~model;
var group;
var samplesLib;

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

var folder = PathName("~/Downloads/cotf_samples/Harpsichord");

var chordPool = [
	[ 0,7,12],
	[ 0,9,14],
	[0,5,12],
	// [-1,  2,  7],
];
// var chordPool = [0,11,4,7];

//------------------------------------------------------------
var noteToMidi = { |noteName|
	var pattern = "([A-G](#|b)?)([0-9])";
	var noteNames = "C C# D D# E F F# G G# A A# B";
	var parts, note, octave, noteIndex;
	parts = noteName.findRegexp(pattern);
	if(parts.size < 3, { Error("Invalid note format: %".format(noteName)).throw });
	note = parts[1][1];
	octave = parts[3][1].asInteger;
	note = note.replace("Cb", "B").replace("Db", "C#").replace("Eb", "D#")
		.replace("Fb", "E").replace("Gb", "F#").replace("Ab", "G#").replace("Bb", "A#");
	noteIndex = noteNames.split($ ).find([note]);
	(octave + 1) * 12 + noteIndex;
};

var parseHarpsichordNote = { |fileStem|
	var parts = fileStem.findRegexp("([0-9])[ ]*([a-g](#|b)?)$");
	if(parts.size < 3, { Error("Invalid harpsichord note format: %".format(fileStem)).throw });
	noteToMidi.(parts[2][1].toUpper ++ parts[1][1]);
};

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\harpsiVoice, {|out=0, bufnum=0, amp=0.2, rate=1, start=0, pan=0,
    attack=0.003, sustain=0.3, release=2.0, freq = 440|
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum,
		rate: rate * BufRateScale.kr(bufnum) * 0.98,
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	var mix = sig * amp * env;
	Out.ar(out, mix);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({ |sample| (sample.midiNote - targetMidi).abs });
		(buffer: closest.buffer, rate: (targetMidi - closest.midiNote).midiratio)
	};

	samplesLib = folder.entries.collect({ |path|
		var midiNote = parseHarpsichordNote.(path.fileNameWithoutExtension);
		var buffer = Buffer.read(s, path.fullPath, action: { |buf|
			postf("buffer alloc [%] \n", buf);
		});
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: midiNote)
	});
	postf("loading harpsichord : % samples \n", samplesLib.size);

	Event.addEventType(eventTypeName, { |e|
		var target = ~note + ~root + (12 * ~octave);
		if(target.isArray) {
			~bufnum = target.collect({ |n| findClosestSample.(n).buffer });
			~rate   = target.collect({ |n| findClosestSample.(n).rate });
		} {
			var found = findClosestSample.(target);
			~bufnum = found.buffer;
			~rate = found.rate;
		};
		~type = \customVisualEvent;
		currentEnvironment.play;
	});

	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \harpsiVoice,
			\group, group,
			\type, eventTypeName,

			\note, Pfunc({ |e| chordPool.wrapAt(e[\chordIdx] ? 0) }),
			// \root, 0,
			\octave, Pseq([6,7,8].stutter(2), inf),
			// \strum, 0.2,
			// \dur, 0.2,
			\legato, 1.6,
			\pan, Pwhite(-0.25, 0.25),

			\shape, \triangle,
			\fill, false,
			\sx, Pwhite(-0.05, 0.05),
			\sy, Pwhite(-0.05, 0.05),
			\ex, Pkey(\sx),
			\ey, Pkey(\sy),
			\duration, 2.1,

			\args, #[],
		);
	);

	Pdef(m.ptn).play(quant: 1);
	Pdef(m.ptn).pause;

	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\chordIdx, 0);
	Pdef(m.ptn).set(\octave, 5);
	Pdef(m.ptn).set(\strum, 0);
	Pdef(m.ptn).set(\rotation, 0);
	Pdef(m.ptn).set(\startSize, 20);
	Pdef(m.ptn).set(\endSize, 120);
	Pdef(m.ptn).set(\startWidth, 4);
	Pdef(m.ptn).set(\endWidth, 0.3);
	Pdef(m.ptn).set(\startColor, Color.hsv(0, 1, 1, 0.8));
	Pdef(m.ptn).set(\endColor, Color.hsv(0, 1, 0.4, 0.0));
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);

	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
		if (samplesLib.notNil) {
			samplesLib.do({ |sample|
				postf("buffer dealloc [%] \n", sample.buffer);
				sample.buffer.free;
				s.sync;
			});
			samplesLib = nil;
		};
	};
};

//------------------------------------------------------------
~next = {|d|
	var chordIdx = (d.sensors.gyroEvent.y / pi.half).linlin(-1, 1, 0, chordPool.size - 1).round.asInteger;
	var strum = (d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5).abs.linlin(0, 0.5, 0.2, 0.01);
	var amp = m.accelMassFiltered.lincurve(0, 0.3, -70, -15, -1).dbamp;
	// var oct = m.accelMassFiltered.lincurve(0, 1.6, 5, 6, 1).round.asInteger;
	var hue = chordIdx / chordPool.size;
	var dur = m.accelMassFiltered.lincurve(0, 1.6, 0.4, 0.1, -1);

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\chordIdx, chordIdx);
	// Pdef(m.ptn).set(\strum, dur/2);
	Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\root, m.com.root ? 0);

	// Pdef(m.ptn).set(\octave, oct);

	Pdef(m.ptn).set(\rotation, chordIdx / chordPool.size * 2pi);
	Pdef(m.ptn).set(\startSize, 20 + (amp * 160));
	Pdef(m.ptn).set(\endSize, 90 + (amp * 500));
	Pdef(m.ptn).set(\startWidth, 2 + (amp * 14));
	Pdef(m.ptn).set(\startColor, Color.hsv(hue, 0.85, 1.0, 0.8));
	Pdef(m.ptn).set(\endColor, Color.hsv(hue, 0.85, 0.35, 0.0));

	if(m.accelMassFiltered > 0.02, {
		if(Pdef(m.ptn).isPlaying.not, {
			Pdef(m.ptn).resume(quant: 0.5);
		});
	}, {
		if(Pdef(m.ptn).isPlaying, {
			Pdef(m.ptn).pause();
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, magenta, cyan]

	// RAW values
	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// Gyro
	// [(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];//left right

	// MODEL values
	// Acceleration
	// [m.accelMass, m.accelMassFiltered].lincurve(0.0,5.0,0.0,1.0,0);
	// Rotation Rate
	// [m.rrateMass, m.rrateMassFiltered].lincurve(0.0,1.0,0.0,1.0,0);

	// COMPUTED values : this file's own ~next, recomputed
	// [(d.sensors.gyroEvent.y / pi.half).linlin(-1, 1, 0, chordPool.size - 1).round / (chordPool.size - 1)];//chordIdx
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5).abs.linlin(0, 0.5, 0, 0.07) * 14];//strum
	// [m.accelMassFiltered.lincurve(0, 1.6, -70, -13, -1).dbamp];//amp
	// [(m.accelMassFiltered > 0.02).binaryValue];//play gate

	[(d.sensors.gyroEvent.y / pi.half)];//up down
};
