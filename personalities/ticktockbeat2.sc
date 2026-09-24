var m = ~model;
var group;
var buffers;

var beat = 1.0;
var divs = [4, 8, 16];

var kitFolder = "~/Downloads/yourDNASamples/ticktock";
var kit = [
	"big clock tick", "big clock tok",
	"tick",           "tock",
	"tick2",          "tock2",
	"woodblok tick",  "woodblok tok"
];

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.88;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay  = 0.3;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
SynthDef(\tickTockPulse, { |out = 0, bufnum = 0, amp = 0.5, rate = 1, start = 0, pan = 0,
	attack = 0.001, release = 0.3|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env([0, 1, 1, 0], [attack, release, 0.03]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: 0) * 5;
	sig = Compander.ar(sig, sig,
		thresh: -18.dbamp,
		slopeBelow: 1,
		slopeAbove: 0.4,
		clampTime: 0.004,
		relaxTime: 0.02
	);
	sig = Balance2.ar(sig[0], sig[1], pan);
	Out.ar(out, sig * amp * env);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var folder = PathName(kitFolder);
	var wavs = folder.entries.select({ |p| p.extension.toLower == "wav" });

	group = Group.new;

	postf("loading samples : % \n", kitFolder);

	buffers = kit.collect({ |stem|
		var path = wavs.detect({ |p| p.fileNameWithoutExtension == stem });
		if(path.isNil, {
			"[ticktockbeat2] missing sample '%' in %".format(stem, kitFolder).warn;
			path = wavs[0];
		});
		Buffer.read(s, path.fullPath, action: { |buf| postf("buffer alloc [%] \n", buf) })
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \tickTockPulse,
			\group, group,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),         Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }), Pkey(\divIdx)),
			\dur,  Pkey(\div).reciprocal * beat,

			\slot, Pfunc({ |e|
				if(e[\step] == 0, { 0 }, { (2 * (e[\clock] ? 1)) + (e[\step] % 2) })
			}),
			\bufnum, Pfunc({ |e| buffers[e[\slot]] }),

			\amp, Pkey(\energy) * Pfunc({ |e| if(e[\step] == 0, { 1.0 }, { 0.6 }) }),
			\release, Pkey(\dur) * 1.4,
			\start, 0,
			\pan, Pfunc({ |e| ((e[\step] / e[\div] * 2) - 1) * 0.5 }),

			\type, \customVisualEvent,
			\shape, \square,
			\fill, Pfunc({ |e| e[\slot].even }),
			\sx, (Pkey(\step) / Pkey(\div) * 1.8) - 0.9,
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e|
				e[\slot].div(2).linlin(0, 3, -0.55, 0.55) + if(e[\slot].even, { -0.05 }, { 0.05 })
			}),
			\ey, Pkey(\sy),
			\startSize, Pfunc({ |e| (e[\amp] ? 0.2).clip(0, 1).linlin(0, 1, 4, 14) }),
			\endSize, Pkey(\startSize),
			\startWidth, 2,
			\endWidth, 2,
			\startColor, Pfunc({ |e|
				if(e[\step] == 0, { Color.new(1.0, 0.72, 0.2, 1.0) }, { Color.white })
			}),
			\endColor, Pfunc({ |e|
				if(e[\step] == 0, { Color.new(1.0, 0.72, 0.2, 0.0) }, { Color.new(1, 1, 1, 0.0) })
			}),
			\colorEnv, Pfunc({ Env([0, 0, 1], [0.85, 0.15]) }),
			\duration, beat,
		)
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\energy, 0);
	Pdef(m.ptn).set(\clock, 1);
	Pdef(m.ptn).set(\rate, 1);

	Pdef(m.ptn).play(quant: 1);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
		if (buffers.notNil) {
			buffers.do({ |buf|
				postf("buffer dealloc [%] \n", buf);
				buf.free;
				s.sync;
			});
			buffers = nil;
		};
	};
};

//------------------------------------------------------------
~next = { |d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var idx = m.accelMassFiltered.lincurve(0, 5.6 * sens, 0, divs.size - 1, -1).round.asInteger.clip(0, divs.size - 1);
	var amp = m.accelMassFiltered.lincurve(0, 2.4 * sens, -46, -2, -1);
	var clock = (d.sensors.gyroEvent.y / pi.half).linlin(-1, 1, 1, 3.99).asInteger;
	var rate = (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.85, 1.2);

	if(amp < 44.neg, { amp = 90.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\energy, amp.dbamp * vol);
	Pdef(m.ptn).set(\clock, clock);
	Pdef(m.ptn).set(\rate, rate);
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
	// [m.accelMassFiltered.lincurve(0, 2.8, 0, divs.size - 1, -1).round / (divs.size - 1)];//divIdx
	// [m.accelMassFiltered.lincurve(0, 1.2, -46, -12, -1).dbamp];//amp
	// [(d.sensors.gyroEvent.y / pi.half).linlin(-1, 1, 1, 3.99).floor / 4];//clock
	// [(d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.85, 1.2) - 1];//rate

	[m.accelMass, m.accelMassFiltered];
};
