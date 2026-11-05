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
