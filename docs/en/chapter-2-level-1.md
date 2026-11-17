# Chapter 2, Level 1

*[Versión en español](../es/chapter-2-level-1.md) · [All pages](README.md)*

Level 1 of Chapter 2 is the factory, four months after Chapter 1: an ordinary scientist hired in the place of the one
who "fell ill", three acts of tasks, a meeting and sneaking, ending in the supervisor's office. It is built in phases
(0 to 11), one at a time, on the **private** branch `chapter-2/level-1` (pushed only to the remote `private`, never to
`origin`) until Levels 1 and 2 of the chapter are published. **Where it stands: phase 11, the QA pass, done: the level is
finished.** It is reached from the main menu (Chapter 2, NEW GAME: the prologue, then the factory) and played from the clock to its end,
the basement swept and back to the menu, every room furnished and peopled. Level 2 is next; both are published together as a demo.

**How it is reached.** The main menu's Chapter 2, NEW GAME: the prologue (skippable), then the factory. The screen is black from
the world's loading until the prologue begins, and the black of its end stays until the player stands in the factory. The level is not saved: it opens the same world as Chapter 1's saved game, so
before going in, where the player stood and what they carried are kept (`Ch2L1Return`), and leaving the world, at the level's end or
halfway, takes them out of the level and gives both back; a world left without leaving (the game stopped) does it on joining. Every
NEW GAME of Chapter 2 starts again from the prologue. In development, `/et ch2 l1 enter` goes in without any of this.

Its code is under `daio7771.everytunnel.chapter2` (the client's under the same package in `src/client`): `level1.core` (plain
Kotlin, tested), `level1.platform` (Minecraft glue) and `level1.client`, apart from the base game, which never names it (see
[Architecture](architecture.md#chapters)). Its texts are in `tools/lang/src/chapter2/`.

## The state machine

`level1.core.Ch2L1State` has ten states, in the order they are played. Each player's current one is kept in their
Chapter 2 progress (`Ch2PlayerProgress.l1State`, saved by `Ch2Progress`, the chapter's own component, as `l1_state`), so it survives leaving the world, a
death and a new session. A player who has never begun the level is at `ACT1_CLOCKIN`; a new game (`ProgressService.reset`)
takes everyone back there.

| State | Meaning |
|---|---|
| `ACT1_CLOCKIN` | Arrived at the factory: the time clock is still to be punched |
| `ACT1_TASK1`, `ACT1_TASK2`, `ACT1_REAGENTS`, `ACT1_GAUGE`, `ACT1_TASK3` | Act 1's five tasks, in order: register the samples, calibrate the analyzer, restock the reagents, log the centrifuge's speed, sweep the west corridor |
| `ACT2_FOLLOW` | The coworker has come to take the player to the lockers |
| `ACT2_MEETING` | The meeting at the lockers (a cinematic) |
| `ACT3_EXPLORE` | Sneaking about the auxiliary laboratory, the staff corridor and the office |
| `ACT3_CUTSCENE` | The office cinematic, from the second paper closed |
| `ACT3_OVERTIME` | After the office, with the unpaid overtime pass: the way to the sealed zone is open |
| `ACT3_BASEMENT` | Through the sealed door, which slammed behind: the overtime, sweeping the basement |
| `DONE` | The basement swept: its lights went out, and the game went back to the menu |

`level1.platform.Ch2L1` is the only door to the state: `setState(player, state, reason)` saves it, logs `Chapter 2 Level 1: <player>
goes A -> B (<reason>)` and tells the listeners; `skip(player)` moves to the next one. A system of the level registers
once with `Ch2L1.onStateChange` and does what entering a state means (an objective, a scene, a speaker line); the state
machine knows none of it. That is what makes a step reachable by playing or by command with the same result.

## The data files

Everything adjustable is JSON, not code: changing a rhythm, a route or a text is editing a file.

| File | Holds | Read by |
|---|---|---|
| `anchors.json` | The named places: points (a position and a facing) and zones (a box) | `Ch2L1AnchorsParser`, typed |
| `tasks.json` | The three tasks of Act 1: which anchors hold the tubes, the terminal, the analyzer, the broom and the dirt, and how long each takes | `Ch2L1TasksParser`, typed |
| `speakers.json` | Rowan's loudspeakers: the zones and their ceiling speakers, the fixed lines and the pool, the cooldown, the first announcement's cinematic | `Ch2L1SpeakersParser`, typed |
| `npcs.json` | The factory's people: twenty-two workers with their routines (the receptionist among them), the groups (the lockers' one) and how alive they look (`life`) | `Ch2L1NpcsParser`, typed |
| `patrols.json` | The patrols: how the guards watch (sight, what is suspicious, the warning, the chase, where a caught player goes back) and each guard's round | `Ch2L1PatrolsParser`, typed |
| `cutscenes/meeting.json` | Act 2: following the coworker and the meeting | `Ch2L1Act2Parser`, typed |
| `act3.json` | Act 3's exploring: the badge reader, the keypad and Rowan's code, the computers and their files, the doors that say no, chamber 02, the arrow's wait | `Ch2L1Act3Parser`, typed |
| `cutscenes/office.json` | The office's cinematic: Rowan's way in, his place by the door and the desk's front (anchors), his lines, the pauses, which line does what (cuffs, palms, leaning, the glance, the tie, pointing, the pass, the clap), the hum | `Ch2L1OfficeParser`, typed |

The jar holds the defaults, in `data/every_tunnel/ch2/l1/`. **To tune without rebuilding**, put a file of the same name in
the game's config folder, `config/every_tunnel/ch2/l1/` (in a development run, `run/config/...`), and run `/et reload`.
The override wins as long as it is usable (valid JSON, an object); a broken one is reported, in the log and in the answer
to the command, and the jar's copy is used, so a typo never leaves the level without data. A file with no usable copy gives
an empty one and a problem. The files are read at launch and on `/et reload`; they do not follow vanilla's `/reload`.

`anchors.json` looks like a level's file (see [Levels](levels.md)):

```json
{
  "dimension": "minecraft:overworld",
  "anchors": [
    { "id": "spawn_clockin", "pos": [-48.5, 65, -135.5], "yaw": -90, "pitch": 0, "provisional": true }
  ],
  "zones": [
    { "id": "speaker_lab", "from": [-40, 64, -146], "to": [3, 69, -127], "provisional": true }
  ]
}
```

Systems ask for a place by name and never by coordinates, so moving the map means editing this one file. An id (a-z, 0-9, `_`)
is unique across points, zones and paths (a path is a line of points, a patrol's route). **`provisional`** marks a place
whose furniture or routine is still to be designed (the terminal, the people round the column, the papers, the patrols). The
jar's `anchors.json` is **written by the map's generator** (see [The map](#the-map)): never edit it by hand; to tune a place,
put an edited copy in the config folder.

## The map

The level's map is **drawn by the author** on paper, with relative sizes; `tools/map/ch2l1/generate.py` turns that drawing into
blocks (`level.py` holds what is where, `preview.py` the picture). It writes `map.json`, the plan the mod builds, and
`anchors.json`, the named places in world coordinates, and `generate.py --install` puts both in `data/every_tunnel/ch2/l1/`
(committed); `tools/map/ch2l1/output/level_preview.png` is the plan from above (git-ignored). **Neither JSON is edited by
hand**: change `level.py` and run the tool again. Its checks stop it if a door does not join two spaces, if an anchor has no
room to stand, or if a restricted space can be reached by a free door.

| Space | X × Z (blocks) | Air high |
|---|---|---|
| Entrance (half an ellipse, with three doors) | 42 × 9 | 9 |
| Main corridor | 11 × 36 | 5 |
| LAB | 30 × 36 | 6 |
| WC (west) · Lockers (with a 2 × 2 column) | 5 × 5 · 14 × 18 | 4 |
| Ring, 4 wide, round the dining room (46 × 25 with its walls) · Dining room | 4 · 36 × 15 | 4 · 6 |
| Auxiliary lab (restricted) | 14 × 24 | 5 |
| Authorized corridor · Security · Office · WC (restricted) | 46 × 4 · 13 × 6 · 12 × 7 · 6 × 3 | 4 |
| Sealed zone, the landing behind the sealed door · the barred way east ("Under construction") | 11 × 6 · 5 × 6 | 4 · 3 |
| Stairs down, south of the landing (8 steps) · Basement, 8 blocks under the factory | 3 × 8 · 26 × 24 | 5 to 6 (the ceiling steps down with them) · 6 |

The look is the prologue's laboratory, clean and modern: white concrete, a light grey floor, a dark grey first course of wall,
glass (the auxiliary lab's wall towards the ring's west stretch: `factory_glass`, a block of the mod with no frame drawn on each
block and a sheen that runs on from one to the next, so the wall reads as one sheet) and the lab's ceiling lamps (160). The
LAB, the corridors, the lockers and the auxiliary lab were enlarged after the first walk through the level (the drawing's measures were relative), and phase 6
widened the auxiliary lab to the west: a room of its own behind a partition (`aux_chamber`, 9 × 24), reached by an opening at the
partition's north end (a door kind of the plan with no door, `opening`), holds sealed chamber 02, and the generator checks that no
line from an eye in the ring through the glass reaches inside it (`check_chamber_hidden`). Phase 9 taught the generator spaces whose
floor lies lower (a space's `y0`: the basement's is -8) and flights of stairs (a space whose cells each have their own floor, one lower a
row, with a stair block on it); the spaces joined with no door (the landing, the stairs, the basement) are its `LINKS`. There are 21
doors, all the mod's own (none is vanilla): **free** ones, the lab door (`factory_door`: white, a tall window; a pair
of leaves opens and shuts together by hand, `LinkedDoorBlock`, with the hinges on the outer edges); the one from the ring into the authorized corridor is free too, but it is the way into a
forbidden zone (the drawing's red circle); **locked** ones, the grey authorized personnel door (`secure_door`, its red plate), which hands do not open, for the auxiliary
lab, the office and the sealed door (the later phases open them with the badge, the code and Rowan's card); and one more of
them at the corridor's east end that opens onto nothing. The three doors of the entrance lead to the main corridor and the LAB, and the
time clock hangs on the entrance's south wall.

**Where and how.** The plan stands at (1024, 64, 1024) of the map world, free of Chapter 1's levels and of the prologue's room.
`level1.platform.Ch2L1Build` puts it there in steps of 8,000 cells a tick with the chunks kept loaded (a second or two, no stutter),
first emptying its box; it remembers the fingerprint of the plan it built (`data/every_tunnel_ch2_l1.dat`), so `Ch2L1.enter`
builds the level only when it is missing or the plan changed. Every block is looked up before the world is touched; a plan
with a block or a state the game does not know fails with a message and leaves the world as it was. `/et ch2 l1 build` builds
again over what is there, and **`/et ch2 l1 unbuild` empties the area: do it before packing the world into a jar**
(`packMapWorld` zips the region files too, and the map belongs to Chapter 2, not to Chapter 1's world).

**Text on walls.** The plan's `texts` say what each of its `wall_text` blocks (Level 3's nameplates) says, a lang key in a style;
`Ch2L1Build` writes them on the blocks' entities once the build is done (chamber 02's label, the portrait's plaque, the landing's two
signs). Its `messages` do the same for its `wall_message` blocks (scrawls in chalk: a lang key, a line's height, an angle, the chalk's
colour): the "???" over the basement's terminal. Scrawls are always in English (their Spanish texts are the English ones), and the generator
picks the chalk from the wall behind: black on a wall that looks white, white on one that looks dark, as the basement's white concrete does
in its light (`Level.chalk`; `wall_chalk`, the painted font's chalk
version from `tools/gui/wall_font.py`, with no drips).

**The furniture** came with the phases that needed it (the tasks' with phase 3; the lockers, the LAB's first work stations and the
cameras with phase 4; Act 3's with phase 6; the basement's with phase 9), and the rest with **phase 10a**, which also gave the factory the
face of the rebuilt company, NONAME INDUSTRIES: corporate and clean, white with its blue.

- **Entrance:** the reception desk with two monitors in front of the logo wall ("NONAME INDUSTRIES" in big blue letters on a white panel,
  the `logo` style), a waiting corner to the west (sofas, a low table), plants. (The `screen` style, a lit screen, is there for later.)
- **LAB:** fume hoods along the north wall, sample storage along the east wall (shelves, then cabinets past the sealed door), two islands
  in the middle (work stations with monitors; instruments: microscopes, beakers, a tube rack), carts. The south wall east of the analyzer
  holds phase 10b's two tasks (below, Act 1).
- **Dining room:** tables of four, the counter with two coffee machines, a water dispenser, three vending machines, plants, wall sconces
  and **warm ceiling lights** of its own (`warm_ceiling_light`; the generator's `SPACE_LIGHTS`).
- **WCs**, with **no cameras**: stalls (a toilet, partitions two high, a door: `toilet`, `stall_wall`, `stall_door`) and sinks with a
  mirror over them (`wc_sink`, `wc_mirror`), new blocks of `tools/blocks/generate.py`.
- **Security** (guard B's rest): a bank of monitors over a desk and a chair, the guards' lockers, a table, a counter with a coffee machine,
  the key rack; its middle stays clear for guard B.
- Plants and a bench in the main corridor, plants in the ring and the authorized corridor.
- **Signs:** a plate over every door with the room's name, on the side one comes from (`DOOR_SIGNS`, centred over a door two blocks wide
  with the plan's `offset`), an arrow to the dining room, and the company's posters (`poster` style: white lines on its blue) along the
  corridors, the LAB and the dining room.

The generator now also checks that no patrol's or person's way runs into furniture and that nothing stands in front of a door. The
hand-over from the prologue to the level is not there yet: `enter` is called by hand.


## Voices and subtitles

The level has 32 voice lines (ElevenLabs, from the user's MP3s: the AI exception of [Audio](audio.md)): the coworker
(`follow_01` to `03`, and most of `meeting_*`), the union's workers A and B (the `_a` and `_b` lines of the meeting) and
Rowan, on the loudspeakers (`rowan_loyal_a1` to `a6`) and in his office (`rowan_office_c1` to `c8`).
`tools/sounds/ch2_l1_voices.py` converts them (mono Ogg, one gain for each of the two groups, the union's and Rowan's, so
that each sits at -18 LUFS as the director's lines do), cuts each into subtitle cues at its pauses and writes
`cutscene/ch2_l1_voices.json`; `export.py` makes each a sound event, `voice.<id>`
(`/playsound every_tunnel:voice.follow_01 voice @s`). The `rowan_loyal_*` now play from Act 1's loudspeakers (see
[Act 1](#act-1-the-tasks-and-the-loudspeakers)); the systems of the later phases play the rest.

**The subtitles are always on.** `client.hud.Ch2L1Subtitles` listens to the sound manager, so whenever one of the level's
voices starts, however it was started, its cues show over the HUD, each from its time: the speaker's name in grey, then the
text in white, on a dark strip, the last cue staying a moment after the voice ends. They do not depend on the game's own
subtitles setting (these sounds have no `subtitle` in `sounds.json`). In a cinematic the HUD is hidden and the cinematic
draws its own cues.

The lang keys are all under `every_tunnel.ch2.l1.`. **A voice line's text is one key for the whole line**, the author's own
English and Spanish: `<id>` (`follow_01`, `rowan_office_c8`; the meeting's `_a` and `_b` lines have the number alone,
`meeting_03` for `meeting_03_a`). The subtitles' **cues are ranges of sentences** of that text, not texts of their own:
`ch2_l1_voices.json` says that from 2.04 s the cue shows sentence 1 on, and the client splits the translated line into
sentences and shows that range, so the timing is the same in every language and a line's words are edited in the lang
files alone. A sentence ends at `.`, `?` or `!` followed by a space (`Ch2L1Sentences`, also the tool's rule), and **the English
and the Spanish of a line must have the same number of them**: `Ch2L1VoicesTest` fails otherwise. The tool reads the
English from `en_us.json` and only rewrites the cues' timing: a quick line (under a second a cue) is shown whole.
The other keys: `speaker.<who>` (`coworker`, `worker_a`, `worker_b`, `rowan_loudspeaker`, `rowan`, `guard`), `objective.<step>`
(the objective panel's texts), `restricted_door` and the documents (`chamber_log`, `authorization`, `medical_file`, `contract_14`,
`basement_note`, `painting_plaque`): the computers' files, with their names in NonameOS (`doc.<file>.name`), and the portrait's plaque.

The basement's terminal (phase 9) is an easter egg of its own: `cipher` in `act3.json` shows its window's words as flickering cipher
symbols (`NonameOsLook.scramble`). It holds `?????????.mp3` (`recordings`: a name, the same in every language, and a sound event), whose
PLAY opens a player (a waveform that moves while it plays, `[??]`, PLAY or STOP) and plays `voice.basement_recording` from the terminal:
a voice played backwards (`ch2_l1_voices.py` reverses it, the echo put on the right way round), with no text and no subtitles on purpose,
for players to reverse and make out. And `basement_note`, a `.txt` whose hidden message is in Base64 in each language. Nothing counts
them and nothing waits on them.

## The people

`FactoryNpcEntity` is one entity type for everyone: Rowan, the coworker, the workers. Its `skin` (synced to the client)
says who: `rowan`, `coworker`, `worker_a`, `worker_b`, `generic_1` to `generic_3` and the guards `guard_1` and `guard_2` (`Ch2L1Skins`: six
faces in the union, three of whom speak, Rowan, and two guards in Chapter 1's uniform: `workers.py` paints a face of their own over
`guard.png`, the player's skin of Chapter 1). `FactoryNpcRenderer` draws the player's model with classic 4 px arms and the
skin's base layer (`textures/entity/npc/<skin>.png`). Rowan's skin is the author's own, copied as it came; the six
workers' are drawn by `tools/entity/workers.py` (a white lab coat, a badge of a colour each, a face each), which also writes
a preview sheet with Rowan's (`tools/entity/output/`, git-ignored). `Ch2L1SkinsTest` checks that every skin is 64x64, has
classic arms and leaves its second layer empty. A **kept** creature (see [Contributing](contributing.md#creatures)).

**The factory's life** (phase 4). **Sixteen ordinary people** work in the factory, from `npcs.json` (`Ch2L1NpcsParser`): ten in the LAB, three in the main corridor and
three in the dining room (`group`: `lab`, `main`, `dining`). Each has a `skin`, a place to start (`spawn`, a point anchor) and a **routine**, a list of steps done in order and
then over again: `stand` (seconds, a number or `[min, max]`), `walk` (a path anchor, optionally `reverse`) and `type` (a point anchor: they go to it, face the way it faces and
type, arms out in front, for `seconds`). Positions are anchors, so moving the map moves the people; the LAB's six work stations (a bench with a monitor, in `level.py`), the
routes (`route_*`) and every worker's spot (`npc_*`) are written by the map generator and marked provisional until the LAB is furnished. Routes are written by hand, not found
by the game's pathfinding: `Ch2L1RoutineRunner` (plain Kotlin, tested) says what to do each tick and `FactoryNpcEntity.tick` does it by setting the entity's own velocity (a stroll of 1.3 blocks a second, straight to the next
point; anyone who stops getting nearer for 5 s, a bump, is put at the point), so twenty-odd people cost almost nothing. `walk_speed` in the file scales their stroll, and a worker's own `pace` (optional) theirs.

**Alive, not robotic** (phase 4B; `life` in `npcs.json`, `Ch2L1Life`, every key optional; the code in `chapter2/level1/core/Ch2L1NpcLife.kt`, plain Kotlin, tested):
- **Walking** (`Ch2L1Gait`): everyone walks at their own pace (`walk_speed` times 1 ± up to `pace_spread`, drawn from their id), eases into it (`accel_seconds`) and brakes before the
  place they stop at; the body turns at most `turn_degrees` a tick, and a sharp turn slows them down first. They still move straight at the point, so they never stray into a wall. At a path's
  middle points they sometimes stop a moment (`linger_chance`, `linger_seconds`).
- **Looking** (`Ch2L1Gaze`): the head moves on its own, never more than 70° from the body. Walking, mostly ahead with a glance aside now and then; standing, somewhere new every `glance_seconds`
  (around, at a colleague within 8 blocks); typing, down at the keyboard, looking up a moment now and then. A player who comes within `player_range` in front of them is followed with the eyes
  for `player_look_seconds`, then not again for `player_cooldown_seconds`. To keep the head and body apart, the entity switches off vanilla's look and body controls: the server sets the body's
  `yaw` and the head's `headYaw` and pitch, which the game syncs and smooths.
- **Talking** (`Ch2L1Chat`, paired by `Ch2L1Npcs` every 5 ticks): two workers within `chat_range` of each other, neither at a station nor just out of a talk, stop `chat_chance` of the times
  (the rest walk on and are not asked again for 10 s), face each other and talk without voice for `chat_seconds`: one moves their hands (`Ch2L1Pose.TALKING`), the other nods
  (`LISTENING`), and they swap every 1.5 to 3.5 s. Their routines wait meanwhile; afterwards neither talks again for `chat_cooldown_seconds`. The pose is synced and drawn by
  `FactoryNpcRenderer` (each person a little out of step with the rest); phase 5 adds the meeting's poses to the same list.

`level1.platform.Ch2L1Npcs` keeps them: while a player plays the level, inside it and before the cinematic, `ensureAmbient` (every 5 s, and on entering) puts in each worker that is missing; the
LAB's go away when the player reaches `ACT3_CUTSCENE` (phase 8 relies on it). **Groups** (`groups` in the file) are people standing at anchors, put in by `Ch2L1Npcs.spawnGroup`: the
six of the **lockers' group** (`taquillas`, round the central column) are for the meeting and are put in by its phase, not at the start.

**The lockers.** The lockers' room (14 x 18, the only space with no cameras) has Level 3's `locker`s along three walls, two blocks tall, facing into the room. They are only furniture here: hiding in
one is off while the level is played (`HidingHooks`). The **predecessor's locker** (`predecessor_locker`, a block of its own: the same locker with its door ajar and the name on its plate scratched out;
anchors `predecessor_locker` and `predecessor_locker_block`) is the one on the west wall by the corner.

**The security cameras** are decorative: a `factory_camera` hangs in the top layer of air of every space but the lockers (19 in all: one in each room, one every 15 blocks or so in the corridors).
It is the Level 5 camera's block and block entity with a different behaviour in `SecurityCameraRenderer`: it never looks for the player, it sweeps 50 degrees either side of the way it faces once
every 10 s (each at its own point of the sweep), its red light blinks, and it whirrs softly (`block.camera.servo`) every few seconds when the player is within 14 blocks. They detect nothing; the
patrols of phase 7 do. Being drawn by the client, they cost the server nothing.

## The level's rules, the clock and the objectives

**The rules follow the player, not the place.** A player is *active* in the level (`Ch2PlayerProgress.l1Active`, saved with
the state and sent to the client by Cardinal; `Ch2L1.active`) from `Ch2L1.enter` to `Ch2L1.leave`. While they are:

The base game's hooks never ask for this level: `Chapter2` registers its rules with `PlayerRules` (no flashlight, no hiding,
safe, silent, no status bars, and the new hire's skin, a scientist in the factory's white coat, in place of the night guard's
uniform) while `Ch2L1.active`, and the hooks below ask `PlayerRules`.

| Rule | Where it is |
|---|---|
| Nothing hurts them (only `/kill` and the void still count), thirst and the battery do not run down, **sprinting is free** (no stamina) | `SafeLevelHooks.isSafe`, which `ResourceHooks` and the damage event ask |
| **No flashlight** (until Rowan gives it after his office, `ACT3_OVERTIME`): its key does nothing | `FlashlightHooks.available` |
| **Nothing they do makes noise** for the AI (sprint included) | `NoiseHooks.makesNoise` |
| **No status bars**: the vanilla ones stay hidden and the mod's (stamina, thirst, battery) are not drawn; the hotbar and the crosshair stay | `StatusHud.render` |

They apply to Survival and Adventure players of a world with levels (`ResourceHooks.appliesTo`), not to Creative.
`enter` also empties the pockets (the new hire has none of Chapter 1's things), switches the flashlight off, sets the state to
`ACT1_CLOCKIN`, makes the clocks red again and teleports to the anchor `spawn_clockin`. If the map is not built, `enter` builds it first (a second
or two) and the player goes in when it is done; the chapter's new game will call it later.

**The time clock** is `TimeClockBlock`, a card reader on a wall with no art of its own (it shares the reader's models and
sounds): red until someone clocks in, then green. Right-clicked by an active player at `ACT1_CLOCKIN` it beeps and moves them
to `ACT1_TASK1`; later it buzzes ("You already clocked in"); for anyone else it does nothing. The map places one (on the
entrance's south wall). It goes red again whenever a player is put back at `ACT1_CLOCKIN`.

**The objective panel** (`client.hud.ObjectivePanel`) is small, in the top right corner: "OBJECTIVE" in grey and under it the
text of the player's state (`Ch2L1Objectives`: one lang key per state, under `every_tunnel.ch2.l1.objective.`; none in the
cinematics and at the end), on a faint strip. It fades in when the objective changes and makes no sound. The client reads the
state from the player's progress, so there is no packet of its own.

## Act 1: the tasks and the loudspeakers

**The three tasks** (`level1.platform.Ch2L1TaskHooks`; the blocks in `Ch2L1TaskBlocks.kt` only hand their use to it; where things are and how long
each takes is `tasks.json`):

1. **Samples** (`ACT1_TASK1`): click the three `sample_tube`s on the bench along the LAB's west wall (each goes, with a pip: 1/3, 2/3, 3/3), then the
   `noname_terminal`. It opens **NonameOS** (`client.screen.NonameOsScreen`, green phosphor in the frame of Level 5's monitors): the SAMPLE REGISTRY
   window, a row for each tube with its code (`S-0412`...), its state and a REGISTER button; a click fills a bar over `register_seconds` and then
   asks the server to register it (`ch2l1_os_register`), which answers with the registry as it stands (`ch2l1_os_open`); the last one closes
   the batch, the screen logs off by itself and the state is `ACT1_TASK2`. The registry is one window of a screen made to be reused for the
   documents of later phases.
2. **Analyzer** (`ACT1_TASK2`): a click **held** on the `analyzer` (a new block, made by `tools/blocks/generate.py`) for `calibrate_seconds`.
   The game repeats a held click every 4 ticks; `level1.core.HoldTracker` counts the ticks while the uses come within 8 of each other and on the
   same block, so looking away or letting go starts it over. A bar (`client.hud.HoldBar`, from the packet `ch2l1_hold`) and a pip that rises in pitch
   show it. Then the LAB's two tasks of phase 10b (`level1.platform.Ch2L1LabTaskHooks`, `tasks.json`'s `reagents` and `gauge`), in the
   corner of the LAB's south wall east of the analyzer:
   - **Reagents** (`ACT1_REAGENTS`): three bottles (`reagent_bottle_blue`, `_yellow`, `_red`) stand on the reagent cabinet; a click takes one
     into the player's hand (the item `factory_reagent_<colour>`), and a click held on the `mixing_tank` for `pour_seconds` pours it in (the
     same bar), in any order (`reagents_taken`, `reagents_poured`; the panel counts them). The third moves on.
   - **Gauge** (`ACT1_GAUGE`): the `centrifuge` on its bench shows a speed in RPM, drawn for each player between `min_rpm` and `max_rpm` in
     steps of ten (`level1.core.Ch2L1Gauge`, the counter `gauge_rpm`; the client draws it on its display, `Ch2L1CentrifugeRenderer`). The
     terminal beside it opens NonameOS's maintenance log (`client.GaugeLogScreen`, packets `ch2l1_gauge_open` / `_try` / `_result`): the
     speed typed is checked, a wrong one buzzes and can be typed again, the right one moves on to `ACT1_TASK3`.
3. **Sweep** (`ACT1_TASK3`): the `leaning_broom` against the east wall of the ring's west stretch; a click takes it (the `broom` item goes to the
   player's hand; it exists only for this task and is taken back at the end). Then a click held for `sweep_seconds` on each of the ten
   `dirt_spot`s on the floor in front of the auxiliary lab's glass sweeps it (a swish and a puff). The last one ends the task:
   `ACT2_FOLLOW`.

How far the player has got is a few counters in their progress (`ProgressService.ch2L1Counter`: the tubes taken, the samples registered and the
dirt swept as bits, whether the broom was taken, and `heard_<line>` for the announcements), so it survives leaving the world; the client reads them too:
the objective panel shows "(1/3)" while the tubes are taken and then registered, and "(4/10)" for the dirt (`objective.count`). Every state
change (playing, `state`, `skip`) brings counters and world to what the state means (`Ch2L1TaskHooks.onState`, `reconcile`): the tubes, the broom and
the dirt stand where the player has not got yet and are gone where they have, so a task can be tried by command with the level as it would be
had they played. The plan's props are put back from the plan itself (`Ch2L1Build.planState`).

**The guide arrow** (`level1.client.TaskArrow`, targets in `level1.core.Ch2L1Guide`, settings in `tasks.json`'s `guide`): a filled V pointing down, blue with white
edges, that turns on itself and bobs a little over the place of the player's current step, so that the way is clear when the LAB is full of benches and machines. It is drawn
without a depth test (furniture does not hide it) and grows with distance so that it is readable across the room. It points at the nearest of what is left: the time clock before
clocking in; the sample tubes not taken yet and then the terminal; the analyzer; the broom and then the patches of dirt not swept. It hides when the player comes within
`hide_within` (1.5 blocks along the floor) of it, in a cinematic and with the HUD hidden; `height` (1.1) is how high it floats over the anchor and `enabled` turns it off.
The later phases add the steps of Acts 2 and 3 to `Ch2L1Guide.targets`.

**The loudspeakers** (`level1.platform.Ch2L1SpeakerHooks`, rules in `level1.core.Ch2L1SpeakerSchedule`, data in `speakers.json`): Rowan's announcements
`rowan_loyal_a1` to `a6`, from the ceiling speakers (`ceiling_speaker` blocks) of four zones, `speaker_entrance`, `speaker_lab`, `speaker_main` and
`speaker_west`, each with its emitter anchor (`speaker_<zone>_src`). The rules:

- **Rowan says nothing until the first task is done.** `a1`, the opening, comes when the last sample is registered **and the player has left NonameOS** (the client
  tells the server when the screen closes, `ch2l1_os_closed`), a beat later (`after_terminal_seconds`, 1 s); nothing speaks to a player who has NonameOS open. `a4` five seconds after
  the analyzer (`ACT1_TASK3`).
- The pool `a2`, `a3`, `a5` (it opens after `a1`) once each, in random order, each after a cooldown of 75 to 120 s from the end of the line before; never two at once.
- Only while the player stands in a speaker zone (otherwise the line waits for them to come back); none from `ACT2_FOLLOW` on, which also stops what is playing.
- `a6` is in neither list: it is kept for the office's scene. A player put past `ACT1_TASK2` by a command is taken to have heard `a1`.
- Each line is the chime (`event.ch2l1.ding`, two soft bell notes; `ding_seconds` long) and then the voice, both sent to that player alone from the zone's speaker with `volume` 3, so they carry
  all through the zone (`attenuation_distance` 48 in `sounds.json`); the subtitles are the usual ones (`Ch2L1Subtitles`). `tools/sounds/ch2_l1_voices.py` runs the six `rowan_loyal_*`
  files through a loudspeaker filter (band-limited, a little drive, a slap of the corridor) before it writes them.

**The first announcement is a cinematic** (`a1`; `level1.core.Ch2L1IntroScene`, `level1.platform.Ch2L1IntroHooks`): when the player leaves NonameOS after the first task they cannot move,
the black bands slide in and the camera turns out of their own eyes, where they stand, to a fixed ceiling speaker (`speakers.json`, `intro.shots`: `camera: "player"`
looking at `speaker_lab_src`, the LAB's speaker, which hangs near the terminal), the chime sounds from it as the shot begins, the voice follows, and the camera flies back
into their eyes as the bands go. It is the machinery of the director's scene and the prologue (`Cutscene`, `Timeline`, `CutsceneView.Kind`, here `Ch2L1IntroView.KIND`); a shot's camera is an anchor
(or `player`) and what it looks at an anchor, in world coordinates, so moving the map moves the shot, and more shots can be added in the file; skippable with the jump key pressed
twice, like the others. The other announcements have no bands: the chime, the voice and its subtitle, and the player walks on.

`/et ch2 l1 tasks` shows how far the player is and anything `tasks.json` or `speakers.json` names that `anchors.json` does not have
(`level1.core.Ch2L1Wiring`, which `/et reload` also reports).

## Act 2: the coworker and the meeting

`chapter2.level1.platform.Ch2L1Act2Hooks` runs it, from `cutscenes/meeting.json` (`Ch2L1Act2`, read by `Ch2L1Act2Parser`; every key
optional; the anchors and paths it names are checked by `Ch2L1Wiring`):

- **Waiting** (`ACT1_TASK3`): the coworker (skin `coworker`) is put at `coworker_wait`, the corner of the ring by the broom, while the
  player is still in the LAB, and stands there still, his arms hanging, his eyes on the player while they sweep.
- **Following** (`ACT2_FOLLOW`, when the last spot is swept): he says `follow_01` from where he stands (a voice of the level like any
  other: its subtitles show; nothing else is written over them, so sweeping the spots gives no message, the panel counts them) and,
  when it ends, walks briskly (`pace`, times the workers' stroll) `coworker_route` (the ring, the main corridor, the lockers' second door) to
  `meeting_coworker`, opening the doors in his way (`Ch2L1Doors`). `Ch2L1Follow` (plain Kotlin, tested) decides each tick: a player farther than
  `lag_blocks` and not ahead of him lags, so he stops, turns and waits; after `remind_after_seconds` of it he says `follow_02` or
  `follow_03`, in turns, then not again for `remind_cooldown_seconds`. The guide arrow floats over his head only when the player is
  farther than `arrow_beyond_blocks` or cannot see him (`Ch2L1Guide.follow`). Meanwhile the meeting's group (`npcs.json`'s `taquillas`)
  stands waiting in the lockers, and worker_a's and worker_b's ambient doubles leave the LAB as soon as nobody sees them (`npcs.json`'s
  `until`, `Ch2L1Npcs.retire`).
- **The meeting** (`ACT2_MEETING`, when the player is in `taquillas_trigger` with him there or close behind): the player is put at
  `meeting_player` and held, in a closed circle of eight round the middle of the room (the coworker across from them, his back to the
  predecessor's locker; in a circle nobody stands between the player and a face), and the scene plays on their client (`ch2l1_meeting_start`: the frame and the door they came in by;
  `Ch2L1MeetingView`). It is built from the data on both sides (`Ch2L1MeetingScene`, plain Kotlin, tested): the camera is the player's
  own eyes, turning smoothly (`turn_seconds`) to whoever speaks (`meeting_01` to `15` in order, `gap_seconds` apart; a speaker's place is
  the coworker's spot or that of the group's member with their skin), and to the door for `door_seconds` after `door_after`. Its subtitles
  go a cue at a time after the speaker's name (`CutsceneView.Kind.subtitleText` and `speakerAt`). On the same ticks the server directs the
  people (`FactoryNpcEntity.direction`): everyone looks at the speaker (the coworker turns to them, body and all, and back to the player for his own lines), and now and then each glances at the door (`Ch2L1Glances`); the
  speaker gestures (`TALKING`), worker_a trembles in `tremble_lines`, worker_b shakes laughing in `laugh_lines`; the others
  listen with their arms hanging still, shifting their weight; the member nearest the door walks to `<door>_inside` and shuts it; and as the
  coworker reaches "Take this" (`card_line`, `card_sentence`) he holds his arm out (`OFFERING`) and the player gets the **lab badge's copy**
  (`lab_badge_copy`, Chapter 1's keycard recoloured by `tools/blocks/generate.py`; it opens only the auxiliary lab's door, phase 6).
- **The end**: when the last line ends everyone leaves along `meeting_leave`, the coworker first, the rest one by one
  (`leave_gap_seconds`, at `leave_pace`), the door opening before them, each gone as soon as the player cannot see them; the bands go and the state is
  `ACT3_EXPLORE`. **Skipping** (`ch2l1_meeting_skip`) ends it at once with the same state: the badge given, the player at
  `taquillas_exit`, the coworker and the group gone, the door open.

Reaching a state by command does what it means: `ACT1_TASK3` puts him by the broom, `ACT2_FOLLOW` starts the walk from there, `ACT2_MEETING`
starts the meeting at once, a later state gives the badge and takes everyone away, an earlier one takes him and the badge away. The walk
and the meeting are kept in memory and picked up again from the state after leaving the world.

## Act 3: the auxiliary lab, the keypad and the office

Phase 6 (`ACT3_EXPLORE`, without the patrols of phase 7): `level1.core.Ch2L1Act3` reads `act3.json` and says what the objective
and the arrow are from the player's counters (`a3_lab_open`, `a3_read`, `a3_office_open`, `a3_in_office`, `a3_triggered`);
`level1.platform.Ch2L1Act3Hooks` does the rest on the server.

- **The auxiliary lab.** The badge reader (`badge_reader`, the card reader's models) on the ring's side of its door: clicked with the
  coworker's badge in Act 3 it turns green, sounds the reader's accept and the grey doors open and stay open (`Ch2L1Doors.set` with
  `locked`); without the badge it buzzes, "Access denied: lab badge required." Its computer (a `noname_terminal` on a desk against the
  partition) holds `chamber_log` and `authorization`; the authorization carries Rowan's code, **4719** (`code`).
- **Chamber 02.** In the room past the partition, a cell of glass up to the ceiling with a label over its front ("SEALED CHAMBER 02"),
  and in it the scientist of the prologue as the Pale man he became, standing with his back to the front, utterly still: the prologue's
  `ScientistEntity` in a stage of its own, `CONTAINED` (no animation, no rising from a crouch). Put in while someone plays the level,
  never saved, put back if lost (`ensureContained`); nothing of it can be seen from the ring.
- **The film set.** A quarter of the auxiliary lab is where the prologue was shot: its bench (`tools/prologue/room.py`'s, the stretch
  the camera saw, with the same things on it and on the wall) against the south wall, and looking at it two studio lights and a cine
  camera on tripods (`film_light`, `film_camera`).
- **The computers.** NonameOS shows a computer's files (`NonameOsFilesScreen`, the registry's look, `NonameOsLook`): a row each with
  NEW or READ and OPEN; an opened file fills the window, typed out quickly (a click or a key shows the rest), the wheel scrolling it.
  Closing it (Escape or CLOSE) tells the server (`ch2l1_file_closed`), which only then counts it as read. Reading the authorization
  moves the objective on from "Auxiliary lab" to "Find a way into the office".
- **The keypad.** On the authorized corridor's side of the office's door (`keypad`, a model of `tools/blocks/generate.py`): its screen
  (`KeypadScreen`) has a small display and twelve keys (1-9, C, 0, OK), clicked or typed (digits, Backspace, Enter); each beeps
  (`block.ch2l1.keypad_key`). The server checks the code (`ch2l1_keypad_try`): a wrong one buzzes and shows DENIED, with no limit
  of tries; Rowan's shows OPEN, turns the keypad green and opens the office's grey doors for good (Rowan shuts them in phase 8).
- **The office.** Furnished (a desk in the middle with Rowan's chair and computer, his portrait on the east wall, a bookshelf behind
  him, a sofa, plants, a runner). Stepping in moves the objective on to "Search the office". His computer holds `medical_file` and
  `contract_14`; **closing the second of them is the office cinematic's moment** (`a3_triggered`, `Ch2L1Act3Hooks.onOfficeTrigger`,
  for phase 8; until then a line in the log, and in a development run one in chat). The portrait (`rowan_portrait`, Python pixel art
  of his skin) shows its plaque's text in the action bar when clicked; the plaque under it is a `wall_text`.
- **Doors that say no.** A click on the restricted door at the corridor's east end says "Restricted access"; on the sealed door, before
  the overtime, "Sealed. Supervisor's card required." (in the overtime, shut, "Locked."); on the lab's or the office's while still shut, "Locked."
- **The arrow** (`Ch2L1Guide`): in Act 3 it shows only once the player has had the same target for `guide_after_seconds` (60): the
  reader, the lab's computer, the keypad, the office's door, Rowan's computer.

Going back before Act 3 (a new hire, `/et ch2 l1 state`) shuts both doors again, turns the reader and the keypad red and forgets the act's
counters; reaching it by command gives the badge (Act 2's hook).

## Act 3: the patrols

Phase 7 (`patrols.json`, `level1.core.Ch2L1Patrols`, `level1.platform.Ch2L1PatrolHooks`): guards (`guard_1`, `guard_2`) walk their
rounds **from the start of the level to its end**: patrol A round the ring, clockwise, stopping 5 s by the door down into the authorized
corridor (`patrol_a_pause`); patrol B out of Security (`patrol_b_spawn`, where it rests 6 s), along the authorized corridor to its east end
and back; and, **from the overtime on** (a patrol's `from`), patrol C round the dark LAB, stopping 4 s by the sealed door
(`patrol_c_pause`). They are factory people directed tick by tick (`FactoryNpcEntity.direction`, `Ch2L1PatrolRunner`); nobody else's
routine moves them. They are never saved: one missing, lost with its chunk or fallen out of the level (a rebuild empties the area under
him) is put back within 5 s, and one stuck on his round for 8 s jumps to his next point; every guard put in or taken out is logged.

They do nothing to a player who behaves. A guard **sees** within 8 blocks (4 while the player crouches) and 70° of where his head looks,
with a clear line, or within 2 blocks whatever the way he looks (`Ch2L1Watch.inView`). A player he sees is **suspicious**:
- inside a forbidden zone (`forbidden`: the auxiliary lab and chamber 02's room, the authorized corridor, the office, Security, the east WC;
  and from the overtime on the LAB, `from`: caught there, back to `return_lab` in the main corridor);
- crouching within 3 blocks of him;
- after staring at the auxiliary lab for more than 10 s (`stare_seconds`): the crosshair on it, a ray from the eyes that reaches into its zone
  through the glass (`Ch2L1Stare`); the corner of the eye does not count.

Then he stops and turns to them while a yellow "?" over his head fills (a bar under it, `notice_seconds` 1.5; `FactoryNpcEntity.alert`,
drawn by `FactoryNpcRenderer`) with a questioning sound (`event.ch2l1.guard_suspect`). Full, a red "!" and he tells them off (`guard_warn`,
a subtitle with no voice yet: `Ch2L1Subtitles.showText`); they have `grace_seconds` (3) to stop. If they do not, he runs at them
(`run_pace`, `event.ch2l1.guard_alert`) and catches them when he reaches them or after `chase_seconds` (2.5) in sight: the screen fades to
black with "You've been caught." (`CaughtOverlay`, `event.ch2l1.guard_caught`), they are put back at the entrance of the zone
(`return_aux_lab`, `return_authorized`; caught elsewhere `return_ring`) with what they opened still open, his line `guard_caught` follows, and
he is back on his round. Out of sight for `give_up_seconds` he gives up. Nobody watches during a cinematic, in Act 2, in Creative or
Spectator, or for 5 s after a catch. **In the dark LAB** (`dark_zones`, from `dark_from`, the overtime) a guard sees only `dark_range` (3)
blocks, unless the player's flashlight is on: then `flashlight_range` (12). **The guards' lines are placeholders** (text only) until the author gives theirs, with a voice.

## Act 3: the office's cinematic

Phase 8 (`cutscenes/office.json`, `level1.core.Ch2L1OfficeScene`, `level1.platform.Ch2L1OfficeHooks`, client `Ch2L1OfficeView`): closing the
second of Rowan's files (`Ch2L1Act3Hooks.onOfficeTrigger`) moves the player to `ACT3_CUTSCENE`, and Rowan catches them at his desk.

- **Before the bands**, a player elsewhere in the office glides to `office_player_mark` behind the desk, facing the door (`glide_seconds`);
  one farther away (a command) is put there.
- **The camera is the player's own eyes**, held there and fixed on Rowan (no shake, no push): first the door, which opens; he comes in by
  `rowan_doorway` to his place just inside beside the door (`rowan_threshold`), turns and shuts it, and talks from there: c1 arms loose;
  c2 fixing his cuffs (`CUFFS`); c3 his palms open from "Or am I special?" (`PALMS`), the low hum coming in (`ambient.ch2l1.office_hum`); a
  pause; c4 bending forward (`LEAN_FORWARD`); c5 a glance at the door; a long pause; c6 the tie (`TIE`); c7 pointing at the door
  (`POINTING`); c8 he comes to the front of the desk (`rowan_desk_front`) and at "Unpaid, naturally" slides the **Unpaid Overtime Pass**
  across it (`OFFERING`; an item display gliding from `card_slide_start` to `card_slide_end`, then in the player's pockets) as the hum goes:
  **the flashlight is his gift too** (the level's rules give it from the overtime on, its battery full; it does not run down here). He
  claps slowly (`CLAPPING`), goes back beside the door and opens it. Everything Rowan does is laid out tick by tick in the scene (its
  `track` of segments), so the server moves him on the very ticks the client's camera follows him. Skippable like the others.
- **After it**, Rowan waits beside the open door, watching the player, until they have left the office; then he shuts the door behind them
  and is gone.
- **However it ends** (played, skipped, or `ACT3_OVERTIME` reached by command): the pass (`overtime_pass`, Chapter 1's keycard in Rowan's
  colours), the flashlight, the objective "Overtime: sealed zone", the LAB almost dark (its lamps off, `factory_light_off`, one in four a
  faint red emergency light, `factory_light_emergency`) and empty of workers, a third patrol in it, and `rowan_loyal_a6` once, 3 to 5 s after
  the player is back in control in a loudspeaker's zone. Going back before it takes the pass and the flashlight back and turns the lights on.

The LAB will have two more tasks once Level 1 is decorated.

## The sealed zone and the end (phase 9)

`act3.json`'s `overtime` (`level1.core.Ch2L1Overtime`), the overtime's task in `tasks.json`, `level1.platform.Ch2L1Act3Hooks` (the door),
`Ch2L1TaskHooks` (the sweeping), `Ch2L1EndingHooks` and the client's `Ch2L1EndingOverlay` (the end).

- **The sealed door** opens at its badge reader on the LAB's side (`sealed_reader`) with Rowan's pass, in `ACT3_OVERTIME` only. It is not
  Level 2 yet: behind it is a landing (`sealed_corridor`) with two ways on. East, the way to the factory's other part, barred and closed for
  works ("UNDER CONSTRUCTION": bars, bricks); south, the stairs down to the basement ("BASEMENT · STORAGE").
- **It slams shut behind** (`block.big_door.slam`) once the player is `slam_after` (2.5) blocks past it on the landing, and stays shut: the
  state goes on to `ACT3_BASEMENT`, the objective to "Overtime: sweep the basement (0/8)". The guards no longer watch from here on.
- **The basement**, where everything is stored, is a store kept in order and well lit (a lamp every 4 blocks; the stairs have one every
  other step): two blocks of metal shelves three high, four rows of six each, a wide aisle between them from the stairs to the south wall,
  things brought from Chapter 1's Noname complex on the shelves' tops and the walls (the director's mask, a trophy, a lounge painting, a
  diploma...), the wet floor sign, and, at the end of the middle aisle, standing free against the south wall, a NonameOS
  terminal under a chalk "???" (see Act 3's computers: the strange recording and a ciphered note). The broom leans by
  the foot of the stairs; its eight patches of dirt are swept as task 3's are (`basement_broom_block`, `basement_dirt_spots`, the counters
  `basement_broom` and `basement_swept`, the guide arrow over the next one).
- **The last patch ends the level** (`DONE`): the counter `completed` is set (the plan's `ch2.l1.completed`), the basement's lights go out
  with a thud (`Ch2L1Build.zoneLights`, as the LAB's after the office), and `dark_seconds` (3) later the screen fades to black over
  `fade_seconds` (2.5), the closing text (`ending.text`) stays `text_seconds` (6), and the world is left, saved, for the main menu. Level 1
  is not published alone, so nothing follows it yet. Going back before `DONE` (a command) lights the basement again; going back to the
  overtime or before it shuts the sealed door and turns its reader red.

## Safety nets (phase 11)

What the QA pass added so that a player cannot get stuck:

- **Key items** (`Ch2L1KeyItems`): the broom, the reagent bottles, the badge's copy and Rowan's pass cannot be lost. Every second, one
  lying on the ground in the level is taken away, and a player who should hold one by their state and counters and does not gets it back.
- **Leaving the world mid-scene**: back in the middle of the office's cinematic, it begins again (`Ch2L1OfficeHooks`, after 2 s);
  back at the end, the basement goes dark and the ending plays again (`Ch2L1EndingHooks`).
- **Stray workers** (`Ch2L1Npcs`): one that has fallen out of the level's box is taken away and put back at the next check.
- **The way back to Chapter 1** (`Ch2L1Return`): see "How it is reached" at the top.

## Debug commands

`level1.platform.Ch2L1Commands`, operator level 2, **development runs only** (as [Debugging](debugging.md) explains for
`/everytunnel`):

| Command | What it does |
|---|---|
| `/et reload` | Reads the level's JSON files again and says what is wrong with them |
| `/et ch2 l1 enter` | You become a new hire: the level's rules apply to you (in Survival or Adventure), your pockets are emptied, the state is the start and you are at `spawn_clockin` |
| `/et ch2 l1 leave` | The rules of the rest of the game apply to you again |
| `/et ch2 l1 build` | Builds the level again over what is there (a new plan is built by itself when someone enters) |
| `/et ch2 l1 unbuild` | Empties the whole area (before packing the world into a jar); whoever stands in it falls |
| `/et ch2 l1 anchors` | Every anchor (points, zones, paths) with its coordinates, the provisional ones marked |
| `/et ch2 l1 goto <anchor>` | Takes you to a point, or to the middle of a zone, one block over its floor |
| `/et ch2 l1 state` | Shows your state and the next one |
| `/et ch2 l1 state <state>` | Puts you in that state (any case, `-` for `_`), as if you had got there by playing |
| `/et ch2 l1 skip` | Moves you on to the next state |
| `/et ch2 l1 npc <skin>` | Puts a person of the factory in front of you, to see its skin (`rowan`, `coworker`, `worker_a`, `worker_b`, `generic_1` to `generic_3`, `guard_1`, `guard_2`) |
| `/et ch2 l1 npc clear` | Takes them all away |
| `/et ch2 l1 npc ambient` | Puts in the factory's workers that are missing (you must be playing the level and be in it) |
| `/et ch2 l1 npc group <name>` | Puts in a group of `npcs.json` at its anchors (`taquillas`) |
| `/et ch2 l1 npc remove <group>` | Takes a group away (`lab`, `main`, `dining`, `taquillas`) |
| `/et ch2 l1 tasks` | How far you are in the three tasks, and what the data names that is missing |
| `/et ch2 l1 tasks reset` | The three tasks start over: the tubes, the broom and the dirt are back where the state says |
| `/et ch2 l1 patrols` | Where each guard is, where he is heading and what he thinks of you (calm, noticing with how full, warned, chasing) |
| `/et ch2 l1 act3` | What you have done in Act 3: the objective, the lab and the office open or shut, the files read |
| `/et ch2 l1 act3 reset` | Shuts the auxiliary lab and the office again, turns the reader and the keypad red and forgets Act 3 (the state stays) |
| `/et ch2 l1 intro` | The first announcement's cinematic, now |
| `/et ch2 l1 say <rowan_loyal_a1..a6>` | An announcement now (the chime, then the voice), from the speaker of the zone you stand in |

`state` and `skip` change the state, and with it the objective on the panel; the rest of a state's effects (an object, a
character) come with the phase that gives them, and will run on the command too, which is what makes every phase testable without playing the ones before.

## Adding a phase's system

1. Plain Kotlin in `chapter2.level1.core` for its rules and its JSON reader, with unit tests (`Ch2L1DataTest` checks that every
   file in the jar parses).
2. Give its file in `data/every_tunnel/ch2/l1/` a shape and a typed reader; read the document with
   `Ch2L1DataLoader.data.document("<name>")` and never cache it, so that `/et reload` reaches it.
3. A hook in `chapter2.level1.platform` (registered by `Chapter2`) that registers with `Ch2L1.onStateChange` and, if it has moments of its own, listens to the
   server tick; it acts only in worlds with levels (`WorldRules`).
4. Texts in `tools/lang/src/chapter2/en_us.json` and `es_es.json` under `every_tunnel.ch2.l1.<id>` (then `tools/lang/build.py`); this page and the [code map](code-map.md), in
   both languages.

## Tests

`Ch2L1StateTest` (the order, `next`, `parse`), `Ch2L1AnchorsParserTest` (the format, every kind of problem) and
`Ch2L1DataTest` (the jar's files, which copy wins, the fallbacks), `Ch2L1VoicesTest` (the 32 lines, their sound events, Oggs
and cues, the texts in both languages) `Ch2L1SkinsTest` (the skins' format) and `Ch2L1ObjectivesTest` (one objective per step, in both languages) and `Ch2L1MapPlanTest` (the plan's blocks and doors, the
anchors standing on its floors), all in `chapter2/level1/core`; `Ch2L1NpcsTest` (the people's file, the routines and a typist's, a walker's and a stuck one's every tick), `Ch2L1Act2Test` (the meeting file and its wiring, the coworker leading and waiting and his reminders, the meeting's lines, door and badge, the glances, the arrow over him), `Ch2L1NpcLifeTest` (the `life` keys and a worker's pace, easing in and braking, slowing for a turn, lingering, where the eyes go and the player's look with its cooldown, pairing for a talk and the turns in it), `Ch2L1TasksTest` (the tasks' file, the bits, the held click), `Ch2L1GuideTest` (what the arrow points at in each step, Act 3's included, and its wait), `Ch2L1Act3Test` (the act3 file, the code, the objective moving on, Rowan's files read), `Ch2L1OfficeTest` (the office's file and its wiring, the door before and after the lines, the lines and pauses in order, the pass and the hum on their ticks, Rowan by the door, at the desk and back, his gestures), `Ch2L1PatrolsTest` (the patrols' file and its wiring, a round with its stops and one there and back, what a guard sees, the "?" filling, the warning, the chase, the catch and giving up, a stare), `ModelRotationTest` (every model's rotation angles are ones the game accepts), `Ch2L1SpeakersTest` (the file and every rule of the schedule, and that a schedule made for an old game is replaced when the progress goes back: silent before the first task, the beat after the terminal, the zone, never two at once, the cooldown, the pool once each, act two cancels), `Ch2L1IntroSceneTest` (the cinematic's shots, timing and missing anchors), `Ch2L1WiringTest` (everything the files name is in `anchors.json`, `act3.json`'s too) and `Ch2L1TaskTextsTest` (the texts in both languages); `Ch2L1MapPlanTest` also checks the props of the tasks and the speakers stand where their anchors say, that the lockers' room has its lockers and no camera, that every other space has one and that every worker and route stands on the floor. `Ch2PlayerProgressTest` covers the state, the flag and the counters in the chapter's progress, and `ChapterBoundaryTest` that no code outside `chapter2` names it.
