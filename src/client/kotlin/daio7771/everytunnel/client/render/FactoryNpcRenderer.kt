package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.ch2l1.Ch2L1Skins
import daio7771.everytunnel.platform.FactoryNpcEntity
import net.minecraft.client.render.entity.BipedEntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.model.ModelPart
import net.minecraft.util.Identifier
import kotlin.math.sin

/**
 * The factory's people (Rowan, the coworker, the workers) on the player's model with classic 4 px arms, each with the
 * skin the entity says (`textures/entity/npc/<skin>.png`: Rowan's is the author's, the workers' are made by
 * `tools/entity/workers.py`). Only the skins' base layer is used. Someone typing at a station ([FactoryNpcEntity.typing]) has their arms out
 * in front of them, the hands moving a little ([FactoryNpcModel]).
 */
class FactoryNpcRenderer(context: EntityRendererFactory.Context) :
    BipedEntityRenderer<FactoryNpcEntity, BipedEntityModel<FactoryNpcEntity>>(
        context, FactoryNpcModel(context.getPart(EntityModelLayers.PLAYER)), 0.5f,
    ) {

    override fun getTexture(entity: FactoryNpcEntity): Identifier = TEXTURES.getValue(Ch2L1Skins.orDefault(entity.skin))

    /** The player's model, with the arms of someone at a keyboard when the entity is typing. */
    private class FactoryNpcModel(root: ModelPart) : BipedEntityModel<FactoryNpcEntity>(root) {
        override fun setAngles(entity: FactoryNpcEntity, limbAngle: Float, limbDistance: Float, animationProgress: Float, headYaw: Float, headPitch: Float) {
            super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch)
            if (!entity.typing) return
            rightArm.pitch = -1.15f + sin(animationProgress * 0.9f) * 0.07f
            leftArm.pitch = -1.15f + sin(animationProgress * 0.9f + 1.7f) * 0.07f
            rightArm.yaw = -0.12f
            leftArm.yaw = 0.12f
            head.pitch = 0.18f
        }
    }

    private companion object {
        val TEXTURES: Map<String, Identifier> = Ch2L1Skins.ALL.associateWith { Identifier(EveryTunnel.MOD_ID, Ch2L1Skins.texturePath(it)) }
    }
}
