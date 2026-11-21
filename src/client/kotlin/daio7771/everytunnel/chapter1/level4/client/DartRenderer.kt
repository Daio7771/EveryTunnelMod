package daio7771.everytunnel.chapter1.level4.client

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter1.level4.platform.DartEntity
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.ProjectileEntityRenderer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier

/** A thrown dart: vanilla's arrow, smaller, with its own texture (tools/blocks/common/generate.py, `dart_entity_tex`). */
class DartRenderer(context: EntityRendererFactory.Context) : ProjectileEntityRenderer<DartEntity>(context) {

    override fun getTexture(entity: DartEntity): Identifier = TEXTURE

    override fun render(entity: DartEntity, yaw: Float, tickDelta: Float, matrices: MatrixStack, consumers: VertexConsumerProvider, light: Int) {
        matrices.push()
        matrices.scale(SCALE, SCALE, SCALE)
        super.render(entity, yaw, tickDelta, matrices, consumers, light)
        matrices.pop()
    }

    private companion object {
        val TEXTURE = Identifier(EveryTunnel.MOD_ID, "textures/entity/dart.png")
        const val SCALE = 0.6f
    }
}
