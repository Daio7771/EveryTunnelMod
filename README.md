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
| Sodium / Iris / Indium | 0.5.13 / 1.7.6 / 1.0.36 (recommended, shader packs) |
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
    systems/     ResourceSystem (stamina, thirst, battery), FlashlightBeam (light cone math),
                 NoiseSystem (what enemies can hear); LookDetection later
    entities/    Pale man, ...                                (later phases)
    audio/       SoundManager                                 (later phases)
    world/       WorldRules, LevelTriggers; LootTables later
src/main/resources/
    fabric.mod.json, every_tunnel.mixins.json
src/main/java/daio7771/everytunnel/mixin/        common mixins (Java): game events -> noise
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
tools/map/                                Python map generator (see "Map generator" below)
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
./gradlew runClientMapWorld    # launch straight into "noname_complex", the generated map (see "Map generator")
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

The map (Noname Complex) is another save, `run/saves/noname_complex`, filled by the map
generator; the same quick-play mechanism opens it (see "Map generator").

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

## Noise

What players do makes noise that enemies can hear (the Pale man, Phase 5). Each noise
has a position and a radius in blocks; values in `gameplay.json`, section `noise`
(0 makes an action silent):

| Action | Key | Default |
|---|---|---|
| Footstep (about one per block) sneaking / walking / sprinting | `sneak_radius` / `walk_radius` / `sprint_radius` | 2 / 6 / 14 |
| Jump | `jump_radius` | 8 |
| Landing (2-block fall; half for small drops, up to twice for long falls) | `land_radius` | 10 |
| Doors, trapdoors, fence gates, levers, buttons, chests, note blocks | `interact_radius` | 8 |
| Flashlight clicks, battery change | `equipment_radius` | 4 |
| Panting while out of stamina (once per second) | `exhausted_breathing_radius` | 5 |

Only Survival/Adventure players in worlds with levels make noise. Noises are remembered
for 5 seconds and travel through walls (whether walls muffle them is up to the
listener). Doors and levers are detected through vanilla's game events, the same
"vibrations" sculk sensors hear. Check them with `/everytunnel noise` and
`/everytunnel noise watch`.

## Fluorescent light

`every_tunnel:fluorescent_light` (Every Tunnel tab) is a shallow fixture with two tubes.
It sticks to the face you place it against: ceilings, walls or floors. On a ceiling or
floor the tubes run the way you look when placing it (`axis`), and it fills the block's
width that way, so lights placed end to end make one long fixture.

- **Modes**: on (light 15 and a hum), flickering, off. In Creative, right-click cycles
  them (sneak + right-click to place a block against the light instead).
- **Flickering** lights stay lit most of the time; every few seconds they go through a
  burst of rapid off/on switching with the flicker sound, and the light around them
  really goes out.
- Switching on plays the starter ticks; switching off the power-down.
- You hear the hum of the 4 nearest lights, fading out within about 8 blocks, each at a
  slightly different pitch.

## Props (`tools/blocks/`)

The levels' props are the mod's own blocks, all in the **Every Tunnel** creative tab
(`/give @s every_tunnel:<id>`). The mod version of a generated level uses them instead of
the re-skinned vanilla stand-ins (no more cones that hurt, working hoppers or bookshelf
letters that change).

| Block | What it is | Material sound |
|---|---|---|
| `cardboard_box` | Full block, label on the sides | cardboard |
| `traffic_cone`, `trash_bin` | Floor props | plastic |
| `fire_extinguisher`, `oil_drum` | Floor props | hollow metal |
| `shopping_cart` | Wire basket on wheels | wire mesh |
| `pallet` | Low wooden pallet (you can walk onto it) | wood |
| `wheel_stop` | Concrete stop with yellow stripes, along the stall's back | stone |
| `pipe` | Round pipe; connects to pipes on all sides and into walls | metal pipe |
| `duct` | Rectangular air duct; connects like the pipe | sheet metal |
| `cable_tray` | Perforated tray with cables; connects sideways, with rails on its open sides | sheet metal |
| `hanging_cable` | A loose cable hanging from the ceiling (no collision) | plastic |
| `metal_panel` | Brushed steel wall of the service elevator | sheet metal |
| `stain` | A mark on a floor, wall or ceiling (oil, dirt, damp, rust, mould...); `facing` + `pattern` | |
| `puddle` | Standing water; adds a splash to your footsteps | puddle |
| `sign_tile` | Wall/pillar block whose 64 numbered `slot`s show a level's sign texts | stone |

Floor props face a random way when the generator places them, or face you when you
place them. Pipes, ducts and trays pick their connections themselves when placed by
hand; the generator sets the same ones (`tools/map/garage/mod_blocks.py`). The sign tile
shows bare concrete on its own: each level's world resource pack maps its slots to that
level's texts. None of these blocks need support, so nothing pops off as items.

```bash
python3 tools/blocks/generate.py
```

writes their models, blockstates, 16x16 textures with LabPBR maps, names and loot tables
into `src/main/resources/` (committed), plus a preview `tools/blocks/output/blocks_sheet.png`.
The blocks themselves are registered in `platform/ModBlocks.kt`; the tool stops if the
two lists of ids differ. Each material's footstep and impact sounds are generated with the
other sounds (group "Materials" in `tools/sounds/catalog.py`).

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
flashlight and battery, the props' materials, heartbeat, tension/chase music, stingers)
are generated by
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

### Shaders (Sodium + Iris + Indium)

[Sodium](https://modrinth.com/mod/sodium), [Iris](https://modrinth.com/mod/iris) and
[Indium](https://modrinth.com/mod/indium) let the game run shader packs. They are
**recommended, not required**, and dev runs include them. Shader packs cannot be bundled:
the mod is tuned for **Complementary Reimagined**. To test, download it from Modrinth into
`run/shaderpacks/` and pick it in Options -> Video Settings -> Shader Packs; in its
settings (Shader Pack Settings), set **RP Support** on the first screen to
**labPBR (RP Required)**, or it ignores the maps below.

Every block texture of the mod and of the generated level has LabPBR maps next to it
(`name_n.png`: relief, `name_s.png`: smoothness, reflectance, porosity and glow), made by
`tools/map/garage/pbr.py`. Concrete stays matte, damp streaks, puddles, oil and paint
shine where light hits them, and the fluorescent tubes glow softly, so the flashlight
stays the main light. Glow and relief strength are the `PBR` values in
`map_generator.py`; the mod's own block textures get their maps from
`python3 tools/blocks/generate.py` (see "Props").

Dev runs pin LWJGL to 3.3.1 (`build.gradle.kts`): Loom puts 3.3.2 on the classpath and
Sodium 0.5 refuses to start with it.

## Map generator (`tools/map/`)

The levels are generated by code, not built by hand. `tools/map/map_generator.py` builds
Level 1 (B1, the parking garage) from the parameters at the top of the file:

- 5 parking halls chained in a zigzag by corridors and a half-level ramp; each hall can
  override any value, and they get worse as you go (wear, dead lights, oddities).
- Start: a lobby with a service elevator (the level builds itself when the world opens
  and puts the player in the cabin). End: a ramp down to a dark "B2" landing (zone
  tagged `exit`).
- Side rooms (storage, electrical room, a locked door marked EXIT, a dead end), props
  (pallets, boxes, drums, bins, cones, extinguishers, a cart, wheel stops), ceiling beams,
  ducts, pipes, cable trays, hanging cables, sign codes on walls and pillars, stains,
  cracks and puddles, and oddities. There are no cars: the stalls are empty, or something
  was left behind in them.
- Two versions of every block choice: the **vanilla** datapack uses re-skinned vanilla
  stand-ins (`STAND_INS`) and plain vanilla blocks (`BLOCKS`); the **mod** datapack uses
  the mod's own props instead (`MOD_BLOCKS`, see "Props").
- A world resource pack with procedural 16x16 textures, applied only in worlds that carry
  it as `resources.zip`: it re-skins the stand-ins and the floor, wall and ceiling blocks
  both versions share, and maps the sign tile's slots to this level's texts.

One-time setup: the generator only installs into a world that already exists. Create it
like `dev_flat` (`./gradlew runClient`, Create New World, name **`noname_complex`**,
Superflat, Allow Commands ON, then quit). Without it the generator prints a warning and
`runClientMapWorld` stops with an error instead of leaving you on the title screen.

```bash
python3 tools/map/map_generator.py
```

writes `tools/map/output/` (git-ignored: datapacks, resource pack, `level_preview.png`,
`texture_sheet.png`, `pbr_sheet.png`) and installs the mod datapack (with the level file: 17 zones, the
start in the elevator, ambience) and `resources.zip` into `run/saves/noname_complex`.
Then open the world (`./gradlew runClientMapWorld`, or `/reload` if it was open): the
level builds itself as soon as a player is in the world. Building clears the previous
build (and the area of the earlier 40x20 garage test, `OLD_BOXES`) and teleports you into
the elevator. Same seeds give the same level.

The datapack remembers which version of the level it built (a fingerprint of the build
commands, in the scoreboard `garage_build`), so it builds once per version: reopening the
world keeps your position and whatever changed in the level, and only a regenerated level
with other parameters builds again (and puts you back in the elevator). If the level area
is not loaded, it says so once in chat and builds when it is. By hand:
`/function garage:build` rebuilds now, `/function garage:clear` removes the level (it is
not rebuilt until a new version).

The level ambience (drone, drips, rumbles...) plays only inside the level's zones, so
outside them the level is silent; `/everytunnel where` shows which zone you are in.

## Debug commands

Need operator level 2 ("Allow Commands" in singleplayer):

| Command | Shows |
|---|---|
| `/everytunnel levels` | loaded levels with zones (corners, tags) and spawn points |
| `/everytunnel where` | your position, current level and zones, whether world rules are active |
| `/everytunnel resources` | your stamina (and whether you are exhausted), thirst and flashlight battery |
| `/everytunnel resources set stamina\|thirst\|battery <value>` | sets one of them, for testing (battery in seconds) |
| `/everytunnel noise` | the noises of the last 5 seconds within 32 blocks |
| `/everytunnel noise watch` | shows each noise you make in the action bar (run again to turn off) |
