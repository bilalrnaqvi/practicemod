# Mechanical Dolphin / Panorama

Editable source: `src/main/blockbench/mechanical_dolphin.bbmodel`.

Modeled from the user's supplied Panorama sheet on 2026-10-09: blue armor,
white lower hull, cyan lights, prone one-player cockpit, swept pectoral fins,
mechanical tail and a framed transparent canopy. The approved art is now integrated into the Minecraft renderer and inventory
item, with a prone rider and separate translucent canopy pass.

- 219 editable cube/mesh elements in 16 named groups; embedded 1024px pixel atlas.
- The user's preferred original slim cyan sensor eyes are restored: dark
  rectangular housings, cyan lenses and bright horizontal cores, without pupils
  or pronounced eyebrows.
- Blockbench Generic Model format (supports meshes and arbitrary rotations).
- Coordinates: Y up, -Z forward; 16 model units = one block. Overall 85.2 long,
  56 wide including fins, and 28.9 tall (5.325 × 3.5 × 1.806 blocks).
- `04_CANOPY_DOOR_rotate_X` owns all moving glass, canopy ribs, rims and latches.
  Its transverse hinge pivot is `[0, 11.15, 15.4]`; the source is closed at 0°.
- `mechanical_dolphin_open_review.bbmodel` is a separate static 68° open pose.
  This is a review pose, not an animation clip. Lift-strut geometry is aligned
  separately in this copy. Neither project has a door animation clip.
- `door-bindings.json` records the pivot and strut endpoints for the later pass.
  The struts need two-point alignment/extension when that animation is authored.
- Pectoral fins, dorsal fin, tail peduncle and tail flukes have distinct groups.
- Native looping `animation.mechanical_dolphin.idle` and `swim` clips drive all
  three fins plus the tail root and child flukes. Idle takes 4.19 seconds; full
  swimming takes 0.87 seconds. Each joint has 129 editable keys.
- The source faces -Z, so X/Z rotations are negated relative to runtime +Z.
  `tail-bindings.json` records the current source and clip metadata. Runtime
  integrates phase and blends speed, water contact and steering independently;
  native clips represent the resting and swimming endpoints.
- The cockpit provides a segmented prone couch, arm supports, grips, display,
  foot stops and a rear bulkhead. The player is not baked into the vehicle.

`model-preview.png`, `closed.png`, and `open.png` render actual project geometry
and embedded UV textures. `validation.json` records structural/UV checks.
`idle-motion.gif` and `swim-motion.gif` show the current runtime mesh with all
five animated joints; `-slow` variants run at half speed. `fin-tail-poses.png`
shows opposing stroke extremes. `tail-validation.json` records independent
native/runtime mesh agreement. Older `tail-motion` artifacts are legacy previews
of the superseded prototype stroke and previous eye styling.
The native project was also opened and visually inspected in Blockbench.
Runtime export is recorded in `runtime-export.json`. The runtime mesh and actual
vanilla pilot preview is `output/mechanical-dolphin/runtime-preview.png`; wide/slim
canopy clearance is recorded alongside it. Build and all 48 GameTests passed.
The development client launches; interactive visual review remains to be done.
Door movement remains a separate animation task.

Rebuild with `tools/author_mechanical_dolphin.py`; render with
`tools/preview_mechanical_dolphin.py` using Python with Pillow and NumPy.
Run `tools/author_dolphin_swim_clips.py` to update only animation clips, then
export/build. Run `tools/preview_dolphin_tail.py` for mesh checks and motion GIFs.
Rebuilding overwrites these generated model files; preserve manual edits first.

Export the current approved artist source to Minecraft with
`python3 tools/generate_mechanical_dolphin_geometry.py`, then `./gradlew build`.
