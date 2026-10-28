package daio7771.everytunnel.client.render

import daio7771.everytunnel.platform.BoxShelfBlock
import daio7771.everytunnel.platform.BoxShelfBlockEntity
import daio7771.everytunnel.platform.ModBlocks
import daio7771.everytunnel.platform.PropBlock
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Direction
import net.minecraft.util.math.RotationAxis

/**
 * The purple boxes on a [BoxShelfBlock]: each filled place draws the box's own block model
 * (`purple_box`), smaller ([SCALE]), standing on its board and facing out like the shelf, at full
 * brightness like the boxes lying about. Drawn from the shelf's foot, so it reaches over all six
 * parts ([rendersOutsideBoundingBox]).
 */
class BoxShelfRenderer(@Suppress("UNUSED_PARAMETER") context: BlockEntityRendererFactory.Context) : BlockEntityRenderer<BoxShelfBlockEntity> {

    override fun render(
        entity: BoxShelfBlockEntity, tickDelta: Float, matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider, light: Int, overlay: Int,
    ) {
        if (entity.count == 0) return
        val state = entity.cachedState
        if (!state.isOf(ModBlocks.BOX_SHELF)) return
        val facing = state.get(BoxShelfBlock.FACING)
        val foot = entity.pos
        val box = ModBlocks.PURPLE_BOX.defaultState.with(PropBlock.FACING, Direction.NORTH)
        val blocks = MinecraftClient.getInstance().blockRenderManager
        for (slot in 0 until BoxShelfBlock.SLOTS) {
            if (!entity.isFilled(slot)) continue
            val at = BoxShelfBlock.slotCentre(foot, facing, slot)
            matrices.push()
            matrices.translate(at.x - foot.x, at.y - foot.y, at.z - foot.z)
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - facing.asRotation()))
            matrices.scale(SCALE, SCALE, SCALE)
            matrices.translate(-0.5, 0.0, -0.5)
            blocks.renderBlockAsEntity(box, matrices, vertexConsumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay)
            matrices.pop()
        }
    }

    override fun rendersOutsideBoundingBox(blockEntity: BoxShelfBlockEntity): Boolean = true

    private companion object {
        /** 0.55 of the floor box: 7 pixels across, 6 high, so it fits between two boards. */
        const val SCALE = 0.55f
    }
}
