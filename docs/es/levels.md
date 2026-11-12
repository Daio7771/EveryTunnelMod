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
| `safe` | no | `false` | Aquí nada hace daño (salvo `/kill` y el vacío); la sed y la batería no bajan y correr no cuesta nada |

### Zonas

| Clave | Obligatoria | Significado |
|---|---|---|
| `id` | sí | `a-z0-9_`. Si se repite, sus cajas forman una sola zona: un jugador en cualquiera de ellas está en ella, y pasar de una a otra no es un cambio (ponles las mismas etiquetas) |
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
| `title` | Texto grande en pantalla: una clave de traducción (`title.every_tunnel.level_2`, "Level 2" en `en_us.json`; un texto que no sea clave se muestra tal cual); vacío no muestra nada |
| `subtitle` | Texto más pequeño debajo |
| `sound` | Evento de sonido que suena en la cabeza del jugador |
| `manual` | `true`: el título y el sonido esperan al código del propio nivel en vez de salir al entrar andando (el del nivel 5, al abrirse su esclusa) |

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
| `end` | `EndingHooks` | Entrar termina el juego (ningún nivel la tiene ahora: termina en la falsa salida del nivel 5) |
| `big_door_shut` | `LoungeClosingHooks` | Al entrar se cierra de golpe la puerta grande, una vez abierta |
| `cinema_wakes` | `LoungeClosingHooks` | Al entrar durante el apagón empieza el ojo del Cinema |
| `b4_door_open` | `LoungeHooks` | Entrar abre la puerta a B4 |
| `lounge_build` | `LoungeHooks` | Entrar cierra esa puerta, borra el nivel 3 y construye el 4 |
| `lounge_door` | `LoungeHooks` | Sus puertas se abren cuando el nivel 4 está en pie (la de Reception) |
| `lab_build` | `LabHooks` | Entrar empieza la construcción del nivel 5 (el tramo oscuro del nivel 4) |
| `lab_airlock` | `LabHooks` | La esclusa del nivel 5: entrar cierra la puerta de atrás (`lab_airlock_rear`) y, cuando el nivel está en pie, abre la de delante |
| `lab_airlock_rear` | `LabHooks` | La puerta de atrás de la esclusa: abierta hasta que el jugador entra |
| `lab_welcome` | `LabHooks` | La puerta de delante de la esclusa, en la pared del Lobby: cerrada hasta que el nivel está en pie |
| `lab_door` | `LabDoorHooks` | Una puerta corredera bloqueada del nivel 5: la abre una palanca, o salir de la Office la de la ruta sur |
| `restart_point` | `ContinueGame` | Entrar hace checkpoint el punto de aparición del nivel con la misma etiqueta (el Lab hall del nivel 5) |
| `exit` | (nadie aún) | Marca la salida de un nivel; reservada para sistemas futuros |
| `valve_door` | `ValveHooks` | Las puertas que abren las válvulas cuando todas están bien; una partida nueva las cierra |
| `observer_home` | `ObserverEntity` | La zona que patrulla el Observer y a la que vuelve (la biblioteca del nivel 3) |
| `book_hatch` | `BookHooks` | Las trampillas que abre el quinto libro devuelto (la del búnker del nivel 3); una partida nueva las cierra |
| `checkpoint` | (nadie aún) | Nivel 3: justo pasada la puerta del checkpoint, donde reiniciará una muerte |
| `collapse` | `CollapseHooks` | Nivel 3: al entrar, el techo del túnel del búnker cae detrás del jugador (una vez por partida) |
| `collapse_blocks` | `CollapseHooks` | Nivel 3: el tramo de túnel que el derrumbe llena de escombros |
| `observer_never` | `ObserverEntity` | El pozo y el túnel del búnker del nivel 3: el Observer no ve a nadie ahí, así que nunca baja detrás de nadie |
| `cloud` | `DistortionHooks` | Nivel 5: donde vienen las Distortion Clouds (una cada 2 a 4 minutos pasados en esas zonas) y donde pueden aparecer |
| `first_cloud` | `LabHallHooks` | El Lab hall del nivel 5: al entrar, un golpe de cuerdas y la primera Cloud, una vez por partida |
| `cloud_game` | `CloudGameHooks` | El pasillo de entrada del nivel 5, desde su línea de salida: al entrar empieza el juego de la Cloud (como el de Halt), al salir se acaba |
| `dead_battery` | `ResourceHooks` | Ahí la batería está vacía y ninguna pila sirve; al salir vuelve a estar llena |
| `no_sprint` | `ResourceHooks` | Ahí nadie esprinta |
| `no_checkpoint` | `ContinueGame` | Ahí no se guarda punto de control: el del nivel espera a su primera zona sin la etiqueta |
| `back_to_key` | `DeathHooks` | Morir ahí devuelve al nivel 4 junto a la mesa de la Master Key, como antes de cogerla |
| `deep_water` | `DeepWaterHooks` | Las piscinas del nivel 5: caer en su agua honda arrastra al jugador al fondo, y se ahoga |
| `chemical_room`, `lab_grate`, `lab_drain` | `ChemicalHooks` | Nivel 5: la sala cuyas luces de alarma giran, la rejilla que disuelve el disolvente, la sala cuya trampilla se abre después |

### Etiquetas de punto de aparición

| Etiqueta | La usa | Efecto |
|---|---|---|
| `player` | `RespawnHooks`, `DeathHooks`, `NewGame` | El inicio del nivel: donde reinicia el jugador. Se usa el primero |
| `pale_man` | `PaleManSpawner` | Aquí aparece un Pale man la primera vez que un jugador entra en el nivel |
| `battery` | `LootSpawner` | Una batería en el suelo |
| `wrench`, `detector` | `LootSpawner` | La llave inglesa y el detector de radiación del nivel 5, en el suelo |
| `lab_lever` | `LabDoorHooks` | Una palanca del nivel 5 en ese bloque; si se llama `lever_<id de la zona de la puerta>`, abre esa puerta |
| `restart_point` | `ContinueGame`, `DeathHooks` | Donde reinician una muerte o CONTINUE una vez se ha entrado en su zona (el Lab hall del nivel 5) |
| `hunters` | `DistortionHooks` | Nivel 5: un grupo de tres Hunters, alrededor de ese punto, mirando hacia donde mira |
| `distortion_junior` | `DistortionHooks` | Nivel 5: un Distortion Junior, flotando ahí |
| `harmless` | `DistortionHooks` | Con `hunters`: esos Hunters empujan y chillan pero nunca hacen daño (la capa 1 del nivel 5) |
| `fuse_5a`, `fuse_10a`, `fuse_15a`, `fuse_20a`, `fuse_30a` | `LootSpawner` | Nivel 5: un fusible de ese amperaje ahí, para la caja de fusibles |
| `lab_figure`, `lab_figure_exit` | `LabFigureHooks` | Nivel 5: donde está la figura que se ve desde el Lobby, una vez por partida, y adónde va cuando la ven |
| `subject` | `SubjectHooks` | Nivel 5: un sujeto de pruebas inmóvil; su id `subject_<1..4>` dice qué fase |
| `terminal` | `TerminalHooks` | Nivel 5: un ordenador en ese bloque; su id dice qué muestra su pantalla |
| `cloud_game` | `CloudGameHooks` | Nivel 5: `cloud_game_start` y `cloud_game_end`, los extremos de la línea por la que va el juego del pasillo de entrada |
| `key_table` | `DeathHooks` | Nivel 4: junto a la mesa de la Master Key, adonde vuelve quien muere en el pasillo de entrada del nivel 5 |
| `reagent_1` a `reagent_4` | `LootSpawner`, `ChemicalHooks` | Nivel 5: un frasco de reactivo ahí; una mezcla fallida lo devuelve |
| `alarm_hunters`, `alarm_junior` | `ChemicalHooks` | Nivel 5: donde la alarma de la sala de químicos trae tres Hunters, un Junior |
| `food_plate`, `food_popcorn`, `food_snack` | `LootSpawner` | Comida, sobre muebles |
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
| `water_source` | `WaterDispenserHooks` | Un dispensador de agua del que el jugador bebe cuantas veces quiera (la cocina del nivel 3) |

Las etiquetas son cadenas libres: un sistema nuevo puede definir las suyas sin tocar el
parser.

### Ganchos del nivel 3

Del nivel 3 de momento solo está la estructura; su archivo ya tiene lo que leerán los
sistemas que vienen:

- **Zonas:** una por habitación (su id en snake case: `archive`, `meeting_room`...,
  `open_office_nw`, `open_office_ne`), `library`, el anillo en cuatro
  (`inner_corridor_north`, `_south`, `_west`, `_east`), `guard_area` (la biblioteca y el
  anillo juntos), `valve_corridor`, `checkpoint`, `checkpoint_door` (con la etiqueta `valve_door`), `antechamber`, `bunker_shaft`,
  `bunker_corridor`, `hatch` (sus trampillas, con la etiqueta `book_hatch`), una zona por cada hueco de puerta (`door_...`, `library_door_...`: cruzar una puerta nunca te saca del nivel), `collapse`, `collapse_blocks`, `b4_approach` (delante de la puerta a B4, con la etiqueta `b4_door_open`) y `b4_door` (su celda). El pasillo de diplomas que hay detrás es del nivel 4 (`ch1_l4_diplomas`, y `ch1_l4_diplomas_build` con la etiqueta `lounge_build`). Las
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
  `water_source_cafeteria` (con la etiqueta `water_source`, el dispensador de la cocina,
  `WATER_SOURCES`).

### Nivel 5

El nivel 5, el laboratorio, se construye en juego (`LabHooks`), sus tres capas (plano, herramientas
y aspecto: [Herramientas en Python](tools.md)). Su archivo nombra las zonas como el plano; una zona de varias cajas
repite su id:

- **Capa 1:** `entry_corridor` (desde el marco de acero del tramo oscuro del nivel 4, el juego de la Cloud: con
  las etiquetas `dead_battery`, `no_sprint`, `no_checkpoint` y `back_to_key`), `cloud_game` (lo mismo desde su línea
  de salida, con la etiqueta `cloud_game`), `airlock` (con la etiqueta `lab_airlock`), `lobby`,
  `workshop`, `waiting`, `lockers`, `layer1_corridor` (el anillo, seis cajas), `layer1_stairs` (las dos salas
  de escaleras y el tramo entre ellas).
- **Capa 2:** `subjects_corridor`, `s1` a `s4` (las salas de pruebas selladas), `lab_hall` (con las etiquetas
  `restart_point`, `cloud` y `first_cloud`), `north_route` (dos cajas) y `south_route` (tres), ambas con la etiqueta
  `cloud`, `office`, `storage`, `chemical_room` (con la etiqueta `chemical_room`), `shortcut`, `drain_hatch` (con la
  etiqueta `lab_drain`).
- **Capa 3, el drenaje:** `drain_ladder` (el pie de la escalera de mano, con la etiqueta `drain_bottom`),
  `drain_tunnels` (once cajas, con las etiquetas `cloud` y `cloud_often`), `junction` (con las etiquetas
  `restart_point`, `cloud`, `cloud_often` y `deep_water`), `hideout`, `pump_room`, `cistern` (con la etiqueta `deep_water`), `office_stairs` (el pozo de la escalera de
  caracol), `anteroom`, `director_office` (con la etiqueta `director_office`), `false_exit` (el pasillo que sale del despacho,
  con la etiqueta `false_exit`) y `false_exit_pit` (el pozo bajo su final, con la misma etiqueta), `drain_shaft` (el hueco de la escalera de mano hasta la
  trampilla). La zona de un espacio baja hasta su agua (los canales, las piscinas), así que un jugador dentro sigue en el nivel.
- **Aberturas,** cada una con su zona: `entry_frame` (el marco: cruzarlo es entrar en el nivel 5, con las
  etiquetas `no_checkpoint` y `back_to_key`),
  `airlock_rear` (con la etiqueta `lab_airlock_rear`), `welcome_door` (con la etiqueta `lab_welcome`), los huecos de puerta
  (`workshop_door`, `waiting_door`, `office_door_west`, `office_door_east`), las puertas
  correderas bloqueadas (`stairs_door`, `south_door`, `shortcut_door`, con la etiqueta `lab_door`) y `hatch_grate`
  (barrotes de hierro, con la etiqueta `lab_grate`); en la capa 3 los huecos de puerta `hideout_door`, `pump_door`,
  `cistern_door`, `spiral_exit` (de lo alto de la escalera de caracol a la antesala), las puertas del despacho
  `office_door` (la puerta doble del director) y `office_exit`, y `exit_door` (la puerta EXIT cerrada del
  nivel 1 al final del pasillo). Las salas de pruebas no tienen: un ventanal cada una (`WINDOWS`).
- El Lab hall y la Junction tienen la etiqueta `restart_point`.
- **Puntos de aparición:** `player_start` (el Lobby, junto a la entrada), `checkpoint` (el Lab hall, con la
  etiqueta `restart_point`), `cloud_game_start` y `cloud_game_end` (con la etiqueta `cloud_game`, la línea por la que
  va el juego del pasillo de entrada), `lever_stairs_door` y `lever_shortcut_door` (con la etiqueta `lab_lever`), y los de la
  capa 1 (`tools/map/lab/furnish.py`): `wrench` y `detector` (el banco central del Workshop), los fusibles `fuse_5a`,
  `fuse_10a`, `fuse_20a`, `fuse_30a` (con la misma etiqueta), las fichas de la pizarra de seguridad `ch1_l5_card_*` (etiqueta `note`, cada una en la celda de su columna en la fila de abajo),
  `hunters_fuse_box` (etiquetas `hunters` y `harmless`), `lab_figure` y `lab_figure_exit`; y los de la capa 2:
  `subject_1` a `subject_4` (etiqueta `subject`), las fichas `ch1_l5_card_s1` a `_s4`, los ordenadores `terminal_*`
  (etiqueta `terminal`, entre ellos `terminal_storage`), `reagent_1` a `reagent_4` (con la misma etiqueta), el loot
  `snack_lockers`, `water_lockers`, `battery_waiting`, `battery_office`, `snack_storage_*`, `water_storage_*`,
  `battery_storage_*` (etiquetas `food_snack`, `water`, `battery`), `junior_north` (etiqueta
  `distortion_junior`), `hunters_storage` (etiqueta `hunters`), `alarm_hunters` y `alarm_junior` (con la misma etiqueta);
  y los de la capa 3: `checkpoint_junction` (etiqueta `restart_point`), `junior_junction` (etiqueta
  `distortion_junior`), `hunters_cistern` (etiqueta `hunters`), `dead_guard` (con la misma etiqueta), la nota del
  guardia `ch1_l5_note_guard` (etiqueta `note`) y el loot `battery_pump_1`, `battery_pump_2`, `snack_pump`,
  `water_pump`, `snack_hideout`.

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
  - `data/every_tunnel/levels/level_1.json` a `level_5.json`;
  - `level4_begin` y `level4_step_<n>`, `level5_begin` y `level5_step_<n>`: los niveles 4 y 5, construidos
    en juego por pasos pequeños (`LoungeHooks`, `LabHooks`);
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
