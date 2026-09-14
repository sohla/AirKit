var m = ~model;
var buffer;
// var notes = [0,1,4,5,7,8,7,5,4,5,4,1,4,1,0,0];
var notes = [0,4,5,7,9,10,16,17,19];

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.8;
m.gyroFilteredDecay = 0.4;

//------------------------------------------------------------
SynthDef(\stereoSampler1, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
	attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=0.9|
	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: 0);
	// var hs = RHPF.ar(sig, 7000, 0.99,13).tanh;
	// sig = RLPF.ar(sig, cutoff, rq);// + osc;
	// sig = FreeVerb.ar(sig,0.5,0.4);
	sig = Pan2.ar(Mix.new(sig), pan);
	// sig = LeakDC.ar(sig);
	Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	// var path = PathName("~/Downloads/yourDNASamples/brenton/BrentonVoice_02.wav");
	var path = PathName("~/Downloads/melSamples/WC_Doo_Loop_2_bpm47.aif");
	var sub = 32 * 3;
	// var path = PathName("~/Downloads/alessioSamples/ahAirEveryPig.wav");
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		Pdef(m.ptn,
			Pbind(
				\instrument, \stereoSampler1,
				\bufnum, buf,
				\note, 33,//Pwhite(33,33, inf).floor,
				\start, Pseries(0, sub.reciprocal, sub).loop,
				\attack,0.1,
				\decay, 0.01,
				\sustain,0.1,
				\release,0.1,
				\pan, Pseq([-1,1], inf),
				\rate, 1,//Pslide(notes.midiratio, inf, Pkey(\range), 0, 0),


			\type, \customVisualEvent,
			\shape, \line,
			\duration, 0.6,
			\sx, 0.25.neg + Pfunc({|e| e.octave * 0.25}),
			\sy, 1.5 - Pfunc({|e| e.rate * 0.75}),
			\ex, Pkey(\sx),
			\ey, Pkey(\sy),
			\startWidth, 100,
			\endWidth, 30,
			\rotation, pi.half,

				\args, #[],
			)
		);
		Pdef(m.ptn).play(quant:0.18);
	});
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	fork{
		1.0.yield;
		postf("buffer dealloc [%] \n", buffer);
		buffer.free;
		s.sync;
	};
};

//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.lincurve(0,3.0 * d.params.sensitivity,0.4,0.06,-3);
	var start = m.gyroXFiltered.lincurve(0.0,1.0 * d.params.sensitivity ,0.1,0.9,0);
	var amp = m.accelMassFiltered.lincurve(0,2.5 * d.params.sensitivity,0,1,-6);
	var rate= m.accelMass.linlin(0,1,0,2);
	var range = m.accelMassFiltered.lincurve(0,2.0 * d.params.sensitivity,1,notes.size,-2).asInteger;
	var octave = m.gyroYFiltered.lincurve(-1.0,1.0,2,4,0).asInteger;
	var bal = m.gyroYFiltered.lincurve(-1.0,1.0,1,1,0).asInteger;
	var roots = [-2,0,3];
	var ri = ((m.gyroYFiltered.fold(-0.5,0.5) * 2)).linlin(-1.0,1.0,0,roots.size,-1).asInteger;

	if(amp < 0.02, {amp = 0});

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\startSize, amp * 50);
	Pdef(m.ptn).set(\endSize, amp * 1);
	Pdef(m.ptn).set(\dur, 0.18/3);
	Pdef(m.ptn).set(\amp, amp * bal);
	Pdef(m.ptn).set(\range, range);
	Pdef(m.ptn).set(\octave, 3);

	Pdef(m.ptn).set(\root, 0);

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.2, m.rrateMass, (m.rrateMass + m.accelMass) * 0.2];
	// [m.rrateMassFiltered];
	// [m.rrateMass, m.rrateMassFiltered, d.sensors.rrateEvent.x];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
	 [ ((m.gyroYFiltered.fold(-0.5,0.5) * 2))]  ;
	// [m.gyroYFiltered];

};
