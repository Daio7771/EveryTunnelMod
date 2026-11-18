# Chapter 2, Level 2

*[Versión en español](../es/chapter-2-level-2.md) · [All pages](README.md)*

Level 2 of Chapter 2 is the catacombs under the factory: the old catacombs the factory's drainage was built into. The scientist comes
down Level 1's emergency manhole, alone, and looks for a way out: the Hunters in the galleries, a creature that hunts by the floor's
vibrations in the pump room, the Observant Guard of Chapter 1 in the lower chambers, and a flood in three stages on the way to the
pumping station and the exit. It is built in phases (1 to 7), one at a time, on the **private** branch `chapter-2/level-2` (off
`chapter-2/level-1`, pushed only to the remote `private`) until Levels 1 and 2 of the chapter are published. **Where it stands: phase 2,
the way in, survival and the saved game.** It is reached down Level 1's manhole and walked from the entry to the exit with the survival
bars, dying brings the player back to the last checkpoint, and the menu's Chapter 2 continues it; there are no enemies, documents or
flood yet.

Its code is under `daio7771.everytunnel.chapter2.level2` (`core`, plain Kotlin, and `platform`), apart from the base game, which never
names it (see [Architecture](architecture.md#chapters)). Its named places use Level 1's format (`level1.core.Ch2L1AnchorsParser`); its map
is built by the chapter's `platform.MapBuild` from a `core.MapPlan`, as Level 1's is.

## How it is reached

**Down Level 1's manhole, without a break.** At the end of Level 1 the player climbs down the emergency manhole while the screen fades to
black (`act3.json`, `overtime`: `fade_seconds`, 1.5, then `dark_seconds`, 1, in the dark); Level 1 tells the chapter
(`Ch2L1EndingHooks.onFinished`), and the player is at the foot of the ladder in the catacombs (`sealed_entry`), where the screen fades
back. No closing text and no menu: the way goes on. The first time, the catacombs are built behind the black (a few seconds).

On arriving (`level2.platform.Ch2L2.enter`), the player's pockets are emptied (Level 1's pass is no use down there), their stamina, thirst
and battery are as `level.json` says (`start`: all full), the lamps too (`lights`), and the first checkpoint is taken. The flashlight is
the one Rowan gave in Level 1: it is equipment, not an item, and works from the first moment.

**Playing it.** While a player plays it (`Ch2L2.active`), `Chapter2` gives them the base game's survival through `PlayerRules`: they can
be hurt, thirst and the battery run down, the bars are drawn, and their steps make noise; the flashlight and the noise as `level.json`
says (`rules`). They are still the new hire (the white coat), not the night guard.

**The states** (`level2.core.Ch2L2State`), in the order they are played; walking into a zone of `level.json`'s `progress` moves the
player on to its state, never back:

| State | Where | Reached by |
|---|---|---|
| `ARRIVED` | At the foot of the ladder | Arriving |
| `GALLERIES` | The Hunters' galleries (phase 3) | `zone_hunter_galleries` |
| `PUMP_ROOM` | The overlook and the pump room, the vibration creature (phase 4) | `zone_pump_overlook`, `zone_vibration_chamber` |
| `CATACOMBS` | The catacombs and lower galleries | `zone_lower_catacombs` |
| `GUARD_APPEARS` | The vestibule: the Guard comes out of the wall (phase 5) | `zone_observer_wall` |
| `GUARD_FIGHT` | The maintenance room and its four traps (phase 5) | `zone_observer_chamber` |
| `GUARD_DEAD` | Back round to the vestibule, BOO! and the grate up (phase 5) | The Guard's fourth stun (phase 5; by command until then) |
| `FLOOD_1` to `FLOOD_3` | The collector, the walkways, the gallery up: the flood's stages (phase 6) | `zone_flood_collector`, `zone_rising_walkways`, `zone_upper_gallery` |
| `STATION` | The pumping station, dry | `zone_pumping_station` |
| `DONE` | Out of the exit (phase 6) | (phase 6) |

The four **encounters** (`Ch2L2Event`) are runs of states: `hunters` (`GALLERIES`), `creature` (`PUMP_ROOM`), `guard` (`GUARD_APPEARS` to
`GUARD_FIGHT`) and `flood` (`FLOOD_1` to `FLOOD_3`). The flood's stage is the state's (0 before it, 3 from the third on). Each later phase
hangs its encounter on the states: `Ch2L2.onStateChange` (a state left for another, by playing or by command) and `Ch2L2.onStart` (the
level started at a state, by arriving, a death, CONTINUE or `reset`: the world as that state wants it, whatever was there).

## Checkpoints and the saved game

**Checkpoints** (`level.json`, `checkpoints`), one before each encounter: `entry` (the foot of the ladder, before the Hunters),
`pump_overlook` (before the creature), `vestibule` (before the Guard), `collector` (after the Guard, before the flood) and `station`.
Walking into a checkpoint's zone, if it lies ahead of the last one taken, takes it: the player's state, counters and what they carry are
Chapter 2's **saved game** (`Ch2L2Snapshot`, in their Chapter 2 progress), and the world's folder gets Chapter 2's marker
(`every_tunnel_chapter2_continue`, `ChapterRequest.setSaved`).

**Dying** is the base game's death (the fade, the death screen with what killed them, full health and resources, the flashlight off;
`DeathHooks`, asked through `ChapterRestarts`): the player stands at the last checkpoint, the state goes back to the one it saved, and the
level is started again there. What they picked up since stays picked up; the death is not counted in Chapter 1's saved game.

**CONTINUE.** With the marker, the menu's Chapter 2 offers CONTINUE instead of NEW GAME. It opens the world (`ChapterRequest.continuing`),
keeps the way back to Chapter 1 (`Ch2Return`), and puts the player at the last checkpoint with that checkpoint's state, counters and
things, full of health and resources. Leaving the world anywhere in Level 2 gives Chapter 1 back as it was (place, things, health,
stamina, thirst and battery) and keeps the saved game. Ctrl+Shift+D in Chapter 2's menu erases it (after asking), and NEW GAME is
back; a NEW GAME of Chapter 2, or a new game of Chapter 1, forgets it too. Level 1 is still played in one go: there is nothing to continue
until Level 2's first checkpoint.

## The map

The level is **data**: `tools/map/ch2l2/layout.json` holds everything it is made of, and `tools/map/ch2l2/generate.py` turns it into what
the mod builds. Nothing it writes is edited by hand: change `layout.json` (or the tool) and run it again.

```bash
python3 tools/map/ch2l2/generate.py --install
```

It runs its checks first and writes nothing if one fails. Then it writes `map.json` (the plan), `anchors.json` (the named places, in
world coordinates) and `flood.json` (the flood's stages) into `tools/map/ch2l2/output/` (git-ignored) with its pictures, and, with
`--install`, the three JSON files into `data/every_tunnel/ch2/l2/` (committed).

**The drawing.** The author's conceptual plan (kept out of git, in `docs/temporal/ch2l2/`) at 10 px of the drawing a block (phase 2.2; it
was 6, and too big): 150 x 105 blocks. Corridors (5-6 wide) meet at right angles; the pump room is 38 x 37, the maintenance room 36 x 25, the
central collector 20 x 55. The drawing's central hall and the crypt off the entry corridor are left out. The drawing's way straight from the lower galleries into the maintenance room is not there: it goes round, down a
corridor west of the room, into the vestibule.

**Four heights**, `levels` in the file: **H** (y 20) the entry, the station and the exit; **M** (10) the hunters' galleries, the
balcony over the collector and the pump room's overlook; **L** (0) the pump room's floor, the catacombs, the vestibule and the
maintenance room; **D** (-8) the bottom of the collectors, where the water is. The central collector is a shaft from D to over H, seen
from two heights: from the galleries' balcony (M) and from the station's broken bridge (H).

**`layout.json`:**

| Key | Holds |
|---|---|
| `origin`, `dimension`, `seed` | Where plan cell 0, 0, 0 goes in the world (1024, 64, 1280, south of Level 1's), and the seed of every detail of the look |
| `levels` | The four heights by name |
| `styles` | The looks: `masonry` (the old catacombs: Chapter 2's own dark damp brick `catacomb_*`, cracked, saltpetre-stained and tide-lined at the foot of the walls, a brick vault, flagstones, empty niches, puddles), `drain` (the factory's drainage: Level 5's drain blocks, which the world's resource pack re-skins as stained concrete, puddles), `machine`. Each gives the shell's roles (wall, dado, floor, ceiling) one block or a weighted mix |
| `spaces` | Each space: its boxes, floor (a height or a number), air's height, style, lamps (`ceiling`; `cable`, work bulbs hung from a cable along the vault; `wall`, lamps on the walls over each standing surface; the `lamp_gaps` leave some bulbs out, flickering or dead, by seed, but never near an anchor); a `ramp` makes a flight of stairs of a whole space (the stairs down from the entry, the gallery up to the station) |
| `links` | Spaces whose air simply meets (a balcony over the shaft, the stairs and the corridors) |
| `openings` | Passages through a wall one block thick: arches, a window of bars, and the **gate** (`vestibule_gate`) with a block for each of its states |
| `features` | What stands in a space: partitions and pillars, solid blocks (the overlook, the walkway along the collector, the pump's housing), flights of stairs, decks (catwalks, the broken bridge), channels with water, still water, rails, ladders, floor patches (the four traps), bricked arches |
| `anchors`, `zones` | The named places and the boxes the level's systems will watch (see below) |
| `route` | The way through the level, in order, from `sealed_entry` to `level_exit` |
| `checks` | What the checks hold the level to |
| `flood` | The flood's three stages (phase 6): trigger zone, delay, seconds, level, the gates' states while it holds water |

**The look's details** (which brick is cracked or damp, where a niche or a puddle is) are drawn from the seed: the generator
picks with `cell_hash`, and the mod with `MapPlan.pick`, the same function bit for bit (`MapPlanTest` holds them together), so the
preview is what the game builds. No vanilla block the world's pack re-skins as Chapter 1's is used, but the drain's stand-ins
(`Ch2L2MapPlanTest`).

**The checks** (`checks.py`), run on every generation:
- the structure: no space overlaps another, every opening joins two spaces, every feature is in its space, every anchor has floor under
  it and room over it, and the zones of the route's places overlap none of each other (the mod tells where the player is by them; the
  overlook's, inside the pump room's, is the one pair allowed, `nested`): a zone of an L-shaped space is its box, which can reach into its
  neighbours, so the return gallery's is its south leg (`box`);
- the route walked as a player walks it (a step up of one, a drop of at most three, ladders, no swimming), with its length (about 580
  blocks, 2.5 minutes of walking alone);
- **no shortcuts**: every way from the entry to the exit, falling and swimming included, crosses the galleries, the pump room, the
  catacombs, the vestibule, the walkways, the upper gallery and the station; with the gate down (`grate`), nothing past it can be reached;
- **nowhere to get stuck**: from every place a player can reach from the entry, the exit can still be reached (a balcony without its
  parapet, for one, would leave a fall into the shaft with no way out);
- **the pump room** needs several cycles of the pump: its shortest way across, swimming included, is at least `min` blocks (56 now:
  the floor is cut by two channels five wide, too wide for a sprinting jump, and their ladders are only by the crossings);
- the openings are clear: two blocks of air through them and room to stand on both sides;
- **the water stays put**: still water and each flood stage touch nothing that water flows into (air, bars, slabs...) outside their own
  spaces, with the gates as the stage says;
- the build: how many cells are cleared and filled, and how long the batches take (about two million cells, mostly empty: a few
  seconds), against `max_build_seconds`.

**The pictures**, in `tools/map/ch2l2/output/`: `level_preview.png` (the whole level from above, lighter where higher, the route in
yellow, the anchors named), `level_H.png` to `level_D.png` (one height each) and `flood_0.png` to `flood_3.png` (the still water, then
after each stage).

| Place (anchor) | Space | Height |
|---|---|---|
| `sealed_entry` | Entrance, at the foot of the manhole's ladder (Level 1's emergency manhole comes down here) | H |
| `collector_junction` | The balcony over the central collector, off the galleries' middle hall | M |
| `hunter_galleries` | The galleries: three halls open along the south | M |
| `pump_overlook` | The pump room's overlook: a caged block of concrete in its north-west corner | M |
| `vibration_chamber` | The pump room: two channels across its floor, the crossings at opposite ends (a zigzag), the exit seen from the overlook | L |
| `lower_catacombs` | The catacombs and the lower galleries (nothing off them now) | L |
| `observer_wall` | The vestibule (`observer_wall_face`: the wall where the Guard comes out, provisional) | L |
| `observer_chamber` | The maintenance room, its four traps where the drawing has its four squares (`trap_1` to `trap_4`) | L |
| `return_gallery` | Round the maintenance room's east and south, back into the vestibule | L |
| `flood_collector` | The collector, a walkway along its channel, past the vestibule's gate and down four steps | D |
| `rising_walkways` | The walkways' hall: up from the walkway to the lower catwalk and up again to the upper one | D to M |
| `upper_gallery` | The stairs up to the station | M to H |
| `pumping_station` | The station, dry; a broken bridge seen through bars over the shaft | H |
| `level_exit` | The exit room, past a quiet corridor | H |

Most rooms of the drawing are sealed crypts, their arches bricked up by the factory; two are open, short side rooms for later phases'
resources and documents (a crypt off the galleries and one off the service corridor before the vestibule).

**The gate.** The vestibule's way to the collector (`vestibule_gate`) has three states: `grate` (down: iron bars, until the Guard is
beaten), `open` and `sealed` (a sluice gate's plate, watertight). Phase 1 builds it open, so the level can be walked from end to end. The
flood's stages need it `sealed` (bars do not hold water back): **when the flood starts, the way back is shut behind the player.**

## The data files

The jar holds the defaults in `data/every_tunnel/ch2/l2/`; a file of the same name in `config/every_tunnel/ch2/l2/` takes its place
while it is usable, and `/et reload` reads them again (with Level 1's). The level reads `anchors.json` (the generator's) and `level.json`
(written by hand); `map.json` and `flood.json` are the generator's and are not overridden. What `level.json` names is looked for in the
anchors, and what is wrong is reported (`/et reload`).

**`level.json`** (`level2.core.Ch2L2Level`):

| Key | Holds |
|---|---|
| `rules` | `flashlight` (true: Rowan's flashlight works), `noise` (true: the steps make noise the creatures hear) |
| `start` | On arriving from Level 1, `battery`, `thirst` and `stamina` as a share of full (0 to 1; 1, 1, 1). A death or CONTINUE gives them all full |
| `lights` | Each zone's lamps: `on`, `flicker` or `off`, set whenever the level is started (arriving, CONTINUE, `reset`). Empty: the plan's own lamps (with their gaps) stand as built; a zone listed here would turn all its lamps `on`/`flicker`/`off` |
| `checkpoints` | In the way's order: `id`, `anchor` (where a death and CONTINUE bring the player back), `zone` (walked into, it is taken), `before` (the encounter it comes before, for `reset`) |
| `progress` | `zone` and `state`: walking into the zone moves the player on to the state |

## Debug commands

`level2.platform.Ch2L2Commands`, operator level 2, **development runs only**:

| Command | What it does |
|---|---|
| `/et ch2 l2 enter` | Begins the level as down Level 1's manhole: built if it is not, pockets emptied, `start`'s resources, at `sealed_entry`, its checkpoint taken. Without the way back to Chapter 1 (leaving the world keeps you there) |
| `/et ch2 l2 leave` | Out of the level: the rest of the game's rules apply again; the saved game stays |
| `/et ch2 l2 state` | Your state, the last checkpoint and the state it saved, the flood's stage, each encounter (`ahead`, `now`, `past`), the counters |
| `/et ch2 l2 state <state>` | Puts you in a state (every system of the level is told, as if you had got there by playing) |
| `/et ch2 l2 skip [<event>]` | On to the next state, or past an encounter (`hunters`, `creature`, `guard`, `flood`) as if lived |
| `/et ch2 l2 reset <event>` | An encounter to live again: the state before it, you at the checkpoint before it (taken), the level started there |
| `/et ch2 l2 build` | Builds the catacombs again over what is there (a new plan is built by itself when `goto` needs it) |
| `/et ch2 l2 unbuild` | Empties the whole area (before packing the world into a jar); whoever stands in it falls |
| `/et ch2 l2 anchors` | Every anchor and zone with its coordinates, the provisional ones marked |
| `/et ch2 l2 goto <anchor>` | Builds the level if it is not, then takes you to a place (`sealed_entry` ... `level_exit`, `trap_1`...) or to the middle of a zone (`zone_<place>`) |
| `/et reload` | Reads Level 1's and Level 2's data again and says what is wrong |

## Tests

`MapPlanTest` (the plan format: one block, mixes picked as the generator picks them, gates, the fingerprint) and `Ch2PlayerProgressTest`
(Level 2's state, counters and saved game, apart from Level 1's) in `chapter2/core`; `Ch2L2MapPlanTest` (the jar's plan: its origin,
every fill inside the cleared box and no cell set twice, every block known and none re-skinned by the world's pack but the drain's, the
anchors and zones of the route, every place to stand standing, the gate open as built, the lamps) and `Ch2L2LevelTest` (the states and
encounters; the jar's `level.json` without a problem; the checkpoints in the route's order, each in its own zone, one before every
encounter; walking the route moves the state only on, and each checkpoint replays its encounter; a broken override falls back) in
`chapter2/level2/core`; `DeathCauseTest` (a later chapter's level says itself whether it is quiet).
