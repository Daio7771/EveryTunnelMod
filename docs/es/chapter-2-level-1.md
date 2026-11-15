# Chapter 2, Nivel 1

*[English version](../en/chapter-2-level-1.md) · [Todas las páginas](README.md)*

El Nivel 1 del Chapter 2 es la fábrica, cuatro meses después del Chapter 1: un científico normal contratado en lugar del
que "se puso enfermo", tres actos de tareas, una reunión y sigilo, hasta el despacho del supervisor. Se construye por
fases (de la 0 a la 10), de una en una, en la rama **privada** `chapter-2/level-1` (solo se sube al remoto `private`,
nunca a `origin`) hasta que se publiquen los Niveles 1 y 2 del capítulo. **Dónde está: fase 5, el Acto 2.** Se
juega desde el reloj hasta el final de la reunión (Actos 1 y 2); el LAB aún no está amueblado y el Acto 3 es lo siguiente.

Su código está en `daio7771.everytunnel.chapter2` (el del cliente, en el mismo paquete dentro de `src/client`): `level1.core`
(Kotlin puro, con tests), `level1.platform` (pegamento con Minecraft) y `level1.client`, aparte del juego base, que nunca lo
nombra (véase [Arquitectura](architecture.md#capítulos)). Sus textos están en `tools/lang/src/chapter2/`.

## La máquina de estados

`level1.core.Ch2L1State` tiene diez estados, en el orden en que se juegan. El estado actual de cada jugador va en su
progreso del Chapter 2 (`Ch2PlayerProgress.l1State`, que `Ch2Progress`, el componente propio del capítulo, guarda como `l1_state`), así que sobrevive a salir del
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

`level1.platform.Ch2L1` es la única puerta al estado: `setState(player, state, reason)` lo guarda, escribe en el log `Chapter 2
Level 1: <jugador> goes A -> B (<motivo>)` y avisa a los oyentes; `skip(player)` pasa al siguiente. Un sistema del nivel
se registra una vez con `Ch2L1.onStateChange` y hace lo que significa entrar en un estado (un objetivo, una escena, una
línea del altavoz); la máquina de estados no sabe nada de eso. Por eso un paso se alcanza jugando o por comando con el
mismo resultado.

## Los archivos de datos

Todo lo ajustable es JSON, no código: cambiar un ritmo, una ruta o un texto es editar un archivo.

| Archivo | Contiene | Quién lo lee |
|---|---|---|
| `anchors.json` | Los lugares con nombre: puntos (una posición y hacia dónde mira) y zonas (una caja) | `Ch2L1AnchorsParser`, con tipos |
| `tasks.json` | Las tres tareas del Acto 1: qué anclas tienen los tubos, el terminal, el analizador, la escoba y la suciedad, y cuánto dura cada una | `Ch2L1TasksParser`, con tipos |
| `speakers.json` | Los altavoces de Rowan: las zonas y sus altavoces de techo, las líneas fijas y el grupo, el enfriamiento, la cinemática del primer aviso | `Ch2L1SpeakersParser`, con tipos |
| `npcs.json` | La gente de la fábrica: dieciséis trabajadores con sus rutinas, los grupos (el de las taquillas) y cuán vivos parecen (`life`) | `Ch2L1NpcsParser`, con tipos |
| `patrols.json` | Las patrullas | Vacío (`{}`) hasta que la fase que lo construye le dé su lector |
| `cutscenes/meeting.json` | El Acto 2: seguir al compañero y la reunión | `Ch2L1Act2Parser`, con tipos |
| `cutscenes/office.json` | La cinemática del despacho (fase 8) | Lo mismo |

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
| WC (oeste) · Taquillas (con una columna de 2 × 2) | 5 × 5 · 14 × 18 | 4 |
| Anillo, de 4 de ancho, alrededor del comedor (46 × 25 con sus muros) · Comedor | 4 · 36 × 15 | 4 · 6 |
| Laboratorio auxiliar (restringido) | 14 × 24 | 5 |
| Pasillo autorizado · Seguridad · Despacho · WC (restringidos) | 46 × 4 · 13 × 6 · 12 × 7 · 6 × 3 | 4 |
| Pasillo "Nivel 2", tras la puerta sellada | 11 × 6 | 4 |

El aspecto es el del laboratorio del prólogo, limpio y moderno: hormigón blanco, suelo gris claro, primera hilada de muro en gris
oscuro, cristal (la pared del laboratorio auxiliar hacia el tramo oeste del anillo: `factory_glass`, un bloque del mod sin marco
dibujado en cada bloque y con un brillo que sigue de uno al siguiente, así que la pared se ve como una sola lámina) y las
lámparas de techo del laboratorio (160). El LAB, los pasillos, las taquillas y el laboratorio auxiliar se agrandaron tras el primer recorrido del nivel (las medidas del dibujo eran
relativas). Hay 21 puertas, todas propias del mod (ninguna es de vanilla): **libres**, la puerta de laboratorio
(`factory_door`: blanca, con una ventana alta; las dos hojas de un par se abren y se cierran a la vez a mano, `LinkedDoorBlock`,
con las bisagras en los bordes exteriores; la del anillo al pasillo autorizado también es libre, pero es
la entrada a una zona prohibida: el círculo rojo del dibujo); **cerradas con llave**, la puerta de emergencia gris (`emergency_door`), que las manos no
abren, para el laboratorio auxiliar, el despacho y la puerta sellada (las fases siguientes las abren con la credencial, el
código y la tarjeta de Rowan); y una más igual en el extremo este del pasillo que no abre a nada. Las tres puertas de la entrada dan al
pasillo principal y al LAB, y el reloj de fichar cuelga de la pared sur de la entrada.

**Dónde y cómo.** El plan se levanta en (1024, 64, 1024) del mundo del mapa, libre de los niveles del Chapter 1 y de la sala del
prólogo. `level1.platform.Ch2L1Build` lo pone ahí a razón de 8.000 celdas por tick, con los chunks cargados (un segundo o dos, sin
tirones), vaciando antes su caja; recuerda la huella del plan que construyó (`data/every_tunnel_ch2_l1.dat`), así que
`Ch2L1.enter` solo construye el nivel si falta o si el plan cambió. Cada bloque se busca antes de tocar el mundo; un plan con
un bloque o un estado que el juego no conoce falla con un mensaje y deja el mundo como estaba. `/et ch2 l1 build` construye de
nuevo sobre lo que hay, y **`/et ch2 l1 unbuild` vacía la zona: hazlo antes de empaquetar el mundo en un jar** (`packMapWorld`
comprime también los archivos de región, y el mapa es del Chapter 2, no del mundo del Chapter 1).

**Todavía no hay:** mobiliario (llega con la fase que lo necesita: el banco con los tubos, el terminal, el analizador, la escoba, la suciedad y los
altavoces de techo llegaron con la 3; las taquillas, las seis estaciones de trabajo del LAB y las cámaras con la 4; el resto del mobiliario del LAB está por llegar, el escritorio y los papeles son de la 6), los carteles de texto ("NIVEL 2", "Acceso restringido", los nombres de las salas: el
plan no puede llevar los datos de un bloque de texto) y el teclado del despacho (un bloque de la fase 6, con su ancla
`office_keypad`). Tampoco está el paso del prólogo al nivel: `enter` se llama a mano.


## Voces y subtítulos

El nivel tiene 32 líneas de voz (ElevenLabs, de los MP3 del usuario: la excepción con IA de [Audio](audio.md)): el
compañero (`follow_01` a `03`, y casi todas las `meeting_*`), los trabajadores A y B de la unión (las líneas `_a` y `_b` de
la reunión) y Rowan, por los altavoces (`rowan_loyal_a1` a `a6`) y en su despacho (`rowan_office_c1` a `c8`).
`tools/sounds/ch2_l1_voices.py` las convierte (Ogg mono, una ganancia para cada uno de los dos grupos, el de la unión y el
de Rowan, de modo que cada uno quede a -18 LUFS como las líneas del director), corta cada una en fragmentos de subtítulo
por sus pausas y escribe `cutscene/ch2_l1_voices.json`; `export.py` hace de cada una un evento de
sonido, `voice.<id>` (`/playsound every_tunnel:voice.follow_01 voice @s`). Las `rowan_loyal_*` ya suenan por los altavoces del Acto 1 (véase
[Acto 1](#acto-1-las-tareas-y-los-altavoces)); las demás las reproducirán los sistemas de las fases siguientes.

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
**mantenida** (véase [Contribuir](contributing.md#criaturas)).

**La vida de la fábrica** (fase 4). **Dieciséis personas corrientes** trabajan en la fábrica, desde `npcs.json` (`Ch2L1NpcsParser`): diez en el LAB, tres en el pasillo principal y
tres en el comedor (`group`: `lab`, `main`, `dining`). Cada una tiene una `skin`, un sitio donde empieza (`spawn`, un ancla de punto) y una **rutina**, una lista de pasos que se hacen en orden y
luego otra vez: `stand` (segundos, un número o `[min, max]`), `walk` (un ancla de camino, con `reverse` opcional) y `type` (un ancla de punto: van allí, miran hacia donde mira y teclean, con los
brazos al frente, durante `seconds`). Las posiciones son anclas, así que mover el mapa mueve a la gente; las seis estaciones de trabajo del LAB (un banco con un monitor, en `level.py`), las
rutas (`route_*`) y el sitio de cada trabajador (`npc_*`) los escribe el generador del mapa y quedan como provisionales hasta que se amueble el LAB. Las rutas se escriben a mano, no las busca el
pathfinding del juego: `Ch2L1RoutineRunner` (Kotlin puro, probado) dice qué hacer cada tick y `FactoryNpcEntity.tick` lo hace fijando la velocidad de la propia entidad (un paseo de 1,3 bloques por segundo, en línea recta al siguiente punto;
a quien deja de acercarse durante 5 s, un tope, se le pone en el punto), así que dieciséis personas casi no cuestan nada. `walk_speed` del archivo escala su paseo, y el `pace` propio de un trabajador (opcional), el suyo.

**Vivos, no robóticos** (fase 4B; `life` en `npcs.json`, `Ch2L1Life`, todas las claves opcionales; el código en `chapter2/level1/core/Ch2L1NpcLife.kt`, Kotlin puro, probado):
- **Andar** (`Ch2L1Gait`): cada uno anda a su ritmo (`walk_speed` por 1 ± hasta `pace_spread`, sacado de su id), acelera poco a poco (`accel_seconds`) y frena antes del sitio donde
  se para; el cuerpo gira como mucho `turn_degrees` por tick, y un giro brusco los frena antes. Siguen yendo en línea recta al punto, así que nunca se desvían hacia una pared. En los puntos
  intermedios de un camino a veces se paran un momento (`linger_chance`, `linger_seconds`).
- **Mirar** (`Ch2L1Gaze`): la cabeza se mueve sola, nunca más de 70° respecto al cuerpo. Andando, casi siempre al frente, con alguna mirada de reojo; de pie, a un sitio nuevo cada
  `glance_seconds` (alrededor, a un compañero a menos de 8 bloques); tecleando, abajo al teclado, levantando la vista de vez en cuando. A un jugador que se pone a menos de `player_range`
  delante de ellos lo siguen con la mirada `player_look_seconds`, y luego no otra vez hasta pasados `player_cooldown_seconds`. Para separar cabeza y cuerpo, la entidad apaga los controles de
  mirada y de cuerpo de vanilla: el servidor fija el `yaw` del cuerpo y el `headYaw` y el pitch de la cabeza, que el juego sincroniza y suaviza.
- **Charlar** (`Ch2L1Chat`, emparejados por `Ch2L1Npcs` cada 5 ticks): dos trabajadores a menos de `chat_range`, ninguno en una estación ni recién salido de una charla, se paran
  `chat_chance` de las veces (los demás siguen andando y no se les vuelve a preguntar en 10 s), se miran y hablan sin voz durante `chat_seconds`: uno mueve las manos (`Ch2L1Pose.TALKING`),
  el otro asiente (`LISTENING`), y se turnan cada 1,5 a 3,5 s. Mientras, sus rutinas esperan; después ninguno vuelve a charlar en `chat_cooldown_seconds`. La pose se sincroniza y la dibuja
  `FactoryNpcRenderer` (cada persona un poco a destiempo de las demás); la fase 5 añade a la misma lista las poses de la reunión.

`level1.platform.Ch2L1Npcs` las mantiene: mientras un jugador juega el nivel, dentro de él y antes de la cinemática, `ensureAmbient` (cada 5 s, y al entrar) pone a cada trabajador que falte; los del LAB
desaparecen cuando el jugador llega a `ACT3_CUTSCENE` (la fase 8 cuenta con ello). Los **grupos** (`groups` del archivo) son personas de pie en anclas, que pone `Ch2L1Npcs.spawnGroup`: las
seis del **grupo de las taquillas** (`taquillas`, alrededor de la columna central) son para la reunión y las pone su fase, no el principio.

**Las taquillas.** La sala de las taquillas (14 × 18, el único espacio sin cámaras) tiene las `locker` del Nivel 3 en tres paredes, de dos bloques de alto, mirando hacia dentro. Aquí son solo mobiliario:
esconderse en una está desactivado mientras se juega el nivel (`HidingHooks`). La **taquilla del predecesor** (`predecessor_locker`, un bloque propio: la misma taquilla con la puerta entreabierta y el
nombre de su placa rascado; anclas `predecessor_locker` y `predecessor_locker_block`) es la de la pared oeste junto a la esquina.

**Las cámaras de seguridad** son decorativas: una `factory_camera` cuelga de la capa de aire más alta de cada espacio salvo las taquillas (19 en total: una por sala, una cada 15 bloques más o menos en los pasillos).
Es el bloque y la entidad de bloque de la cámara del Nivel 5 con otro comportamiento en `SecurityCameraRenderer`: nunca busca al jugador, barre 50 grados a cada lado de adonde mira una vez cada 10 s (cada
una en su punto del barrido), su luz roja parpadea y zumba suave (`block.camera.servo`) cada pocos segundos si el jugador está a menos de 14 bloques. No detectan nada; las patrullas de la fase 7 sí. Al
dibujarlas el cliente, no cuestan nada al servidor.

## Las reglas del nivel, el reloj y los objetivos

**Las reglas siguen al jugador, no al lugar.** Un jugador está *activo* en el nivel (`Ch2PlayerProgress.l1Active`, guardado
con el estado y enviado al cliente por Cardinal; `Ch2L1.active`) desde `Ch2L1.enter` hasta `Ch2L1.leave`. Mientras lo está:

Los hooks del juego base nunca preguntan por este nivel: `Chapter2` registra sus reglas en `PlayerRules` (sin linterna, sin
esconderse, a salvo, en silencio, sin barras de estado) mientras `Ch2L1.active`, y los hooks de abajo preguntan a `PlayerRules`.

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

## Acto 1: las tareas y los altavoces

**Las tres tareas** (`level1.platform.Ch2L1TaskHooks`; los bloques de `Ch2L1TaskBlocks.kt` solo le pasan su uso; dónde está cada cosa y cuánto dura es
`tasks.json`):

1. **Muestras** (`ACT1_TASK1`): haz clic en los tres `sample_tube` del banco junto a la pared oeste del LAB (cada uno desaparece, con un pitido:
   1/3, 2/3, 3/3) y luego en el `noname_terminal`. Abre **NonameOS** (`client.screen.NonameOsScreen`, fósforo verde en el marco de los monitores del
   Nivel 5): la ventana SAMPLE REGISTRY, una fila por tubo con su código (`S-0412`...), su estado y un botón REGISTER; un clic llena una barra durante
   `register_seconds` y pide al servidor que lo registre (`ch2l1_os_register`), que responde con el registro tal como queda (`ch2l1_os_open`); el
   último cierra el lote, la pantalla cierra sesión sola y el estado pasa a `ACT1_TASK2`. El registro es una ventana de una pantalla hecha para
   reutilizarse en los documentos de las fases siguientes.
2. **Analizador** (`ACT1_TASK2`): un clic **mantenido** sobre el `analyzer` (un bloque nuevo, hecho por `tools/blocks/generate.py`) durante
   `calibrate_seconds`. El juego repite un clic mantenido cada 4 ticks; `level1.core.HoldTracker` cuenta los ticks mientras los usos llegan con menos
   de 8 de diferencia y sobre el mismo bloque, así que apartar la vista o soltar lo reinicia. Lo muestran una barra (`client.hud.HoldBar`, del paquete
   `ch2l1_hold`) y un pitido que sube de tono. Luego, `ACT1_TASK3`.
3. **Barrer** (`ACT1_TASK3`): la `leaning_broom` apoyada en la pared este del tramo oeste del anillo; un clic la coge (el objeto `broom` va a la
   mano del jugador; solo existe para esta tarea y se retira al terminarla). Luego un clic mantenido durante `sweep_seconds` sobre cada una de las diez
   `dirt_spot` del suelo frente al cristal del laboratorio auxiliar la barre (un siseo y una nubecilla). La última termina la tarea: `ACT2_FOLLOW`.

Cuánto ha avanzado el jugador son unos contadores de su progreso (`ProgressService.ch2L1Counter`: los tubos cogidos, las muestras registradas y la
suciedad barrida como bits, si cogió la escoba, y `heard_<línea>` para los avisos), así que sobrevive a salir del mundo; el cliente también los lee:
el panel de objetivos muestra "(1/3)" mientras se cogen los tubos y luego mientras se registran, y "(4/10)" para la suciedad (`objective.count`). Cada
cambio de estado (jugando, `state`, `skip`) lleva contadores y mundo a lo que el estado significa (`Ch2L1TaskHooks.onState`, `reconcile`): los
tubos, la escoba y la suciedad están donde el jugador aún no ha llegado y no están donde ya ha pasado, así que una tarea se puede probar por
comando con el nivel como estaría si se hubiera jugado. Los objetos del plano se vuelven a poner desde el propio plano (`Ch2L1Build.planState`).

**La flecha guía** (`level1.client.TaskArrow`, destinos en `level1.core.Ch2L1Guide`, ajustes en el `guide` de `tasks.json`): una V rellena que apunta hacia abajo, azul con
bordes blancos, que gira sobre sí misma y sube y baja un poco sobre el lugar del paso actual del jugador, para que el camino quede claro cuando el LAB esté lleno de mesas
y máquinas. Se dibuja sin prueba de profundidad (los muebles no la tapan) y crece con la distancia para leerse a través de la sala. Apunta a lo más cercano de lo que queda: el
reloj de fichar antes de fichar; los tubos aún sin coger y luego el terminal; el analizador; la escoba y luego las manchas sin barrer. Se oculta cuando el jugador se acerca a
menos de `hide_within` (1,5 bloques a lo largo del suelo), en una cinemática y con el HUD oculto; `height` (1,1) es cuánto flota sobre el ancla y `enabled` la apaga.
Las fases siguientes añaden a `Ch2L1Guide.targets` los pasos de los Actos 2 y 3.

**Los altavoces** (`level1.platform.Ch2L1SpeakerHooks`, reglas en `level1.core.Ch2L1SpeakerSchedule`, datos en `speakers.json`): los avisos de Rowan
`rowan_loyal_a1` a `a6`, desde los altavoces de techo (bloques `ceiling_speaker`) de cuatro zonas, `speaker_entrance`, `speaker_lab`, `speaker_main` y
`speaker_west`, cada una con su ancla emisora (`speaker_<zona>_src`). Las reglas:

- **Rowan no dice nada hasta que se acaba la primera tarea.** `a1`, la apertura, llega cuando se registra la última muestra **y el jugador ha salido de NonameOS** (el cliente
  avisa al servidor al cerrarse la pantalla, `ch2l1_os_closed`), un momento después (`after_terminal_seconds`, 1 s); nada habla a un jugador que tiene NonameOS abierto. `a4` cinco segundos
  después del analizador (`ACT1_TASK3`).
- El grupo `a2`, `a3`, `a5` (se abre tras `a1`) una vez cada uno, en orden aleatorio, cada uno tras un enfriamiento de 75 a 120 s desde el final de la línea anterior; nunca dos a la vez.
- Solo mientras el jugador está en una zona con altavoz (si no, la línea espera a que vuelva); ninguno desde `ACT2_FOLLOW`, que además corta lo que suena.
- `a6` no está en ninguna lista: se reserva para la escena del despacho. A un jugador puesto más allá de `ACT1_TASK2` por un comando se le da por oído el `a1`.
- Cada línea es el timbre (`event.ch2l1.ding`, dos notas suaves de campana; dura `ding_seconds`) y luego la voz, enviados solo a ese jugador desde el altavoz de la zona con `volume` 3, así que
  llegan a toda la zona (`attenuation_distance` 48 en `sounds.json`); los subtítulos son los de siempre (`Ch2L1Subtitles`). `tools/sounds/ch2_l1_voices.py` pasa los seis
  `rowan_loyal_*` por un filtro de megafonía (banda estrecha, algo de saturación, un eco de pasillo) antes de escribirlos.

**El primer aviso es una cinemática** (`a1`; `level1.core.Ch2L1IntroScene`, `level1.platform.Ch2L1IntroHooks`): cuando el jugador sale de NonameOS tras la primera tarea no puede moverse,
las barras negras entran y la cámara se gira desde sus propios ojos, donde está, hacia un altavoz de techo fijo (`speakers.json`, `intro.shots`: `camera: "player"` mirando a
`speaker_lab_src`, el altavoz del LAB, que cuelga cerca del terminal), el timbre suena desde él al empezar la toma, la voz le sigue y la cámara vuelve a sus ojos mientras las
barras se van. Es la maquinaria de la escena del director y del prólogo (`Cutscene`, `Timeline`, `CutsceneView.Kind`, here `Ch2L1IntroView.KIND`); la cámara de una toma es un ancla (o `player`) y lo que
mira otra ancla, en coordenadas del mundo, así que mover el mapa mueve la toma, y se pueden añadir más tomas en el archivo; se salta con la tecla de saltar pulsada dos veces, como las
demás. Los otros avisos no tienen barras: el timbre, la voz y su subtítulo, y el jugador sigue caminando.

`/et ch2 l1 tasks` muestra cuánto ha avanzado el jugador y todo lo que `tasks.json` o `speakers.json` nombran y `anchors.json` no tiene
(`level1.core.Ch2L1Wiring`, que `/et reload` también informa).

## Acto 2: el compañero y la reunión

Lo lleva `chapter2.level1.platform.Ch2L1Act2Hooks`, desde `cutscenes/meeting.json` (`Ch2L1Act2`, que lee `Ch2L1Act2Parser`; todas las
claves opcionales; `Ch2L1Wiring` comprueba las anclas y caminos que nombra):

- **La espera** (`ACT1_TASK3`): el compañero (skin `coworker`) aparece en `coworker_wait`, la esquina del anillo junto a la escoba, mientras
  el jugador sigue en el LAB, y espera allí quieto, con los brazos colgando y la mirada puesta en el jugador mientras barre.
- **Seguirle** (`ACT2_FOLLOW`, al barrer la última mancha): dice `follow_01` desde donde está (una voz del nivel como cualquier otra: salen
  sus subtítulos; nada más se escribe encima, así que barrer las manchas no da mensaje, el panel las cuenta) y, al acabar, recorre a
  paso ligero (`pace`, por el paseo de los trabajadores) `coworker_route` (el anillo, el pasillo principal, la segunda puerta de las taquillas) hasta
  `meeting_coworker`, abriendo las puertas que encuentra (`Ch2L1Doors`). `Ch2L1Follow` (Kotlin puro, con tests) decide cada tick: un jugador
  a más de `lag_blocks` y que no va por delante se queda atrás, así que se para, se gira y le espera; tras `remind_after_seconds` así dice
  `follow_02` o `follow_03`, por turnos, y no otra vez hasta pasados `remind_cooldown_seconds`. La flecha guía flota sobre su cabeza solo si
  el jugador está a más de `arrow_beyond_blocks` o no le ve (`Ch2L1Guide.follow`). Mientras, el grupo de la reunión (`taquillas` de
  `npcs.json`) espera en las taquillas, y los dobles ambientales de worker_a y worker_b salen del LAB en cuanto nadie los ve (`until` de
  `npcs.json`, `Ch2L1Npcs.retire`).
- **La reunión** (`ACT2_MEETING`, cuando el jugador está en `taquillas_trigger` con él dentro o justo detrás): el jugador pasa a
  `meeting_player` y se le sujeta allí, en un círculo cerrado de ocho en el centro de la sala (el compañero enfrente, de espaldas a la
  taquilla del predecesor; en un círculo nadie se interpone entre el jugador y una cara), y la escena se juega en su cliente (`ch2l1_meeting_start`: el marco y la puerta por la que entró;
  `Ch2L1MeetingView`). Se construye desde los datos en los dos lados (`Ch2L1MeetingScene`, Kotlin puro, con tests): la cámara son los ojos
  del propio jugador, que giran suave (`turn_seconds`) hacia quien habla (`meeting_01` a `15` en orden, separadas `gap_seconds`; el sitio
  de un hablante es el del compañero o el del miembro del grupo con su skin), y hacia la puerta durante `door_seconds` tras `door_after`.
  Sus subtítulos van frase a frase tras el nombre de quien habla (`CutsceneView.Kind.subtitleText` y `speakerAt`). En los mismos ticks el
  servidor dirige a la gente (`FactoryNpcEntity.direction`): todos miran a quien habla (el compañero se gira hacia él con todo el cuerpo, y vuelve al jugador en sus propias líneas), y cada uno mira a la puerta de vez en cuando
  (`Ch2L1Glances`); quien habla gesticula (`TALKING`), worker_a tiembla en `tremble_lines`, worker_b se sacude de risa en `laugh_lines`; los
  demás escuchan con los brazos quietos, colgando, cambiando el peso de pie; el miembro más cercano a la puerta va a `<puerta>_inside` y la
  cierra; y cuando el compañero llega a "Take this" (`card_line`, `card_sentence`) alarga el brazo (`OFFERING`) y el jugador recibe la
  **copia de tarjeta del laboratorio** (`lab_badge_copy`, la tarjeta del Chapter 1 recoloreada por `tools/blocks/generate.py`; solo abre la
  puerta del laboratorio auxiliar, fase 6).
- **El final**: cuando acaba la última línea todos se van por `meeting_leave`, el compañero primero y los demás de uno en uno
  (`leave_gap_seconds`, a `leave_pace`), abriéndose la puerta ante ellos, y cada uno desaparece en cuanto el jugador no le ve; las barras se van y el
  estado es `ACT3_EXPLORE`. **Saltarla** (`ch2l1_meeting_skip`) la acaba en el acto con el mismo estado: la tarjeta dada, el jugador en
  `taquillas_exit`, el compañero y el grupo fuera, la puerta abierta.

Llegar a un estado por comando hace lo que significa: `ACT1_TASK3` le pone junto a la escoba, `ACT2_FOLLOW` empieza el camino desde allí,
`ACT2_MEETING` empieza la reunión al momento, un estado posterior da la tarjeta y se lleva a todos, uno anterior se le lleva a él y la
tarjeta. El camino y la reunión se guardan en memoria y se retoman desde el estado tras salir del mundo.

## Comandos de depuración

`level1.platform.Ch2L1Commands`, nivel de operador 2, **solo en ejecuciones de desarrollo** (como explica
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
| `/et ch2 l1 npc ambient` | Pone a los trabajadores de la fábrica que falten (debes estar jugando el nivel y dentro de él) |
| `/et ch2 l1 npc group <nombre>` | Pone un grupo de `npcs.json` en sus anclas (`taquillas`) |
| `/et ch2 l1 npc remove <grupo>` | Quita un grupo (`lab`, `main`, `dining`, `taquillas`) |
| `/et ch2 l1 tasks` | Cuánto has avanzado en las tres tareas, y lo que los datos nombran y falta |
| `/et ch2 l1 tasks reset` | Las tres tareas empiezan de nuevo: los tubos, la escoba y la suciedad vuelven adonde dice el estado |
| `/et ch2 l1 intro` | La cinemática del primer aviso, ahora |
| `/et ch2 l1 say <rowan_loyal_a1..a6>` | Un aviso ahora (el timbre y luego la voz), desde el altavoz de la zona donde estás |

`state` y `skip` cambian el estado y, con él, el objetivo del panel; el resto de los efectos de un estado (un objeto, un
personaje) llegan con la fase que se los dé, y también se ejecutarán con el comando, que es lo que permite probar cada fase sin jugar las anteriores.

## Añadir el sistema de una fase

1. Kotlin puro en `chapter2.level1.core` para sus reglas y su lector de JSON, con tests unitarios (`Ch2L1DataTest` comprueba que
   cada archivo del jar se lee bien).
2. Darle forma y un lector con tipos a su archivo de `data/every_tunnel/ch2/l1/`; leer el documento con
   `Ch2L1DataLoader.data.document("<nombre>")` y no guardarlo nunca, para que `/et reload` lo alcance.
3. Un hook en `chapter2.level1.platform` (que registra `Chapter2`) que se registre con `Ch2L1.onStateChange` y, si tiene momentos propios, escuche el tick del
   servidor; solo actúa en mundos con niveles (`WorldRules`).
4. Textos en `tools/lang/src/chapter2/en_us.json` y `es_es.json` bajo `every_tunnel.ch2.l1.<id>` (luego `tools/lang/build.py`); esta página y el [mapa del código](code-map.md),
   en los dos idiomas.

## Tests

`Ch2L1StateTest` (el orden, `next`, `parse`), `Ch2L1AnchorsParserTest` (el formato, cada tipo de problema) y `Ch2L1DataTest`
(los archivos del jar, qué copia gana, los respaldos), `Ch2L1VoicesTest` (las 32 líneas, sus eventos de sonido, Ogg y
fragmentos, los textos en los dos idiomas) `Ch2L1SkinsTest` (el formato de las skins) y `Ch2L1ObjectivesTest` (un objetivo por paso, en los dos idiomas) y `Ch2L1MapPlanTest` (los bloques y puertas del plan, las anclas
sobre sus suelos), todos en `chapter2/level1/core`; `Ch2L1NpcsTest` (el archivo de la gente, las rutinas y cada tick de quien teclea, de quien camina y de quien se atasca), `Ch2L1Act2Test` (el archivo de la reunión y sus anclas, el compañero guiando y esperando y sus recordatorios, las líneas de la reunión, la puerta y la tarjeta, las miradas, la flecha sobre él), `Ch2L1NpcLifeTest` (las claves de `life` y el ritmo de un trabajador, acelerar y frenar, frenar para girar, las paradas en el camino, adónde miran y la mirada al jugador con su enfriamiento, las parejas para charlar y sus turnos), `Ch2L1TasksTest` (el archivo de tareas, los bits, el clic mantenido), `Ch2L1GuideTest` (adónde apunta la flecha en cada paso), `ModelRotationTest` (los ángulos de giro de todos los modelos son de los que el juego acepta), `Ch2L1SpeakersTest` (el archivo y cada regla del planificador, y que un planificador hecho para una partida vieja se sustituye cuando el progreso retrocede: en silencio antes de la primera tarea, el momento tras la terminal, la zona, nunca dos a la vez, el enfriamiento, el grupo una vez cada uno, el acto dos lo cancela), `Ch2L1IntroSceneTest` (las tomas de la cinemática, su tiempo y las anclas que faltan), `Ch2L1WiringTest` (todo lo que los archivos nombran está en `anchors.json`) y `Ch2L1TaskTextsTest` (los textos en los dos idiomas); `Ch2L1MapPlanTest` también comprueba que los objetos de las tareas y los altavoces están donde dicen sus anclas, que la sala de las taquillas tiene sus taquillas y ninguna cámara, que cada otro espacio tiene una y que cada trabajador y cada ruta pisan el suelo. `Ch2PlayerProgressTest` cubre el estado, la marca y los contadores en el progreso del capítulo, y `ChapterBoundaryTest` que ningún código fuera de `chapter2` lo nombra.
