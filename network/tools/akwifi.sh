#!/bin/bash
# Output dir: $AKDIAG (default ~/akdiag). Created on first run.
mkdir -p "${AKDIAG:-$HOME/akdiag}" 2>/dev/null
# AirKit wifi diagnostics. Never scans - scanning pulls the AP off-channel.
# Lives in ~/akdiag because /tmp is wiped on boot.
#
#   sudo ~/akdiag/akwifi.sh snap <label>   point-in-time state -> ~/akdiag/snap-<label>.txt
#   sudo ~/akdiag/akwifi.sh timeline       reconstruct this boot's A/B from the journal
#   sudo ~/akdiag/akwifi.sh watch          follow station events live

IF=wlan0
OUT=${AKDIAG:-$HOME/akdiag}

snap () {
  local f="$OUT/snap-${1:-unlabelled}.txt"
  {
    echo "== label: ${1:-unlabelled}  at: $(date -Is)  uptime: $(uptime -p)  boot: $(uptime -s)"
    echo; echo "== nm restarts this boot"
    journalctl -b -o short-precise | grep -c 'Started NetworkManager.service'
    journalctl -b -o short-precise | grep -E 'COMMAND=/usr/bin/systemctl restart NetworkManager' | sed 's/.*pi sudo[^:]*: *//'
    echo; echo "== nmcli dev";      nmcli -t -f DEVICE,TYPE,STATE,CONNECTION dev status
    echo; echo "== nmcli con";      nmcli -t -f NAME,TYPE,DEVICE,ACTIVE,AUTOCONNECT con show
    echo; echo "== ap profile";     nmcli -f 802-11-wireless,802-11-wireless-security con show AirKit2_Network | grep -viE ':[[:space:]]*--[[:space:]]*$'
    echo; echo "== iw dev";         iw dev
    echo; echo "== stations";       iw dev $IF station dump
    echo; echo "== station count";  iw dev $IF station dump | grep -c '^Station'
    echo; echo "== iw link";        iw dev $IF link
    echo; echo "== power save";     iw dev $IF get power_save
    echo; echo "== reg";            iw reg get | head -4
    echo; echo "== rfkill";         rfkill list
    echo; echo "== ip addr";        ip -br addr
    echo; echo "== neigh";          ip neigh
    echo; echo "== dnsmasq proc";   pgrep -a dnsmasq
    echo; echo "== dhcp leases";    cat /var/lib/NetworkManager/dnsmasq-$IF.leases 2>/dev/null || echo "(none)"
    echo; echo "== units";          systemctl is-active NetworkManager wpa_supplicant
    echo; echo "== nm/supplicant since boot";
    journalctl -b -o short-precise -u NetworkManager -u wpa_supplicant | tail -120
  } > "$f" 2>&1
  echo "wrote $f"
}

timeline () {
  echo "== boot $(uptime -s) ; now $(date -Is)"
  echo "== every AP lifecycle + station event, in order"
  journalctl -b --no-pager -o short-precise \
    | grep -iE 'AP-STA|AP-ENABLED|AP-DISABLED|DHCPDISCOVER|DHCPREQUEST|DHCPACK|DHCPNAK|Started NetworkManager\.service|COMMAND=/usr/bin/systemctl restart' \
    | sed 's/pi wpa_supplicant\[[0-9]*\]: wlan0: //; s/pi dnsmasq-dhcp\[[0-9]*\]: //; s/pi systemd\[1\]: //; s/pi sudo\[[0-9]*\]: *//'
}

watch_ () {
  journalctl -f -o short-precise -u wpa_supplicant -u NetworkManager \
    | grep --line-buffered -iE 'AP-STA|AP-ENABLED|AP-DISABLED|DHCP'
}

case "$1" in
  snap)     snap "$2" ;;
  timeline) timeline ;;
  watch)    watch_ ;;
  *) sed -n '2,8p' "$0" ;;
esac
