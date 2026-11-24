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
- **Stick IPs:** the sticks have static IPs baked into their firmware, so they
  must sit inside the AP's subnet. If an AP IP changes subnet, the sticks need
  reflashing. Give the range, e.g. `.40–.48`.
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

## airkit2 (Mel) — PMF was back on (fixed 2026-11-25)

**Symptom.** AirKit was very slow to see the sticks, one of four did not appear
at all, and a Mac was refused with "wrong password". The AP was up the whole
time, and the passphrase on the Pi was correct (`AirKit2_Network`).

**Cause.** `802-11-wireless-security.pmf` was `0 (default)`, which means PMF
is on. It has to be `1` (off): stick firmware mishandles PMF, and
`utils/network/README.md` records the same slow joins on this kit in September.
The September fix lived only in the AP profile. The profile was rebuilt by
hand around 20–21 Nov, during the dongle and eth0 debugging, and the setting
was lost. Nothing re-applies it, because airkit2 predates `rpi_tools` and has
never been provisioned. The `network_ap` role sets PMF off, but it binds the AP
to `wlan0`, and airkit2's AP is bound to the dongle's MAC. So it can't simply
be run on this kit.

**Check. On the Pi:**
`nmcli -g 802-11-wireless-security.pmf connection show AirKit2_Network` should
print `1`.

**Fix.** Sticks off first. **On the Mac:**
`ssh pi@192.168.50.110 'sudo nmcli connection modify AirKit2_Network 802-11-wireless-security.pmf 1 && sudo nmcli connection up AirKit2_Network'`
Then sticks on. All four joined, and AirKit saw them quickly. The Mac mini
joined with the passphrase.

**Status at the end of 2026-11-25: PMF is back at `0`.** It was reverted while
testing the lockup below. That made no difference, and all four sticks joined
with PMF at `0` afterwards. So PMF did not cause the lockup. Whether it was
really behind the slow joins is unproven. **Decision: leave it at `0`** until
the lockup is understood, so only one thing changes at a time. `1` is still
what `utils/network/README.md` and the `network_ap` role specify.

**Re-check PMF after any hand edit to the AP profile.** The profile also still
lacks the role's `proto rsn` / `ccmp` settings. They're harmless today, but
they're a difference from the spec.

**Mel's sticks** (from the AP, 2026-11-25):

| MAC | IP | Notes |
|---|---|---|
| 64:e8:33:64:f1:94 | 192.168.200.11 | static |
| 64:e8:33:64:f1:80 | 192.168.200.13 | static. The one missing before the fix |
| 64:e8:33:64:f1:50 | 192.168.200.14 | static |
| 64:e8:33:67:c1:4c | 192.168.200.56 | DHCP, reports itself as `esp32s3-67C14C` |

To see which are on the AP right now. **On the Mac:**
`ssh pi@192.168.50.110 'ip neigh show dev wlan0'`

**Also seen:** the Pi's clock reads 19 Sep 2026. It has no internet to sync
from, so log and file times on airkit2 are Pi-clock times, not real dates.

## airkit2 (Mel) — the dongle locks up, only unplugging clears it (2026-11-25)

**Symptom.** The AP vanishes. Macs no longer list `AirKit2_Network`, and the
sticks drop off. On the Pi, everything still looks fine: `nmcli` says the AP is
connected, and there's no error.

**What the Pi shows.** Nothing at the moment it happens. About 5 minutes later,
every device disconnects within one second (`AP-STA-DISCONNECTED` for all of
them, no reason given). That's the AP's inactivity timeout, so the radio went
deaf 5 minutes before. `wlan0`'s rx counter stays at 0. The
`rtw_8822bu … failed to get tx report from firmware` lines are always there,
including while it works, so they don't signal the lockup.

**What didn't fix it:**
- reloading the driver (the module is `rtw88_8822bu`, not `rtw_8822bu`). The
  driver crashed on unload.
- rebooting the Pi. **A Pi 5 keeps USB powered through a reboot**, so a hung
  dongle stays hung.
- changing PMF either way.

**What fixed it.** Unplug the dongle, wait 10 s, and plug it back into the same
port. The AP profile is bound to the dongle's MAC, so it comes back on its
own, within about 10 s. All four sticks rejoined.

**Check whether it's alive. On the Mac:**
`ssh pi@192.168.50.110 'a=$(cat /sys/class/net/wlan0/statistics/rx_packets); sleep 5; echo $(( $(cat /sys/class/net/wlan0/statistics/rx_packets) - a ))'`
With sticks on, this should be hundreds. `0` means it's locked up.

**Seen once so far:** it worked for ~35 min after a change, then locked up. If
it happens again, **note the time and how long it had been up**, to tell a
fixed run-time from random. The cause (dongle, out-of-tree driver, port or
power) is not known yet.

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

**2026-11-25:** PMF was on at the time (see the section above). It's the
likeliest cause. If this Mac still can't join now that PMF is off, carry on
below.

To test another day:
1. Find the other Mac's macOS version. Older than 27 points at the new OS, and
   the same version points at this Mac's hardware or settings.
2. On the Mac: turn *Private Wi-Fi Address* off and try again.
3. To see the actual reason: reach the Pi over eth0 (on the Mac:
   `sudo ifconfig en10 alias 169.254.1.1 netmask 255.255.0.0`). Then, on the Pi,
   run `utils/network/tools/akwifi.sh timeline` while the Mac tries to join.
