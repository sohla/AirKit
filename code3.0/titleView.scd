var addTitleView = { |d|
	var fs = 70 - (d.name.size * 3.5);
	var button = Button()
		.minWidth_(100)
		.minHeight_(100)
		.font_(Font(size:fs, bold:true))
		.states_([
			[d.name, Color.black, d.color.alpha_(0.8)],
			[d.name, Color.black.alpha_(0.5), Color.white.alpha_(0.1)],
		])
		.value_(if(d.enabled, 0, 1))
		.action_({|but|
			NetAddr.new("127.0.0.1", 57120).sendMsg(if(but.value == 0, "/airkit/unmute", "/airkit/mute"), d.port);
		});

	OSCFunc({ |msg, time, addr, recvPort|
		if(msg[1].asInteger == d.port, {
			var fs = 70 - (msg[2].asString.size * 3.5);
			{
				button.font = Font(size:fs, bold:true);
				button.states = [
				[msg[2].asString, Color.black, d.color.alpha_(0.8)],
				[msg[2].asString, Color.black.alpha_(0.5), Color.white.alpha_(0.1)],
			];
			}.defer;
		});
	},'/airkit/personalityName', NetAddr("127.0.0.1", NetAddr.langPort));

	OSCFunc({ |msg, time, addr, recvPort|
		if(msg[1].asInteger == d.port, {
			{ button.value = 1 }.defer;
		});
	},'/airkit/mute');

	OSCFunc({ |msg, time, addr, recvPort|
		if(msg[1].asInteger == d.port, {
			{ button.value = 0 }.defer;
		});
	},'/airkit/unmute');

	button
};
addTitleView

