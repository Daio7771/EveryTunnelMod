# Herramientas Python

*[English version](../en/tools.md) · [Todas las páginas](README.md)*

Casi todo el contenido lo generan scripts Python en `tools/`, así que se puede rehacer desde
parámetros. Todos usan semilla: los mismos parámetros siempre dan el mismo resultado.

## Preparación

- **Python 3 con Pillow** para todo.
- **Los sonidos** necesitan además **numpy**, **scipy** y la **libsndfile** del sistema (1.1
  o más nueva, para MP3). En Arch: `sudo pacman -S python-numpy python-scipy libsndfile`.
  Se usa el paquete `soundfile` si está instalado; si no, se llama a libsndfile
  directamente.
- **El logo** necesita una fuente serif de rótulo (se usó Noto Serif Display). El PNG está
  en git, así que la fuente solo hace falta para cambiarlo.

Ejecuta cada herramienta **desde la raíz del repositorio**. Cada una escribe su resultado
real en `src/` (va a git) y las vistas previas en su propia carpeta `output/` (ignorada por
git).

| Herramienta | Escribe | Ejecútala tras cambiar... |
|---|---|---|
| `tools/map/map_generator.py` | Datapacks de los niveles, resource pack del mundo, lo instala en `run/saves/noname_complex`; la escena del menú en el mod | el mapa o la escena del menú |
| `tools/blocks/generate.py` | Modelos de bloque y objeto, blockstates, texturas + mapas LabPBR, nombres, loot tables | un bloque del mod |
| `tools/blocks/briefing.py` | La pizarra de seguridad del nivel 5 (la llama `generate.py`, que la corta en las texturas de los bloques): el corcho con dibujos a lápiz renderizados desde los modelos de las entidades y las fichas mecanografiadas, la placa SAFETY BRIEFING; sola, una vista previa | la pizarra |
| `tools/film/film.py` | La película del Cinema: una grabación convertida en fotogramas de cine viejo | la película |
| `tools/film/eye.py` | `film_eye/` | lo que muestra el Cinema durante el apagón: un ojo e INTRUDER DETECTED |
| `tools/entity/pale_man.py` | Geometría, animaciones, textura y máscara de brillo del Pale man, modelo del huevo | el Pale man |
| `tools/entity/observer.py` | Geometría, animaciones y textura del Observer, modelo del huevo | el Observer |
| `tools/entity/hunter.py` | Geometría, animaciones, textura y máscara de brillo de los ojos de los Hunters | los Hunters del nivel 5 |
| `tools/entity/subject.py` | Los cuerpos y pieles de los cuatro sujetos, una fase cada uno, de persona a Pale man, sedados y atados a una camilla vertical | los sujetos del nivel 5 |
| `tools/entity/figure.py` | `textures/entity/lab_figure.png`: una silueta oscura con el formato del jugador | la figura que se ve desde el Lobby del nivel 5 |
| `tools/entity/distortion.py` | Geometría, animaciones, texturas y máscaras de brillo de la Distortion Cloud y el Junior (el ojo del Junior agrietándose en cuatro fases) | las Distortions del nivel 5 |
| `tools/entity/guard.py` | `textures/entity/guard.png` (la skin del uniforme) | el uniforme |
| `tools/entity/dead_guard.py` | El guardia anterior: un cuerpo de jugador sentado contra una pared, la cabeza caída, con la skin del uniforme gastada (después de `guard.py`) | el Hideout del nivel 5 |
| `tools/gui/hud_icons.py` | `hud_icons.png`, `vignette.png`, `detector_icons.png`, `turn_icon.png` | los iconos del HUD, el detector del nivel 5, el símbolo de girar del pasillo de entrada |
| `tools/gui/logo.py` | `logo.png` | el logo |
| `tools/gui/flashlight_spot.py` | `flashlight_spot.png`, `flashlight_glow.png` | el foco sin shaders |
| `tools/gui/locker_view.py` | `locker_view.png` | la vista desde dentro de una taquilla |
| `tools/gui/safe_dial.py` | `safe_dial.png` | el dial de las cajas fuertes de las Suites |
| `tools/gui/wall_font.py` | `wall_paint.png`, `wall_paint.json` | la fuente pintada de los mensajes en la pared |
| `tools/gui/achievement_icons.py` | `achievement_icons.png` | los iconos de logro dibujados para el mod (placa, calavera, Pale man, bloqueado) |
| `tools/gui/guide_paper.py` | `guide_paper.png` (mosaico sin costuras), `guide_stains.png` | el papel de la guía |
| `tools/sounds/generate.py` | WAV de prueba y una página para escucharlos (`tools/sounds/out/index.html`) | un sonido, para oírlo |
| `tools/sounds/export.py` | Los `.ogg` y `sounds.json` en el mod | un sonido, para meterlo en el juego |

## Mapa

`tools/map/map_generator.py` construye los niveles: los dos del aparcamiento y el nivel 3,
las oficinas.

- **Los parámetros** están al principio del archivo: los del Nivel 1 (`HALLS`, `SIDE_ROOMS`,
  `LOOT`, `SEED`, `WEAR`, luces, props, rarezas...) y `LEVEL_2`, un diccionario de valores
  que cambian para el Nivel 2 (más oscuro, más gastado, `PALE_MEN` por planta,
  `KEYCARD_HALL`, `END = "none"`). `LEVEL_3` es el nivel 3: cada caja de su plano
  (habitaciones, puertas, pasillos, la biblioteca, el búnker), alturas, materiales y sus
  bloques sustitutos, los puntos de spawn de los ganchos. `MENU_SCENE` y `MENU_CAMERA` son
  la escena del menú principal: una planta y la cámara.
- **Módulos** en `tools/map/garage/`:

| Módulo | Función |
|---|---|
| `level.py` | Un nivel entero: plantas unidas por pasillos y rampas, el vestíbulo del ascensor o el pasillo de entrada, cuartos laterales, la rampa de salida o la salida con tarjeta |
| `build.py` | Una planta de aparcamiento: estructura, desgaste, props, tuberías y vigas del techo, rarezas, luces |
| `props.py` | Plantillas de props y reglas de colocación; el pasillo queda libre a la altura del cuerpo |
| `mod_blocks.py` | Los estados exactos de los bloques del mod (orientaciones, conexiones, dibujos de manchas), porque `/setblock` toma el estado tal cual |
| `textures.py` | Texturas procedurales de 16×16 y el resource pack del mundo |
| `pbr.py` | Mapas LabPBR 1.3 para cada textura |
| `glyphs.py` | Textos de carteles asignados a pares (orientación, ranura) de `SignTileBlock` |
| `preview.py` | Plano visto desde arriba del nivel y hojas de texturas |
| `menu_scene.py` | La escena del menú principal: una planta, escrita en el mod con los modelos de sus bloques re-texturizados |

- **El nivel 3** tiene sus propios módulos en `tools/map/offices/` (solo en el pack del mod):

| Módulo | Función |
|---|---|
| `level.py` | El nivel a partir de `LEVEL_3`: los espacios como cajas de aire, la carcasa a su alrededor (suelo, paredes y techo con los materiales de cada espacio), los accesorios, las zonas y los puntos de spawn; `Placement` gira y mueve el plano hasta la puerta del nivel 2 |
| `checks.py` | Un recorrido por cada sitio donde se puede estar de pie desde la antesala (todas las habitaciones, la biblioteca, la trampilla, el búnker, la puerta a B4; sin dejar ninguno) y una comprobación de que las habitaciones están cerradas salvo por sus puertas. Si falla, el generador se para |
| `textures.py` | Las texturas propias del nivel 3 (moqueta, yeso, placas de techo, vinilo, madera, hormigón), añadidas al resource pack del mundo |
| `furnish.py` | Los muebles: una plantilla por tipo de sala (`FURNISH` en `LEVEL_3`), calculada en el marco propio de la sala desde su puerta principal y lejos de las puertas; las filas, el claro y la mesa del bibliotecario alrededor de la trampilla (`LIBRARY_LAYOUT`); las plantas y relojes del anillo; los estados de las uniones |
| `preview.py` | Plano visto desde arriba del nivel 3 leído de sus bloques, con los muebles y el búnker al lado |

- **El nivel 4** tiene sus propios módulos en `tools/map/lounge/` (solo en el pack del mod):

| Módulo | Función |
|---|---|
| `level.py` | El nivel a partir de `LEVEL_4`: sus espacios, la carcasa a su alrededor, lámparas, puertas y escalones, en dos conjuntos de bloques (el pasillo, construido con el nivel 3; el resto, en plena partida) |
| `furnish.py` | Los muebles, las nueve notas y el botín, sala por sala, con huecos libres para lo que traen los puzles |
| `checks.py` | Un recorrido desde Reception que llega a cada celda, una pared entre todas las salas, tres escalones hasta la Calm room; detienen el generador |
| `textures.py` | Las texturas propias del nivel 4 (moqueta burdeos, nogal con banda de latón, paredes y techo crema), añadidas al pack del mundo |
| `preview.py` | Plano visto desde arriba del nivel 4 con medidas y aberturas. `python3 -m lounge.preview` desde `tools/map` lo dibuja solo, sin instalar nada |

- **El nivel 5** tiene sus propios módulos en `tools/map/lab/` (solo en el pack del mod), a partir de `LEVEL_5`:
  los planos del usuario a 2/3, pasillos de 4 de ancho (los túneles del drenaje, de 5). Las tres capas comparten
  las coordenadas del plano; la capa 2 está `LAYER_DROP` (10) bajo la capa 1, la capa 3 `DRAIN_DROP` (7) bajo la
  capa 2, al este de ella salvo el pie de la escalera de mano; un espacio puede estar `rise` por encima de su capa
  (la antesala del director, su despacho y el pasillo que sale de él, 10 bloques más arriba por la escalera de caracol, `SPIRAL`). Las
  superficies toman el aspecto del espacio al que dan (`STYLE_OF`, `STYLES`: el azulejo del laboratorio, el hormigón
  manchado del drenaje, acero, el del despacho, los bloques propios del garaje para el pasillo, negro en el pozo).
  El plano se gira en el mundo para que su entrada salga del tramo oscuro del nivel 4 (ahora más largo, 44 bloques)
  por un marco de acero junto a su fondo, al oeste de todos los demás niveles.

| Módulo | Función |
|---|---|
| `level.py` | El nivel a partir de `LEVEL_5`: espacios, aberturas, el tramo de escaleras y la escalera de caracol hasta la antesala, la cáscara alrededor del aire con el aspecto de cada espacio (azulejo blanco, una franja verde al pie, vinilo, paneles de techo; acero y chapa estriada en la esclusa y el marco; los bloques de la biblioteca del nivel 3 en el pasillo de entrada, con sus estanterías a lo largo de las dos paredes; el hormigón del drenaje; el del despacho; el del garaje), luces (fluorescentes; las lámparas enjauladas del drenaje, algunas parpadeando y otras fundidas; los paneles del despacho), puertas de acero, las puertas de la esclusa, la rejilla, las puertas del despacho y la puerta EXIT del nivel 1 con su cartel; la escalera de mano del drenaje y su pozo hasta la trampilla, sus canales y piscinas de agua (colocados al final), pasarelas y barandillas, el derrumbe, las tuberías de sus túneles, su suciedad, el pozo bajo el final del pasillo; dos juegos de bloques (la entrada, construida con el nivel 4; el resto, construido en juego, con su aire); las zonas del archivo del nivel (con la zona del juego del pasillo de entrada y los extremos de su línea, de `CLOUD_GAME`) |
| `furnish.py` | Los muebles de las tres capas sala a sala, la caja de fusibles, lo que hay por ahí (la llave, el detector, los fusibles, los reactivos, el loot), las fichas, los ordenadores, la mezcladora de la sala de químicos, su tubería, las luces de alarma y la trampilla del drenaje; la compuerta del drenaje, las bombas y sus tuberías, las cosas del Hideout y la nota del guardia, el despacho del director; los puntos de aparición de los enemigos, los sujetos, la figura, los enemigos de la alarma, el guardia muerto y los puntos de reinicio |
| `checks.py` | Un recorrido por las tres capas desde el pasillo de entrada que llega a todas las celdas (rodeando los muebles y el derrumbe, fuera del agua de las piscinas; bajando la escalera de mano desde la trampilla), ningún mueble en la boca de una puerta o de un pasillo, pared entre salas, los techos de cada capa bajo los suelos de la de encima, el marco que se abre desde el tramo del nivel 4, cada celda en una zona; detienen el generador |
| `textures.py` | Las texturas propias del nivel 5 (`b5_*`: las del laboratorio, las del drenaje, las del despacho, la escalera de acero y las losas del escombro), añadidas al pack del mundo |
| `preview.py` | Las tres capas una encima de otra con medidas, zonas, aberturas, el agua, las pasarelas, el derrumbe y el pozo de la capa 3. `python3 -m lab.preview` desde `tools/map` lo dibuja solo, sin instalar nada |

- **Salida** (`tools/map/output/`): el datapack del mod `every_tunnel_level1_mod` (archivos
  JSON de los niveles y `garage:build`), el datapack vanilla `every_tunnel_level1_vanilla`,
  `level_resources.zip`, `level_preview.png`, `level3_preview.png`, `texture_sheet.png`,
  `level3_furniture.png` (el mismo, más grande), `level3_texture_sheet.png`, `level4_preview.png`,
  `level4_texture_sheet.png`, `level5_preview.png`, `level5_texture_sheet.png`, `pbr_sheet.png`. La ejecución muestra los chunks y comandos de
  las dos etapas del build. En el mod
  (en git): la escena del menú, `assets/every_tunnel/menu/scene.json` y los modelos y
  texturas de `block/menu/`.
- **Instalación:** en cada mundo de `INSTALL` que exista: `run/saves/noname_complex` y, si
  está, su plantilla de desarrollo `run/dev_templates/noname_complex`.

El mod ejecuta `garage:build` cada vez que se abre el mundo, así que tras ejecutar el
generador basta con abrir el mundo. `/function garage:build` reconstruye a mano. Ver
[Niveles](levels.md).

## Bloques

`tools/blocks/generate.py` hace los recursos de los 146 bloques del mod. Los bloques en sí
(propiedades, formas, sonidos) se registran en Kotlin (`platform/ModBlocks.kt`); la
herramienta **se detiene si las dos listas de ids no coinciden**. Las rotaciones de los
modelos deben coincidir con las formas de `platform/BlockShapes.kt`. Las texturas reutilizan
los pintores del mapa (`garage/textures.py`), así que los props se parecen al nivel que los
rodea. También escribe los nombres de los bloques en `lang/en_us.json` (las demás claves se
conservan). Vista previa: `tools/blocks/output/blocks_sheet.png`.

## Arte de entidades

- `tools/entity/pale_man.py`: unos 2,75 bloques de alto, extremidades largas y finas, manos
  grandes, cuello largo, ojos lechosos, orejas grandes, una camisa de vigilante rota.
  Basado en `tools/entity/reference/pale_man_concept.png` (concept art hecho con IA,
  **solo como referencia**, no va en el juego). También escribe plantillas para repintar a
  mano. Sus números `STOOP_*` son su postura encorvada bajo techos bajos (dibujada en la
  vista previa, unos 1,9 bloques de alto); el juego los aplica en
  `client.render.PaleManModel`, que tiene los mismos números.
- `tools/entity/guard.py`: una skin estándar de 64×64 con brazos anchos. Chaqueta negra con
  SECURITY en amarillo, placa, radio, botas.
- `tools/entity/dead_guard.py`: el vigilante nocturno nº 13 en el Hideout del nivel 5. Un cuerpo de
  jugador (las cajas de la skin) sentado en el suelo contra una pared, las piernas estiradas, las manos en
  el suelo, la cabeza sobre el pecho; el uniforme de `guard.png`, gastado: piel gris, ojos cerrados, barba de
  unos días, mugre y marcas de agua, una rodilla rota.

## GUI

Scripts pequeños, una textura cada uno. Las máscaras se dibujan como texto en el script
(`#` contorno, `o` relleno...), así que son fáciles de editar.

## Sonidos

```
catalog.py (síntesis, una función por sonido) ─┐
cc0_cuts.py (cortes de cc0/ y licensed/)  ─────┼─> generate.py  -> WAV de prueba + index.html
                                               └─> export.py    -> .ogg + sounds.json en el mod
dsp.py: utilidades DSP · audio_io.py: leer/escribir audio
```

- `catalog.py`: cada generador recibe `(rng, variant)` y devuelve un array mono o estéreo.
  Los ids siguen los eventos de sonido (`every_tunnel:<id>`). Mono para los sonidos con
  posición en el mundo, estéreo para la música y los sonidos en la cabeza. Sus funciones
  más nuevas (la segunda pasada del nivel 5 y los sonidos que comparte) suenan menos
  sintéticas que los parciales sueltos: `modal_hit` (los muchos modos desiguales de un cuerpo
  golpeado: `bar`, `plate_modes`, `glass_modes`), `thud`, `scrape`, `hinge`, `motor` (un eje y unos
  engranajes girando, no un tono), `air` (gas con una presión que cae), `rail`. Úsalas para
  sonidos mecánicos nuevos. (`ring` es otra función, más antigua.)
- `menu_piano.py`: el tema del menú, una pieza de piano del usuario, llamada desde
  `catalog.py`. Tiene sus propias funciones y semilla para coincidir con el original.
- `dsp.py`: 44,1 kHz, ruido con semilla (con `peaks` resonantes si se quiere), envolventes,
  filtros, `struck()` (modos que suenan desde el reposo, movidos por un impulso de fuerza cuya
  duración apaga los agudos, en parejas que baten). Los bucles se construyen "en
  círculo" (ciclos completos, ruido moldeado por FFT) para que se repitan sin costura;
  `loop_seam()` lo comprueba.
- `cc0_cuts.py`: un `Cut` convierte una grabación en un evento de sonido: fragmentos,
  remuestreo, mono, fundidos, normalización.
- `export.py`: el codificador Ogg pone un número de stream aleatorio en cada archivo, así
  que `export_manifest.json` guarda un hash del audio de cada archivo y los que no cambian
  no se reescriben. Los diffs de git quedan limpios.

Licencias: en `cc0/` solo van grabaciones CC0 (con su fuente en `cc0/SOURCES.md`). Lo que
necesite crédito va en `licensed/`, con su crédito en `CREDITS.md`.
