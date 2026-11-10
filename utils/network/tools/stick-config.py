#!/usr/bin/env python3
"""Configure Airsticks from their manifests. Run on the kit's Pi, AP up.

    network/tools/stick-config.py STICK.md [STICK.md ...]   set, then read back
    network/tools/stick-config.py --dry-run STICK.md ...    show what would be sent
    network/tools/stick-config.py --show STICK.md ...       read back only

A manifest is one stick, hand-written, `key: value` lines (anything else in
the file is ignored, so it can carry notes):

    mac:    90:70:69:23:b7:90       which stick (sticks.sh lists them)
    id:     1                       its OSC prefix: /1/IMUFusedData
    led:    255, 0, 0, 255          R, G, B, alpha/brightness (0-255)
    stream: auto                    where it sends; auto = this AP:57120
    ip:     192.168.250.182         optional, if the MAC can't be looked up

The stick stores what it's sent. Only settings that differ are sent; each is
then confirmed with /Config/GetConfig.
"""
import re, socket, struct, subprocess, sys

STICK_PORT = 8888
REPLY_PORT = 9999
OSC_PORT = 57120


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


def ap_iface_addr():
    """The interface in AP mode and its IPv4 address."""
    info = subprocess.run(["/usr/sbin/iw", "dev"], capture_output=True, text=True).stdout
    for block in info.split("Interface ")[1:]:
        if "type AP" in block:
            iface = block.split()[0]
            ip = subprocess.run(["ip", "-4", "-br", "addr", "show", iface],
                                capture_output=True, text=True).stdout.split()
            return iface, (ip[2].split("/")[0] if len(ip) > 2 else None)
    return None, None


def ip_for_mac(iface, mac):
    out = subprocess.run(["ip", "neigh", "show", "dev", iface],
                         capture_output=True, text=True).stdout
    for line in out.splitlines():
        f = line.split()
        if "lladdr" in f and f[f.index("lladdr") + 1].lower() == mac:
            return f[0]
    return None


def read_manifest(path):
    m = {}
    for line in open(path):
        hit = re.match(r"^\s*[-*]?\s*(mac|id|led|stream|ip)\s*:\s*([^#]*?)\s*(#.*)?$", line, re.I)
        if hit:
            m[hit.group(1).lower()] = hit.group(2)
    if "mac" not in m and "ip" not in m:
        raise ValueError("needs mac: (or ip:)")
    want = {}
    if "id" in m:
        want["id"] = m["id"]
    if "led" in m:
        led = [int(x) for x in re.split(r"[,\s]+", m["led"]) if x]
        if len(led) != 4 or not all(0 <= x <= 255 for x in led):
            raise ValueError("led: needs 4 numbers 0-255 (R, G, B, alpha)")
        want["led"] = led
    if "stream" in m:
        want["stream"] = m["stream"]
    return m.get("mac", "").lower(), m.get("ip"), want


def get_config(sock, ip):
    """Ask the stick for its config. None if it doesn't answer."""
    for _ in range(3):
        sock.sendto(osc("/Config/GetConfig", REPLY_PORT), (ip, STICK_PORT))
        try:
            while True:
                d, src = sock.recvfrom(2048)
                addr, v = unosc(d)
                if src[0] == ip and addr.endswith("/Config") and len(v) >= 18:
                    return {"id": str(v[0]),
                            "stream": "%d.%d.%d.%d:%d" % tuple(v[8:13]),
                            "led": list(v[-4:]),
                            "raw": v}
        except socket.timeout:
            pass
    return None


def messages(want, have):
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


def main(argv):
    dry = "--dry-run" in argv
    show = "--show" in argv
    paths = [a for a in argv if not a.startswith("--")]
    if not paths:
        print(__doc__.strip())
        return 2

    iface, ap_ip = ap_iface_addr()
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("", REPLY_PORT))
    sock.settimeout(1.0)
    failed = 0

    for path in paths:
        name = path.rsplit("/", 1)[-1]
        try:
            mac, ip, want = read_manifest(path)
        except (OSError, ValueError) as e:
            print("%s: BAD MANIFEST %s" % (name, e)); failed += 1; continue
        if want.get("stream", "").lower() == "auto":
            if not ap_ip:
                print("%s: stream: auto, but no AP is running" % name); failed += 1; continue
            want["stream"] = "%s:%d" % (ap_ip, OSC_PORT)

        ip = ip or (ip_for_mac(iface, mac) if iface else None)
        if not ip:
            print("%s: OFFLINE (%s not on the AP)" % (name, mac)); failed += 1; continue
        have = get_config(sock, ip)
        if not have:
            print("%s: NO ANSWER from %s" % (name, ip)); failed += 1; continue

        now = "id=%s led=%s stream=%s" % (have["id"], ",".join(map(str, have["led"])), have["stream"])
        todo = messages(want, have)
        if show or not todo:
            print("%s: %s %s  %s" % (name, ip, "unchanged" if not show else "is", now))
            continue
        if dry:
            print("%s: %s  %s" % (name, ip, now))
            for key, addr, args in todo:
                print("    would send %s %s" % (addr, " ".join(map(str, args))))
            continue

        for key, addr, args in todo:
            sock.sendto(osc(addr, *args), (ip, STICK_PORT))
        after = get_config(sock, ip)
        left = messages(want, after) if after else todo
        if left:
            print("%s: %s NOT CONFIRMED: %s" % (name, ip, ", ".join(k for k, _, _ in left)))
            if after:
                print("    stick reports id=%s led=%s stream=%s  raw=%s" % (
                    after["id"], ",".join(map(str, after["led"])), after["stream"], after["raw"]))
            failed += 1
        else:
            print("%s: %s set %s" % (name, ip, ", ".join(k for k, _, _ in todo)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
