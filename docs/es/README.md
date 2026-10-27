# Every Tunnel: documentación técnica

Every Tunnel es un mod de terror psicológico para un jugador en Minecraft 1.20.1 (Fabric,
Kotlin). El jugador es el nuevo vigilante del turno de noche del Noname Complex, unas
instalaciones subterráneas abandonadas. La primera versión es una demo de dos niveles en su
aparcamiento: el Nivel 1 (B1) no tiene enemigos y enseña al jugador a escuchar; el Nivel 2
(B2) añade al Pale man, que es ciego y caza por el sonido. El diseño de sonido es el
núcleo del mod.

*[English version](../en/README.md)*

## Para quién son estas páginas

- **Modders** que quieren entender, cambiar o ampliar el mod.
- **Asistentes de IA** que trabajan en el código: empezad por
  [Cómo contribuir](contributing.md), que recoge las reglas del proyecto, y luego
  [Arquitectura](architecture.md).
- **Jugadores** que quieren saber más de lo que cuenta la
  [guía del jugador](guide/01-how-to-play.md).

## Páginas

La primera vez, léelas en este orden:

| Página | Qué cubre |
|---|---|
| [Arquitectura](architecture.md) | Capas, source sets, entrypoints, hilos, paquetes, datos guardados, mixins |
| [Flujo del juego](game-flow.md) | Del menú principal a los créditos finales, paso a paso |
| [Niveles](levels.md) | El formato JSON de los niveles, las etiquetas de zonas y puntos, cómo se construyen |
| [Sistemas del jugador](player.md) | Stamina, sed, linterna, HUD, botín, reglas del mundo, el archivo de configuración |
| [El ruido y el Pale man](noise-and-pale-man.md) | El sistema de ruido, su IA de oído, su entidad y su música |
| [Audio](audio.md) | Cada evento de sonido, los reproductores del cliente, cómo se hacen los sonidos |
| [Gráficos](graphics.md) | Con y sin shaders, luces, brillo, texturas, dirección de arte |
| [Herramientas Python](tools.md) | Los generadores del mapa, sonidos, bloques, arte de entidades y GUI |
| [Compilación y release](building-and-release.md) | Tareas de Gradle, mundos de desarrollo, el jar de release |
| [Depuración](debugging.md) | Comandos de depuración y el registro `[playtest]` |
| [Recetas](how-to.md) | Añadir un nivel, un sonido, un bloque, un consejo, un valor de configuración... |
| [Mapa del código](code-map.md) | Cada archivo fuente y lo que hace |
| [Cómo contribuir](contributing.md) | Convenciones, tests, commits, reglas para asistentes de IA |

## Guía del jugador

Estas páginas también se ven dentro del juego (menú principal, GUIDE):

1. [Cómo jugar](guide/01-how-to-play.md)
2. [El ruido](guide/02-noise.md)
3. [Gráficos y mods](guide/03-graphics-and-mods.md)
4. [Datos técnicos](guide/04-technical-data.md)

## Datos rápidos

| | |
|---|---|
| Minecraft | 1.20.1, Fabric Loader 0.19.5 |
| Lenguaje | Kotlin 2.4 (y unas pocas mixins en Java), destino Java 17 |
| Id del mod | `every_tunnel` |
| Paquete | `daio7771.everytunnel` |
| Requeridos (dentro del jar) | Fabric API, Fabric Language Kotlin, GeckoLib |
| Requeridos (los instala el jugador) | LambDynamicLights |
| Recomendados | Sound Physics Remastered, Sodium + Iris + Indium |
| Licencia | Todos los derechos reservados (todo lo propio; MIT hasta la 0.2.0), grabaciones CC0, una canción CC BY |
