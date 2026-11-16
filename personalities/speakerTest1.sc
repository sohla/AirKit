var m = ~model;
var group;
var synth;
var mouseX = 0;
var mouseY = 0;
var replyKey = ('speakerTest_' ++ m.ptn).asSymbol;

//------------------------------------------------------------
SynthDef(\speakerTest1, { |amp = 0, rate = 4, gate = 1|
	var numChans = s.options.numOutputBusChannels;
	var x = MouseX.kr(-1, 1, 0, 0.1);
	var y = MouseY.kr(-1, 1, 0, 0.1);
	var pos = 0.75 - (atan2(y, x) / pi);
	var edge = hypot(x, y).clip(0, 1);
	var pulse = LFPulse.kr(rate, 0, 0.5).lag(0.005);
	var env = EnvGen.kr(Env.asr(0.1, 1, 0.3), gate, doneAction: 2);
	var sig = PinkNoise.ar * 1.0;
	SendReply.kr(Impulse.kr(30), '/speakerTest', [x, y]);
	sig = (PanAz.ar(numChans, sig, pos, 1, 2, 0) * edge.sqrt) + (sig ! numChans * ((1 - edge) / numChans).sqrt);
	Out.ar(0, sig * amp.lag(0.1) * env);
}).add;

//------------------------------------------------------------
~init = ~init <> { |d|
	group = Group.new;
	synth = Synth(\speakerTest1, [\amp, 0, \rate, 4], group);

	OSCdef(replyKey, { |msg|
		if(synth.notNil and: { msg[1] == synth.nodeID }, { mouseX = msg[3]; mouseY = msg[4] });
	}, '/speakerTest', s.addr);

	~vdef.(\speakerMap, { |ev, c|
		var half = c[\size];
		var mid = c[\pos];
		var src = mid + ((mouseX * half) @ (mouseY.neg * half));
		var corners = [(-1 @ -1), (1 @ -1), (1 @ 1), (-1 @ 1)];
		corners.do({ |k|
			var spk = mid + (k * half);
			var near = 1 - (spk.dist(src) / (half * 2.83)).clip(0, 1);
			c[\draw].(\square, (pos: spk, size: 14), 1, near.squared.max(0.1));
		});
		c[\draw].(\circle, (pos: src, size: 10), 1, 1);
		nil
	});

	(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port, shape: \speakerMap, fill: true, sx: 0, sy: 0, ex: 0, ey: 0, startSize: 180, endSize: 180, startWidth: 1, endWidth: 1, startColor: Color.white, endColor: Color.white, duration: inf).play;
	m.com.bgColor = Color.black;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var dying = group;
	OSCdef(replyKey) !? (_.free);
	synth !? { synth.set(\gate, 0) };
	synth = nil;
	group = nil;
	fork {
		0.5.wait;
		dying !? { dying.free };
	};
};

//------------------------------------------------------------
~next = { |d|
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	synth !? { synth.set(\amp, vol * -10.dbamp) };
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d, p|

	// Acceleration
	// [m.accelMass, m.accelMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right

	[mouseX, mouseY];
};
