(
SynthDef(\woofy, {|out=0, amp=1, freq=100, gate = 1, dcy = 0.5, hpf = 41|
	    var env, burst, sig;
	    env = EnvGen.kr(Env.adsr(0.01, dcy, 0, dcy), gate: gate, doneAction:2);
	    burst = RLPF.ar(WhiteNoise.ar(), freq, [0.3, 0.55]);
	    // burst = Limiter.ar(burst, 0.1);
	    burst = CombL.ar(burst, 0.5, 0.4, 0.4, 1, add: burst);
	    sig = SinOsc.ar([freq * 2, freq * 2.02] + ((env+1) * 30) - 30, 0, 0.04) + burst;
	    sig  = CompanderD.ar(sig,0.01);
	    sig = HPF.ar(sig, hpf);
	    Out.ar(out, sig * env * amp * (freq.reciprocal * 60) * 5);

	}).add;
)
(

Pbind(
	     \instrument, \woofy,
	\note, Pseq([0,2,4,6,8,10], inf),
	\octave,2,
	    \dur, Pseq([0.4, Rest(0.15), 0.15], inf),
	\amp, 1,
	\dcy, Pwhite(0.2,0.6),
	\hpf, Pwhite(20,100)
	).play;
)


(

	SynthDef("woiworung", {|out=0,freq = 1000, amp = 0.2, att = 2.9, dec = 3.3, sus = 0, rel = 6, gate = 1, fb = 1.2|
		var snd, env;
		env = EnvGen.kr(Env.adsr(att, dec, sus, rel), gate: gate, doneAction: 2);

		snd = SinOsc.ar(freq,
			LocalIn.ar(2) * LFNoise1.ar(0.1,2),
			LFNoise1.ar(MouseY.kr(0.2,19),6)
			// LFNoise1.ar(MouseY.kr(0.2,19),MouseX.kr(0.1,4.1))
		).tanh;
	2.do{
		snd = AllpassL.ar(snd,0.3,{0.1.rand+0.03}!2,5)
	};

		Out.ar(out, snd.tanh * amp * env);
	}).add;


)
Synth("woiworung", [\freq, 100], addAction: \addToTail);



(
Ndef(\z,{
	a = SinOsc.ar([57,57.1] * 0.5, Ndef(\x).ar * LFNoise1.ar(0.1,2,1) ,LFNoise1.ar(3,2,1)).tanh;
	8.do{
		a = AllpassL.ar(a,0.4,{0.2.rand+0.1}!2,5)
	};
	Out.ar(0,a.tanh * 0.1)
}).play

)


(play{
	x=0;
	a = (111..116)*MouseX.kr(1,2,6);
	(a).do{|f|
		f=f/2;
		x=SinOsc.ar(
			f+[0,1],
			x*LFTri.kr(80,0,2,0).range(0.02,MouseY.kr(0.1,2.5)).tanh
		)
	};z
	LeakDC.ar(x) * 0.3
}
)

