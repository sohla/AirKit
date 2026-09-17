
(
{
	var chaos = 1, gendyFreq = 40, depth = 1;
	Gendy1.ar(
            ampdist: 10,        // Cauchy distribution for amplitude
            durdist: 1,        // Exponential distribution for durations
            adparam: chaos,    // Chaos parameter
            ddparam: depth,    // Depth of duration variations
			minfreq: [gendyFreq * 0.9999, gendyFreq * 0.9999] ,
            maxfreq: gendyFreq * 1.0001 ,
            ampscale: 0.6,
            durscale: 0.5,
			initCPs:12,
			knum:MouseX.kr(2,12)
        );
}.play
)
