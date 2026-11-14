#!/bin/bash
python3 ~/Develop/SuperCollider/Projects/AirKit/btbdemo/setlist.py || exit 1
if [ "$(uname)" = "Darwin" ]; then
	cd /Applications/SuperCollider/SuperCollider.app/Contents/MacOS
	exec ./sclang ~/Develop/SuperCollider/Projects/AirKit/btbdemo/record.scd
else
	export DISPLAY=:0
	cd ~/supercolliderStandaloneRPI64
	exec ./sclang -a -l sclang.yaml ~/Develop/SuperCollider/Projects/AirKit/btbdemo/record.scd
fi
