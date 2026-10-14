# Arcade skateboard

Run `./gradlew runClient`, then `/give @s practicemod:skateboard` (also in the
Traversal Gadgets and Tools & Utilities creative tabs). Use it on the top of a
block to place it, then right-click to ride. Attack an empty board to recover it.
There is no crafting recipe yet. Third person (F5) is useful for tricks.

The gameplay reference is THPS 1+2's momentum and combo system, including
[Activision's description of manuals, reverts, wallrides, and specials](https://blog.playstation.com/2020/08/12/tony-hawks-pro-skater-1-2-warehouse-demo-everything-you-need-to-know/).
The tuning and terrain interaction here are an original Minecraft adaptation;
this is not an exact recreation of the game's physics, levels, or entire move list.

## Controls

Trick keys are rebindable under **Options → Controls → Skateboarding**. Movement
uses your existing Minecraft bindings. Defaults:

| Control | Action |
| --- | --- |
| W | Push; accelerate to 11.6 blocks/second |
| Ctrl + W | Accelerate to 14.4 blocks/second |
| Release W | Coast with momentum |
| S | Brake |
| Mouse or A / D on the ground | Steer with a bounded turn rate |
| Hold Space, then release | Crouch/charge, then ollie; holding also accelerates |
| A / D in the air | Spin; line up with travel before landing |
| Z in the air | Kickflip; D + Z = Heelflip, S + Z = Pop Shuvit, W + Z = 360 Flip |
| Hold X in the air | Indy; D + X = Melon, W + X = Nosegrab, S + X = Tailgrab |
| Hold C above a rail | Catch a grind while descending |
| W / S / A or D when catching a grind | Nosegrind / 5-0 / Boardslide; neutral = 50-50 |
| Z or X while grinding | Change grind (half-second transition cooldown) |
| A / D while grinding | Correct the balance needle |
| V | Start/end a manual; can be buffered just before landing |
| Quickly tap W then S | Manual |
| Quickly tap S then W | Nose manual |
| W / S during a manual | Correct the balance needle |
| R as you land | Revert, extending the window to link a manual |
| B in the air with SPECIAL ready | The 900; needs sufficient airtime |
| C alongside a wall in the air | Brief wallride |
| Space while wallriding | Wallplant away from the wall |
| Shift | Dismount |

A neutral flip means releasing W; W + Z intentionally selects the longer 360 Flip.
Release grabs before landing. A full charge takes 16 ticks (0.8 seconds). A quick
release makes a lower ollie; a late or unfinished trick can bail on touchdown.
Manual gestures must start within six ticks, so changing from a long push to
braking does not accidentally start a manual. V provides a simpler alternative.

## Scoring and terrain

Completed tricks add base points and a multiplier. Repeated tricks lose value
within the same combo. Spins, grinds, manuals, and reverts link into the same
combo; sustained balance adds points without adding a multiplier every tick.
A clean landing banks the combo after a short six-tick linking window. Manuals
and grinds keep it open; reverts extend that window. Stopping a manual or releasing
a grind lets the combo settle. Bails lose the active combo and special meter,
while previously banked points remain. Total and best combo persist on the placed
entity through saving/reloading. Picking up and replacing the board starts fresh.

The HUD shows total score, best combo, base × multiplier, trick names, landing/bail
feedback, charge, speed, and balance. Completing tricks fills SPECIAL. At 75% it
allows the 900 and temporarily increases speed/ollie caps; the 900 spends it.
An idle meter drains. Ordinary bails briefly stop the board with the rider still
mounted; they do not apply vanilla fall damage. Dismount remains available.

Build continuous fence, wall, or iron-bar lines to grind. Horizontal chains can
also be used. Rails must have a collision shape and an adjacent grindable block;
a single isolated post is not a rail. Approach from above with C held. The catch
uses the actual collision top, rejects obstructions, and never teleports through
a wall. Space charges an ollie while grinding; release to hop out into another
trick. The tag `practicemod:skateboard_grindable` can be extended by a datapack.

Slabs and stairs remain usable terrain; fast stair crests provide a launch.
Full blocks stop the board and high-speed impacts bail. There are no custom
quarter-pipe blocks, bowl transitions, spine transfers, career objectives, timed
runs, or authored skateparks in this implementation. Water slows the board and
ends active combos. Playtest tuning is still needed to judge the arcade feel.

## Model, animation, and multiplayer

The authored alien board, palette, 73 cuboids, and original texture are preserved.
Source: `src/main/blockbench/alien_skateboard.bbmodel`. Regenerate static geometry
with `python3 tools/generate_skateboard_geometry.py` after editing that model.

The existing speed-aware pushing animation and fixed-length knee rig remain.
Action poses add charged crouches, tucked jumps, balance arms, grabs, flips,
shuvits, spins, raised manual trucks, grind poses, and bail recovery. These are
procedural Minecraft poses, not motion-capture animations. Skin/pants UVs and
rigid armor compatibility follow the original renderer.

Movement and scores run on the server. Clients send the entity ID, button mask,
steering, and camera yaw. Input is rider-authenticated, non-finite values are
rejected, steering and flags are clamped, and controls expire after ten ticks.
Opening a menu cancels charging without triggering an ollie. Leaving the board
or losing active input cancels an unbanked combo. Vanilla client vehicle-position
authority remains disabled. Both endpoints must use protocol 16. Position
interpolation is bounded to three ticks; there is no client movement prediction,
so higher network latency still affects responsiveness.

## Verification

```
./gradlew build runGameTestServer
java -cp build/classes/java/main tools/ValidateSkateboardGameplay.java
java -cp build/classes/java/main tools/ValidateSkateboardAnimation.java
```

Server tests cover placement/recovery, mounted feet, persistence, acceleration,
coasting, braking, steering limits, invalid and stale input, menu cancellation,
charged jumps, trick completion, late/sideways bails, manual balance, buffered
revert/manual combos, rail catches and ollie exits, gravity, slabs, and wall impacts.
Pure checks cover repetition, multipliers, special gating, grabs, overflow bounds,
fixed bone lengths, cadence, planted feet, and animation frame independence.

For a visual/network playtest, use two clients and check third-person flips,
manuals, grinds, spins, F5 views, armor, slim/classic skins, menus while charging,
and latency. Automated server tests do not establish exact THPS feel.
