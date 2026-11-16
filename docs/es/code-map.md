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
| `menu/LogoRun.kt` | El easter egg del logo del menú: cuándo se apagan las palabras dejando RUN encendido, y por cuánto tiempo |
| `menu/BlockLight.kt` | Luz de bloque para una caja de bloques sin mundo (la escena del menú) |
| `achievement/Achievement.kt` | Los once logros, en el orden de la lista, por id |
| `puzzle/ValveCode.kt` | El puzle de las válvulas del nivel 3: cifras al azar, un giro, resuelto |
| `puzzle/OrderPuzzle.kt` | Un puzle de cosas en orden: intentos, fallos, la pista |
| `door/SlidingDoor.kt` | Una puerta de dos hojas que se meten en las paredes un cuarto de bloque cada vez: cuánto queda de cada columna (la esclusa del nivel 5) |
| `save/LoungeSave.kt` | El nivel 4 como se dejó, en texto |
| `camera/CameraAim.kt` | Hacia dónde apunta la cabeza de una cámara de seguridad, girando hacia un objetivo no más rápido que su servo |
| `cutscene/Cutscene.kt` | Una cinemática como datos: poses de cámara, el marco de una escena, planos, el vuelo de entrada y de salida, las bandas negras |
| `cutscene/DirectorScene.kt` | La escena del director medida con su voz: encuadres, subtítulos, sus gestos, cuándo aparta la vista, su máscara; `SkipPrompt` (saltar pulsando dos veces) |
| `cutscene/DirectorLines.kt` | Las líneas de voz del director y sus subtítulos, leídas de `cutscene/director.json` |
| `cutscene/DirectorLoop.kt` | Las frases que repite tras su escena: su orden, las pausas, cuánto mira al jugador |
| `cutscene/FinalCredits.kt` | La línea de tiempo de los créditos finales (los dibuja `FinalCreditsScreen`) |
| `wall/WallMessageState.kt` | Los disparadores de un mensaje: fijo, al acercarse, cambia al dejar de mirarlo |
| `puzzle/BreakerCode.kt` | Las cinco palancas del nivel 4: el código, cuántas están bien |
| `puzzle/ChemicalMix.kt` | El puzle químico del nivel 5: la pareja de reactivos sorteada en cada partida, en cualquier orden |
| `puzzle/SafeDial.kt` | El dial de una caja: tics, clics, fijar un número cambiando de sentido |
| `puzzle/darts/DartboardGeometry.kt` | Dónde cayó un dardo en el tablero: número y anillo, en las 4 orientaciones |
| `film/FilmInfo.kt` | La película del Cinema: fotogramas, velocidad, qué fotograma en cada momento |
| `progress/PlayerProgress.kt` | Lo que ha hecho un jugador: piezas, notas leídas, escenas vistas, libros del nivel 3, checkpoint (un capítulo posterior guarda el suyo) |
| `ending/RunSummary.kt` | Los números de la ventana de felicitación: tiempo jugado y muertes de una partida, el tiempo como `H:MM:SS` |
| `progress/Checkpoint.kt` | Un checkpoint como texto: el inicio de un nivel, o `nivel/punto` para un punto de reinicio dentro de él |
| `detector/Detector.kt` | El detector del nivel 5: cuánto se llena su icono, cuándo sale el triángulo, cuántas cuentas Geiger caen por tick |
| `death/DeathCause.kt` | Lo que nombra la pantalla de muerte (quién te mató, la sed, la caída, el agua honda) y dónde sale |
| `puzzle/NoteText.kt` | El texto de una nota: título, párrafos, huecos `{A}` / `{A:word}` |
| `spawn/PostLedger.kt` | Qué criatura es dueña de cada punto de spawn, para que un sobrante que aún muere o que carga tarde no borre el registro del que lo sustituyó (el Observer) |
| `progress/LeftBehind.kt` | Lo que no sirve de un nivel en adelante y se quita del inventario al empezarlo (la tarjeta de acceso desde el Nivel 3, la tarjeta maestra desde el Nivel 5) |
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
| `CloudGame.kt` | El juego del pasillo de entrada del nivel 5, como el de Halt: la Cloud detrás, sus saltos delante y detrás, cada vez más deprisa |
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
| `ModBlocks.kt` | Cada bloque del juego base: la luz, props, puertas, carteles, piezas del ascensor, botín, lector de tarjetas; la clave de la pestaña creativa (`TAB`) y los ajustes que comparten los bloques de un capítulo posterior |
| `ModItems.kt` | Batería, botella de agua, tarjeta; la pestaña creativa |
| `LabItems.kt` | La llave inglesa del nivel 5: un arma pesada que no se desgasta |
| `ChemicalBlocks.kt` | La mezcladora del nivel 5: sus depósitos (un frasco cada uno), su cuerpo (el botón), las luces rojas de la alarma |
| `TerminalHooks.kt` | Los ordenadores del nivel 5 (`LabTerminalBlock`): qué muestra cada pantalla, como claves de traducción (`terminal.every_tunnel.*`) que traduce el cliente |
| `BriefingBlocks.kt` | La pizarra de seguridad del nivel 5: las doce partes del corcho (un clic derecho lee la ficha de la columna donde cae) y la placa de encima |
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
| `DrainBlocks.kt` | El drenaje del nivel 5: su escalera de mano, sus lámparas enjauladas (tenues, parpadean como los fluorescentes), las luces cuadradas del despacho, la barandilla de las pasarelas, la flecha de tiza hacia el Hideout |
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
| `WallTextBlock.kt` | Texto en una pared con la fuente del juego (placas de los niveles 3 y 5, letras de la entrada) y su block entity |
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
| `ObserverHooks.kt` | Genera al Observer, le pasa los avisos de las cajas, lo devuelve a casa al morir el jugador; al reconstruir el mapa descarta a los viejos y pone uno de nuevo |
| `HunterEntity.kt` | Un Hunter: vista (cono, alcance, paredes), avisar a su grupo, moverse, golpes, sonidos, animaciones |
| `DistortionEntity.kt` | Lo que comparten la Cloud y el Junior: flotar, que nada los toque, mirar al jugador, la mirada y el irse sincronizados, el humo |
| `DistortionCloudEntity.kt` | La Distortion Cloud: quién mira hacia ella, matar, irse; la del juego del pasillo de entrada |
| `DistortionJuniorEntity.kt` | El Distortion Junior: a quién molesta y marca, la mirada que acaba con él |
| `LabFigureEntity.kt` | La figura que se ve desde el Lobby del nivel 5: cuando la miran, se va andando tras la esquina y desaparece |
| `LabFigureHooks.kt` | Pone la figura ahí al construirse el nivel 5, una vez por partida |
| `SubjectEntity.kt` | Los sujetos de pruebas, sedados y atados a sus camillas en sus salas selladas, por fase; `SubjectHooks` los pone con el nivel |
| `DeadGuardEntity.kt` | El guardia anterior, muerto en el Hideout del drenaje; `DeadGuardHooks` lo pone ahí con el nivel |
| `DirectorEntity.kt` | El director en su silla (GeckoLib): se mueve con la escena del cliente, con máscara y mirada sincronizadas; `DirectorHooks` lo pone ahí con el nivel |
| `LabHallHooks.kt` | El golpe de cuerdas y la primera Cloud del Lab hall, una vez por partida |
| `ChemicalHooks.kt` | El puzle químico: la pareja de la partida, la mezcladora, la alarma y sus enemigos, la rejilla disolviéndose, la trampilla |
| `ChapterRequest.kt` | Lo que pidió el menú: el mundo se abre solo para la apertura de un capítulo posterior (sin reconstruir el mapa ni nueva partida), que arranca ese capítulo (`onLoaded`); su cliente sale al menú después |
| `DirectorBlocks.kt` | La antesala y el despacho del nivel 5: la puerta doble del director que abre sus dos hojas a la vez, el bloque de la cámara de seguridad, el muro de monitores CCTV |
| `DeepWaterHooks.kt` | El agua honda del nivel 5 (las piscinas del drenaje): al caer, algo arrastra al jugador al fondo y se ahoga |
| `DrainHooks.kt` | El drenaje del nivel 5: la trampilla cerrándose de golpe cuando el jugador ha bajado su escalera, abierta otra vez si muere antes de la Junction |
| `DistortionHooks.kt` | Los enemigos del nivel 5: sus puestos según los puntos de aparición, de vuelta al morir, avisar a los Hunters, cuándo y dónde viene una Cloud, `/everytunnel spawn` |
| `CloudGameHooks.kt` | El juego del pasillo de entrada: lo empieza y lo acaba su zona, mueve su Cloud por la línea del pasillo |

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
| `FoodHooks.kt` | Comer: devuelve vida y da 10 s de energía ilimitada, con cualquier vida |
| `WaterDispenserHooks.kt` | Beber en un dispensador de agua que da agua (la cocina del nivel 3) |
| `JumpCounter.kt` | Saltos por jugador a partir de la estadística de vanilla |
| `NoiseHooks.kt` | Convierte pasos, saltos, aterrizajes, jadeos y game events en ruidos |
| `AmbienceHooks.kt` | Envía a cada jugador el ambiente de su nivel |
| `RespawnHooks.kt` | Pone el punto de reaparición en el inicio del nivel actual |
| `DeathHooks.kt` | La muerte como fundido a negro y reinicio del nivel |
| `LoadingHooks.kt` | Reconstruir al abrir, estado de carga, partida nueva, tiempos límite de seguridad |
| `NewGame.kt` | Reinicia el mundo y el jugador para una partida nueva |
| `ProgressService.kt` | El progreso del jugador tal como lo ve el resto del mod; `onReset`, para que un capítulo posterior olvide el suyo con una partida nueva |
| `CardinalProgress.kt` | `ProgressService` sobre Cardinal Components (con el componente propio de un capítulo posterior, las únicas clases que tocan esa librería) |
| `PlayerRules.kt` | Lo que un capítulo cambia de las reglas del juego base para sus jugadores (linterna, esconderse, daño, ruido, barras de estado): los capítulos registran una fuente, los hooks base preguntan |
| `IntroHooks.kt` | La intro del ascensor: puertas, canción, indicadores de planta, título |
| `LevelEntryHooks.kt` | Títulos de nivel y persianas que se cierran tras el jugador |
| `LootSpawner.kt` | Coloca y repone el botín en los puntos con etiqueta |
| `KeptEntities.kt` | Entidades que nunca se guardan pero se ponen lejos del jugador (las del nivel 5): vuelven a su sitio cuando carga otra vez el chunk que las perdió |
| `KeycardHooks.kt` | Ola de apagado, restaurar luces, bloquear lectores |
| `ValveHooks.kt` | El puzle de las válvulas del nivel 3: cifras por partida, comprobar giros, abrir la puerta, reiniciar |
| `NoteHooks.kt` | Leer una nota (tablón o folio): busca su id y la envía con las cifras de las válvulas |
| `CarryHooks.kt` | Llevar una caja o un libro: en la mano, sin soltarlo, sin correr, de vuelta al morir; en el puntero del ratón o en la cuadrícula de crafteo de la pantalla del inventario sigue contando como llevado |
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
| `ContinueGame.kt` | La partida guardada: puntos de control (los puntos de reinicio de un nivel: el que hay dentro de la zona en la que se entra), CONTINUE, recuperar el nivel 4; saca del inventario, antes de guardarlo, lo que ya no sirve de un nivel en adelante (`core/progress/LeftBehind.kt`) |
| `WallMessageBlock.kt` | Un mensaje pintado en la pared: el bloque y sus datos |
| `SafeLevelHooks.kt` | Niveles sin riesgo: sin daño (el nivel 4) |
| `SuiteBedHooks.kt` | Descansar en una cama: fundido a negro, vida y estamina llenas |
| `BookPhaseHooks.kt` | Mientras los libros están fuera: el reloj en la cabeza y el aviso "He's near." |
| `BoxHooks.kt` | El puzle de las cajas del nivel 3: cajas al azar, llevarlas, la estantería, la muerte, depuración |
| `HidingHooks.kt` | Esconderse en taquillas: entrar, salir, sin ruido dentro, salir en partida nueva |
| `AchievementHooks.kt` | Conseguir logros: qué da cada uno, avisar al cliente |
| `EndingHooks.kt` | El final del juego: la partida guardada borrada, los créditos finales, los números de la partida enviados con ellos |
| `FalseExitHooks.kt` | La falsa salida del nivel 5: el suelo sobre el pozo que cede, la caída, el negro |
| `DirectorSceneHooks.kt` | La escena del director: empieza al abrir su puerta, sujeta al jugador, el portazo, el salto y el final (un punto de reinicio tras su puerta); `DirectorLoopHooks`, en el mismo archivo, manda sus frases después |
| `DebugCommands.kt` | `/everytunnel ...` |
| `LinkedDoorBlock.kt` | Una hoja de un par de puertas que se abren y se cierran a la vez a mano (`factory_door`, con el aspecto de la puerta de laboratorio); sin escena, a diferencia de `PairedDoorBlock` |
| `DevWorlds.kt` | Plantillas de mundo solo de desarrollo, restauradas en cada arranque |
| `PlaytestLog.kt` | El registro `[playtest]` |
| `RunStatsHooks.kt` | Cuenta el tiempo jugado en el progreso del jugador (las muertes se cuentan en `DeathHooks`), para la ventana de felicitación |
| `TickProfiler.kt` | Mide el tick del servidor y cada hook de tick (`/everytunnel profile`); `core/profile/TickStats.kt` guarda los números |

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
| `compat/LambDynamicLightsCompat.kt` | El haz como fuente de luz de LambDynamicLights; la luz de las Clouds del pasillo de entrada |

### `effects/`

| Archivo | Qué hace |
|---|---|
| `AmbiencePlayer.kt` | Bucle del nivel y sonidos aleatorios con posición |
| `FluorescentHum.kt` | Zumbido de las 4 luces más cercanas |
| `HeartbeatEffect.kt` | Latido según la distancia al Pale man |
| `DetectorEffect.kt` | El detector del nivel 5 (sin objeto: donde el servidor dice que funciona): la Cloud más cercana a la vista, los chasquidos Geiger |
| `DistortionEffect.kt` | Las Distortions en los sentidos: susurros, la pantalla distorsionándose ante una Cloud, el violeta de las del pasillo de entrada, el temblor y la deriva del Junior |
| `CameraShake.kt` | La cámara temblando en el derrumbe del túnel del nivel 3 |
| `FilmPlayer.kt` | La película del Cinema en su pantalla, y el sonido del proyector |
| `BreathingEffect.kt` | Respiración fuerte agotado |
| `TensionMusic.kt` | Capas de música según el estado de los Pale men |
| `IntroMusic.kt` | La canción del ascensor y su parada de cinta |
| `MenuMusic.kt` | La música del menú, una pista por capítulo, con fundido de una a otra cuando el menú cambia de capítulo |
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
| `SubtitleStrip.kt` | El subtítulo de una voz sobre el HUD: el hablante en gris, el texto en blanco, sobre una franja oscura |

### `render/`

| Archivo | Qué hace |
|---|---|
| `PaleManRenderer.kt` | Renderizador de GeckoLib con la máscara de brillo de los ojos |
| `FilmWalkerRenderer.kt` | La figura de la película del Cinema, con el modelo del jugador |
| `DartRenderer.kt` | Un dardo lanzado: la flecha de vanilla, más pequeña, con su textura |
| `SecurityCameraRenderer.kt` | Gira la cabeza de la cámara de la antesala para seguir al jugador, parpadea su piloto rojo, suena su servo; una que barre (las cámaras decorativas de un capítulo posterior, con su propio tipo de entidad de bloque) solo barre |
| `WallMessageRenderer.kt` | Dibuja un mensaje con la fuente pintada y recuerda lo que le ha pasado |
| `ObserverRenderer.kt` | Renderizador de GeckoLib del Observer; su cabeza gira con la de la entidad |
| `SubjectRenderer.kt` | Renderizador de GeckoLib de los sujetos, cada fase con su cuerpo y su piel |
| `DeadGuardRenderer.kt` | Renderizador de GeckoLib del guardia anterior, sentado, con el uniforme del jugador gastado |
| `DirectorRenderer.kt` | Renderizador de GeckoLib del director: su modelo, su textura y sus animaciones, girado a su orientación |
| `LabFigureRenderer.kt` | La figura que se ve desde el Lobby del nivel 5, con el modelo del jugador, una silueta oscura |
| `HunterRenderer.kt` | Renderizador de GeckoLib de los Hunters, con los ojos brillando |
| `EyesGlowLayer.kt` | El brillo de los ojos de los Hunters y las Distortions: la textura `_glowmask` dibujada otra vez con la capa `eyes` de vanilla (sustituye a `AutoGlowingGeoLayer` de GeckoLib, que recortaba los ojos de la textura base) |
| `DistortionRenderer.kt` | Renderizador y modelo de GeckoLib de las Distortions: giradas hacia su yaw, el glitch, el ojo del Junior agrietándose |
| `PaleManModel.kt` | Su modelo de GeckoLib, más la postura encorvada bajo techos bajos |
| `BoxShelfRenderer.kt` | Las cajas moradas en la estantería, a brillo completo |
| `WallTextRenderer.kt` | Un texto de pared: blanco sobre una placa oscura, oscuro sobre latón o sobre el plástico blanco del laboratorio, letras grandes oscuras en la pared, o varias líneas en un diploma enmarcado o una placa pequeña de latón |
| `LootRenderer.kt` | Dibuja el botín tumbado |
| `GuardUniform.kt` | La skin del uniforme para todos los jugadores |

### `cutscene/`

| Archivo | Qué hace |
|---|---|
| `CutsceneView.kt` | Una cinemática en el cliente (la del director, o un `Kind` propio de un capítulo posterior, que añade sus sonidos, temblor, destello y final): una cámara propia movida tick a tick, el HUD apagado, el jugador dibujado desde fuera (si el kind lo dice), la pose del director, líneas de voz, saltar, el negro del que sale una escena |
| `CutsceneScreen.kt` | La pantalla que se queda con las teclas y el ratón durante una cinemática: las bandas negras, los subtítulos, el aviso para saltar |
| `DirectorVoice.kt` | Sus frases tras la escena: sonando planas, subtituladas sobre el HUD |

### `menu/`

| Archivo | Qué hace |
|---|---|
| `MenuScene.kt` | Las escenas detrás del menú principal, una por capítulo (la planta del aparcamiento, el laboratorio del prólogo con cámara a la deriva): las carga, las ilumina, las hace parpadear y las dibuja, con fundido por negro de una a otra |
| `MenuSceneView.kt` | Los bloques y la luz de la escena como un mundo para el renderizador de bloques |
| `RotatedModel.kt` | Un modelo cocinado girado por cuartos de vuelta (los bloques girados de la escena) |

### `screen/`

| Archivo | Qué hace |
|---|---|
| `MainMenuScreen.kt` | El menú principal (el logo de tiza en capas, con su easter egg de RUN y la línea del Chapter 2 debajo) |
| `AchievementsScreen.kt` | La pantalla ACHIEVEMENTS: la lista en papel |
| `Paper.kt` | Papel gastado (mosaico, manchas, bordes, tintas) para la guía y los logros |
| `GuideScreen.kt` | La guía del jugador: una hoja de papel con scroll y pestañas |
| `NoteScreen.kt` | Una nota leída en el papel de la guía sobre el juego, con los huecos rellenos |
| `SafeScreen.kt` | El dial de una caja fuerte, que se gira y se escucha |
| `SpoilerAlertScreen.kt` | La alerta de spoiler antes de jugar un Capítulo 2 bloqueado: JUGAR IGUAL o VOLVER |
| `EraseSaveScreen.kt` | El aviso antes de que Ctrl+Shift+D borre la partida guardada |
| `LoadingScreen.kt` | La pantalla de carga y `LoadingCover`, que la dibuja encima de todo |
| `LoadingTips.kt` | Consejos que van cambiando, con las teclas del jugador |
| `CongratulationsScreen.kt` | La ventana de felicitación sobre el menú principal tras los créditos: el Observer con su café, un agradecimiento, tiempo jugado y muertes |
| `FinalCreditsScreen.kt` | Los créditos finales, al ritmo de su canción: dibujos, títulos tecleados, herramientas, "Level 6 coming soon"; la música de la caída |
| `TerminalScreen.kt` | La pantalla de un ordenador del nivel 5, tecleada en verde sobre negro |
| `DeathScreen.kt` | La pantalla de muerte: "Killed by", quién te mató y un consejo sobre el negro al morir, y "Try again" |

### Mixins del cliente: `client/java/daio7771/everytunnel/mixin/client/`

Ver [Arquitectura](architecture.md#mixins) para el objetivo y el motivo de cada una.

## Chapter 2: `main/kotlin/daio7771/everytunnel/chapter2/` y `client/kotlin/daio7771/everytunnel/chapter2/`

Todo lo del Chapter 2, aparte del juego base (el Chapter 1 y lo que comparten todos los capítulos, arriba), que nunca lo nombra (`ChapterBoundaryTest`).
Tiene sus propios entrypoints (`Chapter2`, `Chapter2Client`) y solo llega al juego base por sus puntos de extensión: `PlayerRules`,
`ChapterRequest`, `ProgressService.onReset` y `CutsceneView.Kind`. Un capítulo posterior tendrá un paquete como este. Sus tests están en
`test/kotlin/daio7771/everytunnel/chapter2/`.

### El capítulo: `chapter2/`, `chapter2/core/`, `chapter2/platform/`

| Archivo | Qué hace |
|---|---|
| `Chapter2.kt` | Entrypoint común del Chapter 2 (tras el del juego base en `fabric.mod.json`): registra los registros del capítulo, su progreso, sus reglas, su arranque desde el menú, sus hooks y comandos |
| `core/Ch2PlayerProgress.kt` | Lo que ha hecho un jugador en el Chapter 2 (el estado de su Nivel 1, si lo juega, sus contadores), aparte del Chapter 1 |
| `platform/Ch2Progress.kt` | `Ch2PlayerProgress` sobre Cardinal Components, un componente propio (`every_tunnel:ch2_progress`), que se borra con una partida nueva del Chapter 1 |
| `platform/Ch2Blocks.kt` | Los bloques del Chapter 2 (los de la fábrica, los del banco del prólogo), en la misma pestaña creativa; el tipo de entidad de bloque propio de las cámaras de la fábrica |
| `platform/Ch2Items.kt` | Los objetos del Chapter 2: la escoba |
| `platform/Ch2Sounds.kt` | Los eventos de sonido del Chapter 2 que reproduce el código |
| `platform/Ch2Entities.kt` | Los tipos de entidad del Chapter 2: la gente de la fábrica, el científico del prólogo |

### El prólogo: `chapter2/prologue/` (`core/`, `platform/`)

| Archivo | Qué hace |
|---|---|
| `prologue/core/PrologueScene.kt` | El prólogo del Chapter 2: la línea de tiempo desde el científico (travelling, asentamiento, avance), las cuatro líneas, los subtítulos, el negro de apertura, los pulsos del parpadeo, las etapas de su cambio, el corte a negro y el título tecleado (`PrologueCues`, `PrologueStages`, `PrologueScenePlan`); las curvas del zumbido, el drone y el Geiger |
| `prologue/core/PrologueLines.kt` | Las líneas de voz del científico y sus subtítulos, leídas de `cutscene/prologue.json` |
| `prologue/core/RoomPlan.kt` | El plano de la sala del prólogo (leído de `cutscene/prologue_room.json`) y `BlockSpec`, un bloque con sus estados |
| `prologue/core/GeigerClicks.kt` | Los recuentos de un contador Geiger tick a tick: un proceso de Poisson con semilla a un ritmo que puede subir |
| `prologue/platform/PrologueSceneHooks.kt` | El prólogo del Chapter 2: construye su sala aparte, genera al científico, sujeta al jugador, el salto, el final (el jugador de vuelta donde estaba, la sala borrada); lo empieza `/everytunnel prologue` |
| `prologue/platform/PrologueCommand.kt` | `/everytunnel prologue` (mundos de desarrollo), unido al `/everytunnel` del juego base |
| `prologue/platform/ScientistEntity.kt` | El científico del prólogo (GeckoLib, criatura pasajera): trabaja en su mesa, tiembla, enferma, se estira y luego es un Pale man (`stage`, que pone la escena) |

### Nivel 1, lógica: `chapter2/level1/core/`

| Archivo | Qué hace |
|---|---|
| `level1/core/Ch2L1State.kt` | La máquina de estados del Nivel 1 del Chapter 2: sus diez estados, en el orden en que se juegan |
| `level1/core/Ch2L1Anchors.kt` | Los lugares con nombre del nivel, puntos y zonas, tal como los da `anchors.json` |
| `level1/core/Ch2L1AnchorsParser.kt` | Lee `anchors.json` y cuenta cada problema con su ruta JSON |
| `level1/core/Ch2L1Data.kt` | Los archivos de datos del nivel: cuáles hay, qué copia gana (la de la carpeta de config sobre la del jar), qué da uno que falta o está roto |
| `level1/core/Ch2L1Voices.kt` | Las líneas de voz del nivel (clave del texto, hablante, duración, fragmentos de subtítulo como rangos de frases, de `cutscene/ch2_l1_voices.json`), `Ch2L1Sentences` y `Ch2L1Skins`, el aspecto de sus personas |
| `level1/core/Ch2L1Tasks.kt` | El archivo de tareas (`tasks.json`), su lector, `Ch2L1Bits` (conjuntos de cosas como bits de un Int) y `HoldTracker` (un clic mantenido sobre un objetivo) |
| `level1/core/Ch2L1Speakers.kt` | El archivo de los altavoces (`speakers.json`), su lector y `Ch2L1SpeakerSchedule`: qué suena y cuándo, por las reglas del plan, sin reloj propio |
| `level1/core/Ch2L1IntroScene.kt` | La cinemática del primer aviso: tomas desde anclas, en la maquinaria de las otras escenas |
| `level1/core/Ch2L1Act2.kt` | El archivo del Acto 2 (`cutscenes/meeting.json`), su lector y `Ch2L1Follow`: el compañero guiando, esperando a un jugador que se queda atrás y recordándoselo |
| `level1/core/Ch2L1Act3.kt` | El archivo del Acto 3 (`act3.json`) y su lector: las cerraduras, el código de Rowan, los ordenadores y sus archivos, el objetivo y el destino de la flecha según los contadores del jugador |
| `level1/core/Ch2L1Patrols.kt` | El archivo de las patrullas (`patrols.json`) y su lector, `Ch2L1PatrolRunner` (la ronda de un guardia con sus paradas), `Ch2L1Watch` (qué ve y piensa un guardia: el «?», el aviso, la carrera, la captura) y `Ch2L1Stare` |
| `level1/core/Ch2L1Office.kt` | La cinemática del despacho: su archivo (`cutscenes/office.json`), su lector y `Ch2L1OfficeScene` (la vista fija en Rowan, su recorrido tick a tick, la puerta, el pase, el zumbido) |
| `level1/core/Ch2L1Meeting.kt` | La reunión como escena (`Ch2L1MeetingScene`: los ojos girando hacia cada hablante, la puerta, el tick de la tarjeta) y `Ch2L1Glances` (las miradas de los oyentes a la puerta) |
| `level1/core/Ch2L1Npcs.kt` | La gente de la fábrica: `npcs.json`, su lector, los pasos de una rutina con sus anclas buscadas y `Ch2L1RoutineRunner`, lo que hace una persona cada tick |
| `level1/core/Ch2L1NpcLife.kt` | Lo que hace que la gente de la fábrica parezca viva (`life` de `npcs.json`): `Ch2L1Gait` (andar y girar suave), `Ch2L1Gaze` (adónde mira la cabeza), `Ch2L1Chat` (charlas entre dos), `Ch2L1Pose` |
| `level1/core/Ch2L1Guide.kt` | Adónde apunta la flecha guía en cada paso (el reloj, los tubos, el terminal, el analizador, la escoba, la suciedad, lo siguiente del Acto 3), la más cercana, cuándo se oculta, cuánto espera en el Acto 3 |
| `level1/core/Ch2L1Wiring.kt` | Comprueba que lo que nombran `tasks.json`, `speakers.json`, `npcs.json`, `cutscenes/meeting.json` y `act3.json` está en `anchors.json` |
| `level1/core/Ch2L1Objectives.kt` | El objetivo que muestra el panel en cada estado: una clave de lang por estado, ninguna en las cinemáticas ni al final |
| `level1/core/Ch2L1MapPlan.kt` | El mapa del nivel como plan que construir: la caja que vaciar, las cajas de un bloque, las lámparas, qué dicen sus textos de pared, el origen, la huella del plan (de `map.json`) |

### Nivel 1, pegamento con Minecraft: `chapter2/level1/platform/`

| Archivo | Qué hace |
|---|---|
| `level1/platform/Ch2L1.kt` | El Nivel 1 del Chapter 2: el estado de cada jugador y si lo está jugando (las reglas le siguen: `enter`, `leave`), guardados en su progreso; todo cambio pasa por aquí y llega a los oyentes |
| `level1/platform/Ch2L1DataLoader.kt` | Lee los archivos JSON del nivel (la copia de la carpeta de config, si no la del jar) al arrancar y con `/et reload`, y guarda los datos actuales |
| `level1/platform/Ch2L1Commands.kt` | `/et reload` y `/et ch2 l1 enter\|leave\|clock\|build\|unbuild\|anchors\|goto\|state\|skip\|npc\|tasks\|act3\|patrols\|intro\|say` (solo ejecuciones de desarrollo) |
| `level1/platform/Ch2L1Build.kt` | Construye el nivel desde su plan a razón de 8.000 celdas por tick (y lo vacía), recuerda qué plan construyó, comprueba antes cada bloque |
| `level1/platform/TimeClockBlock.kt` | El reloj de fichar del Nivel 1 del Chapter 2: un lector de tarjetas (mismos modelos) que ficha al nuevo empleado, rojo y luego verde |
| `level1/platform/Ch2L1TaskBlocks.kt` | El tubo de muestra, el terminal de NonameOS, el analizador, la escoba apoyada y la suciedad de las tareas del Capítulo 2: cada uno pasa su uso a `Ch2L1TaskHooks` |
| `level1/platform/Ch2L1TaskHooks.kt` | Las tres tareas del Acto 1: los usos, los contadores en el progreso, los paquetes y los objetos del mundo siguiendo al estado |
| `level1/platform/Ch2L1SpeakerHooks.kt` | Los altavoces de Rowan: un planificador por jugador, el timbre y la voz desde el altavoz de techo de la zona |
| `level1/platform/Ch2L1IntroHooks.kt` | La cinemática del primer aviso en el servidor: sujeta al jugador, el timbre, el final o el salto |
| `level1/platform/Ch2L1Act2Hooks.kt` | El Acto 2 en el servidor: el compañero esperando y guiando, el grupo, la reunión dirigida tick a tick, la tarjeta, la salida de todos, el salto |
| `level1/platform/Ch2L1Act3Hooks.kt` | El Acto 3 en el servidor: el lector de tarjetas y la puerta del laboratorio auxiliar, los archivos de los ordenadores y el disparo del despacho, el teclado y la puerta del despacho, la entrada al despacho, las puertas que dicen que no, el Pale man de la cámara 02, el cierre al volver atrás |
| `level1/platform/Ch2L1Act3Blocks.kt` | El lector de tarjetas y el teclado (una cerradura junto a una puerta cerrada, roja y luego verde) y el retrato de Rowan: cada uno pasa su uso a `Ch2L1Act3Hooks` |
| `level1/platform/Ch2L1PatrolHooks.kt` | Las patrullas en el servidor: los guardias puestos y dirigidos en su ronda, qué hace sospechoso a un jugador, el aviso, la carrera, la captura y el jugador devuelto |
| `level1/platform/Ch2L1OfficeHooks.kt` | La cinemática del despacho en el servidor: el deslizamiento a la marca, Rowan movido por el recorrido de la escena, la puerta, el pase que se desliza, el salto, Rowan esperando junto a la puerta hasta que sales y lo que deja el despacho (el pase, la linterna, el LAB casi a oscuras, a6 una vez) |
| `level1/platform/Ch2L1Doors.kt` | Las puertas de la fábrica como las usa su gente: abiertas a su paso, un vano cerrado o abierto alrededor de un sitio |
| `level1/platform/FactoryNpcEntity.kt` | Una persona del Nivel 1 del Chapter 2 (Rowan, el compañero, los trabajadores): un solo tipo de entidad, su skin sincronizada las distingue; un trabajador sigue su rutina tick a tick (andar suave, a su ritmo), su cabeza mira sola, y su pose sincronizada dice si teclea, habla o escucha; una criatura mantenida |
| `level1/platform/Ch2L1Npcs.kt` | La gente de la fábrica: los trabajadores que se ponen mientras se juega el nivel y los del LAB que se quitan, las charlas entre parejas, los grupos y el `/et ch2 l1 npc` del desarrollador |

### Cliente: `client/kotlin/daio7771/everytunnel/chapter2/` (`prologue/client/`, `level1/client/`)

| Archivo | Qué hace |
|---|---|
| `Chapter2Client.kt` | Entrypoint del cliente del Chapter 2: sus escenas, HUD, flecha, NonameOS, renderers y capas de render |
| `prologue/client/PrologueView.kt` | El prólogo en el cliente: su `CutsceneView.Kind` (sonido, temblor, destello, final en negro) y sus paquetes |
| `prologue/client/PrologueSound.kt` | El sonido del prólogo: el zumbido y el drone (con el volumen siguiendo la escena), los clics Geiger posicionales del contador de radiación, el parpadeo, un crujido de huesos en cada etapa, el golpe y la respiración del Pale man, el golpe del título, todo muerto en el corte |
| `prologue/client/ScientistRenderer.kt` | Renderizador de GeckoLib del científico del prólogo, girado a su orientación; `ScientistModel` lo dibuja como el hombre, con su piel enferma o, ya cambiado, como el Pale man del nivel 2 (ojos brillantes) |
| `level1/client/Ch2L1Client.kt` | Lo que sabe el cliente del Nivel 1 del Chapter 2: si el jugador local está en él, su estado, el objetivo que mostrar (de su progreso) |
| `level1/client/Ch2L1IntroView.kt` | El primer anuncio del Nivel 1 en el cliente: su `CutsceneView.Kind` y sus paquetes |
| `level1/client/Ch2L1MeetingView.kt` | La reunión en el cliente: su `CutsceneView.Kind` (subtítulos frase a frase tras el nombre de cada hablante) y sus paquetes |
| `level1/client/Ch2L1Subtitles.kt` | Los subtítulos de las voces del Nivel 1 del Chapter 2, mostrados cuando suena una (un oyente del gestor de sonido; siempre activos) |
| `level1/client/ObjectivePanel.kt` | El panel de objetivos del Nivel 1 del Chapter 2: arriba a la derecha, pequeño, aparece con un fundido al cambiar el objetivo, con la cuenta de una tarea ("(1/3)") |
| `level1/client/HoldBar.kt` | La barra de una tarea que pide un clic mantenido (el analizador, la suciedad), bajo la mira |
| `level1/client/TaskArrow.kt` | La flecha guía del Nivel 1 del Chapter 2: una V azul con bordes blancos que gira sobre el lugar del paso actual, vista a través de los muebles |
| `level1/client/NonameOsScreen.kt` | NonameOS, el terminal de la fábrica (la tarea 1 del Nivel 1 del Chapter 2): el registro de muestras con sus botones REGISTER |
| `level1/client/NonameOsFilesScreen.kt` | NonameOS en los ordenadores del Acto 3: los archivos, NUEVO o LEÍDO, cada uno abierto, escrito y desplazable; cerrar uno avisa al servidor |
| `level1/client/NonameOsLook.kt` | El aspecto de NonameOS (los colores, el marco del monitor, la barra de título, un botón), compartido por sus ventanas |
| `level1/client/KeypadScreen.kt` | El teclado del despacho de cerca: el visor, doce teclas con clic o tecleando, DENEGADO o ABIERTO según el servidor |
| `level1/client/CaughtOverlay.kt` | Pillado por un guardia: el fundido a negro con su texto; la línea de un guardia como subtítulo |
| `level1/client/Ch2L1OfficeView.kt` | La cinemática del despacho en el cliente: su `CutsceneView.Kind` (los subtítulos de Rowan, el zumbido) y sus paquetes |
| `level1/client/FactoryNpcRenderer.kt` | Las personas de la fábrica sobre el modelo del jugador con brazos clásicos, cada una con su skin (`textures/entity/npc/`), brazos y cabeza según su pose (teclea, habla, escucha) |

## Recursos: `main/resources/`

| Ruta | Qué contiene |
|---|---|
| `fabric.mod.json` | Metadatos del mod, entrypoints, dependencias |
| `every_tunnel.mixins.json`, `client/resources/every_tunnel.client.mixins.json` | Listas de mixins |
| `assets/every_tunnel/lang/en_us.json` | Todos los textos del juego (los genera `tools/lang/build.py` desde `tools/lang/src/<parte>/`, una parte por capítulo) |
| `assets/every_tunnel/lang/es_*.json` | La traducción al español (`es_es.json`, unida como el inglés y copiada a los demás `es_*` por `tools/lang/build.py`) |
| `assets/every_tunnel/guide/` | No está en `src/`: lo copia de `docs/*/guide/` la compilación (`copyGuide`) |
| `assets/every_tunnel/sounds.json`, `sounds/` | Eventos de sonido y archivos `.ogg` (de `tools/sounds/export.py`) |
| `assets/every_tunnel/cutscene/prologue.json` | Las líneas de voz del científico: duración y subtítulos (de `tools/sounds/prologue_voices.py`) |
| `assets/every_tunnel/cutscene/prologue_room.json` | El laboratorio del prólogo: rellenos, bloques, luces, dónde están el científico y el jugador (de `tools/prologue/room.py`) |
| `assets/every_tunnel/cutscene/director.json` | Las líneas de voz del director: parte, duración y subtítulos (de `tools/sounds/voices.py`) |
| `assets/every_tunnel/cutscene/ch2_l1_voices.json` | Las líneas de voz del Nivel 1 del Chapter 2: clave del texto, hablante, duración y fragmentos de subtítulo (de `tools/sounds/ch2_l1_voices.py`) |
| `assets/every_tunnel/textures/entity/npc/` | Las skins de las personas de la fábrica: la de Rowan (del autor) y las de los trabajadores (de `tools/entity/workers.py`) |
| `assets/every_tunnel/textures/`, `models/`, `blockstates/` | De `tools/blocks`, `tools/entity`, `tools/gui` |
| `assets/every_tunnel/geo/`, `animations/` | Los archivos de GeckoLib del Pale man |
| `assets/every_tunnel/menu/`, `models/block/menu/`, `textures/block/menu/` | La escena del menú (de `tools/map/map_generator.py`) |
| `data/every_tunnel/loot_tables/` | Loot tables de los bloques |
| `data/every_tunnel/ch2/l1/` | Los valores por defecto de los datos del Nivel 1 del Chapter 2: `anchors.json` y `map.json` (los escribe `tools/map/ch2l1/generate.py`), `speakers.json`, `tasks.json`, `patrols.json`, `npcs.json`, `cutscenes/*.json` |

## Tests: `test/kotlin/daio7771/everytunnel/`

Ver [Depuración](debugging.md#tests-unitarios).
