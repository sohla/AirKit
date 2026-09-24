var m = ~model;
var bl=false;
var group;
var fxBus;
var verbSynth;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.7;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.99;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\raindrop, {
    |out=0, freq=1000, amp=0.8, pan=0, gate=1, attack=0.001, decay=0.05,
	filterFreq=3000, filterRQ=1, wobble=10|

	  var sig, env;
    env = EnvGen.ar(Env.perc(attack, decay), gate);
    sig = SinOsc.ar(freq * LFPar.ar(wobble,pi/2, 0.2,1)) * env;
    sig = BPF.ar(sig, filterFreq, filterRQ);
  	DetectSilence.ar(sig, doneAction: 2);
    Out.ar(out, PanAz.ar(2, sig, pan, 1, 2, 0.5) * amp);
}).add;

// The reverb the drops used to each carry their own copy of, now one synth
// at the tail of the group. room is clipped : a persistent reverb fed a
// room above 1 does not decay, it grows.
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

  //----------------------------------------------------------
  // visual : not the drop - the ripple it leaves. Each raindrop lands on a
  // still surface seen from above and throws a few concentric wavefronts,
  // which expand, slow and fade. Nothing falls; the impact is the event.
  //
  // Rings are staggered in time, so a ripple is a small train of circles
  // rather than one, and each is born only once the previous has travelled.
  // They expand on age.pow(ease) with ease below 1 - fast out of the impact
  // then slowing, which is how a real wavefront loses energy.
  //
  // The circles are only slightly circular : radius carries a few percent of
  // sine ripple around the rim, rotating slowly, so the water is never quite
  // flat. wob comes from the synth's own \wobble - the same number that
  // wobbles the sine's frequency also wobbles the rim.
  //
  // Lineage: G8 circular/radial, "concentric rings ... phase, unwind", with
  // the impacts scattered as a G1 field. Traversal 6, event-triggered.
  //
  //   filterFreq -> turquoise .. slate .. ocean   (\startColor)
  //   amp        -> how far the ripple travels    (\startSize)
  //   decay      -> how long it lives             (\duration)
  //   pan        -> where it lands, left to right (\sx)
  //   wobble     -> how un-circular the rim is    (\modulation)
  //   reverbRoom -> how many wavefronts           (\modulation)
  //
  // A draw func because each wavefront carries its own radius, width and
  // alpha - one point list cannot hold a train of rings.
  ~vdef.(\ripple, { |ev, c|
    var mod = ev[\modulation] ? ();
    var rings = mod[\rings] ? 3;
    var stagger = mod[\stagger] ? 0.16;
    var wob = mod[\wob] ? 0.035;
    var harm = mod[\harm] ? 3;
    var spin = mod[\spin] ? 1.1;
    var ease = mod[\ease] ? 0.6;
    var n = ev[\numPoints] ? 56;
    var t = c[\normTime];
    var sz = c[\size];
    var pos = c[\pos];

    rings.do { |i|
      var birth = i * stagger;
      var age = (t - birth) / (1 - birth).max(0.05);
      if((age > 0) and: { age < 1 }, {
        var r = sz * age.pow(ease);
        var a = (1 - age).pow(1.7) * (1 - (i / rings * 0.45));
        c[\render].(
          Array.fill(n, { |j|
            var ang = j / n * 2pi;
            pos + Polar(
              r * (1 + (wob * sin((ang * harm) + (c[\now] * spin) + i))),
              ang
            ).asPoint
          }),
          a, a, true
        );
      });
    };
    nil
  });

  group = Group.new;
  fxBus = Bus.audio(s, 2);
  verbSynth = Synth.tail(group, \dropletVerb, [\in, fxBus, \out, 0]);

  Pdef(m.ptn,
    Pbind(
      \instrument, \raindrop,
      \group, group,
      \out, fxBus,
      \octave, 4,
      \note, Pwhite(2,40,inf),//Pseq([13,20,29], inf),//Pwhite(2,40,inf),
      \attack, 0.001,
      \filterFreq, Pexprand(90, 2000, inf) * 2,
      \filterRQ, Pwhite(0.5, 1.5, inf),
      \pan, Pwhite(-1.0, 1.0, inf),
			\args, #[],

      \type, \customVisualEvent,
      \shape, \ripple,
      \numPoints, 56,
      \fill, false,
      \closed, true,

      \sx, Pkey(\pan) * 0.85,
      \ex, Pkey(\sx),
      \sy, Pwhite(-0.75, 0.75),
      \ey, Pkey(\sy),

      \startSize, Pfunc({ |e| (e[\amp] ? 0.5).linlin(0, 1, 0, 200) }),
      \endSize, Pkey(\startSize),

      \startWidth, 3.0,
      \endWidth, 0.6,

      \startColor, Pfunc({ |e|
        var u = (e[\filterFreq] ? 1000).explin(180, 4000, 1.0, 0.0);
        Color.hsv(
          0.47.blend(0.60, u),
          0.85.blend(0.95, u) - (0.6 * sin(u * pi)),
          0.95.blend(0.70, u),
          0.9
        )
      }),
      \endColor, Pkey(\startColor),

      \duration, Pfunc({ |e| (((e[\reverbRoom] ? 0.05) * 1.6) + 0.5) }),

      \modulation, Pfunc({ |e| (
        rings: (e[\wobble] ? 100).max(1).explin(1, 14000, 1, 3),//3,//(((e[\reverbRoom] ? 0.83) * 4).round).clip(2, 5),
        stagger: rrand(0.01,0.03),
        wob: (e[\wobble] ? 100).max(1).explin(1, 14000, 0.015, 0.575),
        harm: [2, 3, 4].choose,
        spin: rrand(0.7, 1.6),
        ease: 0.6,
        amp: 0
      ) })
    )
  );
  Pdef(m.ptn).play(quant:0.2);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
  Pdef(m.ptn).remove;
  if (verbSynth.notNil) { verbSynth.set(\gate, 0) };

  fork {
    0.7.wait;
    if (group.notNil) {
      s.bind { group.freeAll };
      s.sync;
      group.free;
      group = nil;
      verbSynth = nil;
    };
    if (fxBus.notNil) {
      fxBus.free;
      fxBus = nil;
    };
  };
};

//------------------------------------------------------------
~next = {|d|

  var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
  var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
  var dur = m.gyroYFiltered.lincurve(-1.0,1.0,0.5,0.075);
  var wob = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0,1.0,0.01,14000.0,-2);
  var side  = ((d.sensors.accelEvent.y.abs + d.sensors.accelEvent.z.abs) * 0.1).lincurve(0,1.0,1.0,wob,-2);
  var dcy  = ((d.sensors.accelEvent.y.abs + d.sensors.accelEvent.z.abs) * 0.1).lincurve(0,1.0,0.05,3.0,-1);
  var verb = m.accelMassFiltered.lincurve(0, 5 * sens,0.53,0.95,2);
  var amp = m.gyroYFiltered.lincurve(-1.0,1.0,0.0,1,-2);
  // var amp = m.accelMassFiltered.lincurve(0, 0.2 * sens,0.0,1.0,-1);

  if(amp < 0.21, {amp = 0});

  // tells the visual router which device these ripples came from
  Pdef(m.ptn).set(\viewID, d.port);

  Pdef(m.ptn).set(\dur, dur);
  Pdef(m.ptn).set(\amp, amp * vol);
  Pdef(m.ptn).set(\wobble, side);
  Pdef(m.ptn).set(\decay, dcy);
  Pdef(m.ptn).set(\reverbRoom, verb);
  if (verbSynth.notNil) { verbSynth.set(\room, verb) };

  // Pdef(m.ptn).set(\startSize, amp.linlin(0, 1, 45, 200));

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
// [d.sensors.accelEvent.y.abs + d.sensors.accelEvent.z.abs] * 0.1;
	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	[(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2,m.gyroYFiltered,(d.sensors.accelEvent.y.abs + d.sensors.accelEvent.z.abs) * 0.1];
  // [m.gyroYFiltered.lincurve(-1.0,1.0,0,0.4)];
  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
