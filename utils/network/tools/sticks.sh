#!/bin/bash
# Which Airsticks are on this kit's AP, and are they sending?
#   sudo network/tools/sticks.sh [seconds]       default 2
# Read-only and safe with AirKit running: never scans (scanning pulls the AP
# off-channel) and doesn't touch port 57120. Sending = packets/s the radio
# receives from the stick, measured over [seconds]; a streaming stick is ~100+.
PATH="$PATH:/usr/sbin:/sbin"
secs="${1:-2}"
# Which radio is the AP on? override with WIF=wlanN
WIF="${WIF:-$(for i in $(ls /sys/class/net | grep -E "^wlan"); do [ "$(iw dev $i info 2>/dev/null | awk "/type/{print \$2}")" = "AP" ] && echo $i && break; done)}"
[ -n "$WIF" ] || { echo "No AP running (no wlan interface in AP mode)"; exit 1; }
[ "$(id -u)" = 0 ] || { echo "Run with sudo"; exit 1; }

rx() { iw dev "$WIF" station dump | awk '/^Station/{m=$2} /rx packets:/{print m, $3}'; }
before=$(rx); sleep "$secs"; after=$(rx)

ssid=$(iw dev "$WIF" info | awk '$1=="ssid"{print $2}')
echo "$ssid on $WIF: $(echo "$after" | grep -c .) connected"
[ -n "$after" ] || exit 0
printf '%-17s  %-15s  %7s  %9s\n' MAC IP "pkt/s" connected
echo "$after" | while read -r mac n; do
    n0=$(echo "$before" | awk -v m="$mac" '$1==m{print $2}')
    rate=$(( (n - ${n0:-$n}) / secs ))
    ip=$(ip neigh show dev "$WIF" | awk -v m="$mac" '$3==m{print $1; exit}')
    up=$(iw dev "$WIF" station get "$mac" | awk '/connected time/{print $3}')
    printf '%-17s  %-15s  %7s  %8ss\n' "$mac" "${ip:--}" "$rate" "${up:-?}"
done
