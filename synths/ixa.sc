(
var ixa;

ixa = { |freq, in, index|
    var phase, waveform, snd;
    phase = Phasor.ar(Impulse.ar(0), freq / SampleRate.ir, 0, 1);
    waveform = (phase % 0.5 * 2pi).sin * (2 * (phase % 0.5 < 0.25) - 1);
    waveform = waveform + (2 * (phase % 0.5 >= 0.25));
    waveform = waveform + (2 * (phase >= 0.5));
    snd = (waveform + (in * index)).fold2;
    snd;
};

{
    var freq = 50;
    ixa.(freq, SinOsc.ar(freq), MouseX.kr(0, 100)) * 0.5 ! 2;
}.play(fadeTime: 0);
)