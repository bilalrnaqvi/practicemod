"""Package separate Minecraft first-person and outside reviews, normal and half speed."""
from pathlib import Path
from PIL import Image,ImageDraw
R=Path(__file__).resolve().parents[2];O=R/'output/animation/boarding-runtime'
for folder,stem,label,step,rate in [
 (O/'rear-runtime/first-person','first-person','MINECRAFT / FIRST PERSON / full hull',2,20),
 (O/'rear-runtime/outside','outside-runtime','MINECRAFT / OUTSIDE REVIEW / full hull',2,20),
 (O/'rear-review/outside','outside-cutaway','BLENDER / OUTSIDE CONTACT REVIEW / cutaway after 1.27s',3,30)]:
 end=100 if rate==20 else 150
 frames=[]
 for f in range(0,end+1,step):
  im=Image.open(folder/f'{f:03}.png').convert('RGB');im.thumbnail((800,600))
  a=Image.new('RGB',(800,640),'#14202c');a.paste(im,((800-im.width)//2,40));d=ImageDraw.Draw(a)
  d.text((14,10),label,fill='white');d.text((14,25),f'{f/rate:.2f}s / 5.00s',fill='#b7ccd8');frames.append(a)
 for speed,ms in [('normal',100),('half-speed',200)]:
  frames[0].save(O/f'{stem}-{speed}.gif',save_all=True,append_images=frames[1:],duration=ms,loop=0,optimize=False)
 selected=[round(i*(end//step)/15)*step for i in range(16)]
 a=Image.new('RGB',(1200,1000),'#14202c');d=ImageDraw.Draw(a)
 for i,f in enumerate(selected):
  x=i%4*300;y=i//4*250
  im=Image.open(folder/f'{f:03}.png').convert('RGB');im.thumbnail((300,228));a.paste(im,(x+(300-im.width)//2,y+20));d.text((x+8,y+4),f'{f/rate:.2f}s',fill='white')
 a.save(O/f'{stem}-contact-sheet.png')
html='''<!doctype html><html><meta charset="utf-8"><title>Deepseeker boarding review</title><style>body{background:#101b25;color:#ecf2f5;font:17px system-ui;margin:30px auto;max-width:1000px}img{width:100%;max-width:800px}button{padding:10px;margin:8px}section{margin:35px 0}p{color:#bbccd8}</style><h1>Deepseeker boarding — rear hatch</h1><p>5.0 seconds. First-person gameplay uses the authored eye track. Outside and cutaway views are separate inspection cameras.</p><button onclick="speed('normal')">Normal speed</button><button onclick="speed('half-speed')">Half speed</button>'''
for stem,title,desc in [('first-person','First person','Minecraft renderer, complete hull, player head hidden.'),('outside-runtime','Outside review','Minecraft renderer, complete hull, separate inspection camera.'),('outside-cutaway','Contact inspection','Blender source, shell cutaway after 1.27 seconds to expose limb supports.')]:
 html+=f'<section><h2>{title}</h2><p>{desc}</p><img data-stem="{stem}" src="{stem}-normal.gif"></section>'
html+='''<script>function speed(s){document.querySelectorAll('img').forEach(i=>i.src=i.dataset.stem+'-'+s+'.gif')}</script></html>'''
(O/'review.html').write_text(html)
print(O/'review.html')
