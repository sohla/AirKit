#!/bin/bash
# One full test round on a freshly flashed card:
# provision, provision again (must change nothing), verify.
#   ./round.sh airkit3 [ip]
set -uo pipefail
cd "$(dirname "$0")"

[ $# -ge 1 ] || { sed -n '2,4p' "$0"; exit 2; }
./provision.sh "$@" --twice || exit 1
./verify.sh "$@"
