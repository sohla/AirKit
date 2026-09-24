var m = ~model;
var synth;

//------------------------------------------------------------
SynthDef(\sheet4, {

	|out=0, amp=0.5, density=0.5, strength=0.5,
     filterFreq=1000, filterQ=0.5,
     reverbMix=0.5, reverbRoom=0.5, reverbDamp=0.2, gate=0, my=0.5, mx=1x|

    var wind, filtered, reverbed;
    var densityMod, strengthMod;
	var env = EnvGen.ar(Env.asr(1.3,1.0,5.0), gate, doneAction:Done.freeSelf);

    // Modulate density and strength
	densityMod = LFNoise2.kr(0.1).range(0.8, 1.2) * mx.lag(5);
	strengthMod = LFNoise2.kr(0.2).range(0.8, 1.2) * my.lag(3.5);

    // Base wind sound
	wind = [WhiteNoise.ar(1),WhiteNoise.ar(1)];

    // Apply density control (affects graininess of the wind)
    wind = Dust.ar(densityMod * 10000) * wind;

    // Apply strength control (affects amplitude and filter)
    wind = wind * strengthMod;

    // Apply resonant filter
	filtered = RLPF.ar(wind, filterFreq.lag(3)  * strengthMod.linexp(0, 1, 0.5, 2), filterQ);

    // Apply reverb
    reverbed = FreeVerb.ar(filtered, reverbMix, reverbRoom, reverbDamp);


	Out.ar(out, reverbed * amp.lag(1) * env);

}).add;

~init = ~init <> {
	synth = Synth(\sheet4, [\gate, 1]);
};

~deinit = ~deinit <> {
	synth.free;
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var a = m.accelMassFiltered.lincurve(0, 6 * sens,0,5,-6);
	var b = m.accelMassFiltered.linexp(0, 6 * sens,0.1,1);
	var r = m.rrateMassFiltered.linlin(0, 3 * sens,0.8,1.0);
	var e = (d.sensors.gyroEvent.y / 2pi) + 0.5;
	e = e.fold(0,0.5) * 2;
	e = e.linexp(0,1,600,1600);
	if(a<0.03,{a=0});
	if(a>0.9,{a=0.9});
	synth.set(\amp, a * 4 * vol);
	synth.set(\my, b);
	synth.set(\mx, r);
	synth.set(\filterFreq, e);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	[m.rrateMassFiltered];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};






