# Chapter 2, Level 2

*[Versión en español](../es/chapter-2-level-2.md) · [All pages](README.md)*

Level 2 of Chapter 2 is the catacombs under the factory: the old catacombs the factory's drainage was built into. The scientist comes
down Level 1's emergency manhole, alone, and looks for a way out: the Hunters in the galleries, a creature that hunts by the floor's
vibrations in the pump room, the Observant Guard of Chapter 1 in the lower chambers, and a flood in three stages on the way to the
pumping station and the exit. It is built in phases (1 to 8; phase 8 is lighting and decoration), one at a time, on the **private** branch `chapter-2/level-2` (off
`chapter-2/level-1`, pushed only to the remote `private`) until Levels 1 and 2 of the chapter are published. **Where it stands: phase 7,
the integral review.** It is reached down Level 1's manhole and walked from the entry to the exit with the survival
bars, dying brings the player back to the last checkpoint, and the menu's Chapter 2 continues it; the first stretch has its enemies,
resources and documents, the pump room has its creature and its pump, the vestibule and maintenance room have the Guard and its traps, and the way on to the exit floods in three stages and ends at a heavy door that opens on Stone Maw's last stand (phase 6.1), the reunion with the coworker and worker B and "TO BE CONTINUED".

Its code is under `daio7771.everytunnel.chapter2.level2` (`core`, plain Kotlin, and `platform`), apart from the base game, which never
names it (see [Architecture](architecture.md#chapters)). Its named places use Level 1's format (`level1.core.Ch2L1AnchorsParser`); its map
is built by the chapter's `platform.MapBuild` from a `core.MapPlan`, as Level 1's is.

## How it is reached

**Down Level 1's manhole, without a break.** At the end of Level 1 the player climbs down the emergency manhole while the screen fades to
black (`act3.json`, `overtime`: `fade_seconds`, 1.5, then `dark_seconds`, 1, in the dark); Level 1 tells the chapter
(`Ch2L1EndingHooks.onFinished`), and the player is at the foot of the ladder in the catacombs (`sealed_entry`), where the screen fades
back. No closing text and no menu: the way goes on. The first time, the catacombs are built behind the black (a few seconds).

On arriving (`level2.platform.Ch2L2.enter`), the player's pockets are emptied (Level 1's pass is no use down there), their stamina, thirst
and battery are as `level.json` says (`start`: all full), the lamps too (`lights`), and the first checkpoint is taken. The flashlight is
the one Rowan gave in Level 1: it is equipment, not an item, and works from the first moment.

**Playing it.** While a player plays it (`Ch2L2.active`), `Chapter2` gives them the base game's survival through `PlayerRules`: they can
be hurt, thirst and the battery run down, the bars are drawn, and their steps make noise; the flashlight and the noise as `level.json`
says (`rules`). They are still the new hire (the white coat), not the night guard.

**The states** (`level2.core.Ch2L2State`), in the order they are played; walking into a zone of `level.json`'s `progress` moves the
player on to its state, never back:

| State | Where | Reached by |
|---|---|---|
| `ARRIVED` | At the foot of the ladder | Arriving |
| `GALLERIES` | The Hunters' galleries (phase 3) | `zone_hunter_galleries` |
| `PUMP_ROOM` | The overlook and the pump room, the vibration creature (phase 4) | `zone_pump_overlook`, `zone_vibration_chamber` |
| `CATACOMBS` | The catacombs and lower galleries | `zone_lower_catacombs` |
| `GUARD_APPEARS` | The vestibule: the Guard comes out of the wall (phase 5) | `zone_observer_wall` |
| `GUARD_FIGHT` | The maintenance room and its four traps (phase 5) | `zone_observer_chamber` |
| `GUARD_DEAD` | Back round to the vestibule, BOO! and the grates up (phase 5) | The Guard's fourth effective stun (or `skip guard`) |
| `FLOOD_1` to `FLOOD_3` | The collector, the walkways, the gallery up: the flood's stages (phase 6) | `zone_flood_collector`, `zone_rising_walkways`, `zone_upper_gallery` |
| `STATION` | The pumping station, dry | `zone_pumping_station` |
| `MAW` | Stone Maw's arena beyond the exit door (phase 6.1) | `zone_maw_middle` |
| `MAW_ESCAPE` | The last pillar is down and Stone Maw crushed: the arena caves in (phase 9) | the third pillar breaking |
| `MAW_DEAD` | The far door slammed behind the player: the way to the reunion room | leaving the arena by the far door (or `skip maw`) |
| `REUNION` | The reunion with the coworker and worker B is playing, then the walk to the lift | the middle of the reunion room |
| `DONE` | The gate shut and the motor ran: the card, then the menu | the lift's end |

The six **encounters** (`Ch2L2Event`) are runs of states: `hunters` (`GALLERIES`), `creature` (`PUMP_ROOM`), `guard` (`GUARD_APPEARS` to
`GUARD_FIGHT`), `flood` (`FLOOD_1` to `FLOOD_3`), `maw` (`MAW` to `MAW_ESCAPE`) and `reunion` (`REUNION`). The flood's stage is the state's (0 before it, 3 from the third on). Each later phase
hangs its encounter on the states: `Ch2L2.onStateChange` (a state left for another, by playing or by command) and `Ch2L2.onStart` (the
level started at a state, by arriving, a death, CONTINUE or `reset`: the world as that state wants it, whatever was there).

## Checkpoints and the saved game

**Checkpoints** (`level.json`, `checkpoints`), one before each encounter: `entry` (the foot of the ladder, before the Hunters),
`pump_overlook` (before the creature), `vestibule` (before the Guard), `collector` (after the Guard, before the flood) and `station`.
Walking into a checkpoint's zone, if it lies ahead of the last one taken, takes it: the player's state, counters and what they carry are
Chapter 2's **saved game** (`Ch2L2Snapshot`, in their Chapter 2 progress), and the world's folder gets Chapter 2's marker
(`every_tunnel_chapter2_continue`, `ChapterRequest.setSaved`).

**Dying** is the base game's death (the fade, the death screen with what killed them, full health and resources, the flashlight off;
`DeathHooks`, asked through `ChapterRestarts`): the player stands at the last checkpoint, the state goes back to the one it saved, and the
level is started again there. What they picked up since stays picked up; the death is not counted in Chapter 1's saved game.

**CONTINUE.** With the marker, the menu's Chapter 2 offers CONTINUE instead of NEW GAME. It opens the world (`ChapterRequest.continuing`),
keeps the way back to Chapter 1 (`Ch2Return`), and puts the player at the last checkpoint with that checkpoint's state, counters and
things, full of health and resources. Leaving the world anywhere in Level 2 gives Chapter 1 back as it was (place, things, health,
stamina, thirst and battery) and keeps the saved game. Ctrl+Shift+D in Chapter 2's menu erases it (after asking), and NEW GAME is
back; a NEW GAME of Chapter 2, or a new game of Chapter 1, forgets it too. Level 1 is still played in one go: there is nothing to continue
until Level 2's first checkpoint.

## Hunters, loot and documents

**Survival kit.** At the foot of the ladder, a counter (5 x 2, `every_tunnel:counter`) under a poster sign (`every_tunnel.ch2.l2.survival_kit`,
SURVIVAL KIT) holds 3 batteries, 2 water bottles, 4 energy bars (`food_snack`, shown as "Snack") and Level 5's wrench (one blow kills a
Hunter). It is the plan's "Entry" loot row.

**Hunters.** One group of three (Level 5's, put in by `DistortionHooks.putIn` under the level id `chapter2_level2`) stands at the anchor
`hunters_post` in the galleries; they see, they do not hear. Starting the level (entering, a death, CONTINUE, `reset`) takes them away and
puts them whole at their post, for a state no later than the group's `until` (`pump_room`).

**Loot** (provisional table): after the Hunters (east end of the galleries, `loot_galleries`) 1 drink and 1 battery; before the Guard
(lower galleries, `loot_catacombs`) 1 drink, 1 bar and 1 battery; the station (`loot_station`) 1 drink and 1 battery. Nothing in the
optional crypts. What was picked up is the player's Level 2 counter `loot.<id>`, set on pick-up (`LootSpawner.addTakenListener`). Death
keeps the pockets (base rule), so it keeps the counters; CONTINUE restores the checkpoint's counters and pockets, so what was picked up
since lies there again.

**Documents.** Three note boards (`every_tunnel:note_board`, placed by the map's `prop` feature) with the plan's approved texts:
`old_drainage` (entry corridor), `maintenance_observation` (pump room, by the overlook door) and `withdrawal_order` (lower galleries, by
the way to the Guard). The text lives once, in the lang sources (`tools/lang/src/chapter2/*.json`, keys `every_tunnel.ch2.l2.<id>_title`
and `_body`); `tools/lang/langfiles.py` (`NOTES`, `write_notes`, run by `build`) writes `assets/every_tunnel/notes/<en|es>/ch2_l2_<id>.txt`
from them. A board is read through `NoteHooks.Source`; the counter `read.<id>` remembers it. The key `every_tunnel.ch2.l2.boo` is there
for phase 5.

## The Observant Guard and its traps (phase 5)

Entering the vestibule (`GUARD_APPEARS`) begins the fight (`Ch2L2Guard`, over `level.json`'s `guard`; the clocks are the plain-Kotlin
`GuardFight` and `TrapBoard`, unit tested). The player never loses control and nothing is skipped:

- **The arena** is the vestibule, the service corridor and the maintenance room. Four gates of the plan (`guard.barred`: `service_far_gate`
  at the corridor's far end, `chamber_exit`, `return_door` and `vestibule_gate`) drop as the fight begins, so the only ways are back north
  (a dead end where the Guard catches the player) and the trap room; they rise when it dies. All four are built open.
- **Coming out: a warning, a cinematic, then the hunt.** As the fight begins the grates fall (their chains are heard) and for `crack_seconds` (5)
  the vestibule's lamps flicker and, the last second, die, while the patch of the north wall (`guard_wall`, a gate with states
  `whole`/`broken` over a small nest room, `guard_nest`) cracks and sheds dust. Then a **skippable cinematic with bands** (`intro_seconds`, 10; `Ch2L2GuardScenes.INTRO`, the moments in `core.GuardIntro`),
  **all of it in first person**, through the player's own eyes: the camera never leaves them, it only turns, as their head would. It whips
  round to the wall in the dark; the Guard (`ObservantGuardEntity`, the user's model), behind it in its nest, punches the wall open (lamps
  back, rubble, a hard sound and a jolt of the camera), steps out of the hole, turns to the player, points at them and beats its chest
  with both fists five times (animations `punch`, `point`, `chest`: the elbows flared, the fists never meet), the camera shaking at each
  blow. It ends with the player turned to look at the Guard. It has no voice: it speaks with signs, and there is no subtitle. Skipping puts it outside the hole facing the player. Control returns, it stares `stare_seconds` (0.5) and
  hunts: always knowing where the player is inside the arena, straight at them at `chase_speed`
  (a little under a sprint). Within `hit_range` it raises an arm (`windup_seconds`) and, if the player is still in range, takes
  `damage_fraction` of their maximum health (two hits kill), at least `hit_interval_seconds` after the last.
- **Music.** The warning plays Level 5's fall (`music.fall`, recycled: 5 s rising to a hit, which lands as the Guard appears). From its
  appearing until its dying the hunt's own music plays (`music.ch2l2.guard_chase`, 150 bpm, 12.8 s, written in code by
  `tools/sounds/catalog_ch2.py`, played again every 256 ticks by `Ch2L2Guard`), through the stuns and the game of the traps; it stops
  at the fatal stun, on the finish and on a death. Both are played twice at once (the second at half volume, since the game caps a
  sound at full volume) for about 3.5 dB more. The dying has its own short, sad song (`music.ch2l2.guard_lament`, felt piano, 14 s, once).
- **The traps.** Four checker-plate squares (zones `zone_trap_1` to `4`) with a breaker each on the maintenance wall (`trap_breaker`: lever
  and lamp). Pulling one charges it `rearm_seconds` (amber lamp blinking), then it is armed (green). The Guard stepping on an armed plate
  is **one activation**, rolled on entering: `fail_chance` it fizzles, else it is stunned `stun_seconds`; either way the trap is spent
  (lever up) until pulled again. Standing on it never rolls again; a plate entered while the trap is not armed, or while the Guard is
  not hunting, does nothing. The plates never touch the player.
- **Death.** `stuns_to_kill` stuns (a trap may be used again; a failed roll does not count) start a `death_seconds` (14) dying and a
  **cinematic** (`Ch2L2GuardDeathScene`, three shots round the Guard, skippable with the jump key twice; skipping runs the finish at once):
  the Guard falls to the floor, then its body comes apart (the `death` animation) into a cloud of specks and is gone. **One finish** (`Ch2L2Guard.finish`, idempotent; `skip guard` runs the
  same): the grates rise, the wall stays broken with its rubble, BOO! goes up in white paint (`WallMessageBlock`, `Chalk: paint_white`)
  on the wall over the hole, the state becomes `GUARD_DEAD` and the vestibule's checkpoint is taken with it: a death after it comes back to
  the vestibule with the hole open and BOO! up. A death mid-fight comes back with the wall whole. The plates are 6 x 6 and the Guard is frozen in place while stunned.

## The flood and the exit (phase 6)

Entering the collector (`FLOOD_1`) starts the flood (`Ch2L2FloodRun`, over `flood.json`'s stages, `level.json`'s `flood` and the plain-Kotlin
`Ch2L2Flood`, unit tested). The water is atmosphere and hurry: it rises behind and under the player and slows wading, with no current and
nothing lethal but vanilla drowning; the route always stays over each stage (the generator's checks) and the ladders take back up whoever falls.

- **Stage 1.** The vestibule's gate slams down behind the player (`sealed`: the way back is shut for good), the pipes creak for the stage's
  `delay_seconds` (3), then the pipe at the `pipe_burst` anchor bursts (sound, a light camera shake, a jet of particles for the stage's
  seconds and a thinner trickle after) and the water rises layer by layer, bottom up, evenly over the stage's seconds, at most
  `cells_per_tick` cells a tick. **Stages 2 and 3** (`zone_rising_walkways`, `zone_upper_gallery`) only fill. A stage keeps only the cells it
  adds to the one before's (`flood.json` holds each stage's boxes whole); water is a source block in the air or `waterlogged` in a block that
  has it, and never over another; a drained cell is what the map has there. The still water of the map is not in `flood.json` and stays.
- **The rumble** (`ambient.ch2l2.flood_rumble`, a loop played again every 196 ticks) is in the player's head at the stage's volume
  (`rumble.stage_1..3`); from `STATION` on, the **station's door** (`station_south`, a gate: `open` as built, `shut` a sluice gate) shuts
  once the player is `station_door.clear_blocks` (3) inside it and the rumble goes muffled (`rumble.muffled`).
- **A start** (a death, CONTINUE, `reset`) puts the water as the state wants it before control returns: drained up to the flood (a death in it
  comes back to the `collector` checkpoint, dry, and walking in starts it again from zero) and all of it from `STATION`, with the
  gates as they go. `/et ch2 l2 flood <0|1|2|3>` sets the water at once for testing.
- **The exit door** is the gate `exit_hatch` (shut as built, on the exit room's west wall): from `STATION` on, walking into `zone_exit_door`
  (nested in `zone_level_exit`) plays `event.ch2l2.exit_door` and opens it for good (`Ch2L2Maw`, `maw.exit_door`); the corridor beyond leads to the arena.
- **The ending** (`Ch2L2Ending`, `ending` in `level.json`) is no longer at the door: it starts in the lift (below). The client
  (`Ch2L2EndingOverlay`) fades to black over `fade_seconds`, shows "TO BE CONTINUED" (`every_tunnel.ch2.l2.ending.continued`) for
  `card_seconds` and leaves for the main menu. The first time it also sets `DONE` and saves the `chapter_end` checkpoint (anchor `chapter_end`,
  in the lift, phase 9): CONTINUE stands there with the whole flood in place. `/et ch2 l2 ending` plays it for testing.

The generator reports the quiet walk from just inside the station's door (`station_door_in`) to the exit's door: 52 blocks, about 12 s (phase 7: the
exit corridor is a dog-leg, west, south, west, north, west; both doors stayed where they were), and `checks.py` fails under 50.

## Stone Maw's last stand and the reunion (phase 6.1)

Beyond the exit door the map goes on west (negative plan coordinates): `maw_corridor` (4 wide), `maw_arena` (20 x 20, ceiling 7, flat, bare:
its only blocks are lamps under the ceiling; the generator's `no_climb` check proves that every place to stand is on its floor), `maw_pipe` (the
stub Stone Maw comes out of, grated by the gate `maw_pipe_mouth`) and `reunion` (10 x 10, lit, the shut door of Level 3 on its far wall). The gates
`maw_entry` (open as built, shuts behind the player) and `maw_far` (shut until the death) join them. `checks.route_gates` opens `exit_hatch` and
`maw_far` for the route's walk.

- **The fight** (`Ch2L2Maw` in `platform`, `maw` in `level.json`; the rules are `StoneMawFight`, `PipeSupply`, `MawCollapse`, `MawEscape` in `core`). Reaching
  `zone_maw_middle` sets `MAW`: the door shuts, the grate is torn off, Stone Maw (the phase 4 `VibrationCreatureEntity` in fight mode,
  `inFight`; the pump's code leaves it alone) crawls out to `maw_home`, the first piece of pipe falls in front of the player. In its rest, stalk,
  charge and blow it is phase 4's brain (fed by the player's steps in the arena and by pipes landing, loud). A piece (`pipe_piece`, picked up by
  walking over it) is thrown with a right click (`ThrownPipeEntity`, `maw.throw`): on Stone Maw it is a **hit** (it never kills), on anything else it
  breaks and clangs where it lands (it goes to the noise). A hit makes it orient on the player and **ram in a straight line**
  until something solid (the body's `horizontalCollision`) or `ram.max_seconds`: then it is **stunned** (a hit then ends it), and goes back to the
  brain searching. It also charges **on its own** when it hears something and a player is in the arena (`ram.auto_cooldown_seconds` after each stun): the pipes only annoy it, hiding behind a pillar is the game. How fast, how sharp, how long it orients and how long it is stunned are the `stages` of `maw` (one per pillar broken: 6.0, 6.8, 7.6
  blocks a second; the stage 3 is the death, so there are three). A player on its way is struck as in phase 4.
  One piece at a time (`pipes.at_once`): `pipes.respawn_seconds` after one is gone another falls from one of `pipes.points` (never the one nearest the
  creature or the player, nor twice the same). The jump is blocked in the arena while the fight lasts: `platform.MovementRules` (a base hook) read by
  `LivingEntityJumpMixin`, a rule of `Ch2L2Maw` on the server and `JUMP_PACKET` for the client.
- **The pillars** (phase 9; `maw.pillars`, anchors `maw_pillar_1..3` at the corner shared by each 2 x 2 column, floor to ceiling, `catacomb_brick`, built by the
  map): a ram that ends against one (`Ch2L2Maw.pillarNear`, within `hit_radius`) damages it; `hits` of them (2) break it: first cracked (other bricks over its middle,
  dust and pebbles falling at the crack), then broken (its upper blocks fall as `destroyedOnLanding` falling blocks, a stump of rubble two high stays). A wall or a
  stump damages nothing (`StoneMawFight`, tested). Each ram into a pillar shakes the camera and plays `event.ch2l2.maw_pillar_ram`, deeper than the wall's.
  A broken pillar drops `maw.debris`: `count` pieces of the ceiling over `spread_ticks` within `radius`, each announced on the floor `warn_ticks` before (dust, `event.ch2l2.maw_debris_warn`) and
  hurting for `damage_fraction` of the player's health where it lands.
- **The death and the escape** (phase 9; the third-person cinematic of phase 6.2 is gone, and with it `MawDeathScene`): when the third pillar breaks the ceiling comes
  down on Stone Maw where it stands, live and in first person (`core.MawCollapse`, `plan` from a fixed seed, centred on it: `death.bit_count` small bits, then a 3 x 3 slab that crushes it (`crushed`) and
  `slab_count` - 1 more; a mound of at most `max_height` within `collapse_radius`; players near it are thrown clear and hurt a little), the music is cut dry, the state goes to
  `MAW_ESCAPE` and the `maw_escape` checkpoint is taken (the last pillar and the mound's cell are saved in counters: CONTINUE puts the arena back and the escape begins again),
  the far door opens and the arena caves in for `escape.seconds` (8): `core.MawEscape` plans `debris_per_second` pieces over the arena (fewer within `door_clear_radius` of the door), announced as the others,
  with `event.ch2l2.maw_escape_rumble` and shaking. Out through the far door and it slams behind the player (`MAW_DEAD`, `Ch2L2Maw.finishDeath`, the one idempotent end, also `skip maw`);
  still in the arena when the time is up and they die. The animations `limp` and `dying` of the model are no longer used (they stay in `tools/entity/vibration_creature.py`).
- **The fight's music** (phase 6.2): the Observant Guard's hunt music, recycled (`music.ch2l2.guard_chase`, 256 ticks, seamless), played from the arena's door shutting, again every 256 ticks by `Ch2L2Maw.music` (twice at once, the second at half volume, like the Guard's);
  the last pillar breaking cuts it dry.
- **The reunion** (`Ch2L2Reunion`, `reunion` in `level.json`, `ReunionScene` in `core`): at the middle of the room (the `reunion` checkpoint stands
  there) the state goes to `REUNION`, the coworker and worker B (`FactoryNpcEntity`) stand at their anchors and a first-person cinematic turns the
  player's eyes to whoever speaks (`reunion.lines`: who, lang key under `reunion.text`, optional sound, seconds). The NPCs talk with their hands,
  listen, glance at the door now and then; the scene ends after the last line and the player has control again while the two set off (phase 9, `reunion.lead`): the coworker opens Level 3's door (`l3_door`)
  and they lead along the corridor (`lead_1..3`, at `lead.speed`, the player's own walking pace; the second worker walks the coworker's line a few steps behind, never beside them, a turn and a short flight up), stopping to look back when the player is more than `wait_distance` behind; they go into the
  lift (`lift_room`, `lift_coworker`, `lift_worker_b`) and face its gate. When the player is in `zone_lift` the gate shuts (`event.ch2l2.lift_gate`), after `gate_pause_seconds` the motor runs
  (`event.ch2l2.lift_motor`, shaking) and the ending begins. The scene's skip is `Ch2L2Reunion.finish`: the same lead. The lines are the user's (ElevenLabs voices, `tools/sounds/ch2_l2_voices.py`, `voice.reunion_*`): four so far (coworker, coworker, worker B,
  coworker), their subtitles in `tools/lang/src/chapter2`; with no lines the scene is a short silent one.
- **Starts** (entering, a death, CONTINUE, `reset maw`): everything as the state wants it: the fight over, the gates, the pieces out of the pockets,
  the rubble only from `MAW_DEAD`. Checkpoints `maw` (before the arena), `maw_escape` (taken by the collapse, no zone), `reunion` and `chapter_end` (taken by the ending, no zone).

## Phase 7: the integral review

An audit of the saved game, deaths and cinematics, with these fixes: dying in Stone Maw's arena gives the client its jump back; a cinematic is dropped
(no effects) when the level starts again or the player dies; leaving during the Guard's or Stone Maw's death keeps the win (the scene's end runs, and
the Maw's also takes the reunion's checkpoint); and when Stone Maw dies the player is put at a watch spot (collapse radius + 2, entrance side, facing
the centre) so no block lands on them, and the cinematic draws them. A retry floor was not needed: a death already gives full health and resources.
`/et ch2 l2 perf` (see Debug commands) and the achievements are in their own sections.

## Chapter 2's achievements

*What Was That?* (no blow from the pump creature by the time the catacombs are reached: counter `pump_hits`, reset when the level starts before them),
*A Sad Farewell* (5 s alive after the Observant Guard's death), *Explorer\** (every zone of `level.json`'s `explore`, the two optional CRYPTs
included: the counters `seen.<zone>`; the `room_*` zones come from the generator) and *Your Aim Is Not Your Thing\** (more than two pipes that missed
Stone Maw in one fight: counter `maw_misses`). Hidden ones are marked *. The rest are in [Level 1's page](chapter-2-level-1.md#chapter-2s-achievements).

## Phase 8: lighting and decoration

**Light.** In vanilla (no shaders) the first look at the level was too dark, so the lamps are now Chapter 2's `catacomb_lamp` (light 14;
Level 5's `cage_lamp` stays at 10) with a `tint` on its bulb (vanilla light has no colour; shaders colour by block): warm in the catacombs
and the reunion, amber in the pump room and the shaft, cold in the vestibule and maintenance, red in the collector, walkways and upper
gallery, white at the entrance, station, exit and the Maw's arena (`layout.json`, `lamp_tints`). On the route only flicker remains
(about 8 %, no dark, no dead lamps); the optional rooms have some dead ones. The mod's own lamps join them (`lamp_extras`): lit
`emergency_lamp`s and five `alarm_light`s (off as built, switched on by the flood's first stage and off on retry or reset: `MapPlan.alarms`,
`Ch2L2Build.setAlarms`) in the collector and walkways, dead `wall_sconce`s on the old walls. The lamps are few and hung high (`lamp_density`: spacing scaled, at least 5 blocks over the floor; the first pass had too many, too low, and
they got in the way of the Guard's cinematic: the vestibule has none between the player and the nest), so the light of the floor is the
fill's. `checks.light` demands a floor light of at least 9 on every cell a player can stand on in a lit space (5 in the optional rooms),
counting flickering and dead lamps as off; where the lamps leave a cell under it, `light.py` places invisible `minecraft:light[level=14]`
blocks two blocks over it. The flood waterlogs them (they stay lit) and the vestibule's are listed in `dim` (`MapPlan.dim`): the Guard's
warning dims them with the lamps (half while they flicker, none when they die: `Ch2L2Build.zoneLights`). `guard_nest` and `maw_pipe` stay
dark. The iron bars of the plan are joined as the game joins them (`Level.connect_bars`: the channels' cages had gaps at their corners).

**Decoration** (`layout.json`, `decor` rules by style, run by `decor.py`; never near an opening, an anchor or the loot, never where the way
would narrow): the drain's rooms get a few pipe and cable-tray runs along the walls, valves and gauges on the pipes, rust stains low on the walls, drums, pallets
and boxes in the corners; the catacombs get rubble, loose bricks and stains. Six set pieces are `prop` features: the entrance's cones and
tool board, a worker's camp in `side_service_crypt`, the maintenance log author's tools on the overlook, the trap room's panel and shelf,
the station's lockers, first-aid box, time clock and a chalk arrow towards the exit, and a fallen chair and chalk marks in
`side_gallery_room`. New blocks (`tools/blocks`): `catacomb_lamp`, `rubble_pile`, `loose_bricks`, `rust_stain`. The pictures
`light_*.png` and `decor_*.png` of the tool show the floor light and the decoration of each height.

## The map

The level is **data**: `tools/map/ch2l2/layout.json` holds everything it is made of, and `tools/map/ch2l2/generate.py` turns it into what
the mod builds. Nothing it writes is edited by hand: change `layout.json` (or the tool) and run it again.

```bash
python3 tools/map/ch2l2/generate.py --install
```

It runs its checks first and writes nothing if one fails. Then it writes `map.json` (the plan), `anchors.json` (the named places, in
world coordinates) and `flood.json` (the flood's stages) into `tools/map/ch2l2/output/` (git-ignored) with its pictures, and, with
`--install`, the three JSON files into `data/every_tunnel/ch2/l2/` (committed).

**The drawing.** The author's conceptual plan (kept out of git, in `docs/temporal/ch2l2/`) at 10 px of the drawing a block (phase 2.2; it
was 6, and too big): 150 x 105 blocks. Corridors (5-6 wide) meet at right angles; the pump room is 38 x 37, the maintenance room 36 x 25, the
central collector 20 x 55. The drawing's central hall and the crypt off the entry corridor are left out. The drawing's way straight from the lower galleries into the maintenance room is not there: it goes round, down a
corridor west of the room, into the vestibule.

**Four heights**, `levels` in the file: **H** (y 20) the entry, the station and the exit; **M** (10) the hunters' galleries, the
balcony over the collector and the pump room's overlook; **L** (0) the pump room's floor, the catacombs, the vestibule and the
maintenance room; **D** (-8) the bottom of the collectors, where the water is. The central collector is a shaft from D to over H, seen
from two heights: from the galleries' balcony (M) and from the station's broken bridge (H).

**`layout.json`:**

| Key | Holds |
|---|---|
| `origin`, `dimension`, `seed` | Where plan cell 0, 0, 0 goes in the world (1024, 64, 1280, south of Level 1's), and the seed of every detail of the look |
| `levels` | The four heights by name |
| `styles` | The looks: `masonry` (the old catacombs: Chapter 2's own dark damp brick `catacomb_*`, cracked, saltpetre-stained and tide-lined at the foot of the walls, a brick vault, flagstones, empty niches, puddles), `drain` (the factory's drainage: Level 5's drain blocks, which the world's resource pack re-skins as stained concrete, puddles), `machine`. Each gives the shell's roles (wall, dado, floor, ceiling) one block or a weighted mix |
| `spaces` | Each space: its boxes, floor (a height or a number), air's height, style, lamps (`ceiling`; `cable`, work bulbs hung from a cable along the vault; `wall`, lamps on the walls over each standing surface; the `lamp_gaps` leave some bulbs out, flickering or dead, by seed, but never near an anchor); a `ramp` makes a flight of stairs of a whole space (the stairs down from the entry, the gallery up to the station) |
| `links` | Spaces whose air simply meets (a balcony over the shaft, the stairs and the corridors) |
| `openings` | Passages through a wall one block thick: arches, a window of bars, and the **gate** (`vestibule_gate`) with a block for each of its states |
| `features` | What stands in a space: partitions and pillars, solid blocks (the overlook, the walkway along the collector, the pump's housing), flights of stairs, decks (catwalks, the broken bridge), channels with water, still water, rails, ladders, floor patches (the four traps), bricked arches, and `prop`: one block of furniture, sign or note board standing in a space's air (`space`, `at` [x, z], `dy`, `block`; with `text` and `style` for a wall_text sign, which the plan's `texts` carries) |
| `anchors`, `zones` | The named places and the boxes the level's systems will watch (see below) |
| `route` | The way through the level, in order, from `sealed_entry` to `level_exit` |
| `checks` | What the checks hold the level to |
| `flood` | The flood's three stages (phase 6): trigger zone, delay, seconds, level, the gates' states while it holds water. The `fill` feature sets a block in every cell of a box of a space's air (the exit's door) |

**The look's details** (which brick is cracked or damp, where a niche or a puddle is) are drawn from the seed: the generator
picks with `cell_hash`, and the mod with `MapPlan.pick`, the same function bit for bit (`MapPlanTest` holds them together), so the
preview is what the game builds. No vanilla block the world's pack re-skins as Chapter 1's is used, but the drain's stand-ins
(`Ch2L2MapPlanTest`).

**The checks** (`checks.py`), run on every generation:
- the structure: no space overlaps another, every opening joins two spaces, every feature is in its space, every anchor has floor under
  it and room over it, and the zones of the route's places overlap none of each other (the mod tells where the player is by them; the
  overlook's, inside the pump room's, is the one pair allowed, `nested`): a zone of an L-shaped space is its box, which can reach into its
  neighbours, so the return gallery's is its south leg (`box`);
- the route walked as a player walks it (a step up of one, a drop of at most three, ladders, no swimming), with its length (about 580
  blocks, 2.5 minutes of walking alone);
- **no shortcuts**: every way from the entry to the exit, falling and swimming included, crosses the galleries, the pump room, the
  catacombs, the vestibule, the walkways, the upper gallery and the station; with the gate down (`grate`), nothing past it can be reached;
- **nowhere to get stuck**: from every place a player can reach from the entry, the exit can still be reached (a balcony without its
  parapet, for one, would leave a fall into the shaft with no way out);
- **the pump room** needs several cycles of the pump: its shortest way across, swimming included, is at least `min` blocks (57 now:
  the floor is cut by two channels five wide, caged: bars on their edges and a grating lid, so nobody gets in or across);
- **placements**: loot stands in free air over something solid, each note anchor is on a note board, and each Hunters anchor exists;
- the openings are clear: two blocks of air through them and room to stand on both sides;
- **the water stays put**: still water and each flood stage touch nothing that water flows into (air, bars, slabs...) outside their own
  spaces, with the gates as the stage says;
- the build: how many cells are cleared and filled, and how long the batches take (about two million cells, mostly empty: a few
  seconds), against `max_build_seconds`.

**The pictures**, in `tools/map/ch2l2/output/`: `level_preview.png` (the whole level from above, lighter where higher, the route in
yellow, the anchors named), `level_H.png` to `level_D.png` (one height each) and `flood_0.png` to `flood_3.png` (the still water, then
after each stage).

| Place (anchor) | Space | Height |
|---|---|---|
| `sealed_entry` | Entrance, at the foot of the manhole's ladder (Level 1's emergency manhole comes down here) | H |
| `collector_junction` | The balcony over the central collector, off the galleries' middle hall | M |
| `hunter_galleries` | The galleries: three halls open along the south | M |
| `pump_overlook` | The pump room's overlook: a caged block of concrete in its north-west corner | M |
| `vibration_chamber` | The pump room: two caged channels across its floor, the crossings at opposite ends (a zigzag), the exit seen from the overlook | L |
| `lower_catacombs` | The catacombs and the lower galleries (nothing off them now) | L |
| `observer_wall` | The vestibule (`observer_wall_face`: the middle of the patch of wall the Guard comes out of; `guard_nest` behind it; `boo_wall`: BOO!'s block over the hole) | L |
| `observer_chamber` | The maintenance room, its four traps where the drawing has its four squares (`trap_1` to `trap_4`, each with its zone and its breaker `breaker_1` to `4`, kind wall) | L |
| `return_gallery` | Round the maintenance room's east and south, back into the vestibule | L |
| `flood_collector` | The collector, a walkway along its channel, past the vestibule's gate and down four steps | D |
| `rising_walkways` | The walkways' hall: up from the walkway to the lower catwalk and up again to the upper one | D to M |
| `upper_gallery` | The stairs up to the station | M to H |
| `pumping_station` | The station, dry; a broken bridge seen through bars over the shaft | H |
| `level_exit` | The exit room, past a quiet corridor | H |
| `maw_entry`, `maw_centre`, `reunion_player`, `chapter_end` | The arena's entry corridor, the arena's middle, the reunion room's middle and the lift (phase 6.1, 9) | H |
| `survival_kit`, `hunters_post`, `loot_galleries`, `loot_catacombs`, `loot_station` | Phase 3's: the kit's counter, the Hunters' post and the three loot spots, in the spaces above | H, M, L |
| `note_old_drainage`, `note_maintenance`, `note_withdrawal` | A note board's own cell (`kind: wall`: not a place to stand) | H, M, L |

Most rooms of the drawing are sealed crypts, their arches bricked up by the factory; two are open, short side rooms for later phases'
resources and documents (a crypt off the galleries and one off the service corridor before the vestibule).

**The Guard's gates.** Besides the one below, `service_far_gate`, `chamber_exit` and `return_door` are gates with states `grate`/`open`, built open, and `guard_wall` has `whole` (built, `catacomb_bricked`) and `broken`; `Ch2L2Build.setGate` sets any of them. The plan check `arena` proves that with the four grates down only the trap room can be reached from the vestibule.

**The gate.** The vestibule's way to the collector (`vestibule_gate`) has three states: `grate` (down: iron bars, until the Guard is
beaten), `open` and `sealed` (a sluice gate's plate, watertight). Phase 1 builds it open, so the level can be walked from end to end. The
flood's stages need it `sealed` (bars do not hold water back): **when the flood starts, the way back is shut behind the player.**

## The data files

The jar holds the defaults in `data/every_tunnel/ch2/l2/`; a file of the same name in `config/every_tunnel/ch2/l2/` takes its place
while it is usable, and `/et reload` reads them again (with Level 1's). The level reads `anchors.json` (the generator's) and `level.json`
(written by hand, with the flood's tunables and the ending); `flood.json` is the generator's and can be overridden for tuning a stage's timing, `map.json` cannot. What `level.json` names is looked for in the
anchors, and what is wrong is reported (`/et reload`).

**`level.json`** (`level2.core.Ch2L2Level`):

| Key | Holds |
|---|---|
| `rules` | `flashlight` (true: Rowan's flashlight works), `noise` (true: the steps make noise the creatures hear) |
| `start` | On arriving from Level 1, `battery`, `thirst` and `stamina` as a share of full (0 to 1; 1, 1, 1). A death or CONTINUE gives them all full |
| `lights` | Each zone's lamps: `on`, `flicker` or `off`, set whenever the level is started (arriving, CONTINUE, `reset`). Empty: the plan's own lamps (with their gaps) stand as built; a zone listed here would turn all its lamps `on`/`flicker`/`off` |
| `checkpoints` | In the way's order: `id`, `anchor` (where a death and CONTINUE bring the player back), `zone` (walked into, it is taken), `before` (the encounter it comes before, for `reset`) |
| `progress` | `zone` and `state`: walking into the zone moves the player on to the state |
| `hunters` | `id`, `anchor`, optional `yaw`, optional `until` (a state; default `pump_room`): a group of Hunters put at the anchor |
| `loot` | `id`, `item` (a key of `LootSpawner.LOOT`: `battery`, `water`, `food_snack`, `wrench`...), `anchor`, `offset` [dx, dy, dz] blocks from the anchor's block, `yaw` |
| `notes` | `id`, `anchor` (the board's); the text is `ch2_l2_<id>` |
| `creature` | The vibration creature and the pump (below) |

**The creature** (`level2.core.Ch2L2Creature`, `VibrationCreatureBrain`, `PumpCycle`; `platform.VibrationCreatureEntity`, `Ch2L2Pump`): the Stone Maw, 3.5 blocks of grey stone (hitbox 1.6 x 3.5), at rest at `creature.home`. It feels the steps, jumps, landings and touches of the player on the zones of `floor_zones` (the overlook is not one) and the pipe's landing, never its own steps or the pump; sneaking is soft, running, jumping and landing loud. A soft step: it stalks there at `stalk_speed`, listens `stalk_listen_seconds`, then searches. Any other: `orient_seconds` turning to it (teeth click; a new one moves the point), then the point is locked and it charges at `charge_speed` (`charge_speed_loud` if loud). At the point, at the nearest place it can reach, or touching a player (`touch_range`), it raises its fists for `windup_seconds` (the escape) and the blow hurts whoever is then within `contact_range` of its centre for `damage_fraction` of their maximum health (two kill; the health is lowered directly, vanilla damage would be cut by difficulty and armor), at most every `hit_interval_seconds`; then `recover_seconds` still, a search of `search_points` points `search_radius` round the blow (`search_speed`, `search_listen_seconds` at each) and home at `walk_speed`. Speeds are blocks a second. It never takes a stopped path for arrival: a point it cannot reach (in a channel) is struck from the nearest place. The pump (`creature.pump`: `anchor` where it sounds, `beacons`, `run_seconds`, `silent_seconds`, `slow_seconds`) runs while a player who has not passed the encounter is in the room: heavy pistons, three amber beacons (`pump_beacon`, on the housing and two wall posts, blinking in the last second) and dust falling from the ceiling round the player; it masks vibrations while it runs and `grace_after_stop_seconds` after. At the first silence of an attempt a pipe falls from `pipe.from` after `pipe.delay_seconds`; until the creature has struck where it lands, it feels nothing else. Reaching the catacombs ends the encounter.

## Debug commands

`level2.platform.Ch2L2Commands`, operator level 2, **development runs only**:

| Command | What it does |
|---|---|
| `/et ch2 l2 enter` | Begins the level as down Level 1's manhole: built if it is not, pockets emptied, `start`'s resources, at `sealed_entry`, its checkpoint taken. Without the way back to Chapter 1 (leaving the world keeps you there) |
| `/et ch2 l2 leave` | Out of the level: the rest of the game's rules apply again; the saved game stays |
| `/et ch2 l2 state` | Your state, the last checkpoint and the state it saved, the flood's stage, each encounter (`ahead`, `now`, `past`), the counters |
| `/et ch2 l2 state <state>` | Puts you in a state (every system of the level is told, as if you had got there by playing) |
| `/et ch2 l2 skip [<event>]` | On to the next state, or past an encounter (`hunters`, `creature`, `guard`, `flood`) as if lived |
| `/et ch2 l2 reset <event>` | An encounter to live again: the state before it, you at the checkpoint before it (taken), the level started there |
| `/et ch2 l2 build` | Builds the catacombs again over what is there (a new plan is built by itself when `goto` needs it) |
| `/et ch2 l2 hunters` | The level's Hunters back whole at their posts, whatever your state |
| `/et ch2 l2 loot` | All the loot as if none were taken: your counters forgotten, your pockets stay |
| `/et ch2 l2 unbuild` | Empties the whole area (before packing the world into a jar), the Hunters too; whoever stands in it falls |
| `/et ch2 l2 creature state` / `pump` | The creature's brain (state, target, speed in blocks a second) or the pump's cycle (phase, tick, masking) in the action bar; again to turn it off |
| `/et ch2 l2 guard` | The Guard's fight (phase, stuns, each trap: S spent, C charging, A armed) in the action bar; again to turn it off. `skip guard` runs its finish at once, `reset guard` puts the wall whole again |
| `/et ch2 l2 flood <0\|1\|2\|3>` | The flood's water at once to that stage (0 drains it), whatever your state; `skip flood` fills all of it and shuts the station's door, `reset flood` drains it and opens the vestibule's gate again |
| `/et ch2 l2 ending` | Plays the ending (fade, "TO BE CONTINUED", main menu); the level finishes only if you are in the reunion or past it |
| `/et ch2 l2 maw` / `maw hit` / `maw kill` / `maw pillar <n>` | Stone Maw's fight in the chat (phase, pillars broken, mode) / counts a hit / breaks the remaining pillars at once (the collapse and the escape) / damages pillar n once. `skip maw` runs the end of the escape, `reset maw` puts the fight back at its start |
| `/et ch2 l2 reunion` | You are put at the reunion room's spot and the reunion plays |
| `/et ch2 l2 perf` | What the level costs in your world (phase 7): its entities by type (a second Guard, creature, Maw or reunion NPC is flagged `DUPLICATES`), the flood's water cells, the lamps by mode, and the mean and worst ms a server tick spends on the level's own handlers over the last 100 ticks, by handler |
| `/et ch2 l2 anchors` | Every anchor and zone with its coordinates, the provisional ones marked |
| `/et ch2 l2 goto <anchor>` | Builds the level if it is not, then takes you to a place (`sealed_entry` ... `level_exit`, `trap_1`...) or to the middle of a zone (`zone_<place>`) |
| `/et reload` | Reads Level 1's and Level 2's data again and says what is wrong |

## Tests

`MapPlanTest` (the plan format: one block, mixes picked as the generator picks them, gates, the fingerprint) and `Ch2PlayerProgressTest`
(Level 2's state, counters and saved game, apart from Level 1's) in `chapter2/core`; `Ch2L2MapPlanTest` (the jar's plan: its origin,
every fill inside the cleared box and no cell set twice, every block known and none re-skinned by the world's pack but the drain's, the
anchors and zones of the route, every place to stand standing, the gate open as built, the lamps, a note's anchor being its board) and `Ch2L2LevelTest` (the states and
encounters; the jar's `level.json` without a problem; the checkpoints in the route's order, each in its own zone, one before every
encounter; walking the route moves the state only on, and each checkpoint replays its encounter; a broken override falls back) in
`chapter2/level2/core`; `Ch2L2PlacementsTest` (the jar's Hunters, kit, loot table and notes; the parser's handling of bad input) in the same place;
`DeathCauseTest` (a later chapter's level says itself whether it is quiet).
