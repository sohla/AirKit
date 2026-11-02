#!/usr/bin/env python3
"""Regenerate every analysis artefact in pages/output/.

The JSON in pages/output/ is gitignored: these scripts are the source of truth and
the data they emit is disposable. Run this after any stretch of work you want
the visualisations to reflect, then serve the directory - the pages fetch
their data over XHR, so opening them as file:// gets you a blank page.

    python3 update.py
    python3 serve.py

Every analyzer reads the same commit scope (GIT_SCOPE, declared at the
top of each one). That is what makes the timelines comparable to each other -
if you widen or narrow it, do so in all of them or the pages start describing
different populations of commits.

The summary at the end prints the newest commit date found inside each
artefact. If those dates trail the repo HEAD printed at the top, the data is
stale and something failed quietly.
"""

import json
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUTPUT_DIR = HERE / 'pages' / 'output'

# analyzer -> the artefact it writes.
ANALYZERS = [
    ('analyze_file_networks.py',    'file_networks.json'),
    ('work_type_analyzer.py',       'work_type_data.json'),
    ('analyze_sensor_matrix.py',    'sensor_matrix.json'),
    ('analyze_sound_vocabulary.py', 'sound_vocabulary.json'),
    ('analyze_personality_code.py', 'personality_code_data.json'),
    ('personality_analyzer.py',     'personality_similarities.json'),
    ('analyze_personality_history.py', 'personality_history.json'),
    ('analyze_git.py',              'commits_data.json'),
]


def repo_head():
    result = subprocess.run(
        ['git', 'log', '-1', '--pretty=%h %ai %s'],
        cwd=HERE.parent, capture_output=True, text=True
    )
    return result.stdout.strip()


def newest_commit_date(path):
    """Newest commit date embedded anywhere in an artefact.

    A heuristic walk for 'date'/'datetime' keys rather than a per-artefact
    reader - the seven scripts nest their commit records differently, and all
    this needs to answer is "how far forward does this data actually reach".
    Depth and list length are capped so a 7MB similarity matrix stays cheap.

    Granularity varies: work_type_data aggregates to 'YYYY-MM', the rest carry
    full 'YYYY-MM-DD'. Both are accepted; compare a row against itself between
    runs, not against the other rows.

    personality_similarities.json reports None by design - it analyses the
    personality files in the working tree, so it has no commit dates in it.
    """
    newest = None

    def walk(node, depth=0):
        nonlocal newest
        if depth > 6:
            return
        if isinstance(node, dict):
            for key, value in node.items():
                if (key in ('date', 'datetime') and isinstance(value, str)
                        and len(value) >= 7 and value[:4].isdigit()):
                    if newest is None or value[:10] > newest:
                        newest = value[:10]
                else:
                    walk(value, depth + 1)
        elif isinstance(node, list):
            for value in node[:5000]:
                walk(value, depth + 1)

    try:
        walk(json.loads(path.read_text()))
    except (OSError, ValueError):
        return None
    return newest


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    print(f"repo HEAD : {repo_head()}")
    print(f"output    : {OUTPUT_DIR}")
    print()

    failed = []
    for script, _ in ANALYZERS:
        print(f"  {script:30s} ", end='', flush=True)
        start = time.monotonic()
        result = subprocess.run(
            [sys.executable, script], cwd=HERE, capture_output=True, text=True
        )
        elapsed = time.monotonic() - start

        if result.returncode == 0:
            print(f"ok      {elapsed:6.1f}s")
        else:
            failed.append(script)
            print(f"FAILED  {elapsed:6.1f}s")
            for line in result.stderr.strip().splitlines()[-5:]:
                print(f"      {line}")

    print()
    print(f"  {'artefact':32s} {'size':>8s}  newest commit in data")
    for _, artefact in ANALYZERS:
        path = OUTPUT_DIR / artefact
        if not path.exists():
            print(f"  {artefact:32s} {'-':>8s}  MISSING")
            continue
        size = f"{path.stat().st_size / 1024:.0f}K"
        print(f"  {artefact:32s} {size:>8s}  {newest_commit_date(path) or 'n/a'}")

    if failed:
        print(f"\n{len(failed)} analyzer(s) failed: {', '.join(failed)}")
        return 1

    print("\nServe with:  python3 serve.py")
    return 0


if __name__ == '__main__':
    sys.exit(main())
