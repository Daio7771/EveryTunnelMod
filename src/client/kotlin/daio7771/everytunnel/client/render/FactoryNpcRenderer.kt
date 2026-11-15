package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.ch2l1.Ch2L1Skins
import daio7771.everytunnel.platform.FactoryNpcEntity
import net.minecraft.client.render.entity.BipedEntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.util.Identifier

/**
 * The factory's people (Rowan, the coworker, the workers) on the player's model with classic 4 px arms, each with the
 * skin the entity says (`textures/entity/npc/<skin>.png`: Rowan's is the author's, the workers' are made by
 * `tools/entity/workers.py`). Only the skins' base layer is used.
 */
class FactoryNpcRenderer(context: EntityRendererFactory.Context) :
    BipedEntityRenderer<FactoryNpcEntity, BipedEntityModel<FactoryNpcEntity>>(
        context, BipedEntityModel(context.getPart(EntityModelLayers.PLAYER)), 0.5f,
    ) {

    override fun getTexture(entity: FactoryNpcEntity): Identifier = TEXTURES.getValue(Ch2L1Skins.orDefault(entity.skin))

    private companion object {
        val TEXTURES: Map<String, Identifier> = Ch2L1Skins.ALL.associateWith { Identifier(EveryTunnel.MOD_ID, Ch2L1Skins.texturePath(it)) }
    }
}
