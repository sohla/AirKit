# BtB demo recordings

1. Quit AirKit.
2. Run:
   ```
   btbdemo/run.sh
   ```
3. AirKit opens. You have 60 seconds to start your OSC.
4. Each piece plays and records 45 seconds to `btbdemo/output/<piece>.wav`, then it quits.

The pfiles come from the `pfiles` columns of the score sheet. A performer with several pfiles gets an equal share of the 45 seconds for each. Names that aren't in that performer's roster (`lists/list_BtB_*.sc`) are skipped and listed as warnings — fix the name in the sheet.

Seconds, countdown and settle time are at the top of `btbdemo/setlist.py`.
