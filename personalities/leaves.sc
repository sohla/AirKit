var m = ~model;
var synth;
m.rrateMassFilteredAttack = 0.3;
m.rrateMassFilteredDecay = 0.1;
m.accelMassFilteredAttack = 0.09;
m.accelMassFilteredDecay = 0.09;

//------------------------------------------------------------

SynthDef(\syntheticLeaf, {
    |out=0, pan=0, amp=0.1, grainDur=0.05, grainRate=50,
     filterFreq=9000, filterRQ=1, dustiness=0.2, leafType=0, gate=1|

    var sig, env, dust, filterEnv, leafNoise;
    leafNoise = SelectX.ar(leafType, [
        PinkNoise.ar,  // Softer leaves
        BrownNoise.ar, // More crinkly leaves
        GrayNoise.ar   // Crisp leaves
    ]);
    sig = GrainIn.ar(
        numChannels: 1,
        trigger: Impulse.ar(grainRate),
        dur: grainDur,
        in: leafNoise,
        pan: LFNoise1.kr(5)
    );
    dust = Dust.ar(200 * dustiness) * 0.5;
    sig = sig + dust;
   filterEnv = SinOsc.kr(0.3,pi).range(7000, 11000);
    sig = BPF.ar(sig, filterEnv, filterRQ);
    env = EnvGen.kr(Env.asr(4.3, 1, 2.3, \welch), gate, doneAction: 2);
    Out.ar(out, Pan2.ar(sig * env * amp.lag(0.4), pan));
}).add;

~init = ~init <> {
	synth = Synth(\syntheticLeaf, [\gate, 1]);
};

~deinit = ~deinit <> {
	synth.set(\gate,0);
};

//------------------------------------------------------------
~next = {|d|

	var a = m.accelMassFiltered.lincurve(0,3,0.01,1.0,-2);
	var pan = d.sensors.gyroEvent.z.linlin(-1,1,-0.3,0.3);
    
	if(a<0.0003,{a=0});

	synth.set(\amp, a * 1);
	synth.set(\pan, pan);

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






