var spacing = 4;
var contentView;
var addTitleView = Require("titleView.scd");
var addReloadButton = Require("reloadButton.scd");
var addPrevButton = Require("prevButton.scd");
var addNextButton = Require("nextButton.scd");
var addThreeDeeView = Require("threeDeeView.scd");
var addPlotterView = Require("plotterView.scd");

var deviceView = {
	var osc = OSCFunc({ |msg, time, addr, recvPort|
		var d = ~devices.at(msg[1].asInteger);
		if(d != nil,{
			{addDetailView.(d)}.defer;
		});
	},'/airkit/addDevice', NetAddr("127.0.0.1", NetAddr.langPort));
	
	contentView = View()
		.onClose_({osc.free})
		.layout_(GridLayout.new()
			.hSpacing_(spacing)
			.vSpacing_(spacing)
			.margins_([spacing,spacing,spacing])
		);
	contentView
};

var addBatteryView = { |d|
	UserView()
		.animate_(true)
		.background_(Color.black)
		.maxHeight_(40)
		.frameRate_(60)
		.clearOnRefresh_(true)
		.drawFunc_({
				Pen.stringAtPoint("Battery: ", Point(5,5), Font(size:12), Color.grey);
				Pen.stringAtPoint(""++d.charge.asInteger ++ "%", Point(5,20), Font(size:12), Color.grey);
		});
};

var addIDView = {|d|
	StaticText()	
		.stringColor_(Color.grey)
		.background_(Color.black)
		.align_(\left)
		.font_(Font(size:12))
		.maxHeight_(40)
		.string_(" DEVICE:\n" ++ " " ++ d.index);
};

var addNetView = {|d|
	StaticText()	
		.stringColor_(Color.grey)
		.background_(Color.black)
		.align_(\left)
		.font_(Font(size:12))
		.maxHeight_(40)
		.string_(" " ++ d.ip ++ "\n" ++ " " ++ d.port.asString);
};

var addDetailView = {|d|

	var gl;
	var numDevices = contentView.children.size;
	var view = View()
		.layout_(gl = GridLayout.rows(
			[
				[HLayout(addIDView.(d), addNetView.(d), addBatteryView.(d)).spacing_(2), columns:2],									
			],
			[
				// View().maxHeight_(120)
			],
			[
				[addThreeDeeView.(d), columns:2],
			],
			[
				[addTitleView.(d), columns:2],
			],
			[
				addPrevButton.(d),
				addNextButton.(d)
				// [HLayout(addPrevButton.(d),addTitleView.(d), addNextButton.(d)), columns:2],
			]
			
		).hSpacing_(spacing).vSpacing_(spacing).margins_([0,0,0,0]));
		// .mouseDownAction_({false})
		// .mouseUpAction_({false});

		// .mouseUpAction_({|but|
		// 	view.background = Color.black;
		// })
		// .mouseDownAction_({|but|
		// 	view.background = Color.white.alpha_(0.1);
		// 	NetAddr("127.0.0.1", 57120).sendMsg("/airkit/calibrate", d.port);
		// });

	// gl.addSpanning(addPlotterView.(d,view), 1, 0, 1, 2);

	// add device view
	// contentView.layout.add(view, (numDevices/2).floor, numDevices%2);	
	contentView.layout.add(view, 0, numDevices);	

};
deviceView;



