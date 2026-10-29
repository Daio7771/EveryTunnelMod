package daio7771.everytunnel.client.effects

import daio7771.everytunnel.platform.CollapseHooks
import daio7771.everytunnel.systems.CollapseSchedule
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.RotationAxis
import kotlin.math.sin

/**
 * The camera shaking with Level 3's tunnel collapse ([CollapseHooks.SHAKE_PACKET_ID]): the view
 * jolts round all three axes, a mix of uneven wobbles up to [MAX_DEGREES], as strong as
 * [CollapseSchedule.shake] says at that moment. Only the picture moves; where the player looks
 * does not. Applied by `GameRendererMixin` after the hurt tilt.
 */
object CameraShake {

    private const val MAX_DEGREES = 1.6f
    /** World time (ticks) the shake started, or null. */
    private var startedAt: Long? = null

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(CollapseHooks.SHAKE_PACKET_ID) { client, _, _, _ ->
            client.execute { startedAt = client.world?.time }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> startedAt = null }
    }

    @JvmStatic
    fun apply(matrices: MatrixStack, tickDelta: Float) {
        val start = startedAt ?: return
        val world = MinecraftClient.getInstance().world ?: return
        val t = (world.time - start) + tickDelta
        val strength = CollapseSchedule.shake(t)
        if (strength <= 0f) {
            if (t >= CollapseSchedule.LENGTH) startedAt = null
            return
        }
        val a = MAX_DEGREES * strength
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(a * (0.6f * sin(t * 1.9f) + 0.4f * sin(t * 4.3f + 1f))))
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(a * (0.5f * sin(t * 2.3f + 2f) + 0.5f * sin(t * 5.1f))))
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(a * 0.6f * sin(t * 3.7f + 0.5f)))
    }
}
