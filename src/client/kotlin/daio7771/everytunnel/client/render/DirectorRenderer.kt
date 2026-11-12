package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.DirectorEntity
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import software.bernie.geckolib.model.DefaultedEntityGeoModel
import software.bernie.geckolib.renderer.GeoEntityRenderer

/**
 * Draws the director with GeckoLib (`geo/entity/director.geo.json`, `textures/entity/director.png`,
 * from `tools/entity/director.py`). Not a living thing, so it is turned to its yaw here.
 */
class DirectorRenderer(context: EntityRendererFactory.Context) :
    GeoEntityRenderer<DirectorEntity>(context, DefaultedEntityGeoModel(Identifier(EveryTunnel.MOD_ID, "director"))) {

    init {
        shadowRadius = 0.5f
    }

    override fun applyRotations(animatable: DirectorEntity, poseStack: MatrixStack, ageInTicks: Float, rotationYaw: Float, partialTick: Float) {
        super.applyRotations(animatable, poseStack, ageInTicks, animatable.yaw, partialTick)
    }
}
