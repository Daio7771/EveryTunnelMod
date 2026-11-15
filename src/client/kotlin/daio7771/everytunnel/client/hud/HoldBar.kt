package daio7771.everytunnel.client.hud

import daio7771.everytunnel.platform.Ch2L1TaskHooks
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.resource.language.I18n
import net.minecraft.util.Util

/**
 * The bar of a task that takes a held click (Chapter 2 Level 1's analyzer and dirt, [Ch2L1TaskHooks]): above the hotbar, a label and a
 * thin bar filling as the server counts the hold. The server sends the fraction every few ticks while the click is held; when they
 * stop (the player let go or looked away) the bar fades out and the next hold starts from nothing.
 */
object HoldBar {

    private const val VISIBLE_MS = 450L
    private const val FADE_MS = 150L
    private const val WIDTH = 120
    private const val HEIGHT = 5

    private var fraction = 0f
    private var label = ""
    private var lastMs = 0L

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(Ch2L1TaskHooks.HOLD_PACKET) { client, _, buf, _ ->
            val fraction = buf.readFloat()
            val label = buf.readString()
            client.execute {
                this.fraction = fraction
                this.label = label
                lastMs = Util.getMeasuringTimeMs()
            }
        }
        HudRenderCallback.EVENT.register { context, _ -> render(context) }
    }

    private fun render(context: DrawContext) {
        val age = Util.getMeasuringTimeMs() - lastMs
        if (lastMs == 0L || age > VISIBLE_MS) return
        val alpha = ((VISIBLE_MS - age).toFloat() / FADE_MS).coerceIn(0f, 1f)
        val a = (alpha * 255).toInt()
        if (a < 8) return
        val font = MinecraftClient.getInstance().textRenderer
        val x = (context.scaledWindowWidth - WIDTH) / 2
        val y = context.scaledWindowHeight - 62
        val text = I18n.translate(label)
        context.drawText(font, text, (context.scaledWindowWidth - font.getWidth(text)) / 2, y - 12, (a shl 24) or 0xFFFFFF, true)
        context.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, (a * 3 / 4 shl 24))
        context.fill(x, y, x + (WIDTH * fraction).toInt(), y + HEIGHT, (a shl 24) or 0xE8E8E8)
    }
}
