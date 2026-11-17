package daio7771.everytunnel.chapter2.prologue.client

import daio7771.everytunnel.chapter2.prologue.core.PrologueScene
import daio7771.everytunnel.chapter2.prologue.platform.PrologueSceneHooks
import daio7771.everytunnel.client.cutscene.CutsceneView
import daio7771.everytunnel.client.effects.ScreenFade
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.platform.ChapterRequest
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.Vec3d

/**
 * Chapter 2's prologue on the client: the scene ([KIND]) that [CutsceneView] plays when the server says it begins
 * (`PrologueSceneHooks`), with the laboratory's own sound ([PrologueSound]), its shake, flash and vignette, and its end on black. When
 * the world was opened for Chapter 2 (the menu's, [ChapterRequest]), the server takes the player into the factory at its end: the black
 * stays until they are in it (their Chapter 2 progress says they play Level 1), or at most [WAIT_TICKS], then lifts ([FACTORY_FADE_S]).
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
                if (ChapterRequest.chapter == PrologueSceneHooks.MENU_CHAPTER) {
                    ScreenFade.fadeTo(1f, 0f)
                    waiting = WAIT_TICKS
                }
            }
        }
        // Out of the black once the player is in the factory (or it is given up on).
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (waiting <= 0) return@register
            val inFactory = client.player?.let(Ch2Progress::ch2L1Active) == true
            if (inFactory || --waiting <= 0 || client.world == null) {
                waiting = 0
                ScreenFade.fadeTo(0f, FACTORY_FADE_S)
            }
        }
    }

    /** Ticks left of the black kept after the prologue the menu asked for (0: none). */
    private var waiting = 0

    /** The most the black waits for the factory: the level's first build is a second or two. */
    private const val WAIT_TICKS = 300

    /** The black's lifting in the factory. */
    private const val FACTORY_FADE_S = 1.5f
}
