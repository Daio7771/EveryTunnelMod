package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.cutscene.DirectorScene
import daio7771.everytunnel.core.level.SpawnPoint
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
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
 * The director's scene (the boss's cinematic, [DirectorScene]): opening his double door ([PairedDoorBlock])
 * starts it, once each time Level 5 is built. The server holds the player where they stood, unharmed, for
 * the scene's length and then gives them back; the client draws it (`client.cutscene.CutsceneView`: the
 * camera, the bands, the notice to skip) from [START_PACKET_ID], which carries where he sits. A skip from the
 * client ([SKIP_PACKET_ID]) ends it at once, in the same state as its end: [finish] is the one way out.
 * Leaving the world during the scene drops it; the next build plays it again.
 */
object DirectorSceneHooks {

    val START_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "director_scene_start")
    val END_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "director_scene_end")
    val SKIP_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "director_scene_skip")

    private const val LEVEL_ID = "level_5"

    /** Opening a door this near him (blocks) is opening his. */
    private const val DOOR_REACH = 24.0

    /** `/everytunnel director` puts you at the spawn point tagged this, before his door. */
    const val DEBUG_TAG = "director_debug"

    private class Running(val player: UUID, val startTick: Int, val at: Vec3d, val yaw: Float, val pitch: Float)

    private var running: Running? = null
    private var played = false

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayNetworking.registerGlobalReceiver(SKIP_PACKET_ID) { server, player, _, _, _ ->
            server.execute { if (running?.player == player.uuid) finish(server, skipped = true) }
        }
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ -> running?.player != entity.uuid }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            if (running?.player == handler.player.uuid) {
                running = null
                played = false
            }
        }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            running = null
            played = false
        }
    }

    /** Level 5 was built: his scene waits for the door again. */
    fun levelBuilt() {
        running = null
        played = false
    }

    fun isRunning(player: ServerPlayerEntity): Boolean = running?.player == player.uuid

    /** [player] opened a [PairedDoorBlock]: if it is his, and his scene has not played, it starts. */
    fun doorOpened(player: ServerPlayerEntity) {
        if (played || running != null || !ResourceHooks.appliesTo(player)) return
        val him = director() ?: return
        if (player.squaredDistanceTo(him.position.x, him.position.y, him.position.z) > DOOR_REACH * DOOR_REACH) return
        if (!ServerPlayNetworking.canSend(player, START_PACKET_ID)) return
        played = true
        running = Running(player.uuid, player.server.ticks, player.pos, player.yaw, player.pitch)
        player.velocity = Vec3d.ZERO
        player.isSprinting = false
        val buf = PacketByteBufs.create()
        buf.writeDouble(him.position.x)
        buf.writeDouble(him.position.y)
        buf.writeDouble(him.position.z)
        buf.writeFloat(him.yaw)
        ServerPlayNetworking.send(player, START_PACKET_ID, buf)
        EveryTunnel.LOGGER.info("Level 5: the director's scene begins ({} s)", DirectorScene.SCENE.cutscene.totalTicks / 20)
    }

    /** `/everytunnel director`: his scene can play again, and [player] stands before his door. */
    fun again(player: ServerPlayerEntity): Boolean {
        val point = LevelManager.get(LEVEL_ID)?.spawnPoints?.firstOrNull { DEBUG_TAG in it.tags } ?: return false
        if (running != null) finish(player.server, skipped = true)
        played = false
        player.teleport(player.serverWorld, point.position.x, point.position.y, point.position.z, point.yaw, 0f)
        return true
    }

    private fun director(): SpawnPoint? =
        LevelManager.get(LEVEL_ID)?.spawnPoints?.firstOrNull { DirectorHooks.TAG in it.tags }

    private fun tick(server: MinecraftServer) {
        val scene = running ?: return
        val player = server.playerManager.getPlayer(scene.player)
        if (player == null) {
            running = null
            return
        }
        if (server.ticks - scene.startTick >= DirectorScene.SCENE.cutscene.totalTicks) {
            finish(server, skipped = false)
            return
        }
        // Held where they stood: the client takes their keys, this keeps them there whatever it does.
        if (player.pos.squaredDistanceTo(scene.at) > 0.0025) {
            player.networkHandler.requestTeleport(scene.at.x, scene.at.y, scene.at.z, scene.yaw, scene.pitch)
        }
        player.velocity = Vec3d.ZERO
    }

    /** The scene's end, played through or skipped: the player is theirs again. */
    private fun finish(server: MinecraftServer, skipped: Boolean) {
        val scene = running ?: return
        running = null
        val player = server.playerManager.getPlayer(scene.player) ?: return
        if (ServerPlayNetworking.canSend(player, END_PACKET_ID)) {
            val buf = PacketByteBufs.create()
            buf.writeBoolean(skipped)
            ServerPlayNetworking.send(player, END_PACKET_ID, buf)
        }
        EveryTunnel.LOGGER.info("Level 5: the director's scene {}", if (skipped) "skipped" else "ends")
    }

    /** (x, y, z, yaw) of where he sits. */
    fun readStart(buf: PacketByteBuf): DoubleArray =
        doubleArrayOf(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat().toDouble())
}
