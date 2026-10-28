# Code map

*[Versión en español](../es/code-map.md) · [All pages](README.md)*

Every source file and what it does. Paths are relative to `src/`. Each file starts with a
KDoc comment that says more.

## Common: `main/kotlin/daio7771/everytunnel/`

| File | What it does |
|---|---|
| `EveryTunnel.kt` | Common entrypoint: loads the config, registers everything |

### `config/`

| File | What it does |
|---|---|
| `GameplayConfig.kt` | Every tunable value with its default, in sections (stamina, thirst, flashlight, noise, Pale man) |
| `GameplayConfigParser.kt` | JSON ↔ config, value ranges, problems and missing keys. No file IO |
| `ConfigManager.kt` | Loads and writes `config/every_tunnel/gameplay.json` safely |

### `core/`, `core/level/`, `core/guide/`, `core/menu/` and `core/puzzle/`

| File | What it does |
|---|---|
| `LevelManager.kt` | The loaded levels, as one immutable snapshot; zone lookups by position |
| `PlayerLocationTracker.kt` | Each player's level and zones; reports `LocationChange`s |
| `level/LevelDefinition.kt` | The level model: zones, spawn points, ambience, entry, intro, build, `BlockBox` |
| `level/LevelParser.kt` | Parses one level file; documents every key |
| `guide/GuideMarkdown.kt` | Reads a player guide page (the Markdown subset) into blocks |
| `menu/LampFlicker.kt` | When the menu scene's lamps flicker |
| `menu/BlockLight.kt` | Block light for a box of blocks with no world (the menu scene) |
| `achievement/Achievement.kt` | The eight achievements, in list order, by id |
| `puzzle/ValveCode.kt` | Level 3's valve puzzle: random digits, a turn, solved |
| `puzzle/NoteText.kt` | A note's text: title, paragraphs, `{A}` / `{A:word}` placeholders |
| `hiding/LookLimit.kt` | How far the head turns inside a locker |

### `systems/`

| File | What it does |
|---|---|
| `ResourceSystem.kt` | Stamina, thirst and battery simulation, as pure functions |
| `NoiseSystem.kt` | Noise events, their memory, audibility, and the rules that pick radii |
| `FlashlightBeam.kt` | The beam's light at any point (cone, falloff, hot spot, glow) |

### `entities/`

| File | What it does |
|---|---|
| `PaleManBrain.kt` | The Pale man's state machine and loudness formula |
| `Package.kt` | Package doc |

### `util/` and `world/`

| File | What it does |
|---|---|
| `util/JsonFields.kt` | Forgiving typed JSON reader that collects problems with their path |
| `world/WorldRules.kt` | Whether the rules apply (a world with levels, Survival/Adventure) |
| `world/LevelTriggers.kt` | Listeners for players entering and leaving zones |

### `audio/`

| File | What it does |
|---|---|
| `Package.kt` | Placeholder package doc (sounds live in `platform.ModSounds` and the client) |

### `platform/`: registries

| File | What it does |
|---|---|
| `ModBlocks.kt` | Every block: the light, props, doors, signs, elevator parts, loot, card reader |
| `ModItems.kt` | Battery, water bottle, keycard; the creative tab |
| `ModEntities.kt` | The Pale man entity type and spawn egg; the locker seat |
| `ModSounds.kt` | Sound events played from code |
| `ModSoundGroups.kt` | Block sound groups per prop material |
| `Package.kt` | Package doc: Minecraft-version-dependent glue |

### `platform/`: blocks

| File | What it does |
|---|---|
| `BlockShapes.kt` | Outline and collision boxes in pixels, rotated like the blockstates |
| `PropBlock.kt` | A floor prop facing one of four sides |
| `ConnectingPropBlock.kt` | Pipes, ducts and cable trays that connect to neighbours |
| `FluorescentLightBlock.kt` | The light: on, flicker, off; light level 13, drawn full bright |
| `SurfaceMarkBlocks.kt` | Stains and puddles on any face |
| `SignTileBlock.kt` | Painted texts by numbered slot (the world pack maps them) |
| `ElevatorDoorBlock.kt` | Sliding elevator door stages |
| `FloorIndicatorBlock.kt` | The elevator's LED floor display |
| `ShutterBlock.kt` | A piece of a rolling shutter |
| `CardReaderBlock.kt` | The keycard reader that opens iron doors |
| `NoteBoardBlock.kt` | A cork board with a note: right-click reads it |
| `BoxBlocks.kt` | Level 3's purple box (picked up), its shelf (parts, slots, the boxes' block entity) and the office book |
| `LockerBlock.kt` | A locker (a two-block stack): right-click hides in it |
| `LockerSeatEntity.kt` | The invisible seat a hiding player rides: look limit, getting out in front, the door's sound |
| `FurnitureBlocks.kt` | Level 3's furniture that joins its own kind: row pieces (desks, counters, sofas) left and right, table tiles on all sides, stacks (bookshelves, racks, lockers) up and down |
| `ValveBlock.kt` | A gate valve inline in a wall pipe: facing, open, letter A to E (Level 3) |
| `LootBlock.kt` | Loot lying on the floor, picked up with right-click |

### `platform/`: the Pale man

| File | What it does |
|---|---|
| `PaleManEntity.kt` | The entity: hearing, walls, territory, pathing, hits, sounds, animations |
| `PaleManSpawner.kt` | Spawns one per `pale_man` spawn point, once per world; new-game counter |

### `platform/`: hooks (game logic driven by events)

| File | What it does |
|---|---|
| `LevelReloadListener.kt` | Loads level files from datapacks on world load and `/reload` |
| `LevelTrackingHooks.kt` | Feeds positions to the tracker every tick, fires `LevelTriggers` |
| `WorldRulesHooks.kt` | No breaking/placing, no mob spawning, Normal difficulty |
| `ResourceHooks.kt` | Runs `ResourceSystem` each tick, saves and syncs resources |
| `ResourceSyncPacket.kt` | The resources packet to the client |
| `FlashlightHooks.kt` | The flashlight toggle request, battery items, feedback |
| `DrinkHooks.kt` | Drinking water bottles |
| `JumpCounter.kt` | Jumps per player from the vanilla statistic |
| `NoiseHooks.kt` | Turns steps, jumps, landings, panting and game events into noises |
| `AmbienceHooks.kt` | Sends each player their level's ambience |
| `RespawnHooks.kt` | Sets the respawn point to the current level's start |
| `DeathHooks.kt` | Death as fade to black and level restart |
| `LoadingHooks.kt` | Rebuild on open, loading state, new game, safety timeouts |
| `NewGame.kt` | Resets the world and the player for a new game |
| `IntroHooks.kt` | The elevator intro: doors, song, floor indicators, title |
| `LevelEntryHooks.kt` | Level titles and closing shutters behind the player |
| `LootSpawner.kt` | Places and restocks loot at tagged spawn points |
| `KeycardHooks.kt` | Lights-out wave, restoring lights, locking readers |
| `ValveHooks.kt` | Level 3's valve puzzle: digits per new game, checking turns, opening the door, resetting |
| `NoteHooks.kt` | Reading a pinned note: finds its id, sends it with the valve digits |
| `CarryHooks.kt` | Carrying a box or a book: in the hand, never dropped, no sprint, back on death |
| `BookHooks.kt` | Level 3's book puzzle: books appear, handed in at the counter, the hatch opens, debug |
| `BoxHooks.kt` | Level 3's box puzzle: boxes out at random, carrying, the shelf, death, debug |
| `HidingHooks.kt` | Hiding in lockers: in, out, no noise inside, out on a new game |
| `AchievementHooks.kt` | Earning achievements: what earns each, telling the client |
| `EndingHooks.kt` | The end of the demo: footsteps, fade, end screen |
| `DebugCommands.kt` | `/everytunnel ...` |
| `DevWorlds.kt` | Dev-only world templates restored on every launch |
| `PlaytestLog.kt` | The `[playtest]` log |

### Common mixins: `main/java/daio7771/everytunnel/mixin/`

| File | What it does |
|---|---|
| `ServerWorldMixin.java` | Lets the noise system hear vanilla game events |
| `LivingEntityMixin.java` | No vanilla sipping sound for the water bottle |

## Client: `client/kotlin/daio7771/everytunnel/client/`

| File | What it does |
|---|---|
| `EveryTunnelClient.kt` | Client entrypoint: registers everything below |
| `EveryTunnelDataGenerator.kt` | Empty datagen entrypoint |
| `ClientResources.kt` | The player's resources as last sent by the server |
| `FlashlightControls.kt` | The Flashlight key (F) |
| `ShaderMode.kt` | Shaders on or off, asked live through Iris's API |
| `ShaderSettings.kt` | Writes the recommended Complementary settings once |
| `MapWorldInstaller.kt` | Unpacks the map world from the jar into `saves/` |
| `AchievementStore.kt` | The achievements earned, kept for good in `config/every_tunnel/achievements.json` |
| `AchievementIcons.kt` | Each achievement's icon (an item or one drawn for the mod, or the locked "?") |
| `compat/LambDynamicLightsCompat.kt` | The beam as a LambDynamicLights light source |

### `effects/`

| File | What it does |
|---|---|
| `AmbiencePlayer.kt` | Level loop and random positioned sounds |
| `FluorescentHum.kt` | Hum of the 4 nearest lights |
| `HeartbeatEffect.kt` | Heartbeat by distance to the Pale man |
| `BreathingEffect.kt` | Heavy breathing when exhausted |
| `TensionMusic.kt` | Music layers from the Pale men's state |
| `IntroMusic.kt` | The elevator song and its tape-stop fade |
| `MenuMusic.kt` | The menu theme |
| `FlashlightEffect.kt` | Computes the beam each tick; low-battery flicker |
| `ScreenFade.kt` | Black fades sent by the server |

### `hud/`

| File | What it does |
|---|---|
| `StatusHud.kt` | Health, stamina, thirst and battery rows |
| `ExhaustionVignette.kt` | Dark screen edges when exhausted or parched |
| `FlashlightOverlay.kt` | The on-screen spot without shaders |
| `LockerView.kt` | The inside of a locker's door over the screen while hiding |
| `AchievementToast.kt` | The "Achievement Unlocked!" toast; receives the earned achievements |

### `render/`

| File | What it does |
|---|---|
| `PaleManRenderer.kt` | GeckoLib renderer with the eye glow mask |
| `PaleManModel.kt` | His GeckoLib model, plus the stoop under low ceilings |
| `BoxShelfRenderer.kt` | The purple boxes on the box shelf, full bright |
| `LootRenderer.kt` | Draws loot items lying flat |
| `GuardUniform.kt` | The uniform skin for every player |

### `menu/`

| File | What it does |
|---|---|
| `MenuScene.kt` | The parking hall behind the main menu: loads, lights, flickers and draws it |
| `MenuSceneView.kt` | The scene's blocks and light as a world for the block renderer |
| `RotatedModel.kt` | A baked model turned by quarter turns (the scene's turned blocks) |

### `screen/`

| File | What it does |
|---|---|
| `MainMenuScreen.kt` | The main menu |
| `AchievementsScreen.kt` | The ACHIEVEMENTS screen: the list on paper |
| `Paper.kt` | Worn paper (tile, stains, edges, inks) for the guide and the achievements |
| `GuideScreen.kt` | The player guide: a scrolling paper sheet with tabs |
| `NoteScreen.kt` | A note read on the guide's paper over the game, placeholders filled |
| `LoadingScreen.kt` | The loading screen and `LoadingCover`, which draws it over everything |
| `LoadingTips.kt` | Rotating tips with the player's keys |
| `EndOfDemoScreen.kt` | "End of demo" and the rolling credits |

### Client mixins: `client/java/daio7771/everytunnel/mixin/client/`

See [Architecture](architecture.md#mixins) for the target and reason of each.

## Resources: `main/resources/`

| Path | What it holds |
|---|---|
| `fabric.mod.json` | Mod metadata, entrypoints, dependencies |
| `every_tunnel.mixins.json`, `client/resources/every_tunnel.client.mixins.json` | Mixin lists |
| `assets/every_tunnel/lang/en_us.json` | Every in-game text |
| `assets/every_tunnel/lang/es_*.json` | Only the guide screen's own words in Spanish; everything else falls back to English |
| `assets/every_tunnel/guide/` | Not in `src/`: copied from `docs/*/guide/` by the build (`copyGuide`) |
| `assets/every_tunnel/sounds.json`, `sounds/` | Sound events and `.ogg` files (from `tools/sounds/export.py`) |
| `assets/every_tunnel/textures/`, `models/`, `blockstates/` | From `tools/blocks`, `tools/entity`, `tools/gui` |
| `assets/every_tunnel/geo/`, `animations/` | The Pale man's GeckoLib files |
| `assets/every_tunnel/menu/`, `models/block/menu/`, `textures/block/menu/` | The menu scene (from `tools/map/map_generator.py`) |
| `data/every_tunnel/loot_tables/` | Block loot tables |

## Tests: `test/kotlin/daio7771/everytunnel/`

See [Debugging](debugging.md#unit-tests).
