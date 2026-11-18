//------------------------------------------------------------
// visualView.scd
//
// The visual tab. It is now built ALWAYS, whether or not a second screen
// is attached - what changes is only what it holds.
//
// This returns (tab:, host:). The tab is the page for the StackLayout; the
// host is an empty View inside it, not a surface. There is exactly one
// surface in the app and it lives either in that host or in the HDMI
// window; visualCore.moveSurface(host) is what puts it in one or the
// other, and main.sc drops a placeholder into whichever host is not
// currently holding it.
//
// The prev/title/next row sits in the tab, BELOW the host, so they belong to
// the panel and never travel with the surface : the HDMI window is visuals
// only, no UI. They stay on the tab when the surface is on HDMI.
//
// Building the tab unconditionally is what makes the surface movable at
// runtime : the tab set and the StackLayout never change shape, so an
// HDMI cable going in or out only re-parents the surface instead of
// forcing the whole UI to be rebuilt.
//
// The surface itself is a StackLayout whose page 0 is the \main overlay
// canvas and page 1 is the per-device grid. Switch between them with
// /airkit/visualScene overlay|grid, or the scene button on the system
// tab. Switch which SCREEN it is on with /airkit/visualTarget tab|hdmi.
//------------------------------------------------------------

var spacing = 4;
var rowHeight = 100;
var addTitleView = Require("titleView.scd");
var addPrevButton = Require("prevButton.scd");
var addNextButton = Require("nextButton.scd");

{
	var host = View().background_(Color.black);
	var buttons = View().fixedHeight_((rowHeight * 2) + spacing).layout_(GridLayout.new()
		.hSpacing_(spacing).vSpacing_(spacing).margins_([0,0,0,0]));
	var count = 0;

	var osc = OSCFunc({ |msg, time, addr, recvPort|
		var d = ~devices.at(msg[1].asInteger);
		if(d != nil, {
			{
				var view = View()
					.background_(Color.black)
					.layout_(GridLayout.rows(
						[
							[addTitleView.(d), columns:2],
						],[
							addPrevButton.(d),
							addNextButton.(d),
						]
					).hSpacing_(spacing).vSpacing_(spacing).margins_([0,0,0,0]));
				buttons.layout.add(view, 0, count);
				count = count + 1;
			}.defer;
		});
	},'/airkit/addDevice', NetAddr("127.0.0.1", NetAddr.langPort));

	var tab = View()
		.background_(Color.black)
		.onClose_({ osc.free })
		.layout_(VLayout(host, buttons)
			.spacing_(spacing).margins_([spacing,spacing,spacing,spacing]));

	(tab: tab, host: host)
};
