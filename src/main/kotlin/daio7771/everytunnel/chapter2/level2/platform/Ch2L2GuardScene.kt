package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level2.core.GuardIntro
import daio7771.everytunnel.core.cutscene.Cutscene
import daio7771.everytunnel.core.cutscene.Local
import daio7771.everytunnel.core.cutscene.Look
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.core.cutscene.Shot
import daio7771.everytunnel.core.cutscene.Timeline
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import java.util.UUID

/**
 * A cinematic of the Observant Guard (Chapter 2 Level 2, phase 5), as the server sees it: [start] holds the player where they stand for
 * the scene's length, tells the client to begin ([startId], which carries a place and some numbers for [build]; `client.Ch2L2GuardSceneView`
 * plays it with `CutsceneView`, building the same [Timeline] from them) and gives the player back at the end ([endId]), looking where [start]
 * says if it says (the entrance ends with the player looking at the Guard). A skip from the
 * client ([skipId]) ends it at once and runs [onSkip], which leaves the same world as watching it. Leaving the world drops it. Server
 * thread, but [build] is also read by the client (a single-player game shares the data). The two scenes are in [Ch2L2GuardScenes].
 */
class Ch2L2GuardScene(
    key: String,
    private val build: (DoubleArray) -> Timeline,
    private val onSkip: (MinecraftServer, ServerPlayerEntity) -> Unit,
) {
    val startId = Identifier(EveryTunnel.MOD_ID, "ch2l2_guard_${key}_start")
    val endId = Identifier(EveryTunnel.MOD_ID, "ch2l2_guard_${key}_end")
    val skipId = Identifier(EveryTunnel.MOD_ID, "ch2l2_guard_${key}_skip")

    private class Running(
        val player: UUID, val startTick: Long, val at: Vec3d, var yaw: Float, var pitch: Float, val total: Int,
        /** The player is turned to [endYaw], [endPitch] (if given) at this tick of the scene, while the camera is not yet back in their head. */
        val endLookAt: Int, val endYaw: Float?, val endPitch: Float?,
    ) {
        var turned = false
    }

    private var current: Running? = null

    /** The scene is playing. */
    val running: Boolean get() = current != null

    @Volatile
    private var clientTimeline: Timeline? = null

    fun register() {
        ServerPlayNetworking.registerGlobalReceiver(skipId) { server, player, _, _, _ ->
            server.execute { if (current?.player == player.uuid) finish(server, skipped = true) }
        }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> if (current?.player == handler.player.uuid) current = null }
    }

    /**
     * Plays the scene for [player] round [frame] with [extras] for [build]; false if it cannot (one is playing, or the client cannot receive
     * it). [endLook] (yaw, pitch in degrees): at tick [endLookAt] of the scene the player is turned that way, so that the camera, flying
     * back into their eyes at the end, comes back to what they will be looking at.
     */
    fun start(
        player: ServerPlayerEntity, frame: SceneFrame, extras: DoubleArray = DoubleArray(0), endLook: DoubleArray? = null, endLookAt: Int = 0,
    ): Boolean {
        if (current != null || !ServerPlayNetworking.canSend(player, startId)) return false
        val total = build(extras).cutscene.totalTicks
        current = Running(
            player.uuid, player.server.ticks.toLong(), player.pos, player.yaw, player.pitch, total,
            endLookAt, endLook?.get(0)?.toFloat(), endLook?.get(1)?.toFloat(),
        )
        val buf = PacketByteBufs.create()
        buf.writeDouble(frame.x)
        buf.writeDouble(frame.y)
        buf.writeDouble(frame.z)
        buf.writeDouble(frame.yaw)
        buf.writeInt(extras.size)
        extras.forEach(buf::writeDouble)
        ServerPlayNetworking.send(player, startId, buf)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: the Guard's {} cinematic begins for {} ({} ticks)", startId.path, player.gameProfile.name, total)
        return true
    }

    /** The frame and the extras, as [start] wrote them. */
    fun readStart(buf: PacketByteBuf): Pair<SceneFrame, DoubleArray> {
        val frame = SceneFrame(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble())
        return frame to DoubleArray(buf.readInt()) { buf.readDouble() }
    }

    /** The client begins the scene. */
    fun clientBegin(extras: DoubleArray): Boolean {
        clientTimeline = build(extras)
        return true
    }

    /** The timeline the client plays (a bare one when none: it is never drawn then). */
    fun clientTimeline(): Timeline = clientTimeline ?: build(DoubleArray(3))

    private fun tick(server: MinecraftServer) {
        val scene = current ?: return
        val player = server.playerManager.getPlayer(scene.player)
        if (player == null) {
            current = null
            return
        }
        val t = server.ticks - scene.startTick
        if (t >= scene.total) {
            finish(server, skipped = false)
            return
        }
        if (!scene.turned && scene.endYaw != null && scene.endPitch != null && t >= scene.endLookAt) {
            scene.turned = true
            scene.yaw = scene.endYaw
            scene.pitch = scene.endPitch
            player.networkHandler.requestTeleport(scene.at.x, scene.at.y, scene.at.z, scene.yaw, scene.pitch)
        }
        // Held there: the client takes their keys, this keeps them there whatever it does.
        if (player.pos.squaredDistanceTo(scene.at) > 0.0025) {
            player.networkHandler.requestTeleport(scene.at.x, scene.at.y, scene.at.z, scene.yaw, scene.pitch)
        }
        player.velocity = Vec3d.ZERO
    }

    /** The scene's end, played through or skipped (a skip runs [onSkip] at once). */
    private fun finish(server: MinecraftServer, skipped: Boolean) {
        val scene = current ?: return
        current = null
        val player = server.playerManager.getPlayer(scene.player) ?: return
        if (ServerPlayNetworking.canSend(player, endId)) {
            val buf = PacketByteBufs.create()
            buf.writeBoolean(skipped)
            ServerPlayNetworking.send(player, endId, buf)
        }
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: the Guard's {} cinematic {}", startId.path, if (skipped) "skipped" else "ends")
        if (skipped) onSkip(server, player)
    }
}

/**
 * The Observant Guard's two cinematics: its **entrance** ([INTRO]: in first person, through the player's own eyes, it punches the wall open
 * from its nest, steps out, points at the player and beats its chest with both fists; no words, it has no voice) and its **death** ([DEATH]: it falls to the floor and comes apart). Their
 * timelines are built from the Guard's own timings (`guard.intro_seconds`, `guard.death_seconds`) so that they end as the fight's phase does.
 */
object Ch2L2GuardScenes {

    private const val FLY_OUT = GuardIntro.FLY_OUT

    val INTRO = Ch2L2GuardScene("intro", ::introTimeline) { server, _ -> Ch2L2Guard.skipIntro(server) }

    val DEATH = Ch2L2GuardScene("death", ::deathTimeline) { server, player -> Ch2L2Guard.finish(server, player) }

    fun register() {
        INTRO.register()
        DEATH.register()
    }

    private fun ticks(seconds: Double) = Math.round(seconds * 20.0).toInt()

    /** The entrance in first person ([GuardIntro.timeline]): [extras] are the player's eyes in the nest's frame (forward, right, up). */
    private fun introTimeline(extras: DoubleArray): Timeline = GuardIntro.timeline(ticks(Ch2L2DataLoader.data.level.guard.introSeconds), extras)

    /** The death, from a frame at the Guard's feet facing as it does (towards the player): its fall, then its coming apart, then the cloud rising. */
    private fun deathTimeline(@Suppress("UNUSED_PARAMETER") extras: DoubleArray): Timeline {
        val total = ticks(Ch2L2DataLoader.data.level.guard.deathSeconds)
        val shots = (total - 2 * 10 - Cutscene.BANDS_TICKS).coerceAtLeast(90)
        val first = (shots * 0.29).toInt()
        val second = (shots * 0.33).toInt()
        val third = shots - first - second
        return Timeline(
            Cutscene(
                listOf(
                    // The fall, from the front: the head drops, the knees go, it comes down.
                    Shot(first, Local(4.0, 1.0, 1.7), Local(3.4, 0.6, 1.5), Look.At(Local(0.0, 0.0, 1.5)), Look.At(Local(0.8, 0.0, 0.5))),
                    // Low, close, by the body on the floor as it begins to come apart.
                    Shot(second, Local(2.4, -1.4, 0.5), Local(2.0, -0.9, 0.6), Look.At(Local(1.2, 0.0, 0.3))),
                    // Rising and drawing back as it dissolves.
                    Shot(third, Local(3.0, 1.6, 2.4), Local(3.6, 2.2, 3.2), Look.At(Local(1.2, 0.0, 0.3))),
                ),
                10, 10,
            ),
            emptyList(), emptyList(),
        )
    }
}
