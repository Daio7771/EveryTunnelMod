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

### Zones

| Key | Required | Meaning |
|---|---|---|
| `id` | yes | Unique inside the level, `a-z0-9_` |
| `from`, `to` | yes | Two opposite corners, whole block coordinates, in any order. Both corners are included, like `/fill` |
| `tags` | no | Strings that mark special zones (below) |

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
| `lights_stay` | `KeycardHooks` | Its lights stay on when the keycard is taken (the stair shaft down to B3) |
| `exit` | (none yet) | Marks a level's way out; kept for later systems |

### Spawn point tags

| Tag | Used by | Effect |
|---|---|---|
| `player` | `RespawnHooks`, `DeathHooks`, `NewGame` | The level start: where a player restarts. The first one is used |
| `pale_man` | `PaleManSpawner` | A Pale man spawns here the first time a player enters the level |
| `battery` | `LootSpawner` | A battery lying on the floor |
| `water` | `LootSpawner` | A water bottle |
| `keycard` | `LootSpawner` | The keycard |

Tags are open strings: a new system can define its own without touching the parser.

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

The two demo levels are generated by `tools/map/map_generator.py` (see
[Python tools](tools.md)). It writes:

- **`every_tunnel_level1_mod/`**, the datapack the mod uses:
  - `data/every_tunnel/levels/level_1.json` and `level_2.json`;
  - `data/garage/functions/build.mcfunction`: force-loads the map area and schedules
    `build_wait`, which checks every 5 ticks whether the corners are loaded and then runs
    `build_now`;
  - `build_now.mcfunction`: about 12,000 commands. It turns off block drops, removes the
    Pale men in the area, places every block (with the mod's blocks and their exact
    states), restores the gamerule and ends with `everytunnel rebuilt`.
- **`every_tunnel_level1_vanilla/`**, the same level with re-skinned vanilla blocks for
  plain Minecraft (builds itself when the world opens).
- **`level_resources.zip`**, the world resource pack: generated textures and their LabPBR
  maps, and the sign texts (`SignTileBlock` slots). Installed as `<world>/resources.zip`.

The generator installs the mod datapack and the resource pack into
`run/saves/noname_complex` (and the dev template, if there is one).

### Limits

- The build force-loads the whole map, so it must fit in 256 chunks (now about 180).
- `SignTileBlock` has 4 facings × 64 slots; 9 of the 64 slots are still free.
- `/fill` is limited to 32,768 blocks per command and a function to 65,536 commands.

## Adding a level

See [How-to recipes](how-to.md#add-a-level).
