var m = ~model;
var group;
var fxBus;
var plate;
var buffers;
var loaded = 0;
var kickIdx = 0;
var shaker;
var shakerPtn = (m.ptn ++ "_shaker").asSymbol;

var kickNames = ["drum", "kick", "bd"];

var beat = 1;
var divs = [4,8,12,16];
// var divs = [4];
var burst = 4;
var rest = 0.5;

var rates = [1.5, 1.5, -2, 1.5, 2, 1.5, -2] * 1;

var kitFolder = "~/Downloads/openLabSamples/kit";

// TODO make IMU more 


//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\multiBeat43Kit, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0,
    attack=0.005, decay=0.1, sustain=0.8, release=0.02, gate=1, cutoff=50, rq=0.001, dblTime=0.0625, dblAmp=0|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.1],
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = RHPF.ar(sig, cutoff.clip(20, 8000), rq, 20) + sig	;
	sig = Compander.ar(sig, sig,
		thresh: -20.dbamp,
		slopeBelow: 1,
		slopeAbove: 0.5,
		clampTime: 0.01,
		relaxTime: 0.01
	);
	sig = sig * amp * env;
	FreeSelf.kr(TDelay.kr(Done.kr(env), dblTime));
	Out.ar(out, sig + (DelayN.ar(sig, 0.5, dblTime) * dblAmp));
}).add;

SynthDef(\multiBeat4Shaker, {|bufnum=0, out=0, amp=0.2, rate=1, pan=0, start=0, attack=0.03, decay=0.25, cutoff=4000|
	var env = EnvGen.kr(Env.perc(attack, decay, 1, -4), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate * BufRateScale.kr(bufnum), startPos: start * BufSampleRate.kr(bufnum), loop: 0);
	sig = HPF.ar(sig, cutoff.clip(200, 16000));
	sig = Balance2.ar(sig[0], sig[1], pan);
	Out.ar(out, sig * amp * env);
}).add;

// SynthDef(\dpulse, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0,
//     attack=0.005, decay=0.1, sustain=0.8, release=0.02, gate=1, cutoff=50, rq=0.001, sd = 1|
// 	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
// 	var sig = LFPulse.ar(1);
// 	Out.ar(out, sig * amp * env);
// }).add;

SynthDef(\multiBeat4Plate, { |in = 0, out = 0, mix = 0.5, predelay = 0.01, room = 0.7, damp = 0.2, lowcut = 300, hicut = 9000, thresh = 0.02, hold = 0.25, release = 0.03, amp = 1, gate = 1|
	var dry = In.ar(in, 2);
	var env = EnvGen.kr(Env.asr(0.01, 1, 0.5), gate, doneAction: 2);
	var key = Amplitude.kr(dry.sum, 0.001, 0.01);
	var shut = EnvGen.kr(Env([0, 1, 1, 0], [0.001, hold, release]), key > thresh);
	var wet = DelayN.ar(dry, 0.2, predelay);
	wet = 4.collect({ |i| AllpassN.ar(_, 0.05, [0.0047, 0.0053] * (i + 1) * 1.3, 0.08) }).inject(wet, { |sig, ap| ap.(sig) });
	wet = FreeVerb2.ar(wet[0], wet[1], 1, room, damp);
	wet = LPF.ar(HPF.ar(wet, lowcut), hicut);
	Out.ar(out, (dry + (wet * mix * shut)) * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var folder = PathName(kitFolder);

	group = Group.new;
	fxBus = Bus.audio(s, 2);
	plate = Synth.tail(group, \multiBeat4Plate, [
		\in, fxBus, \out, 0, \mix, 0.11, \predelay, 0.01, \room, 0.3, \damp, 0.3, \lowcut, 300, \hicut, 9000, \thresh, 0.08, \hold, 0.15, \release, 0.03]);
// m.com.bgColor = Color.yellow(0.5);

	postf("loading samples : % \n", kitFolder);

	buffers = folder.entries.collect({ |path, i|
		Buffer.read(s, path.fullPath, action: {|buf|
			loaded = loaded + 1;
			postf("buffer alloc [%] \n", buf);
			if((loaded == folder.entries.size) and: { buffers.notNil }, {
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
			\instrument, \multiBeat43Kit,
			// \dblTime, beat / 8,
			\group, group,

			\div,  Pswitch(divs.collect({ |n| if(n == divs.maxItem, { Pseq((n ! burst) ++ [beat / rest]) }, { Pn(n, n) }) }), Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| if(n == divs.maxItem, { Pseq((0..(burst - 1)) ++ [-1]) }, { Pseries(0, 1, n) }) }), Pkey(\divIdx)),
			\dur,  Pkey(\div).reciprocal * beat,
			\note, 0,
			\octave, 5,
			// \out, Pseq([0,1,2,3], inf),
			\out, Pseq([0], inf) + fxBus.index,

			\pick, Pfunc({ |e| if(e[\step] == 0, { kickIdx }, { (e[\pal] ? 1).rand }) }),
			\bufnum, Pfunc({ |e| buffers[e[\pick].clip(0, buffers.size - 1)] }),

			\amp, Pkey(\energy) * Pfunc({ |e| if(e[\step] == 0, { 1.0 }, { 0.55 }) }),
			// \rate, 0.53 * 2,//Pfunc({ |e| rates.choose * (e[\roll] ? 1) }),
			\rate, Pfunc({ |e| rates.choose * (e[\roll] ? 1) }),
			// \start, Pfunc({ |e|
			// 	if(e[\rate] < 0, { 0.98 }, { [0, 0.12].wchoose([0.88, 0.12]) })
			// }),
			\start,0,
			\release, 0.01,//Pkey(\dur) * 1.2,
			\legato, 0.9,
			\pan, Pwhite(-0.1, 0.1),

			\type, Pfunc({ |e| if(e[\step] < 0, { \rest }, { \customVisualEvent }) }),
			\shape, \square,
			\fill, true,
			// \sx, (Pkey(\step) / Pkey(\div) * 1.5) - 0.75,
			// \ex, Pkey(\sx),
			// \sy, Pfunc({ |e| e[\pick].linlin(0, (buffers.size - 1).max(1), 0.65, -0.65) }),
			// \ey, Pkey(\sy),
			\sx, 0,
			\ex, Pkey(\sx),
			\sy, 0,
			\ey, 1,
			\rotation, Pfunc({ |e| e[\rate].abs.log2 * 0.25 }),
			\startSize, Pkey(\dur) / beat * 200,
			\endSize, 0,
			// \startColor, Pfunc({ |e|
			// 	Color.hsv(e[\pick] / (buffers.size.max(1)), 1, 1, 0.85)
			// }),
			// \endColor, Pfunc({ |e|
			// 	Color.hsv(e[\pick] / (buffers.size.max(1)), 1, 0.4, 0.0)
			// }),
			\startColor, Color.white,
			\endColor, Color.white,
			// \startWidth, (Pkey(\amp) * 18) + 1,
			// \endWidth, 0.3,
			\duration, Pkey(\dur) * 3,

			\args, #[],
		)
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\pal, 1);
	Pdef(m.ptn).set(\energy, 0);
	Pdef(m.ptn).set(\roll, 1);
	Pdef(m.ptn).set(\cutoff, 60);

	Pdef(m.ptn).play(quant: 1);

	shaker = Buffer.read(s, "~/Music/cotf_samples/orchkit/Shaker-Chrom_ES1.wav".standardizePath);

	Pdef(shakerPtn,
		Pbind(
			\instrument, \multiBeat4Shaker,
			\group, group,
			\out, fxBus.index,
			\bufnum, shaker,
			\start, 0.018,
			\dur, Pseq([0.75, 0.75, 0.5,0.75, 0.75, 0.25, 0.25], inf) * beat,
			\accent, Pseq([1.0, 0.45, 0.7,1.0, 0.45, 0.7,0.9], inf),
			\amp, Pkey(\gentle) * Pkey(\accent),
			\decay, Pkey(\dur) * 0.9,
			\pan, Pwhite(-0.3, 0.3),

			\type, \customVisualEvent,
			\shape, \square,
			\numPoints, 64,
			\startSize, Pkey(\accent) * 60,
			\endSize, Pkey(\accent) * 10,
			\startColor, Color.white,
			\endColor, Color.white.alpha_(0),
			\startWidth, Pkey(\accent) * 2,
			\endWidth, 0.3,
			\duration, Pkey(\decay),
			\modulation, Pfunc({ |e| (type: \noise, freq: 11, amp: (e[\rate] ? 1).linlin(0.7, 1.5, 4, 16), harmonics: (e[\cutoff] ? 2000).linlin(500, 4000, 4, 24)) }),

			\args, #[],
		)
	);

	Pdef(shakerPtn).set(\gentle, 0);
	Pdef(shakerPtn).set(\rate, 1);
	Pdef(shakerPtn).set(\cutoff, 2000);

	Pdef(shakerPtn).play(quant: 1);
	m.com.bgColor = Color.black;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var g = group;
	var bufs = buffers;
	var bus = fxBus;
	var shakerBuf = shaker;
	var tail = 1.0;
	group = nil;
	buffers = nil;
	fxBus = nil;
	plate = nil;
	shaker = nil;

	Pdef(m.ptn).remove;
	Pdef(shakerPtn).remove;
	fork {
		tail.wait;
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
		bus !? { bus.free };
		bufs.do({ |buf|
			postf("buffer dealloc [%] \n", buf);
			buf.free;
			s.sync;
		});
		shakerBuf !? { shakerBuf.free };
	};
};

//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var e = m.accelMassFiltered;
	var idx = e.lincurve(0, 1.3 * sens, 0, divs.size - 1, 2).round.asInteger.clip(0, divs.size - 1);
	var pal = e.lincurve(0, 2.2 * sens, 1, buffers.size, 1).round.asInteger.clip(1, buffers.size);
	var amp = e.lincurve(0, 0.6 * sens, -22, -5, -1);
	var roll = (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.75, 1.25);
	var cutoff = ((d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5) * 2).lincurve(-1.0, 1.0, 64, 128, 1);//tune to pitch!!
	var dblAmp = e.lincurve(0, 1.4 * sens, -30, -10, -1);
	var shakerAmp = e.lincurve(0, 0.4 * sens, -20, 3, -2);
	var shakerRate = (d.sensors.gyroEvent.y / pi.half).linlin(-1, 1, 1.5, 0.7);
	var shakerCutoff = (d.sensors.gyroEvent.y / pi.half).linlin(-1, 1, 4000, 500, -2);

	if(amp < 20.neg, { amp = 90.neg });
	// if(shakerAmp < 38.neg, { shakerAmp = 120.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\pal, pal);
	Pdef(m.ptn).set(\energy, amp.dbamp * vol);
	Pdef(m.ptn).set(\roll, roll);
	Pdef(m.ptn).set(\cutoff, cutoff / 2);
	Pdef(m.ptn).set(\dblAmp, dblAmp.dbamp * vol);
	Pdef(m.ptn).set(\dblTime, beat / 4);

	Pdef(shakerPtn).set(\viewID, d.port);
	Pdef(shakerPtn).set(\gentle, shakerAmp.dbamp * vol);
	Pdef(shakerPtn).set(\rate, shakerRate);
	Pdef(shakerPtn).set(\cutoff, shakerCutoff);
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
	// [(d.sensors.gyroEvent.y / pi.half).linlin(-0.3, 0.3, 0.85, 1.15) - 1];//shaker rate
	// [m.accelMassFiltered.lincurve(0, 0.2, 1500, 9000, -2) / 9000];//shaker cutoff

	[m.accelMass, m.accelMassFiltered];
};
