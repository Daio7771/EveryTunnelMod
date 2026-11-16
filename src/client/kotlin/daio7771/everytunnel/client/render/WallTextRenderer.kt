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
 *   of a block high on an anthracite plate with a thin grey rim, as wide as the name needs;
 *   kept to one edge of its block when [WallTextBlockEntity.align] says so (beside a door).
 * - [WallTextBlockEntity.Style.BRASS]: the same plate in brass, dark letters.
 * - [WallTextBlockEntity.Style.LAB]: the same plate in Level 5's printed white plastic, black letters.
 * - [WallTextBlockEntity.Style.LETTERS]: big dark grey letters [LETTERS_HEIGHT] of a block high
 *   straight on the wall, a darker shadow under them for relief; they may run over several
 *   blocks either side ([rendersOutsideBoundingBox]), shrunk to fit if they would be wider than [LETTERS_MAX_WIDTH].
 * - [WallTextBlockEntity.Style.DIPLOMA] and [WallTextBlockEntity.Style.PLAQUE] ([multiline]): one line per line of the
 *   text, centred; a certificate on cream paper in a dark wood frame, its first line larger, a gold seal at its foot,
 *   or a small brass plaque, both as wide as their longest line needs.
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
        if (entity.style in MULTILINE) {
            multiline(entity, facing.asRotation(), matrices, vertexConsumers, light)
            return
        }
        val text = Text.translatable(entity.text)
        val width = font.getWidth(text).toFloat()
        val brass = entity.style == WallTextBlockEntity.Style.BRASS
        val lab = entity.style == WallTextBlockEntity.Style.LAB
        val plate = entity.style == WallTextBlockEntity.Style.PLATE || brass || lab
        val fullScale = (if (plate) PLATE_TEXT_HEIGHT else LETTERS_HEIGHT) / GLYPH_HEIGHT
        // Big lettering shrinks, all of it, rather than run past the wall behind it (a longer translation did).
        val scale = if (!plate && width * fullScale > LETTERS_MAX_WIDTH) LETTERS_MAX_WIDTH / width else fullScale
        // Kept to an edge: the whole thing (with the plate's margins) starts EDGE_GAP in from it.
        val halfWidth = (width / 2f + if (plate) PAD_X + RIM else 0f) * scale
        val shift = entity.align * (0.5f - EDGE_GAP - halfWidth)              // align -1: left end at -0.5 + EDGE_GAP

        matrices.push()
        matrices.translate(0.5, 0.5, 0.5)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()))   // local +z: the way it faces
        matrices.translate((entity.offset + shift).toDouble(), 0.0, -0.5 + WALL_GAP)       // against the wall behind (+x: the reader's right)
        matrices.scale(scale, -scale, scale)
        val x = -width / 2f
        val y = -GLYPH_HEIGHT / 2f
        if (plate) {
            val matrix = matrices.peek().positionMatrix
            val buffer = vertexConsumers.getBuffer(RenderLayer.getTextBackground())
            quad(buffer, matrix, x - PAD_X - RIM, y - PAD_Y - RIM, x + width + PAD_X + RIM, y + GLYPH_HEIGHT + PAD_Y + RIM, 0f, if (brass) BRASS_RIM else if (lab) LAB_RIM else RIM_COLOR, light)
            quad(buffer, matrix, x - PAD_X, y - PAD_Y, x + width + PAD_X, y + GLYPH_HEIGHT + PAD_Y, LAYER, if (brass) BRASS_COLOR else if (lab) LAB_COLOR else PLATE_COLOR, light)
            matrices.translate(0.0, 0.0, 2.0 * LAYER)
            font.draw(text, x, y + 1f, if (brass) BRASS_INK else if (lab) LAB_INK else PLATE_INK, false, matrices.peek().positionMatrix, vertexConsumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light)
        } else if (entity.style == WallTextBlockEntity.Style.LOGO) {
            // The company's logo: its blue letters on a white panel with a pale rim.
            val matrix = matrices.peek().positionMatrix
            val buffer = vertexConsumers.getBuffer(RenderLayer.getTextBackground())
            quad(buffer, matrix, x - LOGO_PAD_X - LOGO_RIM, y - LOGO_PAD_Y - LOGO_RIM, x + width + LOGO_PAD_X + LOGO_RIM,
                y + GLYPH_HEIGHT + LOGO_PAD_Y + LOGO_RIM, 0f, LOGO_RIM_COLOR, light)
            quad(buffer, matrix, x - LOGO_PAD_X, y - LOGO_PAD_Y, x + width + LOGO_PAD_X, y + GLYPH_HEIGHT + LOGO_PAD_Y, LAYER, LOGO_PANEL, light)
            matrices.translate(0.0, 0.0, 2.0 * LAYER)
            font.draw(text, x, y + 1f, LOGO_INK, false, matrices.peek().positionMatrix, vertexConsumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light)
        } else {
            font.draw(text, x, y, LETTERS_INK, true, matrices.peek().positionMatrix, vertexConsumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light)
        }
        matrices.pop()
    }

    /**
     * How a panel of several lines looks: its first line's height and the others', in blocks, the gap between them, the margin, the frame,
     * the narrowest half width, its colours, and whether it glows (drawn at full light, as a lit screen is).
     */
    private class Panel(
        val heading: Float, val line: Float, val gap: Float, val margin: Float, val frame: Float, val minHalfWidth: Float,
        val frameColor: Int, val paper: Int, val ink: Int, val headingInk: Int, val glows: Boolean = false,
    )

    /** A certificate, a plaque, a poster or a screen: its lines, in blocks, centred on the block (and [WallTextBlockEntity.offset]). */
    private fun multiline(entity: WallTextBlockEntity, rotation: Float, matrices: MatrixStack, vertexConsumers: VertexConsumerProvider, blockLight: Int) {
        val diploma = entity.style == WallTextBlockEntity.Style.DIPLOMA
        val panel = PANELS.getValue(entity.style)
        val light = if (panel.glows) FULL_BRIGHT else blockLight
        val lines = Text.translatable(entity.text).string.split("\n")
        val heights = lines.indices.map { if (it == 0) panel.heading else panel.line }
        val widths = lines.mapIndexed { i, line -> font.getWidth(line) * heights[i] / GLYPH_HEIGHT }
        val gap = panel.gap
        val margin = panel.margin
        val seal = if (diploma) DIPLOMA_SEAL + gap else 0f
        val halfWidth = maxOf(widths.max() / 2f + margin, panel.minHalfWidth)
        val textHeight = heights.sum() + gap * (lines.size - 1)
        val halfHeight = (textHeight + seal) / 2f + margin
        val frame = panel.frame

        matrices.push()
        matrices.translate(0.5, 0.5, 0.5)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-rotation))
        matrices.translate(entity.offset.toDouble(), 0.0, -0.5 + WALL_GAP)
        matrices.scale(1f, -1f, 1f)                                                     // y down, like the font's
        val buffer = vertexConsumers.getBuffer(RenderLayer.getTextBackground())
        val m = matrices.peek().positionMatrix
        quad(buffer, m, -halfWidth - frame, -halfHeight - frame, halfWidth + frame, halfHeight + frame, 0f, panel.frameColor, light)
        quad(buffer, m, -halfWidth, -halfHeight, halfWidth, halfHeight, BLOCK_LAYER, panel.paper, light)
        if (diploma) {
            val sx = halfWidth - margin - DIPLOMA_SEAL
            val sy = halfHeight - margin - DIPLOMA_SEAL
            quad(buffer, m, sx, sy, sx + DIPLOMA_SEAL, sy + DIPLOMA_SEAL, 2f * BLOCK_LAYER, DIPLOMA_SEAL_COLOR, light)
        }
        var y = -halfHeight + margin
        for ((i, line) in lines.withIndex()) {
            matrices.push()
            matrices.translate((-widths[i] / 2f).toDouble(), y.toDouble(), (3f * BLOCK_LAYER).toDouble())
            val s = heights[i] / GLYPH_HEIGHT
            matrices.scale(s, s, s)
            font.draw(line, 0f, 1f, if (i == 0) panel.headingInk else panel.ink, false, matrices.peek().positionMatrix, vertexConsumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light)
            matrices.pop()
            y += heights[i] + gap
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
        /** The widest big lettering may be (blocks): the wall of Level 3's stairs, where the only one is, is 8 wide. */
        const val LETTERS_MAX_WIDTH = 7.4f
        /** How far off the wall's face the text sits (blocks), so it never flickers into it. */
        const val WALL_GAP = 0.01
        /** How far in from the block's edge an aligned text starts (blocks). */
        const val EDGE_GAP = 0.12f
        /** The plate's margins and rim round the name, and the step between its layers (font units). */
        const val PAD_X = 4f
        const val PAD_Y = 3f
        const val RIM = 0.8f
        const val LAYER = 0.15f
        const val RIM_COLOR = 0xFF6A6E74.toInt()
        const val PLATE_COLOR = 0xFF1E2024.toInt()
        const val PLATE_INK = 0xFFE8E8E4.toInt()
        const val BRASS_RIM = 0xFF70542A.toInt()
        const val BRASS_COLOR = 0xFFB28C48.toInt()
        const val BRASS_INK = 0xFF261C12.toInt()
        const val LAB_RIM = 0xFFB4B8BC.toInt()
        const val LAB_COLOR = 0xFFF2F2EE.toInt()
        const val LAB_INK = 0xFF16181A.toInt()
        const val LETTERS_INK = 0xFF3A3D42.toInt()
        /** Certificates and plaques, in blocks: their lines' heights, the gaps between, margins, frame, seal. */
        const val DIPLOMA_HEADING = 0.075f
        const val DIPLOMA_LINE = 0.045f
        const val DIPLOMA_GAP = 0.035f
        const val DIPLOMA_MARGIN = 0.08f
        const val DIPLOMA_FRAME = 0.035f
        const val DIPLOMA_SEAL = 0.07f
        const val DIPLOMA_MIN_HALF_WIDTH = 0.3f
        const val PLAQUE_LINE = 0.05f
        const val PLAQUE_GAP = 0.025f
        const val PLAQUE_MARGIN = 0.04f
        const val PLAQUE_RIM = 0.012f
        /** The step between a certificate's layers, in blocks. */
        const val BLOCK_LAYER = 0.002f
        const val DIPLOMA_FRAME_COLOR = 0xFF3A2416.toInt()
        const val DIPLOMA_PAPER = 0xFFE8DEC4.toInt()
        const val DIPLOMA_INK = 0xFF2C2018.toInt()
        const val DIPLOMA_SEAL_COLOR = 0xFFC2963E.toInt()
        /** Chapter 2's factory: the company's blue (its logo, its posters) and its screens' glass and light. */
        const val LOGO_INK = 0xFF1F5A9E.toInt()
        const val LOGO_PANEL = 0xFFF6F7F8.toInt()
        const val LOGO_RIM_COLOR = 0xFFC8CDD2.toInt()
        /** The logo's panel round its letters, in font units. */
        const val LOGO_PAD_X = 4f
        const val LOGO_PAD_Y = 3f
        const val LOGO_RIM = 0.6f
        const val POSTER_BLUE = 0xFF1F5A9E.toInt()
        const val POSTER_RIM = 0xFFE8ECF0.toInt()
        const val POSTER_INK = 0xFFF4F6F8.toInt()
        const val POSTER_ACCENT = 0xFFFFD25A.toInt()
        const val SCREEN_GLASS = 0xFF0C1418.toInt()
        const val SCREEN_FRAME = 0xFF202428.toInt()
        const val SCREEN_INK = 0xFF9EE8F0.toInt()
        const val FULL_BRIGHT = 0xF000F0

        val MULTILINE = setOf(
            WallTextBlockEntity.Style.DIPLOMA, WallTextBlockEntity.Style.PLAQUE, WallTextBlockEntity.Style.POSTER, WallTextBlockEntity.Style.SCREEN,
        )
        val PANELS = mapOf(
            WallTextBlockEntity.Style.DIPLOMA to Panel(
                DIPLOMA_HEADING, DIPLOMA_LINE, DIPLOMA_GAP, DIPLOMA_MARGIN, DIPLOMA_FRAME, DIPLOMA_MIN_HALF_WIDTH,
                DIPLOMA_FRAME_COLOR, DIPLOMA_PAPER, DIPLOMA_INK, DIPLOMA_INK,
            ),
            WallTextBlockEntity.Style.PLAQUE to Panel(
                PLAQUE_LINE, PLAQUE_LINE, PLAQUE_GAP, PLAQUE_MARGIN, PLAQUE_RIM, 0f, BRASS_RIM, BRASS_COLOR, BRASS_INK, BRASS_INK,
            ),
            WallTextBlockEntity.Style.POSTER to Panel(0.13f, 0.07f, 0.05f, 0.1f, 0.025f, 0.45f, POSTER_RIM, POSTER_BLUE, POSTER_INK, POSTER_ACCENT),
            WallTextBlockEntity.Style.SCREEN to Panel(0.1f, 0.065f, 0.045f, 0.09f, 0.04f, 0.6f, SCREEN_FRAME, SCREEN_GLASS, SCREEN_INK, SCREEN_INK, glows = true),
        )
    }
}
