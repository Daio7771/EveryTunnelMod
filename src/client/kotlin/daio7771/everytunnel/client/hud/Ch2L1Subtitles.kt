package daio7771.everytunnel.client.hud

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.ch2l1.Ch2L1VoiceLine
import daio7771.everytunnel.core.ch2l1.Ch2L1Voices
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.resource.language.I18n
import net.minecraft.client.sound.SoundInstance
import net.minecraft.client.sound.SoundInstanceListener
import net.minecraft.util.Util

/**
 * The subtitles of Chapter 2's Level 1 voices ([Ch2L1Voices]): whenever one of them starts playing, however it was
 * started (the level's systems, a command, `/playsound`), its subtitles are shown over the HUD, a cue at a time, each
 * from its time ([SubtitleStrip]: the speaker's name in grey, the text in white): the sentences of the line's translated
 * text that the cue says ([Ch2L1VoiceLine.textAt]). The last one stays a moment after the voice ends. They are always on: they do not depend on the game's own subtitles setting, which these sounds do not feed
 * (their `sounds.json` entries have no `subtitle`). Hears the sound manager through a listener, so it needs no packet
 * and plays nothing itself. A newer voice replaces the one showing. In a cinematic the HUD is hidden, and so are these;
 * the cinematics draw their own cues.
 */
object Ch2L1Subtitles {

    /** The last cue stays this long after the voice ends. */
    private const val HOLD_MS = 600L

    private val voices: Ch2L1Voices by lazy { Ch2L1Voices.load() }

    private var line: Ch2L1VoiceLine? = null
    private var startedMs = 0L

    fun register() {
        ClientLifecycleEvents.CLIENT_STARTED.register { client ->
            client.soundManager.registerListener(SoundInstanceListener { sound, _ -> onSound(sound) })
        }
        HudRenderCallback.EVENT.register { context, _ -> render(context) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> line = null }
    }

    /** Nothing is shown any more (a cinematic that was skipped cut the voice off). */
    fun clear() {
        line = null
    }

    private fun onSound(sound: SoundInstance) {
        val id = sound.id
        if (id.namespace != EveryTunnel.MOD_ID) return
        val found = voices.ofSound(id.path) ?: return
        line = found
        startedMs = Util.getMeasuringTimeMs()
    }

    private fun render(context: DrawContext) {
        val current = line ?: return
        val elapsed = Util.getMeasuringTimeMs() - startedMs
        if (current.isOver(elapsed, HOLD_MS)) {
            line = null
            return
        }
        val text = current.textAt(elapsed, I18n.translate(current.textKey)) ?: return
        SubtitleStrip.draw(context, current.speakerKey, text)
    }
}
