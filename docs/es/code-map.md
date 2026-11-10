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
| `GameplayConfig.kt` | Cada valor ajustable con su valor por defecto, por secciones (stamina, sed, comida, linterna, llave inglesa, detector, ruido, Pale man) |
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
| `achievement/Achievement.kt` | Los diez logros, en el orden de la lista, por id |
| `puzzle/ValveCode.kt` | El puzle de las válvulas del nivel 3: cifras al azar, un giro, resuelto |
| `puzzle/OrderPuzzle.kt` | Un puzle de cosas en orden: intentos, fallos, la pista |
| `door/SlidingDoor.kt` | Una puerta de dos hojas que se meten en las paredes un cuarto de bloque cada vez: cuánto queda de cada columna (la esclusa del nivel 5) |
| `save/LoungeSave.kt` | El nivel 4 como se dejó, en texto |
| `wall/WallMessageState.kt` | Los disparadores de un mensaje: fijo, al acercarse, cambia al dejar de mirarlo |
| `puzzle/BreakerCode.kt` | Las cinco palancas del nivel 4: el código, cuántas están bien |
| `puzzle/ChemicalMix.kt` | El puzle químico del nivel 5: la pareja de reactivos sorteada en cada partida, en cualquier orden |
| `puzzle/SafeDial.kt` | El dial de una caja: tics, clics, fijar un número cambiando de sentido |
| `puzzle/darts/DartboardGeometry.kt` | Dónde cayó un dardo en el tablero: número y anillo, en las 4 orientaciones |
| `film/FilmInfo.kt` | La película del Cinema: fotogramas, velocidad, qué fotograma en cada momento |
| `progress/PlayerProgress.kt` | Lo que ha hecho un jugador: piezas, notas leídas, escenas vistas, libros del nivel 3, checkpoint |
| `progress/Checkpoint.kt` | Un checkpoint como texto: el inicio de un nivel, o `nivel/punto` para un punto de reinicio dentro de él |
| `detector/Detector.kt` | El detector del nivel 5: cuánto se llena su icono, cuándo sale el triángulo, a qué ritmo pita |
| `death/DeathCause.kt` | Lo que nombra la pantalla de muerte (quién te mató, la sed, la caída) y dónde sale |
| `puzzle/NoteText.kt` | El texto de una nota: título, párrafos, huecos `{A}` / `{A:word}` |
| `hiding/LookLimit.kt` | Cuánto gira la cabeza dentro de una taquilla |

### `systems/`

| Archivo | Qué hace |
|---|---|
| `ResourceSystem.kt` | Simulación de stamina, sed y batería, como funciones puras |
| `NoiseSystem.kt` | Eventos de ruido, su memoria, audibilidad y las reglas que eligen los radios |
| `FlashlightBeam.kt` | La luz del haz en cualquier punto (cono, caída, punto caliente, brillo) |
| `CollapseSchedule.kt` | Los tiempos del derrumbe del túnel del nivel 3: escombros, parpadeo, polvo, fuerza del temblor |
| `ObserverNearness.kt` | La fase de los libros del nivel 3: ritmo y volumen del reloj y el aviso "He's near" según la distancia al Observer |

### `entities/`

| Archivo | Qué hace |
|---|---|
| `PaleManBrain.kt` | La máquina de estados del Pale man y la fórmula del volumen |
| `ObserverBrain.kt` | La máquina de estados del Observer (patrulla, aviso, persecución, búsqueda, sacar de la taquilla) y sus números |
| `HunterBrain.kt` | La máquina de estados de los Hunters (merodear, perseguir, buscar, volver) y sus golpes |
| `Gaze.kt` | Adónde mira un jugador: por delante (la Cloud), en el blanco (el ojo del Junior) |
| `DistortionClocks.kt` | El reloj de la Cloud (la gracia, irse), el del Junior (la mirada), cuándo viene la siguiente Cloud |
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
| `LabItems.kt` | La llave inglesa del nivel 5: un arma pesada que no se desgasta |
| `ChemicalBlocks.kt` | La mezcladora del nivel 5: sus depósitos (un frasco cada uno), su cuerpo (el botón), las luces rojas de la alarma |
| `TerminalHooks.kt` | Los ordenadores del nivel 5 (`LabTerminalBlock`): qué muestra cada pantalla, enviado al cliente |
| `FuseHolderBlock.kt` | Un hueco de la caja de fusibles del nivel 5: solo admite el fusible de su amperaje; llena la caja, se encienden sus luces y la palanca de al lado funciona |
| `ModEntities.kt` | El tipo de entidad del Pale man y su huevo; el asiento de la taquilla |
| `ModSounds.kt` | Eventos de sonido que reproduce el código |
| `ModSoundGroups.kt` | Grupos de sonido de bloques por material de prop |
| `Package.kt` | Documentación del paquete: el pegamento que depende de la versión de Minecraft |

### `platform/`: bloques

| Archivo | Qué hace |
|---|---|
| `BlockShapes.kt` | Cajas de contorno y colisión en píxeles, giradas como los blockstates; los mobs rodean cualquier bloque del mod con colisión |
| `PathingRules.kt` | Mantiene a los mobs fuera y lejos de encima de los bloques sólidos del mod (muebles, props): los rodean, nunca los atraviesan ni se suben |
| `PropBlock.kt` | Un prop de suelo orientado a uno de cuatro lados |
| `ConnectingPropBlock.kt` | Tuberías, conductos y bandejas de cables que se conectan con sus vecinos |
| `FluorescentLightBlock.kt` | La luz: encendida, parpadeante, apagada; nivel de luz 13, dibujada a brillo máximo |
| `DrainBlocks.kt` | El drenaje del nivel 5: su escalera de mano, sus lámparas enjauladas (tenues, parpadean como los fluorescentes), las luces cuadradas del despacho, la barandilla de las pasarelas |
| `WarmLampBlock.kt` | La luz de techo del nivel 4: cálida y silenciosa, nivel de luz 14 |
| `SuiteBedBlock.kt` | Una cama de las Suites del nivel 4, en dos bloques |
| `PuzzleRackBlock.kt` | Un estante con un disco o un carrete, y su brillo |
| `PuzzleDeviceBlock.kt` | El tocadiscos y el proyector |
| `SurfaceMarkBlocks.kt` | Manchas y charcos en cualquier cara |
| `SignTileBlock.kt` | Textos pintados por ranura numerada (el pack del mundo los asigna) |
| `ElevatorDoorBlock.kt` | Etapas de la puerta corredera del ascensor |
| `FloorIndicatorBlock.kt` | El indicador LED de planta del ascensor |
| `ShutterBlock.kt` | Una pieza de una persiana metálica |
| `CardReaderBlock.kt` | El lector de tarjetas que abre puertas de hierro |
| `NoteBoardBlock.kt` | Un tablón de corcho con una nota: clic derecho para leerla |
| `NotePaperBlock.kt` | Un folio mecanografiado suelto sobre una mesa (los documentos del Nivel 3): clic derecho para leerlo |
| `WallTextBlock.kt` | Texto en una pared con la fuente del juego (placas del nivel 3, letras de la entrada) y su block entity |
| `BoxBlocks.kt` | La caja morada del nivel 3 (se recoge), su estantería (partes, huecos, la entidad de bloque de las cajas) y el libro de oficina |
| `LockerBlock.kt` | Una taquilla (dos bloques apilados): clic derecho para esconderse |
| `LockerSeatEntity.kt` | El asiento invisible en el que va el jugador escondido: límite de giro, salir por delante, el sonido de la puerta |
| `FurnitureBlocks.kt` | Los muebles del nivel 3 que se unen a los de su clase: piezas en fila (mesas, barras, sofás) a izquierda y derecha, tableros de mesa por todos los lados, piezas apiladas (estanterías, racks, taquillas) arriba y abajo |
| `ValveBlock.kt` | Una válvula de compuerta en línea en una tubería de pared: orientación, abierta, letra A a E (nivel 3) |
| `LootBlock.kt` | Botín tirado en el suelo, se recoge con clic derecho |

### `platform/`: los enemigos

| Archivo | Qué hace |
|---|---|
| `PaleManEntity.kt` | La entidad: oído, muros, territorio, rutas, golpes, sonidos, animaciones |
| `PaleManSpawner.kt` | Genera uno por cada punto `pale_man`, una vez por mundo; contador de partidas nuevas |
| `ObserverEntity.kt` | El Observer: vista (cono, alcance, paredes), moverse y mirar alrededor, atrapar, sacar de las taquillas |
| `ObserverHooks.kt` | Genera al Observer, le pasa los avisos de las cajas, lo devuelve a casa al morir el jugador |
| `HunterEntity.kt` | Un Hunter: vista (cono, alcance, paredes), avisar a su grupo, moverse, golpes, sonidos, animaciones |
| `DistortionEntity.kt` | Lo que comparten la Cloud y el Junior: flotar, que nada los toque, mirar al jugador, la mirada y el irse sincronizados, el humo |
| `DistortionCloudEntity.kt` | La Distortion Cloud: quién mira hacia ella, matar, irse |
| `DistortionJuniorEntity.kt` | El Distortion Junior: a quién molesta y marca, la mirada que acaba con él |
| `LabFigureEntity.kt` | La figura que se ve desde el Lobby del nivel 5: cuando la miran, se va andando tras la esquina y desaparece |
| `LabFigureHooks.kt` | Pone la figura ahí al construirse el nivel 5, una vez por partida |
| `SubjectEntity.kt` | Los sujetos de pruebas, inmóviles en sus salas selladas, por fase; `SubjectHooks` los pone con el nivel |
| `DeadGuardEntity.kt` | El guardia anterior, muerto en el Hideout del drenaje; `DeadGuardHooks` lo pone ahí con el nivel |
| `LabHallHooks.kt` | El golpe de cuerdas y la primera Cloud del Lab hall, una vez por partida |
| `ChemicalHooks.kt` | El puzle químico: la pareja de la partida, la mezcladora, la alarma y sus enemigos, la rejilla disolviéndose, la trampilla |
| `DrainHooks.kt` | El drenaje del nivel 5: la trampilla cerrándose de golpe cuando el jugador ha bajado su escalera, abierta otra vez si muere antes de la Junction |
| `DistortionHooks.kt` | Los enemigos del nivel 5: sus puestos según los puntos de aparición, de vuelta al morir, avisar a los Hunters, cuándo y dónde viene una Cloud, `/everytunnel spawn` |

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
| `FoodHooks.kt` | Comer: devuelve vida |
| `WaterDispenserHooks.kt` | Beber en un dispensador de agua que da agua (la cocina del nivel 3) |
| `JumpCounter.kt` | Saltos por jugador a partir de la estadística de vanilla |
| `NoiseHooks.kt` | Convierte pasos, saltos, aterrizajes, jadeos y game events en ruidos |
| `AmbienceHooks.kt` | Envía a cada jugador el ambiente de su nivel |
| `RespawnHooks.kt` | Pone el punto de reaparición en el inicio del nivel actual |
| `DeathHooks.kt` | La muerte como fundido a negro y reinicio del nivel |
| `LoadingHooks.kt` | Reconstruir al abrir, estado de carga, partida nueva, tiempos límite de seguridad |
| `NewGame.kt` | Reinicia el mundo y el jugador para una partida nueva |
| `ProgressService.kt` | El progreso del jugador tal como lo ve el resto del mod |
| `CardinalProgress.kt` | `ProgressService` sobre Cardinal Components, la única clase que toca esa librería |
| `IntroHooks.kt` | La intro del ascensor: puertas, canción, indicadores de planta, título |
| `LevelEntryHooks.kt` | Títulos de nivel y persianas que se cierran tras el jugador |
| `LootSpawner.kt` | Coloca y repone el botín en los puntos con etiqueta |
| `KeycardHooks.kt` | Ola de apagado, restaurar luces, bloquear lectores |
| `ValveHooks.kt` | El puzle de las válvulas del nivel 3: cifras por partida, comprobar giros, abrir la puerta, reiniciar |
| `NoteHooks.kt` | Leer una nota (tablón o folio): busca su id y la envía con las cifras de las válvulas |
| `CarryHooks.kt` | Llevar una caja o un libro: en la mano, sin soltarlo, sin correr, de vuelta al morir |
| `BookHooks.kt` | El puzle de los libros del nivel 3: aparecen, se entregan en el mostrador, se abre la trampilla, depuración |
| `CollapseHooks.kt` | El derrumbe del túnel del nivel 3: sonido, temblor, escombros, polvo, luces parpadeando; se deshace al morir o en partida nueva |
| `LoungeHooks.kt` | Del nivel 3 al 4: la puerta a B4, borrar y construir por tandas, la puerta de Reception |
| `LabHooks.kt` | El nivel 5 construido en juego desde el tramo oscuro del nivel 4, por pasos; las puertas de la esclusa, la de atrás y la de delante |
| `FilmSetHooks.kt` | El escenario para rodar la película del Cinema, aparte de los niveles |
| `FilmWalkerEntity.kt` | La figura que anda por él |
| `LoungePuzzleHooks.kt` | Los puzles del Bar y el Cinema del nivel 4, las pistas y las piezas de la tarjeta maestra |
| `DartHooks.kt` | Los dardos del nivel 4: la bandeja, cada acierto, la pista, la portezuela con la pieza |
| `DartBlocks.kt` | Las nueve partes del tablero, la bandeja y la portezuela |
| `DartEntity.kt` | Un dardo lanzado, sobre la flecha de vanilla; nunca se guarda |
| `LoungeClosingHooks.kt` | El cierre del nivel 4: el apagón, las luces de emergencia, la puerta grande |
| `LoungeClosingBlocks.kt` | La puerta grande, el lector de la Calm room, la luz de emergencia |
| `LabBlocks.kt` | La puerta de la esclusa del nivel 5: una columna de una hoja, cuánto de ella cruza el hueco |
| `SlidingDoors.kt` | Las puertas correderas del nivel 5 en el mundo: cuánto están abiertas, abrir una poco a poco |
| `LabDoorHooks.kt` | Las puertas bloqueadas del nivel 5: las palancas, la ruta sur que se abre al salir de la Office |
| `BreakerHooks.kt` | La sala de mantenimiento del nivel 4: palancas, botón, contador, corriente del lector |
| `SafeHooks.kt` | Las cajas fuertes de las Suites: combinaciones, bloque, resultado del cliente |
| `ContinueGame.kt` | La partida guardada: puntos de control (los puntos de reinicio de un nivel: el que hay dentro de la zona en la que se entra), CONTINUE, recuperar el nivel 4 |
| `WallMessageBlock.kt` | Un mensaje pintado en la pared: el bloque y sus datos |
| `SafeLevelHooks.kt` | Niveles sin riesgo: sin daño (el nivel 4) |
| `SuiteBedHooks.kt` | Descansar en una cama: fundido a negro, vida y estamina llenas |
| `BookPhaseHooks.kt` | Mientras los libros están fuera: el reloj en la cabeza y el aviso "He's near. Hide." |
| `BoxHooks.kt` | El puzle de las cajas del nivel 3: cajas al azar, llevarlas, la estantería, la muerte, depuración |
| `HidingHooks.kt` | Esconderse en taquillas: entrar, salir, sin ruido dentro, salir en partida nueva |
| `AchievementHooks.kt` | Conseguir logros: qué da cada uno, avisar al cliente |
| `EndingHooks.kt` | El final de la demo: fundido, pantalla final |
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
| `SprintCheck.kt` | Apunta en el log por qué el jugador no corre con la tecla de correr pulsada |
| `AchievementStore.kt` | Los logros conseguidos, guardados para siempre en `config/every_tunnel/achievements.json` |
| `AchievementIcons.kt` | El icono de cada logro (un objeto o uno dibujado para el mod, o el "?" de bloqueado) |
| `compat/LambDynamicLightsCompat.kt` | El haz como fuente de luz de LambDynamicLights |

### `effects/`

| Archivo | Qué hace |
|---|---|
| `AmbiencePlayer.kt` | Bucle del nivel y sonidos aleatorios con posición |
| `FluorescentHum.kt` | Zumbido de las 4 luces más cercanas |
| `HeartbeatEffect.kt` | Latido según la distancia al Pale man |
| `DetectorEffect.kt` | El detector del nivel 5: la Cloud más cercana a la vista, los pitidos |
| `DistortionEffect.kt` | Las Distortions en los sentidos: susurros, la pantalla distorsionándose ante una Cloud, el temblor y la deriva del Junior |
| `CameraShake.kt` | La cámara temblando en el derrumbe del túnel del nivel 3 |
| `FilmPlayer.kt` | La película del Cinema en su pantalla, y el sonido del proyector |
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
| `DetectorHud.kt` | El símbolo de radiación que se llena y el triángulo de peligro |
| `ExhaustionVignette.kt` | Bordes oscuros de pantalla agotado o sediento |
| `FlashlightOverlay.kt` | El foco en pantalla sin shaders |
| `LockerView.kt` | El interior de la puerta de la taquilla sobre la pantalla mientras te escondes |
| `AchievementToast.kt` | El aviso "Achievement Unlocked!"; recibe los logros conseguidos |

### `render/`

| Archivo | Qué hace |
|---|---|
| `PaleManRenderer.kt` | Renderizador de GeckoLib con la máscara de brillo de los ojos |
| `FilmWalkerRenderer.kt` | La figura de la película del Cinema, con el modelo del jugador |
| `DartRenderer.kt` | Un dardo lanzado: la flecha de vanilla, más pequeña, con su textura |
| `WallMessageRenderer.kt` | Dibuja un mensaje con la fuente pintada y recuerda lo que le ha pasado |
| `ObserverRenderer.kt` | Renderizador de GeckoLib del Observer; su cabeza gira con la de la entidad |
| `SubjectRenderer.kt` | Renderizador de GeckoLib de los sujetos, cada fase con su cuerpo y su piel |
| `DeadGuardRenderer.kt` | Renderizador de GeckoLib del guardia anterior, sentado, con el uniforme del jugador gastado |
| `LabFigureRenderer.kt` | La figura que se ve desde el Lobby del nivel 5, con el modelo del jugador, una silueta oscura |
| `HunterRenderer.kt` | Renderizador de GeckoLib de los Hunters, con los ojos brillando |
| `DistortionRenderer.kt` | Renderizador y modelo de GeckoLib de las Distortions: giradas hacia su yaw, el glitch, el ojo del Junior agrietándose |
| `PaleManModel.kt` | Su modelo de GeckoLib, más la postura encorvada bajo techos bajos |
| `BoxShelfRenderer.kt` | Las cajas moradas en la estantería, a brillo completo |
| `WallTextRenderer.kt` | Un texto de pared: blanco sobre una placa oscura, o letras grandes oscuras en la pared |
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
| `AchievementsScreen.kt` | La pantalla ACHIEVEMENTS: la lista en papel |
| `Paper.kt` | Papel gastado (mosaico, manchas, bordes, tintas) para la guía y los logros |
| `GuideScreen.kt` | La guía del jugador: una hoja de papel con scroll y pestañas |
| `NoteScreen.kt` | Una nota leída en el papel de la guía sobre el juego, con los huecos rellenos |
| `SafeScreen.kt` | El dial de una caja fuerte, que se gira y se escucha |
| `EraseSaveScreen.kt` | El aviso antes de que Ctrl+Shift+D borre la partida guardada |
| `LoadingScreen.kt` | La pantalla de carga y `LoadingCover`, que la dibuja encima de todo |
| `LoadingTips.kt` | Consejos que van cambiando, con las teclas del jugador |
| `EndOfDemoScreen.kt` | "End of demo" y los créditos que suben |
| `TerminalScreen.kt` | La pantalla de un ordenador del nivel 5, tecleada en verde sobre negro |
| `DeathScreen.kt` | La pantalla de muerte: "Killed by", quién te mató y un consejo sobre el negro al morir, y "Try again" |

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
