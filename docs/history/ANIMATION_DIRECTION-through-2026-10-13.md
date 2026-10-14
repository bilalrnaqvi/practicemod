> ARCHIVE: historical decisions and reports through 2026-10-13. Not active instructions or safe current rebuild commands. Paths in the original text are repository-relative.

# Animation direction

User requirement, 2026-10-08: animations must aim for the quality of the strongest
Minecraft animation projects. A tidier implementation or smoother version of the
current motion is not sufficient evidence of quality.

## Production approach

- Author deliberate performances in a visual animation tool, with the actual
  player rig and vehicle together. Prefer standard, editable animation assets
  over a new proprietary JSON timeline and a home-made editor.
- Evaluate PAL for player playback and GeckoLib 4 for vehicle/model playback on
  the project's Minecraft 1.21.1 / NeoForge target. These are candidates, not
  installed dependencies or a promise of automatic quality. Confirm integration
  with the mod's existing player mixins, equipment layers, camera, and server
  boarding clock before adopting them.
- Keep simulation-driven wheel rotation, suspension and vehicle attitude
  procedural. Use contact constraints to adapt authored reaches and planted
  feet. Do not stretch limbs to disguise unreachable targets; adjust the root,
  torso, target or staging.
- Coordinate player root movement, body poses, hatch, chair, camera and sound
  events on a single sequence timeline. Game state remains server-authoritative.
- Review first person as its own composition as well as third person. Avoid
  assuming a good third-person pose also reads well through the player's eyes.

## First sequence: Deepseeker boarding

Latest user direction, 2026-10-10: use Blender and make entry feel like a quick
GTA-style vehicle interaction, about 4–5 seconds total. Start the player beside
the hatch; omit the exterior ladder approach. Open the hatch, jump/drop inside,
briefly descend the interior ladder, and seat the player with the chair swivel
as the signature flourish. Preserve the vehicle appearance and interior route.
This supersedes the previous long exterior climb and 14.1-second sequence.

Target duration: 4.8 seconds. Suggested overlapping beats: grip/lift hatch
0–0.75 s; pivot and drop feet-first 0.65–1.5 s; short controlled ladder descent
1.4–2.3 s; step through to the waiting chair 2.2–3.15 s; sit and turn into the
controls 3.1–4.65 s; settle/control handoff by 4.8 s. The empty chair can turn
toward the arriving player during descent, then carry the seated player back
to the controls. Do not accelerate the old 14-second clip wholesale or add
extra full revolutions to interpret the user's enthusiasm for the chair turn.

Show body weight moving toward the supporting hand/foot before the other limb
releases. Use deliberate pauses and continuous travel where appropriate; do not
ease to a stop at every route waypoint. Mechanical pieces should convey their
weight and stop cleanly. Additional idle motion must serve a purpose.

Articulated elbows/knees were recommended for climbing/gripping/sitting. Following
the user's "next step" and "continue" instructions, an articulated authoring
prototype was created on 2026-10-09. This is a reviewable style proposal; no final
approval of the player silhouette or runtime replacement is implied.

## Review gate

Before calling a sequence finished:

- Review moving footage at normal speed and slow speed, from the gameplay
  camera and unobstructed side/front views, with actual runtime geometry.
- Reject visible pose snaps, unintended full rotations, hand/foot sliding
  during support, floating weight shifts, rubber limbs, or distracting clipping.
- Inspect slim/wide player skins, clothing/armor/held-item handling, entry and
  exit transitions, cancellation and remounting, remote-player playback and
  variable rendering frame rates. Restore reused player-model state correctly.
- Keep clearance and timeline tests, but never treat them as visual approval.
- Establish one convincing boarding sequence before extending the system to
  all vehicles. No claim of "best" quality without reviewing the resulting motion.

## Existing implementation audit

`tools/ExportBoardingPreview.java` exports actual runtime hull/player meshes and
can capture the current motion at half-tick intervals for comparison. The first
capture is `build/boarding-before.json`; it is diagnostic, not a finished clip.
`output/animation/boarding/scene.json` contains exported runtime geometry.

The current ATV wheel moves while the rider's arm angles remain fixed. Boarding
uses a route timeline plus separately retimed limb formulas and reach rules.
The half-tick capture contains right-arm orientation jumps of 128.84 degrees at
tick 37.5 and left-arm jumps of 116.60 degrees at tick 46, verified with quaternion
angular distances using the model's Z-Y-X rotation order. Do not diagnose these
from raw Euler-channel deltas alone: approximately 2π jumps can represent
equivalent orientations. The capture also confirms variable arm scaling in
contact poses.

A custom clip-reader prototype was discarded before runtime integration when
the user clarified the quality target. No boarding behavior has been replaced
by this diagnostic work.

## Authoring prototype, 2026-10-09

`src/main/blockbench/deepseeker_boarding_study.bbmodel` contains a locked copy of
the canonical submarine, a review mannequin with fixed-length upper/lower limbs,
four native Blockbench IK target/pole pairs, and the 4.8-second animation
`animation.deepseeker.ladder_contact_study`. Hull-space contacts remain planted
while the body shifts and rises. Hatch/landing/seat locators mark later work.

The file loaded in Blockbench and its native animation played. A textured offline
review GIF and pose sheet are in `output/animation/boarding/`; they are authoring
renders, not footage from Minecraft. The independent two-bone reach evaluator
sampled 577 poses with no unreachable targets. This verifies reach, not full mesh
clearance, engine compatibility, or final visual quality. Review details and
regeneration instructions are in that folder's `README.md`.

The user rejected this study on 2026-10-10 and requested a different animation
approach/engine. Do not extend this study through the hatch or treat its generated
target paths as the production approach. Retain the files only as a rejected
experiment and diagnostic reference. Passing reach checks did not demonstrate
convincing movement.

## Revised direction after rejection, 2026-10-10

Recommend evaluating Blender with an established Minecraft player rig and an
authored performance, exported to PAL for playback. This is a recommendation,
not a proven integration or a guarantee of artistic quality. The official
Emotecraft Blender rig provides an export path, IK controls and bend channels;
its documentation also warns that drivers are not exported and easing can differ
in game. Verify baked motion survives export before investing in a long sequence.

First prove a short, visually strong reference clip on the actual player in
Minecraft, checking limb deformation, both skin widths, armor, camera framing and
existing pose-layer conflicts. PAL's first-person mode renders third-person model
parts; it does not automatically supply separately authored first-person hands.
Do not accept an offline mannequin GIF as proof of this pipeline.

For an uncompromising quality target, animation performance must come from skilled
visual authoring, or a suitably licensed source animation with deliberate cleanup
and adaptation. Another script that generates guessed joint paths in Blender
would repeat the rejected method. Machinery can separately use GeckoLib 4 if
integration justifies it; changing playback libraries alone cannot improve the
performance. Locomotion is useful as a production reference but currently has no
promised 1.21.1 backport or stable public animation API for this integration.

## Blender production work, 2026-10-11

The user approved proceeding with Blender and requested a 4–5 second GTA-like
entry, preserving the chair swivel. A 4.8-second working scene now exists at
`src/main/blender/deepseeker_entry.blend`, using the upstream Emotecraft legacy
articulated rig and the actual exported submarine mesh/texture. It includes
explicit pose keys, native Blender hand/foot contact controls, joint retargeting,
continuous skin preview, hatch/chair tracks and review camera cuts. Source rig
and license are under `tools/blender/vendor`.

The first rendered pass exposed foot floating and obstructed interior views.
Follow-up edits add planted foot contacts and following review cameras. The
interior previews are explicitly cutaways. The work is not visually approved,
not proven top-tier quality, and not integrated into Minecraft. Do not confuse
successful endpoint checks or export parsing with those approvals.

`output/animation/submarine-blender/deepseeker_entry.emote.json` is a standard
player clip, not the rejected custom animation format. It has been parsed by
the real PAL 1.1.6 / Minecraft 1.21.1 loader with six body parts and five bend
tracks. Its authored range is 96 ticks, with one terminal tick. The sampled
`evaluated-motion.json` is diagnostic integration data only. Runtime Java,
dependencies, server timing and player rendering are still unchanged.

The foot-contact rebake was briefly blocked by automatic approval review citing
an account usage limit; after the user said continue it ran successfully. This
is no longer an active blocker. See the output folder README for remaining
visual and runtime validation. Do not extend the rejected Blockbench study.

Runtime research found an important caveat: the exact PAL 1.1.6 NeoForge base
renderer reads bend values but does not deform vanilla player cubes. Do not
install PAL alone and claim the Blender articulation is preserved. Emotecraft's
1.21.1 branch embeds `io.github.kosmx.bendy-lib:bendy-lib-forge:5.1` from
`https://maven.kosmx.dev/`; the sources were inspected and provide mutable cuboid
and bending hooks. A scoped PAL/bendy adapter is a concrete next candidate,
still unimplemented and untested with this mod's skins, armor and cameras.

## Primary references checked

- [Official Blender authoring/export guide](https://docs.zigythebird.com/emotecraft/creatingemotes/blender/):
  established player rig, IK controls, export process and limitations.
- [PAL first-person behavior](https://docs.zigythebird.com/pal/features/first_person/):
  third-person body parts rendered in first person, camera following and armor controls.

- [Locomotion README](https://github.com/Trainguy9512/locomotion/blob/master/README.md):
  describes a real-time system inspired by Unreal Animation Blueprints, state
  machines/blend spaces/montage tracks, a Maya exporter, and animator credits.
  It is a reference for approach, not a drop-in dependency recommendation.
- [Mowzie's Mobs first-person animation asset](https://github.com/BobMowzie/MowziesMobs-Public/blob/main/src/main/resources/assets/mowziesmobs/animations/animated_player_first_person.animation.json):
  evidence of dedicated authored player animation assets.
- [GeckoLib getting started](https://github.com/bernie-g/geckolib/wiki/Getting-Started)
  and [version support](https://wiki.geckolib.com/docs/geckolib4/): Blockbench
  authoring and a GeckoLib 4 path for Minecraft 1.21.1.
- [PAL introduction](https://docs.zigythebird.com/pal/intro/) and
  [installation](https://docs.zigythebird.com/pal/gettingstarted/how_to_add_lib_to_mod/):
  authored JSON clips, transitions, priorities, and a listed 1.21.1 NeoForge path.
- [PlayerAnimator repository](https://github.com/KosmX/minecraftPlayerAnimator):
  its maintainer directs new work to PAL. Do not recommend the older library
  merely because older animation tutorials use it.

## Minecraft player playback proof, 2026-10-12

This supersedes the earlier statement that runtime dependencies are unchanged.
PAL Core 1.1.6, mochafloats 4.1.0, Javassist 3.30.2-GA and bendy-lib 5.1 are bundled.
Use the core evaluator with the existing mod render hooks; do not install the
full PAL player mixins alongside this adapter without re-evaluating conflicts.
`BlenderPlayerClip` seeks the standard clip by absolute ticks. `BlenderPlayerPose`
adapts the sampled bones to the existing player and bendy skin/armor cubes.
`/deepseekerpose play|stop|<tick>` is a local unmounted player-pose review command.
The server boarding clock, old route, vehicle props and cameras remain unchanged.

Real Minecraft renders found and fixed: incompatible Euler-order conversion in
the exporter; a shoulder rotation mismatch after quaternion/Euler conversion;
and straight clothing/armor cubes hiding the articulated skin. Do not revert to
raw channel swapping. Matrix conversion preserves combined shoulder rotations.
The reference Blender .blend and its authored choreography were not regenerated.

`./gradlew validateBlenderPlayback jar` passes 1,778 sampling checks.
`./gradlew runClient -PanimationRenderCheck` starts Minecraft, renders wide/slim
skins and armor, compares 388 deformed endpoints against Blender's independent
reference, checks pixel-identical restoration, and closes the test game. Maximum
endpoint discrepancy is 0.10001 model pixels (legacy leg pivot offset). The
runtime GIF and contact sheet are under output/animation/submarine-blender/runtime.
They are fixed-root engine renders, not boarding gameplay or final quality approval.

Next: connect the authored root/prop tracks, server duration and cameras; preserve
chair seating through handoff; review actual vehicle motion and first-person/
remote-player behavior. Do not claim the full 4.8-second sequence is installed yet.

## User correction: restore outline-ladder exterior, 2026-10-12

The user explicitly stopped the animation integration and requested the old
submarine appearance with the outline ladder. They clarified that they like the
old hatch too; do not invent a redesign or retain the raised expedition hatch.
Preserve most of the interior performance, especially seating and the chair turn;
rebuild the exterior entry around the restored vehicle. Do not continue fitting
the exterior to the superseded raised-hatch/physical-ladder model.

The new authoring base is `src/main/blockbench/deepseeker_outline_ladder.bbmodel`.
Its archived exterior is preserved except for moving the two hinges forward, including the low octagonal/round hatch and 22 painted outline marks.
Current cabin furniture and swivel seat are preserved. The interior ladder was
repositioned below the original center hatch, with projecting grip extensions
removed. The player-sized shaft and sampled lid sweep pass the scoped clearance check.
This does not validate a full animated body or exterior climb.
Actual-model preview and restoration report are under
`output/blockbench/deepseeker-outline-restored/`.

Existing Blender scene, clips and playback prototype are retained as prior work,
not updated for this model. Runtime geometry now uses the restored model. Model-first
work now takes precedence over the previous integration next steps. Reuse the
interior acting/seat turn where compatible; re-stage the exterior and hatch-to-
ladder transfer after verifying the restored model's opening and contact points.

## Bounded model review completed, 2026-10-12

Canonical `deepseeker.bbmodel` and generated runtime geometry use the restored
model. Raised-hatch source is backed up at
`archive/deepseeker_raised_hatch_before_outline_restore.bbmodel`. The original
lid opens forward -105 degrees so it clears the aft mast; the two hinge fittings
move forward. The original seal overlap and hinge contacts are intentional.
`tools/check_outline_hatch.py` checks a .6-block-wide/deep vertical envelope and
21 sweep samples. This is a geometry check, not animation or gameplay validation.

Boarding temporarily seats immediately: the retired path targets the wrong
hatch. Existing animation experiments are retained, but integration is paused.
The user requires discussion of scope and expected usage before the next
substantial animation task. Stop after showing closed/open model renders.

## Outside motion draft authorized, 2026-10-12

User agreed to one bounded 4–5-second Blender motion preview and selected an
outside view. Draft source: `src/main/blender/deepseeker_outline_entry_preview.blend`.
Output: `output/animation/outline-entry/outside-entry.gif`. The restored runtime
mesh is imported; upper-guide climb, forward hatch opening and center-shaft
transfer are reblocked. The earlier interior seating/swivel performance is reused.
The exterior shell cuts away at 2.2 seconds and the camera remains third-person.
Glass omission and shell cutaway are review aids, not intended game behavior.
Exterior contacts and full-body collision are not polished/validated; this is a
motion draft. No runtime integration was authorized or done in this step.
Stop after showing the draft and wait for feedback on timing and staging before
another substantial animation pass.

## User-reviewed revision: side-wall ladder and handle opening

User liked the sequence but requested an all-fours crawl along the diagonal
painted guide, opening the hatch by its handle in the opposite direction, and
explicitly restoring the indoor ladder to its former side-wall position.
The current game source had the center ladder because our restoration moved it;
this was not just a preview camera difference. The canonical model now restores
its original X/Z side-wall placement; rails stop at Y66 and higher rungs, treads,
mounts and projecting grips are omitted to fit the low roof. Furniture is unchanged.

Hatch opens aft +90 degrees about (0,93,-14.85) model pixels, with hinge fittings
raised 3.9 pixels. The sampled sweep and vertical opening envelope pass (original
seal/hinge contacts allowed). This supersedes the earlier forward-opening revision.

`tools/blender/preview_outline_entry_v2.py` creates the revised editable source
`src/main/blender/deepseeker_outline_entry_v2.blend` and review under
`output/animation/outline-entry-v2/`. Right-hand contact follows the actual handle
through opening; the character shifts weight to keep it reachable. The sloped
crawl follows the painted route's lateral curve. After lowering through the hatch,
the player turns toward the restored side ladder. Original seated/swivel performance
is retained. Contacts are baked by `outline_hand_contacts.py`; measured distances
are in `hand-contacts.json`. Full-body collision and foot planting remain draft-level.
The model is rebuilt for Minecraft, but boarding playback remains unintegrated and
immediate seating remains active. Stop after showing this revision for feedback.

## Hatch-start revision requested by user

The user rejected the stand-up/repositioning and rigid five-second target. Start
already kneeling on all fours beside the hatch; reach, open, move directly over
the rim into the ladder, then preserve the seating and chair turn. No exterior
climb or movement behind the lid. V3 runs 6.4 seconds and keeps root height low:
right hand lifts the lid through the initial 30 degrees, releases, and the lid
finishes opening (assisted-hinge staging). This avoids chasing the handle upward.
Source: `src/main/blender/deepseeker_outline_entry_v3.blend`; builder:
`tools/blender/preview_outline_entry_v3.py`; output: `output/animation/outline-entry-v3/`.
The side-wall ladder/model remains unchanged. This step changes only the Blender
preview; runtime boarding remains immediate. Full-body collision and feet still
require a polish pass after user approval of staging. Stop after showing V3.

## In-game integration authorized and implemented

User approved V3 staging, reported inverted walking knees/legs, and requested
in-game playback. `build_boarding_runtime.py` replaces the inherited walking thigh
IK twists with explicit forward/back strides and negative knee flexion, adds turn
keys toward the ladder, and smooths the walk handoff. Editable runtime source is
`src/main/blender/deepseeker_boarding_runtime.blend`.

`export_boarding_runtime.py` exports 129 player samples plus `BlenderBoardingTrack`
(root anchor, heading, hatch, chair, head camera). All playback uses the existing
server boarding start time for 128 ticks / 6.4 seconds. Player poses use PAL/bendy-lib;
seated poses retain the authored bent knees after control handoff. Rider rotation
locks to the chair after seating. The camera shows the exterior entry, cuts inside
for descent and seating, then restores the user's normal camera mode. There is no
runtime cutaway. Cancellation and seat reservation remain server-owned.

Validation: PAL fractional sampling/restart checks; all 48 required GameTests;
Minecraft wide/slim playback and armor renders, 192 motion frames, pixel-identical
pose restoration; 516 limb endpoints match Blender within .101 model pixels.
Full animated-body collision and the subjective motion quality are not claimed
finished. User should now test the integrated sequence in the development client.

## User review and model-first correction, 2026-10-13

The user rejects the cut-off interior ladder, the player's starting support on
the small black fitting, the hatch clipping through the head, and the floaty
vault/turn/descent. Redesign the hatch and ladder in Blockbench around physical
player support before reauthoring this section. The user explicitly permits
repositioning the interior ladder as needed for a convincing entry, superseding
the requirement to retain its original side-wall coordinates.

Map a supported start outside the lid sweep, rim hand plants, feet-first vault,
loaded ladder contact, alternating descent and floor step-off. The run to the
chair and swivel are good for now. Actual first-person boarding is required;
outside review cameras and cinematic interior shots are not first-person proof.
Current integrated camera still forces cinematic shots; its separate first-person
function is unused. Keep iterations small and review the model before a substantial
animation change. Do not regenerate or replace the integrated baseline wholesale.

First staged model: `src/main/blockbench/deepseeker_entry_redesign.bbmodel`.
Ten-rung continuous ladder beneath the hatch, low starting tread ledge, relocated
side handle, floor landing, and one moved ceiling conduit. Low octagonal lid,
furniture, chair and existing textures retained. Static review and scoped passing
clearance report: `output/blockbench/deepseeker-entry-redesign/`. No runtime model,
player clip or camera changes yet. Proposed head envelope and descent corridor
are not validation of an articulated player or of convincing motion.

### Rejected model pass: furniture access must remain usable

The user rejected `deepseeker_entry_redesign.bbmodel`: its ladder blocks the bed.
Future free walking, usable bed, desk and rear storage are required design
constraints now, not details to defer until gameplay implementation. The user
suggests relocating the hatch and ladder to the rear and reversing lid opening;
they question the diagonal ladder shown in concept art. Study the aft starboard
corner beside the storage, preserving its front approach and the bed/desk aisle.
Do not place a ladder across storage fronts or treat a clear descent shaft as
proof of a usable cabin. Rear roof/shoulder, fin, headroom and lid sweep need
checking before building the next model. No new animation or runtime changes.

### Rear-corner layout approved; staged geometry built

The user approved the rear-corner layout diagram. The resulting separate model is
`src/main/blockbench/deepseeker_rear_entry.bbmodel`, with review images, proposed
contacts and scoped clearance results in `output/blockbench/deepseeker-rear-entry/`.
Hatch and continuous ladder occupy the aft starboard corner past the bed end,
beside storage; the lid opens forward. A stern starting tread and shallow interior
landing support the proposed route. Bed, desk, storage and chair are retained.
Static access boxes, sampled walking route, lid/head and shaft checks pass.
Geometry review remains distinct from approval of reachable animated poses.
No runtime model, integrated clip or camera changes in this pass. Next isolate
start/open/vault/ladder contact, keeping chair approach/swivel and implementing
actual first-person separately from the outside review camera.

## Rear entry completed under unattended-work authorization, 2026-10-13

The user explicitly requested continuing through completion while asleep,
without additional step-by-step approvals. This supersedes the earlier model
review stop for this revision. Canonical/runtime geometry now adopts the rear
hatch and continuous ladder. The staged source remains alongside an archive of
its predecessor. A small forward tread lip supports toes without intersecting
the sloping inner liner; bed, desk, rear storage and aisle clearances remain valid.

The revised integrated scene runs 192 ticks / 9.6 seconds. Supported start,
assisted hatch lift, palm-supported vault, alternating rung descent and landing
replace the rejected entry. The prior approach timing and chair swivel remain;
transient inherited limb flips and approach foot height were corrected. Named
support controls and first-person gaze keys are editable in the Blender scene.
`tools/blender/build_rear_boarding.py` is the current revision builder, based on
`output/animation/boarding-runtime/baseline-before-rear-entry/`.

First-person boarding is wired into gameplay when first-person perspective is
selected, including animated body/armor and a continuous eye-height camera.
Third-person review shots remain separate. Gameplay keeps the complete hull.
Review footage and validation scope are in the runtime README and `review.html`.
All 48 server GameTests, PAL sampling checks and wide/slim/armor renderer checks
pass. Head clearance passes 289 sampled Blender frames; contact error is below
.0014 blocks. Grips and retained seated pose have intended/support-region overlap;
do not describe this as universal full-body/armor collision certification or a
multiplayer visual test. User taste/approval of this completed revision is not
assumed merely because the implementation and automated checks are complete.

## Five-second slide revision, 2026-10-13

User rejected the 9.6-second entry and the contorted opening silhouette. Target
is now five seconds: human vault, fireman-style ladder slide, brisk approach,
retained chair swivel. Current clip is 100 ticks / 150 Blender frames. The torso
leans as a rigid unit about the hips instead of folding its middle beyond 90°.
The PAL adapter now composes the legacy body rotation about the hip into every
part; clothing and armor inherit it. The slide keeps hands beside the rails and
feet together, replacing alternating rung steps. Joint pole continuity prevents
knee flips. Old integrated revision is in boarding-runtime/baseline-before-slide.
Outside/cutaway footage remains diagnostic; first-person uses the full hull.
Visual quality still requires user feedback; passing numeric checks is not approval.

## Human movement constraint and vault polish, 2026-10-13

User considers the five-second revision much closer, but rejects the first-second
hip/torso behavior. All player animation must obey plausible human joint movement,
without body deformation or illogical poses. Reference intent: a fluid low-barrier
vault in an action game. Preserve five seconds and the slide/seat sequence.
Flatten projecting ladder rungs/treads to reduce forearm intersections. The user
clarified that visible neck/shoulders obstruct FIRST PERSON, not outside review.
Treat local first-person visibility separately from the complete third-person body.

Implemented feedback pass: rigid torso lean below 50 degrees, push-off/release
before the ladder catch, pelvis-relative knee solve without sideways hip twist.
An obsolete rung plant during the slide transition was removed. Local first-person
rendering omits torso/jacket and armor collar, preserving arms/legs and the full
third-person model. `check_rear_anatomy.py` guards vault hip/knee ranges, not artistic
quality. The flatter one-pixel rungs and revised hand targets have no sampled
arm/ladder intersections during slide frames 54–81. Five-second timing retained.

Third-person chair approach camera correction (2026-10-13): user approves the
performance as nearly complete and requests only the obstructed outside camera
fixed. Move the side-wall shot inward over the aisle and blend into it at ticks
60–66. Gameplay and review capture share `SubmarineEntryCamera.reviewEye` so
future preview edits cannot diverge from the in-game camera. First-person and
player animation are unchanged by this camera-only pass.
