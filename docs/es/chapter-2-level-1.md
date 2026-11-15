# Chapter 2, Nivel 1

*[English version](../en/chapter-2-level-1.md) · [Todas las páginas](README.md)*

El Nivel 1 del Chapter 2 es la fábrica, cuatro meses después del Chapter 1: un científico normal contratado en lugar del
que "se puso enfermo", tres actos de tareas, una reunión y sigilo, hasta el despacho del supervisor. Se construye por
fases (de la 0 a la 10), de una en una, en la rama **privada** `chapter-2/level-1` (solo se sube al remoto `private`,
nunca a `origin`) hasta que se publiquen los Niveles 1 y 2 del capítulo. **Dónde está: fase 2B, el mapa.** Todavía
no se puede jugar como nivel (no tiene tareas, mobiliario ni gente), pero su mapa se construye y se puede meter a un jugador en
él: sus reglas se le aplican y el reloj de fichar le pasa a la primera tarea.

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
Un id (a-z, 0-9, `_`) es único entre puntos, zonas y caminos (un camino es una línea de puntos, la ruta de una patrulla). **`provisional`** marca un
lugar cuyo mobiliario o rutina está aún por diseñar (el terminal, la gente de la columna, los papeles, las patrullas). El
`anchors.json` del jar lo **escribe el generador del mapa** (véase [El mapa](#el-mapa)): no lo edites a mano; para ajustar un
lugar, pon una copia editada en la carpeta de config.

## El mapa

El mapa del nivel lo **dibuja el autor** en papel, con tamaños relativos; `tools/map/ch2l1/generate.py` convierte ese dibujo
en bloques (`level.py` guarda qué hay dónde, `preview.py` el dibujo). Escribe `map.json`, el plan que construye el mod, y
`anchors.json`, los lugares con nombre en coordenadas del mundo, y `generate.py --install` mete los dos en
`data/every_tunnel/ch2/l1/` (en git); `tools/map/ch2l1/output/level_preview.png` es el plano visto desde arriba (fuera de git).
**Ninguno de los dos JSON se edita a mano**: se cambia `level.py` y se vuelve a ejecutar la herramienta. Sus comprobaciones la
paran si una puerta no une dos espacios, si un ancla no tiene sitio para estar de pie o si a un espacio restringido se llega
por una puerta libre.

| Espacio | X × Z (bloques) | Aire de alto |
|---|---|---|
| Entrada (media elipse, con tres puertas) | 42 × 9 | 9 |
| Pasillo principal | 11 × 36 | 5 |
| LAB | 30 × 36 | 6 |
| WC (oeste) · Taquillas (con una columna de 2 × 2) | 5 × 5 · 8 × 11 | 4 |
| Anillo, de 4 de ancho, alrededor del comedor (46 × 25 con sus muros) · Comedor | 4 · 36 × 15 | 4 · 6 |
| Laboratorio auxiliar (restringido) | 14 × 24 | 5 |
| Pasillo autorizado · Seguridad · Despacho · WC (restringidos) | 46 × 4 · 13 × 6 · 12 × 7 · 6 × 3 | 4 |
| Pasillo "Nivel 2", tras la puerta sellada | 11 × 6 | 4 |

El aspecto es el del laboratorio del prólogo, limpio y moderno: hormigón blanco, suelo gris claro, primera hilada de muro en gris
oscuro, cristal (la pared del laboratorio auxiliar hacia el tramo oeste del anillo) y las lámparas de techo del laboratorio (152).
El LAB, los pasillos y el laboratorio auxiliar se agrandaron tras el primer recorrido del nivel (las medidas del dibujo eran
relativas). Hay 21 puertas, todas propias del mod (ninguna es de vanilla): **libres**, la puerta de laboratorio
(`factory_door`: blanca, con una ventana alta; las dos hojas de un par se abren y se cierran a la vez a mano, `LinkedDoorBlock`,
con las bisagras en los bordes exteriores; la del anillo al pasillo autorizado también es libre, pero es
la entrada a una zona prohibida: el círculo rojo del dibujo); **cerradas con llave**, la puerta de emergencia gris (`emergency_door`), que las manos no
abren, para el laboratorio auxiliar, el despacho y la puerta sellada (las fases siguientes las abren con la credencial, el
código y la tarjeta de Rowan); y una más igual en el extremo este del pasillo que no abre a nada. Las tres puertas de la entrada dan al
pasillo principal y al LAB, y el reloj de fichar cuelga de la pared sur de la entrada.

**Dónde y cómo.** El plan se levanta en (1024, 64, 1024) del mundo del mapa, libre de los niveles del Chapter 1 y de la sala del
prólogo. `platform.Ch2L1Build` lo pone ahí a razón de 8.000 celdas por tick, con los chunks cargados (un segundo o dos, sin
tirones), vaciando antes su caja; recuerda la huella del plan que construyó (`data/every_tunnel_ch2_l1.dat`), así que
`Ch2L1.enter` solo construye el nivel si falta o si el plan cambió. Cada bloque se busca antes de tocar el mundo; un plan con
un bloque o un estado que el juego no conoce falla con un mensaje y deja el mundo como estaba. `/et ch2 l1 build` construye de
nuevo sobre lo que hay, y **`/et ch2 l1 unbuild` vacía la zona: hazlo antes de empaquetar el mundo en un jar** (`packMapWorld`
comprime también los archivos de región, y el mapa es del Chapter 2, no del mundo del Chapter 1).

**Todavía no hay:** mobiliario (llega con la fase que lo necesita: el terminal y el analizador en la 3, las taquillas en la 4 y
la 5, el escritorio y los papeles en la 6), los carteles de texto ("NIVEL 2", "Acceso restringido", los nombres de las salas: el
plan no puede llevar los datos de un bloque de texto) y el teclado del despacho (un bloque de la fase 6, con su ancla
`office_keypad`). Tampoco está el paso del prólogo al nivel: `enter` se llama a mano.


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

## Las reglas del nivel, el reloj y los objetivos

**Las reglas siguen al jugador, no al lugar.** Un jugador está *activo* en el nivel (`PlayerProgress.ch2l1Active`, guardado
con el estado y enviado al cliente por Cardinal; `Ch2L1.active`) desde `Ch2L1.enter` hasta `Ch2L1.leave`. Mientras lo está:

| Regla | Dónde está |
|---|---|
| Nada le hace daño (solo cuentan `/kill` y el vacío), la sed y la batería no bajan, **correr es gratis** (sin resistencia) | `SafeLevelHooks.isSafe`, que consultan `ResourceHooks` y el evento de daño |
| **Sin linterna**: su tecla no hace nada | `FlashlightHooks.available` |
| **Nada de lo que hace genera ruido** para la IA (correr incluido) | `NoiseHooks.makesNoise` |
| **Sin barras de estado**: las de vanilla siguen ocultas y las del mod (resistencia, sed, batería) no se dibujan; la barra de objetos y la mira se quedan | `StatusHud.render` |

Se aplican a jugadores en Supervivencia o Aventura de un mundo con niveles (`ResourceHooks.appliesTo`), no a Creativo. `enter`
además vacía los bolsillos (el nuevo empleado no tiene nada del Chapter 1), apaga la linterna, pone el estado en
`ACT1_CLOCKIN`, vuelve a poner en rojo los relojes y teletransporta al ancla `spawn_clockin`. Si el mapa no está construido, `enter` lo construye
primero (un segundo o dos) y el jugador entra cuando termina; el nuevo juego del capítulo lo llamará más adelante.

**El reloj de fichar** es `TimeClockBlock`, un lector de tarjetas en una pared sin arte propio (comparte los modelos y sonidos
del lector): rojo hasta que alguien ficha, luego verde. Con clic derecho de un jugador activo en `ACT1_CLOCKIN` pita y le pasa
a `ACT1_TASK1`; después zumba ("Ya has fichado"); para cualquier otro no hace nada. El mapa coloca uno (en la pared
sur de la entrada); `/et ch2 l1 clock` pone otro donde miras. Vuelve a rojo cada vez que se pone a un jugador en `ACT1_CLOCKIN`.

**El panel de objetivos** (`client.hud.ObjectivePanel`) es pequeño, arriba a la derecha: "OBJETIVO" en gris y debajo el texto
del estado del jugador (`Ch2L1Objectives`: una clave de lang por estado, bajo `every_tunnel.ch2.l1.objective.`; ninguna en las
cinemáticas ni al final), sobre una franja tenue. Aparece con un fundido cuando cambia el objetivo y no hace ruido. El cliente
lee el estado del progreso del jugador, así que no hay paquete propio.

## Comandos de depuración

`platform.Ch2L1Commands`, nivel de operador 2, **solo en ejecuciones de desarrollo** (como explica
[Depuración](debugging.md) para `/everytunnel`):

| Comando | Qué hace |
|---|---|
| `/et reload` | Vuelve a leer los archivos JSON del nivel y dice qué falla en ellos |
| `/et ch2 l1 enter` | Te conviertes en nuevo empleado: se te aplican las reglas del nivel (en Supervivencia o Aventura), se vacían tus bolsillos, el estado es el inicial y estás en `spawn_clockin` |
| `/et ch2 l1 leave` | Vuelven a aplicarte las reglas del resto del juego |
| `/et ch2 l1 clock` | Pone un reloj de fichar en la pared que miras |
| `/et ch2 l1 build` | Construye el nivel de nuevo sobre lo que hay (un plan nuevo se construye solo cuando alguien entra) |
| `/et ch2 l1 unbuild` | Vacía toda la zona (antes de empaquetar el mundo en un jar); quien esté dentro cae |
| `/et ch2 l1 anchors` | Todas las anclas (puntos, zonas, caminos) con sus coordenadas, las provisionales marcadas |
| `/et ch2 l1 goto <ancla>` | Te lleva a un punto, o al centro de una zona, un bloque por encima de su suelo |
| `/et ch2 l1 state` | Muestra tu estado y el siguiente |
| `/et ch2 l1 state <estado>` | Te pone en ese estado (sin importar mayúsculas, `-` por `_`), como si hubieras llegado jugando |
| `/et ch2 l1 skip` | Te pasa al siguiente estado |
| `/et ch2 l1 npc <skin>` | Pone delante de ti a una persona de la fábrica, para ver su skin (`rowan`, `coworker`, `worker_a`, `worker_b`, `generic_1` a `generic_3`) |
| `/et ch2 l1 npc clear` | Las quita todas |

`state` y `skip` cambian el estado y, con él, el objetivo del panel; el resto de los efectos de un estado (un objeto, un
personaje) llegan con la fase que se los dé, y también se ejecutarán con el comando, que es lo que permite probar cada fase sin jugar las anteriores.

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
fragmentos, los textos en los dos idiomas) `Ch2L1SkinsTest` (el formato de las skins) y `Ch2L1ObjectivesTest` (un objetivo por paso, en los dos idiomas) y `Ch2L1MapPlanTest` (los bloques y puertas del plan, las anclas
sobre sus suelos), todos en `core/ch2l1`; `PlayerProgressTest` cubre el estado y la marca en el progreso.
