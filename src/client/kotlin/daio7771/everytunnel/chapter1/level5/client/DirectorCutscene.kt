package daio7771.everytunnel.chapter1.level5.client

import daio7771.everytunnel.chapter1.level5.platform.DirectorEntity
import daio7771.everytunnel.chapter1.level5.platform.DirectorSceneHooks
import daio7771.everytunnel.common.client.cutscene.CutsceneView
import daio7771.everytunnel.common.core.cutscene.DirectorScene
import daio7771.everytunnel.common.core.cutscene.SceneFrame
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient

/**
 * The director's scene of Chapter 1's Level 5 on the client: a [CutsceneView.Kind] (the engine is `CutsceneView`'s), started and
 * ended by the packets of [DirectorSceneHooks]. The director takes his moves from the scene ([DirectorEntity.poseSource]).
 */
object DirectorCutscene {

    val KIND = object : CutsceneView.Kind({ DirectorScene.SCENE }, DirectorSceneHooks.SKIP_PACKET_ID, "subtitle.every_tunnel.speaker.director", "voice.director.", true) {
        override fun started(client: MinecraftClient, at: SceneFrame) {
            DirectorEntity.poseSource = ::directorPose
        }

        override fun stopped(client: MinecraftClient) {
            DirectorEntity.poseSource = null
        }
    }

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(DirectorSceneHooks.START_PACKET_ID) { client, _, buf, _ ->
            val (x, y, z, yaw) = DirectorSceneHooks.readStart(buf)
            client.execute { CutsceneView.start(client, KIND, SceneFrame(x, y, z, yaw)) }
        }
        ClientPlayNetworking.registerGlobalReceiver(DirectorSceneHooks.END_PACKET_ID) { client, _, buf, _ ->
            val skipped = buf.readBoolean()
            client.execute { CutsceneView.stop(client, skipped) }
        }
    }

    /** The director at this moment of his scene (a skip shows him as he ends it: breathing, the mask off). */
    private fun directorPose(): DirectorEntity.Pose {
        val timeline = DirectorScene.SCENE
        val at = CutsceneView.tick + MinecraftClient.getInstance().tickDelta.toDouble()
        if (CutsceneView.skippedAt != 0L) return DirectorEntity.Pose(null, 0.0, true)
        return DirectorEntity.Pose(timeline.actionAt(at), timeline.gazeAt(at), CutsceneView.tick >= timeline.maskDown)
    }
}
