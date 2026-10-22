package daio7771.everytunnel.client.render

import daio7771.everytunnel.platform.LootBlock
import daio7771.everytunnel.platform.LootBlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.util.math.RotationAxis

/** Draws a [LootBlock]'s item lying flat on whatever it rests on, turned by the block's facing. */
class LootRenderer(@Suppress("UNUSED_PARAMETER") context: BlockEntityRendererFactory.Context) : BlockEntityRenderer<LootBlockEntity> {

    override fun render(
        entity: LootBlockEntity, tickDelta: Float, matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider, light: Int, overlay: Int,
    ) {
        val state = entity.cachedState
        val block = state.block as? LootBlock ?: return
        val facing = state.get(LootBlock.FACING)
        matrices.push()
        matrices.translate(0.5, LIFT, 0.5)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()))
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f))
        matrices.scale(SCALE, SCALE, SCALE)
        MinecraftClient.getInstance().itemRenderer.renderItem(
            ItemStack(block.item), ModelTransformationMode.FIXED, light, overlay, matrices, vertexConsumers, entity.world, 0,
        )
        matrices.pop()
    }

    private companion object {
        /** Half the thickness of a flat item at [SCALE], so it rests on the surface. */
        const val LIFT = 0.02
        const val SCALE = 0.55f
    }
}
