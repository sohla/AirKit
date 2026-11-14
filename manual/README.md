# AirKit manual

## Take screenshots

1. Quit AirKit.
2. Run:
   ```
   manual/capture.sh
   ```
3. AirKit opens. You have 60 seconds: start your OSC, pick pfiles that draw something, check the visual tab.
4. It screenshots each tab by itself, then quits.

Works the same on a Pi.

## Build the manual

```
python3 manual/build.py
```

Gives `manual/output/AirKit_manual.pdf`.

## Change the text

Edit `manual/manifest.json`, then build again.
