# rpi_tools

Turns a freshly flashed Raspberry Pi 5 into a working AirKit, then proves it.
Everything runs **from the Mac**. Nothing is installed on the Pi first, and you
don't need Claude Code on the Pi.

| Tool | Job |
|---|---|
| Raspberry Pi Imager | flash the card: hostname, user, SSH on |
| `bootstrap.sh` | first contact with a card: SSH key and passwordless sudo |
| Ansible (`ansible/`) | configure the Pi over SSH. Idempotent: re-running changes nothing |
| goss (`tests/goss.yaml`) | check the result and write a pass/fail report |

Logs and reports go to `reports/` (git-ignored), named `<kit>-<timestamp>-…`.
Progress per round is in `docs/rounds.md`.

---

## One-time Mac setup

```bash
brew install ansible nmap
ssh-keygen -t ed25519            # if ~/.ssh/id_ed25519 doesn't exist
```

## Flashing a card (Imager)

Device **Raspberry Pi 5**, OS **Raspberry Pi OS (64-bit)** (Desktop). Under
*Customisation*, save these settings once and reuse them every round:

| Setting | Value |
|---|---|
| Hostname | `pi` on every card. The kit is chosen on the command line, not by name |
| Username | `pi`, with a password |
| Wi-Fi | **leave off**: setup is over eth0, and the AP role takes wlan0 |
| SSH | enabled. Pasting `~/.ssh/id_ed25519.pub` saves one password prompt in `bootstrap.sh` |
| Locale / timezone | as wanted. Wi-Fi country AU is set regardless |

Plug in ethernet and boot. First boot takes a minute or two and reboots once.

## A round

```bash
./find-pi.sh                 # resolve pi.local: IP, ssh up? (warns if two cards are named pi)
./bootstrap.sh               # once per fresh card: key + passwordless sudo (asks the Pi password)
./round.sh airkit5           # provision, provision again (must be changed=0), verify
```

Or the steps on their own:

```bash
./provision.sh airkit5 --check     # dry run with diffs
./provision.sh airkit5             # configure
./provision.sh airkit5 --twice     # configure, then require the rerun to change nothing
./verify.sh airkit5                # goss report
```

The kit name must be one of the files in `../machines/` (`airkit1.scd` →
`airkit1`). That folder is the only record of the kits. Provisioning reads
label, user and AP address from it, so there's no second list to keep in sync.

**A reflashed card has a new host key.** SSH will refuse it with "REMOTE HOST
IDENTIFICATION HAS CHANGED". The scripts here ignore host keys for `pi.local`.
For your own `ssh`, run `ssh-keygen -R pi.local` once per reflash.

Only one card named `pi` should be on the network at a time.

**Powering off:** use `./poweroff.sh`. It shuts the Pi down, waits for the
network to drop and then a further 20 s, and says when it's safe. Remove power
only once the Pi 5's LED is solid red or off. SSH stops answering *before*
the last writes to the card, and pulling power then can corrupt the
filesystem. goss checks the kernel log for filesystem errors on every verify.

**Use branded SD cards.** No-name cards (manufacturer ID `0x000000`, check
with `cat /sys/block/mmcblk0/device/manfid`) silently wrote data to the wrong
places on a fresh flash (round 3). The card matters even with the SSD: it's
the source of the SSD copy and the backup boot drive.

## The lab network

```
Mac Wi-Fi (internet) ── Internet Sharing on en0 ── switch ── Pi
                                                     └─── N600 router (AP + DHCP for the lab ESP32s)
```

Two DHCP servers share the switch: the Mac (`192.168.2.x`, with internet) and
the N600 (`10.1.1.x`, no internet). A Pi that takes the N600's address can't
reach the internet. The `net_ready` role handles this at the start of every
run, and again after any reboot:

- If the Pi's own route can't reach the internet, it adds a **runtime-only**
  address `192.168.2.250` and a default route via the Mac's `192.168.2.1`.
  Nothing is saved on the card, so a reboot removes it and a finished kit
  carries nothing from the lab.
- It waits for the **clock** to sync. A fresh card's clock is the image date,
  and TLS rejects every newer certificate ("certificate is not yet valid").

Elsewhere, on a network with internet of its own, it does nothing.

## Testing and resetting

There is deliberately **no uninstall**. Resetting means reflashing. While one
card is being provisioned, the other is reflashed. Each round proves:

1. **From scratch:** the playbook takes a factory card to a passing `verify.sh`.
2. **Idempotent:** the second run reports `changed=0`, so running it again
   later is safe.

goss checks configuration and behaviour automatically. Things only a person
can judge (picture upright, touch lands, tone audible, stick joins) will be a
guided checklist recorded into the same report.

## Things learned on Trixie

- **cloud-init** sets the card up (Imager writes its config). It owns
  `/etc/hosts` and rewrites it at boot, so the playbook leaves that file alone.
  Anything else cloud-init manages would be reset the same way; the verify
  step after a reboot catches it.
- **sudo asks for a password** on a fresh card. `bootstrap.sh` makes it
  passwordless, as older Pi OS images were. AirKit's Shutdown and Network
  buttons and the updater's `deploy.sh` depend on it.
- **PipeWire runs by default** and can hold the sound card. The audio setup
  turns it off (option A in `docs/requirements.md`).
- **`iw` isn't installed** by default.

## Layout

```
bootstrap.sh find-pi.sh provision.sh verify.sh round.sh poweroff.sh   entry points
survey-kit.sh                     read-only snapshot of a kit (ssh pi@kit 'bash -s' < survey-kit.sh)
lib.sh                            shared: paths, ssh options, MAC→kit
ansible/site.yml                  the role list, in order
ansible/inventory.py              hosts read from ../machines/*.scd
ansible/roles/<name>/             one role per setup item
tests/goss.yaml                   one check (or more) per setup item
tests/probes/                     shell probes goss calls (sclang, OSC…)
docs/                             requirements, kits, round log
reports/                          logs and verify reports (git-ignored)
```

Adding a setup item means adding a role in `ansible/roles/`, listing it in
`site.yml`, and adding its checks to `tests/goss.yaml`. An item isn't done
until it has a check.

## Docs

- `docs/requirements.md`: every setup item, in your order, with questions and status
- `docs/kits.md`: per-kit values (SSID, AP IP, radio, MACs)
- `docs/rounds.md`: what each test round covered and how it went
