var m = ~model;
var synth;

var ratios = [1, 2.756, 5.404, 7.933, 9.34, 11.64];
var amps   = [0.5, 0.3, 0.2, 0.12, 0.08, 0.05];
var rings  = [1.0, 0.8, 0.5, 0.35, 0.25, 0.18];

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------

SynthDef(\bellChime, { |out = 0, amp = 0.3, freq = 523, ratio = 3.0, floorDb = -48,
	deadtime = 0.07, slowAtk = 0.300, slowRel = 0.150, inGain = 0.7,
	decay = 3.0, fullScale = 0.35, curve = 1.5, window = 0.005, tone = 5000|

	var in   = SoundIn.ar(0) * inGain;
	var fast = Amplitude.kr(in, 0.001, 0.05);
	var slow = LagUD.kr(fast, slowAtk, slowRel);
	var over = fast > ((slow * ratio) + floorDb.dbamp);
	var edge = over > Delay1.kr(over);
	var trig = Trig1.kr(edge, deadtime);

	var peak   = RunningMax.kr(fast, trig);
	var report = TDelay.kr(trig, window);

	var vel   = Latch.kr(peak, report).linlin(0.0, fullScale, 0.0, 1.0).clip(0, 1).pow(curve);
	var pitch = Latch.kr(freq, report);

	var strike = Decay2.ar(T2A.ar(report), 0.0002, 0.004)
		* LPF.ar(PinkNoise.ar(1), (tone * vel).clip(200, 18000));

	var bell = DynKlank.ar(`[ratios * pitch, amps, rings * decay], strike * vel);

	Out.ar(out, (bell * amp).softclip ! 2);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	synth = Synth(\bellChime, [\amp,0.2, \ratio, 0.3, \slowRel, 0.25, \floorDb, -32, \fullScale, 0.7, \decay, 3.0]);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.free;
};

//------------------------------------------------------------
~next = {|d|
	var sens = d.params.sensitivity.lincurve(0.0, 1.0, 0.9, 0.1, 0);
	var vol = d.params.volume.lincurve(0.0, 1.0, 0.0, 1.0, 1);
	// var amp = m.accelMassFiltered.lincurve(0.0, 0.6 * sens,-18,-6,-3).dbamp;
	var notes = [0,7,12,16] + 52;
	var rt = (d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,0.0,notes.size,0).asInteger;

	synth.set(\freq, notes[rt].midicps);
	// synth.set(\amp, amp);
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
};
