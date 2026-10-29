# Niveles

*[English version](../en/levels.md) · [Todas las páginas](README.md)*

Un nivel son **datos, no código**: un archivo JSON de áreas (zonas) y puntos (puntos de
aparición) en el datapack de un mundo, más una función de Minecraft que construye sus
bloques. El mod lee los archivos, calcula dónde está cada jugador, y los sistemas de juego
reaccionan a las zonas según sus etiquetas.

## Dónde están los archivos de nivel

```
<mundo>/datapacks/<pack>/data/every_tunnel/levels/<id>.json
```

- El nombre del archivo es el id del nivel (`level_1.json` → `level_1`; solo `a-z`, `0-9`,
  `_`).
- `platform.LevelReloadListener` carga todos los archivos al cargar el mundo y con
  `/reload`, en `core.LevelManager`.
- Un archivo roto queda en el log con el archivo y la ruta JSON de cada problema, y se
  salta; nunca detiene la carga. Una entrada rota dentro de un archivo bueno (una zona, un
  punto) se salta sola.
- Un mundo con al menos un nivel es un "mundo con niveles": se activan las reglas del
  mundo, el HUD, la linterna y la pantalla de carga (`world.WorldRules.active`).

En Noname Complex los dos niveles vienen del datapack `every_tunnel_level1_mod` del
generador de mapas (ver [Herramientas Python](tools.md)).

## Formato

Un ejemplo completo (todas las claves):

```json
{
  "name": "Level 1 - Parking Garage",
  "order": 1,
  "dimension": "minecraft:overworld",
  "zones": [
    { "id": "garage", "from": [0, -60, 0], "to": [31, -55, 31] },
    { "id": "elevator", "from": [-4, -60, 2], "to": [-1, -56, 5], "tags": ["start"] }
  ],
  "spawn_points": [
    { "id": "player_start", "pos": [-2.5, -60, 3.5], "yaw": 90, "tags": ["player"] },
    { "id": "pale_man_1", "pos": [16.5, -60, 16.5], "tags": ["pale_man"], "zone": "garage" }
  ],
  "ambience": {
    "loop": "every_tunnel:ambient.garage.drone",
    "loop_volume": 0.6,
    "random": [
      { "sound": "every_tunnel:ambient.drip", "interval": [6, 18], "distance": [3, 12], "volume": 0.5 }
    ]
  },
  "entry": { "title": "Level 1", "subtitle": "", "sound": "every_tunnel:event.level_transition" },
  "intro": { "seconds": 25, "music": "every_tunnel:music.elevator" },
  "build": "garage:build"
}
```

### Claves de primer nivel

| Clave | Obligatoria | Por defecto | Significado |
|---|---|---|---|
| `name` | no | el id | Nombre legible para el log y `/everytunnel levels` |
| `order` | **sí** | | Orden de progresión: Nivel 1 = 1, Nivel 2 = 2. Entrar en un orden mayor cuenta como avanzar |
| `dimension` | no | `minecraft:overworld` | Id de la dimensión |
| `zones` | **sí**, al menos una | | Áreas del nivel |
| `spawn_points` | no | ninguno | Posiciones con nombre |
| `ambience` | no | silencio | Sonido de fondo |
| `entry` | no | nada | Lo que recibe el jugador al llegar desde un nivel anterior |
| `intro` | no | ninguna | La escena de partida nueva (el ascensor) |
| `build` | no | ninguna | Función que construye los bloques del nivel, ejecutada cada vez que se abre el mundo |

### Zonas

| Clave | Obligatoria | Significado |
|---|---|---|
| `id` | sí | Único dentro del nivel, `a-z0-9_` |
| `from`, `to` | sí | Dos esquinas opuestas, coordenadas de bloque enteras, en cualquier orden. Las dos esquinas se incluyen, como en `/fill` |
| `tags` | no | Cadenas que marcan zonas especiales (abajo) |
| `ambience` | no | El [ambiente](#ambiente) propio de la zona, que suena en lugar del del nivel mientras el jugador está en ella (la primera zona así, en el orden del archivo); `{}` es silencio |

Las zonas pueden solaparse. El **nivel actual** del jugador es el primer nivel (por
`order`) con una zona que lo contiene; el jugador está "en" cada zona que lo contiene, de
cualquier nivel.

### Puntos de aparición

| Clave | Obligatoria | Significado |
|---|---|---|
| `id` | sí | Único dentro del nivel |
| `pos` | sí | `[x, y, z]`, se permiten decimales (`.5` es el centro de un bloque) |
| `yaw`, `pitch` | no | Orientación en grados (yaw -360..360, pitch -90..90) |
| `tags` | no | Para qué sirve el punto (abajo) |
| `zone` | no | Id de una zona del mismo nivel de la que no sale lo que aparece aquí |

### Ambiente

| Clave | Significado |
|---|---|
| `loop` | Evento de sonido en bucle por todo el nivel (sin posición), o ausente |
| `loop_volume` | 0..1, por defecto 1 |
| `random[]` | Sonidos que suenan de vez en cuando alrededor del jugador |
| `random[].sound` | Id del evento de sonido |
| `random[].interval` | `[mín, máx]` segundos entre reproducciones (0,5..3600) |
| `random[].distance` | `[mín, máx]` bloques desde el jugador (0..16, el límite audible de Minecraft) |
| `random[].volume` | 0..1, por defecto 1 |

### Entrada

| Clave | Significado |
|---|---|
| `title` | Texto grande en pantalla ("Level 2"); vacío no muestra nada |
| `subtitle` | Texto más pequeño debajo |
| `sound` | Evento de sonido que suena en la cabeza del jugador |

### Intro

| Clave | Por defecto | Significado |
|---|---|---|
| `seconds` | 25 | Cuánto espera el jugador con las puertas cerradas (0..600) |
| `music` | ninguna | Evento de sonido de la canción |

### Build

Un id de función como `garage:build`. `LoadingHooks` ejecuta cada función distinta una vez
al arrancar el servidor (los niveles que se construyen juntos nombran la misma). La función
debe terminar con `/everytunnel rebuilt` para que el mod sepa que el mundo está listo.

## Etiquetas

### Etiquetas de zona

| Etiqueta | La usa | Efecto |
|---|---|---|
| `start` | `IntroHooks` | El inicio del nivel (el ascensor); aquí suena la intro de una partida nueva |
| `elevator_door` | `IntroHooks` | Los bloques de las puertas del ascensor, cerradas durante la intro y abiertas después |
| `shutter` | `LevelEntryHooks` | La abertura que una persiana cierra tras el jugador que llega del nivel anterior |
| `keycard_door` | `KeycardHooks` | Las puertas de hierro que abre un lector de tarjetas; una partida nueva las cierra y bloquea los lectores |
| `lights_stay` | `KeycardHooks` | Sus luces siguen encendidas al coger la tarjeta (el pozo de escaleras a B3) |
| `end` | `EndingHooks` | Entrar termina la demo |
| `exit` | (nadie aún) | Marca la salida de un nivel; reservada para sistemas futuros |
| `valve_door` | `ValveHooks` | Las puertas que abren las válvulas cuando todas están bien; una partida nueva las cierra |
| `observer_home` | `ObserverEntity` | La zona que patrulla el Observer y a la que vuelve (la biblioteca del nivel 3) |
| `book_hatch` | `BookHooks` | Las trampillas que abre el quinto libro devuelto (la del búnker del nivel 3); una partida nueva las cierra |
| `checkpoint` | (nadie aún) | Nivel 3: justo pasada la puerta del checkpoint, donde reiniciará una muerte |
| `collapse` | (nadie aún) | Nivel 3: al entrar, el techo del pasillo del búnker caerá detrás del jugador |
| `collapse_blocks` | (nadie aún) | Nivel 3: el tramo de pasillo que llenará el derrumbe |

### Etiquetas de punto de aparición

| Etiqueta | La usa | Efecto |
|---|---|---|
| `player` | `RespawnHooks`, `DeathHooks`, `NewGame` | El inicio del nivel: donde reinicia el jugador. Se usa el primero |
| `pale_man` | `PaleManSpawner` | Aquí aparece un Pale man la primera vez que un jugador entra en el nivel |
| `battery` | `LootSpawner` | Una batería en el suelo |
| `water` | `LootSpawner` | Una botella de agua |
| `keycard` | `LootSpawner` | La tarjeta |
| `checkpoint` | (nadie aún) | Nivel 3: donde reiniciará una muerte pasado el checkpoint |
| `observant_guard` | `ObserverHooks` | Nivel 3: el Observer, que aparece ahí una vez (en la biblioteca) |
| `box_spot` | `BoxHooks` | Nivel 3: un sitio donde puede estar una caja morada (una partida nueva pone diez al azar); en el suelo o sobre un mueble (y 2) |
| `box_shelf` | `BoxHooks` | Nivel 3: el pie (izquierda, abajo) de la estantería donde van las cajas |
| `book_spot` | `BookHooks` | Nivel 3: donde puede aparecer un libro cuando están las cajas (los libros de una partida van a cinco al azar), en la biblioteca: sobre un carrito o el fichero (y 2) o en el suelo |
| `book_return` | `BookHooks` | Nivel 3: un sitio de la mesa del bibliotecario, alrededor de la trampilla, al que vuelve un libro (cinco) |
| `valve` | `ValveHooks` | Una válvula del puzle del nivel (`valve_a` a `valve_e`: el `ValveBlock` de ese bloque, con su letra) |
| `note` | `NoteHooks` | Una nota en ese bloque (clavada en un `NoteBoardBlock` o un folio suelto, `NotePaperBlock`); su id nombra el texto, `assets/every_tunnel/notes/<en/es>/<id>.txt` |
| `water_source` | `WaterDispenserHooks` | Un dispensador de agua del que el jugador bebe cuantas veces quiera (la cafetería del nivel 3) |

Las etiquetas son cadenas libres: un sistema nuevo puede definir las suyas sin tocar el
parser.

### Ganchos del nivel 3

Del nivel 3 de momento solo está la estructura; su archivo ya tiene lo que leerán los
sistemas que vienen:

- **Zonas:** una por habitación (su id en snake case: `archive`, `meeting_room`...,
  `open_office_nw`, `open_office_ne`), `library`, el anillo en cuatro
  (`inner_corridor_north`, `_south`, `_west`, `_east`), `guard_area` (la biblioteca y el
  anillo juntos), `valve_corridor`, `checkpoint`, `checkpoint_door` (con la etiqueta `valve_door`), `antechamber`, `bunker_shaft`,
  `bunker_corridor`, `hatch` (sus trampillas, con la etiqueta `book_hatch`), una zona por cada hueco de puerta (`door_...`, `library_door_...`: cruzar una puerta nunca te saca del nivel), `collapse`, `collapse_blocks` y `end` (delante de la puerta a B4). Las
  escaleras desde B2 no pertenecen a ninguna zona, así que "Level 3" aparece al entrar en
  la antesala.
- **Puntos de spawn:** `player_start` (la antesala), `checkpoint`, `observant_guard`,
  `book_1` a `book_13` (en la biblioteca, `LIBRARY_LAYOUT["books"]`: sobre carritos, en el
  suelo, sobre el fichero), `box_1` a `box_25` (con la etiqueta
  `box_spot`, en la zona de su sala o del anillo; `BOX_PUZZLE` dice cuántos), `box_shelf` (en
  Storage), `book_return_1` a `_5` (en la mesa), `valve_a` a `valve_e`, la nota del electricista `pressure_line` y los documentos
  de la empresa (`NOTE_PAPERS`: un id de nota por habitación, un folio suelto en la mesa o el
  mostrador más cercano a sus puertas), todos con la etiqueta `note`, el loot (`LOOT`:
  `battery_1` a `_3`, con la etiqueta `battery`, en el suelo de Electrical) y
  `water_source_cafeteria` (con la etiqueta `water_source`, el dispensador de la cafetería,
  `WATER_SOURCES`).

## Cómo sigue el mod a los jugadores

```
LevelReloadListener --> LevelManager (instantánea inmutable de todos los niveles)
tick del servidor --> LevelTrackingHooks --> PlayerLocationTracker.update(pos)
                                                |
                                                v  LocationChange(anterior, actual)
                                           LevelTriggers.fire --> cada oyente registrado
```

- `PlayerLocationTracker` guarda un `PlayerLocation(levelId, zones)` por jugador y devuelve
  un `LocationChange` solo cuando algo cambia (`levelChanged`, `enteredZones`,
  `exitedZones`).
- Cada cambio también queda en el log, lo que ayuda al colocar zonas.
- `/everytunnel where` muestra tu nivel y zonas actuales; `/everytunnel levels` muestra
  todos los niveles cargados.

## Cómo se construye Noname Complex

Los niveles los genera `tools/map/map_generator.py` (ver
[Herramientas Python](tools.md)). Escribe:

- **`every_tunnel_level1_mod/`**, el datapack que usa el mod:
  - `data/every_tunnel/levels/level_1.json`, `level_2.json` y `level_3.json`;
  - `data/garage/functions/build.mcfunction`: fuerza la carga del área de los niveles 1 y 2 y programa
    `build_wait`, que pide otra comprobación a los 5 ticks y, cuando todas las esquinas
    están cargadas, ejecuta `build_go`: cancela esa comprobación, ejecuta `build_now` y libera
    el área (`garage:clear` funciona igual, con `clear_wait` y `clear_go`);
  - `build_now.mcfunction`: unos 12.000 comandos. Desactiva los drops de bloques, quita a
    los Pale men del área, coloca cada bloque (con los bloques del mod y sus estados
    exactos), restaura la gamerule y programa la segunda etapa, `level3_build`.
  - `level3_build.mcfunction` hace lo mismo con el nivel 3 en su propia área de carga
    forzada (`level3_build_wait` y luego `level3_build_go`, que ejecuta `level3_now`: unos
    2.000 comandos, los bloques unidos en rectángulos capa a capa) y termina con
    `everytunnel rebuilt`. Son dos etapas porque un área sobre los tres niveles pasaría
    de los 256 chunks que admite `/forceload`.
- **`every_tunnel_level1_vanilla/`**, los niveles 1 y 2 con bloques vanilla retexturizados para
  Minecraft sin mods (se construye solo al abrir el mundo).
- **`level_resources.zip`**, el resource pack del mundo: texturas generadas y sus mapas
  LabPBR, y los textos de los carteles (las ranuras de `SignTileBlock`). Se instala como
  `<mundo>/resources.zip`.

El generador instala el datapack del mod y el resource pack en `run/saves/noname_complex`
(y en la plantilla de desarrollo, si existe).

### Límites

- Cada etapa fuerza la carga de su área, que debe caber en 256 chunks (ahora 160 para los
  niveles 1 y 2 y 77 para el nivel 3).
- `SignTileBlock` tiene 4 orientaciones × 64 ranuras; quedan libres 8 de las 64.
- `/fill` está limitado a 32.768 bloques por comando y una función a 65.536 comandos.

## Añadir un nivel

Ver [Recetas](how-to.md#añadir-un-nivel).
