# Contributing

*[Versión en español](../es/contributing.md) · [All pages](README.md)*

How work is done on Every Tunnel. These rules apply to people and to AI assistants alike;
the [last section](#notes-for-ai-assistants) adds what an assistant needs to know.

## Language

- **Code, comments, commit messages, in-game text: English.**
- **Docs:** English and Spanish, the same pages in `docs/en/` and `docs/es/`. Change both in
  the same commit.
- The game is in English (`en_us.json`, the reference) and Spanish from Spain (`es_es.json`,
  also copied to the other `es_*` locales by `tools/lang/spanish_variants.py`). The painted
  wall messages (`wall.every_tunnel.*`) stay in English on purpose. The in-game guide and the
  notes are in both.

## Public pages

`README.md` (GitHub), `INSTALL.md` and `CURSEFORGE.md` (the CurseForge description) are what
players see. They are in English only, direct in tone, and keep everything technical out:
that belongs in `docs/`. The CurseForge description repeats what a player needs and links to
GitHub with full URLs (relative links do not work there). When a player-facing fact changes
(requirements, features, recommended mods, controls), update all three. They hold no notes
for maintainers (how to publish, checklists): those go in `docs/`, such as
[Publishing on CurseForge](building-and-release.md#publishing-on-curseforge).

## Code

- **Kotlin** for everything; Java only for mixins.
- **Logic in plain Kotlin, glue in `platform`.** Anything that can be expressed without
  Minecraft types (rules, decisions, parsing, maths) goes into `config`, `core`, `systems`
  or `entities`, with unit tests. `platform` and the client feed it and apply its result.
- **Robust and clear over short.** Long files are fine. Prefer explicit names and a
  readable flow to clever one-liners.
- **Every file starts with a KDoc** saying what it does, how it fits and where its data
  comes from. Public constants and non-obvious choices get a comment saying *why*.
- **Never crash on input.** Config and level files report problems with their JSON path
  and fall back to defaults (`util.JsonFields`).
- **Vanilla worlds stay vanilla.** Gate every feature on `WorldRules.active` /
  `WorldRules.appliesTo(...)`.
- **Mixins only where Fabric API has no event.** Each one documents its target and reason.
- **Server thread** for game state; hand packets to the client thread with
  `client.execute`.
- **Generated content stays generated.** Change the Python tool and rerun it; never edit
  generated JSON, PNG or OGG files by hand.

## Creatures

Every creature of the mod (every entity) follows one of three life cycles. Choose the one that
fits before writing its hooks, and say which in the KDoc of its file.

| Life cycle | Creatures | How it is put in and taken out |
|---|---|---|
| **Posted**: saved with the world, one per spawn point | Pale man, Observer | Spawned by its hooks (`PaleManSpawner`, `ObserverHooks`) the first time a player walks into its level. The spawn points already filled are recorded in `data/every_tunnel_<name>.dat` ("level/point" keys); `homeKey` and `newGamesSeen` are in its NBT |
| **Kept**: never saved, made again whenever its level is built | Hunter, Distortion Cloud and Junior, Subject, dead guard, director, lab figure | Its type disables saving. It is put in only through `KeptEntities.spawn`, which brings it back if its chunk unloads, and taken out with `KeptEntities.forget` plus `discard()` when the level is put back |
| **Passing**: lives for a moment | Dart, locker seat, film walker | Spawned and discarded by whatever needs it. Nothing is recorded |

- **Never clean a creature up with `/kill`.** On a living thing `/kill` is damage, not removal:
  the creature plays its death for 20 ticks and only then leaves the world. For that second it
  still stands there and its spawn point still looks filled, so a hook that runs right after the
  `kill` spawns nothing, and once he is gone nobody spawns him again. This left Level 3 without
  its Observer after CONTINUE. Discard from Kotlin: `entity.discard()` removes it at once. (The
  `kill` lines in the map generator are for worlds without the mod; the mod discards by itself,
  see `ObserverHooks.levelsRebuilt`.)
- **A rebuild of the map is a reset.** `NewGame.levelsRebuilt` is where each kind of creature
  gets rid of what the build left and puts back what its level needs; `NewGame.resetWorld` does
  the same for a new game and for CONTINUE. A new kind of creature has to be wired into both
  where it applies, or it will keep stale ones or have none.
- **A spawn point is forgotten once, and only by its owner.** A posted creature that is removed
  frees its point, but a second removal (the dying one, after its replacement was spawned) or a
  leftover loaded late from its chunk must not free the point of the one that replaced it.
  `core.spawn.PostLedger` keeps who owns each point (`ObserverHooks.claim` and `forget`).
  `PaleManSpawner` does not use it yet: it spawns again on the next level change.
- **A creature's state lives in the creature.** What it is doing (patrol, chase, flags) is saved
  in its own NBT (`writeCustomDataToNbt`). The world's `.dat` files only record what no single
  entity can: which posts are filled, the new-game count. Do not keep one in the other.
- **Read the log.** A posted creature logs when it is spawned ("Observer spawned at ..."); after
  a CONTINUE, no such line means it is missing.

## Assets and licences

- Everything own (code, tools, assets, story, docs): all rights reserved (`LICENSE.txt`),
  with videos, streams and modpacks allowed. Versions up to 0.2.0 were MIT.
- Bundle a library in the jar only if its licence allows sharing it (LambDynamicLights'
  does not); list each bundled one in `CREDITS.md`.
- Recordings: **CC0 only**, with their source in `tools/sounds/cc0/SOURCES.md`.
- Anything needing credit (only the elevator song today) goes into
  `tools/sounds/licensed/`, `CREDITS.md`, `fabric.mod.json` and the end credits.
- **No AI-generated music or assets in the game.** AI concept art may be used as a
  reference only (`tools/entity/reference/`), never shipped. The one exception is the
  director's voice in Level 5: ElevenLabs library voices (not cloned), generated by the
  user (see [Audio](audio.md)). Music and every other sound stay non-AI.

## Testing

- `./gradlew build` must pass (it runs the unit tests).
- Changes that touch Minecraft are checked **in game**. Write down clear test steps: what to
  do and what should happen.
- For tuning, use the [`[playtest]` log](debugging.md#the-playtest-log).

## Commits

- One commit per finished step, in English, prefixed with the phase:
  `Phase 7: main menu theme`.
- Commit and push only when asked.

## Player guide pages

The pages in `docs/<lang>/guide/` are drawn by the game (main menu, GUIDE; the build copies
them into the jar, `client.screen.GuideScreen` draws them), so they use a
small Markdown subset that reads well both on GitHub and in game:

- `# Title`: one per page, the first line.
- `## Section` and `### Subsection`.
- Paragraphs, separated by a blank line. Lines inside a paragraph are joined.
- Lists with `- ` (one level), numbered lists with `1. `.
- `**bold**` and `*italic*`.
- `---` for a separator line.

Nothing else: no links, tables, images, code or HTML (`GuideMarkdownTest` fails the build
if a page uses them, or if the two languages do not have the same files). Name keys with their default and the
setting ("the Flashlight key, F by default"), since players can rebind them.

Rules for the content:

- Written for players: short, friendly, a little dry humour is fine.
- **No lore.** The guide explains how to play, not the story.
- Hints, not numbers, except on the *Technical data* page.
- Never spoil what the Pale man does beyond what a player needs to survive.

## Notes for AI assistants

- **Start here:** read [Architecture](architecture.md) and the KDoc header of each file you
  touch. The headers are kept accurate; trust them over memory.
- **Ask before deciding.** Before each step, ask the user a batched round of design
  questions. Never decide alone what the brief does not fix (design, wording, tuning,
  scope). The user likes thorough rounds.
- **Reply in Spanish** to the user; code and in-game text stay in English.
- **Never launch the Minecraft client.** Build and run the tests, then give the user test
  steps; the user tests in game.
- **Commit and push only when the user says so.** The remote is SSH, so pushing works from
  a session.
- **Send artifacts** (audio drafts, builds, previews) to the user for checking.
- **Keep token use reasonable:** no unnecessary exploration, no subagents unless asked.
- `PROJECT_STATE.md` at the repository root (not in git) is the hand-over between
  sessions: progress, open questions, decisions. Update it when asked.
- When a change makes a doc page wrong, fix the page (both languages) in the same step.
