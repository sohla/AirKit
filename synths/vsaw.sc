(
SynthDef(\vsaw, {
    |out =0,freq = 2000, amp = 0.3, pan = 0, gate=1, attack=0.02, decay=1.0|

	var li = LocalIn.ar(1);
	var click = Line.ar(40,1,freq.reciprocal);
	var sub = SinOsc.ar(freq * 0.5 * click, 0, 0.1)!2;
    var sig = 	SplayAz.arFill(4,4,{|i|
		VarSaw.ar(
			freq + ( (i+1).reciprocal),
			i,
			 i * 0.01,
			0.2)
		});

	LocalOut.ar(sub);

	sig = sig * EnvGen.kr(Env.perc(attack, decay), gate, doneAction:2);
	sig = sig + (sub * EnvGen.kr(Env.perc(0.002, decay * 0.9)));

    sig = sig * amp;
	Out.ar(out, sig);
}).add;
)

(
Pbindef(\magpiePattern,
    \instrument, \vsaw,
    \dur, 0.2,
	\note, Pseq([20,27,32,39,44,49,55,60,61,63] - 50, inf).trace,
	\root, Pseq([0,4,-2,-4,2].stutter(8), inf),
    \amp, Pkey(\note).linexp(-30,13,2.0,0.5),
    \pan, Pwhite(-1.0, 1.0, inf),
	\attack, Pkey(\note).linlin(-30,13,0.001,0.2),
	\decay, Pkey(\note).linlin(-30,13,0.5,2.2),
).play;
)