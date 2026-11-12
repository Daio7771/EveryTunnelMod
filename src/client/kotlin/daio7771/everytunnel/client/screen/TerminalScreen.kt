package daio7771.everytunnel.client.screen

import daio7771.everytunnel.platform.ModSounds
import daio7771.everytunnel.platform.TerminalHooks
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.text.Text
import net.minecraft.util.Util

/**
 * A Level 5 computer's screen ([TerminalHooks]): green letters on black (red where a line says so) in a monitor's frame, typed out a
 * character at a time with soft key clicks (`block.terminal.key`), a cursor blinking at the end. A click or
 * a key shows the rest at once; Esc, or a click once it is all there, closes it. The game does not pause.
 */
class TerminalScreen(private val lines: List<String>) : Screen(Text.translatable("screen.every_tunnel.terminal")) {

    private val openedMs = Util.getMeasuringTimeMs()
    private var shownAll = false
    private var clicked = 0
    private val total = lines.sumOf { it.removePrefix(TerminalHooks.RED).length + 1 }

    override fun shouldPause(): Boolean = false

    private fun typed(): Int = if (shownAll) total else ((Util.getMeasuringTimeMs() - openedMs) * CHARS_PER_SECOND / 1000).toInt().coerceAtMost(total)

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, DIM)
        val w = minOf(WIDTH, width - 20)
        val h = minOf(HEIGHT, height - 20)
        val x0 = (width - w) / 2
        val y0 = (height - h) / 2
        context.fill(x0 - 6, y0 - 6, x0 + w + 6, y0 + h + 6, FRAME)
        context.fill(x0, y0, x0 + w, y0 + h, SCREEN)
        var left = typed()
        if (left >= total) shownAll = true
        val keys = left / CHARS_PER_CLICK
        while (clicked < keys && !shownAll) {
            clicked++
            client?.soundManager?.play(PositionedSoundInstance.master(ModSounds.TERMINAL_KEY, 1f, 0.25f))
        }
        var y = y0 + 10
        var lastX = x0 + 10
        for (raw in lines) {
            if (left <= 0) break
            val red = raw.startsWith(TerminalHooks.RED)
            val line = raw.removePrefix(TerminalHooks.RED)
            val shown = line.take(left)
            for (part in textRenderer.wrapLines(Text.literal(shown), w - 20)) {
                context.drawText(textRenderer, part, x0 + 10, y, if (red) RED else GREEN, false)
                lastX = x0 + 10 + textRenderer.getWidth(part)
                y += 11
            }
            if (shown.isEmpty()) y += 11
            left -= line.length + 1
        }
        if ((Util.getMeasuringTimeMs() / 500) % 2 == 0L) context.fill(lastX + 1, y - 11, lastX + 6, y - 3, GREEN)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (shownAll) close() else shownAll = true
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true
        shownAll = true
        return true
    }

    companion object {
        private const val CHARS_PER_SECOND = 45L
        private const val CHARS_PER_CLICK = 3
        private const val WIDTH = 360
        private const val HEIGHT = 240
        private const val DIM = 0xA0000000.toInt()
        private const val FRAME = 0xFFD8DAD6.toInt()
        private const val SCREEN = 0xFF060E08.toInt()
        private const val GREEN = 0xFF5CE07A.toInt()
        private const val RED = 0xFFE04848.toInt()

        fun register() {
            ClientPlayNetworking.registerGlobalReceiver(TerminalHooks.PACKET_ID) { client, _, buf, _ ->
                val lines = TerminalHooks.readText(buf)
                client.execute { MinecraftClient.getInstance().setScreen(TerminalScreen(lines)) }
            }
        }
    }
}
