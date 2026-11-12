package daio7771.everytunnel.client.screen

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.AchievementStore
import daio7771.everytunnel.client.hud.AchievementToast
import daio7771.everytunnel.core.achievement.Achievement
import daio7771.everytunnel.core.ending.RunSummary
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.Util

/**
 * The congratulations window, over the main menu once the final credits are done (or skipped with Esc): a clean white
 * panel with the Observer sat with his coffee, steaming and sipping (`congrats_observer.png`, `tools/gui/congrats_art.py`), a title, a few
 * paragraphs of thanks, the time played and the deaths of the whole game ([RunSummary]) and a button that closes it.
 * Enter, Space, Esc or a click on the button close it, back to the menu underneath ([parent]).
 *
 * With [award], opening it earns [Achievement.NOTHING_THERE] (the first time ever: its toast shows over the window).
 * The texts are the lang keys `screen.every_tunnel.congrats.*` (paragraphs split at "\n"). Everything is laid out
 * from the window's size and the translation's length: the art comes down a size, then moves beside the text, when
 * there is no room above it.
 */
class CongratulationsScreen(
    private val parent: Screen?,
    private val summary: RunSummary,
    private val award: Boolean,
) : Screen(Text.translatable("screen.every_tunnel.congrats.title")) {

    private val openedAt = Util.getMeasuringTimeMs()
    private var awarded = false

    override fun init() {
        if (!award || awarded) return
        awarded = true
        if (AchievementStore.unlock(Achievement.NOTHING_THERE)) client?.toastManager?.add(AchievementToast(Achievement.NOTHING_THERE))
    }

    override fun shouldPause(): Boolean = false

    override fun close() {
        client?.setScreen(parent)
    }

    private class Layout(
        val left: Int, val top: Int, val width: Int, val height: Int,
        /** The art: where, and at what size (0: none). */
        val artX: Int, val artY: Int, val artScale: Int,
        val titleY: Int,
        /** The column of text and numbers: its centre and its width. */
        val columnCentre: Int, val columnWidth: Int,
        val bodyY: Int, val paragraphs: List<List<OrderedText>>,
        val statsY: Int,
        val buttonX: Int, val buttonY: Int, val buttonW: Int,
    )

    /**
     * Where everything goes in a window of this size, in the player's language. First the art above the title, as big as
     * fits; if the window is too short for that, the art beside the text, which takes no height of its own; and if
     * that does not fit either, it is the one used all the same.
     */
    private fun layout(): Layout {
        val texts = Text.translatable("screen.every_tunnel.congrats.text").string.split("\n")
        val room = height - 2 * EDGE
        for (scale in ART_SCALES) {
            val above = arranged(PANEL_MAX, scale, true, texts)
            if (above.height <= room) return above
        }
        return arranged(PANEL_WIDE, ART_SCALES.last(), false, texts)
    }

    private fun arranged(panelMax: Int, scale: Int, above: Boolean, texts: List<String>): Layout {
        val panelW = minOf(panelMax, width - 2 * EDGE).coerceAtLeast(120)
        val artW = ART_W * scale
        // Beside the text, the art needs a column of its own; with no room for one it is left out.
        val beside = !above && panelW - 2 * PAD - artW - ART_GAP >= MIN_COLUMN
        val artScale = if (above || beside) scale else 0
        val columnW = if (beside) panelW - 2 * PAD - artW - ART_GAP else panelW - 2 * PAD
        val paragraphs = texts.map { textRenderer.wrapLines(Text.literal(it), columnW) }
        val bodyH = paragraphs.sumOf { it.size * LINE + PARAGRAPH_GAP } - PARAGRAPH_GAP
        val titleH = (textRenderer.fontHeight * TITLE_SCALE).toInt()
        val textH = bodyH + 10 + STATS_H
        val aboveH = if (above) ART_H * scale + 8 else 0
        val middleH = if (beside) maxOf(ART_H * scale, textH) else textH
        val panelH = PAD_Y + aboveH + titleH + 8 + middleH + 12 + BUTTON_H + PAD_Y
        val left = (width - panelW) / 2
        val top = ((height - panelH) / 2).coerceAtLeast(EDGE / 2)
        val titleY = top + PAD_Y + aboveH
        val middleY = titleY + titleH + 8
        val bodyY = middleY + (middleH - textH) / 2
        val artX = if (beside) left + PAD else left + (panelW - artW) / 2
        val artY = if (beside) middleY + (middleH - ART_H * scale) / 2 else top + PAD_Y
        val columnCentre = if (beside) left + PAD + artW + ART_GAP + columnW / 2 else left + panelW / 2
        val buttonW = maxOf(BUTTON_MIN_W, textRenderer.getWidth(Text.translatable("screen.every_tunnel.congrats.close")) + 28)
        return Layout(
            left, top, panelW, panelH, artX, artY, artScale, titleY, columnCentre, columnW, bodyY, paragraphs,
            bodyY + bodyH + 10, left + (panelW - buttonW) / 2, top + panelH - PAD_Y - BUTTON_H, buttonW,
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        parent?.render(context, -1, -1, delta)
        val fade = ((Util.getMeasuringTimeMs() - openedAt) / FADE_MS.toFloat()).coerceIn(0f, 1f)
        context.matrices.push()
        context.matrices.translate(0f, 0f, 400f)
        context.fill(0, 0, width, height, faded(fade, DIM))
        val l = layout()
        context.fill(l.left + SHADOW, l.top + SHADOW, l.left + l.width + SHADOW, l.top + l.height + SHADOW, faded(fade, SHADOW_COLOUR))
        context.fill(l.left, l.top, l.left + l.width, l.top + l.height, faded(fade, PANEL))
        context.drawBorder(l.left, l.top, l.width, l.height, faded(fade, BORDER))
        if (fade > 0.05f) content(context, l, mouseX, mouseY, fade)
        context.matrices.pop()
    }

    private fun content(context: DrawContext, l: Layout, mouseX: Int, mouseY: Int, fade: Float) {
        if (l.artScale > 0) {
            RenderSystem.enableBlend()
            context.setShaderColor(1f, 1f, 1f, fade)
            val shot = shotAt(Util.getMeasuringTimeMs() - openedAt)
            context.drawTexture(ART, l.artX, l.artY, ART_W * l.artScale, ART_H * l.artScale, (shot.pose * ART_W).toFloat(), (shot.phase * ART_H).toFloat(), ART_W, ART_H, SHEET_W, SHEET_H)
            context.setShaderColor(1f, 1f, 1f, 1f)
            RenderSystem.disableBlend()
        }
        val title = Text.translatable("screen.every_tunnel.congrats.title").styled { it.withBold(true) }
        val panelCentre = l.left + l.width / 2
        context.matrices.push()
        context.matrices.scale(TITLE_SCALE, TITLE_SCALE, 1f)
        context.drawText(textRenderer, title, ((panelCentre - textRenderer.getWidth(title) * TITLE_SCALE / 2) / TITLE_SCALE).toInt(), (l.titleY / TITLE_SCALE).toInt(), faded(fade, INK), false)
        context.matrices.pop()

        var y = l.bodyY
        for (paragraph in l.paragraphs) {
            for (line in paragraph) {
                context.drawText(textRenderer, line, l.columnCentre - textRenderer.getWidth(line) / 2, y, faded(fade, INK), false)
                y += LINE
            }
            y += PARAGRAPH_GAP
        }

        // The two numbers of the game, side by side in the text's column: a small label over each value.
        stat(context, "screen.every_tunnel.congrats.time", summary.time(), l.columnCentre - l.columnWidth / 4, l.statsY, fade)
        stat(context, "screen.every_tunnel.congrats.deaths", summary.deaths.toString(), l.columnCentre + l.columnWidth / 4, l.statsY, fade)

        val over = overButton(l, mouseX, mouseY)
        context.fill(l.buttonX, l.buttonY, l.buttonX + l.buttonW, l.buttonY + BUTTON_H, faded(fade, if (over) BUTTON_HOVER else BUTTON))
        val close = Text.translatable("screen.every_tunnel.congrats.close")
        context.drawText(textRenderer, close, l.buttonX + (l.buttonW - textRenderer.getWidth(close)) / 2, l.buttonY + (BUTTON_H - textRenderer.fontHeight) / 2 + 1, faded(fade, WHITE), false)
    }

    private fun stat(context: DrawContext, labelKey: String, value: String, centre: Int, y: Int, fade: Float) {
        val label = Text.translatable(labelKey)
        context.drawText(textRenderer, label, centre - textRenderer.getWidth(label) / 2, y, faded(fade, SOFT), false)
        context.matrices.push()
        context.matrices.scale(VALUE_SCALE, VALUE_SCALE, 1f)
        context.drawText(textRenderer, value, ((centre - textRenderer.getWidth(value) * VALUE_SCALE / 2) / VALUE_SCALE).toInt(), ((y + 12) / VALUE_SCALE).toInt(), faded(fade, INK), false)
        context.matrices.pop()
    }

    private fun overButton(l: Layout, mouseX: Int, mouseY: Int): Boolean =
        mouseX in l.buttonX..(l.buttonX + l.buttonW) && mouseY in l.buttonY..(l.buttonY + BUTTON_H)

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (overButton(layout(), mouseX.toInt(), mouseY.toInt())) {
            close()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == ENTER || keyCode == KEYPAD_ENTER || keyCode == SPACE) {
            close()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    /** The frame of the Observer's animation [elapsedMs] after the window opened: the timeline, looped. */
    private fun shotAt(elapsedMs: Long): Shot {
        var left = elapsedMs.coerceAtLeast(0) % CYCLE_MS
        for (shot in TIMELINE) {
            if (left < shot.ms) return shot
            left -= shot.ms
        }
        return TIMELINE.first()
    }

    /** [colour] with its alpha scaled by [fade]. */
    private fun faded(fade: Float, colour: Int): Int {
        val a = ((colour ushr 24) * fade).toInt().coerceIn(0, 255)
        return (a shl 24) or (colour and 0xFFFFFF)
    }

    companion object {
        private val ART = Identifier(EveryTunnel.MOD_ID, "textures/gui/congrats_observer.png")
        /** One frame of the sheet, and the sheet: 4 poses of his arm across, 4 phases of the steam down. */
        private const val ART_W = 48
        private const val ART_H = 48
        private const val SHEET_W = 192
        private const val SHEET_H = 192

        /** A frame of the animation: the pose, the steam's phase and how long it shows (the same as `tools/gui/congrats_art.py`). */
        private class Shot(val pose: Int, val phase: Int, val ms: Int)

        /** The steam rises at his side for 3.6 s; he lifts the cup, sips twice and puts it back: about 6 s in a loop. */
        private val TIMELINE = List(12) { Shot(0, it % 4, 300) } + listOf(
            Shot(1, 0, 350), Shot(2, 1, 350), Shot(3, 0, 550), Shot(2, 2, 250), Shot(3, 0, 550), Shot(2, 3, 300), Shot(1, 1, 300),
        )
        private val CYCLE_MS = TIMELINE.sumOf { it.ms }.toLong()
        /** The sizes the art is drawn at, biggest first. */
        private val ART_SCALES = listOf(2, 1)

        /** The panel's widest: with the art above the title, and with it beside the text. */
        private const val PANEL_MAX = 300
        private const val PANEL_WIDE = 360
        private const val ART_GAP = 14
        /** The least a column of text may be before the art beside it is left out. */
        private const val MIN_COLUMN = 150
        private const val EDGE = 8
        private const val PAD = 18
        private const val PAD_Y = 14
        private const val LINE = 11
        private const val PARAGRAPH_GAP = 5
        private const val TITLE_SCALE = 1.6f
        private const val VALUE_SCALE = 1.5f
        private const val STATS_H = 30
        private const val BUTTON_H = 18
        private const val BUTTON_MIN_W = 80
        private const val SHADOW = 3
        private const val FADE_MS = 350L

        private const val ENTER = 257
        private const val KEYPAD_ENTER = 335
        private const val SPACE = 32

        private const val DIM = 0xB0000000.toInt()
        private const val SHADOW_COLOUR = 0x50000000
        private const val PANEL = 0xFFFFFFFF.toInt()
        private const val BORDER = 0xFFC9CCD1.toInt()
        private const val INK = 0xFF1E2024.toInt()
        private const val SOFT = 0xFF6A6F78.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val BUTTON = 0xFF1E2024.toInt()
        private const val BUTTON_HOVER = 0xFF3A3F48.toInt()
    }
}
