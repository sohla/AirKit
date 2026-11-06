var m = ~model;
var group;
var bass;

var noteSets = [
	[0,2,-2,7],
	[2,5,10],
	[1,5,8],
	[0,5,9],
	[2,3,5,7]

];
// var noteSets = [[0]];
var noteOffset = 0;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.8;
m.gyroFilteredDecay = 0.4;

//------------------------------------------------------------
SynthDef(\melSynth3Bass, {|out=0, freq=65, amp=0, cutoff=300, drive=1, glide=0.12, gate=1|
	var env = EnvGen.kr(Env.asr(0.3, 1, 2), gate, doneAction: 2);
	var f = freq.lag(glide);
	var osc = Mix([Saw.ar(f * [1, 1.004], 0.35).sum, SinOsc.ar(f * 0.5, 0, 0.7), Pulse.ar(f, 0.3, 0.15)]);
	var sig = RLPF.ar(osc, cutoff.lag(0.1).clip(40, 4000), 0.6);
	sig = LeakDC.ar((sig * drive).tanh);
	Out.ar(out, Pan2.ar(sig, 0, amp.lag(0.1) * env));
}).add;

//------------------------------------------------------------
~init = ~init <> {|d|

	var level = {
		var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
		var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
		m.accelMassFiltered.lincurve(0, 2 * sens, 0, 1, -4) * vol
	};

	group = Group.new;
	bass = Synth.head(group, \melSynth3Bass);

	Pdef(m.ptn,
		Pbind(
			\type, \customVisualEvent,
			\amp, 0,
			\legato, 0.01,
			\baseNote, 36,
			\midinote, Pn(Plazy({ |e| var set = noteSets.wrapAt(m.com.scaleIdx ? 0); Pseq(Array.fill(e[\range] ? 1, { |i| set.wrapAt(i) }) + noteOffset + (e[\baseNote] ? 36), 1) })),
			\pitch, Pfunc({ |e| bass !? { bass.set(\freq, e[\midinote].midicps) }; 1 }),
			\audible, Pfunc({ if(level.() > 0.02, 1, { Rest(0) }) }),

			\shape, \circle,
			\duration, 3.6,
			\sx, 1.neg + Pfunc({|e| e.octave * 0.02}),
			\sy, 1.5 - Pfunc({|e| (e[\midinote] - e[\baseNote]).midiratio * 0.75}),
			\startSize, Pfunc({ level.() * 50 }),
			\endSize, Pfunc({ level.() * 1.4 }),
			\ex, Pkey(\sx),
			\ey, Pkey(\sy),
			\startWidth, 80,
			\endWidth, 30,
			\rotation, pi.half,
		)
	);
	Pdef(m.ptn).play(quant:0.125);
};

~deinit = ~deinit <> {
	var dyingGroup = group;
	var dyingBass = bass;
	group = nil;
	bass = nil;
	Pdef(m.ptn).remove;
	dyingBass !? { dyingBass.set(\gate, 0) };
	fork{
		2.2.yield;
		dyingGroup !? {
			s.bind { dyingGroup.freeAll };
			s.sync;
			dyingGroup.free;
		};
	};
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var dur = m.accelMassFiltered.lincurve(0, 2 * sens, 0.9, 0.15, -3);
	var amp = m.accelMassFiltered.lincurve(0, 2 * sens, 0, 1, -4);
	var cutoff = m.accelMassFiltered.linexp(0, 2 * sens, 120, 1800);
	var drive = m.rrateMassFiltered.linlin(0, 1 * sens, 1, 3);
	var range = m.accelMassFiltered.lincurve(0, 1 * sens, 1, noteSets.wrapAt(m.com.scaleIdx ? 0).size, -2).asInteger;
	var octave = m.gyroYFiltered.lincurve(-1.0, 1.0, 0, 4, 0).asInteger;
	var gain = 0.5;

	if(amp < 0.02, {amp = 0});

	bass !? { bass.set(\amp, amp * vol * gain, \cutoff, cutoff, \drive, drive) };

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\range, range);
	Pdef(m.ptn).set(\octave, octave);

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
