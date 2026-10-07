//------------------------------------------------------------
// visualCore.scd
//
// Canvases as buses.
//
// A "canvas" is a named drawing surface. Devices are ROUTED to canvases
// via ~visuals.routes, so a personality never needs to know where its
// shapes end up. One event can fan out to several canvases.
//
//   ~visuals.canvases  \name -> (view:, events:, bg:)
//   ~visuals.com       the personalities' shared m.com - its \bgColor is
//                      the ground of every canvas (see core.ground)
//   ~visuals.routes    devicePort -> [\canvasName, ...]
//   ~visuals.vdefs     devicePort -> (\shapeName -> drawFunc)
//
// GOTCHA for every *Env key (\sizeEnv, \xEnv, \colorEnv, \widthEnv ...):
// Env implements embedInStream (Env.sc:277), so inside a Pbind an Env is
// consumed as a time-varying VALUE - the event gets a Float, and the draw
// call fails with "Message 'at' not understood". Pass it as
//   \sizeEnv, Pfunc({ myEnv })
// A direct (type: \customVisualEvent, ...).play is unaffected: nothing
// streams the value, so a literal Env arrives intact.
//
// Scenes are just routing-table swaps:
//   \overlay : every device -> \main        (all animations composited)
//   \grid    : device N     -> \devN        (one canvas each)
//
// Back-compat: personalities already pass `viewID: d.port`. That used to
// mean "draw into device d's view"; it now means "I am device d" and the
// router resolves it. No personality edits required.
//------------------------------------------------------------

var vdefLib = Require("vdefLib.scd");
var defaultEnv = Env([0, 1], [1], \linear);
var spacing = 4;

var core = (
	canvases: Dictionary.new,
	routes: Dictionary.new,
	vdefs: Dictionary.new,
	scene: \overlay,
	stack: nil,
	gridView: nil,
	gridCount: 0,
	latency: 0.2	// visuals are stamped this far ahead so they land with the audio
);

//------------------------------------------------------------
// draw one canvas
//------------------------------------------------------------
var drawCanvas = { |name, view|
	var c = core.canvases[name];
	if(c.notNil, {
		var now = thisThread.seconds;
		var mx = view.bounds.width / 2.0;
		var my = view.bounds.height / 2.0;

		if(core.dbgOn == true, {
			core.dbgCount = (core.dbgCount ? 0) + 1;
			if((core.dbgCount % 60) == 0, {
				postf("[draw %] bounds=% events=% now=%\n",
					name, view.bounds, c[\events].size, now.round(0.01));
				c[\events].do { |e, i|
					if(i < 2, {
						postf("    ev% start=% elapsed=% dur=%\n",
							i, e[\startTime].round(0.01),
							(now - e[\startTime]).round(0.01), e[\duration]);
					});
				};
			});
		});

		c[\events].do { |event|
			var elapsed = now - event[\startTime];
			var duration = event[\duration] ? 1;
			var normTime = 0.0;

			if(duration != 0, { normTime = elapsed / duration });

			// normTime < 0 means the event is still latency-delayed
			if((normTime >= 0.0) and: { normTime < 1.0 }, {

				var x = (event[\sx].blend(event[\ex], event[\xEnv].at(normTime)) * mx) + mx;
				var y = (event[\sy].blend(event[\ey], event[\yEnv].at(normTime)) * my) + my;
				var pos = x @ y;
				var size = event[\startSize].blend(event[\endSize], event[\sizeEnv].at(normTime));
				var width = event[\startWidth].blend(event[\endWidth], event[\widthEnv].at(normTime));
				var col = event[\startColor].blend(event[\endColor], event[\colorEnv].at(normTime));
				var points, vdef, table, renderPoints, ctx, drawShape;

				// The whole render pipeline as one callable : warp -> trace ->
				// close -> fill/stroke, using this event's resolved values. The
				// built-in path calls it once; a DRAW FUNC is handed it as
				// c[\render] so each of its sub-paths goes through the same code
				// instead of reimplementing \modulation, \closed and \fill by
				// hand - which is how those quietly go missing.
				//
				// wScale / aScale multiply the event's own width and alpha, for
				// marks whose parts vary - a decaying partial, one mode of a
				// membrane. Absolute values still come from the event.
				renderPoints = { |pts, wScale = 1, aScale = 1, closed|
					var p = pts;
					var useCol = Color.new(col.red, col.green, col.blue,
						col.alpha * aScale);
					if(p.notNil and: { p.size > 1 }, {

						// NB the displacement is windowed by |sin(harmonics * t * 2pi)|,
						// which is 0 at both ends of the path so a warped shape does not
						// tear at its seam. A 2-point path is only ends, so it never moves.
						if(event[\modulation].notNil, {

							var modFreq = event[\modulation][\freq] ? 0.5;
							var baseModAmp = event[\modulation][\amp] ? 5;
							var modPhase = event[\modulation][\phase] ? 0;
							var modType = event[\modulation][\type] ? \radial;
							var modAmp = baseModAmp * (1.0 - event[\sizeEnv].at(normTime));
							var lfoValue = sin(2pi * modFreq * now + modPhase) * modAmp;
							var harmonics = event[\modulation][\harmonics] ? 1;

							p = p.collect { |point, i|
								var t = i / (p.size - 1);
								var vec, len, angle, modVector;

								switch(modType,
									\radial, {
										var modFactor = lfoValue * sin(harmonics * t * 2pi).abs;
										vec = point - pos;
										len = vec.rho;
										angle = vec.theta;
										len = len + modFactor;
										pos + Polar(len, angle).asPoint;
									},
									\normal, {
										vec = point - pos;
										angle = vec.theta;
										modVector = Polar(lfoValue * sin(harmonics * t * 2pi), angle + 0.5pi).asPoint;
										point + modVector;
									},
									\noise, {
										var seed = (i * 1000) + (now.floor * 50);
										var rnd = {|s| sin(s * 12345.6789).abs };
										var jitterFactor = sin(t * harmonics * 2pi);
										var jitterX = lfoValue * jitterFactor * (rnd.(seed) * 2 - 1);
										var jitterY = lfoValue * jitterFactor * (rnd.(seed + 500) * 2 - 1);
										point + (jitterX @ jitterY);
									},
									\phase, {
										var idx = (i + (lfoValue * harmonics)).wrap(0, p.size - 1);
										var idealPoint = p[idx.floor];
										var nextPoint = p[idx.ceil % p.size];
										var blend = idx - idx.floor;
										idealPoint.blend(nextPoint, blend);
									},
									{ point }
								);
							};
						});

						Pen.width = width * wScale;
						Pen.fillColor = useCol;
						Pen.strokeColor = useCol;

						Pen.moveTo(p[0]);
						p[1..].do { |point| Pen.lineTo(point) };
						if(closed ? (event[\closed] ? true), { Pen.lineTo(p[0]) });

						if(event[\fill] ? false, {
							if(event[\shape] == \line, { Pen.stroke },{ Pen.fill });
						},{
							Pen.stroke;
						});
					});
				};

				// shape lookup : the personality's own vdefs, then the shared library
				table = core.vdefs[event[\src]];
				if(table.notNil, { vdef = table[event[\shape]] });
				if(vdef.isNil, {
					table = core.vdefs[\global];
					if(table.notNil, { vdef = table[event[\shape]] });
				});

				Pen.width = width;
				Pen.rotate(event[\rotation], pos.x, pos.y);
				Pen.fillColor = col;
				Pen.strokeColor = col;

				// \points short-circuits the lookup entirely
				if(event[\points].notNil, {
					points = event[\points];
				},{

					// an unrecognised name falls back to a circle, but says so once
					if(vdef.isNil, {
						vdef = vdefLib[\circle];
						if(core.warnedShapes.includes(event[\shape]).not, {
							core.warnedShapes.add(event[\shape]);
							"[visuals] unknown shape % - drawing a circle".format(event[\shape]).warn;
						});
					});

					// Either contract:
					//   points func : returns an Array of Points, and the render call
					//                 below applies \modulation, \closed and \fill.
					//   draw func   : draws with Pen itself, usually via c[\render],
					//                 and returns anything that is not an Array.
					//
					// Position, rotation, Pen.width and both pen colours are already
					// applied either way.
					ctx = (
						pos: pos,
						posStart: ((event[\sx] * mx) + mx) @ ((event[\sy] * my) + my),
						posEnd: ((event[\ex] * mx) + mx) @ ((event[\ey] * my) + my),
						size: size,
						width: width,
						color: col,
						rotation: event[\rotation],
						normTime: normTime,
						elapsed: elapsed,
						now: now,
						bounds: view.bounds,
						view: view,
						render: renderPoints
					);

					// c[\draw] : use any library shape as a sub-path of a draw func.
					// over is an Event of ctx overrides - usually (pos:) and (size:) -
					// so a mark can place the same generator repeatedly. Goes through
					// renderPoints, so sub-paths get \modulation, \closed and \fill.
					// closed overrides \closed for this sub-path only - an arc is open
					// while the rings around it are not.
					drawShape = { |name, over, wScale = 1, aScale = 1, closed|
						var localTable = core.vdefs[event[\src]];
						var fn = nil;
						var sub = ctx.copy;
						if(localTable.notNil, { fn = localTable[name] });
						fn = fn ? vdefLib[name] ? vdefLib[\circle];
						if(over.notNil, { sub.putAll(over) });
						renderPoints.(fn.(event, sub), wScale, aScale, closed);
					};
					ctx[\draw] = drawShape;

					points = vdef.(event, ctx);

					// must be an Array OF POINTS. A draw func whose last statement is
					// a .do returns the collection it iterated, which would otherwise
					// be drawn as coordinates near the origin.
					if(points.isArray.not or: { points[0].isKindOf(Point).not }, {
						points = nil;
					});
				});

				// one call for the built-in path; a draw func has already used
				// c[\render] itself and returned something that is not an Array
				renderPoints.(points);

				Pen.rotate(event[\rotation].neg, pos.x, pos.y);
			});
		};

	});
};

//------------------------------------------------------------
// NOTE on style: core is an Event, and Dictionary:doesNotUnderstand
// AUTO-CALLS any Function stored in it, passing the Event as the first
// argument. So these are written as pseudo-methods - leading `self` arg,
// called as core.makeCanvas(...) NOT core.makeCanvas.(...).
// (Writing core.makeCanvas.(\main) calls makeCanvas(core) first, which
//  keyed a canvas by the self-referential core Event and sent the next
//  dictionary lookup into infinite recursion -> sclang segfault.)
//------------------------------------------------------------

//------------------------------------------------------------
// canvas factory
//------------------------------------------------------------
// The ground every canvas is painted : m.com.bgColor, shared by all
// personalities, black until one sets it. Applied by the cull routine, not
// here in the draw loop, so a p-file assignment lands within 0.1s.
core.ground = { |self|
	(core.com !? { core.com[\bgColor] }) ? Color.black
};

core.makeCanvas = { |self, name, frameRate = 60, action|
	var ground = core.ground;
	var view = UserView()
		.animate_(true)
		.frameRate_(frameRate)
		.background_(ground);

	view.drawFunc_({ drawCanvas.(name, view) });

	if(action.notNil, {
		view.mouseUpAction_({ view.background_(core.ground) });
		view.mouseDownAction_({
			view.background_(Color.white.alpha_(0.1));
			action.();
		});
	},{
		view.mouseDownAction_({false});
		view.mouseUpAction_({false});
	});

	core.canvases[name] = (view: view, events: List.new(32), bg: ground);
	view
};

//------------------------------------------------------------
// scenes : routing-table swaps
//------------------------------------------------------------
// push a set of in-flight events back through the current routing table.
// Shared by applyScene and moveSurface : both throw the canvases away and
// have to put whatever was mid-flight back where it now belongs.
core.routeEvents = { |self, live|
	live.do { |e|
		(core.routes[e[\src]] ? []).do { |cn|
			var c = core.canvases[cn];
			if(c.notNil, { c[\events] = c[\events].add(e) });
		};
	};
};

core.applyScene = { |self, name|
	// Events already in flight are re-routed with the scene, not stranded.
	// A held event (duration: inf) was routed once when it was fired, so
	// without this it would stay on whichever canvas it started on and the
	// other scene would draw nothing at all.
	var live = IdentitySet.new;

	core.canvases.values.do { |c| c[\events].do { |e| live.add(e) } };
	core.canvases.values.do { |c| c[\events] = List.new(32) };

	core.scene = name;
	core.routes.clear;

	switch(name,
		\overlay, {
			~devices.keysDo { |port| core.routes[port] = [\main] };
			if(core.stack.notNil, { core.stack.index = 0 });
		},
		\grid, {
			~devices.do { |d| core.routes[d.port] = [(\dev ++ d.index).asSymbol] };
			if(core.stack.notNil, { core.stack.index = 1 });
		},
		{ "unknown visual scene: %".format(name).warn }
	);

	// NB an event that named an explicit \canvas is re-routed by \src like
	// any other - nothing uses \canvas today.
	core.routeEvents(live);
};

//------------------------------------------------------------
// per-personality draw functions (hot-reloaded with the p-file)
//------------------------------------------------------------
core.vdef = { |self, port, name, func|
	if(core.vdefs[port].isNil, { core.vdefs[port] = Dictionary.new });
	core.vdefs[port][name] = func;
	name
};

core.clearVdefs = { |self, port| core.vdefs[port] = nil };

core.vdefs[\global] = vdefLib;
core.warnedShapes = IdentitySet.new;

//------------------------------------------------------------
// drop a device's in-flight events.
//
// The cull below expires events on duration alone, so anything a
// personality left behind keeps drawing until its time runs out - and
// once clearVdefs has removed the matching draw function, the leftover
// falls through to the shapeLib branch and renders as a stray circle.
// Held events (duration: inf) would never leave at all.
//------------------------------------------------------------
core.clearEvents = { |self, port|
	core.canvases.values.do { |c|
		c[\events] = c[\events].select { |e| e[\src] != port };
	};
};

//------------------------------------------------------------
// the surface : one StackLayout, page 0 = overlay, page 1 = grid.
// Lives either in the HDMI window or in the visual tab - same thing.
//------------------------------------------------------------
core.makeSurface = { |self|
	var overlay = core.makeCanvas(\main);
	var grid = View().layout_(GridLayout.new()
		.hSpacing_(spacing).vSpacing_(spacing).margins_([0,0,0,0]));
	var stack;
	var view;

	core.gridView = grid;

	view = View()
		.background_(Color.black)
		.layout_(stack = StackLayout(overlay, grid).margins_(0).spacing_(0));

	core.stack = stack;
	stack.index = if(core.scene == \grid, 1, 0);

	// the surface may be built after devices have already been detected
	// (HDMI window is created inside initGUI, i.e. after startOSCListening)
	if(~devices.notNil, { ~devices.do { |d| core.addDevice(d) } });

	core.startCull();
	view
};

//------------------------------------------------------------
// move the ONE surface into another host view.
//
// There is only ever one. core.canvases is keyed by name and makeCanvas
// OVERWRITES, so building a second surface would evict the first from the
// registry while leaving its UserViews alive and animating - drawCanvas
// resolves events by NAME, so those orphans would go on rendering the new
// canvas's events into the old window, with no error and no way to stop
// them. Hence: tear the old one down first, always.
//
// addDevice is a no-op when a canvas of that name already exists, so the
// registry has to be emptied or the rebuilt grid comes up blank.
//
// In-flight events are carried across exactly as applyScene carries them
// across a scene change - held (duration: inf) events included, which
// would otherwise be stranded on a canvas that no longer exists.
//------------------------------------------------------------
core.moveSurface = { |self, host|
	var live = IdentitySet.new;

	core.canvases.values.do { |c| c[\events].do { |e| live.add(e) } };
	core.canvases.values.do { |c|
		if(c[\view].notNil, { c[\view].animate_(false) });
	};
	core.canvases.clear;
	core.gridView = nil;
	core.stack = nil;

	if(core.surfaceHost.notNil and: { core.surfaceHost != host }, {
		core.surfaceHost.removeAll;
	});

	core.surfaceHost = host;
	host.removeAll;
	host.layout_(VLayout(core.makeSurface()).margins_(0).spacing_(0));

	core.routeEvents(live);
	host
};

//------------------------------------------------------------
// a device arrived : give it a grid canvas, re-resolve routing
//------------------------------------------------------------
core.addDevice = { |self, d|
	var name = (\dev ++ d.index).asSymbol;
	if(core.canvases[name].isNil and: { core.gridView.notNil }, {
		var canvas = core.makeCanvas(name, 60, {
			// tap a device's canvas to advance its personality (as before)
			d.personality = d.personality + 1;
			NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/loadPersonality", d.port, d.personality);
		});
		core.gridView.layout.add(canvas, 0, core.gridCount);
		core.gridCount = core.gridCount + 1;
	});
	core.applyScene(core.scene);
};

//------------------------------------------------------------
// expire events on their own clock, NOT inside drawFunc.
// (a hidden StackLayout page gets no paint events, so culling there
//  let the event lists grow without bound)
//------------------------------------------------------------
core.startCull = { |self|
	if(core.cullRout.isNil, {
		core.cullRout = Routine {
			loop {
				var now = thisThread.seconds;
				var ground = core.ground;
				// iterate a snapshot - makeCanvas may insert keys
				core.canvases.values.do { |c|
					c[\events] = c[\events].select { |e|
						(now - e[\startTime]) < (e[\duration] ? 1)
					};
					if(c[\bg] != ground and: { c[\view].notNil }, {
						c[\view].background_(ground);
						c[\bg] = ground;
					});
				};
				0.1.yield;
			}
		}.play(AppClock);
	});
};

//------------------------------------------------------------
// the event type
//------------------------------------------------------------
Event.addEventType(\customVisualEvent, {

	var src = ~viewID ? ~src;
	var targets = ~canvas;

	var event = (
		\src: src,
		// NOTE: ~bgColor is deliberately NOT carried. The ground is shared by
		// every device on a canvas, so it is set on m.com.bgColor, not per
		// event. Personalities may still pass bgColor: - it is silently
		// ignored, not an error.
		\shape: ~shape ? \circle,
		\points: ~points,
		\numPoints: ~numPoints,
		\sx: ~sx ? 0.0,
		\sy: ~sy ? 0.0,
		\ex: ~ex ? 0.0,
		\ey: ~ey ? 0.0,
		\xEnv: ~xEnv ? defaultEnv,
		\yEnv: ~yEnv ? defaultEnv,
		\startWidth: ~startWidth ? 1,
		\endWidth: ~endWidth ? 1,
		\widthEnv: ~widthEnv ? defaultEnv,
		\startSize: ~startSize ? 50,
		\endSize: ~endSize ? 50,
		\sizeEnv: ~sizeEnv ? defaultEnv,
		\startColor: ~startColor ? Color.white,
		\endColor: ~endColor ? Color.red.alpha_(0.2),
		\colorEnv: ~colorEnv ? defaultEnv,
		\rotation: ~rotation ? 0,
		\duration: ~duration ? 0.5,
		\envelope: ~envelope ? defaultEnv,
		\alphaEnv: ~alphaEnv ? Env([1, 1, 0], [0.0, 1], \sin),
		\fill: ~fill ? false,
		\closed: ~closed ? true,
		\modulation: ~modulation,
		\startTime: thisThread.seconds + (~vLatency ? core.latency)
	);

	// explicit \canvas overrides the router; otherwise resolve by source device
	if(targets.isNil, { targets = core.routes[src] ? [] });

	targets.asArray.do { |cn|
		var c = core.canvases[cn];
		if(c.notNil, { c[\events] = c[\events].add(event) });
	};

	// audio ALWAYS plays, even when nothing is listening visually.
	// (previously this sat inside the canvas check, so a bad viewID
	//  silently killed the note as well as the shape)
	~type = \note;
	currentEnvironment.play;
});

//------------------------------------------------------------
// OSC
//------------------------------------------------------------
OSCFunc({ |msg|
	{ core.applyScene(msg[1].asSymbol) }.defer;
},'/airkit/visualScene');

OSCFunc({ |msg|
	var d = ~devices.at(msg[1].asInteger);
	if(d.notNil, { { core.addDevice(d) }.defer });
},'/airkit/addDevice', NetAddr("127.0.0.1", NetAddr.langPort));

//------------------------------------------------------------
~visuals = core;
core;
