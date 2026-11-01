# Rounds

One entry per test round. Newest first.

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
