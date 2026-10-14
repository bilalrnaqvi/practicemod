# Project working guidance

- Target Minecraft 1.21.1, NeoForge 21.1.235 and Java 21. Use Mojang names with the configured Parchment supplement; check version-matched APIs when changing integration code.
- Read only the guidance relevant to the task: `ART_DIRECTION.md` for new or changed art, `ANIMATION_DIRECTION.md` for animation/camera work, and the corresponding vehicle document for gameplay. `docs/SOURCE_MAP.md` identifies canonical inputs and exporters.
- Before designing new vehicle/equipment art, inspect the relevant reference images. Keep the mod's visual family while preserving each vehicle's palette.
- Edit canonical sources and regenerate derived assets together. Historical authoring scripts can overwrite newer designs; use the source map, not commands in archived notes.
- For code, runtime assets, dependencies or build changes, run `./gradlew verifyProject` (add `--offline` when dependencies are cached). Run relevant geometry or renderer checks for art/animation changes. Documentation-only edits need link/path checks, not a game launch.
- Keep iteration focused on the requested change. Preserve accepted geometry, movement and camera behavior outside that scope. Review a risky movement or layout early before polishing a whole sequence; do not add approval stops when the user has authorized completion.
- Automated checks establish their tested properties, not visual quality or user approval. Report build/test results separately from in-game, first-person and multiplayer review.
- Update current guidance in place when decisions change. Move obsolete material to `docs/history/`; history is provenance, not active instructions. Do not carry task-specific authorizations or stop points into future tasks.
- Finish with a short handoff: what changed, checks run, remaining limitations and a review location. Keep meaningful source checkpoints; exclude logs, caches and generated review frames. Never include unrelated concurrent work in a checkpoint.
