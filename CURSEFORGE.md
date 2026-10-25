# CurseForge page

The text for Every Tunnel's CurseForge project page, kept here so it is versioned with the mod.
The part between the two lines is the description: paste it into the project's description
editor in **Markdown** mode. Keep it in step with [README.md](README.md).

The repository is private, so the description links to nothing in it: everything a player
needs is in the text itself.

## Upload checklist

- **Summary** (the one line under the name): `Sound-driven horror demo. Don't make a noise.`
- **Logo / project icon:** `docs/media/logo.png`. Images in the description must be uploaded
  to the project's **Images** tab first and linked by their CurseForge URL.
- **Game version:** 1.20.1. **Mod loader:** Fabric. **Java:** 17.
- **Relations:** no required dependencies (Fabric API, Fabric Language Kotlin, GeckoLib and
  LambDynamicLights are embedded). Optional: Sound Physics Remastered, Sodium, Iris, Indium.
- **License:** MIT, with the CC BY 4.0 music credit in the description (it must stay there).
- **File name and version:** to decide (open release question).
- **Changelog for the file:** the first public demo, Levels 1 and 2.

---

# Every Tunnel

**A singleplayer psychological horror mod for Minecraft 1.20.1 (Fabric).**
*Demo: Levels 1 and 2.*

You are the new night-shift security guard of the **Noname Complex**, an abandoned
underground facility. Your shift starts in the service elevator and goes down through its
parking garage.

**Level 1** is empty. It teaches you to listen. **Level 2** adds the **Pale man**: tall,
blind, and hunting by sound. Everything you do makes noise, and every noise tells him where
you were.

## Features

- **Sound-driven horror.** Footsteps, jumps, landings, doors, your flashlight's click, even
  drinking: every action makes a noise with its own range. Walls muffle it. Sneak, and he
  may never know you were there; sprint, and he comes running.
- **A blind hunter.** The Pale man stops and listens, walks to what he heard, runs at loud
  noises, and searches where he lost you. You hear him too: his bare feet, his breathing,
  and the silence when he holds his breath to listen.
- **Your body is the warning system.** Your heartbeat speeds up as he gets closer, and the
  music follows what he is doing.
- **Survival on a budget.** Stamina, thirst and a flashlight battery, with a few batteries
  and water bottles left lying around by previous guards.
- **A complete demo.** Its own main menu, an elevator intro, two levels, an ending and
  credits. Every time you open the world, a new shift starts.
- **An in-game guide** (main menu, GUIDE), in English and Spanish.
- **Looks right with or without shaders.** Tuned for Complementary Reimagined, with material
  maps for every texture. Plays fine on plain Minecraft too.

## Requirements

- **Minecraft 1.20.1** with **Fabric Loader 0.19.5** or newer.
- Nothing else. Fabric API, Fabric Language Kotlin, GeckoLib and LambDynamicLights come
  inside the mod's file, and so does the map. Don't install them separately.

## Installing

1. Install Fabric Loader for Minecraft 1.20.1 (or create a Fabric 1.20.1 profile in your
   launcher).
2. Put the Every Tunnel `.jar` in the `mods` folder.
3. Start the game and press **BEGIN**. New to the game? Open **GUIDE** first.

The map appears in your worlds as **Noname Complex**. Opening it always starts a new shift.

## Recommended mods

Not required, but they make a big difference:

- **Sound Physics Remastered**: echoes in the garage and sounds muffled behind walls. The
  game's sounds are made for it.
- **Sodium + Iris + Indium** (Sodium 0.5.x, Iris 1.7.x) with the **Complementary
  Reimagined** shader pack. In the pack's settings, set *RP Support* to *labPBR*. Every
  Tunnel turns Complementary's *colored lighting* off on its first start: leave it off, or
  the lamps and your flashlight light nothing.

Play with headphones, and keep the Music volume on: the music tells you things.

## Good to know

- **Singleplayer**, Fabric only, Minecraft 1.20.1 only.
- **This is a demo** of the first two levels. More of the Noname Complex is planned.
- In the levels, Brightness always counts as "Moody". The dark is part of the game.
- The mod replaces the title screen with its own menu. **Ctrl+Shift+V** switches to
  Minecraft's title screen and back (other worlds, multiplayer).
- The mod only changes worlds that contain its levels. Your other worlds stay vanilla.

## Credits

Made by **Daio7771**: design, code, sound and art. MIT license. The sounds are synthesized
for the game or come from CC0 recordings.

Music: "Local Forecast - Elevator" Kevin MacLeod (incompetech.com)
Licensed under Creative Commons: By Attribution 4.0 License
http://creativecommons.org/licenses/by/4.0/

---
