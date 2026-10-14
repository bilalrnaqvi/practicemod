# Current status: approved and integrated

The user approved the thicker storage and cabin on 2026-10-08. `src/main/blockbench/deepseeker.bbmodel` is now the in-game source; the expedition file is its synchronized editing copy. Earlier staged-only notes below are historical. The game export includes both texture sheets, the animated side hatch, updated ladder boarding route, open cockpit connection, and gauge bobble. Free walking and storage inventories remain future work. See `DEEPSEEKER.md` for current rebuilding and validation instructions.

# Deepseeker expedition interior — Blockbench revision

The approved same-side entry concept is modeled in `src/main/blockbench/deepseeker_expedition.bbmodel`. This is a complete submarine revision with embedded textures, editable cuboids and shaped meshes, named furniture groups, and a separately pivoted hatch lid. The original runtime source `deepseeker.bbmodel` and gameplay code are unchanged.

`deepseeker-expedition-cutaway.bbmodel` is an inspection copy: exterior elements are hidden, the hatch lid is opened 105 degrees, and the furnishings remain editable. Edit the full revision as the authoritative model; the cutaway is regenerated from it by the authoring script.

## Included

- Starboard interior ladder, offset hatch shaft, raised coaming, upper exterior boarding ladder, transfer sill and handholds. The mast moves to port to clear the shaft.
- Teal bunk with pillow, strap, hinges and two under-bed drawers.
- One combined chart/repair workstation with drawers, painted chart, diagnostic monitor, secured tools and vise.
- Two air bottles, retaining bands, manifold and gauge.
- Rear-center expedition cases and emergency kit.
- Grated deck, central aisle, ladder landing, ribs, lights and overhead pipes.

674 total model elements (657 cuboids and 17 meshes), including 265 newly authored elements. Existing pilot-seat and control elements are preserved. New surfaces use a small embedded 512-square texture alongside the original embedded atlas. `model-preview.png` is a software render of the actual model, not generated concept art or an in-game screenshot.

## Validation and next implementation step

`tools/validate_deepseeker_expedition.py` checks dimensions, texture references, complete outliner membership, preserved pilot controls and 141 standard standing-player envelope samples through the central aisle, landing and open-hatch vertical descent. The clearance envelope is 0.6 by 1.8 blocks. These static samples do not validate articulated limbs, the full usable floor area, the exterior transfer, hatch movement or gameplay collision.

The hatch/climb coordinates differ from the current runtime animation. Integrate geometry, both textures, the hatch pivot and boarding choreography together in the later gameplay step. Do not run the existing single-atlas exporter against this revision unchanged. Free walking, water exclusion, exterior climb fit and hatch animation remain to be implemented and tested. The bunk hinge groups are modeled details; no folding animation is authored.

## Reproduction

From the project root:

```sh
python3 tools/author_deepseeker_expedition.py
python3 tools/validate_deepseeker_expedition.py
python3 tools/preview_deepseeker_expedition.py
javac -d build/deepseeker-preview src/main/java/com/bilal/practicemod/DeepseekerGeometry.java src/main/java/com/bilal/practicemod/DeepseekerPilot.java tools/PreviewDeepseeker.java tools/PreviewDeepseekerExpedition.java
java -Djava.awt.headless=true -cp build/deepseeker-preview com.bilal.practicemod.PreviewDeepseekerExpedition
```

Authoring deterministically rebuilds the revision from the original submarine and overwrites the revision/cutaway. Preserve manual edits before rerunning it. Repeated authoring was verified byte-identical.

## Quality refinement

The first pass was rejected for looking crude beside the exterior. The current revision replaces stretched flat color tiles with face-sized pixel materials, a readable bathymetric chart and diagnostic screen; adds layered window reveals, reinforced storage, recessed drawer fronts, padded bedding and secured tools; replaces square air bottles with eight-sided tapered cylinders; and fits the hatch apron to the sloped hull. Existing panels that occluded the side windows are removed.

`deepseeker-expedition-refined.bbmodel` is a fresh inspection copy for Blockbench. `interior-refined.png` is a close interior render of the current mesh. `first-pass.bbmodel` and `first-pass-preview.png` preserve the rejected version for comparison. Static clearance now includes conservative bounds for shaped meshes. Mesh winding and positive enclosed volumes were also checked.

## Intersection cleanup and gauge idle animation

Furniture backs and supports now fit inside the lower hull bevel; the air assembly is lifted above the bevel; window-adjacent desk fittings are inset; and ribs sit between furniture bays. The rear storage is a single two-door locker and medical case, reduced from 258 to 24 quads. Two simple roof conduits replace the separate pipes/clips/spine assembly. `simplification.json` records the affected groups' before/after geometry counts. Intentional joints within an assembly remain joined.

The gauge uses a separate mesh needle (cuboid), pivoted about the dial's normal. The painted needle was removed from the texture. Play `animation.deepseeker.air_supply_idle` in Blockbench's Animate workspace: it loops over 2.4 seconds at +/-2 degrees. `air-gauge-bobble.gif` previews this actual saved animation. The animation is authored in the Blockbench files; runtime integration remains pending with the rest of the cabin.

Validation now checks furniture against other assemblies and the rotated hull, as well as the original 141 player-envelope samples. An additional 81 angular sweep samples check the needle against its dial and bezel. The full-model and cutaway files both contain the looping animation.

To refresh the animated preview after baking with `preview_deepseeker_expedition.py`:

```sh
javac -cp build/deepseeker-preview -d build/deepseeker-preview tools/PreviewDeepseekerGauge.java
java -Djava.awt.headless=true -cp build/deepseeker-preview com.bilal.practicemod.PreviewDeepseekerGauge
```

## Concept-style storage restored

The latest user request supersedes the single-locker simplification. The current rear storage comprises a large dark supply case, a reinforced equipment case, an olive field case and a separate red medical kit, matching the approved concept's stacked expedition cases. There are 29 storage cuboids (174 quads), below the earlier 43-part/258-quad stack.

Each supply case has a hollow body and a separately grouped front-access panel with its own hinge pivot. This preserves the case appearance while allowing future access without an upper case blocking a top lid. Seven sampled poses per case, from closed to 90 degrees open, clear the surrounding model and other cases. These are modeling checks, not implemented gameplay. `storage-bindings.json` records stable group IDs, pivots and bounds for later inventory/interact code.

The medical case remains its own interaction target. The two ceiling conduits and air-gauge idle animation are retained.

## Rugged storage rebuild

The user rejected the narrow, flat-front case stack. The latest geometry uses a broad 22-unit lower supply case, a smaller offset equipment case, a full-width olive upper case, and a handled red medical kit beside the middle case. Thick beveled lids, recessed fronts, substantial central ochre straps, modeled buckles, reinforced corner guards and carrying handles follow the approved concept. Supporting skids seat the upper cases on the lids. `storage-refined.png` is a close render of the actual current geometry.

Separate hollow bodies, front-access groups and stable interaction bindings are retained. Closed-model, player-clearance, gauge-motion and sampled storage-opening checks all pass. The gauge animation and simplified ceiling conduits remain unchanged.

## Thicker concept cases

The current revision increases supply/equipment/olive case depths to 10.6/9.9/9.1 units, with 1-unit walls, thicker beveled lids, wraparound lid shoes, wider straps and deeper buckles. The medical case is also deeper. Total model: 688 elements. The approach aisle now ends at z=0 ahead of the deeper storage fronts; all 137 standing-player samples including the unchanged ladder landing and climb pass. Furniture/hull separation, gauge sweep, and all three front-opening sweeps pass. Stable case groups remain ready for future gameplay work. `storage-before-thickness.bbmodel` preserves the prior model. Runtime integration remains pending.
