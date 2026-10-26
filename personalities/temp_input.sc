var inputBus = 0;
var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\simple, {|out=0, amp=0.0, freq=440, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var sig = SinOsc.ar(freq,0,0.5)!2;
    Out.ar(out, sig * env * amp);
}).add;


SynthDef(\input, {|inputBus = 0, out=0, amp=0.5, rt=1, attack=0.001, decay=0.03, sustain=0.8, release=2.59, gate=1, al=0.9|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var src = SoundIn.ar(inputBus)!2 * 1;
	var flt = HPF.ar(src,120);
	var li = LocalIn.ar(2) * 0.5;
	var sig = PitchShift.ar(flt+li, 0.4*3, rt, [0.01, 0.012], 0.0, 1, src );
	// var sig = FreqShift.ar(src, 200, SinOsc.ar(10).range(0,2pi), 1, src );
	var dly = AllpassC.ar(flt,2.0,0.4,2.0,0.5) + sig;
	LocalOut.ar(sig);
  Out.ar(out, dly * env * amp);	

}).add;


//------------------------------------------------------------
~init = ~init <> {
	synth = Synth(\input,[\inputBus, inputBus, \amp,0.2]);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|
	var amp = m.accelMassFiltered.lincurve(0.0,0.3,-50,-2,-3);
	var al = m.accelMassFiltered.lincurve(0.0,2.5,0.02,1.0,-3);
	var notes = [0,7,16,18,-12];
	var rt = m.gyroZFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0.0,notes.size,0).asInteger;

  	synth.set(\rt, notes[rt].midiratio);
  	// synth.set(\al, al);

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
	// [m.accelMass, m.accelMassFiltered];
	// [d.sensors.accelEvent.x.abs * d.sensors.accelEvent.y.abs *  m.accelMassFiltered] * 0.5;

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	[(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
