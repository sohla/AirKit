var m = ~model;
var synth;
// var notes = [30,37,42,46,49,54,56,59,63,66];
var notes = [30,32,34,35,37] + 24 + 5;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;

//------------------------------------------------------------
SynthDef(\sheet1, { |out=0, frq=111, gate=0, amp = 0, freq=45, detune=0.01, rtime=1|
	var env = EnvGen.ar(Env.asr(0.3,1.0,8.0), gate, doneAction:Done.freeSelf);
	var sig = Splay.ar( {Gendy1.ar(1,00, 0.001, 1, freq, freq + (freq * detune), 0, 0.0, mul: 0.1)}!20).softclip;
  var follow = Amplitude.kr(amp, 0.1, 0.4);
	sig = GVerb.ar(sig * env * follow, 1, rtime).distort;
	BLowShelf.ar(sig,400, db:-8);
	Out.ar(out, sig);
}).add;
SynthDef(\miniMoog, {
	|freq = 440, amp = 0.0, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0|

    var env, osc, filt, sig;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 0.005],0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
    filt = RLPF.ar(osc.tanh, filterFreq, fq);
    sig = filt * env * amp.lag(1) * 0.5;
    sig = Pan2.ar(sig, pan);
    Out.ar(0, sig.tanh);
}).add;
~init = ~init <> {
	synth = Synth(\miniMoog, [\gate, 1]);
};

~deinit = ~deinit <> {
	synth.set(\release, 5);
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|

	var amp = m.accelMassFiltered.linlin(0,1.5,0.001,1.0);
  var detune = m.accelMassFiltered.linlin(0,2.5,0.1,0.2);
  var filterFreq = m.rrateMassFiltered.linexp(0,1,400,9.2e3);
	
  var index = m.gyroYFiltered.linlin(-1,1,0,notes.size-1).floor;
	var freq = notes[index].midicps;

	if(filterFreq < 400, { filterFreq = 400 });
	if(filterFreq > 9.2e3, { filterFreq = 9.2e3 });

	if(amp < 0.02, { amp = 0 });
	if(amp > 0.9, { amp = 0.9 });
	synth.set(\amp, amp * 1.0);
  synth.set(\filterFreq, filterFreq);
  synth.set(\detune, detune);
  synth.set(\freq, freq);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	// [m.accelMassFiltered * 0.1, d.sensors.rotateEvent.y];
	[d.sensors.gyroEvent.y];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
