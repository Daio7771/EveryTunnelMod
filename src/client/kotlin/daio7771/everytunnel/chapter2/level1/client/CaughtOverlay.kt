package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.chapter2.level1.platform.Ch2L1PatrolHooks
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.Util

/**
 * Caught by a guard of Chapter 2 Level 1's patrols ([Ch2L1PatrolHooks.CAUGHT_PACKET]): the screen fades to black (the server moves the
 * player while it is black, [Ch2L1PatrolHooks.FADE_IN_TICKS] in), "You've been caught." in the middle, then the black lifts. A guard's
 * line, voiced or not, comes as a subtitle ([Ch2L1PatrolHooks.WARN_PACKET], [Ch2L1Subtitles.showText]).
 */
object CaughtOverlay {

    private const val IN_MS = Ch2L1PatrolHooks.FADE_IN_TICKS * 50L
    private const val HOLD_MS = 1400L
    private const val OUT_MS = 900L

    private var startedMs = -1L

    fun register() {
        HudRenderCallback.EVENT.register { context, _ -> render(context) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> startedMs = -1L }
        ClientPlayNetworking.registerGlobalReceiver(Ch2L1PatrolHooks.CAUGHT_PACKET) { client, _, _, _ ->
            client.execute { startedMs = Util.getMeasuringTimeMs() }
        }
        ClientPlayNetworking.registerGlobalReceiver(Ch2L1PatrolHooks.WARN_PACKET) { client, _, buf, _ ->
            val speaker = buf.readString()
            val text = buf.readString()
            val ticks = buf.readVarInt()
            client.execute { Ch2L1Subtitles.showText(speaker, text, ticks * 50L) }
        }
    }

    private fun render(context: DrawContext) {
        if (startedMs < 0) return
        val t = Util.getMeasuringTimeMs() - startedMs
        val alpha = when {
            t < IN_MS -> t.toFloat() / IN_MS
            t < IN_MS + HOLD_MS -> 1f
            t < IN_MS + HOLD_MS + OUT_MS -> 1f - (t - IN_MS - HOLD_MS).toFloat() / OUT_MS
            else -> {
                startedMs = -1L
                return
            }
        }
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        context.fill(0, 0, context.scaledWindowWidth, context.scaledWindowHeight, a shl 24)
        if (a < 24) return
        val font = MinecraftClient.getInstance().textRenderer
        val text = Text.translatable("every_tunnel.ch2.l1.caught")
        context.drawText(font, text, (context.scaledWindowWidth - font.getWidth(text)) / 2, context.scaledWindowHeight / 2 - 4, (a shl 24) or 0xE8E8E8, false)
    }
}
