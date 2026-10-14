#!/usr/bin/env python3
"""One-time enlargement of the fitted CRAG into a two-player side-by-side buggy."""
import copy
import json
import math
import uuid
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root/'src/main/blockbench/crag.bbmodel'
m = json.loads(path.read_text())
assert m.get('crag_proportions') == 'steve-fit-v1', 'Requires the single-seat fitted model; do not apply twice.'
groups = {g['uuid']:g for g in m['groups']}
elements = {e['uuid']:e for e in m['elements']}
def uid(name): return str(uuid.uuid5(uuid.NAMESPACE_URL,'practicemod:crag/two-seat/'+name))
def transform(e, scale=(1,1,1), offset=(0,0,0), pivot=(0,0,0)):
    for key in ('from','to','origin'):
        e[key]=[round((v-pivot[i])*scale[i]+pivot[i]+offset[i],8) for i,v in enumerate(e[key])]

def beam(e,a,b,width=1.8):
    mid=[(a[i]+b[i])/2 for i in range(3)]
    dy,dz=b[1]-a[1],b[2]-a[2]; length=math.hypot(dy,dz)
    e['origin']=mid;e['rotation']=[math.degrees(math.atan2(dz,dy)),0,0]
    e['from']=[mid[0]-width/2,mid[1]-length/2,mid[2]-width/2]
    e['to']=[mid[0]+width/2,mid[1]+length/2,mid[2]+width/2]

seat_node = None
for node in m['outliner']:
    g=groups[node['uuid']]; part=g['name']
    if part=='bucket_seat':
        seat_node=copy.deepcopy(node)
        continue
    for id in node['children']:
        e=elements[id]; name=e['name']
        if part.startswith('wheel_'):
            old=g['origin']; new=[math.copysign(16.8,old[0]),old[1]*1.12,math.copysign(17.1,old[2])]
            transform(e,(1.12,1.12,1.12),[new[i]-old[i] for i in range(3)],old)
        elif part=='steering':
            transform(e,offset=(-8.96,1.28,-1.92))
        elif part=='roll_cage':
            if name in ('roof_front','roof_rear'):
                transform(e,(2.665,1,1.25),(0,1.28,0))
            else:
                side=math.copysign(1,(e['from'][0]+e['to'][0])/2)
                transform(e,(1,1,1.25),(side*10.88,1.28,0))
                x=side*17.416
                if name.startswith('front_pillar'): beam(e,(x,18.36,11.7),(x,37.08,6.625))
                elif name.startswith('rear_pillar'): beam(e,(x,17.06,-17.575),(x,37.08,-13.125))
                elif name.startswith('roof_rail'): beam(e,(x,37.08,-13.125),(x,37.08,6.625))
        elif name.startswith('footwell'):
            # Rebuild the paired footrests below, without stretching the rider fit.
            continue
        else:
            sx=2.8 if name.startswith(('hood','nose','dashboard','instrument','gauge','warning')) else 1.65
            transform(e,(sx,1,1.25),(0,1.28,0))
            if name.startswith('rock_slider'):
                transform(e,offset=(math.copysign(5.6,e['from'][0]),0,0))
            if name=='floor': e['from'][0],e['to'][0]=-16.8,16.8
            if name.startswith('axle'):
                side=math.copysign(1,(e['from'][0]+e['to'][0])/2)
                e['from'][0],e['to'][0]=sorted((0,side*16.8))
                delta=7.1568-(e['from'][1]+e['to'][1])/2
                transform(e,offset=(0,delta,0))
    if part.startswith('wheel_'):
        g['origin']=[math.copysign(16.8,g['origin'][0]),g['origin'][1]*1.12,math.copysign(17.1,g['origin'][2])]
    elif part=='steering': g['origin']=[-8.96,21.28,2.56]

# Preserve the fitted seat and leg geometry, duplicating it for each occupant.
old_seats=[elements[id] for id in seat_node['children']]
seat_ids={e['uuid'] for e in old_seats}
m['elements']=[e for e in m['elements'] if e['uuid'] not in seat_ids]
m['outliner']=[n for n in m['outliner'] if n['uuid']!=seat_node['uuid']]
m['groups']=[g for g in m['groups'] if g['uuid']!=seat_node['uuid']]
for role,x in [('driver',-8.96),('passenger',8.96)]:
    g=dict(name=role+'_seat',uuid=uid(role+'_seat'),origin=[x,0,0],rotation=[0,0,0],export=True,visibility=True)
    node=dict(uuid=g['uuid'],isOpen=False,children=[])
    for original in old_seats:
        e=copy.deepcopy(original);e['name']=role+'_'+e['name'];e['uuid']=uid(e['name'])
        transform(e,offset=(x,1.28,-1.92));m['elements'].append(e);node['children'].append(e['uuid'])
    m['groups'].append(g);m['outliner'].append(node)

chassis=next(n for n in m['outliner'] if groups.get(n['uuid'],{}).get('name')=='chassis')
old_pads=[e for e in m['elements'] if e['name'].startswith('footwell')]
for e in old_pads:
    chassis['children'].remove(e['uuid']);m['elements'].remove(e)
for role,x in [('driver',-8.96),('passenger',8.96)]:
    for original in old_pads:
        e=copy.deepcopy(original);e['name']=role+'_'+e['name'];e['uuid']=uid(e['name'])
        transform(e,offset=(x,1.28,-1.92));m['elements'].append(e);chassis['children'].append(e['uuid'])

# A fixed passenger grab rail, with two mounts on the enlarged dash.
material=next(e for e in m['elements'] if e['name']=='steering_left')
for name,lo,hi in [
    ('passenger_grab_bar',[4.8,20.6,2.5],[13.12,21.4,3.3]),
    ('passenger_grab_mount_left',[5.0,17.8,2.8],[5.8,20.9,4.5]),
    ('passenger_grab_mount_right',[12.1,17.8,2.8],[12.9,20.9,4.5]),
    ('driver_steering_column',[-9.6,17.8,3.0],[-8.3,21.0,4.8])]:
    e=copy.deepcopy(material);e.update(name=name,uuid=uid(name),origin=[0,0,0],rotation=[0,0,0],to=hi)
    e['from']=lo;m['elements'].append(e);chassis['children'].append(e['uuid'])
m['crag_proportions']='two-seat-v1'
path.write_text(json.dumps(m,indent=2)+'\n')
print('Expanded CRAG with two fitted bucket seats and passenger grab rail.')
