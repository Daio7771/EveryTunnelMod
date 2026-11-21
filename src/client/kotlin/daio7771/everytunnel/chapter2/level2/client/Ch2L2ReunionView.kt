package daio7771.everytunnel.chapter2.level2.client

import daio7771.everytunnel.chapter2.level2.core.ReunionScene
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2Reunion
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2Scenes
import daio7771.everytunnel.common.client.cutscene.CutsceneView
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * The reunion with the coworker and worker B on the client (phase 6.1): the scene ([Ch2L2Scenes.REUNION], its timeline built from the data
 * as the server does) that [CutsceneView] plays: the player's own eyes turning to whoever speaks. The subtitle is the timeline's (its
 * lang key under `reunion.text`); the speaker's name before it is Level 1's (`every_tunnel.ch2.l1.speaker.<skin>`).
 */
object Ch2L2ReunionView {

    private val scene = Ch2L2Scenes.REUNION

    private val kind = object : CutsceneView.Kind({ scene.clientTimeline() }, scene.skipId, null, "voice.", false) {
        override fun speakerAt(t: Double): String? {
            val built: ReunionScene.Built = Ch2L2Reunion.built()
            val beat = built.beats.lastOrNull { t >= it.start && t < it.end + ReunionScene.HOLD_TICKS } ?: return null
            return "every_tunnel.ch2.l1.speaker.${beat.line.who}"
        }
    }

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(scene.startId) { client, _, buf, _ ->
            val (frame, extras) = scene.readStart(buf)
            client.execute { if (scene.clientBegin(extras)) CutsceneView.start(client, kind, frame) }
        }
        ClientPlayNetworking.registerGlobalReceiver(scene.endId) { client, _, buf, _ ->
            val skipped = buf.readBoolean()
            client.execute { CutsceneView.stop(client, skipped) }
        }
    }
}
