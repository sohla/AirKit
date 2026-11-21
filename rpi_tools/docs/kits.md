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

To test another day:
1. Find the other Mac's macOS version. Older than 27 points at the new OS, and
   the same version points at this Mac's hardware or settings.
2. On the Mac: turn *Private Wi-Fi Address* off and try again.
3. To see the actual reason: reach the Pi over eth0 (on the Mac:
   `sudo ifconfig en10 alias 169.254.1.1 netmask 255.255.0.0`). Then, on the Pi,
   run `utils/network/tools/akwifi.sh timeline` while the Mac tries to join.
