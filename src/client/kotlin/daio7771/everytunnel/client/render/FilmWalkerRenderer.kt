package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.FilmWalkerEntity
import net.minecraft.client.render.entity.BipedEntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.util.Identifier

/** The figure in the Cinema's film: the player's model, in his suit and mask (`tools/entity/film_walker.py`). */
class FilmWalkerRenderer(context: EntityRendererFactory.Context) :
    BipedEntityRenderer<FilmWalkerEntity, BipedEntityModel<FilmWalkerEntity>>(
        context, BipedEntityModel(context.getPart(EntityModelLayers.PLAYER)), 0.5f,
    ) {

    override fun getTexture(entity: FilmWalkerEntity): Identifier = TEXTURE

    private companion object {
        val TEXTURE = Identifier(EveryTunnel.MOD_ID, "textures/entity/film_walker.png")
    }
}
