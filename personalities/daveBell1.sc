var m = ~model;
var group;
var buffer;

var samplePath = "~/Downloads/yourDNASamples/ding A.wav";
var baseNote = 68;
var partialRatios = [1, 3.01, 5.98];
var partialAmps = [1, 0.44, 0.12];

var melody = [ 0, 7, 12, 19, 12, 7,  0, 16, 24, 16, 12, 4 ] + 4;
// var melody = [ 0] + 4;
var beats  = [ 2, 1,  1,  2,  1, 1, Rest(2), 1,  3,  1,  2, 2 ] * 0.4;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.6;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay  = 0.3;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
SynthDef(\daveBell, { |out = 0, bufnum = 0, freq = 207, basefreq = 207, amp = 0.2, pan = 0,
	attack = 0.003, decay = 0.4, susLevel = 0.6, release = 1.4, gate = 1,
	cutoff = 9000, rq = 0.8|
	var rate = (freq / basefreq) * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.adsr(attack, decay, susLevel, release), gate, doneAction: Done.freeSelf);
	var sig = PlayBuf.ar(2, bufnum, rate, loop: 0);
	sig = RLPF.ar(sig, cutoff, rq);
	sig = Balance2.ar(sig[0], sig[1], pan);
	Out.ar(out, sig * amp * env);
}).add;

//------------------------------------------------------------
~init = ~init <> { |d|
	var path = PathName(samplePath);

	group = Group.new;

	~vdef.(\dingBand, { |ev, c|
		var mod = ev[\modulation] ? ();
		var rest = mod[\rest] ? false;
		var semiPx = mod[\semiPx] ? 5;
		var ring = mod[\ring] ? 1.4;
		var strikeRing = mod[\strikeRing] ? 0.14;
		var n = (ev[\numPoints] ? 16).max(2);
		var onset = c[\pos];
		var reach = c[\size];
		var t = c[\elapsed];
		var head = onset.x + (reach * c[\normTime]);
		var crown = onset.x @ (onset.y - (12 * log2(partialRatios.last) * semiPx));

		if(rest.not, {
			c[\render].(
				Array.fill(n, { |j| onset.blend(crown, j / (n - 1)) }),
				0.6, exp(t.neg / strikeRing), false
			);

			partialRatios.do({ |ratio, i|
				var y = onset.y - (12 * log2(ratio) * semiPx);
				var a = partialAmps[i] * exp(t.neg / (ring / ratio));
				c[\render].(
					Array.fill(n, { |j| (onset.x @ y).blend(head @ y, j / (n - 1)) }),
					partialAmps[i], a, false
				);
			});
		});
		nil
	});

	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action: { |buf|
		postf("buffer alloc [%] \n", buf);

		Pdef(m.ptn,
			Pbind(
				\instrument, \daveBell,
				\group, group,
				\bufnum, buf,
				\basefreq, baseNote.midicps,

				\col, Pseq((0..melody.size - 1), inf),
				\tone, Pseq(melody, inf),
				\beat, Pseq(beats, inf),
				\dur, Pkey(\beat) * Pkey(\pulse),
				\midinote, Pkey(\tone) + baseNote,
				\legato, 0.9,
				\release, 1.4,
				\rq, 0.8,
				\pan, Pfunc({ |e| (e[\col] / (melody.size - 1)).linlin(0, 1, -0.6, 0.6) }),
				\args, #[],

				\type, \customVisualEvent,
				\shape, \dingBand,
				\numPoints, 16,
				\fill, false,
				\closed, false,
				\sx, Pfunc({ |e| (e[\col] / (melody.size - 1)).linlin(0, 1, -0.9, 0.45) }),
				\ex, Pkey(\sx),
				\sy, Pfunc({ |e|
					(e[\midinote] + (e[\ctranspose] ? 0)).linlin(baseNote - 12, baseNote + 36, 0.8, -0.35)
				}),
				\ey, Pkey(\sy),
				\duration, Pfunc({ |e| ((e[\dur] ? 0.3).value * e[\legato]) + e[\release] }),
				\startSize, Pkey(\duration) * 60,
				\endSize, Pkey(\startSize),
				\startWidth, Pfunc({ |e| ((e[\amp] ? 0.1) * 11) + 1.2 }),
				\endWidth, 0.5,
				\startColor, Color.new(0.486, 1.0, 0.627, 0.9),
				\endColor, Color.new(0.486, 1.0, 0.627, 0.0),
				\rotation, 0,
				\modulation, Pfunc({ |e| (
					rest: e.isRest,
					amp: 0,
					semiPx: 5,
					ring: e[\release],
					strikeRing: 0.14,
				) }),
			)
		);

		Pdef(m.ptn).set(\amp, 0);
		Pdef(m.ptn).set(\pulse, 0.22);
		// Pdef(m.ptn).set(\ctranspose, 4);
		Pdef(m.ptn).set(\cutoff, 9000);

		Pdef(m.ptn).play(quant: 0.1);
	});
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
			s.sync;
			buffer = nil;
		};
	};
};

//------------------------------------------------------------
~next = { |d|
	var amp = m.accelMassFiltered.lincurve(0, 1.4, -50, -6, -1);
	var shift = (d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, -12, 12).round;
	var pulse = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 0.34, 0.10, 1);
	var cutoff = m.accelMassFiltered.lincurve(0, 1.4, 1200, 16000, 2);

	if(amp < 44.neg, { amp = 90.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	// Pdef(m.ptn).set(\ctranspose, shift);
	Pdef(m.ptn).set(\pulse, pulse);
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
	// [m.accelMassFiltered.lincurve(0, 1.4, -50, -6, -1).dbamp];//amp
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, -12, 12).round / 12];//shift
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 0.34, 0.10, 1) / 0.34];//pulse
	// [m.accelMassFiltered.lincurve(0, 1.4, 1200, 16000, 2) / 16000];//cutoff

	[m.accelMass, m.accelMassFiltered];
};
