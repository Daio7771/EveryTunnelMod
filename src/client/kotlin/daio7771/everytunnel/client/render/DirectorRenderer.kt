package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.DirectorEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import software.bernie.geckolib.core.animation.AnimationState
import software.bernie.geckolib.model.DefaultedEntityGeoModel
import software.bernie.geckolib.renderer.GeoEntityRenderer
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Draws the director with GeckoLib (`geo/entity/director.geo.json`, `textures/entity/director.png`, his
 * animations; from `tools/entity/director.py`) through [DirectorModel]. Not a living thing, so it is turned to its
 * yaw here.
 */
class DirectorRenderer(context: EntityRendererFactory.Context) : GeoEntityRenderer<DirectorEntity>(context, DirectorModel()) {

    init {
        shadowRadius = 0.5f
    }

    override fun applyRotations(animatable: DirectorEntity, poseStack: MatrixStack, ageInTicks: Float, rotationYaw: Float, partialTick: Float) {
        super.applyRotations(animatable, poseStack, ageInTicks, animatable.yaw, partialTick)
    }
}

/**
 * The director's model, on top of his animations: his head (and his mask with it, while it is on his face)
 * turns to the player's eyes as much as his pose's gaze says, else a little down, at his desk ([LOOK_DOWN]);
 * while he takes the mask off its animation has both. Once the mask lies on the desk (its block there) it is
 * hidden here. Turns as GeckoLib's own head turning does (`DefaultedEntityGeoModel`).
 */
class DirectorModel : DefaultedEntityGeoModel<DirectorEntity>(Identifier(EveryTunnel.MOD_ID, "director")) {

    override fun setCustomAnimations(animatable: DirectorEntity, instanceId: Long, animationState: AnimationState<DirectorEntity>) {
        super.setCustomAnimations(animatable, instanceId, animationState)
        val pose = animatable.pose()
        val head = animationProcessor.getBone("head") ?: return
        val mask = animationProcessor.getBone("mask")
        mask?.isHidden = pose.maskOff
        if (pose.action?.name == "mask_off") return
        var yaw = 0.0
        var pitch = 0.0
        MinecraftClient.getInstance().player?.let { player ->
            val dx = player.x - animatable.x
            val dz = player.z - animatable.z
            val dy = player.eyeY - (animatable.y + EYES)
            yaw = MathHelper.wrapDegrees(Math.toDegrees(atan2(-dx, dz)) - animatable.yaw).coerceIn(-MAX_YAW, MAX_YAW)
            pitch = (-Math.toDegrees(atan2(dy, hypot(dx, dz)))).coerceIn(-MAX_PITCH, MAX_PITCH)
        }
        val w = pose.gaze
        val turnX = -Math.toRadians(MathHelper.lerp(w, LOOK_DOWN, pitch)).toFloat()
        val turnY = -Math.toRadians(yaw * w).toFloat()
        head.rotX = turnX
        head.rotY = turnY
        if (mask != null && !pose.maskOff) {
            mask.rotX = turnX
            mask.rotY = turnY
        }
    }

    companion object {
        /** His eyes over his feet, sitting. */
        private const val EYES = 1.7

        /** Looking away, this far down (degrees; the mask's animation starts and ends there). */
        private const val LOOK_DOWN = 12.0
        private const val MAX_YAW = 70.0
        private const val MAX_PITCH = 35.0
    }
}
