# Stone Maw — modelo para Fabric 1.20.1 / GeckoLib 4

Adaptación cúbica de la criatura de piedra de la imagen de referencia: cabeza sin ojos, boca abierta con colmillos, torso de placas agrietadas, brazos largos y manos con cuatro dedos y pulgar. La parte posterior y las superficies ocultas se han diseñado a partir de la única vista suministrada.

## Abrir y editar

1. Abre Blockbench 5 o posterior e instala el complemento **GeckoLib** desde Archivo → Complementos.
2. Abre `editable/stone_maw.bbmodel`. La textura y las tres animaciones están incorporadas.
3. En Animar, selecciona `animation.stone_maw.idle`, `.walk` o `.attack`.

También se incluye `editable/stone_maw_sin_plugin.bbmodel`, con el formato integrado Bedrock Entity, para inspeccionar el mismo modelo sin instalar el complemento. El destino de los archivos de juego de este paquete es Java con GeckoLib 4.

## Escala y estructura

- Altura de la pose de reposo: **56 unidades / 16 = 3,5 bloques**; suelo en Y=0 y coronilla en Y=56.
- Anchura visual aproximada: 2,95 bloques, incluidas las manos; profundidad aproximada: 0,90 bloques.
- 111 cuboides, 27 huesos, 1.332 triángulos antes de las optimizaciones del renderizador.
- Textura pixel art opaca de 256 × 256, con UV por cara.
- Estructura: `root` → `pelvis` → `body` → cabeza/mandíbula y brazos; piernas desde la pelvis; antebrazos, manos, dedos, espinillas y pies separados.

La altura se refiere a la pose de reposo. Al animar la mandíbula y el cuerpo cambia la extensión de la geometría. El tamaño de la caja de colisión se configura en el código de la entidad.

## Incorporar los recursos al mod

Copia la carpeta `assets` dentro de `src/main/resources` de tu proyecto Fabric. El espacio de nombres de ejemplo es `stone_maw`; si tu mod tiene otro ID, renombra `assets/stone_maw` y utiliza ese mismo ID al crear los identificadores de recursos.

Rutas que debe devolver tu `GeoModel` con GeckoLib 4:

| Método | Identificador de recurso |
| --- | --- |
| `getModelResource` | `stone_maw:geo/stone_maw.geo.json` |
| `getTextureResource` | `stone_maw:textures/entity/stone_maw.png` |
| `getAnimationResource` | `stone_maw:animations/stone_maw.animation.json` |

Mantén la escala del renderizador en **1,0** para conservar los 3,5 bloques de altura. En el registro Fabric de la entidad, configura una altura de colisión de **3,5 bloques**. Puedes usar una anchura inicial de **1,6 bloques** para el cuerpo; si quieres que la colisión incluya todo el ancho de las manos, usa unos **2,96 bloques**. Es una decisión de jugabilidad: la anchura visual no fija automáticamente la colisión.

Asigna las animaciones desde los controladores de tu entidad:

| Nombre | Duración | Reproducción |
| --- | --- | --- |
| `animation.stone_maw.idle` | 3,0 s | Bucle |
| `animation.stone_maw.walk` | 1,2 s | Bucle |
| `animation.stone_maw.attack` | 0,95 s | Una vez |

Reposo y caminar son alternativas del controlador de movimiento. El ataque puede usar un controlador separado. El momento de impacto visual está alrededor de 0,55 s (11 ticks a 20 TPS); sincroniza el daño en el código de tu entidad. Los JSON no programan comportamiento, IA ni daño.

## Vistas y comprobaciones

- `preview/stone_maw.png`: vista de tres cuartos del modelo real con su textura.
- `preview/vistas_y_escala.png`: frontal, lateral y posterior, con escala vertical.
- `preview/ataque.gif`: muestra del movimiento de ataque, renderizada desde el mismo esqueleto.
- `validacion.json`: medición de altura, límites, recuentos y comprobaciones estructurales.

Se han comprobado dimensiones positivas, UV dentro de la textura, referencias de huesos, equivalencia entre la geometría editable y la exportada, y tiempos y signos de las animaciones. Las vistas se han renderizado con el generador incluido en el proyecto. **No se ha probado dentro de Minecraft ni mediante el importador de Blockbench.** El paquete contiene los recursos del modelo; para que aparezca en el juego hay que conectarlos a una entidad y un renderizador registrados en un mod Fabric con GeckoLib 4. No es un mod `.jar` instalable por sí solo.

## Referencias de formato

- [GeckoLib 4: rutas y GeoModel](https://github.com/bernie-g/geckolib/wiki/Geo-Models-%28Geckolib4%29)
- [GeckoLib: complemento de Blockbench](https://wiki.geckolib.com/docs/geckolib5/making-models/blockbench-plugin-usage/) — interfaz actual del editor; los recursos se han dispuesto para GeckoLib 4.
- [Blockbench: formato bbmodel](https://www.blockbench.net/wiki/docs/bbmodel/)

