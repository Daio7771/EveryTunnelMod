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
| `menu/LogoRun.kt` | The menu logo's easter egg: when the words flicker out and leave RUN lit, and for how long |
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
| `progress/PlayerProgress.kt` | What a player has done: pieces, notes read, scenes seen, Level 3's books, checkpoint (a later chapter keeps its own) |
| `ending/RunSummary.kt` | The congratulations window's numbers: time played and deaths of a game, the time as `H:MM:SS` |
| `progress/Checkpoint.kt` | A checkpoint as text: a level's start, or `level/point` for a restart point inside it |
| `detector/Detector.kt` | Level 5's detector: how full its icon is, when the triangle shows, how many Geiger counts fall a tick |
| `death/DeathCause.kt` | What the death screen names (the killer, thirst, the fall, deep water) and where it shows |
| `puzzle/NoteText.kt` | A note's text: title, paragraphs, `{A}` / `{A:word}` placeholders |
| `hiding/LookLimit.kt` | How far the head turns inside a locker |
| `progress/LeftBehind.kt` | What is of no use from a level on and is taken out of the inventory at its start (the keycard from Level 3, the master card from Level 5) |
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
| `ModBlocks.kt` | Every block of the base game: the light, props, doors, signs, elevator parts, loot, card reader; the creative tab's key (`TAB`), and the settings a later chapter's blocks share |
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
| `ChapterRequest.kt` | What the menu asked for: the world opens only for a later chapter's opening (no map build, no new game), which that chapter starts (`onLoaded`) and carries on, from its beginning or its own saved game (`continuing`); the marker in the world's folder that tells the menu a chapter has one (`markerName`, `setSaved`) |
| `ChapterRestarts.kt` | Where a later chapter's player comes back after dying: chapters register a source, `DeathHooks` asks before Chapter 1's levels |
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
| `FoodHooks.kt` | Eating: health back and 10 s of unlimited energy, at any health |
| `WaterDispenserHooks.kt` | Drinking at a water dispenser that gives water (Level 3's kitchen) |
| `JumpCounter.kt` | Jumps per player from the vanilla statistic |
| `NoiseHooks.kt` | Turns steps, jumps, landings, panting and game events into noises |
| `AmbienceHooks.kt` | Sends each player their level's ambience |
| `RespawnHooks.kt` | Sets the respawn point to the current level's start |
| `DeathHooks.kt` | Death as fade to black and level restart (or a later chapter's restart, `ChapterRestarts`) |
| `LoadingHooks.kt` | Rebuild on open, loading state, new game, safety timeouts |
| `NewGame.kt` | Resets the world and the player for a new game |
| `ProgressService.kt` | The player's progress as the rest of the mod sees it; `onReset`, for a later chapter to forget its own on a new game |
| `CardinalProgress.kt` | `ProgressService` on Cardinal Components (with a later chapter's own component, the only classes that touch that library) |
| `PlayerRules.kt` | What a chapter changes of the base game's rules for its players (flashlight, hiding, damage, noise, status bars): chapters register a source, the base hooks ask |
| `IntroHooks.kt` | The elevator intro: doors, song, floor indicators, title |
| `LevelEntryHooks.kt` | Level titles and closing shutters behind the player |
| `LootSpawner.kt` | Places and restocks loot at tagged spawn points |
| `KeptEntities.kt` | Entities never saved but put in far from the player (Level 5's): back where they were put once a chunk that dropped them loads again |
| `KeycardHooks.kt` | Lights-out wave, restoring lights, locking readers |
| `ValveHooks.kt` | Level 3's valve puzzle: digits per new game, checking turns, opening the door, resetting |
| `NoteHooks.kt` | Reading a note (board or sheet): finds its id, sends it with the valve digits |
| `CarryHooks.kt` | Carrying a box or a book: in the hand, never dropped, no sprint, back on death; on the mouse pointer or in the crafting grid of the inventory screen it still counts as carried |
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
| `ContinueGame.kt` | The saved game: checkpoints (a level's restart points: the one inside the zone walked into), CONTINUE, putting Level 4 back; takes what is of no use from a level on out of the inventory before it is saved (`core/progress/LeftBehind.kt`) |
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
| `LinkedDoorBlock.kt` | One leaf of a pair of doors that open and shut together by hand (`factory_door`, the lab door's look); no scene, unlike `PairedDoorBlock` |
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
| `DetectorEffect.kt` | Level 5's detector (no item: where the server says it works): the nearest Cloud in sight, the Geiger clicks |
| `DistortionEffect.kt` | The Distortions on the senses: whispers, the screen distorting at a Cloud, the violet of the entry corridor's, the Junior's tremor and drift |
| `CameraShake.kt` | The camera shaking in Level 3's tunnel collapse |
| `FilmPlayer.kt` | The Cinema's film on its screen, and the projector's sound |
| `BreathingEffect.kt` | Heavy breathing when exhausted |
| `TensionMusic.kt` | Music layers from the Pale men's state |
| `IntroMusic.kt` | The elevator song and its tape-stop fade |
| `MenuMusic.kt` | The menu's music, one track for each chapter, fading from one to the other as the menu changes chapter |
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
| `SubtitleStrip.kt` | A voice's subtitle over the HUD: the speaker in grey, the text in white, on a dark strip |

### `render/`

| File | What it does |
|---|---|
| `PaleManRenderer.kt` | GeckoLib renderer with the eye glow mask |
| `FilmWalkerRenderer.kt` | The figure in the Cinema's film, on the player's model |
| `DartRenderer.kt` | A thrown dart: vanilla's arrow, smaller, with its own texture |
| `SecurityCameraRenderer.kt` | Turns the anteroom camera's head to follow the player, blinks its red light, plays its servo; a sweeping one (a later chapter's decorative cameras, their own block entity type) only sweeps |
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
| `CutsceneView.kt` | A cinematic on the client (the director's, or a later chapter's own `Kind`, which adds its sounds, shake, flash and end): a camera of its own moved tick by tick, the HUD off, the player drawn from outside (if the kind says so), the director's pose, voice lines, skipping, the black a scene opens out of |
| `CutsceneScreen.kt` | The screen that takes the keys and the mouse during a cinematic: the black bands, the subtitles, the notice to skip |
| `DirectorVoice.kt` | His phrases after the scene: played flat, subtitled over the HUD |

### `menu/`

| File | What it does |
|---|---|
| `MenuScene.kt` | The scenes behind the main menu, one for each chapter (the parking hall, the prologue's laboratory with a drifting camera): loads, lights, flickers and draws them, fading through black from one to the other |
| `MenuSceneView.kt` | The scene's blocks and light as a world for the block renderer |
| `RotatedModel.kt` | A baked model turned by quarter turns (the scene's turned blocks) |

### `screen/`

| File | What it does |
|---|---|
| `MainMenuScreen.kt` | The main menu (the chalk logo in layers, its RUN easter egg and Chapter 2's line under it included); CONTINUE and Ctrl+Shift+D are the menu's chapter's |
| `AchievementsScreen.kt` | The ACHIEVEMENTS screen: the list on paper |
| `Paper.kt` | Worn paper (tile, stains, edges, inks) for the guide and the achievements |
| `GuideScreen.kt` | The player guide: a scrolling paper sheet with tabs |
| `NoteScreen.kt` | A note read on the guide's paper over the game, placeholders filled |
| `SafeScreen.kt` | A safe's dial, turned and listened to |
| `SpoilerAlertScreen.kt` | The spoiler alert before a locked Chapter 2 is played: PLAY ANYWAY or BACK |
| `EraseSaveScreen.kt` | The warning before Ctrl+Shift+D erases the saved game |
| `LoadingScreen.kt` | The loading screen and `LoadingCover`, which draws it over everything |
| `LoadingTips.kt` | Rotating tips with the player's keys |
| `CongratulationsScreen.kt` | The congratulations window over the main menu after the credits: the Observer with his coffee, a thank-you, time played and deaths |
| `FinalCreditsScreen.kt` | The final credits, timed to their song: drawings, typed titles, tools, "Level 6 coming soon"; the fall's music |
| `TerminalScreen.kt` | A Level 5 computer's screen, typed out green on black |
| `DeathScreen.kt` | The death screen: "Killed by", the killer and a tip on the black of dying, and "Try again" |

### Client mixins: `client/java/daio7771/everytunnel/mixin/client/`

See [Architecture](architecture.md#mixins) for the target and reason of each.

## Chapter 2: `main/kotlin/daio7771/everytunnel/chapter2/` and `client/kotlin/daio7771/everytunnel/chapter2/`

Everything of Chapter 2, apart from the base game (Chapter 1 and what every chapter shares, above), which never names it (`ChapterBoundaryTest`).
It has its own entrypoints (`Chapter2`, `Chapter2Client`) and reaches the base game only through its extension points: `PlayerRules`,
`ChapterRequest`, `ChapterRestarts`, `ProgressService.onReset` and `CutsceneView.Kind`. A later chapter gets a package like this one. Its tests are under
`test/kotlin/daio7771/everytunnel/chapter2/`.

### The chapter: `chapter2/`, `chapter2/core/`, `chapter2/platform/`

| File | What it does |
|---|---|
| `Chapter2.kt` | Chapter 2's common entrypoint (after the base game's in `fabric.mod.json`): registers the chapter's registries, progress, rules, menu start, hooks and commands |
| `core/Ch2PlayerProgress.kt` | What a player has done in Chapter 2 (the state of its Levels 1 and 2, whether they play one, their counters, Level 2's saved game), apart from Chapter 1's |
| `platform/Ch2Progress.kt` | `Ch2PlayerProgress` on Cardinal Components, its own component (`every_tunnel:ch2_progress`, with the way back to Chapter 1 and the inventory of Level 2's saved game), cleared with a Chapter 1 new game |
| `platform/Ch2Return.kt` | The way back to Chapter 1: before the chapter takes the player anywhere (the prologue's end, CONTINUE), where they stood, what they carried, their health and resources are kept; leaving the world gives them back, out of the chapter's levels |
| `platform/Ch2Blocks.kt` | Chapter 2's blocks (the factory's, the prologue bench's), added to the same creative tab; the factory cameras' own block entity type |
| `platform/Ch2Items.kt` | Chapter 2's items: the broom |
| `platform/Ch2Sounds.kt` | Chapter 2's sound events played from code |
| `platform/Ch2Entities.kt` | Chapter 2's entity types: the factory's people, the prologue's scientist |
| `core/MapPlan.kt` | A level's map as a plan to build (`map.json`, from its generator): the box to empty, the boxes of one block or of a weighted mix (`pick`, the generators' `cell_hash` bit for bit), the lamps, the wall texts, the gates, the origin, the fingerprint |
| `platform/MapBuild.kt` | Builds a level's plan in batches (8,000 cells written a tick, a cell found empty an eighth of one), empties it, remembers which plan it built, checks every block first; one instance per level |
| `platform/Ch2DataReload.kt` | What `/et reload` reads again besides Level 1's data: each later level's loader |

### The prologue: `chapter2/prologue/` (`core/`, `platform/`)

| File | What it does |
|---|---|
| `prologue/core/PrologueScene.kt` | Chapter 2's prologue: the timeline from the scientist (tracking shot, settling, creeping in), the four lines, subtitles, the opening black, the flicker's beats, the stages of his change, the cut to black and the typed title (`PrologueCues`, `PrologueStages`, `PrologueScenePlan`); the hum, drone and Geiger curves |
| `prologue/core/PrologueLines.kt` | The scientist's voice lines and their subtitle cues, read from `cutscene/prologue.json` |
| `prologue/core/RoomPlan.kt` | The prologue room's plan (read from `cutscene/prologue_room.json`) and `BlockSpec`, a block with its states |
| `prologue/core/GeigerClicks.kt` | A Geiger counter's counts tick by tick: a seeded Poisson process at a rate that can rise |
| `prologue/platform/PrologueSceneHooks.kt` | Chapter 2's prologue: builds its room out of the way, spawns the scientist, holds the player, skipping, the end (the player back where they were, the room cleared); started by `/everytunnel prologue` |
| `prologue/platform/PrologueCommand.kt` | `/everytunnel prologue` (dev worlds), merged into the base game's `/everytunnel` |
| `prologue/platform/ScientistEntity.kt` | The prologue's scientist (GeckoLib, a passing creature): works at his bench, trembles, falls sick, stretches, then is a Pale man (`stage`, set by the scene) |

### Level 1, logic: `chapter2/level1/core/`

| File | What it does |
|---|---|
| `level1/core/Ch2L1State.kt` | Chapter 2's Level 1 state machine: its ten states, in the order they are played |
| `level1/core/Ch2L1Anchors.kt` | The level's named places, points and zones, as `anchors.json` gives them |
| `level1/core/Ch2L1AnchorsParser.kt` | Reads `anchors.json`, reporting every problem with its JSON path |
| `level1/core/Ch2L1Data.kt` | The level's data files: which there are, which copy wins (the config folder's over the jar's), what a missing or broken one gives |
| `level1/core/Ch2L1Voices.kt` | The level's voice lines (text key, speaker, length, subtitle cues as ranges of sentences, from `cutscene/ch2_l1_voices.json`), `Ch2L1Sentences` and `Ch2L1Skins`, the look of its people |
| `level1/core/Ch2L1Tasks.kt` | The tasks' file (`tasks.json`), its parser, `Ch2L1Bits` (sets of things as the bits of an Int) and `HoldTracker` (a click held on one target) |
| `level1/core/Ch2L1Speakers.kt` | The loudspeakers' file (`speakers.json`), its parser and `Ch2L1SpeakerSchedule`: what plays when, by the plan's rules, with no clock of its own |
| `level1/core/Ch2L1IntroScene.kt` | The first announcement's cinematic: shots from anchors, in the machinery of the other scenes |
| `level1/core/Ch2L1Act2.kt` | Act 2's file (`cutscenes/meeting.json`), its parser, and `Ch2L1Follow`: the coworker leading the way, waiting for a player who lags and reminding them |
| `level1/core/Ch2L1Act3.kt` | Act 3's file (`act3.json`) and its parser: the locks, Rowan's code, the computers and their files, the objective and the arrow's target from the player's counters |
| `level1/core/Ch2L1Patrols.kt` | The patrols' file (`patrols.json`) and its parser, `Ch2L1PatrolRunner` (a guard's round with its stops), `Ch2L1Watch` (what a guard sees and thinks: the "?", the warning, the chase, the catch) and `Ch2L1Stare` |
| `level1/core/Ch2L1Office.kt` | The office's cinematic: its file (`cutscenes/office.json`), its parser and `Ch2L1OfficeScene` (the eyes fixed on Rowan, his track tick by tick, the door, the pass, the hum) |
| `level1/core/Ch2L1Meeting.kt` | The meeting as a scene (`Ch2L1MeetingScene`: the eyes turning to each speaker, the door, the badge's tick) and `Ch2L1Glances` (the listeners' glances at the door) |
| `level1/core/Ch2L1Npcs.kt` | The factory's people: `npcs.json`, its parser, a routine's steps with their anchors looked up, and `Ch2L1RoutineRunner`, what a person does each tick |
| `level1/core/Ch2L1NpcLife.kt` | What makes the factory's people look alive (`npcs.json`'s `life`): `Ch2L1Gait` (eased walking and turning), `Ch2L1Gaze` (where the head looks), `Ch2L1Chat` (talks between two), `Ch2L1Pose` |
| `level1/core/Ch2L1Guide.kt` | Where the guide arrow points in each step (the clock, the tubes, the terminal, the analyzer, the broom, the dirt, Act 3's next thing to do), the nearest of them, when it hides, how long it waits in Act 3 |
| `level1/core/Ch2L1Wiring.kt` | Checks that what `tasks.json`, `speakers.json`, `npcs.json`, `cutscenes/meeting.json` and `act3.json` name is in `anchors.json` |
| `level1/core/Ch2L1Objectives.kt` | The objective the panel shows in each state: one lang key per state, none in the cinematics and at the end |

### Level 1, Minecraft glue: `chapter2/level1/platform/`

| File | What it does |
|---|---|
| `level1/platform/Ch2L1.kt` | Chapter 2's Level 1: each player's state and whether they are playing it (the rules follow them: `enter`, `leave`), kept in their progress; every change goes through it and reaches the listeners |
| `level1/platform/Ch2L1DataLoader.kt` | Reads the level's JSON files (the config folder's copy, else the jar's) at launch and on `/et reload`, and holds the current data |
| `level1/platform/Ch2L1Commands.kt` | `/et reload` and `/et ch2 l1 enter\|leave\|build\|unbuild\|anchors\|goto\|state\|skip\|npc\|tasks\|act3\|patrols\|intro\|say` (development runs only) |
| `level1/platform/Ch2L1Build.kt` | The level's `MapBuild` (its plan, `data/every_tunnel/ch2/l1/map.json`) and its lamps turned down and up by zone |
| `level1/platform/TimeClockBlock.kt` | The time clock of Chapter 2's Level 1: a card reader (same models) that clocks the new hire in, red and then green |
| `level1/platform/Ch2L1TaskBlocks.kt` | The sample tube, NonameOS's terminal, the analyzer, the leaning broom and the dirt of Chapter 2's tasks: each hands its use to `Ch2L1TaskHooks` |
| `level1/platform/Ch2L1TaskHooks.kt` | The three tasks of Act 1: the uses, the counters in the progress, the packets, and the world's props following the state |
| `level1/platform/Ch2L1SpeakerHooks.kt` | Rowan's loudspeakers: one schedule for each player, the chime and the voice from the zone's ceiling speaker |
| `level1/platform/Ch2L1IntroHooks.kt` | The first announcement's cinematic on the server: holds the player, the chime, the end or the skip |
| `level1/platform/Ch2L1Act2Hooks.kt` | Act 2 on the server: the coworker waiting and leading, the group, the meeting directed tick by tick, the badge, everyone leaving, the skip |
| `level1/platform/Ch2L1Act3Hooks.kt` | Act 3 on the server: the badge reader and the auxiliary lab's door, the computers' files and the office's trigger, the keypad and the office's door, the office entered, the doors that say no, chamber 02's Pale man, the relock going back |
| `level1/platform/Ch2L1Act3Blocks.kt` | The badge reader and the keypad (a lock by a locked door, red then green) and Rowan's portrait: each hands its use to `Ch2L1Act3Hooks`; the emergency manhole in the basement's floor (shut or open), to `Ch2L1EndingHooks` |
| `level1/platform/Ch2L1PatrolHooks.kt` | The patrols on the server: the guards put in and directed on their rounds, what makes a player suspicious, the warning, the chase, the catch and the player put back |
| `level1/platform/Ch2L1OfficeHooks.kt` | The office's cinematic on the server: the glide to the mark, Rowan moved on the scene's track, the door, the pass sliding, the skip, Rowan waiting by the door until the player leaves, and what the office leaves (the pass, the flashlight, the LAB almost dark, a6 once) |
| `level1/platform/Ch2L1Doors.kt` | The factory's doors as its people use them: opened in their way, a doorway shut or opened round a place |
| `level1/platform/FactoryNpcEntity.kt` | A person of Chapter 2's Level 1 (Rowan, the coworker, the workers): one entity type, its synced skin tells them apart; a worker follows its routine tick by tick (eased walking, its own pace), its head looks about on its own, and its synced pose says if it types, talks or listens; a kept creature |
| `level1/platform/Ch2L1KeyItems.kt` | The level's key items cannot be lost: those lying in the level are taken away, and a player who should hold one gets it back |
| `level1/platform/Ch2L1Npcs.kt` | The factory's people: the workers put in while the level is played and the LAB's taken out, the talks between pairs, the groups, and the developer's `/et ch2 l1 npc` |

### Level 2: `chapter2/level2/` (`core/`, `platform/`)

| File | What it does |
|---|---|
| `level2/core/Ch2L2Data.kt` | The level's data as last read (its anchors, Level 1's format, and `level.json`, with what it names looked for in the anchors) and how each file's copy is chosen (the config folder's while usable, else the jar's); `Ch2L2Files`, the files and where they live |
| `level2/core/Ch2L2State.kt` | The level's states in play order (`ARRIVED` to `DONE`, the flood's stage of each) and its four encounters (`Ch2L2Event`: `hunters`, `creature`, `guard`, `flood`) as runs of them |
| `level2/core/Ch2L2Level.kt` | `level.json` and its parser: the rules, the resources on arriving, the lamps, the checkpoints and the zones that move the state on; which checkpoint and state lie ahead |
| `level2/core/Ch2L2Snapshot.kt` | Chapter 2's saved game: the last checkpoint, the state and counters then |
| `level2/platform/Ch2L2.kt` | Chapter 2's Level 2: each player's state (listeners of a change and of a start), whether they play it, entering down Level 1's manhole, the checkpoints and the saved game, a death's restart, CONTINUE |
| `level2/platform/Ch2L2DataLoader.kt` | Reads the level's JSON files at launch and on `/et reload` (through `Ch2DataReload`), and holds the current data |
| `level2/platform/Ch2L2Build.kt` | The level's `MapBuild` (its plan, `data/every_tunnel/ch2/l2/map.json`, at (1024, 64, 1280)) and its lamps by zone |
| `level2/platform/Ch2L2Commands.kt` | `/et ch2 l2 enter\|leave\|state\|skip\|reset\|build\|unbuild\|anchors\|goto` (development runs only) |

### Client: `client/kotlin/daio7771/everytunnel/chapter2/` (`prologue/client/`, `level1/client/`)

| File | What it does |
|---|---|
| `Chapter2Client.kt` | Chapter 2's client entrypoint: its scenes, HUD, arrow, NonameOS, renderers and render layers |
| `prologue/client/PrologueView.kt` | The prologue on the client: its `CutsceneView.Kind` (sound, shake, flash, end on black) and its packets |
| `prologue/client/PrologueSound.kt` | The prologue's sound: the hum and the drone (volume following the scene), the radiation counter's positional Geiger clicks, the flicker, a crack of bones at each stage, the Pale man's hit and breathing, the title's blow, all dead at the cut |
| `prologue/client/ScientistRenderer.kt` | GeckoLib renderer for the prologue's scientist, turned to his yaw; `ScientistModel` draws him as the man, in his sick skin or, once changed, as the Level 2 Pale man (eyes glowing) |
| `level1/client/Ch2L1Client.kt` | What the client knows of Chapter 2's Level 1: whether the local player is in it, their state, the objective to show (from their progress) |
| `level1/client/Ch2L1IntroView.kt` | The Level 1 first announcement on the client: its `CutsceneView.Kind` and its packets |
| `level1/client/Ch2L1MeetingView.kt` | The meeting on the client: its `CutsceneView.Kind` (subtitles a cue at a time after each speaker's name) and its packets |
| `level1/client/Ch2L1Subtitles.kt` | The subtitles of Chapter 2's Level 1 voices, shown whenever one plays (a listener on the sound manager; always on) |
| `level1/client/ObjectivePanel.kt` | Chapter 2's Level 1 objective panel: top right, small, fades in when the objective changes, with the count of a task ("(1/3)") |
| `level1/client/HoldBar.kt` | The bar of a task that takes a held click (the analyzer, the dirt), under the crosshair |
| `level1/client/TaskArrow.kt` | The guide arrow of Chapter 2's Level 1: a blue V with white edges that turns over the place of the current step, seen through furniture |
| `level1/client/NonameOsScreen.kt` | NonameOS, the factory's terminal (Chapter 2 Level 1's task 1): the sample registry with its REGISTER buttons |
| `level1/client/NonameOsFilesScreen.kt` | NonameOS on Act 3's computers: the files, NEW or READ, each opened, typed out and scrolled; closing one tells the server |
| `level1/client/NonameOsLook.kt` | How NonameOS looks (the colours, the monitor's frame, the title bar, a button), shared by its windows |
| `level1/client/KeypadScreen.kt` | The office's keypad close up: the display, twelve keys clicked or typed, DENIED or OPEN from the server |
| `level1/client/CaughtOverlay.kt` | Caught by a guard: the fade to black with its text; a guard's line as a subtitle |
| `level1/client/Ch2L1OfficeView.kt` | The office's cinematic on the client: its `CutsceneView.Kind` (Rowan's subtitles, the hum) and its packets |
| `level1/client/FactoryNpcRenderer.kt` | The factory's people on the player's model with classic arms, each in its skin (`textures/entity/npc/`), arms and head by their pose (typing, talking, listening) |

## Resources: `main/resources/`

| Path | What it holds |
|---|---|
| `fabric.mod.json` | Mod metadata, entrypoints, dependencies |
| `every_tunnel.mixins.json`, `client/resources/every_tunnel.client.mixins.json` | Mixin lists |
| `assets/every_tunnel/lang/en_us.json` | Every in-game text (generated by `tools/lang/build.py` from `tools/lang/src/<part>/`, one part per chapter) |
| `assets/every_tunnel/lang/es_*.json` | The Spanish translation (`es_es.json`, merged like English and copied to the other `es_*` by `tools/lang/build.py`) |
| `assets/every_tunnel/guide/` | Not in `src/`: copied from `docs/*/guide/` by the build (`copyGuide`) |
| `assets/every_tunnel/sounds.json`, `sounds/` | Sound events and `.ogg` files (from `tools/sounds/export.py`) |
| `assets/every_tunnel/cutscene/prologue.json` | The scientist's voice lines: length and subtitle cues (from `tools/sounds/prologue_voices.py`) |
| `assets/every_tunnel/cutscene/prologue_room.json` | The prologue's laboratory: fills, blocks, lights, where the scientist and the player stand (from `tools/prologue/room.py`) |
| `assets/every_tunnel/cutscene/director.json` | The director's voice lines: part, length and subtitle cues (from `tools/sounds/voices.py`) |
| `assets/every_tunnel/cutscene/ch2_l1_voices.json` | Chapter 2 Level 1's voice lines: text key, speaker, length and subtitle cues (from `tools/sounds/ch2_l1_voices.py`) |
| `assets/every_tunnel/textures/entity/npc/` | The factory people's skins: Rowan's (the author's) and the workers' (from `tools/entity/workers.py`) |
| `assets/every_tunnel/textures/`, `models/`, `blockstates/` | From `tools/blocks`, `tools/entity`, `tools/gui` |
| `assets/every_tunnel/geo/`, `animations/` | The Pale man's GeckoLib files |
| `assets/every_tunnel/menu/`, `models/block/menu/`, `textures/block/menu/` | The menu scene (from `tools/map/map_generator.py`) |
| `data/every_tunnel/loot_tables/` | Block loot tables |
| `data/every_tunnel/ch2/l2/` | Chapter 2's Level 2: `map.json`, `anchors.json` and `flood.json`, written by `tools/map/ch2l2/generate.py` from its `layout.json` |
| `data/every_tunnel/ch2/l1/` | The defaults of Chapter 2's Level 1 data: `anchors.json` and `map.json` (written by `tools/map/ch2l1/generate.py`), `speakers.json`, `tasks.json`, `patrols.json`, `npcs.json`, `cutscenes/*.json` |

## Tests: `test/kotlin/daio7771/everytunnel/`

See [Debugging](debugging.md#unit-tests).
