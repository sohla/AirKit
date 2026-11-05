#!/bin/bash
# The standalone's sclang compiles the named classes (from quarks).
#   sc-classes.sh Canvas3D Canvas3DItem   → AIRKIT-CLASSES Canvas3D=true ...
cd /home/pi/supercolliderStandaloneRPI64 || exit 1
t=$(mktemp --suffix=.scd); trap 'rm -f "$t"' EXIT
list=$(printf '\\%s,' "$@")
printf '"AIRKIT-CLASSES".post; [%s].do { |c| " %%=%%".postf(c, c.asClass.notNil) }; "".postln; 0.exit;\n' "${list%,}" > "$t"
chmod 644 "$t"
sudo -u pi QT_QPA_PLATFORM=offscreen timeout 60 ./sclang -a -l sclang.yaml "$t" 2>&1 | grep '^AIRKIT-CLASSES'
