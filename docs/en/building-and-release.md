# Building and release

*[Versión en español](../es/building-and-release.md) · [All pages](README.md)*

## Requirements

- **JDK 17** for the game and **JDK 21** for Gradle. `./gradlew` finds them on its own.
- Everything else (Minecraft, Fabric, libraries, the dev-only mods) is downloaded by the
  build.
- Python 3 for the [tools](tools.md), only when changing generated content.

## Gradle tasks

| Task | What it does |
|---|---|
| `./gradlew build` | Compiles, runs the unit tests, builds the jar in `build/libs/` |
| `./gradlew test` | Only the unit tests |
| `./gradlew runClient` | The game, at the main menu |
| `./gradlew runClientMapWorld` | Straight into `noname_complex` (quick play). Fails early if the world or its datapack is missing |
| `./gradlew runClientDevWorld` | Straight into `dev_flat`, the superflat test world |
| `./gradlew everytunnerBuild` | The release jar: `build/libs/everytunner_build.jar` |
| `./gradlew runDatagen` | Fabric data generation (no providers; assets come from the Python tools) |

Dev runs live in `run/` (not in git) and always log in as `Dev`, so worlds keep the player.
They also load the recommended mods (Sound Physics Remastered, Sodium, Iris, Indium) and
LambDynamicLights. The runtime classpath pins LWJGL to 3.3.1, because Sodium 0.5 refuses
the 3.3.2 Loom brings.

## Dev worlds

Both have to be created once, by hand: `./gradlew runClient`, then a **Superflat** world
with **commands allowed**, named exactly:

- `noname_complex`, the map. Then run `python3 tools/map/map_generator.py` to install the
  levels. The mod rebuilds them every time the world opens.
- `dev_flat`, for tests. Copy `examples/dev_flat_datapack/every_tunnel_levels` into
  `run/saves/dev_flat/datapacks/`. Its `level_2` has a Pale man spawn point.

**Starting from the same point every launch** (`platform.DevWorlds`, dev only): open the
world, stand where you want to start, run `/everytunnel devworld save` and quit. The world
is copied to `run/dev_templates/`, and each launch of the dev client puts it back before
opening it. `/everytunnel devworld forget` turns it off.

**A new PC:** install JDK 17 and 21; `./gradlew runClient` and create `noname_complex` as
above; `python3 tools/map/map_generator.py`; `./gradlew runClientMapWorld`.

## The release jar

`./gradlew everytunnerBuild` → `build/libs/everytunner_build.jar` (about 22 MB). One jar
with everything except Fabric Loader:

| Inside | How |
|---|---|
| The mod | `remapJar` |
| Fabric API, Fabric Language Kotlin, GeckoLib, LambDynamicLights | jar-in-jar (`include`); Fabric Loader uses a newer copy if the player has one |
| The map world | `packMapWorld` zips `run/saves/noname_complex` into `every_tunnel/world/noname_complex.zip` |
| `LICENSE.txt`, `CREDITS.md` | Renamed with the mod's name; the CC BY credit has to travel with the mod |

`packMapWorld` leaves out what belongs to whoever played the world: `playerdata/`,
`stats/`, `advancements/`, `session.lock`, `level.dat_old`, the mod's saved state
(`data/every_tunnel_*`) and `data/chunks.dat` (force-loaded chunks). It packs the world as
it is **at that moment**, so build the map first. Without that world (a fresh clone), the
jar simply has no map.

Not bundled, for licence reasons: Sodium, Iris, Indium, Sound Physics Remastered. They are
in `recommends` in `fabric.mod.json`.

### What a player does

1. Install Fabric Loader 0.19.5 or newer for Minecraft 1.20.1.
2. Put `everytunner_build.jar` in `mods/`.
3. Optionally: Sodium + Iris + Indium and a Complementary shader pack, and Sound Physics
   Remastered.
4. Start the game and press BEGIN.

On first launch `client.MapWorldInstaller` unpacks the world into `saves/noname_complex`,
removes the stored player and makes it Survival. A later mod version only replaces the
world's datapacks and `resources.zip`.

## Versions

All in `gradle.properties`:

| Component | Version |
|---|---|
| Minecraft / Yarn | 1.20.1 / 1.20.1+build.10 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.92.12+1.20.1 |
| Fabric Language Kotlin | 1.14.2+kotlin.2.4.21 |
| GeckoLib | 4.8.4 |
| LambDynamicLights | 4.4.0+1.20.1 |
| Sound Physics Remastered (dev) | fabric-1.20.1-1.5.1 |
| Sodium / Iris / Indium (dev) | 0.5.13 / 1.7.6 / 1.0.36 |
| Mod version | `mod_version` (0.1.0) |

## Open release questions

Still to decide before the first public release: the world resource pack prompt, cheats in
the shipped world, distribution channel and version name, install notes, which shader pack
to recommend, whether the `[playtest]` log stays on, and whether the jar still needs the
pre-built regions (the map rebuilds on every open anyway).
