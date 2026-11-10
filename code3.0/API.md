# OSC API Documentation

### /airkit/startOSCListening
- **Parameters**: [port]
- **Description**: Starts/triggers OSC listening for device detection

### /airkit/stopOSCListening
- **Parameters**: None
- **Description**: Stops OSC listening and frees listeners

### /airkit/addDevice
- **Parameters**: [port]
- **Description**: Adds a detected device to the system

### /airkit/loadPersonality
- **Parameters**: [port, personality_index]
- **Description**: Loads a personality for a specific device

### /airkit/personalityName
- **Parameters**: [port, name]
- **Description**: Sets/gets the personality name for a device

### /airkit/param
- **Parameters**: [port, name, value]
- **Description**: Sets a device param — `volume` or `sensitivity`. `value` is
  normalised and clamped to 0..1. Public, like `/airkit/mute`, so the panel,
  the webapp and MIDI can all drive it. An unknown device or an unknown param
  name is ignored, so OSC cannot add keys to the model.
- **Replies**: `/airkit/paramIs`

### /airkit/paramIs
- **Parameters**: [port, name, value]
- **Description**: Broadcast after a param actually changes, carrying the
  clamped value. Subscribe to this to keep a UI in sync rather than assuming
  the value you sent was the value taken. `controlView`'s slider does not
  subscribe — its draw loop reads the model each frame — but a Button-style
  widget or an out-of-process client should.

Params persist across restarts, keyed by the stick's own ID (`/Config/SetID`,
the number in its OSC address), not by IP — AirStick leases are dynamic. They
live in `state/deviceParams.scd`, which is gitignored and outside
`release_dirs`, so a web update never overwrites them.

---

## TODO: a query endpoint for params

**Not implemented.** There is currently no way to *ask* for a param's value —
`/airkit/paramIs` only fires on change. The panel does not need one because
`controlView`'s draw loop reads the model every frame, but **an out-of-process
client does**: a webapp that loads after a change has no way to learn the
current state, so its sliders would open at the wrong position and stay wrong
until someone moved one.

Wanted, matching `/airkit/remote/devices`, which already replies to the
sender's `addr` rather than broadcasting:

```
/airkit/getParams  [port]   ->  one /airkit/paramIs per param, to addr
```

Replying with `paramIs` rather than a new reply address means the client has
one handler for both "it changed" and "here is what it is", which is the whole
point — a UI that subscribes on connect and then just listens.

Two things to decide when it is built:

- **Reply to `addr` or broadcast?** `/airkit/remote/devices` uses `addr`.
  Broadcasting is simpler but re-notifies every listener on every query.
- **Should `port` be optional**, so a client can ask for the whole rig at once
  on connect rather than iterating devices it has to discover first?

Consumer: the teacher UI in `product_report.md` §17 move 7 (lists, personality
select, volume ceiling, calibrate, panic, who-is-playing) — all of which need
to render current state on load.

