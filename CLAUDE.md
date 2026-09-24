# CLAUDE.md

Guidance for Claude Code when working in this repository.

This file is about **`code3.0/` — the live AirKit system**, and specifically
about adding visuals to personality files.

---

## RULE ZERO — NEVER CHANGE THE SOUND

**Never modify a SynthDef, a sensor mapping, a `Pbind`'s musical content,
model filter coefficients, or `~plot` — unless that is the literal thing you
were asked to do.**

Adding a visual means adding a visual, and nothing else. The synth, `~next`,
the mappings and the plot probes are the instrument. They are
performance-tested on hardware and they are the user's work — not scaffolding
to be tidied while passing through. Commented-out probe lines in `~plot` are
working notes; they are not dead code.

If the existing synth seems hard to draw, **that is the job**. Read it and
find what it actually does. Do *not* replace it with one that is easier to
illustrate — that inverts the design rule in §C ("represent the synth, not the
idea of the instrument") while appearing to obey it.

If you believe the sound genuinely needs to change, **stop and ask**. Never
bundle it into a commit about something else.

> This happened. Commit `5781bd3 "more visuals"` replaced `droplet.sc`'s
> `\raindrop` SynthDef with a new `\droplet`, dropped four of its six sensor
> mappings (gyro-Y, gyro-X and the accel side-channel), changed the model
> filter coefficients, rewrote the `Pbind`'s note material, and deleted every
> `~plot` probe line — all under a heading that said "visuals". Restored from
> `047f07d`.

---

> **Not to be confused with `visuals/visualServer/`.** That is a separate,
> older system: an installed SuperCollider Extension providing
> `VisualServer` / `VisualSynthDef` / `Vbind` and the event types
> `\visual \vpulse \vline \vlive \audioVisual`. It is still installed and
> announces itself at sclang boot. It has its own `CLAUDE.md`. Nothing in this
> document applies to it, and none of its classes are used by `code3.0/`.

---

## Read the atlas first

**Before designing any visual, read `visuals/graphic-scores-atlas.md`.** It is
the design source for everything in this repo — ~90 graphic-score works,
systems and notational traditions from c.1913 to now, reduced to twelve
reusable visual grammars. Do not invent a visual language from scratch; take a
grammar from the atlas and build an original field in it.

It is ~850 lines, so read it in the order it was written to be read:

| Section | Why |
|---|---|
| **§0.2** the `(field, traversal, mapping)` triple | The single most important idea. A graphic score is a rule for converting space into time. |
| **§0.3** six traversal types | Linear playhead, scrolling field, radial sweep, free ranging, superimposition, event-triggered. Decide which one the piece is. |
| **§1** the twelve grammars, G1–G12 | *"This section is the reusable core of the document."* Usually all you need. |
| **§3.1** the space→sound→motion mapping table | The default semantics for x, y, weight, hue, curvature, overlap, whitespace. |
| **§3.4** rules of thumb | Protect the silence, mark the traversal, one grammar per sketch, give marks physics. |
| **§4** coverage map | Indexed **by grammar** — the fast way to find catalogue entries once you know you want, say, G8. |
| **§2** the catalogue | Read only the specific entries you are drawing on. |

Do **not** write the lineage into the p-file. P-files are kept lean (see
**Comments** in §C) and a paragraph of attribution is exactly the kind of prose
that goes stale in them. Say it in the chat when you hand the file over.

**§0.4 is binding:** these works are under copyright and visually distinctive.
Never reproduce, trace or closely approximate a specific published score page.
Build original fields from the same primitives with the same logic. The correct
output is "a piece in the lineage of X", never "page 47 of X".

---

## Where things live

| File | Responsibility |
|---|---|
| `code3.0/visualCore.scd` | Canvases, the device→canvas router, the `\customVisualEvent` type, and `drawCanvas` (the per-frame draw loop). |
| `code3.0/vdefLib.scd` | The shared shape library, registered into `core.vdefs[\global]`: `circle arc square triangle hexagon star cross line wave spiral leaf blobby`. Every entry is a points func. |
| `code3.0/personalityController.scd` | Builds each personality's Environment, injects `~vdef` / `~model` / `~device`, runs `~init` / `~next` / `~plot`. |
| `code3.0/machine.scd` | Resolves which `machines/*.scd` this kit is, at boot. Paths, roster, device count, timing and window all come from it — nothing else hardcodes them. |
| `machines/*.scd` | One tracked file per kit. Not something a visuals task ever needs to touch. |
| `code3.0/plotterView.scd` | The per-device plotter driven by `~plot`. |
| `personalities/*.sc` | The personality files ("p-files"). |
| `visuals/graphic-scores-atlas.md` | Design source document for visual grammars. |
| `code3.0/ak_pfile_authoring.md` | How to write a p-file at all: the controller contract, the model and sensors, sound architectures, cleanup, and the non-visual traps. Read it before writing a new personality; this file stays the authority on the visual API. |
| `personalities/_TEMPLATE_ak_pfile.sc` | The skeleton to copy for a new p-file. |

Reference implementations to read before writing a new one:

| file | shows |
|---|---|
| `_TEMPLATE_ak_pfile.sc` | the lean skeleton to copy. `templateVisual.sc` is the older annotated one — read it for the explanations, do not copy its style |
| `miniMoog.sc` | a points func with a continuous parameter (superellipse) |
| `bongo1.sc` | draw func built entirely from `c[\draw]` — no `Pen` |
| `magicWand.sc`, `bells.sc` | draw funcs built from `c[\render]` |
| `metal1.sc` | a held `duration: inf` event, and the one justified `Pen` case |

---

## A. The API contract

### Emitting a visual event

Add `\type, \customVisualEvent` to a `Pbind`, or build an Event literal and
`.play` it. Both reach the same event type (`visualCore.scd:365`).

```supercollider
// from a pattern
\type, \customVisualEvent,
\shape, \myShape,

// or fired directly, e.g. from a trigger in ~next
(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port,
 shape: \myShape, duration: 1.2).play;
```

**Routing.** The event needs `\viewID`. In a pattern, set it from `~next`:

```supercollider
Pdef(m.ptn).set(\viewID, d.port);
```

Without it the visual is silently dropped **but the audio still plays**
(`visualCore.scd:404`, `:414`). Audio-with-no-picture is the signature of a
missing or wrong `viewID`.

**Visual-only events** (no sound of their own) need `amp: 0` and a short
`dur:`. The event type re-types itself to `\note` and plays at
`visualCore.scd:414`, so without `amp: 0` you get a stray `\default` synth on
every mark.

### Event keys

The authoritative list is the constructor at `visualCore.scd:365`. Carried
keys and their defaults:

| Key | Default | Notes |
|---|---|---|
| `\shape` | `\circle` | a library name, or the personality's own vdef |
| `\points` | `nil` | explicit point array, bypasses shape lookup |
| `\numPoints` | `nil` (→32) | |
| `\sx \sy \ex \ey` | `0.0` | normalised, 0 = canvas centre, ±1 = edge |
| `\xEnv \yEnv` | linear | position envelope over the event |
| `\startWidth \endWidth \widthEnv` | `1`, `1`, linear | stroke weight |
| `\startSize \endSize \sizeEnv` | `50`, `50`, linear | |
| `\startColor \endColor \colorEnv` | white, red α0.2, linear | |
| `\rotation` | `0` | radians, applied about the mark's own origin |
| `\duration` | `0.5` | seconds; `inf` = held (see below) |
| `\fill` | `false` | |
| `\closed` | `true` | |
| `\modulation` | `nil` | see below |

Two further keys are **read by the event type but not carried** into the event
dict, so a draw function never sees them: `\canvas` (overrides the router —
normally omit) and `\vLatency` (how far ahead the mark is stamped, defaults to
`core.latency`).

Three things to know about that table:

- **`\modulation` is the only free-form passthrough.** Any other custom key
  you invent is silently dropped by the constructor. It is therefore how a
  vdef receives per-note data — see the tightness rule in §C.
- **`\envelope` and `\alphaEnv` are dead.** They are carried into the event
  but never read by `drawCanvas`. Setting them does nothing.
- **`\bgColor` is deliberately ignored.** Canvas grounds are fixed black, set
  once in `makeCanvas`.

### What the core does for you

Before your draw function runs, `drawCanvas` has already applied
(`visualCore.scd:97-100`):

- **position** — from `sx/ex` + `xEnv` and `sy/ey` + `yEnv`, mapped to the canvas
- **rotation** — `Pen.rotate` about the mark's own origin, unwound after at `:206`
- **`Pen.width`** — blended from `startWidth`/`endWidth`/`widthEnv`
- **`Pen.fillColor` and `Pen.strokeColor`** — both, blended from `startColor`/`endColor`/`colorEnv`

So a vdef that wants the normal colour can simply call `Pen.stroke` and set
nothing. It also receives a context Event:

```
c: (pos: size: width: color: rotation: normTime: elapsed: now: bounds: view:
    render: draw:)
```

`normTime` runs 0→1 across the event's `duration`.

### Stay on the API — the hard rule

**Everything about where a mark is, how big it is, and how it is oriented
comes from event keys. If a draw function is computing that for itself, the
implementation is wrong. Stop and ask rather than working around it.**

The giveaway is `c[\bounds]`. It is in the context so a draw function can know
the surface it is on — not so it can derive positions. Nearly every use is a
mistake, and it is always self-justifying in the moment ("this mark is
canvas-anchored", "a circle needs the aspect"). It isn't. Two corollaries
cover the cases that tempt it:

**A canvas-relative distance comes from a size, not from bounds.** Sizes are
in **pixels**, and pixels are aspect-independent — a radius of 240 is round on
the panel, the HDMI window and a narrow grid column alike. Deriving the same
distance from `min(b.width, b.height)` or, worse, normalising it against the
canvas and adding a correction term, hardcodes one aspect ratio and distorts
everywhere else.

**An angle goes in `\rotation`.** The core rotates about the mark's *own*
origin and unwinds afterwards, so an event anchored at the centre gets polar
placement for free: `\rotation` is the angle, `\startSize` is the radius, and
the draw function only has to step out along `+x`.

`bongo1`'s ring is the worked example. Its marks sit on a circle centred on
the canvas — apparently the strongest case for reading bounds — and it needs
none:

```supercollider
// in the Pbind : the geometry, entirely in event keys
\rotation,  Pfunc({ |e| (e[\cyc] * 2pi) - 0.5pi }),   // where on the ring
\startSize, Pfunc({ |e| e[\rad] * 400 }),             // how far out, in px
                                                      // \sx \sy default 0 = centre

// in the vdef : no geometry at all
var mid  = c[\pos];            // the centre
var ring = c[\size];           // the radius
var pos  = mid + (ring @ 0);   // \rotation already swung the frame
```

Note also that `\sx`/`\sy` normalise against half-width and half-height
**separately**, so they cannot express a circle on their own. That is a reason
to reach for size-plus-rotation, never a reason to leave the API.

### The two vdef contracts

`~vdef.(\name, func)` registers a shape for this device. Which contract you get
is decided by **what the function returns** (`visualCore.scd:129`):

**Points func — preferred.** Return an `Array` of `Point`s. The core then
applies `\modulation`, `\closed` and `\fill` to it exactly as for a library
shape.

```supercollider
~vdef.(\blob, { |ev, c|
    Array.fill(ev[\numPoints] ? 32, { |i|
        var a = i / 32 * 2pi;
        c[\pos] + Polar(c[\size], a).asPoint
    })
});
```

**Draw func.** For a mark that is several sub-paths — concentric rings
(`bongo1`), disjoint strokes (`magicWand`), separating edges (`bells`). Return
anything that is not an Array.

**Do not call `Pen` yourself. Call `c[\render]`.**

```supercollider
c[\render].(points, widthScale, alphaScale, closed)
```

It is the same pipeline the points-func path uses, bound to this event, so
each sub-path gets `\modulation`, `\closed` and `\fill` correctly. The two
scales default to 1 and **multiply** the event's own width and alpha — for
marks whose parts vary, like a decaying partial or one mode of a membrane.
Absolute values still come from the event.

`closed` overrides `\closed` for that sub-path only, and is the one you will
forget: it defaults to the event's, which is **true**, so an arc gets a chord
drawn back to its start while the rings around it correctly close.

```supercollider
~vdef.(\comb, { |ev, c|
    partialAmps.do { |amp, i|
        var y = c[\pos].y - (i * 26);
        c[\render].([ (c[\pos].x - 40) @ y, (c[\pos].x + 40) @ y ], amp, amp);
    };
});
```

**`c[\draw]` — use a library shape as a sub-path.** Rather than generating
points yourself, ask any entry in `vdefLib.scd` for them:

```supercollider
c[\draw].(name, ctxOverrides, widthScale, alphaScale, closed)
```

`ctxOverrides` is an Event replacing parts of the context for that sub-path —
usually `(pos:)` and `(size:)` — so one generator can be placed repeatedly. It
routes through `c[\render]`, so sub-paths get the pipeline too. Shape
parameters still come from `\modulation`, since that is the event's only
free-form channel.

`bongo1`'s `\membrane` is the worked example — an arc plus four decaying
rings, no `Pen` anywhere:

```supercollider
c[\draw].(\arc, (pos: mid, size: ring), 1, arcAlpha, false);
modeRatios.do { |ratio, i|
    var a = modeAmps[i] * exp(t.neg / modeDecays[i]);
    c[\draw].(\circle, (pos: pos, size: head / ratio), a, a);
};
```

One consequence to expect when converting a draw func: its sub-paths now go
through `\modulation`, which they previously escaped. If the event carries a
`\modulation` Event without an `amp`, the default of 5 will start warping
marks that never used to move — set `amp: 0` to keep the old look.

This only bites sub-paths of **more than two points**; see the trap below on
the endpoint window. `bongo1`'s rings needed `amp: 0`; `magicWand`'s and
`bells`' 2-point spans were never going to move either way.

Reaching for `Pen` directly means reimplementing `\modulation`, `\closed`
and `\fill` by hand, and a partial implementation is **indistinguishable**
from a complete one until someone uses the missing part. That is exactly how
`\morphLine` silently ignored `\fill` and how `\membrane`'s rings stayed
unmodulatable. `Pen` is for output a point list cannot express — fills with
holes, text, images. Nothing in this repo currently needs it.

### Held events

`duration: inf` gives one long-lived mark instead of a stream:

- the cull never expires it, and `normTime` stays pinned at 0
- so every `start*`→`end*` blend resolves to its `start*` value — the event is
  effectively **static**, and all animation must come from the draw func
  reading live state each frame

`metal1`'s `\sheetFrame` is the reference: `~next` writes a plain file-level
var, the draw func reads it at frame rate. That var is the control bus; the
assignment is the visual equivalent of `synth.set`.

`clearEvents` (`visualCore.scd:289`, called from `personalityController.scd:288`
and `:351`) is what removes held events when the personality unloads. Without
it they would outlive their vdef and degrade into a stray fallback circle.

`applyScene` re-routes every live event when the scene changes, so a held
event follows an overlay↔grid switch rather than being stranded on the canvas
it was fired to.

---

## B. SuperCollider traps

Each of these was found the hard way. The symptom is never where the cause is.

### A bare `Env` as a `Pbind` value

`Env` implements `embedInStream`, so inside a pattern it is consumed as a
**time-varying value** — your event receives a Float, not an Env. It fails
later, inside the draw loop, as `Message 'at' not understood`.

```supercollider
\sizeEnv, Env([0,1],[1],2),          // WRONG - becomes a Float
\sizeEnv, Pfunc({ myEnv }),          // right
```

Applies to every `*Env` key. A direct `(...).play` Event literal is unaffected
— nothing streams the value there, so a literal `Env` arrives intact.

### `c.size` / `c.width` inside a vdef

`Set` declares `var <size`, so `size` is a real method: `c.size` returns the
context Event's **item count**, not the radius. `doesNotUnderstand` never
fires, so there is no error — just a wrong number.

**Always bracket-access the context:** `c[\size]`, `c[\width]`, `c[\pos]`.

### `var` after a statement

SuperCollider requires all `var` declarations at the top of a function body.
Break that and the **whole p-file fails to compile**, so `interpret` returns
nil and the Environment ends up with no `~init`, `~next` or `~plot`.

The visible symptom is a **Plotter** error — `Message '-' not understood` on an
all-nil array — because `plotterView.scd` calls a nil `~plot` 33×/sec. It
points nowhere near the actual mistake. If you see that error, suspect a
compile failure in the loaded personality first.

### Rests never reach the visual type

`Event:play` tests `currentEnvironment.isRest.not` **before** it dispatches to
the event type at all, and the `_Event_IsRest` primitive is true when **any**
value in the event is a `Rest` — not only `\dur`. So a rest never reaches
`\customVisualEvent`. Nothing is drawn, nothing is played, and the step still
occupies its place in the pattern's timing.

That is almost always what you want: the silence protects itself. It also means
the guard this section used to prescribe is dead code —

```supercollider
\modulation, Pfunc({ |e| (rest: e.isRest, ...) }),   // never reaches the vdef
```

— because the vdef is not called for a rest in the first place. `bongo1`,
`marimbabeat1` and `_TEMPLATE_ak_pfile.sc` all carry it. It is harmless, so
leave it where it is; a new file does not need it.

(Checked against the standalone's SuperCollider 3.13.0. This entry previously
said the opposite.)

### A `nil` Pbind value ends the whole pattern

`Pbind:embedInStream` returns the moment any key's stream yields nil. So an
off-step written as a bare `nil` does not produce a rest — it **terminates the
layer**, silently, on the first one:

```supercollider
\cell, Pfunc({ |e| lines[e[\seq]][e[\step]] }),                 // WRONG - one
                                                                 // event, then
                                                                 // the layer stops
\cell, Pfunc({ |e| lines[e[\seq]][e[\step]] ?? { Rest(0) } }),   // right
```

`Rest(0)` makes the whole event a rest, per the entry above, so the step goes
silent and undrawn but keeps its time. Read the value back with `.value` —
itself for a Float, `0` for a `Rest(0)` — so the arithmetic downstream is safe:

```supercollider
\note, Pfunc({ |e| e[\cell].value + (e[\shift] ? 0) + trans }),
```

The symptom is a layer that plays exactly one note and stops, with no error.
The same goes for any key holding something that might be nil — `\group`
before `Group.new` has run, `\bufnum` before the buffer exists.
`marimbabeat1` and `nicbb1` both use the `?? { Rest() }` form.

### `Pkey` and `Pdef.set`

`Pkey` **can** read a value set with `Pdef(...).set(...)` — the envir is merged
into the event before the `Pbind` runs. That is how `\viewID` works.

But a `Pbind` key of the same name **overrides** the envir. If `~next` should
own a value, comment the key out of the `Pbind` (see `miniMoog.sc`, where
`\amp, 0.3` had to go so `Pdef.set(\amp, ...)` could take effect).

### `~next` has not run when the first events fire

`Pdef(...).play` in `~init` starts immediately; `~next` follows ~30ms later. So
the first events see nil for anything `~next` supplies. A bare
`Pkey(\amp).linlin(...)` throws on `nil`. Use the defaulted form:

```supercollider
\startSize, Pfunc({ |e| (e[\amp] ? 0.2).linlin(0.15, 0.25, 40, 120) }),
```

### A draw func must not end on `.do` or `.collect`

The two contracts are told apart by the **return value**, so what a draw func
returns matters even though it is meant to be ignored. `.do` returns the
collection it iterated:

```supercollider
~vdef.(\comb, { |ev, c|
    partials.do { |ratio, i|          // WRONG - returns partials
        c[\render].([ ... ], a, a);
    };
});
```

`magicWand` did exactly this and handed back `[1, 4.08, 10.7, 18.8, 24.5,
31.2]`. Those were read as coordinates and drawn as `Point(1,1)` through
`Point(31.2, 31.2)` — six short lines in a 31-pixel box at the **top-left
corner** of the canvas, with no error.

**End a draw func with `nil`:**

```supercollider
~vdef.(\comb, { |ev, c|
    partials.do { |ratio, i| c[\render].([ ... ], a, a) };
    nil
});
```

`.collect`, and an `if` whose last branch is a `.do`, have the same problem —
`bongo1`'s `if(rest.not, { ... modeRatios.do { ... } })` returned
`modeRatios` on every step that sounded.

The core now also requires the array to contain **Points**, so a stray
collection is treated as "the draw func drew itself" rather than as geometry.
That catches it, but returning `nil` says what you mean.

### `\modulation` does nothing to a 2-point path

Every displacement is windowed by `|sin(harmonics * t * 2pi)|`, where
`t = i / (size - 1)`. That window is **0 at both ends of any path** — by
design, so a warped shape does not tear at its seam. A 2-point path is
nothing but ends, so it never moves however large `amp` is:

```
n=2   window = [0.0, 0.0]
n=5   window = [0.0, 1.0, 0.0, 1.0, 0.0]
n=16  window = [0.0, 0.407, 0.743, 0.951, 0.995, 0.866, ...]
```

So a mark built from 2-point spans — `magicWand`'s partials, `bells`' shard
edges — is unmodulatable as written. To make it respond, build the span as an
N-point polyline (`Array.fill(n, { |j| a.blend(b, j / (n - 1)) })`), or call
`c[\draw].(\line, ...)`, which honours `numPoints`. 16 or more looks smooth.

### `Require` caches — reload `main.sc` after editing the core

`Require` caches by path. Reloading a **personality** does not re-evaluate
`visualCore.scd`; only re-running `main.sc` does, because a root `Require` call
clears the `\evaluate` cache.

Symptom: a correct new vdef draws nothing at all, with no error, while
library shapes still work — the live `drawCanvas` is the old one.

### Shape lookup, and unknown names

Lookup is two tiers: the personality's own `~vdef`s, then the shared library
in `vdefLib.scd`. A personality can therefore **shadow** any library shape by
registering the same name — that is the intended way to start from a stock
shape and diverge.

An unrecognised name falls back to `\circle` and warns once. Before the
libraries were folded it fell back silently, so a typo drew a plausible shape
and never said anything.

(The old `shapeLib` `\square` divide-by-zero — nan corners for
`numPoints < 8` — was fixed during the port.)

---

## C. Design rules

These are the repo-specific rules. The design method itself is in
`visuals/graphic-scores-atlas.md` — see **Read the atlas first** above, and go
there before designing a mark.

- **Pick one grammar and commit.** Two at most. Hybrids of four look like a
  moodboard. (atlas §1, §3.4)
- **Represent the synth, not the idea of the instrument.** Mirror the
  SynthDef's own constants into file-level vars and drive the drawing from
  them, so the visual is provably the sound rather than a decoration next to
  it. `magicWand` mirrors its six `DynKlank` partial ratios and ring times;
  `bongo1` mirrors its four membrane modes. Both draw the actual decay.
- **Mark the traversal.** If a viewer cannot tell how time is moving through
  the space, it has failed as a score however pretty it is.
- **Protect the silence.** Rests and empty space are structural, not gaps to
  fill. `bongo1` leaves its five rests as gaps in the ring, and that is the
  piece.
- **Derive the palette from a substrate** (atlas §0.5), not from UI defaults.
  Canvas grounds are fixed black, so the phosphor/ANS and saturated-primary
  rows work; ink-on-vellum and graph-paper do not.

### Comments: the code tells the story

**Keep p-files lean. Almost no comments, no header, no front matter.**

The code is the explanation. If a line needs explaining, name the variable
better or pull the expression out — do not annotate it. The only comments a
p-file carries are the `//---` section separators every file in
`personalities/` uses.

Specifically, do not write:

- a `/* gestures: description: sound: pitch: rhythm: instruments: */` header
- a prose block above `~vdef` describing the mark
- the atlas lineage, or a space→sound mapping table
- a comment inside a `Pbind`, an event literal, or a `~vdef` body
- a comment restating what the next line does

If something genuinely cannot be read off the code, one short line is allowed.
One. Most of the time the honest fix is that the code should be clearer, or
the value should be sitting on the event where it can be seen.

Commented-out code is different and is protected — a disabled `Balance2.ar`
in a SynthDef, and above all the probe menu in `~plot`. `~plot` is deliberately
written as a list of every signal the file could show, all commented but one;
`silence.sc` is the reference and `code3.0/ak_pfile_authoring.md` §12 has the
block to copy. Those are working notes. Leave them, and add to them.

The design reasoning — the grammar chosen, the lineage, the mapping — still
matters; it just does not live in the file. Say it in the chat when handing
the work over.

### The tightness rule

A draw function reads `size`, `width` and `color` **from the event** and scales
them only by whatever the physics says is happening at that instant. Every
other constant is a `?` fallback on a `\modulation` key, set in the `Pbind`
next to everything else.

```supercollider
// in the vdef - no magic numbers, only event lookups
var lane = mod[\lane] ? 26;
var wid  = c[\width];
...
Pen.width = wid * a;          // a = this partial's current amplitude

// in the Pbind - every tunable in one place
\startWidth, 5,
\endWidth, 0.6,
\modulation, Pfunc({ |e| (hardness: e[\hardness] ? 0.5, lane: 26) }),
```

File-level vars are for **structure** (SynthDef mirrors) and **state**
(`step`, `sincePluck` — accumulators that cannot be recomputed). Never for
values you would want to tweak; those belong on the event.

### Never relay values through file-level vars

**A draw function runs inside the personality's Environment, so it can read
`m` — the model — directly, live, at frame rate.** Whatever it needs to know
about the performance, it computes for itself from the same source `~next`
uses.

So do **not** do this:

```supercollider
var pluckRate = 2;                                  // WRONG - a relayed value
~next = { |d| pluckRate = pch.linlin(30,300,1,30) }; // written here...
~vdef.(\band, { |ev, c| ... pluckRate ... });        // ...read there
```

Do this:

```supercollider
~vdef.(\band, { |ev, c|
    var pch = 40 + (m.accelMass * 150);      // the same expression ~next uses
    var pluckRate = pch.linlin(30, 300, 1, 30);
    ...
});
```

Three reasons, in order of how much they bite:

1. The relayed copy is **one tick stale**, and can drift out of step with the
   synth it is supposed to be mirroring.
2. The mapping now lives in **two places** and has to be kept in sync by hand.
3. The draw function's real inputs become **invisible** — you cannot tell what
   it depends on by reading it.

**The test:** could this be computed from `m` and the event? If yes, it must
be. A file-level var is only justified when the answer is genuinely no —
`sincePluck` and `lastNow` in `pluck1` are accumulators over frames, `step` in
`bongo1` counts events; none can be derived.

**Corollary for held events.** A `duration: inf` event's `\modulation` is
fixed at fire time, so live values cannot come from it. Do not hold a
reference and mutate it. Put the **range** on the event and let the model pick
the point inside it:

```supercollider
modulation: (modeMin: 1, modeMax: 10)          // the event owns the bounds
var modes = pch.linlin(40, 190, modeMin, modeMax)   // the model picks
```

`pluck1.sc` and `metal1.sc` are the worked examples — both draw functions
recompute their drive from `m` rather than being handed it.
