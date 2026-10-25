# Gráficos

*[English version](../en/graphics.md) · [Todas las páginas](README.md)*

El mod se ve bien en dos modos: Minecraft normal (con o sin Sodium) y con un shader pack a
través de Iris. Detecta cuál se usa **en vivo** (`client.ShaderMode`, con la API pública de
Iris por reflexión, porque Iris es opcional), así que activar o desactivar los shaders
mientras se juega cambia al momento.

## La linterna

La forma del haz es matemática pura, compartida por los dos modos:
`systems.FlashlightBeam`.

- Un cono desde los ojos del jugador en la dirección en que mira, de `beam_width_degrees`
  de ancho (40) y con un alcance de `beam_range` (20 bloques).
- La luz baja de `beam_brightness` (15) en la lente a 0 en el extremo, y cae unos niveles
  por bloque fuera del cono, así que el borde es suave.
- Se detiene justo detrás del primer bloque que toca, así que ilumina el muro de delante y
  no la sala de detrás.
- Un brillo tenue (`glow_brightness`, 5) alrededor del jugador.

`client.effects.FlashlightEffect` recalcula el haz cada tick del cliente mientras el
servidor dice que la linterna está encendida, incluido el parpadeo de batería baja.
`client.compat.LambDynamicLightsCompat` lo convierte en **luz de bloque** a través de
LambDynamicLights (una dependencia obligatoria; sin ella la linterna no iluminaría nada y
se avisa al jugador una vez).

### Sin shaders

La luz de bloque se ve a escalones, y cada cambio reconstruye secciones de chunk. Por eso:

- La luz se recalcula como mucho 5 veces por segundo, y solo con cambios grandes (menos
  reconstrucciones de chunks, sin tirones).
- `client.hud.FlashlightOverlay` oscurece la pantalla alrededor de un foco redondo en el
  centro, un poco cálido en su corazón (`flashlight_spot.png`, `flashlight_glow.png`, de
  `tools/gui/flashlight_spot.py`). El foco se queda atrás en los giros rápidos de cámara,
  como una luz en la mano, y luego vuelve; sigue el parpadeo y la atenuación.

### Con shaders

El pack suaviza la luz de bloque, así que el haz se moldea como una linterna
(`FlashlightBeam.create(forShaders = true)`): un cono más estrecho, un punto caliente más
brillante en el centro y menos brillo alrededor del jugador. El foco en pantalla se apaga.

## Ajustes de shaders recomendados

Ajustado para **Complementary Reimagined** (Unbound también sirve). `client.ShaderSettings`
los escribe en cada pack Complementary de `shaderpacks/` al arrancar el cliente, una vez
por pack y por versión de estos ajustes (se recuerda en
`config/every_tunnel/shader_settings_applied.txt`):

- **Colored lighting desactivado.** Ilumina el mundo con la lista de bloques luminosos del
  propio pack, que no tiene ninguno del mod, así que los fluorescentes y la linterna no
  iluminarían nada.
- **World Space Reflections desactivado.** El pack se niega a usarlas sin colored lighting y
  muestra una pantalla de error.

Lo que el jugador cambie después se respeta, hasta que cambien los propios ajustes
recomendados. Para que se vean los mapas de materiales, pon "RP Support" en "labPBR" en el
pack.

Otros shader packs pueden necesitar sus propios ajustes; solo se gestiona Complementary.

## Brillo

`LightmapTextureManagerMixin`: en los niveles la opción de Brillo cuenta como **Moody**,
esté como esté, para que la oscuridad sea oscura en cualquier pantalla. La opción en sí no
se cambia nunca; fuera de los niveles funciona como siempre.

## Luces

`platform.FluorescentLightBlock`: una pantalla plana de dos tubos en cualquier cara.

- Modo `on`: nivel de luz **10** (no 15: a 15 el aparcamiento quedaba iluminado por igual,
  sin charcos de luz, y la linterna, limitada a 15 como toda luz de bloque, apenas se
  notaba). Y un zumbido.
- Modo `flicker`: casi siempre encendida, con ráfagas de apagado y encendido rápido cada
  pocos segundos; la luz se apaga de verdad, así que la sala parpadea.
- Modo `off`: oscura y silenciosa.
- En Creativo, clic derecho cambia de modo (agachado para colocar un bloque contra ella).

Algunos props brillan un poco también: el cartel EXIT (7), el monitor CCTV (4), los
indicadores de planta, los paneles de botones y los lectores de tarjetas (1 a 3). La
tarjeta apaga todas las luces del Nivel 2 (`KeycardHooks`).

## Texturas y modelos

- **Pixel art de 16×16** en todo, generado en Python con semillas (ver
  [Herramientas Python](tools.md)).
- **Mapas LabPBR 1.3** junto a cada textura de bloque: `_n` (normal, AO, altura) y `_s`
  (suavidad, F0/metal, porosidad, emisión), que leen los shader packs. Ver
  `tools/map/garage/pbr.py`.
- **El aparcamiento en sí** usa bloques vanilla retexturizados: el resource pack del mundo
  (`resources.zip`) sobrescribe los blockstates de una lista de bloques sustitutos y los
  apunta a sus propios modelos en el namespace `etg`. Nunca reemplaza un archivo de textura
  de vanilla, y solo se aplica en ese mundo.
- **Los bloques propios del mod** (props, puertas, luces, carteles) tienen sus recursos en
  el jar del mod.
- **`SignTileBlock`** solo conoce ranuras numeradas; el resource pack del mundo asigna a
  cada (orientación, ranura) el texto pintado de ese nivel.
- **El Pale man** es un modelo de GeckoLib con una máscara de brillo para los ojos.

## Dirección de arte

- Una paleta fría y enfermiza: hormigón frío y gastado, manchas de óxido, aceite, humedad y
  moho.
- Un HUD al estilo Minecraft (tamaños de iconos de vanilla, con contorno, iluminados desde
  arriba a la izquierda).
- El único efecto de pantalla es la viñeta de agotamiento: sin niebla, grano ni desenfoque.
- El jugador es el vigilante nocturno: uniforme negro con detalles amarillos.
- Créditos como de película: subida lenta sobre negro.
- El menú principal: negro, un logo serif blanco y gastado, minimalista.
