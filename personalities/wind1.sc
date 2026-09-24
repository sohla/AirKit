var m = ~model;
var synth;
m.accelMassFilteredAttack = 0.09;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.4;
m.rrateMassFilteredDecay = 0.4;

//------------------------------------------------------------
SynthDef(\sheet3, {
	|out=0, amp=0.5, density=0.5, strength=0.5,
	filterFreq=100, filterQ=0.5,
	reverbMix=0.5, reverbRoom=0.5, reverbDamp=0.2, gate=0, my=0.5, mx=0.1,pan=0|

	var wind, filtered, reverbed;
	var densityMod, strengthMod;
	var env = EnvGen.ar(Env.asr(1.3,1.0,5.0), gate, doneAction:Done.freeSelf);
	var klank;

	densityMod = LFNoise2.kr(0.1).range(0.8, 1.2) * mx.lag(5);
	strengthMod = LFNoise2.kr(0.2).range(0.8, 1.2) * my.lag(3.5);
	wind = [WhiteNoise.ar(1),WhiteNoise.ar(1)];
  wind = Dust.ar(densityMod * 10000) * wind;
  wind = wind * strengthMod;
	klank = Klank.ar(`[[800, 1071, 1153, 1723] * 0.4, nil, [1, 0.4,0.1,0.03]], wind) * 0.6;

	filtered = RLPF.ar(wind, filterFreq.lag(3)  * strengthMod.linexp(0, 1, 0.5, 2), filterQ);
  reverbed = FreeVerb.ar(filtered, reverbMix, reverbRoom, reverbDamp);
	reverbed = Balance2.ar(reverbed[0],reverbed[1],pan.lag(3));
	Out.ar(out, reverbed * amp.lagud(0.3,0.5) * env);

}).add;
//------------------------------------------------------------
~init = ~init <> {
	synth = Synth(\sheet3, [\gate, 1]);
};
//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.set(\gate, 0);
};
//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	// var a = (d.sensors.accelEvent.y+d.sensors.accelEvent.z).abs;//m.accelMass.lincurve(0, 5 * sens,0,1,-6);
	var a = m.accelMassFiltered.lincurve(0, 4.4 * sens,0,3,-2);
	var b = m.accelMassFiltered.linexp(0, 6 * sens,0.1,1);
	var r = m.rrateMassFiltered.linlin(0, 3 * sens,0.8,1.0);
	var e = (d.sensors.gyroEvent.y / 2pi) + 0.5;
	var pan = d.sensors.gyroEvent.z.linlin(-1,1,-1,1);

	e = e.fold(0,0.5) * 2;
	e = e.linexp(0,1,500,900);
	// if(a<0.03,{a=0});
	if(a>0.9,{a=0.9});
	
	synth.set(\amp, a * 13 * vol);
	synth.set(\my, b);
	synth.set(\mx, r);
	synth.set(\filterFreq, e);
	synth.set(\pan, pan);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[(d.sensors.accelEvent.y+d.sensors.accelEvent.z).abs]
	// [0.2,0.3]
};






