var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.2;
m.rrateMassFilteredDecay = 0.08;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\growl, {|out=0, amp=0.0, freq=66, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1, gr=1|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var in = LocalIn.ar(2);
  var sub = SinOsc.ar(freq*[1.0,1],0,0.2).tanh;
	var sig = LFTri.ar(freq*[1.0,1.009], 0, 2.0 + (in * gr));
	LocalOut.ar(sig);
  sig = (sig *0.5) + sub;
  Out.ar(out, sig * env * amp.lag(0.02));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	synth = Synth(\growl);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|
  var amp = m.rrateMassFiltered.lincurve(0.0,0.05,-70,-25,-10);
 var ampa = m.accelMassFiltered.lincurve(0,2.5,0.0001,1.0,-1);
  var pos = (d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2;
  var gr = pos.lincurve(-1.0,1.0,0.0,2.0,-3);
  var pitches = [0,4,7,11,12,16,19,23,24] + 37;
  var index = ((((d.sensors.gyroEvent.z/pi) + 1).half) * pitches.size).asInteger;
  synth.set(\amp, amp.dbamp + ampa);
  synth.set(\gr, gr);
//   synth.set(\freq, pitches[index].midicps);
  synth.set(\freq, pitches[index].midicps);


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
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];

	[m.accelMassFiltered * 3, m.rrateMassFiltered * 10, (d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];
};
