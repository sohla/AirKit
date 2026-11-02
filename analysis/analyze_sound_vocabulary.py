#!/usr/bin/env python3
"""Which UGens each p-file uses -> pages/output/sound_vocabulary.json.

Reads the personality files in the working tree, strips comments, and counts
every live `Name.ar`, `Name.kr` and `Name.ir`. Commented-out UGens (a disabled
`Balance2.ar`) are working notes, not part of the sound, so they are not
counted.

UGens are grouped by the job they do in the patch. A UGen missing from GROUPS
still gets a column, under "other", so a new one shows up without editing this
file - add it to GROUPS to file it properly.
"""

import json
import re
from collections import Counter
from pathlib import Path

from analyze_sensor_matrix import strip_comments, git_dates, SECTION

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
PERSONALITIES = REPO / 'personalities'
OUTPUT_DIR = HERE / 'pages' / 'output'

GROUPS = {
    'oscillators': ['SinOsc', 'Saw', 'LFTri', 'LFCub', 'LFSaw', 'Pulse', 'VarSaw', 'LFPulse', 'Blip', 'LFPar',
                    'SinOscFB', 'DynKlang', 'Gendy1', 'Gendy3', 'StandardL', 'GrainFM'],
    'noise': ['WhiteNoise', 'PinkNoise', 'BrownNoise', 'GrayNoise', 'ClipNoise', 'Dust', 'Dust2',
              'LFNoise1', 'LFNoise2', 'TRand'],
    'samples': ['PlayBuf', 'BufRd', 'Warp1', 'GrainBuf', 'BufRateScale', 'BufFrames', 'BufDur', 'BufSampleRate',
                'Phasor'],
    'resonators': ['DynKlank', 'Klank', 'Pluck', 'Resonz', 'Ringz', 'Formlet'],
    'filters': ['RLPF', 'RHPF', 'LPF', 'HPF', 'BPF', 'BRF', 'MoogFF', 'BLowShelf', 'BHiShelf', 'LeakDC'],
    'envelopes & triggers': ['EnvGen', 'Line', 'XLine', 'Decay', 'Decay2', 'Impulse', 'Trig1', 'TDelay', 'Latch',
                             'Demand', 'Duty', 'Lag', 'LagUD'],
    'delays & reverbs': ['DelayC', 'DelayN', 'Delay1', 'AllpassC', 'AllpassL', 'CombL', 'CombN', 'LocalIn',
                         'LocalOut', 'FreeVerb', 'FreeVerb2', 'GVerb', 'Greyhole', 'PitchShift', 'GrainIn'],
    'dynamics & shaping': ['Compander', 'Limiter', 'Shaper', 'Clip', 'Wrap'],
    'analysis': ['Amplitude', 'RunningMax', 'DetectSilence', 'SendReply'],
    'routing & space': ['Out', 'In', 'SoundIn', 'Pan2', 'Pan4', 'PanAz', 'Balance2', 'Splay', 'Mix', 'XFade2',
                        'Select', 'SelectX', 'SelectXFocus', 'LinLin', 'K2A', 'A2K', 'T2A', 'MouseX', 'MouseY'],
}
GROUP_OF = {ugen: group for group, ugens in GROUPS.items() for ugen in ugens}
GROUP_ORDER = list(GROUPS) + ['other']

UGEN = re.compile(r'\b([A-Z][A-Za-z0-9]*)\.(ar|kr|ir)\b')


def analyse(path):
    raw = path.read_bytes().decode(errors='replace')
    source = raw.splitlines()
    counts = Counter()
    lines = []
    section = 'top'

    for number, code in enumerate(strip_comments(raw), 1):
        heading = SECTION.match(code.strip())
        if heading and not code.startswith((' ', '\t')):
            section = heading.group(1)

        ugens = [name for name, _rate in UGEN.findall(code)]
        if not ugens:
            continue
        counts.update(ugens)
        lines.append({
            'line': number,
            'section': section,
            'keys': sorted(set(ugens)),
            'text': source[number - 1].strip()[:240],
        })

    return {
        'counts': dict(counts),
        'synthdefs': len(re.findall(r'\bSynthDef\s*\(', '\n'.join(strip_comments(raw)))),
        'lines': lines,
    }


def main():
    dates = git_dates()
    files = []
    for path in sorted(PERSONALITIES.glob('*.sc'), key=lambda p: p.stem.lower()):
        born, modified = dates.get(path.stem, (None, None))
        files.append({'name': path.stem, 'born': born, 'date': modified, **analyse(path)})

    usage = Counter()
    for f in files:
        usage.update(f['counts'].keys())

    group = lambda k: GROUP_OF.get(k, 'other')
    columns = sorted(usage, key=lambda k: (GROUP_ORDER.index(group(k)), -usage[k], k.lower()))
    columns = [{'key': k, 'group': group(k), 'files': usage[k]} for k in columns]

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    out = OUTPUT_DIR / 'sound_vocabulary.json'
    out.write_text(json.dumps({'groups': GROUP_ORDER, 'columns': columns, 'files': files}, indent=1))

    print(f'Wrote {out}')
    print(f'  {len(files)} p-files, {len(columns)} UGens')
    for g in GROUP_ORDER:
        members = [c for c in columns if c['group'] == g]
        if members:
            print(f"  {g:22s} {len(members):3d} UGens  " + ' '.join(f"{c['key']}:{c['files']}" for c in members[:6]))


if __name__ == '__main__':
    main()
