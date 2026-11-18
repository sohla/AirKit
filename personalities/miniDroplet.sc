/*
gestures:    [shake, tilt, roll, twist]
description: Water droplets — a bar of plinks that splits into faster, wider figures the more you shake; roll to make them wobble, tilt to let them ring, twist to open them up
internals:   Ported from Airsticks-RPI personalities/miniDroplet.sc. Pdef on ~beatClock firing self-freeing \miniDroplet synths (wobbling SinOsc through a BPF, tanh, DetectSilence) into a dedicated Group, through one \dropletVerb placed after the Group on a private bus. SynthDefs and Pbind material verbatim; visual keys dropped (no views on COTF hosts). RPI's 0.8 s beat = one ~beatClock bar (8 events). No samples. Patch trim not set — verify at bench.
sound:       soft sine droplets, accented on the downbeat, with a pitch wobble and band-pass colour; light reverb
pitch:       notes [D E C B A] (+24), downbeat always the first (D, octave 3), other steps pick from the first `pal` notes in octaves 2-4. Idle: root 0 (RPI default). Tuning: root -5 and pal 1, so every droplet is an A. Piece: \root puts the downbeat on the lowest note of ctx.voicePool. Curtain holds the last root.
rhythm:      bar subdivided 1 / 2 / 6 / 8 by accel; tuning and curtain hold one droplet per bar
*/

var m = ~model;
var ob = ~outBus ? 0;
var group;
var fxBus;
var verbSynth;

var beat = 8;   // ~beatClock events per RPI 0.8 s (one bar)
var divs = [1, 2, 6, 8];
var notes = [2,4,0,-1,-3].stutter(4) + 24;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\miniDroplet, {
    |out=0, freq=1000, amp=0.8, pan=0, gate=1, attack=0.001, decay=0.05,
	filterFreq=3000, filterRQ=1, wobble=10|

	  var sig, env;
    env = EnvGen.ar(Env.perc(attack, decay), gate);
    sig = SinOsc.ar(freq * LFPar.ar(wobble,pi/2, 0.1, 1)) * env;
    sig = BPF.ar(sig, filterFreq, filterRQ).tanh;
	// sig = BLowShelf.ar(sig, 90, 0.3, );
	sig = Pan2.ar(sig, pan);
	Out.ar(out, sig * amp);
  	DetectSilence.ar(sig, doneAction: 2);
    // Out.ar(out, PanAz.ar(2, sig, pan, 1, 2, 0.8) * amp);
    Out.ar(out, Pan2.ar(sig, pan) * amp);
    // Out.ar(out, Mix.ar(sig) * amp);
}).add;

SynthDef(\dropletVerb, {
    |in=0, out=0, mix=0.2, room=0.83, damp=0.5, amp=1, gate=1, release=0.5|
	var sig = In.ar(in, 2);
	var env = EnvGen.kr(Env.asr(0.01, 1, release), gate, doneAction: 2);
	sig = FreeVerb2.ar(sig[0], sig[1], mix, room.clip(0, 0.95), damp);
	sig = LeakDC.ar(sig);
	Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	topEnvironment.use {
		group = Group.new;
		fxBus = Bus.audio(s, 2);
		// after the Group, not inside it: ~onResync's freeAll must not take the reverb
		verbSynth = Synth.after(group, \dropletVerb, [\in, fxBus, \out, ob, \mix, 0.2, \room, 0.3, \damp, 0.5]);

		Pdef(m.ptn,
			Pbind(
				\instrument, \miniDroplet,
				\group, group,
				\out, fxBus,
				\sendGate, false,

				\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),             Pkey(\divIdx)),
				\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),     Pkey(\divIdx)),
				\dur,  Pkey(\div).reciprocal * beat,
				// \root, Pseq([0,5,-2,2,0].stutter(64), inf),
				// \func, Pfunc({ |e| m.com.root = e[\root] }),

				\accent, Pfunc({ |e|
					var st = e[\step] ? 0, dv = e[\div] ? 1;
					if (st == 0, { 0.45 }, {
						if ((dv > 3) and: { st == (dv div: 2) }, { 0.7 }, { 0.45 })
					})
				}),

				\scale, Scale.minor,
				\pick, Pfunc({ |e| if(e[\step] == 0, { 0 }, { (e[\pal] ? 1).rand }) }),
				\note, Pfunc({ |e| notes[e[\pick].clip(0, notes.size - 1)] }),
				\octave, Pfunc({ |e| if(e[\step] == 0, { 3 }, { [2,3,4].choose }) }),

				\attack, 0.001,
				\filterRQ, Pwhite(0.5, 1.5),
				\pan, Pwhite(-0.8, 0.8),

				\amp, Pfunc({ |e| (e[\gestAmp] ? 0.12) * (e[\accent] ? 1.0) })
			);
		);

		Pdef(m.ptn).set(\divIdx, 0);
		Pdef(m.ptn).set(\pal, 1);
		Pdef(m.ptn).set(\gestAmp, 0);
		Pdef(m.ptn).set(\decay, 0.3);
		Pdef(m.ptn).set(\filterFreq, 1500);
		Pdef(m.ptn).set(\wobble, 10);
		Pdef(m.ptn).set(\root, 0);

		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
	};

	~onResync = { |idx|
		topEnvironment.use {
			Pdef(m.ptn).stop;
			s.bind { group.freeAll };
			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		};
	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	var g = group;
	var v = verbSynth;
	var bus = fxBus;
	group = nil;
	verbSynth = nil;
	fxBus = nil;

	Pdef(m.ptn).remove;

	fork {
		0.7.wait;
		if (v.notNil) { v.set(\gate, 0) };
		0.5.wait;
		if (g.notNil) {
			s.bind { g.freeAll };
			s.sync;
			g.free;
		};
		if (bus.notNil) { bus.free };
	};
};

//------------------------------------------------------------
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  { },
		\piece,   { },
		\curtain, { },
		\silent,  { Pdef(m.ptn).set(\gestAmp, 0) }
	);
};

//------------------------------------------------------------
~idleNext = {|d, ctx|
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var e = m.accelMassFiltered;
	var idx = e.lincurve(0, 2.0 * sens, 0, divs.size - 1, 1).round.asInteger.clip(0, divs.size - 1);
	var pal = e.lincurve(0, 2.0 * sens, 1, notes.size, 1).round.asInteger.clip(1, notes.size);
	var amp = e.lincurve(0.0, 0.2 * sens, -50, -1, -2);

	// var wob = e.lincurve(0, 3.0 * sens, 0.000001, 130, 28);
	// var wob = 400;
	var wob = ((d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5) * 2).lincurve(-1.0, 1.0, 100, 400.0, -2);
	var cut = m.rrateMassFiltered.linexp(0.0, 2.0 * sens, 180, 14000);
	var ring = (d.sensors.gyroEvent.y / pi.half).lincurve(-1.0, 1.0, 0.05, 1.9, 2);
	if(amp < 39.neg, { amp = 120.neg });

	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\pal, pal);
	Pdef(m.ptn).set(\gestAmp, amp.dbamp * vol * 0.8);
	Pdef(m.ptn).set(\filterFreq, cut);
	Pdef(m.ptn).set(\decay, ring);
	Pdef(m.ptn).set(\wobble, wob);
	Pdef(m.ptn).set(\root, 0);
};

~tuningNext = {|d, ctx|
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var e = m.accelMassFiltered;
	var amp = e.lincurve(0.0, 0.4 * sens, -50, -12, -4);
	var wob = ((d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5) * 2).lincurve(-1.0, 1.0, 100, 400.0, -2);
	var cut = m.rrateMassFiltered.linexp(0.0, 2.0 * sens, 180, 14000);
	var ring = (d.sensors.gyroEvent.y / pi.half).lincurve(-1.0, 1.0, 0.05, 1.9, 2);
	if(amp < 39.neg, { amp = 120.neg });

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\pal, 1);
	Pdef(m.ptn).set(\gestAmp, amp.dbamp * vol * 0.8);
	Pdef(m.ptn).set(\filterFreq, cut);
	Pdef(m.ptn).set(\decay, ring);
	Pdef(m.ptn).set(\wobble, wob);
	Pdef(m.ptn).set(\root, -5);   // downbeat is 62 (D) at root 0; -5 puts every droplet on A
};

~pieceNext = {|d, ctx|
	var pool = (ctx !? { ctx.voicePool }) ? [69];
	var loud = ((ctx !? { ctx.loudness }) ? 1.0).linlin(0, 1, 0.3, 1.0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var e = m.accelMassFiltered;
	var idx = e.lincurve(0, 2.0 * sens, 0, divs.size - 1, 1).round.asInteger.clip(0, divs.size - 1);
	var pal = e.lincurve(0, 2.0 * sens, 1, notes.size, 1).round.asInteger.clip(1, notes.size);
	var amp = e.lincurve(0.0, 0.2 * sens, -50, -1, -2);
	var wob = ((d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5) * 2).lincurve(-1.0, 1.0, 100, 400.0, -2);
	var cut = m.rrateMassFiltered.linexp(0.0, 2.0 * sens, 180, 14000);
	var ring = (d.sensors.gyroEvent.y / pi.half).lincurve(-1.0, 1.0, 0.05, 1.9, 2);
	if(amp < 39.neg, { amp = 120.neg });
	if(pool.isEmpty, { pool = [69] });

	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\pal, pal);
	Pdef(m.ptn).set(\gestAmp, amp.dbamp * vol * 0.8 * loud);
	Pdef(m.ptn).set(\filterFreq, cut);
	Pdef(m.ptn).set(\decay, ring);
	Pdef(m.ptn).set(\wobble, wob);
	Pdef(m.ptn).set(\root, (pool.minItem.asInteger - 62).wrap(-6, 5));   // downbeat onto the pool's lowest note
};

~curtainNext = {|d, ctx|
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	var amp = (m.rrateMassFiltered * 2.0).lincurve(0, 1.0, -80, -35, -4);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\gestAmp, amp.dbamp * vol * 0.8);
};

//------------------------------------------------------------
~onTick    = {|ctx| };
~onHalf    = {|ctx| };
~onBeat    = {|ctx| };
~onBar     = {|ctx| };
~onPhrase  = {|ctx| };
~onSection = {|ctx| };
~onChord   = {|ctx| };
~onKey     = {|ctx| };
~onScale   = {|ctx| };

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, cyan , magenta]??

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;

	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	[m.accelMass, m.accelMassFiltered];
	// [d.sensors.accelEvent.x.abs * d.sensors.accelEvent.y.abs *  m.accelMassFiltered] * 0.5;

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2];//wobble
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];
	// [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
