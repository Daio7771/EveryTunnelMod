# Every Tunnel

Singleplayer psychological horror mod for Minecraft (Fabric, Kotlin), set in the
abandoned underground "Noname Complex". You are the new night-shift security guard.

Current state: **Phase 3** (gameplay config, level definitions, world rules, player level/zone tracking, stamina and thirst).

## Versions

| Component              | Version                    |
|------------------------|----------------------------|
| Minecraft              | 1.20.1                     |
| Fabric Loader          | 0.19.5                     |
| Fabric API             | 0.92.12+1.20.1             |
| Fabric Language Kotlin | 1.14.2+kotlin.2.4.21       |
| Kotlin                 | 2.4.21                     |
| Mappings               | Yarn 1.20.1+build.10       |
| Fabric Loom            | 1.17.21                    |
| Gradle                 | 9.8.1 (wrapper)            |
| Java (mod bytecode)    | 17                         |
| Java (runs Gradle)     | 21                         |

Versions live in `gradle.properties` (plus Loom/Kotlin plugin versions in
`build.gradle.kts`).

### Required JDKs

- **JDK 17**: Minecraft 1.20.1 and the compiled mod target Java 17.
- **JDK 21**: Loom 1.11+ requires Gradle itself to run on Java 21. This is
  declared in `gradle/gradle-daemon-jvm.properties`, so `./gradlew` finds an
  installed JDK 21 automatically even when your default `java` is 17.

## Project structure

```
src/main/kotlin/daio7771/everytunnel/     common code (server + client)
    EveryTunnel.kt                        main entrypoint (ModInitializer)
    config/      GameplayConfig, parser, ConfigManager (gameplay.json)
    core/        LevelManager, PlayerLocationTracker; level/ = level model + parser
    platform/    Minecraft-version-dependent glue (datapack loading)
    util/        JsonFields (forgiving typed JSON reader)
    systems/     ResourceSystem (stamina, thirst); NoiseSystem, LookDetection later
    entities/    Pale man, ...                                (later phases)
    audio/       SoundManager                                 (later phases)
    world/       WorldRules, LevelTriggers; LootTables later
src/main/resources/
    fabric.mod.json, every_tunnel.mixins.json
src/client/kotlin/daio7771/everytunnel/client/   client-only code
    EveryTunnelClient.kt                  client entrypoint (ClientModInitializer)
    ClientResources.kt                    stamina/thirst received from the server
    EveryTunnelDataGenerator.kt           datagen entrypoint (empty)
    hud/         custom HUD                                   (later phases)
    effects/     client visual/audio effects                  (later phases)
src/client/java/daio7771/everytunnel/mixin/client/   client mixins (Java)
src/client/resources/
    every_tunnel.client.mixins.json
src/test/kotlin/                          unit tests (./gradlew test)
examples/dev_flat_datapack/               example level datapack for the dev world
```

Only `platform/` uses Minecraft APIs that change between versions; config, level
model, parsers and managers use plain Kotlin types, which keeps ports small.

Each planned package contains a `Package.kt` file that only documents what will
go there. Main and client code are in separate source sets (Loom
`splitEnvironmentSourceSets`), so client-only classes cannot be referenced from
common code by accident.

## Running

```bash
./gradlew build                # compile + produce build/libs/every_tunnel-0.1.0.jar
./gradlew runClient            # launch Minecraft with the mod (title screen)
./gradlew runClientDevWorld    # launch straight into the "dev_flat" world (see below)
./gradlew runServer            # dedicated server (not a target, sanity check only)
```

The log should contain:

```
[Render thread/INFO] (Every Tunnel) Every Tunnel loaded
[Render thread/INFO] (Every Tunnel) Every Tunnel loaded (client)
```

The game directory for dev runs is `run/` (git-ignored). Client runs use the fixed
offline username `Dev`, so worlds remember your player between launches.

## Superflat development world

Development uses a plain vanilla superflat world (no custom world generation).
One-time setup:

1. `./gradlew runClient`
2. Singleplayer -> Create New World.
3. World Name: **`dev_flat`** (exactly, the run config loads this folder name).
4. Game Mode: Creative (convenient while building tests).
5. More World Options -> World Type: **Superflat**. Allow Commands: ON.
6. Create the world, then optionally run once:
   ```
   /gamerule doMobSpawning false
   /gamerule doDaylightCycle false
   /gamerule doWeatherCycle false
   /time set midnight
   ```
7. Quit.

The world is saved as `run/saves/dev_flat/`. From then on:

- `./gradlew runClientDevWorld`, or
- IntelliJ run configuration **"Minecraft Client (dev_flat world)"** (generated
  by Loom on Gradle sync).

Both pass `--quickPlaySingleplayer dev_flat` and skip the menus. To reset the
world, delete `run/saves/dev_flat/` and repeat the setup.

The real hand-built map (Noname Complex) will later be another save in
`run/saves/`; the same quick-play mechanism works for it.

## Gameplay config (`gameplay.json`)

All gameplay values live in `config/every_tunnel/gameplay.json` (in dev:
`run/config/every_tunnel/gameplay.json`). The file is created with defaults on
first launch and read at game start (restart after editing).

- Invalid value (wrong type, out of range): warning in the log, default used for
  that value only.
- Unknown key: warning (usually a typo), ignored.
- File is not valid JSON: error in the log, all defaults, file left untouched.
- Missing keys (e.g. new values added by a mod update): added to the file with
  their default, unless the file has other problems.

The current values are placeholders until playtesting. Defaults and the meaning of
each value: `src/main/kotlin/daio7771/everytunnel/config/GameplayConfig.kt`.

## Level definitions (datapack in the world save)

Levels belong to the map, so they ship inside its save as a datapack:

```
<world>/datapacks/every_tunnel_levels/
    pack.mcmeta                              {"pack": {"pack_format": 15, ...}}  (15 = 1.20.1)
    data/every_tunnel/levels/level_1.json    one file per level; file name = level id
```

They load when the world opens and again on `/reload`, so zones can be edited
without restarting. The log shows `Loaded N level(s): ...` and a warning for every
problem (with file and JSON path). A broken zone or spawn point is skipped; a level
is skipped only if it has no `order` or no valid zone.

```json
{
  "name": "Level 1 - Parking Garage",
  "order": 1,
  "dimension": "minecraft:overworld",
  "zones": [
    { "id": "garage", "from": [0, -60, 0], "to": [31, -55, 31] },
    { "id": "ramp_down", "from": [28, -60, 28], "to": [31, -57, 31], "tags": ["exit"] }
  ],
  "spawn_points": [
    { "id": "player_start", "pos": [4.5, -60, 4.5], "yaw": -45, "tags": ["player"] }
  ]
}
```

| Key | Required | Meaning |
|---|---|---|
| `name` | no (default: file name) | readable name for logs |
| `order` | yes | progression order (1, 2, ...) |
| `dimension` | no (default: overworld) | dimension id |
| `zones[].id` | yes | `a-z`, `0-9`, `_`; unique inside the level |
| `zones[].from` / `to` | yes | opposite block corners, both included (like `/fill`) |
| `zones[].tags` | no | markers for later systems (e.g. `exit`) |
| `spawn_points[].id` | yes | unique inside the level |
| `spawn_points[].pos` | yes | `[x, y, z]`, decimals allowed (4.5 = block center) |
| `spawn_points[].yaw` / `pitch` | no (default 0) | facing direction |
| `spawn_points[].tags` | no | what spawns there (e.g. `player`, `pale_man`) |

Tip: press F3 in game and read "Targeted Block" to get coordinates for `from`/`to`.

### Trying it in the dev world

Copy `examples/dev_flat_datapack/every_tunnel_levels` into
`run/saves/dev_flat/datapacks/`, then open the world (or run `/reload` if it is
already open). The log should show `Loaded 2 level(s): level_1 ..., level_2 ...`.

## World rules

Active only in worlds whose datapacks define Every Tunnel levels (other worlds
stay vanilla):

- **No breaking or placing blocks** for Survival/Adventure players: mining is
  cancelled, and using items on blocks (placing blocks, buckets, flint and steel,
  bone meal, ...) is disabled the same way Adventure mode does it. Doors, buttons
  and levers still work. **Creative and Spectator are exempt**, so the map is
  built and edited in Creative.
- **No vanilla mob spawning**: when the world starts and after `/reload`, the mod
  sets `doMobSpawning`, `doPatrolSpawning`, `doTraderSpawning`, `doInsomnia` and
  `doWardenSpawning` to false (logged). Mod enemies are spawned by code and are
  not affected. The gamerules are not restored if the levels are removed.

## Level and zone tracking

Every server tick the mod checks which level and zones each player is in. Zones
may overlap (e.g. an `exit` zone inside a larger zone); the player is in all of
them, and the current level is the first level by `order` that contains the
player. Every change is logged, e.g.:

```
Player149: level none -> level_1; entered level_1/garage
```

Gameplay systems subscribe to these changes through `world.LevelTriggers`.

## Stamina and thirst

Apply to Survival/Adventure players while the world rules are active (values in
`gameplay.json`, sections `stamina` and `thirst`):

- **Stamina** drains while sprinting and per jump, and regenerates after
  `regen_delay_seconds` without use. At 0 you are **exhausted**: no sprinting until
  stamina is back to `exhausted_recover_percent` of the maximum.
- **Thirst** drains slowly all the time (`sprint_drain_multiplier` faster while
  sprinting). When empty you take `damage_when_empty` every
  `damage_interval_seconds` ("died from dehydration").
- Vanilla hunger is held full: thirst replaces it. Health still regenerates
  slowly the vanilla way (turn off with `/gamerule naturalRegeneration false`).
- Dying resets both to full. Values are saved with the world
  (`<world>/data/every_tunnel_resources.dat`).

There is no HUD yet; check the values with `/everytunnel resources`.

## Debug commands

Need operator level 2 ("Allow Commands" in singleplayer):

| Command | Shows |
|---|---|
| `/everytunnel levels` | loaded levels with zones (corners, tags) and spawn points |
| `/everytunnel where` | your position, current level and zones, whether world rules are active |
| `/everytunnel resources` | your stamina (and whether you are exhausted) and thirst |
| `/everytunnel resources set stamina\|thirst <value>` | sets one of them, for testing |
