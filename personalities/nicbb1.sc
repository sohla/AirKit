var m = ~model;
var group;
var buffer;

var samplePath = "~/Downloads/nicSamples/bb/nic_18.wav";

var beat = 2.0;

var lowDivs = [4, 4, 8];
var lowLines = [
	[ 0, nil, nil, nil ],
	[ 0, nil, nil,   7 ],
	[ 0, nil, nil,   7, nil, nil,  10, nil ]
];

var midDivs = [6, 8, 12];
var midLines = [
	[ 0, nil,   7, nil,   3, nil ],
	[ 0, nil,   3,   7, nil,  10,   7, nil ],
	[ 0, nil,   3,   7, nil,  10, nil,  12,  10,   7, nil,   3 ]
];

var topDivs = [8, 12, 16];
var topLines = [
	[ nil,   0, nil, nil,   7, nil, nil,   3 ],
	[   0, nil,   3, nil,   7, nil,   3, nil,   0, nil,  10, nil ],
	[   0, nil,   3,   7, nil,  10,   7, nil,   3,   0, nil,  10,   0, nil,   7, nil ]
];

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.8;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay  = 0.3;
m.gyroFilteredAttack      = 0.8;
m.gyroFilteredDecay       = 0.8;

//------------------------------------------------------------
SynthDef(\nicbb, { |out = 0, bufnum = 0, amp = 0.5, rate = 1, pan = 0,
	attack = 0.001, sustain = 0.02, release = 0.4, ffreq = 16000|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env([0, 1, 1, 0], [attack, sustain, release], [0, 0, -4]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, loop: 0);
	var verb = CombN.ar(sig, 0.3, 0.05, 0.1);
	sig = HPF.ar(verb, ffreq);
	sig = Pan2.ar(Mix(sig) * amp * env, pan);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> { |d|
	var mkLayer;

	group = Group.new;

	buffer = Buffer.read(s, PathName(samplePath).fullPath, action: { |buf|
		postf("buffer alloc [%] \n", buf);
	});

	~vdef.(\punch, { |ev, c|
		var mod = ev[\modulation] ? ();
		var len = mod[\len] ? 40;
		var ring = mod[\ring] ? 0;
		var shoulder = mod[\shoulder] ? 0.28;
		var slipAmt = mod[\slip] ? 1.6;
		var seed = mod[\seed] ? 0;
		var h = c[\size];
		var hs = h * shoulder;
		var x0 = c[\pos].x + (sin(seed * 12345.6789) * slipAmt);
		var x1 = x0 + len;
		var x2 = x1 + ring;
		var y = c[\pos].y + (sin((seed + 41) * 9876.54321) * slipAmt);

		[ x0 @ (y - h),  x1 @ (y - h),  x1 @ (y - hs), x2 @ (y - hs),
		  x2 @ (y + hs), x1 @ (y + hs), x1 @ (y + h),  x0 @ (y + h) ]
	});

	mkLayer = { |o|
		Pbind(
			\instrument, \nicbb,
			\group, group,
			\bufnum, Pfunc({ buffer }),
			\type, \customVisualEvent,

			\div,  Pswitch(o[\divs].collect({ |n| Pn(n, n) }),         o[\idx]),
			\step, Pswitch(o[\divs].collect({ |n| Pseries(0, 1, n) }), o[\idx]),
			\seq,  Pswitch(o[\divs].collect({ |n, i| Pn(i, n) }),      o[\idx]),
			\cell, Pfunc({ |e| o[\lines][e[\seq]][e[\step]] ?? { Rest(0) } }),
			\acc,  Pfunc({ |e| (e[\step] % o[\accEvery]) == 0 }),

			\amp, o[\lvl] * Pfunc({ |e| if(e[\acc], { 1.0 }, { 0.42 }) }),
			\release, o[\ring] * Pfunc({ |e| if(e[\acc], { 1.5 }, { 1.0 }) }),
			\dur, Pfunc({ |e|
				var len = beat / e[\div];
				if(e[\amp] < 0.003, { Rest(len) }, { len })
			}),
			\note, Pfunc({ |e| e[\cell].value + (e[\shift] ? 0) + o[\trans] }),
			\rate, Pkey(\note).midiratio * 0.5,
			\ffreq, o[\ffreq],
			\pan, Pwhite(-1,1),//Pfunc({ |e| ((e[\step] / e[\div] * 2) - 1) * o[\spread] }),

			\shape, \punch,
			\fill, Pkey(\acc),
			\sx, Pfunc({ |e| (e[\step] / e[\div] * 1.7) - 0.85 }),
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e| (e[\note] ? 0).linlin(-26, 36, 0.84, -0.84) }),
			\ey, Pkey(\sy),
			\startSize, Pfunc({ |e|
				(e[\amp] ? 0.1).linlin(0, 0.6, o[\thick] * 0.25, o[\thick])
			}),
			\endSize, Pkey(\startSize),
			\startWidth, 1.5,
			\endWidth, 1.5,
			\startColor, Pfunc({ |e| Color.new(1, 1, 1, if(e[\acc], { 1.0 }, { 0.6 })) }),
			\endColor, Color.new(1, 1, 1, 0),
			\colorEnv, Pfunc({ Env([0, 0.08, 1], [0.72, 0.28]) }),
			\duration, beat,
			\modulation, Pfunc({ |e| (
				amp: 0,
				len: (e[\dur].value / beat) * 300,
				ring: ((e[\release] - e[\dur].value).max(0) / beat) * 300,
				shoulder: 0.28,
				slip: 1.6,
				seed: e[\step] + (e[\div] * 31) + o[\trans]
			) })
		)
	};

	Pdef(m.ptn,
		Ppar([
			mkLayer.((
				lines: lowLines, divs: lowDivs, idx: Pkey(\lowIdx), lvl: Pkey(\lowLvl),
				trans: -14, ring: 0.7, ffreq: 200, accEvery: 4,
				thick: 24, spread: 0.15
			)),
			mkLayer.((
				lines: midLines, divs: midDivs, idx: Pkey(\midIdx), lvl: Pkey(\midLvl),
				trans: 1, ring: 0.5, ffreq: 600, accEvery: 3,
				thick: 15, spread: 0.45
			)),
			mkLayer.((
				lines: topLines, divs: topDivs, idx: Pkey(\topIdx), lvl: Pkey(\topLvl),
				trans: 14, ring: 0.16, ffreq: 6000, accEvery: 5,
				thick: 9, spread: 0.7
			)),
			Pbind(
				\instrument, \nicbb,
				\group, group,
				\bufnum, Pfunc({ buffer }),
				\type, \customVisualEvent,
				\amp, 0,
				\release, 0.01,
				\dur, beat,
				\shape, \line,
				\closed, false,
				\rotation, 0.5pi,
				\sx, -0.85,
				\ex, 0.85,
				\startSize, 340,
				\endSize, 340,
				\startWidth, 1,
				\endWidth, 1,
				\startColor, Color.new(1.0, 0.15, 0.1, 0.55),
				\endColor, Color.new(1.0, 0.15, 0.1, 0.55),
				\duration, beat
			)
		])
	);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\lowIdx, 0);
	Pdef(m.ptn).set(\midIdx, 0);
	Pdef(m.ptn).set(\topIdx, 0);
	Pdef(m.ptn).set(\lowLvl, 0);
	Pdef(m.ptn).set(\midLvl, 0);
	Pdef(m.ptn).set(\topLvl, 0);
	Pdef(m.ptn).set(\shift, 0);

	Pdef(m.ptn).play(quant: beat);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

	fork {
		if (group.notNil, {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		});
		if (buffer.notNil, {
			postf("buffer dealloc [%] \n", buffer);
			buffer.free;
			buffer = nil;
		});
	};
};

//------------------------------------------------------------
~next = { |d|
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var e = m.accelMassFiltered;
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0) * 0.1;
	var lowIdx = e.lincurve(0, 1.6 * sens, 0, lowDivs.size - 1, 1).round.asInteger.clip(0, lowDivs.size - 1);
	var midIdx = e.lincurve(0, 2.4 * sens, 0, midDivs.size - 1, 1).round.asInteger.clip(0, midDivs.size - 1);
	var topIdx = e.lincurve(0, 3.2 * sens, 0, topDivs.size - 1, 1).round.asInteger.clip(0, topDivs.size - 1);
	var lowLvl = e.lincurve(0, 1.2 * sens, -34, -6, -1);
	var midLvl = e.lincurve(0.05, 1.6 * sens, -40, -9, -1);
	var topLvl = e.lincurve(0.15, 2.0 * sens, -46, -14, -1);
	var shift = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, -5, 5, 1).round.asInteger;

	if(lowLvl < 32.neg, { lowLvl = 90.neg });
	if(midLvl < 38.neg, { midLvl = 90.neg });
	if(topLvl < 44.neg, { topLvl = 90.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\lowIdx, lowIdx);
	Pdef(m.ptn).set(\midIdx, midIdx);
	Pdef(m.ptn).set(\topIdx, topIdx);
	Pdef(m.ptn).set(\lowLvl, lowLvl.dbamp * vol);
	Pdef(m.ptn).set(\midLvl, midLvl.dbamp * vol);
	Pdef(m.ptn).set(\topLvl, topLvl.dbamp * vol);
	Pdef(m.ptn).set(\shift, shift);
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
	[(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];//left right

	// MODEL values
	// Acceleration
	// [m.accelMass, m.accelMassFiltered].lincurve(0.0,5.0,0.0,1.0,0);
	// Rotation Rate
	// [m.rrateMass, m.rrateMassFiltered].lincurve(0.0,1.0,0.0,1.0,0);

	// COMPUTED values : this file's own ~next, recomputed
	// [m.accelMassFiltered.lincurve(0, 1.6 * d.params.sensitivity.max(0.1), 0, lowDivs.size - 1, 1).round / (lowDivs.size - 1)];//lowIdx
	// [m.accelMassFiltered.lincurve(0, 2.4 * d.params.sensitivity.max(0.1), 0, midDivs.size - 1, 1).round / (midDivs.size - 1)];//midIdx
	// [m.accelMassFiltered.lincurve(0, 3.2 * d.params.sensitivity.max(0.1), 0, topDivs.size - 1, 1).round / (topDivs.size - 1)];//topIdx
	// [m.accelMassFiltered.lincurve(0, 1.2 * d.params.sensitivity.max(0.1), -34, -6, -1).dbamp];//lowLvl
	// [m.accelMassFiltered.lincurve(0.05, 1.6 * d.params.sensitivity.max(0.1), -40, -9, -1).dbamp];//midLvl
	// [m.accelMassFiltered.lincurve(0.15, 2.0 * d.params.sensitivity.max(0.1), -46, -14, -1).dbamp];//topLvl
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, -5, 5, 1).round / 5];//shift

	// [m.accelMass, m.accelMassFiltered];
};
