"""Assemble images produced by Minecraft's opt-in animation renderer check."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/animation/submarine-blender/runtime'
frames = []
for frame in range(72):
    canvas = Image.new('RGB', (800, 448), '#17202b')
    draw = ImageDraw.Draw(canvas)
    draw.text((18, 12), 'MINECRAFT RENDERER / Blender pose playback', fill='white')
    draw.text((18, 34), 'Wide skin', fill='#c1d5e8')
    draw.text((418, 34), 'Slim skin', fill='#c1d5e8')
    for x, name in [(8, 'wide'), (408, 'slim')]:
        picture = Image.open(OUT / f'{name}-motion' / f'{frame:03d}.png').convert('RGB')
        canvas.paste(picture.resize((384, 384)), (x, 56))
    draw.text((590, 12), f'{frame/15:.2f}s / 4.80s', fill='white')
    frames.append(canvas)
frames[0].save(OUT / 'player-playback.gif', save_all=True, append_images=frames[1:],
               duration=[70,60,70]*24, loop=0, disposal=2)

items = [(f'wide-{tick}.png',f'Wide / {tick/20:.1f}s') for tick in (0,20,40,60,80,96)]
items += [('slim-0.png','Slim / hatch pose'),('slim-96.png','Slim / seated pose'),
          ('wide-armor-0.png','Wide / armor'),('slim-armor-96.png','Slim / armor'),
          ('wide-restored.png','Wide / normal pose restored'),('slim-restored.png','Slim / normal pose restored')]
sheet = Image.new('RGB', (1024, 890), '#17202b')
draw = ImageDraw.Draw(sheet)
draw.text((16,12),'MINECRAFT RENDER CHECK / skin widths, bent armor and model restoration',fill='white')
for index,(filename,label) in enumerate(items):
    x,y=(index%4)*256,40+(index//4)*282
    draw.text((x+10,y+5),label,fill='#c1d5e8')
    sheet.paste(Image.open(OUT/filename).convert('RGB').resize((256,256)),(x,y+24))
sheet.save(OUT/'contact-sheet.png')
print(OUT/'player-playback.gif')
