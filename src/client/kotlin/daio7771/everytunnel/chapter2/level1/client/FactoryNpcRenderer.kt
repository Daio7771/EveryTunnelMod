package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Pose
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Skins
import daio7771.everytunnel.chapter2.level1.platform.FactoryNpcEntity
import net.minecraft.client.render.entity.BipedEntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.model.ModelPart
import net.minecraft.util.Identifier
import kotlin.math.max
import kotlin.math.sin

/**
 * The factory's people (Rowan, the coworker, the workers) on the player's model with classic 4 px arms, each with the
 * skin the entity says (`textures/entity/npc/<skin>.png`: Rowan's is the author's, the workers' are made by
 * `tools/entity/workers.py`). Only the skins' base layer is used. The arms follow the entity's [FactoryNpcEntity.npcPose]
 * ([FactoryNpcModel]): out in front at a keyboard, moving with the words when talking, still with a nod now and then when listening.
 * Where the head looks is the entity's own (the server's gaze); each person's movements are a little out of step with the rest's.
 */
class FactoryNpcRenderer(context: EntityRendererFactory.Context) :
    BipedEntityRenderer<FactoryNpcEntity, BipedEntityModel<FactoryNpcEntity>>(
        context, FactoryNpcModel(context.getPart(EntityModelLayers.PLAYER)), 0.5f,
    ) {

    override fun getTexture(entity: FactoryNpcEntity): Identifier = TEXTURES.getValue(Ch2L1Skins.orDefault(entity.skin))

    /** The player's model, with the arms and head of what the entity is doing. */
    private class FactoryNpcModel(root: ModelPart) : BipedEntityModel<FactoryNpcEntity>(root) {
        override fun setAngles(entity: FactoryNpcEntity, limbAngle: Float, limbDistance: Float, animationProgress: Float, headYaw: Float, headPitch: Float) {
            super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch)
            val t = animationProgress
            val phase = (entity.id % 17) * 0.37f
            when (entity.npcPose) {
                Ch2L1Pose.TYPING -> {
                    rightArm.pitch = -1.15f + sin(t * 0.9f + phase) * 0.07f
                    leftArm.pitch = -1.15f + sin(t * 0.9f + phase + 1.7f) * 0.07f
                    rightArm.yaw = -0.12f
                    leftArm.yaw = 0.12f
                }
                Ch2L1Pose.TALKING -> {
                    val emphasis = 0.6f + 0.4f * sin(t * 0.05f + phase)
                    rightArm.pitch = -0.45f + sin(t * 0.22f + phase) * 0.3f * emphasis
                    rightArm.yaw = -0.2f + sin(t * 0.13f + phase) * 0.15f
                    leftArm.pitch = -0.25f + sin(t * 0.17f + phase + 2f) * 0.2f * emphasis
                    leftArm.yaw = 0.15f
                    head.pitch += sin(t * 0.3f + phase) * 0.05f
                }
                Ch2L1Pose.LISTENING -> {
                    val nod = max(0f, sin(t * 0.12f + phase))
                    head.pitch += nod * nod * nod * 0.25f
                }
                Ch2L1Pose.NONE -> {}
            }
            hat.copyTransform(head)
        }
    }

    private companion object {
        val TEXTURES: Map<String, Identifier> = Ch2L1Skins.ALL.associateWith { Identifier(EveryTunnel.MOD_ID, Ch2L1Skins.texturePath(it)) }
    }
}
