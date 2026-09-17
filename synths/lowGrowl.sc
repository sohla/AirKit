
(// moving beast
{
	var in = LocalIn.ar(2);
	var sig = LFTri.ar(45*[1.0,1.003], 0, SinOsc.ar(45*MouseX.kr(1,9).round.lag(1.4),in * MouseY.kr(0.1,1.5),2));
	LocalOut.ar(sig);
	sig
}.play
)


(
{
	var in = LocalIn.ar(2);
	var sig = LFTri.ar(45*[1.0,1.003], 0, LFNoise2.ar(3,1*in,2));
	LocalOut.ar(sig);
	sig
}.play
)


(
{
	var in = LocalIn.ar(2);
	var sig = LFTri.ar(45*[1.0,1.003], 0, SinOsc.ar(MouseX.kr(1,8),0,in*2,2));
	LocalOut.ar(sig);
	sig
}.play
)


(
{
	var in = LocalIn.ar(2);
	var sig = LFTri.ar(45*[1.0,1.01], 0, 2 + (in * MouseX.kr(0,1).lincurve(0.0,1.0,0.0,2.0,-3)));
	LocalOut.ar(sig);
	sig
}.play
)
2.pow(3)




(
SynthDef(\lowgrowl, { |out, mod=0|
	var in = LocalIn.ar(2);
	var sig = LFTri.ar(45*[1.0,1.01], 0, 2 + (in * mod));
	LocalOut.ar(sig);
	Out.ar(out, sig);
}).add;
)


a = Synth(\lowgrowl);
a.set(\mod,1.5);
a.free
(
o = OSCFunc({ |msg, time, addr, recvPort|

	var val = msg[2].explin(100.0,3000.0,0.00001,2.3);
	[msg[2], val].postln;

	a.set(\mod, val);

}, '/1/AnalogIn');
)
o.free;

