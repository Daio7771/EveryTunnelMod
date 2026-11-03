package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.wall.WallFont
import daio7771.everytunnel.core.wall.WallMessageState
import daio7771.everytunnel.platform.ModBlocks
import daio7771.everytunnel.platform.WallMessageBlock
import daio7771.everytunnel.platform.WallMessageBlockEntity
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.resource.language.I18n
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RotationAxis
import net.minecraft.util.math.Vec3d
import kotlin.math.acos

/**
 * A [WallMessageBlockEntity] on the wall behind its block, in the painted font (`tools/gui/wall_font.py`),
 * black, lit like the wall. It is not asked when nobody can see it: what has happened to each message is kept
 * here by position ([states]), so that it survives its chunk being reloaded, until the player leaves the world.
 */
class WallMessageRenderer(context: BlockEntityRendererFactory.Context) : BlockEntityRenderer<WallMessageBlockEntity> {

    private val font: TextRenderer = context.textRenderer

    override fun render(
        entity: WallMessageBlockEntity, tickDelta: Float, matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider, light: Int, overlay: Int,
    ) {
        if (entity.text.isEmpty()) return
        val blockState = entity.cachedState
        if (!blockState.isOf(ModBlocks.WALL_MESSAGE)) return
        val pos = entity.pos.toImmutable()
        val state = states.getOrPut(pos) { Remembered(WallMessageState(entity.trigger, entity.once)) }
        val now = System.nanoTime()
        val seconds = ((now - state.lastNanos) / 1e9).toFloat().coerceIn(0f, 0.25f)
        state.lastNanos = now
        val player = MinecraftClient.getInstance().player ?: return
        val centre = Vec3d.ofCenter(pos)
        val toMessage = centre.subtract(player.eyePos)
        val distance = toMessage.length()
        val cosine = if (distance < 1e-3) 1.0 else toMessage.normalize().dotProduct(player.getRotationVec(tickDelta))
        state.message.update(distance, Math.toDegrees(acos(cosine.coerceIn(-1.0, 1.0))), seconds)

        val alpha = (state.message.opacity * 255).toInt()
        if (alpha < MIN_ALPHA) return
        val key = if (state.message.changed && entity.changedText.isNotEmpty()) entity.changedText else entity.text
        val seed = pos.hashCode()
        val lines = I18n.translate(key).split('\n').map { line ->
            Text.literal(WallFont.paint(line, seed)).setStyle(Style.EMPTY.withFont(FONT))
        }
        val width = lines.maxOf { font.getWidth(it) }.toFloat()
        val height = lines.size * LINE_UNITS

        val scale = entity.size / LINE_UNITS
        matrices.push()
        matrices.translate(0.5, 0.5, 0.5)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-blockState.get(WallMessageBlock.FACING).asRotation()))
        matrices.translate(0.0, 0.0, -0.5 + WALL_GAP)
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(entity.rotation))
        matrices.scale(scale, -scale, scale)
        if (entity.patch) {
            val matrix = matrices.peek().positionMatrix
            val buffer = vertexConsumers.getBuffer(RenderLayer.getTextBackground())
            val x0 = -width / 2f - PATCH_PAD
            val x1 = width / 2f + PATCH_PAD
            val y0 = -height / 2f - PATCH_PAD
            val y1 = height / 2f + PATCH_PAD
            buffer.vertex(matrix, x0, y0, 0f).color(PATCH_R, PATCH_G, PATCH_B, alpha).light(light).next()
            buffer.vertex(matrix, x0, y1, 0f).color(PATCH_R, PATCH_G, PATCH_B, alpha).light(light).next()
            buffer.vertex(matrix, x1, y1, 0f).color(PATCH_R, PATCH_G, PATCH_B, alpha).light(light).next()
            buffer.vertex(matrix, x1, y0, 0f).color(PATCH_R, PATCH_G, PATCH_B, alpha).light(light).next()
            matrices.translate(0.0, 0.0, TEXT_OVER_PATCH)
        }
        val colour = (alpha shl 24) or PAINT
        lines.forEachIndexed { i, line ->
            val x = -font.getWidth(line) / 2f
            val y = -height / 2f + i * LINE_UNITS
            font.draw(line, x, y, colour, false, matrices.peek().positionMatrix, vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light)
        }
        matrices.pop()
    }

    override fun rendersOutsideBoundingBox(blockEntity: WallMessageBlockEntity): Boolean = true

    private class Remembered(val message: WallMessageState, var lastNanos: Long = System.nanoTime())

    companion object {
        private val FONT = Identifier(EveryTunnel.MOD_ID, "wall_paint")
        private val states = HashMap<BlockPos, Remembered>()

        /** Font units a line takes: the font's height (`wall_font.py`). */
        private const val LINE_UNITS = 12f
        private const val WALL_GAP = 0.012
        private const val TEXT_OVER_PATCH = 0.3
        private const val PATCH_PAD = 3f
        private const val PATCH_R = 196
        private const val PATCH_G = 188
        private const val PATCH_B = 168
        private const val PAINT = 0x0E0C0C
        /** The font renderer takes an alpha this low for opaque. */
        private const val MIN_ALPHA = 5

        fun register() {
            ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { states.clear() } }
        }
    }
}
