package daio7771.everytunnel.chapter2.level2.client

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter2.level2.platform.ObservantGuardEntity
import daio7771.everytunnel.common.client.render.EyesGlowLayer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.util.Identifier
import software.bernie.geckolib.model.DefaultedEntityGeoModel
import software.bernie.geckolib.renderer.GeoEntityRenderer

/**
 * Draws Chapter 2 Level 2's Observant Guard with GeckoLib from `geo/entity/observant_guard.geo.json`,
 * `animations/entity/observant_guard.animation.json` and `textures/entity/observant_guard.png` (all made by
 * `tools/entity/chapter2/observant_guard.py` from the user's model), with the white point in its broken head glowing from
 * `observant_guard_glowmask.png` ([EyesGlowLayer]).
 */
class ObservantGuardRenderer(context: EntityRendererFactory.Context) :
    GeoEntityRenderer<ObservantGuardEntity>(context, DefaultedEntityGeoModel(Identifier(EveryTunnel.MOD_ID, "observant_guard"))) {

    init {
        shadowRadius = 0.5f
        addRenderLayer(EyesGlowLayer(this))
    }
}
