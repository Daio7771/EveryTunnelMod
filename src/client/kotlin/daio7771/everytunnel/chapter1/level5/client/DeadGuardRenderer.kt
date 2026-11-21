package daio7771.everytunnel.chapter1.level5.client

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter1.level5.platform.DeadGuardEntity
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import software.bernie.geckolib.model.DefaultedEntityGeoModel
import software.bernie.geckolib.renderer.GeoEntityRenderer

/**
 * Draws the guard before you with GeckoLib (`geo/entity/dead_guard.geo.json`, `textures/entity/dead_guard.png`,
 * from `tools/entity/chapter1/dead_guard.py`). Not a living thing, so it is turned to its yaw here.
 */
class DeadGuardRenderer(context: EntityRendererFactory.Context) :
    GeoEntityRenderer<DeadGuardEntity>(context, DefaultedEntityGeoModel(Identifier(EveryTunnel.MOD_ID, "dead_guard"))) {

    init {
        shadowRadius = 0.5f
    }

    override fun applyRotations(animatable: DeadGuardEntity, poseStack: MatrixStack, ageInTicks: Float, rotationYaw: Float, partialTick: Float) {
        super.applyRotations(animatable, poseStack, ageInTicks, animatable.yaw, partialTick)
    }
}
