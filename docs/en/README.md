# Every Tunnel: technical docs

Every Tunnel is a singleplayer psychological horror mod for Minecraft 1.20.1 (Fabric,
Kotlin). The player is the new night-shift security guard of the Noname Complex, an
abandoned underground facility. The first release is a demo of two levels in its parking
garage: Level 1 (B1) has no enemies and teaches the player to listen; Level 2 (B2) adds the
Pale man, who is blind and hunts by sound. Sound design is the core of the mod.

*[Versión en español](../es/README.md)*

## Who these pages are for

- **Modders** who want to understand, change or extend the mod.
- **AI assistants** working on the code: start with [Contributing](contributing.md), which
  lists the rules of the project, then [Architecture](architecture.md).
- **Players** who want to know more than the [player guide](guide/01-how-to-play.md) says.

## Pages

Read them in this order the first time:

| Page | What it covers |
|---|---|
| [Architecture](architecture.md) | Layers, source sets, entrypoints, threads, packets, saved data, mixins |
| [Game flow](game-flow.md) | From the main menu to the end credits, step by step |
| [Levels](levels.md) | The level JSON format, zone and spawn point tags, how levels are built |
| [Player systems](player.md) | Stamina, thirst, flashlight, HUD, loot, world rules, the config file |
| [Noise and the Pale man](noise-and-pale-man.md) | The noise system, his hearing AI, his entity and music |
| [Audio](audio.md) | Every sound event, the client players, how sounds are made |
| [Graphics](graphics.md) | With and without shaders, lights, brightness, textures, art direction |
| [Python tools](tools.md) | The generators for map, sounds, blocks, entity art and GUI |
| [Building and release](building-and-release.md) | Gradle tasks, dev worlds, the release jar |
| [Debugging](debugging.md) | Debug commands and the `[playtest]` log |
| [How-to recipes](how-to.md) | Adding a level, a sound, a block, a tip, a config value... |
| [Code map](code-map.md) | Every source file and what it does |
| [Contributing](contributing.md) | Conventions, testing, commits, rules for AI assistants |

## Player guide

These pages are also shown in game (main menu, GUIDE):

1. [How to play](guide/01-how-to-play.md)
2. [Noise](guide/02-noise.md)
3. [Graphics and mods](guide/03-graphics-and-mods.md)
4. [Technical data](guide/04-technical-data.md)

## Quick facts

| | |
|---|---|
| Minecraft | 1.20.1, Fabric Loader 0.19.5 |
| Language | Kotlin 2.4 (and a few Java mixins), Java 17 target |
| Mod id | `every_tunnel` |
| Package | `daio7771.everytunnel` |
| Required (bundled in the jar) | Fabric API, Fabric Language Kotlin, GeckoLib |
| Required (installed by the player) | LambDynamicLights |
| Recommended | Sound Physics Remastered, Sodium + Iris + Indium |
| Licence | All rights reserved (everything own; MIT up to 0.2.0), CC0 recordings, one CC BY song |
