package daio7771.everytunnel.client.render

import daio7771.everytunnel.platform.ModBlocks
import daio7771.everytunnel.platform.WallTextBlock
import daio7771.everytunnel.platform.WallTextBlockEntity
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.math.RotationAxis
import org.joml.Matrix4f

/**
 * A [WallTextBlockEntity]'s text on the wall behind its block, in the game's font and language,
 * lit like the wall:
 *
 * - [WallTextBlockEntity.Style.PLATE]: a room's nameplate, white letters [PLATE_TEXT_HEIGHT]
 *   of a block high on an anthracite plate with a thin grey rim, as wide as the name needs.
 * - [WallTextBlockEntity.Style.LETTERS]: big dark grey letters [LETTERS_HEIGHT] of a block high
 *   straight on the wall, a darker shadow under them for relief; they may run over several
 *   blocks either side ([rendersOutsideBoundingBox]).
 */
class WallTextRenderer(context: BlockEntityRendererFactory.Context) : BlockEntityRenderer<WallTextBlockEntity> {

    private val font: TextRenderer = context.textRenderer

    override fun render(
        entity: WallTextBlockEntity, tickDelta: Float, matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider, light: Int, overlay: Int,
    ) {
        if (entity.text.isEmpty()) return
        val state = entity.cachedState
        if (!state.isOf(ModBlocks.WALL_TEXT)) return
        val facing = state.get(WallTextBlock.FACING)
        val text = Text.translatable(entity.text)
        val width = font.getWidth(text).toFloat()
        val plate = entity.style == WallTextBlockEntity.Style.PLATE
        val scale = (if (plate) PLATE_TEXT_HEIGHT else LETTERS_HEIGHT) / GLYPH_HEIGHT

        matrices.push()
        matrices.translate(0.5, 0.5, 0.5)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()))   // local +z: the way it faces
        matrices.translate(entity.offset.toDouble(), 0.0, -0.5 + WALL_GAP)                 // against the wall behind (+x: the reader's right)
        matrices.scale(scale, -scale, scale)
        val x = -width / 2f
        val y = -GLYPH_HEIGHT / 2f
        if (plate) {
            val matrix = matrices.peek().positionMatrix
            val buffer = vertexConsumers.getBuffer(RenderLayer.getTextBackground())
            quad(buffer, matrix, x - PAD_X - RIM, y - PAD_Y - RIM, x + width + PAD_X + RIM, y + GLYPH_HEIGHT + PAD_Y + RIM, 0f, RIM_COLOR, light)
            quad(buffer, matrix, x - PAD_X, y - PAD_Y, x + width + PAD_X, y + GLYPH_HEIGHT + PAD_Y, LAYER, PLATE_COLOR, light)
            matrices.translate(0.0, 0.0, 2.0 * LAYER)
            font.draw(text, x, y + 1f, PLATE_INK, false, matrices.peek().positionMatrix, vertexConsumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light)
        } else {
            font.draw(text, x, y, LETTERS_INK, true, matrices.peek().positionMatrix, vertexConsumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light)
        }
        matrices.pop()
    }

    /** A flat rectangle, [z] in front of the wall (local +z, the way the text faces); both windings, whatever the layer culls. */
    private fun quad(buffer: VertexConsumer, m: Matrix4f, x0: Float, y0: Float, x1: Float, y1: Float, z: Float, argb: Int, light: Int) {
        val a = argb ushr 24 and 255
        val r = argb ushr 16 and 255
        val g = argb ushr 8 and 255
        val b = argb and 255
        buffer.vertex(m, x0, y0, z).color(r, g, b, a).light(light).next()
        buffer.vertex(m, x0, y1, z).color(r, g, b, a).light(light).next()
        buffer.vertex(m, x1, y1, z).color(r, g, b, a).light(light).next()
        buffer.vertex(m, x1, y0, z).color(r, g, b, a).light(light).next()
        buffer.vertex(m, x1, y0, z).color(r, g, b, a).light(light).next()
        buffer.vertex(m, x1, y1, z).color(r, g, b, a).light(light).next()
        buffer.vertex(m, x0, y1, z).color(r, g, b, a).light(light).next()
        buffer.vertex(m, x0, y0, z).color(r, g, b, a).light(light).next()
    }

    override fun rendersOutsideBoundingBox(blockEntity: WallTextBlockEntity): Boolean = true

    private companion object {
        /** A glyph's height in font units. */
        const val GLYPH_HEIGHT = 8f
        const val PLATE_TEXT_HEIGHT = 0.16f
        const val LETTERS_HEIGHT = 0.6f
        /** How far off the wall's face the text sits (blocks), so it never flickers into it. */
        const val WALL_GAP = 0.01
        /** The plate's margins and rim round the name, and the step between its layers (font units). */
        const val PAD_X = 4f
        const val PAD_Y = 3f
        const val RIM = 0.8f
        const val LAYER = 0.15f
        const val RIM_COLOR = 0xFF6A6E74.toInt()
        const val PLATE_COLOR = 0xFF1E2024.toInt()
        const val PLATE_INK = 0xFFE8E8E4.toInt()
        const val LETTERS_INK = 0xFF3A3D42.toInt()
    }
}
