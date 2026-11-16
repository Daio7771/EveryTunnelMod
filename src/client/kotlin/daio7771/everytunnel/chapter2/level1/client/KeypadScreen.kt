package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.chapter2.level1.core.Ch2L1Act3
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1Act3Hooks
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
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
 * The keypad by Rowan's office's door ([Ch2L1Act3Hooks], [KeypadBlock][daio7771.everytunnel.chapter2.level1.platform.KeypadBlock]), close
 * up: a brushed steel plate with a small green display and twelve keys, 1 to 9, C (clear), 0 and OK. The keys are clicked, or typed (the
 * digits, Backspace, Enter). OK sends the code to the server ([Ch2L1Act3Hooks.KEYPAD_TRY_PACKET]), which answers ([result]): a wrong code
 * shows DENIED in red a moment and clears; Rowan's shows OPEN in green and the screen closes by itself. No limit of tries. Every key
 * beeps. The game does not pause.
 */
class KeypadScreen(private val pos: BlockPos) : Screen(Text.translatable(KEY + "keypad.title")) {

    private var typed = ""
    private var waiting = false
    private var verdict: Boolean? = null
    private var verdictMs = 0L

    override fun shouldPause(): Boolean = false

    private fun x0() = (width - PLATE_W) / 2

    private fun y0() = (height - PLATE_H) / 2

    private fun keyAt(i: Int): Pair<Int, Int> = (x0() + 16 + (i % 3) * (KEY_W + GAP)) to (y0() + 58 + (i / 3) * (KEY_H + GAP))

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val now = Util.getMeasuringTimeMs()
        when (verdict) {
            true -> if (now - verdictMs > OPEN_MS) {
                close()
                return
            }
            false -> if (now - verdictMs > DENIED_MS) {
                verdict = null
                typed = ""
            }
            null -> {}
        }
        context.fill(0, 0, width, height, 0x90000000.toInt())
        val x = x0()
        val y = y0()
        context.fill(x - 2, y - 2, x + PLATE_W + 2, y + PLATE_H + 2, EDGE)
        context.fill(x, y, x + PLATE_W, y + PLATE_H, PLATE)
        for (row in 0 until PLATE_H step 3) context.fill(x, y + row, x + PLATE_W, y + row + 1, BRUSH)
        // The display, and the light beside it.
        context.fill(x + 14, y + 14, x + PLATE_W - 14, y + 40, BEZEL)
        context.fill(x + 16, y + 16, x + PLATE_W - 16, y + 38, LCD)
        val (shown, colour) = when (verdict) {
            true -> I18n.translate(KEY + "keypad.open") to LCD_GREEN
            false -> I18n.translate(KEY + "keypad.denied") to LCD_RED
            null -> (if (typed.isEmpty()) "----" else typed) to LCD_GREEN
        }
        context.drawText(textRenderer, shown, x + (PLATE_W - textRenderer.getWidth(shown)) / 2, y + 23, colour, false)
        context.fill(x + PLATE_W - 12, y + 6, x + PLATE_W - 7, y + 11, if (verdict == true) LED_GREEN else LED_RED)
        for (i in LABELS.indices) {
            val (kx, ky) = keyAt(i)
            val hover = mouseX in kx..(kx + KEY_W) && mouseY in ky..(ky + KEY_H)
            context.fill(kx, ky, kx + KEY_W, ky + KEY_H, KEY_EDGE)
            context.fill(kx + 1, ky + 1, kx + KEY_W - 1, ky + KEY_H - 1, if (hover) KEY_HOVER else KEY_FACE)
            val label = LABELS[i]
            context.drawText(textRenderer, label, kx + (KEY_W - textRenderer.getWidth(label)) / 2, ky + (KEY_H - 8) / 2, KEY_TEXT, false)
        }
        val hint = I18n.translate(KEY + "keypad.hint")
        context.drawText(textRenderer, hint, (width - textRenderer.getWidth(hint)) / 2, y + PLATE_H + 8, 0xFFB0B4BA.toInt(), true)
    }

    private fun press(label: String) {
        if (waiting || verdict != null) return
        client?.soundManager?.play(PositionedSoundInstance.master(Ch2Sounds.CH2L1_KEYPAD_KEY, 1f, 0.6f))
        when (label) {
            "C" -> typed = ""
            "OK" -> if (typed.isNotEmpty()) send()
            else -> if (typed.length < Ch2L1Act3.MAX_CODE) typed += label
        }
    }

    private fun send() {
        waiting = true
        val buf = PacketByteBufs.create()
        buf.writeBlockPos(pos)
        buf.writeString(typed)
        ClientPlayNetworking.send(Ch2L1Act3Hooks.KEYPAD_TRY_PACKET, buf)
    }

    /** The server's answer to the code sent. */
    fun result(ok: Boolean) {
        waiting = false
        verdict = ok
        verdictMs = Util.getMeasuringTimeMs()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return true
        for (i in LABELS.indices) {
            val (kx, ky) = keyAt(i)
            if (mouseX >= kx && mouseX <= kx + KEY_W && mouseY >= ky && mouseY <= ky + KEY_H) {
                press(LABELS[i])
                return true
            }
        }
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        when (keyCode) {
            in GLFW.GLFW_KEY_0..GLFW.GLFW_KEY_9 -> press((keyCode - GLFW.GLFW_KEY_0).toString())
            in GLFW.GLFW_KEY_KP_0..GLFW.GLFW_KEY_KP_9 -> press((keyCode - GLFW.GLFW_KEY_KP_0).toString())
            GLFW.GLFW_KEY_BACKSPACE -> if (!waiting && verdict == null && typed.isNotEmpty()) typed = typed.dropLast(1)
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> press("OK")
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }

    companion object {
        private const val KEY = "every_tunnel.ch2.l1."
        private val LABELS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK")
        private const val KEY_W = 34
        private const val KEY_H = 22
        private const val GAP = 6
        private const val PLATE_W = 16 * 2 + 3 * KEY_W + 2 * GAP
        private const val PLATE_H = 58 + 4 * KEY_H + 3 * GAP + 16
        private const val OPEN_MS = 1200L
        private const val DENIED_MS = 1100L
        private const val EDGE = 0xFF2A2C30.toInt()
        private const val PLATE = 0xFF6E737A.toInt()
        private const val BRUSH = 0xFF767B82.toInt()
        private const val BEZEL = 0xFF1A1C1F.toInt()
        private const val LCD = 0xFF0C1A10.toInt()
        private const val LCD_GREEN = 0xFF6CF08A.toInt()
        private const val LCD_RED = 0xFFF05A4E.toInt()
        private const val LED_RED = 0xFFE2342C.toInt()
        private const val LED_GREEN = 0xFF40DC60.toInt()
        private const val KEY_EDGE = 0xFF33363B.toInt()
        private const val KEY_FACE = 0xFFA8ADB4.toInt()
        private const val KEY_HOVER = 0xFFC4C9D0.toInt()
        private const val KEY_TEXT = 0xFF1E2024.toInt()

        fun register() {
            ClientPlayNetworking.registerGlobalReceiver(Ch2L1Act3Hooks.KEYPAD_OPEN_PACKET) { client, _, buf, _ ->
                val pos = buf.readBlockPos()
                client.execute { MinecraftClient.getInstance().setScreen(KeypadScreen(pos)) }
            }
            ClientPlayNetworking.registerGlobalReceiver(Ch2L1Act3Hooks.KEYPAD_RESULT_PACKET) { client, _, buf, _ ->
                val ok = buf.readBoolean()
                client.execute { (client.currentScreen as? KeypadScreen)?.result(ok) }
            }
        }
    }
}
