var m = ~model;
var synths = Array.newClear(4);
var states = 0!4;
var buffers;
var group;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\fourButtonSamples, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440, attack=0.01, decay=0.1, sustain=0.9, release=3.7, gate=1,cutoff=20000, rq=1, loop=1|
	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
  var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: loop);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan,  env);
		sig = Compander.ar(sig, sig,
						thresh: -32.dbamp,
						slopeBelow: 1,
						slopeAbove: 0.5,
						clampTime:  0.02,
						relaxTime:  0.01
				);
    Out.ar(out, sig * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	var folder  = PathName("~/Downloads/yourDNASamples/birdcalls");
	postf("loading samples : % \n", folder);

	group = Group.new;

	buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
		});
	});
};
//------------------------------------------------------------
~deinit = ~deinit <> {
	var oldGroup = group;
	var oldBuffers = buffers;
	group = nil;
	buffers = nil;
	synths.do({|o| o !? { o.set(\gate, 0) } });
	synths = Array.newClear(4);
	fork {
		3.9.wait;
		if (oldGroup.notNil) {
			s.bind { oldGroup.freeAll };
			s.sync;
			oldGroup.free;
		};
		oldBuffers.do({|buf|
			postf("buffer dealloc [%] \n", buf);
			buf.free;
			s.sync;
		});
	};
};

//------------------------------------------------------------
~next = {|d|
	var amps = [3,4.4,6,8];
  states.do({|o,i|
	    if(d.sensors.digiInEvent[i] == 1, {
      if(o == 1,{
      	states[i] = 0;
        //max 11
				synths.put(i, Synth(\fourButtonSamples, [\bufnum, buffers[i+0], \rate, 1, \amp, amps[i]], group));
      	// ["on",i].postln;
    	});
		},{
      if(o == 0,{
      	states[i] = 1;
				synths[i].set(\gate, 0);
				synths[i] = nil;
      	// ["off",i].postln;
    	});
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
