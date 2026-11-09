#!/bin/bash
mkdir -p ~/logs
log=~/logs/$(date +"%Y-%m-%d-%Hh%M")-log.txt

if [ "$(uname)" = "Darwin" ]; then
	cd /Applications/SuperCollider/SuperCollider.app/Contents/MacOS
	exec ./sclang ~/Develop/SuperCollider/Projects/AirKit/code3.0/main.sc 2>&1 | tee "$log"
else
	sleep 2
	amixer -c 2 set Speaker 90%
	export DISPLAY=:0
	sleep 1
	cd ~/supercolliderStandaloneRPI64
	exec ./sclang -a -l sclang.yaml ~/Develop/SuperCollider/Projects/AirKit/code3.0/main.sc 2>&1 | tee "$log"
fi
