package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.ScientistEntity
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import software.bernie.geckolib.model.DefaultedEntityGeoModel
import software.bernie.geckolib.renderer.GeoEntityRenderer

/**
 * Draws the prologue's scientist with GeckoLib (`geo/entity/scientist.geo.json`, `textures/entity/scientist.png`,
 * `animations/entity/scientist.animation.json`; from `tools/entity/scientist.py`). Not a living thing, so it is
 * turned to its yaw here.
 */
class ScientistRenderer(context: EntityRendererFactory.Context) :
    GeoEntityRenderer<ScientistEntity>(context, DefaultedEntityGeoModel(Identifier(EveryTunnel.MOD_ID, "scientist"))) {

    init {
        shadowRadius = 0.4f
    }

    override fun applyRotations(animatable: ScientistEntity, poseStack: MatrixStack, ageInTicks: Float, rotationYaw: Float, partialTick: Float) {
        super.applyRotations(animatable, poseStack, ageInTicks, animatable.yaw, partialTick)
    }
}
