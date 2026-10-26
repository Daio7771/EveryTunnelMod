package daio7771.everytunnel.client.render

import daio7771.everytunnel.platform.PaleManEntity
import net.minecraft.client.render.entity.EntityRendererFactory
import software.bernie.geckolib.renderer.GeoEntityRenderer
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer

/**
 * Draws the Pale man with GeckoLib from `geo/entity/pale_man.geo.json`,
 * `animations/entity/pale_man.animation.json` and `textures/entity/pale_man.png`
 * (all made by `tools/entity/pale_man.py`), through [PaleManModel], which also bends him
 * into a stoop under low ceilings. His milky eyes glow faintly in the dark
 * (`pale_man_glowmask.png`, drawn at full brightness on top).
 */
class PaleManRenderer(context: EntityRendererFactory.Context) :
    GeoEntityRenderer<PaleManEntity>(context, PaleManModel()) {

    init {
        shadowRadius = 0.45f
        addRenderLayer(AutoGlowingGeoLayer(this))
    }
}
