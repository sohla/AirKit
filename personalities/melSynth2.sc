var m = ~model;
var samplesLib;
var folder = PathName("~/Downloads/openLabSamples/harp");
var group;
var fxBusA;
var fxBusB;
var verbA;
var verbB;

var noteSets = [
	[0,2,-2,7],
	[2,5,10],
	[1,5,8],
	[0,5,9],
	[2,3,5,7]

];
// var noteSets = [[0]];
var noteOffset = -24;

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


m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.8;
m.gyroFilteredDecay = 0.4;

//------------------------------------------------------------
SynthDef(\melSynth2Harp, {|bufnum=0, out=0, amp=1, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1,cutoff=20000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);// * (freq/440.0);
    var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	// sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig);
}).add;

SynthDef(\melSynth2Verb, {|in=0, out=0, gate=1, release=2|
	var env = EnvGen.kr(Env.asr(0.01, 1, release), gate, doneAction: 2);
	var sig = FreeVerb.ar(In.ar(in, 2),0.5,0.4);
	sig = LeakDC.ar(sig);
	Out.ar(out, sig * env);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var findSample = { |n|
		var sample = samplesLib.detect({ |smp| smp.midiNote == n }) ?? { samplesLib.detect({ |smp| smp.midiNote == (n - 1) }) };
		sample !? { (buffer: sample.buffer, rate: (n - sample.midiNote).midiratio) }
	};
	
	m.com.scaleIdx = 4;

	group = Group.new;
	fxBusA = Bus.audio(s, 2);
	fxBusB = Bus.audio(s, 2);
	verbA = Synth.tail(group, \melSynth2Verb, [\in, fxBusA, \out, 0]);
	verbB = Synth.tail(group, \melSynth2Verb, [\in, fxBusB, \out, 2]);

	samplesLib = folder.entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buffer = Buffer.read(s, path.fullPath, action:{ |buf|
			postf("buffer alloc [%] \n", buf);
		});
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
	});
	postf("loading harp : % samples \n", samplesLib.size);

	Pdef(m.ptn,
		Pbind(
			\instrument, \melSynth2Harp,
			\group, group,
			// \octave, Pxrand([0,1,2,1,3,2], inf),
			\note, Pwhite(33,33, inf).floor,
			\pan, Pwhite(-0.9,0.9),
			// \start,Pwhite(0.0,0.9),
			\out, Pseq([fxBusA, fxBusB], inf),
			// \attack,0.2,
			\decay, 0.2,
			\sustain,0.1,
			\release,1.3,
			\baseNote, 72,
			\midinote, Pn(Plazy({ |e| var set = noteSets.wrapAt(m.com.scaleIdx ? 0); Pseq(Array.fill(e[\range] ? 1, { |i| set.wrapAt(i) }) + noteOffset + (e[\baseNote] ? 72), 1) })),
			\bufnum, Pfunc({ |e| findSample.(e[\midinote]) !? { |found| found[\buffer] } ?? { Rest(0) } }),
			\rate, Pfunc({ |e| findSample.(e[\midinote]) !? { |found| found[\rate] } ? 1 }),

			\type, \customVisualEvent,
			\shape, \circle,

			\duration, 3.6,
			\sx, 1.neg + Pfunc({|e| e.octave * 0.02}),
			\sy, 1.5 - Pfunc({|e| (e[\midinote] - e[\baseNote]).midiratio * 0.75}),
			\startSize, 10,
			\ex, Pkey(\sx),
			\ey, Pkey(\sy),
			\startWidth, 80,
			\endWidth, 30,
			\rotation, pi.half,

			\args, #[],
		)
	);
	Pdef(m.ptn).play(quant:0.125);
};
~deinit = ~deinit <> {
	var dyingLib = samplesLib;
	var dyingGroup = group;
	var dyingBuses = [fxBusA, fxBusB];
	var dyingVerbs = [verbA, verbB];
	samplesLib = nil;
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
		dyingLib.do { |sample|
			postf("buffer dealloc [%] \n", sample.buffer);
			sample.buffer.free;
			s.sync;
		};
		dyingBuses.do { |b| b !? { b.free } };
	};
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var dur = m.accelMassFiltered.lincurve(0, 2 * sens,0.9,0.04,-3);
	var start = m.gyroXFiltered.lincurve(0.0,1.0,0.1,0.9,0);
	var amp = m.accelMassFiltered.lincurve(0, 2 * sens,0,1,-8);
	var rate= m.accelMass.linlin(0, 2 * sens,0,2);
	var range = m.accelMassFiltered.lincurve(0, 1 * sens,1,noteSets.wrapAt(m.com.scaleIdx ? 0).size,-2).asInteger;
	var octave = m.gyroYFiltered.lincurve(-1.0,1.0,0,4,0).asInteger;
	var bal = m.gyroYFiltered.lincurve(-1.0,1.0,1,1,0).asInteger;
	var attack = m.accelMassFiltered.lincurve(0, 2 * sens,0.5,0.003,3);

	if(amp < 0.02, {amp = 0});

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\startSize, amp * 50);
	Pdef(m.ptn).set(\endSize, amp * 1.4);


	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, amp * bal * vol);
	Pdef(m.ptn).set(\range, range);
	Pdef(m.ptn).set(\octave, 2);
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
