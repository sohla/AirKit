#!/bin/bash
# Read-only snapshot of how a working kit is set up. Changes nothing.
# Run from the Mac (password login is fine):
#   ssh pi@<kit> 'bash -s' < rpi_tools/survey-kit.sh | tee rpi_tools/reports/<kit>-survey.txt
# Wi-Fi secrets are never printed: nmcli hides them without -s, and psk lines
# are filtered anyway.

export XDG_RUNTIME_DIR="/run/user/$(id -u)"
export WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-0}"

s()   { echo; echo "=== $1"; }
run() { echo "\$ $*"; "$@" 2>&1; }
show() { for f in "$@"; do [ -e "$f" ] && { echo "--- $f"; cat "$f" 2>&1; } || echo "--- $f (absent)"; done; }
nopsk() { grep -viE 'psk|password|secret|wep-key'; }

s "system"
run hostname; run uname -a
tr -d '\0' < /proc/device-tree/model; echo
grep -E '^(PRETTY_NAME|VERSION_CODENAME)=' /etc/os-release
run uptime

s "desktop session"
run loginctl list-sessions --no-legend
ps -eo user,comm | grep -E 'labwc|wayfire|Xorg|Xwayland|lxsession|kanshi|lightdm|greetd' | sort -u
show /etc/lightdm/lightdm.conf 2>/dev/null | grep -E '^---|^autologin|^user-session|^greeter-session'
run sudo -n raspi-config nonint get_boot_behaviour
run sudo -n raspi-config nonint get_blanking

s "displays"
run wlr-randr
show ~/.config/kanshi/config

s "touch"
grep -A1 -E 'Name=.*(Goodix|Touch|touch)' /proc/bus/input/devices
show ~/.config/labwc/rc.xml

s "autostart"
show ~/.config/labwc/autostart /etc/xdg/labwc/autostart \
     /etc/xdg/lxsession/LXDE-pi/autostart ~/.config/lxsession/LXDE-pi/autostart
for f in ~/.config/autostart/*.desktop; do show "$f"; done
run crontab -l
run systemctl --user list-unit-files --state=enabled --no-legend
ls -la /etc/systemd/system/*.service 2>/dev/null

s "boot config"
grep -vE '^\s*(#|$)' /boot/firmware/config.txt
show /boot/firmware/cmdline.txt
run pinctrl get 23

s "audio"
show /proc/asound/cards
run aplay -l
show ~/.jackdrc
grep -iE '^(Server|Driver|Interface|Priority|Realtime|Frames|Periods|SampleRate)' \
     ~/.config/rncbc.org/QjackCtl.conf 2>/dev/null
for u in pipewire pipewire-pulse wireplumber; do echo "$u: $(systemctl --user is-active $u 2>&1) / $(systemctl --user is-enabled $u 2>&1)"; done
run id
for f in /etc/security/limits.d/*; do show "$f"; done
run ulimit -r
show ~/start_airkit.sh

s "supercollider"
D=~/supercolliderStandaloneRPI64
run git -C $D log -1 --format='%h %ad %s' --date=short
run git -C $D status --short
show $D/sclang.yaml ~/.config/SuperCollider/sc_ide_conf.yaml
ls $D/share/user/Extensions ~/.local/share/SuperCollider/downloaded-quarks 2>&1

s "airkit"
A=~/Develop/SuperCollider/Projects/AirKit
run git -C $A rev-parse --abbrev-ref HEAD
run git -C $A log -1 --format='%h %ad %s' --date=short
run git -C $A status --short
show $A/VERSION ~/version.json
ls -t ~/logs 2>&1 | head -5

s "network"
run nmcli -f NAME,TYPE,DEVICE,AUTOCONNECT con show
for c in $(nmcli -t -f NAME,TYPE con show | awk -F: '$2 ~ /wireless/{print $1}'); do
    echo "--- profile $c"
    nmcli con show "$c" | grep -E '^(802-11-wireless\.(ssid|mode|band|channel|mac-address|powersave|cloned)|802-11-wireless-security\.(key-mgmt|proto|pairwise|group|pmf)|ipv4\.(method|addresses)|connection\.(autoconnect|interface-name))' | nopsk
done
for f in /etc/NetworkManager/conf.d/*; do show "$f"; done
run iw dev
for w in $(ls /sys/class/net | grep ^wlan); do echo "$w power_save: $(iw dev $w get power_save 2>&1)"; done
grep -vE '^\s*(#|$)' /etc/modprobe.d/*.conf 2>/dev/null
run sudo -n iw reg get

s "updater"
run systemctl is-active airkit-updater
show /etc/systemd/system/airkit-updater.service

s "hand-installed packages"
run apt-mark showmanual
