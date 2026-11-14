#!/usr/bin/env python3
# Read each piece's pfiles per performer from the BtB score sheet into setlist.json for record.scd.
import csv, difflib, io, json, pathlib, re, urllib.request

SHEET = "https://docs.google.com/spreadsheets/d/141LOojjAL-YPgWICARAQv5rP0xYEFDPfE9Ve3aBmUG4/export?format=csv"
HERE = pathlib.Path(__file__).resolve().parent
SECONDS = 45
PREPARE = 60
SETTLE = 3

def tidy(s):
    s = re.sub(r"-\s*\n\s*", "", s or "")
    return re.sub(r"\s+", " ", s).strip()

def roster(performer):
    path = HERE.parent / "lists" / f"list_BtB_{performer.capitalize()}.sc"
    return re.findall(r'"([^"]+)"', path.read_text()) if path.exists() else []

def key(name):
    return re.sub(r"[^a-z0-9_]", "", name.lower())

def resolve(name, names):
    keys = {key(n): n for n in names}
    k = key(name)
    hit = k if k in keys else next(iter(difflib.get_close_matches(k, keys, 1, 0.6)), None)
    return keys.get(hit)

def main():
    rows = list(csv.reader(io.StringIO(urllib.request.urlopen(SHEET).read().decode("utf-8"))))
    hi = next(i for i, r in enumerate(rows) if r and tidy(r[0]).lower() == "piece")
    cols, group = {}, ""
    for j, (g, h) in enumerate(zip(rows[hi - 1] + [""] * len(rows[hi]), rows[hi])):
        group = tidy(g).lower() or group
        if tidy(h).lower() == "pfiles" and group:
            cols[group] = j
    pieces = []
    for r in rows[hi + 1:]:
        title = tidy(r[0])
        parts = {p: [n.strip() for n in re.split(r"[,\n]", r[j]) if n.strip()] for p, j in cols.items() if j < len(r)}
        for p, names in parts.items():
            known, found = roster(p), []
            for n in names:
                hit = resolve(n, known)
                if hit is None:
                    print(f"  {tidy(r[0])}: {p} '{n}' matches nothing in list_BtB_{p.capitalize()}.sc - skipped")
                else:
                    if hit != n:
                        print(f"  {tidy(r[0])}: {p} '{n}' -> {hit}")
                    found.append(hit)
            parts[p] = found
        parts = {p: names for p, names in parts.items() if names}
        if title and parts:
            pieces.append({"title": title, "file": re.sub(r"[^\w\- ]", "", title).strip(), "parts": parts})
            print(f"{title}: " + " | ".join(f"{p} {', '.join(n)}" for p, n in parts.items()))
    (HERE / "setlist.json").write_text(json.dumps(
        {"seconds": SECONDS, "prepare": PREPARE, "settle": SETTLE, "pieces": pieces}, indent=2))

main()
