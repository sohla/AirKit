// COTF DEMO entry point (SensiLab demo, 2026-11) — a copy of main_cotf.scd
// (the show profile, untouched) with these changes only: loads
// config_demo.scd; \cotfDemoMonitor replaces \cotfMonitor (mono seat sum,
// equal-power L/C/R pan); adds /airkit/seatPan; adds an output limiter;
// /airkit/outputMode is a logged no-op. Everything else is the show code.
// Run: DEMO_OUT_DEVICE="Mac Studio Speakers" \
//      /Applications/SuperCollider.app/Contents/MacOS/sclang \
//        /Users/m1/AirKit/code3.0/cotf/main_demo.scd
// Env: DEMO_OUT_DEVICE (required), DEMO_NUM_OUT (default 8),
//      DEMO_OUT_L/C/R (0-based buses, default 0/1/2) — see config_demo.scd.
(
var codeRoot = thisProcess.nowExecutingPath.dirname.dirname; // .../code3.0

thisProcess.interpreter.executeFile(codeRoot +/+ "cotf" +/+ "config_demo.scd");

// Same load order as main.sc: personality -> osc -> conductor.
thisProcess.interpreter.executeFile(codeRoot +/+ "personalityController.scd");
thisProcess.interpreter.executeFile(codeRoot +/+ "oscController.scd");
thisProcess.interpreter.executeFile(codeRoot +/+ "conductorController.scd");

// [TRACE] step-0 load-path instrumentation ON for the show profile (2026-08-19).
// Logging only — see the block in personalityController.scd for the line
// format. Set to false here to silence it. The `?` fallback keeps this file
// runnable against a controller checkout that predates the trace block, so a
// nil call can never take down the resync dispatcher.
~airkitTrace = true;
~airkitTraceLog = ~airkitTraceLog ? { |port, phase, extra| };

// [COTF] beat-clock anchor fix (2026-07-15 bench). ~beatClock is a boot-long
// singleton; every seek re-anchors ~beatClock.beats — possibly far BACKWARD
// (seek 0 sets beats=0 after the clock has free-run since boot). A Pdef
// already playing has its next event scheduled in OLD clock beats, which a
// backward jump strands hours in the future: the instrument goes silent while
// the walker, timer and everything else run happily (observed live: harp
// muted at the first QLab seek, "FAILURE /n_set Node not found" red herring).
// Fix: on every (re)anchor the conductor fires ~onResync — restart each live
// device's pattern against the new clock time; the bar quant re-enters in
// musical phase within one bar. COTF-profile only; composer's main.sc is
// untouched (their ~onResync stays the default no-op).
// (2026-07-24) Personalities now ship their own state-aware ~onResync
// (concert_p_files.md §6/§15: skip the restart during \tuning where the Pdef
// is deliberately paused, and s.bind { group.freeAll } to kill in-flight
// synths — only the personality can reach its file-scope `group`).
// They install it into topEnvironment; personalityController's
// captureResyncHook moves each install into d.env[\onResyncHook] (per-device,
// so 5 seats can't clobber each other) and restores this dispatcher.
// Personalities without a hook (e.g. cotf_simple*) get the generic restart.
~cotfResyncDispatcher = { |idx|
    // Capture conductor-env values BEFORE entering each device env — inside
    // d.env.use these ~vars resolve against the personality's environment
    // (nil there; first version crashed on nil * nil).
    var clock = ~beatClock;
    var quant = ~scoreBeatsPerBar * ~scoreEventsPerBeat;
    // [TRACE] captured here for the same reason as clock/quant above — the
    // generic branch logs from inside d.env.use, where a bare ~airkitTraceLog
    // resolves against the personality env and is nil.
    var tlog = ~airkitTraceLog ? { |port, phase, extra| };
    // [TRACE] step-0 only — see the block in personalityController.scd. The
    // route= field is the A3 signal: a seat still mid-load when a resync
    // arrives has no captured hook yet AND no playing Pdef yet, so it takes
    // the generic branch, finds isPlaying false, and is silently left
    // unanchored. That is `route=generic playing=false`, and today it is
    // indistinguishable in the log from a seat that legitimately had nothing
    // to re-anchor.
    tlog.(nil, "resync.enter", "idx=%".format(idx));
    ~devices.do { |d|
        if (d.env.notNil) {
            if (d.env[\onResyncHook].notNil) {
                // Run under topEnvironment: the hook's ~roomState/~beatClock/
                // ~score* live there; its m/group/tuneTime are lexical
                // captures so they survive the environment swap.
                tlog.(d.port, "resync.dev",
                    "idx=% seat=% name=% route=hook".format(idx, d.index, d.name));
                topEnvironment.use { d.env[\onResyncHook].value(idx) };
            } {
                if (d.enabled == true) {
                    d.env.use {
                        var playing = ~model.notNil and: { Pdef(~model.ptn).isPlaying };
                        tlog.(d.port, "resync.dev",
                            "idx=% seat=% name=% route=generic playing=%".format(
                                idx, d.index, d.name, playing));
                        if (playing) {
                            Pdef(~model.ptn).stop;
                            Pdef(~model.ptn).play(clock, quant: quant);
                        };
                    };
                } {
                    tlog.(d.port, "resync.dev",
                        "idx=% seat=% name=% route=disabled".format(idx, d.index, d.name));
                };
            };
        };
    };
    "[COTF] resync at idx %: device patterns re-anchored".format(idx).postln;
};
~onResync = ~cotfResyncDispatcher;

s.waitForBoot({
    // Device bind check (pattern: COTF audio/supercollider/lib/bind-check.scd).
    thisProcess.interpreter.executeFile(codeRoot +/+ "cotf" +/+ "bind-check.scd");

    // --- per-seat monitor chain: private bus -> gain -> final bus -----------
    // The monitor gain is the real mute (tails ring; the pattern keeps
    // playing so unmute is instant and still beat-synced) and the per-seat
    // volume point. Alive from boot, matching the "process is up even when
    // silent" convention.
    // `trim` is a fixed, per-room output pad — distinct from `gain`, which the
    // per-seat voiceMute owns (0/1). voiceMute never touches trim, so the pad
    // survives every mute/unmute.
    // [DEMO] L/C/R monitor: the seat's stereo bus summed to mono at -3 dB (as
    // the show's chair path does), through the SAME gain x trim x masterGain
    // chain and arg names as \cotfMonitor (so voiceMute / seatTrim /
    // masterLevel / patch-trim / resetSeat work unchanged), then panned across
    // three outputs with an equal-power law, p in -1..1:
    //   p <= 0: t = p + 1; gL = cos(t*pi/2); gC = sin(t*pi/2); gR = 0
    //   p >  0: t = p;     gL = 0;           gC = cos(t*pi/2); gR = sin(t*pi/2)
    // The three gains are lagged by panLagT (/airkit/seatPan's fadeSec).
    SynthDef(\cotfDemoMonitor, { |in, outL = 0, outC = 1, outR = 2, gain = 1, lagT = 0.5,
                                  trim = 1, trimLagT = 0.5, masterGain = 1, masterLagT = 0.5,
                                  pan = 0, panLagT = 0.1|
        var sig = In.ar(in, 2).sum * 0.7071
            * gain.lag(lagT) * trim.lag(trimLagT) * masterGain.lag(masterLagT);
        var p = pan.clip(-1, 1);
        var left = p <= 0;                 // 1 on the L-C half, 0 on the C-R half
        var tL = (p + 1) * 0.5pi, tR = p * 0.5pi;
        var gL = Lag.kr(left * cos(tL), panLagT);
        var gC = Lag.kr((left * sin(tL)) + ((1 - left) * cos(tR)), panLagT);
        var gR = Lag.kr((1 - left) * sin(tR), panLagT);
        Out.ar(outL, sig * gL);
        Out.ar(outC, sig * gC);
        Out.ar(outR, sig * gR);
    }).add;
    // [DEMO] output limiter over the L/C/R buses. Lives at the tail of the
    // ROOT node (after the default group), so it always runs after every
    // monitor and anything later added to the default group — resetSeat never
    // re-creates monitors, so this ordering holds for the whole boot. If two
    // of L/C/R share a bus (e.g. a 2-ch bench, L=C=0) both ReplaceOuts compute
    // the identical limited signal; the second simply rewrites the first.
    SynthDef(\cotfDemoLimiter, { |outL = 0, outC = 1, outR = 2|
        [outL, outC, outR].do { |b|
            ReplaceOut.ar(b, Limiter.ar(In.ar(b, 1), 0.89, 0.01));
        };
    }).add;
    s.sync;

    // [COTF] Baked-in −10 dB pad on the Room 2 warm-up voices (2026-07-16):
    // they share the MOTU 9/10 Genelec cable and were running hot vs the
    // atmosphere bed. Room 3 (headsets) is unchanged. 10^(-10/20) = 0.3162.
    ~cotfMonitorTrim = if(~cotfRoom == 2, 0.3162, 1.0);

    // Seat trims (linear gain). Room 3 went FLAT 2026-08-18 (Ciaran): its old
    // baked values ([1.2589, 1.2589, 1.4125, 0.5623, 1.7783]) were patch trims
    // in disguise — patches had never moved seats, so every "seat" trim was
    // really balancing the patch that lived there. They migrated (plus
    // Ciaran's MOTU fader offsets, now zeroed) into ~cotfPatchTrims below so
    // the compensation travels with the SOUND once super-seats moves patches
    // between chairs. Reintroduce a Room 3 chair trim only with evidence of a
    // genuinely chair-shaped imbalance. Room 2 stays seat-keyed as-is (shared
    // stereo pair, no per-seat MOTU path): seat 3 +5dB (Ciaran, Edinburgh
    // day 1), on top of the -10dB ~cotfMonitorTrim room pad.
    ~cotfSeatTrims = if(~cotfRoom == 3,
        { [1, 1, 1, 1, 1] },
        { [1, 1, 1.7783, 1, 1] }
    );

    // Per-PATCH trims (2026-08-18, Ciaran): loudness compensation keyed to the
    // personality, applied to a seat's monitor whenever that personality loads
    // there (via the local /airkit/personalityName broadcast — no new OSC
    // surface). Values = old baked Room 3 seat trim (per today's patch<->seat
    // identity) + Ciaran's MOTU 12-16 fader offsets, after which the MOTU
    // faders are zeroed: BASSBUZZ +2+4.5=+6.5 dB, JUPITERSHARP +2+0=+2 dB,
    // ALTOSYNTH +3+8=+11 dB, PERCUSSION -5+0=-5 dB, SOPRANOVOICE +5+11=+16 dB
    // (SOPRANOVOICE pulled back to +5 dB 2026-08-19 morning, Ciaran: Steph's
    // overnight dynamics rework may make it much more sensitive — re-raise by
    // ear at the dry run if it disappears again).
    // Room 3 only — Room 2 keeps its seat-keyed trims (see above). An unknown
    // personality gets 1 (no compensation).
    ~cotfPatchTrims = if(~cotfRoom == 3, {
        // Ear-tuned live day 1 of super-seats (2026-08-19 afternoon, Ciaran,
        // after group 214): PERC +3, JUP +2, ALTO -2, SOPRANO -3 on top of
        // the morning dry-run values; BASS unchanged.
        // Re-tuned 2026-08-25 (Ciaran, mid-run): BASS -1, JUP +3, ALTO -4 on
        // top of the 08-19 values; four new patches enter padded (STRINGCHORD
        // -4, BIRDY -3, PLUKSYNTH -3, WHIPBIRD -2). RainKeys/WindVoice pad
        // in-patch instead (they must also pad in Room 2 auditions).
        // 2026-08-27 (Ciaran, heard live): STRINGCHORD +3 (-4 -> -1),
        // BIRDY +3 (-3 -> 0).
        IdentityDictionary[
            \BASSBUZZ -> 2.1135,       // +6.5 dB
            \JUPITERSHARP -> 2.5119,   // +8 dB
            \ALTOSYNTH -> 1.2589,      // +2 dB
            \PERCUSSION -> 1.0,        // 0 dB
            \SOPRANOVOICE -> 1.0,      // 0 dB
            \STRINGCHORD -> 1.1220,    // +1 dB (was -1; +2 dB 2026-08-31 Ciaran ear-check)
            \BIRDY -> 1.0,             // 0 dB (was -3; +3 dB 2026-08-27)
            \PLUKSYNTH -> 0.7079,      // -3 dB
            \WHIPBIRD -> 0.7943,      // -2 dB
            \WINDY -> 0.6310          // -4 dB (entered padded 2026-08-26, untested on real load)
        ]
    }, { IdentityDictionary.new });
    // Current patch-trim factor per seat (1..5), updated on personality load;
    // seatTrim's handler multiplies it back in so a live seat trim never
    // stomps the loaded patch's compensation.
    ~cotfSeatPatchTrim = Array.fill(5, 1.0);

    ~cotfSeatBus = Dictionary();   // seat (1..5) -> private Bus (personalities' ~outBus)
    ~cotfMonitors = Dictionary();  // seat (1..5) -> monitor Synth
    // [DEMO] boot pans, seat 1..5 spread L -> R (the demo server re-asserts
    // its saved values via /airkit/seatPan anyway).
    ~demoSeatPan = [-1, -0.5, 0, 0.5, 1];
    5.do { |i|
        var seat = i + 1;
        var bus = Bus.audio(s, 2);
        ~cotfSeatBus.put(seat, bus);
        ~cotfMonitors.put(seat,
            Synth.tail(s, \cotfDemoMonitor, [
                \in, bus.index, \trim, ~cotfMonitorTrim * ~cotfSeatTrims[i],
                \outL, ~demoOut[\L], \outC, ~demoOut[\C], \outR, ~demoOut[\R],
                \pan, ~demoSeatPan[i], \panLagT, 0.1,
            ]));
    };
    ~demoLimiter = Synth.tail(RootNode(s), \cotfDemoLimiter,
        [\outL, ~demoOut[\L], \outC, ~demoOut[\C], \outR, ~demoOut[\R]]);

    // [COTF] last voiceMute-set gain per seat (1 = unmuted, default). Read by
    // resetSeat's un-duck restore so a reset never un-mutes a seat that a
    // staff voiceMute muted before the reset fired — written additively by
    // \cotfVoiceMute below, never by anything else.
    ~cotfSeatGain = Dictionary.new;

    // --- COTF per-seat output meter (day-1 fixes, 2026-07-19) ---------------
    // Taps ~cotfSeatBus (pre gain/trim/master) so a masterLevel dip can't fake
    // five dead seats. Peak-hold between /airkit/getLevels polls so sparse
    // plucks register at ~1 Hz polling. Cost: 5 synths, SendReply at 10 Hz.
    SynthDef(\cotfMeter, { |in = 0, seat = 1|
        var sig = In.ar(in, 2);
        var amp = Amplitude.kr(Mix(sig) * 0.5, attackTime: 0.001, releaseTime: 0.1);
        SendReply.kr(Impulse.kr(10), '/cotf/meter', amp, replyID: seat);
    }).add;
    s.sync;

    ~cotfPeaks = Array.fill(5, 0.0);
    ~cotfMeters = Dictionary();
    (1..5).do { |seat|
        ~cotfMeters.put(seat,
            Synth.tail(s, \cotfMeter, [\in, ~cotfSeatBus.at(seat).index, \seat, seat]));
    };

    OSCdef(\cotfMeterAccum, { |msg|
        var seat = msg[2].asInteger, amp = msg[3];
        if ((seat >= 1) and: { seat <= 5 }) {
            ~cotfPeaks[seat - 1] = max(~cotfPeaks[seat - 1], amp);
        };
    }, '/cotf/meter', s.addr);

    OSCdef(\cotfGetLevels, { |msg, time, addr|
        var peaks = ~cotfPeaks.copy;
        ~cotfPeaks = Array.fill(5, 0.0);
        addr.sendMsg("/airkit/levels/reply", ~cotfRoom, *(peaks.collect(_.asFloat)));
    }, '/airkit/getLevels');

    // --- COTF seat-scoped reset (day-1 fixes) -------------------------------
    // Teardown + silence ONLY. The M0 server re-pushes the saved personality
    // (~300ms later) through the ordinary /airkit/loadPersonality path, so a
    // reloaded seat rejoins exactly like any mid-piece personality load — the
    // conductor, ~beatClock and score position are NEVER touched here.
    ~cotfResetSeat = { |seat|
        var d = ~devices.detect { |dev| dev.index == seat };
        var mon = ~cotfMonitors.at(seat);
        if (d.isNil) {
            "[COTF] resetSeat %: no live device — no-op".format(seat).postln;
        } {
            // duck: silence wins over elegance. Fast, fixed lagT — never
            // inherit a sticky \lagT left over from a prior voiceMute call.
            mon !? { mon.set(\lagT, 0.02, \gain, 0) };
            if (d.env.notNil) {
                d.env.use { ~model !? { Pdef(~model.ptn).stop } };
            };
            // Full teardown via the shared unload path (procRout free + ~deinit):
            NetAddr.localAddr.sendMsg("/airkit/unLoadPersonality", d.port);
            // [COTF] d.name is left stale by unLoadPersonality (it only frees
            // procRout + runs ~deinit, it never resets the personality label).
            // Mark it explicitly so M0's getSeats reconcile backstop and the
            // Patches admin page don't keep reporting the torn-down
            // personality as still loaded if the explicit reload is lost.
            // Task 6 (M0-driven reload) should treat "none" the same as any
            // other drifted/silent seat.
            d.name = "none";
            // Restore the duck after the dust settles; the reload (server-driven)
            // arrives with gain audible. Restore to the last voiceMute-set gain
            // (default 1) rather than a literal 1, so a reset never un-mutes a
            // seat that a staff voiceMute muted before the reset fired.
            {
                mon !? { mon.set(\lagT, 0.2, \gain, ~cotfSeatGain.at(seat) ? 1) };
            }.defer(0.6);
            "[COTF] resetSeat %: torn down (port %)".format(seat, d.port).postln;
        };
    };

    OSCdef(\cotfResetSeat, { |msg|
        if (msg.size > 1) {
            var seat = msg[1].asInteger;
            if ((seat >= 1) and: { seat <= 5 }) { ~cotfResetSeat.(seat) } {
                "[COTF] resetSeat: bad seat %".format(seat).postln;
            };
        } {
            "[COTF] resetSeat: bad seat (no arg)".postln;
        };
    }, '/airkit/resetSeat');

    OSCdef(\cotfPanic, {
        "[COTF] panic: resetting all seats".postln;
        (1..5).do { |seat| ~cotfResetSeat.(seat) };
    }, '/airkit/panic');

    // --- [COTF] /airkit/voiceMute <devicePort> <0|1> [fadeSec] (API.md) -----
    OSCdef(\cotfVoiceMute, { |msg|
        var d = ~devices.at(msg[1].asInteger);
        var muted = msg[2].asInteger.clip(0, 1);
        var fade = (msg[3] ? 1).asFloat.max(0.01);
        d !? {
            ~cotfMonitors.at(d.index) !? { |syn|
                var g = 1 - muted;
                ~cotfSeatGain.put(d.index, g);   // [COTF] resetSeat's un-duck reads this
                syn.set(\lagT, fade, \gain, g);
                "voiceMute: device % (seat %) -> %".format(d.port, d.index, muted).postln;
            };
        };
    }, '/airkit/voiceMute');

    // --- [DEMO] /airkit/outputMode — logged no-op (API.md) ------------------
    // The demo has one output path (L/C/R); there is no webrtc/physical pair
    // to crossfade. Kept bound so a sender gets a log line, not silence.
    OSCdef(\cotfOutputMode, { |msg|
        "[COTF] outputMode ignored under demo profile".postln;
    }, '/airkit/outputMode');

    // --- [DEMO] /airkit/seatPan <seat 1-5> <-1..1> [fadeSec=0.1] (API.md) ----
    // Places one seat across L/C/R (-1 = L, 0 = C, 1 = R). Bad seat -> log and
    // ignore (never clipped onto another seat).
    OSCdef(\cotfSeatPan, { |msg|
        var seat = (msg[1] ? 0).asInteger;
        if ((seat >= 1) and: { seat <= 5 } and: { msg.size > 2 }) {
            var pan = msg[2].asFloat.clip(-1, 1);
            var fade = (msg[3] ? 0.1).asFloat.max(0);
            ~demoSeatPan[seat - 1] = pan;
            ~cotfMonitors.at(seat) !? { |syn| syn.set(\panLagT, fade, \pan, pan) };
            "[COTF] seatPan: seat % -> % (fade %s)".format(seat, pan.round(0.001), fade).postln;
        } {
            "[COTF] seatPan: bad args % — ignored".format(msg[1..]).postln;
        };
    }, '/airkit/seatPan');

    // --- [COTF] /airkit/masterLevel <linearGain> [fadeSec=0.5] (API.md) ------
    // Per-room overall output level, applied to every seat monitor. Additive:
    // independent of gain (voiceMute's 0/1) and trim (the boot-fixed room pad).
    // M0 owns persistence (settings) and re-asserts on our dead->alive.
    OSCdef(\cotfMasterLevel, { |msg|
        var level = msg[1].asFloat.clip(0, 4.0);
        var fade = (msg[2] ? 0.5).asFloat.max(0.05);
        ~cotfMasterLevel = level;
        ~cotfMonitors.do { |syn| syn.set(\masterLagT, fade, \masterGain, level) };
        "[COTF] masterLevel -> % (fade %s)".format(level.round(0.001), fade).postln;
    }, '/airkit/masterLevel');

    // --- [COTF] /airkit/seatTrim <seat 1-5> <linearGain> [fadeSec=0.5] (API.md)
    // Per-seat static trim on top of the room trim — the live tuning knob
    // behind ~cotfSeatTrims' baked defaults. Independent of gain (voiceMute's
    // 0/1) and masterGain (masterLevel); like them it never touches any other
    // seat. Not persisted by M0 — bake the tuned value into ~cotfSeatTrims.
    OSCdef(\cotfSeatTrim, { |msg|
        var seat = msg[1].asInteger.clip(1, 5);
        var level = msg[2].asFloat.clip(0, 2.0);
        var fade = (msg[3] ? 0.5).asFloat.max(0.05);
        ~cotfSeatTrims[seat - 1] = level;
        ~cotfMonitors.at(seat) !? { |syn|
            // Compose with the loaded patch's trim (2026-08-18) so live seat
            // tuning never strips the personality's baked compensation.
            syn.set(\trimLagT, fade,
                \trim, ~cotfMonitorTrim * level * ~cotfSeatPatchTrim[seat - 1]);
        };
        "[COTF] seatTrim: seat % -> % (fade %s)".format(seat, level.round(0.001), fade).postln;
    }, '/airkit/seatTrim');

    // --- [COTF] per-patch trim apply (2026-08-18) ----------------------------
    // Listens to the EXISTING local /airkit/personalityName broadcast that
    // personalityController.scd sends on every load (port, name) and applies
    // ~cotfPatchTrims for the loaded personality to that seat's monitor. Runs
    // alongside the other listeners on this address (own OSCdef key, purely
    // additive). "none" (resetSeat's torn-down label) and unknown names get 1.
    OSCdef(\cotfPatchTrim, { |msg|
        var port = msg[1].asInteger;
        var name = msg[2].asString;
        var d = ~devices.at(port);
        d !? {
            var seat = d.index;
            if ((seat >= 1) and: { seat <= 5 }) {
                var pTrim = ~cotfPatchTrims.at(name.asSymbol) ? 1.0;
                ~cotfSeatPatchTrim[seat - 1] = pTrim;
                ~cotfMonitors.at(seat) !? { |syn|
                    syn.set(\trimLagT, 0.5,
                        \trim, ~cotfMonitorTrim * ~cotfSeatTrims[seat - 1] * pTrim);
                };
                if (pTrim != 1.0) {
                    "[COTF] patchTrim: seat % (%) -> %".format(seat, name, pTrim.round(0.001)).postln;
                };
            };
        };
    }, '/airkit/personalityName');

    // --- [COTF] /airkit/testTone <seat 1-5> [durSec=1.0] (API.md) -----------
    // Speaker-test burst through the seat's monitor chain (so it proves
    // masterGain + the seat's L/C/R pan + the limiter end to end).
    OSCdef(\cotfTestTone, { |msg|
        var seat = msg[1].asInteger.clip(1, 5);
        var dur = (msg[2] ? 1.0).asFloat.clip(0.1, 5.0);
        ~cotfSeatBus.at(seat) !? { |bus|
            { SinOsc.ar(440 * seat.midiratio.max(1), 0, 0.25)
                * EnvGen.kr(Env.linen(0.02, dur - 0.1, 0.08), doneAction: 2) ! 2 }
                .play(s, bus.index, addAction: \addToHead);
            "[COTF] testTone seat % (%s)".format(seat, dur).postln;
        };
    }, '/airkit/testTone');

    NetAddr.localAddr.sendMsg("/airkit/startOSCListening", NetAddr.langPort);

    "COTF AirKit (demo) up: room %, langPort %, scsynth %, audio %, device %, out %"
        .format(~cotfRoom, NetAddr.langPort, s.addr.port, ~conductorAudioEnabled,
            s.options.outDevice, ~demoOut).postln;
});
)
