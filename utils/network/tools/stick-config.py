#!/usr/bin/env python3
"""Configure one Airstick from the manifest. Run on the kit's Pi, AP up.

    sudo network/tools/stick-config.py               set the stick, read it back
    sudo network/tools/stick-config.py --dry-run     show what would be sent
    sudo network/tools/stick-config.py --show        read the stick only
         ... --ip 192.168.250.182 | --mac 90:70:...  which stick, if several are on
         ... --manifest other.md                     default: network/stick.md

Edit the manifest for the stick in hand, then run. The stick stores what it's
sent. Only settings that differ are sent, then confirmed with GetConfig.
"""
import os, re, socket, struct, subprocess, sys

MANIFEST = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "stick.md")
STICK_PORT = 8888
REPLY_PORT = 9999
OSC_PORT = 57120
IW = "/usr/sbin/iw"


def pad(b):
    return b + b"\0" * (4 - len(b) % 4)


def osc(addr, *args):
    tags = "," + "".join("i" if isinstance(a, int) else "s" for a in args)
    out = pad(addr.encode()) + pad(tags.encode())
    for a in args:
        out += struct.pack(">i", a) if isinstance(a, int) else pad(a.encode())
    return out


def unosc(d):
    def s(i):
        e = d.index(b"\0", i)
        return d[i:e].decode(errors="replace"), (e + 4) & ~3
    addr, i = s(0)
    tags, i = s(i)
    vals = []
    for c in tags[1:]:
        if c in "if":
            vals.append(struct.unpack(">i" if c == "i" else ">f", d[i:i + 4])[0])
            i += 4
        elif c == "s":
            v, i = s(i)
            vals.append(v)
    return addr, vals


def run(*cmd):
    return subprocess.run(cmd, capture_output=True, text=True).stdout


def ap():
    """(interface, its IPv4) of the radio in AP mode."""
    for block in run(IW, "dev").split("Interface ")[1:]:
        if "type AP" in block:
            iface = block.split()[0]
            f = run("ip", "-4", "-br", "addr", "show", iface).split()
            return iface, (f[2].split("/")[0] if len(f) > 2 else None)
    return None, None


def sticks_on(iface):
    """[(mac, ip or None)] joined to the AP."""
    neigh = {}
    for line in run("ip", "neigh", "show", "dev", iface).splitlines():
        f = line.split()
        if "lladdr" in f:
            neigh[f[f.index("lladdr") + 1].lower()] = f[0]
    macs = re.findall(r"^Station ([0-9a-f:]{17})", run(IW, "dev", iface, "station", "dump"), re.M)
    return [(m, neigh.get(m)) for m in macs]


def read_manifest(path):
    m = {}
    for line in open(path):
        hit = re.match(r"^\s*[-*]?\s*(id|led|stream)\s*:\s*([^#]*?)\s*(#.*)?$", line, re.I)
        if hit:
            m[hit.group(1).lower()] = hit.group(2)
    want = {}
    if m.get("id"):
        want["id"] = m["id"]
    if m.get("led"):
        led = [int(x) for x in re.split(r"[,\s]+", m["led"]) if x]
        if len(led) != 4 or not all(0 <= x <= 255 for x in led):
            raise ValueError("led: needs 4 numbers 0-255 (R, G, B, alpha)")
        want["led"] = led
    if m.get("stream"):
        want["stream"] = m["stream"]
    if not want:
        raise ValueError("no id:, led: or stream: lines")
    return want


def get_config(sock, ip):
    """The stick's settings, or None if it doesn't answer."""
    for _ in range(3):
        sock.sendto(osc("/Config/GetConfig", REPLY_PORT), (ip, STICK_PORT))
        try:
            while True:
                d, src = sock.recvfrom(2048)
                addr, v = unosc(d)
                # id, ?, ?, own ip x4, own port, stream ip x4, stream port, ..., R G B A
                if src[0] == ip and addr.endswith("/Config") and len(v) >= 18:
                    return {"id": str(v[0]),
                            "stream": "%d.%d.%d.%d:%d" % tuple(v[8:13]),
                            "led": list(v[-4:]),
                            "raw": v}
        except socket.timeout:
            pass
    return None


def describe(c):
    return "id=%s led=%s stream=%s" % (c["id"], ",".join(map(str, c["led"])), c["stream"])


def changes(want, have):
    out = []
    if "id" in want and want["id"] != have["id"]:
        out.append(("id", "/Config/SetID", [want["id"]]))
    if "led" in want and want["led"] != have["led"]:
        out.append(("led", "/Config/SetLED", want["led"]))
    if "stream" in want and want["stream"] != have["stream"]:
        host, port = want["stream"].rsplit(":", 1)
        out.append(("stream", "/Config/RequestStream",
                    [int(x) for x in host.split(".")] + [int(port)]))
    return out


def opt(argv, name):
    return argv[argv.index(name) + 1] if name in argv and argv.index(name) + 1 < len(argv) else None


def main(argv):
    if "-h" in argv or "--help" in argv:
        print(__doc__.strip()); return 0
    dry, show = "--dry-run" in argv, "--show" in argv
    path = opt(argv, "--manifest") or MANIFEST

    iface, ap_ip = ap()
    if not iface:
        print("No AP running."); return 1

    on = sticks_on(iface)
    ip, mac = opt(argv, "--ip"), (opt(argv, "--mac") or "").lower()
    if mac:
        ip = dict(on).get(mac)
        if not ip:
            print("%s isn't on the AP." % mac); return 1
    elif not ip:
        if len(on) != 1:
            print("%d sticks on the AP; pick one with --ip or --mac:" % len(on))
            for m, i in on:
                print("    %s  %s" % (m, i or "(no IP yet)"))
            return 1
        mac, ip = on[0]
        if not ip:
            print("%s is joined but hasn't sent anything yet; try again." % mac); return 1

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("", REPLY_PORT))
    sock.settimeout(1.0)
    have = get_config(sock, ip)
    if not have:
        print("%s: no answer to GetConfig." % ip); return 1
    print("stick %s %s" % (ip, mac or ""))
    print("  now   %s" % describe(have))
    if show:
        return 0

    try:
        want = read_manifest(path)
    except (OSError, ValueError) as e:
        print("manifest %s: %s" % (path, e)); return 1
    if want.get("stream", "").lower() == "auto":
        want["stream"] = "%s:%d" % (ap_ip, OSC_PORT)

    todo = changes(want, have)
    if not todo:
        print("  already matches the manifest"); return 0
    for key, addr, args in todo:
        print("  %s %s %s" % ("would send" if dry else "send", addr, " ".join(map(str, args))))
    if dry:
        return 0

    for key, addr, args in todo:
        sock.sendto(osc(addr, *args), (ip, STICK_PORT))
    after = get_config(sock, ip)
    if not after:
        print("  NOT CONFIRMED: no answer after sending"); return 1
    print("  after %s" % describe(after))
    left = changes(want, after)
    if left:
        print("  NOT CONFIRMED: %s  (raw %s)" % (", ".join(k for k, _, _ in left), after["raw"]))
        return 1
    print("  done")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
