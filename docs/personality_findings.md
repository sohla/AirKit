# SuperCollider Personality Implementation Patterns

## Overview
Personalities in this system are modular, motion-responsive musical behaviors that respond to device sensor data (accelerometer, gyroscope, rotation rate). Each personality is loaded as an Environment and executed in a routine that processes sensor data and controls musical output.

---

## Core Architecture

### Controller Framework (personalityController.scd)
- **Loading System**: Personalities are loaded from `.sc` files and interpreted into Environments
- **Device Model**: Each device has a model with filtered sensor data:
  - `accelMass` / `accelMassFiltered` - Acceleration magnitude with attack/decay smoothing
  - `rrateMass` / `rrateMassFiltered` - Rotation rate magnitude with smoothing
  - Thresholds for detecting hits (`isHit`) and movement (`isMoving`)
- **Key Functions**:
  - `~init` - Initialize synths/patterns (called on load)
  - `~deinit` - Cleanup resources (called on unload)
  - `~next` - Main loop processing sensor data → musical output (30Hz by default)
  - `~onEvent` - Receive events from other personalities (community sharing)
  - `~processDeviceData` - Convert raw sensor data to filtered model values
  - `~play` / `~stop` - Control pattern playback

---

## Implementation Approaches

### 1. **Pdef-based Patterns** (Most Common)

Uses `Pdef` (pattern definitions) with `Pbind` for event-based sequencing.

**Key Pattern Classes Used:**
- **`Pseq`** - Sequential values: `Pseq([0,3,7], inf)` cycles through notes
- **`Pwhite`** / **`Pxrand`** / **`Prand`** - Randomization for variation
- **`Pkey`** - Reference other pattern keys: `Pkey(\octave).linlin(3,6,1,0.2)`
- **`Pfunc`** - Execute functions (used for `~onEvent` callbacks)
- **`Rest`** - Silence: `Pseq([1, Rest(1), 2], inf)` creates rhythmic gaps

**Examples:**

```supercollider
// template.sc - Basic Pdef approach
Pdef(m.ptn,
    Pbind(
        \instrument, \template,
        \scale, Scale.major,
        \octave, 4,
        \note, Pseq([0,4,7,4,2], inf),
        \legato, 1,
        \func, Pfunc({|e| ~onEvent.(e)}),
    )
);
Pdef(m.ptn).play(quant:0.1);
```

**Dynamic Parameter Control:**
```supercollider
// In ~next function, modulate pattern parameters in real-time
~next = {|d|
    var dur = m.accelMassFiltered.linexp(0,2.5,0.5,0.05);
    var amp = m.accelMassFiltered.lincurve(0,2.5,0.3,1, -1);

    Pdef(m.ptn).set(\dur, dur);  // Change duration
    Pdef(m.ptn).set(\amp, amp);  // Change amplitude
    Pdef(m.ptn).set(\note, selectedNote);  // Change pitch

    // Conditional play/pause based on movement
    if(m.accelMassFiltered > 0.1, {
        if(Pdef(m.ptn).isPlaying.not, {
            Pdef(m.ptn).resume(quant:0.1);
        });
    }, {
        if(Pdef(m.ptn).isPlaying, {
            Pdef(m.ptn).pause();
        });
    });
};
```

**Typical Use Cases:**
- Melodic sequences (arialMelody.sc, scale1.sc)
- Rhythmic patterns (drumkit.sc, percTexture.sc)
- Generative music with probabilistic elements

---

### 2. **Ndef-based Patterns** (Node Proxy)

Uses `Ndef` for more dynamic, node-based pattern control with crossfading.

```supercollider
// ndefTest1.sc
~init = ~init <> {
    Ndef(m.ptn, Pbindef(\ap,
        \instrument, \simpleSynth,
        \dur, Pseq([0.2, 0.1, 0.1, 0.2, Rest(0.1), 0.1] * 4, inf),
        \note, Pseq([0,3,8,7,12,5,2,0,3], inf),
        \octave, Pseq([4,6,5,7].stutter(1), inf),
        \root, Pseq([0,3,1,-2].stutter(36), inf),
    ).quant_(0.1));
    Ndef(m.ptn).fadeTime = 0;
};

~deinit = ~deinit <> {
    Ndef(m.ptn).clear(4);  // Fade out over 4 beats
};
```

**Advantages over Pdef:**
- Smooth crossfading between states (`fadeTime`)
- More flexible routing and processing
- Better for live coding scenarios

---

### 3. **Direct Synth Control** (No Patterns)

Create persistent synths and modulate parameters directly.

```supercollider
// bee.sc - Continuous synth approach
SynthDef(\beeSynth1, { |out=0, rr=0.1, amp=0, gate=1, release=2, af=264, bf=398|
    var ampa = Lag.ar(K2A.ar(rr), 0.7) * 2.0 * amp;
    var lrr = Lag.ar(K2A.kr(rr), 0.7);
    var pitch = LinLin.ar(lrr, 0.0, 1.0, af, bf);
    var sig = HPF.ar(LFSaw.ar(StandardL.ar(11, ramp) * 14 + pitch, 1, 0.9), ffrq)!2;
    sig = sig * EnvGen.kr(Env.adsr(0.1, 0.1, 7, release), gate, doneAction:2) * ampa.lag(3);
    Out.ar(out, sig);
}).add;

~init = ~init <> {
    synth = Synth(\beeSynth1, [\af, 280.rrand(390), \bf, 350.rrand(440)]);
};

~next = {|d|
    var amp = m.accelMassFiltered.linlin(0,1,0,0.4);
    var rate = m.accelMassFiltered.lincurve(0.0,2.5,0.3,1.2,4);
    synth.set(\amp, amp * 0.01);
    synth.set(\rr, rate);
};

~deinit = ~deinit <> {
    synth.set(\gate, 0);  // Release envelope
};
```

**When to Use:**
- Continuous sounds (drones, textures, wind sounds)
- Real-time parameter control without retriggering
- Lower CPU overhead for sustained tones
- Examples: bee.sc, funMove1.sc, ROLL_pitchTimbre.sc

---

### 4. **Hybrid: Synth + Pattern** (Layered Approach)

Combines continuous synths with event-based patterns.

```supercollider
// percTexture.sc - Multiple layers
~init = ~init <> {
    // Load buffer and create continuous granular synth
    Buffer.read(s, ~sampleFolder.entries[6].fullPath, action:{|buf|
        sa = Synth(\pullstretchMono, [\buffer, buf, \pch, 0.midiratio, \amp, 0.4]);
        sb = Synth(\pullstretchMono, [\buffer, buf, \pch, 12.midiratio, \amp, 0.3]);
    });

    // Layer with a Pdef pattern
    Pdef(m.ptn,
        Pbind(
            \instrument, \synth2211,
            \octave, Pxrand([3,4,5], inf),
            \note, Pseq([0], inf),
            \root, Pseq([0,3,-2,-4].stutter(24)-3, inf),
            \rel, Pwhite(0.002, 0.9, inf),
        )
    );
};

~next = {|d|
    var dur = m.accelMassFiltered.linexp(0,1,0.8,0.2);
    var amp = m.accelMassFiltered.linlin(0,1,0,0.4);

    // Control pattern
    Pdef(m.ptn).set(\dur, dur);

    // Control continuous synths
    sa.set(\speed, m.rrateMass.linlin(3,10,0.01,2));
    sa.set(\splay, m.rrateMass.linlin(3,10,0.01,1));
    sa.set(\amp, amp);
};
```

**Use Cases:**
- Rich textural backgrounds + percussive elements
- Combining sample playback with synthesis

---

## Sensor Mapping Techniques

### Movement Detection Strategies

```supercollider
// 1. Threshold-based play/pause (most common)
if(m.accelMassFiltered > 0.1, {
    if(Pdef(m.ptn).isPlaying.not, {
        Pdef(m.ptn).resume(quant:0.125);
    });
}, {
    if(Pdef(m.ptn).isPlaying, {
        Pdef(m.ptn).pause();
    });
});

// 2. Smooth amplitude control (no on/off)
var amp = m.accelMassFiltered.lincurve(0,2.5,0,1,-1);
synth.set(\amp, amp);

// 3. Rate limiting (prevent too-rapid retriggering)
if(TempoClock.beats > (lastTime + 0.35), {
    lastTime = TempoClock.beats;
    // Trigger new note
});
```

### Gyroscope Mapping (Spatial Orientation)

```supercollider
// UD_pitch.sc - Map gyro X (up/down tilt) to pitch selection
var cs = [0,4,7,11];  // Chord tones
var notes = cs ++ (cs + 12);  // Two octaves
var index = (d.sensors.gyroEvent.x/pi).linlin(-1, 0.5, notes.size, 0.0);
Pdef(m.ptn).set(\note, notes[index.floor]);

// LR_pitchTimbre.sc - Map gyro Y (left/right tilt) to timbre
var attack = (d.sensors.gyroEvent.y/pi).lincurve(-0.5, 0.5, 0.002, 0.34, -2);
var mix = (d.sensors.gyroEvent.y/pi).lincurve(-0.5, 0.5, 0.0, 1.0, -1);
Pdef(m.ptn).set(\attack, attack);
Pdef(m.ptn).set(\mix, mix);

// ROLL_pitchTimbre.sc - Map rotation rate to note index
var index = (d.sensors.gyroEvent.y/pi).linlin(-0.5, 0.5, 0.0, notes.size);
synth.set(\note, notes[index.floor]);
```

### Acceleration Mapping (Movement Intensity)

```supercollider
// Linear mapping
var amp = m.accelMassFiltered.linlin(0, 2.5, 0, 1);

// Exponential mapping (for frequency/duration)
var dur = m.accelMassFiltered.linexp(0, 2.5, 0.5, 0.05);

// Curved mapping (for more nuanced control)
var amp = m.accelMassFiltered.lincurve(0, 2.5, 0.3, 1, -6);

// Floor for quantized values (octaves, scale degrees)
var oct = m.accelMassFiltered.lincurve(0, 3, 2, 6, -1).floor;
```

### Rotation Rate Mapping

```supercollider
// drumkit.sc - Select buffer based on rotation
bi = (d.sensors.gyroEvent.y.abs / pi) * (~buffers.size-1);
bi = bi.asInteger;

// magicWand.sc - Control duration with rotation
var dur = 0.23 - m.rrateMassFiltered.lincurve(0, 1.0, 0.001, 0.14, -3);
```

---

## SynthDef Patterns & Techniques

### Common Synthesis Techniques

**1. Additive Synthesis (Multiple Oscillators)**
```supercollider
// Detuned oscillators for richness
sig = Mix.ar([
    Saw.ar(freq * (1 - detune)),
    Saw.ar(freq),
    Saw.ar(freq * (1 + detune))
]) + SinOsc.ar(freq) * oscMix;
```

**2. Physical Modeling**
```supercollider
// Klank (modal resonance) - glockenspiel.sc
sig = DynKlank.ar(`[
    [1, 4.08, 10.7, 18.8, 24.5, 31.2] * freq,  // Partials
    [1, 0.8, 0.6, 0.4, 0.2, 0.1] * 0.5,        // Amplitudes
    [1, 0.8, 0.6, 0.4, 0.2, 0.1]               // Decay times
], exciter);

// Pluck (Karplus-Strong) - arialMelody.sc
string = Pluck.ar(
    in: exciter,
    trig: Impulse.kr(10),
    maxdelaytime: 1/freq,
    delaytime: 1/freq,
    decaytime: stringDecay,
    coef: stringDamp
);
```

**3. Granular Synthesis**
```supercollider
// arialMelody.sc
exciter = GrainFM.ar(
    numChannels: 2,
    trigger: Dust.kr(grainDensity),
    dur: grainDur,
    carfreq: freq,
    modfreq: freq * LFNoise1.kr(0.5).range(0.5, 2),
    index: modIndex,
    pan: WhiteNoise.kr(1)
);
```

**4. Sample Playback + Processing**
```supercollider
// drumkit.sc - Buffer playback with filtering
var sig = PlayBuf.ar(1, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum));
sig = RHPF.ar(sig, cutoff, rq);  // High-pass filter
sig = Compander.ar(sig, sig, thresh: -15.dbamp, slopeAbove: 0.5);
```

**5. Distortion & Waveshaping**
```supercollider
// funMove1.sc
filter = [filter.distort, filter.tanh];
Out.ar(out, filter.softclip);

// Multiple stages of saturation
sig = (sig * dist).tanh.distort;
```

### Filter Envelope Patterns

```supercollider
// Separate filter envelope
filterEnv = EnvGen.kr(
    Env.adsr(filterAttack, filterDecay, filterSustain, filterRelease),
    gate
);
sig = RLPF.ar(sig, cutoff * (1 + (filterEnv * filterEnvAmount)), resonance);
```

### Stereo Widening Techniques

```supercollider
// 1. Delay-based widening (arialMelody.sc)
stereoSig = stereoSig + LocalIn.ar(2);
stereoSig = DelayC.ar(stereoSig, 0.01, SinOsc.kr(0.1, [0, pi]).range(0, 0.01) * stereoWidth);
LocalOut.ar(stereoSig * 0.5);

// 2. Splay (multiple voices)
sig = Splay.arFill(8, { |i|
    var detune = i * detuneAmount;
    SinOsc.ar(freq * (1 + detune))
}, spread);

// 3. Simple delay offset
Out.ar(out, DelayN.ar(sig, 0.01, [0.007, 0.009]));
```

---

## Community/Event Sharing

Personalities can share musical context (root note, duration) via `~onEvent`:

```supercollider
// Sender (pattern-based)
Pbind(
    \func, Pfunc({|e| ~onEvent.(e)}),  // Calls ~onEvent with event
)

// Receiver
~onEvent = {|e|
    m.com.root = e.root;  // Update shared root
    m.com.dur = e.dur;

    // React to key changes
    if(e.root != m.com.root, {
        synth.set(\freq, (note + e.root).midicps);
    });
};
```

This allows multiple personalities to stay harmonically synchronized.

---

## Advanced Techniques

### Custom Smoothing (Attack/Decay)

```supercollider
~smooth = {|input, history, attack=0.5, decay=0.05|
    var coeff = attack;
    if(history > input, {coeff = decay});  // Faster decay on falling values
    (coeff * input + ((1 - coeff) * history))
};
```

**Typical Values:**
- Fast attack + slow decay: `attack=0.9, decay=0.5` (sustains movement)
- Fast attack + fast decay: `attack=0.7, decay=0.2` (responsive)
- Slow attack + slow decay: `attack=0.99, decay=0.9` (smooth)

### Buffer Management

```supercollider
~init = ~init <> {
    var folder = PathName("~/Downloads/samples/");
    ~buffers = folder.entries.collect({ |path, i|
        Buffer.read(s, path.fullPath, action:{|buf|
            postf("buffer alloc [%] \n", buf);
        });
    });
};

~deinit = ~deinit <> {
    ~buffers.do({|buf| buf.free });
};
```

### Plot Function (Debugging)

```supercollider
~plot = { |d,p|
    // Return array of values to visualize
    [m.accelMass * 0.1, m.accelMassFiltered * 0.1];

    // Gyro visualization (normalized)
    // [(d.sensors.gyroEvent.x/pi).linlin(-0.8, 0.8, 0.9, -0.9)]  // up/down
    // [(d.sensors.gyroEvent.y/pi).linlin(-0.4, 0.4, 0.9, -0.9)]  // left/right
    // [(d.sensors.gyroEvent.z/(pi/2)).linlin(-0.3, 1.0, -0.9, 0.9)]  // wrist rotate
};
```

---

## Best Practices

### Resource Management
1. **Always pair allocation with cleanup**:
   - `~init` creates synths/buffers → `~deinit` frees them
   - Use `Pdef.remove` or `Ndef.clear(fadeTime)`
2. **Use `doneAction:2`** in SynthDefs to auto-free finished synths
3. **Set gates to 0** instead of `.free` for graceful release envelopes

### Performance Optimization
1. **Limit pattern complexity**: Avoid expensive operations in `Pfunc`
2. **Use `.lag()`** for smooth parameter transitions instead of filtering
3. **Buffer pre-allocation**: Load all samples in `~init`, not during playback

### Movement Responsiveness
1. **Adjust filter coefficients** per personality:
   - Drums/percussive: Fast attack + fast decay
   - Sustained textures: Slow attack + slow decay
2. **Use thresholds** to prevent jitter: `if(amp > 0.07, { play }, { pause })`
3. **Rate limiting**: Prevent retriggering with `TempoClock.beats` checks

### Sensor Mapping Strategy
1. **Start simple**: Linear mappings (`linlin`) first
2. **Add curves** for musical expressivity: `lincurve` with negative values for logarithmic
3. **Use exponential** for frequency/duration: `linexp`
4. **Quantize** where appropriate: `.floor` for discrete values (notes, octaves)

---

## Summary Table

| Approach | Examples | Best For | Key Pattern |
|----------|----------|----------|-------------|
| **Pdef + Pbind** | template.sc, scale1.sc, drumkit.sc | Melodic/rhythmic sequences | `Pdef(m.ptn, Pbind(...))` |
| **Ndef + Pbindef** | ndefTest1.sc | Live coding, crossfading | `Ndef(m.ptn, Pbindef(...))` |
| **Direct Synth** | bee.sc, funMove1.sc | Continuous textures, drones | `synth = Synth(\def, [...])` |
| **Hybrid** | percTexture.sc, arialMelody.sc | Layered complexity | Synth(s) + Pdef |

### Common Pxxx Classes
- **`Pseq`** - Sequential (most common for melodies/rhythms)
- **`Pwhite`** - Random range (for variation)
- **`Pxrand`** - Random choice without immediate repetition
- **`Prand`** - Random choice (allows repetition)
- **`Pkey`** - Reference other event keys
- **`Pfunc`** - Execute custom functions
- **`Rest`** - Silence in sequences
- **`.stutter(n)`** - Repeat each element n times

### Sensor Data Flow
```
Raw Sensor → processDeviceData → Smoothing → ~next → Musical Mapping → Output
(30Hz)       (accelMass, etc.)   (attack/   (linlin/   (Synth/Pdef)
                                   decay)     lincurve)
```
