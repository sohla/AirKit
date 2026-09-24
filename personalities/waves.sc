var m = ~model;
var synth;
var trig = false;
var lastTime = 0;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.3;
m.rrateMassFilteredDecay = 0.2;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\waves22, { |out=0, gate=1, amp = 0.0, lag=1,ff=200|
    var env = EnvGen.ar(Env.asr(4.5,1.0,8 ), gate, doneAction:2);

	var spd = 0.03.rrand(0.09);//MouseY.kr(0.03,0.09);
	var air = RHPF.ar(PinkNoise.ar(0.1), LFNoise2.ar([1,2]).range(9000,14000));
	var rum = RLPF.ar(BrownNoise.ar(0.1), LFNoise1.ar([3,5]).range(30,40));
	var nsa = RHPF.ar(BrownNoise.ar(0.3), ff.lag(lag));
	var sig = SinOsc.ar(spd, LFCub.ar([0.2,0.1]).range(0,1), nsa);
  var mix = sig * amp.lag(lag);
	Out.ar(out, (mix + air + rum) * env);
}).add;

//------------------------------------------------------------
~init = ~init <> {
  synth = Synth(\waves22);
    
};
//------------------------------------------------------------
~deinit = ~deinit <> {
    synth.set(\gate, 0);
};

//------------------------------------------------------------
~onEvent = {|e|
};

//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity;
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = m.accelMassFiltered.lincurve(0, 5 * sens,0.0,1.0,-3);
  var ff = m.gyroYFiltered.linexp(-1.0,1.0,200,19000);


	var time = TempoClock.beats;
	var xPos = [-0.8,-0.6,-0.3,0.0,0.2,0.5,0.7,1.0].choose; // Random x position
	var baseY =  (sin(time) * 1); // Bottom half of screen
	var squareSize = 800;//rrand(800,1000);
	var duration = 3 * amp;//rrand(4.0, 8.0);
	var wavePhase = rrand(0, 2pi); // Random phase offset for wave

	// Oscillating vertical motion - sine wave
	var waveHeight = 0.2;
	var yEnv = Env(
		[0, waveHeight, 0, waveHeight.neg, 0],
		[0.25, 0.25, 0.25, 0.25] * 4,
		\sine
	);

	// Oscillating rotation
	var rotationAmount = rrand(0.2, 0.5);

	// Ocean colors - blues and teals
	var oceanColor = [
		Color(0.1, 0.3, 0.6), // deep blue
		Color(0.2, 0.5, 0.7), // ocean blue
		Color(0.1, 0.4, 0.7), // medium blue
		Color(0.3, 0.6, 0.8), // light blue
		Color(0.2, 0.5, 0.6), // teal blue
	].choose;

	var ev = (
		type: \customVisualEvent,
		amp: 0,
		viewID: d.port,
		shape: \line,
		fill: true,
		rotate: 0,
		startSize: squareSize * 1,
		endSize: squareSize * 1,
		duration: duration,
		startColor: Color.hsv(0.5 + (sin(time * 0.2) * 0.5),1,0),
		endColor: oceanColor.lighten(0.2).alpha_(1.0),
		startWidth: 3 * amp,
		endWidth: 0,
		sx: 0,//xPos * 0.1,
		sy: (baseY * 0.5),//baseY * 0.1, // Start at base y position
		ex: 0,//xPos, // Stay in same x position
		ey: 1,//baseY.neg, // Stay in same y, oscillation via envelope
		yEnv: yEnv, // Vertical oscillation
		rotation: sin(time) * 0.003,
      modulation: (
        type: \normal,
        freq: rrand(0.04, 0.2) * 10,
        amp: 8,
        harmonics: rrand(1, 10)
    ),
	);

  if(TempoClock.beats > (lastTime + 0.05),{
    lastTime = TempoClock.beats;
    ev.play;
  });


  synth.set(\amp, amp * vol);
  synth.set(\ff, ff);
};

//------------------------------------------------------------
~plotMin = -1;  
~plotMax = 1;
~plot = { |d,p|
	//[0.2,0.4,0.6];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.5;
	// [m.accelMass.abs - m.accelMass, m.accelMass - m.accelMass.abs];
	// [d.sensors.velocity.sum.abs * 30 ,m.accelMass];// compare these values we can get direction?

	// [d.sensors.velocity.sum.abs.lincurve(0,0.03,0,1,-2)];


	// // [((m.accelMassFiltered - m.accelMassFiltered.abs)-(m.accelMassFiltered.abs - m.accelMassFiltered)).abs, m.accelMassFiltered.abs];
	// [m.rrateMassFiltered];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	[d.sensors.gyroEvent.y / pi.half];
	// [d.sensors.rotateEvent.x, d.sensors.rotateEvent.y, d.sensors.rotateEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z] * 4;
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;


};




