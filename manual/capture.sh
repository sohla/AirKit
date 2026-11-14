#!/bin/bash
if [ "$(uname)" = "Darwin" ]; then
	cd /Applications/SuperCollider/SuperCollider.app/Contents/MacOS
	exec ./sclang ~/Develop/SuperCollider/Projects/AirKit/manual/capture.scd
else
	export DISPLAY=:0
	cd ~/supercolliderStandaloneRPI64
	exec ./sclang -a -l sclang.yaml ~/Develop/SuperCollider/Projects/AirKit/manual/capture.scd
fi
