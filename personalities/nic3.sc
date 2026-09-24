var m = ~model;
var synth;
var buffer;

var index =0;
var trig = false;
var notes = [0,-12] + 4 + 12;
var note = notes[0];
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.3;
m.rrateMassFilteredDecay = 0.2;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

SynthDef(\pullstretchMonoQ, {|out, amp = 1, buffer = 0, envbuf = -1, pch = 1, div=1, speed = 0.008, splay = 0.3,pan=0, gate=1, delta=0, lag=0.05, ffo=10 rfo=1,pos=0|
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1).range(0.0,0.99);
  var afo = LFCub.ar(ffo,0,rfo).range(1.0 - rfo,2.0 - rfo);
	var sp = Splay.arFill(8,
		{ |i| Warp1.ar(1, buffer, lfo.linlin(0,1,0.01,0.89), pch * (1 / ((i*delta)+1)) * [1] ,splay, envbuf, 8, 0.1 * (i+1), 4)  },
		// { |i| Warp1.ar(1, buffer, pos.lag(0.2) + LFNoise2.ar(1).range(0,0.03), pch.lag(0.4) * (1 / ((i*delta)+1)) * [1] ,splay, envbuf, 8, 0.1 * (i+1), 4)  },
			1,
			1,
			0
	) ;
	var env = EnvGen.ar(Env.adsr(0.4,0.1,0.9,2.0), gate, doneAction:2);
	var mas = HPF.ar(sp,45).tanh * amp.lag(lag) * afo;
	var sig = Compander.ar(mas, mas,
			thresh: -32.dbamp,
			slopeBelow: 1,
			slopeAbove: 0.5,
			clampTime:  0.02,
			relaxTime:  0.01
	);
	sig = Pan2.ar(sig[0],pan) * env;
	Out.ar(out, ((0)!0 ++ sig));
}).add;
//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/nicSamples/1_Phrases/Actual/1.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMonoQ,[\buffer,buf,\pch,-12.midiratio, \amp,0.0, \div, 10]);
	});
};
//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.onFree({
		postf("buffer dealloc [%] \n", buffer);
		buffer.free;
	});	
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~onEvent = {|e|
};

//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity;
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	// var amp = d.sensors.velocity.sum.abs.lincurve(0,0.03,0.0,1.0,-2);
	var amp = m.accelMassFiltered.lincurve(0, 2 * sens,0.0,1,-3);
	var rfo = m.accelMassFiltered.lincurve(0, 3 * sens,0.0,1,-3);
  // var ffo = m.gyroYFiltered.lincurve(-1.0,1,1,18,-3);
  var pos = m.gyroZFiltered.lincurve(-1.0,1,0.0,1.0,0);
    
    if(amp<0.035,{
        amp=0;
        synth.set(\lag,0.8);
        if(trig, {
            trig = false;
        });
    },{
        if(trig.not, {
            trig = true;
            index = index + 1;
            notes = notes.rotate(-1);
            note = notes[0];
        });
        synth.set(\pch, note.midiratio);
        synth.set(\lag,0.1);
    });

    synth.set(\amp, amp * vol);
    // synth.set(\ffo, ffo);
    synth.set(\rfo, rfo);
    synth.set(\pos, pos);
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
	[m.gyroZFiltered];
	// [d.sensors.rotateEvent.x, d.sensors.rotateEvent.y, d.sensors.rotateEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z] * 4;
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;


};




