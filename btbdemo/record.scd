// Boots AirKit, then for each piece in setlist.json plays each performer's pfiles in equal turns on their device and records output/<piece>.wav.
// Run setlist.py first (run.sh does both).
(
var root = File.realpath(thisProcess.nowExecutingPath.dirname);
var setlist = File.readAllString(root +/+ "setlist.json").parseJSON;
var out = root +/+ "output";
var machine = Require("../code3.0/machine.scd");
var me = NetAddr("127.0.0.1", 57120);
var seconds = setlist["seconds"].asFloat;

var rosterFile = { |d|
	var lists = machine[\list];
	if(lists.isString, { lists }, { lists[d.index - 1] })
};

var roster = { |d| (machine[\listsDir] ++ rosterFile.(d)).load };

var deviceFor = { |performer|
	~devices.values.asArray.sort({ |a, b| a.index < b.index })
		.detect({ |d| rosterFile.(d).toLower.contains(performer.toLower) })
};

var play = { |d, index|
	d.personality = index;
	if(d.enabled, {
		me.sendMsg("/airkit/loadPersonality", d.port, index);
	},{
		me.sendMsg("/airkit/unmute", d.port);
	});
};

File.mkdir(out);
(root.dirname +/+ "code3.0/main.sc").load;

Routine {
	while({ w.isNil or: { s.serverRunning.not } }, { 0.5.wait });
	s.recHeaderFormat = "wav";
	s.recSampleFormat = "int24";

	setlist["prepare"].asFloat.asInteger.reverseDo { |n|
		if(n % 10 == 0, { "[btbdemo] start your OSC now - recording in % s".format(n).postln });
		1.wait;
	};

	setlist["pieces"].do { |piece|
		var jobs = List[];

		piece["parts"].keysValuesDo { |performer, names|
			var d = deviceFor.(performer);
			if(d.isNil, {
				"[btbdemo] %: no device is sending for % - skipped".format(piece["title"], performer).warn;
			},{
				var list = roster.(d);
				var found = names.collect({ |n|
					var i = list.indexOfEqual(n);
					if(i.isNil, { "[btbdemo] %: '%' is not in % - skipped".format(piece["title"], n, rosterFile.(d)).warn });
					i
				}).reject(_.isNil);
				if(found.notEmpty, { jobs.add([d, found]) });
			});
		};

		if(jobs.isEmpty, {
			"[btbdemo] %: nothing to play - no recording".format(piece["title"]).warn;
		},{
			var path = out +/+ piece["file"] ++ ".wav";
			var used = jobs.collect(_.first);

			~devices.do { |d| if(used.includes(d).not, { me.sendMsg("/airkit/mute", d.port) }) };
			jobs.do { |job| play.(job[0], job[1][0]) };
			setlist["settle"].asFloat.wait;

			"[btbdemo] recording % (% s)".format(piece["title"], seconds).postln;
			s.record(path, duration: seconds);
			jobs.do { |job|
				var d = job[0], turns = job[1];
				turns.drop(1).do { |index, k|
					AppClock.sched(seconds * (k + 1) / turns.size, { play.(d, index); nil });
				};
			};
			(seconds + 1).wait;
			"[btbdemo] wrote %".format(path).postln;
		});
	};

	"[btbdemo] done".postln;
	s.quit({ 0.exit });
	3.wait;
	0.exit;
}.play(AppClock);
)
