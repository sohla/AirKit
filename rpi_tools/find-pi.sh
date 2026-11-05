#!/bin/bash
# Find AirKit Pis on the wired LAN.
#   ./find-pi.sh            resolve pi.local by mDNS (no scanning)
#   ./find-pi.sh <name>     resolve <name>.local
#   ./find-pi.sh --scan     ping-sweep the /24 of every network this machine
#                           is on and list every Pi MAC.
set -u
source "$(dirname "$0")/lib.sh"

resolve() {
    ping -c1 -t3 "$1.local" 2>/dev/null | sed -n 's/^PING [^(]*(\([0-9.]*\)).*/\1/p'
}

if [ "${1:-}" != "--scan" ]; then
    ip=$(resolve "${1:-pi}")
    if [ -z "$ip" ]; then
        echo "${1:-pi}.local not found. Check the Pi has booted, then try --scan." >&2
        exit 1
    fi
    ssh_ok=no; nc -z -G 2 "$ip" 22 2>/dev/null && ssh_ok=yes
    echo "${1:-pi}.local  $ip  ssh:$ssh_ok"
    if [ -z "${1:-}" ] && [ -n "$(resolve pi-2)" ]; then
        echo "WARNING: pi-2.local also answers — two Pis named 'pi' are on this network." >&2
        exit 1
    fi
    exit 0
fi

scanned=""
for iface in $(ifconfig -l); do
    myip=$(ipconfig getifaddr "$iface" 2>/dev/null) || continue
    [ -n "$myip" ] || continue
    echo "Scanning ${myip%.*}.0/24 on $iface ..."
    nmap -sn -n "${myip%.*}.0/24" >/dev/null 2>&1
    scanned="$scanned $iface"
done
if [ -z "$scanned" ]; then echo "No active network interface." >&2; exit 1; fi

printf "\n%-16s %-18s %-8s %-12s %-5s %s\n" IP MAC IFACE KIT SSH HOSTNAME
found=0
while read -r ip mac iface; do
    mac=$(norm_mac "$mac")
    is_pi_mac "$mac" || continue
    found=1
    kit=$(kit_for_mac "$mac"); kit="${kit:-?}"
    if nc -z -G 2 "$ip" 22 2>/dev/null; then
        ssh_ok=yes
        host=$(pi_ssh "$ip" hostname 2>/dev/null || echo "(key not accepted)")
    else
        ssh_ok=no; host=-
    fi
    printf "%-16s %-18s %-8s %-12s %-5s %s\n" "$ip" "$mac" "$iface" "$kit" "$ssh_ok" "$host"
done < <(for i in $scanned; do arp -an -i "$i" | sed -n 's/^? (\([0-9.]*\)) at \([0-9a-fA-F:]*\) on \([a-z0-9]*\) .*/\1 \2 \3/p'; done)

[ $found = 0 ] && echo "(no Raspberry Pi MACs seen — is the Pi booted and on this switch?)"
echo
echo "KIT '?' means the MAC is not in machines/*.scd yet — add it to that kit's macs: list."
