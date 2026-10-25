# How-to recipes

*[Versión en español](../es/how-to.md) · [All pages](README.md)*

Short recipes for the changes people make most. Each one lists every file to touch.

## Add a level

1. **Build its blocks.** Either extend the map generator (a new level dict like `LEVEL_2`
   in `tools/map/map_generator.py`) or build by hand in Creative and save the build as a
   function.
2. **Write its level file**, `data/every_tunnel/levels/<id>.json` in the world's datapack
   (see [Levels](levels.md#format)):
   - `order` one higher than the previous level;
   - zones covering every walkable space;
   - a spawn point tagged `player` (where a death restarts);
   - a zone tagged `shutter` at the way in, if there must be no way back;
   - an `entry` title;
   - `build` naming the function, if it is rebuilt on open.
3. **Spawn points** tagged `pale_man` (with `zone` to keep him in), `battery`, `water`.
4. `/reload` and check with `/everytunnel levels` and `/everytunnel where`. Problems are
   in the log with their JSON path.
5. If the map grows, keep the build's force-loaded area under 256 chunks.

## Add a zone or spawn point tag

1. Pick a name and document it in [Levels](levels.md#tags) (both languages).
2. Read it where it matters, e.g. `level.zones.filter { "my_tag" in it.tags }`, or react to
   players walking in by registering a `LevelTriggers.Listener` (see `EndingHooks` for a
   small example).
3. The parser needs no change: tags are open strings.

## Add a sound

1. Write a generator in `tools/sounds/catalog.py` (or a `Cut` in `cc0_cuts.py` for a CC0
   recording, with its source in `cc0/SOURCES.md`). Mono if it is positioned in the world.
2. Listen: `python3 tools/sounds/generate.py <word in the id>` and open
   `tools/sounds/out/index.html`.
3. Ship it: `python3 tools/sounds/export.py` (writes the `.ogg` and `sounds.json`).
4. If code plays it, register it in `platform/ModSounds.kt`.
5. Try it in game: `/playsound every_tunnel:<id> master @s`.
6. Add it to the table in [Audio](audio.md#sound-events).

## Add a block

1. Register it in `platform/ModBlocks.kt` (properties, shape in pixels via `BlockShapes`,
   sound group from `ModSoundGroups`). Use `PropBlock` for a simple floor prop.
2. Add it to `BLOCKS` in `tools/blocks/generate.py` with its model and texture painter.
3. `python3 tools/blocks/generate.py`: writes the assets and the English name; it stops if
   the Kotlin and Python id lists disagree.
4. If its texture has see-through pixels, add it to the cutout layer in
   `client/EveryTunnelClient.kt`.
5. To place it in generated levels, add it to `MOD_BLOCKS` in `map_generator.py` (and set
   its state in `garage/mod_blocks.py` if it has facings or connections).

## Add an item

1. Register it in `platform/ModItems.kt`, with a tooltip key if it needs one.
2. Its texture and model: in `tools/blocks/generate.py` (item textures are made there).
3. If it lies around in levels: a loot block in `ModBlocks`, an entry in
   `LootSpawner.LOOT` (spawn point tag → block), and spawn points with that tag.

## Add a gameplay config value

1. Add the property with its default to the right class in `config/GameplayConfig.kt`
   (with a KDoc comment).
2. Read it in `config/GameplayConfigParser.kt` with its allowed range.
3. Use it through `ConfigManager.gameplay`.
4. Existing config files get the new key on the next launch.
5. Add it to the table in [Player systems](player.md#the-config-file).

## Add a loading screen tip

Add `loading.every_tunnel.tip.<n>` to `assets/every_tunnel/lang/en_us.json`, numbered
without gaps. `{flashlight}`, `{sneak}`, `{sprint}`, `{jump}` and `{use}` become the
player's bound keys.

## Add a translation

Copy `assets/every_tunnel/lang/en_us.json` to the language's file (`es_es.json`...) and
translate the values. The block names in `en_us.json` are rewritten by
`tools/blocks/generate.py`; other languages are not touched by it. *(A full Spanish
translation of the game is planned for later.)*

## Add a guide page

1. Write it in both `docs/en/guide/` and `docs/es/guide/`, with the same file name. The
   number at the front sets the order.
2. Use only the Markdown the in-game guide draws (see
   [Contributing](contributing.md#player-guide-pages)).
3. Link it from both `README.md` files of the docs.

## Change a Pale man behaviour

1. Decisions go into `entities/PaleManBrain.kt` (plain Kotlin), with a test in
   `PaleManBrainTest`.
2. Senses and actions (what he hears, how he moves) go into `platform/PaleManEntity.kt`.
3. New numbers go into `PaleManConfig` (see "Add a gameplay config value").
4. Update [Noise and the Pale man](noise-and-pale-man.md).

## Change the map

Edit the parameters at the top of `tools/map/map_generator.py`, run it, open the world.
The same `SEED` always gives the same garage. A new `SEED` reshuffles wear, props and
oddities.
