package daio7771.everytunnel.chapter2.level2.client

import daio7771.everytunnel.chapter2.level2.core.GuardIntro
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2GuardScene
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2GuardScenes
import daio7771.everytunnel.client.cutscene.CutsceneView
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * One of the Observant Guard's cinematics on the client ([Ch2L2GuardScenes]): the scene ([kind], its timeline built by the server's scene
 * from the Guard's timings) that [CutsceneView] plays when the server says it begins, round the place it sends. No speaker, no voice:
 * the Guard has none. The entrance shakes and flashes with [GuardIntro].
 */
class Ch2L2GuardSceneView(
    private val scene: Ch2L2GuardScene,
    private val shakeAt: (Double) -> Double = { 0.0 },
    private val flashAt: (Double) -> Double = { 0.0 },
) {

    val kind = object : CutsceneView.Kind({ scene.clientTimeline() }, scene.skipId, null, "voice.", false) {
        override fun shake(n: Double): Double = shakeAt(n)

        override fun flash(t: Double): Double = flashAt(t)
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

    companion object {
        fun registerAll() {
            Ch2L2GuardSceneView(Ch2L2GuardScenes.INTRO, GuardIntro::shake, GuardIntro::flash).register()
            Ch2L2GuardSceneView(Ch2L2GuardScenes.DEATH).register()
        }
    }
}
