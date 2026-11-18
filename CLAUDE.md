# Every Tunnel

Singleplayer psychological horror mod for Minecraft 1.20.1 (Fabric, Kotlin). The player is the
night guard of the abandoned Noname Complex; the first release is a two-level demo in its
parking garage (Level 2 adds the Pale man, a blind creature who hunts by sound). Sound design
is the core of the mod.

## Read first

1. `PROJECT_STATE.md` (repository root, not in git): where the project stands, what is next,
   open questions. If it is missing, ask the user.
2. `docs/en/contributing.md`: conventions and the notes for AI assistants.
3. `docs/en/architecture.md`, then the page of the area you work on (`docs/en/README.md` lists
   them all). The KDoc header at the top of each source file is the most precise description
   of that file.

## How to work with this user

- **Reply in Spanish.** Code, comments, commit messages and in-game text stay in English.
- **Ask before each step:** one batched round of design questions (the user likes thorough
  rounds). Never decide alone what the brief does not fix: design, wording, tuning, scope.
- **Never launch the Minecraft client.** Verify with `./gradlew build` (it runs the unit
  tests), then give the user clear test steps; the user tests in game.
- **Commit and push only when the user says so** ("commit y push"). One commit per finished
  step, in English, prefixed `Phase N: ...`. The remote is SSH, so pushing works from here.
- Send previews, audio drafts and builds to the user with SendUserFile.
- **Keep token use low** (Pro plan, weekly quota): no unnecessary exploration, short replies, few progress messages.
  - **One session per phase at most.** End it with a short hand-off in memory; above ~150k tokens of context, hand off or compact.
  - **Models:** the main session runs `opusplan` (Opus plans and designs, Sonnet executes). Delegate only to the user's agents
    for this project in `~/.claude/agents/` (`et-mechanic` and `et-docs-writer` on Sonnet, `et-hard-problems` on Opus for a main
    session on Sonnet),
    with a precise brief (files, exact changes, what done is); never a vague one, never for a one-line change.
  - **Docs per phase:** fix only what the phase makes wrong, briefly; the level's full EN/ES page is rewritten when the level closes.
- Update `PROJECT_STATE.md` when the user asks (usually before changing sessions).

## Rules of the codebase

- Logic in plain Kotlin (`config`, `core`, `systems`, `entities`, unit tested); Minecraft glue
  in `platform` and the client. Mixins (Java) only where Fabric API has no event.
- Every feature only acts in worlds with levels (`WorldRules`); vanilla worlds stay vanilla.
- **Chapters stay apart.** Chapter 1 and the shared code are the base game; a later chapter lives in
  `daio7771.everytunnel.chapterN` (one folder per level) with its own entrypoints, registries, progress,
  lang part (`tools/lang/src/chapterN/`) and sound catalog. The base game never names a chapter
  (`ChapterBoundaryTest`): use its extension points (`docs/en/architecture.md`, Chapters).
- Generated content (sounds, textures, models, the map) comes from the Python tools in
  `tools/`: change the tool and rerun it, never edit its output by hand.
- Sounds: synthesized or CC0 only (CC0 SoundFonts too, fetched by `tools/sounds/soundfonts/fetch.py`), plus the one credited CC BY song. The one AI exception is AI
  voices (ElevenLabs library voices, files from the user: the director's in Level 5, the scientist's
  in Chapter 2's prologue, and any further character voice). No other AI audio or assets (nothing from a generator service; music and sounds written and
  synthesized in code by the Python tools are fine).
- **Docs are bilingual:** `docs/en/` and `docs/es/` hold the same pages. When a change makes a
  page wrong, fix it in both languages in the same step. The player guide pages
  (`docs/*/guide/`) are shown in game and use a small Markdown subset (see contributing).
- **Public pages** (`README.md`, `INSTALL.md`, `CURSEFORGE.md`): English only, for players;
  keep the three in step when a player-facing fact changes. No maintainer notes in them
  (those go in `docs/`).
