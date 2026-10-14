> ARCHIVE: historical decisions and reports through 2026-10-13. Not active instructions or safe current rebuild commands. Paths in the original text are repository-relative.

# Mod art direction — persistent project memory

Recorded 2026-10-07 at the user's request: new concept art should reference the design language of the mod's existing art.

## Shared visual language

- Minecraft-native, Blockbench-buildable forms: chunky cuboids, stepped silhouettes, and occasional rotated cuboids for slopes or faceted/octagonal mechanical forms.
- Clear functional silhouettes and deliberately sized components. Broad body panels, readable seating and controls, dark mechanical bases, restrained trim. Keep small details subordinate to the vehicle's main shapes.
- Crisp low-resolution pixel textures, sparse seams and purposeful wear. Avoid photorealistic materials, smooth sculpted surfaces, fine noise, dense greebles, and gratuitous bevels.
- Contrast painted shells with charcoal seats, bumpers, joints, frames, and machinery. Small steel accents and functional lights are appropriate; use vehicle-specific accent colors.
- Concept sheets should show a large three-quarter view and matching orthographic references. Concept projections are guidance; reconcile inconsistencies when modeling.

## References and their authority

1. `output/compact-racer/model-preview.png` — inspected actual-model preview: stepped white shell, graphite lower hull/bumper, short charcoal saddle, simple T handlebars. Source: `src/main/blockbench/compact_racer.bbmodel`; implementation notes: `JETSKI.md` (47 cuboids, 128×128 texture).
2. `build/deepseeker-full-current.png` — inspected existing model preview: ochre painted machinery, charcoal faceted frames, blue pixel glass, warm square lamps and small cyan mechanical accents. Build outputs may be regenerated; source: `src/main/blockbench/deepseeker.bbmodel`, with implementation history in `DEEPSEEKER.md`.
3. `output/imagegen/submarine-concepts/01-refined-industrial.png` — inspected exploratory concept: cohesive panel grouping and integrated equipment. Treat as inspiration, not evidence that all depicted refinements were implemented or approved.
4. `output/jetski-concepts-v2/HANDOFF.md` — prior art prompts and modeling guidance: modest cuboid counts, stepped edges, minor details painted into textures. Actual models take precedence over generated concept embellishments.

## ATV direction from this task

User clarification: the desired vehicle is a dune buggy / ATV hybrid — dune buggy design language, particularly overhead roll-cage bars, with an ATV's compact form factor suited to Minecraft. The initial conventional straddle-seat quad concept did not capture this intent.

Revised proposal: one centered low bucket seat, steering wheel, open square-bar overhead roll cage, short wheelbase, oversized faceted tires, squared ochre fenders, charcoal chassis, and warm rectangular headlights. The single-seat layout and palette are design proposals, not explicitly approved requirements. Keep the cockpit compact; avoid stretching it into a large two-seat UTV. Carry across the family geometry and material hierarchy; do not copy submarine-specific features. No vehicle model or gameplay implementation is implied by the concept.

Concept image and exact generation prompt are stored under `output/imagegen/atv-concepts/`.

Latest preference: the user requested more designs like `output/imagegen/atv-concepts/04-rock-hopper.png`. Use that compact, high-clearance, chunky ochre crawler as the preferred exploration direction; this is not yet approval of a final design.

## Approved Deepseeker expedition interior

The user approved `output/imagegen/submarine-interior-approval/02-side-entry.png` on 2026-10-07. This supersedes the rear-center ladder in `01-expedition-interior.png`. Approval is of the concept; model, animation, and walkable-interior implementation remain future work.

- Align the interior ladder and hatch transfer with the exterior starboard boarding route: climb outside, cross the hatch sill, descend inside on the same side, then step onto a clear landing connected to the cockpit aisle.
- Preserve the teal bunk with cream pillow, under-bunk storage, combined chart/repair workstation, and dual strapped air tanks with manifold and gauge.
- Place the ladder in the aft starboard entry bay beyond the bunk; relocate the stacked expedition cases to the rear center. Keep the air supply opposite the ladder and the combined workstation opposite the bunk.
- Maintain a clear central aisle with the bunk deployed; keep furniture, pipes, and structural beams out of the climb and step-off spaces. Plan for later walkability and validate player headroom, collision, hatch geometry, and animation clearance during implementation.
- Starboard appears on the left in the concept's aft-facing interior view and in its plan with aft at top. Reconcile generated projection inconsistencies against the actual model before authoring geometry.

The exact built-in image-generation edit prompt is saved in `output/imagegen/submarine-interior-approval/02-side-entry-prompt.md`.

Blockbench modeling follow-up: the approved layout is now built as the staged full-submarine revision `src/main/blockbench/deepseeker_expedition.bbmodel`. Actual-model preview, cutaway inspection copy, and implementation handoff are under `output/blockbench/deepseeker-expedition/`. The runtime model and movement code have not yet adopted this revision; the earlier note that modeling is future work is superseded. Static player-envelope checks pass, while animation and walkability remain future work.

Interior quality feedback: the user rejected the first Blockbench interior as much lower quality than the exterior. Preserve the approved layout, but match the exterior's layered construction, defined frames, shaped equipment and purposeful pixel detailing. Avoid slab furniture, square placeholder tanks, stretched flat textures and coarse stepped hatch aprons. The refined revision adds those structural/material details; `output/blockbench/deepseeker-expedition/interior-refined.png` shows the actual updated interior. This refinement has not yet received user approval.

Follow-up: the user flagged intersecting fittings and asked for simpler rear storage and overhead piping, plus a slight air-supply gauge needle bobble. The staged model now has fitted furniture boundaries, one rear locker with a medical case, two ceiling conduits, and the looping Blockbench animation `animation.deepseeker.air_supply_idle` (+/-2 degrees over 2.4 seconds). Keep these areas simple; preserve detail through textures rather than piling on overlapping trim. Gameplay integration remains pending.

Latest storage correction: the user wants the rear storage to match the concept's stacked expedition cases because it will become usable later. This supersedes the single rear locker. The model now has three separate supply cases (large dark, reinforced gray, olive) plus a red medical case, named interaction groups and independently pivoted front-access panels. Front access allows opening while stacked. `storage-bindings.json` in the model output folder records future interaction targets. Inventories and opening gameplay are not yet implemented; the simplified ceiling pipes and gauge bobble remain.

Storage quality correction: the user also rejected the narrow, flat-front stack. Prioritize the concept's broad staggered cases, heavy dark lids, central ochre straps/buckles, corner protection, and handled red medical kit beside the middle case. These defining shapes must be modeled, not replaced with flat decals. The current rebuilt storage is shown in `output/blockbench/deepseeker-expedition/storage-refined.png`; user approval is still pending.

## Implemented CRAG ATV

On 2026-10-07 the user approved the supplied B / CRAG concept for modeling and
implementation. `src/main/blockbench/crag.bbmodel` is the editable model and
`output/crag/model-preview.png` shows the actual mod mesh. Preserve its green (`#1F4700`)
panels, dark bucket seat and square roll cage, chunky tires, and warm headlights.
This supersedes the exploratory/unapproved status of the ATV concepts above.

CRAG feedback: the user rejected the first model's tall, thin proportions and
muddy mustard paint. They specifically value the concept's transformer-like
character: layered angular armor, heavy shoulders, chunky tires, exposed dark
mechanics. Preserve those shapes when refining.

Latest CRAG palette instruction: base the color scheme on **#1F4700**. This
supersedes the golden-orange concept palette. Use #1F4700 body panels, #336310
highlights, #132E00 shadows, charcoal mechanics, steel trim and warm headlights.

The user approved the refined #1F4700 CRAG shown in `output/crag/detail-preview.png`
and requested implementation. That 516-cuboid model with fitted panel textures,
recessed wheel hubs, cage clamps and cockpit details is now the accepted CRAG
design. Use `src/main/blockbench/crag.bbmodel` as its editable source.

Deepseeker storage thickness feedback: user still wanted closer concept proportions and thicker cases. The staged cases are now 10.6 / 9.9 / 9.1 units deep (previously 7 / 6.3 / 5.6), with thicker shell walls, deeper beveled lids, wraparound lid corner shoes and wider ochre straps carried over the front lid rim. Keep the ladder landing clear. This revision remains subject to visual feedback.

Approval and integration (2026-10-08): user accepted the thicker storage and authorized pushing the design through. The expedition cabin is now the canonical runtime model in `src/main/blockbench/deepseeker.bbmodel`, with matching `deepseeker_expedition.bbmodel`. The side hatch/ladder boarding route and live gauge bobble are integrated; the hatch grip is moved outward for access. This supersedes earlier pending-approval/staged-only notes. Free walking and storage inventories remain future work.


CRAG scale clarification (2026-10-09): the user chose realistic full-size
proportions beside Steve instead of literal 2× scaling, and authorized adjusting
the seat. The implemented revision keeps the approved green shape language and
textures while lengthening the wheelbase, narrowing the stance, raising/widening
the bucket seat and fitting the cushion, controls, footrests and cage to the
actual vanilla player mesh. Current dimensions are approximately 2.53 × 1.57
blocks with a 2.32-block cage top. `output/crag/steve-scale-preview.png` is the
current actual-model/player comparison; the older detail preview records the
previously approved paint/detail design. The new proportions are implemented
and checked, but have not yet received the user's visual feedback.

## Mechanical Dolphin / Panorama modeling (2026-10-09)

The user requested modeling the supplied blue-and-white Panorama one-player
submersible concept, with its glass canopy prepared for a separate later door
animation task. The editable staged model is
`src/main/blockbench/mechanical_dolphin.bbmodel`; actual-model views and a static
open-canopy inspection copy are under `output/blockbench/mechanical-dolphin/`.
Preserve blue armor, white belly, cyan lights, prone cockpit, swept fins and
jointed tail. The canopy has a dedicated rear hinge group and separate lift
struts. No door animations or runtime integration were added in this modeling
pass. This initial model remains subject to the user's visual feedback.

Eye/tail refinement (2026-10-10): user requested more distinctive eyes and
preservation of the prototype tail animation. The staged model now has swept
blue brows, recessed dark optical sockets, cyan irises with black pupils and
white glints, clearly distinguished from the small rectangular running lights.
Native `tail_idle` / `tail_swim` clips reproduce the prototype's whole-tail
vertical sine stroke (3° / 21°, 0.654498-second cycle), with the X sign converted
for this model's opposite forward direction. No door animation was added;
gameplay still uses the prototype until the staged art is integrated.

Latest eye correction (2026-10-10): user prefers the earlier eye appearance.
The cyan iris/pupil and large brow experiment is superseded. Restore the original
slim cyan rectangular sensor lenses in compact dark housings; keep the bright
horizontal cores and avoid anthropomorphic pupils or prominent eyebrows. The
prototype idle/swim tail clips remain unchanged.


CRAG two-seat correction (2026-10-11): the user said the previous revision was
still too small and requested seating for two. This supersedes the earlier
single-seat/compact-footprint constraint. The implemented buggy now has two
side-by-side fitted bucket seats, an offset steering wheel, passenger grab rail,
wider hood/cage and larger tires, retaining the approved green paint and layered
forms. Current actual-model preview: `output/crag/two-seat-preview.png`.
Dimensions are about 3.12 × 2.58 blocks, with the cage top at 2.40. Two-player
mounting, driver-only controls and driver handoff are implemented. This revision
has passed automated tests and mesh-fit checks; visual feedback is still pending.


CRAG compactness correction (2026-10-11): user found the two-seat buggy too wide
and requested a more compact exterior while retaining room for two. Current
revision narrows the exterior to 2.19 blocks (from 2.58), with full-size fitted
seats and their existing spacing preserved. Keep this narrower body, tire track
and cage rather than reverting to the wide two-seat silhouette. Current preview:
`output/crag/two-seat-preview.png`. Automated fit and gameplay checks pass;
user visual feedback on this correction is pending.

Mechanical Dolphin integration (2026-10-11): the user authorized pushing the
approved Panorama model into Minecraft. Runtime now uses its 219 parts and
embedded 1024px texture, original slim cyan eyes, translucent closed canopy,
full-size prone pilot and the exact prototype tail stroke. The source remains
`src/main/blockbench/mechanical_dolphin.bbmodel`; export with
`tools/generate_mechanical_dolphin_geometry.py`. This supersedes staged-only
notes above. Door opening/closing animation remains explicitly deferred.

## Deepseeker exterior restoration, 2026-10-12

The user requested return to the old exterior with the painted outline ladder,
then clarified that they prefer the old hatch's appearance too. Preserve that
low round/octagonal hatch and subtle flat painted guide; do not keep the raised
rectangular hatch or external rail ladder from the expedition revision.
`src/main/blockbench/deepseeker_outline_ladder.bbmodel` is the restored authoring
base. It combines the archived exterior with current cabin furniture and
chair; only the two hinges move forward to let the lid clear the mast. The interior ladder moves under the original hatch. Model preview:
`output/blockbench/deepseeker-outline-restored/model-preview.png`. Runtime adoption is complete. The player-sized shaft and sampled lid sweep pass
the geometry check. Animation integration is paused pending user discussion.

Deepseeker user correction after motion review: restore the interior ladder to
its original side-wall position (same original X/Z), trimming its top and fittings
to fit the old low roof. It must not stay on the rear edge of the center hatch.
Hatch opening reverses to aft +90 degrees, with a slightly raised rear hinge axis
for mast clearance; the hand grabs the existing handle. This supersedes the
forward-hinge and centered-interior-ladder notes above. Exterior paint and furniture
remain unchanged. Latest preview: `output/animation/outline-entry-v2/outside-entry.gif`.

Deepseeker entry correction, 2026-10-13: user rejects the truncated ladder and
unsupported hatch transfer, and explicitly permits repositioning the ladder for
a convincing entry. This supersedes the original side-wall coordinate constraint.
First staged proposal: `src/main/blockbench/deepseeker_entry_redesign.bbmodel`;
static review in `output/blockbench/deepseeker-entry-redesign/model-review.png`.
Continuous ladder beneath the low octagonal hatch, a side handle and starting
tread ledge, and a clear floor landing. Existing palette, furniture and chair
are retained. Proposal is not visually approved or integrated yet.

User rejected that proposal because the ladder blocks the bed. Keep bed-side
access, desk access, rear storage fronts and a connected walkable aisle clear.
Future use of all three furnishings is a current layout requirement. Explore
rear hatch/ladder placement and opposite lid opening, preferably a rear corner
that does not cover storage. The diagonal concept ladder is not approved.
`deepseeker_entry_redesign.bbmodel` is a rejected study, not a new authoring base.

Approved rear-corner layout is now staged as `deepseeker_rear_entry.bbmodel`.
Keep the low octagonal lid and original ochre/charcoal palette: a fitted shoulder
collar supports the forward-opening hatch above the aft starboard ladder, past
the bed end and beside storage. Antenna moves to port for clearance. Existing
furniture and texture atlases are retained. Review and static clearance results:
`output/blockbench/deepseeker-rear-entry/`. Layout approval does not imply final
geometry or animation approval; the runtime model has not been replaced.

Rear-entry model adopted into canonical/runtime Deepseeker during the user's
unattended completion request. Prior canonical model is archived at
`src/main/blockbench/archive/deepseeker_before_rear_entry.bbmodel`. Ladder tread
lips extend slightly inward to support the animated toes and avoid liner clipping;
static furniture/aisle clearances still pass. Exterior palette, furnishings and
chair geometry remain unchanged. Current integrated animation and first-person
review are documented in `output/animation/boarding-runtime/README.md`.

Deepseeker ladder polish (2026-10-13): user requested a flatter ladder to stop
forearms appearing embedded during the slide. Rungs/tread caps are now one model
pixel deep, flush with the front of the existing rails. Preserve rear placement,
future furniture access and the established dark metal palette.


Mechanical Dolphin fin/tail update (2026-10-13): the user requested realistic
resting and moving animation for all three fins and the tail. This supersedes
the earlier instruction to preserve the prototype tail curve. Keep the approved
geometry and palette. Current animation uses restrained pectoral/dorsal trim,
a slower resting sweep and a speed-blended two-joint vertical swimming stroke
with delayed fluke feathering. Canonical native `idle`/`swim` clips and runtime
mesh previews are under the existing mechanical-dolphin paths. Live gameplay
visual review is still pending; mesh and transition checks are not visual approval.
