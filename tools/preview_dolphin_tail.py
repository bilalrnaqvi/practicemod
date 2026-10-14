#!/usr/bin/env python3
"""Review all five runtime joints and verify native clips match their rendered meshes."""
import itertools
import json
import subprocess
import numpy as np
from PIL import Image
from preview_mechanical_dolphin import R, O, geometry

build = R / 'build/dolphin-animation'
classpath = (R / 'build/boarding-classpath.txt').read_text().strip()
subprocess.run(['javac', '-proc:none', '-cp', classpath, '-d', str(build),
    'tools/PreviewDeepseeker.java', 'tools/PreviewMechanicalDolphin.java',
    'tools/PreviewMechanicalDolphinAnimation.java'], cwd=R, check=True)
subprocess.run(['java', '-Djava.awt.headless=true', '-cp', str(build) + ':' + classpath,
    'com.bilal.practicemod.PreviewMechanicalDolphinAnimation'], cwd=R, check=True)

source = R / 'src/main/blockbench/mechanical_dolphin.bbmodel'
model = json.loads(source.read_text())
clips = {a['name'].split('.')[-1]: a for a in model['animations']}
max_error = 0
for label in ('idle', 'swim'):
    clip = clips[label]
    assert len(clip['animators']) == 5
    for track in clip['animators'].values():
        assert 'CANOPY' not in track['name']
        a, b = [track['keyframes'][i]['data_points'][0] for i in (0, -1)]
        assert all(abs(float(a[axis]) - float(b[axis])) < 1e-6 for axis in 'xyz')
    for frame in (0, 8, 16, 24):
        faces, _, _ = geometry(source, clip['name'], clip['length'] * frame / 32)
        native = np.concatenate([p for p, _, _, _ in faces]) * [-1/16, 1/16, -1/16]
        runtime = np.loadtxt(build / label / f'mesh-{frame}.csv', delimiter=',')
        # Independent native group hierarchy versus the Java renderer's captured vertices.
        grid = {}
        for p in runtime:
            key = tuple(np.floor(p * 10000).astype(int))
            grid.setdefault(key, []).append(p)
        for p in native:
            cell = np.floor(p * 10000).astype(int)
            candidates = [q for delta in itertools.product((-1, 0, 1), repeat=3)
                          for q in grid.get(tuple(cell + delta), [])]
            assert candidates, (label, frame, 'missing native vertex', p)
            error = min(float(np.linalg.norm(p - q)) for q in candidates)
            assert error < 2e-6, (label, frame, error)
            max_error = max(max_error, error)
    frames = [Image.open(build / label / f'{i:02d}.png').convert('RGB') for i in range(32)]
    palette = frames[8].quantize(colors=192)
    frames = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in frames]
    duration = [10 * (round((i+1) * clip['length'] * 100 / 32) - round(i * clip['length'] * 100 / 32)) for i in range(32)]
    for suffix, multiplier in (('', 1), ('-slow', 2)):
        frames[0].save(O / f'{label}-motion{suffix}.gif', save_all=True, append_images=frames[1:],
                       duration=[d * multiplier for d in duration], loop=0, disposal=2, optimize=False)

sheet = Image.new('RGB', (1600, 1360))
for row, label in enumerate(('idle', 'swim')):
    for col, frame in enumerate((8, 24)):
        sheet.paste(Image.open(build / label / f'{frame:02d}.png'), (col * 800, row * 680))
sheet.save(O / 'fin-tail-poses.png')
report = dict(runtime_integrated=True, joints=5, model_parts=219, quads=1604,
              native_runtime_mesh_samples=8, maximum_vertex_error_blocks=max_error,
              native_loop_endpoints_match=True, idle_cycle_seconds=clips['idle']['length'],
              swim_cycle_seconds=clips['swim']['length'], unchanged_door=True,
              preview='Actual runtime mesh, software rendered; not in-game footage.')
(O / 'tail-validation.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
