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
 * The end of the demo, like a film's: "End of demo" fades in on black, then the credits
 * roll up slowly from the bottom (the music's CC BY credit among them) until "Thank you
 * for playing." stops in the middle. Any key or click after [SKIP_AFTER_MS], Esc, or
 * [AUTO_QUIT_AFTER_END_MS] after the roll ends saves the world and goes back to the title
 * screen; entering the world again starts a new game (`platform.EndingHooks`). Opened by
 * the server at the end of Level 2.
 *
 * From the main menu ([credits], with [parent] set) it is only the credits: no "End of
 * demo", the roll starts at once, any key or click goes back to [parent].
 */
class EndOfDemoScreen private constructor(private val parent: Screen?) : Screen(Text.translatable("screen.every_tunnel.end.title")) {

    constructor() : this(null)

    private val creditsOnly get() = parent != null
    private val rollStartMs get() = if (creditsOnly) CREDITS_ROLL_START_MS else ROLL_START_MS
    private val skipAfterMs get() = if (creditsOnly) 0L else SKIP_AFTER_MS

    private val openedAt = Util.getMeasuringTimeMs()
    private var quitting = false

    private fun elapsed(): Long = Util.getMeasuringTimeMs() - openedAt

    /** Pixels the roll has moved up after [ms]; it stops once the last line is in the middle. */
    private fun scroll(ms: Long): Float {
        val moving = ((ms - rollStartMs).coerceAtLeast(0L) / 1000f) * ROLL_SPEED
        return moving.coerceAtMost(rollLength())
    }

    /** From the title's place to the last line reaching the middle of the screen. */
    private fun rollLength(): Float = (TITLE_GAP + CREDITS.sumOf { it.height }).toFloat()

    private fun rollEndMs(): Long = rollStartMs + (rollLength() / ROLL_SPEED * 1000f).toLong()

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0xFF000000.toInt())
        val ms = elapsed()
        val top = height / 2f - scroll(ms)

        if (!creditsOnly) alpha(ms, 0L, 2000L)?.let { a ->
            context.matrices.push()
            context.matrices.scale(TITLE_SCALE, TITLE_SCALE, 1f)
            context.drawCenteredTextWithShadow(
                textRenderer, title, (width / 2f / TITLE_SCALE).toInt(), ((top - 12f) / TITLE_SCALE).toInt(), a shl 24 or 0xE0E0E0,
            )
            context.matrices.pop()
        }

        var y = top + TITLE_GAP
        for (line in CREDITS) {
            if (line.key != null && y > -20f && y < height + 20f) {
                val colour = if (line.header) 0x8A9098 else 0xD8DCE0
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable(line.key), width / 2, y.toInt(), 0xFF000000.toInt() or colour)
            }
            y += line.height
        }

        if (ms > maxOf(skipAfterMs, rollEndMs())) {
            alpha(ms, maxOf(skipAfterMs, rollEndMs()), 1000L)?.let { a ->
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
        if (elapsed() > rollEndMs() + AUTO_QUIT_AFTER_END_MS) quitToTitle()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (elapsed() > skipAfterMs || keyCode == 256) quitToTitle()
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (elapsed() > skipAfterMs) quitToTitle()
        return true
    }

    override fun close() = quitToTitle()

    override fun shouldPause(): Boolean = false

    /** Like the pause menu's "Save and Quit to Title"; from the menu, just back to it. */
    private fun quitToTitle() {
        if (quitting) return
        quitting = true
        val client = client ?: MinecraftClient.getInstance()
        if (parent != null) return client.setScreen(parent)
        val singleplayer = client.isInSingleplayer
        client.world?.disconnect()
        if (singleplayer) client.disconnect(MessageScreen(Text.translatable("menu.savingLevel"))) else client.disconnect()
        client.setScreen(TitleScreen())
    }

    /** One line of the credits: a lang key (null = a gap), a header (dimmer) or a name. */
    private class Line(val key: String?, val header: Boolean = false, val height: Int = 12)

    companion object {
        private const val TITLE_SCALE = 3f
        /** The roll starts after the title has been alone on screen for a moment. */
        private const val ROLL_START_MS = 4500L
        /** From the menu there is no title to wait for. */
        private const val CREDITS_ROLL_START_MS = 500L
        private const val ROLL_SPEED = 16f
        /** From the title down to the first credit line. */
        private const val TITLE_GAP = 50
        private const val SKIP_AFTER_MS = 6000L
        private const val AUTO_QUIT_AFTER_END_MS = 10000L

        private fun header(key: String) = Line(key, header = true)
        private fun name(key: String) = Line(key)
        private fun gap(height: Int = 18) = Line(null, height = height)

        private val CREDITS = listOf(
            name("screen.every_tunnel.end.game"), gap(30),
            header("screen.every_tunnel.end.by_header"), name("screen.every_tunnel.end.by"), gap(),
            header("screen.every_tunnel.end.music"), name("screen.every_tunnel.end.music_author"),
            name("screen.every_tunnel.end.music_license"), name("screen.every_tunnel.end.music_link"), gap(),
            header("screen.every_tunnel.end.sounds_header"), name("screen.every_tunnel.end.sounds"), gap(),
            header("screen.every_tunnel.end.built_with"), name("screen.every_tunnel.end.libraries"), gap(),
            header("screen.every_tunnel.end.recommended"), name("screen.every_tunnel.end.mods"), gap(60),
            name("screen.every_tunnel.end.thanks"),
        )

        /** Only the credits, opened from the main menu; any key goes back to [parent]. */
        fun credits(parent: Screen): EndOfDemoScreen = EndOfDemoScreen(parent)

        fun register() {
            ClientPlayNetworking.registerGlobalReceiver(EndingHooks.ENDING_PACKET_ID) { client, _, _, _ ->
                client.execute { client.setScreen(EndOfDemoScreen()) }
            }
        }
    }
}
