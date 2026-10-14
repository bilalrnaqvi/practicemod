import bpy, json
from pathlib import Path

out = Path('/tmp/practicemod-rig-inspection')
out.mkdir(exist_ok=True)
for text in bpy.data.texts:
    (out / (text.name.replace('/', '_') + '.txt')).write_text(text.as_string())
data = {'objects': [], 'actions': list(bpy.data.actions.keys())}
for obj in bpy.data.objects:
    entry = {'name': obj.name, 'type': obj.type, 'location': list(obj.location),
             'scale': list(obj.scale), 'dimensions': list(obj.dimensions)}
    if obj.type == 'ARMATURE':
        entry['bones'] = [{'name': b.name, 'head': list(b.head_local),
                           'tail': list(b.tail_local), 'parent': b.parent.name if b.parent else None,
                           'constraints': [{'name': c.name, 'type': c.type,
                             'target': getattr(getattr(c, 'target', None), 'name', None),
                             'subtarget': getattr(c, 'subtarget', None)}
                              for c in obj.pose.bones[b.name].constraints]}
                          for b in obj.data.bones]
    data['objects'].append(entry)
(out / 'rig.json').write_text(json.dumps(data, indent=2))
print('RIG_INSPECTED', out)
