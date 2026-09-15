#!/bin/bash
# Benchmark whatever radio is currently running the AP, for comparing dongles.
#   sudo network/tools/wifi-benchmark.sh [seconds] [label]
# Needs sticks connected and streaming. Appends to $AKDIAG/benchmarks.md
AKHOME="$(getent passwd "${SUDO_USER:-$USER}" | cut -d: -f6)"; AKDIAG="${AKDIAG:-${AKHOME:-$HOME}/akdiag}"; mkdir -p "$AKDIAG" 2>/dev/null
OUT="${AKDIAG}/benchmarks.md"
DUR=${1:-20}
WIF="${WIF:-$(for i in $(ls /sys/class/net | grep -E '^wlan'); do [ "$(iw dev $i info 2>/dev/null | awk '/type/{print $2}')" = "AP" ] && echo $i && break; done)}"
WIF="${WIF:-wlan0}"

DRV=$(basename "$(readlink -f /sys/class/net/$WIF/device/driver 2>/dev/null)" 2>/dev/null)
USBID=$(lsusb 2>/dev/null | grep -iv 'root hub' | grep -iE 'wlan|wireless|802\.11|realtek|ralink|mediatek|atheros|d-link|tp-link' | head -1)
LABEL="${2:-${USBID:-$DRV}}"

echo "benchmarking $WIF ($DRV) for ${DUR}s ..."
NS=$(iw dev "$WIF" station dump 2>/dev/null | grep -c '^Station')
[ "$NS" -eq 0 ] && { echo "  no stations associated - connect the sticks first"; exit 1; }

sudo timeout $((DUR+1)) tcpdump -i "$WIF" -n -tt 'udp port 57120' 2>/dev/null > /tmp/bench.$$
STATS=$(awk '{ts=$1+0; s=$3; sub(/\.[0-9]+$/,"",s)
  if(p[s]!=""){d=(ts-p[s])*1000; n[s]++; sum[s]+=d; ds[s]=ds[s]" "d; if(d>25)late[s]++}
  p[s]=ts}
  END{for(s in n){m=sum[s]/n[s]; split(ds[s],a," "); v=0; for(i in a)v+=(a[i]-m)^2
    printf "| `%s` | %d | %.1f Hz | %.2f ms | %.2f ms | %d |\n", s, n[s]+1, 1000/m, m, sqrt(v/n[s]), late[s]+0}}' /tmp/bench.$$)
rm -f /tmp/bench.$$

{
  echo
  echo "## $LABEL"
  echo
  echo "- **when**: $(date '+%Y-%m-%d %H:%M')  ·  **iface**: \`$WIF\`  ·  **driver**: \`$DRV\`"
  echo "- **channel**: $(iw dev "$WIF" info 2>/dev/null | awk '/channel/{print $2}')  ·  **txpower**: $(iw dev "$WIF" info 2>/dev/null | awk '/txpower/{print $2" "$3}')"
  echo "- **regdom**: $(iw phy "$(cat /sys/class/net/$WIF/phy80211/name)" reg get 2>/dev/null | awk '/country/{print $2,$3; exit}')"
  [ -n "$USBID" ] && echo "- **usb**: $USBID"
  echo
  echo "### Link"
  echo
  echo "| station | signal | tx bitrate | tx retries | tx failed |"
  echo "|---|---|---|---|---|"
  iw dev "$WIF" station dump 2>/dev/null | awk '
    /^Station/{m=$2} /signal:/{s=$2} /tx bitrate:/{b=$3" "$4} /tx retries:/{r=$3} /tx failed:/{f=$3
      printf "| `%s` | %s dBm | %s | %s | %s |\n", m, s, b, r, f}'
  echo
  echo "### OSC stream (${DUR}s)"
  echo
  echo "| source | packets | rate | mean interval | jitter (sd) | gaps >25ms |"
  echo "|---|---|---|---|---|---|"
  echo "$STATS"
  echo
  echo "### Errors"
  echo
  echo '```'
  echo "netdev  rx_err=$(cat /sys/class/net/$WIF/statistics/rx_errors) tx_err=$(cat /sys/class/net/$WIF/statistics/tx_errors) rx_drop=$(cat /sys/class/net/$WIF/statistics/rx_dropped)"
  echo "rails   $(vcgencmd pmic_read_adc 2>/dev/null | awk -F= '/3V7_WL_SW_V/{printf "wl=%.3fV ",$2} /EXT5V_V/{printf "ext5v=%.3fV",$2}')  throttled=$(vcgencmd get_throttled 2>/dev/null | cut -d= -f2)"
  echo '```'
} | tee -a "$OUT"
echo
echo "appended to $OUT"
