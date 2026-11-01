#!/usr/bin/env python3

import subprocess
import json
import re
from datetime import datetime
from collections import defaultdict
from pathlib import Path

# Generated data lands here and is gitignored - the scripts are the source of
# truth, not their output. Anchored to this file rather than the working
# directory so it does not matter where the script is invoked from.
OUTPUT_DIR = Path(__file__).resolve().parent / 'pages' / 'output'

# Commit scope. Must stay identical across every analyzer in this directory,
# or the timelines are drawn from different populations and cannot be read
# against each other. This script was missing --all, so it saw only the
# checked-out branch: 585 commits against the 801 the others analysed.
GIT_SCOPE = ['--all', '--since=2024-01-01', '--no-merges']

def get_git_log():
    """Get git log with file changes and dates"""
    cmd = [
        'git', 'log', '--name-only', '--pretty=format:%H|%ad|%s',
        '--date=iso', *GIT_SCOPE
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, cwd='..')
    return result.stdout.strip()

def categorize_file(filepath):
    """Categorize a file path into work types"""
    filepath = filepath.lower()
    
    # Implementation categories (code3.0 and code2.0 focused)
    if any(x in filepath for x in ['code3.0', 'code2.0']):
        if any(x in filepath for x in ['osc', 'midi', 'api']):
            return ('Implementation', 'Communications')
        elif any(x in filepath for x in ['render', 'visual', 'view', 'gui']):
            return ('Visual', 'System')
        elif any(x in filepath for x in ['main', 'controller', 'logic']):
            return ('Implementation', 'Logic')
        elif any(x in filepath for x in ['imu', 'gyro', 'sensor', 'motion']):
            return ('Implementation', 'IMU Data Processing')
        else:
            return ('Implementation', 'Core System')
    
    # Personalities categories
    elif 'personalities' in filepath:
        if filepath.endswith('.sc'):
            return ('Personalities', 'Synth Design')
        elif filepath.endswith('.txt'):
            return ('Personalities', 'Documentation')
        else:
            return ('Personalities', 'Data Analysis')
    
    # Analysis and visualization work
    elif 'analysis' in filepath:
        if filepath.endswith('.py'):
            return ('Analysis', 'Scripts')
        elif filepath.endswith('.html'):
            return ('Analysis', 'Visualization')
        elif filepath.endswith('.json'):
            return ('Analysis', 'Data')
        else:
            return ('Analysis', 'Other')
    
    # Legacy experimentation (only code1.0 now)
    elif 'code1.0' in filepath:
        return ('Experimentation', 'Legacy Code')
    
    # Synth development
    elif 'synths' in filepath:
        return ('Personalities', 'Synth Design')
    
    # Performance and workshop
    elif any(x in filepath for x in ['workshop', 'live', 'session']):
        return ('Performance', 'Live Session')
    
    # Lists and configuration
    elif 'lists' in filepath:
        return ('Implementation', 'Mapping')
    
    # Infrastructure
    elif any(x in filepath for x in ['readme', 'gitignore', 'config', '.md']):
        return ('Infrastructure', 'Documentation')
    
    # Visual work (dedicated category)
    elif 'visuals' in filepath:
        return ('Visual', 'Code')
    
    # Default
    else:
        return ('Other', 'Uncategorized')

def categorize_commit_message(message):
    """Categorize based on commit message keywords"""
    message = message.lower()
    
    # Merge commits
    if 'merge' in message:
        return ('Infrastructure', 'Version Control')
    
    # Specific work type keywords
    if any(x in message for x in ['viz', 'visual', 'render', 'gui']):
        return ('Visual', 'System')
    elif any(x in message for x in ['osc', 'midi', 'network']):
        return ('Implementation', 'Communications')
    elif any(x in message for x in ['synth', 'sound', 'audio']):
        return ('Personalities', 'Synth Design')
    elif any(x in message for x in ['analysis', 'plot', 'data']):
        return ('Analysis', 'Data Analysis')
    elif any(x in message for x in ['workshop', 'prep', 'session']):
        return ('Performance', 'Live Session')
    elif any(x in message for x in ['fix', 'bug', 'error']):
        return ('Implementation', 'Bug Fixes')
    
    return None

def parse_git_log(log_output):
    """Parse git log output into structured data"""
    commits = []
    lines = log_output.split('\n')
    
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if '|' in line and len(line.split('|')) >= 3:
            # Parse commit header
            parts = line.split('|', 2)
            commit_hash = parts[0]
            date_str = parts[1]
            message = parts[2]
            
            # Parse date
            try:
                date = datetime.fromisoformat(date_str.replace(' +', '+'))
            except:
                # Fallback date parsing
                date = datetime.now()
            
            # Get changed files
            i += 1
            files = []
            while i < len(lines) and lines[i].strip() and '|' not in lines[i]:
                file_path = lines[i].strip()
                if file_path:
                    files.append(file_path)
                i += 1
            
            commits.append({
                'hash': commit_hash,
                'date': date,
                'message': message,
                'files': files
            })
        else:
            i += 1
    
    return commits

def analyze_commits(commits):
    """Analyze commits and categorize work types.

    Each commit counts once, split across categories by its share of files: a
    commit touching three p-files and one core file adds 0.75 to Personalities
    and 0.25 to Implementation. Totals are therefore commits, and a stacked
    chart's height is the commit count, without hiding the minor categories a
    winner-takes-all rule would drop.
    """
    timeline_data = defaultdict(lambda: defaultdict(lambda: defaultdict(float)))
    commit_counts = defaultdict(int)

    for commit in commits:
        date_key = commit['date'].strftime('%Y-%m')
        commit_counts[date_key] += 1

        # Categorize by files
        file_categories = defaultdict(float)
        share = 1 / len(commit['files']) if commit['files'] else 0
        for file_path in commit['files']:
            category, subcategory = categorize_file(file_path)
            file_categories[(category, subcategory)] += share
        
        # If no file-based categorization, try message-based
        if not file_categories:
            msg_category = categorize_commit_message(commit['message'])
            if msg_category:
                category, subcategory = msg_category
                file_categories[(category, subcategory)] = 1
            else:
                file_categories[('Other', 'Uncategorized')] = 1
        
        # Add to timeline
        for (category, subcategory), count in file_categories.items():
            timeline_data[date_key][category][subcategory] += count

    return timeline_data, commit_counts

def generate_visualization_data(timeline_data, commit_counts):
    """Convert timeline data to format suitable for D3.js visualization"""
    
    # Get all categories and subcategories
    all_categories = set()
    all_subcategories = set()
    
    for month_data in timeline_data.values():
        for category, subcats in month_data.items():
            all_categories.add(category)
            for subcat in subcats.keys():
                all_subcategories.add(subcat)
    
    # Convert to time series format
    time_series = []
    sorted_months = sorted(timeline_data.keys())
    
    for month in sorted_months:
        month_entry = {
            'date': month,
            'commits': commit_counts[month],
            'categories': {}
        }
        
        for category in all_categories:
            category_total = 0
            subcategories = {}
            
            if category in timeline_data[month]:
                for subcat, count in timeline_data[month][category].items():
                    subcategories[subcat] = round(count, 3)
                    category_total += count

            month_entry['categories'][category] = {
                'total': round(category_total, 3),
                'subcategories': subcategories
            }
        
        time_series.append(month_entry)
    
    # Generate summary statistics
    category_totals = defaultdict(float)
    subcategory_totals = defaultdict(float)
    
    for month_data in timeline_data.values():
        for category, subcats in month_data.items():
            for subcat, count in subcats.items():
                category_totals[category] += count
                subcategory_totals[f"{category}::{subcat}"] += count
    
    return {
        'timeline': time_series,
        'summary': {
            'categories': {k: round(v, 1) for k, v in category_totals.items()},
            'subcategories': {k: round(v, 1) for k, v in subcategory_totals.items()},
            'total_commits': sum(commit_counts.values()),
            'time_range': {
                'start': sorted_months[0] if sorted_months else None,
                'end': sorted_months[-1] if sorted_months else None
            }
        },
        'metadata': {
            'generated': datetime.now().isoformat(),
            'categories': list(all_categories),
            'subcategories': list(all_subcategories)
        }
    }

def main():
    print("Analyzing git repository for work type categorization...")
    
    # Get git log
    print("Fetching git history...")
    log_output = get_git_log()
    
    # Parse commits
    print("Parsing commits...")
    commits = parse_git_log(log_output)
    print(f"Found {len(commits)} commits")
    
    # Analyze and categorize
    print("Categorizing work types...")
    timeline_data, commit_counts = analyze_commits(commits)
    
    # Generate visualization data
    print("Generating visualization data...")
    viz_data = generate_visualization_data(timeline_data, commit_counts)
    
    # Save to JSON
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output_file = OUTPUT_DIR / 'work_type_data.json'
    with open(output_file, 'w') as f:
        json.dump(viz_data, f, indent=2)
    
    print(f"Work type analysis complete! Data saved to {output_file}")
    print(f"Total commits analyzed: {viz_data['summary']['total_commits']}")
    print(f"Time range: {viz_data['summary']['time_range']['start']} to {viz_data['summary']['time_range']['end']}")
    print("\nWork type breakdown:")
    for category, count in sorted(viz_data['summary']['categories'].items(), key=lambda x: x[1], reverse=True):
        print(f"  {category}: {count} commits")

if __name__ == "__main__":
    main()