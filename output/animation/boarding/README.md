# Deepseeker boarding — first motion study

Status: **authoring prototype, not integrated into Minecraft**.

Open `src/main/blockbench/deepseeker_boarding_study.bbmodel` in Blockbench and
switch to **Animate**. Select `animation.deepseeker.ladder_contact_study` and
play or scrub the 4.8-second timeline. The canonical submarine geometry/textures
are copied into the scene and locked; the runtime vehicle asset is unchanged.

## Review files

- `ladder-study.gif`: textured, 20 fps authoring review with a pause at the end.
- `ladder-study-poses.png`: four representative poses.
- `rig-validation.json`: 577 samples of independent two-bone reach validation.
- `ladder-study.json`: generated beat list and target positions, for reference.
  The editable `.bbmodel` is the source for preview regeneration.
- `scene.json`: earlier runtime-mesh diagnostic export; not this new rig.

The mannequin is an authoring aid, with a simple review texture. It is not the
final player-skin renderer. The GIF uses actual submarine cuboids/textures and
an independent two-bone solver driven by the `.bbmodel` keys. It is not an
in-game recording or a pixel-identical capture of Blockbench's IK solver.

## Rig controls

- `PLAYER_root`: body travel in hull space; its bind heading faces the ladder.
- `PLAYER_torso` / `PLAYER_head`: body lean and gaze.
- `CONTACT_left_hand`, `CONTACT_right_hand`, `CONTACT_left_foot`,
  `CONTACT_right_foot`: native null-object IK targets, independent of the root.
  Holding these still keeps a support contact fixed as the body moves.
- `POLE_*`: elbow/knee bend direction. Move these deliberately; a pole crossing
  its chain axis can flip a joint even if the end target remains reachable.
- `ANCHOR_*`: hatch handle, hinge, cabin landing and seat references for later
  choreography. They are not yet animated interactions.

The sequence establishes support, reaches the next grip, swings and plants the
right foot, transfers weight, repeats on the left, then settles onto the rails.
No limb-scale channels are used. The character rises two rungs; it does not yet
complete the hatch transfer or enter the cabin.

## Regeneration

Use Python with NumPy and Pillow, and the project's Java 21/runtime dependencies.

```sh
# Preview and validate the current, potentially hand-edited Blockbench project:
python3 tools/preview_boarding_study.py

# Create a fresh starting scene only if it does not already exist:
python3 tools/author_boarding_study.py
```

The authoring generator refuses to overwrite existing manual work unless
`--force` is explicitly supplied. The preview reads native linear keys; if
curves are changed to other interpolation modes, review them in Blockbench or
bake them to linear samples before using the offline preview.

Validation confirms target reach and fixed bone lengths. It does **not** certify
full mesh clearance, animated skin/armor compatibility, first-person framing,
networking, or final animation quality. Those checks belong to subsequent
polish/integration work. Do not copy this scene over `deepseeker.bbmodel`.
