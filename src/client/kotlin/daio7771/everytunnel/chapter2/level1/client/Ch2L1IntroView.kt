package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.chapter2.level1.platform.Ch2L1IntroHooks
import daio7771.everytunnel.client.cutscene.CutsceneView
import daio7771.everytunnel.core.cutscene.SceneFrame
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient

/**
 * Chapter 2 Level 1's first loudspeaker announcement on the client: the scene ([KIND], its timeline built by `Ch2L1IntroHooks` from
 * the level's data) that [CutsceneView] plays when the server says it begins, the speaker's name over its subtitles; its end clears the
 * level's own subtitles ([Ch2L1Subtitles]).
 */
object Ch2L1IntroView {

    val KIND = object : CutsceneView.Kind(
        { Ch2L1IntroHooks.clientTimeline() }, Ch2L1IntroHooks.SKIP_PACKET_ID, "every_tunnel.ch2.l1.speaker.rowan_loudspeaker", "voice.", false,
    ) {
        override fun stopped(client: MinecraftClient) = Ch2L1Subtitles.clear()
    }

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(Ch2L1IntroHooks.START_PACKET_ID) { client, _, buf, _ ->
            val (x, y, z, yaw) = Ch2L1IntroHooks.readStart(buf)
            client.execute {
                val at = SceneFrame(x, y, z, yaw)
                if (Ch2L1IntroHooks.clientBegin(at)) CutsceneView.start(client, KIND, at)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(Ch2L1IntroHooks.END_PACKET_ID) { client, _, buf, _ ->
            val skipped = buf.readBoolean()
            client.execute { CutsceneView.stop(client, skipped) }
        }
    }
}
