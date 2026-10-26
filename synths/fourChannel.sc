(
// var input = ServerOptions.devices.indexOfEqual("SensiLab 848");
// var output = ServerOptions.devices.indexOfEqual("SensiLab 848");
var input = ServerOptions.devices.indexOfEqual("SERIES 208i");
var output = ServerOptions.devices.indexOfEqual("SERIES 208i");

o = Server.local.options;
o.inDevice = ServerOptions.devices[input];
o.outDevice = ServerOptions.devices[output];
o.numOutputBusChannels = 4;
o.numInputBusChannels = 2;

s.reboot
)

~quad = Bus.audio(s, 4);

// (
// // 4ch private bus in Pan4 order: FL, FR, BL, BR
~quad = Bus.audio(s, 4);
//
// SynthDef(\quadOut, { |in, amp=1|
// 	var sig = In.ar(in, 4) * amp;
// 	// hardware: 0=FL, 1=FR, 2=unused, 3=BL, 4=BR
// 	Out.ar(0, sig[0]);
// 	Out.ar(1, sig[1]);
// 	Out.ar(3, sig[2]);
// 	Out.ar(4, sig[3]);
// }).add;
// )
// ~out = Synth.tail(s.defaultGroup, \quadOut, [\in, ~quad]);


s.meter

(
SynthDef(\blip, { |out, freq=440, az=0|
    var sig = SinOsc.ar(freq) * EnvGen.kr(Env.perc, doneAction: 2) * 0.2;
    Out.ar(out, Pan4.ar(sig, 0, -1)); // Pan4.ar(in, xpos, ypos)
}).add;
)
~out = Synth.tail(s.defaultGroup, \quadOut, [\in, ~quad]);

Synth(\blip, [\out, ~quad]);


(
SynthDef(\noise, { |out, freq=440, az=0|
	var sig = BrownNoise.ar(0.1);
	var mx = MouseX.kr(-1,1);
	var my = MouseY.kr(-1,1);
    Out.ar(out, Pan4.ar(sig, mx, my)); // Pan4.ar(in, xpos, ypos)
}).add;
)
Synth(\noise, [\out, ~quad]);





(
SynthDef(\droplets, { |
	out=0,
	baseFrq=1000,
	amp=1.0,
	roomsize=80,
	revtime=0.85,
	damping=0.41,
	inputbw=0.19,
	spread=15,
	drylevel=(-3),
	earlylevel=(-11),
	taillevel=(-9)
	|

	var mx = MouseX.kr(-1,1);
	var my = MouseY.kr(-1,1);

    var sig = Resonz.ar(
		Array.fill(4, { Decay.ar(Dust.ar(2)) }),
        baseFrq * [1, 2, 4, 8],
        0.04
    ).sum * 10 * amp;

	var frontVerb = GVerb.ar(
            sig,
            roomsize,
            revtime,
            damping,
            inputbw,
            spread,
            0,
            earlylevel.dbamp,
            taillevel.dbamp,
            roomsize, 0.3);

	var backVerb = GVerb.ar(
            sig,
            roomsize,
            revtime * 8,
            damping,
            inputbw,
            spread,
            0,
            earlylevel.dbamp,
            taillevel.dbamp,
            roomsize, 0.3);

	var mix = Pan4.ar(sig,mx,my) + ( [frontVerb] ++ [backVerb]);

	Out.ar(out, mix);
}).add
)


Synth(\droplets, [\out, ~quad, \baseFrq, 2800, \amp,0.5]);




(
{
    PanAz.ar(
        numChans: 4,
        in: ClipNoise.ar(0.1),
        pos: LFSaw.kr(MouseX.kr(0.2, 20, 'exponential')),
        level: 0.5,
        width: 2,
        orientation: 0.5
    );
}.scope
)





(
{
    var w, x, y, p, q, a, b, c, d;

	// p = WhiteNoise.ar(0.05); // source
	p = Resonz.ar(Array.fill(4, { Decay.ar(Impulse.ar(2)) }), 222 * [1, 2, 4, 8],0.04).sum * 10;
	// q = LFSaw.ar(200, 0, 0.03)+LFSaw.ar(200.37, 0, 0.03)+LFSaw.ar(201, 0, 0.03);
	q = Resonz.ar(Array.fill(4, { Decay.ar(Dust.ar(2)) }), 111 * [1, 2, 4, 8],0.04).sum * 10;
    // B-format encode 2 signals at opposite sides of the circle
    #w, x, y = PanB2.ar(p, -0.5) + PanB2.ar(q, 0.5);

    #x, y = Rotate2.ar(x, y, MouseX.kr(-1, 1));

    // B-format decode to quad
    #a, b, c, d = DecodeB2.ar(4, w, x, y);

    [a, b, d, c] // reorder to my speaker arrangement: Lf Rf Lr Rr
}.play;
)
