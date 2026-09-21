var m = ~model;
var group;
var samplesLib;

var beat = 2.0;

var bassDivs = [8, 8, 16];
var bassLines = [
	[ 0, nil, nil, nil, nil, nil,  7, nil ],
	[ 0, nil, nil,   7, nil, nil,  10, nil ],
	[ 0, nil, nil,  12, nil, nil,  10, nil, nil, 7, nil, 5, nil, nil, 4, nil ]
];

var topDivs = [6, 8, 12];
var topLines = [
	[ 0, nil,  7, nil,  10, nil ],
	[ 0, nil,  4,   7, nil,  10, 12, nil ],
	[ 0,   4,  7, nil,  10,   7, 12, nil, 10, 7, 4, nil ]
];

var kitFolder = "~/Downloads/cotf_samples/African Marimba";
var sampleFilter = "Marimba ln f l1x";

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.75;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay  = 0.3;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
SynthDef(\marimbaBeat, { |out = 0, bufnum = 0, amp = 0.5, rate = 1, start = 0, pan = 0,
	attack = 0.002, sustain = 0.04, release = 1.2|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var folder = PathName(kitFolder);
	var mkLayer;

	var parseMarimbaNote = { |fileStem|
		var noteNames = "C C# D D# E F F# G G# A A# B".split($ );
		var parts = fileStem.findRegexp("([0-9])[ ]*([a-g])$");
		var octave = parts[1][1].asInteger;
		((octave + 1) * 12) + noteNames.indexOfEqual(parts[2][1].toUpper)
	};

	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({ |sample| (sample[\midiNote] - targetMidi).abs });
		(buffer: closest[\buffer], rate: (targetMidi - closest[\midiNote]).midiratio)
	};

	group = Group.new;

	samplesLib = folder.entries
		.select({ |path| path.fileName.contains(sampleFilter) })
		.collect({ |path|
			var midiNote = parseMarimbaNote.(path.fileNameWithoutExtension);
			postf("loading sample : % (midi %) \n", path.fileNameWithoutExtension, midiNote);
			(
				midiNote: midiNote,
				buffer: Buffer.read(s, path.fullPath, action: { |buf|
					postf("buffer alloc [%] \n", buf);
				})
			)
		});

	if(samplesLib.size == 0, {
		"[marimbabeat1] no samples matched '%' in % - the pattern will stay silent"
			.format(sampleFilter, kitFolder).warn;
	});

	Event.addEventType(eventTypeName, {
		var found;
		if(samplesLib.size > 0 and: { currentEnvironment.isRest.not }, {
			~note = ~note + ~root + (12 * ~octave) - 5;
			found = findClosestSample.(~note);
			~bufnum = found[\buffer];
			~rate = found[\rate];
			~type = \customVisualEvent;
			currentEnvironment.play;
		});
	});

	~vdef.(\bar, { |ev, c|
		var mod = ev[\modulation] ? ();
		var len = mod[\len] ? 60;
		var mid = c[\pos];
		var half = c[\size];
		[ mid.x @ (mid.y - half), (mid.x + len) @ (mid.y - half),
		  (mid.x + len) @ (mid.y + half), mid.x @ (mid.y + half) ]
	});

	mkLayer = { |o|
		Pbind(
			\instrument, \marimbaBeat,
			\group, group,
			\type, eventTypeName,

			\div,  Pswitch(o[\divs].collect({ |n| Pn(n, n) }),         o[\idx]),
			\step, Pswitch(o[\divs].collect({ |n| Pseries(0, 1, n) }), o[\idx]),
			\seq,  Pswitch(o[\divs].collect({ |n, i| Pn(i, n) }),      o[\idx]),
			\dur,  Pkey(\div).reciprocal * beat,
			\note, Pfunc({ |e| o[\lines][e[\seq]][e[\step]] ?? { Rest() } }),
			\octave, o[\oct],
			\amp, o[\amp],
			\release, o[\ring],
			\start, 0,
			\pan, Pfunc({ |e| ((e[\step] / e[\div] * 2) - 1) * 0.4 }),

			\shape, \bar,
			\fill, true,
			\sx, (Pkey(\step) / Pkey(\div) * 1.6) - 0.8,
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e|
				((e[\note] ? 0) + (e[\root] ? 0) + (12 * (e[\octave] ? 5)))
					.linlin(36, 95, 0.78, -0.78)
			}),
			\ey, Pkey(\sy),
			\startSize, Pfunc({ |e|
				(e[\amp] ? 0.2).clip(0, 1).linlin(0, 1, o[\thick] * 0.35, o[\thick])
			}),
			\endSize, Pkey(\startSize),
			\startWidth, 1,
			\endWidth, 1,
			\startColor, Color.new(o[\hue].red, o[\hue].green, o[\hue].blue, 1.0),
			\endColor, Color.new(o[\hue].red, o[\hue].green, o[\hue].blue, 0.0),
			\colorEnv, Pfunc({ Env([0, 0.55, 0.55, 1], [0.05, 0.85, 0.1]) }),
			\duration, beat,
			\modulation, Pfunc({ |e| (amp: 0, len: (e[\dur] / beat) * 300) }),
		)
	};

	Pdef(m.ptn,
		Ppar([
			mkLayer.((
				lines: bassLines, divs: bassDivs,
				idx: Pkey(\bassIdx), amp: Pkey(\bassAmp), oct: 3,
				thick: 18, ring: 1.6, hue: Color.new(0.95, 0.18, 0.10)
			)),
			mkLayer.((
				lines: topLines, divs: topDivs,
				idx: Pkey(\topIdx), amp: Pkey(\topAmp), oct: Pkey(\topOct),
				thick: 8, ring: 0.6, hue: Color.new(1.0, 0.82, 0.12)
			))
		])
	);

	Pdef(m.ptn).set(\bassIdx, 0);
	Pdef(m.ptn).set(\topIdx, 0);
	Pdef(m.ptn).set(\bassAmp, 0);
	Pdef(m.ptn).set(\topAmp, 0);
	Pdef(m.ptn).set(\topOct, 6);
	Pdef(m.ptn).set(\root, 0);

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
			samplesLib.do({ |sample|
				postf("buffer dealloc [%] \n", sample[\buffer]);
				sample[\buffer].free;
				s.sync;
			});
			samplesLib = nil;
		};
	};
};

//------------------------------------------------------------
~next = { |d|
	var e = m.accelMassFiltered;
	var r = m.rrateMassFiltered;
	var bassIdx = e.lincurve(0, 1.4, 0, bassDivs.size - 1, 1).round.asInteger.clip(0, bassDivs.size - 1);
	var topIdx = r.lincurve(0, 0.9, 0, topDivs.size - 1, 1).round.asInteger.clip(0, topDivs.size - 1);
	var bassAmp = e.lincurve(0, 0.6, -24, -6, -1);
	var topAmp = r.lincurve(0, 0.8, -22, -4, -1);
	var topOct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 5, 6.99, 1).asInteger;

	if(bassAmp < 22.neg, { bassAmp = 90.neg });
	if(topAmp < 20.neg, { topAmp = 90.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\bassIdx, bassIdx);
	Pdef(m.ptn).set(\topIdx, topIdx);
	Pdef(m.ptn).set(\bassAmp, bassAmp.dbamp);
	Pdef(m.ptn).set(\topAmp, topAmp.dbamp);
	Pdef(m.ptn).set(\topOct, topOct);
	Pdef(m.ptn).set(\root, (m.com.root ? 0).wrap(0, 11));
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
	// [m.accelMassFiltered.lincurve(0, 1.4, 0, bassDivs.size - 1, 1).round / (bassDivs.size - 1)];//bassIdx
	// [m.rrateMassFiltered.lincurve(0, 0.9, 0, topDivs.size - 1, 1).round / (topDivs.size - 1)];//topIdx
	// [m.accelMassFiltered.lincurve(0, 0.6, -44, -6, -1).dbamp];//bassAmp
	// [m.rrateMassFiltered.lincurve(0, 0.8, -52, -14, -1).dbamp];//topAmp
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 5, 6.99, 1).floor / 7];//topOct
	// [(m.com.root ? 0).wrap(0, 11) / 11];//root

	[m.accelMass, m.accelMassFiltered];
};
