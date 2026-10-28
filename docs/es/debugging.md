# Depuración

*[English version](../en/debugging.md) · [Todas las páginas](README.md)*

## Comandos de depuración

`platform.DebugCommands`, nivel de operador 2 (en un jugador: "Permitir trucos").

| Comando | Qué hace |
|---|---|
| `/everytunnel levels` | Niveles cargados con sus zonas y puntos de aparición |
| `/everytunnel where` | El nivel y las zonas en las que estás, y si se aplican las reglas del mundo |
| `/everytunnel resources` | Tu stamina, sed y batería |
| `/everytunnel resources set stamina\|thirst\|battery <valor>` | Cambia uno (la batería en segundos de luz) |
| `/everytunnel noise` | Los ruidos de los últimos 5 segundos cerca de ti |
| `/everytunnel noise watch` | Activa o desactiva mostrar cada ruido tuyo en la barra de acción |
| `/everytunnel paleman` | Los Pale men cercanos: estado y objetivo |
| `/everytunnel intro` | Repite la intro del ascensor (Supervivencia o Aventura) |
| `/everytunnel newgame` | Empieza la partida de nuevo en este mundo |
| `/everytunnel valves` | Las cifras de las válvulas del nivel 3 en esta partida (A a E) |
| `/everytunnel rebuilt` | Lo ejecuta `garage:build` al terminar: vuelve a poner el botín. No es para usarlo a mano |
| `/everytunnel devworld save\|forget` | Solo en desarrollo, ver [Compilación](building-and-release.md#mundos-de-desarrollo) |

Comandos de vanilla útiles:

- `/function garage:build`: reconstruir el mapa a mano.
- `/playsound every_tunnel:<id> master @s`: probar cualquier sonido.
- `/summon every_tunnel:pale_man`, o el huevo de aparición de la pestaña Every Tunnel.
- `/give @s every_tunnel:<bloque u objeto>`: cualquier bloque u objeto del mod.
- `/gamemode creative`: libre de las reglas del mundo, para construir o mirar.

## El registro de playtest

`platform.PlaytestLog` escribe una línea que empieza por `[playtest]` para todo lo que
importa al ajustar, así que se puede releer una partida entera:

```bash
grep '\[playtest\]' run/logs/latest.log
```

- **Al empezar una partida:** la configuración de juego en uso y lo que tiene cada nivel
  (puntos de botín, Pale men).
- **Cada 30 s de juego:** sed, stamina, batería, vida, inventario, posición.
- **Cuando ocurre:** niveles en los que se entra, botín recogido, tragos, cambios de batería
  y batería agotada, agotamiento y recuperación, sed baja y vacía, Pale men que oyen al
  jugador y le golpean, muertes y su causa.
- **Las rutas de los Pale men:** una ruta que no llega a su objetivo (una vez por estado),
  con dónde termina y qué bloques estorban del suelo a la cabeza; por qué termina cada paseo
  o persecución hacia un ruido.
- **Un resumen por nivel** al final de la demo, o cuando el jugador sale o empieza una
  partida nueva a medias: tiempo, muertes por causa, golpes, botín, tragos, baterías, tiempo
  de linterna, agotamiento, lo que quedaba.

Los tiempos son tiempo de juego en ticks del servidor, así que una partida en pausa no
cuenta. Las partidas viven solo en memoria: reabrir el mundo empieza una nueva.

## Otras líneas del log

- Cada cambio de zona de cada jugador (`LevelTrackingHooks`), con los ids de zona.
- Cada cambio de estado de un Pale man, con su posición, objetivo y el ruido más fuerte.
- Problemas de los archivos de configuración y de nivel, con la ruta JSON.

## Tests unitarios

`./gradlew test` (también forma parte de `build`). 55 tests en `src/test/kotlin/`, que cubren
las capas de Kotlin puro:

| Test | Cubre |
|---|---|
| `GameplayConfigParserTest` | Valores por defecto, valores erróneos, claves desconocidas y ausentes |
| `LevelParserTest` | Cada clave, entradas rotas saltadas, errores con rutas |
| `PlayerLocationTrackerTest` | Niveles y zonas en los que se entra y de los que se sale |
| `PaleManBrainTest` | Cada cambio de estado, volumen, muros, ataques |
| `NoiseSystemTest` | Radios, memoria, audibilidad, pasos, aterrizajes |
| `ResourceSystemTest` | Gasto, regeneración, agotamiento, daño por sed, batería |
| `FlashlightBeamTest` | La luz del haz en puntos dentro y fuera del cono |
| `GuideMarkdownTest` | El subconjunto de Markdown de la guía, y que cada página de `docs/*/guide/` lo cumple en los dos idiomas |

Lo que toca Minecraft se prueba en el juego.

## Problemas frecuentes

| Síntoma | Causa probable |
|---|---|
| `runClientMapWorld` falla al momento | Falta `run/saves/noname_complex`, o no tiene datapack de niveles: ejecuta el generador de mapas |
| Quick play se queda en el menú | No existe un mundo con ese nombre |
| Sin HUD, sin linterna | El mundo no tiene archivos de nivel (mira `/everytunnel levels`), o estás en Creativo/Espectador |
| La linterna no ilumina nada | Falta LambDynamicLights, o se volvió a activar colored lighting en Complementary |
| No hay Pale man en el Nivel 2 | Aparece la primera vez que se entra en el nivel; si se le hizo `/kill`, vuelve en la siguiente entrada |
| El Pale man se queda quieto en una persecución | Su ruta se queda corta (necesita 3 bloques libres de altura): busca las líneas de rutas en el registro `[playtest]` |
