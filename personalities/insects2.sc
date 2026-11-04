var m = ~model;
var group, insect, leaf;

// The species. A handover gates the current one off and starts the next :
// slightly different song pitch, body resonance and chirp rate, and its own
// place in the field, so the chorus moves as it changes. Species 0 is
// insects1's tuning, so the file starts on the sound you know.
var species = [
	(freq: 6000, filterFreq: 3000, temp: 1.43, pan: -0.65),
	(freq: 6340, filterFreq: 3280, temp: 1.18, pan:  0.60),
	(freq: 5760, filterFreq: 2820, temp: 1.67, pan: -0.20),
	(freq: 6180, filterFreq: 3420, temp: 1.06, pan:  0.85)
];
var speciesIdx = 0;

// Rest detection. Below restLevel for restTime hands over; only rising above
// moveLevel arms it again. The gap between the two is a dead band, so noise
// sitting on the threshold cannot arm and fire it over and over.
// Read the resting value off the plotter and set restLevel just above it.
var restLevel = 0.05;
var moveLevel = 0.3;
var restTime = 3.0;

// armed starts false : the performer has to play before the first handover,
// so loading the file and leaving it alone changes nothing.
var stillSince = nil;
var armed = false;

m.rrateMassFilteredAttack = 0.3;
m.rrateMassFilteredDecay = 0.1;
m.accelMassFilteredAttack = 0.2;
m.accelMassFilteredDecay = 0.1;

//------------------------------------------------------------

SynthDef(\insects2, {
    |out=0, freq=6000, amp=0.004, pan=0, gate=1, level=1,
    filterFreq=3000, filterQ=0.6,
    reverbMix=0.6, reverbRoom=0.5, reverbDamp=0.5, temp = 1.43|

    var sig, env, filtered, reverbed, mix;
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

    // Apply bandpass filter : the two rq values make this a stereo pair
	filtered = BPF.ar(sig, filterFreq, [0.5,0.4]);
	filtered = HPF.ar(filtered, 6000);


    // Apply reverb
    reverbed = FreeVerb.ar(filtered, reverbMix, reverbRoom, reverbDamp);

    // Final output. The chirp is summed BEFORE the envelope and the
    // placement, so a handover fades it out with everything else and \pan
    // can move it off centre - outside them it would cut dead when the node
    // frees and would always sit in the middle. reverbed is already a pair,
    // so Balance2 places that pair rather than Pan2 doubling it into four
    // channels. level is the device volume; amp stays the internal balance
    // between the sine body and the chirp.
    mix = (reverbed * amp) + (mod1 * 0.8);
    Out.ar(out, Balance2.ar(mix[0], mix[1], pan) * env * level);
}).add;

SynthDef(\insectLeaf2, {
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

//------------------------------------------------------------
~init = ~init <> { |d|
	var sp = species[0];

	group = Group.new;
	speciesIdx = 0;
	stillSince = nil;
	armed = false;

	insect = Synth(\insects2, [
		\gate, 1,
		\amp, 0.02,
		\level, 1,
		\freq, sp[\freq],
		\filterFreq, sp[\filterFreq],
		\temp, sp[\temp],
		\pan, sp[\pan]
	], group);

	leaf = Synth(\insectLeaf2, [\gate, 1, \amp, 0], group);

	~vdef.(\insectGlow, { |ev, c|
		var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
		var a = m.accelMassFiltered.lincurve(0, 2.5 * sens, 0.02, 1.2, -2);
		var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
		var n = ev[\numPoints] ? 48;
		var r = c[\size] * (0.3 + a);
		if(vol < 0.001, { [] }, { Array.fill(n, { |i| c[\pos] + Polar(r * (1 + (0.08 * sin((i * 6) + (c[\elapsed] * 9)))), i / n * 2pi).asPoint }) })
	});
	(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port, shape: \insectGlow, numPoints: 48, duration: inf, startSize: 80, endSize: 80, startColor: Color(1, 1, 1, 0.6), endColor: Color(1, 1, 1, 0.6)).play;

	postf("[insects2] opening on species 0 of % : freq % temp % pan % \n",
		species.size, sp[\freq], sp[\temp], sp[\pan]);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;

	insect !? (_.set(\gate, 0));
	leaf !? (_.set(\gate, 0));
	insect = nil;
	leaf = nil;
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
	var e = m.accelMassFiltered;
	var a = e.lincurve(0, 2.5 * sens, 0.02, 1.2, -2);
	var pan = (d.sensors.gyroEvent.z / pi).linlin(-1,1,-0.3,0.3);
	var now = SystemClock.seconds;

	if(a < 0.0003, { a = 0 });

	insect !? (_.set(\level, vol));
	leaf !? (_.set(\amp, a * vol, \pan, pan));

	if (e > moveLevel, {
		if (armed.not, {
			postf("[insects2] ARMED  (accel % > %) - now rest %s to hand over \n",
				e.round(0.001), moveLevel, restTime);
		});
		armed = true;
		stillSince = nil;
	}, {
		if (e < restLevel, {
			if (stillSince.isNil, {
				stillSince = now;
				postf("[insects2] still  (accel % < %) - clock started, armed % \n",
					e.round(0.001), restLevel, armed);
			});

			if (armed and: { (now - stillSince) > restTime }, {
				var sp;

				speciesIdx = speciesIdx + 1;
				sp = species.wrapAt(speciesIdx);
				armed = false;

				postf("[insects2] HANDOVER after %s still -> species % of % : freq % temp % pan % \n",
					(now - stillSince).round(0.01),
					speciesIdx.mod(species.size), species.size,
					sp[\freq], sp[\temp], sp[\pan]);

				// The old one releases over 3.1s and frees itself; the new
				// one fades up over its 14s attack. They overlap, so the
				// chorus changes rather than switches.
				insect !? (_.set(\gate, 0));
				insect = Synth(\insects2, [
					\gate, 1,
					\amp, 0.02,
					\level, vol,
					\freq, sp[\freq],
					\filterFreq, sp[\filterFreq],
					\temp, sp[\temp],
					\pan, sp[\pan]
				], group);
			});
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	// [m.rrateMassFiltered];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];

	// COMPUTED : this file's own ~next, recomputed
	// [m.accelMassFiltered, restLevel, moveLevel].linlin(0, moveLevel * 2, 0, 1);//thresholds
	// [a] - leaf amp
	// [m.accelMassFiltered.lincurve(0, 2.5 * d.params.sensitivity.lincurve(0.0,1.0,0.9,0.1,0), 0.02, 1.2, -2)];

	// the detector : energy, the arm latch, how far through restTime it is
	[
		m.accelMassFiltered.linlin(0, moveLevel * 2, 0, 1),
		if(armed, 0.6, -0.6),
		if(stillSince.notNil, { ((SystemClock.seconds - stillSince) / restTime).clip(0, 1) }, { 0 })
	];
};
