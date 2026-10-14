"""Label model renders and build the local review gallery. Requires Pillow."""
from pathlib import Path
import json,html,hashlib
from PIL import Image,ImageDraw,ImageFont
R=Path(__file__).resolve().parents[2];O=R/'output/yacht/white-horizon'
fontpath='/System/Library/Fonts/Supplemental/Arial.ttf'
def font(n):return ImageFont.truetype(fontpath,n)
titles={'exterior_aft':'01 / WHITE HORIZON — AFT','exterior_bow':'02 / STEPPED BOW','port_elevation':'03 / PORT ELEVATION','cutaway_longitudinal':'04 / LONGITUDINAL CUTAWAY','deck_machinery':'05 / MACHINERY   Y -8','deck_lower':'06 / GUEST DECK   Y -4','deck_main':'07 / MAIN DECK   Y 0','deck_upper':'08 / UPPER DECK   Y +4','deck_roof':'09 / ROOFTOP   Y +8','salon_interior':'10 / DOUBLE-HEIGHT SALON','guest_interior':'11 / GUEST CABIN','bridge_interior':'12 / CAPTAIN’S BRIDGE','stair_cutaway':'13 / CORE A — STACKED STAIRS','structural':'00 / FULL-SIZE STRUCTURAL CHECK','cabins_cutaway':'14 / PORT CABIN CUTAWAY'}
for key,title in titles.items():
 p=O/(key+'.png')
 if not p.exists():continue
 im=Image.open(p).convert('RGB');w,h=im.size
 sheet=Image.new('RGB',(w,h+120),'#101f2b');sheet.paste(im,(0,80));d=ImageDraw.Draw(sheet)
 d.text((28,17),title,font=font(27),fill='#f6f0df')
 d.text((28,51),'Actual model geometry · 16 model units = 1 block · player references 0.6 × 1.8 blocks',font=font(16),fill='#adc4cf')
 d.text((28,h+91),'STATIC ART REVIEW  /  Not an in-game collision or multiplayer test',font=font(16),fill='#adc4cf')
 if key.startswith('deck_'):
  d.text((28,97),'STERN ↑',font=font(18),fill='#182e3a');d.text((w-125,h+47),'BOW ↓',font=font(18),fill='#182e3a')
  marks={'deck_lower':[(15,'CABINS 1 / 2'),(22,'A / LINEN'),(30,'CABINS 3 / 4'),(38,'CABINS 5 / 6'),(46,'STORE / B')], 'deck_main':[(8,'INFINITY POOL'),(15,'BEACH BAR'),(22,'A / DINING'),(34,'SALON / VOID ABOVE'),(46,'FOYER / B'),(54,'BOW LOUNGE')], 'deck_upper':[(17,'DINING TERRACE'),(22,'STAIR A'),(34,'OPEN SALON VOID'),(46,'BRIDGE / B')], 'deck_machinery':[(22,'STAIR A'),(34,'ENGINE BANKS')], 'deck_roof':[(30,'SUNBEDS'),(38,'LOUNGE'),(46,'B / MAST')]}[key]
  scale=32 if key=='deck_roof' else 65
  # Roof camera target is origin; labels outside the view are omitted.
  for station,label in marks:
   yy=80+h/2+(station-(38 if key=='deck_roof' else 30))/scale*h
   if yy<100 or yy>h+60:continue
   d.rectangle((15,yy-14,210,yy+16),fill='#e9eee9');d.text((22,yy-9),label,font=font(14),fill='#19313e')
 sheet.save(O/(key+'-review.png'))
# An overview made exclusively of generated model renders.
keys=['exterior_aft','exterior_bow','salon_interior','cutaway_longitudinal']
contact=Image.new('RGB',(1600,1170),'#101f2b');d=ImageDraw.Draw(contact);d.text((28,20),'WHITE HORIZON  /  MODEL REVIEW',font=font(34),fill='#f6f0df');d.text((28,63),'60 × 17 blocks · six guest cabins · connected stair cores · editable Blockbench cuboids',font=font(20),fill='#adc4cf')
for i,key in enumerate(keys):
 im=Image.open(O/(key+'-review.png'));im.thumbnail((786,520));contact.paste(im,(8+(i%2)*800,110+(i//2)*525))
contact.save(O/'overview.png')
sections=[]
for key,title in titles.items():
 if (O/(key+'-review.png')).exists():sections.append(f'<figure><a href="{key}-review.png"><img loading="lazy" src="{key}-review.png" alt="{html.escape(title)}"></a><figcaption>{html.escape(title)}</figcaption></figure>')
(O/'index.html').write_text('''<!doctype html><meta charset="utf-8"><title>White Horizon — actual model review</title><style>body{margin:0;background:#101f2b;color:#edf1eb;font:16px system-ui}header{padding:36px;max-width:1100px}h1{font-size:40px;margin:0 0 12px}p{line-height:1.6;color:#b8ced7}main{padding:12px;display:grid;grid-template-columns:repeat(auto-fit,minmax(440px,1fr));gap:18px}figure{margin:0;background:#172e3c}img{display:block;width:100%;height:480px;object-fit:contain}figcaption{padding:14px}a{color:#a8d9ef}</style><header><h1>White Horizon</h1><p>Editable Minecraft construction, 60 × 17 blocks. Full-block courses, slab decks, half-block stair rises, pane glazing and stair seating. These are renders of the delivered cuboids and textures, not concept illustrations.</p><p>Click any panel for full resolution. Deck and cutaway views intentionally remove roofs/walls. The ocean plane and 1.8-block player references are review helpers; they are excluded from the yacht model and collision metadata. Captain is a separate gameplay actor anchor.</p><p>Static clearances only. Moving collision, water exclusion, lighting and multiplayer remain unimplemented and untested in game.</p></header><main>'''+''.join(sections)+'</main>')
print('Review gallery:',O/'index.html')
