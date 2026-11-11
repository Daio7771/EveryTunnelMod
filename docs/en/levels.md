# Levels

*[Versión en español](../es/levels.md) · [All pages](README.md)*

A level is **data, not code**: a JSON file of areas (zones) and points (spawn points) in a
world's datapack, plus a Minecraft function that builds its blocks. The mod reads the
files, works out where each player is, and the gameplay systems react to zones by their
tags.

## Where level files live

```
<world>/datapacks/<pack>/data/every_tunnel/levels/<id>.json
```

- The file name is the level id (`level_1.json` → `level_1`; only `a-z`, `0-9`, `_`).
- `platform.LevelReloadListener` loads every file when the world loads and on `/reload`,
  into `core.LevelManager`.
- A broken file is logged with the file and the JSON path of each problem and skipped; it
  never stops loading. A broken entry inside a good file (one zone, one spawn point) is
  skipped on its own.
- A world with at least one level is a "world with levels": the world rules, HUD,
  flashlight and loading screen switch on (`world.WorldRules.active`).

In Noname Complex both levels come from the map generator's datapack
`every_tunnel_level1_mod` (see [Python tools](tools.md)).

## Format

A full example (every key):

```json
{
  "name": "Level 1 - Parking Garage",
  "order": 1,
  "dimension": "minecraft:overworld",
  "zones": [
    { "id": "garage", "from": [0, -60, 0], "to": [31, -55, 31] },
    { "id": "elevator", "from": [-4, -60, 2], "to": [-1, -56, 5], "tags": ["start"] }
  ],
  "spawn_points": [
    { "id": "player_start", "pos": [-2.5, -60, 3.5], "yaw": 90, "tags": ["player"] },
    { "id": "pale_man_1", "pos": [16.5, -60, 16.5], "tags": ["pale_man"], "zone": "garage" }
  ],
  "ambience": {
    "loop": "every_tunnel:ambient.garage.drone",
    "loop_volume": 0.6,
    "random": [
      { "sound": "every_tunnel:ambient.drip", "interval": [6, 18], "distance": [3, 12], "volume": 0.5 }
    ]
  },
  "entry": { "title": "Level 1", "subtitle": "", "sound": "every_tunnel:event.level_transition" },
  "intro": { "seconds": 25, "music": "every_tunnel:music.elevator" },
  "build": "garage:build"
}
```

### Top-level keys

| Key | Required | Default | Meaning |
|---|---|---|---|
| `name` | no | the id | Readable name for logs and `/everytunnel levels` |
| `order` | **yes** | | Progression order: Level 1 = 1, Level 2 = 2. Walking into a higher order counts as moving on |
| `dimension` | no | `minecraft:overworld` | Dimension id |
| `zones` | **yes**, at least one | | Areas of the level |
| `spawn_points` | no | none | Named positions |
| `ambience` | no | silence | Background sound |
| `entry` | no | nothing | What a player gets on walking in from an earlier level |
| `intro` | no | none | The new-game scene (the elevator) |
| `build` | no | none | Function that builds the level's blocks, run every time the world opens |
| `safe` | no | `false` | Nothing hurts here (but `/kill` and the void); thirst and the battery stay as they are and sprinting costs nothing |

### Zones

| Key | Required | Meaning |
|---|---|---|
| `id` | yes | `a-z0-9_`. Given more than once, its boxes make one zone: a player in any of them is in it, and going from one to another is no move (give them the same tags) |
| `from`, `to` | yes | Two opposite corners, whole block coordinates, in any order. Both corners are included, like `/fill` |
| `tags` | no | Strings that mark special zones (below) |
| `ambience` | no | The zone's own [ambience](#ambience), played instead of the level's while the player is in it (the first such zone, in file order); `{}` is silence |

Zones may overlap. The player's **current level** is the first level (by `order`) with a
zone containing them; the player is "in" every zone containing them, from any level.

### Spawn points

| Key | Required | Meaning |
|---|---|---|
| `id` | yes | Unique inside the level |
| `pos` | yes | `[x, y, z]`, decimals allowed (`.5` is a block's centre) |
| `yaw`, `pitch` | no | Facing, in degrees (yaw -360..360, pitch -90..90) |
| `tags` | no | What the point is for (below) |
| `zone` | no | Id of a zone of the same level that whatever spawns here stays in |

### Ambience

| Key | Meaning |
|---|---|
| `loop` | Sound event looped everywhere in the level (not positioned), or absent |
| `loop_volume` | 0..1, default 1 |
| `random[]` | Sounds played now and then around the player |
| `random[].sound` | Sound event id |
| `random[].interval` | `[min, max]` seconds between plays (0.5..3600) |
| `random[].distance` | `[min, max]` blocks from the player (0..16, Minecraft's audible limit) |
| `random[].volume` | 0..1, default 1 |

### Entry

| Key | Meaning |
|---|---|
| `title` | Big text on screen ("Level 2"); empty shows nothing |
| `subtitle` | Smaller text under it |
| `sound` | Sound event played in the player's head |
| `manual` | `true`: the title and sound wait for the level's own code instead of showing on walking in (Level 5's, as its airlock opens) |

### Intro

| Key | Default | Meaning |
|---|---|---|
| `seconds` | 25 | How long the player waits behind closed doors (0..600) |
| `music` | none | Sound event of the song |

### Build

A function id such as `garage:build`. `LoadingHooks` runs each distinct function once when
the server starts (levels built together name the same one). The function must end with
`/everytunnel rebuilt` so the mod knows the world is ready.

## Tags

### Zone tags

| Tag | Used by | Effect |
|---|---|---|
| `start` | `IntroHooks` | The level's start (the elevator); a new game's intro plays here |
| `elevator_door` | `IntroHooks` | Blocks of the elevator doors, closed during the intro and slid open after |
| `shutter` | `LevelEntryHooks` | The opening a rolling shutter closes behind the player who walks in from the previous level |
| `keycard_door` | `KeycardHooks` | The iron doors a card reader opens; a new game closes them and locks the readers |
| `end` | `EndingHooks` | Walking in ends the demo |
| `big_door_shut` | `LoungeClosingHooks` | Walking in slams the big door, once it is open |
| `cinema_wakes` | `LoungeClosingHooks` | Walking in during the blackout starts the Cinema's eye |
| `b4_door_open` | `LoungeHooks` | Walking in opens the door to B4 |
| `lounge_build` | `LoungeHooks` | Walking in shuts that door, erases Level 3 and builds Level 4 |
| `lounge_door` | `LoungeHooks` | Its doors open once Level 4 stands (Reception's) |
| `lab_build` | `LabHooks` | Walking in starts Level 5's build (Level 4's dark stretch) |
| `lab_airlock` | `LabHooks` | Level 5's airlock: walking in shuts the door behind (`lab_airlock_rear`) and, once the level stands, opens the one ahead |
| `lab_airlock_rear` | `LabHooks` | The airlock's door behind: open until the player is in |
| `lab_welcome` | `LabHooks` | The airlock's door ahead, in the Lobby's wall: shut until the level stands |
| `lab_door` | `LabDoorHooks` | A locked sliding door of Level 5: a lever, or walking out of the Office for the south route's, opens it |
| `restart_point` | `ContinueGame` | Walking in makes the level's spawn point tagged the same the checkpoint (Level 5's Lab hall) |
| `lights_stay` | `KeycardHooks` | Its lights stay on when the keycard is taken (the stair shaft down to B3) |
| `exit` | (none yet) | Marks a level's way out; kept for later systems |
| `valve_door` | `ValveHooks` | The doors the valves open when all are right; a new game shuts them |
| `observer_home` | `ObserverEntity` | The zone the Observer patrols and goes back to (Level 3's library) |
| `book_hatch` | `BookHooks` | The trapdoors the fifth book back opens (Level 3's bunker hatch); a new game shuts them |
| `checkpoint` | (none yet) | Level 3: just past the checkpoint door, where a death will restart |
| `collapse` | `CollapseHooks` | Level 3: walking in brings the bunker tunnel's ceiling down behind the player (once per game) |
| `collapse_blocks` | `CollapseHooks` | Level 3: the stretch of tunnel the collapse fills with rubble |
| `observer_never` | `ObserverEntity` | Level 3's bunker shaft and tunnel: the Observer sees no one there, so never follows anyone down |
| `cloud` | `DistortionHooks` | Level 5: where Distortion Clouds come (one every 2 to 4 minutes spent in such zones), and where they may appear |
| `first_cloud` | `LabHallHooks` | Level 5's Lab hall: walking in, a sting and the first Cloud, once a game |
| `cloud_game` | `CloudGameHooks` | Level 5's entry corridor, from its start line: walking in starts the Cloud's game (as Halt plays it), walking out ends it |
| `dead_battery` | `ResourceHooks` | The battery is empty there and no battery item helps; walking out, it is full again |
| `no_sprint` | `ResourceHooks` | Nobody sprints there |
| `no_checkpoint` | `ContinueGame` | No checkpoint is made there: the level's waits for its first zone without the tag |
| `back_to_key` | `DeathHooks` | A death there goes back to Level 4 by the Master Key's table, as before it was taken |
| `deep_water` | `DeepWaterHooks` | Level 5's pools: falling into their deep water pulls the player under, and they drown |
| `chemical_room`, `lab_grate`, `lab_drain` | `ChemicalHooks` | Level 5: the room whose alarm lights turn, the grate the solvent dissolves, the room whose hatch then opens |

### Spawn point tags

| Tag | Used by | Effect |
|---|---|---|
| `player` | `RespawnHooks`, `DeathHooks`, `NewGame` | The level start: where a player restarts. The first one is used |
| `pale_man` | `PaleManSpawner` | A Pale man spawns here the first time a player enters the level |
| `battery` | `LootSpawner` | A battery lying on the floor |
| `wrench`, `detector` | `LootSpawner` | Level 5's wrench and radiation detector, lying on the floor |
| `lab_lever` | `LabDoorHooks` | A lever of Level 5 at that block; named `lever_<door zone id>`, it opens that door |
| `restart_point` | `ContinueGame`, `DeathHooks` | Where a death or CONTINUE restarts once its zone was walked into (Level 5's Lab hall) |
| `hunters` | `DistortionHooks` | Level 5: a group of three Hunters, round that point, facing its way |
| `distortion_junior` | `DistortionHooks` | Level 5: a Distortion Junior, floating there |
| `harmless` | `DistortionHooks` | With `hunters`: those Hunters push and shriek but never hurt (Level 5's layer 1) |
| `fuse_5a`, `fuse_10a`, `fuse_15a`, `fuse_20a`, `fuse_30a` | `LootSpawner` | Level 5: a fuse of that amperage lying there, for the fuse box |
| `lab_figure`, `lab_figure_exit` | `LabFigureHooks` | Level 5: where the figure seen from the Lobby stands, once a game, and where it goes once seen |
| `subject` | `SubjectHooks` | Level 5: a test subject standing still; its id `subject_<1..4>` says which stage |
| `terminal` | `TerminalHooks` | Level 5: a computer at that block; its id says what its screen shows |
| `cloud_game` | `CloudGameHooks` | Level 5: `cloud_game_start` and `cloud_game_end`, the ends of the line the entry corridor's game runs down |
| `key_table` | `DeathHooks` | Level 4: by the Master Key's table, where a death in Level 5's entry corridor comes back to |
| `reagent_1` to `reagent_4` | `LootSpawner`, `ChemicalHooks` | Level 5: a reagent flask lying there; a failed mix puts it back |
| `alarm_hunters`, `alarm_junior` | `ChemicalHooks` | Level 5: where the chemical room's alarm brings three Hunters, a Junior |
| `food_plate`, `food_popcorn`, `food_snack` | `LootSpawner` | Food, on furniture |
| `water` | `LootSpawner` | A water bottle |
| `keycard` | `LootSpawner` | The keycard |
| `checkpoint` | (none yet) | Level 3: where a death past the checkpoint will restart |
| `observant_guard` | `ObserverHooks` | Level 3: the Observer, spawned there once (in the library) |
| `box_spot` | `BoxHooks` | Level 3: a place a purple box may lie (a new game puts ten at random); on the floor, or on furniture (y 2) |
| `box_shelf` | `BoxHooks` | Level 3: the foot (left, bottom) of the shelf the boxes go on |
| `book_spot` | `BookHooks` | Level 3: where a book may appear once the boxes are done (a new game's books go to five at random), in the library: on a book cart or the card catalogue (y 2) or on the floor |
| `book_return` | `BookHooks` | Level 3: a place on the librarian's desk round the hatch a book goes back to (five) |
| `valve` | `ValveHooks` | A valve of the level's puzzle (`valve_a` to `valve_e`: the `ValveBlock` at that block, lettered) |
| `note` | `NoteHooks` | A note at that block (pinned on a `NoteBoardBlock` or a loose sheet, `NotePaperBlock`); its id names the text, `assets/every_tunnel/notes/<en/es>/<id>.txt` |
| `water_source` | `WaterDispenserHooks` | A water dispenser the player drinks from, as often as they like (Level 3's kitchen) |

Tags are open strings: a new system can define its own without touching the parser.

### Level 3 hooks

Level 3 is built as structure only so far; its file already holds what the systems to come
will read:

- **Zones:** one per room (its id in snake case: `archive`, `meeting_room`...,
  `open_office_nw`, `open_office_ne`), `library`, the ring in four (`inner_corridor_north`,
  `_south`, `_west`, `_east`), `guard_area` (the library and the ring together),
  `valve_corridor`, `checkpoint`, `checkpoint_door` (tagged `valve_door`), `antechamber`, `bunker_shaft`, `bunker_corridor`,
  `hatch` (its trapdoors, tagged `book_hatch`), a zone per doorway (`door_...`, `library_door_...`: walking through a door never leaves the level), `collapse`, `collapse_blocks`, `b4_approach` (in front of the door to B4, tagged `b4_door_open`) and `b4_door` (its cell). The diploma corridor behind it is Level 4's (`ch1_l4_diplomas`, and `ch1_l4_diplomas_build` tagged `lounge_build`). The stairs from B2
  belong to no zone, so "Level 3" shows on walking into the antechamber.
- **Spawn points:** `player_start` (the antechamber), `checkpoint`, `observant_guard`,
  `book_1` to `book_13` (in the library, `LIBRARY_LAYOUT["books"]`: on carts, the floor, the
  card catalogue),
  `box_1` to `box_25` (tagged `box_spot`, in their room's or ring's zone; `BOX_PUZZLE` sets
  how many), `box_shelf` (in Storage), `book_return_1` to `_5` (on the desk), `valve_a` to `valve_e`, the electrician's note `pressure_line` and the company's
  documents (`NOTE_PAPERS`: a note id per room, a loose sheet on the desk or counter nearest
  the room's doors), all tagged `note`, the loot (`LOOT`: `battery_1` to `_3`, tagged `battery`, on the floor of
  Electrical) and `water_source_cafeteria` (tagged `water_source`, the kitchen's dispenser,
  `WATER_SOURCES`).

### Level 5

Level 5, the laboratory, is built in play (`LabHooks`), its three layers (plan, tools and look:
[Python tools](tools.md)). Its file names its zones as the plan does; a zone of several boxes repeats its id:

- **Layer 1:** `entry_corridor` (from the steel frame in Level 4's dark stretch, the Cloud's game: tagged
  `dead_battery`, `no_sprint`, `no_checkpoint` and `back_to_key`), `cloud_game` (the same from its start line on,
  tagged `cloud_game`), `airlock` (tagged `lab_airlock`), `lobby`,
  `workshop`, `waiting`, `lockers`, `layer1_corridor` (the ring, six boxes), `layer1_stairs` (both stair
  rooms and the flight between them).
- **Layer 2:** `subjects_corridor`, `s1` to `s4` (the sealed test chambers), `lab_hall` (tagged `restart_point`,
  `cloud` and `first_cloud`), `north_route` (two boxes) and `south_route` (three), both tagged `cloud`, `office`,
  `storage`, `chemical_room` (tagged `chemical_room`), `shortcut`, `drain_hatch` (tagged `lab_drain`).
- **Layer 3, the drain:** `drain_ladder` (the foot of the ladder, tagged `drain_bottom`), `drain_tunnels` (eleven
  boxes, tagged `cloud` and `cloud_often`), `junction` (tagged `restart_point`, `cloud`, `cloud_often` and
  `deep_water`), `hideout`, `pump_room`, `cistern` (tagged `deep_water`), `office_stairs` (the steel stairs and their landing), `director_office`
  (tagged `director_office`), `false_exit` (the corridor out of the office, tagged `false_exit`) and
  `false_exit_pit` (the pit under its end, tagged the same), `drain_shaft` (the ladder's shaft up to the hatch). A space's zone
  goes down into its water (the channels, the pools), so a player in it is still in the level.
- **Openings,** each its own zone: `entry_frame` (the frame: crossing it is walking into Level 5, tagged
  `no_checkpoint` and `back_to_key`),
  `airlock_rear` (tagged `lab_airlock_rear`), `welcome_door` (tagged `lab_welcome`), the doorways (`workshop_door`,
  `waiting_door`, `office_door_west`, `office_door_east`), the locked sliding doors
  (`stairs_door`, `south_door`, `shortcut_door`, tagged `lab_door`) and `hatch_grate` (iron bars, tagged
  `lab_grate`); on layer 3 the doorways `hideout_door`, `pump_door`, `cistern_door`, the office's doors
  `office_door` and `office_exit`, and `exit_door` (Level 1's locked EXIT door at the corridor's end). The test
  chambers have none: a window each (`WINDOWS`).
- The Lab hall and the Junction are tagged `restart_point`.
- **Spawn points:** `player_start` (the Lobby, by the way in), `checkpoint` (the Lab hall, tagged
  `restart_point`), `cloud_game_start` and `cloud_game_end` (tagged `cloud_game`, the line the entry corridor's game runs down), `lever_stairs_door` and `lever_shortcut_door` (tagged `lab_lever`), and layer 1's
  (`tools/map/lab/furnish.py`): `wrench` and `detector` (the Workshop's island bench), the fuses `fuse_5a`,
  `fuse_10a`, `fuse_20a`, `fuse_30a` (tagged the same), the safety briefing's cards `ch1_l5_card_*` (tagged `note`, each at its column's cell in the board's bottom row),
  `hunters_fuse_box` (tagged `hunters` and `harmless`), `lab_figure` and `lab_figure_exit`; and layer 2's:
  `subject_1` to `subject_4` (tagged `subject`), the cards `ch1_l5_card_s1` to `_s4`, the computers `terminal_*`
  (tagged `terminal`, `terminal_storage` among them), `reagent_1` to `reagent_4` (tagged the same), the loot
  `snack_lockers`, `water_lockers`, `battery_waiting`, `battery_office`, `snack_storage_*`, `water_storage_*`,
  `battery_storage_*` (tagged `food_snack`, `water`, `battery`), `junior_north` (tagged `distortion_junior`),
  `hunters_storage` (tagged `hunters`), `alarm_hunters` and `alarm_junior` (tagged the same); and layer 3's:
  `checkpoint_junction` (tagged `restart_point`), `junior_junction` (tagged `distortion_junior`), `hunters_cistern`
  (tagged `hunters`), `dead_guard` (tagged the same), the guard's note `ch1_l5_note_guard` (tagged `note`) and the
  loot `battery_pump_1`, `battery_pump_2`, `snack_pump`, `water_pump`, `snack_hideout`.

## How the mod tracks players

```
LevelReloadListener --> LevelManager (immutable snapshot of all levels)
server tick --> LevelTrackingHooks --> PlayerLocationTracker.update(pos)
                                           |
                                           v  LocationChange(previous, current)
                                      LevelTriggers.fire --> every registered listener
```

- `PlayerLocationTracker` keeps a `PlayerLocation(levelId, zones)` per player and returns a
  `LocationChange` only when something changed (`levelChanged`, `enteredZones`,
  `exitedZones`).
- Every change is also logged, which helps when placing zones.
- `/everytunnel where` prints your current level and zones; `/everytunnel levels` prints
  all loaded levels.

## How Noname Complex is built

The levels are generated by `tools/map/map_generator.py` (see
[Python tools](tools.md)). It writes:

- **`every_tunnel_level1_mod/`**, the datapack the mod uses:
  - `data/every_tunnel/levels/level_1.json` to `level_5.json`;
  - `level4_begin` and `level4_step_<n>`, `level5_begin` and `level5_step_<n>`: Levels 4 and 5, built in
    play in small steps (`LoungeHooks`, `LabHooks`);
  - `data/garage/functions/build.mcfunction`: force-loads the area of Levels 1 and 2 and schedules
    `build_wait`, which asks for another look in 5 ticks and, once every corner is loaded,
    runs `build_go`: it calls that look off, runs `build_now` and releases the area
    (`garage:clear` works the same way, with `clear_wait` and `clear_go`);
  - `build_now.mcfunction`: about 12,000 commands. It turns off block drops, removes the
    Pale men in the area, places every block (with the mod's blocks and their exact
    states), restores the gamerule and schedules the second stage, `level3_build`.
  - `level3_build.mcfunction` does the same for Level 3 in its own force-loaded area
    (`level3_build_wait`, then `level3_build_go`, which runs `level3_now`: about 2,000
    commands, the blocks merged into rectangles layer by layer) and ends with
    `everytunnel rebuilt`. Two stages because one area over the three levels would pass
    the 256 chunks `/forceload` takes.
- **`every_tunnel_level1_vanilla/`**, Levels 1 and 2 with re-skinned vanilla blocks for
  plain Minecraft (builds itself when the world opens).
- **`level_resources.zip`**, the world resource pack: generated textures and their LabPBR
  maps, and the sign texts (`SignTileBlock` slots). Installed as `<world>/resources.zip`.

The generator installs the mod datapack and the resource pack into
`run/saves/noname_complex` (and the dev template, if there is one).

### Limits

- Each stage force-loads its area, which must fit in 256 chunks (now 160 for Levels 1
  and 2, 77 for Level 3).
- `SignTileBlock` has 4 facings × 64 slots; 8 of the 64 slots are still free.
- `/fill` is limited to 32,768 blocks per command and a function to 65,536 commands.

## Adding a level

See [How-to recipes](how-to.md#add-a-level).
