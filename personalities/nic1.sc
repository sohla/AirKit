var m = ~model;
var synth;
var buffer;
var notes = [-12,-8,-3,1,0];
var note = 0;
var trig = false;

m.accelMassFilteredAttack = 0.98;
m.accelMassFilteredDecay = 0.3;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\pullstretchMonoQN, {|out, amp = 1, buffer = 0, envbuf = -1, pch = 1, div=1, speed = 0.01, splay = 0.3,pan=0, gate=1, delta=0|
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1).range(0.0,0.99);
	var sp = Splay.arFill(8,
		{ |i| Warp1.ar(1, buffer, lfo.linlin(0,1,0.01,0.99), pch * (1 / ((i*delta)+1)) ,splay, envbuf, 8, 0.1 * (i+1), 4)  },
			1,
			1,
			0
	) ;
	var env = EnvGen.ar(Env.adsr(0.4,0.1,0.9,2.0), gate, doneAction:2);
	var mas = HPF.ar(sp,45) * amp.lag(0.2);
	var sig = Compander.ar(mas, mas,
			thresh: -32.dbamp,
			slopeBelow: 1,
			slopeAbove: 0.5,
			clampTime:  0.02,
			relaxTime:  0.01
	);

	sig = Pan2.ar(sig,pan) * env;
	Out.ar(out, (0)!0 ++ sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/nicSamples/4_Dreams/2.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMonoQN,[\buffer,buf, \amp,0.4, \div, 10]);
	});
};

~deinit = ~deinit <> {
	synth.onFree({
		postf("buffer dealloc [%] \n", buffer);
		buffer.free;
	});	
	synth.set(\gate, 0);
};


//------------------------------------------------------------
~next = {|d|

	var sens = d.params.sensitivity;
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var delta = m.gyroYFiltered.linlin(-1,1,10,-10).lcurve;
	var amp = m.rrateMassFiltered.lincurve(0, 1 * sens,0.0,1,1);
	var speed= m.gyroXFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0.1,0.001,-1);


	// if(amp < 0.01, {
	// 	amp = 0;
	// });

	if(amp<0.002,{
		amp=0;
		// synth.set(\lag,0.8);
		if(trig, {
				trig = false;
		});
	},{
		if(trig.not, {
			trig = true;
			// notes = notes.rotate(-1);
			// note = notes[0];
		});
		synth.set(\pch, note.midiratio);
		// synth.set(\lag,0.01);
	});

	// synth.set(\speed, speed);
	synth.set(\amp, amp * 1 * vol);
	synth.set(\delta, delta);
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// [m.accelMass, m.accelMassFiltered];

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];


	// [m.gyroXFiltered.fold(-0.5,0.5)];
	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [m.gyroYFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,1,-1)];
	[m.gyroXFiltered.fold(-0.5,0.5).linlin(-0.5,0.5,-10,10).lcurve];
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
