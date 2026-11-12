package daio7771.everytunnel.client.cutscene

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.cutscene.DirectorLines
import daio7771.everytunnel.core.cutscene.VoiceLine
import daio7771.everytunnel.platform.DirectorLoopHooks
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.Util

/**
 * The director's phrases after his scene (`platform.DirectorLoopHooks` sends which): each played flat under
 * Voice/Speech, as in his scene, and its subtitles shown above the HUD (over the hotbar and the bars), the same
 * as the scene's ("DIRECTOR:" in grey, then white), on a dark strip so they read anywhere.
 */
object DirectorVoice {

    private val phrases: Map<String, VoiceLine> by lazy { DirectorLines.load().loop.associateBy { it.id } }

    private var line: VoiceLine? = null
    private var startedMs = 0L
    private var sound: SoundInstance? = null

    /** The last cue stays this long after the voice ends. */
    private const val HOLD_MS = 600L

    /** The subtitles' bottom, this far over the screen's bottom: above the hotbar and the bars over it. */
    private const val ABOVE_HUD = 62
    private const val SPEAKER = "subtitle.every_tunnel.speaker.director"
    private const val SPEAKER_GREY = 0x9A9A9A
    private const val WHITE = 0xFFFFFF
    private const val STRIP = 0x88000000.toInt()
    private const val WIDTH = 0.6

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(DirectorLoopHooks.PHRASE_PACKET_ID) { client, _, buf, _ ->
            val id = buf.readString()
            client.execute { say(client, id) }
        }
        HudRenderCallback.EVENT.register { context, _ -> render(context) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { stop(client) } }
    }

    private fun say(client: MinecraftClient, id: String) {
        val phrase = phrases[id] ?: return
        stop(client)
        line = phrase
        startedMs = Util.getMeasuringTimeMs()
        val instance = PositionedSoundInstance(
            Identifier(EveryTunnel.MOD_ID, "voice.director.$id"), SoundCategory.VOICE, 1f, 1f, SoundInstance.createRandom(),
            false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true,
        )
        sound = instance
        client.soundManager.play(instance)
    }

    private fun stop(client: MinecraftClient) {
        sound?.let { client.soundManager.stop(it) }
        sound = null
        line = null
    }

    private fun render(context: DrawContext) {
        val phrase = line ?: return
        val elapsed = Util.getMeasuringTimeMs() - startedMs
        if (elapsed > phrase.ms + HOLD_MS) {
            line = null
            return
        }
        val cue = phrase.cues.lastOrNull { it.atMs <= elapsed } ?: return
        val client = MinecraftClient.getInstance()
        val font = client.textRenderer
        val width = context.scaledWindowWidth
        val text = Text.translatable(SPEAKER).styled { it.withColor(SPEAKER_GREY) }
            .append(Text.literal(" "))
            .append(Text.translatable(cue.key).styled { it.withColor(WHITE) })
        val lines = font.wrapLines(text, (width * WIDTH).toInt())
        val step = font.fontHeight + 2
        val widest = lines.maxOf { font.getWidth(it) }
        var y = context.scaledWindowHeight - ABOVE_HUD - lines.size * step
        context.fill((width - widest) / 2 - 4, y - 3, (width + widest) / 2 + 4, y + lines.size * step + 1, STRIP)
        for (l in lines) {
            context.drawText(font, l, (width - font.getWidth(l)) / 2, y, WHITE, true)
            y += step
        }
    }
}
