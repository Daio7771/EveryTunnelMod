package daio7771.everytunnel.chapter2.prologue.client

import daio7771.everytunnel.chapter2.prologue.core.PrologueScene
import daio7771.everytunnel.chapter2.prologue.platform.PrologueSceneHooks
import daio7771.everytunnel.client.cutscene.CutsceneView
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.platform.ChapterRequest
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.Vec3d

/**
 * Chapter 2's prologue on the client: the scene ([KIND]) that [CutsceneView] plays when the server says it begins
 * (`PrologueSceneHooks`), with the laboratory's own sound ([PrologueSound]), its shake, flash and vignette, and its end on black. When
 * the world was opened only for it (the menu's Chapter 2, [ChapterRequest]), its end takes the player back to the menu.
 */
object PrologueView {

    val KIND = object : CutsceneView.Kind({ PrologueScene.SCENE }, PrologueSceneHooks.SKIP_PACKET_ID, null, "voice.", false) {
        override val endsOnBlack = true

        override fun started(client: MinecraftClient, at: SceneFrame) {
            val counter = PrologueScene.COUNTER
            val (x, y, z) = at.at(counter.forward, counter.right, counter.up)
            val (lx, ly, lz) = at.at(0.0, 0.0, 3.2)
            val (hx, hy, hz) = at.at(0.0, 0.0, 1.2)
            PrologueSound.start(client, counterAt = Vec3d(x, y, z), lightsAt = Vec3d(lx, ly, lz), hisAt = Vec3d(hx, hy, hz))
        }

        override fun ticked(client: MinecraftClient, tick: Int) = PrologueSound.tick(client, tick)

        override fun stopped(client: MinecraftClient) = PrologueSound.stop(client)

        override fun shake(n: Double): Double = PrologueScene.shake(n, PrologueScene.PLAN.cues)

        override fun flash(t: Double): Double = PrologueScene.flash(t, PrologueScene.PLAN.cues)

        override fun vignette(t: Double): Double = PrologueScene.vignette(t, PrologueScene.PLAN.cues)
    }

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(PrologueSceneHooks.START_PACKET_ID) { client, _, buf, _ ->
            val (x, y, z, yaw) = PrologueSceneHooks.readStart(buf)
            client.execute { CutsceneView.start(client, KIND, SceneFrame(x, y, z, yaw)) }
        }
        ClientPlayNetworking.registerGlobalReceiver(PrologueSceneHooks.END_PACKET_ID) { client, _, buf, _ ->
            val skipped = buf.readBoolean()
            client.execute {
                CutsceneView.stop(client, skipped)
                // The world was opened only for the prologue (the menu's Chapter 2): back to the menu.
                if (ChapterRequest.chapter == CHAPTER) CutsceneView.leaveForMenu(client)
            }
        }
    }

    /** The chapter the menu asks for to play the prologue alone. */
    const val CHAPTER = 2
}
