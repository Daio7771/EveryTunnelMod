# Audio

*[English version](../en/audio.md) · [Todas las páginas](README.md)*

El diseño de sonido es el núcleo de Every Tunnel. El jugador sobrevive escuchando: los pies
y la respiración del Pale man, su propio latido, cuánto ruido hacen sus propios pasos.

## Reglas

- **Sintetizado o CC0.** Casi todos los sonidos se sintetizan en Python
  (`tools/sounds/catalog.py`, y los del Chapter 2 en `catalog_ch2.py`). Unos pocos se cortan de grabaciones CC0
  (`tools/sounds/cc0/`, con sus fuentes en `SOURCES.md`).
- **Una excepción con crédito:** la canción del ascensor, "Local Forecast – Elevator" de
  Kevin MacLeod (CC BY 4.0). Su crédito debe viajar con el mod (`CREDITS.md`, el jar, los
  créditos finales, `fabric.mod.json`).
- **Nada de audio de generadores de IA.** Los servicios gratuitos de música con IA solo
  permiten uso no comercial. La música escrita y sintetizada en código por las herramientas de
  Python (el tema del menú del Chapter 2, por ejemplo) no es eso, y está bien.
- **Una excepción con IA: las voces con IA.** Las voces de los personajes son de la biblioteca
  de ElevenLabs (plan de pago, no clonadas), generadas por el usuario y entregadas como
  archivos mono: hasta ahora la del director (sus líneas en la cinemática del nivel 5 y las
  frases que repite después) la del científico (el prólogo del Chapter 2) y las del Nivel 1 del Chapter 2: el compañero y los trabajadores de la unión, y Rowan.
  Ningún otro audio viene de un servicio de IA.
- **Archivos secos.** Los sonidos no llevan reverberación: Sound Physics Remastered añade la
  reverberación y el sonido amortiguado tras los muros dentro del juego. Las ejecuciones de
  desarrollo siempre lo incluyen, para juzgar los sonidos con él.
- **Con semilla.** Cada archivo generado sale de una semilla derivada de su id y su
  variación, así que el mismo código siempre da el mismo sonido.

## Eventos de sonido

Cada evento está declarado en `src/main/resources/assets/every_tunnel/sounds.json`, que
genera `tools/sounds/export.py` junto con los `.ogg` (las carpetas siguen el id:
`item.flashlight.on` → `sounds/item/flashlight/on_1.ogg`). Los que reproduce el código
también se registran en `platform.ModSounds`. Cualquiera se puede probar con
`/playsound every_tunnel:<id> master @s`.

| Grupo | Eventos | Origen |
|---|---|---|
| Ambiente | `ambient.garage.drone` (bucle), `ambient.drip`, `ambient.relay`, `ambient.rumble`, `ambient.sparks`, `ambient.vent` | síntesis |
| Ambiente del nivel 3 | `ambient.offices.room_tone` (bucle), `ambient.library.clock` (bucle), `ambient.offices.phone` (2-4 timbrazos) | síntesis |
| Fluorescentes | `block.fluorescent.hum` (bucle), `.flicker`, `.start`, `.off` | síntesis |
| Materiales (pasos e impactos de los props) | `block.cardboard`, `block.hollow_metal`, `block.metal_pipe`, `block.plastic`, `block.puddle`, `block.sheet_metal`, `block.wire_mesh`, `block.wood`, cada uno `.step` e `.impact` | síntesis |
| Eventos del mapa | `block.projector.run`, `block.elevator.ding`, `block.elevator.doors`, `block.shutter.close`, `block.card_reader.accept`, `block.card_reader.deny`, `block.valve.turn`, `block.valve.release`, `block.locker.door`, `block.hatch.open` | síntesis |
| Linterna y batería | `item.flashlight.on`, `.off`, `.dead`, `.flicker`, `item.battery.open`, `.close` | síntesis |
| Jugador | `player.heartbeat` (4 variaciones), `player.tinnitus`, `player.breathing_exhausted`, `player.drink` | síntesis / CC0 (respiración, tragos) |
| El Pale man | `entity.pale_man.step`, `.step_run`, `entity.pale_man.breathing` | síntesis / CC0 (respiración) |
| El Observer | `music.observer.approach`, `music.observer.chase` (sin pasos: lo delata su música); en la fase de los libros `event.observer.tick` / `tock` (el reloj de la biblioteca en la cabeza, más rápido cuanto más cerca) | síntesis |
| Música | `music.menu`, `music.menu.chapter_2`, `music.elevator`, `music.tension.pulse`, `.rising`, `.strings`, `music.chase`, `music.stinger.heard` | síntesis / CC BY (ascensor) |
| Eventos | `event.death`, `event.death.reveal` (al aparecer el texto de la pantalla de muerte), `event.level_transition`, `event.observer.alert` (provisional), `event.collapse` (el derrumbe del túnel del nivel 3) | síntesis |
| Nivel 5 | `block.airlock.close`, `block.airlock.open` (la esclusa y las puertas bloqueadas), `item.wrench.hit` (3 variaciones), `item.detector.click`, `.clicks`, `.crackle` (el detector, un contador Geiger: una cuenta, de dos a cuatro, de seis a doce en un tick; 6 variaciones cada uno), `event.lab.unlock` (la ruta sur al abrirse, a lo lejos), `block.fuse.insert`, `block.fuse.reject`, `block.fuse.power` (la caja de fusibles), `block.breaker.dead` (la palanca de las escaleras sin corriente), `block.mixer.fill`, `block.mixer.run`, `block.grate.dissolve`, `event.lab.alarm` (el puzle químico), `event.lab.sting` (al entrar al Lab hall), `block.terminal.key` (los ordenadores), `block.camera.servo` (la cámara de la antesala al girar, 3 variaciones) | síntesis |
| El laboratorio del nivel 5 | `ambient.lab.air` (bucle: la ventilación, el aire por los conductos, un ventilador, un rumor grave), `ambient.lab.compressor` (el compresor de una nevera en algún sitio, arrancando o parando), `ambient.lab.duct` (un conducto de chapa que cede y cruje, 4 variaciones); en las zonas de la capa 1 desde el Lobby, y en las de la capa 2 además con el teléfono lejano del nivel 3 (`ambient.offices.phone`) (`ZONE_AMBIENCE` de `LEVEL_5`). La trampilla del drenaje al abrirse es el `block.hatch.open` del nivel 3. Las luces de la capa 1, empotradas en el techo, no hacen ruido; las colgadas de la capa 2 zumban por su cuenta (`block.fluorescent.hum`). El juego del pasillo de entrada: `ambient.cloud_game.static` (bucle: una estática monótona, un siseo seco con un zumbido tonal y chasquidos), que suena sin venir de ningún sitio mientras su Cloud está cerca y sube de tono hacia el final (`DistortionEffect`) | síntesis |
| El drenaje del nivel 5 | `ambient.drain.water` (bucle: el agua por los canales, gorgoteos, un rumor grave), `ambient.drain.pumps` (bucle: las bombas de la Pump room, fuertes), `ambient.drain.creak` (una tubería que cruje a lo lejos, metal contra metal, 4 variaciones) y el `ambient.drip` del garaje; por zona (`ZONE_AMBIENCE` de `LEVEL_5`). La trampilla cerrándose sobre el jugador es la trampilla de hierro vanilla con pitch 0.6 | síntesis / vanilla |
| Los Hunters del nivel 5 | `entity.hunter.shriek` (empieza una persecución), `.attack`, `.death`, `.ambient` (3 variaciones cada uno) | síntesis |
| Las Distortions del nivel 5 | `entity.distortion_cloud.whisper` (bucle), `.appear`, `.kill`, `.leave`; `entity.distortion_junior.whisper` (bucle), `.stare` (bucle, el juego le sube el tono), `.die`. Los susurros son aire a través de formantes de vocales que cambian, sin voz | síntesis |
| El director del nivel 5 | `voice.director.boss_01` a `boss_20` (sus líneas en la cinemática, con los subtítulos de `cutscene/director.json`), `voice.director.loop_1` a `loop_6` (lo que repite después); planas, en Voz/Habla, desde `tools/sounds/voices.py` | ElevenLabs (ver arriba) |
| El prólogo del Chapter 2 | `voice.prologue_01` a `prologue_04` (las cuatro líneas del científico, subtituladas por `cutscene/prologue.json`; planas, bajo Voz/Habla; ElevenLabs, de `tools/sounds/prologue_voices.py`), `ambient.prologue.hum` (bucle: el zumbido de las luces, una nevera, la ventilación; plano, control Ambiente) y `music.prologue.drone` (bucle: un drone grave, dos tonos a un semitono batiendo; su volumen sube hasta el cambio), `event.prologue.crack` (3 variaciones: chasquidos secos de articulaciones, un golpe sordo, un crujido; uno en cada etapa de su cambio), `music.prologue.reveal` (el golpe del stinger del nivel 2, al volverse Pale man) y `event.prologue.title` (un golpe grave bajo el título tecleado), todos los reproduce `client.cutscene.PrologueSound`, que también pone los `block.fluorescent.flicker`, `.off` y `.start` del nivel 1 en los pulsos de las luces y el `entity.pale_man.breathing` del Pale man; los clics Geiger del contador de radiación son los `item.detector.click`, `.clicks`, `.crackle` del nivel 5, desde el contador de la mesa (posicionales, cada vez más rápidos) | voces: voces con IA, el resto sintético |
| Chapter 2, Nivel 1 | `voice.follow_01` a `follow_03` (el compañero pidiendo al jugador que le siga), `voice.meeting_01` a `meeting_15` (la reunión en las taquillas: el compañero, y los trabajadores A y B en las `_a` y `_b`), `voice.rowan_loyal_a1` a `a6` (Rowan por los altavoces) y `voice.rowan_office_c1` a `c8` (Rowan en su despacho): 32 archivos, un evento de sonido cada uno, subtitulados por `cutscene/ch2_l1_voices.json`; los `rowan_loyal_*` salen de los altavoces de techo en el Acto 1 (`Ch2L1SpeakerHooks`, con un filtro de megafonía que mete la herramienta, y se oyen en toda una zona: `attenuation_distance` 48) tras el timbre `event.ch2l1.ding`, las demás voces aún no suenan, y `client.hud.Ch2L1Subtitles` muestra los subtítulos de cualquiera que suene, `/playsound` incluido; ElevenLabs, de `tools/sounds/ch2_l1_voices.py` | voces: voces con IA |

## Quién reproduce qué

Lado del servidor (con posición en el mundo, lo oye todo el que esté cerca):

- Sonidos de bloques: materiales de los props (`platform.ModSoundGroups`), luces que cambian
  de modo, puertas, persianas, lectores de tarjetas, puertas de taquilla (`LockerSeatEntity`,
  `HidingHooks`).
- Los pasos, la respiración, el golpe musical y el acúfeno del Pale man (`PaleManEntity`).
- Los sonidos de linterna y batería en el jugador (`FlashlightHooks`), los tragos
  (`DrinkHooks`).
- El sonido de muerte (`DeathHooks`).
- El jazz del nivel 4, por sus altavoces de techo (`client.effects.LoungeMusic`, `LoungeMusicHooks`, `core.audio.LoungeMix`): un sonido en el altavoz más cercano, callado en Mantenimiento y durante la película, bajo en las Suites, apagado en la Calm room, con el pitch deslizándose entre 0,6 y 1,2 en el apagón. La fuente está en `tools/sounds/own/`.

Lado del cliente (`client.effects`), por jugador y sin posición salvo que se indique:

| Clase | Qué reproduce | Control de volumen |
|---|---|---|
| `AmbiencePlayer` | El bucle del nivel (o de la zona; con fundido al cambiar) y sus sonidos aleatorios en puntos aleatorios alrededor del jugador (con posición) | Ambiente/Entorno |
| `FluorescentHum` | El zumbido de las 4 luces más cercanas a 10 bloques o menos, cada una con un tono un poco distinto (con posición) | Bloques |
| `HeartbeatEffect` | Un latido cada vez cerca del Pale man | Jugadores |
| `DetectorEffect` | El detector del nivel 5 chasqueando como un contador Geiger, más cuentas al acercarse una Cloud, hasta crepitar (en la cabeza) | Jugadores |
| `DistortionEffect` | Los bucles de susurros de las Distortions, que las siguen; el de la Cloud más fuerte cuanto más se la mira, el zumbido del Junior más agudo cuanto más se le mira al ojo | Hostiles |
| `BreathingEffect` | Respiración fuerte agotado | Jugadores |
| `TensionMusic` | Las capas de música del Pale man y las del Observer (viniendo hacia ti, corriendo a por ti), con fundido cruzado | Música |
| `IntroMusic` | La canción del ascensor y luego su parada de cinta | Música |
| `MenuMusic` | El tema del menú y luego 15 s de silencio, solo en los menús | Música |

El ambiente lo envía el servidor (`platform.AmbienceHooks`) cada vez que cambia el del
jugador, el del nivel o el propio de una zona (nivel 3: el reloj de la biblioteca, el silencio
del búnker), y tras `/reload`; el cliente hace el bucle y la colocación aleatoria, que no
necesitan trabajo del servidor. Que los sonidos aleatorios caigan dentro de muros no
importa: Sound Physics los amortigua, y suena a algo detrás del muro.

La música de vanilla nunca suena (`MusicTrackerMixin`). Tras la pantalla de carga todo está
en silencio y entra con fundido junto a la imagen (`SoundSystemMixin`).

## Los temas del menú

`music.menu`, sintetizado por `menu_piano.py` (compuesto por el usuario; `menu_theme` en
`catalog.py` lo llama): unos 70 s de piano de fieltro lento e inquietante en 3/4 a 54 bpm,
con rubato y dudas, dos pasadas sobre Dmaj7 - Dadd9 - Bbmaj7#11 - Gmaj7 y un final sin
resolver en re maj7 con mi bemol encima. Usa su propia semilla (7), así que coincide con el
original del compositor. No es un bucle: `MenuMusic` lo toca una vez, espera 15 s en
silencio y lo vuelve a tocar. Al salir de un mundo se recargan los recursos, lo que reinicia
el motor de sonido y lo corta; entonces vuelve a empezar al momento (`SoundSystemMixin`
avisa a `MenuMusic`). Se exporta con el pico a -2,9 dB, unos -16,8 LUFS como el tema
anterior.

`music.menu.chapter_2`, escrito en código (`menu_theme_ch2.py`; `menu_theme_chapter_2` en
`catalog.py` lo llama): un vals lento en re menor para un piano vertical desafinado y un violín
solo, en un disco antiguo gastado, de unos 77 s. La forma sigue el tema de menú de dibujos de los
años 30 que señaló el usuario (violín en tono menor, una frase de 20 s dicha dos veces) y las
notas son nuestras, en tres partes. A (6 compases, 3/4 a 54 bpm sobre Dm | Dm | Gm | A7 | Dm |
A7): el piano toca la melodía en octavas sobre el vals (bajo en el 1, acorde en el 2 y 3, un poco
tarde, con el pedal cambiado cada compás) y rueda el último acorde; el violín solo la dobla muy
suave. B (8 compases, el violín se va): el piano solo, más suave, con el vals aflojándose (el
tempo se frena hacia la mitad y se recupera, acordes arrastrados, un tiempo vacío) por un camino
que nunca aterriza donde apunta, con acordes prestados y notas a un semitono: tras el A7 llega
si bemol mayor, no re menor; la melodía se apoya en un la bemol sobre sol menor; fa menor; mi
bemol mayor sobre un re en el bajo; un re menor suave; sol *mayor* con un si natural donde antes
había si bemol; un A7sus4 cuyo re cae al do sostenido; y el A7 rodado hacia arriba hacia la
vuelta. A' (6 compases): vuelve el violín al frente, la línea una octava más arriba y más
fuerte, y el último compás va a si bemol mayor en vez de a casa con la melodía sosteniendo el
re, así que no resuelve. El piano es el SoundFont CC0 "Old Piano FB" con FluidSynth
(`sampler.Part`); el violín es el Solo Violin CC0 de VSCO 2 CE, tocado por
`sampler.SamplePlayer` (la nota grabada más cercana, desplazada, con las tomas suave y fuerte
mezcladas según la velocidad). El acabado es sintético: una sala pequeña y oscura, wow y
flutter, un sonido de banda limitada y algo saturado, siseo, crujido y algún chasquido.
`MenuMusic` lo toca como el del Chapter 1 (una vez, 15 s de silencio, otra vez) en el menú del
Chapter 2; pico a -5,2 dB, unos -16,8 LUFS. Solo se distribuye el `.ogg` renderizado; las
muestras (`tools/sounds/soundfonts/`, fuentes y licencias en su `SOURCES.md`) no.

## Hacer y cambiar sonidos

Ver [Herramientas Python](tools.md#sonidos) para el proceso y
[Recetas](how-to.md#añadir-un-sonido) para añadir uno.
