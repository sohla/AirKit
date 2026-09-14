var m = ~model;
var group;
var buffers;
var loaded = 0;
var kickIdx = 0;

var kickNames = ["drum", "kick", "bd"];

var beat = 0.5;
var divs = [1,4,3];

var rates = [1, 1, 1, 0.5, 2, 1.5, -1];

var kitFolder = "~/Downloads/openLabSamples/kit";

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\multiBeatKit, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0,
    attack=0.005, decay=0.1, sustain=0.8, release=0.02, gate=1, cutoff=50, rq=0.01|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.2],
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = RHPF.ar(sig, cutoff.clip(20, 8000), rq) * 2;
	sig = Compander.ar(sig, sig,
		thresh: -20.dbamp,
		slopeBelow: 1,
		slopeAbove: 0.5,
		clampTime: 0.01,
		relaxTime: 0.01
	);
	// sig = Balance2.ar(sig[0], sig[1], pan);
	Out.ar(out, sig * amp * env);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var folder = PathName(kitFolder);

	group = Group.new;

	postf("loading samples : % \n", kitFolder);

	buffers = folder.entries.collect({ |path, i|
		Buffer.read(s, path.fullPath, action: {|buf|
			loaded = loaded + 1;
			postf("buffer alloc [%] \n", buf);
			if(loaded == folder.entries.size, {
				buffers.sort({ |a, b| a.numFrames < b.numFrames });
				kickIdx = buffers.detectIndex({ |b|
					var name = PathName(b.path).fileName.toLower;
					kickNames.any({ |k| name.contains(k) });
				}) ? 0;
				postf("kit loaded, sorted short -> long. kick is lane % [%]\n",
					kickIdx, PathName(buffers[kickIdx].path).fileName);
			});
		});
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \multiBeatKit,
			\group, group,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx)),
			\dur,  Pkey(\div).reciprocal * beat,
			\note, 0,
			\octave, 5,

			\pick, Pfunc({ |e| if(e[\step] == 0, { kickIdx }, { (e[\pal] ? 1).rand }) }),
			\bufnum, Pfunc({ |e| buffers[e[\pick].clip(0, buffers.size - 1)] }),

			\amp, Pkey(\energy) * Pfunc({ |e| if(e[\step] == 0, { 1.0 }, { 0.55 }) }),
			\rate, Pfunc({ |e| rates.choose * (e[\roll] ? 1) }),
			\start, Pfunc({ |e|
				if(e[\rate] < 0, { 0.98 }, { [0, 0.12].wchoose([0.88, 0.12]) })
			}),
			\release, Pkey(\dur) * 1.2,
			\legato, 0.9,
			\pan, Pwhite(-0.4, 0.4),

			\type, \customVisualEvent,
			\shape, \square,
			\fill, true,
			\sx, (Pkey(\step) / Pkey(\div) * 1.5) - 0.75,
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e| e[\pick].linlin(0, (buffers.size - 1).max(1), 0.65, -0.65) }),
			\ey, Pkey(\sy),
			\rotation, Pfunc({ |e| e[\rate].abs.log2 * 0.25 }),
			\startSize, Pkey(\amp) * 90 + 10,
			\endSize, Pkey(\amp) * 20 + 4,
			\startColor, Pfunc({ |e|
				Color.hsv(e[\pick] / (buffers.size.max(1)), 1, 1, 0.85)
			}),
			\endColor, Pfunc({ |e|
				Color.hsv(e[\pick] / (buffers.size.max(1)), 1, 0.4, 0.0)
			}),
			\startWidth, (Pkey(\amp) * 8) + 1,
			\endWidth, 0.3,
			\duration, Pkey(\dur) * 2.5,

			\args, #[],
		)
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\pal, 1);
	Pdef(m.ptn).set(\energy, 0);
	Pdef(m.ptn).set(\roll, 1);
	Pdef(m.ptn).set(\cutoff, 60);

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
		buffers.do({ |buf|
			postf("buffer dealloc [%] \n", buf);
			buf.free;
		});
		buffers = nil;
	};
};

//------------------------------------------------------------
~next = {|d|
	var e = m.accelMassFiltered;
	var idx = e.lincurve(0, 1.3, 0, divs.size - 1, 1).round.asInteger.clip(0, divs.size - 1);
	var pal = e.lincurve(0, 1.2, 1, buffers.size, 1).round.asInteger.clip(1, buffers.size);
	var amp = e.lincurve(0, 0.5, -10, 0, -1);
	var roll = (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.75, 1.25);
	var cutoff = ((d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5) * 2).lincurve(-1.0, 1.0, 40, 900, 1);

	if(amp< 9.neg, { amp = 90.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\pal, pal);
	Pdef(m.ptn).set(\energy, amp.dbamp);
	Pdef(m.ptn).set(\roll, roll);
	Pdef(m.ptn).set(\cutoff, cutoff);
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
	// [m.accelMassFiltered.lincurve(0, 3.0, 0, divs.size - 1, 1).round / (divs.size - 1)];//divIdx
	// [m.accelMassFiltered.lincurve(0, 1.2, 1, buffers.size, 1).round / buffers.size.max(1)];//pal
	// [m.accelMassFiltered.lincurve(0, 1.0, -10, 0, -1).dbamp];//energy
	// [(d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.75, 1.25) - 1];//roll
	// [((d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5) * 2).lincurve(-1.0, 1.0, 40, 900, 1) / 900];//cutoff

	[m.accelMass, m.accelMassFiltered];
};
