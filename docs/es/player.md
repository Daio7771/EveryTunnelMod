# Sistemas del jugador

*[English version](../en/player.md) · [Todas las páginas](README.md)*

Todo lo de esta página se aplica solo a **jugadores en Supervivencia o Aventura en un mundo
con niveles** (`WorldRules.appliesTo`). Creativo y Espectador están exentos, para poder
construir el mapa a mano.

## Reglas del mundo

`world.WorldRules` decide; `platform.WorldRulesHooks` las hace cumplir:

- **No se rompe nada.** Los ataques a bloques se cancelan (sin progreso de minado), y el
  servidor rechaza romper como red de seguridad.
- **No se colocan ni cambian bloques con objetos** (bloques, cubos, mechero, polvo de hueso,
  azadas...). Los jugadores en Supervivencia reciben `allowModifyWorld = false`, la misma
  marca que usa Aventura. Puertas, botones y palancas siguen funcionando.
- **Sin aparición de mobs de vanilla.** Las gamerules de aparición se ponen en false al
  arrancar el mundo y tras `/reload`. No se restauran si se quitan los niveles.
- **Sin regeneración natural.** Igual: el hambre se mantiene llena, así que la vida volvería sola.
  Solo curan la comida y las camas.
- **Dificultad fijada en Normal.** En Pacífico vanilla borraría al Pale man en el mismo tick
  en que aparece, los golpes de mobs no harían daño y la vida se regeneraría.
- **El uniforme del vigilante.** La skin de cada jugador es el uniforme
  (`client.render.GuardUniform`, `AbstractClientPlayerEntityMixin`), con el modelo de brazos
  anchos y sin capa.

## Recursos

`systems.ResourceSystem` simula tres recursos por jugador. Es Kotlin puro: funciones puras
del estado, la entrada y la configuración (`step`, `toggleFlashlight`, `insertBattery`,
`switchOff`), con tests. `platform.ResourceHooks` lo ejecuta cada tick del servidor, guarda
los estados con el mundo (`every_tunnel_resources.dat`) y los sincroniza con el cliente
(`ResourceSyncPacket`, ver [Arquitectura](architecture.md#paquetes)).

### Niveles seguros

En un nivel marcado como `safe` (el nivel 4; `platform.SafeLevelHooks`) nada sale mal: solo hacen
daño `/kill` y el vacío, la sed y la batería se quedan como están, y correr y saltar no gastan
estamina, que sigue recuperándose. El HUD atenúa las filas de sed y batería. Una cama de las Suites
(`platform.SuiteBedHooks`) funde a negro en silencio y llena vida y estamina; no es un guardado y no
cambia nada más.

### Stamina

- Baja al esprintar (12/s) y con cada salto (8). Se regenera a 15/s tras 1,5 s sin usarla.
- A 0 el jugador está **agotado**: no puede esprintar hasta que la stamina vuelve al 30%. Lo
  impone el cliente (`ClientPlayerEntityMixin` hace que `canSprint` sea false), porque el
  sprint lo decide el cliente.
- Agotado: respiración fuerte en la cabeza del jugador (`client.effects.BreathingEffect`),
  los bordes de la pantalla se oscurecen y laten con cada respiración
  (`client.hud.ExhaustionVignette`), y el jadeo es un ruido para el Pale man una vez por
  segundo.

### Sed

- Sustituye al hambre: la comida de vanilla se mantiene llena (si no, un nivel de comida
  bajo impediría esprintar sin motivo visible).
- Baja 5 por minuto (llena dura 20 minutos), el doble de rápido al esprintar.
- Vacía: 1 de daño cada 10 s ("deshidratación", la fuente de daño `dryOut` de vanilla).
- Por debajo de un umbral, la viñeta se cierra a medida que se acaba la sed.

### Beber

`ModItems.WATER_BOTTLE`, `platform.DrinkHooks`: mantener usar 1,5 s para beber. +40 de sed,
un trago cada medio segundo más o menos (`player.drink`, de una grabación CC0;
`LivingEntityMixin` silencia el sorbo de vanilla) y un ruido para el Pale man. Un jugador
lleno no puede beber ("You are not thirsty"). Se gasta fuera de Creativo.

### Comer

`ModItems` (`food_plate`, `food_popcorn`, `food_snack`, con el mismo efecto los tres),
`platform.FoodHooks`: mantén pulsado usar 1,5 s para comer, +8 de vida (4 corazones). Hace un ruido
como beber; con la vida llena no se puede comer ("You are not hurt"). Se consume fuera de Creativo.

### La linterna

La linterna es **equipo integrado, no un objeto**: cada jugador tiene una y no se ve nada
en la mano.

- **Tecla F** (`client.FlashlightControls`, se cambia en Controles → Every Tunnel) envía
  `toggle_flashlight`; el servidor (`platform.FlashlightHooks`) comprueba la batería, la
  enciende o apaga y sincroniza el resultado. Los clics suenan `item.flashlight.on/off` y
  son un ruido pequeño.
- **Batería:** 180 s de luz. Baja un segundo por segundo mientras está encendida; a 0 la
  luz muere (`item.flashlight.dead`) y se apaga.
- **Batería baja** (por debajo del 20%): la fila de batería del HUD parpadea en rojo y el haz
  falla de vez en cuando, más a menudo según se agota, con un "bzzt" de contacto suelto
  (`item.flashlight.flicker`).
- **Cambiar la batería:** mantener usar con una batería en la mano 1,5 s
  (`item.battery.open/close`). Recarga el 100%. "The flashlight battery is already full" si
  ya está llena.
- La linterna funciona también en Creativo, pero solo gasta batería donde se aplican las
  reglas.

La luz en sí se dibuja en el cliente, ver [Gráficos](graphics.md#la-linterna).

## Botín

`platform.LootSpawner` y `platform.LootBlock`:

- La primera vez que un jugador entra en un nivel, se coloca un `LootBlock` en cada punto
  de aparición con la etiqueta `battery`, `water`, `keycard`, `food_plate`, `food_popcorn`, `food_snack`, `wrench`, `detector` o un fusible (`fuse_5a`...). El cliente lo dibuja como el
  propio objeto, tumbado (`client.render.LootRenderer`); no tiene colisión. El nivel 4, construido
  en plena partida, recibe su botín cuando está en pie.
- **Clic derecho** lo recoge; el sitio queda vacío.
- Cada sitio se llena una vez por mundo (`every_tunnel_loot.dat`): lo recogido sigue
  recogido tras morir o reabrir el mundo, hasta que una partida nueva lo repone todo.
- Recoger la tarjeta además dispara la ola de apagado (`KeycardHooks.pickedUp`).

## Herramientas del nivel 5

- **La llave inglesa** (`platform.WrenchItem`): un arma pesada que se lleva como una espada. Un golpe completo
  quita `wrench.damage` (7, tres corazones y medio), llega más o menos una vez por segundo
  (`wrench.attacks_per_second`), empuja un poco y suena a metal (`item.wrench.hit`). No se desgasta nunca.
- **El detector de radiación:** funciona desde cualquier sitio del inventario y no gasta nada. Nota la
  Distortion Cloud más cercana a menos de `detector.range` (16) bloques, nunca a través de una pared, y pita en
  la cabeza del jugador (`item.detector.beep`), de cada 1,2 s en el borde a cada 0,1 s pegado a ella
  (`client.effects.DetectorEffect`, números en `core.detector.Detector`). Lo que nota es cualquier entidad de la
  etiqueta de tipos `every_tunnel:radiation_sources` (la Cloud), hasta que empieza a irse.

## HUD

`client.hud.StatusHud` dibuja cuatro filas al estilo vanilla (10 iconos, llenos o medios)
donde estaban las barras de vanilla; `InGameHudMixin` oculta la vida, el hambre, la
armadura, el aire y la barra de experiencia de vanilla.

```
 stamina (pulmones)   arriba izquierda      batería          arriba derecha
 vida (corazones)     abajo izquierda       sed (gotas)      abajo derecha
 [                             barra rápida                             ]
```

- Los corazones tiemblan con poca vida.
- Los pulmones se vuelven grises agotado.
- La fila de batería parpadea en rojo por debajo del aviso de batería baja.

Iconos: `textures/gui/hud_icons.png`, pintados por `tools/gui/hud_icons.py`.

Mientras el jugador lleva el detector del nivel 5, `client.hud.DetectorHud` dibuja el símbolo de radiación a la
derecha de las filas de la derecha, entre ellas: gris sin nada al alcance, y se llena de amarillo y negro de abajo
arriba conforme se acerca una Cloud. A menos de `detector.danger_range` (8) parpadea rápido a su lado un triángulo
rojo de peligro (`textures/gui/detector_icons.png`, de la misma herramienta).

## Muerte

Ver [Flujo del juego](game-flow.md#muerte): sin pantalla de muerte, un fundido a negro y un
reinicio en el inicio del nivel (o en un punto de reinicio alcanzado en él, como el Lab hall del nivel 5) con los
recursos llenos, conservando el inventario.

## El archivo de configuración

`config/every_tunnel/gameplay.json` (en una ejecución de desarrollo: `run/config/...`). Lo
lee una vez al arrancar `config.ConfigManager`.

- Si falta: se crea con los valores por defecto.
- Un valor erróneo: un aviso en el log y el valor por defecto solo para ese valor.
- JSON no válido: un error, todos los valores por defecto, y el archivo no se toca.
- Claves que faltan (una versión más nueva del mod): valores por defecto, y las claves se
  escriben en el archivo, pero solo si no tenía otros problemas, para no sobrescribir nunca
  un archivo con erratas.

Cada clave y su valor por defecto:

| Clave | Por defecto | Significado |
|---|---|---|
| `config_version` | 1 | Versión del formato del archivo |
| **`stamina`** | | |
| `max` | 100 | Stamina máxima |
| `sprint_drain_per_second` | 12 | Se pierde por segundo al esprintar |
| `jump_cost` | 8 | Se pierde por salto |
| `regen_per_second` | 15 | Se recupera por segundo sin esprintar |
| `regen_delay_seconds` | 1.5 | Espera tras el último uso antes de regenerar |
| `exhausted_recover_percent` | 30 | Agotado hasta que la stamina vuelve a este % |
| **`thirst`** | | |
| `max` | 100 | Sed máxima (lleno = sin sed) |
| `drain_per_minute` | 5 | Se pierde por minuto |
| `sprint_drain_multiplier` | 2 | Multiplicador del gasto al esprintar |
| `damage_when_empty` | 1 | Daño cuando está vacía... |
| `damage_interval_seconds` | 10 | ...cada tantos segundos |
| `drink_restore` | 40 | Sed que devuelve una botella |
| `drink_seconds` | 1.5 | Tiempo que se tarda en beber |
| **`food`** | | |
| `heal` | 8 | Vida que devuelve una comida |
| `eat_seconds` | 1,5 | Tiempo que se tarda en comer |
| **`flashlight`** | | |
| `battery_max_seconds` | 180 | Segundos de luz de una batería llena |
| `low_battery_warning_percent` | 20 | Por debajo, el HUD parpadea y el haz falla |
| `battery_refill_percent` | 100 | Cuánto recarga una batería |
| `battery_change_seconds` | 1.5 | Tiempo que se tarda en cambiarla (0 = al instante) |
| `beam_range` | 20 | Alcance del haz en bloques (1..64) |
| `beam_width_degrees` | 40 | Anchura del cono, de borde a borde (1..120) |
| `beam_brightness` | 15 | Nivel de luz en el centro del haz (0..15) |
| `glow_brightness` | 5 | Brillo tenue alrededor del jugador (0..15) |
| **`wrench`** | | El arma del nivel 5 |
| `damage` | 7 | Vida que quita un golpe completo (un jugador tiene 20) |
| `attacks_per_second` | 1 | Golpes completos por segundo |
| **`detector`** | | El detector de radiación del nivel 5 |
| `range` | 16 | Bloques a los que nota la Cloud (nunca a través de una pared) |
| `danger_range` | 8 | Más cerca de esto parpadea el triángulo de peligro |
| `slowest_beep_seconds` | 1.2 | Entre pitidos en el borde de su alcance... |
| `fastest_beep_seconds` | 0.1 | ...y pegado a la Cloud |
| **`hunter`** | | Los Hunters del nivel 5 |
| `health` | 6 | Su vida contra todo menos la llave inglesa (cualquier golpe de llave mata a uno) |
| `damage` | 1 | Vida que quita un golpe (medio corazón)... |
| `attack_seconds` | 1 | ...como mucho cada tantos segundos cada uno... |
| `attack_range` | 1.4 | ...desde como mucho tantos bloques |
| `sight_range` | 16 | Bloques que ven, nunca a través de una pared... |
| `field_of_view` | 140 | ...en un cono de tantos grados... |
| `sense_range` | 3 | ...y más cerca de esto, miren adonde miren |
| `lost_seconds` | 4 | Segundos sin saber del que persiguen antes de buscar... |
| `search_seconds` | 3 | ...y cuánto buscan antes de volver andando |
| `wander_radius` | 5 | Bloques alrededor de su punto de aparición por los que merodean |
| `wander_pause_seconds` | 3 | Segundos que se quedan quietos entre paseo y paseo |
| `walk_speed`, `run_speed` | 0.7, 1.5 | Velocidades al moverse (veces su velocidad base) |
| **`cloud`** | | La Distortion Cloud del nivel 5 |
| `grace_seconds` | 1 | Segundos mirando hacia ella antes de que mate |
| `leave_seconds` | 6 | Segundos sin que nadie mire hacia ella antes de que se vaya |
| `min_seconds`, `max_seconds` | 120, 240 | Viene una tras un tiempo al azar entre estos, pasado en zonas con la etiqueta `cloud` |
| `often_min_seconds`, `often_max_seconds` | 60, 120 | Lo mismo en zonas que además tienen la etiqueta `cloud_often` (el drenaje del nivel 5), contado aparte |
| `nearest`, `farthest` | 8, 14 | Bloques por delante del jugador donde aparece |
| `touch_range` | 1.2 | Más cerca de esto mata mire uno adonde mire |
| **`junior`** | | El Distortion Junior del nivel 5 |
| `range` | 8 | Bloques desde su ojo a los que molesta al jugador (nunca a través de una pared) |
| `stare_seconds` | 4 | Segundos mirándole al ojo, sin apartar la vista, que acaban con él |
| `aim_degrees` | 5 | Grados que puede apartarse la mira de su ojo (más de muy cerca) |
| `tremor_degrees` | 0.6 | Cuánto tiembla la cámara |
| `drift_degrees_per_second` | 14 | Lo deprisa que se desvía |
| **`noise`** | | Radio en bloques; 0 lo hace silencioso |
| `sneak_radius` | 2 | Por paso, agachado |
| `walk_radius` | 6 | Por paso, andando |
| `sprint_radius` | 14 | Por paso, esprintando |
| `jump_radius` | 8 | Por salto |
| `land_radius` | 10 | Aterrizar tras una caída de 2 bloques (la mitad en caídas pequeñas, hasta el doble en caídas largas) |
| `interact_radius` | 8 | Puertas, trampillas, vallas, palancas, botones, cofres |
| `equipment_radius` | 4 | Clics de la linterna, cambios de batería |
| `exhausted_breathing_radius` | 5 | Jadeo, una vez por segundo |
| `drink_radius` | 4 | Cada vez que se bebe |
| **`pale_man`** | | Ver [El ruido y el Pale man](noise-and-pale-man.md) |
| `hearing` | 1 | Multiplica el radio de cada ruido que oye |
| `wall_muffle` | 0.6 | Cada bloque sólido en medio multiplica el radio por esto |
| `chase_loudness` | 0.5 | Volumen (1 en el origen, 0 en el borde) que le hace cargar |
| `listen_seconds` | 2 | Quieto escuchando tras un primer ruido |
| `search_seconds` | 4 | Alerta tras perderte |
| `investigate_seconds` | 20 | Andando hacia un ruido antes de rendirse |
| `roam_speed` | 0.6 | Multiplicadores de velocidad, 1 ≈ 3 bloques/s: vagando |
| `investigate_speed` | 0.8 | Andando hacia un ruido |
| `chase_speed` | 1.6 | Persiguiendo |
| `attack_damage` | 20 | Daño por golpe (un jugador tiene 20 de vida) |
| `attack_cooldown_seconds` | 1 | Entre golpes |
| `attack_range` | 2 | Alcance al perseguir o buscar |
| `touch_range` | 1 | Alcance en cualquier momento (chocarse con él) |

El parser (`config.GameplayConfigParser`) tiene el rango permitido de cada valor.
