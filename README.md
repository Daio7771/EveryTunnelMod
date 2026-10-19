# Every Tunnel

A singleplayer horror mod for Minecraft 1.20.1 (Fabric, Kotlin). You're the new night
guard of the Noname Complex, an abandoned underground facility, and the first release is
a two-level demo set in its parking garage. Level 1 has nothing in it but you, and it
teaches you to listen. Level 2 adds the Pale man, who is blind and finds you by sound.
Most of the work goes into the sound.

Where it stands: player systems, sound, the generated Level 1 map and its props are done.
The Pale man hunts by sound. Next: the chase music and tension layers, then Level 2,
death/restart and loot.

## Running it

You need JDK 17 (the game) and JDK 21 (Gradle; `./gradlew` finds it on its own).
Everything else is downloaded by the build.

```bash
./gradlew build               # jar in build/libs/, runs the unit tests
./gradlew runClientMapWorld   # straight into the map world
./gradlew runClientDevWorld   # straight into the flat test world
./gradlew runClient           # title screen
```

Dev runs live in `run/` (not in git) and always log in as `Dev`, so worlds keep your
player. They also load the optional mods below.

The two worlds have to be created once, by hand: `runClient`, then a Superflat world
with commands allowed, named exactly `noname_complex` (the map) or `dev_flat` (tests).
After that:

- the map: `python3 tools/map/map_generator.py` installs Level 1 into `noname_complex`, and
  the level builds itself the first time you open the world;
- the test world: copy `examples/dev_flat_datapack/every_tunnel_levels` into
  `run/saves/dev_flat/datapacks/`. Its `level_2` has a Pale man spawn point.

To start every launch from the same point instead of keeping what you did, open the
world, stand where you want to start, run `/everytunnel devworld save` and quit. The
world is copied to `run/dev_templates/`, and each launch of the dev client puts it back
before opening it (the map generator installs into that copy too).
`/everytunnel devworld forget` turns it off. This only exists in the dev environment.

### Optional mods

None of them are required, and players get a hint where it matters.

- LambDynamicLights: draws the flashlight's light. Without it the flashlight works but
  doesn't light anything.
- Sound Physics Remastered: reverb and muffling through walls. The sounds are made dry,
  expecting it.
- Sodium + Iris + Indium: shader packs. It's tuned for Complementary Reimagined: put it in
  `run/shaderpacks/` and set "RP Support" to "labPBR" in its settings, or it ignores the
  material maps. Dev runs pin LWJGL to 3.3.1 because Sodium 0.5 won't start on the 3.3.2
  Loom brings.

GeckoLib (the Pale man's animations) is required, but it's packed inside the mod's jar.
Versions are in `gradle.properties`.

## How the game side works

Everything below only happens in worlds whose datapacks define levels. Any other world
stays vanilla.

**Levels** are areas, not code. Each one is a JSON file in the world's datapack
(`data/every_tunnel/levels/<id>.json`) with zones (block boxes, optionally tagged, e.g.
`exit`), spawn points (tagged `player`, `pale_man`...) and an ambience. They reload with
`/reload`, and mistakes are logged with the file and the JSON path. The parser
(`core/level/LevelParser.kt`) documents every key, and the generated `level_1.json` is a
full example. Every tick the mod works out which level and zones each player is in, and
other systems listen to that through `world.LevelTriggers`.

**World rules.** In Survival and Adventure you can't break or place blocks, but doors and
buttons still work. Creative is free, for building. Vanilla mob spawning is turned off
with gamerules, and the difficulty is locked on Normal (Peaceful would delete the Pale
man as soon as he spawns). Entering a level makes its start (the spawn point tagged `player`)
your respawn point, so you don't come back on the roof.

**The player.** Stamina drains when you sprint or jump. Run it dry and you're exhausted
until it's back to 30%, breathing hard. Thirst replaces hunger and hurts when it's empty.
Nothing refills it yet. The flashlight is built in (key F) and runs on a battery; battery
items refill it if you hold use for 1.5 s. The HUD swaps the vanilla bars for four rows:
health, stamina, thirst, battery.

**Noise.** Everything you do makes a noise with a radius: footsteps 2, 6 or 14 (sneaking,
walking, sprinting), jumps, landings, doors and chests, the flashlight, panting. Noises
live for 5 seconds and are what the Pale man will hear. `/everytunnel noise watch` shows
yours as you make them.

**Sound.** Each level has a background loop plus random sounds around you (drips,
rumbles, relays). Fluorescent lights hum, and the four nearest are audible. Every prop
material has its own footsteps and impacts, and puddles splash.

**The Pale man** (`every_tunnel:pale_man`) is about 2.75 blocks tall. Nothing hurts him
except `/kill` and he never despawns. The first time a player enters a level, each
`pale_man` spawn point gets one, and the world remembers it, so he's never doubled and
comes back if he's killed. You can test him with the spawn egg in the Every Tunnel tab.

He hears every noise within its radius, and each solid block in between shrinks that
radius (`wall_muffle`). He wanders until he hears something. A quiet noise makes him stop
and listen for 2 s, then walk to where it came from. A loud one (sprinting within about
7 blocks), or a second noise while he listens, makes him run at it, and you hear the "he
heard you" stinger. While chasing, each new noise moves his target, so if you stop
making noise he reaches the last place he heard you, searches for 4 s (any noise there
and he charges again) and goes back to wandering. He hits you if you're within 2 blocks
while he hunts, or bump into him at any time. By default one hit kills. Within 24 blocks of him you hear your heartbeat, from 60 bpm
up to 140 right next to him, and never under 120 while he's chasing.

All the numbers above (stamina, thirst, battery, flashlight, noise radii, the Pale man's
hearing, speeds and attack) are in
`run/config/every_tunnel/gameplay.json`. It's created with defaults and read at startup.
Bad values fall back to their default with a warning.

## The tools (Python)

Almost all the content is generated, so it can be redone from parameters. All of it needs
Python 3 with Pillow, and the sounds also need numpy, scipy and the system's libsndfile.
Previews go into each tool's `output/` folder, which isn't in git. Whatever ends up in
`src/` gets committed.

`tools/map/map_generator.py` builds Level 1: an elevator, five parking halls that get
worse as you go (wear, dead lights, oddities like stairs into the ceiling), side rooms and
a ramp down to B2. The parameters are at the top of the file and the same seed gives the
same level. `PALE_MAN_HALLS` puts a Pale man spawn point in the middle of
those halls, for testing him in the map until Level 2 exists (`[]` for none). It writes two datapacks:

- the mod one, using the mod's blocks;
- a vanilla one, using re-skinned vanilla blocks.

It also writes a world resource pack (`resources.zip`) with the textures and their LabPBR
maps, and installs it all into `noname_complex`. In game, the level rebuilds only when the
generated commands change. Building turns off block drops and clears loose items, and
`/function garage:build` forces a rebuild.

`tools/blocks/generate.py` makes the models, textures, LabPBR maps, names and loot tables
of the mod's blocks: the fluorescent light and the props (boxes, cones, extinguishers,
cart, pallets, drums, bins, wheel stops, pipes, ducts, cable trays, cables, stains,
puddles, sign tiles). The blocks themselves are registered in `platform/ModBlocks.kt`, and
the tool stops if the two lists of ids disagree.

`tools/entity/pale_man.py` makes the Pale man: GeckoLib geometry, animations (idle,
listen, walk, run, attack), the texture and the glowmask for his eyes. The model is based
on `tools/entity/reference/pale_man_concept.png`, which is AI concept art used only as
reference. The tool also writes a painting template for repainting the texture by hand.

`tools/sounds/` synthesizes the sounds (`catalog.py`, one function per sound) and cuts the
CC0 recordings (`cc0/`, with sources and licences in `SOURCES.md`; CC0 only). `generate.py`
renders previews with a listening page. `export.py` writes the `.ogg` files and
`sounds.json` into the mod, rewriting only the ones that changed. Any sound can be tried
with `/playsound every_tunnel:<id> master @s`.

## Code layout

Common code is in `src/main/kotlin/daio7771/everytunnel/`, client-only code in
`src/client/`. Loom keeps the two apart, so the server can't reach client classes by
accident.

- `config/`, `core/`, `systems/`, `entities/`: plain Kotlin with no Minecraft types
  (config, levels, player tracking, stamina/thirst/battery, noise, enemy AI). This is what
  the unit tests cover.
- `platform/`: everything that touches Minecraft: blocks, the entity, hooks, packets,
  commands. A port to another version should mostly stay in here.
- `world/`: world rules and level triggers.
- `client/`: HUD, flashlight effect, ambience and hum players, the Pale man's renderer.

## Debug commands

These need commands enabled.

- `/everytunnel levels`: loaded levels, zones, spawn points.
- `/everytunnel where`: your level and zones, and whether the rules are active.
- `/everytunnel resources [set stamina|thirst|battery <value>]`: read or set them.
- `/everytunnel noise [watch]`: recent noises, or yours live in the action bar.
- `/everytunnel paleman`: nearby Pale men, what each is doing and where he's going.
- `/everytunnel devworld save|forget`: dev runs only, see "Running it".
