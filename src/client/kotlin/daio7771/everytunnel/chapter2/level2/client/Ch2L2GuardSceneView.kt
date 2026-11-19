package daio7771.everytunnel.chapter2.level2.client

import daio7771.everytunnel.chapter2.level2.core.GuardIntro
import daio7771.everytunnel.chapter2.level2.core.MawDeathScene
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2DataLoader
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2GuardScene
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2GuardScenes
import daio7771.everytunnel.client.cutscene.CutsceneView
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * One of the Observant Guard's cinematics on the client ([Ch2L2GuardScenes]): the scene ([kind], its timeline built by the server's scene
 * from the Guard's timings) that [CutsceneView] plays when the server says it begins, round the place it sends. No speaker, no voice:
 * the Guard has none. The entrance shakes and flashes with [GuardIntro]; Stone Maw's death is third person and draws the player, watching
 * from [Ch2L2Maw]'s watch spot.
 */
class Ch2L2GuardSceneView(
    private val scene: Ch2L2GuardScene,
    private val shakeAt: (Double) -> Double = { 0.0 },
    private val flashAt: (Double) -> Double = { 0.0 },
    drawsPlayer: Boolean = false,
) {

    val kind = object : CutsceneView.Kind({ scene.clientTimeline() }, scene.skipId, null, "voice.", drawsPlayer) {
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
        /**
         * Stone Maw's death (phase 6.2): the camera trembles as the stone cracks, the bits and the slabs falling rumble, the hardest spike is
         * the first slab hitting the creature ([MawDeathScene.Phases.impactAt]), smaller ones follow at the other slabs, then it settles.
         */
        private fun mawShake(tick: Double): Double {
            val phases = MawDeathScene.phases(Ch2L2DataLoader.data.level.maw)
            val crack = (tick - phases.crouchAt) / (phases.collapseAt - phases.crouchAt).coerceAtLeast(1)
            fun spike(at: Int, length: Double, amount: Double) = if (tick < at) 0.0 else (amount * (1.0 - (tick - at) / length)).coerceAtLeast(0.0)
            val rumble = when {
                tick < phases.crouchAt -> 0.0
                tick < phases.collapseAt -> 0.08 + 0.12 * crack.coerceIn(0.0, 1.0)
                tick < phases.impactAt -> 0.3
                else -> 0.0
            }
            val slabs = phases.landings.maxOfOrNull { spike(it, 10.0, 0.4) } ?: 0.0
            val settle = if (tick < phases.impactAt) 0.0 else (0.35 * (1.0 - (tick - phases.impactAt) / (phases.settledAt - phases.impactAt).coerceAtLeast(1))).coerceAtLeast(0.0)
            return maxOf(rumble, slabs, spike(phases.impactAt, 24.0, 1.0), settle)
        }

        fun registerAll() {
            Ch2L2GuardSceneView(Ch2L2GuardScenes.INTRO, GuardIntro::shake, GuardIntro::flash).register()
            Ch2L2GuardSceneView(Ch2L2GuardScenes.DEATH).register()
            Ch2L2GuardSceneView(Ch2L2GuardScenes.MAW_DEATH, shakeAt = ::mawShake, drawsPlayer = true).register()
            Ch2L2ReunionView.register()
        }
    }
}
