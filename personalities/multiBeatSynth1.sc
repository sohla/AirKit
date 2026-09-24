var m = ~model;
var group;
var drone;

var droneNote = 12 * 2;

var divs = [1, 2, 4];
var pool = [0, 4, 7, 11, 12, 11, 7, 2];

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\multiBeatVoice, {|out=0, freq=440, amp=0.2, pan=0,
    attack=0.005, release=0.4, ffreq=3000|
	var env = EnvGen.kr(Env.perc(attack, release), doneAction: 2);
	var sig = Pulse.ar(freq, 0.3, 0.5) + SinOsc.ar(freq / 2, 0, 0.6);
	sig = RLPF.ar(sig, ffreq.clip(80, 12000), 0.4);
	Out.ar(out, Pan2.ar(sig, pan, amp * env));
}).add;

SynthDef(\multiBeatDrone, {|out=0, freq=110, amp=0.05, pan=0,
    attack=1.0, release=0.8, ffreq=1200, gate=1, lag=0.1|
	var env = EnvGen.kr(Env.asr(attack, 1, release, \sin), gate, doneAction: 2);
	var f = freq.lag(lag);
	var sig = Pulse.ar(f, 0.3, 0.5) + SinOsc.ar(f / 2, 0, 0.6);
	sig = RLPF.ar(sig, ffreq.lagud(0.1,0.3).clip(80, 12000), 0.4);
	Out.ar(out, Pan2.ar(sig, pan, amp.lagud(0.08,0.9) * env));
}).add;

//------------------------------------------------------------
~init = ~init <> {|d|
	group = Group.new;

	drone = Synth(\multiBeatDrone, [
		\amp, 0.025, \freq, droneNote.midicps, \ffreq, 500, \gate, 1
	], group);

	//--------------------------------------------------------
	~vdef.(\droneRing, { |ev, c|
		var n = ev[\numPoints] ? 96;
		var mod = ev[\modulation] ? ();
		var ampMin = mod[\ampFloor] ? 0.025;
		var ampMax = mod[\ampCeil] ? 0.3;
		var noteLo = mod[\noteLo] ? 22;
		var noteHi = mod[\noteHi] ? 40;
		var lobeMin = mod[\lobeMin] ? 2;
		var lobeMax = mod[\lobeMax] ? 9;
		var depth = mod[\lobeDepth] ? 0.08;
		var amp = m.accelMassFiltered.lincurve(0, 1.0, -32, -11, -1).dbamp.max(ampMin);
		var open = amp.linlin(ampMin, ampMax, 0.0, 1.0);
		var lobes = droneNote.linlin(noteLo, noteHi, lobeMin, lobeMax).round.asInteger;
		var radius = ev[\startSize].blend(ev[\endSize], open);
		Array.fill(n, { |i|
			var angle = i / n * 2pi;
			var r = radius * (1 + (depth * sin(angle * lobes)));
			c[\pos] + Polar(r, angle).asPoint
		})
	});

	(
		type: \customVisualEvent,
		amp: 0,
		dur: 0.01,
		viewID: d.port,
		shape: \droneRing,
		fill: false,
		startSize: 70,
		endSize: 300,
		startWidth: 1,
		endWidth: 1,
		startColor: Color.new(0.2, 0.55, 0.5, 0.55),
		endColor: Color.new(0.2, 0.55, 0.5, 0.55),
		sx: 0, sy: 0, ex: 0, ey: 0,
		duration: inf,
		modulation: (
			ampFloor: 0.025,
			ampCeil: 0.3,
			noteLo: 22,
			noteHi: 40,
			lobeMin: 2,
			lobeMax: 9,
			lobeDepth: 0.08,
			amp: 0,
		),
	).play;

	Pdef(m.ptn,
		Pbind(
			\instrument, \multiBeatVoice,
			\group, group,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx)),
			\note, Pswitch(divs.collect({ |n| Pseq(pool.keep(n), 1) }), Pkey(\divIdx)),
			// \root, Pseq([0,3,-2,1].stutter(32), inf),
			\octave, Prand([4,5,6], inf),
			\dur,  Pkey(\div).reciprocal * 0.5,
			\pan, Pwhite(-0.2, 0.2),

			\type, \customVisualEvent,
			\shape, \circle,
			\sx, (Pkey(\step) / Pkey(\div) * 1.6) - 0.8,
			\ex, Pkey(\sx),
			\startSize, Pkey(\amp) * 400,
			\endSize, Pkey(\amp) * 8,
			\startColor, Color.new(0.6, 1.0, 0.8),
			\endColor, Color.new(0.1, 0.4, 0.6).alpha_(0.0),
			\startWidth, 3,
			\endWidth, 0.4,
			\duration, Pkey(\dur) * 8,
			\func, Pfunc({|e| ~onEvent.(e)}),

			\args, #[],
		)
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\ffreq, 2000);

	Pdef(m.ptn).play(quant: 1);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	if (drone.notNil) { drone.set(\gate, 0) };
	fork {
		0.9.wait;
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
		drone = nil;
	};
};

//------------------------------------------------------------
~onEvent = {|e|

	m.com.root = e.root;
	m.com.dur = e.dur;

	droneNote = (e[\note] ? 0) + (e[\root] ? 0) + (12 * 2) + 36;
	if (drone.notNil) { drone.set(\freq, droneNote.midicps) };
};

//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var idx = m.accelMassFiltered.lincurve(0, 1.5 * sens, 0, divs.size - 1, 1)
		.round.asInteger.clip(0, divs.size - 1);
	var amp = m.accelMassFiltered.lincurve(0, 1.0 * sens, -40, -10, -1);
	var ffreq = m.accelMassFiltered.lincurve(0, 1.0 * sens, 700, 6000, 2);
	var rel = m.accelMassFiltered.lincurve(0, 1.0 * sens, 0.1, 1.2, 2);

	var droneAmp = m.accelMassFiltered.lincurve(0, 2.0 * sens, -32, -10, -1);
	var droneFfreq = m.accelMassFiltered.lincurve(0, 1.0 * sens, 500, 3000, 2);

	if(droneAmp < 31.neg, { droneAmp = 90.neg });
	if(amp < 39.neg, { amp = 90.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\amp, amp.dbamp * vol);
	Pdef(m.ptn).set(\ffreq, ffreq);
	Pdef(m.ptn).set(\attack, 0.0003);
	Pdef(m.ptn).set(\release, rel);
	Pdef(m.ptn).set(\root, m.com.root ? 0);

	if (drone.notNil) {
		drone.set(
			\amp, droneAmp.dbamp * vol,
			\ffreq, droneFfreq
		);
	};
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
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];//left right

	// MODEL values
	// Acceleration
	// [m.accelMass, m.accelMassFiltered].lincurve(0.0,5.0,0.0,1.0,0);
	// Rotation Rate
	// [m.rrateMass, m.rrateMassFiltered].lincurve(0.0,1.0,0.0,1.0,0);

	// COMPUTED values : this file's own ~next, recomputed
	// [m.accelMassFiltered.lincurve(0, 1.5, 0, divs.size - 1, 1).round / (divs.size - 1)];//divIdx
	// [m.accelMassFiltered.lincurve(0, 1.0, -40, -10, -1).dbamp];//amp
	// [m.accelMassFiltered.lincurve(0, 1.0, 700, 6000, 2) / 6000];//ffreq
	// [m.accelMassFiltered.lincurve(0, 1.0, 0.1, 1.2, 2)];//release
	// [m.accelMassFiltered.lincurve(0, 1.0, 500, 3000, 2) / 3000];//droneFfreq
	// [droneNote / 127];//droneNote

	[m.accelMass, m.accelMassFiltered,
		m.accelMassFiltered.lincurve(0, 2.0, -32, -10, -1).dbamp];//droneAmp
};
