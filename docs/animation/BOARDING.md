# Integrated rear-hatch boarding

The rear-corner model and revised boarding are integrated. Right-click the
Deepseeker to enter. The server-owned sequence lasts **100 ticks / 5.0 seconds**.
It starts on the stern tread, lifts the forward-opening hatch, vaults over the
rim with hand support, grabs the ladder rails, slides down, steps onto the landing and runs to the chair.
The chair swivel is retained and retimed. The opening uses a rigid hip lean rather
than an extreme torso bend; a pelvis-relative leg solver prevents sideways hip twist and straight-knee flips.
The runtime adapter now applies PAL’s legacy body rotation to all attached parts.

First-person mode now stays first person throughout boarding, with the camera at
head-center eye height, a continuous authored gaze, visible animated arms/legs
and limb armor, and no rendered head, torso/collar or conflicting held-item hands. The descent
gaze looks down toward the rungs. Third-person mode uses a separate outside/cabin
review camera. Switching perspective does not restart boarding. Normal camera
behavior and controls resume on completion; cancellation and seat reservation
remain server-owned. The gameplay hull never cuts away.

## Review

Open `output/animation/boarding-runtime/review.html` for normal-speed and half-speed playback:

- `first-person-normal.gif`: Minecraft renderer, complete hull, first-person track.
- `outside-runtime-normal.gif`: Minecraft renderer, complete hull, review camera.
- `outside-cutaway-normal.gif`: Blender contact inspection, shell cutaway after 1.27 s.
- Corresponding `*-half-speed.gif` files and `*-contact-sheet.png` pose sheets.

These are offline captures from the real Minecraft renderer and Blender, not
recordings of a multiplayer session. The outside cutaway is strictly a review aid.

## Sources and regeneration

- Model: `src/main/blockbench/deepseeker.bbmodel` (rear entry adopted).
- Editable scene: `src/main/blender/deepseeker_boarding_runtime.blend`.
- Revision builder: `tools/blender/build_rear_boarding.py`.
- Exporter: `tools/blender/export_boarding_runtime.py`.
- Named hand/foot targets and first-person look curves remain in the scene.
- Previous integration: `src/main/blender/baselines/deepseeker_before_rear_entry.blend`; previous canonical model
  also lives in `src/main/blockbench/archive/deepseeker_before_rear_entry.bbmodel`.

The rejected 9.6-second revision is preserved in `baseline-before-slide/`.

The revision builder reads the tracked baseline in `src/main/blender/baselines/`. Export manual edits directly from the canonical scene; rerunning the builder reconstructs choreography and can overwrite those edits.
Do not run the older `build_boarding_runtime.py` to update this rear-entry scene.
For a deliberate scripted choreography rebuild with Blender 4.5, from the repository root:

```sh
blender --background --disable-autoexec --python tools/blender/build_rear_boarding.py
blender --background --disable-autoexec --python tools/blender/check_rear_body.py
blender --background --disable-autoexec --python tools/blender/check_rear_rotations.py
blender --background --disable-autoexec --python tools/blender/check_rear_anatomy.py
blender --background --disable-autoexec --python tools/blender/review_rear_boarding.py -- --motion
./gradlew verifyProject
./gradlew runClient -PanimationRenderCheck
./gradlew runClient -PsubmarineModelReview
python3 tools/blender/assemble_rear_review.py
```

Python image/geometry tools need Pillow. The exported standard PAL player clip
and server-safe root/hatch/chair/eye tracks are packaged in the mod.

## Validation and limits

- The 2026-10-13 audit passed all 53 required server GameTests, including boarding completion, exit,
  reservation, propulsion lock and continuous first-person eye/orientation tracks.
- PAL fractional-time sampling, repeat renders, seeks and restart pass.
- Minecraft wide/slim skins, 192 motion frames and four armor poses pass;
  reused models restore pixel-identically. All 404 sampled limb endpoints agree
  with Blender within 0.101 model pixels.
- Animated head envelopes clear the hull/lid/ladder at all 151 Blender frames.
- Maximum baked target error: about 0.113 blocks during a released-hand transition;
  this is not a claim of perfectly planted contacts. Largest adjacent sampled
  joint change: 39.89 degrees at 30 fps; the vault half-turn knee flips were removed.
- Static shaft, lid sweep, furniture approach, storage-panel sweep and walking
  route checks pass after flattening the rung/tread depth to one model pixel.

Contact and clearance diagnostics are in `rear-validation-summary.json`,
`rear-contact-errors.json`, `rear-body-clearance.json` and
`rear-rotation-continuity.json`. Grip volumes overlap supporting rungs/rim; the
retained seated pose has seat-pan overlap. These checks do not claim universal
armor-to-hull clearance, arbitrary modded-player compatibility, or multiplayer
latency validation. Cabin walking, shared storage, crafting and bunk sleep are now implemented separately; see `DEEPSEEKER.md`. Their approach spaces remain constraints on animation edits.

## Vault / ladder / first-person polish

The 2026-10-13 feedback pass reduces torso lean below 50 degrees, uses a push-off
and free transfer before catching the rails, and keeps knees in the pelvis plane.
It removes an obsolete foot plant that caused the hips to descend past the foot.
`rear-anatomy.json` records hip/knee range checks; close unobstructed review images
are in `rear-review/front/`. These are diagnostic views, not gameplay cameras.
Ladder rungs and tread caps are now one model pixel deep, flush with the rail
fronts. Sliding palms sit ahead of the rung faces. First-person rendering excludes
the torso/jacket and armor collar; third-person retains the complete player.
The prior five-second baseline is preserved in `baseline-before-vault-polish/`.

Third-person camera polish: the chair approach now stays over the aisle at X=.65
blocks, clear of the side cabin frame, with a short transition from the ladder
shot. Runtime and Minecraft review share the same camera path. First-person,
player poses and five-second timing are unchanged. Full-hull approach and swivel
frames were checked after rebuilding.

## Export an edited canonical scene without rebuilding it

```sh
blender --background --disable-autoexec src/main/blender/deepseeker_boarding_runtime.blend --python tools/blender/export_boarding_runtime.py
./gradlew verifyProject
```

The exporter also updates the tracked `output/animation/boarding-runtime/runtime-reference.json`
used by the Minecraft renderer check. Regenerate it whenever the scene changes. Review output
paths in this document are relative to `output/animation/boarding-runtime/` unless stated otherwise.
The joint/contact/clearance measurements above are historical reports for the documented
revision; rerun diagnostics before using them as evidence for a new revision.
