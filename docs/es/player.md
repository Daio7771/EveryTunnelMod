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
  de aparición con la etiqueta `battery`, `water` o `keycard`. El cliente lo dibuja como el
  propio objeto, tumbado (`client.render.LootRenderer`); no tiene colisión.
- **Clic derecho** lo recoge; el sitio queda vacío.
- Cada sitio se llena una vez por mundo (`every_tunnel_loot.dat`): lo recogido sigue
  recogido tras morir o reabrir el mundo, hasta que una partida nueva lo repone todo.
- Recoger la tarjeta además dispara la ola de apagado (`KeycardHooks.pickedUp`).

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

## Muerte

Ver [Flujo del juego](game-flow.md#muerte): sin pantalla de muerte, un fundido a negro y un
reinicio en el inicio del nivel con los recursos llenos, conservando el inventario.

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
| **`flashlight`** | | |
| `battery_max_seconds` | 180 | Segundos de luz de una batería llena |
| `low_battery_warning_percent` | 20 | Por debajo, el HUD parpadea y el haz falla |
| `battery_refill_percent` | 100 | Cuánto recarga una batería |
| `battery_change_seconds` | 1.5 | Tiempo que se tarda en cambiarla (0 = al instante) |
| `beam_range` | 20 | Alcance del haz en bloques (1..64) |
| `beam_width_degrees` | 40 | Anchura del cono, de borde a borde (1..120) |
| `beam_brightness` | 15 | Nivel de luz en el centro del haz (0..15) |
| `glow_brightness` | 5 | Brillo tenue alrededor del jugador (0..15) |
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
