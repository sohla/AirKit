var m = ~model;
var bl=false;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.7;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\simple, {|out=0, amp=0.0, freq=440, attack=0.001, decay=0.03, sustain=0.8, release=2.59, gate=1|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var li = LocalIn.ar(2);
	var sig = SinOsc.ar(freq, li * 1,0.5)!2;
	LocalOut.ar(sig);
    Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {
  Pdef(m.ptn,
    Pbind(
      \instrument, \simple,
      \octave, 3,
	  \root, Pseq([3].stutter(16), inf),
      \note, Pseq([0,4,7,11,12,11,7,4].stutter(4), inf),
      \attack,0.03,
      \decay, 0.1,
      \sustain,0.1,
      \release,1.04,
	//   \amp,0.5,
      \args, #[],
    )
  );
  Pdef(m.ptn).play(quant:0.2);  
};

//------------------------------------------------------------
~deinit = ~deinit <> {
  Pdef(m.ptn).remove;

};

//------------------------------------------------------------
~next = {|d|

  var sens = d.params.sensitivity;
  var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
  var dur = m.rrateMassFiltered.lincurve(0, 2 * sens,0.4,0.04,-3);

  Pdef(m.ptn).set(\dur, dur);
  Pdef(m.ptn).set(\amp, vol * 0.8);

 	if(m.rrateMassFiltered > 0.1,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:dur);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});

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
