var m = ~model;
var synth;
m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.99;


// SynthDef(\scale1, {
// 	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0|

//     var env, osc, filt, sig;
//     env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
// 		osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 0.005],0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
//     filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
//     sig = filt * env * amp * 0.5;
//     sig = Pan2.ar(sig, pan);
//     Out.ar(0, sig.tanh);
// }).add;

SynthDef(\scale1, {
    |freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, 
    release = 0.3, gate = 1, filterFreq = 800, fq = 0.5, pan = 0|
    
    var env, osc, filt, sig;
    var freqMod = freq * [1, 1.004];  // Calculate frequency modulation once
    
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
    
    // Combine oscillators into single array operation
    osc = Mix([
        Saw.ar(freqMod),
        SinOsc.ar([freq-1, (freq-1) * 0.995]),  // Simplified multiplication
        LFTri.ar([freq+1, freqMod[1]])
    ]);
    
    // Single tanh operation after mixing
    filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
    sig = filt * env * amp ;  // Combined scaling factors
    sig = Pan2.ar(sig, pan);
    
    Out.ar(0, sig.tanh);
}).add;

~init = ~init <> {

	~vdef.(\filterComb, { |ev, c|
		var mod = ev[\modulation] ? ();
		var f0 = mod[\f0] ? 110;
		var fc = mod[\cutoff] ? 500;
		var rq = mod[\rq] ? 0.5;
		var fLo = mod[\fLo] ? 40;
		var fHi = mod[\fHi] ? 1600;
		var floorDb = mod[\floorDb] ? -36;
		var ceilDb = mod[\ceilDb] ? 20;
		var lift = (mod[\lift] ? 120) * (mod[\up] ? 1);
		var span = c[\size];
		var origin = c[\pos];
		var count = (fHi / f0).floor.asInteger.clip(1, 40);
		var pts = [origin];

		count.do { |k|
			var f = f0 * (k + 1);
			var r = f / fc;
			var gain = 1 / sqrt(((1 - r.squared).squared) + (rq * r).squared);
			var h = (gain / (k + 1)).ampdb.linlin(floorDb, ceilDb, 0, 1) * lift;
			var x = origin.x + (f.explin(fLo, fHi, 0, 1) * span);
			pts = pts ++ [x @ origin.y, x @ (origin.y - h), x @ origin.y];
		};
		pts ++ [(origin.x + span) @ origin.y]
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \scale1,
			\scale, Scale.major,
			\octave, Pseq([4,5].stutter(2), inf),
			// \dur, 0.2,	
			// \root, 0,//Pseq([0,4,8].stutter(48), inf),
			\attack, Pwhite(0.002,0.03),
    	\decay, 0.2,
    	\sustain, 0.1,
			\release, Pwhite(0.1,0.8),
			\filterFreq, Pwhite(100,1000),
			\fq, Pwhite(0.1,0.9),
    	\pan, Pseq([-0.3, 0.3], inf),

			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],

			\type, \customVisualEvent,
			\shape, \filterComb,
			\closed, false,
			\fill, false,
			\sx, Pfunc({ |e| (e[\pan] ? -1).sign * 0.96 }),
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e| (e[\octave] ? 4).linlin(4, 5, 0.1, -0.1) }),
			\ey, Pfunc({ |e| (e[\octave] ? 4).linlin(4, 5, 0.95, -0.95) }),
			\rotation, Pfunc({ |e| if((e[\pan] ? -1) < 0, 0, pi) }),
			\startSize, 420,
			\endSize, 300,
			\startWidth, 3.5,
			\endWidth, 0.5,
			\startColor, Pfunc({ |e|
				Color.new255(150, 178, 128, 235).blend(Color.new255(120, 200, 245, 255),
					(e[\fq] ? 0.5).linlin(0.1, 0.9, 1, 0))
			}),
			\endColor, Pfunc({ |e| e[\startColor].copy.alpha_(0) }),
			\colorEnv, Pfunc({ Env([0, 1], [1], -3) }),
			\duration, Pfunc({ |e| ((e[\release] ? 0.4) + 0.1) * 1.4 }),
			\modulation, Pfunc({ |e| (
				f0: e.use { ~freq.value },
				cutoff: e[\filterFreq] ? 500,
				rq: e[\fq] ? 0.5,
				fLo: 40,
				fHi: 1600,
				floorDb: -36,
				ceilDb: 20,
				lift: (e[\amp] ? 0.1).explin(0.001, 1, 40, 230),
				up: if((e[\pan] ? -1) < 0, 1, -1),
				amp: 0
			) })
		);
	);

	Pdef(m.ptn).play(quant:0.1);
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
~onEvent = {|e|
	Pdef(m.ptn).set(\root, m.com.root);
};


//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.rrateMassFiltered.lincurve(0.0, 2.0 * sens,-70,-1,-10);
	var notes = [10];
	var index = ((((d.sensors.gyroEvent.z/pi) + 1).half) * notes.size).asInteger;
	var note = notes[index];
	var dur = m.accelMassFiltered.lincurve(0, 1 * sens,4,12,-10).reciprocal;
	// dur.postln;
	if(dur<0.06,{dur=0.06});
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\note, note - 24);
	Pdef(m.ptn).set(\amp, amp.dbamp  * vol);
	Pdef(m.ptn).set(\viewID, d.port);
	if(amp.dbamp > 0.009,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.2);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});

};

~nextMidiOut = {|d|
	// m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 64 );
};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|

	var dur = m.accelMassFiltered.linlin(0,2.5,1,4).floor.reciprocal;

	[dur/4];

	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	// [m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};




