"""Assemble Blender-rendered frames into a timed GIF and a pose contact sheet."""
from pathlib import Path
from PIL import Image, ImageDraw

out = Path(__file__).resolve().parents[2] / 'output/animation/submarine-blender'
frames = []
for frame in range(0,144,2):
    rendered = Image.open(out / f'review-{frame:03}.png').convert('RGB')
    canvas = Image.new('RGB',(rendered.width,rendered.height+36),(24,28,34))
    canvas.paste(rendered,(0,36))
    draw = ImageDraw.Draw(canvas)
    stage = 'HATCH' if frame < 39 else 'LADDER' if frame < 69 else 'SEAT + SWIVEL'
    draw.text((16,12),f'DEEPSEEKER / {stage} / {frame/30:.2f}s',fill='white')
    draw.text((470,12),'BLENDER CUTAWAY PREVIEW',fill=(170,185,200))
    frames.append(canvas)
# GIF time is quantized to 10 ms. This alternating pattern totals exactly 4.8 s.
frames[0].save(out/'deepseeker-entry.gif',save_all=True,append_images=frames[1:],
               duration=[70,60,70]*24,loop=0,optimize=False)
sheet = Image.new('RGB',(1080,880),(24,28,34))
for index,frame in enumerate([0,12,26,38,48,58,68,78,88,100,120,142]):
    picture = Image.open(out/f'review-{frame:03}.png').convert('RGB').resize((360,200))
    x,y = index%3*360,index//3*220
    sheet.paste(picture,(x,y+20))
    ImageDraw.Draw(sheet).text((x+12,y+4),f'{frame/30:.2f}s',fill='white')
sheet.save(out/'contact-sheet.png')
print('Assembled 72 frames, 4800 ms, plus contact sheet.')
