"""Hand-authored pixel materials for the native Blockbench cabin, using only stdlib."""
import math, random, struct, zlib
COLORS={'ochre':(196,132,29),'dark':(42,51,55),'steel':(113,128,130),'teal':(27,88,93),'cream':(209,204,175),'red':(164,52,34),'olive':(87,99,57),'lamp':(255,211,108),'cyan':(64,191,207),'map':(199,184,131),'screen':(12,32,42),'grate':(65,76,79),'panel':(183,123,31),'rubber':(26,32,34),'canvas':(35,99,102),'paper':(206,197,159),'glass':(20,80,112),'gauge':(212,212,186),'drawer':(180,116,25),'locker':(180,116,25),'medical':(164,52,34),'conduit':(92,106,108),'case_gray':(64,76,79),'case_olive':(89,104,54),'case_shell':(83,89,86),'case_green':(86,98,48)}
SIZE=512; TILE=64

def atlas():
 p=bytearray(SIZE*SIZE*4)
 def pixel(ox,oy,x,y,c):
  if 0<=x<64 and 0<=y<64:
   at=((oy+y)*SIZE+ox+x)*4;p[at:at+4]=bytes((*[max(0,min(255,int(v))) for v in c],255))
 for i,(name,base) in enumerate(COLORS.items()):
  ox=i%8*64;oy=i//8*64;rng=random.Random(291+i)
  for y in range(64):
   for x in range(64):
    # Quiet clustered paint/powder-coat variation, never noisy speckle.
    variation=((x//4*17+y//3*23+i*7)%11-5)*.7
    edge=min(x,y,63-x,63-y)
    d=variation+(13 if edge==2 else -19 if edge<2 else -5 if edge==3 else 0)
    c=tuple(v+d for v in base)
    if name in ('teal','canvas'):
     d=((y//7)%2)*5+((x//3+y//3)%3-1)*2
     if y%16 in (0,1):d-=10
     if x in (3,60):d+=15
     c=tuple(v+d for v in base)
    if name=='cream':
     d=9 if 5<x<58 and 5<y<58 else -9;c=tuple(v+d for v in base)
     if y in (4,59) and x%3:c=(172,170,145)
    if name=='grate':
     c=(29,36,39) if x%10 in range(3,8) and y%16 in range(4,13) else (77,88,91)
     if y%16==3:c=(106,115,112)
     if edge<2:c=(34,42,45)
    if name=='panel':
     if x in (4,59) or y in (4,59):c=(99,70,27)
     if x in (5,58) or y in (5,58):c=(218,152,44)
    if name=='drawer':
     if y in (6,55) or x in (4,59):c=(90,67,31)
     if y in (7,56):c=(228,164,58)
     if 11<=x<=27 and 45<=y<=49:c=(217,197,142)
    if name=='lamp':
     c=(255,226,144) if edge>8 else (220,147,41)
     if x%12<2:c=tuple(v-8 for v in c)
    if name=='glass':
     c=(24+(y//8)*2,72+(y//8)*3,104+(y//8)*3)
     if 8<(x+y)%65<13:c=(56,126,155)
     if edge<3:c=(11,42,57)
    pixel(ox,oy,x,y,c)
  def rect(x,y,w,h,c):
   for yy in range(y,y+h):
    for xx in range(x,x+w):pixel(ox,oy,xx,yy,c)
  def line(x0,y0,x1,y1,c):
   n=max(abs(x1-x0),abs(y1-y0),1)
   for k in range(n+1):pixel(ox,oy,round(x0+(x1-x0)*k/n),round(y0+(y1-y0)*k/n),c)
  if name in ('ochre','panel','drawer','dark','steel','olive','red'):
   for x,y in [(5,5),(56,5),(5,56),(56,56)]:
    rect(x,y,3,3,(30,37,39));rect(x,y,2,1,(133,144,140))
   for j in range(9):
    x=rng.randrange(7,56);y=rng.choice([rng.randrange(3,7),rng.randrange(56,60)])
    rect(x,y,rng.randrange(1,4),1,tuple(v+22 for v in base))
  if name=='map':
   # Coastlines, bathymetric contours, grid, compass and a planned route.
   for y in range(3,61):
    for x in range(3,61):
     a=((x-19)/13)**2+((y-26)/22)**2+.18*math.sin(y*.45)+.13*math.cos(x*.65)
     b=((x-50)/9)**2+((y-47)/11)**2+.22*math.sin(y*.7)
     q=min(a,b)
     c=(78,125,117) if q<.65 else (123,153,127) if q<1 else (165,177,138) if q<1.25 else (208,195,150)
     if 1.35<q<1.43 or 1.85<q<1.94:c=(148,161,133)
     if x%10==0 or y%10==0:c=tuple(v-12 for v in c)
     pixel(ox,oy,x,y,c)
   for a,b in [((11,49),(30,42)),((30,42),(37,28)),((37,28),(46,18))]:line(*a,*b,(164,63,44))
   for x,y in [(11,49),(30,42),(37,28),(46,18)]:rect(x-1,y-1,3,3,(140,52,36))
   line(51,7,51,17,(59,80,71));line(47,12,55,12,(59,80,71))
   for j in range(4):rect(7+j*4,56,3,2,(69,93,88))
  if name=='screen':
   rect(4,4,56,3,(39,91,102));rect(7,10,24,2,(73,163,175))
   for y in (22,24,39,41):line(13,y,43,y,(46,183,192))
   line(8,31,13,22,(46,183,192));line(8,31,13,41,(46,183,192));line(43,22,49,31,(46,183,192));line(43,41,49,31,(46,183,192))
   rect(24,18,9,4,(46,183,192));line(30,25,30,39,(46,183,192))
   for x,h in [(8,4),(14,8),(20,6),(26,11)]:rect(x,57-h,3,h,(47,174,185))
   for y in (19,25,31,37,43):rect(54,y,4,2,(65,149,139))
  if name=='gauge':
   for y in range(64):
    for x in range(64):
     r=math.hypot(x-31.5,y-31.5)
     c=(40,50,54) if r>29 else (124,138,138) if r>26 else (220,216,186)
     pixel(ox,oy,x,y,c)
   for i in range(11):
    a=math.radians(140+i*26);line(round(32+22*math.cos(a)),round(32+22*math.sin(a)),round(32+18*math.cos(a)),round(32+18*math.sin(a)),(45,57,58))
   rect(30,30,4,4,(49,58,56))
  if name in ('case_shell','case_green'):
   # Broad inset pressed panel, restrained edge wear and fastening marks.
   base=COLORS[name]
   for yy in (5,57):rect(6,yy,52,1,tuple(v-19 for v in base))
   for xx in (5,58):rect(xx,6,1,51,tuple(v-17 for v in base))
   for xx,yy in ((8,9),(53,9),(8,52),(53,52)):
    rect(xx,yy,2,2,(36,45,46));rect(xx,yy,1,1,(132,141,132))
   rect(11,43,10,2,(161,160,130));rect(11,47,7,1,(128,134,116))
  if name in ('case_gray','case_olive'):
   rect(3,3,58,3,(116,130,130));rect(3,58,58,3,(26,35,39))
   for xx in (9,48):
    rect(xx,5,6,53,(179,124,29) if name=='case_gray' else (63,76,38))
    rect(xx+1,5,1,53,(222,160,45) if name=='case_gray' else (111,126,66))
   for xx in (2,56):
    for yy in (2,53):rect(xx,yy,6,9,(34,44,48))
   rect(24,48,13,4,(190,186,149))
  if name=='locker':
   rect(3,3,58,2,(214,150,44));rect(3,58,58,3,(93,72,37))
   rect(42,25,12,5,(31,39,42));rect(44,26,8,2,(133,147,146))
   rect(8,49,18,4,(196,191,157))
  if name=='medical':
   rect(25,12,14,40,(218,213,179));rect(12,25,40,14,(218,213,179))
  if name=='conduit':
   for y in (10,45):rect(0,y,64,3,(49,58,61))
  if name=='paper':
   rect(5,5,54,4,(149,126,80))
   for y in (16,21,26,31,42,47):rect(8,y,40 if y%2 else 47,2,(109,110,85))
 def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
 raw=b''.join(b'\0'+p[y*SIZE*4:(y+1)*SIZE*4] for y in range(SIZE))
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',SIZE,SIZE,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')

def uv(material,w=30,h=30):
 i=list(COLORS).index(material);x=i%8*64;y=i//8*64
 # Individual face dimensions preserve approximately two texture pixels per model unit.
 special=material in ('map','screen','gauge','paper','panel','drawer','glass','locker','medical','case_gray','case_olive','case_shell','case_green')
 return [x+1,y+1,x+(63 if special else max(3,min(63,round(w*2)+1))),y+(63 if special else max(3,min(63,round(h*2)+1)))]
