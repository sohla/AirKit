var m = ~model;
var group;
var buffer;

var samplePath = "~/Downloads/cotf_samples/orchkit/Shaker-Chrom_ES1.wav";

var beat = 1.0;
var divs = [4, 6, 8];

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.6;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay  = 0.3;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
SynthDef(\shakerVoice, { |out = 0, bufnum = 0, amp = 0.5, rate = 1, start = 0, pan = 0,
	attack = 0.001, release = 0.2, cutoff = 400, rq = 0.9|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.perc(attack, release, curve: -3), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr,
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = RHPF.ar(sig, cutoff.clip(60, 6000), rq);
	Out.ar(out, Balance2.ar(sig[0], sig[1], pan, amp * env));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;

	postf("loading sample : % \n", samplePath);
	buffer = Buffer.read(s, samplePath.standardizePath, action: { |buf|
		postf("buffer alloc [%] \n", buf);
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \shakerVoice,
			\group, group,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),         Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }), Pkey(\divIdx)),
			\dur,  Pkey(\div).reciprocal * beat,

			\accent, Pfunc({ |e|
				var st = e[\step];
				var dv = e[\div];
				case
					{ st == 0 }           { 1.0 }
					{ st == (dv div: 2) } { 0.7 }
					{ st.odd }            { 0.35 }
					{ true }              { 0.5 };
			}),

			\bufnum, Pfunc({ buffer }),
			\amp, Pkey(\energy) * Pkey(\accent),
			\rate, Pfunc({ |e| e[\accent].linlin(0.35, 1.0, 1.06, 0.93) * rrand(0.985, 1.015) }),
			\start, Pwhite(0.0, 0.02),
			\release, Pfunc({ |e| (e[\dur] * 0.85).clip(0.05, 0.35) }),
			\pan, Pwhite(-0.3, 0.3),
			\func, Pfunc({ |e| ~onEvent.(e) }),
			\args, #[],

			\type, \customVisualEvent,
			\shape, \square,
			\fill, Pfunc({ |e| e[\accent] > 0.6 }),
			\sx, (Pkey(\step) / Pkey(\div) * 1.7) - 0.85,
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e| e[\accent].linlin(0.35, 1.0, 0.45, -0.45) }),
			\ey, Pkey(\sy),
			\startSize, Pfunc({ |e| (e[\amp] ? 0.1).clip(0.005, 1.0).linexp(0.005, 1.0, 8, 60) }),
			\endSize, Pkey(\startSize) * 0.4,
			\startWidth, Pfunc({ |e| (e[\accent] * 7) + 1 }),
			\endWidth, 0.3,
			\startColor, Pfunc({ |e| Color.new(1.0, 0.97, 0.88, 0.3 + (e[\accent] * 0.65)) }),
			\endColor, Color.new(0.85, 0.78, 0.5, 0.0),
			\duration, Pkey(\dur) * 3,
		)
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\energy, 0);
	Pdef(m.ptn).set(\cutoff, 400);

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
		if (buffer.notNil) {
			postf("buffer dealloc [%] \n", buffer);
			buffer.free;
			buffer = nil;
		};
	};
};

//------------------------------------------------------------
~onEvent = { |e|
	m.com.dur = e.dur;
};

//------------------------------------------------------------
~next = { |d|
	var idx = m.accelMassFiltered.lincurve(0, 1.6, 0, divs.size - 1, 1)
		.round.asInteger.clip(0, divs.size - 1);
	var amp = m.accelMassFiltered.lincurve(0, 1.2, -24, -1, -1);
	var cutoff = m.accelMassFiltered.lincurve(0, 1.2, 180, 10400, 2);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\energy, amp.dbamp);
	Pdef(m.ptn).set(\cutoff, cutoff);

	if(amp > 23.neg, {
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
	// [m.accelMassFiltered.lincurve(0, 1.6, 0, divs.size - 1, 1).round / (divs.size - 1)];//divIdx
	// [m.accelMassFiltered.lincurve(0, 1.2, -34, -8, -1).dbamp];//energy
	// [m.accelMassFiltered.lincurve(0, 1.2, 180, 1400, 2) / 1400];//cutoff

	[m.accelMass, m.accelMassFiltered];
};
