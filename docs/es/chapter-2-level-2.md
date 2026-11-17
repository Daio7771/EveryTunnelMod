# Capítulo 2, Nivel 2

*[English version](../en/chapter-2-level-2.md) · [Todas las páginas](README.md)*

El Nivel 2 del Capítulo 2 son las catacumbas bajo la fábrica: las catacumbas antiguas en las que se metió el drenaje de la fábrica. El
científico baja por la alcantarilla de emergencia del Nivel 1, solo, y busca una salida: los Hunters en las galerías, una criatura que
caza por las vibraciones del suelo en la sala de bombas, el Observant Guard del Capítulo 1 en las cámaras de abajo y una inundación en tres
etapas camino de la estación de bombeo y la salida. Se construye por fases (de la 1 a la 7), una cada vez, en la rama **privada**
`chapter-2/level-2` (sale de `chapter-2/level-1` y solo se sube al remoto `private`) hasta que se publiquen los Niveles 1 y 2 del capítulo.
**En qué punto está: fase 2, la entrada, la supervivencia y la partida guardada.** Se llega bajando por la alcantarilla del Nivel 1 y se
recorre de la entrada a la salida con las barras de supervivencia; morir devuelve al último checkpoint, y el Capítulo 2 del menú lo
continúa. Todavía no hay enemigos, documentos ni inundación.

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

**El dibujo.** El plano conceptual del autor (fuera de git, en `docs/temporal/ch2l2/`) a 6 px del dibujo por bloque: unos 256 x 170
bloques. Los pasillos se cruzan en ángulo recto; las salas son gigantes (la sala de bombas 58 x 53, la de mantenimiento 60 x 52, el
colector central 36 x 102). El paso directo del dibujo desde las galerías de abajo a la sala de mantenimiento no existe: se da la vuelta
por un pasillo al oeste de la sala hasta el vestíbulo.

**Cuatro alturas**, `levels` en el archivo: **H** (y 20) la entrada, la estación y la salida; **M** (10) las galerías de los Hunters, el
balcón sobre el colector y el mirador de la sala de bombas; **L** (0) el suelo de la sala de bombas, las catacumbas, el vestíbulo y la
sala de mantenimiento; **D** (-8) el fondo de los colectores, donde está el agua. El colector central es un pozo de D hasta por encima
de H que se ve desde las tres alturas: desde el balcón de las galerías (M), desde la ventana con barrotes de la sala central (L) y desde
el puente roto de la estación (H).

**`layout.json`:**

| Clave | Contiene |
|---|---|
| `origin`, `dimension`, `seed` | Dónde va la celda 0, 0, 0 del plan en el mundo (1024, 64, 1280, al sur de la del Nivel 1) y la semilla de cada detalle del aspecto |
| `levels` | Las cuatro alturas por nombre |
| `styles` | Los aspectos: `masonry` (las catacumbas antiguas: ladrillo de piedra agrietado y con musgo, ladrillo de pizarra al pie de las paredes, nichos vacíos, musgo), `drain` (el drenaje de la fábrica: los bloques del desagüe del Nivel 5, que el paquete de recursos del mundo pinta como hormigón manchado, charcos), `machine`. Cada uno da a cada papel de la cáscara (pared, zócalo, suelo, techo) un bloque o una mezcla con pesos |
| `spaces` | Cada espacio: sus cajas, su suelo (una altura o un número), la altura de su aire, su estilo, sus lámparas; un `ramp` hace de un espacio entero un tramo de escaleras (las de bajada desde la entrada, la galería que sube a la estación) |
| `links` | Espacios cuyo aire se junta sin más (un balcón sobre el pozo, las escaleras y los pasillos) |
| `openings` | Pasos a través de una pared de un bloque: arcos, una ventana de barrotes y la **reja** (`vestibule_gate`) con un bloque para cada uno de sus estados |
| `features` | Lo que hay dentro de un espacio: tabiques y pilares, bloques macizos (el mirador, la pasarela del colector, la carcasa de la bomba), tramos de escaleras, plataformas (pasarelas, el puente roto), canales con agua, agua quieta, barandillas, escalerillas, parches de suelo (las cuatro trampas), arcos tapiados |
| `anchors`, `zones` | Los lugares con nombre y las cajas que vigilarán los sistemas del nivel (más abajo) |
| `route` | El camino por el nivel, en orden, de `sealed_entry` a `level_exit` |
| `checks` | A qué se ajusta el nivel en las comprobaciones |
| `flood` | Las tres etapas de la inundación (fase 6): zona que la dispara, retraso, segundos, nivel, el estado de las compuertas mientras tiene agua |

**Los detalles del aspecto** (qué ladrillo está agrietado o con musgo, dónde hay un nicho, un charco o musgo) salen de la semilla: el
generador elige con `cell_hash` y el mod con `MapPlan.pick`, la misma función bit a bit (`MapPlanTest` las mantiene iguales), así que lo
que se ve en el dibujo es lo que construye el juego. No se usa ningún bloque vanilla que el paquete del mundo pinte como del Capítulo 1,
salvo los del desagüe (`Ch2L2MapPlanTest`).

**Las comprobaciones** (`checks.py`), en cada generación:
- la estructura: ningún espacio se solapa con otro, cada abertura une dos espacios, cada elemento está dentro de su espacio, cada ancla
  tiene suelo debajo y sitio encima, y las zonas de los lugares del recorrido no se solapan entre sí (el mod sabe dónde está el jugador
  por ellas; la del mirador, dentro de la de la sala de bombas, es la única pareja permitida, `nested`): la zona de un espacio en L es su
  caja, que puede meterse en sus vecinos, así que la de la galería de retorno es su tramo sur (`box`);
- el recorrido andado como lo anda un jugador (un escalón de uno, una caída de tres como mucho, escalerillas, sin nadar), con su longitud
  (unos 1.000 bloques, 4 minutos solo andando);
- **sin atajos**: todo camino de la entrada a la salida, cayendo y nadando incluidos, pasa por las galerías, la sala de bombas, las
  catacumbas, el vestíbulo, las pasarelas, la galería superior y la estación; con la reja bajada (`grate`) no se llega a nada de lo que
  hay detrás;
- **ningún sitio donde quedarse atrapado**: desde cualquier sitio al que se llegue desde la entrada se puede seguir llegando a la salida
  (un balcón sin su pretil, por ejemplo, dejaría caer al pozo sin salida);
- **la sala de bombas** pide varios ciclos de la bomba: su camino más corto de lado a lado, nadando incluido, es de `min` bloques como
  poco (94 ahora: el suelo lo cortan dos canales de cinco de ancho, más de lo que se salta corriendo, y sus escalerillas solo están junto
  a los cruces);
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
| `vibration_chamber` | La sala de bombas: dos canales cruzan su suelo, los cruces en extremos opuestos (un zigzag); la salida se ve desde el mirador | L |
| `lower_catacombs` | Las catacumbas y las galerías de abajo (con la sala central al lado, opcional) | L |
| `observer_wall` | El vestíbulo (`observer_wall_face`: la pared de la que sale el Guard, provisional) | L |
| `observer_chamber` | La sala de mantenimiento, sus cuatro trampas donde el dibujo tiene sus cuatro cuadrados (`trap_1` a `trap_4`) | L |
| `return_gallery` | Alrededor de la sala de mantenimiento por el este y el sur, de vuelta al vestíbulo | L |
| `flood_collector` | El colector, una pasarela a lo largo de su canal, pasada la reja del vestíbulo y cuatro escalones abajo | D |
| `rising_walkways` | La sala de las pasarelas: de la pasarela a la pasarela baja y de ahí a la alta | D a M |
| `upper_gallery` | Las escaleras que suben a la estación | M a H |
| `pumping_station` | La estación, seca; un puente roto sobre el pozo, visto a través de barrotes | H |
| `level_exit` | La sala de la salida, tras un pasillo tranquilo | H |

Casi todas las salas del dibujo son criptas cerradas, con los arcos tapiados por la fábrica; cuatro están abiertas, salas cortas a un
lado para los recursos y documentos de fases posteriores (dos criptas junto a las galerías, la sala central y una cripta antes del
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
| `lights` | Las lámparas de cada zona: `on`, `flicker` u `off`, puestas cada vez que se empieza el nivel (al llegar, CONTINUAR, `reset`). Están las siete zonas con lámparas, todas `on` |
| `checkpoints` | En el orden del camino: `id`, `anchor` (adonde devuelven morir y CONTINUAR), `zone` (al entrar se toma), `before` (el encuentro al que precede, para `reset`) |
| `progress` | `zone` y `state`: entrar en la zona lleva al jugador al estado |

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
| `/et ch2 l2 unbuild` | Vacía toda la zona (antes de empaquetar el mundo en un jar); quien esté dentro cae |
| `/et ch2 l2 anchors` | Cada ancla y zona con sus coordenadas, marcadas las provisionales |
| `/et ch2 l2 goto <ancla>` | Construye el nivel si no lo está y te lleva a un lugar (`sealed_entry` ... `level_exit`, `trap_1`...) o al centro de una zona (`zone_<lugar>`) |
| `/et reload` | Vuelve a leer los datos de los Niveles 1 y 2 y dice qué está mal |

## Tests

`MapPlanTest` (el formato del plan: un bloque, mezclas elegidas como las elige el generador, compuertas, la huella) y
`Ch2PlayerProgressTest` (el estado, los contadores y la partida guardada del Nivel 2, aparte de los del Nivel 1) en `chapter2/core`;
`Ch2L2MapPlanTest` (el plan del jar: su origen, cada relleno dentro de la caja que se vacía y ninguna celda dos veces, cada bloque
conocido y ninguno pintado por el paquete del mundo salvo los del desagüe, las anclas y zonas del recorrido, cada sitio para estar de pie
con suelo, la reja abierta al construir, las lámparas) y `Ch2L2LevelTest` (los estados y encuentros; el `level.json` del jar sin
problemas; los checkpoints en el orden del recorrido, cada uno en su zona, uno antes de cada encuentro; andar el recorrido solo hace
avanzar el estado, y cada checkpoint repite su encuentro; un override roto vuelve a la copia del jar) en `chapter2/level2/core`;
`DeathCauseTest` (el nivel de un capítulo posterior dice él mismo si es tranquilo).
