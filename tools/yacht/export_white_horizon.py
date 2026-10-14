"""Export the existing editable model. Never reconstructs or modifies source geometry."""
import base64,json,hashlib
from pathlib import Path
R=Path(__file__).resolve().parents[2];P=R/'src/main/blockbench/white_horizon/white_horizon.bbmodel';D=R/'src/main/yacht/white_horizon';E=D/'exports';E.mkdir(parents=True,exist_ok=True)
m=json.loads(P.read_text());props=m['practicemod'];scene=[]
assert all(g.get('rotation',[0,0,0])==[0,0,0] for g in m['groups']), 'Export open construction pose; rotated groups need baking first'
for e in m['elements']:
 info=props['elements'][e['uuid']]
 if e.get('rotation',[0,0,0])!=[0,0,0]:raise ValueError('Non-axis aligned element: '+e['name'])
 scene.append({'id':e['uuid'],'name':e['name'],'min':[x/16 for x in e['from']],'max':[x/16 for x in e['to']],**info,'faces':e['faces']})
materials=[]
for t in m['textures']:
 name=t['name'].removesuffix('.png');data=base64.b64decode(t['source'].split(',')[1]);dest=E/'textures'/t['name'];dest.parent.mkdir(exist_ok=True);dest.write_bytes(data)
 # Embedded pixels are canonical; mirror external editable texture files on export.
 (P.parent/'textures'/t['name']).write_bytes(data)
 materials.append({'name':name,'path':'textures/'+t['name']})
manifest={'schema':1,'source_sha256':hashlib.sha256(P.read_bytes()).hexdigest(),'coordinate_system':'x starboard, y up, z bow; blocks; origin at main midship','materials':materials,'elements':scene}
(E/'scene.json').write_text(json.dumps(manifest,separators=(',',':')))
(D/'white_horizon.layout.json').write_text(json.dumps(props['layout'],indent=2)+'\n')
coll=[{k:e[k] for k in ('id','name','min','max','deck','category','construction','fixture')} for e in scene if e['solid']]
(D/'white_horizon.collision.json').write_text(json.dumps({'schema':1,'source_sha256':manifest['source_sha256'],'units':'blocks','space':'vessel-local','mode':'open-door static review pose','hollow_interior':True,'runtime_integrated':False,'boxes':coll},separators=(',',':')))
# Explicit per-section bounds; never reuse these envelopes as solid hull collision.
sections={}
for e in scene:
 k=e['deck']+'/'+str(int((e['min'][2]+30)//16))
 if k not in sections:sections[k]={'min':e['min'][:],'max':e['max'][:],'elements':0}
 b=sections[k];b['min']=[min(a,c) for a,c in zip(b['min'],e['min'])];b['max']=[max(a,c) for a,c in zip(b['max'],e['max'])];b['elements']+=1
(D/'white_horizon.sections.json').write_text(json.dumps({'warning':'render/broadphase bounds only, not solid boxes','sections':sections},indent=2)+'\n')
# Portable OBJ cuboid geometry with per-face UV and external material tiles. Quads only.
faces=[('north',(0,3,2,1)),('south',(4,5,6,7)),('west',(0,4,7,3)),('east',(1,2,6,5)),('down',(0,1,5,4)),('up',(3,7,6,2))]
with (E/'white_horizon.obj').open('w') as out:
 out.write('# Blocks; X starboard / Y up / Z bow; static open-door pose\nmtllib white_horizon.mtl\n');vi=ti=1
 for e in scene:
  a,b=e['min'],e['max'];x,y,z=a;X,Y,Z=b;verts=[(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]
  out.write('o '+e['name']+'\nusemtl '+e['material']+'\n')
  for v in verts:out.write('v '+' '.join(map(str,v))+'\n')
  for face,inds in faces:
   u,v,U,V=e['faces'][face]['uv'];coords=[(u/16,1-V/16),(U/16,1-V/16),(U/16,1-v/16),(u/16,1-v/16)]
   for uv in coords:out.write('vt '+' '.join(map(str,uv))+'\n')
   out.write('f '+' '.join(f'{vi+k}/{ti+j}' for j,k in enumerate(inds))+'\n');ti+=4
  vi+=8
with (E/'white_horizon.mtl').open('w') as f:
 for mat in materials:
  f.write(f'newmtl {mat["name"]}\nKd 1 1 1\nmap_Kd {mat["path"]}\n')
  if mat['name'] in ('glass','water'):f.write('d '+('.28' if mat['name']=='glass' else '.65')+'\n')
print(f'Exported {len(scene)} cuboids; {len(coll)} local solid shapes; {len(sections)} render sections')
