# Compilación y release

*[English version](../en/building-and-release.md) · [Todas las páginas](README.md)*

## Requisitos

- **JDK 17** para el juego y **JDK 21** para Gradle. `./gradlew` los encuentra solo.
- Todo lo demás (Minecraft, Fabric, librerías, los mods solo de desarrollo) lo descarga la
  compilación.
- Python 3 para las [herramientas](tools.md), solo al cambiar contenido generado.

## Tareas de Gradle

| Tarea | Qué hace |
|---|---|
| `./gradlew build` | Compila, ejecuta los tests unitarios, crea el jar en `build/libs/` |
| `./gradlew test` | Solo los tests unitarios |
| `./gradlew runClient` | El juego, en el menú principal |
| `./gradlew runClientMapWorld` | Directo a `noname_complex` (quick play). Falla enseguida si falta el mundo o su datapack |
| `./gradlew runClientDevWorld` | Directo a `dev_flat`, el mundo plano de pruebas |
| `./gradlew everytunnerBuild` | El jar de release: `build/libs/everytunner_build.jar` |
| `./gradlew runDatagen` | Generación de datos de Fabric (sin proveedores; los recursos salen de las herramientas Python) |

Las ejecuciones de desarrollo viven en `run/` (no está en git) y siempre entran como `Dev`,
así que los mundos conservan al jugador. También cargan los mods recomendados (Sound Physics
Remastered, Sodium, Iris, Indium) y LambDynamicLights. El classpath de ejecución fija LWJGL
en 3.3.1, porque Sodium 0.5 rechaza la 3.3.2 que trae Loom.

## Mundos de desarrollo

Los dos hay que crearlos una vez, a mano: `./gradlew runClient` y luego un mundo
**Superplano** con **comandos permitidos**, llamado exactamente:

- `noname_complex`, el mapa. Después ejecuta `python3 tools/map/map_generator.py` para
  instalar los niveles. El mod los reconstruye cada vez que se abre el mundo.
- `dev_flat`, para pruebas. Copia `examples/dev_flat_datapack/every_tunnel_levels` en
  `run/saves/dev_flat/datapacks/`. Su `level_2` tiene un punto de aparición del Pale man.

**Empezar siempre desde el mismo punto** (`platform.DevWorlds`, solo en desarrollo): abre el
mundo, ponte donde quieras empezar, ejecuta `/everytunnel devworld save` y sal. El mundo se
copia en `run/dev_templates/`, y cada arranque del cliente de desarrollo lo restaura antes
de abrirlo. `/everytunnel devworld forget` lo desactiva.

**Un PC nuevo:** instala JDK 17 y 21; `./gradlew runClient` y crea `noname_complex` como
arriba; `python3 tools/map/map_generator.py`; `./gradlew runClientMapWorld`.

## El jar de release

`./gradlew everytunnerBuild` → `build/libs/everytunner_build.jar` (unos 21 MB). Un solo jar
con todo menos Fabric Loader y LambDynamicLights:

| Dentro | Cómo |
|---|---|
| El mod | `remapJar` |
| Fabric API, Fabric Language Kotlin, GeckoLib | jar-in-jar (`include`); Fabric Loader usa una copia más nueva si el jugador tiene una |
| El mundo del mapa | `packMapWorld` comprime `run/saves/noname_complex` en `every_tunnel/world/noname_complex.zip` |
| La guía del jugador | `copyGuide` copia `docs/<idioma>/guide/*.md` en `assets/every_tunnel/guide/<idioma>/` |
| `LICENSE.txt`, `CREDITS.md` | Renombrados con el nombre del mod; el crédito CC BY tiene que viajar con el mod, y los créditos listan las librerías incluidas |

`packMapWorld` deja fuera lo que pertenece a quien jugó el mundo: `playerdata/`, `stats/`,
`advancements/`, `session.lock`, `level.dat_old`, el estado guardado del mod
(`data/every_tunnel_*`) y `data/chunks.dat` (chunks forzados). Empaqueta el mundo tal como
está **en ese momento**, así que construye el mapa antes. Sin ese mundo (un clon nuevo del
repositorio), el jar simplemente no lleva mapa.

No van dentro, por licencias:

- **LambDynamicLights**, obligatorio (`depends`): desde la 3.0 usa la Lambda License, que no
  permite compartir su jar sin permiso por escrito del autor. Lo instala el jugador; sin él,
  Fabric Loader se para al arrancar y lo nombra. (La 0.1.0 y la 0.2.0 llevaban la 4.4.0
  dentro; la 2.3.2, MIT, tiene otra API.)
- Sodium, Iris, Indium, Sound Physics Remastered, en `recommends` en `fabric.mod.json`.

### Lo que hace un jugador

1. Instalar Fabric Loader 0.19.5 o más nuevo para Minecraft 1.20.1.
2. Poner `everytunner_build.jar` y LambDynamicLights (4.4.0 o más nuevo) en `mods/`.
3. Opcional: Sodium + Iris + Indium y un shader pack Complementary, y Sound Physics
   Remastered.
4. Arrancar el juego y pulsar BEGIN.

En el primer arranque `client.MapWorldInstaller` desempaqueta el mundo en
`saves/noname_complex`, quita el jugador guardado y lo pone en Supervivencia. Una versión
posterior del mod solo sustituye los datapacks y el `resources.zip` del mundo.

## Publicar en CurseForge

[`CURSEFORGE.md`](../../CURSEFORGE.md), en la raíz del repositorio, es la descripción del
proyecto tal como la ven los jugadores. Para actualizarla en CurseForge, abre el archivo en
GitHub, usa **Copy raw file** y pégalo en el editor de la descripción en modo **Markdown**.
Enlaza a GitHub con URLs completas, porque allí no funcionan los enlaces relativos.

Al subir:

| Campo | Valor |
|---|---|
| Summary (la línea bajo el nombre) | `Sound-driven horror demo. Don't make a noise.` |
| Icono del proyecto | `docs/media/logo.png` (512×512) |
| Versión del juego | 1.20.1 |
| Mod loader | Fabric |
| Java | 17 |
| Dependencias obligatorias | LambDynamicLights (Fabric API, Fabric Language Kotlin y GeckoLib van dentro del jar) |
| Dependencias opcionales | Sound Physics Remastered, Sodium, Iris, Indium |
| Licencia | All Rights Reserved. El crédito CC BY 4.0 de la música debe seguir en la descripción |
| Source | https://github.com/Daio7771/EveryTunnelMod |
| Issues | https://github.com/Daio7771/EveryTunnelMod/issues |
| Archivo | El jar de release (`everytunnerBuild`), tipo **Beta** mientras sea una demo |
| Changelog del archivo | Qué cambió en esta versión, para jugadores |

El mismo jar va a las [Releases](https://github.com/Daio7771/EveryTunnelMod/releases) del
repositorio, a las que enlazan `README.md` e `INSTALL.md`.

## Versiones

Todas en `gradle.properties`:

| Componente | Versión |
|---|---|
| Minecraft / Yarn | 1.20.1 / 1.20.1+build.10 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.92.12+1.20.1 |
| Fabric Language Kotlin | 1.14.2+kotlin.2.4.21 |
| GeckoLib | 4.8.4 |
| LambDynamicLights | 4.4.0+1.20.1 |
| Sound Physics Remastered (desarrollo) | fabric-1.20.1-1.5.1 |
| Sodium / Iris / Indium (desarrollo) | 0.5.13 / 1.7.6 / 1.0.36 |
| Versión del mod | `mod_version` (0.2.1) |

## Preguntas abiertas del release

Por decidir antes de la primera versión pública: el aviso del resource pack del mundo, los
trucos en el mundo que se distribuye, el canal de distribución y el nombre de la versión,
las notas de instalación, qué shader pack recomendar, si el registro `[playtest]` sigue
activo, y si el jar aún necesita las regiones preconstruidas (el mapa se reconstruye al
abrir de todos modos).
