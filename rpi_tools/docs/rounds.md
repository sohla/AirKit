# Rounds

One entry per test round. Newest first.

---

## Round 2b — 2026-11-02 — airkit5, display step 1 (abandoned)

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
