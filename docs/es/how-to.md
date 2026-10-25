# Recetas

*[English version](../en/how-to.md) · [Todas las páginas](README.md)*

Recetas cortas para los cambios más habituales. Cada una lista todos los archivos que hay
que tocar.

## Añadir un nivel

1. **Construye sus bloques.** O amplías el generador de mapas (un diccionario de nivel nuevo
   como `LEVEL_2` en `tools/map/map_generator.py`), o construyes a mano en Creativo y
   guardas la construcción como función.
2. **Escribe su archivo de nivel**, `data/every_tunnel/levels/<id>.json` en el datapack del
   mundo (ver [Niveles](levels.md#formato)):
   - `order` uno más que el nivel anterior;
   - zonas que cubran todo el espacio transitable;
   - un punto de aparición con la etiqueta `player` (donde reinicia una muerte);
   - una zona con la etiqueta `shutter` en la entrada, si no debe haber vuelta atrás;
   - un título de `entry`;
   - `build` con el nombre de la función, si se reconstruye al abrir.
3. **Puntos de aparición** con la etiqueta `pale_man` (con `zone` para retenerlo), `battery`,
   `water`.
4. `/reload` y comprueba con `/everytunnel levels` y `/everytunnel where`. Los problemas
   están en el log con su ruta JSON.
5. Si el mapa crece, mantén el área de carga forzada por debajo de 256 chunks.

## Añadir una etiqueta de zona o de punto

1. Elige un nombre y documéntalo en [Niveles](levels.md#etiquetas) (en los dos idiomas).
2. Léela donde importe, p. ej. `level.zones.filter { "my_tag" in it.tags }`, o reacciona a
   los jugadores que entran registrando un `LevelTriggers.Listener` (ver `EndingHooks` como
   ejemplo pequeño).
3. El parser no necesita cambios: las etiquetas son cadenas libres.

## Añadir un sonido

1. Escribe un generador en `tools/sounds/catalog.py` (o un `Cut` en `cc0_cuts.py` para una
   grabación CC0, con su fuente en `cc0/SOURCES.md`). Mono si tiene posición en el mundo.
2. Escúchalo: `python3 tools/sounds/generate.py <palabra del id>` y abre
   `tools/sounds/out/index.html`.
3. Mételo en el juego: `python3 tools/sounds/export.py` (escribe el `.ogg` y `sounds.json`).
4. Si lo reproduce el código, regístralo en `platform/ModSounds.kt`.
5. Pruébalo en el juego: `/playsound every_tunnel:<id> master @s`.
6. Añádelo a la tabla de [Audio](audio.md#eventos-de-sonido).

## Añadir un bloque

1. Regístralo en `platform/ModBlocks.kt` (propiedades, forma en píxeles con `BlockShapes`,
   grupo de sonido de `ModSoundGroups`). Usa `PropBlock` para un prop de suelo sencillo.
2. Añádelo a `BLOCKS` en `tools/blocks/generate.py` con su modelo y su pintor de textura.
3. `python3 tools/blocks/generate.py`: escribe los recursos y el nombre en inglés; se
   detiene si las listas de ids de Kotlin y Python no coinciden.
4. Si su textura tiene píxeles transparentes, añádelo a la capa cutout en
   `client/EveryTunnelClient.kt`.
5. Para colocarlo en los niveles generados, añádelo a `MOD_BLOCKS` en `map_generator.py` (y
   fija su estado en `garage/mod_blocks.py` si tiene orientaciones o conexiones).

## Añadir un objeto

1. Regístralo en `platform/ModItems.kt`, con una clave de tooltip si la necesita.
2. Su textura y modelo: en `tools/blocks/generate.py` (las texturas de objetos se hacen
   ahí).
3. Si aparece tirado por los niveles: un bloque de botín en `ModBlocks`, una entrada en
   `LootSpawner.LOOT` (etiqueta del punto → bloque) y puntos de aparición con esa etiqueta.

## Añadir un valor de configuración de juego

1. Añade la propiedad con su valor por defecto a la clase que toque en
   `config/GameplayConfig.kt` (con un comentario KDoc).
2. Léela en `config/GameplayConfigParser.kt` con su rango permitido.
3. Úsala a través de `ConfigManager.gameplay`.
4. Los archivos de configuración existentes reciben la clave nueva en el siguiente arranque.
5. Añádela a la tabla de [Sistemas del jugador](player.md#el-archivo-de-configuración).

## Añadir un consejo de la pantalla de carga

Añade `loading.every_tunnel.tip.<n>` a `assets/every_tunnel/lang/en_us.json`, numerado sin
huecos. `{flashlight}`, `{sneak}`, `{sprint}`, `{jump}` y `{use}` se convierten en las
teclas que tiene asignadas el jugador.

## Añadir una traducción

Copia `assets/every_tunnel/lang/en_us.json` al archivo del idioma (`es_es.json`...) y
traduce los valores. `tools/blocks/generate.py` reescribe los nombres de bloques en
`en_us.json`; los demás idiomas no los toca. Las claves que faltan vuelven al inglés. *(Por
ahora los archivos `es_*.json` solo tienen las palabras de la pantalla de la guía; hay
prevista una traducción completa del juego al español para más adelante.)*

## Añadir una página a la guía

1. Escríbela en `docs/en/guide/` y en `docs/es/guide/`, con el mismo nombre de archivo. El
   número del principio fija el orden.
2. Usa solo el Markdown que dibuja la guía del juego (ver
   [Cómo contribuir](contributing.md#páginas-de-la-guía-del-jugador)).
3. Enlázala desde los dos `README.md` de la documentación.

## Cambiar un comportamiento del Pale man

1. Las decisiones van en `entities/PaleManBrain.kt` (Kotlin puro), con un test en
   `PaleManBrainTest`.
2. Los sentidos y las acciones (lo que oye, cómo se mueve) van en
   `platform/PaleManEntity.kt`.
3. Los números nuevos van en `PaleManConfig` (ver "Añadir un valor de configuración de
   juego").
4. Actualiza [El ruido y el Pale man](noise-and-pale-man.md).

## Cambiar el mapa

Edita los parámetros del principio de `tools/map/map_generator.py`, ejecútalo y abre el
mundo. La misma `SEED` siempre da el mismo aparcamiento. Una `SEED` nueva reparte de otra
forma el desgaste, los props y las rarezas.
