#!/bin/bash
# Every device on this Mac's networks, and which are Airsticks. macOS version of sticks.sh.
#   sudo utils/network/tools/sticks-mac.sh            every network
#   sudo IF=en10 utils/network/tools/sticks-mac.sh    one interface
# Asks every address for its config (/Config/GetConfig on port 8888, read-only, as
# stick-config.py --show). Every stick answers, configured or not, with its ID;
# an unset ID shows as the stick's MAC. Everything else on the network is listed
# from the Mac's ARP table. Safe with AirKit running: nothing touches port 57120.
[ "$(id -u)" = 0 ] || { echo "Run with sudo"; exit 1; }

ask='/Config/GetConfig\0\0\0,i\0\0\0\0\047\017'   # reply to port 9999

toint() { local IFS=.; set -- $1; echo $(( ($1 << 24) + ($2 << 16) + ($3 << 8) + $4 )); }
toip()  { echo "$(( $1 >> 24 & 255 )).$(( $1 >> 16 & 255 )).$(( $1 >> 8 & 255 )).$(( $1 & 255 ))"; }

# "en10 192.168.50.53 0xffffff00" for every up IPv4 interface but loopback and VPNs
nets=$(ifconfig | awk '/^[a-z]/{i=$1; sub(":","",i)} $1=="inet" && i !~ /^(lo|utun)/ {print i, $2, $4}')
[ -n "$IF" ] && nets=$(echo "$nets" | awk -v i="$IF" '$1==i')
[ -n "$nets" ] || { echo "No IPv4 network${IF:+ on $IF}"; exit 1; }

cap=$(mktemp)
tcpdump -i any -n -l -A "udp src port 8888 and dst port 9999" > "$cap" 2>/dev/null &
pid=$!
sleep 0.5

echo "$nets" | while read -r i me mask; do
    m=$(( mask )); [ $(( m & 0x3ff )) = 0 ] && { echo "skipping $i: bigger than /22"; continue; }
    net=$(( $(toint "$me") & m )); last=$(( net | (~m & 0xffffffff) ))
    for (( h = net + 1; h < last; h++ )); do
        printf "$ask" 2>/dev/null > /dev/udp/$(toip $h)/8888
    done
done

sleep 2
kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null

# "... IP 192.168.50.101.8888 > ..." then the payload, where the reply's address reads /<ID>/Config
sticks=$(awk '$2=="IP"{split($3,a,"."); ip=a[1]"."a[2]"."a[3]"."a[4]}
              match($0, /\/[A-Za-z0-9:_-]+\/Config/){print ip, substr($0, RSTART+1, RLENGTH-8)}' "$cap" | sort -u)
rm -f "$cap"

echo "$nets" | while read -r i me mask; do
    devs=$(arp -an -i "$i" | awk '$4 ~ /:/ && $4 != "ff:ff:ff:ff:ff:ff" && $2 !~ /^\(224\./ {gsub(/[()]/,"",$2); print $2, $4}' | grep -v "^$me ")
    echo
    echo "$i (this Mac $me): $(echo "$devs" | grep -c .) devices"
    printf '  %-15s  %-17s  %s\n' IP MAC stick
    echo "$devs" | sort -t. -k4 -n | while read -r ip mac; do
        [ -n "$ip" ] || continue
        id=$(echo "$sticks" | awk -v p="$ip" '$1==p{print $2; exit}')
        case "$id" in *:*) id="ID not set" ;; ?*) id="id $id" ;; esac
        printf '  %-15s  %-17s  %s\n' "$ip" "$mac" "$id"
    done
done
