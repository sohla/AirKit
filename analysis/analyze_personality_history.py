#!/usr/bin/env python3
"""Per-personality archival history: where a p-file came from, how it evolved,
what shipped alongside it, and what it sounds like now.

Every other analyzer here aggregates - across commits, across files, across
time. This one is the opposite: it answers questions about *one* p-file, and
its output is keyed by personality name so the page can look one up.

Three things it knows that nothing else in this directory does:

1. **Lineage.** No other analyzer passes -M/-C, so the fact that
   `cotf_celesta1.sc` was born as a 51% copy of `harp2.sc`, or that
   `JUPITERSHARP.sc` is a 96% rename of `cotf_harp1.sc`, is currently
   invisible repo-wide. Git already knows; we just have to ask.

2. **Co-commit membership per file.** `analyze_file_networks.py` builds a
   global co-change graph; this records, for one p-file, exactly which other
   p-files landed in each of its commits. That is the "what else was I working
   on that day" question.

3. **A generated prose narrative.** Composed from the evidence below, not
   authored - so it regenerates with the data and cannot go stale or drift
   from what the files actually say. See `narrate_*` at the bottom.

Cost is ~3s. The three slow analyzers in this directory shell out to `git show`
once per commit; this one takes the entire diff stream in a single `git log -p`
and splits it up in Python.

The commit scope matches the invariant declared in README.md. That costs almost
nothing here: only 5 of the 183 p-files in the working tree predate
2024-01-01, and those are reported with `predates_window: true` and no birth
commit rather than a fabricated origin date.
"""

import json
import re
import subprocess
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
OUTPUT_DIR = HERE / 'pages' / 'output'
PERSONALITIES = REPO / 'personalities'

# The scope invariant (README.md). Change it here and in every other analyzer
# or the pages stop describing the same population of commits.
GIT_SCOPE = ['--all', '--since=2024-01-01', '--no-merges']
SINCE = '2024-01-01'

# A commit gap wider than this starts a new "burst" in the evolution narrative.
# Work on these files is episodic - a run of days around a gig, then months of
# nothing - so bursts, not months, are the unit the narrative is built from.
BURST_GAP_DAYS = 45

COMMIT_MARK = '@@COMMIT@@'


# --------------------------------------------------------------------------
# line classification
#
# The synthdef/mapping split is the same one analyze_personality_code.py makes,
# imported rather than restated so the two pages cannot disagree about what a
# line is. Visual is added here: it postdates that script, and "the month this
# p-file grew a visual" is a real and legible chapter in several of these
# histories.
# --------------------------------------------------------------------------

sys.path.insert(0, str(HERE))
from analyze_personality_code import SYNTHDEF_PATTERNS, MAPPING_PATTERNS  # noqa: E402
import synthdef_graph as sg  # noqa: E402
from synthdef_graph import strip_comments  # noqa: E402

VISUAL_PATTERNS = [
    r'customVisualEvent',
    r'~vdef',
    r'c\[\\(draw|render|pos|size|width|color|normTime|bounds)\]',
    r'\\(shape|points|numPoints|startSize|endSize|startColor|endColor|'
    r'startWidth|endWidth|sizeEnv|colorEnv|widthEnv|xEnv|yEnv|modulation|'
    r'rotation|viewID)\b',
    r'Color\.(new|rgb|hsv|white|black)',
    r'Pen\.',
]


def classify_line(line):
    """synthdef | mapping | visual | None for one added/removed diff line.

    Visual wins ties because its markers are the most specific: a line with
    both `\\startSize` and `.linlin(` is a visual line that happens to scale
    something, not a mapping line.
    """
    text = line.strip()
    if not text or text.startswith('//'):
        return None

    visual = sum(1 for p in VISUAL_PATTERNS if re.search(p, text))
    synth = sum(1 for p in SYNTHDEF_PATTERNS if re.search(p, text))
    mapping = sum(1 for p in MAPPING_PATTERNS if re.search(p, text))

    if visual and visual >= synth and visual >= mapping:
        return 'visual'
    if synth > mapping:
        return 'synthdef'
    if mapping > synth:
        return 'mapping'
    if synth:
        return 'synthdef'
    return None


# --------------------------------------------------------------------------
# git
# --------------------------------------------------------------------------

def diff_stream():
    """The whole in-scope personality diff history, in one subprocess.

    -M/-C/--find-copies-harder is the point of this script: it is what turns
    "a new file appeared" into "this file was born as a 70% copy of that one".
    --find-copies-harder makes git consider unmodified files as copy sources,
    which is exactly the case here - a new personality is usually a duplicate
    of a working one that was then edited.
    """
    result = subprocess.run(
        ['git', 'log', *GIT_SCOPE, '-M', '-C', '--find-copies-harder',
         '--unified=0', '--no-color', '-p',
         f'--pretty=format:{COMMIT_MARK}%H|%ai|%an|%s',
         '--', 'personalities/*.sc'],
        cwd=REPO, capture_output=True, text=True, errors='replace',
    )
    if result.returncode != 0:
        raise SystemExit(f"git log failed:\n{result.stderr}")
    return result.stdout


def commit_file_lists():
    """hash -> every path touched, personality or not.

    Separate pass, because the diff stream above is filtered to
    personalities/*.sc and so cannot answer "what else was in this commit" -
    which is half of what the archive is for. `--name-only` over the same
    scope is cheap.
    """
    result = subprocess.run(
        ['git', 'log', *GIT_SCOPE, '--name-only',
         f'--pretty=format:{COMMIT_MARK}%H'],
        cwd=REPO, capture_output=True, text=True, errors='replace',
    )
    files = {}
    current = None
    for line in result.stdout.splitlines():
        if line.startswith(COMMIT_MARK):
            current = line[len(COMMIT_MARK):].strip()
            files[current] = []
        elif line.strip() and current:
            files[current].append(line.strip())
    return files


def tree_blobs(rev):
    """{path: blob sha} for the personality files present at a revision."""
    result = subprocess.run(
        ['git', 'ls-tree', '-r', rev, '--', 'personalities/'],
        cwd=REPO, capture_output=True, text=True, errors='replace',
    )
    blobs = {}
    for line in result.stdout.splitlines():
        meta, _, path = line.partition('\t')
        parts = meta.split()
        if len(parts) >= 3 and path.endswith('.sc'):
            blobs[path] = parts[2]
    return blobs


class BlobCache:
    """Line sets for blobs, fetched in batches and reused across commits.

    The rival search compares one new file against every p-file alive at the
    time, for every birth commit - tens of thousands of comparisons. Almost
    all of those blobs recur, because most files do not change between any two
    commits, so caching by sha turns it from minutes into seconds.
    """

    def __init__(self):
        self.lines = {}

    def fetch(self, shas):
        missing = [s for s in shas if s not in self.lines]
        if not missing:
            return
        process = subprocess.run(
            ['git', 'cat-file', '--batch'], cwd=REPO,
            input='\n'.join(missing).encode(), capture_output=True,
        )
        out = process.stdout
        position = 0
        for sha in missing:
            newline = out.find(b'\n', position)
            if newline < 0:
                break
            header = out[position:newline].split()
            if len(header) < 3:
                position = newline + 1
                continue
            size = int(header[2])
            body = out[newline + 1:newline + 1 + size].decode(errors='replace')
            self.lines[sha] = {l.strip() for l in body.splitlines() if l.strip()}
            position = newline + 1 + size + 1

    def get(self, sha):
        return self.lines.get(sha, set())


def jaccard(a, b):
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


DIFF_HEADER = re.compile(r'^diff --git a/(.+?) b/(.+)$')
SIMILARITY = re.compile(r'^similarity index (\d+)%$')


def parse_stream(text):
    """The diff stream -> a list of commits, each with per-file records.

    Each file record is {path, old_path, status, similarity, added, removed}
    where added/removed are counts keyed by classification.
    """
    commits = []
    commit = None
    record = None

    def close_record():
        nonlocal record
        if record is not None and commit is not None:
            commit['files'].append(record)
        record = None

    for line in text.splitlines():
        if line.startswith(COMMIT_MARK):
            close_record()
            parts = line[len(COMMIT_MARK):].split('|', 3)
            if len(parts) < 4:
                commit = None
                continue
            commit = {
                'hash': parts[0],
                'short': parts[0][:8],
                'datetime': parts[1],
                'date': parts[1][:10],
                'author': parts[2],
                'subject': parts[3],
                'files': [],
            }
            commits.append(commit)
            continue

        if commit is None:
            continue

        header = DIFF_HEADER.match(line)
        if header:
            close_record()
            old, new = header.group(1), header.group(2)
            record = {
                'path': new,
                'old_path': old if old != new else None,
                'status': 'M',
                'similarity': None,
                'added': Counter(),
                'removed': Counter(),
                'added_lines': [],
            }
            continue

        if record is None:
            continue

        similarity = SIMILARITY.match(line)
        if similarity:
            record['similarity'] = int(similarity.group(1))
            continue
        if line.startswith('rename from '):
            record['status'] = 'R'
            record['old_path'] = line[len('rename from '):]
            continue
        if line.startswith('copy from '):
            record['status'] = 'C'
            record['old_path'] = line[len('copy from '):]
            continue
        if line.startswith('new file mode'):
            record['status'] = 'A'
            record['old_path'] = None
            continue
        if line.startswith('deleted file mode'):
            record['status'] = 'D'
            continue

        # Diff body. The ---/+++ and @@ headers are not content.
        if line.startswith('+++') or line.startswith('---'):
            continue
        if line.startswith('+'):
            kind = classify_line(line[1:])
            if kind:
                record['added'][kind] += 1
            record['added_lines'].append(line[1:])
        elif line.startswith('-'):
            kind = classify_line(line[1:])
            if kind:
                record['removed'][kind] += 1

    close_record()
    return commits


# --------------------------------------------------------------------------
# ancestry
# --------------------------------------------------------------------------

# Two candidates whose similarity to the newborn differs by less than this are
# treated as a tie worth disclosing. Git names exactly one parent, and when a
# family of near-identical patches is in flight - which is the norm here - that
# choice is close to arbitrary. Saying "copied from brenton1" flatly, when
# jamesCello was within half a point, asserts more than the evidence carries.
RIVAL_MARGIN = 0.04


def build_ancestry(commits):
    """path -> its creation record, over every path, not just tracked ones.

    The chain runs through files that no longer exist - mel1's line reaches
    back through jamesCello and mattCello, neither of which is in the working
    tree - so this has to be built over the full set of paths git saw.
    """
    creations = defaultdict(list)
    for commit in commits:
        for record in commit['files']:
            if record['status'] in ('A', 'C', 'R'):
                creations[record['path']].append({
                    'parent': record['old_path'],
                    'similarity': record['similarity'],
                    'kind': {'C': 'copy', 'R': 'rename', 'A': 'new'}[record['status']],
                    'date': commit['date'],
                    'short': commit['short'],
                    'subject': commit['subject'],
                })
    # Earliest creation is the one that starts the file's line.
    return {path: min(records, key=lambda r: r['date'])
            for path, records in creations.items()}


def ancestor_chain(path, ancestry, tracked):
    """[oldest, ..., parent] for a path. Excludes the file itself.

    Cycle-guarded: a copy A->B followed later by B->A is legal in git and
    would otherwise loop forever.
    """
    chain = []
    seen = {path}
    current = path
    while True:
        record = ancestry.get(current)
        if not record or not record['parent'] or record['parent'] in seen:
            break
        parent = record['parent']
        seen.add(parent)
        chain.append({
            'name': Path(parent).stem,
            'path': parent,
            # How close the CHILD was to this node - i.e. the weight of the hop
            # leading away from it, which is the number that belongs on the
            # arrow when the chain is drawn left to right.
            'similarity': record['similarity'],
            'kind': record['kind'],
            'hop_date': record['date'],
            'short': record['short'],
            # This node's own creation date, which is not the same thing and is
            # what "first seen" means.
            'born': (ancestry.get(parent) or {}).get('date'),
            'in_tree': parent in tracked,
        })
        current = parent
    chain.reverse()
    return chain


def find_rivals(birth, cache):
    """Other p-files that were nearly as close a match at the birth commit.

    Git reports the one source it chose and nothing about how close the
    runners-up were. This recomputes the field with a cheap line-set Jaccard -
    not git's similarity metric, so the numbers are not directly comparable to
    the percentage git reports, but the *ranking* is what matters here.
    """
    parent_rev = f"{birth['hash']}^"
    try:
        candidates = tree_blobs(parent_rev)
        newborn = tree_blobs(birth['hash']).get(birth['path'])
    except Exception:
        return []
    if not newborn or not candidates:
        return []

    cache.fetch([newborn] + list(candidates.values()))
    target = cache.get(newborn)
    if not target:
        return []

    scored = []
    for path, sha in candidates.items():
        if path == birth['path']:
            continue
        score = jaccard(target, cache.get(sha))
        if score > 0.3:
            scored.append({'name': Path(path).stem, 'score': round(score, 4)})
    scored.sort(key=lambda s: -s['score'])
    return scored[:4]


# --------------------------------------------------------------------------
# current-state analysis : what this personality sounds like and how it is played
# --------------------------------------------------------------------------

# The sensor vocabulary these files actually use, measured across the corpus
# rather than assumed. Order matters for display; the gloss is what makes the
# generated gesture prose readable.
SENSORS = [
    ('accelMassFiltered', 'smoothed movement energy'),
    ('accelMassAmp',      'movement energy as an amplitude'),
    ('accelMassThreshold', 'a movement-energy gate'),
    ('accelMass',         'raw movement energy'),
    ('rrateMassFiltered', 'smoothed rotation energy'),
    ('rrateMassThreshold', 'a rotation-energy gate'),
    ('rrateMass',         'raw rotation energy'),
    ('gyroXFiltered',     'tilt about x'),
    ('gyroYFiltered',     'tilt about y'),
    ('gyroZFiltered',     'tilt about z'),
]

DIRECT_SENSORS = [
    ('sensors.accelEvent', 'raw per-axis acceleration'),
    ('sensors.gyroEvent',  'raw per-axis orientation'),
    ('sensors.rrateEvent', 'raw per-axis rotation rate'),
    ('sensors.velocity',   'integrated velocity'),
    ('sensors.digiInEvent', 'the buttons'),
]

# UGen -> the role it plays, so the sound paragraph can say "a resonator bank"
# rather than listing class names at the reader.
SOURCES = {
    'PlayBuf': 'sampled playback', 'BufRd': 'sampled playback',
    'Warp1': 'granular time-stretching', 'GrainBuf': 'granular playback',
    'SinOsc': 'sine tones', 'LFTri': 'triangle tones', 'LFCub': 'soft sine-ish tones',
    'Saw': 'sawtooth tones', 'LFSaw': 'sawtooth tones', 'Pulse': 'pulse tones',
    'Blip': 'band-limited pulse', 'VarSaw': 'variable-width saw',
    'WhiteNoise': 'white noise', 'PinkNoise': 'pink noise', 'BrownNoise': 'brown noise',
    'Dust': 'sparse impulses', 'Impulse': 'impulses', 'Crackle': 'chaotic noise',
    'DynKlank': 'a tuned resonator bank', 'Klank': 'a tuned resonator bank',
    'Ringz': 'ringing resonators', 'Pluck': 'a plucked-string model',
    'Gendy1': 'stochastic waveform synthesis', 'Gendy2': 'stochastic waveform synthesis',
    'Gendy3': 'stochastic waveform synthesis',
    'SoundIn': 'live microphone input',
    'Formant': 'formant synthesis', 'Blip': 'band-limited pulse',
}

PROCESSORS = [
    (('RLPF', 'LPF', 'BLowPass'), 'low-pass filtering'),
    (('RHPF', 'HPF', 'BHiPass'), 'high-pass filtering'),
    (('BPF', 'Resonz', 'MoogFF'), 'band-pass filtering'),
    (('FreeVerb', 'GVerb', 'JPverb', 'Reverb'), 'reverb'),
    (('DelayC', 'DelayN', 'DelayL', 'CombC', 'CombN', 'AllpassC', 'AllpassN'), 'delay'),
    (('Compander', 'Limiter', 'Normalizer'), 'compression'),
    (('LeakDC',), 'DC removal'),
    (('Splay', 'Pan2', 'Balance2', 'LinPan2', 'PanAz'), 'stereo placement'),
    (('Amplitude', 'Pitch', 'Onsets'), 'signal analysis'),
    (('LocalIn', 'LocalOut'), 'a feedback loop'),
]

MAPPING_FUNCS = ('lincurve', 'linlin', 'linexp', 'explin', 'expexp',
                 'curvelin', 'range', 'fold', 'wrap', 'clip')

# Pbind keys that carry the music rather than the picture. Used only to order
# what the sound narrative lists first - nothing is discarded.
MUSICAL_KEYS = {
    'instrument', 'dur', 'note', 'degree', 'octave', 'root', 'scale', 'freq',
    'midinote', 'amp', 'db', 'pan', 'legato', 'sustain', 'stretch', 'rate',
    'bufnum', 'attack', 'release', 'detune', 'harmonic', 'ctranspose', 'strum',
}

# Real pattern classes, so `PathName(` and the Pdef/Pbind containers do not
# get reported as compositional material.
PATTERN_CLASSES = {
    'Pseq', 'Pser', 'Prand', 'Pxrand', 'Pshuf', 'Pwrand', 'Pwhite', 'Pbrown',
    'Pexprand', 'Pgauss', 'Pseries', 'Pgeom', 'Pfunc', 'Pfuncn', 'Pkey',
    'Pstutter', 'Pn', 'Pif', 'Pswitch', 'Ppatlace', 'Place', 'Plazy',
    'Pclutch', 'Pconst', 'Pfin', 'Pfindur', 'Pdup', 'Pslide', 'Ppar',
    'Ptpar', 'Pfsm', 'Pindex', 'Pwalk', 'Pavaroh', 'Prout', 'Pdefn',
    'Pcollect', 'Pselect', 'Preject', 'Pfset', 'Pbindf', 'Pchain', 'Prewrite',
}


def read_text(path):
    try:
        return path.read_text(encoding='utf-8')
    except UnicodeDecodeError:
        return path.read_text(encoding='latin-1')


def leading_comment(content):
    """The p-file's own header comment, if it wrote one.

    Several of these files carry an authored block explaining the mark, its
    atlas lineage and its parameter mapping (CLAUDE.md asks for exactly that).
    Where it exists it is better than anything this script could generate, so
    the page shows it verbatim above the generated prose.
    """
    lines = []
    for line in content.splitlines():
        text = line.strip()
        if text.startswith('//'):
            body = text.lstrip('/').strip()
            if set(body) <= {'-', '=', '*'}:      # rule lines
                continue
            lines.append(body)
        elif text == '':
            if lines:
                continue
        else:
            break
    return '\n'.join(lines).strip()


def is_prose(lines):
    """True for a comment block that reads as writing rather than parked code.

    These files keep shelves of commented-out alternatives - `~plot` is
    usually five dead array literals under one live one - and those are
    indistinguishable from documentation to a `//`-counting reader. Showing
    them under "what the file says about itself" is worse than showing
    nothing.

    The separator that works is the statement terminator: commented-out
    SuperCollider lines end in `;` or `,` almost without exception, and
    authored prose almost never does. Deliberately not a keyword scan - the
    genuine mapping tables in `bongo1` and `pluck1` are dense with `\\cyc`,
    `.ar(` and `=`, and any keyword rule throws them out with the code.
    """
    body = [l for l in lines if l.strip()]
    if not body:
        return False

    def is_statement(line):
        # A trailing `//roll` after the semicolon would otherwise hide the
        # terminator and let commented-out code pass as writing, which is
        # exactly how `[(d.sensors.gyroEvent.x / pi)];//roll` got through.
        code = re.split(r'//', line, maxsplit=1)[0].rstrip()
        return code.endswith((';', ','))

    statements = sum(1 for l in body if is_statement(l))
    return statements / len(body) <= 0.5


# A comment block opening with this word is a deliberate note about the file,
# as opposed to a comment explaining the line beneath it. The page promotes
# these above the generated prose. Written in the p-file rather than in a
# sidecar so the note travels with the code it describes, survives renames and
# copies, and is visible while editing.
NOTE_MARKER = re.compile(r'^\s*note\s*[:\-—]\s*', re.IGNORECASE)


def block_comments(content):
    """Every run of comment lines in the file, with its line number.

    The authored blocks in this repo sit above `~vdef` or above an event in
    `~init`, not at the top of the file, so a leading-comment-only reader
    misses them entirely.

    Filtering happens in the caller, because the two consumers want different
    things: an explicit `// note:` counts however short it is and whatever it
    contains, while an unmarked block has to earn its place by being long
    enough and prose enough to be worth showing.
    """
    blocks = []
    current = []
    start = 0

    def flush():
        if current:
            blocks.append({'line': start, 'text': '\n'.join(current).strip()})

    for i, line in enumerate(content.splitlines(), 1):
        text = line.strip()
        if text.startswith('//'):
            body = text.lstrip('/').strip()
            if set(body) <= {'-', '=', '*'} and not current:
                continue
            if not current:
                start = i
            current.append(body)
        else:
            flush()
            current = []
    flush()
    return blocks


def split_notes(content):
    """(deliberate notes, incidental prose blocks) from the file's comments."""
    notes, incidental = [], []
    for block in block_comments(content):
        if NOTE_MARKER.match(block['text']):
            notes.append({'line': block['line'],
                          'text': NOTE_MARKER.sub('', block['text'], count=1)})
        elif len(block['text'].splitlines()) >= 3 and is_prose(block['text'].splitlines()):
            incidental.append(block)
    return notes, incidental[:8]


def paren_block(content, open_index):
    """The text inside the parenthesis opened at `open_index`."""
    depth = 0
    for i in range(open_index, len(content)):
        if content[i] == '(':
            depth += 1
        elif content[i] == ')':
            depth -= 1
            if depth == 0:
                return content[open_index + 1:i]
    return content[open_index + 1:]


def analyze_sound(content):
    """Synthesis inventory for the file as it stands now."""
    synthdefs = re.findall(r'SynthDef\(\s*\\(\w+)', content)
    ugens = Counter(re.findall(r'\b([A-Z][A-Za-z0-9]*)\.(?:ar|kr)\s*\(', content))

    sources = []
    for ugen, role in SOURCES.items():
        if ugens.get(ugen):
            sources.append({'ugen': ugen, 'role': role, 'count': ugens[ugen]})
    sources.sort(key=lambda s: -s['count'])

    processing = []
    for ugen_names, role in PROCESSORS:
        hits = sum(ugens.get(u, 0) for u in ugen_names)
        if hits:
            processing.append({'role': role, 'count': hits,
                               'ugens': [u for u in ugen_names if ugens.get(u)]})
    processing.sort(key=lambda p: -p['count'])

    envelopes = sorted(set(re.findall(r'Env\.(\w+)', content)))
    buffers = bool(re.search(r'Buffer\.(read|alloc|cueSoundFile)|PlayBuf|BufRd|~buffers', content))
    sample_files = sorted(set(re.findall(r'"([^"]+\.(?:wav|aiff|aif|mp3|flac))"', content,
                                         re.IGNORECASE)))

    # Pdef and Pbind are containers, not compositional material - counting them
    # buries the pattern objects that actually shape the music under a
    # meaningless "Pdef x13". PathName and similar non-pattern P-classes are
    # excluded by the whitelist for the same reason.
    pattern_objects = Counter(p for p in re.findall(r'\b(P[a-zA-Z]\w*)\s*\(', content)
                              if p in PATTERN_CLASSES)
    has_pbind = 'Pbind' in content
    held_synth = bool(re.search(r'\bSynth\s*\(', content))

    # Pbind keys are written one per line as `\key, value`. Matching every
    # `\symbol,` anywhere instead picks up symbol *values* too, which is how
    # `\shape, \line` used to be reported as the two keys \shape and \line.
    #
    # Every Pbind in the file is read, not just the first: several p-files
    # declare an audio Pbind and a separate visual one, and taking whichever
    # matched first described the wrong half of the personality.
    pbind_keys = []
    for match in re.finditer(r'Pbind\s*\(', content):
        body = paren_block(content, match.end() - 1)
        pbind_keys += re.findall(r'^\s*\\(\w+)\s*,', body, re.MULTILINE)
    pbind_keys = list(dict.fromkeys(pbind_keys))

    return {
        'synthdefs': list(dict.fromkeys(synthdefs)),
        'sources': sources,
        'processing': processing,
        'envelopes': envelopes,
        'uses_buffers': buffers,
        'sample_files': sample_files[:12],
        'voicing': ('pattern' if has_pbind else ('held_synth' if held_synth else 'none')),
        'pattern_objects': [{'name': n, 'count': c}
                            for n, c in pattern_objects.most_common()],
        'pbind_keys': pbind_keys,
        'uses_visuals': bool(re.search(r'customVisualEvent|~vdef', content)),
        'vdefs': sorted(set(re.findall(r'~vdef\.\(\s*\\(\w+)', content))),
        'ugen_counts': dict(ugens.most_common(30)),
    }


SET_CALL = re.compile(r'\.set\(\s*\\(\w+)\s*,\s*([^;]+?)\)\s*;')
VAR_ASSIGN = re.compile(r'\bvar\s+(\w+)\s*=\s*([^;]+);')
PLAIN_ASSIGN = re.compile(r'^\s*(\w+)\s*=\s*([^;]+);', re.MULTILINE)


def hook_body(content, name):
    """The source of a `~hook = { ... }` block, by brace matching.

    Regex cannot balance braces and these bodies routinely nest three deep,
    so this walks the string. Returns '' when the hook is absent.
    """
    match = re.search(rf'~{name}\s*=\s*(?:~{name}\s*<>\s*)?\{{', content)
    if not match:
        return ''
    start = match.end() - 1
    depth = 0
    for i in range(start, len(content)):
        if content[i] == '{':
            depth += 1
        elif content[i] == '}':
            depth -= 1
            if depth == 0:
                return content[start + 1:i]
    return content[start + 1:]


def split_args(text):
    """Split a call's argument list on top-level commas."""
    args, depth, current = [], 0, ''
    for char in text:
        if char in '([': depth += 1
        elif char in ')]': depth -= 1
        if char == ',' and depth == 0:
            args.append(current.strip())
            current = ''
        else:
            current += char
    if current.strip():
        args.append(current.strip())
    return args


def extract_curve(expr, func):
    """The numeric shape of a mapping call, so the page can plot it.

    `lincurve(0, 0.5, 0.4, 0.07, -3)` says far more about how an instrument
    feels than the word "lincurve" does - it is a steep early response that
    flattens out, and the sign of that last argument inverts the whole shape.
    Reading that off the digits is exactly what a drawing is for.

    Returns None when any argument is a variable rather than a literal
    (`notes.size` is common), because a curve cannot be plotted from a number
    that is not known until runtime. The binding still displays, just without
    the plot.
    """
    if not func:
        return None
    match = re.search(rf'\.{func}\s*\(', expr)
    if not match:
        return None
    args = split_args(paren_block(expr, match.end() - 1))

    numbers = []
    for arg in args:
        try:
            numbers.append(float(arg))
        except ValueError:
            return None

    if func in ('linlin', 'linexp', 'explin', 'expexp') and len(numbers) >= 4:
        return {'func': func, 'in': numbers[:2], 'out': numbers[2:4], 'curve': 0}
    if func == 'lincurve' and len(numbers) >= 4:
        return {'func': func, 'in': numbers[:2], 'out': numbers[2:4],
                'curve': numbers[4] if len(numbers) > 4 else -4}
    if func in ('range', 'clip', 'fold', 'wrap') and len(numbers) >= 2:
        return {'func': func, 'in': None, 'out': numbers[:2], 'curve': 0}
    return None


def describe_expression(expr):
    """(sensor, mapping function) for a mapping expression, or (None, None)."""
    sensor = None
    for name, _ in SENSORS:
        if re.search(rf'\bm\.{name}\b', expr):
            sensor = name
            break
    if sensor is None:
        for name, _ in DIRECT_SENSORS:
            if name.replace('.', r'\.') and re.search(rf'\bd\.{name}', expr):
                sensor = name
                break
    func = None
    for f in MAPPING_FUNCS:
        if re.search(rf'\.{f}\s*\(', expr):
            func = f
            break
    return sensor, func


def analyze_gesture(content):
    """How movement reaches the sound: bindings, gates and responsiveness."""
    next_body = hook_body(content, 'next')

    # Locals declared in ~next that carry a sensor expression, so a binding
    # written as `var amp = m.accelMassFiltered.lincurve(...); ....set(\amp, amp)`
    # resolves to the sensor rather than being lost as an opaque name.
    locals_map = {}
    for match in list(VAR_ASSIGN.finditer(next_body)) + list(PLAIN_ASSIGN.finditer(next_body)):
        name, expr = match.group(1), match.group(2)
        sensor, func = describe_expression(expr)
        if sensor:
            locals_map[name] = {'sensor': sensor, 'func': func, 'expr': expr.strip()[:120]}

    bindings = []
    seen = set()
    for match in SET_CALL.finditer(next_body or content):
        param, expr = match.group(1), match.group(2).strip()
        sensor, func = describe_expression(expr)
        if sensor is None and expr in locals_map:
            sensor = locals_map[expr]['sensor']
            func = locals_map[expr]['func']
            expr = locals_map[expr]['expr']
        if sensor is None or param in ('viewID',):
            continue
        key = (param, sensor)
        if key in seen:
            continue
        seen.add(key)
        bindings.append({'param': param, 'sensor': sensor, 'func': func,
                         'curve': extract_curve(expr, func),
                         'expr': expr[:120]})

    sensors_used = [{'name': n, 'gloss': g, 'count': len(re.findall(rf'\bm\.{n}\b', content))}
                    for n, g in SENSORS if re.search(rf'\bm\.{n}\b', content)]
    direct_used = [{'name': n, 'gloss': g, 'count': content.count(f'd.{n}')}
                   for n, g in DIRECT_SENSORS if f'd.{n}' in content]

    gates = []
    for match in re.finditer(r'if\s*\(\s*(m\.\w+|d\.[\w.]+)\s*([<>]=?)\s*([\d.]+)', content):
        gates.append({'signal': match.group(1), 'op': match.group(2),
                      'threshold': float(match.group(3))})
    if re.search(r'\bm\.\w*Threshold\b', content):
        gates.append({'signal': 'model threshold', 'op': '>', 'threshold': None})

    triggers = []
    if re.search(r'Pdef\([^)]*\)\.(resume|pause)', content):
        triggers.append('pattern gating (the pattern is paused and resumed by movement)')
    if '~onEvent' in content:
        triggers.append('note events (~onEvent, usually sharing key or tempo with the ensemble)')
    if '~onHit' in content:
        triggers.append('hit detection (~onHit)')
    if '~onMoving' in content:
        triggers.append('movement onset (~onMoving)')
    if 'digiInEvent' in content:
        triggers.append('the hardware buttons')
    if re.search(r'~midiControllerValue', content):
        triggers.append('MIDI controller input')

    smoothing = {}
    for match in re.finditer(r'm\.(\w*(?:Attack|Decay))\s*=\s*([\d.]+)', content):
        smoothing[match.group(1)] = float(match.group(2))

    return {
        'sensors': sensors_used,
        'direct_sensors': direct_used,
        'bindings': bindings,
        'gates': gates,
        'triggers': triggers,
        'smoothing': smoothing,
        'continuous': bool(next_body.strip()),
        'next_lines': len([l for l in next_body.splitlines() if l.strip()
                           and not l.strip().startswith('//')]),
    }


# --------------------------------------------------------------------------
# timeline : the file's state at each commit, deduplicated to the moments it
# actually changed
# --------------------------------------------------------------------------

def state_at(text):
    """Everything the timeline tracks, extracted from one revision of a file."""
    live = strip_comments(text)
    gesture = analyze_gesture(live)
    sound = analyze_sound(live)
    notes, authored = split_notes(text)
    return {
        'bindings': gesture['bindings'],
        'gates': gesture['gates'],
        'triggers': gesture['triggers'],
        'smoothing': gesture['smoothing'],
        'sensors': [s['name'] for s in gesture['sensors']],
        'synthdefs': sound['synthdefs'],
        'sources': [s['ugen'] for s in sound['sources']],
        'processing': [p['role'] for p in sound['processing']],
        'envelopes': sound['envelopes'],
        'voicing': sound['voicing'],
        'vdefs': sound['vdefs'],
        'uses_visuals': sound['uses_visuals'],
        'samples': sound['sample_files'],
        'notes': notes,
        # Full comment bodies would dominate the file; the timeline only needs
        # to show that documentation appeared or changed.
        'comment_digest': [b['text'][:140] for b in authored],
        'lines': len(text.splitlines()),
        'graphs': sg.graphs_for(text),
        'played': sg.played_instrument(text),
    }


def binding_key(binding):
    return (binding['sensor'], binding['param'])


def state_signature(state):
    """What counts as a change. Any difference here starts a new timeline stop."""
    return json.dumps({k: state[k] for k in state if k != 'lines'},
                      sort_keys=True, default=str)


def diff_states(previous, current):
    """What changed between two states, in the terms the page displays."""
    if previous is None:
        return {'first': True, 'added': [], 'removed': [], 'reshaped': [],
                'sound': [], 'notes': []}

    before = {binding_key(b): b for b in previous['bindings']}
    after = {binding_key(b): b for b in current['bindings']}

    added = [f"{s} → \\{p}" for (s, p) in sorted(after.keys() - before.keys())]
    removed = [f"{s} → \\{p}" for (s, p) in sorted(before.keys() - after.keys())]

    # A binding that survived but whose numbers moved - the case the drawn
    # curves exist to make visible.
    reshaped = []
    for key in sorted(after.keys() & before.keys()):
        old, new = before[key], after[key]
        if (old.get('curve') != new.get('curve')
                or old.get('func') != new.get('func')):
            reshaped.append({
                'label': f"{key[0]} → \\{key[1]}",
                'param': key[1], 'sensor': key[0],
                'from': old.get('curve'), 'to': new.get('curve'),
                'from_func': old.get('func'), 'to_func': new.get('func'),
            })

    sound_changes = []
    for field, noun in (('synthdefs', 'SynthDef'), ('sources', 'sound source'),
                        ('processing', 'processing'), ('envelopes', 'envelope'),
                        ('vdefs', 'visual'), ('samples', 'sample')):
        gained = [x for x in current[field] if x not in previous[field]]
        lost = [x for x in previous[field] if x not in current[field]]
        for item in gained:
            sound_changes.append(f"+{noun} {item}")
        for item in lost:
            sound_changes.append(f"−{noun} {item}")
    if previous['voicing'] != current['voicing']:
        sound_changes.append(f"voicing {previous['voicing']} → {current['voicing']}")
    if previous['triggers'] != current['triggers']:
        gained = [t for t in current['triggers'] if t not in previous['triggers']]
        for item in gained:
            sound_changes.append(f"+trigger: {item.split(' (')[0]}")
    if previous['smoothing'] != current['smoothing']:
        sound_changes.append('filter smoothing retuned')
    if previous.get('graph') != current.get('graph'):
        sound_changes.append('signal flow changed')

    note_changes = []
    if previous['notes'] != current['notes']:
        note_changes.append('note added or edited' if current['notes']
                            else 'note removed')
    if previous['comment_digest'] != current['comment_digest']:
        note_changes.append('comments changed')

    return {'first': False, 'added': added, 'removed': removed,
            'reshaped': reshaped, 'sound': sound_changes, 'notes': note_changes}


def build_timelines(personalities, cache):
    """A deduplicated per-file timeline of every state the file passed through.

    Storing one snapshot per commit would be mostly repetition - 98 of the 183
    files never change their mappings at all - so a stop is only recorded when
    something the timeline tracks actually differs. That turns ~1400 file
    revisions into a few hundred meaningful stops, and gives the scrubber
    positions worth stopping on rather than dozens of identical frames.
    """
    needed = set()
    trees = {}
    for entry in personalities.values():
        for commit in entry['commits']:
            if commit['hash'] not in trees:
                trees[commit['hash']] = tree_blobs(commit['hash'])
            sha = trees[commit['hash']].get(entry['path'])
            if sha:
                needed.add(sha)

    contents = fetch_blobs(needed)

    for entry in personalities.values():
        timeline = []
        previous = None
        # Signal-flow graphs are far bulkier than the rest of a state and
        # change far less often, so each distinct one is stored once per file
        # and referenced by index. A file with 37 commits and one topology
        # stores that topology once.
        pool = []
        pool_index = {}

        def intern(graphs):
            key = json.dumps(
                [[g['name'], [n['label'] for n in g['nodes']],
                  [(e['from'], e['to']) for e in g['edges']]] for g in graphs],
                sort_keys=True)
            if key not in pool_index:
                pool_index[key] = len(pool)
                pool.append(graphs)
            return pool_index[key]

        for commit in sorted(entry['commits'], key=lambda c: c['datetime']):
            sha = trees.get(commit['hash'], {}).get(entry['path'])
            if not sha or sha not in contents:
                continue
            state = state_at(contents[sha])
            # Intern before comparing, so both states carry the same keys and
            # a topology change shows up as a changed index rather than as a
            # difference in shape between the two dicts.
            state['graph'] = intern(state.pop('graphs'))
            if previous is not None and \
                    state_signature(state) == state_signature(previous):
                continue
            timeline.append({
                'date': commit['date'],
                'short': commit['short'],
                'subject': commit['subject'],
                'state': state,
                'changes': diff_states(previous, state),
            })
            previous = state
        entry['timeline'] = timeline
        entry['graph_pool'] = pool


def fetch_blobs(shas):
    """{sha: text} for a set of blobs, in one `git cat-file --batch`."""
    shas = list(shas)
    if not shas:
        return {}
    process = subprocess.run(
        ['git', 'cat-file', '--batch'], cwd=REPO,
        input='\n'.join(shas).encode(), capture_output=True,
    )
    out = process.stdout
    contents = {}
    position = 0
    for sha in shas:
        newline = out.find(b'\n', position)
        if newline < 0:
            break
        header = out[position:newline].split()
        if len(header) < 3:
            position = newline + 1
            continue
        size = int(header[2])
        contents[header[0].decode()] = out[newline + 1:newline + 1 + size].decode(errors='replace')
        position = newline + 1 + size + 1
    return contents


# --------------------------------------------------------------------------
# narrative
#
# All prose below is composed from the structures above. Nothing is authored
# per-personality, so it regenerates with the data - which is the point, but
# it also means every sentence has to be defensible from evidence. Where the
# evidence is thin the narrative says so rather than padding.
# --------------------------------------------------------------------------

def plural(n, one, many=None):
    return one if n == 1 else (many or one + 's')


def join_list(items, conjunction='and'):
    items = [str(i) for i in items]
    if not items:
        return ''
    if len(items) == 1:
        return items[0]
    return f"{', '.join(items[:-1])} {conjunction} {items[-1]}"


def month_name(date_str):
    return datetime.strptime(date_str[:10], '%Y-%m-%d').strftime('%B %Y')


def long_date(date_str):
    parsed = datetime.strptime(date_str[:10], '%Y-%m-%d')
    return f"{parsed.strftime('%d').lstrip('0')} {parsed.strftime('%B %Y')}"


def span_phrase(burst):
    """"October 2025" or "October–December 2025" for a run of commits."""
    start, end = burst[0]['date'], burst[-1]['date']
    if start[:7] == end[:7]:
        return month_name(start)
    if start[:4] == end[:4]:
        early = datetime.strptime(start[:10], '%Y-%m-%d').strftime('%B')
        return f"{early}–{month_name(end)}"
    return f"{month_name(start)}–{month_name(end)}"


def gap_months(earlier, later):
    a = datetime.strptime(earlier[:10], '%Y-%m-%d')
    b = datetime.strptime(later[:10], '%Y-%m-%d')
    return (b - a).days / 30.44


def quantity(n):
    """Small numbers as words, which is what prose wants."""
    words = {1: 'one', 2: 'two', 3: 'three', 4: 'four', 5: 'five', 6: 'six',
             7: 'seven', 8: 'eight', 9: 'nine', 10: 'ten', 11: 'eleven',
             12: 'twelve'}
    return words.get(n, str(n))


def months_between(first, last):
    a = datetime.strptime(first[:10], '%Y-%m-%d')
    b = datetime.strptime(last[:10], '%Y-%m-%d')
    return max(0, round((b - a).days / 30.44))


def find_bursts(commits):
    """Commits grouped into episodes of work separated by BURST_GAP_DAYS."""
    if not commits:
        return []
    ordered = sorted(commits, key=lambda c: c['date'])
    bursts = [[ordered[0]]]
    for commit in ordered[1:]:
        previous = datetime.strptime(bursts[-1][-1]['date'], '%Y-%m-%d')
        current = datetime.strptime(commit['date'], '%Y-%m-%d')
        if (current - previous).days > BURST_GAP_DAYS:
            bursts.append([commit])
        else:
            bursts[-1].append(commit)
    return bursts


def dominant_kind(commits):
    """Which of synthdef/mapping/visual dominates a set of commits."""
    totals = Counter()
    for commit in commits:
        for kind, count in commit['added'].items():
            totals[kind] += count
    if not totals:
        return None, totals
    return totals.most_common(1)[0][0], totals


KIND_PHRASE = {
    'synthdef': 'synthesis work',
    'mapping': 'mapping work',
    'visual': 'visual work',
}


def narrate_origin(entry):
    """Where the file came from. One paragraph."""
    origin = entry['origin']
    name = entry['name']

    if origin['predates_window']:
        return (f"{name} is older than the analysis window. It already existed "
                f"on {long_date(origin['first_seen'])}, the earliest commit "
                f"inside the {SINCE} scope that touches it, so its birth is "
                f"not visible here.")
    if not origin.get('born'):
        return f"No commit inside the analysis window creates {name}."

    born = origin['born']
    ancestor = origin.get('ancestor')
    if ancestor:
        verb = 'copied from' if ancestor['kind'] == 'copy' else 'renamed from'
        sentence = (f"{name} begins on {long_date(born['date'])}, {verb} "
                    f"{ancestor['name']} \u2014 {ancestor['similarity']}% of it was "
                    f"still {ancestor['name']} the day it was made.")
    else:
        sentence = (f"{name} begins on {long_date(born['date'])}, written "
                    f"fresh rather than duplicated from an existing p-file.")

    if origin.get('ancestor_contested'):
        rivals = [r for r in origin.get('rivals', [])][:2]
        named = join_list([r['name'] for r in rivals], 'and')
        sentence += (f" That parent is not certain: {named} were near-identical "
                     f"to each other at the time, and git names only the single "
                     f"closest. The family is the same either way.")

    siblings = born.get('sibling_personalities', [])
    sentence += f" It arrived in a commit called \u201c{born['subject']}\u201d"
    if siblings:
        listed = siblings[:5]
        extra = len(siblings) - 5
        named = (join_list(listed) if extra <= 0
                 else ', '.join(listed) +
                 f" and {quantity(extra)} {plural(extra, 'other')}")
        sentence += f", which also brought in {named}"
    else:
        sentence += ", the only p-file in it"
    return sentence + '.'


def narrate_shape(entry):
    """How much work, of what kind. One paragraph."""
    commits = entry['commits']
    bursts = find_bursts(commits)
    span = months_between(commits[-1]['date'], commits[0]['date'])

    if span:
        scale = (f"{len(commits)} {plural(len(commits), 'commit')} over "
                 f"{quantity(span)} {plural(span, 'month')}, in "
                 f"{quantity(len(bursts))} {plural(len(bursts), 'burst')} of work")
    elif len(commits) == 1:
        scale = "a single commit, never revisited"
    else:
        scale = (f"{quantity(len(commits))} {plural(len(commits), 'commit')}, "
                 f"all on the one day")

    _, totals = dominant_kind(commits)
    if not totals:
        return (scale.capitalize() + ", none of which changed code this script "
                "can classify \u2014 renames, deletions and whitespace.")

    ordered = totals.most_common()
    if len(ordered) == 1:
        kind, count = ordered[0]
        body = (f"{scale.capitalize()}. Every one of the {count} added "
                f"{plural(count, 'line')} it can classify is "
                f"{KIND_PHRASE[kind]}")
    else:
        counted = join_list([f"{count} of {KIND_PHRASE[kind].split()[0]}"
                             for kind, count in ordered])
        leads = ordered[0][1] > sum(c for _, c in ordered[1:])
        body = (f"{scale.capitalize()}. The added lines run {counted}"
                + (f", so this is mostly {KIND_PHRASE[ordered[0][0]]}" if leads
                   else ", with no single kind dominating"))

    # Sound settling before the feel does is the most legible pattern in this
    # corpus, and it is worth saying in words rather than leaving in the chart.
    synth_commits = [c for c in commits if c['added'].get('synthdef')]
    if len(commits) > 3 and synth_commits:
        by_date = sorted(commits, key=lambda c: c['date'])
        last_synth = max(synth_commits, key=lambda c: c['date'])
        position = [c['hash'] for c in by_date].index(last_synth['hash']) + 1
        if position <= len(by_date) / 2:
            body += (f". The synthesis settles early \u2014 nothing after "
                     f"{month_name(last_synth['date'])} touches the SynthDef, "
                     f"and that is only commit {position} of {len(by_date)}. "
                     f"Everything later is adjusting how it is played")
    return body + '.'


def narrate_chronology(entry):
    """The burst-by-burst story, with the gaps between them. One paragraph."""
    commits = entry['commits']
    bursts = find_bursts(commits)
    if len(bursts) == 1 and len(commits) <= 2:
        return None

    def burst_clause(burst, lead):
        kind, totals = dominant_kind(burst)
        count = len(burst)
        subject = max(burst, key=lambda c: sum(c['added'].values()))['subject']
        work = (f"{KIND_PHRASE[kind]} (+{totals[kind]} "
                f"{plural(totals[kind], 'line')})" if kind
                else "tidying that changed no classifiable code")
        # "the largest being X" only makes sense when there is more than one
        # to be largest of.
        named = (f", \u201c{subject}\u201d" if count == 1
                 else f", the largest being \u201c{subject}\u201d")
        return (f"{lead} {quantity(count)} {plural(count, 'commit')} of "
                f"{work}{named}")

    sentences = []
    biggest = max(bursts, key=len)
    opening = bursts[0]

    # Where the mass of the work sits is the shape of the story, so it leads.
    front_loaded = (biggest is opening
                    and len(opening) >= max(2, 0.4 * len(commits))
                    and len(bursts) > 1)
    if front_loaded:
        sentences.append(burst_clause(
            opening, f"Most of the work happens straight away, "
                     f"{span_phrase(opening)} carrying"))
    else:
        sentences.append(burst_clause(
            opening, f"It opens in {span_phrase(opening)} with"))

    for index in range(1, len(bursts)):
        previous, burst = bursts[index - 1], bursts[index]
        gap = gap_months(previous[-1]['date'], burst[0]['date'])
        last = index == len(bursts) - 1

        # Connectives are varied by position so a file with six consecutive
        # bursts does not read "X follows with... Y follows with... Z follows
        # with". Chosen by index rather than at random, so the narrative is
        # identical on every regeneration.
        CONTINUATIONS = ['{} follows with', 'then comes {}, with',
                         '{} adds', 'work resumes in {} with']
        if gap >= 6:
            lead = (f"Then nothing for {quantity(round(gap))} months, until "
                    f"{span_phrase(burst)} brings")
        elif gap >= 3:
            lead = (f"After a quiet few months it returns in "
                    f"{span_phrase(burst)} with")
        else:
            lead = CONTINUATIONS[index % len(CONTINUATIONS)].format(
                span_phrase(burst))
        if last and burst is biggest and len(bursts) > 2:
            lead = (f"The heaviest stretch comes last: {span_phrase(burst)} "
                    f"accounts for")
        sentences.append(burst_clause(burst, lead))

    # The connectives are written to read mid-sentence, so each has to be
    # capitalised as it becomes a sentence of its own. Month names and the
    # pronoun forms are already capital; only the "then comes" style needs it.
    story = '. '.join(s[0].upper() + s[1:] if s else s for s in sentences) + '.'

    # First visual work is a genuine milestone in this repo's timeline.
    visual = [c for c in sorted(commits, key=lambda c: c['date'])
              if c['added'].get('visual')]
    if visual and len(commits) > 2:
        first_visual = visual[0]
        story += (f" Its visual arrives on {long_date(first_visual['date'])} in "
                  f"\u201c{first_visual['subject']}\u201d"
                  + (f", and {quantity(len(visual) - 1)} later "
                     f"{plural(len(visual) - 1, 'commit')} "
                     f"{plural(len(visual) - 1, 'develops', 'develop')} it."
                     if len(visual) > 1
                     else ", and no later commit adds to it."))

    last_commit = commits[0]
    story += (f" It was last touched on {long_date(last_commit['date'])}, in "
              f"\u201c{last_commit['subject']}\u201d.")
    return story


def narrate_chain(entry):
    """The line the file descends from, beyond its immediate parent.

    Only worth a paragraph when there is more than one hop - a single-parent
    file has already had that said in the origin sentence.
    """
    chain = entry['lineage']['chain']
    if len(chain) < 2:
        return None

    hops = []
    for index, node in enumerate(chain):
        target = chain[index + 1]['name'] if index + 1 < len(chain) else entry['name']
        verb = 'renamed to' if node['kind'] == 'rename' else 'copied to'
        pct = f" at {node['similarity']}%" if node['similarity'] else ""
        hops.append(f"{node['name']} was {verb} {target}{pct}"
                    f" ({month_name(node['hop_date'])})")

    oldest = chain[0]
    first_seen = oldest.get('born') or oldest['hop_date']
    span = (months_between(first_seen, entry['origin']['born']['date'])
            if entry['origin'].get('born') else 0)
    lead = (f"Its line runs back {quantity(len(chain))} "
            f"{plural(len(chain), 'generation')} to {oldest['name']}, first seen "
            f"{month_name(first_seen)}")
    if span:
        lead += f", {quantity(span)} {plural(span, 'month')} before this file existed"

    dead = [n['name'] for n in chain if not n['in_tree']]
    tail = ''
    if dead:
        tail = (f" The chain passes through {join_list(dead)}, "
                f"{plural(len(dead), 'which is', 'which are')} no longer in the "
                f"working tree and cannot be opened here.")

    return lead + ': ' + '; '.join(hops) + '.' + tail


def narrate_kin(entry):
    """Renames, descendants and the files it travels with. One paragraph."""
    sentences = []
    for rename in entry['lineage']['renames']:
        sentences.append(f"It was renamed from {rename['from']} to "
                         f"{rename['to']} on {long_date(rename['date'])}")

    descendants = entry['lineage']['descendants']
    if descendants:
        listed = ['{} ({}%)'.format(d['name'], d['similarity'])
                  for d in descendants[:8]]
        named = (join_list(listed) if len(descendants) <= 8
                 else ', '.join(listed) + f" and {quantity(len(descendants) - 8)} others")
        sentences.append(
            f"{quantity(len(descendants)).capitalize()} later "
            f"{plural(len(descendants), 'p-file')} "
            f"{plural(len(descendants), 'was', 'were')} built from it: {named}")

    siblings = entry['siblings'][:5]
    if siblings:
        named = ['{} ({}\u00d7)'.format(s['name'], s['count']) for s in siblings]
        sentences.append(f"It travels with {join_list(named)}, which is the "
                         f"company it was written to play in")
    return '. '.join(sentences) + '.' if sentences else None


def narrate_evolution(entry):
    """The evolution account, as paragraphs.

    Deliberately prose rather than a per-period table: the periods, counts and
    line totals are all in the chart and the commit list directly below it, and
    repeating them as sentences reads like a report being dictated. What the
    prose is for is the shape - where the mass of the work sits, how long the
    silences are, when the sound stopped changing and the playing started.
    """
    if not entry['commits']:
        return [narrate_origin(entry),
                'No commits inside the analysis window touch this file.']
    paragraphs = [narrate_origin(entry), narrate_chain(entry),
                  narrate_shape(entry), narrate_chronology(entry),
                  narrate_kin(entry)]
    return [p for p in paragraphs if p]


def narrate_sound(entry):
    sound = entry['current']['sound']
    parts = []

    if sound['synthdefs']:
        parts.append(f"Defines {len(sound['synthdefs'])} "
                     f"{plural(len(sound['synthdefs']), 'SynthDef')}: "
                     f"{join_list(['\\' + s for s in sound['synthdefs']])}.")
    else:
        parts.append("Defines no SynthDef of its own \u2014 it plays instruments "
                     "declared elsewhere.")

    if sound['sources']:
        top = sound['sources'][:4]
        parts.append("Sound comes from " + join_list([s['role'] for s in top]) +
                     f" ({join_list([s['ugen'] for s in top])}).")
    if sound['uses_buffers']:
        if sound['sample_files']:
            parts.append(f"Sample-based: {len(sound['sample_files'])} audio "
                         f"{plural(len(sound['sample_files']), 'file')} referenced, "
                         f"including {join_list(sound['sample_files'][:3])}.")
        else:
            parts.append("Sample-based \u2014 it reads buffers, though the file "
                         "paths come from elsewhere.")

    if sound['processing']:
        parts.append("Processed with " +
                     join_list([p['role'] for p in sound['processing'][:5]]) + ".")
    if sound['envelopes']:
        parts.append(f"Amplitude shaped by "
                     f"{join_list(['Env.' + e for e in sound['envelopes']])}.")

    if sound['voicing'] == 'pattern':
        objects = sound['pattern_objects'][:5]
        sentence = "Voiced by a pattern"
        if objects:
            sentence += (" built from " +
                         join_list([f"{o['name']}\u00d7{o['count']}" for o in objects]))
        keys = sound['pbind_keys']
        if keys:
            # Several p-files run audio and visual keys through one Pbind, and
            # taking the first eight in file order described the picture rather
            # than the music. Musical keys lead; the rest are counted, not listed.
            musical = [k for k in keys if k in MUSICAL_KEYS]
            other = [k for k in keys if k not in MUSICAL_KEYS]
            shown = (musical + other)[:8]
            sentence += f", keyed on {join_list(['\\' + k for k in shown])}"
            if len(keys) > len(shown):
                extra = len(keys) - len(shown)
                sentence += f" and {extra} further {plural(extra, 'key')}"
        parts.append(sentence + ".")
    elif sound['voicing'] == 'held_synth':
        parts.append("Voiced by a single held Synth driven continuously by "
                     "`.set`, rather than by a pattern of discrete notes.")

    if sound['uses_visuals']:
        if sound['vdefs']:
            parts.append(f"Carries its own visual: "
                         f"{join_list(['\\' + v for v in sound['vdefs']])}.")
        else:
            parts.append("Emits visual events using library shapes.")

    return ' '.join(parts)


def narrate_gesture(entry):
    gesture = entry['current']['gesture']
    parts = []

    if gesture['sensors'] or gesture['direct_sensors']:
        reads = [s['gloss'] for s in gesture['sensors'][:4]]
        reads += [s['gloss'] for s in gesture['direct_sensors'][:2]]
        parts.append("Reads " + join_list(reads) + ".")
    else:
        parts.append("Reads no sensor signal \u2014 it runs open-loop.")

    if gesture['bindings']:
        described = []
        for binding in gesture['bindings'][:6]:
            func = f" via {binding['func']}" if binding['func'] else ""
            described.append(f"{binding['sensor']} \u2192 \\{binding['param']}{func}")
        parts.append("Movement is bound to sound as " + join_list(described) + ".")
    elif gesture['continuous']:
        parts.append("~next runs every tick but sets no parameter directly "
                     "from a sensor \u2014 the coupling is indirect.")

    if gesture['gates']:
        numeric = [g for g in gesture['gates'] if g['threshold'] is not None]
        if numeric:
            first = numeric[0]
            parts.append(f"Gated: sound is held off until {first['signal']} "
                         f"{first['op']} {first['threshold']}"
                         + (f", plus {len(numeric) - 1} further "
                            f"{plural(len(numeric) - 1, 'threshold')}"
                            if len(numeric) > 1 else "") + ".")
        else:
            parts.append("Gated on a model threshold.")

    if gesture['triggers']:
        parts.append("Triggered by " + join_list(gesture['triggers']) + ".")

    if gesture['smoothing']:
        values = list(gesture['smoothing'].values())
        average = sum(values) / len(values)
        feel = ("heavily smoothed, so it responds to sustained motion rather "
                "than to individual strikes" if average >= 0.95 else
                "lightly smoothed \u2014 fast, strike-responsive"
                if average <= 0.6 else "moderately smoothed")
        parts.append(f"Filter tuning is {feel} ("
                     + join_list([f"{k} {v}" for k, v in gesture['smoothing'].items()])
                     + ").")

    style = []
    if gesture['bindings'] and gesture['gates']:
        style.append("continuous control inside a movement gate")
    elif gesture['bindings']:
        style.append("continuous control")
    elif gesture['gates']:
        style.append("gate-only control")
    if 'note events (~onEvent, usually sharing key or tempo with the ensemble)' in gesture['triggers']:
        style.append("ensemble-synchronised")
    if style:
        parts.append("In short: " + join_list(style) + ".")

    return ' '.join(parts)


# --------------------------------------------------------------------------
# assembly
# --------------------------------------------------------------------------

def build():
    tracked = sorted(PERSONALITIES.glob('*.sc'))
    if not tracked:
        raise SystemExit(f"no personality files found in {PERSONALITIES}")
    wanted = {f'personalities/{p.name}' for p in tracked}

    print(f"Reading git history ({' '.join(GIT_SCOPE)}) with rename and copy detection...")
    commits = parse_stream(diff_stream())
    print(f"  {len(commits)} commits touch personality files in scope")
    all_files = commit_file_lists()

    # Per-file commit records, newest first. A file is followed through its
    # renames: a commit that renamed old -> new is recorded against BOTH, so
    # looking up either name finds the moment.
    history = defaultdict(list)
    descendants = defaultdict(list)
    renames = defaultdict(list)

    for commit in commits:
        touched = [r['path'] for r in commit['files']]
        for record in commit['files']:
            paths = {record['path']}
            if record['old_path'] and record['status'] in ('R',):
                paths.add(record['old_path'])
            others = sorted({Path(p).stem for p in touched if p != record['path']})
            all_touched = all_files.get(commit['hash'], [])
            non_personality = sorted({p for p in all_touched
                                      if not p.startswith('personalities/')})

            entry = {
                'hash': commit['hash'],
                'short': commit['short'],
                'date': commit['date'],
                'datetime': commit['datetime'],
                'author': commit['author'],
                'subject': commit['subject'],
                'status': record['status'],
                'old_path': record['old_path'],
                'similarity': record['similarity'],
                'added': dict(record['added']),
                'removed': dict(record['removed']),
                'added_total': sum(record['added'].values()),
                'removed_total': sum(record['removed'].values()),
                'siblings': others,
                'other_files': non_personality[:20],
                'other_file_count': len(non_personality),
            }
            for path in paths:
                history[path].append(entry)

            if record['status'] in ('C', 'R') and record['old_path']:
                descendants[record['old_path']].append({
                    'name': Path(record['path']).stem,
                    'path': record['path'],
                    'similarity': record['similarity'],
                    'kind': 'copy' if record['status'] == 'C' else 'rename',
                    'date': commit['date'],
                    'short': commit['short'],
                })
            if record['status'] == 'R' and record['old_path']:
                renames[record['path']].append({
                    'from': Path(record['old_path']).stem,
                    'to': Path(record['path']).stem,
                    'date': commit['date'],
                    'short': commit['short'],
                    'similarity': record['similarity'],
                })

    ancestry = build_ancestry(commits)
    cache = BlobCache()
    print("Tracing ancestor chains and checking for near-tie parents...")

    personalities = {}
    for path in tracked:
        key = f'personalities/{path.name}'
        name = path.stem
        records = sorted(history.get(key, []), key=lambda r: r['datetime'], reverse=True)

        birth = next((r for r in reversed(records) if r['status'] in ('A', 'C', 'R')), None)
        predates = birth is None
        origin = {'predates_window': predates,
                  'first_seen': records[-1]['date'] if records else None,
                  'born': None, 'ancestor': None}
        if birth:
            origin['born'] = {
                'date': birth['date'], 'short': birth['short'],
                'hash': birth['hash'], 'subject': birth['subject'],
                'author': birth['author'],
                'sibling_personalities': birth['siblings'],
                'other_files': birth['other_files'],
            }
            if birth['old_path']:
                origin['ancestor'] = {
                    'name': Path(birth['old_path']).stem,
                    'path': birth['old_path'],
                    'similarity': birth['similarity'],
                    'kind': 'copy' if birth['status'] == 'C' else 'rename',
                }
                rivals = find_rivals({'hash': birth['hash'], 'path': key}, cache)
                origin['rivals'] = rivals
                # A tie only counts if the runner-up is a *different* file from
                # the one git named.
                chosen = origin['ancestor']['name']
                contenders = [r for r in rivals if r['name'] != chosen]
                top = next((r['score'] for r in rivals if r['name'] == chosen), None)
                origin['ancestor_contested'] = bool(
                    contenders and top is not None
                    and contenders[0]['score'] >= top - RIVAL_MARGIN)

        sibling_counts = Counter()
        for record in records:
            sibling_counts.update(record['siblings'])
        sibling_counts.pop(name, None)

        content = read_text(path)
        # Comments are stripped for the code analyses but kept for the authored
        # notes, which are the one place the file's own words are wanted.
        live = strip_comments(content)
        notes, authored = split_notes(content)
        entry = {
            'name': name,
            'path': key,
            'lines': len(content.splitlines()),
            'origin': origin,
            'lineage': {
                'ancestor': origin['ancestor'],
                'chain': ancestor_chain(key, ancestry, wanted),
                'descendants': descendants.get(key, []),
                'renames': renames.get(key, []),
            },
            'commits': records,
            'siblings': [{'name': n, 'count': c} for n, c in sibling_counts.most_common(20)],
            'stats': {
                'commit_count': len(records),
                'first': records[-1]['date'] if records else None,
                'last': records[0]['date'] if records else None,
                'added': dict(sum((Counter(r['added']) for r in records), Counter())),
                'removed': dict(sum((Counter(r['removed']) for r in records), Counter())),
            },
            'current': {
                'sound': analyze_sound(live),
                'gesture': analyze_gesture(live),
                'authored_header': leading_comment(content),
                'authored_blocks': authored,
                'notes': notes,
                'graphs': sg.graphs_for(content),
                'played': sg.played_instrument(content),
            },
        }
        entry['narrative'] = {
            'evolution': narrate_evolution(entry),
            'sound': narrate_sound(entry),
            'gesture': narrate_gesture(entry),
        }
        personalities[name] = entry

    print("Building per-file timelines...")
    build_timelines(personalities, cache)
    stops = sum(len(e['timeline']) for e in personalities.values())
    moving = sum(1 for e in personalities.values() if len(e['timeline']) > 1)
    print(f"  {stops} timeline stops across {moving} files that change")

    return {
        'metadata': {
            'analysis_date': datetime.now().isoformat(),
            'git_scope': ' '.join(GIT_SCOPE),
            'since': SINCE,
            'rename_copy_detection': '-M -C --find-copies-harder',
            'personality_count': len(personalities),
            'commit_count': len(commits),
            'burst_gap_days': BURST_GAP_DAYS,
            'note': ('Working-tree p-files only. Ancestors and siblings may name '
                     'files that no longer exist; those have no entry of their own.'),
        },
        'personalities': personalities,
    }


def main():
    data = build()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output = OUTPUT_DIR / 'personality_history.json'
    output.write_text(json.dumps(data, indent=2, default=str))

    entries = data['personalities']
    with_ancestor = [e for e in entries.values() if e['lineage']['ancestor']]
    predating = [e for e in entries.values() if e['origin']['predates_window']]
    print(f"\nWrote {output}")
    print(f"  {len(entries)} personalities")
    print(f"  {len(with_ancestor)} have a detected ancestor (copy or rename)")
    print(f"  {len(predating)} predate the {SINCE} window")
    busiest = sorted(entries.values(), key=lambda e: -e['stats']['commit_count'])[:5]
    print("  busiest: " + ', '.join(f"{e['name']} ({e['stats']['commit_count']})"
                                    for e in busiest))
    return 0


if __name__ == '__main__':
    sys.exit(main())
