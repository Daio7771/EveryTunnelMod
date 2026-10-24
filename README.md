# Every Tunnel

A singleplayer horror mod for Minecraft 1.20.1 (Fabric, Kotlin). You're the new night
guard of the Noname Complex, an abandoned underground facility, and the first release is
a two-level demo set in its parking garage. Level 1 has nothing in it but you, and it
teaches you to listen. Level 2 adds the Pale man, who is blind and finds you by sound.
Most of the work goes into the sound.

Where it stands: player systems, sound, the generated Level 1 map and its props are done.
The Pale man hunts by sound, and the music follows what he does. A new game starts in the
elevator; dying restarts the level, and walking into the next one closes a shutter behind
you. Batteries and water bottles lie around to be picked up. Level 2 is built, with its keycard, lights out and the end of the demo. Next: playtesting, tuning and the release.

## Running it

You need JDK 17 (the game) and JDK 21 (Gradle; `./gradlew` finds it on its own).
Everything else is downloaded by the build.

```bash
./gradlew build               # jar in build/libs/, runs the unit tests
./gradlew runClientMapWorld   # straight into the map world
./gradlew runClientDevWorld   # straight into the flat test world
./gradlew runClient           # title screen
```

Dev runs live in `run/` (not in git) and always log in as `Dev`, so worlds keep your
player. They also load the optional mods below.

The two worlds have to be created once, by hand: `runClient`, then a Superflat world
with commands allowed, named exactly `noname_complex` (the map) or `dev_flat` (tests).
After that:

- the map: `python3 tools/map/map_generator.py` installs Level 1 into `noname_complex`, and
  the mod rebuilds the level behind its loading screen every time you open the world;
- the test world: copy `examples/dev_flat_datapack/every_tunnel_levels` into
  `run/saves/dev_flat/datapacks/`. Its `level_2` has a Pale man spawn point.

To start every launch from the same point instead of keeping what you did, open the
world, stand where you want to start, run `/everytunnel devworld save` and quit. The
world is copied to `run/dev_templates/`, and each launch of the dev client puts it back
before opening it (the map generator installs into that copy too).
`/everytunnel devworld forget` turns it off. This only exists in the dev environment.

### Optional mods

None of them are required, and players get a hint where it matters.

- LambDynamicLights: draws the flashlight's light. Without it the flashlight works but
  doesn't light anything.
- Sound Physics Remastered: reverb and muffling through walls. The sounds are made dry,
  expecting it.
- Sodium + Iris + Indium: shader packs. It's tuned for Complementary Reimagined: put it in
  `run/shaderpacks/` and set "RP Support" to "labPBR" in its settings, or it ignores the
  material maps. Dev runs pin LWJGL to 3.3.1 because Sodium 0.5 won't start on the 3.3.2
  Loom brings.

GeckoLib (the Pale man's animations) is required, but it's packed inside the mod's jar.
Versions are in `gradle.properties`.

## How the game side works

Everything below only happens in worlds whose datapacks define levels. Any other world
stays vanilla.

**Levels** are areas, not code. Each one is a JSON file in the world's datapack
(`data/every_tunnel/levels/<id>.json`) with zones (block boxes, optionally tagged, e.g.
`exit`, `shutter`), spawn points (tagged `player`, `pale_man`...), an ambience and what
happens when you walk in from the previous level (`entry`). They reload with
`/reload`, and mistakes are logged with the file and the JSON path. The parser
(`core/level/LevelParser.kt`) documents every key, and the generated `level_1.json` is a
full example. Every tick the mod works out which level and zones each player is in, and
other systems listen to that through `world.LevelTriggers`.

**World rules.** In Survival and Adventure you can't break or place blocks, but doors and
buttons still work. Creative is free, for building. Vanilla mob spawning is turned off
with gamerules, and the difficulty is locked on Normal (Peaceful would delete the Pale
man as soon as he spawns). Entering a level makes its start (the spawn point tagged `player`)
your respawn point, so you don't come back on the roof.

**The main menu.** Vanilla's title screen is replaced by Every Tunnel's: black, the logo
and BEGIN / OPTIONS / LANGUAGE / CREDITS / EXIT on the left, the version at the bottom.
BEGIN opens Noname Complex; CREDITS rolls the end credits; OPTIONS and LANGUAGE are
vanilla's. After 15 s without moving the mouse, the Pale man's silhouette slowly appears,
faint, on the right, and vanishes as soon as the mouse moves (`tools/gui/menu_figure.py`
draws it). Its music is the sound event
`music.menu` (silent until the song is added). Ctrl+Shift+V swaps to vanilla's title
screen and back, for other worlds, mods and multiplayer. The logo (`textures/gui/logo.png`,
worn serif capitals in pixel art) comes from `tools/gui/logo.py`. Vanilla's music never
plays, in the menus or in a world.

**Opening the world.** Every time a world with levels opens, one black loading screen
covers everything from the click until the elevator, vanilla's own loading screens and
the resource reload included. It shows a tip in the middle (`loading.every_tunnel.tip.*`
in the language file, a new one every 7 seconds; `{flashlight}`, `{sneak}`, `{sprint}`,
`{jump}` and `{use}` become the player's keys) and "Loading..." below, and the game is
silent. Behind it the mod runs the levels' `build` function (the map's `garage:build`)
and then starts a new game in Survival and Adventure, whether the last one was finished,
halfway or never started; Creative and Spectator players stay where they were. Once the
elevator is drawn, the screen and the sound fade in together, and the intro starts.

**The start.** A new game begins in the elevator with its doors shut while a song plays
(`music.elevator`: Kevin MacLeod's "Local Forecast - Elevator", CC BY 4.0, see below). After 25 seconds the bell rings,
the song distorts and fades like a tape running down, the doors slide open and "Level 1"
shows. It happens once per world and player (the level's `intro`, its start zone tagged
`start` and the door zone tagged `elevator_door`); dying later brings you back to the
elevator with the doors open. `/everytunnel intro` plays it again.

**Dying and moving on.** There's no death screen. When you'd die, you hear it, the screen
fades to black and you can't move; in the dark you're taken back to the start of the level
you were in, with full health and resources, and its Pale men go back to their spawn
points. Then the screen fades back in. You keep what you picked up.

**Level 2's way out.** The EXIT door by hall 3 opens only with your keycard: its reader
buzzes "It needs a keycard" until you have it. The keycard lies on the counter of a glass
booth in hall 5. Picking it up puts out every light still on in Level 2, in a wave
spreading from you. Behind the EXIT door, stairs go down to B3: a few steps down you hear
bare feet coming down behind you, the screen fades, and "End of demo" and the credits
show. Then the game saves and goes back to the title screen; opening the world again
starts a new game, as always. Walking into a level
with a higher `order` shows its title ("Level 2"), plays its sound, and closes
every shutter in its zones tagged `shutter` once you're past it: a rolling shutter rattles
down and slams, and there's no way back.

**The player.** Stamina drains when you sprint or jump. Run it dry and you're exhausted
until it's back to 30%, breathing hard. Thirst replaces hunger (full lasts 20 minutes) and
hurts when it's empty; a water bottle gives back 40 of its 100 if you hold use for 1.5 s,
with a gulp about every half second (a noise for the Pale man). The flashlight is built in
(key F) and runs on a battery; battery items refill it if you hold use for 1.5 s. The HUD
swaps the vanilla bars for four rows: health, stamina, thirst, battery. A new game starts
with nothing but the flashlight, full.

**With and without shaders.** The flashlight's light is block light (LambDynamicLights) in
both. Without a shader pack (no Iris, or Iris with shaders off; checked live), where block
light shows its steps, the light is recomputed at most 5 times a second and only for bigger
changes (fewer chunk rebuilds, no stutter), and the screen goes dark around a round, warmer
spot that sways a little behind the camera and follows the flicker
(`tools/gui/flashlight_spot.py` draws its textures). With shaders the beam is narrower,
brightest in the middle and with less glow around you, so it reads as a flashlight. In the
levels the Brightness option counts as Moody, with or without shaders; the option itself is
not changed.

On start, the mod puts its recommended settings once into every Complementary pack in
`shaderpacks/` (`client/ShaderSettings.kt`): colored lighting off, and world space
reflections off with it (the pack refuses them without it). That mode lights the
world from the pack's own list of glowing blocks, which has none of the mod's, so the
fluorescent lights and the flashlight would light nothing. Changing it back later is kept.

**Loot.** Batteries and water bottles lie on the floor against walls and props, drawn as
the item itself; right-click picks one up. A level places them at its spawn points tagged
`battery` or `water` the first time you enter it, and what you pick up stays picked up,
also after dying. `/everytunnel newgame` puts it all back.

**Noise.** Everything you do makes a noise with a radius: footsteps 2, 6 or 14 (sneaking,
walking, sprinting), jumps, landings, doors and chests, the flashlight, panting. Noises
live for 5 seconds and are what the Pale man will hear. `/everytunnel noise watch` shows
yours as you make them.

**Sound.** Each level has a background loop plus random sounds around you (drips,
rumbles, relays). Fluorescent lights hum, and the four nearest are audible. Every prop
material has its own footsteps and impacts, and puddles splash.

**The Pale man** (`every_tunnel:pale_man`) is about 2.75 blocks tall. Nothing hurts him
except `/kill` and he never despawns. The first time a player enters a level, each
`pale_man` spawn point gets one, and the world remembers it, so he's never doubled and
comes back if he's killed. A spawn point can name a `zone`: then he never leaves it and
ignores every noise and player outside it. You can test him with the spawn egg in the
Every Tunnel tab.

He hears every noise within its radius, and each solid block in between shrinks that
radius (`wall_muffle`). He wanders until he hears something. A quiet noise makes him stop
and listen for 2 s, then walk to where it came from; a second faint one while he listens
sends him walking there at once. A loud one (sprinting within about 7 blocks), or a
second one at least half that loud while he listens, makes him run at it, and you hear
the "he heard you" stinger. While chasing, each new noise moves his target, so if you stop
making noise he reaches the last place he heard you, searches for 4 s (any noise there
and he charges again) and goes back to wandering. He hits you if you're within 2 blocks
while he hunts, or bump into him at any time. By default one hit kills. Within 24 blocks of him you hear your heartbeat, from 60 bpm
up to 140 right next to him, and never under 120 while he's chasing.

You hear him too: his bare feet (walking up to 16 blocks away, running up to 24) and his
breathing within 8 blocks. He holds his breath while he listens or searches. Surviving
his hit leaves your ears ringing.

The music comes from what he does (`client/effects/TensionMusic.kt`): silence while he's
more than 48 blocks away, a low pulse while he wanders nearer, a rising tone while he
walks to a noise, dissonant strings within 12 blocks, and the chase music while he
chases. It crossfades, and a chase fades out without telling you he lost you. It's in
the Music volume slider, and vanilla's music is off in level worlds.

All the numbers above (stamina, thirst, battery, flashlight, noise radii, the Pale man's
hearing, speeds and attack) are in
`run/config/every_tunnel/gameplay.json`. It's created with defaults and read at startup.
Bad values fall back to their default with a warning.

## The tools (Python)

Almost all the content is generated, so it can be redone from parameters. All of it needs
Python 3 with Pillow, and the sounds also need numpy, scipy and the system's libsndfile.
Previews go into each tool's `output/` folder, which isn't in git. Whatever ends up in
`src/` gets committed.

`tools/map/map_generator.py` builds both levels. Level 1 (B1): an elevator, five parking
halls that get worse as you go (wear, dead lights, oddities like stairs into the ceiling),
side rooms and a ramp down. A shutter closes at the foot of that ramp behind you; the dark
landing past it is where Level 2 starts. Level 2 (B2, `LEVEL_2`) continues through a
doorway under the landing's "B2" sign: five darker, more worn halls, one Pale man in each
and two in the bigger last one, each kept in his hall. Your keycard lies in a glass booth
in hall 5; the way out is the EXIT door with a card reader by hall 3, stairs down to B3,
where the demo ends. The parameters are at the top of the file and the same seed gives
the same level. `LOOT` lists the batteries and water bottles and the zone each lies in
(two of each per level, mostly in side rooms). Level 1 has no Pale man; `PALE_MAN_HALLS`
can put one in chosen halls for testing. Rebuilding the level removes the Pale men in it.
It writes two datapacks:

- the mod one, using the mod's blocks;
- a vanilla one, using re-skinned vanilla blocks.

The halls carry painted signage: white arrows down the aisle towards each hall's way out,
yellow keep-clear hatching edged in red in front of exits and doors, pillar bases in black
and yellow stripes, a number on the wall behind every stall, and the level ("B1", "B2")
painted big on the walls at both ends of the aisle. It also writes a world resource pack
(`resources.zip`) with the textures (cold, worn concrete, see `garage/textures.py`) and
their LabPBR maps, and installs it all into `noname_complex`. In game, the mod rebuilds the
level every time the world opens, behind its loading screen, then starts a new game. The
map is bigger than what is loaded around a player, so
building force-loads its chunks first, waits until they are loaded, then builds. Building
turns off block drops and clears loose items, removes the Pale men and then tells the mod
(`/everytunnel rebuilt`), which puts all the loot back. `/function garage:build` rebuilds
by hand.

`tools/blocks/generate.py` makes the models, textures, LabPBR maps, names and loot tables
of the mod's blocks: the fluorescent light and the props (boxes, cones, extinguishers,
cart, pallets, drums, bins, wheel stops, pipes, ducts, cable trays, cables, stains,
puddles, sign tiles); the doors (grey steel service doors with a window and a plate for
their room, the emergency exit door with a push bar) and the lit green EXIT sign; the
service elevator's checker plate, moving blankets, handrail, button panels and floor
indicators (the intro counts them down 3, 2, 1, G, B1); and the parking booth's CCTV
monitor (animated static), phone, logbook, chair and key rack. The blocks themselves are registered in `platform/ModBlocks.kt`, and
the tool stops if the two lists of ids disagree.

`tools/entity/pale_man.py` makes the Pale man: GeckoLib geometry, animations (idle,
listen, walk, run, attack), the texture and the glowmask for his eyes. The model is based
on `tools/entity/reference/pale_man_concept.png`, which is AI concept art used only as
reference. The tool also writes a painting template for repainting the texture by hand.

`tools/sounds/` synthesizes the sounds (`catalog.py`, one function per sound) and cuts the
CC0 recordings (`cc0/`, with sources and licences in `SOURCES.md`; CC0 only), plus the one
credited exception, the elevator song (`licensed/`, credited in `CREDITS.md`). `generate.py`
renders previews with a listening page. `export.py` writes the `.ogg` files and
`sounds.json` into the mod, rewriting only the ones that changed. Any sound can be tried
with `/playsound every_tunnel:<id> master @s`.

## Code layout

Common code is in `src/main/kotlin/daio7771/everytunnel/`, client-only code in
`src/client/`. Loom keeps the two apart, so the server can't reach client classes by
accident.

- `config/`, `core/`, `systems/`, `entities/`: plain Kotlin with no Minecraft types
  (config, levels, player tracking, stamina/thirst/battery, noise, enemy AI). This is what
  the unit tests cover.
- `platform/`: everything that touches Minecraft: blocks, the entity, hooks, packets,
  commands. A port to another version should mostly stay in here.
- `world/`: world rules and level triggers.
- `client/`: HUD, flashlight effect, ambience and hum players, the Pale man's renderer.

## Debug commands

These need commands enabled.

- `/everytunnel levels`: loaded levels, zones, spawn points.
- `/everytunnel where`: your level and zones, and whether the rules are active.
- `/everytunnel resources [set stamina|thirst|battery <value>]`: read or set them.
- `/everytunnel noise [watch]`: recent noises, or yours live in the action bar.
- `/everytunnel paleman`: nearby Pale men, what each is doing and where he's going.
- `/everytunnel intro`: the elevator intro again (in Survival or Adventure).
- `/everytunnel newgame`: start over in the same world: shutters open, all the loot back,
  every Pale man at his spawn point, empty inventory, everything full, Survival, and the
  elevator intro.
- `/everytunnel devworld save|forget`: dev runs only, see "Running it".

## Credits

"Local Forecast - Elevator" Kevin MacLeod (incompetech.com)
Licensed under Creative Commons: By Attribution 4.0 License
http://creativecommons.org/licenses/by/4.0/

Everything else is the project's own (MIT) or CC0; see `CREDITS.md`.
