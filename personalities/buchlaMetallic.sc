var m = ~model;
var group;

var beat = 0.5;
var divs = [1, 3, 6];
var notes = [0];

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------

SynthDef(\buchlaInspired, {
    |out=0, freq=440, amp=0.5, pan=0, gate=1,
    attack=0.01, decay=0.3, sustain=0.5, release=1,
    osc1Waveform=0, osc2Waveform=0,
    osc1Ratio=1, osc2Ratio=1,
    osc1Index=1, osc2Index=1,
    osc1Level=1, osc2Level=1,
    lpgDecay=0.5, lpgSustain=0,
    complexOscFold=1, complexOscWarp=0.5,
    modOscFreq=1, modOscAmount=0,
    randomFreq=10, randomAmount=0,
    lowpassCutoff=1000, lowpassResonance=0.5,
    reverbMix=0.3, reverbRoom=0.5, reverbDamp=0.5|

    var sig, osc1, osc2, complexOsc, modOsc, rand, lpg, verb;
	var buf = [-1,0,1].as(LocalBuf);

    modOsc = SinOsc.ar(modOscFreq, 0, modOscAmount);
    rand = LFNoise2.kr(randomFreq).bipolar(randomAmount);

    osc1 = Select.ar(osc1Waveform, [
        SinOsc.ar(freq * osc1Ratio + modOsc + rand),
        Saw.ar(freq * osc1Ratio + modOsc + rand),
        Pulse.ar(freq * osc1Ratio + modOsc + rand, 0.5)
    ]);

    osc2 = Select.ar(osc2Waveform, [
        SinOsc.ar(freq * osc2Ratio + modOsc + rand),
        Saw.ar(freq * osc2Ratio + modOsc + rand),
        Pulse.ar(freq * osc2Ratio + modOsc + rand, 0.5)
    ]);

    complexOsc = SinOscFB.ar(freq + modOsc + rand, complexOscFold);
    complexOsc = Shaper.ar(
        buf,
        complexOsc
    );

    sig = (osc1 * osc1Level * osc1Index) + (osc2 * osc2Level * osc2Index) + complexOsc;

    lpg = EnvGen.ar(
        Env.new([0, 1, lpgSustain, 0], [attack, lpgDecay, release], [2, -4, -4], 2),
        gate,
        doneAction: 2
    );
    sig = sig * lpg;

    sig = RLPF.ar(sig, lowpassCutoff.clip(80, 18000), lowpassResonance);

    sig = PanAz.ar(4, sig, pan);

    verb = FreeVerb.ar(sig, reverbMix, reverbRoom, reverbDamp);

    Out.ar(out, verb * amp);

}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \buchlaInspired,
			\group, group,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),             Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),     Pkey(\divIdx)),
			\dur,  Pkey(\div).reciprocal * beat,

			\accent, Pfunc({ |e|
				var st = e[\step] ? 0, dv = e[\div] ? 1;
				if (st == 0, { 1.0 }, {
					if ((dv > 3) and: { st == (dv div: 2) }, { 0.7 }, { 0.45 })
				})
			}),
			\legato, Pfunc({ |e| if(e[\step] == 0, { 0.5 }, { 0.3 }) }),

			\scale, Scale.minor,
			\root, 2,
			\pick, Pfunc({ |e| if(e[\step] == 0, { 0 }, { (e[\pal] ? 1).rand }) }),
			\note, Pfunc({ |e| notes[e[\pick].clip(0, notes.size - 1)] }),
			\octave, Pfunc({ |e| if(e[\step] == 0, { 3 }, { [2, 3,4,5].choose }) }),

			\osc1Waveform, 1, \osc1Index, 0.8,
			\osc2Waveform, 2, \osc2Index, 0.6,
			\osc1Ratio, Pfunc({ |e| 1.0 * (e[\roll] ? 1) }),
			\osc2Ratio, Pfunc({ |e| 1.0 * (e[\roll] ? 1) }),
			\release, Pkey(\dur) * 3.5,
			\complexOscFold, 1.5, \complexOscWarp, 0.3,
			\lpgSustain, 0,
			\lowpassResonance, 0.1,
			\attack, 0.001,
			\pan, Pwhite(-0.2, 0.2),
			\reverbMix, 0.2, \reverbRoom, 0.3, \reverbDamp, 0.5,

			\amp, Pfunc({ |e| (e[\gestAmp] ? 0.12) * (e[\accent] ? 1.0) })
		);
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\pal, 1);
	// Pdef(m.ptn).set(\roll, 1);
	Pdef(m.ptn).set(\gestAmp, 0.12);
	Pdef(m.ptn).set(\lpgDecay, 0.9);
	Pdef(m.ptn).set(\lowpassCutoff, 5000);
	Pdef(m.ptn).set(\pan, 0);

	Pdef(m.ptn).play(quant: 0.5);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

	fork {
		if (group.notNil) {
			group.set(\gate, 0);
			1.0.wait;
			group.freeAll;
			s.sync;
			group.free;
			group = nil;
		};
	};
};

//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity;
	var e = m.accelMassFiltered;	
	var idx = e.lincurve(0, 2.0 * sens, 0, divs.size - 1, 1).round.asInteger.clip(0, divs.size - 1);
	var pal = e.lincurve(0, 2.0 * sens, 1, notes.size, 1).round.asInteger.clip(1, notes.size);
	var amp = e.lincurve(0.0, 2.0 * sens, -50, -3, -2);
	var roll = (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.75, 1.25);
	var cut = m.rrateMassFiltered.linexp(0.0, 2.0 * sens, 900, 12000);
	var ring = (d.sensors.gyroEvent.y / pi.half).lincurve(-1.0, 1.0, 0.05, 0.9, 2);

	if(amp < 49.neg, { amp = 120.neg });

	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\pal, pal);
	// Pdef(m.ptn).set(\roll, roll);
	Pdef(m.ptn).set(\gestAmp, amp.dbamp);
	Pdef(m.ptn).set(\lowpassCutoff, cut);
	Pdef(m.ptn).set(\lpgDecay, ring);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, cyan , magenta]??

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;

	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	[m.accelMass, m.accelMassFiltered];
	// [d.sensors.accelEvent.x.abs * d.sensors.accelEvent.y.abs *  m.accelMassFiltered] * 0.5;

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];
  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
