# Chapter 2, Nivel 1

*[English version](../en/chapter-2-level-1.md) · [Todas las páginas](README.md)*

El Nivel 1 del Chapter 2 es la fábrica, cuatro meses después del Chapter 1: un científico normal contratado en lugar del
que "se puso enfermo", tres actos de tareas, una reunión y sigilo, hasta el despacho del supervisor. Se construye por
fases (de la 0 a la 10), de una en una, en la rama **privada** `chapter-2/level-1` (solo se sube al remoto `private`,
nunca a `origin`) hasta que se publiquen los Niveles 1 y 2 del capítulo. **Dónde está: fase 1, recursos, textos y modelos.** Todavía
no se puede jugar: el nivel tiene su máquina de estados, sus archivos de datos, sus voces, sus personas y sus comandos de
depuración, y nada en el mundo los usa.

## La máquina de estados

`core.ch2l1.Ch2L1State` tiene diez estados, en el orden en que se juegan. El estado actual de cada jugador va en su
progreso (`PlayerProgress.ch2l1State`, que `CardinalProgress` guarda como `ch2_l1_state`), así que sobrevive a salir del
mundo, a una muerte y a una sesión nueva. Un jugador que nunca ha empezado el nivel está en `ACT1_CLOCKIN`; una partida
nueva (`ProgressService.reset`) devuelve a todos ahí.

| Estado | Significado |
|---|---|
| `ACT1_CLOCKIN` | Recién llegado a la fábrica: falta fichar en el reloj |
| `ACT1_TASK1`, `ACT1_TASK2`, `ACT1_TASK3` | Las tres tareas del acto 1, en orden: registrar las muestras, calibrar el analizador, barrer el pasillo oeste |
| `ACT2_FOLLOW` | El compañero se ha acercado para llevar al jugador a las taquillas |
| `ACT2_MEETING` | La reunión en las taquillas (una cinemática) |
| `ACT3_EXPLORE` | Sigilo por el laboratorio auxiliar, el pasillo del personal y el despacho |
| `ACT3_CUTSCENE` | La cinemática del despacho, desde que se cierra el segundo papel |
| `ACT3_OVERTIME` | Después del despacho, con el pase de horas extra sin cobrar: el camino a la zona sellada está abierto |
| `DONE` | El jugador ha cruzado al Nivel 2 |

`platform.Ch2L1` es la única puerta al estado: `setState(player, state, reason)` lo guarda, escribe en el log `Chapter 2
Level 1: <jugador> goes A -> B (<motivo>)` y avisa a los oyentes; `skip(player)` pasa al siguiente. Un sistema del nivel
se registra una vez con `Ch2L1.onStateChange` y hace lo que significa entrar en un estado (un objetivo, una escena, una
línea del altavoz); la máquina de estados no sabe nada de eso. Por eso un paso se alcanza jugando o por comando con el
mismo resultado.

## Los archivos de datos

Todo lo ajustable es JSON, no código: cambiar un ritmo, una ruta o un texto es editar un archivo.

| Archivo | Contiene | Quién lo lee |
|---|---|---|
| `anchors.json` | Los lugares con nombre: puntos (una posición y hacia dónde mira) y zonas (una caja) | `Ch2L1AnchorsParser`, con tipos |
| `speakers.json`, `tasks.json`, `patrols.json`, `npcs.json` | Los altavoces, las tareas, las patrullas, los personajes | Vacíos (`{}`) hasta que la fase que los construye les dé su lector |
| `cutscenes/meeting.json`, `cutscenes/office.json` | Las dos cinemáticas | Lo mismo |

El jar lleva los valores por defecto, en `data/every_tunnel/ch2/l1/`. **Para ajustar sin reconstruir**, pon un archivo del
mismo nombre en la carpeta de configuración del juego, `config/every_tunnel/ch2/l1/` (en una ejecución de desarrollo,
`run/config/...`), y ejecuta `/et reload`. El archivo de `config` gana mientras sea utilizable (JSON válido, un objeto); si
está roto se avisa, en el log y en la respuesta del comando, y se usa la copia del jar, así que una errata nunca deja el
nivel sin datos. Un archivo sin copia utilizable da uno vacío y un aviso. Los archivos se leen al arrancar y con `/et
reload`; no siguen al `/reload` de vanilla.

`anchors.json` se parece al archivo de un nivel (véase [Niveles](levels.md)):

```json
{
  "dimension": "minecraft:overworld",
  "anchors": [
    { "id": "spawn_clockin", "pos": [-48.5, 65, -135.5], "yaw": -90, "pitch": 0, "provisional": true }
  ],
  "zones": [
    { "id": "speaker_lab", "from": [-40, 64, -146], "to": [3, 69, -127], "provisional": true }
  ]
}
```

Los sistemas piden un lugar por su nombre y nunca por coordenadas, así que mover el mapa es editar este único archivo.
Un id (a-z, 0-9, `_`) es único entre puntos y zonas. **`provisional`** marca un lugar aún no medido en el mapa final. Los
del jar son todos provisionales: están en sitios libres del primer nivel del Chapter 1, para que `goto` te deje en un
lugar real. El mapa se generará con una herramienta Python, como el del Chapter 1, y esa herramienta escribirá
`anchors.json` (el contenido generado no se edita a mano una vez existe su herramienta); hasta entonces se escribe a mano.

## Voces y subtítulos

El nivel tiene 32 líneas de voz (ElevenLabs, de los MP3 del usuario: la excepción con IA de [Audio](audio.md)): el
compañero (`follow_01` a `03`, y casi todas las `meeting_*`), los trabajadores A y B de la unión (las líneas `_a` y `_b` de
la reunión) y Rowan, por los altavoces (`rowan_loyal_a1` a `a6`) y en su despacho (`rowan_office_c1` a `c8`).
`tools/sounds/ch2_l1_voices.py` las convierte (Ogg mono, una ganancia para cada uno de los dos grupos, el de la unión y el
de Rowan, de modo que cada uno quede a -18 LUFS como las líneas del director), corta cada una en fragmentos de subtítulo
por sus pausas y escribe `cutscene/ch2_l1_voices.json`; `export.py` hace de cada una un evento de
sonido, `voice.<id>` (`/playsound every_tunnel:voice.follow_01 voice @s`). Todavía no las reproduce nada: lo harán los
sistemas de las fases siguientes.

**Los subtítulos están siempre activos.** `client.hud.Ch2L1Subtitles` escucha al gestor de sonido, así que cuando empieza
a sonar una voz del nivel, la haya iniciado quien sea, sus fragmentos salen sobre el HUD, cada uno desde su momento: el
nombre del hablante en gris, luego el texto en blanco, sobre una franja oscura, y el último fragmento se queda un momento
tras acabar la voz. No dependen del ajuste de subtítulos del juego (estos sonidos no llevan `subtitle` en `sounds.json`).
En una cinemática el HUD está oculto y la cinemática dibuja los suyos.

Todas las claves de lang están bajo `every_tunnel.ch2.l1.`. **El texto de una línea de voz es una sola clave para toda la
línea**, el inglés y el español del propio autor: `<id>` (`follow_01`, `rowan_office_c8`; las líneas `_a` y `_b` de la reunión
llevan solo el número, `meeting_03` para `meeting_03_a`). Los **fragmentos de subtítulo son rangos de frases** de ese texto,
no textos propios: `ch2_l1_voices.json` dice que desde los 2,04 s el fragmento muestra la frase 1 en adelante, y el cliente
divide la línea traducida en frases y muestra ese rango, así que el tiempo es el mismo en todos los idiomas y las palabras
de una línea se editan solo en los archivos de lang. Una frase termina en `.`, `?` o `!` seguido de un espacio
(`Ch2L1Sentences`, que es también la regla de la herramienta), y **el inglés y el español de una línea deben tener el mismo
número de frases**: si no, `Ch2L1VoicesTest` falla. La herramienta lee el inglés de `en_us.json` y solo reescribe el tiempo de
los fragmentos: una línea rápida (menos de un segundo por fragmento) se muestra entera.
Las demás claves: `speaker.<quién>` (`coworker`, `worker_a`, `worker_b`, `rowan_loudspeaker`, `rowan`), `objective.<paso>`
(los textos del panel de objetivos, para la fase 2), `restricted_door` (definitiva) y los siete documentos (`notice`,
`union_list`, `chamber_log`, `authorization`, `medical_file`, `contract_14`, `painting_plaque`). **Los textos de los documentos
son provisionales hasta la fase 6**, cuando se diseñen los papeles y su pantalla de lectura.

## Las personas

`FactoryNpcEntity` es un solo tipo de entidad para todos: Rowan, el compañero, los trabajadores. Su `skin` (sincronizada con
el cliente) dice quién es: `rowan`, `coworker`, `worker_a`, `worker_b` y `generic_1` a `generic_3` (`Ch2L1Skins`: seis caras
en la unión, tres de las cuales hablan, y Rowan). `FactoryNpcRenderer` dibuja el modelo del jugador con brazos clásicos de
4 px y la capa base de la skin (`textures/entity/npc/<skin>.png`). La skin de Rowan es del autor, copiada tal como llegó;
las de los seis trabajadores las dibuja `tools/entity/workers.py` (bata blanca, una tarjeta de un color cada uno, una cara
cada uno), que también escribe una hoja de previews con la de Rowan (`tools/entity/output/`, fuera de git).
`Ch2L1SkinsTest` comprueba que cada skin sea de 64x64, tenga brazos clásicos y deje vacía su segunda capa. Una criatura
**mantenida** (véase [Contribuir](contributing.md#criaturas)). Por ahora solo se quedan quietas donde las pone
`/et ch2 l1 npc`; sus rutinas llegan en la fase 4.

## Comandos de depuración

`platform.Ch2L1Commands`, nivel de operador 2, **solo en ejecuciones de desarrollo** (como explica
[Depuración](debugging.md) para `/everytunnel`):

| Comando | Qué hace |
|---|---|
| `/et reload` | Vuelve a leer los archivos JSON del nivel y dice qué falla en ellos |
| `/et ch2 l1 anchors` | Todas las anclas con sus coordenadas, las provisionales marcadas |
| `/et ch2 l1 goto <ancla>` | Te lleva a un punto, o al centro de una zona, un bloque por encima de su suelo |
| `/et ch2 l1 state` | Muestra tu estado y el siguiente |
| `/et ch2 l1 state <estado>` | Te pone en ese estado (sin importar mayúsculas, `-` por `_`), como si hubieras llegado jugando |
| `/et ch2 l1 skip` | Te pasa al siguiente estado |
| `/et ch2 l1 npc <skin>` | Pone delante de ti a una persona de la fábrica, para ver su skin (`rowan`, `coworker`, `worker_a`, `worker_b`, `generic_1` a `generic_3`) |
| `/et ch2 l1 npc clear` | Las quita todas |

Por ahora `state` y `skip` solo cambian el estado. Cuando una fase dé efectos a un estado (un objeto, un objetivo, un
personaje), también se ejecutarán con el comando, que es lo que permite probar cada fase sin jugar las anteriores.

## Añadir el sistema de una fase

1. Kotlin puro en `core.ch2l1` para sus reglas y su lector de JSON, con tests unitarios (`Ch2L1DataTest` comprueba que
   cada archivo del jar se lee bien).
2. Darle forma y un lector con tipos a su archivo de `data/every_tunnel/ch2/l1/`; leer el documento con
   `Ch2L1DataLoader.data.document("<nombre>")` y no guardarlo nunca, para que `/et reload` lo alcance.
3. Un hook en `platform` que se registre con `Ch2L1.onStateChange` y, si tiene momentos propios, escuche el tick del
   servidor; solo actúa en mundos con niveles (`WorldRules`).
4. Textos en `en_us.json` y `es_es.json` bajo `every_tunnel.ch2.l1.<id>`; esta página y el [mapa del código](code-map.md),
   en los dos idiomas.

## Tests

`Ch2L1StateTest` (el orden, `next`, `parse`), `Ch2L1AnchorsParserTest` (el formato, cada tipo de problema) y `Ch2L1DataTest`
(los archivos del jar, qué copia gana, los respaldos), `Ch2L1VoicesTest` (las 32 líneas, sus eventos de sonido, Ogg y
fragmentos, los textos en los dos idiomas) y `Ch2L1SkinsTest` (el formato de las skins), todos en `core/ch2l1`;
`PlayerProgressTest` cubre el estado en el progreso.
