"""Package the bounded outside review; no runtime assets are changed."""
from pathlib import Path
import sys
from PIL import Image,ImageDraw
O=Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).resolve().parents[2]/'output/animation/outline-entry'
selected=[0,18,36,54,72,90,112,134,150]
sheet=Image.new('RGB',(900,846),(18,25,34))
for i,f in enumerate(selected):
 p=O/f'frame-{f:03}.png'
 if not p.exists():continue
 im=Image.open(p).convert('RGB').resize((300,262));x=i%3*300;y=i//3*282
 sheet.paste(im,(x,y+20));ImageDraw.Draw(sheet).text((x+10,y+4),f'{f/30:.2f}s'+(' / CUTAWAY' if f>=66 else ''),fill='white')
sheet.save(O/'contact-sheet.png')
if all((O/f'frame-{f:03}.png').exists() for f in range(0,150,2)):
 frames=[]
 for f in range(0,150,2):
  im=Image.open(O/f'frame-{f:03}.png').convert('RGB')
  canvas=Image.new('RGB',(800,736),(18,25,34));canvas.paste(im,(0,36))
  label='CLIMB' if f<27 else 'HATCH + TRANSFER' if f<63 else 'DESCEND' if f<87 else 'SEAT + SWIVEL'
  ImageDraw.Draw(canvas).text((14,12),f'MOTION DRAFT / {label} / {f/30:.2f}s'+(' / CUTAWAY' if f>=66 else ''),fill='white')
  frames.append(canvas)
 frames[0].save(O/'outside-entry.gif',save_all=True,append_images=frames[1:],duration=[70,60,70]*25,loop=0,optimize=False)
print(O)
