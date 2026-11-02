#!/bin/bash
# Shut the Pi down and say when it's safe to remove power.
#   ./poweroff.sh [host]           default pi.local
# SSH stops answering before the last writes to the card, so "ssh is gone"
# alone is not safe. Pulling power early corrupts the filesystem (round 2b).
set -uo pipefail
source "$(dirname "$0")/lib.sh"
host="${1:-pi.local}"
settle=20

ip=$(ping -c1 -t3 "$host" 2>/dev/null | sed -n 's/^PING [^(]*(\([0-9.]*\)).*/\1/p')
[ -n "$ip" ] || { echo "$host not found." >&2; exit 1; }

pi_ssh "$ip" 'sudo systemctl poweroff' 2>/dev/null
echo "Shutting down $host ($ip) ..."

for i in $(seq 1 60); do
    ping -c1 -t1 "$ip" >/dev/null 2>&1 || break
    sleep 1
done
echo "Network gone. Waiting ${settle}s more for the card to finish ..."
sleep $settle

echo
echo "SAFE to remove power once the Pi 5's LED is solid red (or off)."
echo "If it is still flashing green, wait until it stops."
