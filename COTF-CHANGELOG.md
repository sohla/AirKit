# COTF changes — plain-language log for Steph

One entry per push batch from the Concerts of the Future side, newest first: what we changed,
why, and what (if anything) behaves differently on your machine. The intent is that nothing
here ever changes how AirKit behaves for you — if it does, that's a bug, tell us.

## 2026-11-04 — demo profile for the SensiLab demo, on branch `feat/sensilab-demo` (cotf)

**Nothing changes for you, and nothing changes on AirConcert** — this lives on its own branch.
Two new files, `cotf/config_demo.scd` and `cotf/main_demo.scd`: a copy of the show profile for
an off-season demo on ordinary speakers. Each seat is summed to mono and placed across three
speakers (left / centre / right) with a new message, `/airkit/seatPan <seat> <-1..1> [fade]`,
and a limiter sits on those outputs. The output device and which outputs are L/C/R come from
env (`DEMO_OUT_DEVICE`, `DEMO_NUM_OUT`, `DEMO_OUT_L/C/R`). `/airkit/outputMode` does nothing
there (it logs that it was ignored). The show files (`main_cotf.scd`, `config.scd`), the shared
controllers and your personalities are untouched; API.md has the new row.
Later the same day: the demo limiter's lookahead went from 10 ms to 3 ms (it delays the sound by twice
that, so ~6 ms instead of ~20 ms); demo profile only.

## 2026-08-31 — STRINGCHORD up +2 dB in the Room 3 trim table (cotf)

**Nothing changes for you.** One `~cotfPatchTrims` value in `cotf/main_cotf.scd` only
(Room 3 show loudness, Ciaran ear-check from the 08-28 groups, applied at season close):
STRINGCHORD -1 -> +1 dB. No personality files touched, no roster change.

## 2026-08-27 — STRINGCHORD and BIRDY up +3 dB each in the Room 3 trim table (cotf)

**Nothing changes for you.** Two `~cotfPatchTrims` values in `cotf/main_cotf.scd` only
(Room 3 show loudness, ear-tuned by Ciaran during today's groups): STRINGCHORD -4 → -1 dB,
BIRDY -3 → 0 dB. No personality files touched, no roster change.

## 2026-08-26 — WINDY into the live pool: header keys, -4 dB trim, roster BIRDY dedupe (cotf)

**One small behaviour change on your side: WINDY gets a `~cotfPatchTrims` entry (-4 dB)** in
`cotf/main_cotf.scd`, so it enters the Room 3 pool padded like the other 08-25 newcomers
(Room 2/composer behaviour unchanged — the trim table is Room 3 only). Also: added the
M0-consumed super-seats header keys to `personalities/WINDY.sc` (seats 1-5, wind/noise/white
noise affinity words, register wild, family wind — header comment block only, zero code
touched), and removed the duplicate `"BIRDY"` line your push added to `lists/list_cotf.sc`
(it was already rostered a few lines up; your new WINDY entry stays). With this, WINDY and
your BIRDY fixes go live to real audiences at the next between-groups pull on M1.

## 2026-08-25 — trims re-tune + four patches into the live pool + affinity word pass (cotf)

**One behaviour change on your side to know about: `~cotfPatchTrims` in `cotf/main_cotf.scd` is
re-tuned** (Ciaran, by ear, mid-run): BASSBUZZ +7.5 -> +6.5 dB, JUPITERSHARP +5 -> +8,
ALTOSYNTH +6 -> +2, and new padded entries for STRINGCHORD (-4), BIRDY (-3), PLUKSYNTH (-3),
WHIPBIRD (-2). PLUKSYNTH and WHIPBIRD are added to `lists/list_cotf.sc` and, with STRINGCHORD
and BIRDY, are now in the open super-seats pool on M0. Header comment blocks got an
affinity-word pass (audience-word matching only — no code): BASSBUZZ/JUPITERSHARP/SOPRANOVOICE
gained words; PLUKSYNTH dropped harp/harpsichord (owned by JUPITERSHARP/STRINGCHORD) and gained
chime/bells words; WHIPBIRD's list was re-themed to pipes/pan pipes/flute/gentle and its
register set to traditional, so BIRDY exclusively catches bird/whistle words. RainKeys and
WindVoice (still audition-only) now self-pad -7 / -3 dB in-file.

## 2026-08-25 — STRINGCHORD header keys + BIRDY/STRINGCHORD audition-gated on M0 (cotf)

**Nothing changes on your machine.** Added the M0-consumed super-seats header keys
(`prints/seats/affinity/register/family`) to `personalities/STRINGCHORD.sc` and moved your
technical description line to `internals:` (kept verbatim) with an audience-legible
`description:` in its place — header comment block only, zero code touched. On the M0 side,
BIRDY and STRINGCHORD are admin-disabled for the super-seats auto-pick (same audition gate as
Birdsong/RainKeys/WindVoice), so they reach real audiences via staff hand-assignment first and
graduate to the open pool when Ciaran flips them on. PLUKSYNTH and WHIPBIRD are left exactly
as you pushed them (not in `list_cotf.sc`, so not loadable yet).

## 2026-08-25 — RainKeys + WindVoice: two more Ciaran audition patches (cotf)

**Nothing changes on your machine.** Two new personalities Ciaran wrote and auditioned in the
COTF Patch Lab, added to `lists/list_cotf.sc` (before the trailing `silence`) and
admin-disabled for the super-seats auto-pick on M0 — no audience member gets them unless a
staff member hand-assigns one for an audition group. `RainKeys` is JUPITERSHARP's harp engine
(reads your `~/Music/cotf_samples/harp` library, own SynthDefs `\labRainPluck` /
`\labRainWeather`) inside a synthesised rain field; `WindVoice` is SOPRANOVOICE's two voice
engines (reads `voice/aah.wav`, own SynthDefs `\labWindPad` / `\labWindNote` / `\labWindAir`)
inside a synthesised wind field. Env-unique Pdefs/event types, no shared names touched, core
UGens only. Not Steph patches; leave as is, or tell Ciaran if they get in your way.

## 2026-08-23 — Birdsong: Ciaran's TEST personality for widening the pool (cotf)

**Nothing changes on your machine.** `personalities/Birdsong.sc` is a new, fully synthesised
(no samples) blackbird personality Ciaran wrote and auditioned in the COTF Patch Lab, added to
`lists/list_cotf.sc` (before the trailing `silence`) as a test patch for widening the pool.
It is admin-disabled for the super-seats auto-pick on M0, so no audience member gets it unless
a staff member hand-assigns it to a seat for an audition group. It defines only its own
SynthDef (`\labBirdChirp`) and an env-unique Pdef/event type — no shared names touched.
Not a Steph patch; please leave it as is, or tell Ciaran if it gets in your way.

## 2026-08-19 — patch trims ear-tuned at the dry run (cotf)

**Nothing changes on your machine.** With sc3-plugins in, your reworked sounds live, and
super-seats live, Ciaran re-balanced the per-patch trims by ear: BASSBUZZ +7.5 dB,
JUPITERSHARP +3, ALTOSYNTH +8, PERCUSSION −3, SOPRANOVOICE +3 (all in `~cotfPatchTrims`,
Room 3 only; MOTU faders stay zeroed).

## 2026-08-19 — two show-fixes to last night's rework, found at the live-flip dry run (cotf)

Both found with real testers this morning; minimal edits so we could go live at 1pm —
both are yours to re-do properly.

- **ALTOSYNTH: `BMoog` doesn't exist on M1** (no sc3-plugins installed — M1 had only
  Require + Canvas3D), so every ALTOSYNTH load died with `ERROR: Class not defined` and
  the seat was completely silent. Briefly ran a core-`BPF` stand-in, then **installed
  sc3-plugins 3.14.0 (universal, user Extensions on M1, both engines) the same morning
  and restored your BMoog line verbatim** — your patch runs exactly as you wrote it,
  including the `add: 0.9` into `.tanh` (worth an ear-check that the DC bias is the
  colour you intended, since it never compiled on the rig before today). Install is
  documented in CLAUDE.md machine setup.
- **SOPRANOVOICE: idle chops lost their rotation gate.** `Pdef .set(\amp, ampa * 2.0)` —
  `ampa`'s curve bottoms out at 0.5 (dB) and `.dbamp` of that is ≈1.06, so a full-volume
  chop fired every couple of seconds with the stick at rest ("a note just plays
  constantly"). Restored the `amp *` rrate gate (your own header's contract: rotation
  drives the pattern) and kept your new 2.0 top. Piece/tuning states were fine.

## 2026-08-19 — SOPRANOVOICE patch trim +16 → +5 dB (cotf)

**Nothing changes on your machine.** Precaution ahead of this morning's dry run: your
overnight SOPRANO dynamics rework may make the patch much more responsive, and +16 dB of
compensation on top of that could be a blast. Pulled its `~cotfPatchTrims` entry back to
+5 dB (1.7783 — the old SC-side seat-5 trim alone, i.e. 11 dB below how the chair ran
with the now-zeroed MOTU fader); will re-raise by ear at the dry run if it still
struggles to cut through. The other four patch trims are unchanged.

## 2026-08-18 — Ciaran added super-seats headers to the five live sounds (cotf)

**Nothing changes on your machine — comment-block-only edits, zero code touched.**
The five live personalities ({BASSBUZZ, JUPITERSHARP, ALTOSYNTH, PERCUSSION,
SOPRANOVOICE}.sc) now carry the SUPER-SEATS.md header keys (`prints:`, `seats:`,
`affinity:`, `register:`, `family:`) so the matcher can pick who gets which sound.
`description:` was rewritten audience-legible (it feeds staff surfaces and the corridor
call-sheet hints); your original technical description is preserved verbatim on a new
`internals:` line right under it (unparsed, documentation-only). Seat pools are
deliberate: BASSBUZZ [1,2,3] · JUPITERSHARP [2,3,4,5] · ALTOSYNTH [3,4,5] ·
PERCUSSION [1,2,3,4] · SOPRANOVOICE [3,4,5] — seat 1 is bass-or-drums only on purpose.
Sent without pre-review to make tomorrow's dry run (Ciaran's call) — please review after;
everything here is your call to amend.

## 2026-08-18 — Room 3 trims moved from seats onto patches (cotf)

**Nothing changes on your machine** (`~cotfRoom` isn't 3, so both structures are inert
for you). For super-seats (sounds soon move between chairs), Room 3's loudness
compensation now belongs to the personality, not the chair: new `~cotfPatchTrims`
(main_cotf.scd, IdentityDictionary keyed by personality name) is applied to a seat's
monitor whenever that personality loads there, via a new listener on the existing local
`/airkit/personalityName` broadcast — no OSC surface change. Values are the old baked
Room 3 seat trims (which were really patch trims in disguise — patches had never moved)
plus Ciaran's MOTU fader offsets, now zeroed at the desk: BASSBUZZ +6.5 dB,
JUPITERSHARP +2 dB, ALTOSYNTH +11 dB, PERCUSSION −5 dB, SOPRANOVOICE +16 dB. Room 3's
`~cotfSeatTrims` goes flat; Room 2's stays exactly as it was (seat 3 +5 dB).
`/airkit/seatTrim` still works and now composes with the patch trim instead of stomping
it. Net Room 3 output tonight is identical to how Ciaran left it (same total gain per
today's patch↔seat identity).

## 2026-08-13 — Room 3 seat 5 trim +5 dB baked (cotf)

**Nothing changes on your machine.** Room 3 seat 5 performers kept reporting they
couldn't hear themselves ("struggles to cut through"); we live-trimmed the seat-5
monitor +5 dB via `/airkit/seatTrim 5 1.7783` on 08-12 and it held up well over a full
show day, so it's now baked into `~cotfSeatTrims` (Room 3 array) and documented in
`API.md`. Restarts re-assert it. The underlying feel question (is the patch itself too
subtle at low input?) is separate and lives in `EDINBURGH-NOTES.md`'s day-6 notes.

## 2026-08-06 — seat trims made room-aware + `EDINBURGH-NOTES.md` (cotf)

**Nothing changes on your machine.** Edinburgh day 1 balance notes: Room 3 seats 1+2
(LUMIVOX/GRAVITONE) ran a little quiet against the rest of the chair-speaker mix, and
Room 2 seat 3 (ALTOSYNTH) needed more presence under the Genelecs than the flat room
pad gave it. Extended the per-seat static trim from the previous entry to be room-aware:

- `~cotfSeatTrims` — Room 3 is now `[1.2589, 1.2589, 1.4125, 0.5623, 1]` (seat 1 +2 dB,
  seat 2 +2 dB, seat 3 +3 dB, seat 4 −5 dB, all ear-tuned live). Room 2 is now
  `[1, 1, 1.7783, 1, 1]` (seat 3 +5 dB, on top of the existing −10 dB `~cotfMonitorTrim`
  room pad; other Room 2 seats stay flat). On your machine `~cotfRoom` isn't 2 or 3, so
  this is inert either way.
- New `EDINBURGH-NOTES.md` — a running, append-only log of live observations that don't
  necessarily need a patch-side change (as opposed to this changelog, which is changes we
  made). First entry: seat 3 (ALTOSYNTH) didn't sound like an A pitch-set during tuning —
  flagging for whenever you're next in the patch, no action requested.

## 2026-08-06 — per-seat static trim `/airkit/seatTrim` + Room 3 seat 4 −5 dB (cotf)

**Nothing changes on your machine.** Edinburgh day 1 balance note: PERCUSSION on Room 3
seat 4 ran hot against the other four in the chair-speaker mix, so we added a per-seat
static trim to our monitor chain (`main_cotf.scd` only — your controllers untouched):

- `~cotfSeatTrims` — per-seat multipliers folded into each `\cotfMonitor`'s existing
  `trim` at boot. Room 3 ships `[1, 1, 1.4125, 0.5623, 1]` (seat 3 +3 dB, seat 4 −5 dB,
  ear-tuned with a test group in the room); Room 2 stays flat (its −10 dB room pad is
  unchanged). On your machine `~cotfRoom` isn't 3, so all 1s.
- `/airkit/seatTrim seat linearGain [fadeSec]` — additive OSC address (API.md updated
  same commit) so we can tune a seat live by ear, then bake the value. Doesn't touch
  voiceMute's `gain`, masterLevel's `masterGain`, or any other seat.
- `\cotfMonitor`'s `trim` now goes through `.lag(trimLagT=0.5)` so a live trim change
  fades instead of clicking. Boot value is set via creation args, so no ramp at start.

If PERCUSSION suddenly sounds quieter than you intend in the Room 3 mix, this is why —
tell us and we'll re-tune or drop the trim.

## 2026-07-25 — prints.json inventory export + optional `prints:` header key (cotf)

**Nothing changes for your workflow unless you opt in.** Two additions, both data/docs:

- `personalities/prints.json` — a machine-generated snapshot of our physical 3D-print
  inventory (the instrument bodies performers carry). Regenerated on M0 by
  `npm run export:prints`; **don't hand-edit it**. It's marked `"status": "TEST_DATA"`
  right now — the entries are placeholders until the real catalog (14+ prints) lands
  late July and flips it to `"LIVE"`.
- `concert_p_files.md` §21 — documents a new **optional** header key you can add to any
  personality: `prints: [whiteViolin, copperViolin]` (ids from prints.json), recommending
  which physical bodies suit that patch's sound. Our instrument crafter treats it as a
  soft preference when assigning a performer their print; omit it to express no
  preference. No SC code reads it — it's parsed on our side only.

## 2026-07-24 — Multi-device ~onResync capture (cotf)

**Nothing changes for your solo/GUI workflow.** Your new state-aware `~onResync` hooks
(celesta, drums, marimba, dulcimer, harp) are exactly right — this makes them work when
*five* devices are loaded at once. As written, each personality's install into
`topEnvironment` overwrites the previous device's hook, so in the show only the
last-loaded seat would get the clean stop → `group.freeAll` → conditional restart on a
seek; the other four would dump their TempoClock backlog (node bursts / stuck notes).

- `personalityController.scd`: after `~init` runs, a small `captureResyncHook` moves the
  hook the personality just installed into that device's env and restores our dispatcher.
  **Guarded on the COTF profile being active** — in your GUI (`main.sc`) there is no
  dispatcher, the guard fails, and behaviour is byte-for-byte what `concert_p_files.md`
  §6 documents. Also: unloading a personality now drops its captured hook (a survivor
  would `freeAll` a group `~deinit` already nil'd).
- `cotf/main_cotf.scd` (ours): the old generic fan-out is now a dispatcher that prefers
  each device's own hook (run under `topEnvironment` so `~roomState`/`~beatClock`
  resolve) and falls back to the generic stop/replay for personalities without one
  (e.g. `cotf_simple*`).
- `concert_p_files.md` §6: short note added — keep installing into `topEnvironment`
  exactly as you do now; no p-file changes needed, ever.

Also confirmed on our side while pulling your batch: the new `/Config/GetConfig` probe in
`addDevice` lands on the COTF router's fixed source port (9001) — it's ignored gracefully
there, and since `/airkit/addDevice` only feeds GUI views (which the headless profile
never loads), the gated emission is harmless in production.

## 2026-07-20 — Research data collection note (cotf, docs only)

**Nothing in AirKit changes.** We've started collecting anonymised movement data from the show
for a CHI/NIME-oriented paper on DMI evaluation — a flag-gated tap in the COTF server's router
on M0, upstream of your input, feeding a brand-new separate process (`cotf-research`) with its
own database. No new OSC address, no code on M1, no change to what reaches your devices. Added
`docs/cotf-research-plan.md` (one page: study framing, the 8-feature movement battery, survey
items, the anonymisation model, and co-authorship) in case you want the detail or want to be a
co-author. If the research collector is ever off or crashed, nothing about AirKit's behaviour
changes — it's designed to be invisible from your side.

## 2026-07-19 — Day-1 fixes: per-seat meters, seat reset, and panic (cotf)

Three new additive OSC commands on `\cotfMonitor` in `main_cotf.scd` (your `main.sc` untouched),
all born from real-world issues discovered 2026-07-18. **Nothing changes for your workflow** —
these are staff-side operational tools.

- **`/airkit/getLevels`** — replies `/airkit/levels/reply <room> <p1..p5>` with per-seat linear
  peak amplitude since the last poll, tapped on the seat bus **before** any gain/trim/master
  multiplier. Your patches are untouched. Why: Room 3 is loud; staff had no way to see whether a
  patch was genuinely silent, broken, or just playing quietly. Now the staff iPad shows a live
  meter per seat (updates ~1 Hz, lightweight), so a dead patch is visible immediately.
- **`/airkit/resetSeat <seat 1-5>`** — tears down one seat only (ducks the monitor gain
  mute-aware via a new `~cotfSeatGain` dict, stops the Pdef, calls unload via the shared path,
  sets `d.name = "none"`). **No reload in SC.** When the M0 server sees a seat reset, it
  re-pushes the saved personality ~300 ms later — the seat re-enters like any mid-piece load, the
  conductor and beat clock stay locked, and the other four seats keep playing without interruption.
  Why: two 2026-07-18 experiences had a seat go completely silent mid-show (groups 27–29), and
  the only safe recovery is per-seat reload, not a full-room engine restart.
- **`/airkit/panic`** — fires `/airkit/resetSeat` for all five seats in sequence (no delay
  between). Our server fires this on every room transition and at close-down, so hanging voices
  die cleanly without requiring an engine stop. Why: a harp lingered in Room 2 long after its
  performer had moved to Room 3 — likely an un-released voice or held OSC value. This might be
  the same family as the stuck note at end-of-day, which we'll track alongside.
- **New `COTF-ISSUES.md`** — shared issue tracking for you and us. The first entries show what we found
  on 2026-07-18 (stuck note, hanging voices, a dead seat, and your compositional balance notes);
  feel free to add and comment. Checkbox on each when it's closed.

All three commands ship in commits `08096fe` + `7aa1cac` + fix `7fcd505`, both in `code3.0/cotf/`
only. `API.md` updated to match.

## 2026-07-18 (overnight) — Master level + speaker-test tone + state single-writer (cotf)

Two small additive OSC controls on `\cotfMonitor` (both rooms, `main_cotf.scd` only — your
`main.sc` untouched), plus a change in *which* states our server sends (no AirKit code change):

- **`/airkit/masterLevel <linearGain> [fadeSec=0.5]`** — per-room overall output level, a new
  independent `masterGain` multiplier on every seat monitor (alongside `gain`/voiceMute and the
  Room 2 `trim` pad, never through them). Default 1 = exactly today's loudness; the level is
  dialled from our admin iPad (−24…+6 dB) and re-pushed automatically after every engine restart,
  same as seat personalities. Never receiving the message = nothing changes.
- **`/airkit/testTone <seat 1-5> [durSec=1.0]`** — a short sine burst into that seat's monitor
  input bus, for our start-of-day speaker test (proves the whole chain incl. masterGain +
  outputMode). One-shot, self-freeing, harmless to a running piece but we only fire it between
  groups.
- **State single-writer (server-side change, heads-up):** our server now sends **only
  `/airkit/state idle`** (on Room 3 arrival and on stop/reset/clear). `tuning`/`piece` come from
  the QLab timeline cues, `curtain` from your conductor's own score-end trigger — so AirKit will
  no longer see a server `tuning` on group walk-in or a server `piece` at staff Start. API.md's
  transport-orchestration table updated to match.

## 2026-07-17 — Room 3 physical chair speakers (cotf)
- **Fix (same day, bench-caught):** the outputMode whitelist used `Array.includes`, which is identity-based in SC and always false for Strings — the env default and the OSC command were both silently ignored. Now `includesEqual` (commit 9ce7500). Verified live on M1: boot line reads `outputMode physical`, crossfade proven by ear both directions.
- `\cotfMonitor` now dual-writes: stereo → BlackHole (webrtcGain, unchanged path) + mono sum → MOTU outs 12–16 per seat (physicalGain). Exactly one gain active; crossfade via new `/airkit/outputMode <mode> [fadeSec]` (Room 3 only, additive). Boot default from `COTF_OUTPUT_MODE` env; absent env = webrtc-only, i.e. behaviour before this change. Nothing changes for main.sc / Room 2.

## 2026-07-16 — Docs-only: source-port convention + transport orchestration + Room 2 monitor heads-up

No code changes at all, just catching `API.md` up to how our side actually talks to yours — nothing
changes for your `main.sc` workflow.

- Documented our sender-side convention: our router binds a **fixed source port 9001** and
  addresses devices as `devicePort = 9001 + seat − 1` (seat 1 = 9001 … seat 5 = 9005). That's the
  stable source port your "device = source port + N−1" rule has been keying off already — just
  writing it down.
- Added a **"COTF transport orchestration"** section spelling out exactly when we send what:
  `state tuning` on Room 3 arrival, `state piece` before the QLab cue fires, `state idle` on
  stop/reset/clear, and a reminder that **we never send `/airkit/go`** ourselves — QLab drives
  transport via the Network cue, we only drive `state`.
- Heads-up: our drift monitor now also polls `/airkit/getState` + `/airkit/getSeats` against your
  Room 2 instance on port 57121 every ~10s (it already did this against Room 3 on 57120) — so
  you'll start seeing periodic queries land on the Room 2 instance too. Read-only, same polling
  pattern as before, nothing it does can touch a seat mid-piece.

## 2026-07-16 — Room 2 warm-up output pad (−10 dB)

Purely a level trim on our Room 2 (warm-up) boot path — **nothing changes for your `main.sc`
workflow or for Room 3.**

- **`cotf/main_cotf.scd`**: the `\cotfMonitor` SynthDef gained a fixed `trim` arg (default 1,
  i.e. no change) multiplied alongside the existing `gain`. On COTF Room 2 only (`COTF_ROOM=2`)
  we set `trim = 0.3162` (−10 dB) when we spawn the per-seat monitors, because the warm-up
  voices share one MOTU 9/10 cable to the Room 2 Genelecs with our ambient bed and were running
  hot. `gain` (the per-seat voiceMute, 0/1) is untouched and still owns muting; `trim` is a
  separate baked-in pad that survives every mute/unmute. Room 3 keeps `trim = 1`.

## 2026-07-15 — merged your reload fix + logging; added roster query

Merged your `reload p-file bug fix` and `welcome and device details logged` commits — clean
merge, no conflicts, nothing on our side touched the same lines.

- **`/airkit/getRoster`** (new) — replies `/airkit/roster/reply name1 name2 ...` to the sender
  with the loaded personality names in list-index order, so our admin UI can list what's
  available. Lives in `personalityController.scd` next to `loadPersonality` (that's where the
  list is actually in scope). Additive, replies only to whoever asks — no behaviour change for
  your `main.sc` workflow.

## 2026-07-14 (later) — headset latency bench

Chasing the gesture→headset delay (the VR headset leg, not your patches — those feel tight on
the room speakers). **Nothing changes for your `main.sc` workflow** — both edits are in our
`cotf/` boot path only.

- **`cotf/config.scd`**: the small-IO-buffer request is now **opt-in** (env `COTF_HW_BUFFER`;
  nothing set = device default). The first version unconditionally requested 128 samples and,
  against our drift-corrected Aggregate device, produced a server that booted and ticked while
  emitting pure silence on every channel — and left the loopback driver wedged machine-wide
  until a `sudo killall coreaudiod`. Your `main.sc` doesn't load this file, but if you ever see
  "server up, meters moving, zero sound" on an aggregate device, that's the shape of it.
- **`cotf/main_cotf.scd`**: a comment documenting our bench measurement tap (one eval line that
  mirrors seat 1's monitor onto a room speaker so we can phone-record speaker-vs-headset onset
  gaps). No behaviour change.

## 2026-07-14 — AirKit becomes the COTF show engine (bench day 1: it works!)

We ran a real AirStick through the venue routing into AirKit on our audio Mac, playing
`cotf_simple1` locked to Beethoven fired from QLab, heard in a VR headset. Sounding great.
Everything below supports that. **Nothing changes for your normal `main.sc` workflow** — every
edit defaults to today's behaviour on a standalone machine.

- **`CLAUDE.md`** (new) — house rules for this shared branch (both of us push here): pull before
  push, no history rewrites, our commits prefixed `cotf:`, and OSC compatibility rules. Also a
  machine-setup checklist (quarks, samples, data files) learned the hard way.
- **`code3.0/API.md`** rewritten as the living OSC contract — every address in and out, including
  the new COTF ones. Rule we hold ourselves to: any commit that changes OSC updates this file in
  the same commit. Yours to lean on too.
- **Hardcoded `57120` → `NetAddr.langPort`** everywhere — identical on your machine (your langPort
  IS 57120); lets us run a second AirKit instance for our warm-up room later.
- **`~conductorAudioEnabled`** (default `true` = you) — when `false`, the conductor walks the
  score/beat clock but doesn't play the Beethoven file locally, because QLab owns the audio at
  the show. Also fixed a crash we caused: seeking twice in that mode threw "Cannot close a Buffer
  that has been freed" and silently killed the score walker.
- **`~outBus` convention** — personalities route output via `~outBus` (`\out, ob` in Pbinds).
  Defaults to `0` (hardware out) standalone; under our show profile each device lands on its own
  bus → per-seat headset stream. All 14 `cotf_*` personalities + `template.sc` updated; each file
  captures `var ob = ~outBus ? 0;` at the top (the `topEnvironment.use{}` blocks would otherwise
  read the wrong environment).
- **`/airkit/state idle|tuning|piece`** + **`/airkit/getState`** — room-wide state; `tuning` pins
  the voice pool to A (69). Personalities can react via a new optional `~onState = {|old,new|}`
  hook. Nothing fires unless the message is sent.
- **`/airkit/voiceMute <devicePort> <0|1> [fadeSec]`** — a real audio mute (gain fade on our
  show profile's per-seat monitor; patterns keep playing so unmute re-enters in time). Distinct
  from your `/airkit/mute` title-button semantics, which we deliberately did not touch.
- **`code3.0/cotf/`** (new, ours) — the show entry point (`main_cotf.scd` + `config.scd`): same
  controllers as your `main.sc`, headless, our audio device + per-seat buses. You never need to
  run it, but you can.
- **`personalityController.scd` paths** now derive from the checkout (`lists/`, `personalities/`
  relative to the repo) — no more per-machine path editing at the top of the file; your old
  hardcoded lines are kept as comments.
- **`config.scd` sets `numBuffers = 2048`** matching your `main.sc`, so the sampler personalities
  don't starve under the show profile.

Things we learned that live outside the repo (documented in CLAUDE.md): the `Require` and
`Canvas3D` quarks are prerequisites; sampler personalities need `~/Music/cotf_samples/`; our rig
needs the Beethoven WAV at 48 kHz (yours can stay 44.1k).

## 2026-07-15 (later) — /airkit/getSeats live-state query
- New `/airkit/getSeats` (personalityController.scd, beside getRoster): replies
  `/airkit/seats/reply port1 name1 ...` to the sender — what each live device
  actually has loaded. Why: devices are created lazily on their first IMU
  packet with the default "silence", which was overwriting the COTF server's
  personality pushes after a restart (found on the bench today). The server
  now polls this and re-pushes saved assignments. Additive, no behaviour
  change to anything of yours.

## 2026-07-15 (bench fix) — patterns survive seek re-anchor
- cotf/main_cotf.scd: implemented the ~onResync hook (your no-op default) —
  restarts each live device's Pdef against ~beatClock's new time on every
  anchor/re-anchor. Why: seek 0 jumps the boot-long beat clock BACKWARD, so a
  pattern already playing had its next event stranded hours in the future —
  instruments went dead-silent at the first QLab seek of the night. Probably
  the same family as the gesture-delay we chased on the 14th. COTF profile
  only; your main.sc behaviour unchanged.

## 2026-07-17 — captured a stray bench edit (cotf_simple1)
- personalities/cotf_simple1.sc: `~scoreAnchorBeat = 0;` added to ~init — found
  sitting uncommitted on M1, almost certainly from the seek-anchor bench
  session on the 15th (same family as ~onResync). Committing it so the branch
  matches what the machine is actually running before your next push lands.
  If this collides with your current work, your version wins — shout and we
  will reconcile.

## 2026-07-24 — bind-check now verifies the Aggregate's channel count
- cotf/bind-check.scd: after the device-NAME check passes, we now ask CoreAudio
  (system_profiler) how many output channels the Aggregate actually has and
  refuse to run below 144. Why: this morning the venue power-cycled with the
  MOTU accidentally unplugged — the Aggregate still existed under its usual
  name, scsynth bound it at 16 channels (BlackHole alone), the old check
  printed "BOUND — OK", and no room audio was possible. Under PM2 the failure
  now exits after 8s so PM2 retries until the MOTU is back (a climbing restart
  counter is visible; a green lie is not); in the IDE it only refuses, never
  kills your session. COTF profile only, no OSC change, no API.md change.

## 2026-07-24 (evening) — captured your live M1 tuning from the test run
- personalities/cotf_{drums4,harp1,harpsichord2,simple2}.sc: your ssh edits on M1
  during our "5 fingered test" run tonight (amp lincurves, octave shifts, hit-window
  0.15→0.1), committed from M1 under your name so the branch matches what the
  machine is running. If any of these were experiments you didn't want kept,
  shout and we'll reconcile — nothing else was touched.

## 2026-07-25 — the "ghost harp in an empty room" (amp seed on every cotf personality)
- **Symptom** (heard at the 2026-07-24 bench): a room with nobody in it was
  *louder* than a room with a motionless performer. `cotf_harp1` on a seat whose
  stick was switched off / never connected kept plinking away on its own — one
  note per beat, forever, with the sticks all boxed.
- **Cause — an Event-default inversion.** The Pdef's Pbind declares no `\amp`
  and no `\dur`; both are supplied *only* by the state tick hooks
  (`~idleNext`/`~pieceNext`/…) via `Pdef(m.ptn).set(...)`. Those hooks run only
  while the seat's device is enabled. With no stick there is no device, so no
  hook ever runs, and the pattern falls through to SuperCollider's Event
  defaults: `amp = 0.1`, `dur = 1`. A motionless *performer* floors at −60 dB
  (`~idleNext`'s lincurve). So the empty seat sat ~50 dB above the occupied one.
- **Fix — seed the Pdef envir immediately after `.play` in `~init`:**
  `Pdef(m.ptn).set(\amp, 0);` (plus `\dur, 2` for `cotf_harp1`, which had no
  `\dur` anywhere either). Deliberately an envir `.set` and **not** a Pbind key:
  a Pbind key overrides the envir, which would permanently defeat the hooks'
  own `.set` and mute the personality for real. `cotf_simple1`/`cotf_simple2`
  already did exactly this with `\amp, 0` on their `Synth(\simple, …)` — this
  brings the Pdef personalities up to that standard.
- **`~onResync` needs no second seed.** `Pdef.set` writes the proxy's `envir`,
  which is untouched by `.stop`/`.play` of the same key — only redefinition or
  `.clear`/`.remove` would drop it, and neither happens on a seek. The seed
  therefore survives every beat-clock re-anchor.
- **Fixed (13 files):** `cotf_harp1` (amp + dur — the one actually heard),
  `cotf_celesta1`, `cotf_dulcimer1`, `cotf_harpsichord1`, `cotf_harpsichord2`,
  `cotf_marimba1`, `cotf_marimba2`, `cotf_simple3`, `cotf_test1` (amp only —
  each already seeded or declared its own `\dur`), and `cotf_drums1..4` (amp
  only). The drums pause themselves in `~init` so they were not the empty-room
  noise, but their Pbinds omit `\amp` too, and `~onRoomState \piece` resumes
  them — an unoccupied seat during the piece would have played at 0.1. Seeded
  for the same reason.
- **Audited clean (2 files):** `cotf_simple1`, `cotf_simple2` — long-lived
  `Synth`, already created with `\amp, 0`.
- Nothing restructured, no OSC change, no `API.md` change; non-cotf
  personalities untouched. Every enabled seat behaves exactly as before — the
  first tick overwrites the seed within ~33 ms.
- **Related, on the COTF server side (landed today):** the room state `silent`
  is now actually reachable — the server sends `/airkit/state silent` on group
  clear/finish, so `~onRoomState \silent` (which already set `\amp, 0` in these
  files) fires for real instead of only ever being a code path. Belt and
  braces: the seed covers the never-had-a-stick case, `silent` covers the
  had-a-stick-and-the-group-left case.

## 2026-07-28 — real print inventory: 11 confirmed prints (LIVE)

`personalities/prints.json` is regenerated with the first CONFIRMED physical
print list — status flipped TEST_DATA → LIVE. The 20 placeholder ids
(whiteViolin/blackHonker/copperViolin + variants) are gone; the real pool is:
whiteWing, blackGlonker, blackShaker, brownShaker, boneRod, brownBall,
compactViolin, gymBro, oliveBird, shinyStick, thinViolin (11 total, more may
be confirmed later). Any `prints:` recommendation key in a personality header
that references a retired placeholder id will simply stop matching — worth a
sweep of your headers when convenient. Appearance/characteristics tags come
from Ciaran's descriptions of the actual printed objects.

## 2026-07-30 — two new prints confirmed: bopIt + greenHolder (13 total)

`personalities/prints.json` regenerated (still LIVE). Two new physical prints
join the pool: **bopIt** (toy-like, rattle, holdable, small, twisty — fun,
light, easy, playful) and **greenHolder** (mysterious, green, lumpy,
medium-size — dark, funky, weird, interesting). Both are valid targets for a
personality header's `prints:` recommendation key.

## 2026-07-30 — Sensi-Test session notes (Steph live on M1, ~15:30–16:45)

Live tuning committed as `cotf: Sensi-Test 2026-07-30 live tuning` (harp1 amp/octave curve,
percussionist1 kit voicing + accelMassFilteredDecay 0.98→0.4 + baseAmp×activity curve,
simple2 osc mix, test3). SC-relevant observations from the show logs:

- **cotf_percussionist1 went silent twice needing a seat reset+reload** (Room 3 seat 4,
  15:59 and 16:32) — sound returned after `/airkit/resetSeat` + personality re-push. Both
  incidents are this personality only. Possible angles: the 15-buffer orchkit load racing the
  first state tick / gestures; the amp deadZone (activity < 0.15 → amp 0) interacting with
  the live-edited decay (0.98→0.4 makes activity fall to the dead zone much faster); Pdef
  `\baseAmp` only being written inside the state-next hooks. Room 2 log also shows repeated
  `FAILURE IN SERVER /g_freeAll Group N not found` around percussionist1 reload churn.
- **After a `/airkit/panic`, seats report personality `none` until M0's monitor re-pushes
  (~10 s)** — a stick played in that window is silent. Known shape, just noting the measured
  gap during real groups.
- A `/airkit/loadPersonality` sent while a seat's device doesn't exist yet errors
  `'index' not understood, RECEIVER: nil` (seen earlier today) — a `d.notNil` guard in the
  load handler would make pre-power-on loads harmless no-ops.

## 2026-07-31 — Friday test session notes (Steph live on M1, afternoon)

Rebrand pull (`ff681c0`) deployed to both engines mid-morning: show set is now
BASSBUZZ / JUPITERSHARP / ALTOSYNTH / PERCUSSION / SOPRANOVOICE + silence.sc; COTF
saved seats re-pointed 1=BASSBUZZ 2=JUPITERSHARP 3=ALTOSYNTH 4=PERCUSSION
5=SOPRANOVOICE (seat 3 was Ciaran-side judgment — cotf_whisperer1 is off-roster but
still on disk; shout if ALTOSYNTH is not the intent). Full sample set incl. new
orchkit timpani/kicks synced to `~/Music/cotf_samples/`.

Steph then live-tuned all five on M1 (committed as `acb01de`, authored steph):
ALTOSYNTH idle-note +24 / tuning pitch 52→64 / octave offset drop; BASSBUZZ tuning
amp ceiling −8→−1 dB; PERCUSSION baseAmp curve top 0.2→0.7; JUPITERSHARP and
SOPRANOVOICE voicing reworks. Testing declared a success — no silence incidents
noted this session.

## 2026-08-01 — Open day test live tuning (Steph on M1)
- ALTOSYNTH: longer dur sequence, amp ceiling -12→-8, octave range narrowed
- BASSBUZZ: amp curve tightened (2.0→1.1 input range), ffreq ceiling 12k→8k
- JUPITERSHARP: amp -1→-3, tt 15→20
- PERCUSSION: amp range tightened, rest floor 0.3→0.2, activity curve -4→-3
- SOPRANOVOICE: amp -28→-24, RLPF lag 0.1→0.04

## 2026-08-15 — cotf: SUPER-SEATS.md added (docs only)

- New working doc SUPER-SEATS.md: the super-seats feature — performer-bound sound assignment from the personality pool. Proposes new OPTIONAL header keys (seats/affinity/register/family), the roster-name=filename rule, and a seat character table to fill in together. No code changes; nothing behavioural. Please read + push back in the doc itself.
