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
| `tools/entity/pale_man.py` | Geometría, animaciones, textura y máscara de brillo del Pale man, modelo del huevo | el Pale man |
| `tools/entity/observer.py` | Geometría, animaciones y textura del Observer, modelo del huevo | el Observer |
| `tools/entity/guard.py` | `textures/entity/guard.png` (la skin del uniforme) | el uniforme |
| `tools/gui/hud_icons.py` | `hud_icons.png`, `vignette.png` | los iconos del HUD |
| `tools/gui/logo.py` | `logo.png` | el logo |
| `tools/gui/flashlight_spot.py` | `flashlight_spot.png`, `flashlight_glow.png` | el foco sin shaders |
| `tools/gui/locker_view.py` | `locker_view.png` | la vista desde dentro de una taquilla |
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

- **Salida** (`tools/map/output/`): el datapack del mod `every_tunnel_level1_mod` (archivos
  JSON de los niveles y `garage:build`), el datapack vanilla `every_tunnel_level1_vanilla`,
  `level_resources.zip`, `level_preview.png`, `level3_preview.png`, `texture_sheet.png`,
  `level3_furniture.png` (el mismo, más grande), `level3_texture_sheet.png`, `pbr_sheet.png`. La ejecución muestra los chunks y comandos de
  las dos etapas del build. En el mod
  (en git): la escena del menú, `assets/every_tunnel/menu/scene.json` y los modelos y
  texturas de `block/menu/`.
- **Instalación:** en cada mundo de `INSTALL` que exista: `run/saves/noname_complex` y, si
  está, su plantilla de desarrollo `run/dev_templates/noname_complex`.

El mod ejecuta `garage:build` cada vez que se abre el mundo, así que tras ejecutar el
generador basta con abrir el mundo. `/function garage:build` reconstruye a mano. Ver
[Niveles](levels.md).

## Bloques

`tools/blocks/generate.py` hace los recursos de los 72 bloques del mod. Los bloques en sí
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
  posición en el mundo, estéreo para la música y los sonidos en la cabeza.
- `menu_piano.py`: el tema del menú, una pieza de piano del usuario, llamada desde
  `catalog.py`. Tiene sus propias funciones y semilla para coincidir con el original.
- `dsp.py`: 44,1 kHz, ruido con semilla, envolventes, filtros. Los bucles se construyen "en
  círculo" (ciclos completos, ruido moldeado por FFT) para que se repitan sin costura;
  `loop_seam()` lo comprueba.
- `cc0_cuts.py`: un `Cut` convierte una grabación en un evento de sonido: fragmentos,
  remuestreo, mono, fundidos, normalización.
- `export.py`: el codificador Ogg pone un número de stream aleatorio en cada archivo, así
  que `export_manifest.json` guarda un hash del audio de cada archivo y los que no cambian
  no se reescriben. Los diffs de git quedan limpios.

Licencias: en `cc0/` solo van grabaciones CC0 (con su fuente en `cc0/SOURCES.md`). Lo que
necesite crédito va en `licensed/`, con su crédito en `CREDITS.md`.
