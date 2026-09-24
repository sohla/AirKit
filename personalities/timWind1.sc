var m = ~model;
var synth;
var synth2;

var lastTime=0;
var notes = [0,0,7,0,0,0,7,0,7,5,0,0,0,7,0,0,7,5,0,0,0,-2,7,-2,7,5,-2,-2,7,5,-2,-2,-2,-4,7,5,-4,-4,7,5,-2,-2,7,5,-2,7,5];
var mr = [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-4,-4,-4,-4,-4,-4,-4,-2,-2,-2,-2,-2,-2,-2].stutter(2);
var roots = [0,3].stutter(47*2);
var currentNote = notes[0];
var currentRoot = roots[0];

m.accelMassFilteredAttack = 0.2;
m.accelMassFilteredDecay = 0.99;

//------------------------------------------------------------
SynthDef(\timWind1, { |out, freq=111, gate=0, amp = 0.3, pchx=0|
	var env = EnvGen.ar(Env.asr(0.007,1.0,5.0), gate, doneAction:Done.freeSelf);
	var follow = Amplitude.kr(amp, 0.03, 0.03);
	var trig = PinkNoise.ar(0.01) * env * follow.lag(2);
	var sig =  DynKlank.ar(`[[freq, freq*2].lag(3), [1,0.4,0.3], [2, 1, 1, 1]], trig);
  	var tone = SinOsc.ar([freq * 4, freq * 0.5] * LFNoise2.ar(12,0.02,1), LFNoise2.ar(3,6),[0.1,0.12 ]* env * 6);
	var dly = DelayC.ar(sig + tone,0.03,[0.02,0.027]);
	var eq = BLowShelf.ar(dly,1000,0.4, -7);
	Out.ar(out, eq * amp);
}).add;


~init = ~init <> {
};

~deinit = ~deinit <> {
  synth.set(\gate,0);

};

//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity;
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var move = m.accelMassFiltered.linlin(0, 6 * sens,0,1.5);
  var a = m.accelMassFiltered * 0.5;
  var amp = m.accelMassFiltered.lincurve(0, 3 * sens,0.02,0.7,-5);
  var gap = m.accelMassFiltered.lincurve(0, 3 * sens,0.8,0.1,-5);
	var oct = d.sensors.gyroEvent.y.linlin(-1,1,1,5).floor * 12;
	var av = d.sensors.gyroEvent.y.lincurve(-1,1,1,0.1,-2);
	if(a<0.02,{a=0});
	// if(a>0.9,{a=1});
	if(move > 0.02, {
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
			synth = Synth(\timWind1, [
				\freq, (36 + currentNote + currentRoot + oct).midicps,
				\gate, 1,
				\amp, amp * 0.4 * av * vol,
			]);
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
