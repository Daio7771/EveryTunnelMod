# Architecture

*[Versión en español](../es/architecture.md) · [All pages](README.md)*

## The big picture

Every Tunnel is a Fabric mod with two halves:

- **Common code** (`src/main/`) runs on the logical server. In singleplayer that is the
  integrated server inside the game. It owns the rules: levels, resources, noise, the
  Pale man, the game flow.
- **Client code** (`src/client/`) only runs on the game client. It owns what the player
  sees and hears: HUD, menus, loading screen, music, ambience, the flashlight's light.

Loom's `splitEnvironmentSourceSets()` keeps them apart: common code cannot reach client
classes by accident. The two talk through a few custom packets (see below).

Nothing happens outside **worlds with levels**. A world has levels when one of its
datapacks holds `data/every_tunnel/levels/*.json`. Any other world stays vanilla: no
world rules, no HUD, no flashlight, no loading screen.

## Layers

The common code is split by how much it depends on Minecraft:

```
config/   core/   systems/   entities/   util/      plain Kotlin, no Minecraft types, unit tested
world/                                              thin rules on top of core (still no MC types)
platform/                                           everything that touches Minecraft or Fabric
```

| Package | What it holds |
|---|---|
| `config` | `GameplayConfig` (every tunable number), its parser and the file on disk |
| `core` | `LevelManager` (loaded levels), `PlayerLocationTracker` (who is in which zone) |
| `core.level` | `LevelDefinition` and the level JSON parser |
| `core.guide` | `GuideMarkdown`, the reader of the player guide pages |
| `core.puzzle` | Level 3's valve code and the notes' text |
| `core.achievement` | `Achievement`, the eight achievements |
| `core.hiding` | `LookLimit`, how far the head turns in a locker |
| `systems` | `ResourceSystem` (stamina, thirst, battery), `NoiseSystem`, `FlashlightBeam` (beam math) |
| `entities` | `PaleManBrain`, the Pale man's decisions |
| `util` | `JsonFields`, a forgiving JSON reader shared by both parsers |
| `world` | `WorldRules` (when the rules apply), `LevelTriggers` (zone enter/exit listeners) |
| `platform` | Blocks, items, the entity, sounds, every `*Hooks` object, packets, commands |

The rule: **logic goes into the plain Kotlin layers, glue goes into `platform`.** The
plain layers take numbers, positions and UUIDs and return decisions; `platform` feeds them
from Minecraft and applies the result. That is why the brain, the resources, the noise
rules and the parsers have unit tests, and why a port to another Minecraft version should
mostly mean rewriting `platform` and the client.

The client (`src/client/kotlin/daio7771/everytunnel/client/`) is organised by what it
draws or plays:

| Package | What it holds |
|---|---|
| `client` | The client entrypoint, the flashlight key, the synced resources, shader detection and settings, the map world installer, the achievements earned |
| `client.compat` | LambDynamicLights integration (the flashlight's light) |
| `client.effects` | Everything heard (ambience, hum, heartbeat, breathing, music) and the screen fade |
| `client.hud` | Status bars, exhaustion vignette, the no-shader flashlight spot, the locker view, the achievement toast |
| `client.render` | The Pale man's renderer, loot on the floor, the guard uniform |
| `client.screen` | Main menu, player guide, achievements, loading screen and tips, end of demo and credits, the menu figure |

Mixins live in Java: `src/main/java/.../mixin/` (common) and
`src/client/java/.../mixin/client/` (client). See [Mixins](#mixins).

## Entrypoints

Declared in `src/main/resources/fabric.mod.json`:

| Entrypoint | Class | Job |
|---|---|---|
| `main` | `EveryTunnel` | Loads the config, then calls `register()` on every registry and hook object |
| `client` | `client.EveryTunnelClient` | Registers the client effects, HUD, screens, key, packets |
| `fabric-datagen` | `client.EveryTunnelDataGenerator` | Empty: assets are made by the Python tools instead |
| `lambdynlights:initializer` | `client.compat.LambDynamicLightsCompat` | Adds the flashlight beam as a dynamic light |

`EveryTunnel.onInitialize()` is the table of contents of the server side. The order
matters only in that registries (items, blocks, entities, sounds) come first.

## How a server tick flows

Most of the game is driven by Fabric's `ServerTickEvents`, one hook object per concern:

```
LevelTrackingHooks  position -> PlayerLocationTracker -> LevelTriggers (zone changes)
                                   |-> AmbienceHooks, RespawnHooks, LevelEntryHooks,
                                   |   PaleManSpawner, LootSpawner, EndingHooks, IntroHooks
ResourceHooks       sprint/jumps -> ResourceSystem.step -> damage, sync packet
NoiseHooks          steps, jumps, landings, panting, game events -> NoiseSystem.emit
PaleManEntity.tick  NoiseSystem.audibleAt -> loudness (walls) -> PaleManBrain.tick -> move/hit
LoadingHooks, IntroHooks, DeathHooks, KeycardHooks, LevelEntryHooks   timed scenes
PlaytestLog         listens to everything and writes [playtest] lines
```

`LevelTriggers` is the main extension point: a system that cares about players walking
into or out of zones registers a `LevelTriggers.Listener` once, and is called on the
server thread with a `LocationChange` (level changed, zones entered, zones left). A
listener that throws is logged and does not stop the others.

## Threads

- Everything in `platform` and the plain layers runs on the **server thread**.
  `NoiseSystem` and `ResourceSystem` assume it and are not synchronised.
- `LevelManager` and `PlayerLocationTracker` are safe from any thread: the level set is
  swapped as one immutable snapshot, and locations live in a `ConcurrentHashMap`.
- `ConfigManager.gameplay` is `@Volatile` and replaced as a whole.
- Client code runs on the **render thread**. Packets from the server are handed to it with
  `client.execute { ... }`.

## Packets

All ids are `every_tunnel:<name>`. Fabric networking API (1.20.1 style,
`ServerPlayNetworking` / `ClientPlayNetworking` with `PacketByteBuf`).

| Id | Direction | Sent by | What it carries |
|---|---|---|---|
| `resources` | server → client | `ResourceSyncPacket` | Stamina, thirst, battery, flashlight on, exhausted, active; rounded to 0.1, sent only on change |
| `toggle_flashlight` | client → server | `FlashlightControls` | The Flashlight key was pressed |
| `ambience` | server → client | `AmbienceHooks` | The ambience of the player's current level |
| `intro_music` | server → client | `IntroHooks` | Start or end (distort and fade) the intro song |
| `screen_fade` | server → client | `DeathHooks` | Target darkness (0 to 1) and seconds to get there |
| `loading` | server → client | `LoadingHooks` | Loading state: `0` loading, `1` ready |
| `loading_done` | client → server | `LoadingCover` | The client has drawn the world and faded in |
| `ending` | server → client | `EndingHooks` | Open the end-of-demo screen |

The Pale man's state reaches clients as tracked entity data (`PaleManEntity.brainState`),
not as a packet.

## Saved data

What the mod remembers is saved with the world, in `<world>/data/`, as vanilla
`PersistentState` files:

| File | Owner | What it remembers |
|---|---|---|
| `every_tunnel_resources.dat` | `ResourceHooks` | Each player's stamina, thirst, battery, flashlight |
| `every_tunnel_intro.dat` | `IntroHooks` | Who has had the elevator intro |
| `every_tunnel_loot.dat` | `LootSpawner` | Which loot spots were filled |
| `every_tunnel_pale_men.dat` | `PaleManSpawner` | Which Pale man spawn points have spawned |
| `every_tunnel_lights.dat` | `KeycardHooks` | The lights the keycard put out, to restore them |

`packMapWorld` leaves all of these out of the release jar, so a fresh install starts clean.

Outside the world, in the game's `config/` folder: `every_tunnel/gameplay.json` (the
gameplay config) and `every_tunnel/shader_settings_applied.txt` (which shader packs
`ShaderSettings` has already set up).

## Mixins

Kept to the minimum: a mixin only where Fabric API has no event.

| Mixin | Target | Why |
|---|---|---|
| `ServerWorldMixin` | `ServerWorld.emitGameEvent` | Lets the noise system hear doors, levers, chests (vanilla "vibrations"); changes nothing |
| `LivingEntityMixin` | `LivingEntity.shouldSpawnConsumptionEffects` | No vanilla sipping sound for the water bottle; it plays its own gulps |
| `ClientPlayerEntityMixin` | `canSprint` | No sprinting while exhausted (sprint is decided on the client) |
| `InGameHudMixin` | `renderStatusBars`, `renderExperienceBar` | Hides vanilla bars while the mod's HUD replaces them |
| `AbstractClientPlayerEntityMixin` | `getSkinTexture`, `getModel`, `getCapeTexture` | Every player wears the guard uniform in level worlds |
| `LightmapTextureManagerMixin` | `update` (gamma) | Brightness counts as Moody in the levels |
| `MinecraftClientMixin` | `setScreen` | Replaces vanilla's title screen with the main menu |
| `MusicTrackerMixin` | `tick` | No vanilla music anywhere |
| `ScreenMixin` | `renderWithTooltip` | Draws the loading screen over vanilla's loading screens |
| `SplashOverlayMixin` | `render` | Draws the loading screen over the resource reload splash |
| `WorldListEntryMixin` | `openReadingWorldScreen` | Notices a world with levels opening from the world list |
| `QuickPlayMixin` | `startSingleplayer` | The same for quick play (dev runs, launchers) |
| `SoundSystemMixin` | `getAdjustedVolume`, `reloadSounds` | Silence behind the loading screen, fading in with the picture; menu music restarts after a sound reload |

## Design principles

- **Levels are data, not code.** A level is a JSON file of areas and points in the world's
  datapack; the blocks are built by a function. See [Levels](levels.md).
- **Forgiving input.** Config and level files never crash the game: every bad value is
  reported with its JSON path and replaced by its default, or the broken entry is skipped.
- **Vanilla stays vanilla.** Every feature checks `WorldRules.active` or
  `WorldRules.appliesTo(...)`. Creative and Spectator are exempt from the rules, so the
  map can be built by hand.
- **Generated content.** Sounds, textures, models and the map come from seeded Python
  scripts, so they can be remade from parameters. See [Python tools](tools.md).
- **Comments say why.** Every file starts with a KDoc that explains what it does and how
  it fits. This documentation summarises those headers; when in doubt, the code is right.
