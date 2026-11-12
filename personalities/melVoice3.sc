var m = ~model;
var buffer;
var dead = false;
var group;
var fxBusA;
var fxBusB;
var verbA;
var verbB;

var noteSets = [
	[0,2,-2,7],
	[2,5,7,-2],
	[1,5,8],
	[0,5,9],
	[2,3,5,7]
];
// var noteSets = [[0]];
var noteOffset = 0;
var lanes = (noteSets.flat + noteOffset).asSet.asArray.sort;


m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.8;
m.gyroFilteredDecay = 0.4;

//------------------------------------------------------------
SynthDef(\melVoice3Sampler, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0,
	attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1,cutoff=20000, rq=0.9, freq=440|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: 1,doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
	var hs = RHPF.ar(sig, [6000,7000], 0.99,13).tanh;
	var sub = SinOsc.ar(freq * 2, LFNoise2.ar(10,10), EnvGen.kr(Env.perc(0.1, release), gate) * 0.04);
	sig = Pan2.ar(Mix.ar(hs[0] + sig[0] + sub), pan, amp * env);
	Out.ar(out, sig);
}).add;

SynthDef(\melVoice3Verb, {|in=0, out=0, gate=1, release=2|
	var env = EnvGen.kr(Env.asr(0.01, 1, release), gate, doneAction: 2);
	var sig = FreeVerb.ar(In.ar(in, 2),0.5,0.4);
	sig = LeakDC.ar(sig);
	Out.ar(out, sig * env);
}).add;

//------------------------------------------------------------
~vdef.(\oval, { |ev, c|
	var n = ev[\numPoints] ? 32;
	var mod = ev[\modulation] ? ();
	var aspect = mod[\aspect] ? 1;
	Array.fill(n, { |i| var a = i / n * 2pi; c[\pos] + Point(cos(a) * c[\size], sin(a) * c[\size] * aspect) })
});

//------------------------------------------------------------
~init = ~init <> {

	// var path = PathName("~/Downloads/yourDNASamples/brenton/BrentonVoice_02.wav");
	var path = PathName("~/Downloads/melSamples/mel_sing_dry-005.wav");
	// var path = PathName("~/Downloads/alessioSamples/ahAirEveryPig.wav");
	postf("loading sample : % \n", path.fileName);

	m.com.scaleIdx = 0;

	group = Group.new;
	fxBusA = Bus.audio(s, 2);
	fxBusB = Bus.audio(s, 2);
	verbA = Synth.tail(group, \melVoice3Verb, [\in, fxBusA, \out, 0]);
	verbB = Synth.tail(group, \melVoice3Verb, [\in, fxBusB, \out, 2]);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		if(dead.not, {
			Pdef(m.ptn,
				Pbind(
					\instrument, \melVoice3Sampler,
					\group, group,
					\bufnum, buf,
					// \octave, Pxrand([0,1,2,1,3,2], inf),
					\note, Pwhite(33,33, inf).floor,
					\pan, Pwhite(-0.9,0.9),
					\start,Pwhite(0.0,0.9),
					\out, Pseq([fxBusA, fxBusB], inf),
					// \attack,0.2,
					\decay, 0.2,
					\sustain,0.1,
					\release,1.3,
					\rate, Pn(Plazy({ |e| var set = noteSets.wrapAt(m.com.scaleIdx ? 0); Pseq((Array.fill(e[\range] ? 1, { |i| set.wrapAt(i) }) + noteOffset).midiratio, 1) })),
					\baseNote, 72,
					\freq, Pfunc({ |e| (e[\baseNote] + e[\rate].ratiomidi).midicps }),


				\type, \customVisualEvent,
				\shape, \oval,
				\lane, Pfunc({ |e| lanes.indexOf(e[\rate].ratiomidi.round.asInteger) ? 0 }),
				\hue, Pfunc({ |e| e[\lane].linlin(0, lanes.size - 1, 0.0, 0.12) }),
				\alpha, Pfunc({ |e| (e[\amp] ? 0).linlin(0, 0.5, 0.0, 0.7) }),
				\sx, 1,
				\ex, 0,
				\sy, Pfunc({ |e| e[\lane].linlin(0, lanes.size - 1, 0.8, -0.8) }),
				\fan, Pseg([0.3, 1.5, 0.3], [5, 5], \sine, inf),
				\ey, Pfunc({ |e| e[\sy] * e[\fan] }),
				\rotation, 0.5pi,
				\startSize, Pfunc({ |e| (e[\amp] ? 0).linlin(0, 1, 5, 10) }),
				\endSize, Pfunc({ |e| e[\startSize] * 3.3 }),
				\modulation, Pfunc({ |e| (amp: 0, aspect: e[\freq].cpsmidi.linlin(68, 83, 0.5, 2.0)) }),
				\startWidth, 20,
				\endWidth, 1,
				\fill, true,
				\duration, 4,
				\startColor, Pfunc({ |e| Color.hsv(e[\hue], 0.8, 0.95, e[\alpha]) }),
				\endColor, Pfunc({ |e| Color.hsv(e[\hue], 0.8, 0.95, 0.0) }),

					\args, #[],
				)
			);
			Pdef(m.ptn).play(quant:0.125);
		});
	});
};
~deinit = ~deinit <> {
	var dyingBuffer = buffer;
	var dyingGroup = group;
	var dyingBuses = [fxBusA, fxBusB];
	var dyingVerbs = [verbA, verbB];
	dead = true;
	buffer = nil;
	group = nil;
	fxBusA = nil;
	fxBusB = nil;
	verbA = nil;
	verbB = nil;
	Pdef(m.ptn).remove;
	fork{
		1.5.yield;
		dyingVerbs.do { |v| v !? { v.set(\gate, 0) } };
		2.2.yield;
		dyingGroup !? {
			s.bind { dyingGroup.freeAll };
			s.sync;
			dyingGroup.free;
		};
		dyingBuffer !? {
			postf("buffer dealloc [%] \n", dyingBuffer);
			dyingBuffer.free;
			s.sync;
		};
		dyingBuses.do { |b| b !? { b.free } };
	};
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var dur = m.accelMassFiltered.lincurve(0, 2 * sens,0.4,0.02,-3);
	var start = m.gyroXFiltered.lincurve(0.0,1.0,0.1,0.9,0);
	var amp = m.accelMassFiltered.lincurve(0, 2 * sens,0,1,-8);
	var rate= m.accelMass.linlin(0, 2 * sens,0,2);
	var range = m.accelMassFiltered.lincurve(0, 1 * sens,1,noteSets.wrapAt(m.com.scaleIdx ? 0).size,-2).asInteger;
	var octave = m.gyroYFiltered.lincurve(-1.0,1.0,0,4,0).asInteger;
	var bal = m.gyroYFiltered.lincurve(-1.0,1.0,1,1,0).asInteger;
	var attack = m.accelMassFiltered.lincurve(0, 2 * sens,0.5,0.003,3);

	if(amp < 0.02, {amp = 0});

	Pdef(m.ptn).set(\viewID, d.port);


	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, amp * bal * vol);
	Pdef(m.ptn).set(\range, range);
	Pdef(m.ptn).set(\octave, octave);
	Pdef(m.ptn).set(\attack, attack);

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.2, m.rrateMass, (m.rrateMass + m.accelMass) * 0.2];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMass, m.rrateMassFiltered, d.sensors.rrateEvent.x];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
	//  [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	[m.gyroYFiltered];

};
