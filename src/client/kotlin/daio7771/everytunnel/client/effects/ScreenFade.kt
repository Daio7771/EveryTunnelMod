package daio7771.everytunnel.client.effects

import daio7771.everytunnel.platform.DeathHooks
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Util

/**
 * Black over the whole screen, faded in and out by the server (`platform.DeathHooks`:
 * dying fades to black, the restart at the level start fades back in). Drawn over the
 * HUD; fades in real time, so it keeps going while the game is paused.
 */
object ScreenFade {

    private var from = 0f
    private var to = 0f
    private var startMs = 0L
    private var durationMs = 0L

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(DeathHooks.FADE_PACKET_ID) { client, _, buf, _ ->
            val (darkness, seconds) = DeathHooks.readFade(buf)
            client.execute { fadeTo(darkness, seconds) }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { fadeTo(0f, 0f) } }
        HudRenderCallback.EVENT.register { context, _ -> render(context) }
    }

    private fun fadeTo(darkness: Float, seconds: Float) {
        from = current()
        to = darkness.coerceIn(0f, 1f)
        startMs = Util.getMeasuringTimeMs()
        durationMs = (seconds * 1000f).toLong().coerceAtLeast(0L)
    }

    private fun current(): Float {
        if (durationMs <= 0L) return to
        val t = ((Util.getMeasuringTimeMs() - startMs).toFloat() / durationMs).coerceIn(0f, 1f)
        return from + (to - from) * t
    }

    private fun render(context: DrawContext) {
        val darkness = current()
        if (darkness <= 0f) return
        val alpha = (darkness * 255f).toInt().coerceIn(0, 255)
        context.fill(0, 0, context.scaledWindowWidth, context.scaledWindowHeight, alpha shl 24)
    }
}
