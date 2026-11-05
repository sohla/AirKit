#!/bin/bash
# First contact with a freshly flashed card. Run it yourself: it asks for the
# Pi's password (up to twice). After this, everything else runs unattended.
#   ./bootstrap.sh [host]          default pi.local
# 1. makes this Mac's SSH key if it has none, and installs it on the Pi
# 2. gives pi passwordless sudo, as older Pi OS images did. AirKit's
#    Shutdown/Network buttons and the updater's deploy.sh rely on it.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
host="${1:-pi.local}"
key=~/.ssh/id_ed25519
opts=(-o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -o LogLevel=ERROR)

[ -f "$key" ] || ssh-keygen -t ed25519 -N "" -C "airkit-provision@$(hostname -s)" -f "$key"

if pi_ssh "$host" true 2>/dev/null; then
    echo "key login: already works"
else
    echo "Installing this Mac's key on $host (enter the Pi password):"
    ssh-copy-id -i "$key.pub" "${opts[@]}" "$PI_USER@$host"
fi

if pi_ssh "$host" 'sudo -n true' 2>/dev/null; then
    echo "sudo: already passwordless"
else
    echo "Making sudo passwordless for $PI_USER (enter the Pi password for sudo):"
    ssh -t "${opts[@]}" "$PI_USER@$host" \
        "echo '$PI_USER ALL=(ALL) NOPASSWD: ALL' | sudo tee /etc/sudoers.d/010_pi-nopasswd >/dev/null &&
         sudo chmod 0440 /etc/sudoers.d/010_pi-nopasswd &&
         sudo visudo -cf /etc/sudoers.d/010_pi-nopasswd"
fi

pi_ssh "$host" 'sudo -n true' && echo "OK: $host ready to provision"
