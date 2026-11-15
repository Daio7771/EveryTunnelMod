package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.chapter2.level1.core.Ch2L1MeetingScene
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1Act2Hooks
import daio7771.everytunnel.client.cutscene.CutsceneView
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.resource.language.I18n

/**
 * The meeting in the lockers on the client: the scene ([KIND], built by `Ch2L1Act2Hooks` from the level's data, the frame and the door
 * the server sends) that [CutsceneView] plays: the player's own eyes turning to whoever speaks. Its subtitles go a cue at a time (the
 * line's sentences that each cue says, in the player's language), after the name of whoever speaks the line; its end clears the level's
 * own subtitles ([Ch2L1Subtitles]).
 */
object Ch2L1MeetingView {

    val KIND = object : CutsceneView.Kind({ Ch2L1Act2Hooks.clientTimeline() }, Ch2L1Act2Hooks.SKIP_PACKET_ID, null, "voice.", false) {
        override fun stopped(client: MinecraftClient) = Ch2L1Subtitles.clear()

        override fun subtitleText(t: Double): String? {
            val beat = beat(t) ?: return null
            return beat.line.textAt(((t - beat.start) * 50).toLong(), I18n.translate(beat.line.textKey))
        }

        override fun speakerAt(t: Double): String? = beat(t)?.line?.speakerKey

        private fun beat(t: Double): Ch2L1MeetingScene.Beat? = Ch2L1Act2Hooks.clientScene()?.subtitleBeatAt(t)
    }

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(Ch2L1Act2Hooks.START_PACKET_ID) { client, _, buf, _ ->
            val (frame, door) = Ch2L1Act2Hooks.readStart(buf)
            client.execute { if (Ch2L1Act2Hooks.clientBegin(frame, door)) CutsceneView.start(client, KIND, frame) }
        }
        ClientPlayNetworking.registerGlobalReceiver(Ch2L1Act2Hooks.END_PACKET_ID) { client, _, buf, _ ->
            val skipped = buf.readBoolean()
            client.execute { CutsceneView.stop(client, skipped) }
        }
    }
}
