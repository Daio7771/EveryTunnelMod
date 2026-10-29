# Debugging

*[Versión en español](../es/debugging.md) · [All pages](README.md)*

## Debug commands

`platform.DebugCommands`, operator level 2 (in singleplayer: "Allow Commands").

| Command | What it does |
|---|---|
| `/everytunnel levels` | Loaded levels with their zones and spawn points |
| `/everytunnel where` | The level and zones you stand in, and whether the world rules apply |
| `/everytunnel resources` | Your stamina, thirst and battery |
| `/everytunnel resources set stamina\|thirst\|battery <value>` | Changes one (battery in seconds of light) |
| `/everytunnel noise` | The noises of the last 5 seconds near you |
| `/everytunnel noise watch` | Toggles showing each noise you make in the action bar |
| `/everytunnel paleman` | The Pale men near you: state and target |
| `/everytunnel intro` | Plays the elevator intro again (Survival or Adventure) |
| `/everytunnel newgame` | Starts the game over in this world |
| `/everytunnel setlevel <number>` | Takes you to the start of level 1, 2, 3... (its `order`), as if you walked in (its title, its enemies); puzzles stay as they are |
| `/everytunnel valves` | This game's valve digits for Level 3 (A to E) |
| `/everytunnel boxes` | Level 3's box puzzle: how many are on the shelf, where the others lie |
| `/everytunnel books` | Level 3's book puzzle: how many are on the counter, where the others lie |
| `/everytunnel books fill` | Finishes the boxes and puts the books out with all but one already on the desk (to test the last) |
| `/everytunnel boxes fill` | Fills the shelf but for one place and leaves one box lying about (to test the last) |
| `/everytunnel rebuilt` | Run by `garage:build` at its end: puts the loot back. Not for hand use |
| `/everytunnel devworld save\|forget` | Dev runs only, see [Building](building-and-release.md#dev-worlds) |

Useful vanilla commands:

- `/function garage:build`: rebuild the map by hand.
- `/playsound every_tunnel:<id> master @s`: try any sound.
- `/summon every_tunnel:pale_man`, or the spawn egg in the Every Tunnel tab.
- `/give @s every_tunnel:<block or item>`: any mod block or item.
- `/gamemode creative`: free from the world rules, to build or look around.

## The playtest log

`platform.PlaytestLog` writes a line starting with `[playtest]` for everything that matters
when tuning, so a whole playthrough can be read back:

```bash
grep '\[playtest\]' run/logs/latest.log
```

- **At the start of a run:** the gameplay config in use and what each level holds (loot
  spots, Pale men).
- **Every 30 s of play:** thirst, stamina, battery, health, inventory, position.
- **As it happens:** levels entered, loot picked up, drinks, battery changes and the battery
  dying, exhaustion and recovery, thirst low and empty, Pale men hearing the player and
  hitting them, deaths and their cause.
- **The Pale men's paths:** a path that does not reach its target (once per state), with
  where it ends and which blocks are in the way from floor to head; why each walk or chase
  towards a noise ends.
- **A summary per level** at the end of the demo, or when the player leaves or starts a new
  game halfway: time, deaths by cause, hits, loot, drinks, batteries, flashlight time,
  exhaustion, what was left.

Times are play time in server ticks, so a paused game does not count. Runs live in memory
only: reopening the world starts a new one.

## Other log lines

- Every zone change of every player (`LevelTrackingHooks`), with the zone ids.
- Every Pale man state change, with his position, target and the loudest noise.
- Every Observer state change (`Observer … PATROL -> CHASE`), whom he caught or saw get into a
  locker, and how fast each run really was: `[observer] ran 64.0 blocks in 9.8 s: 6.5 blocks/s
  (ALERT_RUN)`, to tune `ObserverSettings`.
- `[sprint] held but not sprinting at (x, y, z): …` (`client.SprintCheck`): while the sprint
  key is held walking forwards and the player does not sprint, every 2 s, what stands in the
  way (exhausted, carrying a box or book, hungry, using an item, crouching, bumping into
  something, in the air, blind, riding).
- Config and level file problems, with the JSON path.

## Unit tests

`./gradlew test` (also part of `build`). 55 tests in `src/test/kotlin/`, covering the plain
Kotlin layers:

| Test | Covers |
|---|---|
| `GameplayConfigParserTest` | Defaults, bad values, unknown and missing keys |
| `LevelParserTest` | Every key, broken entries skipped, errors with paths |
| `PlayerLocationTrackerTest` | Levels and zones entered and left |
| `PaleManBrainTest` | Every state change, loudness, walls, attacks |
| `NoiseSystemTest` | Radii, memory, audibility, steps, landings |
| `ResourceSystemTest` | Drain, regeneration, exhaustion, thirst damage, battery |
| `FlashlightBeamTest` | The beam's light at points in and out of the cone |
| `GuideMarkdownTest` | The guide's Markdown subset, and that every page in `docs/*/guide/` follows it in both languages |

Anything that touches Minecraft is tested in game.

## Common problems

| Symptom | Likely cause |
|---|---|
| `runClientMapWorld` fails at once | `run/saves/noname_complex` missing, or no level datapack: run the map generator |
| Quick play stays on the menu | The world name does not exist |
| No HUD, no flashlight | The world has no level files (check `/everytunnel levels`), or you are in Creative/Spectator |
| The flashlight lights nothing | LambDynamicLights missing, or Complementary's colored lighting turned back on |
| No Pale man in Level 2 | He spawns on the first entry into the level; if he was `/kill`ed, he comes back on the next entry |
| The Pale man stands still in a chase | His path falls short (he needs 3 free blocks of height): look for path lines in the `[playtest]` log |
| A level file is ignored | Look for its problems in the log; `order` and one valid zone are required |
