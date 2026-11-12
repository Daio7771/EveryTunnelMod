package daio7771.everytunnel.client.cutscene

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.effects.ScreenFade
import daio7771.everytunnel.core.cutscene.CameraPose
import daio7771.everytunnel.core.cutscene.DirectorScene
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.core.cutscene.SkipPrompt
import daio7771.everytunnel.platform.DirectorSceneHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.Perspective
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EntityType
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import net.minecraft.world.World

/**
 * The director's scene on the client ([DirectorScene], started by `platform.DirectorSceneHooks`): the view
 * leaves the player for a camera of its own, which [DirectorScene.SCENE] moves tick by tick (the game blends
 * between ticks; a cut jumps), the HUD goes (`InGameHudMixin`), and a [CutsceneScreen] takes the keys and the
 * mouse and draws the bands, his subtitles and the notice to skip. The player stays where they stood, seen
 * from outside. His lines play on their ticks, flat (the same in every shot), under Voice/Speech; nothing
 * else of the scene makes a sound.
 *
 * Skipping (the jump key twice, [SkipPrompt]) fades to black and asks the server to end it; its answer, like
 * the scene's own end, gives the player their view back ([stop]), out of black if skipped, his voice cut.
 */
object CutsceneView {

    /** The camera's eyes are this high over its feet: a standing player's, so handing the view over moves nothing. */
    private const val EYE = 1.62f

    /** Skipping fades to black this fast before the scene ends, and out of it this slowly after. */
    private const val SKIP_FADE_MS = 250L
    private const val SKIP_FADE_OUT_S = 0.6f

    private var frame: SceneFrame? = null
    private var eyes = CameraPose(0.0, 0.0, 0.0, 0.0, 0.0)
    private var tick = 0
    private var camera: Entity? = null
    private var hudWasHidden = false
    private var perspective = Perspective.FIRST_PERSON
    private var skippedAt = 0L
    private var voice: SoundInstance? = null

    val skip = SkipPrompt()

    @JvmStatic
    fun active(): Boolean = frame != null

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(DirectorSceneHooks.START_PACKET_ID) { client, _, buf, _ ->
            val (x, y, z, yaw) = DirectorSceneHooks.readStart(buf)
            client.execute { start(client, SceneFrame(x, y, z, yaw)) }
        }
        ClientPlayNetworking.registerGlobalReceiver(DirectorSceneHooks.END_PACKET_ID) { client, _, buf, _ ->
            val skipped = buf.readBoolean()
            client.execute { stop(client, skipped) }
        }
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { stop(client, false) } }
    }

    private fun start(client: MinecraftClient, at: SceneFrame) {
        val player = client.player ?: return
        val world = client.world ?: return
        if (active()) stop(client, false)
        frame = at
        eyes = CameraPose(player.x, player.eyeY, player.z, player.yaw.toDouble(), player.pitch.toDouble())
        tick = 0
        skippedAt = 0L
        camera = CameraEntity(world).also { place(it, 0) }
        hudWasHidden = client.options.hudHidden
        perspective = client.options.perspective
        client.options.hudHidden = true
        client.options.perspective = Perspective.FIRST_PERSON
        client.setCameraEntity(camera)
        client.setScreen(CutsceneScreen())
    }

    /** The view back to the player, out of black if [skipped]. Safe to call when nothing plays. */
    fun stop(client: MinecraftClient, skipped: Boolean) {
        if (!active()) return
        frame = null
        camera = null
        voice?.let { client.soundManager.stop(it) }
        voice = null
        client.options.hudHidden = hudWasHidden
        client.options.perspective = perspective
        client.player?.let { client.setCameraEntity(it) }
        if (client.currentScreen is CutsceneScreen) client.setScreen(null)
        if (skipped) {
            ScreenFade.fadeTo(1f, 0f)
            ScreenFade.fadeTo(0f, SKIP_FADE_OUT_S)
        }
    }

    /** The jump key, pressed on the [CutsceneScreen]: the first time a notice, the second a skip. */
    fun skipPressed() {
        if (skippedAt != 0L || !skip.press(Util.getMeasuringTimeMs())) return
        skippedAt = Util.getMeasuringTimeMs()
        ClientPlayNetworking.send(DirectorSceneHooks.SKIP_PACKET_ID, PacketByteBufs.empty())
    }

    /** How far the bands are in, at this frame. */
    fun bands(tickDelta: Float): Double = DirectorScene.SCENE.cutscene.bands(tick + tickDelta.toDouble())

    /** The lang key of his subtitle on screen at this frame, or null. */
    fun subtitle(tickDelta: Float): String? =
        if (skippedAt != 0L) null else DirectorScene.SCENE.subtitleAt(tick + tickDelta.toDouble())?.key

    /** The black of a skip on its way, at this frame: 0 none, 1 all. */
    fun skipBlack(): Float =
        if (skippedAt == 0L) 0f else ((Util.getMeasuringTimeMs() - skippedAt) / SKIP_FADE_MS.toFloat()).coerceIn(0f, 1f)

    private fun tick(client: MinecraftClient) {
        val cam = camera ?: return
        val timeline = DirectorScene.SCENE
        if (tick < timeline.cutscene.totalTicks) tick++
        place(cam, tick)
        if (skippedAt == 0L) {
            timeline.lines.firstOrNull { it.start == tick }?.let { speak(client, it.line.id) }
        }
        // Another screen may have taken over (and gone): the keys stay the scene's.
        if (client.currentScreen == null) client.setScreen(CutsceneScreen())
    }

    /** Puts [cam] at [n] ticks, on its way to [n] + 1 (the game blends between); across a cut, at [n] + 1 already. */
    private fun place(cam: Entity, n: Int) {
        val at = frame ?: return
        val scene = DirectorScene.SCENE.cutscene
        val player = MinecraftClient.getInstance().player
        val playerEyes = if (player != null) Triple(player.x, player.eyeY, player.z) else Triple(eyes.x, eyes.y, eyes.z)
        val next = scene.pose(n + 1.0, at, eyes, playerEyes)
        val now = if (scene.shotAt(n.toDouble()) != scene.shotAt(n + 1.0) && scene.shotAt(n.toDouble()) >= 0) {
            next
        } else {
            scene.pose(n.toDouble(), at, eyes, playerEyes)
        }
        val then = next.yawNear(now.yaw)
        cam.prevX = now.x
        cam.prevY = now.y - EYE
        cam.prevZ = now.z
        cam.prevYaw = now.yaw.toFloat()
        cam.prevPitch = now.pitch.toFloat()
        cam.setPos(then.x, then.y - EYE, then.z)
        cam.yaw = then.yaw.toFloat()
        cam.pitch = then.pitch.toFloat()
    }

    private fun speak(client: MinecraftClient, id: String) {
        voice?.let { client.soundManager.stop(it) }
        val sound = PositionedSoundInstance(
            Identifier(EveryTunnel.MOD_ID, "voice.director.$id"), SoundCategory.VOICE, 1f, 1f, SoundInstance.createRandom(),
            false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true,
        )
        voice = sound
        client.soundManager.play(sound)
    }

    /** A camera nobody sees: never in the world, its eyes [EYE] over its feet. */
    private class CameraEntity(world: World) : Entity(EntityType.MARKER, world) {
        override fun initDataTracker() = Unit
        override fun readCustomDataFromNbt(nbt: NbtCompound) = Unit
        override fun writeCustomDataToNbt(nbt: NbtCompound) = Unit
        override fun getEyeHeight(pose: EntityPose, dimensions: EntityDimensions): Float = EYE
        override fun createSpawnPacket(): Packet<ClientPlayPacketListener> = throw UnsupportedOperationException("client only")
    }
}
