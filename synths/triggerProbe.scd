// ----------------------------------------------------------------------------
//  TRIGGER PROBE
//
//  Runs temp_input3's detector and temp_input4's detector side by side on
//  the same microphone, and reports what each one actually sees. Mic on
//  input 0. No audio output -- this only measures.
//
//  Load a personality first if you want to measure under performance
//  conditions, or run it on its own for a clean reading.
//
//  Evaluate the block, hit the drum a dozen times at a range of
//  dynamics, then read the post window.
// ----------------------------------------------------------------------------
(
SynthDef(\triggerProbe, { |ratio = 0.3, floorDb = -18,
	slowAtk = 0.300, slowRel = 0.25, deadtime = 0.10, i3Thresh = -6|

	var in = SoundIn.ar(0);

	// --- temp_input4 \bellChime, exactly as written ---
	var fast   = Amplitude.kr(in, 0.001, 0.05);
	var slow   = LagUD.kr(fast, slowAtk, slowRel);
	var thresh = (slow * ratio) + floorDb.dbamp;
	var over   = fast > thresh;
	var edge   = over > Delay1.kr(over);
	var trig   = Trig1.kr(edge, deadtime);

	// --- temp_input3 \warmBass, exactly as written ---
	var i3      = Amplitude.kr(in, 0.001, 0.05);
	var i3over  = i3 > i3Thresh.dbamp;
	var i3edge  = i3over > Delay1.kr(i3over);

	var tick  = Impulse.kr(4);
	var reset = TDelay.kr(tick, 0.003);

	SendReply.kr(tick, '/probeBg', [
		RunningMax.kr(fast, reset),
		RunningMax.kr(i3,   reset),
		Latch.kr(slow, tick),
		RunningMax.kr(thresh, reset),
		RunningMax.kr(edge,   reset),
		RunningMax.kr(i3edge, reset),
		RunningMax.kr(fast, 0),
		RunningMax.kr(i3,   0)
	]);

	SendReply.kr(trig, '/probeFire', [fast, slow, thresh]);
}).add;

OSCdef(\probeBg, { |msg|
	var f = msg[3], t3 = msg[4], slow = msg[5], thr = msg[6];
	var fired4 = msg[7] > 0, fired3 = msg[8] > 0;
	var maxF = msg[9], maxT3 = msg[10];
	if (f > 0.02 or: { t3 > 0.02 }) {
		"in4 %  (% dB) %    in3 %  (% dB) %    slow %   in4thresh % (% dB)".format(
			f.round(0.0001),  f.ampdb.round(0.1),  if (fired4) { "FIRE" } { "----" },
			t3.round(0.0001), t3.ampdb.round(0.1), if (fired3) { "FIRE" } { "----" },
			slow.round(0.0001),
			thr.round(0.0001), thr.ampdb.round(0.1)
		).postln;
		~probePeaks = [maxF, maxT3];
	};
}, '/probeBg');

OSCdef(\probeFire, { |msg|
	"      -> in4 fired: fast %   slow %   thresh %".format(
		msg[3].round(0.0001), msg[4].round(0.0001), msg[5].round(0.0001)).postln;
}, '/probeFire');

~probeStart = {
	~probe.free;
	~probePeaks = nil;
	~probe = Synth(\triggerProbe);
	"probe running -- Cmd+Period if anything stacks up".postln;
	~probe;
};

~probeStop = {
	~probe.free;
	~probe = nil;
	"probe stopped".postln;
};

~probeClear = {
	~probe.free;
	~probe = nil;
	OSCdef(\probeBg).free;
	OSCdef(\probeFire).free;
	"probe stopped, responders removed".postln;
};

~probeReport = {
	var f = ~probePeaks !? (_[0]) ? 0, t3 = ~probePeaks !? (_[1]) ? 0;
	"".postln;
	"---------------------------------------------------------------".postln;
	("loudest hit seen".postln);
	("  in4 fast follower   %   (% dB)".format(f.round(0.0001),  f.ampdb.round(0.1))).postln;
	("  in3 follower        %   (% dB)".format(t3.round(0.0001), t3.ampdb.round(0.1))).postln;
	"".postln;
	("suggested fullScale   %".format(f.round(0.01))).postln;
	("suggested floorDb     % dB   (6 dB under your quietest hit)".format(
		(f.ampdb - 20).round(1))).postln;
	"---------------------------------------------------------------".postln;
};
)

~probeStart.value;

// ... hit the drum a dozen times, soft to hard ...

~probeReport.value;


// ----------------------------------------------------------------------------
//  Sweep the floor while it runs. Walk floorDb down until in4 fires on
//  every hit you meant, then back off a little. Watch the FIRE columns.
// ----------------------------------------------------------------------------
~probe.set(\floorDb, -14);
~probe.set(\floorDb, -18);
~probe.set(\floorDb, -24);


// ----------------------------------------------------------------------------
//  Done. ~probeStop leaves the responders in place so you can start again;
//  ~probeClear removes them too.
// ----------------------------------------------------------------------------
~probeStop.value;
~probeClear.value;
