var m = ~model;
var group;

var beat = 1.0;
var divs = [4, 8,16];
var pool = [11, 9, 7, 2, 7, 11, 9, 2] + 5 + 12;
// var pool = [0];

var partials       = [0.5, 1, 2.76, 5.4, 8.93, 13.34];
var partialAmps    = [0.7, 1, 0.55, 0.32, 0.18, 0.09];
var partialTimes   = [1.0, 0.9, 0.62, 0.4, 0.24, 0.13];
var partialStretch = [0, 0, 0.012, 0.024, 0.038, 0.055];

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.88;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay  = 0.3;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
SynthDef(\beatBell, { |out = 0, freq = 440, amp = 0.2, pan = 0,
	ring = 3.0, hard = 0.4, inharm = 0.3, warble = 0.0014, gate = 1|

	var strike = Env.perc(0.0002, 0.004 + (0.02 * (1 - hard))).ar(0);
	var hammer = PinkNoise.ar(1) * strike;
	var knock = BPF.ar(hammer, 1800 + (hard * 4200), 0.5) * (0.6 + hard);
	var exciter = HPF.ar(hammer + knock, 180) * 0.7;

	var bell = DynKlank.ar(`[
		freq * partials * (1 + (inharm * partialStretch)),
		partialAmps,
		partialTimes * ring
	], exciter, freqscale: 1 + (warble * [-1, 1])).sum * 0.3;

	var env = EnvGen.kr(Env.perc(0.002, ring, curve: -4.5), gate, doneAction: 2);
	var sig = LeakDC.ar(LPF.ar(bell, 9000)) * env * amp;

	Out.ar(out, Pan2.ar(sig, pan));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;

	~vdef.(\chime, { |ev, c|
		var mod = ev[\modulation] ? ();
		var rest = mod[\rest] ? false;
		var head = mod[\head] ? 30;
		var inharm = mod[\inharm] ? 0.3;
		var ring = mod[\ring] ? 3;
		var dialAlpha = mod[\dialAlpha] ? 0.16;
		var handAlpha = mod[\handAlpha] ? 0.5;
		var t = c[\elapsed];
		var mid = c[\pos];
		var rad = c[\size];
		var pos = mid + (rad @ 0);

		if(rest.not, {
			c[\draw].(\arc, (pos: mid, size: rad), 1, dialAlpha, false);
			c[\draw].(\line, (pos: mid + ((rad / 2) @ 0), size: rad / 2),
				1, handAlpha * exp(t.neg / (ring * 0.5)), false);

			partials.do { |ratio, i|
				var f = ratio * (1 + (inharm * partialStretch[i]));
				var a = partialAmps[i] * exp(t.neg / (partialTimes[i] * ring));
				if(a > 0.01, {
					c[\draw].(\circle, (pos: pos, size: head / f), a, a);
				});
			};
		});
		nil
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \beatBell,
			\group, group,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx)),
			\note, Pswitch(divs.collect({ |n| Pseq(pool.keep(n), 1) }), Pkey(\divIdx)),
			\octave, Pwhite(5, 7),
			\root, Pseq([0,-2,3].stutter(32), inf),
			\dur, Pkey(\div).reciprocal * beat,
			\pan, Pwhite(-0.25, 0.25),
			\warble, Pwhite(0.0008, 0.0022),
			\sendGate, false,
			\func, Pfunc({ |e| ~onEvent.(e) }),
			\args, #[],

			\type, \customVisualEvent,
			\shape, \chime,
			\cyc, Pkey(\step) / Pkey(\div),
			\rotation, Pfunc({ |e| (e[\cyc] * 2pi) - 0.5pi }),
			\startSize, Pfunc({ |e|
				((e[\note] ? 0) + (e[\root] ? 0) + ((e[\octave] ? 5) * 12))
					.linlin(60, 74, 150, 230)
			}),
			\endSize, Pkey(\startSize),
			\startWidth, 3,
			\endWidth, 0.6,
			\startColor, Color.new(1.0, 0.76, 0.28, 0.95),
			\endColor, Color.new(0.85, 0.42, 0.06, 0.0),
			\duration, Pfunc({ |e| ((e[\ring] ? 3) * 0.4).clip(0.5, 8) }),
			\modulation, Pfunc({ |e| (
				rest: e.isRest or: { (e[\amp] ? 0) < 80.neg.dbamp },
				amp: 0,
				inharm: e[\inharm] ? 0.3,
				ring: e[\ring] ? 3,
				head: (e[\amp] ? 0.2).clip(0.01, 1).linexp(0.01, 1, 9, 46),
				dialAlpha: 0.16,
				handAlpha: 0.5,
			) }),
		);
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\ring, 3);
	Pdef(m.ptn).set(\hard, 0.3);
	Pdef(m.ptn).set(\inharm, 0.3);
	Pdef(m.ptn).set(\root, 0);

	Pdef(m.ptn).play(quant: 1);
	Pdef(m.ptn).pause;
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
	};
};

//------------------------------------------------------------
~onEvent = { |e|
	m.com.root = e.root;
	m.com.dur = e.dur;
};

//------------------------------------------------------------
~next = { |d|
	var idx = m.accelMassFiltered.lincurve(0, 2.8, 0, divs.size - 1, -1).round.asInteger.clip(0, divs.size - 1);
	var amp = m.accelMassFiltered.lincurve(0, 0.4, -26, -10, 1);
	var ring = m.accelMassFiltered.lincurve(0, 0.5, 5.5, 0.9, 2);
	var hard = m.rrateMassFiltered.lincurve(0, 0.8, 0.05, 1.0, -1);
	var inharm = (d.sensors.gyroEvent.y / pi.half).linlin(-1, 1, 0.0, 1.0);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\ring, ring);
	Pdef(m.ptn).set(\hard, hard);
	Pdef(m.ptn).set(\inharm, inharm);
	Pdef(m.ptn).set(\root, m.com.root ? 0);

	if(amp > 24.neg, {
		if(Pdef(m.ptn).isPlaying.not, { Pdef(m.ptn).resume(quant: 0.25) });
	}, {
		if(Pdef(m.ptn).isPlaying, { Pdef(m.ptn).pause });
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
	// [m.accelMassFiltered.lincurve(0, 1.8, 0, divs.size - 1, 1).round / (divs.size - 1)];//divIdx
	// [m.accelMassFiltered.lincurve(0, 1.2, -46, -12, -1).dbamp];//amp
	// [m.accelMassFiltered.lincurve(0, 1.5, 5.5, 0.9, 2) / 5.5];//ring
	// [m.rrateMassFiltered.lincurve(0, 0.8, 0.05, 1.0, -1)];//hard
	// [(d.sensors.gyroEvent.y / pi.half).linlin(-1, 1, 0.0, 1.0)];//inharm

	[m.accelMass, m.accelMassFiltered];
};
