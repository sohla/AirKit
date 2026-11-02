var m = ~model;
var group, synth, synth2;
m.rrateMassFilteredAttack = 0.3;
m.rrateMassFilteredDecay = 0.1;
m.accelMassFilteredAttack = 0.4;
m.accelMassFilteredDecay = 0.4;

//------------------------------------------------------------

SynthDef(\insects, {
    |out=0, freq=6000, amp=0.004, pan=0, gate=1,
    filterFreq=3000, filterQ=0.6,
    reverbMix=0.6, reverbRoom=0.5, reverbDamp=0.5, temp = 1.43|

    var sig, env, filtered, reverbed;
  var modulator, mod1, mod2, mod3;

	// repeat time is 0.7s: equates to 1.43 Hz.
	modulator = LFSaw.ar(temp, 1, 0.5, 0.5) ;
	mod2 = (modulator * 40.6 * 2pi).cos.squared;
	mod3 = modulator * 3147;
	mod3 = (mod3 * 2pi).cos + ((mod3 * 2 * 2pi).cos * 0.3);
	mod1 = ((Wrap.ar(modulator.min(0.1714) * 5.84) - 0.5).squared * (-4) + 1) * (mod2 * mod3);
	mod1 = (mod1 * 0.002)!2;

    // Basic cicada sound: a combination of sine waves
    sig = SinOsc.ar(freq) * SinOsc.ar(freq * 0.5) * SinOsc.ar(freq * 0.5) ;

    // Amplitude modulation for the characteristic pulsing
	sig = sig * LFTri.ar(55+ LFNoise2.kr([1,2], 10), 0, 5, 1);

    // Envelope
    env = EnvGen.kr(Env.asr(14, 1, 3.1), gate, doneAction: 2);

    // Apply bandpass filter
	filtered = BPF.ar(sig, filterFreq, [0.5,0.4]);
	filtered = HPF.ar(filtered, 6000);


    // Apply reverb
    reverbed = FreeVerb.ar(filtered, reverbMix, reverbRoom, reverbDamp);

    // Final output with panning and envelope
    Out.ar(out, Pan2.ar(reverbed * env * amp, pan)+(mod1 *0.8));
}).add;
SynthDef(\insectLeaf, {
    |out=0, pan=0, amp=0.1, grainDur=0.05, grainRate=50,
     filterFreq=9000, filterRQ=1, dustiness=0.2, leafType=0, gate=1,
     spread=0.07|

    var sig, env, dust, filterEnv, leafNoise;

    // Create base sound for granulation : one independent source per side,
    // so the channels are decorrelated at the source rather than being one
    // mono signal panned into place
    leafNoise = SelectX.ar(leafType, [
        PinkNoise.ar!2,  // Softer leaves
        BrownNoise.ar!2, // More crinkly leaves
        GrayNoise.ar!2   // Crisp leaves
    ]);

    // Granular synthesis : the right side runs slightly faster and starts
    // half a period late, so the two grain trains never tick together
    sig = GrainIn.ar(
        numChannels: 1,
        trigger: Impulse.ar(grainRate * [1, 1 + spread], [0, 0.5]),
        dur: grainDur * [1, 1 - (spread * 0.5)],
        in: leafNoise,
        pan: LFNoise1.kr(5)
    );

    // Add some dust for additional texture
    dust = Dust.ar((200 * dustiness)!2) * 0.5;
    sig = sig + dust;

    // Moving filter : in antiphase, so one side opens as the other closes
    filterEnv = SinOsc.kr(0.1 * [1, 1 + (spread * 2)], [pi, 0]).range(7000, 11000);
    sig = BPF.ar(sig, filterEnv, filterRQ);

    // Envelope
    env = EnvGen.kr(Env.asr(4.3, 1, 2.3, \welch), gate, doneAction: 2);

    // Output : already a stereo pair, so Balance2 shifts it without
    // collapsing the width the way Pan2 on a mono sum would
    sig = sig * env * amp.lag(2);
    Out.ar(out, Balance2.ar(sig[0], sig[1], pan));
}).add;

~init = ~init <> {
	group = Group.new;
	synth = Synth(\insects, [\gate, 1, \amp, 0.02], group);
	synth2 = Synth(\insectLeaf, [\gate, 1, \amp, 0], group);
};

~deinit = ~deinit <> {
	var dying = group;

	synth !? (_.set(\gate, 0));
	synth2 !? (_.set(\gate, 0));
	synth = nil;
	synth2 = nil;
	group = nil;

	if (dying.notNil) {
		fork {
			3.6.wait;
			s.bind { dying.freeAll };
			s.sync;
			dying.free;
		};
	};
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var a = m.accelMassFiltered.lincurve(0, 2.5 * sens, 0.02, 1.2, -2);
    var f = (d.sensors.gyroEvent.x / pi).linlin(-1,1,1.13,1.56);
	var pan = (d.sensors.gyroEvent.z / pi).linlin(-1,1,-0.3,0.3);
    
    // synth.set(\temp, f);
	if(a<0.0003,{a=0});

	synth !? (_.set(\amp, 0.02 * vol));
	synth2 !? (_.set(\amp, 0.2 * vol * a, \pan, pan));

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






