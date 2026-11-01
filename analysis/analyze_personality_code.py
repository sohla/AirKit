#!/usr/bin/env python3
import subprocess
import re
import json
from collections import defaultdict
from datetime import datetime
from pathlib import Path

# Generated data lands here and is gitignored - the scripts are the source of
# truth, not their output. Anchored to this file rather than the working
# directory so it does not matter where the script is invoked from.
OUTPUT_DIR = Path(__file__).resolve().parent / 'pages' / 'output'

# Regex patterns for classification
SYNTHDEF_PATTERNS = [
    r'SynthDef\(',
    r'\.ar\(',
    r'\.kr\(',
    r'Out\.ar\(',
    r'Out\.kr\(',
    r'EnvGen\.(ar|kr)\(',
    r'(SinOsc|LFSaw|LFTri|LFNoise[012]|WhiteNoise|Dust|Klank|Pulse|Saw)\.(ar|kr)\(',
    r'(RLPF|HPF|LPF|BPF|FreeVerb|Balance2|Pan2|Mix)\.(ar|kr)\(',
    r'\.add;',
    r'Env\.(perc|adsr|asr|new)',
    r'doneAction:',
    r'Done\.',
]

MAPPING_PATTERNS = [
    r'd\.sensors\.',
    r'd\.port',
    r'm\.(accel|rrate)',
    r'm\.\w+(Filtered|Mass)',
    r'\.(linlin|lincurve|linexp|clip|fold|range)\(',
    r'\.set\(',
    r'Pdef\(.*\)\.set\(',
    r'Pdef\(.*\)\.(play|pause|stop)',
    r'~next\s*=',
    r'~init\s*=',
    r'~deinit\s*=',
    r'~onEvent\s*=',
    r'~midiControllerValue\s*=',
]

def classify_line(line, context='unknown'):
    """Classify a line as synthdef, mapping, or ambiguous."""
    line_clean = line.strip()

    # Skip empty lines and pure comments
    if not line_clean or line_clean.startswith('//'):
        return None

    # Context-based classification (highest priority)
    if context == 'synthdef':
        return 'synthdef'
    elif context == 'mapping':
        return 'mapping'

    # Pattern-based scoring
    synthdef_score = sum(1 for pattern in SYNTHDEF_PATTERNS if re.search(pattern, line_clean))
    mapping_score = sum(1 for pattern in MAPPING_PATTERNS if re.search(pattern, line_clean))

    if synthdef_score > mapping_score:
        return 'synthdef'
    elif mapping_score > synthdef_score:
        return 'mapping'
    elif synthdef_score > 0 or mapping_score > 0:
        return 'ambiguous'
    else:
        return None  # Neither category

def get_context_from_line(line):
    """Determine if a line starts a SynthDef or mapping context."""
    line_clean = line.strip()

    if re.search(r'SynthDef\s*\(', line_clean):
        return 'synthdef'
    elif re.search(r'~(next|init|deinit|onEvent|midiControllerValue)\s*=\s*\{', line_clean):
        return 'mapping'

    return None

def parse_unified_diff(diff_output):
    """Parse unified diff output and classify changed lines."""
    changes = {
        'synthdef': {'added': [], 'removed': []},
        'mapping': {'added': [], 'removed': []},
        'ambiguous': {'added': [], 'removed': []}
    }

    current_context = 'unknown'
    brace_depth = 0

    lines = diff_output.split('\n')
    for line in lines:
        # Track context by looking for SynthDef or ~next declarations
        context_change = get_context_from_line(line)
        if context_change:
            current_context = context_change
            brace_depth = 0

        # Track brace depth to know when we exit a context
        brace_depth += line.count('{') - line.count('}')
        if brace_depth <= 0 and current_context != 'unknown':
            current_context = 'unknown'
            brace_depth = 0

        # Process added lines
        if line.startswith('+') and not line.startswith('+++'):
            content = line[1:]
            classification = classify_line(content, current_context)
            if classification:
                changes[classification]['added'].append(content)

        # Process removed lines
        elif line.startswith('-') and not line.startswith('---'):
            content = line[1:]
            classification = classify_line(content, current_context)
            if classification:
                changes[classification]['removed'].append(content)

    return changes

def get_personality_commits():
    """Get all commits that modified personality files."""
    result = subprocess.run(
        ['git', 'log', '--all', '--since=2024-01-01', '--no-merges',
         '--pretty=format:%H|%ai|%an|%s', '--', 'personalities/*.sc'],
        capture_output=True,
        text=True,
        cwd='..'
    )

    commits = []
    for line in result.stdout.strip().split('\n'):
        if not line or '|' not in line:
            continue

        parts = line.split('|', 3)
        if len(parts) >= 4:
            commits.append({
                'hash': parts[0],
                'datetime': parts[1],
                'author': parts[2],
                'subject': parts[3]
            })

    return commits

def get_commit_diff(commit_hash):
    """Get unified diff for a specific commit (personality files only)."""
    result = subprocess.run(
        ['git', 'show', '--unified=0', '--no-color', commit_hash, '--', 'personalities/*.sc'],
        capture_output=True,
        text=True,
        cwd='..'
    )
    return result.stdout

def get_changed_files(commit_hash):
    """Get list of files changed in a commit."""
    result = subprocess.run(
        ['git', 'show', '--name-only', '--pretty=format:', commit_hash, '--', 'personalities/*.sc'],
        capture_output=True,
        text=True,
        cwd='..'
    )
    files = [f.strip() for f in result.stdout.strip().split('\n') if f.strip()]
    return files

def analyze_commits():
    """Analyze all commits and classify code changes."""
    commits = get_personality_commits()
    print(f"Found {len(commits)} commits affecting personality files")

    analyzed_commits = []

    for i, commit in enumerate(commits):
        if i % 10 == 0:
            print(f"Processing commit {i+1}/{len(commits)}...")

        # Get diff and changed files
        diff = get_commit_diff(commit['hash'])
        files = get_changed_files(commit['hash'])

        # Parse and classify changes
        changes = parse_unified_diff(diff)

        analyzed_commit = {
            'hash': commit['hash'][:8],
            'date': commit['datetime'][:10],
            'datetime': commit['datetime'],
            'author': commit['author'],
            'subject': commit['subject'],
            'files_changed': files,
            'changes': {
                'synthdef': {
                    'lines_added': len(changes['synthdef']['added']),
                    'lines_removed': len(changes['synthdef']['removed']),
                    'net_change': len(changes['synthdef']['added']) - len(changes['synthdef']['removed'])
                },
                'mapping': {
                    'lines_added': len(changes['mapping']['added']),
                    'lines_removed': len(changes['mapping']['removed']),
                    'net_change': len(changes['mapping']['added']) - len(changes['mapping']['removed'])
                },
                'ambiguous': {
                    'lines_added': len(changes['ambiguous']['added']),
                    'lines_removed': len(changes['ambiguous']['removed']),
                    'net_change': len(changes['ambiguous']['added']) - len(changes['ambiguous']['removed'])
                }
            }
        }

        analyzed_commits.append(analyzed_commit)

    return analyzed_commits

def aggregate_by_period(commits, period='monthly'):
    """Aggregate commits by time period."""
    aggregations = defaultdict(lambda: {
        'synthdef_lines': 0,
        'mapping_lines': 0,
        'ambiguous_lines': 0,
        'synthdef_commits': set(),
        'mapping_commits': set(),
        'mixed_commits': set(),
        'total_commits': set(),
        'commits': []
    })

    for commit in commits:
        try:
            date = datetime.fromisoformat(commit['datetime'].replace('Z', '+00:00'))
        except:
            # Handle timezone offset format
            date_str = commit['datetime']
            if '+' in date_str or date_str.count('-') > 2:
                # Parse manually
                date_part = date_str[:10]
                date = datetime.strptime(date_part, '%Y-%m-%d')
            else:
                continue

        # Determine period key
        if period == 'weekly':
            # ISO week number
            iso_year, iso_week, _ = date.isocalendar()
            key = f"{iso_year}-W{iso_week:02d}"
            label = f"Week {iso_week}, {iso_year}"
        else:  # monthly
            key = date.strftime('%Y-%m')
            label = date.strftime('%b %Y')

        # Aggregate line counts
        synthdef_lines = commit['changes']['synthdef']['lines_added']
        mapping_lines = commit['changes']['mapping']['lines_added']
        ambiguous_lines = commit['changes']['ambiguous']['lines_added']

        aggregations[key]['synthdef_lines'] += synthdef_lines
        aggregations[key]['mapping_lines'] += mapping_lines
        aggregations[key]['ambiguous_lines'] += ambiguous_lines
        aggregations[key]['total_commits'].add(commit['hash'])
        aggregations[key]['commits'].append(commit)

        # Categorize commit type
        if synthdef_lines > 0 and mapping_lines > 0:
            aggregations[key]['mixed_commits'].add(commit['hash'])
        elif synthdef_lines > 0:
            aggregations[key]['synthdef_commits'].add(commit['hash'])
        elif mapping_lines > 0:
            aggregations[key]['mapping_commits'].add(commit['hash'])

    # Convert to list format
    result = []
    for key in sorted(aggregations.keys()):
        agg = aggregations[key]

        # Determine label
        if period == 'weekly':
            parts = key.split('-W')
            label = f"Week {parts[1]}, {parts[0]}"
        else:
            date = datetime.strptime(key, '%Y-%m')
            label = date.strftime('%b %Y')

        result.append({
            'period': key,
            'period_label': label,
            'synthdef_lines': agg['synthdef_lines'],
            'mapping_lines': agg['mapping_lines'],
            'ambiguous_lines': agg['ambiguous_lines'],
            'total_commits': len(agg['total_commits']),
            'synthdef_commits': len(agg['synthdef_commits']),
            'mapping_commits': len(agg['mapping_commits']),
            'mixed_commits': len(agg['mixed_commits']),
            'commits': agg['commits']
        })

    return result

def main():
    print("Analyzing personality code changes...")
    print("=" * 60)

    # Analyze all commits
    commits = analyze_commits()

    # Calculate totals
    total_synthdef_lines = sum(c['changes']['synthdef']['lines_added'] for c in commits)
    total_mapping_lines = sum(c['changes']['mapping']['lines_added'] for c in commits)
    total_ambiguous_lines = sum(c['changes']['ambiguous']['lines_added'] for c in commits)

    print(f"\nTotal commits analyzed: {len(commits)}")
    print(f"Total SynthDef lines added: {total_synthdef_lines}")
    print(f"Total Mapping lines added: {total_mapping_lines}")
    print(f"Total Ambiguous lines added: {total_ambiguous_lines}")

    # Aggregate by period
    print("\nAggregating by time period...")
    weekly = aggregate_by_period(commits, 'weekly')
    monthly = aggregate_by_period(commits, 'monthly')

    # Build output data
    data = {
        'metadata': {
            'analysis_date': datetime.now().isoformat(),
            'total_commits': len(commits),
            'date_range': {
                'start': '2024-01-01',
                'end': datetime.now().strftime('%Y-%m-%d')
            },
            'totals': {
                'synthdef_lines': total_synthdef_lines,
                'mapping_lines': total_mapping_lines,
                'ambiguous_lines': total_ambiguous_lines
            }
        },
        'commits': commits,
        'aggregations': {
            'weekly': weekly,
            'monthly': monthly
        }
    }

    # Write to file
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output_file = OUTPUT_DIR / 'personality_code_data.json'
    with open(output_file, 'w') as f:
        json.dump(data, f, indent=2)

    print(f"\nData written to {output_file}")
    print(f"Weekly periods: {len(weekly)}")
    print(f"Monthly periods: {len(monthly)}")
    print("\nDone!")

if __name__ == '__main__':
    main()
