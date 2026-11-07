package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.LabFigureEntity
import net.minecraft.client.render.entity.BipedEntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.util.Identifier

/** The figure seen far off from Level 5's Lobby: the player's model, a dark shape (`tools/entity/figure.py`). */
class LabFigureRenderer(context: EntityRendererFactory.Context) :
    BipedEntityRenderer<LabFigureEntity, BipedEntityModel<LabFigureEntity>>(
        context, BipedEntityModel(context.getPart(EntityModelLayers.PLAYER)), 0f,
    ) {

    override fun getTexture(entity: LabFigureEntity): Identifier = TEXTURE

    private companion object {
        val TEXTURE = Identifier(EveryTunnel.MOD_ID, "textures/entity/lab_figure.png")
    }
}
