var m = ~model;
var buffer;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;


//------------------------------------------------------------
SynthDef(\stereoSampler1, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: 1,doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.013], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    // sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig * amp * env);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var path = PathName("~/Downloads/yourDNASamples/violin/Violin_04.wav");
	postf("loading sample : % \n", path.fileName);

	~vdef.(\bowRibbon, { |ev, c|
		var mod = ev[\modulation] ? ();
		var env = mod[\env] ? Env([0, 1, 0], [0.01, 1], -4);
		var warp = mod[\warp] ? 2.5;
		var n = ev[\numPoints] ? 48;
		var total = env.duration;
		var reach = c[\normTime].pow(warp.reciprocal);
		var a = c[\posStart];
		var b = c[\posEnd];
		var normal = Polar(1, (b - a).theta + 0.5pi).asPoint;
		var half = c[\size];
		var edge = { |u, side| a.blend(b, u) + (normal * (half * side * env.at(total * u.pow(warp)))) };

		Array.fill(n, { |i| edge.(i / (n - 1) * reach, 1) })
		++ Array.fill(n, { |i| edge.((n - 1 - i) / (n - 1) * reach, -1) })
	});

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		Pdef(m.ptn,
			Pbind(
				\instrument, \stereoSampler1,
				\bufnum, buf,
				// \octave, Pxrand([3], inf),
				// \note, Pxrand([33,35,37], inf),
				\note, Pxrand([33,38,45,52].stutter(4), inf),
				\decay, 0.2,
				\sustain,0.1,
				\release,3.2,
				\rate, 0.25,
				// \dur, Pseq([0.25], inf),
				\args, #[],

				\type, \customVisualEvent,
				\shape, \bowRibbon,
				\bowEnv, Pfunc({ |e|
					var atk = e[\attack] ? 0.01;
					var gate = (e[\sustain] ? 0.1) * TempoClock.default.beatDur;
					var held = Env([0, 1, e[\sustain] ? 0.1], [atk, e[\decay] ? 0.2], -4).at(gate);
					Env([0, 1, held, 0], [atk, (gate - atk).max(0.001), e[\release] ? 3.2], -4)
				}),
				\sx, Pfunc({ |e| (e[\start] ? 0.02).linlin(0.02, 0.09, -1, -0.6) }),
				\ex, 1,
				\sy, Pfunc({ |e| ((e[\note] ? 33) + (((e[\octave] ? 5) - 5) * 12) + 60).linlin(81, 160, 0.85, -0.85) }),
				\ey, Pkey(\sy),
				\startSize, Pfunc({ |e| (e[\amp] ? 0).clip(0, 15).lincurve(0, 15, 8, 110, -2) }),
				\endSize, Pkey(\startSize),
				\startWidth, 1,
				\endWidth, 1,
				\fill, true,
				\closed, true,
				\numPoints, Pfunc({ |e| (e[\dur] ? 0.5).linlin(0.03, 0.5, 16, 48).round.asInteger }),
				\startColor, Pfunc({ |e|
					Color.new(0.87, 0.64, 0.24,
						(e[\amp] ? 0).clip(0, 15).lincurve(0, 15, 0, 0.85, -3)
						* (e[\dur] ? 0.5).linlin(0.03, 0.5, 0.35, 1))
				}),
				\endColor, Color.new(0.62, 0.04, 0.14, 0),
				\duration, Pfunc({ |e| e[\bowEnv].duration * (e[\dur] ? 0.5).linlin(0.03, 0.5, 0.2, 1) }),
				\modulation, Pfunc({ |e| (env: e[\bowEnv], warp: 2.5, amp: 0) }),
			)
		);
		Pdef(m.ptn).play(quant:0.125);
	});
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	postf("buffer dealloc [%] \n", buffer);
	buffer.free;
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var dur = m.accelMassFiltered.linlin(0, 2 * sens,0.5,0.03);
	var start = m.gyroXFiltered.lincurve(0.0,1.0,0.02,0.4,-2);
	var amp = m.accelMassFiltered.lincurve(0, 0.6 * sens,0,1,-2);
	var rate= m.accelMass.linlin(0, 2 * sens,0,2);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,2,7,3).floor;

	if(amp < 0.03, {amp = 0});

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\octave, 2 + oct);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, amp * 15 * vol);
 	Pdef(m.ptn).set(\start, start.linlin(0,1,0.02,0.09));

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	[m.accelMass * 0.2, m.rrateMass, (m.rrateMass + m.accelMass) * 0.2];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMass, m.rrateMassFiltered, d.sensors.rrateEvent.x];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
