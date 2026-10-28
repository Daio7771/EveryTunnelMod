# Mapa del código

*[English version](../en/code-map.md) · [Todas las páginas](README.md)*

Cada archivo fuente y lo que hace. Las rutas son relativas a `src/`. Cada archivo empieza
con un comentario KDoc que cuenta más.

## Común: `main/kotlin/daio7771/everytunnel/`

| Archivo | Qué hace |
|---|---|
| `EveryTunnel.kt` | Entrypoint común: carga la configuración y registra todo |

### `config/`

| Archivo | Qué hace |
|---|---|
| `GameplayConfig.kt` | Cada valor ajustable con su valor por defecto, por secciones (stamina, sed, linterna, ruido, Pale man) |
| `GameplayConfigParser.kt` | JSON ↔ configuración, rangos de valores, problemas y claves ausentes. Sin E/S de archivos |
| `ConfigManager.kt` | Carga y escribe `config/every_tunnel/gameplay.json` de forma segura |

### `core/`, `core/level/`, `core/guide/`, `core/menu/` y `core/puzzle/`

| Archivo | Qué hace |
|---|---|
| `LevelManager.kt` | Los niveles cargados, como una instantánea inmutable; búsqueda de zonas por posición |
| `PlayerLocationTracker.kt` | El nivel y las zonas de cada jugador; informa de los `LocationChange` |
| `level/LevelDefinition.kt` | El modelo de nivel: zonas, puntos de aparición, ambiente, entrada, intro, build, `BlockBox` |
| `level/LevelParser.kt` | Lee un archivo de nivel; documenta cada clave |
| `guide/GuideMarkdown.kt` | Lee una página de la guía del jugador (el subconjunto de Markdown) en bloques |
| `menu/LampFlicker.kt` | Cuándo parpadean las lámparas de la escena del menú |
| `menu/BlockLight.kt` | Luz de bloque para una caja de bloques sin mundo (la escena del menú) |
| `puzzle/ValveCode.kt` | El puzle de las válvulas del nivel 3: cifras al azar, un giro, resuelto |
| `puzzle/NoteText.kt` | El texto de una nota: título, párrafos, huecos `{A}` / `{A:word}` |

### `systems/`

| Archivo | Qué hace |
|---|---|
| `ResourceSystem.kt` | Simulación de stamina, sed y batería, como funciones puras |
| `NoiseSystem.kt` | Eventos de ruido, su memoria, audibilidad y las reglas que eligen los radios |
| `FlashlightBeam.kt` | La luz del haz en cualquier punto (cono, caída, punto caliente, brillo) |

### `entities/`

| Archivo | Qué hace |
|---|---|
| `PaleManBrain.kt` | La máquina de estados del Pale man y la fórmula del volumen |
| `Package.kt` | Documentación del paquete |

### `util/` y `world/`

| Archivo | Qué hace |
|---|---|
| `util/JsonFields.kt` | Lector de JSON tipado y tolerante que recoge los problemas con su ruta |
| `world/WorldRules.kt` | Si se aplican las reglas (un mundo con niveles, Supervivencia/Aventura) |
| `world/LevelTriggers.kt` | Oyentes de jugadores que entran y salen de zonas |

### `audio/`

| Archivo | Qué hace |
|---|---|
| `Package.kt` | Documentación provisional del paquete (los sonidos viven en `platform.ModSounds` y el cliente) |

### `platform/`: registros

| Archivo | Qué hace |
|---|---|
| `ModBlocks.kt` | Cada bloque: la luz, props, puertas, carteles, piezas del ascensor, botín, lector de tarjetas |
| `ModItems.kt` | Batería, botella de agua, tarjeta; la pestaña creativa |
| `ModEntities.kt` | El tipo de entidad del Pale man y su huevo |
| `ModSounds.kt` | Eventos de sonido que reproduce el código |
| `ModSoundGroups.kt` | Grupos de sonido de bloques por material de prop |
| `Package.kt` | Documentación del paquete: el pegamento que depende de la versión de Minecraft |

### `platform/`: bloques

| Archivo | Qué hace |
|---|---|
| `BlockShapes.kt` | Cajas de contorno y colisión en píxeles, giradas como los blockstates |
| `PropBlock.kt` | Un prop de suelo orientado a uno de cuatro lados |
| `ConnectingPropBlock.kt` | Tuberías, conductos y bandejas de cables que se conectan con sus vecinos |
| `FluorescentLightBlock.kt` | La luz: encendida, parpadeante, apagada; nivel de luz 13, dibujada a brillo máximo |
| `SurfaceMarkBlocks.kt` | Manchas y charcos en cualquier cara |
| `SignTileBlock.kt` | Textos pintados por ranura numerada (el pack del mundo los asigna) |
| `ElevatorDoorBlock.kt` | Etapas de la puerta corredera del ascensor |
| `FloorIndicatorBlock.kt` | El indicador LED de planta del ascensor |
| `ShutterBlock.kt` | Una pieza de una persiana metálica |
| `CardReaderBlock.kt` | El lector de tarjetas que abre puertas de hierro |
| `NoteBoardBlock.kt` | Un tablón de corcho con una nota: clic derecho para leerla |
| `FurnitureBlocks.kt` | Los muebles del nivel 3 que se unen a los de su clase: piezas en fila (mesas, barras, sofás) a izquierda y derecha, tableros de mesa por todos los lados, piezas apiladas (estanterías, racks, taquillas) arriba y abajo |
| `ValveBlock.kt` | Una válvula de compuerta en línea en una tubería de pared: orientación, abierta, letra A a E (nivel 3) |
| `LootBlock.kt` | Botín tirado en el suelo, se recoge con clic derecho |

### `platform/`: el Pale man

| Archivo | Qué hace |
|---|---|
| `PaleManEntity.kt` | La entidad: oído, muros, territorio, rutas, golpes, sonidos, animaciones |
| `PaleManSpawner.kt` | Genera uno por cada punto `pale_man`, una vez por mundo; contador de partidas nuevas |

### `platform/`: hooks (lógica de juego movida por eventos)

| Archivo | Qué hace |
|---|---|
| `LevelReloadListener.kt` | Carga los archivos de nivel de los datapacks al cargar el mundo y con `/reload` |
| `LevelTrackingHooks.kt` | Pasa las posiciones al tracker cada tick y dispara `LevelTriggers` |
| `WorldRulesHooks.kt` | Sin romper ni colocar, sin aparición de mobs, dificultad Normal |
| `ResourceHooks.kt` | Ejecuta `ResourceSystem` cada tick, guarda y sincroniza los recursos |
| `ResourceSyncPacket.kt` | El paquete de recursos al cliente |
| `FlashlightHooks.kt` | La petición de encender la linterna, las baterías, los avisos |
| `DrinkHooks.kt` | Beber botellas de agua |
| `JumpCounter.kt` | Saltos por jugador a partir de la estadística de vanilla |
| `NoiseHooks.kt` | Convierte pasos, saltos, aterrizajes, jadeos y game events en ruidos |
| `AmbienceHooks.kt` | Envía a cada jugador el ambiente de su nivel |
| `RespawnHooks.kt` | Pone el punto de reaparición en el inicio del nivel actual |
| `DeathHooks.kt` | La muerte como fundido a negro y reinicio del nivel |
| `LoadingHooks.kt` | Reconstruir al abrir, estado de carga, partida nueva, tiempos límite de seguridad |
| `NewGame.kt` | Reinicia el mundo y el jugador para una partida nueva |
| `IntroHooks.kt` | La intro del ascensor: puertas, canción, indicadores de planta, título |
| `LevelEntryHooks.kt` | Títulos de nivel y persianas que se cierran tras el jugador |
| `LootSpawner.kt` | Coloca y repone el botín en los puntos con etiqueta |
| `KeycardHooks.kt` | Ola de apagado, restaurar luces, bloquear lectores |
| `ValveHooks.kt` | El puzle de las válvulas del nivel 3: cifras por partida, comprobar giros, abrir la puerta, reiniciar |
| `NoteHooks.kt` | Leer una nota clavada: busca su id y la envía con las cifras de las válvulas |
| `EndingHooks.kt` | El final de la demo: pasos, fundido, pantalla final |
| `DebugCommands.kt` | `/everytunnel ...` |
| `DevWorlds.kt` | Plantillas de mundo solo de desarrollo, restauradas en cada arranque |
| `PlaytestLog.kt` | El registro `[playtest]` |

### Mixins comunes: `main/java/daio7771/everytunnel/mixin/`

| Archivo | Qué hace |
|---|---|
| `ServerWorldMixin.java` | Permite al sistema de ruido oír los game events de vanilla |
| `LivingEntityMixin.java` | Sin el sorbo de vanilla para la botella de agua |

## Cliente: `client/kotlin/daio7771/everytunnel/client/`

| Archivo | Qué hace |
|---|---|
| `EveryTunnelClient.kt` | Entrypoint del cliente: registra todo lo de abajo |
| `EveryTunnelDataGenerator.kt` | Entrypoint de datagen vacío |
| `ClientResources.kt` | Los recursos del jugador según los envió el servidor por última vez |
| `FlashlightControls.kt` | La tecla de la linterna (F) |
| `ShaderMode.kt` | Shaders sí o no, consultado en vivo con la API de Iris |
| `ShaderSettings.kt` | Escribe una vez los ajustes recomendados de Complementary |
| `MapWorldInstaller.kt` | Desempaqueta el mundo del mapa del jar en `saves/` |
| `compat/LambDynamicLightsCompat.kt` | El haz como fuente de luz de LambDynamicLights |

### `effects/`

| Archivo | Qué hace |
|---|---|
| `AmbiencePlayer.kt` | Bucle del nivel y sonidos aleatorios con posición |
| `FluorescentHum.kt` | Zumbido de las 4 luces más cercanas |
| `HeartbeatEffect.kt` | Latido según la distancia al Pale man |
| `BreathingEffect.kt` | Respiración fuerte agotado |
| `TensionMusic.kt` | Capas de música según el estado de los Pale men |
| `IntroMusic.kt` | La canción del ascensor y su parada de cinta |
| `MenuMusic.kt` | El tema del menú |
| `FlashlightEffect.kt` | Calcula el haz cada tick; parpadeo de batería baja |
| `ScreenFade.kt` | Fundidos a negro que manda el servidor |

### `hud/`

| Archivo | Qué hace |
|---|---|
| `StatusHud.kt` | Filas de vida, stamina, sed y batería |
| `ExhaustionVignette.kt` | Bordes oscuros de pantalla agotado o sediento |
| `FlashlightOverlay.kt` | El foco en pantalla sin shaders |

### `render/`

| Archivo | Qué hace |
|---|---|
| `PaleManRenderer.kt` | Renderizador de GeckoLib con la máscara de brillo de los ojos |
| `PaleManModel.kt` | Su modelo de GeckoLib, más la postura encorvada bajo techos bajos |
| `LootRenderer.kt` | Dibuja el botín tumbado |
| `GuardUniform.kt` | La skin del uniforme para todos los jugadores |

### `menu/`

| Archivo | Qué hace |
|---|---|
| `MenuScene.kt` | La planta del aparcamiento detrás del menú: la carga, la ilumina, la hace parpadear y la dibuja |
| `MenuSceneView.kt` | Los bloques y la luz de la escena como un mundo para el renderizador de bloques |
| `RotatedModel.kt` | Un modelo cocinado girado por cuartos de vuelta (los bloques girados de la escena) |

### `screen/`

| Archivo | Qué hace |
|---|---|
| `MainMenuScreen.kt` | El menú principal |
| `GuideScreen.kt` | La guía del jugador: una hoja de papel con scroll y pestañas |
| `NoteScreen.kt` | Una nota leída en el papel de la guía sobre el juego, con los huecos rellenos |
| `LoadingScreen.kt` | La pantalla de carga y `LoadingCover`, que la dibuja encima de todo |
| `LoadingTips.kt` | Consejos que van cambiando, con las teclas del jugador |
| `EndOfDemoScreen.kt` | "End of demo" y los créditos que suben |

### Mixins del cliente: `client/java/daio7771/everytunnel/mixin/client/`

Ver [Arquitectura](architecture.md#mixins) para el objetivo y el motivo de cada una.

## Recursos: `main/resources/`

| Ruta | Qué contiene |
|---|---|
| `fabric.mod.json` | Metadatos del mod, entrypoints, dependencias |
| `every_tunnel.mixins.json`, `client/resources/every_tunnel.client.mixins.json` | Listas de mixins |
| `assets/every_tunnel/lang/en_us.json` | Todos los textos del juego |
| `assets/every_tunnel/lang/es_*.json` | Solo las palabras propias de la pantalla de la guía en español; el resto vuelve al inglés |
| `assets/every_tunnel/guide/` | No está en `src/`: lo copia de `docs/*/guide/` la compilación (`copyGuide`) |
| `assets/every_tunnel/sounds.json`, `sounds/` | Eventos de sonido y archivos `.ogg` (de `tools/sounds/export.py`) |
| `assets/every_tunnel/textures/`, `models/`, `blockstates/` | De `tools/blocks`, `tools/entity`, `tools/gui` |
| `assets/every_tunnel/geo/`, `animations/` | Los archivos de GeckoLib del Pale man |
| `assets/every_tunnel/menu/`, `models/block/menu/`, `textures/block/menu/` | La escena del menú (de `tools/map/map_generator.py`) |
| `data/every_tunnel/loot_tables/` | Loot tables de los bloques |

## Tests: `test/kotlin/daio7771/everytunnel/`

Ver [Depuración](debugging.md#tests-unitarios).
