package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Pose
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Skins
import daio7771.everytunnel.chapter2.level1.platform.FactoryNpcEntity
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.BipedEntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.model.ModelPart
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The factory's people (Rowan, the coworker, the workers) on the player's model with classic 4 px arms, each with the
 * skin the entity says (`textures/entity/npc/<skin>.png`: Rowan's is the author's, the workers' are made by
 * `tools/entity/chapter2/workers.py`). Only the skins' base layer is used. The arms follow the entity's [FactoryNpcEntity.npcPose]
 * ([FactoryNpcModel]): out in front at a keyboard, moving with the words when talking, hanging still when they do nothing with
 * them (a nod now and then and the weight shifting when listening); in the meeting, trembling, laughing, or held out handing
 * something over; Rowan in his office fixing his cuffs, leaning on the desk, palms open, bending forward, at his tie, pointing, clapping.
 * Where the head looks is the entity's own (the server's gaze); each person's movements are a little out of step with the rest's.
 * A patrolling guard who has noticed the player ([FactoryNpcEntity.alert]) has a yellow "?" over his head with a bar under it that
 * fills; once he has told them off, or runs at them, a red "!".
 */
class FactoryNpcRenderer(context: EntityRendererFactory.Context) :
    BipedEntityRenderer<FactoryNpcEntity, BipedEntityModel<FactoryNpcEntity>>(
        context, FactoryNpcModel(context.getPart(EntityModelLayers.PLAYER)), 0.5f,
    ) {

    override fun getTexture(entity: FactoryNpcEntity): Identifier = TEXTURES.getValue(Ch2L1Skins.orDefault(entity.skin))

    override fun render(entity: FactoryNpcEntity, yaw: Float, tickDelta: Float, matrices: MatrixStack, vertexConsumers: VertexConsumerProvider, light: Int) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        val alert = entity.alert
        if (alert == FactoryNpcEntity.ALERT_NONE) return
        matrices.push()
        matrices.translate(0.0, entity.height + 0.55, 0.0)
        matrices.multiply(dispatcher.rotation)
        matrices.scale(-0.04f, -0.04f, 0.04f)
        val matrix = matrices.peek().positionMatrix
        val font = textRenderer
        val full = LightmapTextureManager.MAX_LIGHT_COORDINATE
        if (alert > 100) {
            val mark = "!"
            font.draw(mark, -font.getWidth(mark) / 2f, -4f, RED, false, matrix, vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, full)
        } else {
            val mark = "?"
            font.draw(mark, -font.getWidth(mark) / 2f, -6f, YELLOW, false, matrix, vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, full)
            // The bar: a row of spaces drawn with a background, grey, and over it the filled part in yellow.
            val width = font.getWidth(BAR)
            font.draw(BAR, -width / 2f, 5f, 0, false, matrix, vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, BAR_EMPTY, full)
            val filled = BAR.take((BAR.length * alert / 100).coerceIn(0, BAR.length))
            if (filled.isNotEmpty()) {
                font.draw(filled, -width / 2f, 5f, 0, false, matrix, vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, BAR_FULL, full)
            }
        }
        matrices.pop()
    }

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
                    shiftWeight(t, phase)
                }
                Ch2L1Pose.TREMBLING -> {
                    // Arms drawn in, hands trembling, the head too, and small nervous gestures with the words.
                    val shake = sin(t * 2.6f + phase) * 0.05f
                    rightArm.pitch = -0.55f + sin(t * 0.3f + phase) * 0.15f + shake
                    leftArm.pitch = -0.45f - shake
                    rightArm.yaw = 0.35f
                    leftArm.yaw = -0.35f
                    head.pitch += sin(t * 3.1f + phase) * 0.03f
                    head.yaw += sin(t * 2.3f + phase) * 0.03f
                }
                Ch2L1Pose.LAUGHING -> {
                    // Hands on the belly, the head thrown back, the shoulders shaking.
                    val heave = sin(t * 1.7f + phase)
                    rightArm.pitch = -0.75f + heave * 0.06f
                    leftArm.pitch = -0.75f + heave * 0.06f
                    rightArm.yaw = 0.55f
                    leftArm.yaw = -0.55f
                    head.pitch = -0.3f + heave * 0.08f
                }
                Ch2L1Pose.OFFERING -> {
                    rightArm.pitch = -1.3f
                    rightArm.yaw = -0.1f
                    head.pitch += 0.1f
                }
                Ch2L1Pose.CUFFS -> {
                    // Fixing a cuff: the left hand at the right wrist in front of him, a small tug now and then.
                    val tug = sin(t * 0.4f + phase) * 0.06f
                    rightArm.pitch = -0.75f + tug
                    rightArm.yaw = 0.35f
                    leftArm.pitch = -0.95f - tug
                    leftArm.yaw = 0.55f
                }
                Ch2L1Pose.LEANING -> {
                    // Leaning back on the desk's edge, the hands on it behind him.
                    body.pitch = -0.1f
                    rightArm.pitch = 0.45f
                    leftArm.pitch = 0.45f
                    rightArm.roll = 0.18f
                    leftArm.roll = -0.18f
                }
                Ch2L1Pose.PALMS -> {
                    rightArm.pitch = -0.55f
                    leftArm.pitch = -0.55f
                    rightArm.yaw = -0.45f
                    leftArm.yaw = 0.45f
                    rightArm.roll = 0.35f
                    leftArm.roll = -0.35f
                }
                Ch2L1Pose.LEAN_FORWARD -> {
                    // Bent over the player, as a crouch does it.
                    body.pitch = 0.42f
                    body.pivotY = 3.0f
                    head.pivotY = 3.8f
                    rightArm.pivotY = 5.0f
                    leftArm.pivotY = 5.0f
                    rightArm.pitch = 0.25f
                    leftArm.pitch = 0.25f
                    rightLeg.pivotZ = 3.6f
                    leftLeg.pivotZ = 3.6f
                    rightLeg.pivotY = 12.2f
                    leftLeg.pivotY = 12.2f
                }
                Ch2L1Pose.TIE -> {
                    rightArm.pitch = -1.35f + sin(t * 0.25f + phase) * 0.12f
                    rightArm.yaw = 0.55f
                }
                Ch2L1Pose.POINTING -> {
                    rightArm.pitch = -1.5f
                    rightArm.yaw = -0.05f
                }
                Ch2L1Pose.CLAPPING -> {
                    // A slow clap: the hands meet in front of him every second or so.
                    val apart = (0.5f + 0.5f * cos(t * 0.35f + phase)) * 0.45f
                    rightArm.pitch = -1.05f
                    leftArm.pitch = -1.05f
                    rightArm.yaw = -0.05f - apart
                    leftArm.yaw = 0.05f + apart
                }
                Ch2L1Pose.NONE -> {}
            }
            if (entity.npcPose == Ch2L1Pose.NONE || entity.npcPose == Ch2L1Pose.LISTENING) armsDown(limbAngle, limbDistance)
            hat.copyTransform(head)
        }

        /** Doing nothing with their hands: the arms hang still, swinging only with the stride (not the vanilla idle sway). */
        private fun armsDown(limbAngle: Float, limbDistance: Float) {
            val swing = cos(limbAngle * 0.6662f) * limbDistance
            rightArm.pitch = -swing
            leftArm.pitch = swing
            rightArm.yaw = 0f
            leftArm.yaw = 0f
            rightArm.roll = 0f
            leftArm.roll = 0f
        }

        /** Standing a long while: the weight goes slowly from one foot to the other. */
        private fun shiftWeight(t: Float, phase: Float) {
            val sway = sin(t * 0.035f + phase) * 0.06f
            rightLeg.roll = sway
            leftLeg.roll = sway
            body.roll = -sway * 0.5f
        }
    }

    private companion object {
        const val YELLOW = 0xFFFFD040.toInt()
        const val RED = 0xFFFF4A3A.toInt()
        const val BAR = "        "
        const val BAR_EMPTY = 0x90202020.toInt()
        const val BAR_FULL = 0xFFFFD040.toInt()

        val TEXTURES: Map<String, Identifier> = Ch2L1Skins.ALL.associateWith { Identifier(EveryTunnel.MOD_ID, Ch2L1Skins.texturePath(it)) }
    }
}
