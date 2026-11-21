package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.chapter2.level1.core.Ch2L1Tasks
import daio7771.everytunnel.chapter2.level1.platform.CentrifugeBlockEntity
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.common.platform.PropBlock
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.RotationAxis

/**
 * The centrifuge's display (the gauge's task, `Ch2L1LabTaskHooks`): the speed this player is to log ([Ch2L1Tasks.GAUGE_RPM], from their
 * progress), "#### RPM" in green on the dark window of its front, lit; dashes while there is none. Each player sees their own.
 */
class Ch2L1CentrifugeRenderer(context: BlockEntityRendererFactory.Context) : BlockEntityRenderer<CentrifugeBlockEntity> {

    private val font: TextRenderer = context.textRenderer

    override fun render(
        entity: CentrifugeBlockEntity, tickDelta: Float, matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider, light: Int, overlay: Int,
    ) {
        val player = MinecraftClient.getInstance().player ?: return
        val state = entity.cachedState
        if (!state.contains(PropBlock.FACING)) return
        val rpm = Ch2Progress.ch2L1Counter(player, Ch2L1Tasks.GAUGE_RPM)
        val text = if (rpm > 0) "$rpm RPM" else "---- RPM"
        val facing = state.get(PropBlock.FACING)
        matrices.push()
        matrices.translate(0.5, 0.5, 0.5)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()))   // local +z: the way its front looks
        matrices.translate(0.0, DISPLAY_Y, FRONT_Z)
        matrices.scale(SCALE, -SCALE, SCALE)
        font.draw(text, -font.getWidth(text) / 2f, -4f, INK, false, matrices.peek().positionMatrix, vertexConsumers,
            TextRenderer.TextLayerType.POLYGON_OFFSET, 0, FULL_BRIGHT)
        matrices.pop()
    }

    private companion object {
        /** The display's middle, from the block's centre: its window is pixels 9..11 from the top of the 8-pixel high front. */
        const val DISPLAY_Y = -0.18
        /** The front face (6 pixels in from the block's edge), and a hair in front of it. */
        const val FRONT_Z = 0.375 + 0.004
        const val SCALE = 0.0085f
        const val INK = 0xFF7CF29A.toInt()
        const val FULL_BRIGHT = 0xF000F0
    }
}
