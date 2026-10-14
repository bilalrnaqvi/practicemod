# Upstream rig

`emote_creator_bend.blend` is the legacy articulated player rig from
[KosmX/emotes](https://github.com/KosmX/emotes/blob/dev/blender/legacy/emote_creator_bend.blend),
downloaded 2026-10-10. Git blob: `01a6be6d130b35b3e1323dae3f1cccdafe2b5abe`.
The repository's GPL-3.0 license is preserved in `LICENSE.emotecraft.txt`.
The original rig remains unchanged here. Its embedded scripts are not automatically
executed by the build workflow.

The derived `src/main/blender/deepseeker_entry.blend` adds the mod's vehicle,
explicit boarding poses, native Blender IK contact controls, an armature-driven
continuous skin preview, mechanical animation and camera staging. Its generating
and exporting source is provided in the parent directory.

The `emote_creator_bend_item_scale.blend` download was inspected during compatibility
research but is not used by this workflow. The current upstream main rig targets
newer Blender/PAL versions; the legacy rig was selected for the 1.21.1 export path.
