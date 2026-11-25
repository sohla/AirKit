# Kits

One row per kit. Fill the blanks. Values in *italics* came from
`machines/*.scd` or `utils/network/README.md`. Correct them if they're wrong.

When an AP address here differs from `apAddr` in that kit's machine file,
**the machine file has to change too**. The app identifies itself by that
address at boot, and provisioning reads it from there.

## Network

| Kit | Label | User | SSID | AP IP (want) | Radio | Stick IPs |
|---|---|---|---|---|---|---|
| airkit1 | *AirKit1* | *MiM* | AirKit1_Network | *192.168.100.1* | internal | |
| airkit2 | *AirKit2* | *Mel* | AirKit2_Network | *192.168.200.1* | *USB dongle (Techkey RTL88x2BU?)* | |
| airkit3 | *AirKit3* | *Alon* | AirKit3_Network | *192.168.150.1* | internal | |
| airkit4 | *AirKit4* | *Nic* | AirKit4Network | *192.168.50.1* | internal | |
| airkit5 | AirKit5 | *SensiLab* | AirKit5Network | *192.168.250.1* | internal | |
| development | *AirKitDevelopment* | *Steph* | AirKitDevelopment | *192.168.70.1* | internal | |

- **Passphrases are not recorded here.** You keep them in
  `ansible/vault.yml`, encrypted with `ansible-vault`. Provisioning decrypts
  them at run time and never logs them.
- **Stick IPs:** the sticks get their address from the AP by DHCP. The firmware
  has no static IP. What is compiled into the firmware is the network name and
  password (`globals.h`), so a stick only joins the kit it was flashed for.
- **Radio:** `internal`, or the USB dongle model. A dongle kit also gets the
  internal Wi-Fi disabled, as airkit2 has now.

## Hardware

| Kit | Pi eth0 MAC | Pi wlan0 MAC | Dongle MAC | Audio interface | Notes |
|---|---|---|---|---|---|
| airkit1 | *d8:3a:dd:ca:e3:d4?* | *d8:3a:dd:ca:e3:d5?* | | | |
| airkit2 | *d8:3a:dd:ca:e3:3e?* | *d8:3a:dd:ca:e3:3f?* | | | |
| airkit3 | | | | | |
| airkit4 | | | | | |
| airkit5 | d8:3a:dd:ca:e2:7e | d8:3a:dd:ca:e2:7f | | Sound Blaster Play! 3 (`S3`, card 2) | test board, 2026-11-02 |
| development | | | | | |

**The machine files are the record, not this table.** On its first run for a
kit whose `macs:` is empty, provisioning writes the board's eth0 and wlan0 MACs
into `machines/<kit>.scd` (commit it afterwards). After that, it refuses to run
if the board's MACs don't match the kit named on the command line. The MACs
belong to the Pi board, so the board is the kit; cards can move between boards.

On a Pi 5, wlan0 is eth0 + 1 (airkit5: `…e2:7e` / `…e2:7f`), which fits the
airkit1 and airkit2 pairs above.

**Audio interface:** `start_airkit.sh` runs `amixer -c 2 set Speaker 90%`, so
each kit expects a sound card at index 2 with a `Speaker` control. Which device
is it, and is it the same on every kit?

## airkit2 (Mel) — PMF is `0`, against the spec (2026-11-25)

The AP profile has `802-11-wireless-security.pmf` at `0 (default)`, which
means PMF is on. `utils/network/README.md` and the `network_ap` role both
specify `1` (off). It was already `0` on 21 Nov, while the sticks were
working, so when it changed isn't known. airkit2 predates `rpi_tools` and has
never been provisioned, so nothing re-applies the spec. The role can't simply
be run on this kit either: it binds the AP to `wlan0`, and airkit2's AP is
bound to the dongle's MAC.

On 2026-11-25 PMF was set to `1` and then back to `0`. Neither made a
difference to the real fault, which was a Mac on the AP (next section).
**Decision: leave it at `0`.** That's the value the sticks worked with.

**Check. On the Pi:**
`nmcli -g 802-11-wireless-security.pmf connection show AirKit2_Network`

The profile also lacks the role's `proto rsn` / `ccmp` settings, which is
another difference from the spec.

**Mel's sticks** (from the AP, 2026-11-25):

| ID | MAC | IP | Notes |
|---|---|---|---|
| 1 | 64:e8:33:64:f1:94 | 192.168.200.11 | |
| 2 | 64:e8:33:67:c1:4c | 192.168.200.56 | doesn't rejoin after a drop. Power-cycle it |
| 3 | 64:e8:33:64:f1:80 | 192.168.200.13 | |
| 4 | 64:e8:33:64:f1:50 | 192.168.200.14 | |

All four use DHCP; none has a fixed address. The firmware
(`~/Develop/Embed/Projects/AirStick-ESP-Arduino`) has no static-IP code, and
the network name and password are compiled into `globals.h`. After a drop, the
firmware's own retry loop doesn't run: it only runs at boot, and runtime
reconnects are left to the ESP32 library. Sticks 1, 3 and 4 came back by
themselves; stick 2 stayed off until power-cycled. All four report the same
settings over OSC (`stick-config.py --show`), apart from ID and LED.

To see which are on the AP right now. **On the Mac:**
`ssh pi@192.168.50.110 'ip neigh show dev wlan0'`

**Also seen:** the Pi's clock reads 19 Sep 2026. It has no internet to sync
from, so log and file times on airkit2 are Pi-clock times, not real dates.

## airkit2 (Mel) — the AP drops every stick at once (2026-11-25, cause not proven)

**Rule for now: keep Macs off `AirKit2_Network`** (on each Mac, forget it or
turn Auto-Join off), and keep the dongle in its usual port (`1-2`).

**Symptom.** Every device is disconnected in the same second
(`AP-STA-DISCONNECTED` for all, no reason given). Macs may stop listing the AP.
`nmcli` on the Pi still says the AP is connected. Sticks 1, 3 and 4 usually
rejoin by themselves within about a minute; stick 2 doesn't (see the stick
table above). The driver logs `rtw_8822bu … failed to get tx report from
firmware` around the drops. Before 2026-11-25 there were none.

**The drops (Pi clock, 2026-11-25):**

| dropped | Mac on the AP? | dongle port |
|---|---|---|
| 01:29 | yes, joined 01:20 | 1-2 |
| 02:10 | yes, joined 02:08 | 1-2 |
| 02:17:54 | yes, joined 02:17:38 (16 s before) | 3-1 |
| 02:46 | **no, and nothing tried to join** | 3-1 |

A Mac joining logs `HT: Forty MHz Intolerant is set by <the Mac>`. With sticks
only, it stayed up 12 min on port `3-1`, dropped at 02:46, and then ran **24 min
clean on port `1-2`** (03:11–03:35, 0 driver errors). The dongle had moved to
`3-1` at a replug and was moved back. So the cause isn't proven. A Mac on the
AP is the strongest lead, and the port is a second one.

**Ruled out by test:** PMF (either value), eth0 / the ASUS network (cable
unplugged: it still dropped), a Pi reboot, and reloading the driver.
Reloading the driver crashed it; the module is `rtw88_8822bu`. On the Pi the
settings, kernel, driver and firmware are identical to the last good boot
(21 Nov).

**If it's down.** Get any Mac off the AP. Unplug the dongle for 10 s and plug it
back into the **same port** (`1-2`). The profile is bound to the dongle's MAC,
so the AP comes back by itself. Then power-cycle stick 2. A reboot doesn't
always clear it, because a Pi 5 keeps USB powered through a reboot. If AirKit
isn't running, start it; sticks only appear in AirKit while `sclang` is running.

**Is it alive? On the Mac:**
`ssh pi@192.168.50.110 'sudo timeout 3 tcpdump -n -i wlan0 udp dst port 57120 | awk "{print \$3}" | sort | uniq -c'`
should list each stick at ~200 per 3 s.
**Which port is the dongle on? On the Mac:**
`ssh pi@192.168.50.110 'readlink /sys/class/net/wlan0/device'` should end in `1-2:1.0`.

**When it drops, record:** the time, whether a Mac was on or trying to join, and
the dongle's port.

## eth0 must be `auto`, never `link-local` (airkit2, fixed 2026-11-21)

**Rule:** on the lab desk (Mac – switch – router/AP – Pi), a kit's eth0 profile
(`Wired connection 1`) must have `ipv4.method auto`. With `link-local` it never
asks the router for an address. It sits on `169.254.x` (`pi.local`), and the Mac
cannot reach it on `192.168.50.x`.

`link-local` is only a workaround for a Pi cabled **straight to the Mac**, where
nothing hands out addresses and `auto` makes eth0 flap. If you turn it on for
that, turn it off again before the kit goes back on the desk.

**What happened.** On 2026-11-13, while debugging airkit2's new USB Wi-Fi dongle
on a direct cable, eth0 was set to `link-local` to stop the flapping. It was
never reverted. On 20–21 Nov, on the lab desk, eth0 got no DHCP and SSH could
not reach it. That looked like a router or Mac problem, but it was the profile.

**Check. On the Pi:**
`nmcli -g ipv4.method connection show "Wired connection 1"` should print `auto`.

**Fix. On the Pi** (the SSH session drops; the kit comes back on `192.168.50.x`):
`sudo nmcli connection modify "Wired connection 1" ipv4.method auto && sudo nmcli connection up "Wired connection 1"`

**Reaching a kit stuck on `169.254.x`.** On the Mac:
`sudo ifconfig en10 alias 169.254.1.1 netmask 255.255.0.0`, then
`ssh -b 169.254.1.1 pi@169.254.223.120`. The `-b` matters: without it the Mac
sends from `192.168.50.53`, which the Pi cannot reply to, so ping and SSH just
time out. Remove the alias afterwards. On the Mac:
`sudo ifconfig en10 -alias 169.254.1.1`.

## airkit2 (Mel) — Mac locked out of the AP (2026-11-20, did not recur)

On 2026-11-20 a Mac could not join `AirKit2_Network` with the passphrase
`utils/network/README.md` records, while the sticks could. On 2026-11-21 the same
passphrase worked from a Mac. The cause is unknown, and the passphrase on the Pi
was not wrong. If it recurs, watch a join attempt with
`utils/network/tools/akwifi.sh timeline`.

On 2026-11-21 it still failed from Steph's Mac (two tries, the prompt came back),
while another Mac joined. So the fault is this Mac's join, not the AP:

- Steph's Mac: macOS 27.0 (`26A428`), Broadcom Wi-Fi 6E card (`0x14E4, 0x4388`),
  private Wi-Fi address on.
- The AP: 2.4 GHz b/g only, WPA2-PSK, PMF off, channel 11 (crowded), on the
  RTL88x2BU dongle with an out-of-tree driver.
- Theory, unconfirmed: macOS 27 refuses the handshake with this AP, and shows a
  failed handshake as "requires a password".

**2026-11-25:** Macs could join, and a Mac on the AP then brought it down
for the sticks (see "the AP drops every stick at once" above). Keep Macs off
`AirKit2_Network` until that is understood.

To test another day:
1. Find the other Mac's macOS version. Older than 27 points at the new OS, and
   the same version points at this Mac's hardware or settings.
2. On the Mac: turn *Private Wi-Fi Address* off and try again.
3. To see the actual reason: reach the Pi over eth0 (on the Mac:
   `sudo ifconfig en10 alias 169.254.1.1 netmask 255.255.0.0`). Then, on the Pi,
   run `utils/network/tools/akwifi.sh timeline` while the Mac tries to join.
