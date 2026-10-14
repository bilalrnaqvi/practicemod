# Canonical sources and export map

Paths are repository-relative. An exporter converts an existing source; an authoring/revision
builder reconstructs it and may replace manual edits. Prefer the exporter after editing art.
Generated Java geometry and runtime textures are committed alongside their editable inputs.

| Feature | Canonical editable source | Exporter (run from repository root) | Runtime geometry / notes |
| --- | --- | --- | --- |
| White Horizon yacht (static art) | `src/main/blockbench/white_horizon/white_horizon.bbmodel` with embedded pixel tiles and metadata | `python3 tools/yacht/export_white_horizon.py` | `src/main/yacht/white_horizon/`: OBJ/MTL, scene, layout, collision and render-section metadata; [model handoff](yacht/WHITE_HORIZON_MODEL.md). No runtime vehicle yet. |
| Jetpack | `src/main/blockbench/SCAVPACK_MK1.bbmodel` | `node tools/generate_scavpack_geometry.mjs src/main/blockbench/SCAVPACK_MK1.bbmodel > src/main/java/com/bilal/practicemod/ScavpackMk1Geometry.java` | `ScavpackMk1Geometry.java`; flight/pose/HUD code remains hand-authored |
| Umbrella glider | `src/main/blockbench/mechanical_octagonal_glider.bbmodel` | `node tools/generate_umbrella_glider_geometry.mjs src/main/blockbench/mechanical_octagonal_glider.bbmodel src/main/resources/assets/practicemod/textures/entity/umbrella_glider > src/main/java/com/bilal/practicemod/UmbrellaGliderGeometry.java` | `UmbrellaGliderGeometry.java` |
| Rocket boots | `src/main/blockbench/rocket_boots_mk1.bbmodel` | `python3 tools/generate_rocket_boots_geometry.py` | `RocketBootsGeometry.java` |
| Compact Racer | `src/main/blockbench/compact_racer.bbmodel` | `python3 tools/generate_jetski_geometry.py` | `JetskiGeometry.java`; `JETSKI.md` |
| CRAG | `src/main/blockbench/crag.bbmodel` | `python3 tools/generate_atv_geometry.py` | `AtvGeometry.java`; `ATV.md` |
| Skateboard | `src/main/blockbench/alien_skateboard.bbmodel` | `python3 tools/generate_skateboard_geometry.py` | `SkateboardGeometry.java`; `SKATEBOARD.md` |
| Mechanical Dolphin | `src/main/blockbench/mechanical_dolphin.bbmodel` | `python3 tools/generate_mechanical_dolphin_geometry.py` | `MechanicalDolphinGeometry.java`; `MECHANICAL_DOLPHIN.md` |
| Deepseeker | `src/main/blockbench/deepseeker.bbmodel` plus `deepseeker_screen_bindings.json` in the same directory | `python3 tools/generate_deepseeker_geometry.py` | `DeepseekerGeometry.java`, cabin rig and panel attachments; `DEEPSEEKER.md` |
| Deepseeker boarding | `src/main/blender/deepseeker_boarding_runtime.blend` | Blender: `tools/blender/export_boarding_runtime.py` on the open canonical scene | PAL asset `src/main/resources/assets/practicemod/player_animations/deepseeker_entry.json`, `BlenderBoardingTrack.java` and renderer reference JSON |

Java paths above are under `src/main/java/com/bilal/practicemod/`. Model exporters generally
extract embedded atlases into `src/main/resources/assets/practicemod/textures/`.
Do not run exporters for unrelated vehicles just to complete a task.

## Boarding sources and review

`tools/blender/build_rear_boarding.py` intentionally rebuilds choreography using the tracked
`src/main/blender/baselines/deepseeker_before_rear_entry.blend`. Exporting an edited scene
is a different operation. See [boarding instructions](animation/BOARDING.md).

- Current local review: `output/animation/boarding-runtime/review.html`.
- Minecraft render check: `./gradlew runClient -PanimationRenderCheck`.
- Full-hull camera review: `./gradlew runClient -PsubmarineModelReview`.
- Required renderer fixture: `output/animation/boarding-runtime/runtime-reference.json` (tracked; updated by exporter).
- Gameplay and preview share `SubmarineEntryCamera.reviewEye` for the outside camera.

Old `deepseeker_entry`, `outline_entry*`, staged Blockbench variants and model archives are
historical sources, not replacements for the canonical model/scene. Old scripts such as
`author_deepseeker_expedition.py`, compaction scripts and `build_boarding_runtime.py` can
restore obsolete designs. The expedition script requires an explicit legacy override;
other historical builders must likewise be used only for deliberate reconstruction.

## Verification by change

| Change | Check |
| --- | --- |
| Code, runtime resources, dependencies, build configuration | `./gradlew verifyProject` |
| Documentation only | Verify referenced paths, commands and current-state claims |
| Boarding pose/export | Core verification plus Blender body/anatomy/rotation diagnostics and Minecraft renderer review from the boarding guide |
| Vehicle model/player fit | Relevant scoped preview/fit checks and actual in-game inspection; these older utilities have individual prerequisites |
| Camera, HUD, transparency or lighting | Actual affected first/third-person views; compilation/server tests cannot establish appearance |
| Networking or passenger ownership | Relevant GameTests plus two-client behavior when assessing real latency/presentation |

`verifyProject` runs the existing GameTests and PAL validator, not every standalone tool.
Some historical geometry audits target old layouts, and some preview utilities contain
macOS-specific Java paths. Check their scope before using their result; do not turn the
whole tools directory into a mandatory test suite.

## Reference and history storage

`docs/art-references/` contains curated visual references. The Deepseeker image is explicitly
an older exterior style reference; its hatch location is not current. `ART_DIRECTION.md`
identifies the current constraints. `docs/history/` preserves prior decisions verbatim under
an archive notice. Old output Markdown preserves provenance but may point to local-only media.
No generated media or historical experiments were deleted during the workflow cleanup.
