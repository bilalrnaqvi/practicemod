# Current animation direction

## Performance requirements

Author deliberate, editable performances with the actual player rig and vehicle together.
Use Blender and standard PAL clips for Deepseeker player animation. Procedural simulation
remains appropriate for wheels, suspension and vehicle attitude. A different playback library
or smoother interpolation alone does not establish better acting.

Movement must read as plausible human motion: fixed limb lengths, supported weight transfer,
reachable contacts, continuous joint orientation and no implausible torso deformation.
Adjust root/targets/staging rather than stretching limbs. Review the difficult beat early;
then validate contacts, mesh clearance, export and playback. Numeric checks are not visual approval.

Keep root, player pose, hatch, chair and camera on the same server-owned sequence clock.
Review first person separately from third person with the complete gameplay hull. Cutaways
are diagnostic views. Check wide/slim skins, clothing, armor, pose restoration and cancellation
when changes affect them; remote-player presentation requires a multiplayer review.

## Current Deepseeker baseline

- Rear-corner hatch/ladder; **100 ticks / five seconds**.
- Supported human vault, fireman-style ladder slide, brisk chair approach and retained swivel.
- Plausible hip/knee motion, rigid torso lean below 50 degrees in the current opening.
- First person follows authored gaze and shows arms/legs and limb armor, omitting head,
  torso/jacket and armor collar. Third person retains the complete body.
- Third-person chair-approach view stays over the aisle and blends at ticks 60–66;
  gameplay and review share `SubmarineEntryCamera.reviewEye`.
- User described the performance as nearly complete before the final camera correction.
  Do not infer final visual approval from passing tests.
- Cabin exit/reseating reuses the chair performance; preserve that integration.

Canonical scene: `src/main/blender/deepseeker_boarding_runtime.blend`.
Revision builder: `tools/blender/build_rear_boarding.py`; its required historical input is
`src/main/blender/baselines/deepseeker_before_rear_entry.blend`.
Export the open canonical scene with `tools/blender/export_boarding_runtime.py`.
Rebuilding from the baseline is a deliberate choreography operation, not the default way
to export a manual scene edit. Never overwrite hand edits by casually rerunning a builder.

PAL Core 1.1.6, mochafloats 4.1.0, Javassist and bendy-lib 5.1 are integrated through existing
render hooks. Do not add full PAL player mixins without checking conflicts. Matrix-based
axis conversion and clothing/armor deformation must be retained. GeckoLib is not adopted.

The Mechanical Dolphin now uses speed-blended fin trim and a two-joint vertical tail stroke;
the old exact prototype-tail preservation requirement is superseded. See `MECHANICAL_DOLPHIN.md`.

## Validation and review

Run `./gradlew verifyProject` for runtime changes. Animation/source changes also need the
scoped Blender diagnostics and Minecraft renderer checks in `docs/animation/BOARDING.md`.
Report their scope honestly: head clearance and sampled contacts are not universal armor,
terrain or multiplayer collision certification. Use current reports instead of copying old test counts.

Earlier studies, durations, rejected motion and task-specific approval stops are archived in
`docs/history/ANIMATION_DIRECTION-through-2026-10-13.md`. They are not active instructions.
