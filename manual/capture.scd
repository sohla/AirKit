// Boots AirKit (plus any fakeDevices), waits "prepare" seconds, screenshots every tab in manifest.json into shots/, then quits.
// sclang manual/capture.scd   (nothing else may be running on 57120)
(
var root = thisProcess.nowExecutingPath.dirname;
var manifest = File.readAllString(root +/+ "manifest.json").parseJSON;
var shots = root +/+ "shots";
var me = NetAddr("127.0.0.1", 57120);
var fakes = manifest["fakeDevices"] ? [];
var feed;

File.mkdir(shots);
(root.dirname +/+ "code3.0/main.sc").load;

Routine {
	while({ w.isNil or: { s.serverRunning.not } }, { 0.5.wait });
	if(fakes.notEmpty) { s.mute };

	feed = Routine {
		var t = 0;
		loop {
			fakes.do { |f, i|
				var a = t * (0.6 + (i * 0.25)), half = a * 0.5;
				me.sendMsg("/%/IMUFusedData".format(i + 1), sin(a * 1.3) * 6, cos(a) * 4, sin(a * 0.7) * 8,
					sin(half) * 0.6, cos(half * 1.4) * 0.3, sin(half * 0.8) * 0.5, cos(half));
				me.sendMsg("/%/Battery".format(i + 1), 3.9, f["battery"].asFloat);
			};
			t = t + 0.033;
			0.033.wait;
		}
	}.play(AppClock);

	1.wait;
	fakes.do { |f, i| me.sendMsg("/%/Config".format(i + 1), *f["color"].collect(_.asInteger)) };
	1.wait;
	fakes.do { |f, i|
		var d = ~devices.at(57120 + i);
		d.personality = f["personality"].asInteger;
		me.sendMsg("/airkit/loadPersonality", d.port, d.personality);
	};
	me.sendMsg("/airkit/visualTarget", "tab");
	(manifest["prepare"] ? 0).asFloat.asInteger.reverseDo { |n| if(n % 10 == 0) { "[manual] capturing in % s".format(n).postln }; 1.wait };
	manifest["settle"].asFloat.wait;

	manifest["tabs"].do { |tab|
		var bar = w.view.children.first;
		var button = bar.children[tab["index"].asInteger];
		button.mouseDownAction.value(button);
		manifest["settle"].asFloat.wait;
		w.front;
		w.view.refresh;
		0.5.wait;
		Image.fromWindow(w).write(shots +/+ tab["id"] ++ ".png", "png");
		"[manual] captured %".format(tab["id"]).postln;
	};

	feed.stop;
	s.quit({ 0.exit });
	3.wait;
	0.exit;
}.play(AppClock);
)
