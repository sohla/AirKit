#!/usr/bin/env python3
"""Ansible inventory read from machines/*.scd, so the kit record has one home.

Every `platform: \\linux` machine file is a host, named by its file stem
(airkit1.scd -> airkit1). All of them are reached at pi.local: one card is
on the network at a time, and --limit picks which kit it becomes.
"""
import json, re, sys
from pathlib import Path

MACHINES = Path(__file__).resolve().parents[2] / "machines"

def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)

def value(text, key):
    m = re.search(rf"\b{key}\s*:\s*(\"[^\"]*\"|\\\w+|\[[^\]]*\]|[\w.]+)", text)
    if not m:
        return None
    v = m.group(1)
    if v.startswith('"'):
        return v[1:-1]
    if v.startswith("\\"):
        return v[1:]
    if v.startswith("["):
        return re.findall(r'"([^"]*)"', v)
    return v

def kits():
    for f in sorted(MACHINES.glob("*.scd")):
        t = strip_comments(f.read_text())
        if value(t, "platform") != "linux":
            continue
        yield f.stem, {
            "kit_label": value(t, "label"),
            "kit_user": value(t, "user"),
            "ap_addr": value(t, "apAddr"),
            "kit_macs": value(t, "macs") or [],
            "machine_file": f"machines/{f.name}",
        }

hosts = dict(kits())
inventory = {
    "_meta": {"hostvars": hosts},
    "all": {"vars": {"ansible_user": "pi", "ansible_host": "pi.local",
                     "pi_hostname": "pi"}},
    "airkits": {"hosts": list(hosts)},
}

if "--host" in sys.argv:
    print(json.dumps(hosts.get(sys.argv[-1], {})))
else:
    print(json.dumps(inventory, indent=2))
