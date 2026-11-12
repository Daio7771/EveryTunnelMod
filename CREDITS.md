# Credits

Every Tunnel is made by Daio7771: the design, the story, the music of the main menu and of
Level 4, and the direction of everything else (see "Voice and AI" below). All rights reserved
(`LICENSE.txt`), except the work listed below, which keeps its own licence.

## Voice and AI

The Director's lines in Level 5 (the voice files in `tools/sounds/voice/`, their texts in
`tools/sounds/voices.py`) are voices from ElevenLabs' library, generated with the author's own
plan. They are the only AI-made sound or music in the game: the rest is synthesized for it, the
author's own, or listed below. The design, the story and the music are the author's. The code, the
Python tools that generate the map, textures, models and sound effects, and part of the in-game text
and its Spanish translation were written with the help of AI tools (Claude Code), directed,
approved and tested by the author.

## Music

**"Local Forecast - Elevator"** Kevin MacLeod (incompetech.com)
Licensed under Creative Commons: By Attribution 4.0 License
http://creativecommons.org/licenses/by/4.0/

Plays in the elevator at the start of a new game. The mod uses its first 30 seconds, cut,
faded out and converted to Ogg Vorbis (`assets/every_tunnel/sounds/music/elevator_1.ogg`);
in game it slows down, wavers and fades out when the elevator doors open. This credit must
stay wherever the mod is shared.
Source: `tools/sounds/licensed/local_forecast_elevator.mp3`, see `tools/sounds/licensed/SOURCES.md`.

## Recordings

Three of the mod's sounds are made from CC0 (public domain) recordings from Freesound: the
player's exhausted breathing, the Pale man's breathing and drinking. No credit is required;
their sources are listed in `tools/sounds/cc0/SOURCES.md`. The originals stay public domain; the mod's
sounds made from them are part of Every Tunnel.

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
