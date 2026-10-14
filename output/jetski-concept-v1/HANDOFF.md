# White jet ski — Blockbench art handoff

Reference: `white-jetski-blockbench-reference.png`

## Design

Modern white personal watercraft translated into Minecraft cuboids: stepped bow, white side rails, dark lower hull and bumper, recessed footwells, charcoal two-person saddle, compact handlebars, and a rear boarding deck. Preserve the broad silhouette; simplify small details before adding more geometry.

## Proposed modeling specification

- Overall target envelope: 16 units wide × 32 long × 16 high (1 × 2 × 1 Minecraft blocks). This is an art target, not a verified match to the existing entity hitbox.
- Coordinates: X = width, Y = up, bow toward negative Z. Center X/Z on the hull; place hull bottom at Y = 0.
- Use cuboids and stepped contours. Slanted cowl pieces may use rotated cuboids if supported by the selected export format. Avoid smooth bevels and high-density meshes.
- Suggested groups: `root`, `hull`, `bumper`, `shell`, `footwells`, `saddle`, `handlebars`, `rear_deck`, `jet_nozzle`.
- Keep handlebars separate, with their pivot at the steering column, for optional steering animation.
- Suggested texture starting point: one 128 × 128 pixel atlas with crisp pixel details. Paint panel seams and tread instead of modeling every line shown in the reference.
- Palette: shell #F2F2F0; saddle #4A4A4A; bumper/underside #2E2E2E; footwells/details #1C1C1C.
- Select the model/export format to match the mod's renderer before production. This handoff does not change runtime assets or prescribe a renderer.

## Interpretation

The generated sheet is a visual concept, not CAD or an importable `.bbmodel`. Its projections and dimension arrows can differ slightly. Use the numeric target envelope above, keep left/right hull symmetry, and reconcile the views in one coherent model rather than tracing each independently. Seat count is a visual intention, not a change to gameplay passenger support.

## Generation

Created using the built-in image generation tool, with the earlier realistic white jet ski as the visual reference.

Final generation prompt:

```text
Use case: stylized-concept
Asset type: Minecraft mod vehicle concept sheet for a Blockbench artist.
Input image: visual design reference of the original modern white jet ski; adapt its identity to a Minecraft cuboid model, do not preserve realistic curved geometry.
Primary request: A clean, highly legible professional modeling reference sheet of ONE consistent Minecraft jet ski. White upper body and white hull sides, thin dark graphite bumper and underside, stepped charcoal two-person saddle, squared handlebar column and horizontal bar, dark recessed footwells, short rear boarding deck. Modern streamlined shape translated into chunky cuboids and stepped rectangular contours. Entire craft modeled from a modest number of axis-aligned or simply rotated rectangular cuboids, square-edged white panels; stepped narrowing bow, no smooth curves, no complex mesh surfaces. Pixel-art textures at Minecraft scale, nearest-neighbor crispness, restrained off-white shading and charcoal palette, no glossy photoreal rendering.
Composition: landscape technical concept sheet on very pale gray grid paper, generous spacing. Upper left: larger three-quarter in-game style preview. Upper middle/right: strict orthographic TOP view showing stepped plan shape. Lower left: strict orthographic SIDE view. Lower middle: strict orthographic FRONT view. Lower right: strict orthographic REAR view and a small material palette. Use consistent proportions and the same geometry across all views. All views fully visible with no overlap. Orthographic views must be flat, pixel-crisp, with no perspective and minimal shading. Jet ski nominal bounds 16 model units wide, 32 model units long, 16 model units tall; 16 units = one Minecraft block. Label the views simply 'ISOMETRIC', 'TOP', 'SIDE', 'FRONT', 'REAR'. Main title 'WHITE JET SKI — BLOCKBENCH CONCEPT'. Small bottom note '16 units = 1 block | 16 W × 32 L × 16 H'. Add restrained labels identifying 'White shell', 'Charcoal saddle', 'Graphite bumper', 'Footwells', 'Handlebar'.
Constraints: practical buildable Minecraft style; simplified cuboid geometry is essential, no rounded bevels, no realistic smooth marine bodywork, no riders, no brand logos, no water, no decorative scenery. This is design reference art, not a fabricated screenshot of Blockbench. Produce a polished usable modeling handoff sheet.
```
