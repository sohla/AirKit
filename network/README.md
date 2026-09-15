# Network

How an AirKit machine puts its access point on the air, and how to diagnose it when
the Airsticks will not connect.

Nothing in `code3.0/` configures networking. The AP is **NetworkManager state on each
machine**, created once and never touched by the application. `machines/*.scd` decides
which kit this is; it does not decide anything about the network.

---

## The access point

Each kit runs a WPA2 hotspot on its onboard Wi-Fi, with the sticks on static IPs and
OSC arriving on UDP 57120.

| | |
|---|---|
| Profile | `AirKit<N>_Network`, NetworkManager, `802-11-wireless.mode=ap` |
| Passphrase | the same string as the SSID |
| Addressing | `ipv4.method=shared`, `192.168.200.1/24`, DHCP `.10–.254` via NM's own dnsmasq |
| Sticks | **static IPs**, no DHCP lease is ever issued to them |
| OSC | stick `:8888` → Pi `:57120`, tag `IMUFusedData`, ~100 Hz per device |

### Settings that matter — verify these first

```bash
# PMF (802.11w) must be DISABLED.
# NM's default advertises PMF-capable in the beacon; some ESP32 firmware mishandles it.
# Disabling it cut the join delay from 60-100 s to 10 s on airkit2.
sudo nmcli con modify AirKit2_Network 802-11-wireless-security.pmf 1

# Channel 11. The sticks scan this channel and effectively only this channel —
# on channel 1 they produce zero association attempts at all.
sudo nmcli con modify AirKit2_Network 802-11-wireless.channel 11

# Power save off. NetworkManager does this via conf.d; hostapd does NOT.
sudo iw dev wlan0 set power_save off
```

### Bringing the sticks up

Order matters. The sticks must be **off** while the AP restarts.

1. Sticks off — confirm with `iw dev wlan0 station dump`
2. Press **Network** in AirKit (`code3.0/systemView.scd` → `systemctl restart NetworkManager`)
3. Power the sticks on. Their LEDs flicker while handshakes fail — this is expected
4. Wait. A stick appears in AirKit within ~1 s of its handshake completing
5. Nothing after ~2 minutes: repeat from 1. Needing two or three attempts is normal

**The Network button is correct as written.** Restarting NetworkManager is the only thing
that clears stale per-station state, and NM's AP mode does not age out dead stations.

### Things not to do

- **Don't adopt hostapd.** Tried on airkit2: 0 successful handshakes in 13 attempts,
  where NetworkManager works. Useful as a *diagnostic* only — it logs every handshake
  stage with reasons, where NM logs almost nothing.
- **Don't move off channel 11** without reflashing the sticks.
- **Don't add a second `autoconnect=yes` wifi profile.** Two profiles at equal priority
  race for one radio; a client profile will seize `wlan0` the moment the AP releases it.

---

## The USB dongle (airkit2, from 15 Sep 2026)

The internal BCM43455 on airkit2 has a faulty transmit path (see the worked example
below). The AP now runs on a **USB dongle**, and the internal radio is disabled at boot
via `dtoverlay=disable-wifi` in `/boot/firmware/config.txt`.

The `AirKit2_Network` profile is **locked to the dongle's MAC** rather than an interface
name, so it survives the interface being renamed — and, deliberately, it will *not* fall
back to the internal radio if the dongle is unplugged. The AP simply stays down, which is
the honest failure.

### Disabling the internal radio

On airkit2 the internal BCM43455 is turned off at three levels, so nothing can fall back
to it:

```bash
# 1. device tree - from the next boot the device does not exist at all
#    /boot/firmware/config.txt
dtoverlay=disable-wifi

# 2. runtime - off immediately, no reboot needed
sudo modprobe -r brcmfmac_wcc brcmfmac

# 3. blacklist - nothing can reload it
#    /etc/modprobe.d/blacklist-internal-wifi.conf
blacklist brcmfmac
blacklist brcmfmac_wcc
```

With the internal radio gone the dongle may come up as `wlan0` rather than `wlan1`. That
is harmless: the profile is bound by MAC, and the tools detect the AP interface rather
than assuming a name.

To restore it, remove the overlay line and the blacklist file. A backup of the original
`config.txt` is kept at `~/akdiag/config.txt.pre-disable-wifi`.

Swapping or re-seating a dongle needs a rebind. One command:

```bash
sudo network/tools/ap-bind.sh          # finds the USB radio, binds, brings the AP up
```

It skips `brcmfmac` by design, and warns if the radio doesn't advertise AP mode — not all
dongles do.

### Comparing dongles

```bash
sudo network/tools/wifi-benchmark.sh 30 "label for this dongle"
```

Appends signal, bitrate, retries, packet rate, **jitter** and late-packet counts to
`~/akdiag/benchmarks.md`. For 100 Hz sensor streams jitter matters far more than
throughput.

**Read the results carefully.** The sticks are handheld, so signal moves with them — the
same dongle measured −30 dBm, −70 dBm and −50 dBm within twenty minutes. Jitter tracks
signal closely. A comparison is only meaningful with the sticks still, in the same place,
with the same number connected.

Tried so far:

| dongle | driver | AP | 5 GHz | monitor | notes |
|---|---|---|---|---|---|
| D-Link DWA-131 rev E1 (RTL8192EU) | `rtl8xxxu` | yes | no | yes | in-kernel driver. Best jitter close in (3.95 ms at ≥ −58 dBm) but falls off a cliff beyond −67 dBm: 78 ms jitter, rate dropping to 60–65 Hz |
| Techkey RTL88x2BU (0bda:b812) | `rtw_8822bu` | yes | yes | yes | out-of-tree driver, patchy stats (some stations report `signal: 0`). Slightly worse close in (5.67 ms) but **degrades gracefully** — at −67 dBm still 10.3 ms interval / 14 ms jitter, and passes ~3× the packets the D-Link does at the same distance |

Both are enormously better than the internal radio. The Techkey's two antennas buy real
range; the D-Link is marginally tighter at close quarters. Neither comparison is
tightly controlled — see the caveat above.

### Usable range

Measured on airkit2, four sticks, 100 Hz streams, walking out and back:

| signal | jitter | verdict |
|---|---|---|
| ≥ −58 dBm | 4–6 ms | playable, timing imperceptible |
| −59 to −66 dBm | ~20 ms | marginal |
| ≤ −67 dBm | 35–78 ms | unusable on the D-Link; borderline on the Techkey |

Check where that lands in a given room by walking to the edge of the performing area and
reading:

```bash
sudo iw dev $(ls /sys/class/net | grep ^wlan | head -1) station dump | grep -E '^Station|signal:'
```

Recovery is immediate in both cases — walking back restores full timing within seconds.
Nothing latches.

---

## Diagnosis

Tools live in `network/tools/`. They write to `$AKDIAG` (default `~/akdiag`).

### Start here

```bash
sudo network/tools/ak-wifi-diagnose.sh
```

~30 seconds, prints a verdict. Interface, power save, PMF, PMIC rails, SDIO bus,
**the TX-path test**, stations, firmware console, recent radio events.

**Read section 4 — "TX PATH" — first.** It needs a peer to test against: an associated
client in AP mode, or a gateway in client mode.

```
[FAIL]  TX PATH DEAD — Linux sent 26 frames, the chip transmitted 0 bytes
[ OK ]  TX path working (26 frames -> 3448 bytes on air)
```

### The two-line test

If you have nothing but a shell, this is the whole diagnosis. With `wlan0` associated to
something, generate traffic and compare what Linux sent against what the chip sent:

```bash
cat /sys/class/net/wlan0/statistics/tx_packets   # what Linux handed to the driver
iw dev wlan0 link | grep TX:                     # what the chip actually transmitted
```

One climbing while the other stays frozen means the radio is accepting frames and not
transmitting them. `tx_errors` will still read `0`.

### The other tools

| Tool | Use it when |
|---|---|
| `wifi-monitor.sh 7200` then `wifi-report.sh` | The fault is intermittent. Samples rails, SDIO counters, station churn, nl80211 events and the firmware console every 2 s |
| `akwifi.sh timeline` | Reconstruct any past boot from the journal — AP lifecycle, associations, handshake failures |
| `osc-trace.sh 150` | Application layer. Captures `wlan0` **and loopback**, so it shows the stick's first data packet, the app's `/Config/GetConfig`, the reply, and `addDevice` |

### Instruments worth knowing about

| Path / command | What it gives |
|---|---|
| `/sys/kernel/debug/ieee80211/phy0/forensics` | **The radio's own firmware console.** Mostly noise (`tdls_sta_info`, `No PMKID`), but real errors appear here and nowhere else |
| `/sys/kernel/debug/ieee80211/phy0/counters` | SDIO bus health — `tx_sderrs`, `rxc_errors`, `rx_badseq`, `regfails` |
| `sudo vcgencmd pmic_read_adc` | Per-rail power. The radio has its own supply, `3V7_WL_SW` (~3.71 V) |
| `vcgencmd get_throttled` | `0x0` means no undervoltage has ever occurred this boot |
| `iw event -t` | Station add/delete, scans, channel and regulatory changes |
| ESP32 serial console | The only view from the far end. Reason 15 = 4-way timeout, 2 = auth expire, 201 = no AP found, 39 = block-ack timeout |

### The trap

**Every host-side indicator is upstream of the antenna and reads green during total radio
failure.** `AP-ENABLED` means the driver accepted a command. `type AP, channel 11` is read
back from the driver's own structs. The power rail means the chip has power. None of it
observes the air.

Beacons are generated *inside* the firmware and never appear in `tx_packets` — over 80
idle minutes that counter moved by **eight**, where a 100 ms beacon interval would be
~48,000. And the BCM43455 refuses monitor mode, so the radio cannot hear itself.

```
Linux → driver → SDIO bus → firmware → RF front end → antenna → air
└────────── everything observable ──────────┘ └──── invisible ────┘
```

If the Pi says the AP is fine and no other device can see it, **believe the other device.**

---

## Worked example — airkit2, September 2026

Four days of investigation on a fault that reduced to two counters disagreeing.

**Symptom.** Sticks took 10–100 s to appear, then worked; later would not connect at all.
The AP was "on" by every measure on the Pi while a Mac and an iPhone saw nothing.

**Cause.** The BCM43455's transmit path intermittently stops while reception continues.
Linux hands frames to the chip, the chip's transmit counter never moves, `tx_errors=0`,
no kernel message. It reproduces in plain client mode against an ordinary router, so it
is not AP mode, not the sticks, and not AirKit.

**History.** Eight clean usage days Oct 2025 – Mar 2026 with zero handshake failures.
Onset 11 June 2026, progressive since, on unchanged kernel, firmware, storage and power.

**Eliminated by test:** power management, Bluetooth coexistence, an alternative firmware
blob, kernel version, AP mode, interference from the NVMe and DSI ribbons, power delivery,
SDIO bus, regulatory domain, credentials, channel, PMF, the application, the sticks.

**One real fix came out of it:** disabling PMF cut the join delay from 60–100 s to 10 s,
with `EAPOL-4WAY-HS-COMPLETED` logged for both sticks — a line never seen before.

**Outcome.** Hardware. Fit a USB Wi-Fi dongle and claim the board under warranty.

Full report, including the measurements and a list of the wrong turns taken along the way:
<https://claude.ai/code/artifact/3379b2a9-45cf-4236-9b45-e8f925beb467>
