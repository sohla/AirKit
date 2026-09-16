# product_report.md

Possible shapes for AirKit as a product for schools and specialist
music/visual/movement settings — and the moves that get there without
slowing the R&D team down.

Status: exploration, nothing decided, no code changed. Written 2026-09-09 on
`Airsticks-RPI`.

Inputs assumed, from the brief:

- **We control the AirSticks hardware.** Firmware, OSC schema and enclosure are
  ours to change, and we could sell them.
- **~100–300 units in three years.**
- **The two constraints that decide it: zero-IT install, and non-expert
  authoring.**
- **The vehicle is undecided.** So this report keeps vehicle open and asks what
  each shape would need. See §8.
- **R&D velocity is a requirement, not a nice-to-have** — tap, pressure and
  stretch sensors are in the pipeline and the platform must absorb them.

---

## 0. The four findings

1. **You already have a product; you have not named it as one.** There is a
   tagged release pipeline, a customer-facing web updater with backup and
   rollback, generated end-user documentation, thirteen shipped versions and
   a deployment guide that uses the word "customer". The gap between where you
   are and a sellable kit is smaller than the question implies. §1.

2. **The Pi is not one decision, it is four.** It is simultaneously the audio
   host, the WiFi access point, the kiosk display, and the appliance boundary.
   Only the *access point* role is load-bearing for zero-IT install — and it is
   the single most valuable thing you have for getting into schools. Keep that;
   everything else is negotiable. §3.

3. **New sensors are blocked by one specific thing, and it is not the OSC
   layer.** It is that ~226 personality files read `d.sensors.gyroEvent` and
   friends directly, 2,600+ times. The device model is IMU-shaped by
   construction (`sensorsProto`, a 7-channel `sensorBus`, an
   IMU-specific `~processDeviceData`). Adding a stretch sensor is easy. Adding
   it *without a flag day across 226 files* is the actual design problem, and
   `digiIn` already shows the shape of the answer. §4.

4. **Non-expert authoring has a much cheaper first step than anyone expects.**
   The teacher-facing artefact already exists and is already the right
   granularity: a **list**. `lists/*.sc` is 32 curated set-lists, selected by
   the `list` key in the kit's own machine file since 2026-09-10 — previously
   by hand-editing a variable in `personalityController.scd`. Turning list
   selection and list editing into a web UI is one contained change and it is
   the highest-value single move in this document. §5.

---

## 1. Where you actually are

### The asset

| | |
|---|---|
| Personality files | **226**, ~62,500 lines |
| SynthDefs inside them | **271** |
| Curated set-lists | **32** |
| p-files using samples | 106 |
| p-files with visuals | 66 emit visual events, 21 define their own shapes |
| Git history | 983 commits, Sept 2017 → now |

This corpus is the product. It is nine years of tuned-by-ear instrument design
that cannot be reconstructed from a spec, and it is the thing a competitor
cannot copy. The engine is replaceable; the repertoire is not.

The corpus is also **evidence of the market you already have**. The list names
are the customer list: `list_ITR_Mel`, `list_ITR_Nic`, `list_ITR_Brenton`,
`list_ITR_Alessio`, `list_MIM25_Caz`, `list_MIM25_Heather`, `list_MIM25_James`,
`list_MIM25_Matt`, `list_MIM25_Tim`, `list_glenroy`, `list_workshop1`,
`list_OpenLab25`, `list_yourDNA24/25/26`. Named performers, named venues, named
workshops, named years. You have been running a bespoke-per-site service for
years without calling it one. Productisation here means *repeating* what you
already do, not inventing something new.

### The product machinery that already exists

Most of this was built in `AirKitWebApp` and is running:

- **Release pipeline.** `git tag v*` → GitHub Action → zips `machines/`,
  `code3.0/`, `personalities/`, `lists/` + `VERSION` → GitHub Release.
  Thirteen tags cut (`v0.0.1`–`v0.0.13`).
- **Field updater.** Pi runs a Bottle server on `:8080`. Customer joins the
  AirKit WiFi, opens a browser, uploads the zip. The updater shuts SC down,
  backs up, installs, relaunches. One-click rollback.
- **Generated end-user docs.** `how_to_update.template.md` → `make doc` →
  `dist/How To Update AirKit v0.0.N.{md,html}`, one per release.
- **Offline-first by design.** No pip install on the Pi, no internet needed at
  the site. This is exactly right for schools and was a good call.

### What is missing, honestly

- **No fleet view.** You cannot answer "what version is Glenroy running?"
  without visiting Glenroy. At 300 units that is the first thing that breaks.
- ~~**No configuration story.**~~ **Resolved 2026-09-10.** `configPlan.md` is
  implemented: per-machine values live in `machines/<kit>.scd`, one tracked
  file per kit, and `code3.0/machine.scd` picks the right one at boot from the
  kit's own AP address. No machine edits another machine's file, so the silent
  merge documented in that report cannot recur. The machine branches
  (`Airsticks-RPI-Mel`, `AirKitDesktop`) are now retirable; `AirConcert` stays,
  being a different output rather than a machine variant.
  Still open: the config ships, but there is no way to *see* what a given kit
  is running without visiting it. The machine file knows `label`, `user` and
  the roster and posts them at boot — so the data a fleet view needs now exists
  in one place and is named. Per §16, CotF already has a fleet view in
  production, which makes this a matter of connecting two existing things
  rather than building one.
- **Version drift.** `VERSION` says `0.0.5`; the latest tag is `v0.0.13`.
  `systemView.scd` displays that file on the Pi, so **every unit in the field
  is currently reporting the wrong version.** That is a support problem the
  moment there is more than one version in the field.
- **Nothing a non-coder can change.** Changing what a device does means editing
  SuperCollider. Changing the set-list means editing SuperCollider.
- **No safety envelope.** No volume ceiling, no session limits, no "reset to
  factory", no guard against a p-file that crashes sclang mid-lesson.
- **No support surface.** No logs off-device, no crash reporting, no way for a
  teacher to say what went wrong other than describing it.

### The one structural risk worth naming now

`gatePlan.md` §"Rollback" identifies it: a bad **class file** lands outside
`APP_DIR`, so sclang dies at class-library compile, `main.sc` never runs, and
the web updater's rollback cannot reach it. **The unit bricks and it is an
in-the-room fix.** At 10 units that is a bad afternoon. At 300 units in
schools across three states it is the end of the product.

The mitigation is already written down in that document and it should become
policy, not a preference: **the field-updatable surface and the
non-recoverable surface must never be the same release.** Everything in
`machines/`, `code3.0/`, `personalities/`, `lists/` is one-click recoverable.
Anything else requires a visit. Design so that "anything else" changes
approximately never.

`machines/` joining that list is the point of shipping it: per-kit config is
now on the recoverable side of the line, so a bad roster or a wrong device
count rolls back with the code rather than needing someone in the room.

---

## 2. What the product could be — five shapes

These are not mutually exclusive; the last section suggests a combination. Each
is stated with what it is, who pays, what it costs you, and what it forecloses.

---

### Shape A — **The Kit.** A sealed appliance you plug in.

A case containing: two AirSticks, charger, a Pi-class box, a small screen or
HDMI out, speaker or line-out. Turn it on, it makes its own WiFi, it works.

- **Buyer:** a specialist school's music therapist or specialist teacher, on a
  program budget. NDIS-adjacent funding in Australia. Possibly a disability
  arts organisation.
- **Why it fits:** it is the *only* shape that fully satisfies zero-IT install.
  No school network, no admin rights, no software to approve, no laptop to
  borrow. You already have the AP + updater to make it true.
- **Cost to you:** you are now a hardware company. Enclosures, BOM, batteries
  (lithium shipping rules), warranty, spares, RMA. Every unit is a physical
  object someone can break.
- **Forecloses:** cheap scale. Every additional school is a physical build.
- **Verdict:** this is your current trajectory and it is the right *core*.
  100–300 units is squarely in hand-built-with-a-fabrication-partner territory,
  which is exactly what SensiLab makes possible. But see §3 — the case for the
  Pi is much narrower than the case for the Kit.

---

### Shape B — **Software + bring your own computer.**

AirKit as an installable app on a school laptop. AirSticks sold separately.

- **Buyer:** a music teacher in a mainstream secondary school with a music-tech
  lab; a university.
- **Why it might fit:** near-zero marginal cost per school; scales past 300.
- **Why it probably fails your constraints:** it fails zero-IT install badly.
  A locked-down school laptop will not install unsigned software, will not open
  UDP ports, and its WiFi will not let a sensor device talk to it. **The AP is
  the feature**, and this shape deletes it. Also inherits every audio-driver,
  latency and permissions bug on every machine you don't control.
- **Verdict:** not the lead product. Viable as a **second SKU for
  universities and conservatoria**, where there is IT support and a reason to
  want it on their own machines. Do not let it drive architecture.

---

### Shape C — **Instrument-only.** Sell AirSticks; give AirKit away.

Hardware is the product; the software is free and open, and the sensors work
with anything that speaks OSC (Max, Ableton via a bridge, TouchDesigner, Unity).

- **Buyer:** artists, universities, makers — *not* your stated audience.
- **Why it is interesting anyway:** it is the lowest-effort route to a real
  revenue line, it seeds the R&D community, and it does not require you to
  support classroom outcomes.
- **Why it is not the answer:** a specialist school cannot use a bare sensor.
  The value you provide is the 62,500 lines of repertoire and the fact that it
  works on arrival. Selling only the stick sells the least valuable half.
- **Verdict:** run it as a **side channel** to fund and feed the main product.
  It is also the natural home for the R&D community — see §6.

---

### Shape D — **The platform.** A curated content system with an authoring layer.

The Kit, plus: personalities become a distributable content format with
metadata; lists become teacher-authored programs; there is a library of
"instruments" a teacher browses, selects and arranges without code; new
sensors appear as new capabilities rather than new versions.

- **Buyer:** same as A, but the recurring value is the content and updates, so
  it supports a subscription or a service contract rather than a one-off sale.
- **Cost to you:** this is the largest engineering commitment in this document.
  It needs the sensor abstraction (§4) and the authoring tier (§5).
- **Verdict:** this is the destination, not the starting point. The important
  thing is that **A and D are the same product at different stages** — nothing
  in Shape A needs undoing to become Shape D, provided the sensor and authoring
  work in §4–5 lands before the fleet gets large.

---

### Shape E — **The service.** Residencies, programs, training, bespoke sets.

Sell the intervention, not the object. You bring kits, run a term-long program,
train the staff, leave a kit and a set-list built for those students.

- **Buyer:** the same schools, out of a program/arts budget rather than
  equipment. Often easier money than capital equipment.
- **Why it is here:** *you are already doing this.* Every `list_*.sc` is an
  instance. It is the highest-margin thing on the list and requires no new
  engineering. It is also what generates the repertoire that makes A and D
  valuable.
- **Verdict:** do this **now**, deliberately and priced, regardless of which
  other shape you pick. It funds the rest and it is the source of the content
  library.

---

### The shape I would argue for

**A + E now, converging on D.** Sell the Kit; sell the program that comes with
it; build toward the platform. Keep C as an open side channel for the R&D
community and universities. Treat B as a later SKU, never as the architecture.

The reasoning is that A is the only shape that satisfies zero-IT install, E is
the only shape that is already profitable and already exists, and D is what
turns 226 hand-tuned files from a liability into a catalogue.

---

## 3. Do we need the Pi?

The question is malformed, and unpacking it is most of the answer. The Pi is
doing four separate jobs:

| Job | Load-bearing? | Alternatives |
|---|---|---|
| **1. WiFi access point** | **Yes — critically** | ESP32-based AP; a small router in the case; nothing else that meets zero-IT |
| **2. Appliance boundary** | **Yes** | Any dedicated computer you control |
| **3. Audio engine host** | Negotiable | x86 mini-PC, Mac mini, phone/tablet, a DSP board |
| **4. Kiosk display** | Negotiable / arguably harmful | HDMI out to a TV; a tablet on the WiFi; no screen at all |

### Job 1 is why the Pi wins, and it has nothing to do with the Pi

The Pi makes its own network. That single fact is what makes AirKit installable
in a school with no IT involvement: the sticks join the AirKit WiFi, the teacher
joins the AirKit WiFi to change settings, and the school's network is never
touched, never asked for a port, never asked for a firewall exception. Your
updater already relies on this (`http://192.168.2.4:8080`).

**Every alternative must preserve this or it is disqualified by your own
stated constraint.** That is the test to apply to any "could we just use…"
proposal.

### Job 3 is where the Pi is weakest, and it is worth measuring

You are running SuperCollider with per-device routines at **100 Hz**
(`personalityController.scd:147`, `~secs` from the machine file), up to nine
virtual devices, plus a Qt GUI with per-frame `Pen` drawing, on a Pi.
`configPlan.md` records that the desktop once ran the same loop at 33 Hz — a 3×
difference that existed as a per-machine value, which suggested the rate was
being tuned against available headroom rather than against a musical
requirement.

**§16 supersedes that reading.** The 100 Hz is neither musical nor a headroom
artefact: it is `reportIntervalUs = 10000`, a sensor default in the firmware.
The two machine values have also since converged on `0.01`, so the per-machine
evidence is gone as well. `secs` stays a machine-file key regardless — not
because the rate is contested, but so the answer can be changed per host by
measurement rather than by editing shared code.

Before deciding anything: **measure it.** How much CPU headroom does a real
kit have with two devices, a sample-heavy personality, and visuals on HDMI?
`systemView.scd` already reads `/proc/loadavg` and the thermal zone, so the
instrumentation is there. If the answer is "comfortable", the Pi stays and this
section is closed. If the answer is "thermally throttling in a warm classroom
in February", that is a product-defining fact and you want to know it now, not
at unit 150.

A specific thing to check: `screens.scd` shells out to `xrandr`, and `main.sc`
notes that `unixCmdGetStdOut` **blocks the same language thread that runs
`~next`**. The code already avoids polling for that reason. Any future feature
that shells out — fleet reporting, log upload, health checks — hits the same
hazard and must not be written naively.

### The honest options

1. **Stay on Pi.** Cheapest, already works, already deployed, thermal and CPU
   headroom is the open question. Best choice unless measurement says otherwise.
2. **Pi for AP + control, separate box for audio.** Splits jobs 1/2 from 3.
   More BOM, more failure modes, more cables. Only justified if measurement
   forces it.
3. **x86 mini-PC (N100-class).** ~3–5× the CPU, runs an ordinary Linux, still
   makes its own AP, still fits in a case, costs perhaps 2–3× a Pi. If the Pi
   is genuinely marginal this is the boring correct answer, and it changes
   almost nothing above the OS.
4. **Phone/tablet as host.** Attractive for cost and screen. Fails on
   SuperCollider deployment, background audio policy, and app-store review, and
   deletes the AP. Not viable for the lead product.

### The recommendation

**Keep the Pi for now, but stop treating "Pi" as part of the identity.** The
product is "a box that makes its own network and plays". The mechanism that
makes the host swappable is now **in place** — per-machine config files
instead of per-machine branches — so the choice of host is a line in a file
rather than a branch. `machine.scd` already defaults `latency`, `border` and
`fullScreen` per platform, so a different host needs a machine file, not a
port. This question is now open cheaply, as intended.

**And separately: reconsider the screen.** The kiosk GUI is a performer's tool
— device panels, orientation cubes, personality names, server stats, a shutdown
button. In a classroom it is mostly an attractive nuisance. The HDMI-visuals
path in `main.sc` is the part with classroom value; the control GUI probably
wants to be a web page on the teacher's own tablet, served by the same Bottle
server that already serves the updater. That also solves a problem you will
otherwise hit: **you cannot put a touchscreen in front of a student who is
meant to be moving.**

---

## 4. New sensors — the real architecture question

Tap, pressure and stretch are in R&D. Here is precisely what stands in the way,
grounded in the code.

### The current shape

```
oscController.scd:10   var oscMessageTag = "IMUFusedData";   // ONE packet shape
oscController.scd:11   var numAirwareVirtualDevices = 9;
oscController.scd:37   sensorsProto = ( gyroEvent, gyroMass, rrateEvent,
                                        rrateMass, accelEvent, accelMass,
                                        quatEvent, quatReference,
                                        quatCalibrated, velocity, ...,
                                        digiInEvent )
oscController.scd:72   sensorBus = Bus.control(s, 7)         // ax ay az w x y z
```

and the derivation in `personalityController.scd:151–186` is entirely
IMU-specific: `accelMass` from `accelEvent.sumabs`, `rrateMass` from
`rrateEvent.sumabs`, three normalised filtered gyro axes.

So the device model is not "a sensor device". It is "an IMU, plus four buttons".

### The good news: `digiIn` already proves the pattern

Buttons are not IMU data. They arrive on their own OSC address
(`/N/DigiIn`), have their own listener, land in their own field
(`digiInEvent`), need no derivation, and **39 personality files use them
today**. Nothing about adding them required changing the IMU path.

That is the template. A pressure sensor is `digiIn` with a float. A stretch
sensor is `digiIn` with a float and a calibration. The OSC layer is not the
problem.

### The actual problem: 2,600 direct reads

```
sensors.gyroEvent   1009 uses across personalities/
sensors.rrateEvent   727
sensors.accelEvent   606
sensors.velocity     201
sensors.quatEvent     60
sensors.digiInEvent   61
sensors.rotateEvent   25
```

Personalities reach into the device's sensor Event by name. That is fine and
was the right call for a live-coding instrument — but it means **the sensor
schema is public API across 226 files**, and any change to its shape is a flag
day.

Note `rotateEvent`: declared in `sensorsProto` at line 49, **never written
anywhere in `code3.0/`**, and read 25 times in personalities. That is a field
that used to be populated and no longer is — a silent zero flowing into 25 read
sites. It is a small, concrete example of exactly the failure mode a schema
with no contract produces.

### What to build

**Do not** refactor the sensor model. It works, 226 files depend on it, and
"correct in isolation, wrong for the situation" is the likeliest outcome.

**Do** add alongside it:

1. **A capability descriptor per device.** When a device announces itself, it
   says what it has: `(imu: true, digiIn: 4, pressure: 2, stretch: 1)`. Today
   `addDevice` already does a `/Config/GetConfig` handshake and reads colour
   bytes out of the reply — the handshake exists, it just carries almost
   nothing. Extend the reply, not the architecture.

2. **A generic sensor channel with the `digiIn` shape.** New sensor types get
   their own OSC address, their own listener, their own named field. Existing
   fields never change. `sensorsProto` grows; it never mutates.

3. **A declared requirement per personality**, so a p-file can say "I need
   stretch" and the system can grey it out — rather than a teacher selecting an
   instrument that silently does nothing because the stick in their hand has no
   stretch sensor. This is the single most important item for classroom
   robustness, and it is *also* the thing that makes a teacher-facing library
   possible at all (§5). Cheapest form: one optional line at the top of a
   p-file, defaulting to "IMU only" so all 226 existing files remain valid
   without edits.

4. **A capability-aware `~processDeviceData`.** Today it is one fixed IMU
   derivation. It should run the IMU derivation *if the device has an IMU*, and
   run a pressure derivation if it has pressure. Additive, per capability.
   No existing p-file changes.

5. **Widen the control bus, or don't.** `Bus.control(s, 7)` is exactly the IMU
   payload, and SynthDefs read it directly. New sensors either get their own
   bus or the bus becomes capability-sized. Worth deciding early because it is
   the one place where the change is genuinely visible to SynthDefs.

### And the thing to protect

Whatever is built here, **`m.accelMassFiltered` must keep meaning what it means
today** (891 uses). Every one of those is a tuned mapping. A "cleaner" model
that shifts the scaling of `accelMass` by even a little silently retunes 226
instruments, and you will find out on stage. Additive only.

---

## 5. Non-expert authoring

You named this as one of two deciding constraints. There are three tiers, and
they are wildly different in cost.

### Tier 1 — **Curate.** Choose and order instruments. *(cheap, do this first)*

The artefact already exists. A list is:

```supercollider
( [ "silence", "trainMove2", "trainBass3", "multiBeat1", ... ] )
```

An array of names, selected by the `list` key in the kit's machine file
(`machines/<kit>.scd`) since 2026-09-10 — previously by hand-editing
`var list =` in `personalityController.scd`. There are 32 of them and they are
named after performers, venues and workshops — which is to say **a list is
already the unit of teacher-facing work.**

The move: put list selection and list editing in the web UI that is already
running on the Pi. A teacher browses available instruments, drags them into an
order, names the list, saves it, and it is live. No SuperCollider, no file
editing, no zip.

This is a contained change to a system that already exists, and it converts
the single most common bespoke request ("can we have a set for this student?")
from a developer task into a teacher task. **This is the highest-value single
move in this document.**

Two prerequisites, both small:
- Personalities need **metadata** — a display name, a one-line description, and
  ideally a tag or two (loud/quiet, tonal/percussive, needs-stretch). Right now
  a p-file's identity is its filename. `bongo1` and `trainChooka2` mean nothing
  to a teacher.
- ~~Lists need to move out of `personalityController.scd`.~~ **Done.** The
  roster is the `list` key in the kit's machine file, kept separate from the
  platform values so one kit can run another's repertoire without inheriting
  its platform settings. `list` accepts a String or a per-device Array.

### Tier 2 — **Tune.** Change how an instrument responds. *(moderate)*

Sensitivity, volume ceiling, pitch range, how much movement is needed to
trigger a sound. In a specialist setting this is not a nicety — it is the
difference between an instrument a student can play and one they cannot.

The good news is that the house style already points here. The "tightness rule"
in `CLAUDE.md` says a draw function reads its constants off the event, and
"every tunable in one place". `gatePlan.md` documents that the amplitude gate
thresholds are per-file magic numbers that ought to be named parameters. The
codebase is already arguing with itself in favour of surfacing parameters.

The move: let a p-file **declare** a small set of named, ranged, labelled
parameters, and let the web UI render sliders for them. `ControlSpec` is
already used for exactly this shape in commented-out model fields
(`personalityController.scd:55, 63`). Per-student presets fall out of it.

This should be **opt-in per personality**. Do not attempt to retrofit 226
files. Add it to the template, use it on the next 20 files written, and let it
spread by usefulness.

### Tier 3 — **Create.** Make a new instrument. *(expensive; probably don't)*

The temptation is a visual patcher. Resist it. It is a multi-year project, it
will be worse than what exists, and the people who would use it are the same
people who could learn to write a p-file.

The better answer is **Shape E**: creating instruments stays a service you
provide, and stays how the repertoire grows. A teacher who wants something new
asks for it; it arrives in the next release; it also ships to everyone else.
That is a feature of the business model, not a limitation of the software.

If a middle tier is wanted later, it is **templates** — "a percussion instrument
with these four samples", "a drone with this scale" — not a general patcher.
The 226 files already cluster into a handful of architectures;
`personality_findings.md` documents four of them.

---

## 6. Keeping the R&D team fast

This was stated as important in the brief and it deserves an explicit section,
because most of the natural productisation moves are exactly the moves that
would kill it.

### What is already right

**The personality file is the plugin format, and it is a good one.** It is
plain interpreted SuperCollider, hot-reloaded on save
(`personalityController.scd:240`), namespaced per device, with a documented
contract (`ak_pfile_authoring.md`, 700 lines) and a template. A researcher can
open a file, save it, and hear the change in 10 milliseconds without restarting
anything. That is a better iteration loop than most commercial audio software
offers, and it is worth defending against every "for production we should…"
argument that will come.

**The core/content split is already the release boundary.**
`release.config.json` ships `code3.0` (core), `personalities` (content) and
`lists` (curation). That is the right decomposition and it happened by
instinct. Make it explicit and versioned.

### What would kill it

- **Compiled class files in the boot path.** `gatePlan.md` establishes this
  in detail: `-a` standalone mode ignores the Extensions dirs, the class must be
  copied into the standalone tree, it only compiles at the *next* boot, and a
  syntax error in it bricks a unit beyond the reach of the web rollback.
  Its recommendation — **Option 1, environment functions in `code3.0/`, not
  class extensions** — is the right call and should be a standing rule, not a
  one-off decision. The house idiom (`~curveAbove`) already does this.
- **A GUI that must be updated whenever a sensor is added.** Hence the
  capability descriptor in §4: the UI reads what the device says it has, rather
  than being taught about each new sensor.
- **Freezing the schema.** Once there is a published personality format,
  changing it breaks the field. Version it from day one so it can change.
- **Branch-per-machine.** ~~The fix is written and unimplemented.~~ **Fixed
  2026-09-10** — machines differ by a file in `machines/`, identified
  automatically, and that file ships and rolls back with the release. What
  this does *not* yet fix is the symptom that prompted it: `gatePlan.md` was
  written on `AirConcert` referring to p-files (`PERCUSSION.sc`,
  `SOPRANOVOICE.sc`, `BIRDY.sc`, `WindVoice.sc`) that do not exist on this
  branch. **Repertoire is still diverging** across the remaining branches, and
  consolidating it is a separate job from consolidating config.

### The SensiLab loop

Fabrication access is a genuine strategic asset, and it argues for a specific
architecture: **make new sensors cheap to try, not cheap to ship.** A researcher
should be able to bring up a stretch sensor on a breadboard, have it announce
itself over OSC, and have it show up in AirKit as a new capability with a p-file
that uses it — in an afternoon, without touching `code3.0/`, and without any
of the 226 existing files noticing. Everything in §4 is in service of that
sentence.

The corollary is a **two-speed release**: a stable channel that goes to schools
and a research channel that goes to SensiLab and to your own performances. They
share the core; they do not share a release cadence. Today they are the same
thing, which is why a workshop set-list and a concert set-list are different
branches.

---

## 7. The moves

Ordered by dependency, not by ambition. Nothing here requires the vehicle
decision to be made first.

### Phase 0 — Stop the bleeding *(weeks, no product decisions)*

1. **Fix the `VERSION` drift.** Units are reporting `0.0.5` against a `v0.0.13`
   tag. It is a two-line fix and it is a support blocker.
2. ~~**Implement `configPlan.md`.**~~ **Done 2026-09-10** (migration steps 1–2).
   `machines/` + `code3.0/machine.scd`, shipped in the release, kits identify
   themselves by AP address. This unblocked host swapping (§3),
   roster-as-product (§5) and per-site configuration. **Not yet verified on a
   Pi** — that is the remaining risk, and it is the step that can break boot.
3. **Make the "never ship a class file with anything else" rule explicit** in
   the release process, per `gatePlan.md`.
4. **Fold `AirKitDesktop` into `Airsticks-RPI` and retire the machine
   branches** — `configPlan.md` steps 3–5. Safe now that 2 has landed, because
   the shared files no longer hold machine-specific values. `AirConcert` is
   excluded by design.

### Phase 1 — Make a fleet possible *(the 100–300 unit prerequisite)*

5. **Units report in.** Version, uptime, last error, which list is loaded —
   whenever they see the internet, or via a file the teacher can email. Mind the
   blocking-`unixCmd` hazard noted in §3.
6. **Logs off-device.** A rolling log the updater can hand back as a download,
   so a support conversation starts with data.
7. **A safety envelope.** Volume ceiling, a factory-reset path, and a
   watchdog that restarts sclang if it dies mid-lesson. `start_airkit.sh` is
   currently a one-line background launch with nothing watching it.
8. **Decide the host.** Measure Pi headroom under realistic classroom load and
   close §3 with a number.

### Phase 2 — Absorb the new sensors *(unblocks R&D)*

9. **Capability descriptor** in the device handshake.
10. **Additive sensor channels**, `digiIn`-shaped.
11. **Capability-aware derivation** in `~processDeviceData`, additive only.
12. **Per-personality requirements**, defaulting to IMU-only.

### Phase 3 — Give it to teachers *(the authoring constraint)*

13. **Personality metadata** — name, description, tags, requirements.
14. **List editing in the web UI.** The high-value move.
15. **Declared parameters + sliders**, opt-in, on new files first.
16. **Move the control GUI to the browser**; keep HDMI for visuals.

### Phase 4 — Decide the vehicle

Only once 1–3 are real, because they are needed under every vehicle.

### The "not yet" list

- A visual patcher for instrument creation.
- A cloud account system.
- Porting off SuperCollider.
- An iOS/Android app.
- Any rewrite of the sensor model.
- Any refactor of the 226 personalities.

---

## 8. The vehicle — what each option would need

Undecided, so this is the decision framing rather than a recommendation.

| Vehicle | Needs | Good if |
|---|---|---|
| **Spin-out** | Capital, an IP assignment from Monash, someone doing sales full-time, warranty and liability structure | You want this to be the main thing, and 300 units is a floor not a ceiling |
| **Research platform** | Grant continuity, a partner-school network, publication output | R&D velocity and access matter more than revenue; SensiLab is the natural home |
| **Licence to a distributor** | A product stable enough to hand over, documentation, a support SLA someone else can meet | You want reach without becoming a company; costs margin and control |
| **Open source + services** | A public repo you are willing to maintain, a services capacity | Community R&D matters; revenue comes from §2 Shape E |

Two observations that hold under all four:

- **The moves in Phase 0–2 are required regardless.** None of them commits you
  to a vehicle. Do them while deciding.
- **Shape E (services) is available immediately under every vehicle**, needs no
  engineering, is already what you do, and generates the content library the
  other shapes depend on. If there is one thing to formalise this year, it is
  pricing and packaging the residency/program work you are already delivering.

---

## 9. Naming

`AirKit` is the codebase name and the host software. `AirSticks` is the
instrument. Neither is a product name for a school.

Points to consider:

- What a teacher buys is **the whole thing** — sticks, box, repertoire,
  support. That needs a name, and it probably isn't "AirKit", which describes
  the OSC host and means nothing to a buyer.
- Keeping `AirKit` internally as the platform name is fine and probably right —
  it keeps the R&D identity intact while the school-facing product gets its own
  name.
- **Check trademark availability early**, in AU at minimum. It is cheap now and
  expensive after you have printed enclosures.
- "Air-" is a crowded prefix in music tech and in Apple's product line.

---

## 10. Open questions

Ordered by how much they change the plan.

1. **What does a Pi actually have left over** under two devices, a
   sample-heavy personality and HDMI visuals, in a warm room? This closes §3
   and it is a measurement, not an opinion.
2. **Is the 100 Hz process rate a musical requirement or a headroom artefact?**
   The desktop runs the same loop at 33 Hz. If 33 Hz is fine, a lot of §3
   pressure disappears.
3. **Who is the buyer, precisely** — a therapist with a program budget, a music
   department with an equipment budget, or a school executive? They have
   different price ceilings, different procurement paths, and want different
   evidence.
4. **What does the sensor roadmap actually look like?** Tap, pressure and
   stretch on the *stick*, or a family of different objects — a mat, a cushion,
   a wearable? The second is a much bigger product question, and it is a
   plausible read of the specialist-school need (not every student can hold a
   stick).
5. **Is there an evidence requirement?** Specialist settings often need to
   justify a purchase against outcomes. If so, that is a research output
   SensiLab is well placed to produce, and it should be planned rather than
   improvised.
6. **What is the support commitment you are willing to make?** This determines
   more about the product than any technical choice. "Next release fixes it" and
   "someone comes out within a week" are different companies.
7. **Accessibility and safety compliance** — electrical, battery shipping,
   volume limits for hearing safety, and any duty-of-care requirements in
   specialist settings. Worth finding out before the enclosure is designed, not
   after.

---
---

# Part II — the four-repo system

Added 2026-09-10. Part I was written against `AirKit` alone. This part folds in
`AirStick-ESP-Arduino`, `AirKitWebApp` and `PyOSCCam` as what they are — the
same project — and answers four specific questions about them. §16 lists the
Part I claims that change as a result.

---

## 11. The system is five repositories, and one of them has already shipped the product

| Repo | Language | Role | State |
|---|---|---|---|
| `AirKit` | SuperCollider | Engine, device model, 226 personalities, visuals | Live |
| `AirStick-ESP-Arduino` | C++ / ESP32-S3 | Instrument firmware, OSC schema, config protocol | Live, fw 0.4 |
| `AirKitWebApp` | Python / Bottle | The appliance's HTTP surface: update, rollback, restart, device list | Live, 13 releases |
| `PyOSCCam` | Python | Synchronised video + OSC record and playback | Working, unintegrated |
| `CotF` | TS / Node / Unity | A production deployment of all of the above | **Ran Edinburgh Fringe, Aug 2026** |

### The finding that reframes Part I

**Concerts of the Future is not an adjacent project. It is AirKit in production
at product scale, and it already built most of what §1 called missing.** 25
AirSticks, five seats, three rooms, a fleet with battery and RSSI telemetry, a
staff dashboard, a remote admin surface, an operator kill-switch, and a research
data pipeline — running nightly for most of August in a venue with no
sympathetic IT.

It drives AirKit over an OSC vocabulary far larger than the one documented here:

```
/airkit/getState   /airkit/getRoster  /airkit/getSeats   /airkit/getLevels
/airkit/state/reply  /airkit/roster/reply  /airkit/seats/reply  /airkit/levels/reply
/airkit/masterLevel  /airkit/voiceMute  /airkit/outputMode  /airkit/resetSeat
/airkit/testTone     /airkit/panic      /airkit/seek        /airkit/loadPersonality
```

**None of those exist on `Airsticks-RPI`.** They live on `origin/AirConcert`,
whose `code3.0/API.md` is a 161-line OSC contract against this branch's 22 lines,
and whose `code3.0/cotf/` holds the deployment-specific entry point
(`main_cotf.scd`, `config.scd`, `bind-check.scd`).

### AirConcert stays separate, and should

That branch is not a superset of this one, and merging it would be destructive:
it has **no `visualCore.scd` and no `vdefLib.scd`** — it carries the older
`shapeLib.scd` line instead — and `code3.0/` differs by 6380 insertions against
2217 deletions. It is a divergent fork carrying a season of show-specific tuning
(`~cotfPatchTrims`, per-patch dB ear-tunes dated to individual nights). That
work belongs to a production, not to the product line. §16 already excludes it
from the config consolidation for the same reason.

So what AirConcert holds for this document is **evidence and a contract, not
code to take**. Two things cross the boundary, and neither is a merge.

**1. The OSC contract discipline.** `AirConcert:code3.0/API.md` opens with rules
this repo wants regardless of CotF:

> This file is the single source of truth for every OSC address AirKit consumes
> or emits. Any commit that changes OSC behaviour MUST update this file in the
> same commit.
>
> 1. Additive only — add new addresses; never change or repurpose existing ones.
> 2. New arguments are appended, with defaults preserving old behaviour.
> 3. Defaults must preserve composer-machine behaviour exactly.
> 4. Anything COTF-specific is marked **[COTF]**.

That fourth rule is the one that makes the file portable: it was written to be
read by someone who does not have CotF, and it already separates what is general
from what is deployment-specific. It also records things nothing on this branch
records — that `/airkit/mute` is a GUI unload/reload and **not** an audio mute
("Do not repurpose"), and that devices are keyed by sender source port, so
**senders must keep a stable source port**.

Adopting that file and that rule here costs nothing, closes the gap between
`API.md` (five addresses) and reality (sixteen live on this branch alone),
and is the thing that would have made the divergence visible while it was small.

**2. Proof, for §8 and §10.** CotF is a live demonstration that the intervention
sells (§2 Shape E), that a fleet at product scale is operable, and — via §15 —
that §10 Q5's evidence question has a working answer. Read it for what it
proves; do not plan to inherit its code.

### What this changes in the plan

Nothing in §13 waits on AirConcert. A control API that has been designed and
proven once can be written again here, on this branch's terms, against a
contract file — a smaller and far safer job than reconciling two forks of
`code3.0/`. The only thing worth doing before designing it is reading
`AirConcert:code3.0/API.md`, so the second implementation does not contradict
the first where it has no reason to.

---

## 12. AirStick middleware and configuring for different sensors

### What is there today

Firmware 0.4, ESP32-S3 Feather, `AirStick-ESP-Arduino-FW/`. Seven sensor/IO
modules, each a header with a `setupX()` / `updateX()` pair, plus the WiFi and
OSC controller:

| Module | Hardware | OSC out | Notes |
|---|---|---|---|
| `BNO085.h` | BNO085 IMU over SPI | `/N/IMUFusedData` (7 floats) | `reportIntervalUs = 10000` — the 100 Hz |
| `Air_Battery.h` | MAX17048 fuel gauge, I²C 0x36 | `/N/Battery` | |
| `Air_I2CIn.h` | CAP1188 8-pad capacitive, I²C 0x29 | `/N/I2CIn` | proximity-tuned, ~28 Hz ceiling |
| `Air_DigiIn.h` | 4 buttons, Bounce2 | `/N/DigiIn` | GPIO 1–4 |
| `Air_AnalogIn.h` | flex/pressure, ADC | `/N/AnalogIn` | GPIO 1–4 |
| `Air_I2COut.h` | I²C output | `/N/I2COut` | |
| `Air_LED.h` | NeoPixel | — | colour persisted in NVS |
| `WifiController.h` | — | `/N/Config` | the config protocol |

**This is not middleware. It is a set of build variants selected by commenting
lines out.** `AirStick-ESP-Arduino-FW.ino:70` is the whole configuration story,
and `:83` repeats it for the update calls:

```cpp
  // setupDigiIn();
  setupAnalogIn();
  // setupI2COut();
  // setupI2CIn();
```

That block read the other way round a fortnight ago — CAP1188 on, analog off —
and flipped with the `more sensor testing` commits. Which is the argument, not a
complaint: **the record of which sensors a stick has is a comment character in a
file every variant edits.**

Four consequences worth naming:

1. **Which sensors a stick has is decided at compile time and is invisible at
   runtime.** Nothing in the OSC protocol says which of those `setup` calls ran.
2. **`Air_DigiIn` and `Air_AnalogIn` both claim GPIO 1–4.** They are mutually
   exclusive as written — not by policy but by pin. So "which sensors are
   fitted" is partly a board question, not a firmware flag, and any capability
   model has to say so rather than pretend every combination is buildable.
3. **The host does not listen to most of it.** `oscController.scd:233-235`
   builds exactly three listener patterns — `IMUFusedData`, `Battery`,
   `DigiIn`. There is none for `/N/AnalogIn` or `/N/I2CIn`. So as the firmware
   stands *today*, with `setupAnalogIn()` enabled, the stick is streaming
   analog readings at ~100 Hz that AirKit receives and discards. That is the §4
   gap in its most literal form, and it is live right now.
4. A researcher bringing up a new sensor edits the `.ino`, which means the
   `.ino` is a merge point every variant touches. Same failure mode as
   `personalityController.scd` holding the set-list — which has since been
   fixed by giving each machine its own file (§16), and the same fix shape
   applies here: one file per variant, none of them shared.

### `Air_I2CIn.h` is the model to copy

It is the best-engineered file in the firmware and it should be the template for
every sensor added from here. It derives its own output rate from its tuning
registers as compile-time constants, documents *why* (an earlier version gated
at 41 ms against a chip producing data every 328 ms, so seven of every eight OSC
frames were byte-identical duplicates), runs a 1 s health check, and on fault
goes offline and retries rather than blocking the loop — because "if this
blocks, the IMU stream stops and the config listener stops, and the stick is
gone." That last property is the one that matters for a school kit: **a broken
sensor must degrade to a missing sensor, never to a dead stick.**

### The config protocol already exists, and AirKit throws most of it away

Part I §4 said the `/Config/GetConfig` handshake "carries almost nothing".
That is wrong. `WifiController.h:363 sendConfig()` replies with **eighteen
values**:

```
ID, firmwareMajor, firmwareMinor,
stickIP[4], localPort,
oscIP[4], oscPort,
frameDelay,
r, g, b, a
```

And `oscController.scd:355` reads this:

```supercollider
var a = msg2.keep(-4)/255;
```

**The last four. The colour.** The stick ID, the firmware version, its IP, its
frame delay — all on the wire, once per connect, all discarded. §1's "you cannot
answer *what version is Glenroy running?*" is, for the sticks at least, a
parsing problem rather than a protocol problem. Fixing it is one function, no
firmware change, no personality change.

The writable side is equally real and equally undocumented here:
`/Config/SetLED`, `/Config/SetName`, `/Config/SetID`, `/Config/RequestStream`,
`/Config/SetFrameDelay`, `/Config/RecalI2CIn` — all persisted to NVS via
`Preferences`, all surviving a power cycle. A stick can already be renamed,
re-pointed at a different host, throttled and recalibrated over the air.

### What to build, then

Additive on both sides, in this order:

1. **Parse the whole config reply.** `d.config = (id:, fw:, ip:, port:,
   frameDelay:, color:)`. Nothing else changes. This is a half-day and it
   unblocks fleet reporting (§7 move 5) for the instrument half of the fleet.
   While in there: **add a listener for `/N/AnalogIn`**, in the `digiIn` shape,
   landing in its own `sensorsProto` field. The stream already exists and is
   being dropped; catching it costs one `OSCFunc` and no existing p-file
   notices.
2. **Add a capability field to `sendConfig`.** A bitfield plus a variant string
   (`"v0.4-imu-cap8"`). The firmware knows which `setup` calls it made; have it
   say so. This is §4's capability descriptor, and the handshake to carry it is
   already running.
3. **Make the module set a runtime decision where the pins allow it.** One
   binary, NVS flags, `/Config/SetModules`. Where the pins do not allow it
   (DigiIn vs AnalogIn), the variant string is the honest answer.
4. **Finish the rate work.** `optimize_report.md` sequenced two changes.
   **Step A is done**: `WifiController.h:95` now calls `WiFi.setSleep(false)`
   with a comment explaining the DTIM-beacon stall it removes, so the radio no
   longer parks between beacons and blocks `udp.endPacket()` for 3–100 ms.
   **Step B is not**: `reportIntervalUs = 10000` still stands at `BNO085.h:18`,
   so the stream is still capped at 100 Hz by the sensor itself. Dropping it to
   `5000` is the one-line change to 200 Hz. Measure average rate *and* max
   inter-packet gap before and after — the report tells you exactly how, and
   warns that the both-reports-ready gate skews as the period shrinks.
5. **Implement `Multi-Network-WiFi-Plan.md`.** Written, unimplemented. A kit
   that must sometimes join its own AP and sometimes a venue network needs it,
   and the non-blocking state machine it specifies is the right shape.

### And there are two firmware trees

`CotF/airstick/AirStick-CStickTest-FW/` is a restructured fork with modules this
one does not have: `Air_Heartbeat.h` (a 2 s `/airstick/{id}/heartbeat` carrying
battery %, RSSI and charging state, with a `-1` sentinel so a dropped I²C read
does not flap the UI to 0 %), `Air_DeepSleep.h`, `Air_DigiOut.h`, and a
`AirStick-ESP-Arduino-FW.h` that pulls the globals out of the `.ino`.

That heartbeat is exactly §7 move 5 for the sticks, already written and already
proven across a month of shows. Unlike the AirKit fork, that module is
genuinely portable — a self-contained header with its own timer and its own OSC
address, depending on nothing CotF-specific. Copying it is not merging a branch.

---

## 13. The WebApp — deploying samples, and remote control

### What exists

Bottle on `:8080`, SSE progress streaming, zip upload and install, automatic
backup, one-click rollback, restart, a `/devices` route that queries AirKit over
OSC (`/airkit/remote/devices` → `/airkitremote/devices`), generated end-user
documentation, deploy scripts and a release builder. Zero pip dependencies on
the Pi by design. This is good work and Part I §1 undersold it.

The UI is 96 lines of HTML, 430 of JS, 404 of CSS, with `<meta name="viewport">`
and one `@media (max-width: 600px)`. **Mobile-capable, not mobile-designed** —
which is the right amount for an updater and not enough for a control surface.

### Finding: the audio-deploy pipeline installs to a directory nothing reads

`engine.py:320 _update_audio()` is complete. It downloads an
independently-versioned `audio_vX.Y.zip` from the manifest, verifies the SHA256,
backs up `/home/pi/audio` to `/home/pi/audio_backup`, extracts, and restores the
backup on failure. Audio has its own version line in `version.json` and its own
row in the manifest.

**No personality file reads `/home/pi/audio`.** 107 p-files load samples, and
every path is a literal:

```
92 × "~/Downloads/yourDNASamples/…"     18 × "~/Downloads/melSamples/…"
14 × "~/Music/cotf_samples/…"            7 × "~/Downloads/openLabSamples/…"
 6 × "~/Downloads/alessioSamples/…"      5 × "~/Downloads/nicSamples/…"
 … eleven distinct roots, none of them the managed one.
```

So the whole sample-distribution machine — R2 hosting, checksums, versioning,
backup, rollback — is built, shipped, and connected to nothing. This is the
cheapest large win available in the web app.

**The fix, and the care it needs.** Introduce one indirection in the p-file
contract — `~samples.("yourDNASamples/drums")` resolving against a configured
root, defaulting to `/home/pi/audio` on the Pi and to the current locations on a
laptop. Then:

- Add it to `_TEMPLATE_ak_pfile.sc` and `ak_pfile_authoring.md`.
- Use it on new files. Migrate old ones as they are touched.
- **Do not flag-day 107 files.** And be precise about RULE ZERO: changing a
  *path* is not changing the sound, but changing *which file loads* is. Any
  migration must be name-for-name, and a mismatch will be silent — a
  `Buffer.read` on a missing file fails asynchronously and the synth simply
  never starts.
- The audio root belongs in `configPlan.md`'s per-machine config, not in a
  variable each machine edits. **That config now exists**, so this is a key on
  the machine file — `audioDir`, alongside `listsDir` and `personalityDir`.
  Note those two are *derived* from the checkout rather than configured; if the
  audio root can be derived the same way, it should be, and no machine file
  needs to mention it at all.

This also removes a real support hazard: today a fresh Pi with the right app
version and the wrong `~/Downloads` contents runs 107 instruments that make no
sound, with nothing on screen to say why.

### Remote control

This branch's AirKit has almost no control API. AirConcert has a good one and is
staying where it is (§11), so this is a design to write, not a merge to perform —
read `AirConcert:code3.0/API.md` first so the two do not contradict each other,
then build against a contract file of this branch's own.

What the teacher-facing page needs, in the order §5 argues for:

1. **Browse and choose a list** — the highest-value move in Part I, unchanged.
2. **Per-device personality select** — `/airkit/loadPersonality` exists here and
   is already public (no `NetAddr` filter, deliberately, per the comment at
   `personalityController.scd:379`).
3. **Volume ceiling, mute, panic** — designed already as `masterLevel`,
   `voiceMute`, `panic`. Note the trap AirConcert's contract records:
   `/airkit/mute` on this branch is a GUI unload/reload, not an audio mute. Do
   not repurpose it.
4. **Calibrate** — `/airkit/calibrate` exists here.
5. **Fleet/status** — `getState`, `getRoster`, `getSeats` as designed; stick
   firmware and battery from §12's config parse and the heartbeat module.
6. **Who is playing** — needed by §15, and nothing anywhere has it yet.

Three constraints to write down before anyone starts:

- **Extend the Bottle server; do not add a second one.** It is the only
  always-on HTTP surface on the box and its dependency-free design is load-bearing
  for the offline-install story.
- **Respect `PERFORMANCE_TUNING.md`.** The Pi's WiFi IRQ must out-rank the audio
  threads (FIFO 55 vs ≤ 40, NIC on core 0, audio on 1–3). That balance exists
  because a priority inversion once took AP latency from 6 ms to 1300 ms.
  A control UI adds network load to the same radio that makes zero-IT install
  possible. Poll slowly, prefer SSE over polling, and re-run that document's
  benchmark after the UI lands.
- **The tablet is the control surface, not the panel.** §3's argument stands and
  this is what makes it actionable: you cannot put a touchscreen in front of a
  student who is meant to be moving.

---

## 14. Where PyOSCCam fits

### What it is

Synchronised video + OSC recorder and player. Records camera to `.mp4` while
logging every incoming OSC message with a receive timestamp to `.jsonl`, plus a
`.vtt` sidecar; on playback it re-fires each message at its original offset
while the video plays. Headless mode, OSC transport control
(`/pyosccam/record|stop|load|play|pause|seek|quit`), multi-camera, UVC PTZ,
`picamera2` backend for RPi5.

**It has already recorded AirSticks.** The two sample recordings committed in
the repo are `/4/IMUFusedData` with the seven-float body, from 12 March 2026 —
real packets in AirKit's own schema.

### The integration is already half-wired, from both ends

| Direction | Where it is written |
|---|---|
| AirKit → PyOSCCam | `oscController.scd:11` `outAddr` (the address itself now comes from the machine file, not a literal); `:407` `if(oscThru, { outAddr.sendMsg(*msg) })`. 5005 is PyOSCCam's default listen port. |
| PyOSCCam → AirKit | `config.yaml`: `send_port: 57120`, with `send_ip` commented `# playback OSC destination AirKit`. |

`oscThru` is toggled by `/airkit/oscThru`. So the record path is one OSC message
away from working, and it is a tee — it forwards the message verbatim without
touching the device path.

### The playback path has a specific trap, and it will look like a different bug

AirKit's device listeners are **source-filtered and source-keyed**:

```supercollider
var address = NetAddr.new(d.ip, d.port - i);        // oscController.scd:241
d.listeners.airware = OSCFunc({ ... }, pattern, address);
…
var d = addDevice.(addr.ip, addr.port + i, i + 1);  // :410 — keyed by SOURCE PORT
```

A replayed packet arrives from PyOSCCam's socket, not the stick's. AirKit will
therefore auto-register a *new* device at the replay's source port — and then
`addDevice` sends `/Config/GetConfig` back to that port (`:389`), which nothing
answers. The one-shot config listener never fires, so `/airkit/addDevice` is
never sent, so `visualCore` and `deviceView` never learn the device exists.
`/airkit/loadPersonality` *is* sent unconditionally (`:384`).

**Expected symptom: the replay makes sound, with no canvas and no device panel**
— the exact "audio-with-no-picture" signature `CLAUDE.md` documents for a
missing `viewID`, arriving from a completely different cause. Worth stating in
advance so nobody spends a day in `visualCore`.

*This is read off the code and needs confirming on hardware.* But the
source-port half of it is already a known, documented constraint:
`AirConcert:code3.0/API.md` states it as a rule — "Devices are keyed by sender
source port + (N−1): senders must keep a stable source port" — and records that
the CotF router binds a **fixed source port 9001**, addressing seats as
9001..9005. So the fix has been found once already: pin PyOSCCam's send socket
to a fixed source port, and add a replay registration path that skips the config
handshake rather than teaching the handshake to tolerate a silent peer.

This is a good illustration of §11's point. The constraint is real, it is
written down, and it is written down somewhere this branch does not read.

### Should it run on the AirKit Pi?

The plan was to deploy it alongside AirKit. I would not, for the school build:

- `PERFORMANCE_TUNING.md` documents a Pi 5 whose four cores are already
  allocated — NIC on 0, audio on 1–3 — because getting that wrong broke the
  access point. A camera thread plus an H.264 encoder plus disk writes has
  nowhere to go that is not audio or WiFi.
- PyOSCCam's own `rpi5_video_osc_plan.md` says "SD card I/O causes frame drops —
  USB SSD required for reliable recording". That is another cable and another
  failure mode in a classroom kit.
- It makes §10 Q1 a harder question than it was. If Pi headroom is measured, it
  should be measured *without* video, because the school configuration should
  not have video.

**Recommendation: PyOSCCam is a second box.** Fed by `oscThru` over the AirKit
AP, it records the session without touching the appliance at all — which is the
same containment principle CotF applied (§15) and the same two-speed release
argument as §6. If it must be co-resident, that is a research-channel build.

### The reason to do it anyway

The classroom value of PyOSCCam is modest. The **engineering** value is large,
and it is the thing this project is currently missing:

> RULE ZERO says never change the sound. There is at present no way to *prove*
> the sound did not change.

A recorded `.jsonl` is a deterministic gesture input. Replay one session against
a personality before and after an edit, against firmware at 100 Hz and 200 Hz,
against the old sensor derivation and a new one — and the question "did that
refactor retune 226 instruments?" becomes measurable instead of a matter of
nerve. Every additive-only warning in §4 exists because there is no regression
test. This is the regression test.

That argues for prioritising the **playback** path over the video path, and for
a small corpus of committed reference recordings — one per sound architecture —
rather than a video archive.

---

## 15. Gesture profiles and engagement

### CotF already built this, and the design document is the template

`CotF/docs/superpowers/specs/2026-07-20-research-data-collection-design.md`
specifies, and August shipped, a continuous movement-and-survey research
pipeline running under a live show. Its architecture:

```
AirSticks ─UDP─▶ router ─▶ AirKit                    (show path, unchanged)
                    └─ flag-gated tee ─▶ 127.0.0.1:950{2,3}
                                              └─▶ separate process ─▶ separate SQLite
```

The constraint it opens with is the one to inherit verbatim:

> Nothing in this subsystem may crash, lag, block, or otherwise perturb the show.

Concretely: one extra localhost `send()` of an already-composed buffer in the
hot path, default off, error handling is a counter increment. All decode and all
computation in another process, on another database, which the show code never
opens. No alerts, no preflight entry, nothing operationally load-bearing. Plus a
runtime kill switch an operator can hit mid-show without a restart.

Its feature battery — the eight to aim at:

| | Feature |
|---|---|
| 1 | RMS linear acceleration |
| 2 | RMS angular speed (quaternion successive-difference axis-angle / Δt) |
| 3 | Active movement ratio |
| 4 | Submovement rate (smoothed peak count per minute) |
| 5 | Rotational smoothness (SPARC) |
| 6 | Translational smoothness (LDLJ-A) |
| 7 | Orientation coverage entropy |
| 8 | Rotation / translation ratio |

Each stored as a whole-phase value plus median and IQR across 5 s windows at
50 % overlap, with QC columns (`sample_count`, `expected_sample_count`,
`max_gap_ms`, `gap_count_over_100ms`, `phase_duration_s`) that are recorded but
never used to exclude at write time. No raw movement data is kept. Identity is
an HMAC pseudonym so a post-show survey can be joined without the research store
holding a name.

Two things follow for the product. First, §10 Q5 — "is there an evidence
requirement?" — has an answer and a machine that produces it. Second, the ethics
model, the survey instrument and the feature definitions are done work with a
paper behind them; a school-facing version is a port, not a research project.

### The shape a placeholder in AirKit should take

**No code written. This is the design, for a later decision.** Proposed as
`code3.0/sessionProfile.scd`, Required from `main.sc` alongside the other
modules, **off by default** — so an unset flag means it is inert, not merely
idle.

- Samples `~devices` on its own clock at ~20 Hz. Reads only; writes nothing
  back. Touches no SynthDef, no mapping, no `~plot`, no `~next`. RULE ZERO is
  not at risk because the module has no path to the sound.
- Accumulates running sums per device, so memory is constant regardless of
  session length and no raw movement data is retained — CotF's "features only,
  no raw archive" decision, for the same reason.
- On stop, writes one JSONL row per device to `~/AirKitSessions/`:
  `rmsAccel`, `accelPeak`, `rmsAngSpeed`, `angSpeedPeak`, `activeRatio`,
  `rotTransRatio`, plus `personality`, `label`, `durationSecs`, `sampleCount`
  and `expectedSampleCount`.
- Control from sclang, and over OSC so the web UI (§13) can drive it:

```supercollider
~sessionProfile.enabled = true;
~sessionProfile.startSession("glenroy-w1");
~sessionProfile.stopSession;
```
```
/airkit/profile/enable 1
/airkit/profile/start "glenroy-w1"
/airkit/profile/stop
/airkit/profile/state   ->  /airkit/profile/state/reply
```

That is four of CotF's eight features — the ones that fall out of running sums.
The other four need a retained window and would be deliberately absent from a
first cut.

Its output directory is the one thing here that should not be invented: it is a
per-kit path, so it belongs as a key on the machine file (§16) alongside the
other paths, not as a literal in the module.

Two house rules it has to respect, both cheap to get wrong: functions on the
returned Event take `self` as their first argument (`core.startSession = {
|self, label| … }`, per `visualCore.scd:313`), and `stop` cannot be used as a
key because it is a real method on Object — sclang warns and then calls the
method instead. Use `OSCdef`, not `OSCFunc`, so re-running `main.sc` replaces
the responders rather than stacking them.

**`engagement` in that row would be a stub.** The active-movement ratio under
another name, present only so the field exists and call sites can be written
against it. It would have no research behind it and must not be reported as a
measure. CotF pointedly does *not* compute a single engagement number; it
computes eight descriptors and leaves interpretation to analysis. That is the
right instinct, and the pressure to abandon it will come from buyers, not from
researchers.

### What it would deliberately not do yet

- **It polls, rather than tees.** Sampling `~devices` at 20 Hz sees a decimated
  view of a 100 Hz stream and cannot see the gaps — so `max_gap_ms`, the QC
  column that tells you whether a stick dropped off the AP mid-session, is not
  computable from it. The honest version tees the OSC the way CotF does. Polling
  is the placeholder; teeing is the design.
- **It has no notion of a person.** CotF got identity from its kiosk. A school
  kit has no equivalent, so a "who is playing" selector has to come from the web
  UI (§13) before a profile means anything beyond "this device, this session".
- **It has no session boundary of its own.** Start and stop are manual. Phase
  segmentation in CotF came from polling room occupancy; here the natural
  boundary is probably personality load, or a teacher pressing a button.

### Order of work

1. Write it, then run it on hardware and check the cost is nil. It is 20 Hz of
   Event reads on the language thread, which is a fraction of what `~next`
   already does at 100 Hz — but that is a prediction, not a measurement, and the
   language thread is the one that also runs the draw loop.
2. Replace polling with an OSC tee.
3. Port the four window-based features (SPARC, LDLJ-A, entropy, submovement
   rate) — or move computation out of sclang entirely, which is the CotF answer
   and is what PyOSCCam's `.jsonl` format is already shaped for.
4. Decide identity, with the web UI.
5. Decide what is shown to a teacher, and resist showing a single number.

---

## 16. Amendments to Part I

| § | Claim in Part I | Correction |
|---|---|---|
| §1 | "No fleet view" | CotF has one, in production. And the sticks already report firmware version, ID, IP and frame delay on every connect — AirKit discards them. |
| §4 | "the handshake exists, it just carries almost nothing" | Wrong. `sendConfig` carries eighteen values. `oscController.scd:355` reads the last four. |
| §4 | "the OSC layer is not the problem" | Still right, and better evidenced: four non-IMU sensor modules are already written and shipping. |
| §3, §5 | "move the control GUI to the browser" | Most of the OSC API for it exists — in a fork outside this repository. |
| §6 | "29 remote branches" | Understates it — there are two firmware trees as well. But `origin/AirConcert` is a deliberate separation, not drift: it is a production fork and it stays separate (§11). **The branch count is no longer the config problem** either — see the config row below. What remains is repertoire divergence, and an OSC contract that diverged invisibly; `configPlan.md` never claimed to fix either. |
| §7 | Phase 1 move 8, "measure Pi headroom" | Must now also settle the PyOSCCam question (§14) and be measured against the documented WiFi-IRQ/audio balance, not in isolation. |
| §10 | Q2, "is 100 Hz musical or a headroom artefact?" | Neither. It is `reportIntervalUs = 10000` at `BNO085.h:18` (a **firmware** repo, not this one — see §11), a sensor default. Changing it is one line, and `optimize_report.md` has the measurement plan. §3 has been corrected to defer to this. |

**Config, added 2026-09-10.** `configPlan.md` was implemented between Part I
and this section, so several Part I claims about it are now historical:

| § | Claim in Part I | Correction |
|---|---|---|
| §1 | "No configuration story" | Implemented. `machines/<kit>.scd`, one tracked file per unit, resolved at boot by the kit's own AP address. Ships in the release, so it is versioned and rolls back with the code. |
| §3 | "`configPlan.md` already **proposes** the mechanism that makes the host swappable" | It is built. `machine.scd` defaults `latency`, `border` and `fullScreen` per platform, so a new host needs a machine file, not a port. |
| §5 | "Lists need to move out of `personalityController.scd`" | Done — the `list` key, String or per-device Array. |
| §6 | "`configPlan.md` is the fix and it is **written and unimplemented**" | Implemented, steps 1–3. Steps 4–5 (fold `AirKitDesktop` in, retire the machine branches) are open. `AirConcert` is excluded by design. |
| §7 | Phase 0 move 2, "Implement `configPlan.md`" | Landed. **Not yet booted on a Pi** — that is the outstanding risk, and it is the step that can break boot. |

Two things this did **not** fix, worth stating so nobody assumes otherwise:

- **Repertoire divergence.** Config no longer differs per branch; p-files still
  do. The `AirConcert` p-files named in `gatePlan.md` still do not exist here,
  and §16's fork count makes that worse, not better.
- **The fleet still cannot be seen.** The machine file names each kit and posts
  it at boot, so the *data* exists — but nothing collects it.

---

## 17. Revised move list

Part I's Phases 0–4 stand. These insert into them.

*Phase 0 move 2, "Implement `configPlan.md`", has since landed — see §16. It
still needs booting on a Pi before it counts as finished. Phase 0 move 4,
retiring the machine branches, is unblocked by it.*

**Into Phase 0**

0. **Adopt an OSC contract file**, on AirConcert's rules: one source of truth,
   updated in the same commit as any OSC change, additive only, deployment
   specifics tagged. `code3.0/API.md` documents five addresses against at least
   fifteen that exist. AirConcert is not merged and does not become a
   dependency — only its contract discipline crosses over (§11).
1. Parse the full `/Config` reply into `d.config`. Half a day; unblocks fleet
   reporting for the sticks.
2. Drop `reportIntervalUs` to `5000` and add a max-inter-packet-gap counter.
   Modem sleep is already off (§12), so this is the remaining half of the rate
   work. Measure before and after.

**Into Phase 1**

3. Port the `/airstick/{id}/heartbeat` module from the CotF firmware tree —
   battery, RSSI, charging, with the `-1` sentinel. Self-contained header, no
   CotF dependency.
4. Point `AUDIO_DIR` at something. Introduce `~samples.()`, template first, no
   flag day.

**Into Phase 2**

5. Capability field in `sendConfig`; runtime module enables where the pins allow.
6. `Air_I2CIn.h`'s structure — derived rate, health check, offline-on-fault —
   as the standing pattern for new sensor modules.

**Into Phase 3**

7. Teacher UI in the existing Bottle server: lists, personality select, volume
   ceiling, calibrate, panic, who-is-playing.
8. PyOSCCam on a second box, fed by `oscThru`; commit a small corpus of
   reference recordings and use replay as the regression test for RULE ZERO.
9. Build the session-profile module if §15 is wanted — poll first, then tee,
   then the four window features.

**Hardware test matrix** — everything above needs running on all of it, because
each has been the thing that broke something before: Pi 5 kit (AP + audio +
HDMI visuals), desktop/laptop AirKit, Mac mini, AirStick fw 0.4, the CotF
firmware fork, and at least two sticks at once.
