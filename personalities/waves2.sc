var m = ~model;
var buffers;
var bi = 0;
var dur = 0.3;
var synth;
// var trig = false;
var lastTime = 0;
var bgWaveBuffer1;
var bgWaveSynth1;
var bgWaveBuffer2;
var bgWaveSynth2;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\simple, {|out=0, amp=0.0, freq=440, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1|
	// var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var env = EnvGen.kr(Env.perc(attack, release), gate, timeScale: 1,doneAction: 2);
	var sig = SinOsc.ar(freq,0,0.5)!2;
    Out.ar(out, sig * env * amp);
}).add;

SynthDef(\waveSampler, {|bufnum=0, out=0, amp=0.5, rate=1, start=0.2, pan=0, freq=440,
	attack=0.01, decay=0.1, sustain=0.3, release=3.2, gate=1,cutoff=20000, rq=0.9|
	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	// var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: 2,doneAction: 2);
	var env = EnvGen.kr(Env.perc(attack, release), gate, timeScale: 1,doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: 0);
	// sig = RLPF.ar(sig, cutoff, rq);// + osc;
	sig = Balance2.ar(sig[0], sig[1], pan, amp);
	sig = LeakDC.ar(sig * env);
	Out.ar(out, sig);
}).add;


SynthDef(\waveSampler, {|bufnum=0, out=0.5, amp=0.5, rate=1, start=0, pan=0, freq=440,
	attack=0.01, decay=0.1, sustain=0.3, release=3.2, gate=1,cutoff=20000, rq=0.9|
	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	// var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: 2,doneAction: 2);
	var env = EnvGen.kr(Env.perc(attack, release), gate, timeScale: 1,doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: 0);
	// sig = RLPF.ar(sig, cutoff, rq);// + osc;
	sig = Balance2.ar(sig[0], sig[1], pan, amp);
	sig = LeakDC.ar(sig * env);
	Out.ar(out, sig);
}).add;


SynthDef(\looper, {|bufnum=0, out=0, amp=1.0, rate=1, start=0, pan=0, freq=440,
	attack=2.1, decay=0.1, sustain=0.99, release=1.2, gate=1,cutoff=20000, rq=0.9|
	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: 1,doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: 1);
	// sig = RLPF.ar(sig, cutoff, rq);// + osc;
	sig = Pan2.ar(sig, pan, amp);
	sig = LeakDC.ar(sig * env);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var folder = PathName("~/Downloads/waveSamples/oneshots");
	var bgWave1 = PathName("~/Downloads/waveSamples/bgs/wave_bg_16.wav");
	var bgWave2 = PathName("~/Downloads/waveSamples/bgs/wave_bg_splashes_16.wav");

	postf("loading samples : % \n", folder);

	buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
			buf.normalize(0.8);

			if(folder.entries.size - 1 == i,{   
				"samples loaded".postln;
			});
		});
	});

	bgWaveBuffer1 = Buffer.read(s, bgWave1.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		bgWaveSynth1 = Synth(\looper, [\bufnum, buf, \amp, 0.3]);
	});
	bgWaveBuffer2 = Buffer.read(s, bgWave2.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		bgWaveSynth2 = Synth(\looper, [\bufnum, buf, \amp, 0.2]);
	});

};

//------------------------------------------------------------
~deinit = ~deinit <> {

	bgWaveSynth1.set(\gate, 0);
	bgWaveSynth2.set(\gate, 0);

	bgWaveSynth1.onFree({
		"bg synth1 on free".postln;
		bgWaveBuffer1.free;
	});

	bgWaveSynth2.onFree({
		"bg synth2 on free".postln;
		bgWaveBuffer2.free;
	});

	buffers.do({|buf|
		postf("buffer dealloc [%] \n", buf);
		buf.free;
	});

};

//------------------------------------------------------------
~next = {|d|
  var sens = d.params.sensitivity;
  var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
  var amp = m.accelMassFiltered.lincurve(0, 4 * sens,0.3,2, 2);

	var time = TempoClock.beats;
	var xPos = [-0.8,-0.6,-0.3,0.0,0.2,0.5,0.7,1.0].choose; // Random x position
	var baseY =  (sin(time) * 1); // Bottom half of screen
	var squareSize = 200;//rrand(800,1000);
	var duration = rrand(1, 2);
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
		shape: \circle,
		fill: false,
		rotate: 0,
		startSize: squareSize * 0.01,
		endSize: squareSize * 1,
		duration: duration,
		startColor: oceanColor,
		endColor: oceanColor.lighten(0.2).alpha_(0),
		startWidth: 10.5,
		endWidth: 1,
		sx: (baseY * 0.5),//xPos * 0.1,
		sy: (baseY * 0.5),//baseY * 0.1, // Start at base y position
		ex: 0,//xPos, // Stay in same x position
		ey: 1,//baseY.neg, // Stay in same y, oscillation via envelope
		yEnv: yEnv, // Vertical oscillation
		rotation: sin(time) * 0.003,
      modulation: (
        type: \noise,
        freq: 3,
        amp: 10,
        harmonics: 15
    ),
	);

	if(TempoClock.beats > (lastTime + rrand(0.1,0.2)),{
		lastTime = TempoClock.beats;
		if(m.accelMass>0.05,{
	    ev.play;
			synth = Synth(\waveSampler, [\bufnum, bi, \amp, amp * 0.2 * vol]);
			bi = bi + 1;
			if(bi >= (buffers.size-1),{bi=0});
		},{
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

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right

  [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
