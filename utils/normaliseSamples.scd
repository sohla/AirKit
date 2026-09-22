(
var srcDir = "/home/pi/Downloads/yourDNASamples/hover_original/";
var dstDir = "/home/pi/Downloads/yourDNASamples/hover/";
var logPath = "/home/pi/Downloads/yourDNASamples/hover_normalise.log";
var log = File(logPath, "w");
var say = { |str| str.postln; log.write(str ++ "\n"); };

var targetRms = -20.0.dbamp;
var ceiling   = -1.0.dbamp;
var maxGain   = 40.0.dbamp;
var knee      = -9.0.dbamp;

// soft knee : linear below the knee, tanh above it, asymptotic to 1.0
var soften = { |v|
	var a = v.abs;
	if(a <= knee, { v }, {
		(knee + ((1 - knee) * tanh((a - knee) / (1 - knee)))) * v.sign
	})
};

say.("normalise : " ++ Date.getDate.stamp);

PathName(srcDir).files.select({ |p| p.extension.toLower == "wav" }).do({ |p|
	var f = SoundFile.openRead(p.fullPath);
	var frames, ch, sr, data, peak = 0, sumsq = 0, rms, gain, out;
	var rms2 = 0, peak2 = 0, trim;

	if(f.isNil, {
		say.("SKIP unreadable : " ++ p.fileName);
	},{
		frames = f.numFrames;
		ch     = f.numChannels;
		sr     = f.sampleRate;
		data   = FloatArray.newClear(frames * ch);
		f.readData(data);
		f.close;

		data.do({ |v|
			var a = v.abs;
			if(a > peak, { peak = a });
			sumsq = sumsq + (v * v);
		});
		rms = (sumsq / data.size).sqrt;

		gain = if(rms > 0, { targetRms / rms }, { 1 });
		gain = gain.min(maxGain);

		sumsq = 0;
		data.size.do({ |i|
			var v = soften.(data[i] * gain);
			var a = v.abs;
			data[i] = v;
			if(a > peak2, { peak2 = a });
			sumsq = sumsq + (v * v);
		});

		trim = if(peak2 > ceiling, { ceiling / peak2 }, { 1 });
		if(trim < 1, {
			data.size.do({ |i| data[i] = data[i] * trim });
			peak2 = peak2 * trim;
		});
		rms2 = (sumsq / data.size).sqrt * trim;

		out = SoundFile.new
			.numChannels_(ch)
			.sampleRate_(sr)
			.headerFormat_("WAV")
			.sampleFormat_("int24");
		out.openWrite(dstDir ++ p.fileName);
		out.writeData(data);
		out.close;

		say.("%  %ch %s   rms % -> % dB   peak % -> % dB   gain % dB".format(
			p.fileName, ch, (frames / sr).round(0.01),
			rms.ampdb.round(0.1), rms2.ampdb.round(0.1),
			peak.ampdb.round(0.1), peak2.ampdb.round(0.1),
			gain.ampdb.round(0.1)));
	});
});

say.("--- done ---");
log.close;
0.exit;
)
