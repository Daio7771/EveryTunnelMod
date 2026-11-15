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
| `speakers.json`, `tasks.json`, `patrols.json`, `npcs.json` | The loudspeakers, the tasks, the patrols, the characters | Empty (`{}`) until the phase that builds them gives each its reader |
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
| WC (west) · Lockers (with a 2 × 2 column) | 5 × 5 · 8 × 11 | 4 |
| Ring, 4 wide, round the dining room (46 × 25 with its walls) · Dining room | 4 · 36 × 15 | 4 · 6 |
| Auxiliary lab (restricted) | 14 × 24 | 5 |
| Authorized corridor · Security · Office · WC (restricted) | 46 × 4 · 13 × 6 · 12 × 7 · 6 × 3 | 4 |
| "Level 2" corridor, behind the sealed door | 11 × 6 | 4 |

The look is the prologue's laboratory, clean and modern: white concrete, a light grey floor, a dark grey first course of wall,
glass (the auxiliary lab's wall towards the ring's west stretch) and the lab's ceiling lamps (152). The LAB, the corridors and
the auxiliary lab were enlarged after the first walk through the level (the drawing's measures were relative). There are 21
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

**Not there yet:** furniture (it comes with the phase that needs it: the terminal and the analyzer in phase 3, the lockers in
4 and 5, the desk and the papers in 6), the text signs ("LEVEL 2", "Restricted access", the room names: the plan cannot carry
a text block's data) and the office's keypad (a block of phase 6, with its anchor `office_keypad`). The hand-over from the
prologue to the level is not there either: `enter` is called by hand.


## Voices and subtitles

The level has 32 voice lines (ElevenLabs, from the user's MP3s: the AI exception of [Audio](audio.md)): the coworker
(`follow_01` to `03`, and most of `meeting_*`), the union's workers A and B (the `_a` and `_b` lines of the meeting) and
Rowan, on the loudspeakers (`rowan_loyal_a1` to `a6`) and in his office (`rowan_office_c1` to `c8`).
`tools/sounds/ch2_l1_voices.py` converts them (mono Ogg, one gain for each of the two groups, the union's and Rowan's, so
that each sits at -18 LUFS as the director's lines do), cuts each into subtitle cues at its pauses and writes
`cutscene/ch2_l1_voices.json`; `export.py` makes each a sound event, `voice.<id>`
(`/playsound every_tunnel:voice.follow_01 voice @s`). Nothing plays them yet: the systems of the later phases do.

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
classic arms and leaves its second layer empty. A **kept** creature (see [Contributing](contributing.md#creatures)). For
now they only stand where `/et ch2 l1 npc` puts them; their routines come in phase 4.

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
anchors standing on its floors), all in `core/ch2l1`; `PlayerProgressTest` covers the state and the flag in the progress.
