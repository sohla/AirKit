var m = ~model;
var lastTime = 0;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.4;

//------------------------------------------------------------

//------------------------------------------------------------
~init = ~init <> {|d|

// m.com.bgColor = Color.green(1);
m.com.bgColor = Color.yellow(0.5);
// m.com.bgColor = Color.black(0.3);
// m.com.bgColor = Color.blue(1);



// m.com.bgColor = Color.black(0.5);






};
//------------------------------------------------------------
~deinit = ~deinit <> {
};

//------------------------------------------------------------
~onEvent = {|e|

};

//------------------------------------------------------------
~next = {|d|

	// // Create oscillating squares in bottom half - ocean effect
	// var time = TempoClock.beats;

	// if(TempoClock.beats > (lastTime + rrand(0.3, 0.5)),{

	// 	var xPos = rrand(-0.1,0.1); // Random x position
	// 	var baseY = 4.5 - (sin(time * 0.5) * 0.5); // Bottom half of screen
	// 	var squareSize = 1000;//rrand(1300,1700);
	// 	var duration = rrand(3.0, 4.0) * 2;
	// 	var wavePhase = rrand(0, 2pi); // Random phase offset for wave

	// 	// Oscillating vertical motion - sine wave
	// 	var waveHeight = 0.1;
	// 	var yEnv = Env(
	// 		[0, waveHeight, 0, waveHeight.neg, 0],
	// 		[0.25, 0.25, 0.25, 0.25],
	// 		\sine
	// 	);
	// 	var colEnv = Env([0, 1], [2], \linear);
	// 	var colMod = ((time.mod(10)/10)*0.1);

	// 	// Oscillating rotation
	// 	var rotationAmount = rrand(0.2, 0.5);

	// 	// Ocean colors - blues and teals
	// 	var oceanColor = [
	// 		Color(0.1, 0.3, 0.6), // deep blue
	// 		Color(0.2, 0.5, 0.7), // ocean blue
	// 		Color(0.1, 0.4, 0.7), // medium blue
	// 		Color(0.3, 0.4, 0.9), // light blue
	// 		Color(0.7, 0.8, 1.0),
	// 		Color(0.2, 0.5, 0.6), // teal blue
	// 	].choose;

	// 	var ev = (
	// 		type: \customVisualEvent,
	// 		amp: 0,
	// 		viewID: d.port,
	// 		numPoints: 128,
	// 		shape: \square,
	// 		fill: true,
	// 		bgColor: Color(1.0, 1.0, 0.3).alpha_(0.7), 
	// 		rotate: 0,
	// 		startSize: squareSize,
	// 		endSize: squareSize*1.1,
	// 		sizeEnv: Env.sine(dur: 1.0, level: 1.0),
	// 		duration: duration,
	// 		startColor: oceanColor.alpha_(0.0),
	// 		endColor: oceanColor.lighten(0.2).alpha_(0.2),
	// 		startWidth: 1,
	// 		endWidth: 1,
	// 		sx: xPos,
	// 		sy: baseY,
	// 		ex: xPos, 
	// 		ey: baseY,
	// 		// yEnv: yEnv, 
	// 		rotation: sin(time) * 0.02,
	// 		modulation: (
	// 			type: \normal,
	// 			freq: rrand(0.1,0.3),
	// 			amp:rrand(80,100),
	// 			harmonics: 10,
	// 		)
	// 	);
	// 	ev.play;
	// 	lastTime = time;

	// });

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, magenta, cyan]

	// RAW values
	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// Gyro
	// [(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2];//roll
	[(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];//left right

	// MODEL value	
	// Acceleration
	// [m.accelMass, m.accelMassFiltered].lincurve(0.0,5.0,0.0,1.0,0);
	
	// Rotation Rate
	// [m.rrateMass, m.rrateMassFiltered].lincurve(0.0,1.0,0.0,1.0,0);

};




