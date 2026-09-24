var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.1;

//------------------------------------------------------------
SynthDef(\treeWind, { |out, frq=111, gate=0, amp = 0, pchx=0|
	var env = EnvGen.ar(Env.asr(1.3,1.0,8.0), gate, doneAction:Done.freeSelf);
	var follow = Amplitude.kr(amp, 0.3, 0.5).lag(2);
	var tone = Saw.ar([60 + 7 - 12 + pchx.lagud(0.03,0.1)].midicps,0.002 * env * amp.lagud(0.01,0.05)).tanh;
	var trig = PinkNoise.ar(0.01) * env * follow + tone;
	var sig =  DynKlank.ar(`[[60 + 7 - 12 + pchx.lagud(0.02,0.08) - 24].midicps, nil, [2, 1, 1, 1]], trig);
	var dly = DelayC.ar(sig,0.03,[0.02,0.027]);
	Out.ar(out, dly * 0.5);
}).add;

~init = ~init <> {
	synth = Synth(\treeWind, [\frq, 140.rrand(80), \gate, 1]);
};

~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var a = m.accelMass * 1;
	var f = 50 + (m.accelMassFiltered * 100);
	var pchs = [0,2,4,5,7,9,10,12] - 7;
	var i = (d.sensors.gyroEvent.x.abs / pi).lincurve(0.0,1.0,0.0, pchs.size);
	// pchs[i.floor].postln;
	if(a<0.02,{a=0});
	if(a>0.9,{a=0.9});
	synth.set(\amp, a * 1 * vol);
	synth.set(\pchx,pchs[i.floor]);
  // synth.set(\pchx, m.com.root);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	// [m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
	[d.sensors.gyroEvent.x];


};
