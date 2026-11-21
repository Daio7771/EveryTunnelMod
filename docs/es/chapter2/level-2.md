# Capítulo 2, Nivel 2

*[English version](../../en/chapter2/level-2.md) · [Todas las páginas](../README.md)*

El Nivel 2 del Capítulo 2 son las catacumbas bajo la fábrica: las catacumbas antiguas en las que se metió el drenaje de la fábrica. El
científico baja por la alcantarilla de emergencia del Nivel 1, solo, y busca una salida: los Hunters en las galerías, una criatura que
caza por las vibraciones del suelo en la sala de bombas, el Observant Guard del Capítulo 1 en las cámaras de abajo y una inundación en tres
etapas camino de la estación de bombeo y la salida. Se construye por fases (de la 1 a la 8; la fase 8 es iluminación y decoración), una cada vez, en la rama **privada**
`chapter-2/level-2` (sale de `chapter-2/level-1` y solo se sube al remoto `private`) hasta que se publiquen los Niveles 1 y 2 del capítulo.
**En qué punto está: fase 7, la revisión integral.** Se llega bajando por la alcantarilla del Nivel 1 y se
recorre de la entrada a la salida con las barras de supervivencia; morir devuelve al último checkpoint, y el Capítulo 2 del menú lo
continúa; el primer tramo ya tiene sus enemigos, recursos y documentos, la sala de bombas tiene su criatura y su bomba, el vestíbulo y la sala de mantenimiento tienen el Guard y sus trampas, y el camino a la salida se inunda en tres etapas y acaba en una puerta pesada que se abre a la última batalla de Stone Maw (fase 6.1), el reencuentro con el compañero y el trabajador B y "CONTINUARÁ".

Su código está en `daio7771.everytunnel.chapter2.level2` (`core`, Kotlin puro, y `platform`), aparte del juego base, que nunca lo nombra
(véase [Arquitectura](../common/architecture.md#capítulos)). Sus lugares con nombre usan el formato del Nivel 1 (`level1.core.Ch2L1AnchorsParser`); su
mapa lo construye el `platform.MapBuild` del capítulo a partir de un `core.MapPlan`, igual que el del Nivel 1.

## Cómo se llega

**Por la alcantarilla del Nivel 1, sin corte.** Al final del Nivel 1 el jugador baja por la alcantarilla de emergencia mientras la pantalla
se funde a negro (`act3.json`, `overtime`: `fade_seconds`, 1,5, y luego `dark_seconds`, 1, a oscuras); el Nivel 1 avisa al capítulo
(`Ch2L1EndingHooks.onFinished`) y el jugador está al pie de la escalera, en las catacumbas (`sealed_entry`), donde la pantalla vuelve. Sin
texto de cierre ni menú: el camino sigue. La primera vez, las catacumbas se construyen detrás del negro (unos segundos).

Al llegar (`level2.platform.Ch2L2.enter`) se le vacían los bolsillos (el pase del Nivel 1 no sirve allí abajo), su aguante, sed y batería
quedan como dice `level.json` (`start`: todo lleno), las lámparas también (`lights`), y se toma el primer checkpoint. La linterna es la que
Rowan le dio en el Nivel 1: es equipo, no un objeto, y funciona desde el primer momento.

**Cómo se juega.** Mientras un jugador lo juega (`Ch2L2.active`), `Chapter2` le da la supervivencia del juego base a través de
`PlayerRules`: puede recibir daño, la sed y la batería se gastan, se dibujan las barras y sus pasos hacen ruido; la linterna y el ruido,
como diga `level.json` (`rules`). Sigue siendo el nuevo empleado (la bata blanca), no el guardia nocturno.

**Los estados** (`level2.core.Ch2L2State`), en el orden en que se juegan; entrar en una zona del `progress` de `level.json` lleva al
jugador a su estado, nunca hacia atrás:

| Estado | Dónde | Se llega |
|---|---|---|
| `ARRIVED` | Al pie de la escalera | Al llegar |
| `GALLERIES` | Las galerías de los Hunters (fase 3) | `zone_hunter_galleries` |
| `PUMP_ROOM` | El mirador y la sala de bombas, la criatura de vibraciones (fase 4) | `zone_pump_overlook`, `zone_vibration_chamber` |
| `CATACOMBS` | Las catacumbas y las galerías de abajo | `zone_lower_catacombs` |
| `GUARD_APPEARS` | El vestíbulo: el Guard sale de la pared (fase 5) | `zone_observer_wall` |
| `GUARD_FIGHT` | La sala de mantenimiento y sus cuatro trampas (fase 5) | `zone_observer_chamber` |
| `GUARD_DEAD` | De vuelta al vestíbulo, BOO! y las rejas arriba (fase 5) | El cuarto aturdimiento efectivo del Guard (o `skip guard`) |
| `FLOOD_1` a `FLOOD_3` | El colector, las pasarelas, la galería de subida: las etapas de la inundación (fase 6) | `zone_flood_collector`, `zone_rising_walkways`, `zone_upper_gallery` |
| `STATION` | La estación de bombeo, seca | `zone_pumping_station` |
| `MAW` | La arena de Stone Maw tras la puerta de salida (fase 6.1) | `zone_maw_middle` |
| `MAW_ESCAPE` | Cae el último pilar y Stone Maw queda aplastada: la arena se derrumba (fase 9) | romperse el tercer pilar |
| `MAW_DEAD` | La puerta del fondo se cerró tras el jugador: el camino a la sala del reencuentro | salir de la arena por la puerta del fondo (o `skip maw`) |
| `REUNION` | Suena el reencuentro con el compañero y el trabajador B, y luego el paseo hasta el ascensor | el centro de la sala del reencuentro |
| `DONE` | La reja cerrada y el motor en marcha: el cartel y el menú | el final del ascensor |

Los seis **encuentros** (`Ch2L2Event`) son tramos de estados: `hunters` (`GALLERIES`), `creature` (`PUMP_ROOM`), `guard` (de
`GUARD_APPEARS` a `GUARD_FIGHT`), `flood` (de `FLOOD_1` a `FLOOD_3`), `maw` (de `MAW` a `MAW_ESCAPE`) y `reunion` (`REUNION`). La etapa de la inundación es la del estado (0 antes, 3 desde la
tercera). Cada fase posterior cuelga su encuentro de los estados: `Ch2L2.onStateChange` (un estado que se deja por otro, jugando o por
comando) y `Ch2L2.onStart` (el nivel empezado en un estado, al llegar, al morir, con CONTINUAR o con `reset`: el mundo como ese estado lo
quiere, haya lo que haya).

## Checkpoints y partida guardada

**Checkpoints** (`level.json`, `checkpoints`), uno antes de cada encuentro: `entry` (el pie de la escalera, antes de los Hunters),
`pump_overlook` (antes de la criatura), `vestibule` (antes del Guard), `collector` (después del Guard, antes de la inundación) y
`station`. Entrar en la zona de un checkpoint, si está por delante del último tomado, lo toma: el estado del jugador, sus contadores y lo
que lleva son la **partida guardada** del Capítulo 2 (`Ch2L2Snapshot`, en su progreso del Capítulo 2), y la carpeta del mundo recibe la marca
del Capítulo 2 (`every_tunnel_chapter2_continue`, `ChapterRequest.setSaved`).

**Morir** es la muerte del juego base (el fundido, la pantalla de muerte con lo que le mató, vida y recursos llenos, la linterna apagada;
`DeathHooks`, preguntado a través de `ChapterRestarts`): el jugador está en el último checkpoint, el estado vuelve al que guardó y el nivel
se empieza otra vez allí. Lo que recogió desde entonces sigue recogido; la muerte no cuenta en la partida guardada del Capítulo 1.

**CONTINUAR.** Con la marca, el Capítulo 2 del menú ofrece CONTINUAR en vez de NUEVA PARTIDA. Abre el mundo (`ChapterRequest.continuing`), guarda
la vuelta al Capítulo 1 (`Ch2Return`) y pone al jugador en el último checkpoint con el estado, los contadores y las cosas de ese
checkpoint, con la vida y los recursos llenos. Salir del mundo en cualquier punto del Nivel 2 devuelve el Capítulo 1 como estaba (sitio,
cosas, vida, aguante, sed y batería) y conserva la partida guardada. Ctrl+Shift+D en el menú del Capítulo 2 la borra (tras preguntar) y
vuelve NUEVA PARTIDA; un NUEVA PARTIDA del Capítulo 2, o una partida nueva del Capítulo 1, también la olvida. El Nivel 1 se sigue jugando del tirón:
no hay nada que continuar hasta el primer checkpoint del Nivel 2.

## Hunters, botín y documentos

**Kit de supervivencia.** Al pie de la escalera, un mostrador (5 x 2, `every_tunnel:counter`) bajo un cartel de póster
(`every_tunnel.ch2.l2.survival_kit`, SURVIVAL KIT) tiene 3 baterías, 2 botellas de agua, 4 barras de energía (`food_snack`, que se ve
como "Snack") y la llave inglesa del Nivel 5 (un golpe mata a un Hunter). Es la fila de botín "Entry" del plan.

**Hunters.** Un grupo de tres (el del Nivel 5, puesto con `DistortionHooks.putIn` bajo el id de nivel `chapter2_level2`) está en el ancla
`hunters_post` de las galerías; ven, no oyen. Empezar el nivel (al llegar, al morir, con CONTINUAR, `reset`) se los lleva y los pone
enteros en su puesto, para un estado no posterior al `until` del grupo (`pump_room`).

**Botín** (tabla provisional): tras los Hunters (extremo este de las galerías, `loot_galleries`) 1 bebida y 1 batería; antes del Guard
(galerías de abajo, `loot_catacombs`) 1 bebida, 1 barra y 1 batería; la estación (`loot_station`) 1 bebida y 1 batería. Nada en las
criptas opcionales. Lo recogido es el contador `loot.<id>` del jugador en el Nivel 2, que se pone al recoger
(`LootSpawner.addTakenListener`). Morir conserva los bolsillos (regla base), así que conserva los contadores; CONTINUAR restaura los
contadores y bolsillos del checkpoint, de modo que lo recogido desde entonces vuelve a estar ahí.

**Documentos.** Tres tableros de notas (`every_tunnel:note_board`, puestos por el elemento `prop` del mapa) con los textos aprobados del
plan: `old_drainage` (pasillo de entrada), `maintenance_observation` (sala de bombas, junto a la puerta del mirador) y
`withdrawal_order` (galerías de abajo, junto al camino al Guard). El texto vive una sola vez, en las fuentes de idioma
(`tools/lang/src/chapter2/*.json`, claves `every_tunnel.ch2.l2.<id>_title` y `_body`); `tools/lang/common/langfiles.py` (`NOTES`,
`write_notes`, que ejecuta `build`) escribe a partir de ellas `assets/every_tunnel/notes/<en|es>/ch2_l2_<id>.txt`. Un tablero se lee
a través de `NoteHooks.Source`; el contador `read.<id>` lo recuerda. La clave `every_tunnel.ch2.l2.boo` queda para la fase 5.

## El Observant Guard y sus trampas (fase 5)

Entrar en el vestíbulo (`GUARD_APPEARS`) empieza el combate (`Ch2L2Guard`, sobre el `guard` de `level.json`; los relojes son `GuardFight` y
`TrapBoard`, Kotlin puro con tests). El jugador nunca pierde el control y no hay nada que saltar:

- **La arena** es el vestíbulo, el pasillo de servicio y la sala de mantenimiento. Cuatro puertas del plan (`guard.barred`:
  `service_far_gate` al fondo del pasillo, `chamber_exit`, `return_door` y `vestibule_gate`) caen al empezar, así que solo quedan el camino de
  vuelta al norte (un callejón sin salida donde el Guard alcanza al jugador) y la sala de las trampas; suben al morir. Las cuatro se construyen abiertas.
- **Salir: un aviso, una cinemática y la caza.** Al empezar el combate caen las rejas (se oyen las cadenas) y durante `crack_seconds` (5) las
  lámparas del vestíbulo parpadean y, el último segundo, se apagan, mientras el parche de la pared norte (`guard_wall`, una puerta con estados
  `whole`/`broken` sobre un cuartito, `guard_nest`) se agrieta y suelta polvo. Luego una **cinemática saltable con bandas** (`intro_seconds`, 10; `Ch2L2GuardScenes.INTRO`, los momentos en `core.GuardIntro`),
  **toda en primera persona**, con los ojos del propio jugador: la cámara no los deja, solo gira, como lo haría su cabeza. Gira de golpe hacia
  la pared a oscuras; el Guard (`ObservantGuardEntity`, el modelo del usuario), detrás en su nido, revienta la pared de un puñetazo (vuelven las
  lámparas, escombros, un sonido seco y una sacudida de la cámara), sale del hueco, se gira hacia el jugador, le señala y se golpea el pecho
  con los dos puños cinco veces (animaciones `punch`, `point`, `chest`: los codos abiertos, los puños nunca se tocan), con la cámara temblando
  en cada golpe. Termina con el jugador girado mirando al Guard. No tiene voz: se comunica con señales, y no hay subtítulo. Saltarla lo deja fuera del hueco mirando al jugador. Vuelve el control, mira fijo
  `stare_seconds` (0,5) y caza: siempre sabe dónde está el jugador dentro de la arena, derecho hacia él a `chase_speed` (algo menos
  que un sprint). A `hit_range` levanta un brazo (`windup_seconds`) y, si el jugador sigue a su alcance, le quita `damage_fraction` de su
  vida máxima (dos golpes matan), al menos `hit_interval_seconds` después del anterior.
- **Música.** El aviso suena con la caída del Nivel 5 (`music.fall`, reciclada: 5 s que suben hasta un golpe, que cae justo cuando aparece el
  Guard). Desde que aparece hasta su agonía suena la música de la caza (`music.ch2l2.guard_chase`, 150 bpm, 12,8 s, escrita en código en
  `tools/sounds/chapter2/catalog_ch2.py`, repetida cada 256 ticks por `Ch2L2Guard`), durante los aturdimientos y el juego de las trampas; se
  corta con el aturdimiento mortal, al terminar y al morir. Ambas suenan dos veces a la vez (la segunda a media volumen, porque el juego
  limita un sonido a su volumen máximo) para ganar unos 3,5 dB. La agonía tiene su propia canción corta y triste (`music.ch2l2.guard_lament`,
  piano de fieltro, 14 s, una vez).
- **Las trampas.** Cuatro cuadrados de chapa (zonas `zone_trap_1` a `4`) con un disyuntor cada uno en la pared de mantenimiento
  (`trap_breaker`: palanca y lámpara). Tirar de uno lo carga `rearm_seconds` (lámpara ámbar parpadeando) y queda armado (verde). El Guard
  pisando una chapa armada es **una activación**, tirada al entrar: con `fail_chance` falla, si no queda aturdido `stun_seconds`; de
  cualquier modo la trampa se gasta (palanca arriba) hasta que se vuelva a tirar. Seguir encima nunca vuelve a tirar; una chapa pisada con
  la trampa sin armar, o con el Guard sin cazar, no hace nada. Las chapas nunca tocan al jugador.
- **Muerte.** `stuns_to_kill` aturdimientos (una trampa puede repetirse; una tirada fallida no cuenta) empiezan una agonía de
  `death_seconds` (14) y una **cinemática** (`Ch2L2GuardDeathScene`, tres planos alrededor del Guard, saltable con la tecla de salto dos veces;
  saltarla ejecuta el final al instante): el Guard se tira al suelo y luego su cuerpo se deshace (la animación `death`) en una nube de motas y desaparece. **Un solo final** (`Ch2L2Guard.finish`,
  idempotente; `skip guard` hace lo mismo): suben las rejas, la pared queda rota con sus escombros, BOO! aparece en pintura blanca
  (`WallMessageBlock`, `Chalk: paint_white`) en la pared sobre el hueco, el estado pasa a `GUARD_DEAD` y se toma con él el checkpoint del
  vestíbulo: una muerte después vuelve al vestíbulo con el hueco abierto y BOO! puesto. Una muerte en pleno combate vuelve con la pared entera. Las chapas son de 6 x 6 y el Guard queda clavado en el sitio mientras está aturdido.

## La inundación y la salida (fase 6)

Entrar en el colector (`FLOOD_1`) desencadena la inundación (`Ch2L2FloodRun`, sobre las etapas de `flood.json`, el bloque `flood` de
`level.json` y el Kotlin puro `Ch2L2Flood`, con tests). El agua es ambiente y prisa: sube por detrás y por debajo del jugador y frena al
vadear, sin corriente y sin más letalidad que el ahogo de vanilla; la ruta siempre queda por encima de cada etapa (las comprobaciones del
generador) y las escaleras suben de nuevo a quien caiga.

- **Etapa 1.** La compuerta del vestíbulo se cierra de golpe a la espalda del jugador (`sealed`: el camino de vuelta queda cerrado para
  siempre), las tuberías crujen los `delay_seconds` de la etapa (3), después revienta la tubería del ancla `pipe_burst` (sonido, un
  temblor ligero de cámara, un chorro de partículas durante los segundos de la etapa y un hilo más fino después) y el agua sube capa a
  capa, de abajo arriba, a ritmo parejo durante los segundos de la etapa, con un máximo de `cells_per_tick` celdas por tick. Las
  **etapas 2 y 3** (`zone_rising_walkways`, `zone_upper_gallery`) solo llenan. Cada etapa guarda solo las celdas que añade a las de la
  anterior (`flood.json` trae las cajas enteras de cada una); el agua es un bloque fuente en el aire o `waterlogged` en un bloque que lo
  admite, y nunca sobre otro; una celda vaciada queda como la tiene el mapa. El agua quieta del mapa no está en `flood.json` y se queda.
- **El rugido** (`ambient.ch2l2.flood_rumble`, un bucle que se vuelve a lanzar cada 196 ticks) suena en la cabeza del jugador al volumen de la
  etapa (`rumble.stage_1..3`); desde `STATION`, la **puerta de la estación** (`station_south`, una compuerta: `open` al construir, `shut` una
  compuerta de esclusa) se cierra cuando el jugador está `station_door.clear_blocks` (3) dentro y el rugido se amortigua (`rumble.muffled`).
- **Un arranque** (una muerte, CONTINUAR, `reset`) deja el agua como la quiere el estado antes de devolver el control: vaciada hasta la
  inundación (morir dentro vuelve al checkpoint `collector`, en seco, y al entrar de nuevo empieza desde cero) y toda desde `STATION`, con
  las compuertas como toca. `/et ch2 l2 flood <0|1|2|3>` pone el agua al instante, para probar.
- **La puerta de salida** es la compuerta `exit_hatch` (cerrada al construir, en la pared oeste de la sala de salida): desde `STATION`, entrar en
  `zone_exit_door` (anidada en `zone_level_exit`) suena `event.ch2l2.exit_door` y se abre para siempre (`Ch2L2Maw`, `maw.exit_door`); el pasillo de
  detrás lleva a la arena.
- **El final** (`Ch2L2Ending`, `ending` en `level.json`) ya no está en la puerta: empieza en el ascensor (abajo). El cliente
  (`Ch2L2EndingOverlay`) funde a negro en `fade_seconds`, muestra "CONTINUARÁ" (`every_tunnel.ch2.l2.ending.continued`) durante `card_seconds` y
  sale al menú principal. La primera vez también fija `DONE` y guarda el checkpoint `chapter_end` (ancla `chapter_end`, en el ascensor,
  fase 9): CONTINUAR deja ahí, con toda la inundación puesta. `/et ch2 l2 ending` lo reproduce, para probar.

El generador informa del paseo tranquilo desde justo dentro de la puerta de la estación (`station_door_in`) hasta la puerta de salida: 52
bloques, unos 12 s (fase 7: el pasillo de salida es un zigzag, oeste, sur, oeste, norte, oeste; las dos puertas siguen donde estaban), y
`checks.py` falla por debajo de 50.

## La última batalla de Stone Maw y el reencuentro (fase 6.1)

Tras la puerta de salida el mapa sigue al oeste (coordenadas de plan negativas): `maw_corridor` (4 de ancho), `maw_arena` (20 x 20, techo de 7,
llana y desnuda: sus únicos bloques son lámparas bajo el techo; el check `no_climb` del generador prueba que todo sitio donde pisar está a ras de
suelo), `maw_pipe` (el hueco del que sale Stone Maw, con reja en la compuerta `maw_pipe_mouth` hasta que irrumpe) y `reunion` (10 x 10, iluminada,
con la puerta cerrada del Nivel 3 en el muro del fondo). Las compuertas `maw_entry` (abierta al construir, se cierra a la espalda del jugador) y
`maw_far` (cerrada hasta la muerte) las unen. `checks.route_gates` abre `exit_hatch` y `maw_far` para el paseo de la ruta.

- **La pelea** (`Ch2L2Maw` en `platform`, `maw` en `level.json`; las reglas son `StoneMawFight`, `PipeSupply`, `MawCollapse` y `MawEscape` en `core`). Llegar a
  `zone_maw_middle` pone `MAW`: la puerta se cierra, la reja se arranca, Stone Maw (la `VibrationCreatureEntity` de la fase 4 en modo pelea,
  `inFight`; el código de la bomba la deja en paz) sale a rastras hasta `maw_home` y cae el primer trozo de tubería delante del jugador. En su
  reposo, acecho, carga y golpe es el cerebro de la fase 4 (alimentado por los pasos del jugador en la arena y por las tuberías al caer, fuertes).
  Un trozo (`pipe_piece`, se recoge pasando por encima) se lanza con clic derecho (`ThrownPipeEntity`, `maw.throw`): contra Stone Maw es un
  **golpe** (nunca la mata); contra cualquier otra cosa se rompe y resuena donde cae (va hacia el ruido). Un golpe la orienta hacia el jugador y **embiste en línea recta**
  hasta algo sólido (el `horizontalCollision` del cuerpo) o `ram.max_seconds`: entonces queda **aturdida** (un golpe entonces la despierta) y
  vuelve al cerebro, buscando. También embiste **por su cuenta** cuando oye algo y hay un jugador en la arena (`ram.auto_cooldown_seconds` tras cada aturdimiento): las tuberías solo la molestan, esconderse tras un pilar es el juego. Lo rápida, lo cerrada que gira, lo que tarda en orientarse y lo que dura el aturdimiento son las `stages` de `maw` (una por pilar roto:
  6,0, 6,8 y 7,6 bloques por segundo; la etapa 3 es la muerte, así que son tres). Un jugador en su camino recibe el golpe como en la fase 4. Un trozo a la vez (`pipes.at_once`):
  `pipes.respawn_seconds` después de que uno desaparezca cae otro de uno de `pipes.points` (nunca el más cercano a la criatura o al jugador, ni el
  mismo dos veces). El salto está bloqueado en la arena mientras dura la pelea: `platform.MovementRules` (un gancho del juego base) que lee
  `LivingEntityJumpMixin`, una regla de `Ch2L2Maw` en el servidor y `JUMP_PACKET` para el cliente.
- **Los pilares** (fase 9; `maw.pillars`, anclas `maw_pillar_1..3` en la esquina que comparten las cuatro celdas de cada columna de 2 x 2, del suelo al techo, `catacomb_brick`, puestos por el
  mapa): una embestida que acaba contra uno (`Ch2L2Maw.pillarNear`, a menos de `hit_radius`) lo daña; `hits` de ellas (2) lo rompen: primero agrietado (otros ladrillos en
  su mitad, polvo y guijarros cayendo de la grieta) y luego roto (sus bloques de arriba caen como bloques que caen y se destruyen al llegar al suelo; queda un muñón de escombros de dos de alto).
  Una pared o un muñón no dañan nada (`StoneMawFight`, con tests). Cada embestida contra un pilar sacude la cámara y suena `event.ch2l2.maw_pillar_ram`, más hondo que el de la pared.
  Un pilar roto suelta `maw.debris`: `count` trozos de techo durante `spread_ticks` a menos de `radius`, cada uno anunciado en el suelo `warn_ticks` antes (polvo, `event.ch2l2.maw_debris_warn`) y
  que hace daño (`damage_fraction` de la vida) donde cae.
- **La muerte y la huida** (fase 9; la cinemática en tercera persona de la fase 6.2 ya no existe, ni `MawDeathScene`): al romperse el tercer pilar el techo cae
  sobre Stone Maw donde está, en vivo y en primera persona (`core.MawCollapse`, `plan` con una semilla fija, centrado en ella: `death.bit_count` trocitos, una losa de 3 x 3 que la aplasta (`crushed`) y
  `slab_count` - 1 más; un montículo de como mucho `max_height` a menos de `collapse_radius`; quien está cerca sale despedido y recibe un poco de daño), la música se corta en seco, el estado pasa a
  `MAW_ESCAPE` y se toma el checkpoint `maw_escape` (el último pilar y la celda del montículo se guardan en contadores: CONTINUAR devuelve la arena a su sitio y la huida vuelve a empezar),
  la puerta del fondo se abre y la arena se derrumba durante `escape.seconds` (8): `core.MawEscape` planea `debris_per_second` trozos por la arena (menos a menos de `door_clear_radius` de la puerta), anunciados como los otros,
  con `event.ch2l2.maw_escape_rumble` y temblor. Quien sale por la puerta del fondo la oye cerrarse de golpe a su espalda (`MAW_DEAD`, `Ch2L2Maw.finishDeath`, el final único e idempotente, también `skip maw`);
  quien sigue en la arena cuando se acaba el tiempo muere. Las animaciones `limp` y `dying` del modelo ya no se usan (siguen en `tools/entity/chapter2/vibration_creature.py`).
- **La música de la pelea** (fase 6.2): la música de caza del Guardia Observador, reciclada (`music.ch2l2.guard_chase`, 256 ticks, sin costuras), desde que se cierra la puerta de la arena, repetida cada 256 ticks por `Ch2L2Maw.music` (dos veces a la vez, la segunda a medio volumen, como la del Guardia);
  al romperse el último pilar se corta en seco.
- **El reencuentro** (`Ch2L2Reunion`, `reunion` en `level.json`, `ReunionScene` en `core`): en el centro de la sala (ahí está el checkpoint
  `reunion`) el estado pasa a `REUNION`, el compañero y el trabajador B (`FactoryNpcEntity`) están en sus anclas y una cinemática en primera
  persona gira los ojos del jugador hacia quien habla (`reunion.lines`: quién, clave de lang bajo `reunion.text`, sonido opcional, segundos). Los
  NPC hablan con las manos, escuchan y miran la puerta de vez en cuando; la escena acaba tras la última línea y el jugador recupera el control mientras los dos se ponen en marcha (fase 9, `reunion.lead`): el compañero abre la puerta del Nivel 3 (`l3_door`)
  y los guían por el pasillo (`lead_1..3`, a `lead.speed`, el paso del jugador; el segundo trabajador sigue la línea del compañero unos pasos detrás, nunca a su lado, un giro y una escalera corta), parándose a mirar atrás si el jugador queda a más de `wait_distance`; entran en el
  ascensor (`lift_room`, `lift_coworker`, `lift_worker_b`) y miran su reja. Con el jugador en `zone_lift` la reja se cierra (`event.ch2l2.lift_gate`), tras `gate_pause_seconds` arranca el motor
  (`event.ch2l2.lift_motor`, con temblor) y empieza el final. El salto de la escena es `Ch2L2Reunion.finish`: el mismo paseo. Las líneas son del usuario (voces de ElevenLabs, `tools/sounds/chapter2/ch2_l2_voices.py`, `voice.reunion_*`): cuatro por ahora (compañero, compañero,
  trabajador B, compañero), con sus subtítulos en `tools/lang/src/chapter2`; sin líneas la escena es una corta y muda.
- **Arranques** (entrar, una muerte, CONTINUAR, `reset maw`): todo como el estado quiere: la pelea terminada, las compuertas, los trozos fuera de
  los bolsillos, los escombros solo desde `MAW_DEAD`. Checkpoints `maw` (antes de la arena), `maw_escape` (lo toma el derrumbe, sin zona), `reunion` y `chapter_end` (lo toma el final, sin zona).

## Fase 7: la revisión integral

Una auditoría de la partida guardada, las muertes y las cinemáticas, con estos arreglos: morir en la arena de Stone Maw devuelve el salto al cliente; una
cinemática se descarta (sin efectos) cuando el nivel vuelve a empezar o el jugador muere; salir durante la muerte del Guardia o de Stone Maw conserva la
victoria (corre el final de la escena, y el de la Maw toma también el checkpoint del reencuentro); y al morir Stone Maw el jugador se pone en un punto de
vigilancia (radio del derrumbe + 2, lado de la entrada, mirando al centro) para que no le caiga ningún bloque, y la cinemática lo dibuja. No hizo falta un
suelo de reintento: la muerte ya da vida y recursos al máximo. `/et ch2 l2 perf` (ver Comandos de depuración) y los logros tienen sus propias secciones.

## Logros del Capítulo 2

*¿Qué era eso?* (ningún golpe de la criatura de las bombas al llegar a las catacumbas: contador `pump_hits`, que se pone a cero al empezar el nivel antes de
ellas), *Una despedida triste* (5 s vivo tras la muerte del Guardia observador), *Explorador\** (todas las zonas de `explore` de `level.json`, incluidas las dos
CRYPT opcionales: los contadores `seen.<zona>`; las zonas `room_*` las pone el generador) y *La puntería no es lo tuyo\** (más de dos tuberías que fallaron a
Stone Maw en un combate: contador `maw_misses`). Los ocultos llevan *. El resto está en [la página del Nivel 1](level-1.md#logros-del-capítulo-2).

## Fase 8: iluminación y decoración

**Luz.** En vanilla (sin shaders) el nivel se veía demasiado oscuro, así que las lámparas son ahora la `catacomb_lamp` del Capítulo 2 (luz
14; la `cage_lamp` del Nivel 5 sigue en 10) con un `tint` en la bombilla (la luz de vanilla no tiene color; los shaders colorean por
bloque): cálida en las catacumbas y el reencuentro, ámbar en la sala de bombas y el pozo, fría en el vestíbulo y mantenimiento, roja en el
colector, las pasarelas y la galería superior, blanca en la entrada, la estación, la salida y la arena del Maw (`layout.json`,
`lamp_tints`). En la ruta solo queda parpadeo (un 8 %, sin zonas oscuras ni lámparas muertas); las salas opcionales tienen alguna muerta.
Las lámparas propias del mod se suman (`lamp_extras`): `emergency_lamp` encendidas y cinco `alarm_light` (apagadas al construir; la primera
etapa de la inundación las enciende y se apagan al reintentar o reiniciar: `MapPlan.alarms`, `Ch2L2Build.setAlarms`) en el colector y las
pasarelas, y `wall_sconce` apagados en los muros viejos. Las lámparas son pocas y cuelgan altas (`lamp_density`: separación escalada, al menos 5 bloques sobre el suelo; la primera pasada tenía
demasiadas y demasiado bajas, y tapaban la cinemática del Guardia: el vestíbulo no tiene ninguna entre el jugador y el nido), así que la luz
del suelo es la del relleno. `checks.light` exige una luz de suelo de al menos 9 en cada celda donde se pueda estar en un espacio
iluminado (5 en las salas opcionales), contando las lámparas que parpadean o están muertas como apagadas; donde las lámparas dejan una
celda por debajo, `light.py` pone bloques `minecraft:light[level=14]` invisibles dos bloques por encima. La inundación los anega (siguen
encendidos) y los del vestíbulo están en `dim` (`MapPlan.dim`): el aviso del Guardia los atenúa con las lámparas (a la mitad mientras
parpadean, a cero cuando mueren: `Ch2L2Build.zoneLights`). `guard_nest` y `maw_pipe` siguen oscuros. Las barras de hierro del plan se
unen como las une el juego (`Level.connect_bars`: las jaulas de los canales tenían huecos en las esquinas).

**Decoración** (`layout.json`, reglas `decor` por estilo, ejecutadas por `decor.py`; nunca junto a una abertura, un ancla o el botín, ni
donde se estrechase el camino): las salas del drenaje llevan unos pocos tramos de tuberías y bandejas de cables por las paredes, válvulas y manómetros sobre las tuberías,
manchas de óxido bajas, bidones, palés y cajas en las esquinas; las catacumbas, escombros, ladrillos sueltos y manchas. Seis piezas de
escena son `prop`: los conos y el tablero de herramientas de la entrada, el campamento de un obrero en `side_service_crypt`, las
herramientas del autor del registro de mantenimiento en el mirador, el cuadro eléctrico y la estantería de la sala de trampas, las
taquillas, el botiquín, el reloj de fichar y una flecha de tiza hacia la salida en la estación, y una silla caída y marcas de tiza en
`side_gallery_room`. Bloques nuevos (`tools/blocks`): `catacomb_lamp`, `rubble_pile`, `loose_bricks`, `rust_stain`. Las imágenes
`light_*.png` y `decor_*.png` de la herramienta muestran la luz del suelo y la decoración de cada altura.

## El mapa

El nivel son **datos**: `tools/map/chapter2/ch2l2/layout.json` tiene todo aquello de lo que está hecho, y `tools/map/chapter2/ch2l2/generate.py` lo
convierte en lo que construye el mod. Nada de lo que escribe se edita a mano: se cambia `layout.json` (o la herramienta) y se vuelve a
ejecutar.

```bash
python3 tools/map/chapter2/ch2l2/generate.py --install
```

Primero pasa sus comprobaciones y no escribe nada si alguna falla. Luego escribe `map.json` (el plan), `anchors.json` (los lugares con
nombre, en coordenadas del mundo) y `flood.json` (las etapas de la inundación) en `tools/map/chapter2/ch2l2/output/` (ignorada por git) con sus
dibujos y, con `--install`, los tres JSON en `data/every_tunnel/ch2/l2/` (en el repositorio).

**El dibujo.** El plano conceptual del autor (fuera de git, en `docs/temporal/ch2l2/`) a 10 px del dibujo por bloque (fase 2.2; era 6 y
resultaba enorme): 150 x 105 bloques. Los pasillos (de 5-6 de ancho) se cruzan en ángulo recto; la sala de bombas mide 38 x 37, la de
mantenimiento 36 x 25, el colector central 20 x 55. Faltan la sala central del dibujo y la cripta junto al pasillo de entrada. El paso directo del dibujo desde las galerías de abajo a la sala de mantenimiento no existe: se da la vuelta
por un pasillo al oeste de la sala hasta el vestíbulo.

**Cuatro alturas**, `levels` en el archivo: **H** (y 20) la entrada, la estación y la salida; **M** (10) las galerías de los Hunters, el
balcón sobre el colector y el mirador de la sala de bombas; **L** (0) el suelo de la sala de bombas, las catacumbas, el vestíbulo y la
sala de mantenimiento; **D** (-8) el fondo de los colectores, donde está el agua. El colector central es un pozo de D hasta por encima
de H que se ve desde dos alturas: desde el balcón de las galerías (M) y desde el puente roto de la estación (H).

**`layout.json`:**

| Clave | Contiene |
|---|---|
| `origin`, `dimension`, `seed` | Dónde va la celda 0, 0, 0 del plan en el mundo (1024, 64, 1280, al sur de la del Nivel 1) y la semilla de cada detalle del aspecto |
| `levels` | Las cuatro alturas por nombre |
| `styles` | Los aspectos: `masonry` (las catacumbas antiguas: ladrillo oscuro y húmedo propio del Capítulo 2, `catacomb_*`, agrietado, con salitre y con marca de agua al pie de las paredes, bóveda de ladrillo, losas, nichos vacíos, charcos), `drain` (el drenaje de la fábrica: los bloques del desagüe del Nivel 5, que el paquete de recursos del mundo pinta como hormigón manchado, charcos), `machine`. Cada uno da a cada papel de la cáscara (pared, zócalo, suelo, techo) un bloque o una mezcla con pesos |
| `spaces` | Cada espacio: sus cajas, su suelo (una altura o un número), la altura de su aire, su estilo, sus lámparas (`ceiling`; `cable`, bombillas de obra colgadas de un cable por la bóveda; `wall`, lámparas en las paredes sobre cada superficie; los `lamp_gaps` dejan algunas bombillas fuera, parpadeando o muertas, según la semilla, nunca junto a un ancla); un `ramp` hace de un espacio entero un tramo de escaleras (las de bajada desde la entrada, la galería que sube a la estación) |
| `links` | Espacios cuyo aire se junta sin más (un balcón sobre el pozo, las escaleras y los pasillos) |
| `openings` | Pasos a través de una pared de un bloque: arcos, una ventana de barrotes y la **reja** (`vestibule_gate`) con un bloque para cada uno de sus estados |
| `features` | Lo que hay dentro de un espacio: tabiques y pilares, bloques macizos (el mirador, la pasarela del colector, la carcasa de la bomba), tramos de escaleras, plataformas (pasarelas, el puente roto), canales con agua, agua quieta, barandillas, escalerillas, parches de suelo (las cuatro trampas), arcos tapiados, y `prop`: un bloque de mobiliario, cartel o tablero de notas dentro del aire de un espacio (`space`, `at` [x, z], `dy`, `block`; con `text` y `style` para un cartel wall_text, que lleva el `texts` del plan) |
| `anchors`, `zones` | Los lugares con nombre y las cajas que vigilarán los sistemas del nivel (más abajo) |
| `route` | El camino por el nivel, en orden, de `sealed_entry` a `level_exit` |
| `checks` | A qué se ajusta el nivel en las comprobaciones |
| `flood` | Las tres etapas de la inundación (fase 6): zona que la dispara, retraso, segundos, nivel, el estado de las compuertas mientras tiene agua. El rasgo `fill` pone un bloque en cada celda de una caja del aire de un espacio (la puerta de la salida) |

**Los detalles del aspecto** (qué ladrillo está agrietado o húmedo, dónde hay un nicho o un charco) salen de la semilla: el
generador elige con `cell_hash` y el mod con `MapPlan.pick`, la misma función bit a bit (`MapPlanTest` las mantiene iguales), así que lo
que se ve en el dibujo es lo que construye el juego. No se usa ningún bloque vanilla que el paquete del mundo pinte como del Capítulo 1,
salvo los del desagüe (`Ch2L2MapPlanTest`).

**Las comprobaciones** (`checks.py`), en cada generación:
- la estructura: ningún espacio se solapa con otro, cada abertura une dos espacios, cada elemento está dentro de su espacio, cada ancla
  tiene suelo debajo y sitio encima, y las zonas de los lugares del recorrido no se solapan entre sí (el mod sabe dónde está el jugador
  por ellas; la del mirador, dentro de la de la sala de bombas, es la única pareja permitida, `nested`): la zona de un espacio en L es su
  caja, que puede meterse en sus vecinos, así que la de la galería de retorno es su tramo sur (`box`);
- el recorrido andado como lo anda un jugador (un escalón de uno, una caída de tres como mucho, escalerillas, sin nadar), con su longitud
  (unos 580 bloques, 2,5 minutos solo andando);
- **sin atajos**: todo camino de la entrada a la salida, cayendo y nadando incluidos, pasa por las galerías, la sala de bombas, las
  catacumbas, el vestíbulo, las pasarelas, la galería superior y la estación; con la reja bajada (`grate`) no se llega a nada de lo que
  hay detrás;
- **ningún sitio donde quedarse atrapado**: desde cualquier sitio al que se llegue desde la entrada se puede seguir llegando a la salida
  (un balcón sin su pretil, por ejemplo, dejaría caer al pozo sin salida);
- **la sala de bombas** pide varios ciclos de la bomba: su camino más corto de lado a lado, nadando incluido, es de `min` bloques como
  poco (57 ahora: el suelo lo cortan dos canales de cinco de ancho, enjaulados: barrotes en sus bordes y una tapa de rejilla, así que nadie
  entra ni los cruza);
- **colocaciones**: el botín está en aire libre sobre algo sólido, cada ancla de nota está en un tablero de notas y cada ancla de Hunters
  existe;
- las aberturas están despejadas: dos bloques de aire a través y sitio para estar a ambos lados;
- **el agua no se sale**: el agua quieta y cada etapa de la inundación no tocan nada por donde pase el agua (aire, barrotes, losas...)
  fuera de sus propios espacios, con las compuertas como dice la etapa;
- la construcción: cuántas celdas se vacían y se llenan y cuánto tardan las tandas (unos dos millones de celdas, casi todas vacías:
  unos segundos), frente a `max_build_seconds`.

**Los dibujos**, en `tools/map/chapter2/ch2l2/output/`: `level_preview.png` (todo el nivel desde arriba, más claro cuanto más alto, el recorrido en
amarillo, las anclas con su nombre), `level_H.png` a `level_D.png` (una altura cada uno) y `flood_0.png` a `flood_3.png` (el agua quieta y
luego tras cada etapa).

| Lugar (ancla) | Espacio | Altura |
|---|---|---|
| `sealed_entry` | La entrada, al pie de la escalerilla de la alcantarilla (aquí baja la alcantarilla de emergencia del Nivel 1) | H |
| `collector_junction` | El balcón sobre el colector central, junto a la sala del medio de las galerías | M |
| `hunter_galleries` | Las galerías: tres salas abiertas por el sur | M |
| `pump_overlook` | El mirador de la sala de bombas: un bloque de hormigón enjaulado en su esquina noroeste | M |
| `vibration_chamber` | La sala de bombas: dos canales enjaulados cruzan su suelo, los cruces en extremos opuestos (un zigzag); la salida se ve desde el mirador | L |
| `lower_catacombs` | Las catacumbas y las galerías de abajo (sin nada al lado ahora) | L |
| `observer_wall` | El vestíbulo (`observer_wall_face`: el centro del parche de pared del que sale el Guard; `guard_nest` detrás; `boo_wall`: el bloque de BOO! sobre el hueco) | L |
| `observer_chamber` | La sala de mantenimiento, sus cuatro trampas donde el dibujo tiene sus cuatro cuadrados (`trap_1` a `trap_4`) | L |
| `return_gallery` | Alrededor de la sala de mantenimiento por el este y el sur, de vuelta al vestíbulo | L |
| `flood_collector` | El colector, una pasarela a lo largo de su canal, pasada la reja del vestíbulo y cuatro escalones abajo | D |
| `rising_walkways` | La sala de las pasarelas: de la pasarela a la pasarela baja y de ahí a la alta | D a M |
| `upper_gallery` | Las escaleras que suben a la estación | M a H |
| `pumping_station` | La estación, seca; un puente roto sobre el pozo, visto a través de barrotes | H |
| `level_exit` | La sala de la salida, tras un pasillo tranquilo | H |
| `maw_entry`, `maw_centre`, `reunion_player`, `chapter_end` | El pasillo de entrada de la arena, el centro de la arena, el centro de la sala del reencuentro y el ascensor (fase 6.1, 9) | H |
| `survival_kit`, `hunters_post`, `loot_galleries`, `loot_catacombs`, `loot_station` | De la fase 3: el mostrador del kit, el puesto de los Hunters y los tres sitios de botín, en los espacios de arriba | H, M, L |
| `note_old_drainage`, `note_maintenance`, `note_withdrawal` | La celda propia de un tablero de notas (`kind: wall`: no es un sitio donde estar) | H, M, L |

Casi todas las salas del dibujo son criptas cerradas, con los arcos tapiados por la fábrica; dos están abiertas, salas cortas a un
lado para los recursos y documentos de fases posteriores (una cripta junto a las galerías y otra junto al pasillo de servicio, antes del
vestíbulo).

**Las puertas del Guard.** Además de la de abajo, `service_far_gate`, `chamber_exit` y `return_door` son puertas con estados `grate`/`open`, construidas abiertas, y `guard_wall` tiene `whole` (construido, `catacomb_bricked`) y `broken`; `Ch2L2Build.setGate` pone cualquiera. La comprobación `arena` del plan demuestra que con las cuatro rejas abajo, desde el vestíbulo solo se llega a la sala de las trampas.

**La reja.** El paso del vestíbulo al colector (`vestibule_gate`) tiene tres estados: `grate` (bajada: barrotes de hierro, hasta vencer
al Guard), `open` y `sealed` (la plancha de una compuerta, estanca). La fase 1 la construye abierta, para poder recorrer el nivel de punta
a punta. Las etapas de la inundación la necesitan `sealed` (los barrotes no retienen el agua): **cuando empieza la inundación, el camino de
vuelta se cierra detrás del jugador.**

## Los archivos de datos

El jar tiene los valores por defecto en `data/every_tunnel/ch2/l2/`; un archivo con el mismo nombre en `config/every_tunnel/ch2/l2/`
ocupa su lugar mientras sirva, y `/et reload` los vuelve a leer (junto con los del Nivel 1). El nivel lee `anchors.json` (del generador)
y `level.json` (escrito a mano, con los ajustes de la inundación y el final); `flood.json` es del generador y se puede sustituir para afinar el tiempo de una etapa, `map.json` no. Lo que nombra `level.json` se busca en
las anclas, y lo que está mal se avisa (`/et reload`).

**`level.json`** (`level2.core.Ch2L2Level`):

| Clave | Contiene |
|---|---|
| `rules` | `flashlight` (true: la linterna de Rowan funciona), `noise` (true: los pasos hacen ruido que oyen las criaturas) |
| `start` | Al llegar del Nivel 1, `battery`, `thirst` y `stamina` como parte del máximo (0 a 1; 1, 1, 1). Morir o CONTINUAR los deja todos llenos |
| `lights` | Las lámparas de cada zona: `on`, `flicker` u `off`, puestas cada vez que se empieza el nivel (al llegar, CONTINUAR, `reset`). Vacío: las lámparas del plan (con sus huecos) quedan como se construyen; una zona listada aquí encendería todas sus lámparas en `on`/`flicker`/`off` |
| `checkpoints` | En el orden del camino: `id`, `anchor` (adonde devuelven morir y CONTINUAR), `zone` (al entrar se toma), `before` (el encuentro al que precede, para `reset`) |
| `progress` | `zone` y `state`: entrar en la zona lleva al jugador al estado |
| `hunters` | `id`, `anchor`, `yaw` opcional, `until` opcional (un estado; por defecto `pump_room`): un grupo de Hunters puesto en el ancla |
| `loot` | `id`, `item` (una clave de `LootSpawner.LOOT`: `battery`, `water`, `food_snack`, `wrench`...), `anchor`, `offset` [dx, dy, dz] bloques desde el bloque del ancla, `yaw` |
| `notes` | `id`, `anchor` (el del tablero); el texto es `ch2_l2_<id>` |
| `creature` | La criatura de vibraciones y la bomba (abajo) |

**La criatura** (`level2.core.Ch2L2Creature`, `VibrationCreatureBrain`, `PumpCycle`; `platform.VibrationCreatureEntity`, `Ch2L2Pump`): el Stone Maw, 3,5 bloques de piedra gris (caja de colisión 1,6 x 3,5), en reposo en `creature.home`. Siente los pasos, saltos, aterrizajes y toques del jugador en las zonas de `floor_zones` (el mirador no es una) y la caída de la tubería, nunca sus propios pasos ni la bomba; ir agachado es suave, correr, saltar y aterrizar fuerte. Un paso suave: se acerca al acecho a `stalk_speed`, escucha `stalk_listen_seconds` y luego busca. Cualquier otro: `orient_seconds` girándose hacia él (chasquido de dientes; uno nuevo mueve el punto), luego el punto queda fijado y carga a `charge_speed` (`charge_speed_loud` si fue fuerte). En el punto, en el sitio más cercano al que llega o al tocar a un jugador (`touch_range`), alza los puños durante `windup_seconds` (la escapatoria) y el golpe hiere a quien esté entonces a menos de `contact_range` de su centro por `damage_fraction` de su vida máxima (dos matan; la vida se baja directamente, el daño de vanilla lo recortarían la dificultad y la armadura), como mucho cada `hit_interval_seconds`; luego `recover_seconds` quieta, una búsqueda por `search_points` puntos a `search_radius` del golpe (`search_speed`, `search_listen_seconds` en cada uno) y a casa a `walk_speed`. Las velocidades son bloques por segundo. Nunca toma un camino parado por una llegada: un punto al que no llega (en un canal) lo golpea desde el sitio más cercano. La bomba (`creature.pump`: `anchor` donde suena, `beacons`, `run_seconds`, `silent_seconds`, `slow_seconds`) funciona mientras haya en la sala un jugador que no ha pasado el encuentro: pistones pesados, tres balizas ámbar (`pump_beacon`, en la carcasa y en dos postes de pared; parpadean en el último segundo) y polvo que cae del techo alrededor del jugador; enmascara las vibraciones mientras funciona y `grace_after_stop_seconds` después. En el primer silencio de un intento cae una tubería desde `pipe.from` tras `pipe.delay_seconds`; hasta que la criatura golpea donde cae, no siente nada más. Llegar a las catacumbas termina el encuentro.

## Comandos de depuración

`level2.platform.Ch2L2Commands`, nivel de operador 2, **solo en ejecuciones de desarrollo**:

| Comando | Qué hace |
|---|---|
| `/et ch2 l2 enter` | Empieza el nivel como al bajar por la alcantarilla del Nivel 1: se construye si no lo está, bolsillos vacíos, los recursos de `start`, en `sealed_entry`, tomado su checkpoint. Sin vuelta al Capítulo 1 (al salir del mundo sigues allí) |
| `/et ch2 l2 leave` | Fuera del nivel: vuelven las reglas del resto del juego; la partida guardada se queda |
| `/et ch2 l2 state` | Tu estado, el último checkpoint y el estado que guardó, la etapa de la inundación, cada encuentro (`ahead`, `now`, `past`), los contadores |
| `/et ch2 l2 state <estado>` | Te pone en un estado (se avisa a todos los sistemas del nivel, como si hubieras llegado jugando) |
| `/et ch2 l2 skip [<evento>]` | Al estado siguiente, o más allá de un encuentro (`hunters`, `creature`, `guard`, `flood`) como si se hubiera vivido |
| `/et ch2 l2 reset <evento>` | Un encuentro para vivirlo otra vez: el estado de antes, tú en el checkpoint de antes (tomado), el nivel empezado allí |
| `/et ch2 l2 build` | Vuelve a construir las catacumbas encima de lo que haya (un plan nuevo se construye solo cuando `goto` lo necesita) |
| `/et ch2 l2 hunters` | Los Hunters del nivel, enteros otra vez en sus puestos, sea cual sea tu estado |
| `/et ch2 l2 loot` | Todo el botín como si no se hubiera cogido nada: tus contadores olvidados, tus bolsillos se quedan |
| `/et ch2 l2 unbuild` | Vacía toda la zona (antes de empaquetar el mundo en un jar), también los Hunters; quien esté dentro cae |
| `/et ch2 l2 creature state` / `pump` | El cerebro de la criatura (estado, objetivo, velocidad en bloques por segundo) o el ciclo de la bomba (fase, tick, enmascarado) en la barra de acción; otra vez para apagarlo |
| `/et ch2 l2 guard` | El combate del Guard (fase, aturdimientos, cada trampa: S gastada, C cargando, A armada) en la barra de acción; otra vez para apagarlo. `skip guard` ejecuta su final al instante, `reset guard` vuelve a poner la pared entera |
| `/et ch2 l2 flood <0\|1\|2\|3>` | El agua de la inundación al instante en esa etapa (0 la vacía), sea cual sea tu estado; `skip flood` la llena toda y cierra la puerta de la estación, `reset flood` la vacía y reabre la compuerta del vestíbulo |
| `/et ch2 l2 ending` | Reproduce el final (fundido, "CONTINUARÁ", menú principal); el nivel solo se termina si estás en el reencuentro o más allá |
| `/et ch2 l2 maw` / `maw hit` / `maw kill` / `maw pillar <n>` | La pelea de Stone Maw en el chat (fase, pilares rotos, modo) / cuenta un golpe / rompe a la vez los pilares que quedan (el derrumbe y la huida) / daña una vez el pilar n. `skip maw` ejecuta el final de la huida, `reset maw` devuelve la pelea a su inicio |
| `/et ch2 l2 reunion` | Te pone en el punto de la sala del reencuentro y suena el reencuentro |
| `/et ch2 l2 perf` | Lo que cuesta el nivel en tu mundo (fase 7): sus entidades por tipo (un segundo Guardia, criatura, Maw o PNJ del reencuentro sale marcado `DUPLICATES`), las celdas de agua de la inundación, las lámparas por modo, y los ms medios y peores que un tick del servidor gasta en los manejadores del nivel en los últimos 100 ticks, por manejador |
| `/et ch2 l2 anchors` | Cada ancla y zona con sus coordenadas, marcadas las provisionales |
| `/et ch2 l2 goto <ancla>` | Construye el nivel si no lo está y te lleva a un lugar (`sealed_entry` ... `level_exit`, `trap_1`...) o al centro de una zona (`zone_<lugar>`) |
| `/et reload` | Vuelve a leer los datos de los Niveles 1 y 2 y dice qué está mal |

## Tests

`MapPlanTest` (el formato del plan: un bloque, mezclas elegidas como las elige el generador, compuertas, la huella) y
`Ch2PlayerProgressTest` (el estado, los contadores y la partida guardada del Nivel 2, aparte de los del Nivel 1) en `chapter2/core`;
`Ch2L2MapPlanTest` (el plan del jar: su origen, cada relleno dentro de la caja que se vacía y ninguna celda dos veces, cada bloque
conocido y ninguno pintado por el paquete del mundo salvo los del desagüe, las anclas y zonas del recorrido, cada sitio para estar de pie
con suelo, la reja abierta al construir, las lámparas, que el ancla de una nota sea su tablero) y `Ch2L2LevelTest` (los estados y encuentros; el `level.json` del jar sin
problemas; los checkpoints en el orden del recorrido, cada uno en su zona, uno antes de cada encuentro; andar el recorrido solo hace
avanzar el estado, y cada checkpoint repite su encuentro; un override roto vuelve a la copia del jar) en `chapter2/level2/core`;
`Ch2L2PlacementsTest` (los Hunters, el kit, la tabla de botín y las notas del jar; cómo trata el parser la entrada mala) en el mismo
sitio; `DeathCauseTest` (el nivel de un capítulo posterior dice él mismo si es tranquilo).
