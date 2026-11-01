#!/usr/bin/env python3
"""
Personality Similarity Analyzer

Analyzes SuperCollider personality files to extract and compare:
1. IMU sensor mappings (how sensor data maps to synth parameters)
2. Synthesis techniques (sample-based, synthesis methods, effects)
3. Overall approaches (patterns vs direct control, timing, visual events)
"""

import os
import re
import json
import glob
from pathlib import Path
from typing import Dict, List, Set, Tuple, Any
from collections import defaultdict, Counter

# Generated data lands here and is gitignored - the scripts are the source of
# truth, not their output. Anchored to this file rather than the working
# directory so it does not matter where the script is invoked from.
OUTPUT_DIR = Path(__file__).resolve().parent / 'pages' / 'output'


def jaccard(s1: Set, s2: Set) -> float:
    """Jaccard index, treating two empty sets as agreeing rather than differing.

    Absence is a shared property: two personalities that both use no gyro are
    alike in that respect, not maximally unlike. The mapping, technique and
    approach metrics below used to score the both-empty case 0 while still
    dividing by the full axis count, which capped similarity well below 1 for
    anything sparse - melChair1 and toot1, with byte-identical IMU mappings,
    scored 0.667. calculate_pattern_similarity already used this convention.
    """
    if not s1 and not s2:
        return 1.0
    return len(s1 & s2) / len(s1 | s2)


class PersonalityAnalyzer:
    def __init__(self, personalities_dir: str):
        self.personalities_dir = Path(personalities_dir)
        self.personalities = {}
        
    def analyze_all(self) -> Dict[str, Any]:
        """Analyze all personality files and generate similarity data"""
        print(f"🔍 Analyzing personality files in {self.personalities_dir}")
        
        # Find all .sc files
        sc_files = list(self.personalities_dir.glob("*.sc"))
        print(f"Found {len(sc_files)} personality files")
        
        # Analyze each personality
        for sc_file in sc_files:
            try:
                personality_data = self.analyze_personality(sc_file)
                if personality_data:
                    self.personalities[personality_data['name']] = personality_data
            except Exception as e:
                print(f"❌ Error analyzing {sc_file.name}: {e}")
        
        print(f"✅ Successfully analyzed {len(self.personalities)} personalities")
        
        # Generate similarity comparisons
        similarity_data = self.generate_similarities()
        
        return {
            'personalities': self.personalities,
            'similarities': similarity_data,
            'statistics': self.generate_statistics()
        }
    
    def analyze_personality(self, file_path: Path) -> Dict[str, Any]:
        """Analyze a single personality file"""
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
        except UnicodeDecodeError:
            # Try with different encoding
            with open(file_path, 'r', encoding='latin-1') as f:
                content = f.read()
        
        name = file_path.stem
        
        analysis = {
            'name': name,
            'file_path': str(file_path),
            'content_length': len(content),
            'imu_mappings': self.extract_imu_mappings(content),
            'synthesis_techniques': self.extract_synthesis_techniques(content),
            'overall_approach': self.extract_overall_approach(content),
            'visual_events': self.extract_visual_events(content),
            'patterns_and_timing': self.extract_patterns_timing(content),
            'pattern_analysis': self.extract_detailed_patterns(content),
            'complexity_metrics': self.calculate_complexity(content)
        }
        
        return analysis
    
    def extract_imu_mappings(self, content: str) -> Dict[str, Any]:
        """Extract IMU sensor to parameter mappings"""
        mappings = {
            'sensor_usage': {
                'accel': [],
                'gyro': [],
                'rrate': [],
            },
            'mapping_functions': [],
            'parameter_targets': [],
            'filter_settings': {}
        }
        
        # Find sensor usage patterns
        accel_patterns = re.findall(r'm\.accel\w*', content, re.IGNORECASE)
        gyro_patterns = re.findall(r'm\.gyro\w*', content, re.IGNORECASE)
        rrate_patterns = re.findall(r'm\.rrate\w*', content, re.IGNORECASE)
        
        mappings['sensor_usage']['accel'] = list(set(accel_patterns))
        mappings['sensor_usage']['gyro'] = list(set(gyro_patterns))
        mappings['sensor_usage']['rrate'] = list(set(rrate_patterns))
        
        # Extract mapping functions (linlin, lincurve, etc.)
        mapping_funcs = re.findall(r'\.(\w*lin\w*)\(', content, re.IGNORECASE)
        mappings['mapping_functions'] = list(set(mapping_funcs))
        
        # Extract parameter targets
        param_targets = re.findall(r'Pdef\([^)]+\)\.set\(\\(\w+)', content)
        mappings['parameter_targets'] = list(set(param_targets))
        
        # Extract filter attack/decay settings
        filter_settings = re.findall(r'm\.(\w+)\s*=\s*([\d.]+)', content)
        for param, value in filter_settings:
            mappings['filter_settings'][param] = float(value)
        
        return mappings
    
    def extract_synthesis_techniques(self, content: str) -> Dict[str, Any]:
        """Extract synthesis technique information"""
        techniques = {
            'synthdefs': [],
            'sample_based': False,
            'generative': False,
            'effects': [],
            'synthesis_methods': [],
            'buffer_usage': False
        }
        
        # Find SynthDef names
        synthdef_matches = re.findall(r'SynthDef\(\\(\w+)', content)
        techniques['synthdefs'] = list(set(synthdef_matches))
        
        # Check for sample-based synthesis
        if 'PlayBuf' in content or 'Buffer.read' in content or 'BufRateScale' in content:
            techniques['sample_based'] = True
            techniques['buffer_usage'] = True
        
        # Check for generative synthesis
        generative_keywords = ['SinOsc', 'Saw', 'Pulse', 'WhiteNoise', 'PinkNoise', 'LFO', 'Env.']
        techniques['generative'] = any(keyword in content for keyword in generative_keywords)
        
        # Extract synthesis methods
        synth_methods = []
        if 'PlayBuf' in content: synth_methods.append('sample_playback')
        if 'SinOsc' in content: synth_methods.append('sine_oscillator')
        if 'Saw' in content: synth_methods.append('sawtooth')
        if 'Pulse' in content: synth_methods.append('pulse')
        if 'WhiteNoise' in content: synth_methods.append('white_noise')
        if 'PinkNoise' in content: synth_methods.append('pink_noise')
        if 'Granular' in content: synth_methods.append('granular')
        
        techniques['synthesis_methods'] = synth_methods
        
        # Extract effects
        effects = []
        effect_patterns = [
            (r'RLPF|RHPF|BPF|HPF|LPF', 'filter'),
            (r'Reverb|GVerb|FreeVerb', 'reverb'),
            (r'Delay|DelayN|DelayL|DelayC', 'delay'),
            (r'Compander|Limiter', 'compression'),
            (r'distort|tanh|clip', 'distortion'),
            (r'Pan2|Balance2|LinPan2', 'panning'),
            (r'LeakDC', 'dc_removal')
        ]
        
        for pattern, effect_name in effect_patterns:
            if re.search(pattern, content, re.IGNORECASE):
                effects.append(effect_name)
        
        techniques['effects'] = effects
        
        return techniques
    
    def extract_overall_approach(self, content: str) -> Dict[str, Any]:
        """Extract overall approach characteristics"""
        approach = {
            'pattern_based': False,
            'direct_control': False,
            'event_driven': False,
            'continuous_control': False,
            'conditional_logic': False,
            'uses_pbind': False,
            'uses_pdef': False,
            'timing_approach': [],
            'control_style': []
        }
        
        # Pattern-based vs direct control
        if 'Pbind' in content or 'Pdef' in content:
            approach['pattern_based'] = True
            approach['uses_pbind'] = 'Pbind' in content
            approach['uses_pdef'] = 'Pdef' in content
        
        # Direct parameter setting
        if '.set(' in content:
            approach['direct_control'] = True
        
        # Event-driven approach
        if '~onEvent' in content or 'Event' in content:
            approach['event_driven'] = True
        
        # Continuous control (in ~next function)
        if '~next' in content:
            approach['continuous_control'] = True
        
        # Conditional logic
        if_count = len(re.findall(r'\bif\s*\(', content))
        approach['conditional_logic'] = if_count > 0
        approach['conditional_complexity'] = if_count
        
        # Timing approaches
        timing = []
        if 'TempoClock' in content: timing.append('tempo_clock')
        if 'quant:' in content: timing.append('quantized')
        if 'dur' in content: timing.append('duration_based')
        if 'lastTime' in content: timing.append('time_tracking')
        
        approach['timing_approach'] = timing
        
        # Control style analysis
        control_style = []
        if 'threshold' in content.lower(): control_style.append('threshold_based')
        if 'curve' in content.lower(): control_style.append('curve_mapping')
        if 'random' in content.lower() or 'rand' in content: control_style.append('randomization')
        if 'scale' in content.lower(): control_style.append('scaling')
        
        approach['control_style'] = control_style
        
        return approach
    
    def extract_visual_events(self, content: str) -> Dict[str, Any]:
        """Extract visual event information"""
        visual = {
            'uses_visuals': False,
            'visual_shapes': [],
            'visual_parameters': [],
            'color_usage': False,
            'animation_properties': []
        }
        
        if 'customVisualEvent' in content or 'visual' in content.lower():
            visual['uses_visuals'] = True
            
            # Extract shapes
            shape_matches = re.findall(r'\\shape,\s*\\(\w+)', content)
            visual['visual_shapes'] = list(set(shape_matches))
            
            # Extract visual parameters
            visual_params = re.findall(r'\\(start\w*|end\w*|duration|rotation|size\w*)', content)
            visual['visual_parameters'] = list(set(visual_params))
            
            # Check for color usage
            visual['color_usage'] = 'Color' in content
            
            # Animation properties
            anim_props = []
            if 'Env(' in content: anim_props.append('envelopes')
            if 'duration' in content: anim_props.append('timed_animation')
            if 'rotation' in content: anim_props.append('rotation')
            if 'modulation' in content: anim_props.append('modulation')
            
            visual['animation_properties'] = anim_props
        
        return visual
    
    def extract_patterns_timing(self, content: str) -> Dict[str, Any]:
        """Extract pattern and timing information"""
        patterns = {
            'pattern_types': [],
            'sequence_patterns': [],
            'timing_patterns': [],
            'loop_structures': [],
            'pattern_complexity': 0
        }
        
        # Pattern types
        pattern_types = []
        pattern_keywords = ['Pseq', 'Pwhite', 'Pxrand', 'Prand', 'Pfunc', 'Pkey', 'Pseries']
        for keyword in pattern_keywords:
            if keyword in content:
                pattern_types.append(keyword.lower())
        patterns['pattern_types'] = pattern_types
        
        # Extract sequence data
        seq_matches = re.findall(r'Pseq\(\s*\[([^\]]+)\]', content)
        patterns['sequence_patterns'] = [seq.strip() for seq in seq_matches]
        
        # Timing patterns
        timing = []
        if 'inf)' in content: timing.append('infinite_loop')
        if 'stutter(' in content: timing.append('stuttering')
        if 'dur,' in content: timing.append('explicit_duration')
        
        patterns['timing_patterns'] = timing
        
        # Pattern complexity (number of pattern objects)
        patterns['pattern_complexity'] = sum(1 for pt in pattern_keywords if pt in content)
        
        return patterns
    
    def extract_detailed_patterns(self, content: str) -> Dict[str, Any]:
        """Extract detailed pattern usage for similarity comparison"""
        patterns = {
            'approach_type': 'logic',  # 'pattern', 'logic', 'hybrid'
            'pattern_complexity': 0,
            'pbind_parameters': [],
            'pattern_objects': [],
            'parameter_mapping_style': [],
            'control_flow_patterns': [],
            'timing_control': [],
            'dynamic_parameters': [],
            'static_parameters': [],
            'pattern_vs_logic_ratio': 0.0
        }
        
        # Determine primary approach type
        has_pbind = 'Pbind' in content
        has_pdef = 'Pdef' in content
        pattern_count = len(re.findall(r'P\w+\(', content))
        logic_count = len(re.findall(r'\bif\s*\(', content)) + len(re.findall(r'\.set\(', content))
        
        if has_pbind or has_pdef:
            if logic_count > pattern_count:
                patterns['approach_type'] = 'hybrid'
            else:
                patterns['approach_type'] = 'pattern'
        else:
            patterns['approach_type'] = 'logic'
        
        patterns['pattern_vs_logic_ratio'] = pattern_count / max(1, pattern_count + logic_count)
        
        # Extract Pbind parameters
        pbind_matches = re.findall(r'\\(\w+),\s*([^,\n}]+)', content)
        pbind_params = []
        for param, value in pbind_matches:
            pbind_params.append({
                'parameter': param,
                'value_type': self.classify_parameter_value(value.strip()),
                'raw_value': value.strip()[:50]  # First 50 chars
            })
        patterns['pbind_parameters'] = pbind_params
        
        # Extract pattern objects used
        pattern_objects = []
        pattern_types = ['Pseq', 'Pwhite', 'Pxrand', 'Prand', 'Pfunc', 'Pkey', 'Pseries', 'Pstutter', 'Pn']
        for ptype in pattern_types:
            matches = re.findall(rf'{ptype}\([^)]*\)', content)
            if matches:
                pattern_objects.extend([(ptype, match) for match in matches])
        pattern_counter = Counter([p[0] for p in pattern_objects])
        patterns['pattern_objects'] = [(ptype, count) for ptype, count in pattern_counter.items()]
        
        # Parameter mapping style analysis
        mapping_styles = []
        if '.set(' in content: mapping_styles.append('dynamic_setting')
        if 'Pfunc' in content: mapping_styles.append('functional_mapping')
        if 'Pkey' in content: mapping_styles.append('key_reference')
        if re.search(r'P\w+.*\.lin', content): mapping_styles.append('scaling_in_pattern')
        patterns['parameter_mapping_style'] = mapping_styles
        
        # Control flow patterns
        control_patterns = []
        if re.search(r'if.*Pdef.*play', content): control_patterns.append('conditional_playback')
        if re.search(r'if.*\.set', content): control_patterns.append('conditional_parameter_setting')
        if 'quant:' in content: control_patterns.append('quantized_timing')
        if 'lastTime' in content: control_patterns.append('time_tracking')
        patterns['control_flow_patterns'] = control_patterns
        
        # Categorize parameters as dynamic vs static
        dynamic_params = []
        static_params = []
        for param_info in pbind_params:
            param_name = param_info['parameter']
            value_type = param_info['value_type']
            
            if value_type in ['pattern', 'function', 'random']:
                dynamic_params.append(param_name)
            else:
                static_params.append(param_name)
        
        patterns['dynamic_parameters'] = dynamic_params
        patterns['static_parameters'] = static_params
        patterns['pattern_complexity'] = len(dynamic_params) + len(pattern_objects)
        
        return patterns
    
    def classify_parameter_value(self, value: str) -> str:
        """Classify the type of parameter value"""
        value = value.strip()
        
        if value.startswith('P'):
            return 'pattern'
        elif value.startswith('{') or 'Pfunc' in value:
            return 'function'
        elif any(rand in value.lower() for rand in ['rand', 'white', 'choose']):
            return 'random'
        elif re.match(r'^[\d.-]+$', value):
            return 'static_number'
        elif value.startswith('\\'):
            return 'symbol'
        elif value.startswith('"') or value.startswith("'"):
            return 'string'
        elif '[' in value and ']' in value:
            return 'array'
        else:
            return 'expression'
    
    def calculate_complexity(self, content: str) -> Dict[str, float]:
        """Calculate various complexity metrics"""
        lines = content.split('\n')
        non_empty_lines = [line for line in lines if line.strip()]
        
        return {
            'total_lines': len(lines),
            'code_lines': len(non_empty_lines),
            'comment_lines': len([line for line in lines if line.strip().startswith('//')]),
            'function_count': len(re.findall(r'~\w+\s*=', content)),
            'synthdef_count': len(re.findall(r'SynthDef\(', content)),
            'pattern_count': len(re.findall(r'P\w+\(', content)),
            'variable_count': len(set(re.findall(r'\b[a-z]\w*\s*=', content))),
            'control_flow_count': len(re.findall(r'\b(if|while|for)\s*\(', content))
        }
    
    def generate_similarities(self) -> Dict[str, Any]:
        """Generate similarity comparisons between personalities"""
        print("🔗 Generating similarity comparisons...")
        
        similarities = {
            'mapping_similarity': {},
            'technique_similarity': {},
            'approach_similarity': {},
            'pattern_similarity': {},
            'overall_similarity': {},
            'clusters': {}
        }
        
        names = list(self.personalities.keys())
        
        # Calculate pairwise similarities
        for i, name1 in enumerate(names):
            similarities['mapping_similarity'][name1] = {}
            similarities['technique_similarity'][name1] = {}
            similarities['approach_similarity'][name1] = {}
            similarities['pattern_similarity'][name1] = {}
            similarities['overall_similarity'][name1] = {}
            
            for j, name2 in enumerate(names):
                if i != j:
                    mapping_sim = self.calculate_mapping_similarity(
                        self.personalities[name1], self.personalities[name2])
                    technique_sim = self.calculate_technique_similarity(
                        self.personalities[name1], self.personalities[name2])
                    approach_sim = self.calculate_approach_similarity(
                        self.personalities[name1], self.personalities[name2])
                    pattern_sim = self.calculate_pattern_similarity(
                        self.personalities[name1], self.personalities[name2])
                    
                    # Overall similarity (weighted combination)
                    overall_sim = (mapping_sim * 0.25 + technique_sim * 0.3 + approach_sim * 0.25 + pattern_sim * 0.2)
                    
                    similarities['mapping_similarity'][name1][name2] = mapping_sim
                    similarities['technique_similarity'][name1][name2] = technique_sim
                    similarities['approach_similarity'][name1][name2] = approach_sim
                    similarities['pattern_similarity'][name1][name2] = pattern_sim
                    similarities['overall_similarity'][name1][name2] = overall_sim
        
        # Find clusters/groups
        similarities['clusters'] = self.find_clusters(names, similarities['overall_similarity'])
        
        return similarities
    
    def calculate_mapping_similarity(self, p1: Dict, p2: Dict) -> float:
        """Calculate similarity in IMU mapping approaches"""
        m1, m2 = p1['imu_mappings'], p2['imu_mappings']
        
        # Sensor usage similarity
        sensor_types = ['accel', 'gyro', 'rrate']
        sensor_sim = sum(
            jaccard(set(m1['sensor_usage'][s]), set(m2['sensor_usage'][s]))
            for s in sensor_types
        ) / len(sensor_types)

        # Mapping function similarity
        func_sim = jaccard(set(m1['mapping_functions']), set(m2['mapping_functions']))

        # Parameter target similarity
        param_sim = jaccard(set(m1['parameter_targets']), set(m2['parameter_targets']))

        return (sensor_sim + func_sim + param_sim) / 3
    
    def calculate_technique_similarity(self, p1: Dict, p2: Dict) -> float:
        """Calculate similarity in synthesis techniques"""
        t1, t2 = p1['synthesis_techniques'], p2['synthesis_techniques']
        
        # Synthesis method similarity
        method_sim = jaccard(set(t1['synthesis_methods']), set(t2['synthesis_methods']))

        # Effects similarity
        effect_sim = jaccard(set(t1['effects']), set(t2['effects']))

        # Binary feature similarity
        binary_features = ['sample_based', 'generative', 'buffer_usage']
        binary_sim = sum(1 for feat in binary_features if t1[feat] == t2[feat]) / len(binary_features)
        
        return (method_sim + effect_sim + binary_sim) / 3
    
    def calculate_approach_similarity(self, p1: Dict, p2: Dict) -> float:
        """Calculate similarity in overall approach"""
        a1, a2 = p1['overall_approach'], p2['overall_approach']
        
        # Binary features
        binary_features = ['pattern_based', 'direct_control', 'event_driven', 'continuous_control']
        binary_sim = sum(1 for feat in binary_features if a1[feat] == a2[feat]) / len(binary_features)
        
        # List features
        timing_sim = jaccard(set(a1['timing_approach']), set(a2['timing_approach']))

        control_sim = jaccard(set(a1['control_style']), set(a2['control_style']))

        return (binary_sim + timing_sim + control_sim) / 3
    
    def calculate_pattern_similarity(self, p1: Dict, p2: Dict) -> float:
        """Calculate similarity in pattern usage and approach"""
        pat1, pat2 = p1['pattern_analysis'], p2['pattern_analysis']
        
        # Approach type similarity (exact match gets high score)
        approach_sim = 1.0 if pat1['approach_type'] == pat2['approach_type'] else 0.3
        
        # Pattern vs logic ratio similarity
        ratio_diff = abs(pat1['pattern_vs_logic_ratio'] - pat2['pattern_vs_logic_ratio'])
        ratio_sim = 1.0 - ratio_diff
        
        # Pattern objects similarity
        objects1 = dict(pat1['pattern_objects'])
        objects2 = dict(pat2['pattern_objects'])
        all_objects = set(objects1.keys()).union(set(objects2.keys()))
        
        if all_objects:
            object_sim = sum(
                1 - abs(objects1.get(obj, 0) - objects2.get(obj, 0)) / 
                max(1, max(objects1.get(obj, 0), objects2.get(obj, 0)))
                for obj in all_objects
            ) / len(all_objects)
        else:
            object_sim = 1.0 if not objects1 and not objects2 else 0.0
        
        # Parameter mapping style similarity
        styles1, styles2 = set(pat1['parameter_mapping_style']), set(pat2['parameter_mapping_style'])
        style_sim = len(styles1.intersection(styles2)) / len(styles1.union(styles2)) if (styles1 or styles2) else 1.0
        
        # Dynamic vs static parameter similarity
        dyn1, dyn2 = set(pat1['dynamic_parameters']), set(pat2['dynamic_parameters'])
        stat1, stat2 = set(pat1['static_parameters']), set(pat2['static_parameters'])
        
        # Parameter type similarity (focus on parameter names used)
        all_dyn_params = dyn1.union(dyn2)
        all_stat_params = stat1.union(stat2)
        
        param_sim = 0.0
        if all_dyn_params or all_stat_params:
            dyn_sim = len(dyn1.intersection(dyn2)) / len(all_dyn_params) if all_dyn_params else 1.0
            stat_sim = len(stat1.intersection(stat2)) / len(all_stat_params) if all_stat_params else 1.0
            param_sim = (dyn_sim + stat_sim) / 2
        else:
            param_sim = 1.0
        
        # Control flow pattern similarity
        control1, control2 = set(pat1['control_flow_patterns']), set(pat2['control_flow_patterns'])
        control_sim = len(control1.intersection(control2)) / len(control1.union(control2)) if (control1 or control2) else 1.0
        
        # Pattern complexity similarity (normalized difference)
        complexity_diff = abs(pat1['pattern_complexity'] - pat2['pattern_complexity'])
        max_complexity = max(pat1['pattern_complexity'], pat2['pattern_complexity'], 1)
        complexity_sim = 1.0 - (complexity_diff / max_complexity)
        
        # Weighted combination
        total_sim = (
            approach_sim * 0.25 +      # Approach type is important
            ratio_sim * 0.2 +          # Pattern vs logic ratio
            object_sim * 0.2 +         # Pattern objects used
            style_sim * 0.15 +         # Mapping styles
            param_sim * 0.1 +          # Parameter usage
            control_sim * 0.05 +       # Control flow
            complexity_sim * 0.05      # Complexity
        )
        
        return max(0.0, min(1.0, total_sim))
    
    def find_clusters(self, names: List[str], similarities: Dict[str, Dict[str, float]]) -> Dict[str, Any]:
        """Find clusters/groups of similar personalities"""
        # Simple clustering based on similarity thresholds
        clusters = {
            'high_similarity': [],  # > 0.7
            'medium_similarity': [],  # 0.4 - 0.7
            'low_similarity': []  # < 0.4
        }
        
        processed = set()
        
        for name1 in names:
            if name1 in processed:
                continue
                
            similar_group = [name1]
            for name2 in names:
                if name1 != name2 and name2 not in processed:
                    sim_score = similarities.get(name1, {}).get(name2, 0)
                    if sim_score > 0.5:  # Threshold for grouping
                        similar_group.append(name2)
                        processed.add(name2)
            
            processed.add(name1)
            
            if len(similar_group) > 1:
                avg_similarity = sum(
                    similarities.get(similar_group[i], {}).get(similar_group[j], 0)
                    for i in range(len(similar_group))
                    for j in range(i+1, len(similar_group))
                ) / max(1, len(similar_group) * (len(similar_group) - 1) / 2)
                
                if avg_similarity > 0.7:
                    clusters['high_similarity'].append({
                        'members': similar_group,
                        'avg_similarity': avg_similarity
                    })
                elif avg_similarity > 0.4:
                    clusters['medium_similarity'].append({
                        'members': similar_group,
                        'avg_similarity': avg_similarity
                    })
                else:
                    clusters['low_similarity'].append({
                        'members': similar_group,
                        'avg_similarity': avg_similarity
                    })
        
        return clusters
    
    def generate_statistics(self) -> Dict[str, Any]:
        """Generate overall statistics about the personality collection"""
        if not self.personalities:
            return {}
        
        stats = {
            'total_personalities': len(self.personalities),
            'sensor_usage_stats': defaultdict(int),
            'synthesis_technique_stats': defaultdict(int),
            'approach_stats': defaultdict(int),
            'complexity_stats': {}
        }
        
        # Aggregate statistics
        complexities = []
        
        for p in self.personalities.values():
            # Sensor usage
            for sensor_type, sensors in p['imu_mappings']['sensor_usage'].items():
                stats['sensor_usage_stats'][sensor_type] += len(sensors)
            
            # Synthesis techniques
            for method in p['synthesis_techniques']['synthesis_methods']:
                stats['synthesis_technique_stats'][method] += 1
            
            # Approach stats
            if p['overall_approach']['pattern_based']:
                stats['approach_stats']['pattern_based'] += 1
            if p['overall_approach']['direct_control']:
                stats['approach_stats']['direct_control'] += 1
            
            complexities.append(p['complexity_metrics']['code_lines'])
        
        # Complexity statistics
        if complexities:
            avg_lines = sum(complexities) / len(complexities)
            std_lines = (sum((x - avg_lines) ** 2 for x in complexities) / len(complexities)) ** 0.5
            stats['complexity_stats'] = {
                'avg_code_lines': avg_lines,
                'min_code_lines': min(complexities),
                'max_code_lines': max(complexities),
                'std_code_lines': std_lines
            }
        
        return dict(stats)


def main():
    # Configuration
    personalities_dir = "../personalities"
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output_file = OUTPUT_DIR / "personality_similarities.json"
    
    # Initialize analyzer
    analyzer = PersonalityAnalyzer(personalities_dir)
    
    # Run analysis
    print("🚀 Starting personality analysis...")
    results = analyzer.analyze_all()
    
    # Save results
    print(f"💾 Saving results to {output_file}")
    with open(output_file, 'w') as f:
        json.dump(results, f, indent=2, default=str)
    
    print(f"✅ Analysis complete! Found {results['statistics']['total_personalities']} personalities")
    print(f"📊 Generated {len(results['similarities']['overall_similarity'])} similarity comparisons")
    
    # Print some interesting statistics
    print("\n📈 Key Statistics:")
    stats = results['statistics']
    print(f"  • Total personalities: {stats.get('total_personalities', 0)}")
    print(f"  • Average code lines: {stats.get('complexity_stats', {}).get('avg_code_lines', 0):.1f}")
    print(f"  • Pattern-based approaches: {stats.get('approach_stats', {}).get('pattern_based', 0)}")
    print(f"  • Direct control approaches: {stats.get('approach_stats', {}).get('direct_control', 0)}")
    
    # Print top synthesis techniques
    if 'synthesis_technique_stats' in stats:
        print(f"  • Top synthesis techniques:")
        for technique, count in sorted(stats['synthesis_technique_stats'].items(), 
                                     key=lambda x: x[1], reverse=True)[:5]:
            print(f"    - {technique}: {count}")


if __name__ == "__main__":
    main()