# AirKit OSC Contract

**This file is the single source of truth for every OSC address AirKit
consumes or emits.** Any commit that changes OSC behaviour MUST update this
file in the same commit.

## Compatibility rules

1. Additive only — add new addresses; never change or repurpose existing ones.
2. New arguments are appended, with defaults preserving old behaviour.
3. Defaults must preserve composer-machine behaviour exactly (single
   instance, langPort 57120, stereo out 0, conductor audio enabled).
4. Anything COTF-specific is marked **[COTF]** below.

## Device-side input (what an AirStick/router sends TO AirKit)

Sent to the instance's sclang port (composer default 57120; COTF Room 3 =
57120, Room 2 = 57121). `N` = virtual device 1..5. Devices are keyed by
sender source port + (N−1): senders must keep a stable source port.

**[COTF]** the COTF router binds a **fixed source port 9001** and addresses
devices as `devicePort = 9001 + seat − 1` (seat 1 = 9001 … seat 5 = 9005) —
this is the stable sender port the "device = source port + N−1" rule above
relies on.

| Address | Args | Notes |
|---|---|---|
| `/N/IMUFusedData` | `ax ay az qx qy qz qw` (floats; msg[1..7] read) | ~100 Hz IMU stream; first packet auto-creates the device |
| `/N/Battery` | `volts charge%` | optional |
| `/N/DigiIn` | `buttonIndex state` | up to 4 buttons |
| `/N/Config` | config dump; bytes [15..17] = RGB | reply to `/Config/GetConfig` |

## Host API (`/airkit/*`)

All on the instance's sclang port unless noted.

### Devices & personalities
| Address | Args | Notes |
|---|---|---|
| `/airkit/startOSCListening` | `port` | begin auto-detect (arg currently unused by the listener) |
| `/airkit/stopOSCListening` | — | free auto-detect listeners |
| `/airkit/addDevice` | `port` | emitted internally on detection; views subscribe |
| `/airkit/loadPersonality` | `port index` | load personality `index` from the device's list |
| `/airkit/reLoadPersonality` | `port index` | re-run `~init` (live-reload on file save) |
| `/airkit/unLoadPersonality` | `port index` | run `~deinit`, stop the routine |
| `/airkit/personalityName` | `port name` | broadcast; views update the title button |
| `/airkit/mute` / `/airkit/unmute` | `port` | **GUI semantic: unload/reload personality** (title button). NOT an audio mute — see `/airkit/voiceMute`. Do not repurpose. |
| `/airkit/calibrate` | `port` | capture current quaternion as reference |
| `/airkit/oscThru` | `bool` | forward incoming IMU packets to outAddr |
| `/airkit/remote/devices` | — | replies `/airkitremote/devices` with device ports |
| `/airkit/cc/<num>` | `value` | MIDI CC rebroadcast (composer GUI path) |
| `/airkit/getRoster` | — | replies `/airkit/roster/reply name1 name2 ...` to the sender: the loaded personality names in index order (index == `loadPersonality` index, both `silence` entries included). Used by the COTF server to enumerate patches. |
| `/airkit/getSeats` | — | replies `/airkit/seats/reply port1 name1 port2 name2 ...` to the sender: flat (devicePort, personalityName) pairs for every live device. Devices are lazily created on first IMU packet with personality `silence`; the COTF server polls this to reconcile actually-loaded vs saved assignments. |

### Transport (conductor)
| Address | Args | Notes |
|---|---|---|
| `/airkit/go` | — | start from t=0 (≡ seek 0) |
| `/airkit/seek` | `seconds (float)` | reposition score walker + beat clock (+ local audio if enabled) |
| `/airkit/beek` | `beatIndex (int)` | seek by beat index |
| `/airkit/pause` | — | stop transport, freeze playhead |

### [COTF] Transport orchestration
| Trigger | Action | Notes |
|---|---|---|
| Room 3 arrival | `/airkit/state idle` | sent on group arrival (2026-07-17: was `tuning`) |
| Stop / reset / clear | `/airkit/state idle` | room reset to free play |

**Single-writer rule (2026-07-17):** the COTF server now sends **only `idle`**.
`tuning` and `piece` come from QLab's own timeline OSC cues (network patch
`airkit-room3` → 127.0.0.1:57120) at the musically-correct moments, and
`curtain` fires internally from the conductor at score end — the server sends
neither. The admin manual-override endpoint can still send any state.

The COTF server **never sends `/airkit/go`** — the conductor is QLab-slaved
via the Network cue; QLab's cue drives transport, COTF only drives `state`.
Staff seat-mute bridges to `/airkit/voiceMute devicePort muted [fadeSec=1]`
(real audio fade — packet-drop alone would leave the pattern playing). The
COTF monitor polls `/airkit/getState` + `/airkit/getSeats` on **both rooms**
(57120 Room 3, 57121 Room 2) every ~10 s and re-pushes only drifted seats
(never a sounding seat mid-piece); Room 2's desired state mirrors Room 3's
saved seats.

### [COTF] Room state & voice mute
| Address | Args | Notes |
|---|---|---|
| `/airkit/state` | `idle\|tuning\|piece\|curtain\|silent` | room-wide. `idle`: transport stopped, free play. `tuning`: transport stopped, `~scoreVoicePool` pinned to `[69]` (A4). `piece`: conductor owns pool/hooks; transport still started by go/seek. `curtain`: piece is over; transport untouched, personalities decide tail behaviour (musical fade / resolve). Can be sent externally, but is **also triggered internally** by the conductor when the score walker exhausts (natural end — not on manual `~stop`). `silent`: hard mute; transport & routine keep running so pitch/pattern state stays fresh — leaving `silent` back into `piece` resumes mid-phrase in perfect beat sync. Unlike the other four states, `silent` has **no per-tick hook** (`~silentNext` doesn't exist) — personalities handle it one-shot in their `~onRoomState \silent` branch by setting amp/gain to 0. Dispatches `~onRoomState.(ctx)` to personalities, where `ctx = (state:, prevState:, stateChanged:)`. |
| `/airkit/getState` | — | replies `/airkit/state/reply <name>` to sender |
| `/airkit/voiceMute` | `devicePort muted(0|1) [fadeSec=1]` | real audio mute on the device's monitor gain; pattern keeps playing so unmute is instant and beat-synced |
| `/airkit/outputMode` | `"physical"\|"webrtc" [fadeSec=0.5]` | COTF Room 3 only: crossfade all five seat monitors between the MOTU chair-speaker outs (12–16, mono) and the BlackHole webrtc buses. Inactive side is hard 0. Room 2 ignores it. Boot default: env `COTF_OUTPUT_MODE` (absent = webrtc). |
| `/airkit/masterLevel` | `linearGain(0–4.0) [fadeSec=0.5]` | per-room overall output level, applied to all five seat monitors independently of voiceMute (0/1) and the boot-fixed room trim. M0 owns persistence (settings) and re-asserts on both Room 2 and Room 3 dead→alive. |
| `/airkit/seatTrim` | `seat(1–5) linearGain(0–2.0) [fadeSec=0.5]` | [COTF] per-seat static trim, multiplied with the boot room trim AND (2026-08-18) the loaded personality's per-patch trim (`~cotfPatchTrims`, main_cotf.scd — Room 3 loudness compensation now travels with the SOUND for super-seats, applied on the local `/airkit/personalityName` broadcast; Room 3 `~cotfSeatTrims` is flat) on that seat's monitor. Independent of voiceMute's gain and masterLevel's masterGain; never touches other seats. NOT persisted by M0 — bake tuned values into `~cotfSeatTrims` (chair-shaped) or `~cotfPatchTrims` (sound-shaped) so restarts re-assert them. Baked patch trims (ear-tuned live 2026-08-19 afternoon, after group 214): BASSBUZZ +7.5 dB, JUPITERSHARP +5 dB, ALTOSYNTH +6 dB, PERCUSSION 0 dB, SOPRANOVOICE 0 dB. Room 2 seat trims unchanged (seat 3 +5 dB). |
| `/airkit/testTone` | `seat(1–5) [durSec=1.0]` | speaker-test burst (440 Hz × seat.midiratio) routed through the seat's monitor chain, proving masterGain + outputMode + the physical/webrtc leg end to end. |
| `/airkit/getLevels` | — | [COTF] replies `/airkit/levels/reply <room> <p1..p5>` to sender: per-seat linear peak (0–1) since the previous getLevels, tapped pre-gain/trim/master on `~cotfSeatBus`; peaks reset on reply |
| `/airkit/resetSeat` | `seat(1-5)` | [COTF] teardown+silence one seat: duck monitor gain, stop Pdef, unload personality via the shared unload path. No reload (M0 re-pushes saved personality), no conductor/clock/state side-effects, other seats untouched |
| `/airkit/panic` | — | [COTF] resetSeat for seats 1–5; used by M0 on room transition/clear/close-down to kill hanging voices without an engine restart |
| `/airkit/seatPan` | `seat(1–5) pan(-1..1) [fadeSec=0.1]` | [COTF DEMO profile only — `cotf/main_demo.scd`] place one seat's mono monitor across the demo's L/C/R outputs with an equal-power law (-1 = L, 0 = C, +1 = R; `p<=0`: L=cos((p+1)π/2), C=sin((p+1)π/2); `p>0`: C=cos(pπ/2), R=sin(pπ/2)), gains lagged by fadeSec. Pan clamped; a bad seat is logged and ignored. Boot pans seats 1–5: -1, -0.5, 0, 0.5, 1. Not bound by the show profile (`main_cotf.scd`). |

**[COTF DEMO] profile notes (`cotf/main_demo.scd` + `config_demo.scd`, 2026-11 SensiLab demo):** every address above behaves as documented except `/airkit/outputMode`, which is a **logged no-op** under the demo profile (`[COTF] outputMode ignored under demo profile` — there is no webrtc/physical pair, only L/C/R), and `/airkit/testTone`, whose burst follows the seat's pan. Seats are mono-summed at -3 dB, panned to `DEMO_OUT_L/C/R`, and pass a `Limiter.ar(sig, 0.89, 0.003)` on those buses. QLab owns the Beethoven audio (`~conductorAudioEnabled = false`). The show profile is unchanged.

## Personality environment contract (informational)

Personalities may override:

- Lifecycle: `~init`, `~deinit`, `~plot`.
- IMU-rate tick: `~next` (always fires when device is enabled, ~30 Hz).
- **[COTF]** state-gated ticks (fire alongside `~next` only while the
  matching `~roomState` is current, same rate ~30 Hz):
  `~idleNext`, `~tuningNext`, `~pieceNext`, `~curtainNext`. Signature
  is `|d, ctx|` — `d` is the device, `ctx` is a trimmed snapshot of
  the score context (fields: `state`, `voicePool`, `loudness`,
  `tension`, `brightness`, `density`, `register`, `sectionId`,
  `phraseId`, `chord`, `key`, `scale`, `p`, `m`). No change flags or
  beat-event booleans — those live on the full ctx passed to beat
  hooks. Lets state ticks mix gesture and score features at IMU rate.
  Nil-safe defaults are set at controller boot, so `ctx` is always a
  valid Event even before the first beat. The `\silent` state has no
  per-tick hook — it's a one-shot hard mute handled in `~onRoomState`
  (set amp to 0 in the `\silent` branch).
- Beat-aligned hooks dispatched from the conductor's score Routine
  (single ctx Event arg): `~onTick`, `~onHalf`, `~onBeat`, `~onBar`,
  `~onPhrase`, `~onSection`, `~onChord`, `~onKey`, `~onScale`. ctx
  fields: `idx, beatInBar, isDown, isTrueBeat, barIdx, half, sectionId,
  phraseId, chord, key, scale` (each with matching `prev*` +
  `*Changed` where applicable), `voicePool`, raw score features as
  floats (`loudness, tension, brightness, density, register`), `state`
  (current `~roomState`), `p` (score bar entry — everything else the
  score has), `m` (meta bar entry).
- Clock re-anchor: `~onResync.(idx)`.
- **[COTF]** Room state change: `~onRoomState.(ctx)` with `ctx = (state:,
  prevState:, stateChanged:)`.

Output convention: route audio via `~outBus` (`\out, ~outBus` in Pbinds
/ `out:` on Synths); defaults to 0 on composer machines, points at a
per-seat bus under COTF.

### Personality front matter (informational)

Each personality file starts with a YAML-ish block comment describing
what it does. Not consumed by SC — for humans and LLMs reading the
file. All keys optional; keep to one line each (arrays inline in
`[ ]`). Reference example is `personalities/cotf_simple1.sc`.

| key           | purpose                                                       |
|---------------|---------------------------------------------------------------|
| `gestures`    | tags from an open vocabulary (beat, shake, tilt, roll, swing, sweep, stab, hover…) — which IMU motions the personality responds to |
| `description` | one-line technical / architectural summary (what it *is*, not what it sounds like) |
| `sound`       | one-line character / timbre (what you *hear*) |
| `pitch`       | how notes are chosen (score voice pool, fixed array, algorithmic…) |
| `rhythm`      | rhythmic pattern (continuous drone, per-beat, pattern-based, gesture-triggered…) |
| `instruments` | fixed set: `Lumivox`, `Gravitone`, `Velaphone`, `Aetherharp`, `Cellaris` — which physical instruments this pairs with |

Template:

```
/*
gestures:    [beat, shake]
description: <what the personality is, architecturally>
sound:       <what it sounds like, one sentence>
pitch:       <where notes come from>
rhythm:      <rhythmic behaviour>
instruments: [Aetherharp]
*/
```
