# Changelog

## 1.0: Chapter 1 complete

What is new since 0.4. The requirements are the same (Minecraft 1.20.1, Fabric Loader,
LambDynamicLights and Cardinal Components API).

### Level 5: the laboratory

- **A way in.** From Level 4's dark stretch, through a steel frame and a long dark corridor
  between library shelves where something follows you, to an airlock.
- **A laboratory on three floors**, in the same cold look, with its own sounds: locked doors and
  levers, a fuse box that has to be filled before the stairs work, test chambers, a chemical
  puzzle whose answer changes every game, computers with files and company papers, and a flooded
  drain under it all. Deep water is not safe: keep to the catwalks.
- **New creatures.** Hunters, who come in threes and hunt by sight; Distortion Clouds, which you
  must not look at; and Distortion Juniors, which follow them.
- **New tools.** A wrench, and a radiation detector that clicks like a Geiger counter.
- **A final scene and a new ending**, with new final credits and a congratulations window when
  you return to the menu: the Observer with his coffee, and the time you played and your deaths
  over the whole game.

### Everything else

- **Spanish (Spain) translation** of the whole game: menus, items, signs, notes, subtitles and
  credits. Every Spanish locale uses it. The messages painted on the walls stay in English.
- **Death screen** that names what killed you, with a tip and a "Try again" button.
- **A new achievement**, "Nothing in There": 11 in all.
- **The main menu shows the version as "v1.0"**, without the beta tag.
- **Level titles and the laboratory's computers follow the game's language.**

### Fixes

- The watcher of Level 3 did not appear if you opened the world inside Level 3 (for example,
  with CONTINUE). It does now.
- Level 5's entry corridor was almost black without a shader pack. Without shaders the dark is
  now lifted a little while its game runs (`game_dark_lift` in `gameplay.json`; shader packs are
  untouched).
- The Cloud in the corridor to Level 5 could vanish just before its mini-game, taken by the
  map's own clean-up. It stays now.
- Hunters and Distortion Clouds had no glowing eyes without a shader pack (white and purple).
  They glow with vanilla graphics too.
- Credits and achievements no longer run into each other or off the screen with longer
  translations. The same goes for Level 3's lettering and the Observer's warning, which now
  shrinks to fit or takes two lines.
- The notes and the credits now agree on who Rowan, Daniel and the Observer are.

Earlier versions: see [Releases](https://github.com/Daio7771/EveryTunnelMod/releases).
