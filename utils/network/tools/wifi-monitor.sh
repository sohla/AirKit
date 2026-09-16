#!/bin/bash
# Output dir: $AKDIAG (default ~/akdiag). Created on first run.
AKHOME="$(getent passwd "${SUDO_USER:-$USER}" | cut -d: -f6)"; AKDIAG="${AKDIAG:-${AKHOME:-$HOME}/akdiag}"; mkdir -p "$AKDIAG" 2>/dev/null

# Which radio is the AP on? override with WIF=wlanN
WIF="${WIF:-$(for i in $(ls /sys/class/net | grep -E "^wlan"); do [ "$(iw dev $i info 2>/dev/null | awk "/type/{print \$2}")" = "AP" ] && echo $i && break; done)}"
WIF="${WIF:-$(iw dev 2>/dev/null | awk "/Interface/{print \$2; exit}")}"
WIF="${WIF:-${WIF}}"
# Continuous wifi-board monitor. Read-only. Logs radio, power, bus and driver state.
#   sudo ~/akdiag/wifi-monitor.sh [seconds]      default 3600
# While it runs, note (by clock time) whenever the AP vanishes on another machine.
DUR=${1:-3600}
OUT=${AKDIAG}/wifimon-$(date +%Y%m%d-%H%M%S)
P=/sys/kernel/debug/ieee80211/$(cat /sys/class/net/${WIF}/phy80211/name 2>/dev/null || echo phy0)
S=/sys/class/net/${WIF}/statistics

echo "logging ${DUR}s -> $OUT.*"

# --- event streams ---
iw event -t > "$OUT.events" 2>&1 &                     E=$!
journalctl -f -k -o short-precise --since now 2>/dev/null | grep --line-buffered -iE 'brcmf|wlan|mmc1|ieee80211' > "$OUT.kernel" &  K=$!
journalctl -f -u wpa_supplicant -u NetworkManager -o short-precise --since now > "$OUT.nm" 2>&1 &  J=$!

# --- firmware console poller ---
( PREV=""; while :; do
    C=$(cat $P/forensics 2>/dev/null | tail -5)
    [ "$C" != "$PREV" ] && { echo "=== $(date +%H:%M:%S.%3N)"; echo "$C"; PREV="$C"; }
    sleep 2
  done ) > "$OUT.fwconsole" 2>&1 & F=$!

# --- sampler ---
( echo -e "time\tstations\tnet_rx\tnet_tx\trx_err\ttx_err\trx_drop\twl_V\twl_A\text5V\ttemp\tthrottled\tchan\ttxpwr\tsd_txerr\tsd_rxcerr\tsd_badseq\tsd_regfail\tsta_rx\tsta_txfail"
  while :; do
    D=$(iw dev ${WIF} station dump 2>/dev/null)
    N=$(echo "$D" | grep -c '^Station')
    SR=$(echo "$D" | awk '/rx packets/{s+=$3} END{print s+0}')
    SF=$(echo "$D" | awk '/tx failed/{s+=$3} END{print s+0}')
    PM=$(vcgencmd pmic_read_adc 2>/dev/null)
    WV=$(echo "$PM" | awk -F'=' '/3V7_WL_SW_V/{printf "%.3f",$2}')
    WA=$(echo "$PM" | awk -F'=' '/3V7_WL_SW_A/{printf "%.4f",$2}')
    E5=$(echo "$PM" | awk -F'=' '/EXT5V_V/{printf "%.3f",$2}')
    IW=$(iw dev ${WIF} info 2>/dev/null)
    CH=$(echo "$IW" | awk '/channel/{print $2}')
    TP=$(echo "$IW" | awk '/txpower/{print $2}')
    CT=$(cat $P/counters 2>/dev/null)
    printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
      "$(date +%H:%M:%S)" "$N" "$(cat $S/rx_packets)" "$(cat $S/tx_packets)" \
      "$(cat $S/rx_errors)" "$(cat $S/tx_errors)" "$(cat $S/rx_dropped)" \
      "$WV" "$WA" "$E5" "$(vcgencmd measure_temp 2>/dev/null | tr -dc '0-9.')" \
      "$(vcgencmd get_throttled 2>/dev/null | cut -d= -f2)" "$CH" "$TP" \
      "$(echo "$CT" | awk '/tx_sderrs/{print $2}')" "$(echo "$CT" | awk '/rxc_errors/{print $2}')" \
      "$(echo "$CT" | awk '/rx_badseq/{print $2}')" "$(echo "$CT" | awk '/regfails/{print $2}')" \
      "$SR" "$SF"
    sleep 2
  done ) > "$OUT.samples" 2>&1 & M=$!

sleep "$DUR"
kill $E $K $J $F $M 2>/dev/null
echo "done -> $OUT.*"
