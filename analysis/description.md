# SCbounce Analysis & Visualization System

## Overview

This analysis directory contains a comprehensive data analysis and visualization system for the SCbounce project - a SuperCollider-based musical performance system using motion-controlled "Airsticks" on Raspberry Pi. The visualizations provide deep insights into the project's development patterns, code evolution, and musical personality design.

## What This System Does

The visualization system performs three main types of analysis:

### 1. **Git Repository Analysis & Word Cloud Visualization**
- **Script**: `analyze_git.py`
- **Output**: Interactive HTML timelines and word clouds
- **Purpose**: Analyzes commit history to understand development patterns and terminology usage

**Key Features:**
- Extracts and categorizes commits by type (personality, implementation, mixed, other)
- Generates word frequency analysis from commit messages
- Creates interactive timeline visualizations showing development evolution
- Filters out common stopwords to highlight domain-specific terminology
- Tracks 200+ most significant words with commit associations

### 2. **File Change Network Analysis**
- **Script**: `analyze_file_networks.py` 
- **Output**: Interactive network graph (`file_networks.html`)
- **Purpose**: Visualizes how files are interconnected through simultaneous modifications

**Key Features:**
- Maps relationships between files that are frequently changed together
- Categorizes files by type (SuperCollider, JavaScript, Python, HTML, etc.)
- Shows network connectivity patterns in the codebase
- Identifies the most connected and central files in the project
- Interactive D3.js-based network visualization with filtering

### 3. **Personality Similarity Analysis**
- **Script**: `personality_analyzer.py`
- **Output**: Comprehensive personality comparison data
- **Purpose**: Analyzes SuperCollider personality files to understand musical approaches

**Key Features:**
- **IMU Mapping Analysis**: How sensor data (accelerometer, gyroscope) maps to synthesis parameters
- **Synthesis Technique Detection**: Identifies sample-based vs. generative approaches, effects usage
- **Approach Classification**: Pattern-based vs. logic-based control, timing strategies
- **Visual Event Integration**: Analyzes use of visual feedback and animation
- **Similarity Scoring**: Quantifies relationships between different musical personalities
- **Clustering**: Groups personalities by similar approaches and techniques

### 4. **Personality Code Pattern Analysis**
- **Script**: `analyze_personality_code.py`
- **Output**: Time-series analysis of code changes
- **Purpose**: Tracks the evolution of synthesis vs. mapping code over time

**Key Features:**
- Distinguishes between SynthDef (sound generation) and mapping (sensor control) code
- Tracks code evolution through git diffs with pattern recognition
- Aggregates changes by weekly and monthly periods
- Classifies ambiguous code patterns for comprehensive analysis

### 5. **Work Type Categorization**
- **Script**: `work_type_analyzer.py`
- **Output**: Timeline visualization of development activities
- **Purpose**: Categorizes development work into meaningful activity types

**Key Categories:**
- **Implementation**: Core system, communications, IMU processing, logic
- **Personalities**: Synth design and documentation
- **Analysis**: Scripts, visualization, data processing
- **Visual**: System rendering and GUI development
- **Performance**: Live session preparation
- **Infrastructure**: Documentation and version control

## Interactive Visualizations

### Timeline Visualizations
- **Standard Timelines** (`timeline_*.html`): Various timeline views of different data aspects
- **Work Type Timeline** (`work_type_timeline.html`): Categorized development activity over time

### Network Analysis
- **File Networks** (`file_networks.html`): Interactive graph showing file relationships
- **Personality Similarities** (`personality_similarities.html`): Comparison matrix of musical personalities
  - **Co-change Threshold**: Controls the minimum similarity score (0.0-1.0) required to display connections between personalities. Higher thresholds show only the most similar personalities, while lower thresholds reveal broader relationship patterns.

### Data Analysis
- **Personality Code Timeline** (`personality_code_timeline.html`): Evolution of synthesis vs. mapping code
- Multiple JSON data files containing processed analysis results for further exploration

## Technical Implementation

### Data Processing Pipeline
1. **Git History Extraction**: Uses subprocess calls to extract commit data, file changes, and diffs
2. **Pattern Recognition**: Advanced regex patterns identify SuperCollider-specific code structures
3. **Classification Algorithms**: Multiple classification systems for commits, files, and code patterns
4. **Similarity Metrics**: Multi-dimensional similarity scoring for personality comparison
5. **Visualization Generation**: D3.js-based interactive web visualizations

### Code Analysis Sophistication
- **Context-Aware Parsing**: Recognizes SynthDef vs. mapping contexts in SuperCollider code
- **Semantic Classification**: Distinguishes between different types of musical control approaches
- **Time-Series Analysis**: Tracks development patterns and code evolution over time
- **Network Analysis**: Identifies central files and collaboration patterns

## Data Insights

The system reveals key insights about the SCbounce project:

### Development Patterns
- Evolution from experimentation to structured implementation
- Balance between technical infrastructure and creative musical development
- Collaborative development patterns through file co-modification networks

### Musical Personality Analysis
- Diversity of approaches: pattern-based vs. direct control
- Synthesis technique preferences: sample-based vs. generative
- IMU sensor mapping strategies and parameter targeting
- Visual-audio integration patterns

### Code Evolution
- Progression of SuperCollider synthesis complexity
- Development of sensor mapping sophistication
- Integration of visual feedback systems

## Usage Context

This analysis system appears to support:
- **Research Documentation**: Understanding how musical interfaces develop over time
- **Collaborative Development**: Visualizing team collaboration patterns and file dependencies  
- **Musical Interface Design**: Analyzing different approaches to motion-controlled synthesis
- **Performance Preparation**: Understanding the relationship between different musical personalities
- **Educational Documentation**: Demonstrating evolution of creative coding practices

The comprehensive nature of these visualizations suggests this is both a working development tool and a research artifact documenting the evolution of a sophisticated digital musical instrument system.