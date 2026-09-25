var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.2;
m.rrateMassFilteredDecay = 0.08;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\growl, {|out=0, amp=0.0, freq=66, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1, gr=0.1, mix = 0.5|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var sub = SinOsc.ar(freq*[1.0,1.01] * 0.5, LFNoise2.ar(freq,pi * gr), (1-mix) * 4).tanh;
	var sig = LFTri.ar(freq*[1.0,1.01], 0, mix * 10);
	var sum = (sig + sub).tanh;
	var verb = FreeVerb.ar(sum, mix: 0.4, room: 0.9, damp: 0.2);
	Out.ar(out, (sum + verb) * env * amp.lag(0.02));
}).add;

//------------------------------------------------------------
~init = ~init <> { |d|

	~vdef.(\growlEdge, { |ev, c|
		var n = ev[\numPoints] ? 96;
		var mod = ev[\modulation] ? ();
		var sub = (mod[\voice] ? \tri) == \sub;
		var detune = mod[\detune] ? 1;
		var cycScale = mod[\cycles] ? 0.05;
		var scroll = mod[\scroll] ? 0.01;
		var block = mod[\block] ? 0.00133;
		var gain = mod[\gain] ? 0.5;
		var full = mod[\full] ? 0.3;
		var floor = mod[\floor] ? 0;
		var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
		var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
		var amp = m.rrateMassFiltered.lincurve(0.0, 2 * sens, -70, -25, -10);
		var ampa = m.accelMassFiltered.lincurve(0, 2 * sens, 0.0001, 0.3, -1);
		var pos = (d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5) * 2;
		var gr = pos.lincurve(-1.0, 1.0, 0.0, 2.0, -3);
		var pitches = [0,4,7,11,12,16,19,23,24] + 37;
		var index = ((((d.sensors.gyroEvent.z / pi) + 1).half) * pitches.size).asInteger;
		var f0 = pitches.clipAt(index).midicps;
		var level = ((amp.dbamp + ampa) * vol / full).clip(0, 1);
		var cycles = f0 * detune * cycScale;
		var phase = c[\now] * f0 * (scroll + detune - 1);
		var delta = block * f0 * detune;
		var a = c[\posStart];
		var b = c[\posEnd];
		var normal = Polar(1, (b - a).theta + 0.5pi).asPoint;
		var tri = { |ph| (4 * (((ph + 0.75).frac) - 0.5).abs) - 1 };
		var pts = Array.fill(n, { |i|
			var t = i / (n - 1);
			var ph = (t * cycles) - phase;
			var y = if(sub, { sin(2pi * ph) }, {
				var v = 2 * tri.(ph - (2 * delta));
				v = tri.(ph - delta) * (2 + (gr * v));
				v = tri.(ph) * (2 + (gr * v));
				(((v * 0.5) + (0.2 * sin(2pi * ph))) * gain).tanh
			});
			a.blend(b, t) + (normal * y * c[\size] * level)
		});

		c[\render].(pts, level.linlin(0, 1, 0.3, 1), level.sqrt.linlin(0, 1, floor, 1), false);
		nil
	});

	[
		[-0.94, -0.88, 0.94, -0.88, \tri, 1, 0.05, 70, 5, Color.new255(214, 96, 58, 235)],
		[0.94, -0.88, 0.94, 0.88, \sub, 1, 0.03, 36, 3, Color.new255(226, 168, 44, 220)],
		[0.94, 0.88, -0.94, 0.88, \tri, 1.009, 0.05, 70, 5, Color.new255(214, 96, 58, 235)],
		[-0.94, 0.88, -0.94, -0.88, \sub, 1, 0.03, 36, 3, Color.new255(226, 168, 44, 220)]
	].do { |edge|
		(
			type: \customVisualEvent,
			amp: 0,
			dur: 0.01,
			viewID: d.port,
			shape: \growlEdge,
			numPoints: 120,
			closed: false,
			fill: false,
			sx: edge[0], sy: edge[1],
			ex: edge[2], ey: edge[3],
			startSize: edge[7],
			endSize: edge[7],
			startWidth: edge[8],
			endWidth: edge[8],
			startColor: edge[9],
			endColor: edge[9],
			duration: inf,
			modulation: (
				voice: edge[4],
				detune: edge[5],
				cycles: edge[6],
				scroll: 0.01,
				block: 0.00133,
				gain: 0.5,
				full: 0.3,
				floor: 0,
				amp: 0
			)
		).play;
	};

	synth = Synth(\growl);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|
  var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
  var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
  var amp = m.rrateMassFiltered.lincurve(0.0, 2 * sens,-70,-5,-10);
  var ampa = m.accelMassFiltered.lincurve(0, 2 * sens,0.0001,0.3,-1);
  var pos = (d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2;
  var pitches = [0,4,7,11,12,16,19,23,24] + 25;
  var index = ((((d.sensors.gyroEvent.z/pi) + 1).half) * pitches.size).asInteger;
	var gr = (d.sensors.gyroEvent.y / pi.half).lincurve(-1.0, 1.0, 0.0, 0.2, -3);

  synth.set(\amp, (amp.dbamp + ampa) * vol * 0.5);
  synth.set(\mix, gr);
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
	[(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];

	// [m.accelMassFiltered * 3, m.rrateMassFiltered * 10, (d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];
};
