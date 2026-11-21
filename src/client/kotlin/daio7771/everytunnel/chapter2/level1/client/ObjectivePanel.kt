package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.chapter2.level1.core.Ch2L1Objectives
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.Util

/**
 * The objective panel of Chapter 2's Level 1: in the top right corner, "OBJECTIVE" in grey and under it the objective
 * ([Ch2L1Objectives]: the text of the player's state), small, on a faint strip. When the objective changes it fades in
 * over [FADE_MS] and stays. No sound. Nothing is drawn outside the level, nor in a state with no objective (a cinematic
 * hides the whole HUD anyway). Its text is in the lang files (`every_tunnel.ch2.l1.objective.*`).
 */
object ObjectivePanel {

    private const val FADE_MS = 700L
    private const val MARGIN = 8
    private const val PADDING = 4

    /** The panel is this much of the screen's width at most; a longer objective wraps. */
    private const val WIDTH = 0.26
    private const val MIN_WIDTH = 120
    private const val TITLE_GREY = 0x9A9A9A
    private const val WHITE = 0xFFFFFF
    private const val STRIP_ALPHA = 0.45

    private var shownKey: String? = null
    private var shownAtMs = 0L

    fun register() {
        HudRenderCallback.EVENT.register { context, _ -> render(context) }
    }

    private fun render(context: DrawContext) {
        val key = Ch2L1Client.objectiveKey()
        if (key != shownKey) {
            shownKey = key
            shownAtMs = Util.getMeasuringTimeMs()
        }
        if (key == null) return
        val fade = ((Util.getMeasuringTimeMs() - shownAtMs).toFloat() / FADE_MS).coerceIn(0f, 1f)
        val alpha = (fade * 255).toInt()
        if (alpha < 8) return // below this the game draws text opaque

        val font = MinecraftClient.getInstance().textRenderer
        val screenWidth = context.scaledWindowWidth
        val lines = font.wrapLines(Ch2L1Client.objectiveText(key), (screenWidth * WIDTH).toInt().coerceAtLeast(MIN_WIDTH))
        val title = Text.translatable(Ch2L1Objectives.TITLE_KEY)
        val step = font.fontHeight + 2
        val inner = maxOf(font.getWidth(title), lines.maxOf { font.getWidth(it) })
        val width = inner + 2 * PADDING
        val height = step * (lines.size + 1) + 2 * PADDING - 2
        val left = screenWidth - MARGIN - width
        val top = MARGIN
        context.fill(left, top, left + width, top + height, ((STRIP_ALPHA * alpha).toInt() shl 24))
        var y = top + PADDING
        context.drawText(font, title, left + PADDING, y, (alpha shl 24) or TITLE_GREY, true)
        y += step
        for (line in lines) {
            context.drawText(font, line, left + PADDING, y, (alpha shl 24) or WHITE, true)
            y += step
        }
    }
}
