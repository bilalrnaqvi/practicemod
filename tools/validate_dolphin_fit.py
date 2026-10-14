#!/usr/bin/env python3
"""Check captured vanilla wide/slim meshes against the closed canopy envelope."""
import csv,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
rings=[(-24.4,6.25,10.4,13.1),(-18.9,8.35,10.95,19.5),(-18.1,8.35,11.05,19.5),(-8,9.1,11.05,21.6),(3.7,9.4,11.05,22.1),(14.7,8.95,11.05,19.9)]
minimum=100; count=0
for name,x,y,z in csv.reader((root/'build/dolphin-rider-points.csv').open()):
    x,y,z=abs(float(x)*16),float(y)*16,-float(z)*16
    if y<11.05: continue # Hull/couch region, below the glass sill.
    a,b=next((a,b) for a,b in zip(rings,rings[1:]) if a[0]<=z<=b[0])
    t=(z-a[0])/(b[0]-a[0]); w,low,high=[a[i]+(b[i]-a[i])*t for i in range(1,4)]
    assert x<w,(name,'side',x,w)
    roof=high if x<=w*.58 else high-min(3.4,(high-low)*.65)*(x-w*.58)/(w*.42)
    clearance=roof-y
    assert clearance>0,(name,'roof',clearance)
    minimum=min(minimum,clearance);count+=1
report={'wide_and_slim_with_skin_layers':True,'vertices_above_sill_checked':count,'minimum_glass_roof_clearance_blocks':minimum/16,'tail_curve_and_pose_restoration':'passed in PreviewMechanicalDolphin','limits':'Glass envelope check; trim, armor and equipped-item fit need visual review.'}
(root/'output/mechanical-dolphin/rider-fit.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
