#!/usr/bin/env python3
"""Validate the compact cockpit with the boarding doorway and swivel chair."""
import json,runpy
from pathlib import Path
root=Path(__file__).resolve().parents[1]
m=json.loads((root/'src/main/blockbench/deepseeker.bbmodel').read_text())
b=json.loads((root/'src/main/blockbench/deepseeker_screen_bindings.json').read_text())
assert b['aft_compaction']['scale']==.5
assert not any(g['name']=='rear_cabin' for g in m['groups'])
door=next(e for e in m['elements'] if e['name']=='entry_door')
assert door['from'][0]==-9 and door['to'][0]==9 and door['to'][1]>=68
paint=[e for e in m['elements'] if e['name'].startswith('boarding_paint_')]
assert len(paint)==22 and all(e['type']=='mesh' and len(e['faces'])==1 for e in paint)
assert not any(e['name'].startswith(('boarding_rail_','boarding_rung_','boarding_mount_')) for e in m['elements'])
cushion=next(e for e in m['elements'] if e['name']=='seat_cushion')
assert cushion['to'][1]==42, 'Cushion must clear actual seated thighs'
runpy.run_path(str(root/'tools/validate_deepseeker_entry.py'))
print('PASS: compact cabin, clear boarding doorway, subtle painted guide and fitted swivel seat')
print('Run tools/validate_submarine_boarding.py after compiling for the full moving-mesh sweep.')
