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

### Etiquetas de punto de aparición

| Etiqueta | La usa | Efecto |
|---|---|---|
| `player` | `RespawnHooks`, `DeathHooks`, `NewGame` | El inicio del nivel: donde reinicia el jugador. Se usa el primero |
| `pale_man` | `PaleManSpawner` | Aquí aparece un Pale man la primera vez que un jugador entra en el nivel |
| `battery` | `LootSpawner` | Una batería en el suelo |
| `water` | `LootSpawner` | Una botella de agua |
| `keycard` | `LootSpawner` | La tarjeta |

Las etiquetas son cadenas libres: un sistema nuevo puede definir las suyas sin tocar el
parser.

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

Los dos niveles de la demo los genera `tools/map/map_generator.py` (ver
[Herramientas Python](tools.md)). Escribe:

- **`every_tunnel_level1_mod/`**, el datapack que usa el mod:
  - `data/every_tunnel/levels/level_1.json` y `level_2.json`;
  - `data/garage/functions/build.mcfunction`: fuerza la carga del área del mapa y programa
    `build_wait`, que cada 5 ticks comprueba si las esquinas están cargadas y entonces
    ejecuta `build_now`;
  - `build_now.mcfunction`: unos 12.000 comandos. Desactiva los drops de bloques, quita a
    los Pale men del área, coloca cada bloque (con los bloques del mod y sus estados
    exactos), restaura la gamerule y termina con `everytunnel rebuilt`.
- **`every_tunnel_level1_vanilla/`**, el mismo nivel con bloques vanilla retexturizados para
  Minecraft sin mods (se construye solo al abrir el mundo).
- **`level_resources.zip`**, el resource pack del mundo: texturas generadas y sus mapas
  LabPBR, y los textos de los carteles (las ranuras de `SignTileBlock`). Se instala como
  `<mundo>/resources.zip`.

El generador instala el datapack del mod y el resource pack en `run/saves/noname_complex`
(y en la plantilla de desarrollo, si existe).

### Límites

- La construcción fuerza la carga de todo el mapa, así que debe caber en 256 chunks (ahora
  unos 180).
- `SignTileBlock` tiene 4 orientaciones × 64 ranuras; quedan libres 9 de las 64.
- `/fill` está limitado a 32.768 bloques por comando y una función a 65.536 comandos.

## Añadir un nivel

Ver [Recetas](how-to.md#añadir-un-nivel).
