var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.1;

//------------------------------------------------------------
SynthDef(\treeWind, { |out, frq=111, gate=0, amp = 0, pchx=0|
	var env = EnvGen.ar(Env.asr(1.3,1.0,8.0), gate, doneAction:Done.freeSelf);
	var follow = Amplitude.kr(amp, 0.03, 0.05).lag(2);
	// var sig = Saw.ar(frq.lag(2),0.3 * env * amp.lag(1));
	var trig = PinkNoise.ar(0.01) * env * follow;
	var sig =  DynKlank.ar(`[[60 + 7 - 12 + pchx.lag(4) - 12].midicps, nil, [2, 1, 1, 1]], trig);
	var dly = DelayC.ar(sig,0.03,[0.02,0.027]);
	Out.ar(out, dly);
}).add;

~init = ~init <> {
	synth = Synth(\treeWind, [\frq, 140.rrand(80), \gate, 1]);
};

~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|

	var a = m.accelMass * 1;
	var f = 50 + (m.accelMassFiltered * 100);
	var pchs = [0,5,10,15,20];
	// var i = (d.sensors.gyroEvent.y.abs / pi) * (pchs.size);
	var pidx = ((m.gyroYFiltered.fold(-0.5,0.5) * 2)).linlin(-1.0,1.0,0,pchs.size,-1).asInteger;


	// pchs[i.floor].postln;
	if(a<0.02,{a=0});
	if(a>0.9,{a=0.9});
	synth.set(\amp, a * 0.5);
	synth.set(\pchx,pchs[pidx]);
  // synth.set(\pchx, m.com.root);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	[m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
