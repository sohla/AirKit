#!/bin/bash
# Copy tests/ to the Pi, run goss, save the report to reports/.
#   ./verify.sh airkit3 [ip]
# Exit status is goss's: 0 = every check passed.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

[ $# -ge 1 ] || { sed -n '2,4p' "$0"; exit 2; }
kit="$1"; host="${2:-pi.local}"
report="$REPORTS/$kit-$(stamp)-verify.txt"

pi_ssh "$host" 'rm -rf airkit-tests airkit-tests.new' || { echo "Cannot ssh to $host" >&2; exit 1; }
scp -q -r "${SSH_OPTS[@]}" "$RPI_TOOLS/tests" "$PI_USER@$host:airkit-tests.new" &&
    pi_ssh "$host" 'mv airkit-tests.new airkit-tests && chmod +x airkit-tests/probes/*.sh 2>/dev/null; true'

{
    echo "# $kit ($host)  $(date)"
    echo "# rpi_tools rev $(git -C "$RPI_TOOLS" rev-parse --short HEAD 2>/dev/null)$(git -C "$RPI_TOOLS" diff --quiet -- . 2>/dev/null || echo '-dirty')"
    echo
    # One check at a time: the jack and audio probes each start jackd.
    pi_ssh "$host" "cd airkit-tests && sudo goss -g goss.yaml --vars-inline '{\"kit\":\"$kit\"}' validate --max-concurrent 1 --format documentation --no-color"
} 2>&1 | tee "$report"
rc=${PIPESTATUS[0]}

echo "report: $report"
exit $rc
