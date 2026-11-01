# Shared by the rpi_tools scripts. Sourced, not run. bash 3.2 safe (macOS).

RPI_TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(dirname "$RPI_TOOLS")"
MACHINES="$REPO/machines"
REPORTS="$RPI_TOOLS/reports"
PI_USER="${PI_USER:-pi}"

# Every reflash gives the Pi a new host key at the same name/IP, so known_hosts
# is bypassed. Fine on a private LAN for provisioning; never for anything else.
SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout=5
          -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no
          -o LogLevel=ERROR)

# Raspberry Pi Ltd / Foundation MAC prefixes.
PI_OUIS="b8:27:eb dc:a6:32 e4:5f:01 28:cd:c1 d8:3a:dd 2c:cf:67 88:a2:9e"

mkdir -p "$REPORTS"

stamp() { date +%Y%m%d-%H%M%S; }

# macOS arp drops leading zeros (d8:3a:dd:a:3:4); machines/*.scd does not.
norm_mac() {
    echo "$1" | tr 'A-F' 'a-f' |
        awk 'BEGIN{FS=OFS=":"} {for(i=1;i<=NF;i++) if(length($i)==1) $i="0"$i; print}'
}

is_pi_mac() {
    local oui="${1:0:8}" o
    for o in $PI_OUIS; do [ "$o" = "$oui" ] && return 0; done
    return 1
}

# Label of the machines/*.scd file listing this MAC, or empty.
kit_for_mac() {
    local f
    f=$(grep -il "\"$1\"" "$MACHINES"/*.scd 2>/dev/null | head -1)
    [ -n "$f" ] && grep -o 'label: *"[^"]*"' "$f" | head -1 | sed 's/.*"\(.*\)"/\1/'
}

pi_ssh() { local host="$1"; shift; ssh "${SSH_OPTS[@]}" "$PI_USER@$host" "$@"; }
