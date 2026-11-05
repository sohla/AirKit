#!/bin/bash
# jackd starts from ~/.jackdrc as pi, serves clients, and its audio thread
# runs SCHED_FIFO at the -P priority. Prints JACK-OK rt=<prio> on success.
# If jackd is already running (AirKit up), checks that one instead.
as_pi() { sudo -u pi XDG_RUNTIME_DIR=/run/user/1000 "$@"; }
want=$(grep -o -- '-P[0-9]*' /home/pi/.jackdrc | tr -d -- -P)
[ -n "$want" ] || { echo "no -P in ~/.jackdrc"; exit 1; }

pid=$(pgrep -u pi -x jackd | head -1)
started=""
if [ -z "$pid" ]; then
    log=$(mktemp); trap 'rm -f "$log"' EXIT
    as_pi bash -c "exec $(cat /home/pi/.jackdrc)" >"$log" 2>&1 &
    started=$!
    if ! as_pi jack_wait -w -t 10 >/dev/null 2>&1; then
        echo "jackd did not start:"; tail -5 "$log"; kill $started 2>/dev/null; exit 1
    fi
    pid=$(pgrep -u pi -x jackd | head -1)
fi

rt=$(ps -L -o cls=,rtprio= -p "$pid" | awk '$1=="FF"{print $2}' | sort -n | tail -1)
[ -n "$started" ] && { kill "$pid" 2>/dev/null; wait "$started" 2>/dev/null; }

if [ "$rt" = "$want" ]; then echo "JACK-OK rt=$rt"; else echo "JACK-BAD want FIFO $want, got '${rt:-none}'"; exit 1; fi
