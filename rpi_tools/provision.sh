#!/bin/bash
# Run the Ansible playbook against one kit, logging to reports/.
#   ./provision.sh airkit3                 uses airkit3.local
#   ./provision.sh airkit3 192.168.1.57    explicit IP
#   ./provision.sh airkit3 --twice         run again and require changed=0
#   ./provision.sh airkit3 --check         dry run with diff, changes nothing
set -uo pipefail
source "$(dirname "$0")/lib.sh"

[ $# -ge 1 ] || { sed -n '2,6p' "$0"; exit 2; }
kit="$1"; shift
ip=""; twice=0; check=()
for a in "$@"; do
    case "$a" in
        --twice) twice=1 ;;
        --check) check=(--check --diff) ;;
        *)       ip="$a" ;;
    esac
done
extra=(); [ -n "$ip" ] && extra=(-e "ansible_host=$ip")

run() {
    local log="$REPORTS/$kit-$(stamp)-provision$1.log"
    ( cd "$RPI_TOOLS/ansible" &&
      ansible-playbook site.yml --limit "$kit" \
          ${extra[@]+"${extra[@]}"} ${check[@]+"${check[@]}"} ) 2>&1 | tee "$log"
    local rc=${PIPESTATUS[0]}
    echo "log: $log"
    LAST_LOG="$log"
    return $rc
}

run "" || { echo "FAILED: playbook did not complete on $kit"; exit 1; }

if [ $twice = 1 ]; then
    echo; echo "=== idempotency run ==="
    run "-rerun" || { echo "FAILED: second run did not complete"; exit 1; }
    changed=$(grep -E "^$kit +:" "$LAST_LOG" | sed -n 's/.*changed=\([0-9]*\).*/\1/p')
    if [ "$changed" = "0" ]; then
        echo "IDEMPOTENT: second run changed nothing"
    else
        echo "NOT IDEMPOTENT: second run changed $changed task(s) — see the 'changed:' lines in $LAST_LOG"
        exit 1
    fi
fi
