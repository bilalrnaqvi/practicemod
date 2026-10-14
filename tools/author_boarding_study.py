#!/usr/bin/env python3
"""Create the editable Blockbench ladder study; never overwrite manual work by default.

Native Blockbench IK targets drive fixed-length upper/lower limb groups. The
submarine is copied from the canonical asset, including its embedded textures.
This produces authoring assets only; it does not change runtime boarding.
"""
import argparse
import base64
import copy
import io
import json
import math
from pathlib import Path
import uuid
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'src/main/blockbench/deepseeker_boarding_study.bbmodel'
OUT = ROOT / 'output/animation/boarding'
DURATION = 4.8
ORIGIN = [48.0, 58.5, 0.0]
S = .9375


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'practicemod/boarding-study/' + name))


def ease(t):
    return t*t*t*(10+t*(-15+6*t))


def ramp(t, a, b):
    return ease(max(0, min(1, (t-a)/(b-a))))


def trajectory(start, end, a, b, lift=0, outward=0):
    """Explicit release/swing/plant keys; held keys remain numerically identical."""
    keys = [(0.0, start)]
    if a:
        keys.append((a, start))
    for i in range(1, 17):
        f = i/16
        u = ease(f)
        v = [x+(y-x)*u for x,y in zip(start,end)]
        # A compact swing arc; zero velocity at release and plant.
        arc = math.sin(math.pi*f)**2
        v[0] += outward*arc
        v[1] += lift*arc
        keys.append((a+(b-a)*f, v))
    if b < DURATION:
        keys.append((DURATION, end))
    return keys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--force', action='store_true', help='replace the generated study, discarding manual edits')
    args = parser.parse_args()
    if DEST.exists() and not args.force:
        parser.error(f'{DEST.name} already exists. Edit it in Blockbench; use --force only to regenerate.')
    OUT.mkdir(parents=True, exist_ok=True)
    m = json.loads((ROOT/'src/main/blockbench/deepseeker.bbmodel').read_text())
    m['name'] = 'Deepseeker · boarding motion study'
    m['model_identifier'] = 'deepseeker_boarding_study'
    for g in m['groups']:
        g['locked'] = True
        g['selected'] = False
    for e in m['elements']:
        e['locked'] = True
    m['animations'] = []
    groups = {}

    def group(name, origin, parent=None, rotation=None):
        g = {'name': name, 'uuid': uid(name), 'origin': origin,
             'rotation': rotation or [0,0,0], 'export': True, 'visibility': True,
             'locked': False, 'color': 3, 'children': [], 'isOpen': True}
        m['groups'].append(g)
        node = {'uuid': g['uuid'], 'isOpen': True, 'children': []}
        (groups[parent][1]['children'] if parent else m['outliner']).append(node)
        groups[name] = (g,node)
        return g

    def absolute(x,y,z):
        return [ORIGIN[0]+x*S, ORIGIN[1]+y*S, ORIGIN[2]+z*S]

    # Purpose-made review mannequin. Palette/UV strips make joint twisting legible.
    atlas = Image.new('RGBA',(64,64),(0,0,0,0))
    draw = ImageDraw.Draw(atlas)
    swatches = {'skin':(195,153,115), 'shirt':(52,133,145), 'pants':(51,63,77),
                'boot':(34,40,49), 'hair':(66,49,40), 'eye':(39,51,62)}
    tiles = {}
    for i,(name,col) in enumerate(swatches.items()):
        x = i*10
        draw.rectangle((x,0,x+7,15), fill=col+(255,))
        draw.rectangle((x,12,x+7,15), fill=tuple(round(v*.82) for v in col)+(255,))
        draw.line((x,0,x+7,0), fill=tuple(min(255,round(v*1.1)) for v in col)+(255,))
        tiles[name] = [x,0,x+8,16]
    b=io.BytesIO();atlas.save(b,format='PNG')
    texture_id=len(m['textures'])
    m['textures'].append({'name':'boarding_review_mannequin.png','id':str(texture_id),
        'uuid':uid('texture'), 'width':64,'height':64,'uv_width':64,'uv_height':64,
        'render_mode':'default','render_sides':'auto','visible':True,'internal':True,
        'saved':True,'source':'data:image/png;base64,'+base64.b64encode(b.getvalue()).decode()})
    atlas.save(OUT/'review-mannequin.png')

    def cube(name, lo, hi, parent, material):
        e={'name':name,'uuid':uid(name),'type':'cube','from':absolute(*lo),'to':absolute(*hi),
           'origin':groups[parent][0]['origin'],'rotation':[0,0,0],'box_uv':False,
           'export':True,'visibility':True,'autouv':0,'color':3,
           'faces':{side:{'uv':tiles[material], 'texture':texture_id} for side in ('north','south','east','west','up','down')}}
        m['elements'].append(e);groups[parent][1]['children'].append(e['uuid'])

    group('PLAYER_root',ORIGIN,rotation=[0,90,0])
    group('PLAYER_hips',absolute(0,12,0),'PLAYER_root')
    group('PLAYER_torso',absolute(0,12,0),'PLAYER_hips')
    cube('player_jacket',[-4,12,-2],[4,24,2],'PLAYER_torso','shirt')
    group('PLAYER_head',absolute(0,24,0),'PLAYER_torso')
    cube('player_head',[-4,24,-4],[4,32,4],'PLAYER_head','skin')
    cube('player_hair',[-4.02,30,-4.02],[4.02,32.02,4.02],'PLAYER_head','hair')
    cube('player_left_eye',[1,27,-4.03],[2,28,-4.01],'PLAYER_head','eye')
    cube('player_right_eye',[-2,27,-4.03],[-1,28,-4.01],'PLAYER_head','eye')
    for side,sign in [('left',1),('right',-1)]:
        x=5*sign
        upper=f'PLAYER_{side}_upper_arm'; lower=f'PLAYER_{side}_forearm'; end=f'PLAYER_{side}_hand'
        group(upper,absolute(x,22,0),'PLAYER_torso')
        group(lower,absolute(x,16,0),upper)
        group(end,absolute(x,11,0),lower)
        cube(side+'_sleeve',[x-2,16,-2],[x+2,22,2],upper,'shirt')
        cube(side+'_forearm',[x-2,11,-2],[x+2,16,2],lower,'skin')
        cube(side+'_hand',[x-2,10,-2],[x+2,12,2],end,'skin')
        x=1.9*sign
        upper=f'PLAYER_{side}_thigh';lower=f'PLAYER_{side}_shin';end=f'PLAYER_{side}_foot'
        group(upper,absolute(x,12,0),'PLAYER_hips')
        group(lower,absolute(x,6,0),upper)
        group(end,absolute(x,1.2,0),lower)
        cube(side+'_thigh',[x-2,6,-2],[x+2,12,2],upper,'pants')
        cube(side+'_shin',[x-2,1.2,-2],[x+2,6,2],lower,'pants')
        cube(side+'_boot',[x-2,0,-3],[x+2,2.3,2],end,'boot')

    animation={'uuid':uid('animation'),'name':'animation.deepseeker.ladder_contact_study',
        'loop':'hold','override':True,'length':DURATION,'snapping':40,'selected':True,
        'animators':{}}
    m['animations'].append(animation)
    tracks={}

    def channel(name, kind, keys, animator_type='bone'):
        animator=animation['animators'].setdefault(uid(name), {'name':name,'type':animator_type,
            'rotation_global':False,'quaternion_interpolation':False,'keyframes':[]})
        for time,value in keys:
            animator['keyframes'].append({'uuid':uid(f'{name}/{kind}/{time:.6f}'),
                'channel':kind,'time':round(time,6),'interpolation':'linear','color':-1,
                'data_points':[{axis:str(round(v,6)) for axis,v in zip('xyz',value)}]})
        tracks.setdefault(name,{})[kind]=keys

    # The supporting leg starts the rise; weight arrives after the next foot
    # plants. Lateral shifts load support before the other leg releases.
    root_keys=[];torso_keys=[];head_keys=[]
    for frame in range(97):
        t=frame*.05
        rise=6*ramp(t,1.20,2.05)+6*ramp(t,3.0,4.05)
        z=-.75*ramp(t,.35,.8)+1.5*ramp(t,1.8,2.4)-.75*ramp(t,4.0,4.65)
        x=-.35*ramp(t,.5,1.2)+.35*ramp(t,1.6,2.1)-.35*ramp(t,2.6,3.2)+.35*ramp(t,3.7,4.2)
        root_keys.append((t,[x,rise,z]))
        tilt=-4-5*ramp(t,.6,1.25)+5*ramp(t,1.65,2.15)-5*ramp(t,2.6,3.15)+5*ramp(t,3.65,4.15)
        torso_keys.append((t,[tilt,0,1.5*z]))
        head_keys.append((t,[-8+5*ramp(t,3.9,4.7),0,0]))
    channel('PLAYER_root','position',root_keys)
    channel('PLAYER_torso','rotation',torso_keys)
    channel('PLAYER_head','rotation',head_keys)

    targets={
        'left_hand':trajectory([42,77.5,-5],[42,83.5,-5],.2,.8,lift=1.0,outward=2),
        'right_hand':trajectory([42,83.5,5],[41.6,87.0,8.8],2.0,2.65,lift=.7,outward=1.8),
        'right_foot':trajectory([44.2,60.625,2.4],[44.2,72.625,2.4],.8,1.55,lift=1.6,outward=2.5),
        'left_foot':trajectory([44.2,66.625,-2.4],[44.2,78.625,-2.4],2.65,3.45,lift=1.6,outward=2.5),
    }
    # Left hand follows to the opposite rail after the second weight transfer.
    targets['left_hand']=targets['left_hand'][:-1]+trajectory([42,83.5,-5],[41.6,87,-8.8],4.05,4.65,lift=.7,outward=1.8)[1:]

    for limb,keys in targets.items():
        side,part=limb.split('_')
        source=f'PLAYER_{side}_'+('upper_arm' if part=='hand' else 'thigh')
        pole_name='POLE_'+limb
        pole=[59,ORIGIN[1]+(20 if part=='hand' else 7),(-8 if side=='left' else 8)] if part=='hand' else [37,ORIGIN[1]+7,(-3 if side=='left' else 3)]
        # These stay in hull space: the moving body cannot drag a planted target.
        for name,position,extra in [
            (pole_name,pole,{}),
            ('CONTACT_'+limb,keys[0][1],{'ik_target':uid('PLAYER_'+limb),'ik_source':uid(source),
                                     'ik_pole':uid(pole_name),'lock_ik_target_rotation':True})]:
            element={'name':name,'uuid':uid(name),'type':'null_object','position':position,
                     'export':False,'visibility':True,'locked':False,**extra}
            m['elements'].append(element);m['outliner'].append(element['uuid'])
        base=keys[0][1]
        channel('CONTACT_'+limb,'position',[(t,[v[i]-base[i] for i in range(3)]) for t,v in keys],'null_object')
        channel(pole_name,'position',[(t,[0,v[1],0]) for t,v in root_keys],'null_object')

    # Empty, named locators identify the remaining sequence's real interaction
    # anchors without adding invented vehicle geometry.
    for name,pos in {'HATCH_handle_closed':[25.5,95,-9], 'HATCH_hinge':[15,87,12],
                     'CABIN_ladder_landing':[24,22.5,0], 'PILOT_seat':[0,42,-75]}.items():
        e={'name':'ANCHOR_'+name,'uuid':uid('anchor/'+name),'type':'locator',
           'position':pos,'rotation':[0,0,0],'export':False,'visibility':True,'locked':True}
        m['elements'].append(e);m['outliner'].append(e['uuid'])

    DEST.write_text(json.dumps(m,indent=2)+'\n')
    study={'duration':DURATION,'units':'Blockbench pixels; 16 pixels = 1 block',
        'stage':'authoring study, not runtime-integrated','root_origin':ORIGIN,
        'contacts':{name:[[round(t,6),*[round(x,6) for x in v]] for t,v in keys] for name,keys in targets.items()},
        'beats':[{'time':t,'label':label} for t,label in [(0,'Establish support'),(.2,'Left hand reaches'),
            (.8,'Right foot releases'),(1.55,'Right foot plants'),(2.05,'Weight arrives'),
            (2.65,'Left foot releases'),(3.45,'Left foot plants'),(4.05,'Second weight transfer'),(4.8,'Settle on rails')]],
        'tracks':tracks}
    (OUT/'ladder-study.json').write_text(json.dumps(study,indent=2)+'\n')
    print(f'Created {DEST.relative_to(ROOT)} ({len(m["groups"])} groups, four native IK chains)')


if __name__=='__main__':
    main()
