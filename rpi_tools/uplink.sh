#!/bin/bash
# Give a Pi internet through the Mac's Internet Sharing, when it took the
# N600's 10.1.1.x (the lab's two-DHCP race, see README). Same fix as net_ready:
# runtime only, nothing saved on the card, a reboot removes it.
#   ./uplink.sh [host]          default pi.local, e.g. ./uplink.sh 10.1.1.9
set -uo pipefail
source "$(dirname "$0")/lib.sh"

host="${1:-pi.local}"
gw=192.168.2.1; addr=192.168.2.250      # as ansible/roles/net_ready/defaults

ifconfig bridge100 2>/dev/null | grep -q "inet $gw " ||
    { echo "Mac Internet Sharing isn't up (no $gw on bridge100). Turn it on, sharing to Ethernet."; exit 1; }

pi_ssh "$host" bash -s -- "$gw" "$addr" <<'PI' || { echo "Cannot reach $host (or the uplink failed)" >&2; exit 1; }
gw=$1; addr=$2
net() { curl -4 -s -o /dev/null -m 6 http://deb.debian.org/debian/; }
if net; then echo "Internet already OK via $(ip route show default | head -1)"; exit 0; fi
ip -4 addr show dev eth0 | grep -q " $addr/" || sudo ip addr add "$addr/24" dev eth0
sudo ip route replace default via "$gw" dev eth0 src "$addr" metric 50
for i in 1 2 3; do net && { echo "Internet OK via the Mac ($gw); Pi also at $addr. Gone on reboot."; exit 0; }; sleep 2; done
echo "Still no internet via $gw"; exit 1
PI
