"""Render inspectable Blender review frames and report camera obstructions."""
import bpy, sys, json
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/animation/submarine-blender'
scene = bpy.context.scene
args = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
frames = list(range(0,145,2)) if 'motion' in args else [0,12,27,39,57,75,99,111,123,144]
scene.render.engine = 'BLENDER_WORKBENCH'
scene.render.resolution_percentage = 75
scene.display.shading.light = 'STUDIO'
scene.display.shading.color_type = 'TEXTURE'
scene.display.shading.show_shadows = True
scene.display.shading.show_cavity = True
scene.display.shading.cavity_type = 'BOTH'
scene.display.shading.background_type = 'WORLD'
scene.world.color = (.06,.08,.10)
scene.render.image_settings.file_format = 'PNG'
report = []
for frame in frames:
    scene.frame_set(frame)
    root = bpy.data.objects['PLAYER — hull-space root']
    target = root.location + Vector((0,0,.45))
    camera = scene.camera
    direction = target-camera.location
    deps = bpy.context.evaluated_depsgraph_get()
    result = scene.ray_cast(deps,camera.location,direction.normalized(),distance=direction.length)
    report.append({'frame':frame,'camera':camera.name,'first_surface':result[4].name if result[0] else None})
    # Explicit cutaway review, retaining every full-hull object in the source file.
    bpy.data.collections['Hull — hide for interior review'].hide_render = frame >= 39
    scene.render.filepath = str(OUT / f'review-{frame:03}.png')
    bpy.ops.render.render(write_still=True)
(OUT / 'camera-review.json').write_text(json.dumps(report,indent=2))
