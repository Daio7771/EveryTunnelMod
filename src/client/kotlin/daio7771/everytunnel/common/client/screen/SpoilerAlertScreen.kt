package daio7771.everytunnel.common.client.screen

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

/**
 * The warning before a locked Chapter 2 is played ([ChapterScreen]): a black box over the menu, in its style, headed
 * SPOILER ALERT, saying it gives away the end of Chapter 1, with PLAY ANYWAY and BACK. BACK is chosen to begin with,
 * so Enter alone spoils nothing; Esc goes back. It returns to its [parent] first and then runs [play].
 */
class SpoilerAlertScreen(private val parent: Screen, private val play: () -> Unit) : Screen(Text.empty()) {

    private var playChosen = false

    override fun shouldPause(): Boolean = false

    private val boxLeft get() = (width - BOX_WIDTH) / 2
    private val boxTop get() = (height - BOX_HEIGHT) / 2
    private val playCentre get() = width / 2 - BUTTON_APART
    private val backCentre get() = width / 2 + BUTTON_APART
    private val buttonsY get() = boxTop + BOX_HEIGHT - 26

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        parent.render(context, -1, -1, delta)
        context.matrices.push()
        context.matrices.translate(0f, 0f, 400f)
        context.fill(0, 0, width, height, DIM)
        context.fill(boxLeft, boxTop, boxLeft + BOX_WIDTH, boxTop + BOX_HEIGHT, BOX)
        context.drawBorder(boxLeft, boxTop, BOX_WIDTH, BOX_HEIGHT, OUTLINE)
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("menu.every_tunnel.spoiler.title"), width / 2, boxTop + 12, ALERT)
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("menu.every_tunnel.spoiler.line1"), width / 2, boxTop + 28, TEXT)
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("menu.every_tunnel.spoiler.line2"), width / 2, boxTop + 40, TEXT)
        when {
            over(mouseX, mouseY, playCentre) -> playChosen = true
            over(mouseX, mouseY, backCentre) -> playChosen = false
        }
        button(context, "menu.every_tunnel.spoiler.play", playCentre, playChosen)
        button(context, "menu.every_tunnel.spoiler.back", backCentre, !playChosen)
        context.matrices.pop()
    }

    private fun button(context: DrawContext, key: String, centre: Int, chosen: Boolean) {
        val label = Text.translatable(key)
        val x = centre - textRenderer.getWidth(label) / 2
        if (chosen) context.drawText(textRenderer, Text.literal(">"), x - 10, buttonsY, CHOSEN, false)
        context.drawText(textRenderer, label, x, buttonsY, if (chosen) CHOSEN else OTHER, false)
    }

    private fun over(mouseX: Int, mouseY: Int, centre: Int): Boolean =
        mouseX in (centre - BUTTON_REACH)..(centre + BUTTON_REACH) && mouseY in (buttonsY - 4)..(buttonsY + 12)

    private fun answer(yes: Boolean) {
        client?.setScreen(parent)
        if (yes) play()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        when {
            over(mouseX.toInt(), mouseY.toInt(), playCentre) -> answer(true)
            over(mouseX.toInt(), mouseY.toInt(), backCentre) -> answer(false)
        }
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        when (keyCode) {
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D -> playChosen = !playChosen
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> answer(playChosen)
            GLFW.GLFW_KEY_ESCAPE -> answer(false)
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }

    private companion object {
        const val BOX_WIDTH = 300
        const val BOX_HEIGHT = 92
        const val BUTTON_APART = 70
        const val BUTTON_REACH = 50
        const val DIM = 0x90000000.toInt()
        const val BOX = 0xFF0A0A0B.toInt()
        const val OUTLINE = 0xFF6E737A.toInt()
        const val ALERT = 0xFFB17FD0.toInt()
        const val TEXT = 0xFFD6D6D0.toInt()
        const val CHOSEN = 0xFFE8E8E4.toInt()
        const val OTHER = 0xFF6E737A.toInt()
    }
}
