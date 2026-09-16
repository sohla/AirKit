#!/bin/bash
# Output dir: $AKDIAG (default ~/akdiag). Created on first run.
AKHOME="$(getent passwd "${SUDO_USER:-$USER}" | cut -d: -f6)"; AKDIAG="${AKDIAG:-${AKHOME:-$HOME}/akdiag}"; mkdir -p "$AKDIAG" 2>/dev/null

# Which radio is the AP on? override with WIF=wlanN
WIF="${WIF:-$(for i in $(ls /sys/class/net | grep -E "^wlan"); do [ "$(iw dev $i info 2>/dev/null | awk "/type/{print \$2}")" = "AP" ] && echo $i && break; done)}"
WIF="${WIF:-$(iw dev 2>/dev/null | awk "/Interface/{print \$2; exit}")}"
WIF="${WIF:-${WIF}}"
# Trace the full working sequence: restart network -> stick connects -> appears in app.
#   sudo ~/akdiag/osc-trace.sh [seconds] [--restart]
# With --restart the script restarts NetworkManager itself and tells you when to
# power the stick on. Without it, hit the Network button yourself after CAPTURING.
DUR=${1:-150}
OUT=${AKDIAG}/osctrace-$(date +%H%M%S)

echo "capturing ${DUR}s -> $OUT.pcap"
# -i any so the capture survives ${WIF} going down during the NM restart
tcpdump -i any -n -s0 -U -w "$OUT.pcap" udp >/dev/null 2>&1 &
TPID=$!
journalctl -f -u wpa_supplicant -u NetworkManager -o short-precise --since "now" > "$OUT.assoc" 2>&1 &
JPID=$!
sleep 1

if [ "$2" = "--restart" ]; then
  echo "  $(date +%H:%M:%S.%3N)  restarting NetworkManager..."
  systemctl restart NetworkManager
  for i in $(seq 1 30); do
    grep -q 'AP-ENABLED' "$OUT.assoc" && break
    sleep 1
  done
  echo "  $(date +%H:%M:%S.%3N)  AP is up"
fi
echo ">>> POWER THE STICK ON NOW <<<"
sleep "$DUR"
kill $TPID $JPID 2>/dev/null; sleep 1

echo
echo "=============== RADIO / ASSOCIATION ==============="
grep -E 'AP-ENABLED|AP-DISABLED|AP-STA-CONNECTED|PSK-MISMATCH|AP-STA-DISCONNECTED' "$OUT.assoc" \
  | sed 's/.*wpa_supplicant\[[0-9]*\]: ${WIF}: //; s/.*NetworkManager\[[0-9]*\]: //' | cut -c1-100

echo
echo "=============== CONFIG EXCHANGE ==============="
tcpdump -r "$OUT.pcap" -n -tttt -A udp 2>/dev/null | awk '
  $1 ~ /^20[0-9][0-9]-/ { t=$2; for(i=3;i<=NF;i++) if($i=="IP"){s=$(i+1); d=$(i+3); sub(/:$/,"",d); break} next }
  /GetConfig/        { printf "  %-16s GETCONFIG  %s -> %s\n", t, s, d; next }
  /\/[0-9]+\/Config/ { printf "  %-16s CONFIG     %s -> %s\n", t, s, d; next }
'
echo "  (nothing above = the config exchange never completed)"

echo
echo "=============== PER-SOURCE SUMMARY ==============="
tcpdump -r "$OUT.pcap" -n -tttt -A udp 2>/dev/null | awk '
  $1 ~ /^20[0-9][0-9]-/ { t=$2; for(i=3;i<=NF;i++) if($i=="IP"){s=$(i+1); sub(/\.[0-9]*$/,"",s); break} next }
  /IMUFusedData/     { if(!(s in f)) f[s]=t; n[s]++ ; next }
  /\/[0-9]+\/Config/ { if(!(s in c)) c[s]=t ; next }
  END {
    printf "  %-16s %-16s %-16s %8s\n","SOURCE","FIRST DATA","CONFIG REPLY","PKTS"
    for (s in n) printf "  %-16s %-16s %-16s %8d\n", s, f[s], (s in c)?c[s]:"-never-", n[s]
  }'
echo
echo "pcap: $OUT.pcap"
