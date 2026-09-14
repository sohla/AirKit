#!/bin/bash
# Output dir: $AKDIAG (default ~/akdiag). Created on first run.
mkdir -p "${AKDIAG:-$HOME/akdiag}" 2>/dev/null
# AirKit wifi diagnosis — one command, prints a verdict.
#   sudo ~/akdiag/ak-wifi-diagnose.sh
# Works in AP mode (needs an associated station) or client mode (needs a gateway).
# The decisive test is TX PATH: does the chip actually transmit what Linux hands it?

P=/sys/kernel/debug/ieee80211/phy0
S=/sys/class/net/wlan0/statistics
ok(){ printf "  \033[32m%-8s\033[0m %s\n" "[ OK ]" "$1"; }
bad(){ printf "  \033[31m%-8s\033[0m %s\n" "[FAIL]" "$1"; }
inf(){ printf "  %-8s %s\n" "" "$1"; }

echo "===================================================================="
echo " AirKit wifi diagnosis   $(date '+%Y-%m-%d %H:%M:%S')   kernel $(uname -r)"
echo "===================================================================="

echo; echo "-- 1. INTERFACE ---------------------------------------------------"
MODE=$(iw dev wlan0 info 2>/dev/null | awk '/type/{print $2}')
SSID=$(iw dev wlan0 info 2>/dev/null | awk '/ssid/{print $2}')
CH=$(iw dev wlan0 info 2>/dev/null | awk '/channel/{print $2}')
[ -n "$MODE" ] && ok "mode=$MODE ssid=${SSID:-none} channel=${CH:-none}" || bad "wlan0 not present"
inf "ip: $(ip -br addr show wlan0 2>/dev/null | tr -s ' ')"
inf "nm: $(nmcli -t -f DEVICE,STATE,CONNECTION dev status 2>/dev/null | grep ^wlan0)"

echo; echo "-- 2. KNOWN-GOOD SETTINGS -----------------------------------------"
PS=$(iw dev wlan0 get power_save 2>/dev/null | awk '{print $3}')
[ "$PS" = "off" ] && ok "power save off" || bad "power save is $PS  -> iw dev wlan0 set power_save off"
PMF=$(nmcli -f 802-11-wireless-security.pmf con show AirKit2_Network 2>/dev/null | awk '{print $2}')
[ "$PMF" = "1" ] && ok "PMF disabled (fixes the 10-100s join delay)" || bad "PMF=$PMF -> nmcli con modify AirKit2_Network 802-11-wireless-security.pmf 1"
inf "chip regdom : $(iw phy0 reg get 2>/dev/null | awk '/country/{print $2, $3; exit}')  (99/DFS-UNSET is normal here, harmless)"
inf "kernel regdom: $(iw reg get 2>/dev/null | awk '/country/{print $2, $3; exit}')"

echo; echo "-- 3. POWER & BUS -------------------------------------------------"
PM=$(vcgencmd pmic_read_adc 2>/dev/null)
WV=$(echo "$PM" | awk -F= '/3V7_WL_SW_V/{printf "%.3f",$2}')
E5=$(echo "$PM" | awk -F= '/EXT5V_V/{printf "%.3f",$2}')
TH=$(vcgencmd get_throttled 2>/dev/null | cut -d= -f2)
[ "$TH" = "0x0" ] && ok "throttled=$TH  wifi rail ${WV}V  ext5v ${E5}V  $(vcgencmd measure_temp 2>/dev/null)" \
                  || bad "throttled=$TH (undervoltage/thermal) rail ${WV}V"
C=$(cat $P/counters 2>/dev/null)
SDE=$(echo "$C" | awk '/tx_sderrs/{print $2}'); RCE=$(echo "$C" | awk '/rxc_errors/{print $2}')
[ "${SDE:-0}" = "0" ] && [ "${RCE:-0}" = "0" ] && ok "SDIO bus clean (tx_sderrs=$SDE rxc_errors=$RCE)" || bad "SDIO errors tx_sderrs=$SDE rxc_errors=$RCE"
inf "netdev errors: rx_err=$(cat $S/rx_errors) tx_err=$(cat $S/tx_errors) rx_drop=$(cat $S/rx_dropped)"

echo; echo "-- 4. TX PATH  (the decisive test) --------------------------------"
# find a peer to transmit to
PEER=""
if [ "$MODE" = "AP" ]; then
  PEER=$(ip neigh show dev wlan0 2>/dev/null | awk '/lladdr/{print $1; exit}')
  [ -z "$PEER" ] && PEER=$(iw dev wlan0 station dump 2>/dev/null | awk '/^Station/{print $2; exit}')
else
  PEER=$(ip route 2>/dev/null | awk '/default.*wlan0/{print $3; exit}')
fi
if [ -z "$PEER" ] || ! echo "$PEER" | grep -q '\.'; then
  bad "no peer to test against (AP: need an associated client; client: need a gateway)"
  inf "connect something to wlan0, then re-run"
else
  WIP=$(ip -4 -o addr show wlan0 2>/dev/null | awk '{print $4}' | cut -d/ -f1)
  N0=$(cat $S/tx_packets); F0=$(iw dev wlan0 link 2>/dev/null | awk '/^\tTX:/{print $2}')
  [ -z "$F0" ] && F0=$(iw dev wlan0 station dump 2>/dev/null | awk '/tx bytes/{print $3; exit}')
  ping -c 15 -i 0.2 -W 1 ${WIP:+-I $WIP} "$PEER" >/dev/null 2>&1
  sleep 2
  N1=$(cat $S/tx_packets); F1=$(iw dev wlan0 link 2>/dev/null | awk '/^\tTX:/{print $2}')
  [ -z "$F1" ] && F1=$(iw dev wlan0 station dump 2>/dev/null | awk '/tx bytes/{print $3; exit}')
  ND=$((N1-N0)); FD=$(( ${F1:-0} - ${F0:-0} ))
  inf "peer $PEER   netdev tx +$ND frames   firmware tx +$FD bytes"
  if [ "$ND" -gt 0 ] && [ "$FD" -eq 0 ]; then
    bad "TX PATH DEAD — Linux sent $ND frames, the chip transmitted 0 bytes"
    inf ">>> This is the fault. The radio accepts frames and does not transmit them."
    inf ">>> Not fixable in software. See ~/akdiag/warranty-claim.txt"
  elif [ "$ND" -gt 0 ] && [ "$FD" -gt 0 ]; then
    ok "TX path working ($ND frames -> $FD bytes on air)"
  else
    inf "inconclusive (netdev didn't move; is the peer reachable?)"
  fi
fi

echo; echo "-- 5. STATIONS (AP mode) ------------------------------------------"
NS=$(iw dev wlan0 station dump 2>/dev/null | grep -c '^Station')
if [ "$NS" -gt 0 ]; then
  iw dev wlan0 station dump 2>/dev/null | grep -E '^Station|inactive time|tx failed|rx bytes|connected time' | sed 's/^/  /'
  inf "ghost check: high 'inactive time' + climbing 'tx failed' = dead station the AP still holds"
else
  inf "no stations associated"
fi

echo; echo "-- 6. FIRMWARE CONSOLE -------------------------------------------"
cat $P/forensics 2>/dev/null | sed 's/^[0-9]*\.[0-9]*[[:space:]]*//; s/^wl0: //' | grep -v '^$' \
  | sort | uniq -c | sort -rn | head -6 | sed 's/^/  /'
inf "('tdls_sta_info' and 'No PMKID' are harmless noise)"

echo; echo "-- 7. RECENT RADIO EVENTS ----------------------------------------"
journalctl --since "-10min" --no-pager -o short-precise -u wpa_supplicant 2>/dev/null \
  | sed 's/.*wlan0: //' | grep -iE 'AP-ENABLED|AP-STA|PSK-MISMATCH|ASSOC-REJECT|EAPOL|CONNECTED' | tail -8 | sed 's/^/  /'
echo
echo "===================================================================="
