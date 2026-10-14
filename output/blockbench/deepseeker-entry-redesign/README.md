# Deepseeker entry redesign — model pass 1

**REJECTED BY USER:** the relocated ladder blocks access to the bed. Do not
integrate this layout. Its passing descent-corridor check did not evaluate
furniture use or cabin circulation. The next layout study moves hatch and ladder
to the rear, preferably the starboard corner, while preserving bed, desk and
storage access. The user is open to reversing the lid swing and questions the
viability of the concept's diagonal ladder. Roof and mast clearance still need
design work. Keep this model only as a record of the rejected pass.

Staged editable source: `src/main/blockbench/deepseeker_entry_redesign.bbmodel`.
Baseline remains `deepseeker.bbmodel` and the integrated 6.4-second animation.
This proposal is not installed in Minecraft and has not received visual approval.

The user rejected the cut-off ladder, starting support, lid/head collision and
floaty hatch-to-ladder transfer. They explicitly authorized repositioning the
ladder for a convincing entry. Preserve the chair approach and swivel for now;
actual first-person boarding is required in a subsequent small iteration.

This pass replaces the offset truncated ladder with ten evenly spaced rungs
directly beneath the hatch, on its starboard edge. Rails reach Y86.3 just below
the Y87.45 sill. A floor landing connects to the existing aisle. The low octagonal
lid and hinge remain; its handle moves to the starboard side next to a low tread
ledge. One ceiling conduit moves four model pixels outward to clear the ladder.
The former ladder landing grate is removed. Furniture, chair and textures remain.

`model-review.png` contains static outside, cutaway and cabin inspection views.
These are software renders of the actual staged geometry, not first-person
gameplay or proof of a finished performance. The cutaway intentionally removes
the hull. `entry-contacts.json` records proposed contacts and the staging route.

`clearance.json` checks a 0.6-block descent corridor, a proposed 8×10×8-pixel
starting head envelope and the lid sweep in 2-degree increments. All pass.
Original hinge contacts and up to .8-pixel closed seal overlap are allowed.
Cube SAT is supplemented with convex-mesh narrow phase for the sloped tail.
These checks do not validate a crouched player, arm reach, an articulated vault,
loaded feet, or the full animated body. Test those with the actual rig next.

Next bounded animation work: establish supported starting pose and handle reach;
then rim hand plants, feet-first vault, loaded first rung, alternating descent,
and planted floor step-off. Rejoin the retained chair approach. Review each in
motion, and author/inspect first person independently of the outside review view.

Regeneration:

```sh
python3 tools/design_deepseeker_entry.py
python3 tools/check_deepseeker_entry_redesign.py
python3 tools/preview_deepseeker_expedition.py src/main/blockbench/deepseeker_entry_redesign.bbmodel output/blockbench/deepseeker-entry-redesign/closed 0
python3 tools/preview_deepseeker_expedition.py src/main/blockbench/deepseeker_entry_redesign.bbmodel output/blockbench/deepseeker-entry-redesign/open 90
mkdir -p build/entry-redesign-preview
javac -d build/entry-redesign-preview tools/PreviewDeepseeker.java tools/PreviewDeepseekerExpedition.java tools/PreviewDeepseekerEntryRedesign.java src/main/java/com/bilal/practicemod/DeepseekerGeometry.java src/main/java/com/bilal/practicemod/DeepseekerPilot.java
java -Djava.awt.headless=true -cp build/entry-redesign-preview com.bilal.practicemod.PreviewDeepseekerEntryRedesign
```
