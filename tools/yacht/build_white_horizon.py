"""Initial authoring builder. Reconstructs the model; --replace required to overwrite edits.
Export edited bbmodel with export_white_horizon.py instead. Requires Pillow.
"""
import argparse,base64,json,math,random,uuid
from pathlib import Path
from PIL import Image,ImageDraw
R=Path(__file__).resolve().parents[2]
D=R/'src/main/blockbench/white_horizon'; D.mkdir(parents=True,exist_ok=True)
P=D/'white_horizon.bbmodel'
ap=argparse.ArgumentParser();ap.add_argument('--replace',action='store_true');ap.add_argument('--structural',action='store_true');args=ap.parse_args()
if P.exists() and not args.replace: raise SystemExit('Existing canonical model: use exporter, or explicitly --replace for reconstruction')
uid=lambda s:str(uuid.uuid5(uuid.NAMESPACE_URL,'practicemod:white_horizon:'+s))
# Original 16px material tiles. No extracted Minecraft textures.
colors={'quartz':(235,235,225),'quartz_shadow':(208,214,210),'wood':(200,173,125),'darkwood':(102,73,47),'frame':(39,49,55),'glass':(72,139,176),'water':(30,151,200),'tile':(148,201,203),'linen':(234,225,202),'navy':(44,78,102),'lamp':(253,202,109),'metal':(76,88,93),'leaf':(74,111,51),'soil':(79,61,43),'screen':(32,71,86),'carpet':(181,195,190),'brass':(180,139,66)}
textures=[]
for i,(mat,c) in enumerate(colors.items()):
 im=Image.new('RGBA',(16,16),c+(255,)); dr=ImageDraw.Draw(im); rng=random.Random(mat)
 if mat in ('glass','water'):
  im=Image.new('RGBA',(16,16),c+((74 if mat=='glass' else 160),));dr=ImageDraw.Draw(im)
  if mat=='glass':
   dr.line((1,1,5,1),fill=(153,211,228,135));dr.line((1,2,1,6),fill=(153,211,228,135))
  else:
   for yy in (3,10):dr.line((2,yy,8,yy),fill=(100,205,231,170))
 elif mat in ('wood','darkwood'):
  for yy in (0,8):dr.line((0,yy,15,yy),fill=tuple(max(0,v-24) for v in c)+(255,))
  for xx,yy in ((3,1),(11,9)):dr.line((xx,yy,xx,yy+6),fill=tuple(max(0,v-14) for v in c)+(255,))
  for _ in range(12):
   x,y=rng.randrange(16),rng.randrange(16); dr.line((x,y,min(15,x+3),y),fill=tuple(min(255,v+8) for v in c)+(255,))
 elif mat=='lamp':
  dr.rectangle((0,0,15,15),outline=(140,100,43,255),width=2);dr.rectangle((4,4,11,11),fill=(255,234,164,255))
 elif mat=='screen':
  dr.rectangle((0,0,15,15),outline=(22,32,40,255),width=2)
  for x in (4,8,12):dr.line((x,4,x,10),fill=(82,167,160,255))
 elif mat=='leaf':
  for _ in range(45):
   x,y=rng.randrange(16),rng.randrange(16);dr.rectangle((x,y,x+1,y+1),fill=(86,125,59,255))
 else:
  for _ in range(14):
   x,y=rng.randrange(16),rng.randrange(16);d=rng.choice([-5,4]);dr.point((x,y),fill=tuple(max(0,min(255,v+d)) for v in c)+(255,))
  if mat in ('quartz','quartz_shadow','tile','metal'):dr.line((0,15,15,15),fill=tuple(max(0,v-10) for v in c)+(255,))
 path=D/'textures'/f'{mat}.png';path.parent.mkdir(exist_ok=True);im.save(path)
 textures.append({'name':path.name,'id':str(i),'uuid':uid('texture/'+mat),'path':'textures/'+path.name,'width':16,'height':16,'uv_width':16,'uv_height':16,'render_mode':'default','render_sides':'double' if mat in ('glass','water') else 'front','source':'data:image/png;base64,'+base64.b64encode(path.read_bytes()).decode()})
mi={m:i for i,m in enumerate(colors)}; elements=[]; groups=[]; out=[]; gn={}; meta={}; routes=[]; anchors=[]; rooms=[]; counter=0; seen=set()

def group(path,origin=None):
 key='/'.join(path)
 if key in gn:return gn[key]
 g={'name':path[-1],'uuid':uid('group/'+key),'origin':origin or [0,0,0],'rotation':[0,0,0],'export':True,'visibility':True,'isOpen':False,'children':[],'color':len(path)%8}
 groups.append(g);n={'uuid':g['uuid'],'isOpen':False,'children':[]};gn[key]=n
 (group(path[:-1])['children'] if len(path)>1 else out).append(n)
 return n

def split(a,b):
 p=a
 while p<b-1e-7:
  q=min(b,math.floor(p+1e-7)+1)
  if q<=p+1e-7:q=min(b,p+1)
  yield p,q;p=q

def box(name,x0,y0,s0,x1,y1,s1,mat='quartz',deck='main',cat='structure',solid=True,shape='block',fixture=None):
 global counter
 if min(x1-x0,y1-y0,s1-s0)<=0:return
 # Canonical origin has half-block X offset to center an odd 17-block beam.
 for a,b in split(x0+.5,x1+.5):
  for c,d in split(y0,y1):
   for e,f in split(s0,s1):
    a0,b0=a-.5,b-.5
    signature=(a0,b0,c,d,e,f,mat)
    if signature in seen:continue
    seen.add(signature);counter+=1
    key=f'{name}/{counter}';ident=uid(key);lo=[a0,c,e-30];hi=[b0,d,f-30]
    dx,dy,dz=[(hi[i]-lo[i])*16 for i in range(3)]
    dims={'north':(dx,dy),'south':(dx,dy),'east':(dz,dy),'west':(dz,dy),'up':(dx,dz),'down':(dx,dz)}
    el={'name':f'{name}_{counter:05}','uuid':ident,'type':'cube','from':[v*16 for v in lo],'to':[v*16 for v in hi],'origin':[0,0,0],'rotation':[0,0,0],'box_uv':False,'autouv':0,'shade':True,'export':True,'visibility':True,'faces':{k:{'uv':[0,0,w,h],'texture':mi[mat]} for k,(w,h) in dims.items()}}
    elements.append(el);path=['WHITE_HORIZON',deck,cat,f'section_{int(e//16):02}']
    if fixture:path.append(fixture)
    group(path)['children'].append(ident)
    meta[ident]={'material':mat,'deck':deck,'category':cat,'solid':solid,'construction':shape,'fixture':fixture,'station':[e,f]}

def route(name,pts):routes.append({'name':name,'feet':[[x,y,s-30] for x,y,s in pts]})
def anchor(name,x,y,s,kind='interaction'):anchors.append({'name':name,'position':[x,y,s-30],'kind':kind})
def in_core(x,s):return (-6<x<-1 and 18<s<26) or (1<x<6 and 42<s<50)
def slab(name,x0,x1,s0,s1,y,deck,cut=None,mat='wood'):
 for a,b in split(x0+.5,x1+.5):
  for c,d in split(s0,s1):
   # Subdivide into .5 blocks around core edges; explicit local shape remains slab.
   for xx in (a-.5,(a+b)/2-.5):
    xe=xx+(b-a)/2
    if not cut or not cut((xx+xe)/2,(c+d)/2):
     trim=deck in ('main','upper','roof') and (xx<x0+.5 or xe>x1-.5 or c<s0+.5 and s1-s0>2 or d>s1-.5 and s1-s0>2)
     box(name,xx,y-.5,c,xe,y,d,'quartz' if trim else mat,deck,shape='slab')
def fence(name,x0,s0,x1,s1,y,deck):
 # Vanilla-like wall/pane proportions; 1.5-high collision on rails.
 if x1-x0>s1-s0:
  for x in range(math.ceil(x0),math.floor(x1)+1):box(name+'_post',x-.125,y,s0-.125,x+.125,y+1.5,s0+.125,'quartz',deck,'rails',shape='fence')
  box(name+'_rail',x0,y+.75,s0-.0625,x1,y+1,s0+.0625,'quartz',deck,'rails',shape='fence')
  box(name+'_glass',x0,y,s0-.0625,x1,y+.75,s0+.0625,'glass',deck,'glazing',shape='pane')
 else:
  for s in range(math.ceil(s0),math.floor(s1)+1):box(name+'_post',x0-.125,y,s-.125,x0+.125,y+1.5,s+.125,'quartz',deck,'rails',shape='fence')
  box(name+'_rail',x0-.0625,y+.75,s0,x0+.0625,y+1,s1,'quartz',deck,'rails',shape='fence')
  box(name+'_glass',x0-.0625,y,s0,x0+.0625,y+.75,s1,'glass',deck,'glazing',shape='pane')

def stair(core,x,s,base,deck):
 # Entry platform at s..s+2, intermediate at s+6..s+8. Return to entry end.
 box(core+'_entry',x,base-.5,s,x+5,base,s+2,'wood',deck,shape='slab')
 for k in range(4):
  box(core+'_out_tread',x,base+k*.5,s+2+k,x+2,base+(k+1)*.5,s+3+k,'wood',deck,shape='stair')
  box(core+'_return_tread',x+3,base+3.5-k*.5,s+2+k,x+5,base+4-k*.5,s+3+k,'wood',deck,shape='stair')
 box(core+'_half_landing',x,base+1.5,s+6,x+5,base+2,s+8,'wood',deck,shape='slab')
 # Central fence between opposed flights; stair-edge rails follow each tread.
 for k in range(4):
  box(core+'_divider',x+2.375,base+.5+k*.5,s+2+k,x+2.625,base+2+k*.5,s+3+k,'quartz',deck,'rails',shape='fence')
 for xx,rev in ((x,False),(x+5,True)):
  for k in range(4):
   yy=base+4-k*.5 if rev else base+(k+1)*.5
   box(core+'_outer_rail',xx-.0625,yy,s+2+k,xx+.0625,yy+1.5,s+3+k,'glass',deck,'rails',shape='pane')
 pts=[(x+1,base,s+1)]+[(x+1,base+(k+1)*.5,s+2.5+k) for k in range(4)]+[(x+1,base+2,s+7),(x+4,base+2,s+7)]+[(x+4,base+2.5+k*.5,s+5.5-k) for k in range(4)]+[(x+4,base+4,s+1)]
 route(f'{core}_{base}_to_{base+4}',pts)

# Structural blockout first: no furniture until circulation validation stage.
# Main deck / angular bow. Hull is stepped blocks, never rotated geometry.
def halfwidth(s):
 return 8.5 if s<50 else 7.5 if s<52 else 6.5 if s<54 else 5.5 if s<56 else 4.5 if s<58 else 2.5 if s<59 else 1.5
for s in range(3,60):
 w=halfwidth(s)
 slab('main_deck',-w,w,s,s+1,0,'main',lambda x,z:in_core(x,z) or (abs(x)<4 and 4<z<12) or ((-6<x<-4 or 4<x<6) and 3<z<7))
 # White hull parapet shell, lower windows in cabin range.
 for side in (-1,1):
  x0,x1=(-w,-w+.5) if side<0 else (w-.5,w)
  for y in range(-5,0):
   m='glass' if 12<=s<50 and y in (-3,-2) and s%8 not in (2,3) else 'quartz'
   box('hull_side',x0,y,s,x1,min(y+1,-.5),s+1,m,'lower','glazing' if m=='glass' else 'hull',shape='block')
 # Waterline plinth narrows in steps under guest deck.
 for y in (-6,-7,-8):
  if 18<=s<50:
   ww=min(w,6.5)
   for side in (-1,1):
    xx=-ww if side<0 else ww-.5
    box('stepped_underbody',xx,y,s,xx+.5,y+1,s+1,'quartz_shadow','machinery','hull',shape='slab')
# Bow step return walls and bottom close the tapered forward hull.
for ss in (50,52,54,56,58,59):
 old,new=halfwidth(ss-1),halfwidth(ss)
 for a,b in ((-old,-new),(new,old)):
  box('bow_step_return',a,-5,ss,b,-.5,ss+.5,'quartz','lower','hull',shape='slab')
box('bow_stem',-1,-5,59.5,1,-.5,60,'quartz','lower','hull',shape='slab')
for ss in range(50,60):
 w=halfwidth(ss);box('bow_bottom',-w+.5,-5,ss,w-.5,-4.5,ss+1,'quartz_shadow','lower','hull',shape='slab')
# Bottom decks, forward/aft transom bulkheads.
slab('lower_floor',-8,8,3,50,-4,'lower',lambda x,s:in_core(x,s))
slab('keel_floor',-6.5,6.5,18,50,-8,'machinery',lambda x,s:-6<x<-1 and 18<s<26,mat='metal')
box('keel',-6.5,-9,18,6.5,-8.5,50,'quartz_shadow','machinery','hull',shape='slab')
for a,b in ((-8,-6),(-4,4),(6,8)):
 box('transom',a,-5,3,b,-.5,3.5,'quartz','lower','hull',shape='slab')
for a,b in ((-6,-4),(4,6)):
 box('transom_step_support',a,-5,3,b,-2,3.5,'quartz','lower','hull',shape='slab')
box('forward_bulkhead',-8,-4,49.5,8,-.5,50,'quartz','lower','structure',shape='slab')
box('machinery_end',-5.5,-8,49.5,5.5,-4.5,50,'quartz_shadow','machinery','structure',shape='slab')
# Swim deck.
slab('swim_platform',-8.5,8.5,0,3,-2,'swim',mat='wood')
for x in (-6,4):
 for k in range(4):box('swim_steps',x,-2+k*.5,3+k,x+2,-1.5+k*.5,4+k,'wood','swim',shape='stair')
 route('swim_to_main_'+str(x),[(x+1,-2,2)]+[(x+1,-1.5+k*.5,3.5+k) for k in range(4)]+[(x+1,0,8)])
# Ladder with Minecraft ladder-like 2/16 rails and rungs.
for x in (-1.5,1.5):
 for xx in (x-.375,x+.375):box('swim_ladder_rail',xx-.0625,-5,0,xx+.0625,-1.5,.125,'darkwood','swim','interaction',shape='ladder')
 for y in [-4.75,-4.25,-3.75,-3.25,-2.75,-2.25]:box('swim_ladder_rung',x-.375,y,0,x+.375,y+.125,.125,'darkwood','swim','interaction',shape='ladder')
# Upper slab, true central void, roof. Stair A ends at upper; B ends roof.
slab('upper_deck',-8,8,12,52,4,'upper',lambda x,s:in_core(x,s) or (-3<x<3 and 26<s<42))
slab('roof_deck',-6.5,6.5,26,50,8,'roof',lambda x,s:1<x<6 and 42<s<50)
# White deck-edge slab tiles provide fascia without overlapping wood geometry.
# Stairs: structural deck holes are identical at all occupied levels.
for base,deck in ((-8,'machinery'),(-4,'lower'),(0,'main')):stair('A',-6,18,base,deck)
for base,deck in ((-4,'lower'),(0,'main'),(4,'upper')):stair('B',1,42,base,deck)
# Close terminal top floor apertures not needed for a higher flight.
box('A_top_landing',-6,3.5,18,-1,4,20,'wood','upper',shape='slab')
box('B_top_landing',1,7.5,42,6,8,44,'wood','roof',shape='slab')
# Rails around open terminal holes; do not cross entrance landings.
for x in (-6,-1):fence('A_upper_opening',x,20,x,26,4,'upper')
fence('A_upper_end',-6,26,-1,26,4,'upper')
for x in (1,6):fence('B_roof_opening',x,44,x,50,8,'roof')
fence('B_roof_end',1,50,6,50,8,'roof')
# Double height salon. Dark frames use full/half block posts; glazing panes 2/16.
for side in (-1,1):
 x=side*5.5
 for s in range(26,42):
  # Two side doors at s=32..34, 3-high opening. Frame every four blocks.
  for y in range(0,8):
   if 32<=s<34 and y<3:continue
   if s in (26,30,34,38,41):
    box('salon_mullion',x-.25,y,s,x+.25,min(y+1,7.5),s+1,'frame','main' if y<4 else 'upper','structure',shape='slab')
   else:box('salon_glass',x-.0625,y,s,x+.0625,min(y+1,7.5),s+1,'glass','main' if y<4 else 'upper','glazing',shape='pane')
 # Gallery rail stays inside void, preserving 2-block floor width x=3..5.
 fence('salon_void_guard',side*2.875,26,side*2.875,42,4,'upper')
# End walls around 3-wide main doors; upper open connections over full crossdeck.
for s in (26,42):
 for x0,x1 in ((-5.5,-1.5),(1.5,5.5)):
  box('salon_end_wall',x0,0,s-.125,x1,3,s+.125,'glass','main','glazing',shape='pane')
 box('salon_end_lintel',-5.5,3,s-.25,5.5,3.5,s+.25,'frame','main',shape='slab')
# Lower corridor and six cabins, doors with open leaf + preserved closed envelope metadata.
doors=[]
def door(name,x,y,s,orientation='x',deck='lower'):
 # Two 1-block leaves, 3 high with hinge pivots; shown open, no arbitrary rotations.
 for side in (-1,1):
  # x wall: opening runs along s. Leaves swing into rooms, along x, at s and s+2.
  ss=s if side<0 else s+2
  xx=x if x>0 else x-1
  fixture=name+('_aft_leaf' if side<0 else '_fore_leaf')
  box(name,xx,y,ss,xx+1,y+3,ss+.1875,'darkwood',deck,'doors',shape='door',fixture=fixture)
  for key,n in gn.items():
   if key.endswith('/'+fixture):
    g=next(g for g in groups if g['uuid']==n['uuid']);g['origin']=[x*16,y*16,(ss-30)*16]
  doors.append({'name':fixture,'pivot':[x,y,ss-30],'axis':'y','open_degrees':90*side,'closed_bounds':[x,y,s-30,x+.1875,y+3,s+2-30]})
for a,b in ((12,18),(26,34),(34,42)):
 for side in (-1,1):
  name=f'guest_{"port" if side<0 else "starboard"}_{a}'
  x0,x1=(-7.5,-1) if side<0 else (1,7.5)
  # Shared corridor-side wall, 2-wide door at a+1..a+3.
  wallx=-1.5 if side<0 else 1
  box(name+'_wall',wallx,-4,a,wallx+.5,-.5,a+1,'quartz','lower',shape='slab')
  box(name+'_wall',wallx,-4,a+3,wallx+.5,-.5,b,'quartz','lower',shape='slab')
  box(name+'_lintel',wallx,-1,a+1,wallx+.5,-.5,a+3,'quartz','lower',shape='slab')
  for ss in (a,b-.5):box(name+'_partition',x0,-4,ss,x1,-.5,ss+.5,'quartz','lower',shape='slab')
  door(name+'_door',-1.5 if side<0 else 1.5,-4,a+1)
  rooms.append({'name':name,'bounds':[x0,-4,a-30,x1,-.5,b-30],'door_center':[side*1.5,-4,a+2-30]})
  route(name+'_entry',[(0,-4,a+2),(side*2.5,-4,a+2),(side*3.5,-4,a+2)])
# Service compartments remain separate from bedrooms and center corridor.
for name,xx,aa,bb,ds in [('linen',1,18,26,21),('storage',-1.5,42,50,44)]:
 for a,b in ((aa,ds),(ds+2,bb)):
  box(name+'_partition',xx,-4,a,xx+.5,-.5,b,'quartz','lower',shape='slab')
 box(name+'_lintel',xx,-1,ds,xx+.5,-.5,ds+2,'quartz','lower',shape='slab')
 door(name+'_service_door',1.5 if xx>0 else -1.5,-4,ds)
 route(name+'_access',[(0,-4,ds+1),(3 if xx>0 else -3,-4,ds+1)])
# Upper end of pool services is a bulkhead with 2-block opening to center corridor.
for a,b in ((-8,-1),(1,8)):
 box('pool_service_wall',a,-4,11.5,b,-.5,12,'quartz','lower',shape='slab')
# Pool proper: recess excludes deck, basin holds translucent visual volume only.
box('pool_floor',-3.5,-2,4,3.5,-1.5,12,'tile','main','pool',shape='slab')
for x in (-4,3.5):box('pool_coping',x,-1.5,4,x+.5,0,12,'quartz','main','pool',shape='slab')
box('infinity_glass',-3.5,-1.5,3.875,3.5,0,4,'glass','main','pool',shape='pane')
box('return_gutter',-3.5,-1,3.5,3.5,-.5,3.875,'tile','main','pool',shape='slab')
box('pool_water',-3.5,-1.5,4,3.5,-.25,11,'water','main','water',False,'fluid')
for i in range(3):box('pool_exit_step',-1,-1.5+i*.5,9+i,1,-1+i*.5,10+i,'tile','main','pool',shape='stair')
# Bridge side/front glass, solid white band. Aft entrance open x=-1..1.
for x in (-5.5,):
 box('bridge_sill',x,4,42,x+.5,5,50,'quartz','upper',shape='slab')
 box('bridge_glass',x,5,42,x+.125,7,50,'glass','upper','glazing',shape='pane')
box('bridge_front_sill',-5.5,4,50,1,5,50.5,'quartz','upper',shape='slab')
box('bridge_front_glass',-5.5,5,50,1,7,50.125,'glass','upper','glazing',shape='pane')
box('bridge_front_roof',-6.5,7.5,50,2,8,52,'quartz','roof',shape='slab')
# Main side rails bypass stair core using the full 17-block beam.
for side in (-1,1):
 fence('main_edge',side*8.375,7,side*8.375,50,0,'main')
 fence('terrace_edge',side*7.75,12,side*7.75,52,4,'upper')
 fence('upper_forward_edge',side*7.75,42,side*7.75,52,4,'upper')
 fence('roof_edge',side*6.25,26,side*6.25,50,8,'roof')
fence('terrace_stern_guard',-7.75,12,7.75,12,4,'upper')
fence('upper_bow_guard',-7.75,52,7.75,52,4,'upper')
fence('roof_stern_guard',-6.25,26,6.25,26,8,'roof')
fence('roof_bow_guard',-6.25,50,6.25,50,8,'roof')
for s in range(50,58):
 w=halfwidth(s)-.5
 for side in (-1,1):fence('bow_stepped_guard',side*w,s,side*w,s+1,0,'main')
fence('bow_tip_guard',-4,58,4,58,0,'main')
# Main routes used in both graybox and detailed validation.
route('lower_corridor',[(0,-4,s) for s in range(13,50)])
route('main_center',[(0,0,s) for s in range(12,57)])
for x in (-6.875,6.875):route('main_promenade_'+str(x),[(x,0,s) for s in range(8,50)]+[(math.copysign(6.5,x),0,49),(math.copysign(6.5,x),0,51),(0,0,51)])
for x in (-4,4):route('upper_gallery_'+str(x),[(x,4,s) for s in range(27,43)])
route('upper_cross_entrance',[(x,4,27) for x in range(-4,5)]) if False else None
# Crosswalks just outside salon void; avoid A half landing by approaching from starboard.
route('upper_a_to_gallery',[(-2,4,19),(0,4,19),(0,4,25),(4,4,25),(4,4,27)])
route('upper_gallery_to_bridge',[(4,4,41),(4,4,43),(0,4,43),(-2,4,43)])
route('roof_b_arrival',[(5,8,43),(0,8,43),(0,8,40),(0,8,28)])
anchor('captain_standing',-2.5,4,47,'actor');anchor('captain_interaction',-.5,4,47)
anchor('boarding_port',-5,-2,2,'boarding');anchor('boarding_starboard',5,-2,2,'boarding')
anchor('vessel_origin',0,0,30,'root');anchor('machinery_service',0,-8,28)
# Furnishings are Minecraft slab / stair / fence / trapdoor forms, no smooth meshes.
def sofa(name,x,y,s,length,deck,face=1):
 # Minecraft stair seat: half-block seat + half-depth back, trapdoor-like armrests.
 box(name+'_seat',x,y,s,x+length,y+.5,s+1,'linen',deck,'furniture',shape='stair')
 zz=s if face>0 else s+.5
 box(name+'_back',x,y+.5,zz,x+length,y+1,zz+.5,'linen',deck,'furniture',shape='stair')
 for xx in (x-.1875,x+length):box(name+'_arm',xx,y,s,xx+.1875,y+1,s+1,'darkwood',deck,'furniture',shape='trapdoor')
 anchor(name+'_sit',x+length/2,y+.5,s+.75 if face>0 else s+.25,'seat')
 front=s+1.5 if face>0 else s-.5
 route(name+'_'+str(x)+'_'+str(s)+'_access',[(0,y,front),(x+length/2,y,front)])
def table(name,x,y,s,w,l,deck):
 box(name+'_top',x,y+.875,s,x+w,y+1.0625,s+l,'wood',deck,'furniture',shape='trapdoor')
 for xx in (x+.125,x+w-.375):
  for zz in (s+.125,s+l-.375):box(name+'_leg',xx,y,zz,xx+.25,y+.875,zz+.25,'darkwood',deck,'furniture',shape='fence')
def plant(x,y,s,deck):
 box('planter',x,y,s,x+1,y+.5,s+1,'quartz_shadow',deck,'furniture',shape='slab')
 box('leaves',x,y+.5,s,x+1,y+1.5,s+1,'leaf',deck,'furniture',shape='leaves')
def lamp(x,y,s,deck):
 box('warm_lantern',x,y,s,x+.5,y+.5,s+.5,'lamp',deck,'lighting',True,'lantern')
 if deck in ('upper','roof'):
  floor=4 if deck=='upper' else 8
  box('lantern_pedestal',x+.125,floor,s+.125,x+.375,y,s+.375,'quartz',deck,'lighting',shape='wall')
 elif deck=='main':box('lantern_hanger',x+.1875,y+.5,s+.1875,x+.3125,3.5,s+.3125,'frame',deck,'lighting',shape='fence')
if not args.structural:
 # Beach bar and lounge; core A occupies aft-port stair zone.
 box('beach_bar_base',-4.5,0,13,-3,1,17,'darkwood','main','furniture')
 box('beach_bar_counter',-4.5,1,13,-2.5,1.1875,17,'quartz','main','furniture',shape='trapdoor')
 for s in (13.5,15.5):box('bar_stool',-1.5,0,s,-.5,.5,s+1,'darkwood','main','furniture',shape='slab')
 sofa('beach_sofa',2,0,14,2,'main');table('beach_table',2,0,16,2,1,'main')
 table('main_dining',2,0,21,2,2,'main')
 sofa('dining_bench_a',2,0,19,2,'main');sofa('dining_bench_b',2,0,24,2,'main',-1)
 # Salon: perimeter sofas face inward, long unbroken middle lane.
 for s in (29,36):
  sofa('salon_port',-4.5,0,s,2,'main');sofa('salon_starboard',2.5,0,s,2,'main')
  table('salon_coffee_port',-4.5,0,s+2,2,1,'main');table('salon_coffee_starboard',2.5,0,s+2,2,1,'main')
 box('salon_rug',-1.5,0,29,1.5,.0625,39,'carpet','main','furniture',True,'carpet')
 for x in (-4.5,3.5):plant(x,0,40,'main')
 # Foyer and forward lounge are clear of center and B stair.
 sofa('quiet_lounge',-4.5,0,45,2,'main');table('quiet_table',-4.5,0,47,2,1,'main')
 for x in (-4,2):sofa('bow_sunseat',x,0,53,2,'main')
 # Upper dining under open sky and aft bar.
 table('terrace_dining',1.5,4,16,3,4,'upper')
 for s in (14,21):sofa('terrace_bench',1.5,4,s,3,'upper',1 if s==14 else -1)
 box('terrace_bar',-5.5,4,14,-2.5,5,15,'darkwood','upper','furniture')
 box('terrace_bar_top',-5.5,5,14,-2.5,5.1875,15.5,'quartz','upper','furniture',shape='trapdoor')
 # Bridge console at forward end, captain placeholder intentionally absent.
 box('bridge_console',-4.5,4,48,-1,5,49,'darkwood','upper','furniture')
 box('bridge_screen',-4.5,5,48.75,-1,5.5,49,'screen','upper','interaction',shape='trapdoor')
 anchor('navigation_console',-2.5,4,47,'interaction')
 # Rooftop symmetrical retreats leave central 2-block aisle.
 for x in (-4.5,2.5):
  for s in (29,33):
   box('sunbed_base',x,8,s,x+2,8.25,s+2,'darkwood','roof','furniture',shape='slab')
   box('sunbed_cushion',x,8.25,s,x+2,8.5625,s+2,'linen','roof','furniture',shape='slab')
   box('sunbed_pillow',x,8.5625,s,x+2,8.625,s+.5,'navy','roof','furniture',shape='slab')
  sofa('roof_lounge',x,8,38,2,'roof');table('roof_table',x,8,40,2,1,'roof')
 # Canopy half slabs + full fence pillars, 2.5+ headroom.
 box('roof_canopy',-5.5,11,36,5.5,11.5,42,'quartz','roof','structure',shape='slab')
 for x in (-5,5):
  for s in (36,41):box('canopy_pier',x-.125,8,s,x+.125,11,s+.25,'darkwood','roof','structure',shape='fence')
 box('mast_base',-4,8,48,-3,10,49,'frame','roof','structure')
 box('mast',-3.75,10,48.25,-3.25,14,48.75,'frame','roof','structure',shape='wall')
 box('mast_crossarm',-4.5,12,48.25,-2.5,12.5,48.75,'frame','roof','structure',shape='slab')
 # Six guest suites. Beds aft-facing; 2-wide door aisle at a+2 never obstructed.
 for a,b in ((12,18),(26,34),(34,42)):
  for side in (-1,1):
   x=-6.5 if side<0 else 4.5
   box('guest_bed_base',x,-4,a+3.5,x+2,-3.75,a+5.5,'darkwood','lower','furniture',shape='slab')
   box('guest_mattress',x,-3.75,a+3.5,x+2,-3.4375,a+5.5,'linen','lower','furniture',shape='slab')
   box('guest_pillow',x,-3.4375,a+5,x+2,-3.375,a+5.5,'navy','lower','furniture',shape='slab')
   box('guest_wardrobe',x,-4,a+.5,x+2,-2,a+1.5,'darkwood','lower','furniture')
   lamp(x,-2,a+.75,'lower')
   # Compact washstand, recognizable block basin; no fake inaccessible bathroom.
   xx=-3 if side<0 else 2
   box('washstand',xx,-4,b-1.5,xx+1,-3,b-.5,'quartz_shadow','lower','furniture')
   box('basin',xx,-3,b-1.5,xx+1,-2.75,b-.5,'tile','lower','furniture',shape='slab')
   anchor(f'guest_bed_{side}_{a}',x+1,-4,a+3,'bed')
   route(f'guest_bed_{side}_{a}_access',[(0,-4,a+2),(side*3.5,-4,a+2),(x+1,-4,a+2),(x+1,-4,a+3)])
 # Service rooms: linen starboard of A, stores port of B; machinery separated below.
 for s in (20,22,24):
  box('linen_cabinet',4,-4,s,6,-2,s+1,'quartz_shadow','lower','furniture')
  box('linen_front',3.8125,-4,s,4,-2,s+1,'wood','lower','furniture',shape='trapdoor')
 for s in (44,46,48):
  box('storage_case',-6,-4,s,-4,-3,s+1,'darkwood','lower','furniture')
  box('storage_lid',-6,-3,s,-4,-2.8125,s+1,'wood','lower','furniture',shape='trapdoor')
  anchor('storage_'+str(s),-3,-4,s+.5)
 for x in (-4,2):
  for s in (28,32,36,40):
   box('engine_bank',x,-8,s,x+2,-6,s+2,'metal','machinery','furniture')
   box('engine_access',x,-6,s,x+2,-5.5,s+2,'quartz_shadow','machinery','furniture',shape='slab')
 for s in (29,35,41):lamp(0,-5,s,'machinery')
 # Pool pumps under basin at aft end, service reach through lower corridor.
 for x in (-4,2):box('pool_pump',x,-4,8,x+2,-3,10,'metal','lower','furniture')
 for deck,y,ss in (('main',0,[12,26,40,50]),('upper',4,[13,25,43]),('roof',8,[28,37])):
  for s in ss:
   for x in (-7,6) if deck!='roof' else (-5.5,5):
    if deck=='main' and s in (26,40):continue # promenades stay unencumbered
    if deck=='main':continue
    lamp(x,y+1.5,s,deck)
 for s in (27,35,41):
  for x in (-5,4.5):lamp(x,2.5,s,'main')
 for s in (13,27,35,43):lamp(-.25,-1,s,'lower')
 for x in (-6,5):plant(x,4,12.5,'upper')
 for x in (-5.5,4.5):plant(x,8,26.5,'roof')
# Normalized block joins: trim coincident solid intersections, avoiding z-fighting.
# This does not add detail or change a silhouette; it joins axis-aligned block pieces.
from collections import defaultdict
occupied=defaultdict(list);clean=[];replacement={};trimmed=0
def keys(a,b):
 return [(x,y,z) for x in range(math.floor(a[0]/16),math.ceil(b[0]/16)) for y in range(math.floor(a[1]/16),math.ceil(b[1]/16)) for z in range(math.floor(a[2]/16),math.ceil(b[2]/16))]
def subtract(a,b,c,d):
 lo=[max(a[i],c[i]) for i in range(3)];hi=[min(b[i],d[i]) for i in range(3)]
 if any(lo[i]>=hi[i]-1e-7 for i in range(3)):return [(a,b)]
 result=[];aa=a[:];bb=b[:]
 for axis in range(3):
  if aa[axis]<lo[axis]:
   q=bb[:];q[axis]=lo[axis];result.append((aa[:],q));aa[axis]=lo[axis]
  if bb[axis]>hi[axis]:
   q=aa[:];q[axis]=hi[axis];result.append((q,bb[:]));bb[axis]=hi[axis]
 return result
for el in elements:
 ident=el['uuid'];info=meta[ident];parts=[(el['from'],el['to'])]
 if info['solid']:
  near={j for k in keys(el['from'],el['to']) for j in occupied[k]}
  for j in sorted(near):
   old=clean[j];parts=[p for a,b in parts for p in subtract(a,b,old['from'],old['to'])]
   if not parts:break
 replacement[ident]=[]
 for j,(a,b) in enumerate(parts):
  q=dict(el);q['from']=a;q['to']=b
  if (a,b)!=(el['from'],el['to']):
   trimmed+=1;q['uuid']=uid(ident+'/join/'+str(j));q['name']=el['name']+'_join'+str(j)
   dx,dy,dz=[b[i]-a[i] for i in range(3)]
   dims={'north':(dx,dy),'south':(dx,dy),'east':(dz,dy),'west':(dz,dy),'up':(dx,dz),'down':(dx,dz)}
   q['faces']={k:{'uv':[0,0,w,h],'texture':mi[info['material']]} for k,(w,h) in dims.items()}
  meta[q['uuid']]=info;replacement[ident].append(q['uuid']);idx=len(clean);clean.append(q)
  if info['solid']:
   for k in keys(a,b):occupied[k].append(idx)
def replace_refs(nodes):
 new=[]
 for n in nodes:
  if isinstance(n,str):new.extend(replacement[n])
  else:n['children']=replace_refs(n['children']);new.append(n)
 return new
out=replace_refs(out);elements=clean;meta={e['uuid']:meta[e['uuid']] for e in elements}
print('Normalized joined block fragments:',trimmed)
# Fixtures and route additions usable in both phases.
route('machinery_center',[(0,-8,s) for s in range(19,44)])
route('lower_to_A',[(0,-4,19),(-5,-4,19)])
route('main_to_A',[(0,0,19),(-5,0,19)])
route('lower_to_B',[(0,-4,43),(2,-4,43)])
route('main_to_B',[(0,0,43),(2,0,43)])
route('upper_to_B',[(0,4,43),(2,4,43)])
route('main_bar_access',[(0,0,17.5),(-2,0,17.5),(-2,0,14)])
route('pool_exit',[ (0,-1.5,8.5),(0,-1,9.5),(0,-.5,10.5),(0,0,11.5),(0,0,12.5)])
route('captain_reach',[(0,4,43),(0,4,47),(-2.5,4,47)])
layout={'schema':1,'units':'blocks','blockbench_units_per_block':16,'axes':{'x':'starboard','y':'up','z':'bow'},'station_origin_z':-30,'waterline_y':-5,'phase':'structural' if args.structural else 'furnished','overall_length':60,'beam':17,'deck_y':{'machinery':-8,'lower':-4,'swim':-2,'main':0,'upper':4,'roof':8},'player':{'width':.6,'height':1.8,'eye_height':1.62,'step_height':.6},'anchors':anchors,'doors':doors,'routes':routes,'rooms':rooms,'dry_volume_proposals':[[-7.5,-4,12-30,7.5,-.5,50-30],[-5,-8,26-30,5,-4.5,42-30]],'water_volume':[-3.5,-1.5,4-30,3.5,-.25,11-30],'runtime_status':'not integrated; collision metadata is static local geometry, not tested moving collision'}
model={'meta':{'format_version':'5.0','model_format':'free','box_uv':False},'name':'White Horizon','model_identifier':'white_horizon','resolution':{'width':16,'height':16},'elements':elements,'groups':groups,'outliner':out,'textures':textures,'animations':[],'practicemod':{'layout':layout,'elements':meta,'authoring':'build_white_horizon.py is a reconstruction tool; export manual edits with export_white_horizon.py'}}
P.write_text(json.dumps(model,separators=(',',':')))
print(f'Wrote {P}: {len(elements)} cubes, {len(groups)} groups, phase={layout["phase"]}')
