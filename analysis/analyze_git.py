#!/usr/bin/env python3
import subprocess
import re
import json
from collections import defaultdict, Counter
from pathlib import Path

# Generated data lands here and is gitignored - the scripts are the source of
# truth, not their output. Anchored to this file rather than the working
# directory so it does not matter where the script is invoked from.
OUTPUT_DIR = Path(__file__).resolve().parent / 'pages' / 'output'
REPO = Path(__file__).resolve().parent.parent

# Commit scope. Must stay identical across every analyzer in this directory,
# or the timelines are drawn from different populations and cannot be read
# against each other.
GIT_SCOPE = ['--all', '--since=2024-01-01', '--no-merges']

# Field and record separators for `git log --pretty`. A commit body spans
# multiple lines and may itself contain '|', so neither a newline nor a pipe
# can delimit anything. Splitting on them truncated every multi-line body to
# its first line, and turned one body line that happened to contain pipes
# ("/airkit/state now lists idle|tuning|piece|curtain") into a phantom commit
# whose "hash" was then handed to `git show`.
#
# The format string uses git's %x00/%x01 placeholders rather than the bytes
# themselves: a literal NUL cannot be passed through argv (subprocess raises
# "embedded null byte"), so git emits the separators and only the parsing side
# below splits on the real characters.
FIELD_FMT, FIELD_SEP = '%x00', '\x00'
RECORD_FMT, RECORD_SEP = '%x01', '\x01'

TYPES = ['implementation', 'personality', 'visual', 'mixed', 'other']

# Matched against the start of the path, so a vendored copy such as
# sc_osx_standalone-3.7.0-template/personalities/ is not mistaken for ours.
VISUAL = re.compile(r'^(visuals/|code[\d.]+/.*(visual|vdef|shapeLib|render))', re.IGNORECASE)
PERSONALITY = re.compile(r'^(personalities/|lists/|synths/)')
IMPLEMENTATION = re.compile(r'^(code[\d.]+/|machines/)')

TRAILER = re.compile(r'^[A-Za-z][\w-]*: .+$')
GENERATED = re.compile(r'generated with \[?claude code', re.IGNORECASE)

STOPWORDS = {
    'the', 'a', 'an', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for',
    'of', 'with', 'by', 'from', 'up', 'about', 'into', 'through', 'during',
    'i', 'me', 'my', 'we', 'our', 'you', 'your', 'it', 'its', 'they', 'them',
    'this', 'that', 'these', 'those', 'is', 'are', 'was', 'were', 'be', 'been',
    'have', 'has', 'had', 'do', 'does', 'did', 'will', 'would', 'should',
    'can', 'could', 'may', 'might', 'must', 'shall',
    'github', 'sohla', 'com', 'https', 'http', 'www', 'scbounce', 'oscmusicmachine',
    'airkit', 'airsticks', 'cotf',
}


def file_category(path):
    if VISUAL.match(path):
        return 'visual'
    if PERSONALITY.match(path):
        return 'personality'
    if IMPLEMENTATION.match(path):
        return 'implementation'
    return None


def categorize_commit(files):
    """The dominant category if it holds two thirds of the categorised files."""
    counts = Counter(c for c in map(file_category, files) if c)
    if not counts:
        return 'other'
    category, n = counts.most_common(1)[0]
    return category if n * 3 >= sum(counts.values()) * 2 else 'mixed'


def message_text(subject, body):
    """Subject and body with trailers and the generated-by line removed."""
    lines = body.rstrip().splitlines()
    while lines and (TRAILER.match(lines[-1].strip()) or not lines[-1].strip()):
        lines.pop()
    lines = [l for l in lines if not GENERATED.search(l)]
    return subject + '\n' + '\n'.join(lines)


def get_all_commits():
    """Every commit in scope with its files, from a single git log."""
    fmt = RECORD_FMT + FIELD_FMT.join(['%H', '%ai', '%an', '%s', '%b']) + FIELD_FMT
    result = subprocess.run(
        ['git', 'log', *GIT_SCOPE, '--name-only', '--pretty=format:' + fmt],
        cwd=REPO, capture_output=True, text=True, errors='replace',
    )

    commits = []
    for record in result.stdout.split(RECORD_SEP):
        parts = record.split(FIELD_SEP)
        if len(parts) < 6:
            continue
        files = [f.strip() for f in parts[5].splitlines() if f.strip()]
        commits.append({
            'hash': parts[0][:8],
            'datetime': parts[1],
            'date': parts[1][:10],
            'author': parts[2],
            'subject': parts[3],
            'files': files,
            'type': categorize_commit(files),
            'text': message_text(parts[3], parts[4]),
        })
    return commits


def analyze_word_frequency(commits):
    """{word: [hash, ...]} - each commit counted once per word."""
    word_to_commits = defaultdict(list)
    for commit in commits:
        words = set(re.findall(r'\b[a-z]{3,}\b', commit['text'].lower())) - STOPWORDS
        for word in words:
            word_to_commits[word].append(commit['hash'])
    return word_to_commits


def main():
    print("Analyzing git repository...")
    commits = get_all_commits()
    print(f"Found {len(commits)} commits")

    word_to_commits = analyze_word_frequency(commits)
    top = sorted(word_to_commits.items(), key=lambda kv: (-len(kv[1]), kv[0]))[:200]
    print(f"Found {len(word_to_commits)} unique words")

    dates = sorted(c['datetime'] for c in commits)
    data = {
        'types': TYPES,
        'commits_list': [{k: v for k, v in c.items() if k != 'text'} for c in commits],
        'words': [{'text': w, 'size': len(h), 'commits': h} for w, h in top],
        'timeline': {
            'min_date': dates[0] if dates else None,
            'max_date': dates[-1] if dates else None,
        },
    }

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output_file = OUTPUT_DIR / 'commits_data.json'
    with open(output_file, 'w') as f:
        json.dump(data, f, indent=1)

    counts = Counter(c['type'] for c in commits)
    print('  ' + '  '.join(f'{t}: {counts[t]}' for t in TYPES))
    print('  top words: ' + ', '.join(f'{w} {len(h)}' for w, h in top[:20]))
    print(f"Wrote {output_file}")


if __name__ == '__main__':
    main()
