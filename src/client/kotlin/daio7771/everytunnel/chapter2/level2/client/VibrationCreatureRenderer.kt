package daio7771.everytunnel.chapter2.level2.client

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level2.platform.VibrationCreatureEntity
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.util.Identifier
import software.bernie.geckolib.model.DefaultedEntityGeoModel
import software.bernie.geckolib.renderer.GeoEntityRenderer

/**
 * Draws Chapter 2 Level 2's vibration creature with GeckoLib from `geo/entity/vibration_creature.geo.json`,
 * `animations/entity/vibration_creature.animation.json` and `textures/entity/vibration_creature.png` (all made by
 * `tools/entity/vibration_creature.py`). Nothing glows: it has no eyes.
 */
class VibrationCreatureRenderer(context: EntityRendererFactory.Context) :
    GeoEntityRenderer<VibrationCreatureEntity>(context, DefaultedEntityGeoModel(Identifier(EveryTunnel.MOD_ID, "vibration_creature"))) {

    init {
        shadowRadius = 1.0f
    }
}
