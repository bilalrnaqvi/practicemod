# Art Pipeline

How to get textures made with Retro Diffusion / Blockbench into the mod.

This mod currently has exactly **three** texture files: two item/armor textures and one
GUI background. All are flat PNGs with a fixed pixel grid, not 3D-painted models. Retro
Diffusion output and Blockbench texture exports both work fine as long as you export/save
at the exact dimensions listed below.

## The texture slots

### 1. Item icon (inventory/hand/ground icon)

| | |
|---|---|
| **What it is** | The 2D icon shown in the inventory, hotbar, when held, and dropped on the ground. |
| **Dimensions** | **16 × 16 px** |
| **Format** | PNG, RGBA (must support transparency) |
| **Destination path** | `src/main/resources/assets/practicemod/textures/item/jetpack.png` |
| **Filename must be exactly** | `jetpack.png` |

Any pixel with alpha = 0 is fully transparent (see-through). There's no requirement to
use a specific background color — just make sure the parts of the image that should
show through to the inventory background are actually transparent, not black or white.

### 2. Equipped armor-layer texture (what renders on the player's body)

| | |
|---|---|
| **What it is** | The texture drawn on the player model when the jetpack is worn in the chest slot. |
| **Dimensions** | **64 × 32 px** |
| **Format** | PNG, RGBA |
| **Destination path** | `src/main/resources/assets/practicemod/textures/models/armor/jetpack_layer_1.png` |
| **Filename must be exactly** | `jetpack_layer_1.png` |

The jetpack no longer renders as a vanilla chestplate. It has a **custom 3D model**
(`JetpackModel.java`): a flat mounting plate on the back, two vertical fuel tanks
sticking out behind it, and two downward-pointing nozzles under the tanks. Nothing is
drawn on the front of the torso, the arms, the head, or the legs — so this file does
**not** use Minecraft's classic armor UV layout anymore. Each rectangular region maps
to one face of one of those five boxes. See
[UV regions](#uv-regions-where-things-map-on-the-model) below.

Only `jetpack_layer_1.png` exists because the jetpack sits in the chest slot; the
`_layer_2` file is exclusively for leggings, which this item doesn't have.

Transparent (alpha = 0) pixels inside a mapped region punch a see-through hole in that
face of the model — keep every mapped region fully opaque unless you want holes. Areas
of the file outside the mapped regions are never sampled; leaving them transparent is
the safe default.

### 3. Fueling menu GUI background

| | |
|---|---|
| **What it is** | The background of the jetpack's fueling menu (sneak-right-click the jetpack, or the Open Jetpack Fueling key while wearing it). |
| **Dimensions** | **256 × 256 px** canvas (Minecraft GUI convention). Only two regions are actually drawn — see below. |
| **Format** | PNG, RGBA |
| **Destination path** | `src/main/resources/assets/practicemod/textures/gui/jetpack_fueling.png` |
| **Filename must be exactly** | `jetpack_fueling.png` |

The current file is generated programmer art (industrial plating look). Feel free to
replace it wholesale — but the screen code blits fixed pixel rectangles out of this file,
so some regions are **structural** and must stay exactly where they are:

**Drawn regions of the 256×256 canvas** (everything else is ignored; keep it transparent):

| Region | Pixel bounds (x, y, w, h) | Role |
|---|---|---|
| Menu background | (0, 0, 176, 166) | The visible menu panel. |
| Fuel-bar fill strip | (0, 166, 88, 8) | Cropped left-to-right to the current fuel fraction and drawn over the gauge groove. Design it as the "100% full" bar. |

**Fixed (structural) pixels inside the 176×166 panel** — the screen and menu code place
slots, the gauge, and text at these exact coordinates, so redraw their *style* freely but
do not move or resize them:

| Element | Pixel bounds (x, y, w, h) | Notes |
|---|---|---|
| Coal slot frame | (79, 21, 18, 18) | Interior 16×16 at (80, 22). Must read as a slot (players drop coal here). |
| Fuel gauge groove | (43, 47, 90, 10) | Interior 88×8 at (44, 48) is where the fill strip is drawn over. Keep the interior dark/low-contrast so the orange fill reads. |
| Player inventory slots | (7 + col×18, 83 + row×18, 18, 18) | 9×3 grid, rows at y = 83, 101, 119. |
| Hotbar slots | (7 + col×18, 141, 18, 18) | 9 slots. |

**Text bands** — vanilla font renders directly on top of these areas, so keep them
low-contrast plate (no busy detail, no strong highlights), but their styling is otherwise
yours:

| Band | Pixel area | What renders there |
|---|---|---|
| Title | (8, 6) leftward-anchored, ~1 text row | "Jetpack" |
| Status line | centered in (30, 58)–(146, 68) | "Fuel: Xs / Ys   Coal: N" (drawn in orange #FFB861) |
| Inventory label | (8, 72), ~1 text row | "Inventory" |

Everything else inside the 176×166 panel — the plating, rivets, hazard stripes, seams,
the shape of the raised machine panel — is pure decoration and completely safe to
redesign.

## UV regions — where things map on the model

Reference files (already in `art/templates/`):

- `jetpack_layer_1_model_uv.png` — the **current** layout, **upscaled 8× (512×256)**,
  one color family per box (see legend below), lighter shade = more visible face.
  Scale it down to 64×32 (nearest-neighbor) to use it as a paint-under layer.
- `jetpack_layer_1.png` — a 1:1 copy of the *pre-model* in-game texture. Still useful
  for sampling the established colors, but its painted shapes were placed for the old
  vanilla-chestplate layout, not the current one.
- `jetpack_layer_1_annotated.png` — **obsolete.** It documents the vanilla armor layout
  the jetpack used before it got a custom model; do not paint against it.

The model is five boxes, all mounted on the player's back. Each box unwraps in the
standard Minecraft way: a strip of side faces (in order: right, inner, left, outer) with
top and bottom faces above it. "Inner" is the face touching the player's back (almost
never visible); "outer" is the face you see when the player faces away from the camera.
"Right" means the player's right (screen-left when viewed from behind).

Native 64×32 coordinates, given as (x, y, width, height):

| Box (template color) | Face strip (y range) | right | inner | left | outer | top | bottom |
|---|---|---|---|---|---|---|---|
| Mounting plate (blue) | y 17–28 | (0, 17, 1, 11) | (1, 17, 8, 11) | (9, 17, 1, 11) | (10, 17, 8, 11) | (1, 16, 8, 1) | (9, 16, 8, 1) |
| Right tank (red) | y 3–13 | (0, 3, 3, 10) | (3, 3, 3, 10) | (6, 3, 3, 10) | (9, 3, 3, 10) | (3, 0, 3, 3) | (6, 0, 3, 3) |
| Left tank (green) | y 3–13 | (16, 3, 3, 10) | (19, 3, 3, 10) | (22, 3, 3, 10) | (25, 3, 3, 10) | (19, 0, 3, 3) | (22, 0, 3, 3) |
| Right nozzle (orange) | y 18–21 | (20, 18, 2, 3) | (22, 18, 2, 3) | (24, 18, 2, 3) | (26, 18, 2, 3) | (22, 16, 2, 2) | (24, 16, 2, 2) |
| Left nozzle (purple) | y 18–21 | (32, 18, 2, 3) | (34, 18, 2, 3) | (36, 18, 2, 3) | (38, 18, 2, 3) | (34, 16, 2, 2) | (36, 16, 2, 2) |

The two tanks and the two nozzles are separate boxes with separate UVs (nothing is
mirrored), so the left and right sides can look different if you want.

Visibility, for deciding where to spend detail: the tanks' **outer, left, and right**
faces and the plate's **outer** face (the sliver visible between the tanks) dominate the
view from behind; tank **tops** show when looking down at the player; nozzle **bottoms
and outers** show from below/behind, especially in flight. Inner faces are buried
against the player's back.

Everything outside the mapped regions is unused. In particular the old body/arm areas
(x 40–64) and everything below y 28 are free space.

## Color / transparency requirements

- Both files must be RGBA PNGs (need an alpha channel, even if you don't use partial
  transparency — fully opaque alpha=255 everywhere except intentional cutouts is fine).
- No specific palette or background color is required by the game engine.
- Avoid partially-transparent (semi-see-through) pixels along edges unless you want that
  effect — Minecraft's armor renderer doesn't blend/antialias edges specially, so
  semi-transparent pixels can look like faint halos rather than smooth edges.
- Keep dimensions *exact*. If a file isn't 16×16 (icon) or 64×32 (armor layer), Minecraft
  will either stretch it or fail to load it correctly.

## Getting new art into the game

1. Export/save your new PNG from Blockbench (or wherever) at the correct dimensions.
2. Overwrite the existing file at its destination path, keeping the exact filename:
   - Icon → `src/main/resources/assets/practicemod/textures/item/jetpack.png`
   - Armor layer → `src/main/resources/assets/practicemod/textures/models/armor/jetpack_layer_1.png`
   - Fueling menu GUI → `src/main/resources/assets/practicemod/textures/gui/jetpack_fueling.png`
3. Rebuild/reload to see it:
   - **Fastest**: if the game is already running, press **F3 + T** (reload
     resources/textures). This picks up texture changes without restarting the client.
     It does *not* require a Gradle rebuild since textures are loaded straight from
     `src/main/resources` in the dev environment.
   - **If F3+T doesn't pick it up** (uncommon, but can happen with texture atlas
     caching): fully restart the client — stop `./gradlew runClient` and run it again.
   - You do **not** need to run a datagen step (`./gradlew runData`) for texture swaps —
     datagen only regenerates the item *model JSON* (which face of the item points at
     which texture file), not the texture pixels themselves. You only need to re-run
     datagen if you're adding a *new* item or changing which texture file an item model
     points at.

## Working files

- `art/source/` — put your Retro Diffusion exports, Blockbench project files, and other
  working files here. Nothing in this folder is read by the build; it's just storage.
- `art/templates/` — reference UV templates (see above). Not read by the build either.

The whole `art/` folder lives outside `src/`, so nothing in it is ever bundled into the
mod jar or touched by `./gradlew build`.
