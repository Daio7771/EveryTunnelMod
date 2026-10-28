package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.ObserverEntity
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.util.Identifier
import software.bernie.geckolib.model.DefaultedEntityGeoModel
import software.bernie.geckolib.renderer.GeoEntityRenderer

/**
 * Draws the Observer with GeckoLib from `geo/entity/observer.geo.json`,
 * `animations/entity/observer.animation.json` and `textures/entity/observer.png` (all made by
 * `tools/entity/observer.py`). The model turns its `head` bone with the entity's head, so when
 * he stands looking round (the server sweeps his head) the blank head turns with it.
 */
class ObserverRenderer(context: EntityRendererFactory.Context) :
    GeoEntityRenderer<ObserverEntity>(context, DefaultedEntityGeoModel(Identifier(EveryTunnel.MOD_ID, "observer"), true)) {

    init {
        shadowRadius = 0.45f
    }
}
