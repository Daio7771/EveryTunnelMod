package daio7771.everytunnel.client.screen

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

/**
 * The warning before something is erased with Ctrl+Shift+D, the saved game in [MainMenuScreen] or the
 * achievements in [AchievementsScreen]: a black box over it, in the menu's style, with ERASE and CANCEL. CANCEL is chosen to begin with, so Enter alone loses nothing. Esc cancels.
 */
class EraseSaveScreen(private val parent: Screen, private val warningKey: String, private val erase: () -> Unit) : Screen(Text.empty()) {

    private var eraseChosen = false

    override fun shouldPause(): Boolean = false

    private val boxLeft get() = (width - BOX_WIDTH) / 2
    private val boxTop get() = (height - BOX_HEIGHT) / 2
    private val eraseCentre get() = width / 2 - BUTTON_APART
    private val cancelCentre get() = width / 2 + BUTTON_APART
    private val buttonsY get() = boxTop + BOX_HEIGHT - 26

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        parent.render(context, -1, -1, delta)
        context.matrices.push()
        context.matrices.translate(0f, 0f, 400f)
        context.fill(0, 0, width, height, DIM)
        context.fill(boxLeft, boxTop, boxLeft + BOX_WIDTH, boxTop + BOX_HEIGHT, BOX)
        context.drawBorder(boxLeft, boxTop, BOX_WIDTH, BOX_HEIGHT, OUTLINE)
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable(warningKey), width / 2, boxTop + 16, TEXT)
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("menu.every_tunnel.erase.sure"), width / 2, boxTop + 30, TEXT)
        when {
            over(mouseX, mouseY, eraseCentre) -> eraseChosen = true
            over(mouseX, mouseY, cancelCentre) -> eraseChosen = false
        }
        button(context, "menu.every_tunnel.erase.yes", eraseCentre, eraseChosen)
        button(context, "menu.every_tunnel.erase.no", cancelCentre, !eraseChosen)
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
        if (yes) erase()
        client?.setScreen(parent)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        when {
            over(mouseX.toInt(), mouseY.toInt(), eraseCentre) -> answer(true)
            over(mouseX.toInt(), mouseY.toInt(), cancelCentre) -> answer(false)
        }
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        when (keyCode) {
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D -> eraseChosen = !eraseChosen
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> answer(eraseChosen)
            GLFW.GLFW_KEY_ESCAPE -> answer(false)
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }

    private companion object {
        const val BOX_WIDTH = 240
        const val BOX_HEIGHT = 76
        const val BUTTON_APART = 50
        const val BUTTON_REACH = 34
        const val DIM = 0x90000000.toInt()
        const val BOX = 0xFF0A0A0B.toInt()
        const val OUTLINE = 0xFF6E737A.toInt()
        const val TEXT = 0xFFD6D6D0.toInt()
        const val CHOSEN = 0xFFE8E8E4.toInt()
        const val OTHER = 0xFF6E737A.toInt()
    }
}
