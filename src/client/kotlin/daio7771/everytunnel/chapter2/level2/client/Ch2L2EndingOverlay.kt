package daio7771.everytunnel.chapter2.level2.client

import daio7771.everytunnel.chapter2.level2.platform.Ch2L2Ending
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.MessageScreen
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.text.Text
import net.minecraft.util.Util

/**
 * The end of Chapter 2's Level 2 ([Ch2L2Ending.PACKET]), at the exit's door: the screen fades to black, the card (a lang key; a line
 * break makes two lines) fades in in its middle, large, and stays, and then the world is left for the main menu, saved.
 */
object Ch2L2EndingOverlay {

    private const val TEXT_IN_MS = 1200L
    private const val AFTER_MS = 800L
    private const val SCALE = 2.5f
    private const val LINE = 12

    private var startedMs = -1L
    private var fadeMs = 0L
    private var cardMs = 0L
    private var textKey = ""
    private var quitting = false

    fun register() {
        HudRenderCallback.EVENT.register { context, _ -> render(context) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> startedMs = -1L }
        ClientPlayNetworking.registerGlobalReceiver(Ch2L2Ending.PACKET) { client, _, buf, _ ->
            val fade = buf.readVarInt()
            val card = buf.readVarInt()
            val key = buf.readString()
            client.execute {
                fadeMs = fade * 50L
                cardMs = card * 50L
                textKey = key
                quitting = false
                startedMs = Util.getMeasuringTimeMs()
            }
        }
    }

    private fun render(context: DrawContext) {
        if (startedMs < 0) return
        val t = Util.getMeasuringTimeMs() - startedMs
        val black = (t.toFloat() / fadeMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        context.fill(0, 0, context.scaledWindowWidth, context.scaledWindowHeight, (black * 255).toInt() shl 24)
        val since = t - fadeMs
        if (since > 0) {
            val a = ((since.toFloat() / TEXT_IN_MS).coerceIn(0f, 1f) * 255).toInt()
            if (a >= 8) {
                val font = MinecraftClient.getInstance().textRenderer
                val lines = Text.translatable(textKey).string.split("\n")
                val matrices = context.matrices
                matrices.push()
                matrices.scale(SCALE, SCALE, 1f)
                var y = (context.scaledWindowHeight / SCALE / 2 - lines.size * LINE / 2).toInt()
                for (line in lines) {
                    context.drawText(font, line, ((context.scaledWindowWidth / SCALE - font.getWidth(line)) / 2).toInt(), y, (a shl 24) or 0xE8E8E8, false)
                    y += LINE
                }
                matrices.pop()
            }
        }
        if (since > cardMs + AFTER_MS && !quitting) {
            quitting = true
            MinecraftClient.getInstance().execute(::quitToTitle)
        }
    }

    /** Leaves the world (saved) for the main menu, as the end of Chapter 1's credits does. */
    private fun quitToTitle() {
        startedMs = -1L
        val client = MinecraftClient.getInstance()
        val singleplayer = client.isInSingleplayer
        client.world?.disconnect()
        if (singleplayer) client.disconnect(MessageScreen(Text.translatable("menu.savingLevel"))) else client.disconnect()
        client.setScreen(TitleScreen())
    }
}
