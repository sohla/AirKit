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
var numOut = ("DEMO_NUM_OUT".getenv ? "8").asInteger;
var outL = ("DEMO_OUT_L".getenv ? "0").asInteger;
var outC = ("DEMO_OUT_C".getenv ? "1").asInteger;
var outR = ("DEMO_OUT_R".getenv ? "2").asInteger;

if (outDevice.isNil or: { outDevice.isEmpty }) {
    Error("COTF demo: DEMO_OUT_DEVICE is not set (exact CoreAudio output device name, e.g. \"Mac Studio Speakers\")").throw;
};
if (numOut < 1) {
    Error("COTF demo: DEMO_NUM_OUT must be >= 1 (got %)".format(numOut)).throw;
};
[["DEMO_OUT_L", outL], ["DEMO_OUT_C", outC], ["DEMO_OUT_R", outR]].do { |pair|
    if ((pair[1] < 0) or: { pair[1] >= numOut }) {
        Error("COTF demo: % = % is outside 0..% (DEMO_NUM_OUT = %)"
            .format(pair[0], pair[1], numOut - 1, numOut)).throw;
    };
};

// Room 3 so every seat-keyed helper copied from the show profile (patch
// trims, flat seat trims, no Room 2 pad) behaves as Room 3 does.
~cotfRoom = 3;
~expectedDevice = outDevice;
~patchLabel = "AirKit/Demo";
~demoOut = (L: outL, C: outC, R: outR);
// bind-check.scd's channel-count probe reads the SHOW Aggregate by name
// ("Aggregate (MOTU+BlackHole)"), which is meaningless here — and on M1, where
// that Aggregate still exists at 16 ch without the MOTU, it would refuse the
// boot (and exit under PM2). 0 disables the count gate; the device-name check
// still runs, and scsynth itself fails loudly if the device can't be opened.
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
        Error("COTF demo: missing data file (from the composer; archive: /Users/m0/cotf-assets/airkit-data/; the .wav is read for its sample rate only — QLab plays the audio): %".format(p)).throw;
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
