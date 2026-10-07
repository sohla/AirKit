//
// (
// Ndef(\dattorro_dg_reverb).addSpec(
// 	\drywet, [0.0,1.0],
// 	\predelay, [0.0,0.08],
// 	\bandwidth, [0.0,0.999999999],
// 	\decay, [0.0,0.999999999],
// 	\damping, [0.0,0.999999999],
// 	\input_diff_1, [0.001, 3, \exp],
// 	\input_diff_2, [0.001, 3, \exp],
// 	\decay_diff_1, [0.001, 3, \exp],
// 	\decay_diff_2, [0.001, 3, \exp],
// );
// )

(
Ndef(\dattorro_dg_reverb,

	{ arg
		drywet = 1,
		predelay = 0.0,
		input_diff_1 = 1,
		input_diff_2 = 1,
		bandwidth = 0.1,		// input bandwidth
		decay = 0.9,	// tank decay
		decay_diff_1 = 1,
		decay_diff_2 = 1,
		damping = 0.999; // tank bandwidth


		var src, input, local;
		var input_diff = [ input_diff_1, input_diff_2 ];
		var dltimes;
		var tank0, tank1, tankdelays0, tankdelays1, outdelaysL, outdelaysR;
		var n_out_0, n_out_1, n_out_2, n_out_3, n_out_4, n_out_5, n_out_6;

		// input = In.ar(inBus, 2).sum;
		src = SoundIn.ar([0,1]);
		// src = Impulse.ar(0.5).dup;

		// input = OnePole.ar(
		// 	DelayC.ar(src.mean, 0.08, predelay),
		// 	coef:(bandwidth - 1).abs
		// );

		input = Integrator.ar(
			DelayC.ar(src.mean * bandwidth, 0.08, predelay),
			coef: 1 - bandwidth
		);

		// [142,107,379,277]/29761;
		dltimes = [ 0.0047713450488895, 0.0035953092974026, 0.012734787137529, 0.0093074829474816 ];


		dltimes.do { |it i|
			input = AllpassN.ar(input, it, it, decaytime: input_diff[ i.trunc(2) / 2 ]);
		};
		///////////////////////////////////Tank///////////

		// [ 672, 4453, 1800, 3720  ]/ 27961
		tankdelays0 = [ 0.022579886428547, 0.1496253486106, 0.060481838647895, 0.12499579987232 ];

		// [908, 4217, 2656, 3163] / 27961
		tankdelays1 = [ 0.030509727495716, 0.14169550754343, 0.089244313027116, 0.10628003091294 ];

		local = LocalIn.ar(2);
		////////////////////////// 0 //////////////////

		n_out_1 = AllpassL.ar(
			input + local[1],
			0.4,
			// (tankdelays0[0] + SinOsc.ar(0.7,mul:0.00025)),
			(tankdelays0[0] + LFNoise2.ar(0.7,mul:0.00025)),
			decaytime: decay_diff_1
		);

		tank0 = DelayC.ar(
			n_out_1,
			tankdelays0[1],
			tankdelays0[1]
		);

		// n_out_2 = OnePole.ar(tank0, 1 - damping);
		n_out_2	= Integrator.ar(tank0 * ( 1 - damping ), damping) * decay;

		n_out_3 = AllpassL.ar(
			n_out_2,
			tankdelays0[2],
			tankdelays0[2],
			decaytime: decay_diff_2
		);

		tank0 = DelayC.ar(
			n_out_3,
			tankdelays0[3],
			tankdelays0[3] - ControlDur.ir
		) * decay;

		////////////////////////////// 1 ///////////////

		n_out_4 = AllpassL.ar(
			input + local[0],
			0.4,
			// (tankdelays1[0] + SinOsc.ar(0.71, mul:0.00018)),
			(tankdelays1[0] + LFNoise2.kr(0.71, mul:0.00018)),
			decaytime: decay_diff_1
		);

		tank1 = DelayC.ar(
			n_out_4,
			tankdelays1[1],
			tankdelays1[1]
		);

		n_out_5	= Integrator.ar(tank1 * ( 1 - damping ), damping) * decay;

		n_out_6 = AllpassL.ar(
			n_out_5,
			tankdelays1[2],
			tankdelays1[2],
			decaytime: decay_diff_2
		);

		tank1 = DelayC.ar(
			n_out_6,
			tankdelays1[3],
			tankdelays1[3] - ControlDur.ir

		) * decay;

		LocalOut.ar([
			tank0 * -1,
			tank1 * -1
		]);

		// [266,  2974, 1913, 1996, 1990, 187,  1066] / 29761
		outdelaysL = [ 0.0089378717113, 0.099929437854911, 0.064278754074124, 0.067067638856221, 0.066866032727395, 0.0062833910150869, 0.035818688888142 ];

		// [353, 3627, 1228, 2673, 2111, 335, 121] / 29761
		outdelaysR = [ 0.011861160579282, 0.12187090487551, 0.041262054366453, 0.089815530392124, 0.070931756325392, 0.011256342192803, 0.0040657235979974 ];


		// Out.ar(out,
		Mix([
			[
				Mix([
					DelayN.ar( n_out_4, outdelaysL[0] ),
					DelayN.ar( n_out_4, outdelaysL[1] ),
					DelayN.ar( n_out_5, outdelaysL[2] ).neg,
					DelayN.ar( n_out_6, outdelaysL[3] ),
					DelayN.ar( n_out_1, outdelaysL[4] ).neg,
					DelayN.ar( n_out_2, outdelaysL[5] ).neg,
					DelayN.ar( n_out_3, outdelaysL[6] ).neg
				])
				,
				Mix([
					DelayN.ar( n_out_1, outdelaysR[0] ),
					DelayN.ar( n_out_1, outdelaysR[1] ),
					DelayN.ar( n_out_2, outdelaysR[2] ).neg,
					DelayN.ar( n_out_3, outdelaysR[3] ),
					DelayN.ar( n_out_4, outdelaysR[4] ).neg,
					DelayN.ar( n_out_5, outdelaysR[5] ).neg,
					DelayN.ar( n_out_6, outdelaysR[6] ).neg
				])
			]  * drywet,
			src * ( 1-drywet )
		])
		// );

	}
	// .draw

).gui;


Ndef('dattorro_dg_reverb').set('decay_diff_2', 0.80686295991887, 'input_diff_1', 0.078512552440328, 'decay', 0.89735449685714, 'damping', 0.56613756557143, 'bandwidth', 0.80423280342857, 'input_diff_2', 0.1613229053115, 'drywet', 0.82169312169312, 'decay_diff_1', 0.40454960396665);
)

//

