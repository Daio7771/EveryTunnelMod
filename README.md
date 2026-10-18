# Every Tunnel

Singleplayer psychological horror mod for Minecraft (Fabric, Kotlin), set in the
abandoned underground "Noname Complex". You are the new night-shift security guard.

Current state: **Phase 4 in progress** (Phases 1-3: gameplay config, levels, world rules, zone tracking, stamina, thirst, flashlight with battery, custom HUD; Phase 4 so far: sound generator, CC0 recordings, sounds in game, Sound Physics Remastered).

## Versions

| Component              | Version                    |
|------------------------|----------------------------|
| Minecraft              | 1.20.1                     |
| Fabric Loader          | 0.19.5                     |
| Fabric API             | 0.92.12+1.20.1             |
| Fabric Language Kotlin | 1.14.2+kotlin.2.4.21       |
| LambDynamicLights      | 4.4.0+1.20.1 (recommended) |
| Sound Physics Remastered | 1.20.1-1.5.1 (recommended) |
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
    platform/    Minecraft-version-dependent glue (datapack loading, items, hooks, packets)
    util/        JsonFields (forgiving typed JSON reader)
    systems/     ResourceSystem (stamina, thirst, battery), FlashlightBeam (light cone math);
                 NoiseSystem, LookDetection later
    entities/    Pale man, ...                                (later phases)
    audio/       SoundManager                                 (later phases)
    world/       WorldRules, LevelTriggers; LootTables later
src/main/resources/
    fabric.mod.json, every_tunnel.mixins.json
    assets/every_tunnel/                  lang, item models, textures (placeholder art)
src/client/kotlin/daio7771/everytunnel/client/   client-only code
    EveryTunnelClient.kt                  client entrypoint (ClientModInitializer)
    ClientResources.kt                    stamina/thirst/flashlight received from the server
    FlashlightControls.kt                 the Flashlight key
    EveryTunnelDataGenerator.kt           datagen entrypoint (empty)
    hud/         StatusHud (health, stamina, thirst, battery bars)
    effects/     FlashlightEffect (beam per tick, low battery flicker)
    compat/      LambDynamicLightsCompat (only loaded when LambDynamicLights is installed)
src/client/java/daio7771/everytunnel/mixin/client/   client mixins (Java): sprint block, vanilla bars
src/client/resources/
    every_tunnel.client.mixins.json
src/test/kotlin/                          unit tests (./gradlew test)
examples/dev_flat_datapack/               example level datapack for the dev world
tools/sounds/                             Python sound generator (see "Sounds" below)
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
| `ambience` | no | background sound of the level (see below) |

Tip: press F3 in game and read "Targeted Block" to get coordinates for `from`/`to`.

### Level ambience

```json
"ambience": {
  "loop": "every_tunnel:ambient.garage.drone",
  "loop_volume": 0.6,
  "random": [
    { "sound": "every_tunnel:ambient.drip", "interval": [6, 18], "distance": [3, 12], "volume": 0.5 },
    { "sound": "every_tunnel:ambient.rumble", "interval": [30, 90], "distance": [8, 16], "volume": 0.9 }
  ]
}
```

- `loop` plays everywhere in the level, not positioned, and fades in/out (2 s) when
  you enter or leave the level. `loop_volume` 0-1.
- Each `random` sound plays every `interval` seconds (random between min and max) at a
  random spot `distance` blocks away (max 16, Minecraft's hearing range), from 2 blocks
  below to 3 above you. Spots inside walls are fine: Sound Physics Remastered muffles
  them. `volume` 0-1.
- Any sound event works, including vanilla ones (`minecraft:...`); the mod's are listed
  on the sound preview page. Volumes are in the Ambient/Environment slider.
- Changes apply with `/reload`.

### Trying it in the dev world

Copy `examples/dev_flat_datapack/every_tunnel_levels` into
`run/saves/dev_flat/datapacks/`, then open the world (or run `/reload` if it is
already open). The log should show `Loaded 2 level(s): level_1 ..., level_2 ...`.

## Fluorescent light

`every_tunnel:fluorescent_light` (Functional Blocks tab) is a ceiling light panel for
building the map (placeholder model and textures). It sticks to the face you place it
against: ceilings, walls or floors.

- **Modes**: on (light 15 and a hum), flickering, off. In Creative, right-click cycles
  them (sneak + right-click to place a block against the light instead).
- **Flickering** lights stay lit most of the time; every few seconds they go through a
  burst of rapid off/on switching with the flicker sound, and the light around them
  really goes out.
- Switching on plays the starter ticks; switching off the power-down.
- You hear the hum of the 8 nearest lights within 16 blocks, each at a slightly
  different pitch.

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

## Flashlight and batteries

- **The flashlight is built-in equipment**, not an item: every player always has it
  and nothing is shown in the hand. It exists only in worlds with levels (other
  worlds stay vanilla) and not in Spectator. The **Flashlight key** (default **F**,
  in Options -> Controls -> Every Tunnel) switches it on and off. F is also vanilla's
  "Swap Item With Offhand": while both are on the same key the mod unbinds the
  vanilla one (logged once); rebind it to another key if you want it.
- **Battery charge**: `battery_max_seconds` of light, drained only while on and only
  where the survival resources apply (in Creative it works without drain). At 0 it
  switches off ("The flashlight battery is dead").
- **Battery item** (Tools creative tab, or `/give @s every_tunnel:battery`): hold use
  for `battery_change_seconds` to refill `battery_refill_percent` of the battery. Only
  when the battery is not full; consumed outside Creative.
- **Low battery** (below `low_battery_warning_percent`): the HUD battery row blinks
  red and the light flickers now and then, more often as it runs out.
- Sounds: switch clicks, a failing-contact "bzzt" when it flickers or dies, the tail
  cap unscrewed at the start of a battery change and screwed back on at the end.

### The light (LambDynamicLights)

Minecraft 1.20.1 has no moving light, so the beam is drawn by the
[LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) mod (client only,
4.4.0+1.20.1 or newer). It is **recommended, not required**: without it the game
runs and the flashlight works (battery, HUD, sounds), but gives no light; the
player gets a chat message the first time they switch it on. Dev runs
(`runClient`, `runClientDevWorld`) include it automatically.

The light is a cone from your eyes in the look direction (`beam_range` blocks long,
`beam_width_degrees` wide, `beam_brightness` at the lens, dimming to 0 at the end)
plus a faint glow around you (`glow_brightness`). It stops just past the first block
you look at, so it lights the wall in front of you but not the room behind it. Light
levels are 0-15 like torches (15). The beam values are read on the client.

LambDynamicLights' own settings (Options -> Video Settings -> Dynamic Lights, or
`config/lambdynlights.toml`) still apply; with dynamic lights set to Off there is no
beam.

## HUD

While the survival resources apply (Survival/Adventure in a world with levels),
the vanilla health, hunger, armor, air and experience bars are hidden and replaced by
Minecraft-style rows of 10 icons above the hotbar:

```
stamina (top left)            battery (top right)
health  (bottom left)         thirst  (bottom right)
[                       hotbar                       ]
```

Health uses the vanilla hearts (shaking when low). Stamina icons turn grey while
exhausted; the battery row blinks red when low. Hotbar, crosshair and the rest of the
vanilla HUD stay; other worlds and game modes keep the vanilla HUD. The icons are
placeholder art in `assets/every_tunnel/textures/gui/hud_icons.png` (the layout of
that file is documented in `client/hud/StatusHud.kt`); replacing the PNG changes the
look without code changes. The battery item texture is a placeholder as well.

## Sounds (`tools/sounds/`)

The synthetic sounds of the Levels 1-2 demo (garage ambience, fluorescent lights,
flashlight and battery, heartbeat, tension/chase music, stingers) are generated by
Python (numpy + scipy), one function per sound in `catalog.py`. Natural/organic sounds
come from CC0 recordings instead (`tools/sounds/cc0/`, with every file's source and
license in `SOURCES.md`), and vanilla sounds are only referenced, never copied.

```bash
python3 tools/sounds/generate.py            # all sounds -> tools/sounds/out/ (git-ignored)
python3 tools/sounds/generate.py drip       # only ids containing "drip"
```

Every file comes from a fixed seed, so the same code always gives the same sounds.
`tools/sounds/out/index.html` lists them all with players and a few preview mixes; in
Claude Code the `sound-preview` entry of `.claude/launch.json` serves that page. The
files are dry (no reverb) because Sound Physics Remastered adds the echo in game.

### Into the mod

```bash
python3 tools/sounds/export.py
```

writes every generated sound and every CC0 cut (`cc0_cuts.py`: which part of which
recording, mono or stereo) as Ogg Vorbis to `src/main/resources/assets/every_tunnel/sounds/`
and generates `assets/every_tunnel/sounds.json`. Run it after changing a sound and commit
the result. Files whose audio did not change are not rewritten (`export_manifest.json`).
Audio is read and written with the system's libsndfile (no Python packages needed; the
`soundfile` package is used instead when installed).

Every sound can be tried in game, with the garage's reverb:
`/playsound every_tunnel:ambient.drip master @s ~ ~ ~` (sound ids as on the preview page,
e.g. `block.fluorescent.hum`, `music.tension`). Played from code so far: flashlight and
battery sounds, `player.breathing_exhausted` when you run out of stamina (in your head,
not positioned), fluorescent lights, and whatever the level files' `ambience` lists.

### Sound Physics Remastered

[Sound Physics Remastered](https://modrinth.com/mod/sound-physics-remastered) adds reverb,
echo and muffling through blocks, which is a large part of the garage's atmosphere. Like
LambDynamicLights it is **recommended, not required**, and dev runs include it, so sounds
are always judged with it.

## Debug commands

Need operator level 2 ("Allow Commands" in singleplayer):

| Command | Shows |
|---|---|
| `/everytunnel levels` | loaded levels with zones (corners, tags) and spawn points |
| `/everytunnel where` | your position, current level and zones, whether world rules are active |
| `/everytunnel resources` | your stamina (and whether you are exhausted), thirst and flashlight battery |
| `/everytunnel resources set stamina\|thirst\|battery <value>` | sets one of them, for testing (battery in seconds) |
