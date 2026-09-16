# configPlan.md

A plan for replacing per-machine **branches** with per-machine **config files**.

Written 2026-08-11, on `Airsticks-RPI`.

Status, 2026-09-10: **implemented** on `Airsticks-RPI`, migration steps 1–3.
`machines/` holds one file per unit, `code3.0/machine.scd` resolves the right
one at boot from the kit's own AP address, the four core files read it, and
`machines/` ships in the release. Steps 4–5 — folding `AirKitDesktop` in and
retiring the machine branches — are not done. `AirConcert` is deliberately out
of scope: a different output with its own UI and mechanism, not a machine
variant.

**Not yet booted on a Pi.** See *Outstanding* under Migration order.

The delta table below is the **2026-08-11 measurement and is now stale** — see
the note under it for what actually differed on the day of implementation.

---

## The decision this rests on

`Airsticks-RPI` is now the single branch every AirKit gets. Branches stop being
the way machines differ; a config file becomes the way machines differ.

---

## Why

Several AirKit units need different settings, and until now each unit had its
own branch. Two merges in one day showed what that costs.

**Merge 1 — `AirKitDesktop` → `Airsticks-RPI` (landed, `ebc8fab`).**
Brought the new visual core to the Pi. Divergence was ~40 commits each side,
162 files. Of that, exactly **three core files** needed hand-resolution and one
file — `oscController.scd` — had to be **force-kept** from the RPI side because
git reported *no conflict* on it at all: only desktop had touched it, so
desktop's version would have been taken silently, replacing the OSC device-add
handshake and the airstick colour indices.

**Merge 2 — `Airsticks-RPI` → `AirKitDesktop` (attempted, aborted).**
Worse. Because merge 1 absorbed all of desktop's history into RPI, desktop is
now a full **ancestor** of RPI — not divergent from it, just behind. So the
merge had nothing to reconcile and reported *"Automatic merge went well"* with
zero conflicts, while handing desktop RPI's entire tree. Desktop would have
silently lost every one of its platform values: booting borderless-fullscreen
and talking to the wrong IP, with nothing in the output to say why.

That is the real argument. A conflict stops and asks. This does not.

---

## The actual per-machine delta

Measured, not guessed — this is the complete set of values that differ between
`Airsticks-RPI` (`1a3dd17`) and `AirKitDesktop` (`153c813`).

*The `file:line` references in this table are as of 2026-08-11 and no longer
resolve — `personalityController.scd` has since lost 43 lines off the top. They
are kept as the historical record; the re-measured table below is the current
one.*

| # | Value | File | RPI | Desktop |
|---|---|---|---|---|
| 1 | window border / fullScreen | `main.sc:74,78` | `border: false`, `.fullScreen` | `border: true`, no fullScreen |
| 2 | `outAddr` | `oscController.scd:9` | `192.168.100.40:3333` | `192.168.70.211:5005` |
| 3 | config colour indices | `oscController.scd:353` | `[15,16,17]` | `[16,17,18]` |
| 4 | `list` (roster) | `personalityController.scd:16` | `list_yourDNA26.sc` | `list_workshop1.sc` |
| 5 | `~secs` (routine rate) | `personalityController.scd:187` | `0.01` | `0.03` |

Five values. That is the entire permanent difference between a Pi and a laptop.

**Re-measured 2026-09-10, at implementation time.** Three of the five had
already been fixed on `Airsticks-RPI` and two new ones had appeared, so the
machine files carry this set instead:

| # | Value | 2026-08-11 | 2026-09-10 |
|---|---|---|---|
| 1 | window border / fullScreen | divergent | **fixed** — `main.sc` had become a `Platform.case`; now a config key so a Pi on a dev monitor can have a border |
| 2 | `outAddr` | divergent | divergent — `192.168.50.53` vs `192.168.70.211`, both port 5005 |
| 3 | config colour indices | divergent | **gone** — `oscController.scd:355` now reads `msg2.keep(-4)`, no indices |
| 4 | `list` (roster) | divergent | divergent — `list_glenroy.sc` vs `list_alon26.sc` |
| 5 | `~secs` | `0.01` vs `0.03` | **converged** on `0.01`; kept as a key anyway |
| 6 | `numAirwareVirtualDevices` | not measured | **new** — 9 vs 5, and it must equal the roster count |
| 7 | `s.latency` | not measured | **new** — `0.1` vs `0.03` |

Item 6 is the one that would have bitten. It was never in the original table,
it is a silent-clobber value exactly like item 3, and it has to stay in step
with the roster length or `defaultLists[i]` returns nil for the high devices.
It is therefore a single `\numDevices` key that drives both.

Two things that are *not* in the table, and are worth knowing:

- **`listsDir` / `personalityDir` are already identical** on both branches
  (`~/Develop/SuperCollider/Projects/AirKit/`). They are not currently a
  difference. They should still move to config, because the commented-out
  alternatives at the top of `personalityController.scd` show they have been
  one before, and will be again on a machine that clones elsewhere.
  *Resolved better than proposed: they are now **derived** from the checkout
  location, so they need no configuring at all.*
- **`VERSION` path is a hardcoded absolute** in `systemView.scd`. It happens to
  match on both machines, so it is not a divergence today — but it throws on
  any machine whose checkout is elsewhere. *Now derived; `systemView.scd:160`.*

Item 3 deserves emphasis: `[15,16,17]` vs `[16,17,18]` is an **airstick
firmware** difference, not a preference. It is per-hardware, and it is the kind
of thing that gets silently clobbered and then debugged for an hour as "the
colours are wrong".

---

## Why not an untracked local file

The obvious design is a gitignored `local.scd` per machine. It was rejected:
if config is not in the repo, a freshly imaged Pi clones and has nothing, the
settings are not backed up, and they cannot be reviewed or diffed.

**The collisions were never caused by config being tracked.** They were caused
by per-machine values living in files that *every* machine edits — `main.sc`,
`oscController.scd`, `personalityController.scd`. Two machines editing one
shared file is what git has to reconcile.

Give each machine its own file and there is nothing to reconcile. AirKit2 only
ever edits `machines/airkit2.scd`; the laptop only ever edits
`machines/desktop.scd`. Different files never conflict, however many branches
exist.

So: **tracked, in the repo, one file per machine.**

**And shipped.** `machines/` is in `release_dirs`, so config travels with the
release: it is versioned, reviewable, and rolls back with the code it was
tested against. Leaving it out of the zip was considered and rejected — it
would recreate this same argument one layer down, where a freshly imaged Pi
gets a core that is looking for a file the zip does not carry. See
**Interaction with AirKitWebApp** below.

---

## The design

### `machines/*.scd`

One small tracked file per kit, returning an Event. As built, one per unit in
the fleet:

```
machines/
  airkit1.scd      AirKit1  MiM    192.168.100.1
  airkit2.scd      AirKit2  Mel    192.168.200.1
  airkit3.scd      AirKit3  Alon   192.168.150.1
  airkit4.scd      AirKit4  Nic    192.168.50.1
  development.scd  AirKitDevelopment  Steph  192.168.70.1
  desktop.scd      the laptop, identified by hostname
```

Each holds the per-machine values plus its own identifiers, `user`, and the
derived paths. **Glenroy is not here**: it has no network and no unit, and
`list_glenroy.sc` already exists — it is a *roster*, not a machine. That
distinction is the point of the next paragraph.

Two fields matter conceptually and stay **separate**:

- **platform** — window, `secs`, `latency`, `outAddr`, paths
- **roster** — which `lists/*.sc` to load

Keeping them separate is what makes "Alon reviews Mel's set" work: Alon's file
points at Mel's roster while keeping Alon's own platform settings. Collapse
them into one opaque "kit" and loading Mel's kit drags Mel's platform too,
which is the coupling branches already gave us.

### Rosters already exist

`lists/*.sc` is the roster mechanism, working today — 20+ files, formerly
selected by hand-editing `var list =` in `personalityController.scd`. The plan
does not introduce rosters; it moves the *selection* out of a shared file.

### Selection — automatic, no pointer file

**Resolved 2026-09-10.** The open question was that this laptop reports
`MU00157721X` (an institution-managed asset name that may change on reimage)
and the Pis' hostnames were unknown. The answer turned out not to be hostnames
at all: **each Pi is the router on its own AP network at a fixed `.1`**, and
those addresses are already assigned and documented. That is a stronger
identifier than a hostname *and* than a MAC — it survives a board swap, and it
required no field trip to collect.

The ladder in `code3.0/machine.scd`, in order:

| # | Signal | Why this order |
|---|---|---|
| 1 | `apAddr` | the kit's own AP address, read from `hostname -I`. Fixed, documented, survives a board swap |
| 2 | `macs` | hardware addresses, for a machine with no AP |
| 3 | `hostnames` | for a machine that is not a router — the laptop |
| 4 | platform | the single file claiming this `platform`, if exactly one exists |
| 5 | — | nothing matched: a loud block, then built-in defaults |

Each machine file declares **its own** identifiers, so there is still no shared
machine→identity table for two kits to conflict over. That property is what the
whole plan rests on, and it survives adding two more signals.

There is **no `CURRENT` pointer file.** An earlier draft had one as step 0; it
was removed for three reasons, any one of which is sufficient:

- Being gitignored, it could not ship, be reviewed, or roll back — the exact
  three objections this document raises against `local.scd` above.
- Once `machines/` ships, it lives inside a directory the updater `rmtree`s on
  every update, so it would be silently deleted by the first web update.
  Shipping `machines/` and keeping `CURRENT` are incompatible.
- Its only real use was "temporarily pretend to be another kit" — a dev
  affordance, not a deployment mechanism.

Step 5 fails loudly but does **not** throw: an unrecognised Pi still boots and
still makes sound, and says why it is unsure. Throwing would brick a unit
beyond the reach of the web rollback, which `gatePlan.md` argues against for
class files and which applies just as well here. The block prints the
machine's own addresses, hostname and MACs — so the error message hands you
the value to paste into a machine file.

### A trap worth recording

The AP match silently never fired at first. **`Collection.includes` in
SuperCollider compares by identity**, so it is always false for Strings; the
lookup never matched and selection fell through to the hostname rule, looking
entirely plausible. The fix is `includesEqual`. Parse-checking cannot catch
this — only running it can.

---

## What changes in the core

Four files stop hardcoding and start reading:

| File | Change |
|---|---|
| `main.sc` | load the machine file; window `border` / `fullScreen` and `s.latency` from it |
| `oscController.scd` | `outAddr` and `numDevices` from it |
| `personalityController.scd` | `listsDir`, `personalityDir`, roster, `~secs` from it |
| `systemView.scd` | `VERSION` path from it |

One logical change. No new mechanism beyond "read a file at boot".

The colour indices are absent from this table because they stopped being a
per-machine value before implementation — `oscController.scd:355` now reads
`msg2.keep(-4)` and takes the colour off the end of the message regardless of
firmware offset.

Paths are **derived**, not configured: `machine.scd` computes the repo root
from `thisProcess.nowExecutingPath.dirname.dirname`, so a clone anywhere works
and no machine file needs to mention `~/Develop`. The keys exist so a machine
*can* override them, but none does.

One consequence to know: `Require` clears its cache on each top-level call, so
`machine.scd` is re-evaluated once per root `Require` in `main.sc` — three or
four times at boot, hence the repeated `[machine]` line. Harmless for
read-only config (the values are identical and nothing mutates them), but it
means a Require'd file is **not** a singleton. Never keep mutable shared state
there.

---

## Migration order

1. ~~Add `machines/`, carrying the exact values in the table. Nothing reads
   them yet.~~ **Done** — plus `code3.0/machine.scd`, the loader.
2. ~~Point the four core files at the machine file.~~ **Done, but only verified
   on macOS.** Parse-checked; the loader and its four consumers were evaluated
   under `sclang`, and all four identification paths were exercised. The Pi
   boot is still untested and is the step that can break boot.
3. ~~Add a file per kit.~~ **Done** — `airkit1`–`airkit4`, `development`,
   `desktop`. Brought forward from step 4 because the AP addresses arrived
   early and there was no reason to hold them.
4. **Then** merge `Airsticks-RPI` into `AirKitDesktop`, or fold `AirKitDesktop`
   into `Airsticks-RPI` and make that the main branch. Safe at this point,
   because the shared files no longer contain anything machine-specific. This
   is also what stops the 17 p-file conflicts from merge 1 recurring.
5. Retire `Airsticks-RPI-Mel` and `AirKitDesktop` as machine branches.
   `AirConcert` is **not** retired — it is a different output with its own UI
   and mechanism, not a machine variant.

The merge must not happen before step 2. Attempting it early is what was
aborted on 2026-08-11, and it fails silently.

### Outstanding before this is finished

- **Boot one Pi.** Specifically, confirm `hostname -I` reports the AP `.1`.
  It should, since the Pi holds that address as router — but it is unverified,
  and with five Linux files there is no single-file platform fallback any more,
  so a Pi that fails to match lands on defaults.
- **Rosters.** `list` is commented out in all five kit files, so they fall back
  to `list_dev.sc` and say so on the `[machine]` line at boot. Repertoire was
  not guessed.
- **`numDevices` is 9 on every kit**, inherited from the old shared value
  rather than known per unit. Too high is harmless (spare OSC listeners); too
  low means devices never connect — so 9 errs the safe way, but it wants
  confirming.

---

## What it enables

- **Alon is a file, not a branch.** `machines/airkit3.scd` is a handful of
  lines naming a roster. No branch, no merges, no ongoing cost.
- **Cross-loading repertoire.** Alon can run Mel's set by pointing at Mel's
  roster, without inheriting Mel's platform settings.
- **Merges stop being dangerous in both directions**, including the silent one.
- **Freezing a kit for a run of gigs** becomes a tag, not a branch — the
  machine file travels with the tag, because `machines/` is in the release.
  There are already tags `v0.0.3`–`v0.0.7` and a workflow that fires on `v*`.
- **A new kit is a file, not a provisioning ritual.** Add its AP address to a
  new `machines/*.scd`, tag, and the unit identifies itself on first boot.
- **`user` travels with the config** — it is on every machine file and appears
  in the boot log, so a log line says which kit produced it. Config and logging
  only; no view reads it.

---

## Interaction with AirKitWebApp (the updater)

Checked 2026-09-10 against `~/Develop/Web/Projects/AirKitWebApp` @ `6979644`.
**No change is needed in the updater.**

`release.config.json` ships `machines`, `code3.0`, `personalities`, `lists` and
a generated `VERSION` (`.github/workflows/release.yml`), and
`engine.py:107-171` backs up, `rmtree`s and replaces **only the top-level
directories actually present in the zip** — "everything else in APP_DIR
(`.git`, `synths`, `analysis`, …) is left untouched." So adding `machines` to
the list was sufficient on its own; the updater needed no knowledge of it.

**`machines` is in `release_dirs`, so config ships with the code.** An update
backs up, replaces and rolls back the machine files exactly as it does
`code3.0` — no updater change was needed, because `engine.py` derives its
targets from the zip's own top-level dirs rather than a hardcoded list.

An earlier draft of this section argued the opposite — leave `machines/` out
of the zip so a kit's settings survive updates. That was wrong, and worth
recording why:

- It recreates the `local.scd` objection one layer down. Config that does not
  ship is config a freshly imaged Pi does not have, cannot be rolled out, and
  cannot be reviewed.
- Its mitigation was "place the file on each Pi once by hand" — an SSH step in
  a system built specifically to update kits without SSH. That does not scale
  to the fleet this is heading for.
- It makes rollback incoherent. `engine.py:218` restores every backed-up dir,
  so shipping config means a rollback returns the kit to a matching code+config
  pair. Not shipping it means old code runs against whatever config is present.
- It breaks a stated goal of this document: *"freezing a kit for a run of gigs
  becomes a tag"* only works if the machine file actually travels with the tag.

The concern behind it was real but misaimed. One file was holding two kinds of
value: fleet-authored (roster, device count, timing — which *should* be updated
by a release) and kit-local (`outAddr`, identifiers). A layered
`machines.local/` was designed for that split and then dropped, because
`outAddr` turned out to be near-dormant — see the next section — which left
nothing a release should not overwrite.

Three continuity checks, all fine:

- `APP_DIR` is `/home/pi/Develop/SuperCollider/Projects/AirKit`
  (`config/settings.py:13`) — the same layout as the Mac, so `machine.scd`'s
  `dirname.dirname` lands on the repo root and `machines/` sits beside
  `code3.0/`. The derived `listsDir` / `personalityDir` / `versionPath` come
  out identical to the old hardcoded `~/Develop/...` strings, because `~` is
  `/home/pi` there. Nothing moves.
- **`VERSION` still works.** The zip extracts it to the APP_DIR root and
  `systemView.scd` now reads `<root>/VERSION` — the same file. The updater
  keeps owning the version display.
- **Restart re-reads config.** `_launch_startup_script()` (`app.py:328`) runs
  `/home/pi/start_airkit.sh`, a full sclang relaunch, so `main.sc` re-`Require`s
  `machine.scd`. Config is boot-time and the updater's restart is a real
  restart.

The updater reads nothing else that changed — no roster, no
`personalityController.scd`, no `machines/`. `release.config.json` also exists
as a copy in the AirKitWebApp repo; the GitHub Action reads the **AirKit** one,
so the copy is a template and the two can drift.

---

## `outAddr` is smaller than the table implies

Worth recording, because it changed two design decisions.

`outAddr` appears exactly once in the live path — `oscController.scd:407`,
inside `if(oscThru, ...)`. `oscThru` defaults to `false` and is toggled at
runtime by a button in `systemView.scd:121`. So it is a dormant OSC-forwarding
destination, not a live per-site setting, and it was carrying far more weight
in the original delta table than it deserved.

That is what removed the case for a local-override layer: with `outAddr`
demoted, the only genuinely per-kit values left are `list` and `numDevices` —
whose kit this is and what it plays — and both of those are things a release
*should* be able to update.

**A caveat, unrelated to this plan.** Each Pi is the router on its own AP
network, so an OSC-thru target is a *client* on that network holding a dynamic
lease. The recorded values decode accordingly: `192.168.50.53` is a client on
AirKit4's network, `192.168.70.211` one on AirKitDevelopment's. So a hardcoded
`outAddr` is unreliable by construction whenever someone does press that
button. That is pre-existing and was not introduced here, but if OSC-thru is
ever used in earnest it needs a discovery mechanism rather than a constant.

### AirStick identity needs nothing

For the same reason, sticks do **not** need MAC mapping. `startOSCListening`
(`oscController.scd:397-415`) listens on `/1/…` … `/N/…` and takes the device
index from **the pattern the stick sends on**, then reads `addr.ip` off the
incoming packet:

```supercollider
var d = addDevice.(addr.ip, addr.port+i, i+1);
```

Dynamic stick addresses are already a non-issue — nothing ever needs to know a
stick's IP in advance, and its colour comes back from its own `/Config` reply.
Adding a MAC→device table would replace a working self-describing scheme with
one to maintain. The only per-kit value here is `numDevices`, how many slots to
listen on, and that is in the machine file.

---

## Loose ends

- `VERSION` currently reads `0.0.5` while the latest tag is `v0.0.7`, and
  `systemView.scd` displays that file on the Pi — so kits are reporting the
  wrong version today. Worth fixing whenever the next tag is cut.
- `origin/merge-desk-into-rpi` is a leftover branch label pointing at the same
  commit as `Airsticks-RPI`. Safe to delete once any Pi still tracking it has
  been switched over.
- `AirKitDesktop` still holds stripped versions of 17 p-files (visuals deleted
  from `mel1`/`silence`, `funBass` muted to amp `0.01`, an empty `((0)!0 ++ sig)`
  channel scaffold in `mel1`). The merge in step 4 resolves this by giving
  desktop RPI's versions.
- `AirKitDesktop` also holds `code2.0/`, `code1.0/`,
  `sc_osx_standalone-3.7.0-template/` and `airstickTemplate.sc`, which do not
  exist on this branch. They return with the merge; `README.md` says so.
- The `[machine]` line is posted three or four times at boot, once per root
  `Require` that reaches `machine.scd`. Cosmetic. Silencing it would mean
  caching across evaluations, which would stop a re-run of `main.sc` re-reading
  config — a worse trade.
