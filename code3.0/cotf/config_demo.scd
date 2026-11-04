// COTF DEMO configuration (SensiLab demo, 2026-11) — loaded by
// cotf/main_demo.scd BEFORE the shared controllers. COTF-only; neither the
// composer's main.sc nor the show profile (main_cotf.scd / config.scd) ever
// loads this file. OSC contract: ../API.md.
//
// Differences from config.scd (the show config, untouched):
//   - output device + channel count come from env, not the show Aggregate
//     (no MOTU, no BlackHole, no webrtc/physical outputs);
//   - every seat is mixed into an L/C/R trio of output buses (~demoOut);
//   - QLab owns the Beethoven audio, always (~conductorAudioEnabled = false).
//
// Env (set by the demo boot script):
//   DEMO_OUT_DEVICE  REQUIRED — exact CoreAudio output device name
//   DEMO_NUM_OUT     output channel count to open (default 8)
//   DEMO_OUT_L/C/R   0-based output buses for Left/Centre/Right (default 0/1/2)
//   COTF_HW_BUFFER   optional, same opt-in as config.scd
(
var repoRoot = thisProcess.nowExecutingPath.dirname.dirname.dirname;
var outDevice = "DEMO_OUT_DEVICE".getenv;
// Every refusal is ONE clear line + exit(1), before the server boots, so the
// boot script / PM2 sees a non-zero exit instead of a silent half-boot.
// sclang's exit is deferred until the interpreter is idle, so `fail` also
// aborts this file with a non-local return (^) at the FIRST failure, and sets
// ~demoConfigError, which main_demo.scd checks to abort before the
// controllers load and the server boots.
var fail = { |msg|
    ~demoConfigError = msg;
    ("ERROR: COTF demo: " ++ msg).postln;
    1.exit;
    ^nil
};
// Non-negative integer env, default when unset; anything else is refused
// (asInteger would silently read "two" or "1.5" as 0 / 1).
var envInt = { |name, default|
    var v = name.getenv;
    if (v.isNil or: { v.isEmpty }) { default } {
        if (v.every(_.isDecDigit).not) {
            fail.("% = \"%\" is not a non-negative integer".format(name, v));
        };
        v.asInteger
    }
};
var numOut, outL, outC, outR, devChans;

~demoConfigError = nil;

if (outDevice.isNil or: { outDevice.isEmpty }) {
    fail.("DEMO_OUT_DEVICE is not set (exact CoreAudio output device name, e.g. \"Mac Studio Speakers\")");
};
numOut = envInt.("DEMO_NUM_OUT", 8);
outL = envInt.("DEMO_OUT_L", 0);
outC = envInt.("DEMO_OUT_C", 1);
outR = envInt.("DEMO_OUT_R", 2);
if (numOut < 1) {
    fail.("DEMO_NUM_OUT must be >= 1 (got %)".format(numOut));
};

// --- output device check (real, unlike bind-check's name compare) ----------
// CoreAudio truth via system_profiler, read in sclang BEFORE boot — one
// source for both the device's existence and its real output channel count.
// 1. The device must exist as an OUTPUT device right now (exact name).
// 2. DEMO_NUM_OUT and every L/C/R bus must fit in its REAL output channel
//    count, or a speaker would be written to a channel that does not exist —
//    silent, with no error.
devChans = Dictionary.new;   // output device name -> output channel count
block {
    var json = "system_profiler SPAudioDataType -json 2>/dev/null".unixCmdGetStdOut.parseYAML;
    if (json.notNil and: { json["SPAudioDataType"].notNil }) {
        json["SPAudioDataType"].do { |grp|
            (grp["_items"] ? []).do { |dev|
                var n = (dev["coreaudio_device_output"] ? "0").asInteger;
                if (n > 0) { devChans.put(dev["_name"], n) };
            };
        };
    };
};
if (devChans.isEmpty) {
    fail.("could not list CoreAudio output devices (system_profiler SPAudioDataType gave no answer)");
};
if (devChans.keys.asArray.includesEqual(outDevice).not) {
    fail.("output device \"%\" not found; available: %"
        .format(outDevice, devChans.keys.asArray.sort.join(", ")));
};
devChans = devChans.at(outDevice);
if (numOut > devChans) {
    fail.("DEMO_NUM_OUT = % but \"%\" has only % output channels".format(numOut, outDevice, devChans));
};
[["DEMO_OUT_L", outL], ["DEMO_OUT_C", outC], ["DEMO_OUT_R", outR]].do { |pair|
    if (pair[1] >= numOut) {
        fail.("% = % is outside 0..% (DEMO_NUM_OUT = %, \"%\" has % output channels)"
            .format(pair[0], pair[1], numOut - 1, numOut, outDevice, devChans));
    };
};

// Room 3 so every seat-keyed helper copied from the show profile (patch
// trims, flat seat trims, no Room 2 pad) behaves as Room 3 does.
~cotfRoom = 3;
~patchLabel = "AirKit/Demo";
~demoOut = (L: outL, C: outC, R: outR);
// bind-check.scd (shared with the show, untouched) still runs from
// main_demo.scd, but under this profile it verifies NOTHING: its name check
// compares s.options.outDevice with ~expectedDevice, both set from
// DEMO_OUT_DEVICE here (always equal), and its channel-count probe reads the
// SHOW Aggregate by name — meaningless here, and on M1 (Aggregate present at
// 16 ch without the MOTU) it would refuse the boot and exit under PM2. So the
// count gate is disabled (0) and its "BOUND ... OK" line is informational
// only (its "(N ch)" is the Aggregate's count, not this device's). The real
// checks are the device-exists and channel-count checks above.
~expectedDevice = outDevice;
~expectedMinOutChannels = 0;

// --- data-file pre-check: fail with ONE clear line, not a mid-boot throw ----
// Same list as config.scd. The conductor still walks the score, so the beat,
// tick and meta files are needed. The .wav is needed too even though QLab
// owns the audio: conductorController.scd opens it UNCONDITIONALLY at load to
// read its sample rate/length (and pins the server rate to it); only DiskIn
// playback is skipped when ~conductorAudioEnabled is false.
[
    repoRoot +/+ "out" +/+ "Beethoven4_4_MSO_beats_2_mul4.txt",
    repoRoot +/+ "data" +/+ "audio" +/+ "Beethoven4_4_MSO.wav",
    repoRoot +/+ "data" +/+ "score" +/+ "tick_index_2.json",
    repoRoot +/+ "out" +/+ "Beethoven4_4_MSO_beats_2_mul4_meta.json",
].do { |p|
    if (File.exists(p).not) {
        fail.("missing data file (from the composer; archive: ~/cotf-assets/airkit-data/ on M0; the .wav is read for its sample rate only — QLab plays the audio): %".format(p));
    };
};

// --- server options: env-chosen device, demo channel count ------------------
s.options.outDevice = outDevice;
s.options.numOutputBusChannels = numOut;
s.options.numWireBufs = 1024;
s.options.memSize = 65536;
s.options.blockSize = 128;
// Same opt-in as config.scd — never set hardwareBufferSize unconditionally.
("COTF_HW_BUFFER".getenv !? { |v|
    if (v.asInteger > 0) { s.options.hardwareBufferSize = v.asInteger };
});
s.options.numBuffers = 2048; // sampler personalities load big libraries (matches composer main.sc)
s.latency = 0.05;
s.addr = NetAddr("127.0.0.1", 57110);

// QLab owns the Beethoven audio in the demo, always.
~conductorAudioEnabled = false;
)
