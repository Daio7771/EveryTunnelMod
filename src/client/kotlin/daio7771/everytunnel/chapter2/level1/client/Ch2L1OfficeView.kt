package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.chapter2.level1.core.Ch2L1OfficeScene
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1OfficeHooks
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.common.client.cutscene.CutsceneView
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.resource.language.I18n
import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.random.Random

/**
 * The office's cinematic on the client: the scene ([KIND], built by `Ch2L1OfficeHooks` from the level's data and the frame the server
 * sends) that [CutsceneView] plays: the player's own eyes, held behind the desk, fixed on Rowan. Its subtitles go a cue at a time after
 * his name; a very low hum ([Hum], `ambient.ch2l1.office_hum`) fades in
 * as his lines turn cold and out as he hands the pass over. Its end clears the level's own subtitles.
 */
object Ch2L1OfficeView {

    private var hum: Hum? = null

    val KIND = object : CutsceneView.Kind({ Ch2L1OfficeHooks.clientTimeline() }, Ch2L1OfficeHooks.SKIP_PACKET_ID, null, "voice.", false) {
        override fun ticked(client: MinecraftClient, tick: Int) {
            val built = scene() ?: return
            val wanted = tick in built.humStart until built.humEnd
            if (wanted && hum == null) hum = Hum().also { client.soundManager.play(it) }
            hum?.target = if (wanted) 1f else 0f
        }

        override fun stopped(client: MinecraftClient) {
            Ch2L1Subtitles.clear()
            hum?.target = 0f
            hum = null
        }

        override fun subtitleText(t: Double): String? {
            val beat = scene()?.subtitleBeatAt(t) ?: return null
            return beat.line.textAt(((t - beat.start) * 50).toLong(), I18n.translate(beat.line.textKey))
        }

        override fun speakerAt(t: Double): String? = scene()?.subtitleBeatAt(t)?.line?.speakerKey
    }

    private fun scene(): Ch2L1OfficeScene.Built? = Ch2L1OfficeHooks.clientScene()

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(Ch2L1OfficeHooks.START_PACKET_ID) { client, _, buf, _ ->
            val frame = Ch2L1OfficeHooks.readStart(buf)
            client.execute { if (Ch2L1OfficeHooks.clientBegin(frame)) CutsceneView.start(client, KIND, frame) }
        }
        ClientPlayNetworking.registerGlobalReceiver(Ch2L1OfficeHooks.END_PACKET_ID) { client, _, buf, _ ->
            val skipped = buf.readBoolean()
            client.execute { CutsceneView.stop(client, skipped) }
        }
    }

    /** The hum, looping, its volume easing towards [target]; it stops itself once faded out. */
    private class Hum : MovingSoundInstance(Ch2Sounds.CH2L1_OFFICE_HUM, SoundCategory.AMBIENT, Random.create()) {
        var target = 1f

        init {
            repeat = true
            repeatDelay = 0
            volume = 0.01f
            relative = true
            attenuationType = SoundInstance.AttenuationType.NONE
        }

        override fun tick() {
            volume += (target * MAX - volume) * 0.08f
            if (target == 0f && volume < 0.01f) setDone()
        }

        companion object {
            private const val MAX = 0.8f
        }
    }
}
