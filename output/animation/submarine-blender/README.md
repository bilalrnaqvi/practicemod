# Deepseeker Blender boarding

This is a **work-in-progress boarding scene**. Player pose playback is now connected
to Minecraft for review; the complete submarine boarding sequence is not replaced
and the performance is not approved as final quality.

- Editable source: `src/main/blender/deepseeker_entry.blend`.
- Motion review: `deepseeker-entry.gif`, 4.8 seconds at 15 review frames per second.
- Pose review: `contact-sheet.png`.
- Player clip: `deepseeker_entry.emote.json`, the existing Emotecraft/PAL format.
- `evaluated-motion.json` records Blender's sampled hull-space root, prop and pose
  tracks for integration review. It is diagnostic data, not a custom playback API.
- `export-validation.txt` records the actual PAL 1.1.6 / Minecraft 1.21.1 parser check.
- Contact reports measure endpoints, not visual quality or whole-mesh clearance.

## Choreography

At the hatch from frame 0; open and release the lid, hop feet-first through the
opening, descend the interior ladder, step toward the waiting chair, sit, and
swivel into the forward position. The empty chair turns during descent; the
occupied chair turns back during the last second. No exterior approach climb.
The scene runs at 30 fps, frames 0–144. The standard player export has 96 ticks of
authored motion plus one terminal tick required by the legacy clip envelope.

Interior review shots deliberately hide the outer hull from frame 39. They are
cutaways, not validated gameplay cameras. The vehicle itself retains its original
geometry and pixel texture. The character is a textured reference player; arbitrary
player skins, slim arms, armor and first-person rendering have not been validated.

## Editing and export

Use Blender 4.5 LTS or a compatible newer release. Disable automatic execution of
embedded scripts when opening the upstream rig. Edit the working `.blend` directly
to refine the performance; regenerating it replaces those edits.

From the repository root, with `blender` replaced by your executable path:

```sh
blender --background --disable-autoexec src/main/blender/deepseeker_entry.blend --python tools/blender/export_submarine_entry.py
blender --background --disable-autoexec src/main/blender/deepseeker_entry.blend --python tools/blender/review_submarine_entry.py -- motion
```

`build_submarine_entry.py` reproduces the initial scene and its keys; it is not the
required editor. It refuses to replace an existing working scene unless explicitly
given `-- --force`. `boarding_contacts.py` adds Blender IK contacts and retargets them
to the reference rig's single-axis elbows/knees. The source rig and its license are
under `tools/blender/vendor`.

## Remaining production work

Resolve any visible foot sliding, prop/mesh intersections and camera obstructions
in the full-speed review before accepting the performance. Then implement PAL
playback against the server-owned boarding clock, synchronize the root/hatch/chair,
replace the old camera timing, and test cancellation, remounting, remote players,
skin/armor variants and first-person views. Parser success alone proves none of
those runtime behaviors. No runtime Java or mod dependency was changed by this pass.

## Runtime compatibility finding

The exact PAL 1.1.6 NeoForge sources parse the bend tracks, but their base
`RenderUtil.translatePartToBone` does not deform the skin. PAL alone therefore
cannot reproduce the articulated preview. Emotecraft's maintained 1.21.1 branch
embeds **bendy-lib-forge 5.1**, available from `https://maven.kosmx.dev/`, together
with the older PlayerAnimator. Its renderer provides `BendableCuboid` and mutable
cube hooks. A scoped PAL-to-bendy adapter is the next integration candidate;
its compatibility with our player/armor mixins still needs an actual runtime test.
Neither the old PlayerAnimator nor Emotecraft was installed into the mod.

Evidence: [Emotecraft 1.21.1 dependency versions](https://github.com/KosmX/emotes/blob/1.21.1/gradle.properties),
[NeoForge dependency setup](https://github.com/KosmX/emotes/blob/1.21.1/minecraft/neoforge/build.gradle),
and [bendy-lib 1.21 implementation](https://github.com/KosmX/bendy-lib/tree/1.21).

## Minecraft playback proof, 2026-10-12

PAL Core 1.1.6 and bendy-lib 5.1 are now bundled. The core evaluator is used through
our existing player hooks, avoiding the full PAL mod's competing player/camera
mixins. The development classpath follows NeoForge's documented
[plain library setup](https://docs.neoforged.net/toolchain/docs/dependencies/nonmclibs/).
Minecraft's bundled utility versions are retained; the new dependencies are not
allowed to replace Gson, Netty, Fastutil or SLF4J.

The first runtime renders exposed two conversion problems and an outer-layer
problem. Export now converts rotation matrices between the Blender and Minecraft
coordinate systems instead of swapping XYZ Euler channels. The player adapter
composes torso rotation using matrices and explicitly extracts Minecraft's Z-Y-X
angles. Sleeves, pants and armor register their bend builders before copying
poses, so those layers follow the skin.

`runtime/player-playback.gif` is generated by Minecraft's actual player models,
shaders and bend mixins. It is a fixed-root pose test, **not gameplay footage of
entering the submarine**. `runtime/contact-sheet.png` includes wide/slim skins,
vanilla diamond armor and normal poses restored after playback.

Checks completed:
- 1,778 PAL samples match exported rotations and bends, including fractional
  time, repeated renders, reverse seeks, restart and out-of-range clamping.
- 388 runtime hand/foot endpoints were compared with independently evaluated
  Blender coordinates: maximum difference 0.10001 model pixels, from the legacy
  rig's leg pivot offset (about 0.006 blocks at the player rendering scale).
- 144 moving frames, 12 skin poses and four armor poses rendered successfully.
- Both player models return to pixel-identical baseline renders after restoration.
- The development game starts and the mod JAR packages all four new libraries.

Reproduce with `./gradlew validateBlenderPlayback jar`, then
`./gradlew runClient -PanimationRenderCheck`. The latter renders into `runtime/`
and closes its own development game. It needs `runtime-reference.json` generated
by the Blender exporter. Assemble the review with
`tools/blender/assemble_runtime_review.py` using Python with Pillow.

For a manual review in the development game, use third person and
`/deepseekerpose play`, `/deepseekerpose 40` (freeze at a tick), or
`/deepseekerpose stop`. This is local pose review while unmounted. It does not
move the character or animate the submarine. The clip is packaged at
`assets/practicemod/player_animations/deepseeker_entry.json`; copy the exported
player clip there after re-exporting approved source edits.

Remaining: synchronize the hull-space route, hatch, chair and camera against the
server boarding clock; resolve the seated-pose handoff; review the full moving
vehicle sequence; test held items, other armor models, actual gameplay cameras,
remote players, interruption and remounting. The isolated render checks do not
establish those behaviors or prove artistic quality.
