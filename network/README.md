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
