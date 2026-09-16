
(
var loadAndPlot; 

loadAndPlot = {
	FileDialog({ |paths|

		var file = File(paths[0], "r");
		var strToFloats = file.readAllString.split($, ).asFloat;
		var data = strToFloats.reshape(strToFloats.size,2);
		var values, runningAvg, movingAvg;
		var window = 20;   // sliding-window size in frames
		var settle = 2;    // drop the first N frames while the system settles
		var colors = [Color.gray, Color.blue, Color.green(0.5)];
		var labels;
		var colX = [60, 230, 340, 450];      // x position of each column
		var colW = [160, 100, 100, 100];     // width of each column
		var headers = ["series", "min", "max", "mean"];
		var prevOnClose;

		data = data.reshape(data.size.half.asInteger,2);
		data = data.lace(data.size * 2);
		data = data.reshape(2,data.size.half.asInteger);

		// derived series from the y-values (data[1]), dropping the settle frames
		values     = data[1].drop(settle);
		runningAvg = values.integrate / (1..values.size);       // cumulative mean
		movingAvg  = values.size.collect({ |i|                   // sliding-window mean
			var lo = max(0, i - window + 1);
			values[lo..i].mean;
		});

		a = [values, runningAvg, movingAvg].plot(bounds: Rect(100, 100, 1400, 800));
		a.superpose = true;

		a.plotMode = \linear;
		a.minval_(0);
		a.maxval_(0.06);
		a.setProperties(
			\labelX, "Frames",
			\labelY, "ms",
		);
		a.plotColor = colors;
		a.refresh;

		// stats grid overlaid on the plot window
		labels = ["raw", "running avg", "moving avg (" ++ window ++ ")"];

		headers.do({ |h, c|
			StaticText(a.parent, Rect(colX[c], 8, colW[c], 18))
				.string_(h)
				.stringColor_(Color.black)
				.background_(Color.white.alpha_(0.6))
				.font_(Font.default.boldVariant);
		});

		[values, runningAvg, movingAvg].do({ |series, i|
			var y = 28 + (i * 20);
			[ labels[i],
			  series.minItem.round(0.0001),
			  series.maxItem.round(0.0001),
			  series.mean.round(0.0001) ].do({ |val, c|
				StaticText(a.parent, Rect(colX[c], y, colW[c], 18))
					.string_(val.asString)
					.stringColor_(colors[i])
					.background_(Color.white.alpha_(0.6))
					.font_(Font.default.boldVariant);
			});
		});

		prevOnClose = a.parent.onClose;
		a.parent.onClose = { |win|
			prevOnClose.value(win);
			loadAndPlot.value;
		};

	}, {
		postln("Cancelled. Try again.");
	});
};

loadAndPlot.value;
)
