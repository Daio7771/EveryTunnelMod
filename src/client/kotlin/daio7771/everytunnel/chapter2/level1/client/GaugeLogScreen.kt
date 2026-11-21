package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.chapter2.level1.client.NonameOsLook.DIM_GREEN
import daio7771.everytunnel.chapter2.level1.client.NonameOsLook.GREEN
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Gauge
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1LabTaskHooks
import daio7771.everytunnel.common.platform.ModSounds
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.resource.language.I18n
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.text.Text
import net.minecraft.util.Util
import net.minecraft.util.math.BlockPos
import org.lwjgl.glfw.GLFW

/**
 * The centrifuge's maintenance log in NonameOS ([Ch2L1LabTaskHooks], the gauge's task): the look of [NonameOsScreen] ([NonameOsLook]), a
 * line asking for the speed on the centrifuge's display, a field the digits are typed into (Backspace, Enter), LOG and CLOSE. LOG sends
 * it ([Ch2L1LabTaskHooks.GAUGE_TRY_PACKET]); the server answers ([result]): wrong, "VALUE REJECTED" a moment and the field clears; right,
 * "LOGGED" and the window closes by itself. No limit of tries. Escape closes it. The game does not pause.
 */
class GaugeLogScreen(private val pos: BlockPos) : Screen(Text.translatable(KEY + "os.title")) {

    private var typed = ""
    private var waiting = false
    private var verdict: Boolean? = null
    private var verdictMs = 0L

    override fun shouldPause(): Boolean = false

    private fun w() = minOf(WIDTH, width - 20)

    private fun h() = minOf(HEIGHT, height - 20)

    private fun left() = (width - w()) / 2

    private fun top() = (height - h()) / 2

    private fun logX() = left() + w() - 2 * BUTTON_W - 24

    private fun closeX() = left() + w() - BUTTON_W - 16

    private fun buttonY() = top() + h() - 24

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val now = Util.getMeasuringTimeMs()
        when (verdict) {
            true -> if (now - verdictMs > LOGGED_MS) {
                close()
                return
            }
            false -> if (now - verdictMs > REJECTED_MS) {
                verdict = null
                typed = ""
            }
            null -> {}
        }
        val x0 = left()
        val y0 = top()
        val w = w()
        NonameOsLook.window(context, textRenderer, width, height, x0, y0, w, h())
        text(context, I18n.translate(KEY + "os.gauge.title"), x0 + 12, y0 + 32, GREEN)
        context.fill(x0 + 12, y0 + 43, x0 + w - 12, y0 + 44, DIM_GREEN)
        text(context, I18n.translate(KEY + "os.gauge.prompt"), x0 + 14, y0 + 56, GREEN)
        // The field: a green outline, the digits typed and a blinking cursor.
        val fx = x0 + 14
        val fy = y0 + 72
        context.fill(fx, fy, fx + FIELD_W, fy + 16, GREEN)
        context.fill(fx + 1, fy + 1, fx + FIELD_W - 1, fy + 15, NonameOsLook.SCREEN)
        val cursor = if (verdict == null && (now / 400) % 2 == 0L) "_" else ""
        text(context, typed + cursor, fx + 5, fy + 4, GREEN)
        text(context, "RPM", fx + FIELD_W + 6, fy + 4, DIM_GREEN)
        when (verdict) {
            true -> text(context, I18n.translate(KEY + "os.gauge.logged"), x0 + 14, y0 + 100, GREEN)
            false -> text(context, I18n.translate(KEY + "os.gauge.rejected"), x0 + 14, y0 + 100, REJECTED)
            null -> {}
        }
        NonameOsLook.button(context, textRenderer, I18n.translate(KEY + "os.button.log"), logX(), buttonY(), BUTTON_W, BUTTON_H,
            typed.isNotEmpty() && !waiting && verdict == null, inside(mouseX, mouseY, logX(), buttonY()))
        NonameOsLook.button(context, textRenderer, I18n.translate(KEY + "os.button.close"), closeX(), buttonY(), BUTTON_W, BUTTON_H, true,
            inside(mouseX, mouseY, closeX(), buttonY()))
    }

    private fun text(context: DrawContext, text: String, x: Int, y: Int, colour: Int) {
        context.drawText(textRenderer, text, x, y, colour, false)
    }

    private fun inside(mx: Number, my: Number, x: Int, y: Int) =
        mx.toDouble() >= x && mx.toDouble() <= x + BUTTON_W && my.toDouble() >= y && my.toDouble() <= y + BUTTON_H

    private fun beep() {
        client?.soundManager?.play(PositionedSoundInstance.master(ModSounds.TERMINAL_KEY, 1f, 0.25f))
    }

    private fun type(digit: Char) {
        if (waiting || verdict != null || typed.length >= Ch2L1Gauge.MAX_DIGITS) return
        typed += digit
        beep()
    }

    private fun send() {
        if (waiting || verdict != null || typed.isEmpty()) return
        waiting = true
        val buf = PacketByteBufs.create()
        buf.writeBlockPos(pos)
        buf.writeString(typed)
        ClientPlayNetworking.send(Ch2L1LabTaskHooks.GAUGE_TRY_PACKET, buf)
        beep()
    }

    /** The server's answer to what was sent. */
    fun result(ok: Boolean) {
        waiting = false
        verdict = ok
        verdictMs = Util.getMeasuringTimeMs()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return true
        if (inside(mouseX, mouseY, logX(), buttonY())) send()
        if (inside(mouseX, mouseY, closeX(), buttonY())) close()
        return true
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (chr.isDigit()) type(chr)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        when (keyCode) {
            GLFW.GLFW_KEY_BACKSPACE -> if (!waiting && verdict == null && typed.isNotEmpty()) {
                typed = typed.dropLast(1)
                beep()
            }
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> send()
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }

    companion object {
        private const val KEY = "every_tunnel.ch2.l1."
        private const val WIDTH = 300
        private const val HEIGHT = 150
        private const val FIELD_W = 60
        private const val BUTTON_W = 64
        private const val BUTTON_H = 16
        private const val LOGGED_MS = 1200L
        private const val REJECTED_MS = 1100L
        private const val REJECTED = 0xFFE05A4A.toInt()

        fun register() {
            ClientPlayNetworking.registerGlobalReceiver(Ch2L1LabTaskHooks.GAUGE_OPEN_PACKET) { client, _, buf, _ ->
                val pos = buf.readBlockPos()
                client.execute { MinecraftClient.getInstance().setScreen(GaugeLogScreen(pos)) }
            }
            ClientPlayNetworking.registerGlobalReceiver(Ch2L1LabTaskHooks.GAUGE_RESULT_PACKET) { client, _, buf, _ ->
                val ok = buf.readBoolean()
                client.execute { (client.currentScreen as? GaugeLogScreen)?.result(ok) }
            }
        }
    }
}
