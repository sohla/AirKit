var m = ~model;
var group;
var samplesLib;

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

var folder = PathName("~/Downloads/cotf_samples/Celesta_ES_mf");

var step = 0;

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

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.99;

//------------------------------------------------------------
SynthDef(\celesteVoice, {|out=0, bufnum=0, amp=0.2, rate=1, start=0, pan=0,
    attack=0.005, sustain=0.4, release=1.6|
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum,
		rate: rate * BufRateScale.kr(bufnum),
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	Out.ar(out, Balance2.ar(sig[0], sig[1], pan, amp * env));
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({ |sample| (sample.midiNote - targetMidi).abs });
		(buffer: closest.buffer, rate: (targetMidi - closest.midiNote).midiratio)
	};

	//--------------------------------------------------------
	~vdef.(\gongPoint, { |ev, c|
		var mod = ev[\modulation] ? ();
		var rest = mod[\rest] ? false;
		var head = mod[\head] ? 26;
		var spoke = mod[\spoke] ? 90;
		var decay = mod[\decay] ? 0.35;
		var t = c[\normTime];
		var mid = c[\pos];
		var ring = c[\size];
		var pos = mid + (ring @ 0);
		var a = exp(t.neg / decay);
		var reach = spoke * a;

		if(rest.not, {
			c[\draw].(\circle, (pos: pos, size: head * (1 + (2 * (1 - a)))), a, a);
			c[\draw].(\line, (pos: pos + ((reach * 0.5) @ 0), size: reach * 0.5), a, a, false);
		});
		nil
	});

	samplesLib = folder.entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buffer = Buffer.read(s, path.fullPath, action: { |buf|
			postf("buffer alloc [%] \n", buf);
		});
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
	});
	postf("loading celeste : % samples \n", samplesLib.size);

	Event.addEventType(eventTypeName, { |e|
		var target = ~note + ~root + (12 * ~octave);
		var found = findClosestSample.(target);
		~bufnum = found.buffer;
		~rate = found.rate;
		~type = \customVisualEvent;
		currentEnvironment.play;
	});

	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \celesteVoice,
			\group, group,
			\type, eventTypeName,

			\dur, Pseq([0.5, Rest(0.5), 0.25, 0.25, 0.5, Rest(0.25), 0.75, 0.25, Rest(0.5), 0.5, 0.25] * 0.5, inf),
			\note, Pseq([0, 2, 4, 7, 9], inf),
			\octave, Pseq([6, 6, 7, 6, 5, 6, 7], inf),
			// \root, 0,
			\legato, 0.4,
			\pan, Pwhite(-1.0, 1.0),

			\shape, \gongPoint,
			\cyc, Pfunc({
				var ringSteps = 96;
				var ph = (step % ringSteps) / ringSteps;
				step = step + 1;
				ph
			}),
			\rad, Pfunc({ |e|
				((e[\note] ? 0) + ((e[\octave] ? 6) * 12)).linlin(60, 96, 0.3, 0.8)
			}),
			\rotation, Pfunc({ |e| (e[\cyc] * 2pi) - 0.5pi }),
			\startSize, Pfunc({ |e| e[\rad] * 780 }),
			\endSize, Pkey(\startSize),
			\startColor, Color.new(1.0, 1.0, 1.0, 0.9),
			\endColor, Color.new(0.35, 0.85, 1.0, 0.0),
			\startWidth, 4,
			\endWidth, 0.5,
			\duration, Pfunc({ |e| (e[\release] ? 1.6) * 1.4 }),
			\modulation, Pfunc({ |e| (
				rest: e.isRest,
				head: (e[\amp] ? 0.2).clip(0.01, 1).linexp(0.01, 1, 8, 40),
				spoke: 110,
				decay: ((e[\release] ? 1.6) * 0.22).clip(0.08, 0.6),
				amp: 0
			) }),

			\args, #[],
		);
	);

	Pdef(m.ptn).play(quant: 1);
	Pdef(m.ptn).pause;

	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\release, 1.6);
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
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.accelMassFiltered.lincurve(0, 1.4 * sens, -30, -4, -1);
	var release = m.accelMassFiltered.lincurve(0, 1.4 * sens, 2.6, 0.5, 2);

	if(amp < 29.neg, { amp = 90.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol);
	Pdef(m.ptn).set(\release, release);
	Pdef(m.ptn).set(\root, m.com.root ? 0);

	if(m.accelMassFiltered > 0.03, {
		if(Pdef(m.ptn).isPlaying.not, {
			Pdef(m.ptn).resume(quant: 0.125);
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
	// [m.accelMassFiltered.lincurve(0, 1.4, -60, -14, -1).dbamp];//amp
	// [m.accelMassFiltered.lincurve(0, 1.4, 2.6, 0.5, 2) / 2.6];//release
	// [(m.accelMassFiltered > 0.03).binaryValue];//play gate

	[m.accelMass, m.accelMassFiltered];
};
