# Setup requirements

Your list in your order, with your notes kept. **Q:** lines are my questions.
Answer them inline under each one. **Check:** lines say how each step would be
proved: `auto` (goss or a probe script decides) or `human` (someone looks,
listens or touches).

---

## A. Card and access

### 1. Install OS on card
Raspberry Pi Imager. Hostname `pi`, user `pi`, SSH on, no Wi-Fi.
- **Status:** passing (round 1)
- **Q:** Wi-Fi country. Imager only sets it when Wi-Fi is configured, and with
  no country set wlan0 can stay blocked, which would stop the AP in 18. AU?
  **Answered by the survey:** Imager already puts
  `cfg80211.ieee80211_regdom=AU` on the kernel command line.
- **Check:** auto (OS is 64-bit Trixie on a Pi 5)

### 2. Boot machine

### 3. Find machine and ssh into it
`./find-pi.sh`, then `./bootstrap.sh` (key and passwordless sudo).
- **Check:** auto
- **Status:** passing (round 1)

### 4. `sudo apt-get update`
- **Q:** update only, or also `full-upgrade`? The `base` role currently does a
  full upgrade.
- **Check:** auto
- **Status:** passing (round 1). Full upgrade, after `net_ready` sorts the
  uplink and clock

## B. Display and touch

### 5–6. Waveshare panel overlay
`/boot/firmware/config.txt`:
```
dtoverlay=vc4-kms-dsi-waveshare-panel,10_1_inch
```
Reference: https://www.waveshare.com/wiki/10.1inch_DSI_LCD_(C)

### 7. Reboot. Screen working, correct rotation, touch in sync
Your notes:
```
sudo nano /etc/xdg/lxsession/LXDE-pi/autostart
rotate right display in Display settings          (note name of display)
@xrandr --output DSI-1 --rotate right             (use name of display)
fix touch mappings  ~/.config/labwc/rc.xml
add to rc.xml, check it has
  </libinput><touch deviceName="4-0014 Goodix Capacitive TouchScreen" mapToOutput="DSI-2" mouseEmulation="yes"/>
```
- **Q:** These notes mix two desktops. LXDE autostart with `xrandr` is the
  old X11 desktop. `rc.xml` and kanshi (item 19) belong to labwc, the
  Wayland desktop Trixie uses, and there `xrandr` lines in an LXDE autostart
  do nothing. Is the xrandr line from older kits?
- **Q:** Rotation. Your kanshi profile in 19 says `DSI-2 … transform normal`
  at 1280x800, which is landscape and not rotated. Is the panel rotated
  ("right") or not? If it is, on labwc the rotation belongs in that kanshi
  line (`transform 90`), and touch follows it via `mapToOutput`.
- **Q:** `DSI-1` vs `DSI-2`. It depends on which DSI connector the ribbon is
  in. Is it the same connector on every kit?
- **Proposed: labwc (Wayland) only.** Drop the LXDE/xrandr parts. Rotation
  goes in kanshi, and touch follows via `rc.xml`. To settle it, paste the
  output of these from a working kit:
  ```bash
  echo $XDG_SESSION_TYPE
  wlr-randr 2>/dev/null || xrandr
  cat ~/.config/kanshi/config
  grep -A2 -i touch ~/.config/labwc/rc.xml
  ```
  Also: in its case, is the panel landscape (1280x800) or portrait?
- **Check:** auto: panel output present at 1280x800 with the right transform,
  Goodix touch device present, rc.xml mapping present. Then a screenshot
  copied to `reports/`.
  human: picture is upright, and touching the four corners lands on the
  four corners.

## C. Audio

### 8. `sudo apt-get install qjackctl`
- **Q:** Is qjackctl only for setting up JACK by hand, or does it run on the
  kit (e.g. to start jackd)? If jackd is started from `~/.jackdrc` (see 13),
  qjackctl may not be needed at all on the kits.

### 9. `git clone https://github.com/redFrik/supercolliderStandaloneRPI64 --depth 1`
- **Q:** Into `~`? `start_airkit.sh` expects `~/supercolliderStandaloneRPI64`.
- **Q:** That repo's README lists apt packages it depends on. Do you install
  any of them, or has it always just run?
- **Q:** Has the standalone been run on **Trixie** yet? It was built against
  an earlier OS, so newer library versions could stop it starting. The first
  round will show this either way.

### 10–11. SC IDE config
```
mkdir -p ~/.config/SuperCollider
cp supercolliderStandaloneRPI64/sc_ide_conf_temp.yaml ~/.config/SuperCollider/sc_ide_conf.yaml
```
- **Check:** auto

### 12–13. Configure JACK
```
/usr/bin/jackd -R -P75 -dalsa -dhw:S3 -r44100 -p256 -n3
```
RT audio:
```
sudo usermod -aG audio $USER
/etc/security/limits.d/audio.conf:
  pi   -  rtprio     95
  pi   -  memlock    unlimited
  pi   -  nice       -19
```
- **Q:** Where does that jackd line live: `~/.jackdrc` (scsynth starts jackd
  from it), qjackctl's settings, or something else?
- **Q:** `-P75` puts audio **above** the Wi-Fi interrupt (FIFO 50–55). That's
  the priority inversion `PERFORMANCE_TUNING.md` diagnosed in June as the
  cause of the AP latency spikes. Tuning is on hold, so I'll write exactly
  what you give me. Is 75 still what the kits run?
  **Proposed: `-P40`.** That's below the Wi-Fi interrupt's default of 50, so
  it needs none of the on-hold tuning. Measured by the audio probe (xruns
  with simulated stick OSC running). Raise toward 49 if needed, never to 50.
  *Awaiting your OK.*
- **Q:** `hw:S3` picks the card by name, but `start_airkit.sh` sets the volume
  with `amixer -c 2` (by index). Are they the same card? If so,
  `amixer -c S3` keeps working when the card numbering moves.
- **Q:** Trixie runs PipeWire by default, and it can hold the sound card, which
  makes jackd fail with "device busy". Do you disable PipeWire on the kits?
- **Check:** auto: `pi` is in `audio`, limits applied (`ulimit -r` = 95), card
  S3 present

### 14. Boot the server to check audio works
Currently by hand in SuperCollider. To be scripted.
- **Check:** auto, by a probe: boots scsynth on JACK, plays a test tone,
  confirms the server is running, the output isn't silent and there are 0
  xruns over 30 s.
  human: you hear the tone through the speaker at the right level.

## D. AirKit

### 15–16. Clone AirKit, branch `Airsticks-RPI`
`git clone https://github.com/sohla/AirKit.git`, then
`git checkout Airsticks-RPI`.
- **Status:** passing (round 1). Cloned once, never pulled after that
- **Q:** Into `~/Develop/SuperCollider/Projects/AirKit`? That's where
  `start_airkit.sh` and the updater expect it.
- **Check:** auto

### 17. Install the 3DCanvas quark from a script
`Quarks.install("3DCanvas")` run in sclang, not via `Quarks.gui`. Needed by
`code3.0/threeDeeView.scd`.
- **Q:** With `-a -l sclang.yaml`, the quark gets recorded in the
  standalone's `sclang.yaml`. Is that where it ends up on your kits now?
- **Check:** auto: sclang compiles with `Canvas3D` defined

## E. Network

### 18. Access point
```
sudo nmcli con modify AirKit4Network 802-11-wireless-security.proto rsn
sudo nmcli con modify AirKit4Network 802-11-wireless-security.pairwise ccmp
sudo nmcli con modify AirKit4Network 802-11-wireless-security.group ccmp
sudo nmcli con up AirKit4Network
AP power mode off.
```
Plus, from `utils/network/README.md`: channel 11, PMF off, `ipv4.method
shared` at the kit's AP IP.
- **Q:** These are `modify` commands. How is the profile created in the first
  place (`nmcli con add …`, or the desktop's hotspot dialog)? Paste it if you
  have it.
- **Q:** Is the connection name always the same as the SSID (`AirKit4Network`)?
  The network tools assume `AirKit2_Network`.
- **Check:** auto: profile settings, AP up on channel 11, PMF off, power save
  off, address = kit's AP IP.
  human: a real stick joins.

### 19. Second screen (HDMI)
`~/.config/kanshi/config`:
```
profile dual {
output DSI-2 enable mode 1280x800@60.026 position 0,0 transform normal
output HDMI-A-1 enable mode 1024x768@60.004 position 1280,0 transform normal
}

profile single {
output DSI-2 enable mode 1280x800@60.026 position 0,0 transform normal
}
```
- **Q:** Is HDMI always 1024x768 (one projector model), or does it vary by
  venue? kanshi only applies a profile when the connected outputs match it.
- **Check:** auto: config present, and with HDMI plugged in, both outputs
  active.
  human: second screen shows the right thing.

## F. Whole system

### 20. Run AirKit, send OSC data, check AP and everything works
- **Check:** auto, by a probe: AirKit boots and resolves **this kit's**
  machine file (the boot post names it), a simulated stick sends
  `IMUFusedData` to :57120 and the device gets added.
  human: real sticks connect, and visuals and sound respond to movement.

### 21. Power button
```
/boot/firmware/config.txt:
gpio=23=op,dh        pins GND, GPIO23     7,8 pin
```
- **Q:** `gpio=23=op,dh` makes GPIO23 an output held high. That isn't a button
  input, so it sounds like a keep-alive signal for a power board. What's the
  hardware? Also, GPIO23 is physical pin 16 (GND at 14), not 7/8. Which is
  right?
- **Steph to add:** the button hardware.
- **Check:** auto: line present, GPIO23 high (`pinctrl get 23`).
  human: the button powers the kit on and off.

### 22. Logs directory
- **Status:** passing (round 1). `~/logs`
"Create a /logs dir at root."
- **Q:** `start_airkit.sh` writes to `~/logs`, which is `/home/pi/logs`, not
  `/logs`. That's the one that already gets made. OK?
- **Check:** auto

## G. Storage

### 23. Boot from the NVMe SSD
Your notes:
```
/boot/firmware/config.txt:
dtparam=pciex1
dtparam=nvme
sudo raspi-config → Advanced → boot order
lsblk
https://www.jeffgeerling.com/blog/2023/nvme-ssd-boot-raspberry-pi-5
```
- **Answered:** the kit boots from the SSD. The SD card stays in as a backup.
- **Q:** If it boots from SSD, how does the system get there? Either
  provision the SD as now and **copy SD → SSD as the last step** (and test it
  booting from the SSD), or flash the SSD directly with Imager over a USB
  adapter and provision that. The first keeps the two-card rotation working
  unchanged.
- **Note:** on a Pi 5, `nvme` is another name for `pciex1`, so one of the two
  lines is enough. Harmless to keep both.
- **Check:** auto: PCIe enabled, the SSD shows up in `lsblk`, boot order
  has NVMe first, and if booting from it, `/` is on `nvme0n1`.
  human: cold boot with the SD card out comes up in AirKit.

---

## Not on the list yet

Each of these exists on a working kit, or will need to.

- **Q: Passwordless sudo.** A fresh Trixie card asks `pi` for a password on
  `sudo` (survey, 2026-11-02). AirKit's own buttons run `sudo shutdown now`
  and `sudo systemctl restart NetworkManager` (`code3.0/systemView.scd`).
  With no terminal to type a password into, those fail silently. So does
  `deploy.sh`'s `sudo mv`. Either all of sudo is passwordless (what older
  Pi OS images did), or only those commands are.
- **`iw` package.** It isn't installed on a fresh card, and the network tools
  and AP checks use it. It'll be added with the AP role.

- **Q: Autostart (Steph to find).** What runs `start_airkit.sh` at boot? On labwc it's
  usually `~/.config/labwc/autostart`. The LXDE file in 7 isn't read by
  labwc.
- **Q: Updater (AirKitWebApp).** Its first install, via its own `deploy.sh`.
  Where in the order?
  **Status:** passing. Installed in round 1; the page loads after a reboot
  (hand check). Its place in the order is still open.
- **Q: Desktop auto-login and screen blanking off.** Needed so AirKit has a
  display after a cold boot and the panel doesn't go dark mid-show. Do you
  set these?
- **Q: airkit2's USB dongle.** Internal Wi-Fi disabled and the profile bound
  to the dongle. Part of 18 for that kit?
- **Performance tuning:** on hold.
