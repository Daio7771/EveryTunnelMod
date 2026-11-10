# Audio

*[Versión en español](../es/audio.md) · [All pages](README.md)*

Sound design is the core of Every Tunnel. The player survives by listening: to the
Pale man's feet and breath, to their own heartbeat, to how loud their own steps are.

## Rules

- **Synthesized or CC0.** Almost every sound is synthesized in Python
  (`tools/sounds/catalog.py`). A few are cut from CC0 recordings (`tools/sounds/cc0/`,
  sources in `SOURCES.md`).
- **One credited exception:** the elevator song, Kevin MacLeod's "Local Forecast –
  Elevator" (CC BY 4.0). Its credit must travel with the mod (`CREDITS.md`, the jar, the
  end credits, `fabric.mod.json`).
- **No AI music.** Free AI music services only allow non-commercial use.
- **Dry files.** Sounds have no reverb: Sound Physics Remastered adds reverb and muffling
  through walls in game. Dev runs always include it, so sounds are judged with it.
- **Seeded.** Every generated file comes from a seed derived from its id and variation, so
  the same code always gives the same sound.

## Sound events

Every event is declared in `src/main/resources/assets/every_tunnel/sounds.json`, generated
by `tools/sounds/export.py` together with the `.ogg` files (folders follow the id:
`item.flashlight.on` → `sounds/item/flashlight/on_1.ogg`). Events that code plays are also
registered in `platform.ModSounds`. Any of them can be tried with
`/playsound every_tunnel:<id> master @s`.

| Group | Events | Source |
|---|---|---|
| Ambience | `ambient.garage.drone` (loop), `ambient.drip`, `ambient.relay`, `ambient.rumble`, `ambient.sparks`, `ambient.vent` | synth |
| Level 3 ambience | `ambient.offices.room_tone` (loop), `ambient.library.clock` (loop), `ambient.offices.phone` (2-4 rings) | synth |
| Fluorescent lights | `block.fluorescent.hum` (loop), `.flicker`, `.start`, `.off` | synth |
| Materials (prop steps and impacts) | `block.cardboard`, `block.hollow_metal`, `block.metal_pipe`, `block.plastic`, `block.puddle`, `block.sheet_metal`, `block.wire_mesh`, `block.wood`, each `.step` and `.impact` | synth |
| Map events | `block.projector.run`, `block.elevator.ding`, `block.elevator.doors`, `block.shutter.close`, `block.card_reader.accept`, `block.card_reader.deny`, `block.valve.turn`, `block.valve.release`, `block.locker.door`, `block.hatch.open` | synth |
| Flashlight and battery | `item.flashlight.on`, `.off`, `.dead`, `.flicker`, `item.battery.open`, `.close` | synth |
| Player | `player.heartbeat` (4 variations), `player.tinnitus`, `player.breathing_exhausted`, `player.drink` | synth / CC0 (breathing, gulps) |
| The Pale man | `entity.pale_man.step`, `.step_run`, `entity.pale_man.breathing` | synth / CC0 (breathing) |
| The Observer | `music.observer.approach`, `music.observer.chase` (no footsteps: his music gives him away); in the book phase `event.observer.tick` / `tock` (the library clock in the head, quicker as he nears) | synth |
| Music | `music.menu`, `music.elevator`, `music.tension.pulse`, `.rising`, `.strings`, `music.chase`, `music.stinger.heard` | synth / CC BY (elevator) |
| Events | `event.death`, `event.death.reveal` (the death screen's words coming up), `event.level_transition`, `event.observer.alert` (placeholder), `event.collapse` (Level 3's tunnel collapse) | synth |
| Level 5 | `block.airlock.close`, `block.airlock.open` (the airlock's and the locked doors), `item.wrench.hit` (3 variations), `item.detector.click`, `.clicks`, `.crackle` (the detector, a Geiger counter: one count, two to four, six to twelve in a tick; 6 variations each), `event.lab.unlock` (the south route opening, far off), `block.fuse.insert`, `block.fuse.reject`, `block.fuse.power` (the fuse box), `block.breaker.dead` (the stairs' lever without power), `block.mixer.fill`, `block.mixer.run`, `block.grate.dissolve`, `event.lab.alarm` (the chemical puzzle), `event.lab.sting` (into the Lab hall), `block.terminal.key` (the computers) | synth |
| Level 5's lab | `ambient.lab.air` (loop: the air handling, ducts rushing softly, a fan, a low rumble), `ambient.lab.compressor` (a fridge's compressor off somewhere, starting or stopping), `ambient.lab.duct` (a sheet-steel duct popping and ticking, 4 variations); in layer 1's zones from the Lobby on (`ZONE_AMBIENCE` in `LEVEL_5`). Its lights hum by themselves (`block.fluorescent.hum`) | synth |
| Level 5's drain | `ambient.drain.water` (loop: water down the channels, gurgles, a low rush), `ambient.drain.pumps` (loop: the Pump room's pumps, loud), `ambient.drain.creak` (a pipe creaking far off, metal on metal, 4 variations), and the garage's `ambient.drip`; by zone (`ZONE_AMBIENCE` in `LEVEL_5`). The hatch shutting over the player is vanilla's iron trapdoor at pitch 0.6 | synth / vanilla |
| Level 5's Hunters | `entity.hunter.shriek` (a chase starts), `.attack`, `.death`, `.ambient` (3 variations each) | synth |
| Level 5's Distortions | `entity.distortion_cloud.whisper` (loop), `.appear`, `.kill`, `.leave`; `entity.distortion_junior.whisper` (loop), `.stare` (loop, its pitch raised by the game), `.die`. The whispers are breath through moving vowel formants, no voice | synth |

## Who plays what

Server side (positioned in the world, heard by everyone near):

- Block sounds: prop materials (`platform.ModSoundGroups`), lights changing mode, doors,
  shutters, card readers, locker doors (`LockerSeatEntity`, `HidingHooks`).
- The Pale man's steps, breathing, stinger and the tinnitus (`PaleManEntity`).
- Flashlight and battery sounds at the player (`FlashlightHooks`), gulps (`DrinkHooks`).
- The death sound (`DeathHooks`).
- Level 4's jazz, out of its ceiling speakers (`client.effects.LoungeMusic`, `LoungeMusicHooks`, `core.audio.LoungeMix`): one sound at the nearest speaker, silent in Maintenance and during the film, low in the Suites, muffled in the Calm room, its pitch sliding between 0.6 and 1.2 in the blackout. The source is `tools/sounds/own/`.

Client side (`client.effects`), per player and not positioned unless noted:

| Class | What it plays | Volume slider |
|---|---|---|
| `AmbiencePlayer` | The level's (or zone's) loop (fades over changes) and its random sounds at random spots around the player (positioned) | Ambient/Environment |
| `FluorescentHum` | The hum of the 4 nearest lights within 10 blocks, each at a slightly different pitch (positioned) | Blocks |
| `HeartbeatEffect` | One heartbeat at a time near the Pale man | Players |
| `DetectorEffect` | Level 5's detector clicking like a Geiger counter, more counts as a Cloud nears, up to a crackle (in the head) | Players |
| `DistortionEffect` | The Distortions' whisper loops, following them; the Cloud's louder as it is looked at, the Junior's stare hum higher as it is stared at | Hostile |
| `BreathingEffect` | Heavy breathing when exhausted | Players |
| `TensionMusic` | The Pale man's music layers, and the Observer's (walking your way, running at you), crossfaded | Music |
| `IntroMusic` | The elevator song, then its tape-stop fade | Music |
| `MenuMusic` | The menu theme, then 15 s of silence, only in menus | Music |

The ambience is sent by the server (`platform.AmbienceHooks`) whenever the player's changes,
the level's or a zone's own (Level 3: the library's clock, the bunker's silence), and after
`/reload`; the client does the looping and random placement, which needs
no server work. Random sounds inside walls are fine: Sound Physics muffles them, which
sounds like something behind the wall.

Vanilla music never plays (`MusicTrackerMixin`). Behind the loading screen everything is
silent and fades in with the picture (`SoundSystemMixin`).

## The menu theme

`music.menu`, synthesized by `menu_piano.py` (composed by the user; `menu_theme` in
`catalog.py` calls it): about 70 s of slow, uneasy felt piano in 3/4 at 54 bpm, with rubato
and hesitations, two passes over Dmaj7 - Dadd9 - Bbmaj7#11 - Gmaj7 and an unresolved end on
D maj7 with E flat on top. It keeps its own seed (7), so it matches the composer's original.
It is not a loop: `MenuMusic` plays it once, waits 15 s in silence and plays it again.
Leaving a world reloads the resources, which restarts the sound engine and cuts it; then it
starts again at once (`SoundSystemMixin` tells `MenuMusic`).
Exported with its peak at -2.9 dB, about -16.8 LUFS like the earlier theme.

## Making and changing sounds

See [Python tools](tools.md#sounds) for the pipeline and
[How-to recipes](how-to.md#add-a-sound) for adding one.
