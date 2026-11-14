#!/usr/bin/env python3
# Build the AirKit manual from manifest.json + shots/ (made by capture.scd): one landscape A4 page per tab.
import html, json, pathlib, subprocess

HERE = pathlib.Path(__file__).resolve().parent
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
OUT = HERE / "output"

def page(tab):
    e = html.escape
    shot = HERE / "shots" / f'{tab["id"]}.png'
    pins = "".join(f'<b class="pin" style="left:{c["at"][0] * 100:.1f}%;top:{c["at"][1] * 100:.1f}%">{i + 1}</b>'
                   for i, c in enumerate(tab["components"]))
    items = "".join(f'<li><span class="n">{i + 1}</span><div><h3>{e(c["name"])}{" <em>Pi only</em>" if c.get("pi") else ""}</h3>'
                    f'<p>{e(c["text"])}</p></div></li>' for i, c in enumerate(tab["components"]))
    img = f'<img src="{shot.as_uri()}" alt="">' if shot.exists() else '<div class="missing">no screenshot yet — run capture.scd</div>'
    return f'''<section class="page">
  <header><h1>{e(tab["label"])}</h1><p>{e(tab["summary"])}</p></header>
  <div class="body"><div class="shot">{img}{pins}</div><ol>{items}</ol></div>
</section>'''

CSS = """
@page{size:A4 landscape;margin:0}
*{box-sizing:border-box}
body{margin:0;font-family:"Atkinson Hyperlegible",Verdana,sans-serif;color:#111;-webkit-print-color-adjust:exact;print-color-adjust:exact}
.page{width:297mm;height:210mm;padding:10mm 12mm;display:flex;flex-direction:column;gap:5mm;page-break-after:always;overflow:hidden}
header{display:flex;align-items:baseline;gap:6mm;border-bottom:1mm solid #111;padding-bottom:2mm}
h1{margin:0;font-size:28pt;text-transform:capitalize}
header p{margin:0;font-size:12pt;color:#444}
.body{flex:1;display:flex;gap:7mm;min-height:0}
.shot{position:relative;flex:none;width:150mm;align-self:flex-start}
.shot img{display:block;width:100%;border-radius:1.5mm}
.missing{aspect-ratio:16/10;display:flex;align-items:center;justify-content:center;background:#222;color:#aaa;border-radius:1.5mm}
.pin{position:absolute;transform:translate(-50%,-50%);width:7mm;height:7mm;border-radius:50%;background:#ffd400;color:#000;border:0.5mm solid #000;display:flex;align-items:center;justify-content:center;font-size:10pt}
ol{list-style:none;margin:0;padding:0;flex:1;columns:2;column-gap:5mm;overflow:hidden}
li{display:flex;gap:3mm;break-inside:avoid;margin-bottom:2.5mm}
.n{flex:none;width:6mm;height:6mm;border-radius:50%;background:#ffd400;border:0.4mm solid #000;display:flex;align-items:center;justify-content:center;font-size:9pt;font-weight:700}
h3{margin:0;font-size:11pt}
h3 em{font-style:normal;font-weight:400;font-size:8pt;background:#111;color:#fff;border-radius:1mm;padding:0 1.5mm;margin-left:2mm}
li p{margin:0.5mm 0 0;font-size:9.5pt;line-height:1.3;color:#333}
"""

def main():
    m = json.loads((HERE / "manifest.json").read_text())
    OUT.mkdir(exist_ok=True)
    pages = "".join(page(t) for t in m["tabs"])
    src = OUT / "AirKit_manual.html"
    src.write_text(f'<!doctype html><html><head><meta charset="utf-8"><title>{html.escape(m["title"])}</title>'
                   '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Atkinson+Hyperlegible:wght@400;700&display=swap">'
                   f'<style>{CSS}</style></head><body>{pages}</body></html>', encoding="utf-8")
    pdf = OUT / "AirKit_manual.pdf"
    subprocess.run([CHROME, "--headless", "--disable-gpu", "--no-pdf-header-footer", "--virtual-time-budget=5000",
                    f"--print-to-pdf={pdf}", src.as_uri()], check=True, capture_output=True)
    print(pdf)

main()
