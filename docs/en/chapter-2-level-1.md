# Chapter 2, Level 1

*[Versión en español](../es/chapter-2-level-1.md) · [All pages](README.md)*

Level 1 of Chapter 2 is the factory, four months after Chapter 1: an ordinary scientist hired in the place of the one
who "fell ill", three acts of tasks, a meeting and sneaking, ending in the supervisor's office. It is built in phases
(0 to 10), one at a time, on the **private** branch `chapter-2/level-1` (pushed only to the remote `private`, never to
`origin`) until Levels 1 and 2 of the chapter are published. **Where it stands: phase 2B, the map.** Nothing is
playable yet as a level (it has no tasks, no furniture and no people), but its map is built and a player can be put in it: its
rules apply to them and the time clock moves them to the first task.

## The state machine

`core.ch2l1.Ch2L1State` has ten states, in the order they are played. Each player's current one is kept in their
progress (`PlayerProgress.ch2l1State`, saved by `CardinalProgress` as `ch2_l1_state`), so it survives leaving the world, a
death and a new session. A player who has never begun the level is at `ACT1_CLOCKIN`; a new game (`ProgressService.reset`)
takes everyone back there.

| State | Meaning |
|---|---|
| `ACT1_CLOCKIN` | Arrived at the factory: the time clock is still to be punched |
| `ACT1_TASK1`, `ACT1_TASK2`, `ACT1_TASK3` | Act 1's three tasks, in order: register the samples, calibrate the analyzer, sweep the west corridor |
| `ACT2_FOLLOW` | The coworker has come to take the player to the lockers |
| `ACT2_MEETING` | The meeting at the lockers (a cinematic) |
| `ACT3_EXPLORE` | Sneaking about the auxiliary laboratory, the staff corridor and the office |
| `ACT3_CUTSCENE` | The office cinematic, from the second paper closed |
| `ACT3_OVERTIME` | After the office, with the unpaid overtime pass: the way to the sealed zone is open |
| `DONE` | The player crossed into Level 2 |

`platform.Ch2L1` is the only door to the state: `setState(player, state, reason)` saves it, logs `Chapter 2 Level 1: <player>
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
| `npcs.json` | The factory's people: sixteen workers with their routines, the groups (the lockers' one) and how alive they look (`life`) | `Ch2L1NpcsParser`, typed |
| `patrols.json` | The patrols | Empty (`{}`) until the phase that builds them gives it its reader |
| `cutscenes/meeting.json`, `cutscenes/office.json` | The two cinematics | The same |

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
| "Level 2" corridor, behind the sealed door | 11 × 6 | 4 |

The look is the prologue's laboratory, clean and modern: white concrete, a light grey floor, a dark grey first course of wall,
glass (the auxiliary lab's wall towards the ring's west stretch: `factory_glass`, a block of the mod with no frame drawn on each
block and a sheen that runs on from one to the next, so the wall reads as one sheet) and the lab's ceiling lamps (160). The
LAB, the corridors, the lockers and the auxiliary lab were enlarged after the first walk through the level (the drawing's measures were relative). There are 21
doors, all the mod's own (none is vanilla): **free** ones, the lab door (`factory_door`: white, a tall window; a pair
of leaves opens and shuts together by hand, `LinkedDoorBlock`, with the hinges on the outer edges); the one from the ring into the authorized corridor is free too, but it is the way into a
forbidden zone (the drawing's red circle); **locked** ones, the grey emergency door (`emergency_door`), which hands do not open, for the auxiliary
lab, the office and the sealed door (the later phases open them with the badge, the code and Rowan's card); and one more of
them at the corridor's east end that opens onto nothing. The three doors of the entrance lead to the main corridor and the LAB, and the
time clock hangs on the entrance's south wall.

**Where and how.** The plan stands at (1024, 64, 1024) of the map world, free of Chapter 1's levels and of the prologue's room.
`platform.Ch2L1Build` puts it there in steps of 8,000 cells a tick with the chunks kept loaded (a second or two, no stutter),
first emptying its box; it remembers the fingerprint of the plan it built (`data/every_tunnel_ch2_l1.dat`), so `Ch2L1.enter`
builds the level only when it is missing or the plan changed. Every block is looked up before the world is touched; a plan
with a block or a state the game does not know fails with a message and leaves the world as it was. `/et ch2 l1 build` builds
again over what is there, and **`/et ch2 l1 unbuild` empties the area: do it before packing the world into a jar**
(`packMapWorld` zips the region files too, and the map belongs to Chapter 2, not to Chapter 1's world).

**Not there yet:** furniture (it comes with the phase that needs it: the bench with the tubes, the terminal and the analyzer, the broom, the dirt
and the ceiling speakers came with phase 3; the lockers, the LAB's six work stations and the cameras with phase 4; the rest of the LAB's furniture is still to come, the desk and the papers are for 6), the text signs ("LEVEL 2", "Restricted access", the room names: the plan cannot carry
a text block's data) and the office's keypad (a block of phase 6, with its anchor `office_keypad`). The hand-over from the
prologue to the level is not there either: `enter` is called by hand.


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
The other keys: `speaker.<who>` (`coworker`, `worker_a`, `worker_b`, `rowan_loudspeaker`, `rowan`), `objective.<step>`
(the objective panel's texts, for phase 2), `restricted_door` (final) and the seven documents (`notice`, `union_list`,
`chamber_log`, `authorization`, `medical_file`, `contract_14`, `painting_plaque`). **The documents' texts are
placeholders until phase 6**, when the papers and their reading screen are designed.

## The people

`FactoryNpcEntity` is one entity type for everyone: Rowan, the coworker, the workers. Its `skin` (synced to the client)
says who: `rowan`, `coworker`, `worker_a`, `worker_b` and `generic_1` to `generic_3` (`Ch2L1Skins`: six faces in the
union, three of whom speak, and Rowan). `FactoryNpcRenderer` draws the player's model with classic 4 px arms and the
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
point; anyone who stops getting nearer for 5 s, a bump, is put at the point), so sixteen people cost almost nothing. `walk_speed` in the file scales their stroll, and a worker's own `pace` (optional) theirs.

**Alive, not robotic** (phase 4B; `life` in `npcs.json`, `Ch2L1Life`, every key optional; the code in `core/ch2l1/Ch2L1NpcLife.kt`, plain Kotlin, tested):
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

`platform.Ch2L1Npcs` keeps them: while a player plays the level, inside it and before the cinematic, `ensureAmbient` (every 5 s, and on entering) puts in each worker that is missing; the
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

**The rules follow the player, not the place.** A player is *active* in the level (`PlayerProgress.ch2l1Active`, saved with
the state and sent to the client by Cardinal; `Ch2L1.active`) from `Ch2L1.enter` to `Ch2L1.leave`. While they are:

| Rule | Where it is |
|---|---|
| Nothing hurts them (only `/kill` and the void still count), thirst and the battery do not run down, **sprinting is free** (no stamina) | `SafeLevelHooks.isSafe`, which `ResourceHooks` and the damage event ask |
| **No flashlight**: its key does nothing | `FlashlightHooks.available` |
| **Nothing they do makes noise** for the AI (sprint included) | `NoiseHooks.makesNoise` |
| **No status bars**: the vanilla ones stay hidden and the mod's (stamina, thirst, battery) are not drawn; the hotbar and the crosshair stay | `StatusHud.render` |

They apply to Survival and Adventure players of a world with levels (`ResourceHooks.appliesTo`), not to Creative.
`enter` also empties the pockets (the new hire has none of Chapter 1's things), switches the flashlight off, sets the state to
`ACT1_CLOCKIN`, makes the clocks red again and teleports to the anchor `spawn_clockin`. If the map is not built, `enter` builds it first (a second
or two) and the player goes in when it is done; the chapter's new game will call it later.

**The time clock** is `TimeClockBlock`, a card reader on a wall with no art of its own (it shares the reader's models and
sounds): red until someone clocks in, then green. Right-clicked by an active player at `ACT1_CLOCKIN` it beeps and moves them
to `ACT1_TASK1`; later it buzzes ("You already clocked in"); for anyone else it does nothing. The map places one (on the
entrance's south wall); `/et ch2 l1 clock` puts another where you look. It goes red again whenever a player is put back at `ACT1_CLOCKIN`.

**The objective panel** (`client.hud.ObjectivePanel`) is small, in the top right corner: "OBJECTIVE" in grey and under it the
text of the player's state (`Ch2L1Objectives`: one lang key per state, under `every_tunnel.ch2.l1.objective.`; none in the
cinematics and at the end), on a faint strip. It fades in when the objective changes and makes no sound. The client reads the
state from the player's progress, so there is no packet of its own.

## Act 1: the tasks and the loudspeakers

**The three tasks** (`platform.Ch2L1TaskHooks`; the blocks in `Ch2L1TaskBlocks.kt` only hand their use to it; where things are and how long
each takes is `tasks.json`):

1. **Samples** (`ACT1_TASK1`): click the three `sample_tube`s on the bench along the LAB's west wall (each goes, with a pip: 1/3, 2/3, 3/3), then the
   `noname_terminal`. It opens **NonameOS** (`client.screen.NonameOsScreen`, green phosphor in the frame of Level 5's monitors): the SAMPLE REGISTRY
   window, a row for each tube with its code (`S-0412`...), its state and a REGISTER button; a click fills a bar over `register_seconds` and then
   asks the server to register it (`ch2l1_os_register`), which answers with the registry as it stands (`ch2l1_os_open`); the last one closes
   the batch, the screen logs off by itself and the state is `ACT1_TASK2`. The registry is one window of a screen made to be reused for the
   documents of later phases.
2. **Analyzer** (`ACT1_TASK2`): a click **held** on the `analyzer` (a new block, made by `tools/blocks/generate.py`) for `calibrate_seconds`.
   The game repeats a held click every 4 ticks; `core.ch2l1.HoldTracker` counts the ticks while the uses come within 8 of each other and on the
   same block, so looking away or letting go starts it over. A bar (`client.hud.HoldBar`, from the packet `ch2l1_hold`) and a pip that rises in pitch
   show it. Then `ACT1_TASK3`.
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

**The guide arrow** (`client.render.TaskArrow`, targets in `core.ch2l1.Ch2L1Guide`, settings in `tasks.json`'s `guide`): a filled V pointing down, blue with white
edges, that turns on itself and bobs a little over the place of the player's current step, so that the way is clear when the LAB is full of benches and machines. It is drawn
without a depth test (furniture does not hide it) and grows with distance so that it is readable across the room. It points at the nearest of what is left: the time clock before
clocking in; the sample tubes not taken yet and then the terminal; the analyzer; the broom and then the patches of dirt not swept. It hides when the player comes within
`hide_within` (1.5 blocks along the floor) of it, in a cinematic and with the HUD hidden; `height` (1.1) is how high it floats over the anchor and `enabled` turns it off.
The later phases add the steps of Acts 2 and 3 to `Ch2L1Guide.targets`.

**The loudspeakers** (`platform.Ch2L1SpeakerHooks`, rules in `core.ch2l1.Ch2L1SpeakerSchedule`, data in `speakers.json`): Rowan's announcements
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

**The first announcement is a cinematic** (`a1`; `core.ch2l1.Ch2L1IntroScene`, `platform.Ch2L1IntroHooks`): when the player leaves NonameOS after the first task they cannot move,
the black bands slide in and the camera turns out of their own eyes, where they stand, to a fixed ceiling speaker (`speakers.json`, `intro.shots`: `camera: "player"`
looking at `speaker_lab_src`, the LAB's speaker, which hangs near the terminal), the chime sounds from it as the shot begins, the voice follows, and the camera flies back
into their eyes as the bands go. It is the machinery of the director's scene and the prologue (`Cutscene`, `Timeline`, `CutsceneView.Kind.FACTORY`); a shot's camera is an anchor
(or `player`) and what it looks at an anchor, in world coordinates, so moving the map moves the shot, and more shots can be added in the file; skippable with the jump key pressed
twice, like the others. The other announcements have no bands: the chime, the voice and its subtitle, and the player walks on.

`/et ch2 l1 tasks` shows how far the player is and anything `tasks.json` or `speakers.json` names that `anchors.json` does not have
(`core.ch2l1.Ch2L1Wiring`, which `/et reload` also reports).

## Debug commands

`platform.Ch2L1Commands`, operator level 2, **development runs only** (as [Debugging](debugging.md) explains for
`/everytunnel`):

| Command | What it does |
|---|---|
| `/et reload` | Reads the level's JSON files again and says what is wrong with them |
| `/et ch2 l1 enter` | You become a new hire: the level's rules apply to you (in Survival or Adventure), your pockets are emptied, the state is the start and you are at `spawn_clockin` |
| `/et ch2 l1 leave` | The rules of the rest of the game apply to you again |
| `/et ch2 l1 clock` | Puts a time clock on the wall you look at |
| `/et ch2 l1 build` | Builds the level again over what is there (a new plan is built by itself when someone enters) |
| `/et ch2 l1 unbuild` | Empties the whole area (before packing the world into a jar); whoever stands in it falls |
| `/et ch2 l1 anchors` | Every anchor (points, zones, paths) with its coordinates, the provisional ones marked |
| `/et ch2 l1 goto <anchor>` | Takes you to a point, or to the middle of a zone, one block over its floor |
| `/et ch2 l1 state` | Shows your state and the next one |
| `/et ch2 l1 state <state>` | Puts you in that state (any case, `-` for `_`), as if you had got there by playing |
| `/et ch2 l1 skip` | Moves you on to the next state |
| `/et ch2 l1 npc <skin>` | Puts a person of the factory in front of you, to see its skin (`rowan`, `coworker`, `worker_a`, `worker_b`, `generic_1` to `generic_3`) |
| `/et ch2 l1 npc clear` | Takes them all away |
| `/et ch2 l1 npc ambient` | Puts in the factory's workers that are missing (you must be playing the level and be in it) |
| `/et ch2 l1 npc group <name>` | Puts in a group of `npcs.json` at its anchors (`taquillas`) |
| `/et ch2 l1 npc remove <group>` | Takes a group away (`lab`, `main`, `dining`, `taquillas`) |
| `/et ch2 l1 tasks` | How far you are in the three tasks, and what the data names that is missing |
| `/et ch2 l1 tasks reset` | The three tasks start over: the tubes, the broom and the dirt are back where the state says |
| `/et ch2 l1 intro` | The first announcement's cinematic, now |
| `/et ch2 l1 say <rowan_loyal_a1..a6>` | An announcement now (the chime, then the voice), from the speaker of the zone you stand in |

`state` and `skip` change the state, and with it the objective on the panel; the rest of a state's effects (an object, a
character) come with the phase that gives them, and will run on the command too, which is what makes every phase testable without playing the ones before.

## Adding a phase's system

1. Plain Kotlin in `core.ch2l1` for its rules and its JSON reader, with unit tests (`Ch2L1DataTest` checks that every
   file in the jar parses).
2. Give its file in `data/every_tunnel/ch2/l1/` a shape and a typed reader; read the document with
   `Ch2L1DataLoader.data.document("<name>")` and never cache it, so that `/et reload` reaches it.
3. A hook in `platform` that registers with `Ch2L1.onStateChange` and, if it has moments of its own, listens to the
   server tick; it acts only in worlds with levels (`WorldRules`).
4. Texts in `en_us.json` and `es_es.json` under `every_tunnel.ch2.l1.<id>`; this page and the [code map](code-map.md), in
   both languages.

## Tests

`Ch2L1StateTest` (the order, `next`, `parse`), `Ch2L1AnchorsParserTest` (the format, every kind of problem) and
`Ch2L1DataTest` (the jar's files, which copy wins, the fallbacks), `Ch2L1VoicesTest` (the 32 lines, their sound events, Oggs
and cues, the texts in both languages) `Ch2L1SkinsTest` (the skins' format) and `Ch2L1ObjectivesTest` (one objective per step, in both languages) and `Ch2L1MapPlanTest` (the plan's blocks and doors, the
anchors standing on its floors), all in `core/ch2l1`; `Ch2L1NpcsTest` (the people's file, the routines and a typist's, a walker's and a stuck one's every tick), `Ch2L1NpcLifeTest` (the `life` keys and a worker's pace, easing in and braking, slowing for a turn, lingering, where the eyes go and the player's look with its cooldown, pairing for a talk and the turns in it), `Ch2L1TasksTest` (the tasks' file, the bits, the held click), `Ch2L1GuideTest` (what the arrow points at in each step), `ModelRotationTest` (every model's rotation angles are ones the game accepts), `Ch2L1SpeakersTest` (the file and every rule of the schedule, and that a schedule made for an old game is replaced when the progress goes back: silent before the first task, the beat after the terminal, the zone, never two at once, the cooldown, the pool once each, act two cancels), `Ch2L1IntroSceneTest` (the cinematic's shots, timing and missing anchors), `Ch2L1WiringTest` (everything the files name is in `anchors.json`) and `Ch2L1TaskTextsTest` (the texts in both languages); `Ch2L1MapPlanTest` also checks the props of the tasks and the speakers stand where their anchors say, that the lockers' room has its lockers and no camera, that every other space has one and that every worker and route stands on the floor. `PlayerProgressTest` covers the state and the flag in the progress.
