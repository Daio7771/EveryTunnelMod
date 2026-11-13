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
| `GameplayConfig.kt` | Every tunable value with its default, in sections (stamina, thirst, food, flashlight, wrench, detector, noise, Pale man) |
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
| `achievement/Achievement.kt` | The eleven achievements, in list order, by id |
| `puzzle/ValveCode.kt` | Level 3's valve puzzle: random digits, a turn, solved |
| `puzzle/OrderPuzzle.kt` | A puzzle of things in order: attempts, failures, the hint |
| `door/SlidingDoor.kt` | A door of two leaves sliding into the walls a quarter of a block at a time: how much of each column is left (Level 5's airlock) |
| `save/LoungeSave.kt` | Level 4 as it was left, as text |
| `camera/CameraAim.kt` | Where a security camera's head points, turning towards a target no faster than its servo |
| `cutscene/Cutscene.kt` | A cinematic as plain data: camera poses, a scene's frame, shots, the fly-in and fly-out, the black bands |
| `cutscene/DirectorScene.kt` | The director's scene timed from his voice: framings, subtitles, his gestures, where he looks away, his mask; `SkipPrompt` (skipping by pressing twice) |
| `cutscene/DirectorLines.kt` | The director's voice lines and their subtitle cues, read from `cutscene/director.json` |
| `cutscene/DirectorLoop.kt` | The phrases he repeats after his scene: their order, the gaps, how much he looks at the player |
| `cutscene/FinalCredits.kt` | The final credits' timeline (drawn by `FinalCreditsScreen`) |
| `wall/WallMessageState.kt` | A wall message's triggers: fixed, on approach, changing when looked away from |
| `puzzle/BreakerCode.kt` | Level 4's five levers: the code, how many are right |
| `puzzle/ChemicalMix.kt` | Level 5's chemical puzzle: the pair of reagents drawn each game, either way round |
| `puzzle/SafeDial.kt` | A safe's dial: ticks, clicks, fixing a number by changing direction |
| `puzzle/darts/DartboardGeometry.kt` | Where a dart landed on the board: number and ring, in the 4 orientations |
| `film/FilmInfo.kt` | The Cinema's film: frames, speed, which frame at what time |
| `progress/PlayerProgress.kt` | What a player has done: pieces, notes read, scenes seen, Level 3's books, checkpoint |
| `ending/RunSummary.kt` | The congratulations window's numbers: time played and deaths of a game, the time as `H:MM:SS` |
| `progress/Checkpoint.kt` | A checkpoint as text: a level's start, or `level/point` for a restart point inside it |
| `detector/Detector.kt` | Level 5's detector: how full its icon is, when the triangle shows, how many Geiger counts fall a tick |
| `death/DeathCause.kt` | What the death screen names (the killer, thirst, the fall, deep water) and where it shows |
| `puzzle/NoteText.kt` | A note's text: title, paragraphs, `{A}` / `{A:word}` placeholders |
| `hiding/LookLimit.kt` | How far the head turns inside a locker |
| `spawn/PostLedger.kt` | Which creature owns each spawn point, so a dying or late-loading leftover never wipes the record of the one that replaced it (the Observer) |

### `systems/`

| File | What it does |
|---|---|
| `ResourceSystem.kt` | Stamina, thirst and battery simulation, as pure functions |
| `NoiseSystem.kt` | Noise events, their memory, audibility, and the rules that pick radii |
| `FlashlightBeam.kt` | The beam's light at any point (cone, falloff, hot spot, glow) |
| `CollapseSchedule.kt` | The timing of Level 3's tunnel collapse: rubble, flicker, dust, the camera shake's strength |
| `ObserverNearness.kt` | Level 3's book phase: the clock's pace and volume and the "He's near" warning from the Observer's distance |

### `entities/`

| File | What it does |
|---|---|
| `PaleManBrain.kt` | The Pale man's state machine and loudness formula |
| `ObserverBrain.kt` | The Observer's state machine (patrol, alerted, chase, search, grab) and his numbers |
| `HunterBrain.kt` | The Hunters' state machine (wander, chase, search, return) and their hits |
| `Gaze.kt` | Where a player looks: in front (the Cloud), on target (the Junior's eye) |
| `DistortionClocks.kt` | The Cloud's clock (grace, going), the Junior's (the stare), when the next Cloud comes |
| `CloudGame.kt` | Level 5's entry corridor game, as Halt plays it: the Cloud behind, its jumps in front and back, quickening |
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
| `LabItems.kt` | Level 5's wrench: a heavy weapon that never wears out |
| `ChemicalBlocks.kt` | Level 5's mixer: its tanks (a reagent flask each), its body (the button), the alarm's red lights |
| `TerminalHooks.kt` | Level 5's computers (`LabTerminalBlock`): what each screen shows, as translation keys (`terminal.every_tunnel.*`) the client translates |
| `BriefingBlocks.kt` | Level 5's safety briefing: the cork board's twelve parts (a right click reads the sheet of the column it lands in) and the plate over it |
| `FuseHolderBlock.kt` | A slot of Level 5's fuse box: takes only the fuse of its amperage; the box full, its lamps light and the lever beside it works |
| `ModEntities.kt` | The Pale man entity type and spawn egg; the locker seat |
| `ModSounds.kt` | Sound events played from code |
| `ModSoundGroups.kt` | Block sound groups per prop material |
| `Package.kt` | Package doc: Minecraft-version-dependent glue |

### `platform/`: blocks

| File | What it does |
|---|---|
| `BlockShapes.kt` | Outline and collision boxes in pixels, rotated like the blockstates; mobs path round any block of the mod with collision |
| `PathingRules.kt` | Keeps mobs out of and off the mod's solid blocks (furniture, props): they path round them, never through or over |
| `PropBlock.kt` | A floor prop facing one of four sides |
| `ConnectingPropBlock.kt` | Pipes, ducts and cable trays that connect to neighbours |
| `FluorescentLightBlock.kt` | The light: on, flicker, off; light level 13, drawn full bright |
| `DrainBlocks.kt` | Level 5's drain: its ladder, its caged lamps (dim, flickering like the fluorescents), the office's panel lights, the catwalks' railing, the chalk arrow to the Hideout |
| `WarmLampBlock.kt` | Level 4's ceiling light: warm and silent, light level 14 |
| `SuiteBedBlock.kt` | A bed in Level 4's Suites, in two blocks |
| `PuzzleRackBlock.kt` | A rack holding a record or a reel, with its glow |
| `PuzzleDeviceBlock.kt` | The record player and the projector |
| `SurfaceMarkBlocks.kt` | Stains and puddles on any face |
| `SignTileBlock.kt` | Painted texts by numbered slot (the world pack maps them) |
| `ElevatorDoorBlock.kt` | Sliding elevator door stages |
| `FloorIndicatorBlock.kt` | The elevator's LED floor display |
| `ShutterBlock.kt` | A piece of a rolling shutter |
| `CardReaderBlock.kt` | The keycard reader that opens iron doors |
| `NoteBoardBlock.kt` | A cork board with a note: right-click reads it |
| `NotePaperBlock.kt` | A loose typed sheet on a desk (Level 3's documents): right-click reads it |
| `WallTextBlock.kt` | Text on a wall in the game's font (Level 3's and Level 5's nameplates, entrance lettering) and its block entity |
| `BoxBlocks.kt` | Level 3's purple box (picked up), its shelf (parts, slots, the boxes' block entity) and the office book |
| `LockerBlock.kt` | A locker (a two-block stack): right-click hides in it |
| `LockerSeatEntity.kt` | The invisible seat a hiding player rides: look limit, getting out in front, the door's sound |
| `FurnitureBlocks.kt` | Level 3's furniture that joins its own kind: row pieces (desks, counters, sofas) left and right, table tiles on all sides, stacks (bookshelves, racks, lockers) up and down |
| `ValveBlock.kt` | A gate valve inline in a wall pipe: facing, open, letter A to E (Level 3) |
| `LootBlock.kt` | Loot lying on the floor, picked up with right-click |

### `platform/`: the enemies

| File | What it does |
|---|---|
| `PaleManEntity.kt` | The entity: hearing, walls, territory, pathing, hits, sounds, animations |
| `PaleManSpawner.kt` | Spawns one per `pale_man` spawn point, once per world; new-game counter |
| `ObserverEntity.kt` | The Observer: sight (cone, range, walls), moving and looking round, catching, pulling out of lockers |
| `ObserverHooks.kt` | Spawns the Observer, passes him the box alerts, sends him home on a death; on a map rebuild discards the old ones and puts one back |
| `HunterEntity.kt` | A Hunter: sight (cone, range, walls), telling its group, moving, hits, sounds, animations |
| `DistortionEntity.kt` | What the Cloud and the Junior share: floating, untouchable, facing the player, stare and going synced, smoke |
| `DistortionCloudEntity.kt` | The Distortion Cloud: who looks its way, killing, going; the entry corridor's game's one |
| `DistortionJuniorEntity.kt` | The Distortion Junior: whom it troubles and marks, the stare that ends it |
| `LabFigureEntity.kt` | The figure seen from Level 5's Lobby: once looked at, it walks off round the corner and is gone |
| `LabFigureHooks.kt` | Puts the figure there when Level 5 is built, once a game |
| `SubjectEntity.kt` | The test subjects, sedated and strapped to their stretchers in their sealed chambers, by stage; `SubjectHooks` puts them in with the level |
| `DeadGuardEntity.kt` | The guard before you, dead in the drain's Hideout; `DeadGuardHooks` puts him there with the level |
| `DirectorEntity.kt` | The director in his chair (GeckoLib): moves with the client's scene, mask and gaze synced; `DirectorHooks` puts him there with the level |
| `LabHallHooks.kt` | The Lab hall's sting and first Cloud, once a game |
| `ChemicalHooks.kt` | The chemical puzzle: the game's pair, the mixer run, the alarm and its enemies, the grate dissolving, the hatch |
| `DirectorBlocks.kt` | Level 5's anteroom and office: the director's double door that opens both leaves at once, the security camera block, the wall of CCTV screens |
| `DeepWaterHooks.kt` | Level 5's deep water (the drain's pools): falling in pulls the player under, and they drown |
| `DrainHooks.kt` | Level 5's drain: the hatch slamming shut once the player is down its ladder, open again on a death before the Junction |
| `DistortionHooks.kt` | Level 5's enemies: posts from spawn points, back on a death, telling the Hunters, when and where a Cloud comes, `/everytunnel spawn` |
| `CloudGameHooks.kt` | The entry corridor's game: started and ended by its zone, its Cloud moved along the corridor's line |

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
| `FoodHooks.kt` | Eating: health back |
| `WaterDispenserHooks.kt` | Drinking at a water dispenser that gives water (Level 3's kitchen) |
| `JumpCounter.kt` | Jumps per player from the vanilla statistic |
| `NoiseHooks.kt` | Turns steps, jumps, landings, panting and game events into noises |
| `AmbienceHooks.kt` | Sends each player their level's ambience |
| `RespawnHooks.kt` | Sets the respawn point to the current level's start |
| `DeathHooks.kt` | Death as fade to black and level restart |
| `LoadingHooks.kt` | Rebuild on open, loading state, new game, safety timeouts |
| `NewGame.kt` | Resets the world and the player for a new game |
| `ProgressService.kt` | The player's progress as the rest of the mod sees it |
| `CardinalProgress.kt` | `ProgressService` on Cardinal Components, the only class that touches that library |
| `IntroHooks.kt` | The elevator intro: doors, song, floor indicators, title |
| `LevelEntryHooks.kt` | Level titles and closing shutters behind the player |
| `LootSpawner.kt` | Places and restocks loot at tagged spawn points |
| `KeptEntities.kt` | Entities never saved but put in far from the player (Level 5's): back where they were put once a chunk that dropped them loads again |
| `KeycardHooks.kt` | Lights-out wave, restoring lights, locking readers |
| `ValveHooks.kt` | Level 3's valve puzzle: digits per new game, checking turns, opening the door, resetting |
| `NoteHooks.kt` | Reading a note (board or sheet): finds its id, sends it with the valve digits |
| `CarryHooks.kt` | Carrying a box or a book: in the hand, never dropped, no sprint, back on death |
| `BookHooks.kt` | Level 3's book puzzle: books appear, handed in at the counter, the hatch opens, debug |
| `CollapseHooks.kt` | Level 3's tunnel collapse: sound, shake, rubble, dust, flickering lights; undone on a death or new game |
| `LoungeHooks.kt` | From Level 3 to Level 4: the door to B4, erasing and building in steps, Reception's door |
| `LabHooks.kt` | Level 5 built in play from Level 4's dark stretch, in steps; the airlock's doors, behind and ahead |
| `FilmSetHooks.kt` | The set for filming the Cinema's film, away from the levels |
| `FilmWalkerEntity.kt` | The figure who walks along it |
| `LoungePuzzleHooks.kt` | Level 4's Bar and Cinema puzzles, hints, and the pieces of the master card |
| `DartHooks.kt` | Level 4's darts: the tray, each hit, the hint, the hatch with the piece |
| `DartBlocks.kt` | The dartboard's nine parts, the tray and the hatch |
| `DartEntity.kt` | A thrown dart, built on vanilla's arrow; never saved |
| `LoungeClosingHooks.kt` | Level 4's closing: the blackout, the emergency lamps, the big door |
| `LoungeClosingBlocks.kt` | The big door, the Calm room's reader, the emergency lamp |
| `LabBlocks.kt` | Level 5's airlock door: a column of a leaf, how much of it is across |
| `SlidingDoors.kt` | Level 5's sliding doors in the world: setting how open they are, sliding one open over time |
| `LabDoorHooks.kt` | Level 5's locked doors: the levers, the south route opening on walking out of the Office |
| `BreakerHooks.kt` | Level 4's Maintenance room: levers, button, counter, power for the reader |
| `SafeHooks.kt` | The safes in the Suites: the combinations, the block, the result from the client |
| `ContinueGame.kt` | The saved game: checkpoints (a level's restart points: the one inside the zone walked into), CONTINUE, putting Level 4 back |
| `WallMessageBlock.kt` | A message painted on a wall: the block and its data |
| `SafeLevelHooks.kt` | Levels without risk: no damage (Level 4) |
| `SuiteBedHooks.kt` | Resting in a bed: fade to black, health and stamina full |
| `BookPhaseHooks.kt` | While the books are out: the clock in the head and the "He's near." warning |
| `BoxHooks.kt` | Level 3's box puzzle: boxes out at random, carrying, the shelf, death, debug |
| `HidingHooks.kt` | Hiding in lockers: in, out, no noise inside, out on a new game |
| `AchievementHooks.kt` | Earning achievements: what earns each, telling the client |
| `EndingHooks.kt` | The end of the game: the saved game erased, the final credits, the game's numbers sent with them |
| `FalseExitHooks.kt` | Level 5's false exit: the floor over the pit giving way, the fall, the black |
| `DirectorSceneHooks.kt` | The director's scene: starts when his door is opened, holds the player, the door's slam, skipping, the end (a restart point inside his door); `DirectorLoopHooks`, in the same file, sends his phrases after it |
| `DebugCommands.kt` | `/everytunnel ...` |
| `DevWorlds.kt` | Dev-only world templates restored on every launch |
| `PlaytestLog.kt` | The `[playtest]` log |
| `RunStatsHooks.kt` | Counts the time played into the player's progress (deaths are counted in `DeathHooks`), for the congratulations window |
| `TickProfiler.kt` | Times the server tick and each tick hook (`/everytunnel profile`); `core/profile/TickStats.kt` holds the numbers |

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
| `SprintCheck.kt` | Logs why the player does not sprint while the sprint key is held |
| `AchievementStore.kt` | The achievements earned, kept for good in `config/every_tunnel/achievements.json` |
| `AchievementIcons.kt` | Each achievement's icon (an item or one drawn for the mod, or the locked "?") |
| `compat/LambDynamicLightsCompat.kt` | The beam as a LambDynamicLights light source; the entry corridor's Clouds' light |

### `effects/`

| File | What it does |
|---|---|
| `AmbiencePlayer.kt` | Level loop and random positioned sounds |
| `FluorescentHum.kt` | Hum of the 4 nearest lights |
| `HeartbeatEffect.kt` | Heartbeat by distance to the Pale man |
| `DetectorEffect.kt` | Level 5's detector: the nearest Cloud in sight, the Geiger clicks |
| `DistortionEffect.kt` | The Distortions on the senses: whispers, the screen distorting at a Cloud, the violet of the entry corridor's, the Junior's tremor and drift |
| `CameraShake.kt` | The camera shaking in Level 3's tunnel collapse |
| `FilmPlayer.kt` | The Cinema's film on its screen, and the projector's sound |
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
| `DetectorHud.kt` | The radiation sign filling up and the warning triangle |
| `ExhaustionVignette.kt` | Dark screen edges when exhausted or parched |
| `FlashlightOverlay.kt` | The on-screen spot without shaders |
| `LockerView.kt` | The inside of a locker's door over the screen while hiding |
| `AchievementToast.kt` | The "Achievement Unlocked!" toast; receives the earned achievements |

### `render/`

| File | What it does |
|---|---|
| `PaleManRenderer.kt` | GeckoLib renderer with the eye glow mask |
| `FilmWalkerRenderer.kt` | The figure in the Cinema's film, on the player's model |
| `DartRenderer.kt` | A thrown dart: vanilla's arrow, smaller, with its own texture |
| `SecurityCameraRenderer.kt` | Turns the anteroom camera's head to follow the player, blinks its red light, plays its servo |
| `WallMessageRenderer.kt` | Draws a wall message in the painted font; remembers what happened to it |
| `ObserverRenderer.kt` | GeckoLib renderer for the Observer, his head turning with the entity's |
| `SubjectRenderer.kt` | GeckoLib renderer for the subjects, each stage its body and skin |
| `DeadGuardRenderer.kt` | GeckoLib renderer for the guard before you, sitting, in the player's uniform worn out |
| `DirectorRenderer.kt` | GeckoLib renderer for the director: his model, texture and animations, turned to his yaw |
| `LabFigureRenderer.kt` | The figure seen from Level 5's Lobby, on the player's model, a dark shape |
| `HunterRenderer.kt` | GeckoLib renderer for the Hunters, their eyes glowing |
| `EyesGlowLayer.kt` | The glow of the Hunters' and Distortions' eyes: the `_glowmask` texture drawn again with vanilla's `eyes` layer (it replaces GeckoLib's `AutoGlowingGeoLayer`, which cut the eyes out of the base texture) |
| `DistortionRenderer.kt` | GeckoLib renderer and model for the Distortions: turned to their yaw, the glitch, the Junior's cracking eye |
| `PaleManModel.kt` | His GeckoLib model, plus the stoop under low ceilings |
| `BoxShelfRenderer.kt` | The purple boxes on the box shelf, full bright |
| `WallTextRenderer.kt` | A wall text: white on a dark plate, dark on brass or on the lab's white plastic, big dark letters on the wall, or several lines on a framed certificate or a small brass plaque |
| `LootRenderer.kt` | Draws loot items lying flat |
| `GuardUniform.kt` | The uniform skin for every player |

### `cutscene/`

| File | What it does |
|---|---|
| `CutsceneView.kt` | A cinematic on the client: a camera of its own moved tick by tick, the HUD off, the player drawn from outside, the director's pose, his lines, skipping |
| `CutsceneScreen.kt` | The screen that takes the keys and the mouse during a cinematic: the black bands, the subtitles, the notice to skip |
| `DirectorVoice.kt` | His phrases after the scene: played flat, subtitled over the HUD |

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
| `SafeScreen.kt` | A safe's dial, turned and listened to |
| `EraseSaveScreen.kt` | The warning before Ctrl+Shift+D erases the saved game |
| `LoadingScreen.kt` | The loading screen and `LoadingCover`, which draws it over everything |
| `LoadingTips.kt` | Rotating tips with the player's keys |
| `CongratulationsScreen.kt` | The congratulations window over the main menu after the credits: the Observer with his coffee, a thank-you, time played and deaths |
| `FinalCreditsScreen.kt` | The final credits, timed to their song: drawings, typed titles, tools, "Level 6 coming soon"; the fall's music |
| `TerminalScreen.kt` | A Level 5 computer's screen, typed out green on black |
| `DeathScreen.kt` | The death screen: "Killed by", the killer and a tip on the black of dying, and "Try again" |

### Client mixins: `client/java/daio7771/everytunnel/mixin/client/`

See [Architecture](architecture.md#mixins) for the target and reason of each.

## Resources: `main/resources/`

| Path | What it holds |
|---|---|
| `fabric.mod.json` | Mod metadata, entrypoints, dependencies |
| `every_tunnel.mixins.json`, `client/resources/every_tunnel.client.mixins.json` | Mixin lists |
| `assets/every_tunnel/lang/en_us.json` | Every in-game text |
| `assets/every_tunnel/lang/es_*.json` | The Spanish translation (`es_es.json`, copied to the other `es_*` by `tools/lang/spanish_variants.py`) |
| `assets/every_tunnel/guide/` | Not in `src/`: copied from `docs/*/guide/` by the build (`copyGuide`) |
| `assets/every_tunnel/sounds.json`, `sounds/` | Sound events and `.ogg` files (from `tools/sounds/export.py`) |
| `assets/every_tunnel/cutscene/director.json` | The director's voice lines: part, length and subtitle cues (from `tools/sounds/voices.py`) |
| `assets/every_tunnel/textures/`, `models/`, `blockstates/` | From `tools/blocks`, `tools/entity`, `tools/gui` |
| `assets/every_tunnel/geo/`, `animations/` | The Pale man's GeckoLib files |
| `assets/every_tunnel/menu/`, `models/block/menu/`, `textures/block/menu/` | The menu scene (from `tools/map/map_generator.py`) |
| `data/every_tunnel/loot_tables/` | Block loot tables |

## Tests: `test/kotlin/daio7771/everytunnel/`

See [Debugging](debugging.md#unit-tests).
