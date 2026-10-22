package daio7771.everytunnel.client.screen

import daio7771.everytunnel.platform.EndingHooks
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.MessageScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.text.Text
import net.minecraft.util.Util

/**
 * "End of demo" on black, then the credits line by line (the music's CC BY credit among
 * them). Any key or click after [SKIP_AFTER_MS], Esc, or [AUTO_QUIT_MS] saves the world and
 * goes back to the title screen; entering the world again starts a new game
 * (`platform.EndingHooks`). Opened by the server at the end of Level 2.
 */
class EndOfDemoScreen : Screen(Text.translatable("screen.every_tunnel.end.title")) {

    private val openedAt = Util.getMeasuringTimeMs()
    private var quitting = false

    private fun elapsed(): Long = Util.getMeasuringTimeMs() - openedAt

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0xFF000000.toInt())
        val ms = elapsed()

        alpha(ms, 0L, 2000L)?.let { a ->
            context.matrices.push()
            context.matrices.scale(TITLE_SCALE, TITLE_SCALE, 1f)
            context.drawCenteredTextWithShadow(
                textRenderer, title, (width / 2f / TITLE_SCALE).toInt(), (height / 5f / TITLE_SCALE).toInt(), a shl 24 or 0xE0E0E0,
            )
            context.matrices.pop()
        }

        var y = height / 5 + 50
        CREDITS.forEachIndexed { i, key ->
            if (key == null) {
                y += 8
                return@forEachIndexed
            }
            alpha(ms, CREDITS_START_MS + i * CREDIT_LINE_MS, 1200L)?.let { a ->
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable(key), width / 2, y, a shl 24 or 0xA0A0A0)
            }
            y += 12
        }
        if (ms > SKIP_AFTER_MS) {
            alpha(ms, SKIP_AFTER_MS, 1000L)?.let { a ->
                context.drawCenteredTextWithShadow(
                    textRenderer, Text.translatable("screen.every_tunnel.end.continue"), width / 2, height - 20, a shl 24 or 0x606060,
                )
            }
        }
    }

    /** 0-255 fading in from [start] over [length] ms, or null while too faint to draw (text alpha under 4 draws opaque). */
    private fun alpha(ms: Long, start: Long, length: Long): Int? {
        val a = (((ms - start).toFloat() / length).coerceIn(0f, 1f) * 255f).toInt()
        return if (a < 8) null else a
    }

    override fun tick() {
        if (elapsed() > AUTO_QUIT_MS) quitToTitle()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (elapsed() > SKIP_AFTER_MS || keyCode == 256) quitToTitle()
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (elapsed() > SKIP_AFTER_MS) quitToTitle()
        return true
    }

    override fun close() = quitToTitle()

    override fun shouldPause(): Boolean = false

    /** Like the pause menu's "Save and Quit to Title". */
    private fun quitToTitle() {
        if (quitting) return
        quitting = true
        val client = client ?: MinecraftClient.getInstance()
        val singleplayer = client.isInSingleplayer
        client.world?.disconnect()
        if (singleplayer) client.disconnect(MessageScreen(Text.translatable("menu.savingLevel"))) else client.disconnect()
        client.setScreen(TitleScreen())
    }

    companion object {
        private const val TITLE_SCALE = 3f
        private const val CREDITS_START_MS = 2500L
        private const val CREDIT_LINE_MS = 500L
        private const val SKIP_AFTER_MS = 6000L
        private const val AUTO_QUIT_MS = 30000L

        /** Lang keys of the credit lines; null = a gap. */
        private val CREDITS = listOf(
            "screen.every_tunnel.end.game", "screen.every_tunnel.end.by", null,
            "screen.every_tunnel.end.music", "screen.every_tunnel.end.music_author",
            "screen.every_tunnel.end.music_license", "screen.every_tunnel.end.music_link", null,
            "screen.every_tunnel.end.sounds", null,
            "screen.every_tunnel.end.thanks",
        )

        fun register() {
            ClientPlayNetworking.registerGlobalReceiver(EndingHooks.ENDING_PACKET_ID) { client, _, _, _ ->
                client.execute { client.setScreen(EndOfDemoScreen()) }
            }
        }
    }
}
