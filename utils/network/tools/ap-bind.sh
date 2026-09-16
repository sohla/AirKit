#!/bin/bash
# Bind the AirKit AP profile to whichever USB wifi radio is present, and bring it up.
#   sudo network/tools/ap-bind.sh [profile]        default AirKit2_Network
# Deliberately skips the internal brcmfmac radio: on airkit2 its TX path is faulty,
# and a profile that can fall back to it will silently do so.
PROFILE="${1:-AirKit2_Network}"

DONGLE=""
for i in $(ls /sys/class/net 2>/dev/null | grep -E '^wlan'); do
  d=$(basename "$(readlink -f /sys/class/net/$i/device/driver 2>/dev/null)" 2>/dev/null)
  [ "$d" = "brcmfmac" ] && continue
  DONGLE="$i"; DRV="$d"; break
done

if [ -z "$DONGLE" ]; then
  echo "no USB wifi radio found (only the internal brcmfmac, which is not trusted here)"
  echo "plug a dongle in, or bind manually:"
  echo "  sudo nmcli con modify $PROFILE 802-11-wireless.mac-address <MAC>"
  exit 1
fi

MAC=$(tr 'a-z' 'A-Z' < /sys/class/net/$DONGLE/address)
USBID=$(lsusb 2>/dev/null | grep -iv 'root hub' | grep -iE 'wlan|wireless|802\.11|realtek|ralink|mediatek|atheros|d-link|tp-link' | head -1)
echo "found  : $DONGLE  $MAC  driver=$DRV"
[ -n "$USBID" ] && echo "         $USBID"

if ! iw phy "$(cat /sys/class/net/$DONGLE/phy80211/name)" info 2>/dev/null | grep -qE '^\s+\* AP$'; then
  echo "WARNING: this radio does not advertise AP mode - it may not work as an access point"
fi

nmcli con modify "$PROFILE" 802-11-wireless.mac-address "$MAC" || exit 1
nmcli con modify "$PROFILE" connection.interface-name ""
iw dev "$DONGLE" set power_save off 2>/dev/null
nmcli con down "$PROFILE" >/dev/null 2>&1
sleep 1
nmcli con up "$PROFILE" | tail -1
sleep 4

echo
iw dev "$DONGLE" info 2>/dev/null | grep -E 'ssid|type|channel|txpower' | sed 's/^/  /'
ip -br addr show "$DONGLE" | sed 's/^/  /'
echo "  power save: $(iw dev $DONGLE get power_save 2>/dev/null | awk '{print $3}')"
