package daio7771.everytunnel.client.screen

import daio7771.everytunnel.client.AchievementIcons
import daio7771.everytunnel.client.AchievementStore
import daio7771.everytunnel.client.hud.AchievementToast
import daio7771.everytunnel.core.achievement.Achievement
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.OrderedText
import net.minecraft.text.StringVisitable
import net.minecraft.text.Text
import net.minecraft.util.Language
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The ACHIEVEMENTS screen, opened from the main menu ([MainMenuScreen]): one sheet of worn paper
 * like the player guide's ([Paper]) with the title, how many are earned ("3 / 8") and every
 * achievement in list order ([Achievement]). An earned one shows its icon, its title and the
 * line on how it was earned; one not earned yet a "?" and "Not yet unlocked"
 * ([AchievementIcons], [AchievementStore]).
 *
 * The sheet scrolls (wheel, up/down, page keys) when the window is too short for it; Esc or
 * BACK at the top left goes back to the menu, whose music keeps playing.
 */
class AchievementsScreen(private val parent: Screen?) : Screen(Text.translatable("screen.every_tunnel.achievements.title")) {

    private var unlocked: Set<Achievement> = emptySet()
    private var sheetLeft = 0
    private var sheetWidth = 0
    private var scrollTarget = 0.0
    private var scroll = 0.0
    private var lastFrameMs = Util.getMeasuringTimeMs()
    private val openedAtMs = Util.getMeasuringTimeMs()

    private val viewTop get() = MARGIN
    private val viewBottom get() = height - MARGIN
    private val sheetHeight get() = maxOf(PAD_TOP + HEADER_HEIGHT + Achievement.entries.size * ROW_HEIGHT + PAD_BOTTOM, viewBottom - viewTop - SHEET_GAP * 2)

    override fun init() {
        unlocked = AchievementStore.unlocked
        val backWidth = textRenderer.getWidth(backText) + MARGIN * 2
        sheetWidth = minOf(SHEET_MAX, width - backWidth * 2).coerceAtLeast(minOf(SHEET_MIN, width - MARGIN * 2))
        sheetLeft = (width - sheetWidth) / 2
        scrollTarget = scrollTarget.coerceIn(0.0, maxScroll())
        scroll = scroll.coerceIn(0.0, maxScroll())
    }

    override fun shouldPause(): Boolean = false

    override fun close() {
        client?.setScreen(parent)
    }

    private fun maxScroll(): Double = maxOf(0, sheetHeight + SHEET_GAP * 2 - (viewBottom - viewTop)).toDouble()

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val now = Util.getMeasuringTimeMs()
        val seconds = ((now - lastFrameMs) / 1000.0).coerceIn(0.0, 0.1)
        lastFrameMs = now
        scroll += (scrollTarget - scroll) * (1.0 - exp(-seconds * SCROLL_EASE))
        if (abs(scrollTarget - scroll) < 0.5) scroll = scrollTarget

        context.fill(0, 0, width, height, 0xFF000000.toInt())
        context.drawText(textRenderer, backText, MARGIN, MARGIN + 2, if (overBack(mouseX, mouseY)) BACK_HOVER else BACK_COLOR, false)
        drawSheet(context)

        val fade = ((now - openedAtMs) / FADE_IN_MS.toFloat()).coerceIn(0f, 1f)
        if (fade < 1f) context.fill(0, 0, width, height, ((1f - fade) * 255f).toInt() shl 24)
    }

    private fun drawSheet(context: DrawContext) {
        val top = viewTop + SHEET_GAP - scroll.roundToInt()
        val h = sheetHeight
        val textLeft = sheetLeft + PAD_X
        val textWidth = sheetWidth - PAD_X * 2
        context.enableScissor(0, viewTop, width, viewBottom)
        context.fill(sheetLeft + SHADOW, top + SHADOW, sheetLeft + sheetWidth + SHADOW, top + h + SHADOW, Paper.SHADOW_COLOR)
        Paper.tile(context, sheetLeft, top, sheetWidth, h, 0, 0)
        Paper.stains(context, STAIN_SEED, sheetLeft, top, sheetWidth, h)
        Paper.edges(context, sheetLeft, top, sheetWidth, h)

        // The title, twice the size, and the count under it.
        var y = top + PAD_TOP
        context.matrices.push()
        context.matrices.translate(textLeft.toFloat(), y.toFloat(), 0f)
        context.matrices.scale(TITLE_SCALE, TITLE_SCALE, 1f)
        context.drawText(textRenderer, title, 0, 0, Paper.INK_DARK, false)
        context.matrices.pop()
        val count = Text.translatable("screen.every_tunnel.achievements.count", unlocked.size, Achievement.entries.size)
        context.drawText(textRenderer, count, textLeft, y + COUNT_Y, Paper.INK_SOFT, false)
        y += HEADER_HEIGHT

        for (achievement in Achievement.entries) {
            context.fill(textLeft, y, textLeft + textWidth, y + 1, Paper.RULE_COLOR)
            val earned = achievement in unlocked
            AchievementIcons.draw(context, achievement, textLeft, y + ICON_Y, locked = !earned)
            val textX = textLeft + ICON_SIZE + ICON_GAP
            val available = textLeft + textWidth - textX
            if (earned) {
                val name = AchievementToast.title(achievement).copy().styled { it.withBold(true) }
                context.drawText(textRenderer, reorder(textRenderer.trimToWidth(name, available)), textX, y + TITLE_Y, Paper.INK_DARK, false)
                val line = textRenderer.trimToWidth(AchievementToast.description(achievement), available)
                context.drawText(textRenderer, reorder(line), textX, y + LINE_Y, Paper.INK_SOFT, false)
            } else {
                context.drawText(textRenderer, lockedText, textX, y + LOCKED_Y, Paper.INK_SOFT, false)
            }
            y += ROW_HEIGHT
        }
        context.disableScissor()
    }

    private fun reorder(text: StringVisitable): OrderedText = Language.getInstance().reorder(text)

    private fun overBack(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (MARGIN - 4)..(MARGIN + textRenderer.getWidth(backText) + 4) && mouseY in MARGIN..(MARGIN + 13)

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (overBack(mouseX.toInt(), mouseY.toInt())) {
            close()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        scrollBy(-amount * WHEEL_STEP)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val page = (viewBottom - viewTop - PAGE_KEEP).toDouble()
        when (keyCode) {
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> scrollBy(-KEY_STEP)
            GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> scrollBy(KEY_STEP)
            GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-page)
            GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> scrollBy(page)
            GLFW.GLFW_KEY_HOME -> scrollBy(-maxScroll())
            GLFW.GLFW_KEY_END -> scrollBy(maxScroll())
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }

    private fun scrollBy(amount: Double) {
        scrollTarget = (scrollTarget + amount).coerceIn(0.0, maxScroll())
    }

    companion object {
        private val backText: Text get() = Text.translatable("screen.every_tunnel.guide.back").let { Text.literal("< ").append(it) }
        private val lockedText: Text get() = Text.translatable("screen.every_tunnel.achievements.locked")

        // The same sizes as the guide's sheet.
        private const val MARGIN = 8
        private const val SHEET_MAX = 300
        private const val SHEET_MIN = 160
        private const val SHEET_GAP = 6
        private const val SHADOW = 3
        private const val PAD_X = 18
        private const val PAD_TOP = 18
        private const val PAD_BOTTOM = 18
        private const val STAIN_SEED = 0xAC41L

        private const val TITLE_SCALE = 2f
        private const val COUNT_Y = 22
        private const val HEADER_HEIGHT = 40

        /** One achievement: a rule on top, the icon, the title and its line beside it. */
        private const val ROW_HEIGHT = 28
        private const val ICON_SIZE = 16
        private const val ICON_GAP = 8
        private const val ICON_Y = 6
        private const val TITLE_Y = 5
        private const val LINE_Y = 16
        private const val LOCKED_Y = 10

        private const val WHEEL_STEP = 24.0
        private const val KEY_STEP = 20.0
        private const val PAGE_KEEP = 30
        private const val SCROLL_EASE = 18.0
        private const val FADE_IN_MS = 400L
        private const val BACK_COLOR = 0xFF6E737A.toInt()
        private const val BACK_HOVER = 0xFFE8ECF0.toInt()
    }
}
