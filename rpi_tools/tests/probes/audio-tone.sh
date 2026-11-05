#!/bin/bash
# Item 14: scsynth on JACK plays a 3 s 440 Hz tone, then idles; 30 s in all.
# Prints one line:
#   AIRKIT-AUDIO peak=<output peak> connected=<yes|no> xruns=<n> sr=<rate>
# peak is measured on scsynth's output bus, so it proves the server makes
# sound, not that the speaker plays it. That part is a person listening.
as_pi() { sudo -u pi XDG_RUNTIME_DIR=/run/user/1000 "$@"; }
SC=/home/pi/supercolliderStandaloneRPI64

if pgrep -u pi -x 'jackd|scsynth' >/dev/null; then
    echo "AIRKIT-AUDIO busy: jackd or scsynth already running (AirKit up?)"; exit 1
fi

work=$(mktemp -d); chmod 755 "$work"; trap 'rm -rf "$work"' EXIT

# Same jackd as AirKit gets: the line in ~/.jackdrc. Started here so its log
# (where xruns are reported) can be read.
as_pi bash -c "exec $(cat /home/pi/.jackdrc)" >"$work/jackd.log" 2>&1 &
jack=$!
as_pi jack_wait -w -t 10 >/dev/null 2>&1 || { echo "AIRKIT-AUDIO jackd did not start"; tail -3 "$work/jackd.log"; exit 1; }

# Level as start_airkit.sh sets it.
amixer -q -c S3 set Speaker 90%

cat > "$work/tone.scd" <<'SCD'
var s = Server.default;
AppClock.sched(60, { "AIRKIT-AUDIO timeout".postln; 1.exit });
s.waitForBoot({
    var peak = 0;
    OSCFunc({ |msg| peak = max(peak, max(msg[3], msg[5])) }, '/airkit_peak');
    SynthDef(\airkitTone, {
        var env = EnvGen.kr(Env.linen(0.05, 2.9, 0.05), doneAction: 2);
        Out.ar(0, SinOsc.ar(440, 0, 0.1) * env ! 2);
    }).add;
    SynthDef(\airkitMeter, { SendPeakRMS.kr(In.ar(0, 2), 10, 3, '/airkit_peak') }).add;
    s.sync;
    Synth.tail(s, \airkitMeter);
    Synth.head(s, \airkitTone);
    "AIRKIT-TONE-PLAYING".postln;
    27.wait;
    ("AIRKIT-AUDIO-SC peak=" ++ peak.round(0.001) ++ " sr=" ++ s.sampleRate.round).postln;
    s.quit;
    1.wait;
    0.exit;
});
SCD
chmod 644 "$work/tone.scd"

( sleep 6; as_pi jack_lsp -c > "$work/ports.txt" 2>&1 ) &
ports=$!
cd "$SC" && as_pi env QT_QPA_PLATFORM=offscreen timeout 90 ./sclang -a -l sclang.yaml "$work/tone.scd" > "$work/sclang.log" 2>&1
wait "$ports"

# Signal jackd itself: sudo doesn't pass a signal on to its child here.
pkill -u pi -x jackd; wait "$jack" 2>/dev/null

sc=$(grep -o 'AIRKIT-AUDIO-SC .*' "$work/sclang.log" | sed 's/AIRKIT-AUDIO-SC //')
[ -n "$sc" ] || { echo "AIRKIT-AUDIO scsynth failed"; grep -iE 'error|fail|timeout' "$work/sclang.log" | head -5; exit 1; }
# scsynth's out_1 must feed a system playback port.
connected=no
awk '/^SuperCollider:out_1$/{f=1;next} /^[^ ]/{f=0} f && /system:playback/{print; exit}' "$work/ports.txt" | grep -q . && connected=yes
xruns=$(grep -ciE 'xrun' "$work/jackd.log")
echo "AIRKIT-AUDIO ${sc% sr=*} connected=$connected xruns=$xruns sr=${sc##*sr=}"
