# Rounds

One entry per test round. Newest first.

---

## Round 3b — 2026-11-05 — airkit5, touch mapping

Same card. Raspberry Pi OS's `autotouch` (run at each login from
`/etc/xdg/autostart/autotouch.desktop`) had already written the right line to
`~/.config/labwc/rc.xml`. But it only does that when exactly one display is
connected, and never again once a touch line exists. So HDMI plugged in at
first boot would leave touch spanning both screens for good. The `display`
role now writes the same line itself, taking the device and output names from
the board, and stops with a clear message if the panel isn't answering.

| Step | Result |
|---|---|
| provision | ok, 1 changed (provision marker, new tools commit); touch line already right |
| provision again | **0 changed** |
| verify | **58 pass, 0 fail**, 1 skipped (standalone) |

New checks: `touch-mapped-to-panel` and `panel-landscape-1280x800` (DSI
output enabled, 1280x800, transform normal, read with `wlr-randr`).

**Hand check:** touching the four corners lands on the four corners (Steph).
Pass. Picture upright, landscape, desktop showing.

kanshi is left empty for now; with only the panel there's nothing for it to
do. It comes with HDMI (item 19).

---

## Round 3 — 2026-11-05 — airkit5, good card, display overlay

**Bad cards first.** Two no-name cards (`SD16G`, manufacturer ID `0x000000`,
02/2025) both showed EXT4 errors within 21 s of a **fresh** flash, on the same
inode (`/var/lib/apt/lists/partial`). Checked from the Mac:

- The image is clean: the cached download passes `xz -t`, and `e2fsck -fn` on
  its root partition finds nothing.
- On the card, that directory's first block held an exact copy of a block
  from the **boot partition** about 5.6 GB earlier: data written to the wrong
  address.
- A file-by-file compare of the boot partition, plus `dpkg --verify`, found
  more silent damage: `kernel8.img`, `initramfs8`, `issue.txt`,
  `/boot/config-6.18.50+rpt-rpi-v8`, and 2–3 directories. It booted only
  because none of those are read during a Pi 5 boot.

A third card (`MSSD0`, ID `0xc9`, 29.2 GB), flashed from the same image in the
same reader, was clean on every check. **Cause: the no-name cards**, not the
image and not the reader. They're retired, and the SD card has to be
trustworthy even with the SSD plan: the SD → SSD copy would carry its damage
over, and the SD card is the backup boot drive.

Legitimate first-boot differences from the image, for later comparisons:
`cmdline.txt`, `user-data`, `meta-data`, Firefox's `distribution.ini` (welcome
URL id), `g_ether.conf`, and the `/etc` config files. The Mac also writes
`.Spotlight-V100` and `.fseventsd` to the boot partition while the card is in
the reader.

| Step | Result |
|---|---|
| provision | ok, 11 changed, 0 failed |
| provision again | **0 changed**, idempotent |
| verify | 51 pass, **1 fail**: no touch device (panel not seated, see below) |
| verify, after refit | **54 pass, 0 fail**, 1 skipped |

**Display:** the overlay loads and `DSI-2` (CAM/DISP 1) reports `connected`
at 1280x800, but the panel's controller (`11-0045`) and touch chip (`11-0014`)
didn't answer on I²C (error -121), and the panel's LED was off. The Pi's
supply was fine (5.14 V, no throttling, 5 A detected), so no 5V was reaching
the pogo pins. With the M.2 HAT+ removed, the panel came up and touch
registered. **With the HAT refitted, still on pogo pins, it also worked**: the
panel shows the desktop, touch registers, and the SSD is present. Cause: the
first assembly wasn't seated, so the pogo pins weren't touching. Not a
HAT/panel conflict. The official M.2 HAT+ is powered over the PCIe ribbon and
only passes the GPIO pins through, so it doesn't load the header's 5V pins.
Boot order can't matter either: those pins are live from power-on.

Re-verify with HAT and panel fitted: **54 pass, 0 fail**, 1 skipped
(standalone, no role yet).

**Found:**
- `connected` proves only that the overlay is loaded, not that a panel is
  there. Added goss `dsi-panel-answers`: no I²C failures from the panel or
  touch chip this boot.
- The touch device on this board is `11-0014 Goodix Capacitive TouchScreen`,
  not `4-0014` as in the `rc.xml` notes. The touch role must read the name
  from the board.
- With the HAT on, the SSD shows as `nvme0n1` (238.5 GB) with no
  `dtparam=pciex1`; the HAT+ enables PCIe itself.

---

## Round 2b — 2026-11-02 — airkit5, display step 1 (abandoned)

> **Correction (round 3):** the cause was very likely the no-name card, not
> the power-off. The same corruption showed up on a fresh flash of the same
> card type with no power-off at all.

Pi powered off over SSH to reseat the DSI ribbon (CAM/DISP 1), then powered
on. The next provision failed in `apt`: a corrupt package list. The kernel log
showed **EXT4 checksum errors on the root partition** from 4 s after boot. The
filesystem was damaged, most likely because power was removed before the
shutdown finished writing to the card (SSH stops answering before that).

**Outcome:** card to be reflashed, not repaired; a round on a card we can't
trust proves nothing. If the errors come back on a fresh flash, replace the
card.

**Added:** `poweroff.sh` (waits, then says when it's safe), and a goss check
for filesystem errors in this boot's kernel log.

**Display:** panel dark. Expected before the overlay is loaded (the driver
turns the backlight on). If it's still dark after the overlay, check the
panel's power feed and the ribbon orientation.

---

## Round 2 — 2026-11-02 — airkit5, clean card

**Card:** second card, freshly flashed with the Imager preset (no SSH key in
the preset yet, so `bootstrap.sh` asked for the password twice). Nothing done
by hand apart from `bootstrap.sh`.

**Roles:** `net_ready`, `base`, `airkit_app`, `updater`, `goss`

| Step | Result |
|---|---|
| provision | ok, 8 changed, 0 failed |
| provision again | **0 changed**, idempotent |
| verify | **45 pass, 0 fail**, 1 skipped (standalone, no role yet) |

**Proved:** a factory card gets this far unaided. `net_ready` found no
internet, added the temporary uplink via the Mac and waited for the clock.

**Incident:** a first attempt was interrupted from the Mac while `apt`
full-upgrade was running on the Pi. The upgrade carried on on the Pi and
finished cleanly (`dpkg --audit` clean, nothing pending), and the re-run
found nothing to upgrade. So an interrupted run doesn't wreck a card, but
check `dpkg --audit` before carrying on after one.

---

## Round 1b — 2026-11-02 — airkit5, reboot test

Same card, rebooted, then `verify.sh` only.

| Step | Result |
|---|---|
| reboot | back in ~20 s, on the N600's `10.1.1.9` only (temporary uplink gone, as designed) |
| verify | 46 pass, **2 fail**: internet reachable and clock synced |

Both failures are about the lab, not the kit. `net_ready` already requires
them during every provisioning run, so they were moved out of `goss.yaml`. Its
checks are now **kit state only** and must pass after a reboot with or
without internet. Re-verify: **45 pass, 0 fail**, 1 skipped.

Nothing cloud-init manages undid any setting. The updater service came back
by itself.

**Hand check:** the updater page at http://pi.local:8080 loads correctly
(Steph, after the reboot). Pass.

**Note for later:** with no internet at a venue, the kit's clock comes from
the Pi 5's RTC. That only keeps time across power-off if an RTC battery is
fitted. Without one, log file names in `~/logs` carry wrong dates. Not a
functional problem; worth knowing.

---

## Round 1 — 2026-11-02 — airkit5, test card

**Card:** Raspberry Pi OS Trixie, image of 2026-10-06, kernel 6.18. Not a
clean round: before it, the card had been surveyed, and the lab uplink and
clock sync were first done by hand while being diagnosed.

**Roles:** `net_ready`, `base`, `airkit_app`, `updater`, `goss`

| Step | Result |
|---|---|
| provision | ok, 9 changed, 0 failed |
| provision again | **0 changed**, idempotent |
| verify | **48 pass, 0 fail**, 1 skipped (standalone, no role yet) |

**Found and fixed during the round:**
- Pi had no internet: it took the N600's DHCP, and the N600 has no WAN. Fixed
  by `net_ready`'s runtime-only uplink via the Mac.
- Pi clock was 27 days behind (still at the image date), so TLS rejected
  GitHub. `net_ready` now waits for NTP.
- Read-only probes were skipped in `--check` mode. They now run.
- An `/etc/hosts` task fought cloud-init. Removed.
- The goss check for "no RT limits in the updater unit" matched a comment.
  It now reads systemd's live value.

**Not yet covered:** recording the card's MACs into its machine file.

**Next:** a **clean round** on a freshly flashed card. Flash → `bootstrap.sh` →
`round.sh airkit5`, with nothing done by hand. That proves `net_ready` and the
bootstrap work unaided.
