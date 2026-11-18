# Capítulo 2, Nivel 2

*[English version](../en/chapter-2-level-2.md) · [Todas las páginas](README.md)*

El Nivel 2 del Capítulo 2 son las catacumbas bajo la fábrica: las catacumbas antiguas en las que se metió el drenaje de la fábrica. El
científico baja por la alcantarilla de emergencia del Nivel 1, solo, y busca una salida: los Hunters en las galerías, una criatura que
caza por las vibraciones del suelo en la sala de bombas, el Observant Guard del Capítulo 1 en las cámaras de abajo y una inundación en tres
etapas camino de la estación de bombeo y la salida. Se construye por fases (de la 1 a la 7), una cada vez, en la rama **privada**
`chapter-2/level-2` (sale de `chapter-2/level-1` y solo se sube al remoto `private`) hasta que se publiquen los Niveles 1 y 2 del capítulo.
**En qué punto está: fase 4, la criatura de vibraciones y la bomba.** Se llega bajando por la alcantarilla del Nivel 1 y se
recorre de la entrada a la salida con las barras de supervivencia; morir devuelve al último checkpoint, y el Capítulo 2 del menú lo
continúa; el primer tramo ya tiene sus enemigos, recursos y documentos, y la sala de bombas tiene su criatura y su bomba. Faltan el Guard y la inundación.

Su código está en `daio7771.everytunnel.chapter2.level2` (`core`, Kotlin puro, y `platform`), aparte del juego base, que nunca lo nombra
(véase [Arquitectura](architecture.md#capítulos)). Sus lugares con nombre usan el formato del Nivel 1 (`level1.core.Ch2L1AnchorsParser`); su
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
| `GUARD_DEAD` | De vuelta al vestíbulo, BOO! y la reja arriba (fase 5) | El cuarto aturdimiento del Guard (fase 5; hasta entonces, por comando) |
| `FLOOD_1` a `FLOOD_3` | El colector, las pasarelas, la galería de subida: las etapas de la inundación (fase 6) | `zone_flood_collector`, `zone_rising_walkways`, `zone_upper_gallery` |
| `STATION` | La estación de bombeo, seca | `zone_pumping_station` |
| `DONE` | Fuera por la salida (fase 6) | (fase 6) |

Los cuatro **encuentros** (`Ch2L2Event`) son tramos de estados: `hunters` (`GALLERIES`), `creature` (`PUMP_ROOM`), `guard` (de
`GUARD_APPEARS` a `GUARD_FIGHT`) y `flood` (de `FLOOD_1` a `FLOOD_3`). La etapa de la inundación es la del estado (0 antes, 3 desde la
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
(`tools/lang/src/chapter2/*.json`, claves `every_tunnel.ch2.l2.<id>_title` y `_body`); `tools/lang/langfiles.py` (`NOTES`,
`write_notes`, que ejecuta `build`) escribe a partir de ellas `assets/every_tunnel/notes/<en|es>/ch2_l2_<id>.txt`. Un tablero se lee
a través de `NoteHooks.Source`; el contador `read.<id>` lo recuerda. La clave `every_tunnel.ch2.l2.boo` queda para la fase 5.

## El mapa

El nivel son **datos**: `tools/map/ch2l2/layout.json` tiene todo aquello de lo que está hecho, y `tools/map/ch2l2/generate.py` lo
convierte en lo que construye el mod. Nada de lo que escribe se edita a mano: se cambia `layout.json` (o la herramienta) y se vuelve a
ejecutar.

```bash
python3 tools/map/ch2l2/generate.py --install
```

Primero pasa sus comprobaciones y no escribe nada si alguna falla. Luego escribe `map.json` (el plan), `anchors.json` (los lugares con
nombre, en coordenadas del mundo) y `flood.json` (las etapas de la inundación) en `tools/map/ch2l2/output/` (ignorada por git) con sus
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
| `flood` | Las tres etapas de la inundación (fase 6): zona que la dispara, retraso, segundos, nivel, el estado de las compuertas mientras tiene agua |

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

**Los dibujos**, en `tools/map/ch2l2/output/`: `level_preview.png` (todo el nivel desde arriba, más claro cuanto más alto, el recorrido en
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
| `observer_wall` | El vestíbulo (`observer_wall_face`: la pared de la que sale el Guard, provisional) | L |
| `observer_chamber` | La sala de mantenimiento, sus cuatro trampas donde el dibujo tiene sus cuatro cuadrados (`trap_1` a `trap_4`) | L |
| `return_gallery` | Alrededor de la sala de mantenimiento por el este y el sur, de vuelta al vestíbulo | L |
| `flood_collector` | El colector, una pasarela a lo largo de su canal, pasada la reja del vestíbulo y cuatro escalones abajo | D |
| `rising_walkways` | La sala de las pasarelas: de la pasarela a la pasarela baja y de ahí a la alta | D a M |
| `upper_gallery` | Las escaleras que suben a la estación | M a H |
| `pumping_station` | La estación, seca; un puente roto sobre el pozo, visto a través de barrotes | H |
| `level_exit` | La sala de la salida, tras un pasillo tranquilo | H |
| `survival_kit`, `hunters_post`, `loot_galleries`, `loot_catacombs`, `loot_station` | De la fase 3: el mostrador del kit, el puesto de los Hunters y los tres sitios de botín, en los espacios de arriba | H, M, L |
| `note_old_drainage`, `note_maintenance`, `note_withdrawal` | La celda propia de un tablero de notas (`kind: wall`: no es un sitio donde estar) | H, M, L |

Casi todas las salas del dibujo son criptas cerradas, con los arcos tapiados por la fábrica; dos están abiertas, salas cortas a un
lado para los recursos y documentos de fases posteriores (una cripta junto a las galerías y otra junto al pasillo de servicio, antes del
vestíbulo).

**La reja.** El paso del vestíbulo al colector (`vestibule_gate`) tiene tres estados: `grate` (bajada: barrotes de hierro, hasta vencer
al Guard), `open` y `sealed` (la plancha de una compuerta, estanca). La fase 1 la construye abierta, para poder recorrer el nivel de punta
a punta. Las etapas de la inundación la necesitan `sealed` (los barrotes no retienen el agua): **cuando empieza la inundación, el camino de
vuelta se cierra detrás del jugador.**

## Los archivos de datos

El jar tiene los valores por defecto en `data/every_tunnel/ch2/l2/`; un archivo con el mismo nombre en `config/every_tunnel/ch2/l2/`
ocupa su lugar mientras sirva, y `/et reload` los vuelve a leer (junto con los del Nivel 1). El nivel lee `anchors.json` (del generador)
y `level.json` (escrito a mano); `map.json` y `flood.json` son del generador y no se sustituyen. Lo que nombra `level.json` se busca en
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
