#!/usr/bin/env python3
# Pull the live BtB score sheet and print one A4 page per piece, one PDF per performer.
import csv, datetime, html, io, pathlib, re, subprocess, sys, tempfile, urllib.request

SHEET = "https://docs.google.com/spreadsheets/d/141LOojjAL-YPgWICARAQv5rP0xYEFDPfE9Ve3aBmUG4/export?format=csv"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
OUT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path(__file__).parent / "output")
SHARED = {"piece", "words", "timeline", "energy", "task", "visuals", "intro", "notes", "movments"}
GROUPED = {"where": "where it's worn", "airstick": "where it's worn", "pfiles": "mapping", "setup": "setup", "needs": "needs"}
STAGED = {"mel", "nic"}
OPTIONAL = ["actions"]
INK = {"mel": "#0d7d74", "nic": "#b25a00", "alon": "#6a45b8", "biddy": "#b3305a"}

def tidy(s):
    s = re.sub(r"-\s*\n\s*", "", s or "")
    return re.sub(r"\s+", " ", s).replace("’", "'").strip().rstrip(",").strip()

def load():
    raw = urllib.request.urlopen(SHEET).read().decode("utf-8")
    rows = list(csv.reader(io.StringIO(raw)))
    hi = next(i for i, r in enumerate(rows) if r and tidy(r[0]).lower() == "piece")
    heads, group = [], ""
    for g, h in zip(rows[hi - 1] + [""] * len(rows[hi]), rows[hi]):
        h, group = tidy(h).lower(), tidy(g).lower() or group
        heads.append(f"{GROUPED[h]} {group}" if h in GROUPED and group else h)
    groups = [tidy(g).lower() for g in rows[hi - 1] if tidy(g)]
    people = [h.capitalize() for h in groups + heads if h and " " not in h and h not in SHARED]
    pieces = [{h: tidy(v) for h, v in zip(heads, r) if h} for r in rows[hi + 1:]]
    return heads, people, [p for p in pieces if p.get("piece")]

def get(r, field, p):
    return r.get(f"{field} {p.lower()}") or r.get(field) or ""

def part(r, p):
    d = {f: get(r, f, p) for f in OPTIONAL}
    d.update(worn=get(r, "where it's worn", p), sound=get(r, "mapping", p), play=r.get(p.lower(), ""))
    if d["play"] == "-" or d["sound"] == "-":
        d["state"] = "rest"
        return d
    has = d["worn"] or d["sound"] or d["play"] or d["actions"]
    solo = "solo" in r["piece"].lower()
    if solo and p.lower() in r["piece"].lower(): d["state"] = "mine"
    elif solo and not has: d["state"] = "rest"
    else: d["state"] = "mine" if has else "blank"
    return d

SKELE = (pathlib.Path(__file__).resolve().parent.parent / "visuals" / "skele.png").as_uri()
JOINTS = {"wrist r": (181, 114), "wrist l": (517, 232), "shoulder": (264, 250),
          "foot r": (62, 515), "foot l": (330, 712), "hand r": (196, 62), "hand l": (543, 196)}

def figure(worn, ink):
    w = worn.lower()
    spots = []
    if "foot" in w or "feet" in w: spots += ["foot r", "foot l"]
    if "shoulder" in w: spots += ["shoulder"]
    if "held" in w: spots += ["hand r", "hand l"]
    elif "left and right" in w or "both" in w: spots += ["wrist r", "wrist l"]
    elif "wrist" in w: spots += ["wrist r"]
    marks = "".join(f'<circle cx="{JOINTS[k][0]}" cy="{JOINTS[k][1]}" r="30" fill="none" stroke="{ink}" stroke-width="12"/>' for k in spots)
    return (f'<div class="skele"><img src="{SKELE}" alt="">'
            f'<svg viewBox="0 0 587 756">{marks}</svg></div>')

def energy(level):
    steps = [{"low": 1, "med": 2, "hig": 3}.get(w.strip()[:3], 0) for w in level.lower().split("/")]
    fill = sum(steps) / len(steps)
    return '<div class="dots">' + "".join(f'<i class="{"on" if fill >= j + 1 else "half" if fill > j else ""}"></i>' for j in range(3)) + "</div>"

def timeline(bits, ticks=32):
    marks = "".join(
        f'<line x1="{x:.2f}" y1="{100 - (7 if j % 8 == 0 else 4 if j % 4 == 0 else 2)}" x2="{x:.2f}" y2="100"/>'
        for j in range(ticks + 1) for x in [j * 1000 / ticks])
    return (f'<svg class="tl" viewBox="0 0 1000 100" preserveAspectRatio="none">'
            f'<line class="mid" x1="0" y1="50" x2="1000" y2="50"/><g class="ticks">{marks}</g></svg>'
            f'<div class="bits">{"".join(f"<span>{html.escape(b)}</span>" for b in bits)}</div>')

def stage():
    marks = [("BIDDY", 84.81, 20.5, "562/261", -45), ("ALON", 0, 22.4, "614/590", 0)]
    marks = "".join(f'<div class="mark" style="right:{x}%;width:{w}%;aspect-ratio:{a};transform:rotate({r}deg)">{n}</div>'
                    for n, x, w, a, r in marks)
    return ('<div class="field stage"><div class="lab">Stage</div><div class="plan">'
            '<svg viewBox="0 0 274 201" preserveAspectRatio="none"><path d="M0,0 H274 V100.5 A137,100.5 0 0 1 0,100.5 Z"/></svg>'
            f'{marks}</div></div>')

def box(label, cls="write"):
    return f'<div class="field"><div class="lab">{label}</div><div class="{cls}"></div></div>'

def page(r, i, n, p, people, pieces, stamp, wears):
    e = html.escape
    d, ink = part(r, p), INK.get(p.lower(), "#333")
    happens = r.get("what happens") or r.get("task")
    task = f'<div class="task"><div class="lab">What happens</div>{e(happens)}</div>' if happens else ""
    if d["state"] == "rest":
        soloist = next((o for o in people if o.lower() in r["piece"].lower()), "")
        body = f'<div class="rest">{"Rest and watch " + soloist if soloist else "Rest this piece"}</div>'
    else:
        what = d["sound"] or d["play"]
        kind = "Your sound" if d["sound"] else "You play" if d["play"] else "Your part"
        part_html = (f'<div class="big">{e(what)}</div>' if what else '<div class="write"></div>')
        fig = (f'<div class="fig">{figure(d["worn"], ink)}<div class="worn">{e(d["worn"]) or "&nbsp;"}</div></div>' if wears else "")
        body = (f'<div class="main">{fig}<div class="facts">'
                f'<div class="field"><div class="lab">{kind}</div>{part_html}</div>'
                f'<div class="field"><div class="lab">Visuals</div>{f"<div class=big>{e(r.get('visuals', ''))}</div>" if r.get("visuals") else "<div class=write></div>"}</div>'
                f'<div class="field"><div class="lab">Energy</div>{energy(r.get("energy", ""))}</div>'
                f'</div><div class="needs">{box("Needs", "write tall")}</div></div>'
                f'{task}'
                f'{stage() if p.lower() in STAGED else ""}'
                f'<div class="field grow"><div class="lab">Timeline</div><div class="tlbox">{timeline([b.strip() for b in r.get("timeline", "").split("->") if b.strip()])}</div></div>')
    return f'''<section class="page" style="--ink:{ink}">
  <header><span>Beyond the Bones · <b>{e(p)}</b></span></header>
  <div class="title"><h1>{e(r["piece"])}</h1></div>
  {body}
  <footer><span>{e(p)} · page {i + 1} of {n}</span><span>score sheet {stamp}</span></footer>
</section>'''

CSS = """
@page{size:A4;margin:0}
*{box-sizing:border-box}
body{margin:0;font-family:"Atkinson Hyperlegible",Verdana,sans-serif;color:#111;-webkit-print-color-adjust:exact;print-color-adjust:exact}
.page{width:210mm;height:297mm;padding:12mm 15mm 10mm;display:flex;flex-direction:column;gap:5mm;page-break-after:always;overflow:hidden}
header{display:flex;justify-content:space-between;align-items:center;font-size:12pt;color:#444;gap:6mm}
.title{display:flex;align-items:center;gap:6mm;border-bottom:1.2mm solid #111;padding-bottom:3mm}
h1{margin:0;font-size:40pt;line-height:1.05}
.lab{font-size:11pt;letter-spacing:.08em;text-transform:uppercase;color:#555;margin-bottom:1.5mm}
.main{display:flex;gap:9mm;align-items:stretch}
.fig{flex:none;width:34mm;display:flex;flex-direction:column;align-items:center;gap:1mm}
.skele{position:relative;width:34mm;aspect-ratio:587/756}
.skele img,.skele svg{position:absolute;inset:0;width:100%;height:100%}
.worn{font-size:12pt;font-weight:700;text-align:center;min-height:9mm;border-bottom:0.4mm solid #777;width:100%}
.facts{flex:1;display:flex;flex-direction:column;gap:6mm;min-width:0}
.big{font-size:14pt;font-weight:700;line-height:1.15}
.write{height:10mm;border-bottom:0.4mm solid #777}
.write.tall{flex:1;min-height:24mm;border:0.4mm solid #777;border-radius:2mm}
.needs,.needs .field{flex:1;display:flex;flex-direction:column;min-width:0}
.dots{display:flex;gap:3mm}
.dots i{width:10mm;height:10mm;border-radius:50%;border:0.6mm solid #333}
.dots i.on{background:#333}
.dots i.half{background:linear-gradient(90deg,#333 50%,transparent 50%)}
.stage{flex:1;display:flex;flex-direction:column;min-height:0}
.plan{flex:1;position:relative;min-height:0}
.plan svg{position:absolute;inset:0;width:100%;height:100%;overflow:visible}
.mark{position:absolute;top:0;transform-origin:top right;display:flex;align-items:center;justify-content:center;background:#999;border:0.3mm solid #000;font-size:9pt}
.stage path{fill:none;stroke:#111;stroke-width:1.2mm;vector-effect:non-scaling-stroke}
.stage ~ .grow{flex:none;height:32mm;min-height:0}
.grow{flex:1;display:flex;flex-direction:column;min-height:25mm}
.tlbox{flex:1;position:relative;min-height:0}
.tl{position:absolute;inset:0;width:100%;height:100%;border:0.5mm solid #333;border-radius:2mm}
.bits{position:absolute;inset:0;display:flex;align-items:center}
.bits span{flex:1;text-align:center;font-size:12pt;font-weight:700;padding:0 2mm}
.tl .ticks line{stroke:#333;stroke-width:1.6;vector-effect:non-scaling-stroke}
.tl .mid{stroke:#bbb;stroke-width:1;stroke-dasharray:4 6;vector-effect:non-scaling-stroke}
.task{font-size:15pt;line-height:1.35;background:#f0f0f0;border-radius:3mm;padding:4mm 6mm}
.rest{font-size:34pt;font-weight:700;padding:12mm 0;border:1mm dashed #888;border-radius:4mm;text-align:center}
footer{margin-top:auto;display:flex;justify-content:space-between;font-size:9pt;color:#777}
"""

def main():
    heads, people, pieces = load()
    stamp = datetime.datetime.now().strftime("%-d %b %Y %-I:%M%p").lower()
    OUT.mkdir(parents=True, exist_ok=True)
    tmp = tempfile.mkdtemp()
    for p in people:
        wears = any(h == f"where it's worn {p.lower()}" for h in heads)
        pages = "".join(page(r, i, len(pieces), p, people, pieces, stamp, wears) for i, r in enumerate(pieces))
        doc = (f'<!doctype html><html><head><meta charset="utf-8"><title>BtB {p}</title>'
               '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Atkinson+Hyperlegible:wght@400;700&display=swap">'
               f'<style>{CSS}</style></head><body>{pages}</body></html>')
        src = pathlib.Path(tmp) / f"BtB_{p}.html"
        src.write_text(doc, encoding="utf-8")
        pdf = OUT / f"BtB_{p}.pdf"
        subprocess.run([CHROME, "--headless", "--disable-gpu", "--no-pdf-header-footer", "--virtual-time-budget=5000",
                        f"--print-to-pdf={pdf}", src.as_uri()], check=True, capture_output=True)
        print(pdf)

main()
