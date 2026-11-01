#!/usr/bin/env python3
"""Which sensor signals each p-file reads -> pages/output/sensor_matrix.json.

Reads the personality files in the working tree, strips comments, and counts
every live read of a model signal (`m.accelMassFiltered`) or a raw sensor
(`d.sensors.gyroEvent.y`). Columns are discovered from the corpus rather than
listed here, so a new signal shows up without editing this file.

Reads inside `~plot` are counted apart from the rest: the plot is a probe for
the performer to look at, not a mapping that drives the sound.

The filter constants each file sets (`m.accelMassFilteredAttack = 0.5`) are
collected too, since they shape what every read of that signal hears.
"""

import json
import re
import subprocess
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
PERSONALITIES = REPO / 'personalities'
OUTPUT_DIR = HERE / 'pages' / 'output'

# m.<field> names that are not sensor signals.
NOT_SIGNALS = {'ptn', 'com', 'name', 'midiChannel', 'midiOut'}

MODEL_READ = re.compile(r'\bm\.([A-Za-z]\w*)')
SENSOR_READ = re.compile(r'\bsensors\.([A-Za-z]\w*)\.([A-Za-z]\w*)')
FILTER_SET = re.compile(r'\bm\.(\w+?)(Attack|Decay)\s*=(?!=)\s*(-?[\d.]+)')
ASSIGNMENT = re.compile(r'\bm\.(\w+)\s*=(?!=)')
SECTION = re.compile(r'^~(\w+)\s*=')


def strip_comments(text):
    """Blank out comments but keep line numbering intact."""
    text = re.sub(r'/\*.*?\*/', lambda m: '\n' * m.group(0).count('\n'), text, flags=re.S)
    return [re.sub(r'//.*', '', line) for line in text.splitlines()]


def git_dates():
    """{name: (born, last_modified)} for every p-file, from one git log."""
    result = subprocess.run(
        ['git', 'log', '--format=%x00%as', '--name-only', '--', 'personalities/'],
        cwd=REPO, capture_output=True, text=True,
    )
    born, modified = {}, {}
    date = None
    for line in result.stdout.splitlines():
        if line.startswith('\x00'):
            date = line[1:]
        elif line.endswith('.sc'):
            name = Path(line).stem
            modified.setdefault(name, date)
            born[name] = date
    return {name: (born[name], modified[name]) for name in born}


def column_key(match_model=None, match_sensor=None):
    if match_model:
        return match_model
    return f'{match_sensor[0]}.{match_sensor[1]}'


def analyse(path):
    raw = path.read_bytes().decode(errors='replace')
    source = raw.splitlines()
    live = strip_comments(raw)

    counts, probes = Counter(), Counter()
    filters = defaultdict(dict)
    defines = set()
    lines = []
    section = 'top'

    for number, code in enumerate(live, 1):
        heading = SECTION.match(code.strip())
        if heading and not code.startswith((' ', '\t')):
            section = heading.group(1)

        for name, stage, value in FILTER_SET.findall(code):
            filters[name][stage.lower()] = float(value)
        for name in ASSIGNMENT.findall(code):
            if not name.endswith(('Attack', 'Decay')):
                defines.add(name)

        reads_here = ASSIGNMENT.sub('', code)
        keys = [k for k in MODEL_READ.findall(reads_here)
                if k not in NOT_SIGNALS and not k.endswith(('Attack', 'Decay'))]
        keys += [column_key(match_sensor=s) for s in SENSOR_READ.findall(reads_here)]
        if not keys:
            continue

        tally = probes if section == 'plot' else counts
        tally.update(keys)
        lines.append({
            'line': number,
            'section': section,
            'keys': sorted(set(keys)),
            'text': source[number - 1].strip()[:240],
        })

    return {
        'counts': dict(counts),
        'probes': dict(probes),
        'filters': dict(filters),
        'defines': sorted(defines),
        'lines': lines,
    }


def model_fields():
    """Signals the model publishes, and its filter defaults, from personalityController."""
    text = (REPO / 'code3.0' / 'personalityController.scd').read_text(errors='replace')
    block = re.search(r'~model\s*=\s*\((.*?)\n\s*\);', text, re.S)
    live = '\n'.join(strip_comments(block.group(1))) if block else ''
    signals = [k for k in re.findall(r'\\(\w+)\s*:', live)
               if k not in NOT_SIGNALS and not k.endswith(('Attack', 'Decay'))]
    defaults = defaultdict(dict)
    for name, stage, value in re.findall(r'\\(\w+?)(Attack|Decay)\s*:\s*(-?[\d.]+)', live):
        defaults[name][stage.lower()] = float(value)
    return signals, dict(defaults)


def main():
    dates = git_dates()
    model, filter_defaults = model_fields()
    files = []
    for path in sorted(PERSONALITIES.glob('*.sc'), key=lambda p: p.stem.lower()):
        record = analyse(path)
        born, modified = dates.get(path.stem, (None, None))
        files.append({'name': path.stem, 'born': born, 'date': modified, **record})

    usage = Counter()
    for f in files:
        usage.update(set(f['counts']) | set(f['probes']))

    def group(key):
        if '.' in key:
            return 'raw'
        return 'model' if key in model else 'p-file'

    order = {'model': 0, 'p-file': 1, 'raw': 2}
    columns = sorted(usage, key=lambda k: (order[group(k)], k.lower()))
    columns = [{'key': k, 'group': group(k), 'files': usage[k]} for k in columns]

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    out = OUTPUT_DIR / 'sensor_matrix.json'
    out.write_text(json.dumps({
        'model_signals': model,
        'filter_defaults': filter_defaults,
        'columns': columns,
        'files': files,
    }, indent=1))

    print(f'Wrote {out}')
    print(f'  {len(files)} p-files, {len(columns)} signals')
    for c in columns:
        print(f"  {c['group']:7s} {c['key']:28s} {c['files']:4d} files")


if __name__ == '__main__':
    main()
