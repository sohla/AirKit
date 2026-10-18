var m = ~model;
var group;
var samplesLib;
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;
var folder = PathName("~/Downloads/openLabSamples/harp");

var beat = 0.8;
var divs = [1, 2, 4,4,4,4,8];
var notes = [2,4,0,-1,-3].stutter(4);

//------------------------------------------------------------
// harp1's sample naming : HA_ES_mf_<note><octave>.wav
var noteToMidi = { |noteName|
	var pattern = "([A-G](#|b)?)([0-9])";
	var noteNames = "C C# D D# E F F# G G# A A# B";
	var parts, note, octave, noteIndex;
	parts = noteName.findRegexp(pattern);
	if(parts.size < 3, { Error("Invalid note format: %".format(noteName)).throw });
	note = parts[1][1];
	octave = parts[3][1].asInteger;
	note = note.replace("Cb", "B").replace("Db", "C#").replace("Eb", "D#")
		.replace("Fb", "E").replace("Gb", "F#").replace("Ab", "G#").replace("Bb", "A#");
	noteIndex = noteNames.split($ ).find([note]);
	(octave + 1) * 12 + noteIndex;
};

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------

// harp1's sampler, unchanged
SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=1, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1,cutoff=20000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);// * (freq/440.0);
    var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	// sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	// harp1's lookup : samples sit on even midi notes, odd notes pitch up a semitone
	var findSampleBuffer = {|note|
		var bufnum;
		samplesLib.do({|sample|
			if(sample.midiNote == note,{
				bufnum = sample.buffer;
			});
		});
		bufnum
	};

	samplesLib = folder.entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buffer = Buffer.read(s, path.fullPath, action:{ |buf|
			postf("buffer alloc [%] \n", buf);
		});
		postf("loading sample : % \n", path.fileNameWithoutExtension);
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
	});

	Event.addEventType(eventTypeName, { |e|
		var n = currentEnvironment.use { ~midinote.value }.round.asInteger;
		if(n.odd,{
			~bufnum = findSampleBuffer.(n-1);
			~rate = 1.midiratio;
		},{
			~bufnum = findSampleBuffer.(n);
			~rate = 1;
		});
		~type = \customVisualEvent;
		if(~bufnum.notNil, { currentEnvironment.play });
	});

	group = Group.new;

	~vdef.(\spectrumRay, { |ev, c|
		var mod = ev[\modulation] ? ();
		var freq = mod[\freq] ? 220;
		var cut = mod[\cutoff] ? 5000;
		var rq = mod[\rq] ? 0.1;
		var sawLvl = mod[\sawLvl] ? 0.8;
		var pulseLvl = mod[\pulseLvl] ? 0.6;
		var foldLvl = mod[\foldLvl] ? 0.5;
		var partials = mod[\partials] ? 16;
		var lo = mod[\lo] ? 60;
		var hi = mod[\hi] ? 14000;
		var inner = mod[\inner] ? 60;
		var reach = mod[\reach] ? 80;
		var peakClip = mod[\peakClip] ? 3;
		var outer = c[\size];
		var mid = c[\pos];
		var t = c[\normTime];
		var lpg = 1 - ((1 - exp(-4 * t)) / (1 - exp(-4)));
		var rad = { |f| f.explin(lo, hi, inner, outer) };

		c[\render].([mid + (rad.(freq) @ 0), mid + (rad.(freq * partials) @ 0)], 0.5, 0.25 * lpg, false);
		partials.do { |i|
			var k = i + 1;
			var f = freq * k;
			var x = f / cut;
			var gain = (1 / (((1 - x.squared).squared + (rq * x).squared).sqrt)).min(peakClip);
			var lvl = (sawLvl + if(k.odd, pulseLvl, 0) + foldLvl) / k * gain;
			var r = rad.(f);
			var len = reach * lvl * lpg;
			if(len > 0.5, {
				c[\render].([mid + (r @ len.neg), mid + (r @ len)], 1, lpg, false);
			});
		};
		nil
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \stereoSampler,
			\group, group,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),             Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),     Pkey(\divIdx)),
			\dur,  Pkey(\div).reciprocal * beat,

			\accent, Pfunc({ |e|
				var st = e[\step] ? 0, dv = e[\div] ? 1;
				if (st == 0, { 1.0 }, {
					if ((dv > 3) and: { st == (dv div: 2) }, { 0.7 }, { 0.45 })
				})
			}),
			\legato, Pfunc({ |e| if(e[\step] == 0, { 0.5 }, { 0.3 }) }),

			\scale, Scale.minor,
			// \root, 0,
			\pick, Pfunc({ |e| if(e[\step] == 0, { 0 }, { (e[\pal] ? 1).rand }) }),
			\note, Pfunc({ |e| notes[e[\pick].clip(0, notes.size - 1)] }),
			\octave, Pfunc({ |e| if(e[\step] == 0, { 5 }, { [6,7].choose }) }),

			\release, Pkey(\dur) * 6.5,
			\lowpassResonance, 0.1,
			\attack, Pwhite(0.001,0.01),
			\pan, Pwhite(-0.2, 0.2),
			\reverbMix, 0.2, \reverbRoom, 0.3, \reverbDamp, 0.5,

			\amp, Pfunc({ |e| (e[\gestAmp] ? 0.12) * (e[\accent] ? 1.0) }),

			\type, eventTypeName,
			\shape, \spectrumRay,
			\sx, 0, \sy, 0, \ex, 0, \ey, 0,
			\rotation, Pfunc({ |e| ((e[\step] ? 0) / (e[\div] ? 1) * 2pi) - 0.5pi }),
			\startSize, 760,
			\endSize, 760,
			\startWidth, Pfunc({ |e| (e[\accent] ? 0.45) * 7 }),
			\endWidth, Pkey(\startWidth),
			\startColor, Pfunc({ |e|
				var lit = (e[\amp] ? 0.1).explin(0.003, 0.3, 0, 0.95);
				if(e[\step] == 0, {
					Color.new(1.0, 0.78, 0.12, lit)
				}, {
					Color.hsv(0.62, (e[\lowpassCutoff] ? 5000).explin(900, 12000, 0.85, 0.12), 1.0, lit)
				})
			}),
			\endColor, Pkey(\startColor),
			\duration, Pfunc({ |e| (e[\attack] ? 0.001) + (e[\lpgDecay] ? 0.5) }),
			\modulation, Pfunc({ |e| (
				freq: e.use { ~freq.value },
				cutoff: (e[\lowpassCutoff] ? 5000).clip(80, 18000),
				rq: e[\lowpassResonance] ? 0.1,
				sawLvl: 0.8, pulseLvl: 0.6, foldLvl: 0.5,
				partials: 16, lo: 60, hi: 14000, inner: 50, peakClip: 3,
				reach: (e[\amp] ? 0.1).explin(0.003, 0.3, 20, 150),
				amp: 0
			) })
		);
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\pal, 1);
	// Pdef(m.ptn).set(\roll, 1);
	Pdef(m.ptn).set(\gestAmp, 0.12);
	Pdef(m.ptn).set(\lpgDecay, 0.9);
	Pdef(m.ptn).set(\lowpassCutoff, 5000);
	Pdef(m.ptn).set(\pan, 0);

	Pdef(m.ptn).play(quant: beat);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);

	fork {
		if (group.notNil) {
			group.set(\gate, 0);
			1.0.wait;
			group.freeAll;
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
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, -2);
	var e = m.accelMassFiltered;	
	var idx = e.lincurve(0, 2.0 * sens, 0, divs.size - 1, 1).round.asInteger.clip(0, divs.size - 1);
	var pal = e.lincurve(0, 2.0 * sens, 1, notes.size, 1).round.asInteger.clip(1, notes.size);
	var amp = e.lincurve(0.0, 2.0 * sens, -50, -3, -2);
	var roll = (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.75, 1.25);
	var cut = m.rrateMassFiltered.linexp(0.0, 2.0 * sens, 900, 12000);
	var ring = (d.sensors.gyroEvent.y / pi.half).lincurve(-1.0, 1.0, 0.05, 0.9, 2);

	if(amp < 49.neg, { amp = 120.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\pal, pal);
	// Pdef(m.ptn).set(\roll, roll);
	Pdef(m.ptn).set(\gestAmp, amp.dbamp * vol);
	Pdef(m.ptn).set(\lowpassCutoff, cut);
	Pdef(m.ptn).set(\lpgDecay, ring);
	Pdef(m.ptn).set(\root, m.com.root);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, cyan , magenta]??

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;

	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	[m.accelMass, m.accelMassFiltered];
	// [d.sensors.accelEvent.x.abs * d.sensors.accelEvent.y.abs *  m.accelMassFiltered] * 0.5;

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];
  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
