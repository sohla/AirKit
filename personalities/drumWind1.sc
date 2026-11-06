var m = ~model;
var group;
var wind;
var buffers;
var loaded = 0;
var kickIdx = 0;

var kickNames = ["drum", "kick", "bd"];

var beat = 0.5;
var divs = [2,3,4,6];

var rates = [1, 1, 1, 0.5, 2, 1.5, -1] * 1;

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
SynthDef(\drumWind1Kit, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0,
    attack=0.005, decay=0.1, sustain=0.8, release=0.02, gate=1, cutoff=50, rq=0.01|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.1],
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
	Out.ar(out, Mix(sig) * amp * env);
}).add;

SynthDef(\dpulse, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0,
    attack=0.005, decay=0.1, sustain=0.8, release=0.02, gate=1, cutoff=50, rq=0.01, sd = 1|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = LFPulse.ar(1);
	Out.ar(out, sig * amp * env);
}).add;

SynthDef(\drumWind1Wind, {

	|out=0, amp=0.5, density=0.5, strength=0.5,
     filterFreq=1000, filterQ=0.5,
     reverbMix=0.6, reverbRoom=0.5, reverbDamp=0.2, gate=0, my=0.5, mx=1x|

    var wind, filtered, reverbed;
    var densityMod, strengthMod;
	var env = EnvGen.ar(Env.asr(1.3,1.0,5.0), gate, doneAction:Done.freeSelf);

    // Modulate density and strength
	densityMod = LFNoise2.kr(0.1).range(0.8, 1.2) * mx.lag(5);
	strengthMod = LFNoise2.kr(0.2).range(0.8, 1.2) * my.lag(3.5);

    // Base wind sound
	wind = [WhiteNoise.ar(1),WhiteNoise.ar(1)] * 1;

    // Apply density control (affects graininess of the wind)
    wind = Dust.ar(densityMod * 10000) * wind * [1, pi];	

    // Apply strength control (affects amplitude and filter)
    wind = wind * strengthMod;

    // Apply resonant filter
	filtered = RLPF.ar(wind, filterFreq.lag(3)  * strengthMod.linexp(0, 1, 0.5, 2), filterQ);

    // Apply reverb
    reverbed = FreeVerb.ar(filtered, reverbMix, reverbRoom, reverbDamp);


	Out.ar(out, reverbed * amp.lag(2) * env);

}).add;

//------------------------------------------------------------
~init = ~init <> {
	var folder = PathName(kitFolder);

	group = Group.new;
	wind = Synth.head(s, \drumWind1Wind, [\gate, 1]);
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
			\instrument, \drumWind1Kit,
			\group, group,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx)),
			\dur,  Pkey(\div).reciprocal * beat,
			\note, 0,
			\octave, 5,
			\out, Pseq([0,1,2,3], inf),

			\pick, Pfunc({ |e| if(e[\step] == 0, { kickIdx }, { (e[\pal] ? 1).rand }) }),
			\bufnum, Pfunc({ |e| buffers[e[\pick].clip(0, buffers.size - 1)] }),

			\amp, Pkey(\energy) * Pfunc({ |e| if(e[\step] == 0, { 1.0 }, { 0.55 }) }),
			\rate, 0.53,//Pfunc({ |e| rates.choose * (e[\roll] ? 1) }),
			\start, Pfunc({ |e|
				if(e[\rate] < 0, { 0.98 }, { [0, 0.12].wchoose([0.88, 0.12]) })
			}),
			\release, 0.01,//Pkey(\dur) * 1.2,
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
			\startSize, Pkey(\amp) * 200 + 10,
			\endSize, Pkey(\amp) * 200 + 4,
			// \startColor, Pfunc({ |e|
			// 	Color.hsv(e[\pick] / (buffers.size.max(1)), 1, 1, 0.85)
			// }),
			// \endColor, Pfunc({ |e|
			// 	Color.hsv(e[\pick] / (buffers.size.max(1)), 1, 0.4, 0.0)
			// }),
			\startColor, Color.white,
			\endColor, Color.white,
			\startWidth, (Pkey(\amp) * 18) + 1,
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
	var g = group;
	var bufs = buffers;
	var tail = 1.0;
	wind !? { wind.set(\gate, 0) };
	wind = nil;
	group = nil;
	buffers = nil;

	Pdef(m.ptn).remove;
	fork {
		tail.wait;
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
		bufs.do({ |buf|
			postf("buffer dealloc [%] \n", buf);
			buf.free;
			s.sync;
		});
	};
};

//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var e = m.accelMassFiltered;
	var idx = e.lincurve(0, 2.0 * sens, 0, divs.size - 1, -2).round.asInteger.clip(0, divs.size - 1);
	var pal = e.lincurve(0, 1.2 * sens, 1, buffers.size, 1).round.asInteger.clip(1, buffers.size);
	var amp = e.lincurve(0, 2.5 * sens, -10, -5, -1);
	var roll = (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.75, 1.25);
	var cutoff = ((d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5) * 2).lincurve(-1.0, 1.0, 40, 900, 1);
	var windAmp = m.accelMassFiltered.lincurve(0, 6 * sens,0,5,-6);
	var windMy = m.accelMassFiltered.linexp(0, 2 * sens,0.1,0.9);
	var windMx = m.rrateMassFiltered.linlin(0, 2 * sens,0.8,1.0);
	var windFreq = ((d.sensors.gyroEvent.y / 2pi) + 0.5).fold(0,0.5) * 2;

	if(amp< 9.neg, { amp = 90.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\pal, pal);
	Pdef(m.ptn).set(\energy, amp.dbamp * vol);
	Pdef(m.ptn).set(\roll, roll);
	Pdef(m.ptn).set(\cutoff, cutoff);

	windFreq = windFreq.linexp(0,1,600,1600);
	if(windAmp<0.03,{windAmp=0});
	if(windAmp>0.9,{windAmp=0.9});
	wind !? {
		wind.set(\amp, windAmp * 1 * vol);
		wind.set(\my, windMy);
		wind.set(\mx, windMx);
		wind.set(\filterFreq, windFreq);
	};
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
