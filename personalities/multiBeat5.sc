var m = ~model;
var group;
var samplesLib;

var beat = 0.5;
var divs = [1, 2, 4, 8];
var pool = [0, 4, 7, 12, 14, 12, 7, 4] + 12;

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\multiBeatSampler, {|bufnum=0, out=0, amp=1, rate=1, ptch=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1, cutoff=20000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum) * ptch;
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.007], startPos: start * BufFrames.kr(bufnum), loop: 0);
	Out.ar(out, sig * amp * env);
}).add;

//------------------------------------------------------------
~init = ~init <> {

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
	var parseMarimbaNote = { |fileStem|
		var parts = fileStem.findRegexp("([0-9])[ ]*([a-g])$");
		if(parts.size < 3, { Error("Invalid marimba note format: %".format(fileStem)).throw });
		noteToMidi.(parts[2][1].toUpper ++ parts[1][1]);
	};

	var sampleFilter = "Marimba ln mf l1x";
	var folder = PathName("~/Downloads/cotf_samples/African Marimba");

	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({ |sample| (sample.midiNote - targetMidi).abs });
		var semitoneDiff = targetMidi - closest.midiNote;
		(buffer: closest.buffer, rate: semitoneDiff.midiratio, shift: semitoneDiff)
	};

	~vdef.(\bar, { |ev, c|
		var mod = ev[\modulation] ? ();
		var lane = mod[\lane] ? 15;
		var shift = mod[\shift] ? 0;
		var ghost = mod[\ghost] ? 0.3;
		var mid = c[\pos];
		var halfLen = c[\size];
		var halfDep = c[\width];
		var slab = { |dy|
			[ (mid.x - halfLen) @ (mid.y + dy - halfDep),
			  (mid.x + halfLen) @ (mid.y + dy - halfDep),
			  (mid.x + halfLen) @ (mid.y + dy + halfDep),
			  (mid.x - halfLen) @ (mid.y + dy + halfDep) ]
		};
		c[\render].(slab.(shift * lane), 1, ghost, true);
		c[\render].(slab.(0), 1, 1, true);
		nil
	});

	samplesLib = folder.entries
		.select({|path| path.fileName.contains(sampleFilter) })
		.collect({ |path|
			var midiNote = parseMarimbaNote.(path.fileNameWithoutExtension);
			var buffer = Buffer.read(s, path.fullPath, action:{|buf|
				postf("buffer alloc [%] \n", buf);
			});
			postf("loading sample : % (midi %) \n", path.fileNameWithoutExtension, midiNote);
			(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: midiNote)
		});

	if(samplesLib.size == 0, {
		"[multiBeat5] no samples matched '%' in % - the pattern will stay silent"
			.format(sampleFilter, folder.fullPath).warn;
	});

	Event.addEventType(eventTypeName, {|e|
		var found;
		if(samplesLib.size > 0, {
			~note = ~note + ~root + (12 * ~octave);
			found = findClosestSample.(~note);
			~bufnum = found.buffer;
			~rate = found.rate;
			~modulation = ~modulation ? ();
			~modulation[\shift] = found.shift;
			~type = \customVisualEvent;
			currentEnvironment.play;
		});
	});

	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \multiBeatSampler,
			\group, group,
			\type, eventTypeName,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx)),
			\note, Pswitch(divs.collect({ |n| Pseq(pool.keep(n), 1) }), Pkey(\divIdx)),
			\dur,  Pkey(\div).reciprocal * beat,
			\octave, Pseq([3,4,5].stutter(4), inf),
			\legato, 0.8,
			\attack, 0.004,
			\release, (Pkey(\dur) * 4) + 0.2,
			\amp, Pkey(\energy) * Pfunc({ |e| if(e[\step] == 0, { 1.0 }, { 0.6 }) }),
			\pan, Pfunc({ |e| ((e[\panBias] ? 0) + rrand(-0.2, 0.2)).clip(-1, 1) }),
			\start, 0,

			\shape, \bar,
			\fill, true,
			\sx, (Pkey(\step) / Pkey(\div) * 1.5) - 0.75,
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e|
				(e[\note] + (e[\root] ? 0) + (12 * (e[\octave] ? 5)))
					.linlin(45, 92, 0.7, -0.7)
			}),
			\ey, Pkey(\sy),
			\rotation, Pfunc({ |e| (e[\ptch] ? 1).log2 * 2 }),
			\startSize, Pkey(\dur) * 380,
			\endSize, Pkey(\dur) * 380,
			\startWidth, (Pkey(\amp) * 22) + 3,
			\endWidth, 1,
			\startColor, Pfunc({ |e|
				Color.hsv(((e[\octave] ? 5) - 4).linlin(0, 2, 0.055, 0.15), 0.85, 1.0, 0.9)
			}),
			\endColor, Pfunc({ |e|
				Color.hsv(((e[\octave] ? 5) - 4).linlin(0, 2, 0.055, 0.15), 1.0, 0.35, 0.0)
			}),
			\duration, (Pkey(\dur) * 4) + 0.2,
			\modulation, Pfunc({ |e| (lane: 15, ghost: 0.3, amp: 0) }),
		);
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\energy, 0);
	Pdef(m.ptn).set(\root, 0);
	Pdef(m.ptn).set(\octave, 5);
	Pdef(m.ptn).set(\ptch, 1);
	Pdef(m.ptn).set(\panBias, 0);

	Pdef(m.ptn).play(quant: 1);
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
			samplesLib.do({|sample|
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
	var e = m.accelMassFiltered;
	var idx = e.lincurve(0, 1.5 * sens, 0, divs.size - 1, 1)
		.round.asInteger.clip(0, divs.size - 1);
	var amp = e.lincurve(0, 0.4 * sens, -41, -10, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 4, 6, 1).asInteger;
	var ptch = (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.94, 1.06);
	var panBias = (d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, -0.5, 0.5);

	if(amp < 40.neg, { amp = 90.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\energy, amp.dbamp * vol);
	Pdef(m.ptn).set(\root, m.com.root ? 0);
	Pdef(m.ptn).set(\octave, oct);
	// Pdef(m.ptn).set(\ptch, ptch);
	Pdef(m.ptn).set(\panBias, panBias);
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
	// [m.accelMassFiltered.lincurve(0, 1.5, 0, divs.size - 1, 1).round / (divs.size - 1)];//divIdx
	// [m.accelMassFiltered.lincurve(0, 1.4, -41, 0 -1).dbamp];//energy
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 4, 6, 1) / 6];//octave
	// [(d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.94, 1.06) - 1];//ptch
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, -0.5, 0.5)];//panBias

	[m.accelMass, m.accelMassFiltered, (d.sensors.gyroEvent.y / pi.half)];
};
