package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.HunterEntity
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.util.Identifier
import software.bernie.geckolib.model.DefaultedEntityGeoModel
import software.bernie.geckolib.renderer.GeoEntityRenderer
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer

/**
 * Draws a Hunter with GeckoLib from `geo/entity/hunter.geo.json`, `animations/entity/hunter.animation.json`
 * and `textures/entity/hunter.png` (all made by `tools/entity/hunter.py`). The model turns its `head`
 * bone with the entity's head; its blank white eyes glow in the dark (`hunter_glowmask.png`).
 */
class HunterRenderer(context: EntityRendererFactory.Context) :
    GeoEntityRenderer<HunterEntity>(context, DefaultedEntityGeoModel(Identifier(EveryTunnel.MOD_ID, "hunter"), true)) {

    init {
        shadowRadius = 0.3f
        addRenderLayer(AutoGlowingGeoLayer(this))
    }
}
