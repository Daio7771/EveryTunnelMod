# Credits

Every Tunnel is made by Daio7771: the design, the story, the music of Chapter 1's main menu and of
Level 4, and the direction of everything else (see "Voice and AI" below). All rights reserved
(`LICENSE.txt`), except the work listed below, which keeps its own licence.

## Voice and AI

The character voices (the Director's in Level 5, and Chapter 2's characters) are ElevenLabs library
voices: the only AI-made audio in the game. The design, the story and the music are the author's,
except Chapter 2's menu theme, composed in code by Claude from the CC0 samples listed below, directed
and approved by the author. Claude Code also helped with the code, the asset tools and part of the
text, directed, approved and tested by the author.

## Music

**"Local Forecast - Elevator"** Kevin MacLeod (incompetech.com)
Licensed under Creative Commons: By Attribution 4.0 License
http://creativecommons.org/licenses/by/4.0/

Plays in the elevator at the start of a new game. The mod uses its first 30 seconds, cut,
faded out and converted to Ogg Vorbis (`assets/every_tunnel/sounds/music/elevator_1.ogg`);
in game it slows down, wavers and fades out when the elevator doors open. This credit must
stay wherever the mod is shared.
Source: `tools/sounds/common/licensed/local_forecast_elevator.mp3`, see `tools/sounds/common/licensed/SOURCES.md`.

## Recordings

Three of the mod's sounds are made from CC0 (public domain) recordings from Freesound: the
player's exhausted breathing, the Pale man's breathing and drinking. No credit is required;
their sources are listed in `tools/sounds/common/cc0/SOURCES.md`. The originals stay public domain; the mod's
sounds made from them are part of Every Tunnel.

## Samples

Chapter 2's menu theme is rendered from two CC0 (public domain) sample sets. No credit is required
by their licence, but one asks for it, so here it is (sources in
`tools/sounds/common/soundfonts/SOURCES.md`). Only the music rendered from them ships, not the samples.

- **VSCO 2: Community Edition, Solo Violin** by Versilian Studios and Sam Gossner (CC0 1.0).
- **Old Piano FB** from the FreePats project (CC0 1.0).

## Libraries inside the jar

| Library | Authors | Licence |
|---|---|---|
| Fabric API | FabricMC | Apache 2.0 |
| Fabric Language Kotlin, with the Kotlin libraries | FabricMC, JetBrains | Apache 2.0 |
| GeckoLib, with mclib | the GeckoLib team, McHorse | MIT |

Each one carries its own licence file inside its jar.

## Required, not included

**LambDynamicLights** by LambdAurora gives the flashlight its light. Its licence (the Lambda
License) does not allow sharing its jar without permission, so it is not bundled: install it
yourself (version 4.4.0 or newer for Minecraft 1.20.1).

**Cardinal Components API** by Ladysnake and OnyxStudios (MIT) keeps the player's progress.
It is not bundled: install it yourself (version 5.2.3 or newer for Minecraft 1.20.1).
