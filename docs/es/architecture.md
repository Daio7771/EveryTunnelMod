# Arquitectura

*[English version](../en/architecture.md) · [Todas las páginas](README.md)*

## Visión general

Every Tunnel es un mod de Fabric con dos mitades:

- **Código común** (`src/main/`): se ejecuta en el servidor lógico. En un jugador es el
  servidor integrado dentro del juego. Es dueño de las reglas: niveles, recursos, ruido,
  el Pale man, el flujo de la partida.
- **Código de cliente** (`src/client/`): solo se ejecuta en el cliente del juego. Es dueño
  de lo que el jugador ve y oye: HUD, menús, pantalla de carga, música, ambiente, la luz de
  la linterna.

`splitEnvironmentSourceSets()` de Loom las mantiene separadas: el código común no puede
usar clases del cliente por accidente. Las dos se comunican con unos pocos paquetes
propios (ver abajo).

Nada ocurre fuera de los **mundos con niveles**. Un mundo tiene niveles cuando uno de sus
datapacks contiene `data/every_tunnel/levels/*.json`. Cualquier otro mundo sigue siendo
vanilla: sin reglas del mundo, sin HUD, sin linterna, sin pantalla de carga.

## Capas

El código común se divide según cuánto depende de Minecraft:

```
config/   core/   systems/   entities/   util/      Kotlin puro, sin tipos de Minecraft, con tests
world/                                              reglas finas sobre core (tampoco usa tipos de MC)
platform/                                           todo lo que toca Minecraft o Fabric
```

| Paquete | Qué contiene |
|---|---|
| `config` | `GameplayConfig` (cada número ajustable), su parser y el archivo en disco |
| `core` | `LevelManager` (niveles cargados), `PlayerLocationTracker` (quién está en qué zona) |
| `core.level` | `LevelDefinition` y el parser del JSON de niveles |
| `systems` | `ResourceSystem` (stamina, sed, batería), `NoiseSystem`, `FlashlightBeam` (matemática del haz) |
| `entities` | `PaleManBrain`, las decisiones del Pale man |
| `util` | `JsonFields`, un lector de JSON tolerante que usan ambos parsers |
| `world` | `WorldRules` (cuándo se aplican las reglas), `LevelTriggers` (oyentes de entrada y salida de zonas) |
| `platform` | Bloques, objetos, la entidad, sonidos, cada objeto `*Hooks`, paquetes, comandos |

La regla: **la lógica va en las capas de Kotlin puro, el pegamento va en `platform`.** Las
capas puras reciben números, posiciones y UUIDs y devuelven decisiones; `platform` las
alimenta desde Minecraft y aplica el resultado. Por eso el cerebro, los recursos, las
reglas del ruido y los parsers tienen tests unitarios, y por eso portar el mod a otra
versión de Minecraft debería consistir sobre todo en reescribir `platform` y el cliente.

El cliente (`src/client/kotlin/daio7771/everytunnel/client/`) se organiza por lo que dibuja
o reproduce:

| Paquete | Qué contiene |
|---|---|
| `client` | El entrypoint del cliente, la tecla de la linterna, los recursos sincronizados, la detección y ajustes de shaders, el instalador del mundo del mapa |
| `client.compat` | Integración con LambDynamicLights (la luz de la linterna) |
| `client.effects` | Todo lo que se oye (ambiente, zumbido, latido, respiración, música) y el fundido de pantalla |
| `client.hud` | Barras de estado, viñeta de agotamiento, el foco de la linterna sin shaders |
| `client.render` | El renderizador del Pale man, el botín en el suelo, el uniforme del vigilante |
| `client.screen` | Menú principal, pantalla de carga y consejos, fin de la demo y créditos, la figura del menú |

Las mixins están en Java: `src/main/java/.../mixin/` (comunes) y
`src/client/java/.../mixin/client/` (cliente). Ver [Mixins](#mixins).

## Entrypoints

Declarados en `src/main/resources/fabric.mod.json`:

| Entrypoint | Clase | Función |
|---|---|---|
| `main` | `EveryTunnel` | Carga la configuración y llama a `register()` de cada registro y hook |
| `client` | `client.EveryTunnelClient` | Registra los efectos, HUD, pantallas, tecla y paquetes del cliente |
| `fabric-datagen` | `client.EveryTunnelDataGenerator` | Vacío: los recursos los hacen las herramientas Python |
| `lambdynlights:initializer` | `client.compat.LambDynamicLightsCompat` | Añade el haz de la linterna como luz dinámica |

`EveryTunnel.onInitialize()` es el índice del lado del servidor. El orden solo importa en
que los registros (objetos, bloques, entidades, sonidos) van primero.

## Cómo fluye un tick del servidor

Casi todo el juego lo mueven los `ServerTickEvents` de Fabric, un objeto hook por tema:

```
LevelTrackingHooks  posición -> PlayerLocationTracker -> LevelTriggers (cambios de zona)
                                   |-> AmbienceHooks, RespawnHooks, LevelEntryHooks,
                                   |   PaleManSpawner, LootSpawner, EndingHooks, IntroHooks
ResourceHooks       sprint/saltos -> ResourceSystem.step -> daño, paquete de sincronización
NoiseHooks          pasos, saltos, aterrizajes, jadeo, game events -> NoiseSystem.emit
PaleManEntity.tick  NoiseSystem.audibleAt -> volumen (muros) -> PaleManBrain.tick -> moverse/golpear
LoadingHooks, IntroHooks, DeathHooks, KeycardHooks, LevelEntryHooks   escenas con tiempos
PlaytestLog         escucha todo y escribe líneas [playtest]
```

`LevelTriggers` es el principal punto de extensión: un sistema al que le importa que los
jugadores entren o salgan de zonas registra un `LevelTriggers.Listener` una vez, y se le
llama en el hilo del servidor con un `LocationChange` (cambio de nivel, zonas en las que
entró, zonas de las que salió). Un oyente que lanza una excepción queda en el log y no
detiene a los demás.

## Hilos

- Todo lo de `platform` y las capas puras corre en el **hilo del servidor**.
  `NoiseSystem` y `ResourceSystem` lo dan por hecho y no están sincronizados.
- `LevelManager` y `PlayerLocationTracker` son seguros desde cualquier hilo: el conjunto de
  niveles se cambia como una sola instantánea inmutable, y las posiciones viven en un
  `ConcurrentHashMap`.
- `ConfigManager.gameplay` es `@Volatile` y se reemplaza entero.
- El código del cliente corre en el **hilo de render**. Los paquetes del servidor se le
  pasan con `client.execute { ... }`.

## Paquetes

Todos los ids son `every_tunnel:<nombre>`. API de red de Fabric (estilo 1.20.1,
`ServerPlayNetworking` / `ClientPlayNetworking` con `PacketByteBuf`).

| Id | Dirección | Lo envía | Qué lleva |
|---|---|---|---|
| `resources` | servidor → cliente | `ResourceSyncPacket` | Stamina, sed, batería, linterna encendida, agotado, activo; redondeado a 0,1 y enviado solo si cambia |
| `toggle_flashlight` | cliente → servidor | `FlashlightControls` | Se pulsó la tecla de la linterna |
| `ambience` | servidor → cliente | `AmbienceHooks` | El ambiente del nivel actual del jugador |
| `intro_music` | servidor → cliente | `IntroHooks` | Empezar o terminar (distorsionar y apagar) la canción de la intro |
| `screen_fade` | servidor → cliente | `DeathHooks` | Oscuridad objetivo (0 a 1) y segundos para llegar |
| `loading` | servidor → cliente | `LoadingHooks` | Estado de carga: `0` cargando, `1` listo |
| `loading_done` | cliente → servidor | `LoadingCover` | El cliente ha dibujado el mundo y ha hecho el fundido de entrada |
| `ending` | servidor → cliente | `EndingHooks` | Abrir la pantalla de fin de la demo |

El estado del Pale man llega a los clientes como dato sincronizado de la entidad
(`PaleManEntity.brainState`), no como paquete.

## Datos guardados

Lo que el mod recuerda se guarda con el mundo, en `<mundo>/data/`, como archivos
`PersistentState` de vanilla:

| Archivo | Dueño | Qué recuerda |
|---|---|---|
| `every_tunnel_resources.dat` | `ResourceHooks` | La stamina, sed, batería y linterna de cada jugador |
| `every_tunnel_intro.dat` | `IntroHooks` | Quién ha tenido ya la intro del ascensor |
| `every_tunnel_loot.dat` | `LootSpawner` | Qué puntos de botín se llenaron |
| `every_tunnel_pale_men.dat` | `PaleManSpawner` | Qué puntos de aparición del Pale man ya lo generaron |
| `every_tunnel_lights.dat` | `KeycardHooks` | Las luces que apagó la tarjeta, para restaurarlas |

`packMapWorld` deja todos estos fuera del jar de release, así que una instalación nueva
empieza limpia.

Fuera del mundo, en la carpeta `config/` del juego: `every_tunnel/gameplay.json` (la
configuración de juego) y `every_tunnel/shader_settings_applied.txt` (qué packs de shaders
ya ha configurado `ShaderSettings`).

## Mixins

Las mínimas: una mixin solo donde Fabric API no tiene evento.

| Mixin | Objetivo | Por qué |
|---|---|---|
| `ServerWorldMixin` | `ServerWorld.emitGameEvent` | Permite al sistema de ruido oír puertas, palancas, cofres (las "vibraciones" de vanilla); no cambia nada |
| `LivingEntityMixin` | `LivingEntity.shouldSpawnConsumptionEffects` | Sin el sorbo de vanilla para la botella de agua; tiene sus propios tragos |
| `ClientPlayerEntityMixin` | `canSprint` | No se puede esprintar agotado (el sprint lo decide el cliente) |
| `InGameHudMixin` | `renderStatusBars`, `renderExperienceBar` | Oculta las barras de vanilla mientras el HUD del mod las sustituye |
| `AbstractClientPlayerEntityMixin` | `getSkinTexture`, `getModel`, `getCapeTexture` | Todos los jugadores llevan el uniforme del vigilante en los mundos con niveles |
| `LightmapTextureManagerMixin` | `update` (gamma) | El brillo cuenta como Moody en los niveles |
| `MinecraftClientMixin` | `setScreen` | Sustituye la pantalla de título de vanilla por el menú principal |
| `MusicTrackerMixin` | `tick` | Sin música de vanilla en ningún sitio |
| `ScreenMixin` | `renderWithTooltip` | Dibuja la pantalla de carga encima de las de vanilla |
| `SplashOverlayMixin` | `render` | Dibuja la pantalla de carga encima del splash de recarga de recursos |
| `WorldListEntryMixin` | `openReadingWorldScreen` | Detecta que se abre un mundo con niveles desde la lista de mundos |
| `QuickPlayMixin` | `startSingleplayer` | Lo mismo para quick play (ejecuciones de desarrollo, launchers) |
| `SoundSystemMixin` | `getAdjustedVolume` | Silencio tras la pantalla de carga, con fundido junto a la imagen |

## Principios de diseño

- **Los niveles son datos, no código.** Un nivel es un archivo JSON de áreas y puntos en el
  datapack del mundo; los bloques los construye una función. Ver [Niveles](levels.md).
- **Entrada tolerante.** Los archivos de configuración y de niveles nunca cuelgan el juego:
  cada valor erróneo se informa con su ruta JSON y se sustituye por su valor por defecto, o
  la entrada rota se salta.
- **Vanilla sigue siendo vanilla.** Cada función comprueba `WorldRules.active` o
  `WorldRules.appliesTo(...)`. Creativo y Espectador están exentos de las reglas, así que el
  mapa se puede construir a mano.
- **Contenido generado.** Sonidos, texturas, modelos y el mapa salen de scripts Python con
  semilla, así que se pueden rehacer desde parámetros. Ver
  [Herramientas Python](tools.md).
- **Los comentarios dicen el porqué.** Cada archivo empieza con un KDoc que explica qué
  hace y cómo encaja. Esta documentación resume esas cabeceras; en caso de duda, manda el
  código.
