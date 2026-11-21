package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1IntroScene
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Voices
import daio7771.everytunnel.common.core.cutscene.Cutscene
import daio7771.everytunnel.common.core.cutscene.Local
import daio7771.everytunnel.common.core.cutscene.Look
import daio7771.everytunnel.common.core.cutscene.SceneFrame
import daio7771.everytunnel.common.core.cutscene.Shot
import daio7771.everytunnel.common.core.cutscene.Timeline
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import java.util.UUID
import daio7771.everytunnel.common.platform.TickProfiler
/**
 * The cinematic of Rowan's first announcement ([Ch2L1IntroScene]), as the server sees it: [start] holds the player where they stand for
 * the scene's length, tells the client to begin ([START_PACKET_ID], which carries where they stand; `common.client.cutscene.CutsceneView`
 * draws it from the anchors of `speakers.json`, building the same [Timeline] with [build]), sounds the chime from the speaker as
 * the first shot begins, and gives the player back at the end ([END_PACKET_ID]). A skip from the client ([SKIP_PACKET_ID]) ends it at once,
 * the chime cut: [finish] is the one way out, so both leave the same level. Leaving the world during it drops it. Server thread, but
 * [build] and the client's copy of the timeline are also read on the client (a single-player game shares the data).
 */
object Ch2L1IntroHooks {

    val START_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "ch2l1_intro_start")
    val END_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "ch2l1_intro_end")
    val SKIP_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "ch2l1_intro_skip")

    private class Running(
        val player: UUID, val startTick: Long, val at: Vec3d, val yaw: Float, val pitch: Float,
        val built: Ch2L1IntroScene.Built, val emitter: Vec3d,
    ) {
        var dinged = false
    }

    private var running: Running? = null

    /** What the client is playing (its own copy, built from the same data and the same frame as the server's). */
    @Volatile
    private var clientScene: Ch2L1IntroScene.Built? = null

    private val voices: Ch2L1Voices by lazy { Ch2L1Voices.load() }

    fun register() {
        ServerPlayNetworking.registerGlobalReceiver(SKIP_PACKET_ID) { server, player, _, _, _ ->
            server.execute { if (running?.player == player.uuid) finish(server, skipped = true) }
        }
        TickProfiler.endServerTick.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> if (running?.player == handler.player.uuid) running = null }
    }

    fun isRunning(player: ServerPlayerEntity): Boolean = running?.player == player.uuid

    /** Where the chime comes from: the speaker the first shot looks at (the cinematic turns to it), else [fallback]. */
    fun speakerOf(fallback: Vec3d): Vec3d {
        val shot = Ch2L1DataLoader.speakers.intro?.shots?.firstOrNull() ?: return fallback
        val at = Ch2L1DataLoader.data.anchors.point(shot.look)?.position ?: return fallback
        return Vec3d(at.x, at.y, at.z)
    }

    /** The scene seen from [frame] (where the player stands), or null: no cinematic in `speakers.json`, or an anchor of it is missing. */
    fun build(frame: SceneFrame): Ch2L1IntroScene.Built? {
        val speakers = Ch2L1DataLoader.speakers
        val spec = speakers.intro ?: return null
        val line = voices.line(spec.line) ?: return null
        val result = Ch2L1IntroScene.build(spec, Ch2L1DataLoader.data.anchors, frame, line.ms, line.textKey, speakers.dingTicks)
        result.problem?.let { EveryTunnel.LOGGER.warn("Chapter 2 Level 1: the first announcement's cinematic cannot be made: {}", it) }
        return result.built
    }

    /** The client begins a scene seen from [frame]; false if it cannot be built. */
    fun clientBegin(frame: SceneFrame): Boolean {
        clientScene = build(frame)
        return clientScene != null
    }

    /** The timeline the client is playing (a bare placeholder when none: it is never drawn then). */
    fun clientTimeline(): Timeline = clientScene?.timeline ?: PLACEHOLDER

    private val PLACEHOLDER = Timeline(Cutscene(listOf(Shot(1, Local(0.0, 0.0, 0.0), Local(0.0, 0.0, 0.0), Look.Player)), 1, 1), emptyList(), emptyList())

    /**
     * Plays the cinematic for [player], the chime coming from [emitter]; false if it cannot (one is playing, the client cannot receive
     * it, the data lacks it), and the caller does what it would without.
     */
    fun start(player: ServerPlayerEntity, emitter: Vec3d): Boolean {
        if (running != null || !ServerPlayNetworking.canSend(player, START_PACKET_ID)) return false
        val frame = SceneFrame(player.x, player.y, player.z, player.yaw.toDouble())
        val built = build(frame) ?: return false
        running = Running(player.uuid, player.server.ticks.toLong(), player.pos, player.yaw, player.pitch, built, emitter)
        val buf = PacketByteBufs.create()
        buf.writeDouble(frame.x)
        buf.writeDouble(frame.y)
        buf.writeDouble(frame.z)
        buf.writeFloat(player.yaw)
        ServerPlayNetworking.send(player, START_PACKET_ID, buf)
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: the first announcement's cinematic begins for {}", player.gameProfile.name)
        return true
    }

    /** (x, y, z, yaw) of where the player stands, as [start] wrote it. */
    fun readStart(buf: PacketByteBuf): DoubleArray = doubleArrayOf(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat().toDouble())

    private fun tick(server: MinecraftServer) {
        val scene = running ?: return
        val player = server.playerManager.getPlayer(scene.player)
        if (player == null) {
            running = null
            return
        }
        val t = server.ticks - scene.startTick
        if (!scene.dinged && t >= scene.built.dingTick) {
            scene.dinged = true
            Ch2L1SpeakerHooks.send(player, Ch2Sounds.CH2L1_DING, scene.emitter)
        }
        if (t >= scene.built.timeline.cutscene.totalTicks) {
            finish(server, skipped = false)
            return
        }
        // Held there: the client takes their keys, this keeps them there whatever it does.
        if (player.pos.squaredDistanceTo(scene.at) > 0.0025) {
            player.networkHandler.requestTeleport(scene.at.x, scene.at.y, scene.at.z, scene.yaw, scene.pitch)
        }
        player.velocity = Vec3d.ZERO
    }

    /** The scene's end, played through or skipped. */
    private fun finish(server: MinecraftServer, skipped: Boolean) {
        val scene = running ?: return
        running = null
        val player = server.playerManager.getPlayer(scene.player) ?: return
        if (skipped) player.networkHandler.sendPacket(StopSoundS2CPacket(Ch2Sounds.CH2L1_DING.id, SoundCategory.VOICE))
        if (ServerPlayNetworking.canSend(player, END_PACKET_ID)) {
            val buf = PacketByteBufs.create()
            buf.writeBoolean(skipped)
            ServerPlayNetworking.send(player, END_PACKET_ID, buf)
        }
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: the first announcement's cinematic {}", if (skipped) "skipped" else "ends")
    }
}
