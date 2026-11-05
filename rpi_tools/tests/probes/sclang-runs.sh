#!/bin/bash
# The standalone's sclang starts headless, compiles its class library and
# interprets code. Prints AIRKIT-SCLANG-OK <version> on success.
cd /home/pi/supercolliderStandaloneRPI64 || exit 1
t=$(mktemp --suffix=.scd)
trap 'rm -f "$t"' EXIT
printf '"AIRKIT-SCLANG-OK %%\\n".postf(Main.version); 0.exit;\n' > "$t"
chmod 644 "$t"
sudo -u pi QT_QPA_PLATFORM=offscreen timeout 60 ./sclang -a -l sclang.yaml "$t" 2>&1 | grep -o 'AIRKIT-SCLANG-OK [0-9.]*'
