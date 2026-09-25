var m = ~model;
var bi = 0;
var dur = 0.3 ;
var localRoot = 0;
var buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.2;

//------------------------------------------------------------
SynthDef(\drumkitNN, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=0.3, gate=1,cutoff=10, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);
	var cd = BufDur.kr(bufnum);
  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr,lr], startPos: start * BufFrames.kr(bufnum), loop: 0) ;
    sig = RHPF.ar(sig, cutoff, rq);
		sig = Compander.ar(sig, sig,
        thresh: -15.dbamp,
        slopeBelow: 1,
        slopeAbove: 0.5,
        clampTime:  0.01,
        relaxTime:  0.01
	);
	sig = Mix.ar([sig]);
    sig = Balance2.ar(sig[0],sig[1], pan) * amp * env;
    Out.ar(out, ((0)!0 ++ sig));
}).add;

//--------------------------------------
~init = ~init <> {

	var folder = PathName("~/Downloads/nicSamples/3_Bites/Percussive");
	var perimeter = { |t|
		var u = (t * 4).wrap(0, 4);
		var k = (u.frac * 2) - 1;
		switch(u.floor.asInteger,
			0, { k @ -1 },
			1, { 1 @ k },
			2, { k.neg @ 1 },
			{ -1 @ k.neg }
		) * 0.8
	};
	var hue = { |e, alpha| if((e[\root] ? 0) == 0, { Color.new(0.69, 0.42, 1.0, alpha) }, { Color.new(1.0, 0.31, 0.85, alpha) }) };
	postf("loading samples : % \n", folder);
	buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
			if(folder.entries.size - 1 == i,{
				"samples loaded".postln;
			});
		});
	});

	~vdef.(\medusa, { |ev, c|
		var mod = ev[\modulation] ? ();
		var arms = mod[\arms] ? 5;
		var reach = mod[\reach] ? 1.6;
		var sway = mod[\sway] ? 0.25;
		var swim = mod[\swim] ? 1.5;
		var n = ev[\numPoints] ? 16;
		var mid = c[\pos];
		var r = c[\size];
		var now = c[\now];
		var fall = r * reach * (1 - (c[\normTime] * 0.4));

		c[\draw].(\arc, (pos: mid, size: r), 1, 1, false);
		arms.do { |k|
			var root = mid + ((r * (((k + 0.5) / arms) * 1.6 - 0.8)) @ 0);
			c[\render].(
				Array.fill(n, { |j|
					var t = j / (n - 1);
					var bend = sin((2pi * swim * now) + (k * 0.9) - (t * 3)) * sway * r * t;
					root + (bend @ (fall * t))
				}),
				0.5, 0.7, false
			);
		};
		nil
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \drumkitNN,			
			\bufnum, Pfunc{
				bi = bi + 1;
				if(bi >= (9),{bi=0});
				buffers[bi];
			},
			\start, 0.03,
			\pan, Pwhite(-0.1,0.1),
			\root, Pseq([0, -2].stutter(64), inf),
			\func, Pfunc({|e|localRoot=e.root}),
			\args, #[],

			\type, \customVisualEvent,
			\shape, \medusa,
			\sx, Pfunc({ perimeter.(bi / 9).x }),
			\sy, Pfunc({ perimeter.(bi / 9).y }),
			\ex, Pfunc({ |e| e[\sx] + (e[\pan] * 0.6) }),
			\ey, Pfunc({ |e| e[\sy] - 0.1 }),
			\duration, Pfunc({ |e| (((e[\dur] ? 0.2) * 0.8) + (e[\release] ? 0.3)).clip(0.15, 2.5) }),
			\startSize, Pfunc({ |e| 90 / (e[\rate] ? 1) }),
			\endSize, Pfunc({ |e| 320 / (e[\rate] ? 1) }),
			\sizeEnv, Pfunc({ |e| var a = ((e[\attack] ? 0.01) / e[\duration]).clip(0.02, 0.5); Env([0, 1, 0.8], [a, 1 - a], [-4, 0]) }),
			\startWidth, 3,
			\endWidth, 0.5,
			\startColor, Pfunc({ |e| hue.(e, (e[\amp] ? 0.5).linlin(0, 1.6, 0.35, 0.95)) }),
			\endColor, Pfunc({ |e| hue.(e, 0) }),
			\numPoints, 16,
			\fill, false,
			\modulation, Pfunc({ |e| (amp: 0, arcSpan: pi, arcStart: pi, arms: 3 + ((bi % 3) * 2), reach: 1.6, sway: 0.25, swim: (e[\rate] ? 1) * 1.5) }),
		)
	);

	Pdef(m.ptn).play(quant:dur);
	Pdef(m.ptn).set(\bufnum, buffers[0]);

};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	{
	buffers.do({|buf|
		buf.free;
		// s.sync;
		postf("buffer dealloc [%] \n", buf);
	});
	}.defer(0.3);
};


//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var rate = m.rrateMassFiltered.linlin(0, 2 * sens,0.6,3);
	var amp = m.accelMassFiltered.lincurve(0, 5 * sens,0.4,1, -2);
	var notes = [0,7,5,10] + localRoot + 5;
	var amps = [2,1,1,1] * 0.8;
	var index = m.gyroYFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,notes.size,-1).asInteger;
	var attack = m.accelMassFiltered.lincurve(0.0, 3 * sens,0.1,0.002,-1);
	var release = m.accelMassFiltered.lincurve(0.0, 3 * sens,4.3,0.001,-1);

	dur = m.accelMassFiltered.lincurve(0, 1.2 * sens,0.2,0.06, -1);

	Pdef(m.ptn).set(\amp, amp * amps[index] * vol);
	Pdef(m.ptn).set(\rate, (notes[index]).midiratio );
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\attack, attack);
	Pdef(m.ptn).set(\release, release);
	Pdef(m.ptn).set(\viewID, d.port);

	if(m.accelMassFiltered > 0.1,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [m.gyroXFiltered.fold(-0.5,0.5).linlin(-0.5,0.5,-10,10).lcurve];

	[m.accelMassFiltered];

};
