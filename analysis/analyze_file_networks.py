#!/usr/bin/env python3
import subprocess
import json
from collections import defaultdict, Counter
from datetime import datetime
from pathlib import Path
import itertools

# Generated data lands here and is gitignored - the scripts are the source of
# truth, not their output. Anchored to this file rather than the working
# directory so it does not matter where the script is invoked from.
OUTPUT_DIR = Path(__file__).resolve().parent / 'pages' / 'output'

# Commit scope. Must stay identical across every analyzer in this directory.
GIT_SCOPE = ['--all', '--since=2024-01-01', '--no-merges']

def get_commit_files():
    """Extract all commits with their changed files."""
    result = subprocess.run(
        ['git', 'log', *GIT_SCOPE, '--pretty=format:%H|%ai|%an|%s', '--name-only'],
        capture_output=True,
        text=True
    )
    
    commits = []
    current_commit = None
    
    for line in result.stdout.strip().split('\n'):
        if not line:
            continue
            
        # Check if this is a commit header line
        if '|' in line and len(line.split('|')) >= 4:
            # Save previous commit if exists
            if current_commit and current_commit.get('files'):
                commits.append(current_commit)
            
            # Start new commit
            parts = line.split('|', 3)
            current_commit = {
                'hash': parts[0],
                'date': parts[1],
                'author': parts[2],
                'subject': parts[3],
                'files': []
            }
        elif current_commit is not None:
            # This is a file path
            file_path = line.strip()
            if file_path and not file_path.startswith('.'):  # Skip hidden files
                current_commit['files'].append(file_path)
    
    # Don't forget the last commit
    if current_commit and current_commit.get('files'):
        commits.append(current_commit)
    
    return commits

def analyze_file_networks(commits):
    """Analyze which files are frequently modified together."""
    
    # Track file pairs that appear together in commits
    file_pairs = Counter()
    file_commit_count = Counter()
    
    # Track commits for each file
    file_to_commits = defaultdict(list)
    
    for commit in commits:
        files = commit['files']
        
        # Count individual file changes
        for file_path in files:
            file_commit_count[file_path] += 1
            file_to_commits[file_path].append({
                'hash': commit['hash'][:8],
                'date': commit['date'][:10],
                'author': commit['author'],
                'subject': commit['subject'],
                'files_count': len(files)
            })
        
        # Generate all pairs of files in this commit
        for file1, file2 in itertools.combinations(files, 2):
            # Sort to ensure consistent ordering
            pair = tuple(sorted([file1, file2]))
            file_pairs[pair] += 1
    
    return file_pairs, file_commit_count, file_to_commits

def calculate_file_categories(file_paths):
    """Categorize files by type for visualization."""
    categories = {}
    
    for file_path in file_paths:
        if file_path.endswith(('.sc', '.scd')):
            categories[file_path] = 'SuperCollider'
        elif file_path.endswith(('.js', '.jsx', '.ts', '.tsx')):
            categories[file_path] = 'JavaScript'
        elif file_path.endswith(('.py', '.pyx')):
            categories[file_path] = 'Python'
        elif file_path.endswith(('.html', '.htm')):
            categories[file_path] = 'HTML'
        elif file_path.endswith(('.css', '.scss', '.sass')):
            categories[file_path] = 'CSS'
        elif file_path.endswith(('.json', '.yaml', '.yml', '.toml')):
            categories[file_path] = 'Config'
        elif file_path.endswith(('.md', '.txt', '.rst')):
            categories[file_path] = 'Documentation'
        elif file_path.endswith(('.wav', '.mp3', '.aiff', '.flac')):
            categories[file_path] = 'Audio'
        elif '/' not in file_path or file_path.count('/') <= 1:
            categories[file_path] = 'Root'
        else:
            categories[file_path] = 'Other'
    
    return categories

def generate_network_json(file_pairs, file_commit_count, file_to_commits, min_connections=2, top_files=100):
    """Generate JSON data for network visualization."""
    
    # Filter to most active files
    top_files_list = [file for file, count in file_commit_count.most_common(top_files)]
    
    # Calculate file categories
    file_categories = calculate_file_categories(top_files_list)
    
    # Build nodes (files)
    nodes = []
    for file_path in top_files_list:
        nodes.append({
            'id': file_path,
            'label': file_path.split('/')[-1],  # Just filename for display
            'full_path': file_path,
            'commit_count': file_commit_count[file_path],
            'category': file_categories[file_path],
            'commits': file_to_commits[file_path]
        })
    
    # Build edges (file relationships)
    edges = []
    for (file1, file2), weight in file_pairs.items():
        if weight >= min_connections and file1 in top_files_list and file2 in top_files_list:
            edges.append({
                'source': file1,
                'target': file2,
                'weight': weight,
                'label': f"{weight} commits"
            })
    
    # Calculate network statistics
    total_commits = len(set(commit['hash'] for file_commits in file_to_commits.values() 
                           for commit in file_commits))
    
    # Find most connected files
    file_connections = Counter()
    for edge in edges:
        file_connections[edge['source']] += edge['weight']
        file_connections[edge['target']] += edge['weight']
    
    most_connected = file_connections.most_common(10)
    
    return {
        'nodes': nodes,
        'edges': edges,
        'statistics': {
            'total_files': len(nodes),
            'total_edges': len(edges),
            'total_commits': total_commits,
            'most_connected_files': most_connected,
            'categories': list(set(file_categories.values()))
        },
        'filters': {
            'min_connections': min_connections,
            'top_files': top_files
        }
    }

def main():
    print("Analyzing file change networks...")
    
    # Get commit data with file changes
    commits = get_commit_files()
    print(f"Found {len(commits)} commits with file changes")
    
    # Analyze file relationships
    file_pairs, file_commit_count, file_to_commits = analyze_file_networks(commits)
    print(f"Found {len(file_pairs)} file pairs")
    print(f"Tracking {len(file_commit_count)} unique files")
    
    # Generate network data
    network_data = generate_network_json(file_pairs, file_commit_count, file_to_commits)
    
    # Save to file
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output_file = OUTPUT_DIR / 'file_networks.json'
    with open(output_file, 'w') as f:
        json.dump(network_data, f, indent=2)
    
    print(f"Generated network with {len(network_data['nodes'])} nodes and {len(network_data['edges'])} edges")
    print(f"Most connected files:")
    for file_path, connections in network_data['statistics']['most_connected_files'][:5]:
        print(f"  {file_path}: {connections} connections")
    
    print(f"Wrote {output_file}")

if __name__ == '__main__':
    main()