package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.PlayerLocationTracker
import daio7771.everytunnel.core.cutscene.DirectorLines
import daio7771.everytunnel.core.cutscene.DirectorLoop
import daio7771.everytunnel.core.cutscene.DirectorScene
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.core.level.SpawnPoint
import daio7771.everytunnel.core.progress.Checkpoint
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.Blocks
import net.minecraft.block.DoorBlock
import net.minecraft.network.PacketByteBuf
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.state.property.Properties
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.util.UUID

/**
 * The director's scene (the boss's cinematic, [DirectorScene]) and what follows it.
 *
 * Opening his double door ([PairedDoorBlock]) starts it, once each time Level 5 is built: the player is held
 * unharmed for the scene's length, the door slams shut while the camera flies in ([DOOR_SLAM]) and stays locked
 * ([doorLocked]), and once the camera is in, looking at him, the player is put just inside the door behind it
 * ([MOVE_IN]; the spawn point tagged [INSIDE_TAG]), facing him, where the later shots see them.
 * The client draws the scene (`client.cutscene.CutsceneView`) from [START_PACKET_ID], which carries where he
 * sits. A skip from the client ([SKIP_PACKET_ID]) ends it at once, in the same state as its end: [finish] is the
 * one way out. On the way his mask goes onto his desk when his animation lays it there, or at once on a skip
 * ([putMaskDown]).
 *
 * At its end the spot inside the door becomes the checkpoint ([ContinueGame.restartPoint]); a game continued from
 * there finds him as the scene left him ([levelBuilt]), and his phrases ([DirectorLoopHooks]) start. Leaving the
 * world during the scene drops it: the next build plays it again.
 */
object DirectorSceneHooks {

    val START_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "director_scene_start")
    val END_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "director_scene_end")
    val SKIP_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "director_scene_skip")

    const val LEVEL_ID = "level_5"

    /** Opening a door this near him (blocks) is opening his. */
    private const val DOOR_REACH = 24.0

    /** `/everytunnel director` puts you at the spawn point tagged this, before his door. */
    const val DEBUG_TAG = "director_debug"

    /** Just inside his door: where the scene puts you, and its checkpoint (that spawn point's id). */
    private const val INSIDE_TAG = "director_inside"

    /** His door slams this many ticks into the scene, the camera past it. */
    private const val DOOR_SLAM = 50

    /** The player is moved inside when the camera has flown in (its back to them): DirectorScene's fly-in. */
    private const val MOVE_IN = 60

    /** Held at [at] (where they opened the door), then at [inside]. */
    private class Running(val player: UUID, val startTick: Int, var at: Vec3d, var yaw: Float, var pitch: Float, val inside: SpawnPoint)

    private var running: Running? = null
    private var played = false
    private var maskDown = false
    private var doorShut = false

    /** His door stays shut: his scene has begun this build. Read by the door on both sides (the game is singleplayer). */
    @Volatile
    var doorLocked = false
        private set

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayNetworking.registerGlobalReceiver(SKIP_PACKET_ID) { server, player, _, _, _ ->
            server.execute { if (running?.player == player.uuid) finish(server, skipped = true) }
        }
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ -> running?.player != entity.uuid }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            if (running?.player == handler.player.uuid) reset()
        }
        ServerLifecycleEvents.SERVER_STOPPED.register { reset() }
    }

    private fun reset() {
        running = null
        played = false
        maskDown = false
        doorShut = false
        doorLocked = false
        DirectorLoopHooks.stop()
    }

    /**
     * Level 5 was built (before he is put in his chair): his scene waits for the door again; but continuing from the
     * checkpoint past it, it is over: the door locked, his mask on the desk, his phrases going.
     */
    fun levelBuilt(server: MinecraftServer) {
        reset()
        val level = LevelManager.get(LEVEL_ID) ?: return
        val inside = level.spawnPoints.firstOrNull { INSIDE_TAG in it.tags } ?: return
        val past = server.playerManager.playerList.any {
            Checkpoint.parse(ProgressService.get().checkpoint(it)) == Checkpoint(LEVEL_ID, inside.id)
        }
        if (!past) return
        played = true
        doorShut = true
        doorLocked = true
        putMaskDown(server)
        DirectorLoopHooks.start(server)
        EveryTunnel.LOGGER.info("Level 5: past the director's scene (checkpoint), his phrases start")
    }

    /** His mask is on his desk, so a director put in his chair now comes without it. */
    fun maskIsDown(): Boolean = maskDown

    fun isRunning(player: ServerPlayerEntity): Boolean = running?.player == player.uuid

    /** [player] opened a [PairedDoorBlock]: if it is his, and his scene has not played, it starts. */
    fun doorOpened(player: ServerPlayerEntity) {
        if (played || running != null || !ResourceHooks.appliesTo(player)) return
        val him = director() ?: return
        if (player.squaredDistanceTo(him.position.x, him.position.y, him.position.z) > DOOR_REACH * DOOR_REACH) return
        if (!ServerPlayNetworking.canSend(player, START_PACKET_ID)) return
        val inside = LevelManager.get(LEVEL_ID)?.spawnPoints?.firstOrNull { INSIDE_TAG in it.tags } ?: return
        played = true
        doorLocked = true
        running = Running(player.uuid, player.server.ticks, player.pos, player.yaw, player.pitch, inside)
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
        doorShut = false
        doorLocked = false
        DirectorLoopHooks.stop()
        maskOn(player.server)
        player.teleport(player.serverWorld, point.position.x, point.position.y, point.position.z, point.yaw, 0f)
        return true
    }

    /**
     * His mask on the desk, face up in front of him (the block [ModBlocks.DIRECTOR_MASK], its brow towards him),
     * and gone from his face: where his scene's animation leaves it. Once: a skip comes here too.
     */
    private fun putMaskDown(server: MinecraftServer) {
        if (maskDown) return
        maskDown = true
        val (world, him) = where(server) ?: return
        for (entity in world.iterateEntities()) if (entity is DirectorEntity) entity.maskOff = true
        val pos = deskCell(him)
        if (world.getBlockState(pos).isAir) {
            val facing = Direction.fromRotation(him.yaw + 180.0)
            world.setBlockState(pos, ModBlocks.DIRECTOR_MASK.defaultState.with(Properties.HORIZONTAL_FACING, facing))
        }
    }

    /** `/everytunnel director`: the mask back on his face, off the desk. */
    private fun maskOn(server: MinecraftServer) {
        maskDown = false
        val (world, him) = where(server) ?: return
        for (entity in world.iterateEntities()) if (entity is DirectorEntity) entity.maskOff = false
        val pos = deskCell(him)
        if (world.getBlockState(pos).isOf(ModBlocks.DIRECTOR_MASK)) world.setBlockState(pos, Blocks.AIR.defaultState)
    }

    private fun deskCell(him: SpawnPoint): BlockPos {
        val (x, y, z) = frame(him).at(1.0, 0.0, 1.0)
        return BlockPos.ofFloored(x, y, z)
    }

    /** His door slams shut behind the player: both leaves, with a dry knock. Once. */
    private fun shutDoor(server: MinecraftServer) {
        if (doorShut) return
        doorShut = true
        val (world, him) = where(server) ?: return
        val f = frame(him)
        val leaves = mutableSetOf<BlockPos>()
        for (forward in 12..18) for (right in -3..3) {
            val (x, y, z) = f.at(forward.toDouble(), right.toDouble(), 0.5)
            val pos = BlockPos.ofFloored(x, y, z)
            if (world.getBlockState(pos).block is PairedDoorBlock) leaves += pos
        }
        for (pos in leaves) {
            val state = world.getBlockState(pos)
            (state.block as DoorBlock).setOpen(null, world, state, pos, false)
        }
        leaves.firstOrNull()?.let { world.playSound(null, it, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 2f, 0.6f) }
    }

    private fun frame(him: SpawnPoint) = SceneFrame(him.position.x, him.position.y, him.position.z, him.yaw.toDouble())

    fun where(server: MinecraftServer): Pair<ServerWorld, SpawnPoint>? {
        val level = LevelManager.get(LEVEL_ID) ?: return null
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return null
        return world to (director() ?: return null)
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
        val t = server.ticks - scene.startTick
        if (t >= DOOR_SLAM) shutDoor(server)
        if (t >= MOVE_IN) moveIn(scene)
        if (t >= DirectorScene.SCENE.maskDown) putMaskDown(server)
        if (t >= DirectorScene.SCENE.cutscene.totalTicks) {
            finish(server, skipped = false)
            return
        }
        // Held there: the client takes their keys, this keeps them there whatever it does.
        if (player.pos.squaredDistanceTo(scene.at) > 0.0025) {
            player.networkHandler.requestTeleport(scene.at.x, scene.at.y, scene.at.z, scene.yaw, scene.pitch)
        }
        player.velocity = Vec3d.ZERO
    }

    /** The scene's end, played through or skipped: the door shut, the mask down, the checkpoint here, his phrases. */
    private fun finish(server: MinecraftServer, skipped: Boolean) {
        val scene = running ?: return
        running = null
        shutDoor(server)
        putMaskDown(server)
        DirectorLoopHooks.start(server)
        val player = server.playerManager.getPlayer(scene.player) ?: return
        moveIn(scene)
        player.networkHandler.requestTeleport(scene.at.x, scene.at.y, scene.at.z, scene.yaw, scene.pitch)
        val level = LevelManager.get(LEVEL_ID)
        val inside = level?.spawnPoints?.firstOrNull { INSIDE_TAG in it.tags }
        if (level != null && inside != null) ContinueGame.restartPoint(player, level, inside.id)
        if (ServerPlayNetworking.canSend(player, END_PACKET_ID)) {
            val buf = PacketByteBufs.create()
            buf.writeBoolean(skipped)
            ServerPlayNetworking.send(player, END_PACKET_ID, buf)
        }
        EveryTunnel.LOGGER.info("Level 5: the director's scene {}", if (skipped) "skipped" else "ends")
    }

    /** From now on the scene holds its player just inside the door, facing him (the next hold check moves them). */
    private fun moveIn(scene: Running) {
        val p = scene.inside.position
        scene.at = Vec3d(p.x, p.y, p.z)
        scene.yaw = scene.inside.yaw
        scene.pitch = 0f
    }

    /** (x, y, z, yaw) of where he sits. */
    fun readStart(buf: PacketByteBuf): DoubleArray =
        doubleArrayOf(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat().toDouble())
}

/**
 * After his scene, while the player stays in his office (the zone [ROOM]), the director says his phrases over and
 * over ([DirectorLoop]: last to first, every 20-30 s). Each goes to the client ([PHRASE_PACKET_ID], its id), which
 * plays it flat and shows its subtitles over the HUD (`client.cutscene.DirectorVoice`); while he says it he turns
 * to the player a little less each time ([DirectorEntity.look]), and looks away again after.
 */
object DirectorLoopHooks {

    val PHRASE_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "director_phrase")

    private const val ROOM = "director_office"

    private var loop: DirectorLoop? = null
    private var lookUntil = 0

    /** His phrases from now on (once his scene is over). */
    fun start(server: MinecraftServer) {
        if (loop != null) return
        loop = DirectorLoop(DirectorLines.load().loop).also { it.start(server.ticks) }
    }

    fun stop() {
        loop = null
        lookUntil = 0
    }

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    private fun tick(server: MinecraftServer) {
        val loop = loop ?: return
        val player = server.playerManager.playerList.firstOrNull() ?: return
        val inRoom = PlayerLocationTracker.locationOf(player.uuid).zones.any {
            it.levelId == DirectorSceneHooks.LEVEL_ID && it.zoneId == ROOM
        }
        if (lookUntil != 0 && server.ticks >= lookUntil) {
            lookUntil = 0
            setLook(server, 0f)
        }
        val n = loop.said
        val line = loop.tick(server.ticks, inRoom) ?: return
        lookUntil = server.ticks + DirectorScene.ticks(line.ms)
        setLook(server, loop.gaze(n).toFloat())
        if (ServerPlayNetworking.canSend(player, PHRASE_PACKET_ID)) {
            ServerPlayNetworking.send(player, PHRASE_PACKET_ID, PacketByteBufs.create().also { it.writeString(line.id) })
        }
    }

    private fun setLook(server: MinecraftServer, look: Float) {
        val (world, _) = DirectorSceneHooks.where(server) ?: return
        for (entity in world.iterateEntities()) if (entity is DirectorEntity) entity.look = look
    }
}
