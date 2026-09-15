var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------

SynthDef(\help_Klank, { |out = 0, freq=250|
    var klank, n, harm, amp, ring;
	var i = Decay.ar(Impulse.ar(Rand(0.8, 2.2)), 0.03, ClipNoise.ar(0.01));
	var src = SoundIn.ar(0)!2;
	var gated = Compander.ar(src, src,
        thresh: -6.dbamp,
        slopeBelow: 10,
        slopeAbove:  1,
        clampTime:   0.01,
        relaxTime:   0.01
    );


    harm = \harm.ir(Array.series(4, 1.0, 1));
    amp = \amp.ir(Array.fill(4, 0.05));
    ring = \ring.ir(Array.fill(4, 0.6));

    klank = DynKlank.ar(`[harm, amp, ring], gated * 0.01, freq.lagud(1,0.1));

    Out.ar(out, klank.tanh);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	synth = Synth(\help_Klank,[\amp,0.3]);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	// synth.set(\gate, 0);
	synth.free;
};

//------------------------------------------------------------
~next = {|d|
	var amp = m.accelMassFiltered.lincurve(0.0,0.3,-50,-2,-3);
	var al = m.accelMassFiltered.lincurve(0.0,2.5,0.02,1.0,-3);
	var notes = [0,3,5,10,12] + 60 - 24;
	// var rt = m.gyroZFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0.0,notes.size,0).asInteger;
	var rt = (d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,0.0,notes.size,0).asInteger;

  	// synth.set(\rt, notes[rt].midiratio);
	synth.set(\freq, notes[rt].midicps);
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
	[(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];
  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
