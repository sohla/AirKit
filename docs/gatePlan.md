# gatePlan.md

Notes on adding a reusable **amplitude gate** helper to the personality
vocabulary, and on whether it can be a class extension given how AirKit units
are updated in the field.

Status: investigation only, nothing implemented. Written 2026-09-06, on
`AirConcert`. No code was changed.

---

## The starting point

The idiom in question:

```supercollider
var amp = m.accelMassFiltered.lincurve(0, 2.5, -40, -5, -2);
if(amp < 39.neg, { amp = 120.neg});
```

The question was whether this belongs on `Number` as `linCurveGate` (and
variants), or somewhere else.

---

## What the SC docs actually give you

- **`lincurve`'s only boundary behaviour is `clip`** — `\minmax` (default),
  `\min`, `\max`, `nil`. It *clamps*, it never zeroes. At `this <= inMin` it
  returns `outMin` exactly, via an early return before any curve math
  (`SimpleNumber.sc:452-463`).
- **ControlSpec-with-curve is the same function.** `[min,max,curve].asSpec`
  builds a `CurveWarp`, whose `map` (`Spec.sc:318-320`) is literally
  `lincurve`'s tail — same `grow = exp(curve)`, same `a`, `b`, same
  `b - (a * pow(grow, x))`. The spec adds `unmap`, `step` rounding,
  `clipLo/clipHi`, grid/plot/GUI integration and a reusable name. It adds
  nothing curve-wise.
- **Neither has a threshold**, and there is no gated `Warp` in the class
  library.
- A threshold **cannot** live inside a `Warp`: it makes the mapping
  non-injective (everything below the threshold collapses onto one output), so
  `unmap` can't invert it. The ControlSpec docs state the round-trip
  expectation explicitly, and `unmap` is what sliders and EZ GUIs call. A
  `GatedCurveWarp` would work for map-only use and be silently wrong under any
  GUI.

Conclusion: the gate belongs at the call site, not in the mapping classes.

---

## Two populations in the repo, not one

### A. dB gates — threshold coupled to `outMin`

| file | `outMin` | gate | delta | off |
|---|---|---|---|---|
| `personalities/dulcimer1.sc:191` | -40 | -39 | +1 | -120 |
| `personalities/multiBeatSynth1.sc:166` | -40 | -39 | +1 | -90 |
| `personalities/multiBeatSynth1.sc:165` (drone) | -32 | -31 | +1 | -90 |
| `personalities/multiBeat5.sc:196` | -41 | -40 | +1 | -90 |
| `personalities/multiBeat4.sc:153` | -10 | -9 | +1 | -90 |
| `personalities/piano1.sc:182` | -34 | -29 | +5 | -120 |
| `personalities/nicTwoNote.sc:214` | -60 | -58 | +2 | -90 |

Five of seven sit at exactly `outMin + 1`. **The threshold is a restatement of
the mapping's own floor, not an independent musical choice.**

What it is really doing: `lincurve` returns `outMin` exactly for any input
`<= inMin`, and -40 dB is 0.01 amp — quiet, but the pattern keeps sounding
forever with the stick on the table. The gate converts that clamp into real
silence.

With a concave curve it fires in a very narrow band. For `dulcimer1`
(`0, 2.5, -40, -5, -2`) the output crosses -39 dB at input ≈ **0.031** of a 2.5
range — 1.25%. So it is not "quiet means off", it is "the stick is at rest",
written in the output domain because that was convenient.

**The hazard:** written as an absolute number, retuning `outMin` from -40 to
-30 leaves a -39 gate that never fires again. The instrument silently stops
going silent, nothing in the file looks wrong, and you find out on stage.

### B. Linear-amp gates — threshold genuinely independent

| file | source mapping | `outMin` | gate |
|---|---|---|---|
| `personalities/brenton1.sc:71` | `lincurve(0,2.5,0,1,-5)` | 0 | 0.07 |
| `personalities/droplet.sc:196` | `lincurve(-1,1,0.0,1,-2)` | 0 | 0.21 |
| `personalities/suz4.sc:99` | `lincurve(0,1.5,0.002,1,2)` | 0.002 | 0.02 |
| `personalities/trainMelody2.sc:184` | `lincurve(0,2,0.001,0.1,-2)` | 0.001 | 0.02 |
| `personalities/mel2.sc:53` | **`linlin`**(0,2,0.00001,1) | 1e-5 | 0.01 |
| `personalities/mel3.sc:61` | **`linlin`**(0,2,0.00001,1) | 1e-5 | 0.001 |

Plus `heatherLaugh1.sc:91`, `heatherTeddies.sc:68`, `aless2.sc:59`,
`brenton4.sc:51`, `velocity3.sc:56`, `nic1.sc:70`, `scale2.sc:96`.

Half already have `outMin = 0` — the curve's floor *is* silence, and the gate
exists to widen the dead zone above it. Several aren't `lincurve` at all. There
is no `outMin` coupling here and no drift hazard.

### Related, already-solved cases (leave alone)

- **`~curveAbove`** — `cotf_whisperer1.sc:129`, `SOPRANOVOICE.sc:183`,
  `WindVoice.sc:220`. An *input*-domain gate: below `thresh` → 0, else
  `in.lincurve(thresh, inMax, outMin, outMax, curve)`. It remaps `inMin` to
  `thresh` so the curve keeps its full shape above the gate, and `outMin` is
  deliberately non-zero (0.3) — it opens *to an audible level*.
- **`PERCUSSION.sc:520-525` and `:577-582`** — same shape inline, `restFloor`
  0.2, `outMin` 0.05. The comment at `:517-519` explains why non-zero: the
  handler gates on `base > 0.001`, so a curve starting at 0 leaves a dead band
  just above the threshold.
- **`violin3.sc:75`** — `if(amp < 0.1, {amp = 0}, {amp = 1})` is a binary
  switch, not a gate.
- **`melChair2.sc:95`** — gates on `amp.dbamp > 0.009` to `pause`/`resume` the
  Pdef. Transport control, and arguably more correct: it stops the pattern
  rather than playing silent notes.

---

## What is worth building

Two shapes, serving the two populations:

```
linCurveGate(inMin, inMax, outMin, outMax, curve, gate = 1, off = -inf)
    v = lincurve(...); if (v < outMin + gate) { off } { v }
```

`gate` is **headroom above `outMin`**, not an absolute value — which is the
whole point. `dulcimer1` becomes a call with zero magic numbers, and the gate
tracks `outMin` when the curve is retuned. `piano1` passes `gate: 5` and its
deliberate outlier stays visible as an outlier. `off = -inf` because
`-inf.dbamp` is exactly 0.0, whereas -90/-120 dB are 3e-5/1e-6 and still pass a
downstream `> 0.001` test.

```
gateBelow(thresh, off = 0)
    if (this < thresh) { off } { this }
```

For population B and for anything that didn't come from a single `lincurve`.

**Rule of thumb separating them:** if you'd have to change the gate number when
you retune `outMin`, use `linCurveGate`.

### Honest assessment

Population A is the one that benefits. Population B's sites are already written
as a separate `if` on the next line, so `amp = gateBelow(amp, 0.07)` is barely
different from what's there. **If only one thing gets built, build
`linCurveGate` and leave the linear-amp `if`s alone.**

### Not covered by either: hysteresis

Both gates run at tick rate (~30 Hz) against a single threshold on a filtered
but still noisy signal, so a hand hovering at the rest floor chatters. The repo
already applies hysteresis wherever it makes a *discrete* decision —
`PERCUSSION.sc:4` (tier), `BIRDY.sc:433` and `Birdsong.sc:218` (tilt→octave) —
but not on the amp gates. Separate open/close thresholds is the one thing a
helper could add that `lincurve` genuinely cannot do.

A `SimpleNumber` method has no state, so this **cannot** live in a class
extension — it needs a stateful helper in the p-file. And retrofitting it into
PERCUSSION or whisperer changes how they respond, so it must be opt-in per
personality. Treat as a separate question.

### Variants

"…and all its variants" (`linExpGate`, `linLinGate`, `curveLinGate`) is
probably unnecessary. Headroom-above-`outMin` is a dB idea; of the ~40
`lincurve` calls in `personalities/`, only the dB ones gate. The index, cutoff,
dur and octave mappings never do. Write one; add more when a site asks.

---

## Method syntax requires a class file. There is no way around it.

Tested in sclang 3.14.0: `addUniqueMethod` — SC's nearest thing to runtime
monkeypatching — keys by **value** for Floats.

```
0.5.addUniqueMethod(\gateBelow, {...});
0.5.gateBelow(0.2)  ->  0.5
0.4.gateBelow(0.2)  ->  DoesNotUnderstandError
0.7.gateBelow(0.2)  ->  DoesNotUnderstandError
```

So `.method` syntax on numbers means a compiled class file, which means a
class-library path, which is where the deployment problem starts.

The alternative is an **environment function** in `code3.0/` —
`~linCurveGate.(m.accelMassFiltered, 0, 2.5, -40, -5, -2)`. Ordinary
interpreter code, no compile, no path, ships today. The receiver is the first
argument either way, so for population A it reads almost identically to the
method form. `~curveAbove` is already exactly this pattern, so it is the
existing house idiom, not a new one.

---

## Deployment: the constraint that decides it

Requirement: **no in-the-room work.** Updates must reach units through the
webapp path only.

### Two channels

| | webapp `/upload` | SSH |
|---|---|---|
| Who | customer, on AirKit WiFi, browser only | you, laptop + key, on site |
| Can write | `APP_DIR/<top-level dirs in the zip>` and nothing else | anything |
| Needs a visit | **no** | **yes** |

The zip path cannot escape `APP_DIR`: `engine.py:134-136` rejects any member
with `..` or a leading `/`, then `extractall(APP_DIR)`. And the updater cannot
update itself in the field — the UI only calls `/upload`, `/rollback`,
`/restart`, `/status`, `/progress`, `/ui-version`; the manifest-driven
`run_update` path in `engine.py` isn't wired to anything (cf. AirKitWebApp
commit `9239f45`).

**Therefore any change to the updater itself, or to anything outside
`APP_DIR`, is one SSH visit per device.** Both earlier ideas — patching
`scripts/deploy.sh`, or adding a symlink step to `engine.py` — are in-the-room
work and were discarded on that basis.

### The pipeline, for reference

```
git tag v0.0.6 -> GH Action -> zip of release_dirs + VERSION -> GitHub Release
    -> customer uploads at http://192.168.2.4:8080
    -> updater: SC shutdown -> backup -> replace -> relaunch
```

- `.github/workflows/release.yml` reads the dir list from
  `release.config.json` (`machines`, `code3.0`, `personalities`, `lists`) and
  zips it. Adding `"classes"` to that list is a one-line change.
- `src/updater/engine.py:137-143` derives its targets from the zip's own
  top-level dirs — there is **no** hardcoded dir list, so a new `classes/`
  dir is backed up, replaced and rolled back for free. No updater change
  needed for the zip half.
- That same property is why `machines/` could be added to the release without
  touching the updater: per-kit config is versioned, ships, and rolls back
  with the code it was tested against. See `configPlan.md`.
- `src/server/app.py:328-341` already shuts SC down and relaunches it via
  `start_airkit.sh`, which is exactly the restart a class extension needs.

### Where a class file has to land on these units

The launcher:

```bash
cd ~/supercolliderStandaloneRPI64
exec ./sclang -a -l sclang.yaml ~/Develop/SuperCollider/Projects/AirKit/code3.0/main.sc ...
```

- **`-a` is standalone mode** — it forces `excludeDefaultPaths` true
  (`SC_LanguageConfig.cpp:203`), so sclang ignores the default
  `SCClassLibrary` *and* both Extensions directories. **A symlink into
  `~/.local/share/SuperCollider/Extensions` would do nothing on these units.**
  Everything comes from `sclang.yaml`.
- That yaml already includes `./share/user/Extensions`, relative to the `cd`
  target. So the destination is:

  ```
  ~/supercolliderStandaloneRPI64/share/user/Extensions/AirKit/
  ```

- **No yaml edit is needed.** SC code in `code3.0/` can copy `classes/*.sc`
  into that dir at boot — ships in the zip, runs as `pi`, no SSH.
- Editing the yaml would also work if ever needed:
  `LanguageConfig.addIncludePath(p); LanguageConfig.store;` writes back to the
  file `-l` designated (`PyrPrimitive.cpp:3271-3275`). Paths are stored
  verbatim, so `./share/...` entries stay relative and the standalone remains
  portable. `store` rewrites rather than appends: it keeps `includePaths`,
  `excludePaths`, `postInlineWarnings`, `excludeDefaultPaths` and drops
  anything else in the file.

### The one-restart delay, and why it can't be shortened

sclang reads the yaml and compiles the class library **before** any of our code
runs. So a class file copied into place at boot N only compiles at boot N+1.

`thisProcess.recompile` cannot shortcut this. Tested: it does **not** re-run
the command-line script. In a run of `sclang r.scd` where the script calls
recompile, the body executed exactly once; sclang came back up and never
re-ran the file. On a unit that means `main.sc` never executes — a live,
silent, unreachable device. **Never call recompile in the boot path.**

Rollout that respects this, with no visit:

- **Release N** — adds `classes/` plus the idempotent copy step. Customer
  updates; SC restarts; file in place; class not yet live.
- **Any later restart** — the Restart button, or a power cycle — compiles it.
- **Release N+1 onward** — p-files may use the methods.

Shipping the class and its first use in one zip means the first boot after
install runs p-files calling methods that don't exist yet. The alternative is
adding "press Restart once when it finishes" to the customer instructions
(generated from `how_to_update.template.md` via `make doc`), which collapses it
to one session but depends on them doing it.

---

## Rollback: what it covers and what it does not

The webapp has a Rollback button (`/rollback` -> `engine.py:207-241`). It
restores the backed-up dirs into `APP_DIR`, restores `VERSION`, and restarts
SC.

- **One level only.** Each install overwrites `/home/pi/app_backup`, so you can
  go back exactly one update. Rolling back twice re-restores the same version.
- **It only touches `APP_DIR`.**

**A bad class file is not recoverable from the webapp.** This is the decisive
risk:

1. A syntax error in the class means sclang dies at class-library compile.
2. `main.sc` never runs, so the copy step that would replace the file never
   runs.
3. The web UI stays up (separate Python service) and Rollback reverts
   `APP_DIR` — but the bad class is in
   `share/user/Extensions/AirKit/`, outside `APP_DIR`, so it survives.
4. The unit still won't boot. That is an in-the-room fix.

Also: on the *first* release introducing `classes/`, that dir doesn't exist in
`APP_DIR` yet, so it isn't backed up — rollback won't remove it either.

Scorecard: everything in `code3.0` / `personalities` / `lists` is one-click
recoverable. The class file is the single component that isn't, and its failure
mode is total.

---

## Where this leaves the decision

**Option 1 — environment function in `code3.0/`.** Zero infrastructure, ships
through the existing zip today, fully covered by rollback, no brick risk, no
restart delay. Costs `~linCurveGate.(v, ...)` instead of `v.linCurveGate(...)`,
which for population A is a near-identical read.

**Option 2 — class extension via the boot copy.** Gives method syntax. Costs a
one-restart rollout delay, and carries a failure mode that the webapp cannot
recover from. Mitigations: parse-check the class locally before tagging (see
`reference_sclang_parse_check`), keep it to the two methods and nothing else,
and never ship a class change in the same release as anything else.

**Option 3 — class extension with the symlink done by hand.** Rejected: `-a`
means the Extensions dirs aren't scanned anyway, and it's an SSH visit per
device.

Open questions for later:

1. Function or class — i.e. is `.method` syntax worth a non-recoverable
   failure mode on units you can't reach?
2. `linCurveGate` only, or `gateBelow` as well?
3. Hysteresis: separate question, needs state, must be opt-in per personality
   because it changes how existing instruments respond.
4. If a shared helper lands, where — injected by
   `code3.0/personalityController.scd` alongside `~vdef` / `~model` /
   `~device` (additive, existing p-files unaffected), or left per-file as
   `~curveAbove` currently is?

---

## Incidental findings (not acted on)

- **`personalities/multiBeat5.sc:196`** — `e.lincurve(0, 1.4, -41, 0 -1)` is
  missing a comma. Verified in sclang: `0 -1` parses as `-1`, so the line runs
  as `outMax = -1, curve = -4` (the default), not `outMax = 0, curve = -1`. It
  compiles, it sounds like something, and it has been tuned by ear in that
  state — so changing it would change the sound. Left alone.
- **`personalities/piano1.sc:180-182`** — the comment says the floor exists so
  "a test bench has to keep sounding when the stick is put down", but the gate
  two lines later silences the bottom 5 dB of that floor. The comment probably
  predates the gate. Worth a look when piano1 is next benched.
