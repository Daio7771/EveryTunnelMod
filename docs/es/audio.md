# Audio

*[English version](../en/audio.md) · [Todas las páginas](README.md)*

El diseño de sonido es el núcleo de Every Tunnel. El jugador sobrevive escuchando: los pies
y la respiración del Pale man, su propio latido, cuánto ruido hacen sus propios pasos.

## Reglas

- **Sintetizado o CC0.** Casi todos los sonidos se sintetizan en Python
  (`tools/sounds/catalog.py`). Unos pocos se cortan de grabaciones CC0
  (`tools/sounds/cc0/`, con sus fuentes en `SOURCES.md`).
- **Una excepción con crédito:** la canción del ascensor, "Local Forecast – Elevator" de
  Kevin MacLeod (CC BY 4.0). Su crédito debe viajar con el mod (`CREDITS.md`, el jar, los
  créditos finales, `fabric.mod.json`).
- **Nada de música con IA.** Los servicios gratuitos de música con IA solo permiten uso no
  comercial.
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
| Eventos del mapa | `block.elevator.ding`, `block.elevator.doors`, `block.shutter.close`, `block.card_reader.accept`, `block.card_reader.deny`, `block.valve.turn`, `block.valve.release`, `block.locker.door`, `block.hatch.open` | síntesis |
| Linterna y batería | `item.flashlight.on`, `.off`, `.dead`, `.flicker`, `item.battery.open`, `.close` | síntesis |
| Jugador | `player.heartbeat` (4 variaciones), `player.tinnitus`, `player.breathing_exhausted`, `player.drink` | síntesis / CC0 (respiración, tragos) |
| El Pale man | `entity.pale_man.step`, `.step_run`, `entity.pale_man.breathing` | síntesis / CC0 (respiración) |
| El Observer | `music.observer.approach`, `music.observer.chase` (sin pasos: lo delata su música); en la fase de los libros `event.observer.tick` / `tock` (el reloj de la biblioteca en la cabeza, más rápido cuanto más cerca) | síntesis |
| Música | `music.menu`, `music.elevator`, `music.tension.pulse`, `.rising`, `.strings`, `music.chase`, `music.stinger.heard` | síntesis / CC BY (ascensor) |
| Eventos | `event.death`, `event.level_transition`, `event.observer.alert` (provisional), `event.collapse` (el derrumbe del túnel del nivel 3) | síntesis |

## Quién reproduce qué

Lado del servidor (con posición en el mundo, lo oye todo el que esté cerca):

- Sonidos de bloques: materiales de los props (`platform.ModSoundGroups`), luces que cambian
  de modo, puertas, persianas, lectores de tarjetas, puertas de taquilla (`LockerSeatEntity`,
  `HidingHooks`).
- Los pasos, la respiración, el golpe musical y el acúfeno del Pale man (`PaleManEntity`).
- Los sonidos de linterna y batería en el jugador (`FlashlightHooks`), los tragos
  (`DrinkHooks`).
- Los pasos del final detrás del jugador (`EndingHooks`), el sonido de muerte (`DeathHooks`).

Lado del cliente (`client.effects`), por jugador y sin posición salvo que se indique:

| Clase | Qué reproduce | Control de volumen |
|---|---|---|
| `AmbiencePlayer` | El bucle del nivel (o de la zona; con fundido al cambiar) y sus sonidos aleatorios en puntos aleatorios alrededor del jugador (con posición) | Ambiente/Entorno |
| `FluorescentHum` | El zumbido de las 4 luces más cercanas a 10 bloques o menos, cada una con un tono un poco distinto (con posición) | Bloques |
| `HeartbeatEffect` | Un latido cada vez cerca del Pale man | Jugadores |
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

## El tema del menú

`music.menu`, sintetizado por `menu_piano.py` (compuesto por el usuario; `menu_theme` en
`catalog.py` lo llama): unos 70 s de piano de fieltro lento e inquietante en 3/4 a 54 bpm,
con rubato y dudas, dos pasadas sobre Dmaj7 - Dadd9 - Bbmaj7#11 - Gmaj7 y un final sin
resolver en re maj7 con mi bemol encima. Usa su propia semilla (7), así que coincide con el
original del compositor. No es un bucle: `MenuMusic` lo toca una vez, espera 15 s en
silencio y lo vuelve a tocar. Al salir de un mundo se recargan los recursos, lo que reinicia
el motor de sonido y lo corta; entonces vuelve a empezar al momento (`SoundSystemMixin`
avisa a `MenuMusic`). Se exporta con el pico a -2,9 dB, unos -16,8 LUFS como el tema
anterior.

## Hacer y cambiar sonidos

Ver [Herramientas Python](tools.md#sonidos) para el proceso y
[Recetas](how-to.md#añadir-un-sonido) para añadir uno.
