var m = ~model;
var group;
var buffers;

var kitFolder = "~/Downloads/yourDNASamples/ticktock";
var kit = [
	"big clock tick", "big clock tok",
	"tick",           "tock",
	"tick2",          "tock2",
	"woodblok tick",  "woodblok tok"
];

var bigTick  = 0, bigTok  = 1;
var tick     = 2, tock    = 3;
var tick2    = 4, tock2   = 5;
var blokTick = 6, blokTok = 7;

var voices = [
	[ tick,    tock,    tick,     tock ],
	[ tick,    tock,    tick,     tock,    tick,     tock ],
	[ bigTick, blokTok, tock,     bigTok,  blokTick, tock ],
	[ bigTick, tick2,   tock2,    tick2,   tock2,    tock,  blokTick, blokTok ],
	[ bigTick, tick,    tick2,    blokTick, bigTok,  tock,  tock2, blokTok, tick2, tock2, blokTick, blokTok ]
];
var durs = [
	[ 4,       4,       4,        4 ],
	[ 3,       1,       4,        3,        1,       4 ],
	[ 4,       2,       2,        4,        2,       2 ],
	[ 2,       1,       1,        1,        1,       Rest(2), 4, 4 ],
	[ 2,       1,       1,        2,        2,       1,     1,     Rest(1), 1, 1, 2, 1 ]
];

var spans  = durs.collect({ |d| d.collect(_.value) });
var starts = spans.collect({ |d| [0] ++ d.integrate.drop(-1) });
var cycles = spans.collect(_.sum);

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.7;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay  = 0.3;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
SynthDef(\tickTockKit, { |out = 0, bufnum = 0, amp = 0.5, rate = 1, start = 0, pan = 0,
	attack = 0.001, release = 0.36|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env([0, 1, 1, 0], [attack, release, 0.03]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: 0) * 4;
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
			"[ticktockbeat1] missing sample '%' in %".format(stem, kitFolder).warn;
			path = wavs[0];
		});
		Buffer.read(s, path.fullPath, action: { |buf| postf("buffer alloc [%] \n", buf) })
	});

	~vdef.(\escape, { |ev, c|
		var mod = ev[\modulation] ? ();
		var rest = mod[\rest] ? false;
		var tooth = mod[\tooth] ? 18;
		var sign = mod[\sign] ? 1;
		var ring = mod[\ring] ? 0.36;
		var trackAlpha = mod[\trackAlpha] ? 0.22;
		var handAlpha = mod[\handAlpha] ? 0.55;
		var t = c[\elapsed];
		var mid = c[\pos];
		var rad = c[\size];
		var strike = exp(t.neg / ring);

		if(rest.not, {
			c[\draw].(\arc, (pos: mid, size: rad), 0.5, trackAlpha, false);
			c[\draw].(\line, (pos: mid + ((rad * 0.5) @ 0), size: rad * 0.5),
				0.7, handAlpha * strike, false);
			c[\draw].(\line,
				(pos: mid + ((rad + (sign * tooth * 0.5)) @ 0), size: tooth * 0.5),
				1, 0.35 + (0.65 * strike), false);
		});
		nil
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \tickTockKit,
			\group, group,

			\seq,  Pswitch(voices.collect({ |v, i| Pn(i, v.size) }),     Pkey(\cell)),
			\pick, Pswitch(voices.collect({ |v| Pseq(v, 1) }),          Pkey(\cell)),
			\unit, Pswitch(durs.collect({ |d| Pseq(d, 1) }),            Pkey(\cell)),
			\step, Pswitch(durs.collect({ |d| Pseries(0, 1, d.size) }), Pkey(\cell)),
			\dur,  Pkey(\unit) * Pkey(\pulse) * 0.5,

			\slot, Pfunc({ |e| (e[\pick] + (2 * (e[\shift] ? 0))).mod(kit.size) }),
			\bufnum, Pfunc({ |e| buffers[e[\slot]] }),
			\span, Pfunc({ |e| spans[e[\seq]][e[\step]] }),
			\cyc, Pfunc({ |e| starts[e[\seq]][e[\step]] / cycles[e[\seq]] }),

			\amp, Pkey(\energy) * Pfunc({ |e| if(e[\step] == 0, { 1.0 }, { 0.62 }) }),
			\release, 0.36,
			\start, 0,
			\pan, Pfunc({ |e| sin(e[\cyc] * 2pi) * 0.55 }),

			\type, \customVisualEvent,
			\shape, \escape,
			\numPoints, 24,
			\fill, false,
			\rotation, Pfunc({ |e| (e[\cyc] * 2pi) - 0.5pi }),
			\startSize, Pfunc({ |e| 296 - (e[\slot].div(2) * 56) }),
			\endSize, Pkey(\startSize),
			\startWidth, (Pkey(\amp) * 9) + 1.5,
			\endWidth, 0.4,
			\startColor, Pfunc({ |e|
				Color.new(0.486, 1.0, 0.627, if(e[\slot].even, { 0.95 }, { 0.6 }))
			}),
			\endColor, Color.new(0.486, 1.0, 0.627, 0.0),
			\duration, Pfunc({ |e| cycles[e[\seq]] * (e[\pulse] ? 0.15) }),
			\modulation, Pfunc({ |e| (
				rest: e.isRest,
				amp: 0,
				arcSpan: e[\span] / cycles[e[\seq]] * 2pi,
				arcStart: 0,
				sign: if(e[\slot].even, { 1 }, { -1 }),
				tooth: (e[\amp] ? 0.2).clip(0.01, 1).linexp(0.01, 1, 10, 46),
				ring: e[\release] ? 0.36,
				trackAlpha: 0.22,
				handAlpha: 0.55,
			) }),
		)
	);

	Pdef(m.ptn).set(\cell, 0);
	Pdef(m.ptn).set(\energy, 0);
	Pdef(m.ptn).set(\pulse, 0.15);
	Pdef(m.ptn).set(\shift, 0);
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
	var e = m.accelMassFiltered;
	var cell = e.lincurve(0, 1.6, 0, voices.size - 1, 1).round.asInteger.clip(0, voices.size - 1);
	var amp = e.lincurve(0, 0.5, -44, -1, -1);
	var pulse = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 0.30, 0.09, 1);
	var shift = (d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0, 3.99).asInteger;
	var rate = (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.8, 1.3);

	if(amp < 42.neg, { amp = 90.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\cell, cell);
	Pdef(m.ptn).set(\energy, amp.dbamp);
	Pdef(m.ptn).set(\pulse, pulse);
	Pdef(m.ptn).set(\shift, shift);
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
	// [m.accelMassFiltered.lincurve(0, 1.6, 0, voices.size - 1, 1).round / (voices.size - 1)];//cell
	// [m.accelMassFiltered.lincurve(0, 0.5, -44, -8, -1).dbamp];//amp
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 0.30, 0.09, 1) / 0.30];//pulse
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0, 3.99).floor / 4];//shift
	// [(d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.8, 1.3) - 1];//rate

	[m.accelMass, m.accelMassFiltered];
};
