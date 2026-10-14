# Rear-corner entry model review

The user approved the rear-corner layout diagram. This is the resulting staged
Blockbench geometry for review, not an integrated gameplay or animation update.

Model: `src/main/blockbench/deepseeker_rear_entry.bbmodel`.
Review: `model-review.png`; larger interior view: `cabin-access.png`.

The hatch sits over the aft starboard corner, beyond the bed end and beside the
storage. Its original low octagonal lid opens forward. A shaped ochre collar
joins the sloped shoulder, with an actual opening through the roof and liners.
The former roof opening is filled; the antenna assembly moves to port for lid
clearance. A stern tread provides the proposed starting support. Ten continuous
ladder rungs lead to a shallow landing step, then the central aisle. Furniture,
chair and texture atlases are preserved.

`clearance.json` reports passing static checks for the descent corridor, proposed
start-head envelope, sampled lid sweep, bed/desk/storage approach boxes, sampled
walking route, and storage-panel sweeps against the entry fittings. These are
geometry checks, not proof of articulated player clearance, reachable hand/foot
poses, final walkability, or full hull watertightness. `entry-contacts.json` holds
the proposed support coordinates for the next animation pass.

Build and check from the repository root:

```sh
python3 tools/design_deepseeker_rear_entry.py
python3 tools/check_deepseeker_entry_redesign.py src/main/blockbench/deepseeker_rear_entry.bbmodel output/blockbench/deepseeker-rear-entry
```

The model was loaded in Blockbench. The review images are model inspection
cameras, including an interior inspection view; they are not first-person
gameplay. The integrated 128-tick clip and camera remain the baseline. Next is a
small supported start/open/vault/ladder-contact study, preserving the later chair
approach and swivel. Actual first-person boarding still needs implementation.

## Adopted during animation completion

The user subsequently authorized completing the animation and integration without
further reviews. This model is now canonical/runtime geometry. The tread lips
extend slightly toward the climber for toe support; static access checks still
pass. The predecessor is archived in
`src/main/blockbench/archive/deepseeker_before_rear_entry.bbmodel`, which is also
the model builder's stable input. Current animation, first-person behavior and
validation scope are in `output/animation/boarding-runtime/README.md`.
