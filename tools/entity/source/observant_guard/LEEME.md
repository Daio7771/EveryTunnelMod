# Observant Guard — corrupto

Nueva variante basada en la última referencia aportada: cabeza gris abierta por
una fractura escalonada, interior negro y un único punto blanco muy pequeño,
hundiéndose en la cavidad. Pajarita roja, traje oscuro y roturas asimétricas.
La cabeza es una carcasa de piezas con hueco real, no un cubo con un agujero pintado.
Se conserva la entrega anterior en otra carpeta.

## Abrir

- `editable/observant_guard_corrupto.bbmodel`: proyecto para Blockbench 5 o posterior con GeckoLib.
- `editable/observant_guard_corrupto_sin_plugin.bbmodel`: mismo modelo en Bedrock Entity, sin complemento.
- Ambos incorporan la textura y las animaciones. Los recursos de juego siguen GeckoLib 4.

## Escala y construcción

Altura en reposo: **40 unidades = 2,5 bloques**, con suelo Y=0 y coronilla Y=40.
Cabeza de 10,5 × 10,5 × 10,5 unidades; el torso se ha acortado ligeramente para
conservar la altura total. La cavidad se abre por delante y por parte del lateral
derecho del personaje. El punto está 1.85 unidades
detrás del plano frontal. No se añaden una boca, dientes ni un segundo ojo.

101 cuboides · 20 huesos · textura 256 × 256.
El tamaño de colisión se configura en el mod; usa altura 2,5 y escala de render 1,0
para conservar la dimensión prevista. Las animaciones pueden cambiar los límites visuales.

## Animaciones

| Nombre | Duración | Movimiento |
| --- | --- | --- |
| `animation.observant_guard_corrupto.idle` | 5,6 s, bucle | Quietud prolongada, punto que se desplaza levemente y sacudida breve de cabeza |
| `animation.observant_guard_corrupto.walk` | 1,4 s, bucle | Paso rígido y cabeza inclinada que mantiene su postura |
| `animation.observant_guard_corrupto.attack` | 1,15 s, una vez | Alcance brusco y asimétrico de brazos |

Reposo y caminar se eligen como alternativas en el controlador del mod.

## Recursos

Copia `assets` dentro de `src/main/resources` y adapta el espacio de nombres al ID de tu mod.

| Recurso | Identificador |
| --- | --- |
| Geometría | `observant_guard_corrupto:geo/observant_guard_corrupto.geo.json` |
| Textura | `observant_guard_corrupto:textures/entity/observant_guard_corrupto.png` |
| Animaciones | `observant_guard_corrupto:animations/observant_guard_corrupto.animation.json` |

También se incluye `textures/entity/observant_guard_corrupto_glowmask.png`, una máscara RGBA que
contiene únicamente los texeles del punto blanco. **La emisión en el juego requiere
configurar una capa emisiva en el renderizador del mod**; estos recursos por sí solos
no activan brillo ni bloom. El punto ya es blanco en la textura base. Las vistas
incluidas usan la textura base y no añaden un halo artificial.

## Vistas y comprobaciones

`preview/observant_guard_corrupto.png`: tres cuartos; `frontal.png`: frontal;
`detalle_cabeza.png`: abertura y profundidad; `vistas_y_escala.png`: tres vistas;
`idle.gif`, `walk.gif`, `attack.gif`: animaciones del mismo esqueleto exportado.

`validacion.json` comprueba escala, jerarquía, UV, textura incorporada, equivalencia
entre recursos de juego y editor, animaciones, profundidad real y visibilidad del
único punto blanco. **No se ha probado mediante el importador de Blockbench ni dentro
de Minecraft.** El paquete es un modelo con recursos, no un mod `.jar` instalable.

Regenerar desde la raíz del proyecto: `python3 tools/build_observant_guard_corrupto.py`.
Requiere Python 3, NumPy y Pillow; los tres scripts utilizados se incluyen en `generador/tools`.
La nueva imagen de referencia se conserva en `referencias`.
