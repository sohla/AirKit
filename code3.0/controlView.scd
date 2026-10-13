var renderRate = 30;
var spacing = 4;
var contentView;
var addTitleView = Require("titleView.scd");
var addPrevButton = Require("prevButton.scd");
var addNextButton = Require("nextButton.scd");

var sliderView = {|lt="default", rt="", d|
  var st;
  View().layout_(HLayout(
    UserView()
      .background_(Color.black.lighten(0.1))
      // .maxHeight_(80)
      .frameRate_(30)
      .animate_(true)
      .drawFunc_({|v|
        var vw = v.bounds.width;
        var vh = v.bounds.height;
        var tw = v.bounds.width * 0.15;
		var pos = (vw * d.params.at(lt.asSymbol)) - (tw/2);
        lt.drawLeftJustIn(Rect(10,0,vw,vh), Font(size:v.bounds.width * 0.15), Color.black());
        // rt.drawRightJustIn(Rect(vw/2,0,(vw/2)-10,vh), Font(size:48), Color.black());
        Pen.fillColor = d.color.alpha_(0.5);
        Pen.addRect(Rect(pos, 2, tw, vh - 4));
        Pen.fill;
        // lt.drawCenteredIn(Rect(pos,0,tw,40), Font(size:24), Color.black());
      })
      // The slider sends a command and never writes the model : the
      // controller owns the value, clamps it and echoes /airkit/paramIs,
      // so the webapp and MIDI see panel moves and vice versa.
      //
      // There is no paramIs responder here on purpose - drawFunc reads
      // d.params every frame, so this view is already synced from any
      // source. titleView needs responders because a Button holds its
      // own state; this one only renders the model.
      .mouseMoveAction_({|v,x,y|
        NetAddr.new("127.0.0.1", 57120)
          .sendMsg("/airkit/param", d.port, lt, x / v.bounds.width);
      })
      .mouseUpAction_({|v,x,y|
      })
      .mouseDownAction_({|v,x,y|
        NetAddr.new("127.0.0.1", 57120)
          .sendMsg("/airkit/param", d.port, lt, x / v.bounds.width);
      });
  ));
  
};

// A personality may supply its own ~controlView. Most do not, and a device
// exists before any personality is interpreted (\env defaults to nil in
// oscController's deviceProto), so both lookups have to be guarded or a nil
// reaches the GridLayout in addDeviceView. Falling back to the generic
// per-param slider grid keeps the tab useful in both cases.
var controlsView = {|d|
	var view;
	if(d.env != nil, {
		d.env.use{
			view = ~controlView.(d);
		};
	});
	view ?? {
		var sliders = d.params.keys.asArray.sort.collect({|k|
			[sliderView.(k.asString, "", d)]
		});
		View().layout_(GridLayout.columns(sliders).hSpacing_(spacing).vSpacing_(spacing))
	};
};

var controlView = {
	var osc = OSCFunc({ |msg, time, addr, recvPort|
		var d = ~devices.at(msg[1].asInteger);
		if(d != nil,{
			{addDeviceView.(d)}.defer;
		});
	},'/airkit/addDevice', NetAddr("127.0.0.1", NetAddr.langPort));
	contentView = View()
		.onClose_({osc.free})
		.layout_(GridLayout.new().hSpacing_(spacing).vSpacing_(spacing).margins_([spacing,spacing,spacing,spacing]));
		// .mouseDownAction_({false});
	contentView
};

var addDeviceView = {|d|

	var numDevices = contentView.children.size;
	var view = View()
		.background_(Color.black)
		.layout_(GridLayout.rows(
			[				
				[controlsView.(d), columns:2]
			],[
				[addTitleView.(d), columns:2],
			],[
				addPrevButton.(d),
				addNextButton.(d),
			]
		).hSpacing_(spacing).vSpacing_(spacing).margins_([0,0,0,0]));

	// add device view
	// contentView.layout.add(view, (numDevices/2).floor, numDevices%2);	
	contentView.layout.add(view, 0, numDevices);	

};

// OSCFunc({ |msg, time, addr, recvPort|
//     controls.put("volume",msg[1]/127.0);
// },'/airkit/cc/72', NetAddr("127.0.0.1", NetAddr.langPort));

// OSCFunc({ |msg, time, addr, recvPort|
//     controls.put("sensitivity",msg[1]/127.0);
// },'/airkit/cc/75', NetAddr("127.0.0.1", NetAddr.langPort));

controlView;


