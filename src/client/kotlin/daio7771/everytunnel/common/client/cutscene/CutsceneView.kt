package daio7771.everytunnel.common.client.cutscene

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.common.client.effects.ScreenFade
import daio7771.everytunnel.common.core.cutscene.CameraPose
import daio7771.everytunnel.common.core.cutscene.DirectorScene
import daio7771.everytunnel.common.core.cutscene.SceneFrame
import daio7771.everytunnel.common.core.cutscene.SkipPrompt
import daio7771.everytunnel.common.core.cutscene.Timeline
import daio7771.everytunnel.common.core.cutscene.TitleCue
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.MessageScreen
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.resource.language.I18n
import net.minecraft.client.option.Perspective
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EntityType
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/**
 * A cinematic on the client: the director's scene ([DirectorScene], started by `chapter1.level5.platform.DirectorSceneHooks`) or a later
 * chapter's (Chapter 2's prologue and its Level 1's first announcement, which that chapter's client starts with [start]), each a [Kind]. The view leaves the
 * player for a camera of its own, which the scene's timeline moves tick by tick (the game blends between ticks;
 * a cut jumps), the HUD goes (`InGameHudMixin`), and a [CutsceneScreen] takes the keys and the mouse and draws
 * the bands, the subtitles and the notice to skip, and the black a scene opens out of. In the director's scene
 * the player stays where they stood, seen from outside ([drawPlayer]); the prologue never draws them. Voice
 * lines play on their ticks, flat (the same in every shot), under Voice/Speech; anything else a scene does (its own sounds, a
 * shake, a flash) is its [Kind]'s. The director takes his moves from it ([DirectorEntity.poseSource]).
 *
 * Skipping (the jump key twice, [SkipPrompt]) fades to black and asks the server to end it; its answer, like
 * the scene's own end, gives the player their view back ([stop]), out of black if skipped, the voice cut.
 */
object CutsceneView {

    /**
     * A scene this view plays: its [timeline], the packet that skips it, the lang key of the speaker's name before the subtitles
     * (null: none), the prefix of its voice sounds, and whether the player is drawn. What else it does is its own: a chapter's
     * scenes override [started], [ticked] and [stopped] (their own sounds), [shake], [flash] and [vignette] (0: none), and
     * [endsOnBlack] (it gives the view back out of black even when not skipped).
     */
    open class Kind(
        val timeline: () -> Timeline,
        val skipPacket: Identifier,
        val speaker: String?,
        val voicePrefix: String,
        val drawsPlayer: Boolean,
    ) {
        open val endsOnBlack: Boolean = false

        open fun started(client: MinecraftClient, at: SceneFrame) {}

        open fun ticked(client: MinecraftClient, tick: Int) {}

        open fun stopped(client: MinecraftClient) {}

        /** How hard the camera shakes at tick [n], 0 to 1. */
        open fun shake(n: Double): Double = 0.0

        /** A white flash (0..1) and a dark vignette (0..1) at tick [t]. */
        open fun flash(t: Double): Double = 0.0

        open fun vignette(t: Double): Double = 0.0

        /** The subtitle's text at tick [t], already translated, instead of the timeline's (null: the timeline's, if any). */
        open fun subtitleText(t: Double): String? = null

        /** The lang key of the speaker's name before the subtitle at tick [t] (a scene with several speakers changes it). */
        open fun speakerAt(t: Double): String? = speaker
    }

    /** The camera's eyes are this high over its feet: a standing player's, so handing the view over moves nothing. */
    private const val EYE = 1.62f

    /** Skipping fades to black this fast before the scene ends, and out of it this slowly after. */
    private const val SKIP_FADE_MS = 250L
    private const val SKIP_FADE_OUT_S = 0.6f

    /** At full strength a scene's shake moves the camera this far (blocks) and turns it this far (degrees). */
    private const val SHAKE_MOVE = 0.05
    private const val SHAKE_TURN = 2.4

    /** The player is not drawn with the camera nearer their eyes than this (blocks). */
    private const val NEAR = 1.2

    private var frame: SceneFrame? = null
    private var kind: Kind = Kind({ DirectorScene.SCENE }, Identifier(EveryTunnel.MOD_ID, "no_scene"), null, "", true)
    private var eyes = CameraPose(0.0, 0.0, 0.0, 0.0, 0.0)
    /** The scene's tick, for the pose of a scene's actor (the director's: `DirectorCutscene`). */
    var tick = 0
        private set
    private var camera: Entity? = null
    private var hudWasHidden = false
    private var perspective = Perspective.FIRST_PERSON
    /** When a skip began (`Util.getMeasuringTimeMs`), 0 if none. */
    var skippedAt = 0L
        private set
    private var voice: SoundInstance? = null

    val skip = SkipPrompt()

    @JvmStatic
    fun active(): Boolean = frame != null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        WorldRenderEvents.AFTER_ENTITIES.register(::drawPlayer)
        ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { stop(client, false) } }
    }

    /** Plays [scene] from [at] (a chapter's client calls this when its server says the scene begins). */
    fun start(client: MinecraftClient, scene: Kind, at: SceneFrame) {
        val player = client.player ?: return
        val world = client.world ?: return
        if (active()) stop(client, false)
        kind = scene
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
        scene.started(client, at)
    }

    /** Leaves the world, saving it, for the main menu (the title screen is the menu: `MinecraftClientMixin`). */
    fun leaveForMenu(client: MinecraftClient) {
        val singleplayer = client.isInSingleplayer
        client.world?.disconnect()
        if (singleplayer) client.disconnect(MessageScreen(Text.translatable("menu.savingLevel"))) else client.disconnect()
        client.setScreen(TitleScreen())
    }

    /** The view back to the player, out of black if [skipped]. Safe to call when nothing plays. */
    fun stop(client: MinecraftClient, skipped: Boolean) {
        if (!active()) return
        val scene = kind
        frame = null
        camera = null
        scene.stopped(client)
        voice?.let { client.soundManager.stop(it) }
        voice = null
        client.options.hudHidden = hudWasHidden
        client.options.perspective = perspective
        client.player?.let { client.setCameraEntity(it) }
        if (client.currentScreen is CutsceneScreen) client.setScreen(null)
        // A skip, and the end of a scene that ends on black, give the view back out of black.
        if (skipped || scene.endsOnBlack) {
            ScreenFade.fadeTo(1f, 0f)
            ScreenFade.fadeTo(0f, SKIP_FADE_OUT_S)
        }
    }

    /** The jump key, pressed on the [CutsceneScreen]: the first time a notice, the second a skip. */
    fun skipPressed() {
        if (skippedAt != 0L || !skip.press(Util.getMeasuringTimeMs())) return
        skippedAt = Util.getMeasuringTimeMs()
        ClientPlayNetworking.send(kind.skipPacket, PacketByteBufs.empty())
    }

    /** How far the bands are in, at this frame. */
    fun bands(tickDelta: Float): Double = kind.timeline().cutscene.bands(tick + tickDelta.toDouble())

    /** The subtitle on screen at this frame (translated), or null. */
    fun subtitle(tickDelta: Float): String? {
        if (skippedAt != 0L) return null
        val t = tick + tickDelta.toDouble()
        return kind.subtitleText(t) ?: kind.timeline().subtitleAt(t)?.key?.let { I18n.translate(it) }
    }

    /** The lang key of the speaker's name to show before the subtitles, or null for none. */
    fun speaker(): String? = kind.speakerAt(tick.toDouble())

    /** The title the scene types on its black, if it has one, and this frame's tick (fractional). */
    fun title(): TitleCue? = kind.timeline().title

    fun sceneTick(tickDelta: Float): Double = tick + tickDelta.toDouble()

    /** The black a scene opens out of, and falls into at its cut, at this frame: 0 none, 1 all. */
    fun openingBlack(tickDelta: Float): Float = kind.timeline().blackAt(tick + tickDelta.toDouble()).toFloat()

    /**
     * The player, seen from the scene's camera: the game leaves its own player out whenever the view is another
     * entity's (as when spectating), so it is drawn here, with the world's other entities; but not while the camera
     * is still in (or just out of) their head.
     */
    private fun drawPlayer(context: WorldRenderContext) {
        if (!active() || !kind.drawsPlayer) return
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val matrices = context.matrixStack() ?: return
        val consumers = context.consumers() ?: return
        val delta = context.tickDelta()
        val cam = context.camera().pos
        val x = MathHelper.lerp(delta.toDouble(), player.prevX, player.x) - cam.x
        val y = MathHelper.lerp(delta.toDouble(), player.prevY, player.y) - cam.y
        val z = MathHelper.lerp(delta.toDouble(), player.prevZ, player.z) - cam.z
        if (x * x + (y + player.standingEyeHeight) * (y + player.standingEyeHeight) + z * z < NEAR * NEAR) return
        standStill(player)
        val dispatcher = client.entityRenderDispatcher
        val yaw = MathHelper.lerp(delta, player.prevYaw, player.yaw)
        dispatcher.render(player, x, y, z, yaw, delta, matrices, consumers, dispatcher.getLight(player, delta))
    }

    /**
     * The player stands still in the scene's shots: no step left in their legs from walking to the door, nor from the
     * server putting them back in place (each of its moves would count as a step), and no drift.
     */
    private fun standStill(player: PlayerEntity) {
        player.limbAnimator.setSpeed(0f)
        player.velocity = Vec3d.ZERO
    }

    /** The black of a skip on its way, at this frame: 0 none, 1 all. */
    fun skipBlack(): Float =
        if (skippedAt == 0L) 0f else ((Util.getMeasuringTimeMs() - skippedAt) / SKIP_FADE_MS.toFloat()).coerceIn(0f, 1f)

    private fun tick(client: MinecraftClient) {
        val cam = camera ?: return
        client.player?.let(::standStill)
        val timeline = kind.timeline()
        if (tick < timeline.cutscene.totalTicks) tick++
        place(cam, tick)
        if (skippedAt == 0L) {
            timeline.lines.firstOrNull { it.start == tick }?.let { speak(client, it.line.id) }
            kind.ticked(client, tick)
        }
        // Another screen may have taken over (and gone): the keys stay the scene's.
        if (client.currentScreen == null) client.setScreen(CutsceneScreen())
    }

    /** Puts [cam] at [n] ticks, on its way to [n] + 1 (the game blends between); across a cut, at [n] + 1 already. */
    private fun place(cam: Entity, n: Int) {
        val at = frame ?: return
        val scene = kind.timeline().cutscene
        val player = MinecraftClient.getInstance().player
        val playerEyes = if (player != null) Triple(player.x, player.eyeY, player.z) else Triple(eyes.x, eyes.y, eyes.z)
        // The way back is into their eyes now: the scene puts them inside the door.
        val back = if (player != null) CameraPose(player.x, player.eyeY, player.z, player.yaw.toDouble(), player.pitch.toDouble()) else eyes
        val next = scene.pose(n + 1.0, at, eyes, playerEyes, back)
        val now = if (scene.shotAt(n.toDouble()) != scene.shotAt(n + 1.0) && scene.shotAt(n.toDouble()) >= 0) {
            next
        } else {
            scene.pose(n.toDouble(), at, eyes, playerEyes, back)
        }
        val before = shaken(now, n)
        val after = shaken(next.yawNear(now.yaw), n + 1)
        val then = after.yawNear(before.yaw)
        cam.prevX = before.x
        cam.prevY = before.y - EYE
        cam.prevZ = before.z
        cam.prevYaw = before.yaw.toFloat()
        cam.prevPitch = before.pitch.toFloat()
        cam.setPos(then.x, then.y - EYE, then.z)
        cam.yaw = then.yaw.toFloat()
        cam.pitch = then.pitch.toFloat()
    }

    /** [pose] shaken as the scene says at tick [n]: a little in place and in aim, by noise that is the same every time. */
    private fun shaken(pose: CameraPose, n: Int): CameraPose {
        val amount = kind.shake(n.toDouble())
        if (amount <= 0.0) return pose
        fun noise(axis: Int): Double {
            val v = Math.sin(n * 12.9898 + axis * 78.233) * 43758.5453
            return (v - Math.floor(v)) * 2.0 - 1.0
        }
        return CameraPose(
            pose.x + noise(0) * SHAKE_MOVE * amount, pose.y + noise(1) * SHAKE_MOVE * amount, pose.z + noise(2) * SHAKE_MOVE * amount,
            pose.yaw + noise(3) * SHAKE_TURN * amount, pose.pitch + noise(4) * SHAKE_TURN * amount,
        )
    }

    /** The scene's white flash (0..1) and dark vignette (0..1) at this frame; 0 in a scene without them. */
    fun flash(tickDelta: Float): Float = kind.flash(tick + tickDelta.toDouble()).toFloat()

    fun vignette(tickDelta: Float): Float = kind.vignette(tick + tickDelta.toDouble()).toFloat()

    private fun speak(client: MinecraftClient, id: String) {
        voice?.let { client.soundManager.stop(it) }
        val sound = PositionedSoundInstance(
            Identifier(EveryTunnel.MOD_ID, "${kind.voicePrefix}$id"), SoundCategory.VOICE, 1f, 1f, SoundInstance.createRandom(),
            false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true,
        )
        voice = sound
        client.soundManager.play(sound)
        EveryTunnel.LOGGER.info("Cutscene voice {}{} at tick {}", kind.voicePrefix, id, tick)
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
