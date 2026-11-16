package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1VoiceLine
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Voices
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.resource.language.I18n
import net.minecraft.client.sound.SoundInstance
import net.minecraft.client.sound.SoundInstanceListener
import net.minecraft.util.Util
import daio7771.everytunnel.client.hud.SubtitleStrip

/**
 * The subtitles of Chapter 2's Level 1 voices ([Ch2L1Voices]): whenever one of them starts playing, however it was
 * started (the level's systems, a command, `/playsound`), its subtitles are shown over the HUD, a cue at a time, each
 * from its time ([SubtitleStrip]: the speaker's name in grey, the text in white): the sentences of the line's translated
 * text that the cue says ([Ch2L1VoiceLine.textAt]). The last one stays a moment after the voice ends. They are always on: they do not depend on the game's own subtitles setting, which these sounds do not feed
 * (their `sounds.json` entries have no `subtitle`). Hears the sound manager through a listener, so it needs no packet
 * and plays nothing itself. A newer voice replaces the one showing. In a cinematic the HUD is hidden, and so are these;
 * the cinematics draw their own cues. A line with no voice yet (a guard's) is shown the same way for a while ([showText]).
 */
object Ch2L1Subtitles {

    /** The last cue stays this long after the voice ends. */
    private const val HOLD_MS = 600L

    private val voices: Ch2L1Voices by lazy { Ch2L1Voices.load() }

    private var line: Ch2L1VoiceLine? = null
    private var startedMs = 0L

    /** A line with no voice: its speaker's and text's lang keys, until when (client time). */
    private var text: Triple<String, String, Long>? = null

    fun register() {
        ClientLifecycleEvents.CLIENT_STARTED.register { client ->
            client.soundManager.registerListener(SoundInstanceListener { sound, _ -> onSound(sound) })
        }
        HudRenderCallback.EVENT.register { context, _ -> render(context) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> clear() }
    }

    /** Nothing is shown any more (a cinematic that was skipped cut the voice off). */
    fun clear() {
        line = null
        text = null
    }

    /** Shows the text of [textKey] after the name of [speakerKey] for [ms], as a voice's subtitle (a line not voiced yet). */
    fun showText(speakerKey: String, textKey: String, ms: Long) {
        line = null
        text = Triple(speakerKey, textKey, Util.getMeasuringTimeMs() + ms)
    }

    private fun onSound(sound: SoundInstance) {
        val id = sound.id
        if (id.namespace != EveryTunnel.MOD_ID) return
        val found = voices.ofSound(id.path) ?: return
        line = found
        text = null
        startedMs = Util.getMeasuringTimeMs()
    }

    private fun render(context: DrawContext) {
        text?.let { (speaker, key, untilMs) ->
            if (Util.getMeasuringTimeMs() > untilMs) text = null else SubtitleStrip.draw(context, speaker, I18n.translate(key))
            return
        }
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
