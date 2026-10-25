# Installing Every Tunnel

Every Tunnel is one file. It carries the map and every library it needs, so the only thing to
install before it is Fabric Loader.

- [1. Install Fabric Loader](#1-install-fabric-loader)
- [2. Add Every Tunnel](#2-add-every-tunnel)
- [3. Play](#3-play)
- [Recommended mods](#recommended-mods)
- [Shaders](#shaders)
- [Settings](#settings)
- [Updating and uninstalling](#updating-and-uninstalling)
- [Troubleshooting](#troubleshooting)

## 1. Install Fabric Loader

You need **Minecraft 1.20.1** with **Fabric Loader 0.19.5 or newer**. Forge and NeoForge are
not supported.

**Official Minecraft Launcher**

1. Download the installer from [fabricmc.net/use](https://fabricmc.net/use/installer/) and
   run it.
2. Choose Minecraft **1.20.1** and the latest loader version, then **Install**.
3. The launcher now has a profile called `fabric-loader-1.20.1`.

**CurseForge app, Modrinth App, Prism Launcher**

Create a new profile (instance) for Minecraft **1.20.1** with the **Fabric** loader. If you
install Every Tunnel from the CurseForge app, it does this for you.

## 2. Add Every Tunnel

Put the Every Tunnel `.jar` file in your profile's `mods` folder:

| Launcher | `mods` folder |
|---|---|
| Official launcher, Windows | `%appdata%\.minecraft\mods` |
| Official launcher, macOS | `~/Library/Application Support/minecraft/mods` |
| Official launcher, Linux | `~/.minecraft/mods` |
| CurseForge, Modrinth App, Prism | Open the profile and use its "Open folder" or "Mods" button |

Do **not** add Fabric API, Fabric Language Kotlin, GeckoLib or LambDynamicLights separately:
they are inside Every Tunnel. If you already have newer copies of them, that is fine too.

## 3. Play

1. Start the `fabric-loader-1.20.1` profile (or your Fabric 1.20.1 profile).
2. The Every Tunnel main menu replaces Minecraft's title screen. Press **BEGIN**.

The first start puts the map in your worlds as **Noname Complex**. Opening it always starts a
new shift: the map is rebuilt behind the loading screen, which takes a moment, and you begin
in the elevator.

New to the game? Open **GUIDE** in the main menu first.

## Recommended mods

None of these are required. Each one makes the game better:

- **[Sound Physics Remastered](https://modrinth.com/mod/sound-physics-remastered)**: echoes
  and sound muffled through walls. The game's sounds are made dry, for it. Strongly
  recommended.
- **[Sodium](https://modrinth.com/mod/sodium)**, **[Iris](https://modrinth.com/mod/iris)** and
  **[Indium](https://modrinth.com/mod/indium)**: better performance and shader support. Use
  the versions for Minecraft 1.20.1 (Sodium 0.5.x with Iris 1.7.x).

Put them in the same `mods` folder.

## Shaders

The game is tuned for **[Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined)**.

1. Install Sodium, Iris and Indium (above).
2. Put the Complementary Reimagined `.zip` in the `shaderpacks` folder, next to `mods`.
3. In game: **Options → Video Settings → Shader Packs**, select it.
4. In the shader pack's settings, set **RP Support** to **labPBR** to see the surfaces in full
   detail.

On its first start, Every Tunnel turns **colored lighting** off in Complementary (and the
reflections that depend on it). **Leave it off**: with it on, the lamps and your flashlight
light nothing.

Other shader packs may work, but they are not tuned.

## Settings

- **Headphones.** You need to hear where sounds come from.
- **Music volume on.** The music tells you what the Pale man is doing.
- **Brightness** always counts as "Moody" in the levels. That is intended: the dark is part of
  the game.
- **Language:** the in-game guide follows Minecraft's language (English or Spanish); change it
  in Options → Language.

## Updating and uninstalling

- **Updating:** replace the old `.jar` with the new one. The map is updated on the next start.
- **Uninstalling:** remove the `.jar`. The map stays in your worlds as `noname_complex`;
  delete it from the world list if you don't want it.

## Troubleshooting

**The game crashes on start, or the mod is not loaded.**
Check that the profile is Minecraft **1.20.1** with **Fabric** (not Forge or NeoForge), and
that Fabric Loader is 0.19.5 or newer. The crash report names the mod that failed.

**"The map is missing. Reinstall the mod."**
The Noname Complex world was deleted or moved. Close the game; on the next start Every Tunnel
puts it back.

**Everything is very dark.**
Without shaders, your flashlight (key **F**) lights a round spot in the middle of the screen;
that is normal. If the flashlight does nothing, its battery may be empty: hold use with a
battery in your hand to change it.

**With shaders, the lamps and the flashlight give no light.**
Complementary's colored lighting was turned back on. Turn it off in the shader pack's settings.

**I want Minecraft's normal title screen.**
Press **Ctrl+Shift+V** in the main menu. Press it again to come back.

**Something else is wrong.**
Report it on the page you downloaded the mod from, with your `logs/latest.log` (in the
`logs` folder, next to `mods`).
