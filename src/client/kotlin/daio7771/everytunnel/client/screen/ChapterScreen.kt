package daio7771.everytunnel.client.screen

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

/**
 * What NEW GAME in [MainMenuScreen] opens: a black box over the menu, in its style, with the two chapters, CHAPTER 1
 * and CHAPTER 2. Chapter 2 carries a padlock, and a line under it, until Chapter 1 has been completed ([locked]: the
 * achievement "Nothing in There", earned on the congratulations window after the final credits). The lock is a warning,
 * not a wall: choosing a locked Chapter 2 is [onLocked]'s to ask about (a spoiler alert, [SpoilerAlertScreen]). The
 * screen goes back to its [parent] first and then runs the choice, so what the choice opens is not covered by it.
 * Mouse or keyboard (arrows, Enter); Esc goes back.
 */
class ChapterScreen(
    private val parent: Screen,
    private val locked: Boolean,
    private val onChapterOne: () -> Unit,
    private val onChapterTwo: () -> Unit,
    private val onLocked: () -> Unit,
) : Screen(Text.empty()) {

    private var selected = 0

    override fun shouldPause(): Boolean = false

    private val boxLeft get() = (width - BOX_WIDTH) / 2
    private val boxTop get() = (height - BOX_HEIGHT) / 2
    private val firstY get() = boxTop + 20
    private val lineX get() = boxLeft + 40

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        parent.render(context, -1, -1, delta)
        context.matrices.push()
        context.matrices.translate(0f, 0f, 400f)
        context.fill(0, 0, width, height, DIM)
        context.fill(boxLeft, boxTop, boxLeft + BOX_WIDTH, boxTop + BOX_HEIGHT, BOX)
        context.drawBorder(boxLeft, boxTop, BOX_WIDTH, BOX_HEIGHT, OUTLINE)
        hovered(mouseX, mouseY)?.let { selected = it }
        for (i in 0..1) drawOption(context, i)
        if (locked) {
            val hint = Text.translatable("menu.every_tunnel.chapter_2.locked")
            context.drawCenteredTextWithShadow(textRenderer, hint, width / 2, boxTop + BOX_HEIGHT - 16, HINT)
        }
        context.matrices.pop()
    }

    private fun drawOption(context: DrawContext, index: Int) {
        val chosen = index == selected
        val dim = index == 1 && locked
        val label = Text.translatable(if (index == 0) "menu.every_tunnel.chapter_1" else "menu.every_tunnel.chapter_2")
        val colour = when {
            chosen -> CHOSEN
            dim -> LOCKED
            else -> OTHER
        }
        val y = firstY + index * LINE
        val matrices = context.matrices
        matrices.push()
        matrices.translate(lineX.toDouble(), y.toDouble(), 0.0)
        matrices.scale(SCALE, SCALE, 1f)
        if (chosen) context.drawText(textRenderer, Text.literal(">"), -9, 0, CHOSEN, false)
        context.drawText(textRenderer, label, 0, 0, colour, false)
        matrices.pop()
        if (index == 1 && locked) padlock(context, lineX + (textRenderer.getWidth(label) * SCALE).toInt() + 10, y - 1, if (chosen) CHOSEN else LOCKED)
    }

    /** A small padlock, 8 wide and 11 high at ([x], [y]): the shackle's arch over a body. */
    private fun padlock(context: DrawContext, x: Int, y: Int, colour: Int) {
        context.fill(x + 1, y, x + 7, y + 1, colour)
        context.fill(x + 1, y + 1, x + 2, y + 5, colour)
        context.fill(x + 6, y + 1, x + 7, y + 5, colour)
        context.fill(x, y + 5, x + 8, y + 11, colour)
        context.fill(x + 3, y + 7, x + 5, y + 9, BOX)
    }

    private fun hovered(mouseX: Int, mouseY: Int): Int? {
        for (i in 0..1) {
            val y = firstY + i * LINE
            if (mouseX in (lineX - 14)..(lineX + 150) && mouseY in (y - 2)..(y + (9 * SCALE).toInt() + 2)) return i
        }
        return null
    }

    private fun choose(index: Int) {
        client?.setScreen(parent)
        when {
            index == 0 -> onChapterOne()
            locked -> onLocked()
            else -> onChapterTwo()
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        hovered(mouseX.toInt(), mouseY.toInt())?.let { choose(it) }
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        when (keyCode) {
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_S -> selected = 1 - selected
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> choose(selected)
            GLFW.GLFW_KEY_ESCAPE -> client?.setScreen(parent)
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }

    private companion object {
        const val BOX_WIDTH = 240
        const val BOX_HEIGHT = 96
        const val LINE = 24
        const val SCALE = 1.5f
        const val DIM = 0x90000000.toInt()
        const val BOX = 0xFF0A0A0B.toInt()
        const val OUTLINE = 0xFF6E737A.toInt()
        const val CHOSEN = 0xFFE8ECF0.toInt()
        const val OTHER = 0xFF6E737A.toInt()
        const val LOCKED = 0xFF4A4E54.toInt()
        const val HINT = 0xFF8A8E94.toInt()
    }
}
