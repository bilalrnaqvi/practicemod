#!/usr/bin/env python3
"""Update only native animation clips, sampling the Java runtime's steady poses."""
import csv
import json
import subprocess
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'build/dolphin-animation'
BUILD.mkdir(parents=True, exist_ok=True)
subprocess.run(['javac', '-d', str(BUILD),
    str(ROOT / 'src/main/java/com/bilal/practicemod/MechanicalDolphinAnimation.java'),
    str(ROOT / 'tools/ValidateMechanicalDolphinAnimation.java')], check=True)
subprocess.run(['java', '-cp', str(BUILD), 'com.bilal.practicemod.ValidateMechanicalDolphinAnimation'], cwd=ROOT, check=True)

path = ROOT / 'src/main/blockbench/mechanical_dolphin.bbmodel'
model = json.loads(path.read_text())
geometry = {k: model[k] for k in ('elements', 'groups', 'outliner', 'textures')}
groups = {g['name']: g for g in model['groups']}
tracks = {
    '09_TAIL_peduncle': ('tail', None, None),
    '10_TAIL_FLUKES_pitch_X': ('flukes', None, None),
    '08_port_pectoral_fin': ('portPitch', None, 'portRoll'),
    '08_starboard_pectoral_fin': ('starboardPitch', None, 'starboardRoll'),
    '07_dorsal_fin': (None, 'dorsalYaw', 'dorsalRoll'),
}
rows = list(csv.DictReader((BUILD / 'clips.csv').open()))
uid = lambda name: str(uuid.uuid5(uuid.NAMESPACE_URL, 'practicemod:dolphin:swimming:' + name))
clips = []
for label in ('idle', 'swim'):
    samples = [r for r in rows if r['clip'] == label]
    animators = {}
    for name, channels in tracks.items():
        keys = []
        for i, row in enumerate(samples):
            # Native -Z to runtime +Z is Y=180: negate X and Z, preserve Y.
            rotation = {axis: str(round(float(row[channel]) * sign, 8)) if channel else '0'
                        for axis, channel, sign in zip('xyz', channels, (-1, 1, -1))}
            keys.append(dict(uuid=uid(f'{label}:{name}:{i}'), channel='rotation', time=float(row['time']),
                             interpolation='linear', color=-1, data_points=[rotation]))
        animators[groups[name]['uuid']] = dict(name=name, type='bone', rotation_global=False,
                                               quaternion_interpolation=False, keyframes=keys)
    clips.append(dict(uuid=uid(label), name='animation.mechanical_dolphin.' + label,
                      loop='loop', override=True, length=float(samples[-1]['time']), snapping=100,
                      selected=False, animators=animators))
replaced = {'animation.mechanical_dolphin.' + name for name in ('idle', 'swim', 'tail_idle', 'tail_swim')}
model['animations'] = [a for a in model.get('animations', []) if a['name'] not in replaced] + clips
path.write_text(json.dumps(model, indent=2) + '\n')
assert {k: json.loads(path.read_text())[k] for k in geometry} == geometry
report = dict(runtime_source='src/main/java/com/bilal/practicemod/MechanicalDolphinAnimation.java',
              clips={a['name']: {'seconds': a['length'], 'joints': 5, 'samples_per_joint': 129} for a in clips},
              geometry_and_texture_unchanged=True, runtime_integrated=True, door_animation_authored=False,
              note='Supersedes the prototype single-hinge tail clips. Runtime integrates phase and smooths speed/water/steering; native clips show steady endpoints.')
(ROOT / 'output/blockbench/mechanical-dolphin/tail-bindings.json').write_text(json.dumps(report, indent=2) + '\n')
print('Updated idle/swim native clips; geometry, joints, UVs and textures preserved.')
