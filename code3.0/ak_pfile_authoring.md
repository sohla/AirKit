# AirKit p-file Authoring

The reference for writing a personality file in **this** repo — the
`code3.0/` live AirKit system on the `Airsticks-RPI` branch.

This is a rewrite of the `personality_authoring.md` / `_TEMPLATE_authored.sc`
pair from the `AirConcert` branch, with the COTF concert machinery removed.
**None of the following exists here:** `~outBus`, `~beatClock`,
`~scoreVoicePool`, `~scoreBeatsPerBar`, `~roomState`, `~onRoomState`, the
`\idle / \tuning / \piece / \curtain / \silent` state ticks, the `ctx` score
snapshot, `~onResync`, and the beat hooks `~onTick / ~onHalf / ~onBeat /
~onBar / ~onPhrase / ~onSection / ~onChord / ~onKey / ~onScale`. If you are
reading an old p-file that mentions any of them, it came from another branch
and will silently do nothing here. There is no tempo, no conductor and no
score. There is one device, one Environment, and a 100 Hz tick.

**What this doc does not cover:**

| Topic | Where |
|---|---|
| The visual event API — keys, vdef contracts, `c[\render]`, held events | `CLAUDE.md` at the repo root. It is authoritative; nothing here restates it. |
| Visual *design* — what to draw and why | `visuals/graphic-scores-atlas.md`. Read before designing a mark. |
| The shared shape library | `code3.0/vdefLib.scd` — `circle arc square triangle hexagon star cross line wave spiral leaf blobby` |

---

## 1. What a p-file is

A file of plain SuperCollider source in `personalities/`. On load it is read
and `interpret`ed **into a fresh Environment**, one per device, one per load
(`personalityController.scd:214`). All it does is assign a handful of `~`
names into that Environment. That is the entire interface — there is no class,
no superclass, no registration call.

Two consequences worth internalising straight away:

- **Every load is a new Environment.** File-level `var`s are re-created. There
  is no state carried across a reload except what you put on the server, on
  `m.com`, or in a `Pdef` (which is keyed by `m.ptn`, itself regenerated per
  load — so in practice, nothing).
- **Top-level statements run at interpret time**, which is *before* `~init`
  and before the server has been asked for anything.

---

## 2. The lifecycle, in order

`personalityController.scd`, `loadPersonality`:

```
1.  old env's ~deinit.()                              :316
2.  ~visuals.clearVdefs(d.port)                       :330
    ~visuals.clearEvents(d.port)                      :331
3.  Environment.make {                                :84
       install ~model ~device ~vdef ~secs
               ~processDeviceData ~play ~stop
               ~init ~deinit                          :88 - :250
       interpret(<your file>)                         :257   <-- top-level runs HERE
       install ~smooth ~slope                         :260 - :267
    }
4.  Routine { s.sync; ~init.(d); s.sync }.play        :338
5.  d.procRout starts on AppClock                     :344
```

and then, forever, once every `~secs` (default `0.01`, so ~100 Hz, best-effort
on AppClock — `personalityController.scd:147`, `:251`):

```
    if(File.mtime(~filePath) changed) -> re-fire /airkit/loadPersonality   :283
    if(d.enabled) {
        ~processDeviceData.(d)     // raw sensors -> m.*Filtered            :290
        ~next.(d)                                                          :292
    }
```

Separately, `plotterView.scd` runs its own Routine at `0.03` (~33 Hz) calling
`~plot.(d)`.

### Saving the file IS the reload

Step `:283` polls `File.mtime` on every tick. Write the file to disk and the
personality reloads itself within ~10 ms — `~deinit`, `clearEvents`, fresh
Environment, `~init`. You never reload by hand. This is the single most
important fact about working here: **`~deinit` is not a tidiness exercise, it
is on the hot path of your own edit loop.** A `~deinit` that leaks a Synth
leaks one per keystroke-save.

---

## 3. The slots you fill

| Name | Signature | When | How to assign |
|---|---|---|---|
| `~init` | `{ \|d\| }` | once, on a Routine, between two `s.sync` | **compose**: `~init = ~init <> { ... }` |
| `~next` | `{ \|d\| }` | ~100 Hz | plain assign: `~next = { \|d\| ... }` |
| `~deinit` | `{ }` | before the next load | **compose**: `~deinit = ~deinit <> { ... }` |
| `~plot` | `{ \|d, p\| }` → Array of Floats | ~33 Hz | plain assign |
| `~plotMin` / `~plotMax` | number | read **once**, at view build | plain assign |
| `~secs` | number | read each tick | optional override; default `0.01` |
| `~vdef` | `.(\name, func)` | **call**, don't assign | inside `~init` |
| `~onEvent` | `{ \|e\| }` | whatever you call it from | convention only — see §5 |

`~init` and `~deinit` have controller-supplied defaults that post a line
(`:244`, `:250`); that is why they are composed rather than overwritten.
`~next` and `~plot` have **no** default — a file that omits `~next` simply
ticks and does nothing.

`~plot` is *effectively required*: `plotterView.scd:10` calls it 33×/sec and
pushes the result into a Plotter. A nil `~plot` yields an array of nils and
the Plotter throws `Message '-' not understood` — pointing nowhere near the
cause. **That error almost always means the p-file failed to compile**, so
`interpret` returned nil and nothing at all got assigned. Look for a `var`
after a statement first (§10).

### `~init = ~init <> { ... }` — what it actually does

`<>` is function composition (`AbstractFunction.sc:214`):

```supercollider
f <> g   ==   { |...args| f.value(g.value(*args)) }
```

So `~init = ~init <> { |d| myBody }` builds `default(myBody(d))`:

- **your block runs first**, the controller's default post line second. The
  `init : <name> [<ptn>]` line appears *after* your own output, not before.
- your block's return value is passed as the default's argument; the default
  takes none, and SC ignores surplus arguments, so it is harmless.
- composing twice in one file runs the blocks **last-written-first**.

Same for `~deinit`.

---

## 4. What is in scope

Injected into the Environment before your file is interpreted:

| Name | What |
|---|---|
| `~model` | the per-device model Event. Always aliased `var m = ~model;` on line 1. |
| `~device` | the device `d`. Same object `~init` / `~next` / `~plot` are handed. |
| `~vdef` | `.(\name, func)` — registers a shape for **this device only** |
| `~secs` | tick period for the proc Routine |
| `~processDeviceData` | raw sensors → `m.*Filtered`. Called for you. |
| `~play` / `~stop` | `Pdef(m.ptn).play(0.125)` / `.stop` — wired to the device UI |
| `~filePath` / `~fileTime` | used by the save-detect. Don't touch. |
| `~smooth` / `~slope` | one-pole smoother / difference — see the trap below |

Installed *after* `interpret` (`:260`, `:267`): **`~smooth` and `~slope` are
nil at file level.** Calling either from a top-level statement fails; calling
them from inside `~init` / `~next` is fine, because those run later.

### What is NOT in scope

The Environment is built by `Environment.make` with no parent and no proto, so
**`topEnvironment` is invisible from a p-file**. `~visuals`, `~devices` and
anything else living up there resolve to `nil` — silently. (This is exactly
why `personalityController.scd:34` captures `~visuals` into a lexical `var`
before building the Environment.)

Interpreter globals are unaffected: `s`, `w`, `z` and the class library work
normally. It is only `~names` that are scoped away.

---

## 5. The model, `m`

`personalityController.scd:45`. Every field is writable; the filter constants
are meant to be set at the top of your file.

| Field | Computed | Typical range |
|---|---|---|
| `m.accelMass` | `d.sensors.accelEvent.sumabs * 0.33` | 0 .. ~3 |
| `m.accelMassFiltered` | `~smooth` of the above | 0 .. ~2.5 |
| `m.accelMassFilteredAttack` / `Decay` | you set these | 0 .. 1, default 0.9 / 0.7 |
| `m.rrateMass` | `d.sensors.rrateEvent.sumabs` | 0 .. ~1.5 |
| `m.rrateMassFiltered` | `~smooth` of the above | 0 .. ~1.5 |
| `m.rrateMassFilteredAttack` / `Decay` | you set these | default 0.9 / 0.7 |
| `m.gyroXFiltered` | `~smooth` of `gyroEvent.x / pi` | -1 .. 1 |
| `m.gyroYFiltered` | `~smooth` of `gyroEvent.y / pi.half` | -1 .. 1 |
| `m.gyroZFiltered` | `~smooth` of `gyroEvent.z / pi` | -1 .. 1 |
| `m.gyroFilteredAttack` / `Decay` | you set these | default 0.9 / 0.7 |
| `m.name` | the p-file's name | |
| `m.ptn` | **16-char random symbol, unique per device per load** | |
| `m.com` | the shared bus — see below | |

`~smooth` is asymmetric: `attack` is the coefficient when the signal is
*rising*, `decay` when falling. Higher = slower. The standard opening block:

```supercollider
m.accelMassFilteredAttack = 0.99;   // near-instant rise
m.accelMassFilteredDecay  = 0.5;    // slow fall = a gesture "envelope"
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay  = 0.3;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;
```

### `m.ptn` — the pattern key

A fresh random 16-character string per load. Two things follow:

- `Pdef(m.ptn)` is unique per device *and* per reload, so two devices running
  the same p-file never collide, and a reload never inherits the old Pdef.
- Anything else you register globally must be namespaced with it too. The
  idiom for a custom event type is `(\customEvent_ ++ m.ptn).asSymbol` —
  `Event.addEventType` writes into a **class-level** dictionary, so two
  personalities registering plain `\customEvent` would clobber each other and
  device 1 would start playing device 2's samples.

### `m.com` — the only cross-personality channel

`personalityController.scd:8`. One Event, created once, **shared by every
device's model**. Fields: `root`, `dur`, `accelMass`, `rrateMass`.

That is the whole inter-device vocabulary. One p-file publishes, others read:

```supercollider
// publisher: hang it off the Pbind's \func
\func, Pfunc({ |e| ~onEvent.(e) }),
...
~onEvent = { |e| m.com.root = e.root; m.com.dur = e.dur; };

// reader, in ~next
Pdef(m.ptn).set(\root, m.com.root ? 0);
```

`multiBeat1.sc` publishes; `multiBeat3.sc` and `multiBeat5.sc` read. Always
`? 0` on read — you cannot know whether a publisher is loaded.

`~onEvent` is a naming convention, not a hook. Nothing calls it but you.

---

## 6. The device, `d`

`oscController.scd:55` (device) and `:35` (sensors).

| Field | Meaning |
|---|---|
| `d.port` | the device's OSC port. **This is the `viewID` for visual events.** |
| `d.index` | 1-based device number; picks the list file |
| `d.name` | current personality name |
| `d.enabled` | false = `~next` is skipped and the plot flatlines |
| `d.color` | the device's UI colour |
| `d.sensors` | the sensor Event, below |

| Sensor | Units | Range |
|---|---|---|
| `d.sensors.accelEvent.{x,y,z}` | m/s² × 0.1 | ~-1 .. 1 per axis |
| `d.sensors.gyroEvent.{x,y,z}` | **radians**, Euler from the fused quaternion | x,z: -pi..pi; y: -pi/2..pi/2 |
| `d.sensors.rrateEvent.{x,y,z}` | radians per frame (angle difference) | small, ~-0.3 .. 0.3 |
| `d.sensors.quatEvent.{w,x,y,z}` | orientation quaternion | -1 .. 1 |
| `d.sensors.quatCalibrated` | quat relative to the captured reference | only when `isCalibrated` |
| `d.sensors.velocity.{x,y,z}` | integrated accel, leaky (×0.3/frame) | drifts; treat as a gesture, not a position |
| `d.sensors.digiInEvent` | `Array[4]` of 0/1 — the buttons | |

The normalisations everyone uses, so gyro comes out in -1..1:

```supercollider
(d.sensors.gyroEvent.x / pi)                    // roll
(d.sensors.gyroEvent.y / pi.half)               // up/down tilt
(d.sensors.gyroEvent.z / pi)                    // twist / left-right
(d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5)    // fold when the axis wraps
```

Buttons, from `button1.sc`:

```supercollider
if(d.sensors.digiInEvent[0] == 1, { ... });
```

---

## 7. Sound architectures

Four shapes cover essentially every file in `personalities/`. Pick one; a
file may combine A with B or C, but not all three.

### A — long-lived Synth

One `Synth` created in `~init`, `.set` from `~next`, released in `~deinit`.
No note events, no pattern. Use for drones, wind, textures.

```supercollider
~init = ~init <> {
    group = Group.new;
    synth = Synth(\myDrone, [\amp, 0, \gate, 1], group);
};
~next = { |d|
    synth.set(\amp, m.accelMassFiltered.lincurve(0, 1.0, -60, -12, -1).dbamp);
};
```
Reference: `metal1.sc`, `wind1.sc`, `drone1.sc`.

**Release, don't free.** A hard `freeAll` on a gated voice is a click. Set
`\gate, 0`, `fork`, wait out the release, *then* free the group.

### B — Pdef + per-event synth voice

A `Pbind` in `~init` firing a percussive SynthDef; `~next` steers it with
`Pdef(m.ptn).set(...)`. The most common shape in the repo.

Reference: `multiBeat1.sc`, `pluck1.sc`, `bongo1.sc`.

### C — Pdef + per-event sampler

As B, but a buffer library and a **custom event type** that resolves
note → bufnum + rate before delegating to `\note` (or to
`\customVisualEvent`, which re-types to `\note` itself).

Reference: `cotf_marimba1.sc` (nearest-sample lookup), `multiBeat5.sc`
(the same lookup on the multiBeat grid), `multiBeat4.sc` (unpitched kit).

### D — the dynamic trigger: detector Synth + per-hit voice

An audio-input detector Synth that makes no sound and ends at
`SendReply`, plus an `OSCdef` that spawns one voice per hit. No pattern —
the *player* is the clock. Use for anything struck: a mic on a drum, a
contact mic, a pad.

```supercollider
~init = ~init <> {
    group = Group.new;
    trig  = Synth(\inputTrigger, [...], group);

    OSCdef(hitKey, { |msg|
        if (msg[1] == trig.nodeID) {
            Synth(\myVoice, [\vel, msg[3], ...], group);
        };
    }, '/akHit', s.addr);
};
```

Reference: `temp_input5.sc` (struck resonator), `temp_input6.sc` (the
same trigger driving `\miniMoog`). The detector itself, and why it is
built the way it is, is `synths/drum-mic-instrument.scd` §7.4.

Four things this shape needs that A–C do not:

- **Key the `OSCdef` on `m.ptn`** and free it in `~deinit`. Reloads fire
  on every save; a fixed key leaves the old responder spawning into a
  freed Group.
- **Filter on the detector's `nodeID`** (`msg[1]`), or two devices
  running the same personality trigger each other.
- **Spawn with a bare `Synth()`, never `s.bind`.** `s.latency` is 200 ms.
  `s.bind` is right for freeing and catastrophic for spawning.
- **Release, don't `freeAll`.** `~deinit` fires on every save, so a hard
  free chops every ringing tail. Set `\gate, 0` on the Group, wait out
  the release, *then* free it — see §11.

### The one-shot

No pattern at all — fire an Event straight from `~next`, throttled. Use when
the gesture *is* the rhythm.

```supercollider
if(TempoClock.beats > (lastTime + 0.25), {
    (instrument: \myVoice, freq: 440, amp: 0.2, group: group).play;
    lastTime = TempoClock.beats;
});
```

`lastTime` is a file-level `var` — an accumulator across frames, which is one
of the two things file-level vars are legitimately for (§9).

---

## 8. Mapping gesture to parameter

### Use `lincurve`, and put the amp in dB

```supercollider
var amp = m.accelMassFiltered.lincurve(0, 1.4, -50, -5, -1);
Pdef(m.ptn).set(\amp, amp.dbamp);
```

`lincurve(inMin, inMax, outMin, outMax, curve)` clips at both ends, so it is
safe on a sensor that overshoots. Negative curve = fast rise then flatten
(right for amplitude and density); positive = slow start (right for filter
cutoff, which wants to open late).

There is no shared amp-curve palette in this branch. `-50 .. -5` dB over an
accel range of `0 .. 1.4` is the working default; quieter voices sit around
`-70 .. -20`.

### `Pdef.set` vs. a `Pbind` key

The envir set by `Pdef(...).set(...)` is merged into each event *before* the
`Pbind` runs, so `Pkey(\thing)` reads it — that is how `\viewID` reaches the
visual event. **But a `Pbind` key of the same name overrides the envir.**

So the rule is: *anything `~next` owns must not appear as a `Pbind` key.*
If a value needs to come from a gesture, comment its key out of the `Pbind`.

### Deriving one value from another inside the Pbind

`Pbind` evaluates its keys in written order, so a later `Pfunc` can read an
earlier key off the event:

```supercollider
\step, stepPat,
\amp,  Pkey(\energy) * Pfunc({ |e| if(e[\step] == 0, { 1.0 }, { 0.6 }) }),
```

### Throttling

`TempoClock.beats > (lastTime + gap)`, gap 0.2–1.0. Used in ~40 files. `~next`
runs at 100 Hz; anything that fires a synth needs a gate of some kind, or a
single shake produces a hundred voices.

---

## 9. Where values live

Three places, and the choice is not stylistic:

**On the event / `Pbind`** — anything you might want to tweak. Every tunable
in one column, next to everything else it interacts with.

**File-level `var`** — only two justified cases:
- *structure*: constants the SynthDef also uses (partial ratios, ring times,
  a sample library, a subdivision ladder). Mirroring them means the drawing
  and the sound provably agree.
- *state*: accumulators over frames that cannot be recomputed — `lastTime`,
  a step counter, `group`, `samplesLib`.

**Recomputed from `m`** — anything a `~vdef` needs to know about the
performance. A draw function runs inside the personality's Environment, at
frame rate, so it reads `m` directly. Never relay a value from `~next` into a
file-level var for a draw func to read: the copy is a tick stale, the mapping
now lives in two places, and the draw function's real inputs become invisible.
(The full argument, with worked examples, is in `CLAUDE.md` §C.)

### Keep code close to its use — do not hoist a mapping

The tempting move, when `~next` and a `~vdef` both need the same number, is to
name the mapping once at the top of the file and call it from both:

```supercollider
var ampFloor = 0.025;                                                  // NO
var droneAmp = { m.accelMassFiltered.lincurve(0, 1.0, -32, -11, -1)
                  .dbamp.max(ampFloor) };
var droneOctave = 2;
```

It looks like the *opposite* of relaying — the closure reads `m` live, so
nothing is a tick stale. It is still wrong, and for a different reason: the
mapping is now invisible at both points of use. Reading `~next` no longer
tells you what the drone's level actually does, and reading the `~vdef` no
longer tells you what it is drawing. You have to go and look somewhere else,
and the somewhere else is a name you invented.

**Write the expression out, at each place it is needed.** Three copies of a
`lincurve` are cheaper to read than one indirection, and if the copies ever
need to differ — and they usually do — they already can.

```supercollider
// in ~next : local var, next to the .set it feeds
var droneAmp = m.accelMassFiltered.lincurve(0, 1.0, -32, -11, -1).dbamp.max(0.025);
drone.set(\amp, droneAmp, \ffreq, droneFfreq);

// in the vdef : recomputed off the same source, with the floor off the event
var ampMin = mod[\ampFloor] ? 0.025;
var amp = m.accelMassFiltered.lincurve(0, 1.0, -32, -11, -1).dbamp.max(ampMin);
```

Same rule for helper functions: if only `~init` calls it, declare it at the
top of `~init`, not at file level.

`multiBeatSynth1.sc` is the worked example — it was written the hoisted way
and refactored to this. One file-level var survived the refactor,
`droneNote`, and the reason is the test:

> **Could this be computed from `m` and the event? If yes, it must be.**

`droneNote` cannot. It is the pitch the *pattern* last played, and nothing in
`m` knows where inside a subdivision group the figure currently is. That makes
it state, the same category as `lastTime` — and state is declared at file
level with a comment saying why it had to be.

---

## 10. Traps

The SuperCollider traps that bite hardest here, plus the AirKit-specific ones.
Several are also in `CLAUDE.md`; they are repeated because the symptom always
appears somewhere other than the cause.

### `var` after a statement kills the whole file

SC requires all `var` declarations at the top of a function body. Break it and
the file fails to compile, `interpret` returns nil, and the Environment gets
**no** `~init`, `~next` or `~plot`. The visible symptom is the Plotter error
`Message '-' not understood`, 33 times a second, pointing at nothing.

If you see that error, suspect a compile failure before anything else.

### `~smooth` / `~slope` are nil at file level

Installed after `interpret` (`:260`). Use them from inside `~init` / `~next`.

### `topEnvironment` is invisible

`~visuals`, `~devices`, any `~name` from outside → nil, silently. §4.

### `~plotMin` / `~plotMax` are read once

`plotterView.scd:11-12` evaluates them when the *view* is built, not per
frame. `~plot` itself re-resolves `d.env` each call so it follows reloads —
but changing the plot range needs the device view rebuilt, not just a save.

### A bare `Env` as a `Pbind` value

`Env` implements `embedInStream`, so a pattern consumes it as a time-varying
value and your event receives a Float. Wrap it: `Pfunc({ myEnv })`. A direct
`(...).play` Event literal is unaffected.

### `~next` has not run when the first events fire

`Pdef(...).play` in `~init` starts immediately; the first `~next` is ~10 ms
later. So seed the envir at the end of `~init` for every key `~next` supplies,
and read with a default in the `Pbind`:

```supercollider
\startSize, Pfunc({ |e| (e[\amp] ? 0.2).linlin(0.15, 0.25, 40, 120) }),
```

A bare `Pkey(\amp).linlin(...)` throws on nil.

### `Event.addEventType` is class-level

Namespace with `m.ptn` (§5) and remove it in `~deinit`.

### `c.size` inside a vdef

`Set` declares `var <size`, so `c.size` returns the context Event's item
count, not the radius, with no error. **Always** `c[\size]`, `c[\width]`,
`c[\pos]`.

### A draw func must not end on `.do` or `.collect`

The two vdef contracts are told apart by the return value. End a draw func
with `nil`.

### `Require` caches by path

Reloading a personality does not re-evaluate `visualCore.scd` or
`vdefLib.scd`. After editing either, re-run `main.sc`. Symptom: a correct new
vdef draws nothing, with no error, while library shapes still work.

### Missing `\viewID` drops the picture, not the sound

`visualCore.scd:521` resolves the route to an empty list and nothing is
drawn; `:531` re-types to `\note` and plays the sound anyway.
Audio-with-no-visual is the signature. Always
`Pdef(m.ptn).set(\viewID, d.port);` in `~next`.

---

## 11. Cleanup

Every personality with a server resource needs all of this. Reloads fire on
every save, and `~deinit` can fire twice on the same Environment, so the
guards are load-bearing rather than defensive.

```supercollider
~deinit = ~deinit <> {
    Pdef(m.ptn).remove;
    Event.eventTypes.removeAt(eventTypeName);   // if you registered one

    fork {
        if (group.notNil) {
            s.bind { group.freeAll };   // latency-safe
            s.sync;
            group.free;
            group = nil;
        };
        if (buffers.notNil) {
            buffers.do({ |b| b.free; s.sync });
            buffers = nil;
        };
    };
};
```

- **`Group.new` in `~init`, `\group, group` in the `Pbind`.** Without it your
  synths land in the default group with every other device's and there is no
  handle to free them.
- **`fork`**, so `s.sync` actually waits. `s.sync` outside a Routine is a no-op.
- **Order matters**: kill synths, sync, *then* free buffers. Freeing a buffer
  a `PlayBuf` is still reading is a server-side crash.
- **`notNil` guards** everywhere, and null the var after.
- Held visual events are cleared for you (`clearEvents`, `:331`) — but only
  because the controller does it. Don't rely on your own cleanup for those.

---

## 12. Comments — keep p-files lean

**Almost none. No header, no front matter.** The code is the explanation; if a
line needs explaining, name the variable better or pull the expression out.

The only comments a p-file carries are the `//---` section separators every
file in `personalities/` uses. Do not write:

- a `/* gestures: description: sound: pitch: rhythm: instruments: */` header
- a prose block above a `~vdef`, an atlas lineage, or a mapping table
- a comment inside a `Pbind`, an event literal, or a `~vdef` body
- a comment restating what the next line does

If something genuinely cannot be read off the code, **one short line**. One.
Most of the time the honest fix is that the code should be clearer, or the
value should be sitting on the event where it can be seen.

**Commented-out code is protected.** Probe lines in `~plot`, a disabled
`Balance2.ar` in a SynthDef — those are working notes, not dead code. Leave
them exactly where they are.

### The `~plot` block is the one exception

`~plot` is written as a **menu**: every signal the file could usefully show,
one per line, all commented out except one. `silence.sc` is the reference.
Copy the block whole into every new p-file:

```supercollider
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, magenta, cyan]

	// RAW values
	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// Gyro
	// [(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];//left right

	// MODEL values
	// Acceleration
	// [m.accelMass, m.accelMassFiltered].lincurve(0.0,5.0,0.0,1.0,0);
	// Rotation Rate
	// [m.rrateMass, m.rrateMassFiltered].lincurve(0.0,1.0,0.0,1.0,0);

	// COMPUTED values : this file's own ~next, recomputed
	// [m.accelMassFiltered.lincurve(0, 1.5, 0, divs.size - 1, 1).round / (divs.size - 1)];//divIdx
	// [m.accelMassFiltered.lincurve(0, 1.0, -60, -12, -1).dbamp];//amp

	[m.accelMass, m.accelMassFiltered];
};
```

Four rules for it:

- **The RAW and MODEL sections are fixed** — the same lines in every file, so
  you can switch between devices without re-deriving anything.
- **The COMPUTED section is per file**: one line for every value `~next`
  computes, `//`-labelled with the name it has in `~next`. Tuning a mapping
  means uncommenting its line, not writing a probe from scratch mid-session.
- **The probes recompute; they do not read `~next`'s locals** (they can't —
  different function). Same rule as a `~vdef`: write the expression out. When
  a curve changes in `~next`, change it here too — a probe that has drifted
  is worse than no probe.
- **Scale each probe into -1..1**, since `~plotMin` / `~plotMax` are read once
  at view build and are not worth changing per signal. `.dbamp` for levels,
  `/ max` for frequencies and indices.

**Exactly one line is left live, and it goes last.** Only the final expression
in a function is returned, so an earlier uncommented line is evaluated and
thrown away — harmless, but it means the plotter is not showing what you think.

Older files carry the heavy annotated style — `templateVisual.sc`, the train
quartet, most of `cotf_*`. Read them for the explanations; do not copy the
style, and do not go and strip them either unless asked.

The reasoning behind a file still matters — the grammar chosen, why a mapping
is shaped the way it is. It just does not live in the file. Say it in the chat
when handing the work over, or put it here if it is a rule rather than a
one-off.

---

## 13. Lists

`lists/list_*.sc` is a file containing one parenthesised Array of file-name
strings, `interpret`ed at load (`personalityController.scd:24`). Which list a
device gets is `defaultLists[d.index - 1]`.

`defaultLists` comes from **this kit's machine file** — `machines/<name>.scd`,
the `list` key — not from `personalityController.scd`. Set it to a String to
give every device the same roster, or to an Array for one roster per device:

```supercollider
list: "list_glenroy.sc",
list: ["list_NZ_Mel.sc", "list_NZ_Mel.sc", "list_NZ_Suz.sc"],
```

The kit picks its own machine file at boot; see `configPlan.md`. The roster in
force is posted on the `[machine]` line at startup, so if the sounds are not
what you expected, read that line first.

```supercollider
(
	[
	"silence",
	"multiBeat1",
	"multiBeat5",
	"silence",
	]
)
```

Two things to know:

- Selection is `index.mod(list.size - 1)` (`:324`), so the **last entry is
  unreachable**. Lists conventionally open and close with `"silence"`, which
  is why this rarely bites.
- Templates are deliberately *not* in any list — a template turning up on a
  device mid-performance is exactly the failure you don't want.

---

## 14. Working loop

1. Copy `personalities/_TEMPLATE_ak_pfile.sc` to
   `personalities/<name>.sc`. Delete the slots you don't need.
2. Add it to the list the device is on.
3. Save. It loads. Save again, it reloads — every save.
4. Watch the post window for `init :` / `deinit :` lines and buffer
   alloc/dealloc pairs. **Unbalanced alloc/dealloc means `~deinit` is
   leaking**, and you are about to leak once per save.
5. Tune constants live in the post window —
   `Pdef(<the ptn string>).set(\dur, 0.5)` — then write the number into the
   file.
6. Watch the plotter for the signal you are actually mapping. Put the raw and
   the filtered value side by side; most mapping bugs are a range problem you
   can see in one glance.

### Checklist before calling it done

- [ ] `~deinit` frees the Group, the buffers, and any held Synth, guarded and
      forked
- [ ] `Event.eventTypes.removeAt` if a custom type was registered
- [ ] every key `~next` sets is seeded at the end of `~init`
- [ ] no key `~next` owns is also a `Pbind` key
- [ ] `Pdef(m.ptn).set(\viewID, d.port)` if there is a visual
- [ ] `~plot` returns a real array; `~plotMin` / `~plotMax` set
- [ ] every `var` at the top of its body
- [ ] no header, no front matter, effectively no comments (§12)
- [ ] save it twice in a row and confirm the post window balances

---

## 15. Growing this document

If translating an idea into a p-file made you hesitate, the missing piece
belongs here. Add it in the section it fits, with the file it first appeared
in. Numbers do not belong here — personalities are the source of truth for
constants. What belongs here is *contract* (what the controller guarantees),
*mechanism* (why a thing behaves as it does) and *trap* (the symptom, and how
far it is from the cause).
