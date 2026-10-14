# Grand Yacht — reusable design prompt

Design a humongous, luxurious player-owned yacht for this Minecraft mod. Its primary purpose is to be a prestigious place to own, hang out with friends and host parties. Players should be able to freely walk around the vessel, go upstairs and downstairs, and explore a basement-like lower deck.

Give it spacious outdoor party decks, furnished indoor lounges, dining and bar areas, guest bedrooms, an upper pool terrace and a rooftop sun deck. Connect occupied levels with proper staircases and clear landings. Keep rooms, doors, passageways and furniture usable at normal Minecraft player scale. A starting scale to explore is roughly 60 blocks long and 17 blocks wide, subject to layout and technical validation.

Place an NPC captain in an accessible bridge. The player walks up and right-clicks the captain to open a world-map menu, selects a destination, then the captain slowly sails the yacht there. Players remain free to walk around and socialize during the journey. The experience should emphasize enjoying life aboard rather than manually steering or racing. For eventual implementation, resolve navigable-water destination validation, routes around land, stopping behavior and multiplayer movement aboard the vessel.

Match ART_DIRECTION.md and its actual-model references: chunky cuboids, stepped silhouettes, broad readable panels, charcoal structural elements, blue pixel glass, restrained trim and crisp low-resolution pixel textures. Interpret these shapes as a luxury yacht with a light painted shell, dark hull, warm timber decking and warm lighting. Preserve a coherent maritime palette rather than copying another vehicle's paint scheme.

Produce a large three-quarter exterior concept, matching top and side references, a cutaway explaining the interior levels, and a captain/map interaction vignette. Make the vessel feel enormous using normal-size players for scale. Prioritize generous habitable space and a distinctive yacht silhouette.

## Concept handoff

- `concept.png`: initial generated concept sheet, awaiting user feedback.
- `concept-prompt.md`: exact image-generation prompt, generated with the built-in imagegen tool.
- No model or gameplay implementation is included.
- Generated projections are illustrative and must be reconciled before modeling. In particular, the side cutaway's vertical “17 blocks wide” dimension is misplaced: 17 blocks refers to proposed beam, not height. Bridge/roof level alignment and machinery-bay depth also need resolution in a consistent plan.
