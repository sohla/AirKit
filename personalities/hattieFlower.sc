var m = ~model;
var synth;
var synth2;
var windSynth;
var leafSynth;
var group;
var fxBus;
var verb;

var lastTime=0;
var notes = [0,4,8];
var mr = [0].stutter(2);
var roots = [0,3,-3].stutter(24);
var currentNote = notes[0];
var currentRoot = roots[0];

m.accelMassFilteredAttack = 0.2;
m.accelMassFilteredDecay = 0.99;

//------------------------------------------------------------
SynthDef(\hattieFlowerVoice, { |out, freq=111, gate=0, amp = 0.3, pchx=0, fx = 0, send = 0|
	var env = EnvGen.ar(Env.asr(0.007,0.8,1.2), gate, doneAction:Done.freeSelf);
	var follow = Amplitude.kr(amp, 0.03, 0.03);
	var trig = PinkNoise.ar(0.01) * env * follow.lag(2);
	var sig =  DynKlank.ar(`[[freq, freq*2].lag(3), [1,0.4,0.3], [2, 1, 1, 1]], trig);
  	var tone = SinOsc.ar([freq * 1, freq * 0.5] * LFNoise2.ar(12,0.02,1), LFNoise2.ar(3,6),[0.1,0.12 ]* env * 6);
	var dly = DelayC.ar(sig + tone,0.03,[0.02,0.027]);
	var eq = BLowShelf.ar(dly,1000,0.4, -7);
	Out.ar(out, eq * amp);
	Out.ar(fx, eq * amp * send);
}).add;

SynthDef(\hattieFlowerWind, {
	|out=0, amp=0.5, density=0.5, strength=0.5,
	filterFreq=100, filterQ=0.5,
	reverbMix=0.5, reverbRoom=0.5, reverbDamp=0.2, gate=0, my=0.5, mx=0.1,pan=0, fx = 0, send = 0|

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

	filtered = RLPF.ar(wind, filterFreq.lag(2)  * strengthMod.linexp(0, 1, 0.5, 2), filterQ);
  reverbed = FreeVerb.ar(filtered, reverbMix, reverbRoom, reverbDamp);
	reverbed = Balance2.ar(reverbed[0],reverbed[1],pan.lag(3));
	reverbed = reverbed * amp.lagud(0.9,4.5) * env;
	Out.ar(out, reverbed);
	Out.ar(fx, reverbed * send);

}).add;

SynthDef(\hattieFlowerLeaf, {
    |out=0, pan=0, amp=0.1, grainDur=0.05, grainRate=50,
     filterFreq=9000, filterRQ=1, dustiness=0.2, leafType=0, gate=1, fx = 0, send = 0|

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
    sig = Pan2.ar(sig * env * amp.lagud(0.7,3), pan);
    Out.ar(out, sig);
    Out.ar(fx, sig * send);
}).add;

SynthDef(\hattieFlowerVerb, { |in = 0, out = 0, room = 0.9, damp = 0.4, lowcut = 120, hicut = 7000, amp = 1, gate = 1|
	var env = EnvGen.kr(Env.asr(0.01, 1, 4), gate, doneAction: 2);
	var wet = In.ar(in, 2);
	wet = FreeVerb2.ar(wet[0], wet[1], 1, room, damp);
	wet = LPF.ar(HPF.ar(wet, lowcut), hicut);
	Out.ar(out, wet * env * amp);
}).add;


~init = ~init <> {
	group = Group.new;
	fxBus = Bus.audio(s, 2);
	verb = Synth.tail(group, \hattieFlowerVerb, [\in, fxBus, \out, 0, \room, 0.9, \damp, 0.4]);
	windSynth = Synth(\hattieFlowerWind, [\gate, 1, \fx, fxBus, \send, 0.2], group);
	leafSynth = Synth(\hattieFlowerLeaf, [\gate, 1, \fx, fxBus, \send, 0.3], group);
};

~deinit = ~deinit <> {
	var oldGroup = group;
	var oldVerb = verb;
	var oldBus = fxBus;
	group = nil;
	verb = nil;
	fxBus = nil;
	synth !? { synth.set(\gate,0) };
	synth = nil;
	windSynth !? { windSynth.set(\gate,0) };
	windSynth = nil;
	leafSynth !? { leafSynth.set(\gate,0) };
	leafSynth = nil;
	fork {
		5.5.wait;
		oldVerb !? { oldVerb.set(\gate,0) };
		4.wait;
		if (oldGroup.notNil) {
			s.bind { oldGroup.freeAll };
			s.sync;
			oldGroup.free;
		};
		oldBus !? { oldBus.free };
	};
};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var move = m.accelMassFiltered.linlin(0, 6 * sens,0,1.5);
  	var a = m.accelMassFiltered * 0.5;
  	var amp = m.accelMassFiltered.lincurve(0, 2 * sens,0.02,0.1,-5);
  	var gap = m.accelMassFiltered.lincurve(0, 2 * sens,1.3,0.1,-5);
	var oct = d.sensors.gyroEvent.y.linlin(-1,1,1,5).floor * 12;
	var av = d.sensors.gyroEvent.y.lincurve(-1,1,1,0.1,-2);

	var windAmp = m.accelMassFiltered.lincurve(0, 4.4 * sens,0,3,-2);
	var windMy = m.accelMassFiltered.linexp(0, 6 * sens,0.1,1);
	var windMx = m.rrateMassFiltered.linlin(0, 3 * sens,0.8,1.0);
	var windFreq = (d.sensors.gyroEvent.y / 2pi) + 0.5;
	var windPan = d.sensors.gyroEvent.z.linlin(-1,1,-1,1);

	var leafAmp = m.accelMassFiltered.lincurve(0,3,0.01,1.0,-2);
	var leafPan = d.sensors.gyroEvent.z.linlin(-1,1,-0.3,0.3);

	windFreq = windFreq.fold(0,0.5) * 2;
	windFreq = windFreq.linexp(0,1,500,900);
	// if(windAmp>0.9,{windAmp=0.9});
	// if(leafAmp<0.03,{leafAmp=0});

	windSynth !? { |x| x.set(\amp, windAmp * 0.4 * vol, \my, windMy, \mx, windMx, \filterFreq, windFreq, \pan, windPan) };
	leafSynth !? { |x| x.set(\amp, leafAmp * 0.1, \pan, leafPan) };

	if(a<0.02,{a=0});
	// if(a>0.9,{a=1});
	if(move > 0.01, {
    // synth.set(\amp, amp);
  	if(TempoClock.beats > (lastTime + gap),{
			lastTime = TempoClock.beats;
			notes = notes.rotate(-1);
			currentNote = notes[0];
			roots = roots.rotate(-1);
			mr = mr.rotate(-1);
			currentRoot = roots[0];
			m.com.root = currentRoot + mr[0];
      // synth.set(\freq, (36 + currentNote).midicps); 
			synth = Synth(\hattieFlowerVoice, [
				\freq, (36 + currentNote + currentRoot + oct).midicps,
				\gate, 1,
				\amp, amp * 2 * av * vol,
				\fx, fxBus ? 0,
				\send, if(fxBus.isNil, 0, 0.4),
			], group);
			synth.server.sendBundle(0.3,[\n_set, synth.nodeID, \gate, 0]);
		});
	});  
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	// [m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered,d.sensors.gyroEvent.y * 0.1];
	[d.sensors.rotateEvent.y];
	// [d.sensors.rotateEvent.x, d.sensors.rotateEvent.y, d.sensors.rotateEvent.z];
	
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
