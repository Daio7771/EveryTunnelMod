package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.PlayerLocationTracker
import daio7771.everytunnel.core.level.BlockBox
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtHelper
import net.minecraft.nbt.NbtList
import net.minecraft.network.PacketByteBuf
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.PersistentState
import java.util.UUID

/**
 * The scene a new game starts with: waiting in the elevator.
 *
 * A player who has not had it yet in this world, and is in the start zone (tagged
 * [START_TAG]) of a level with an `intro` while the world rules apply, is put at the level
 * start; the elevator doors (zones tagged [DOOR_TAG]) close and the intro's song plays.
 * After the intro's seconds the bell rings, the song distorts and fades
 * (`client.effects.IntroMusic`), the doors slide open and the level's title shows
 * ([LevelEntryHooks.announce]).
 *
 * The world remembers who has had it (`data/every_tunnel_intro.dat`), so dying in the level
 * later brings the player back to the elevator with its doors open. `/everytunnel intro`
 * plays it again.
 */
object IntroHooks {

    const val START_TAG = "start"
    const val DOOR_TAG = "elevator_door"

    /** To the client: 0 = start the song (then its sound id), 1 = distort and fade it (then the seconds). */
    val MUSIC_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "intro_music")
    const val MUSIC_START: Byte = 0
    const val MUSIC_END: Byte = 1

    private const val CHECK_TICKS = 10
    /** From the bell to the doors starting to move, and between the doors' stages. */
    private const val DOORS_DELAY_TICKS = 10
    private const val DOOR_STAGE_TICKS = 4
    private const val MUSIC_FADE_SECONDS = 3f

    private class Running(val levelId: String, var ticksLeft: Int)

    /** Doors sliding open: [ticks] counts up from minus the delay; stages advance every [DOOR_STAGE_TICKS]. */
    private class Opening(val world: ServerWorld, val boxes: List<BlockBox>, var ticks: Int = -DOORS_DELAY_TICKS)

    // Server thread only.
    private val running = HashMap<UUID, Running>()
    private val opening = mutableListOf<Opening>()
    private var checkTicks = 0

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> running.remove(handler.player.uuid) }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            running.clear()
            opening.clear()
        }
    }

    /** Forgets that [player] had the intro and takes them to the start of the first level with one. */
    fun replay(player: ServerPlayerEntity): LevelDefinition? {
        val level = LevelManager.all().filter { it.intro != null }.minByOrNull { it.order } ?: return null
        val state = state(player.server)
        if (state.done.remove(player.uuid)) state.markDirty()
        running.remove(player.uuid)
        teleportToStart(player, level)
        return level
    }

    private fun tick(server: MinecraftServer) {
        if (++checkTicks >= CHECK_TICKS) {
            checkTicks = 0
            for (player in server.playerManager.playerList) {
                if (player.uuid in running || !WorldRules.appliesTo(player.isCreative, player.isSpectator)) continue
                val location = PlayerLocationTracker.locationOf(player.uuid)
                val level = location.levelId?.let(LevelManager::get) ?: continue
                if (level.intro == null || player.uuid in state(server).done) continue
                val inStart = location.zones.any { it.levelId == level.id && level.zone(it.zoneId)?.tags?.contains(START_TAG) == true }
                if (inStart) start(player, level)
            }
        }
        val iterator = running.entries.iterator()
        while (iterator.hasNext()) {
            val (id, intro) = iterator.next()
            if (--intro.ticksLeft > 0) continue
            iterator.remove()
            val player = server.playerManager.getPlayer(id) ?: continue
            LevelManager.get(intro.levelId)?.let { finish(player, it) }
        }
        opening.removeIf { doors ->
            val ticks = doors.ticks++
            if (ticks < 0 || ticks % DOOR_STAGE_TICKS != 0) return@removeIf false
            val stage = ticks / DOOR_STAGE_TICKS + 1
            for (box in doors.boxes) {
                forEachBlock(box) { pos ->
                    val state = doors.world.getBlockState(pos)
                    if (state.isOf(ModBlocks.ELEVATOR_DOOR)) {
                        doors.world.setBlockState(
                            pos,
                            if (stage > 2) Blocks.AIR.defaultState else state.with(ElevatorDoorBlock.STAGE, stage),
                        )
                    }
                }
            }
            stage > 2
        }
    }

    private fun start(player: ServerPlayerEntity, level: LevelDefinition) {
        val intro = level.intro ?: return
        val world = worldOf(player.server, level) ?: return
        teleportToStart(player, level)
        for (box in doors(level)) {
            forEachBlock(box) { pos ->
                val state = world.getBlockState(pos)
                if (state.isAir || state.isOf(ModBlocks.ELEVATOR_DOOR)) world.setBlockState(pos, closedDoor(box, pos))
            }
        }
        opening.removeIf { it.world == world }
        intro.music?.let { sendMusic(player, MUSIC_START) { buf -> buf.writeString(it) } }
        running[player.uuid] = Running(level.id, (intro.seconds * 20.0).toInt().coerceAtLeast(1))
        EveryTunnel.LOGGER.info("{}: intro of {} ({} s)", player.name.string, level.id, intro.seconds)
    }

    private fun finish(player: ServerPlayerEntity, level: LevelDefinition) {
        val state = state(player.server)
        state.done += player.uuid
        state.markDirty()
        sendMusic(player, MUSIC_END) { it.writeFloat(MUSIC_FADE_SECONDS) }
        val world = worldOf(player.server, level)
        val boxes = doors(level)
        if (world != null && boxes.isNotEmpty()) {
            val (x, y, z) = centre(boxes.first())
            world.playSound(null, x, y + 1.0, z, ModSounds.ELEVATOR_DING, SoundCategory.BLOCKS, 1f, 1f)
            world.playSound(null, x, y, z, ModSounds.ELEVATOR_DOORS, SoundCategory.BLOCKS, 1f, 1f)
            opening += Opening(world, boxes)
        }
        LevelEntryHooks.announce(player, level)
        EveryTunnel.LOGGER.info("{}: intro of {} done, doors open", player.name.string, level.id)
    }

    /** A closed door for [pos] in the opening [box]: the low half slides to the low side, the rest the other way. */
    private fun closedDoor(box: BlockBox, pos: BlockPos): BlockState {
        val alongX = box.maxX - box.minX >= box.maxZ - box.minZ
        val (coordinate, middle) = if (alongX) pos.x to (box.minX + box.maxX) / 2.0 else pos.z to (box.minZ + box.maxZ) / 2.0
        return ModBlocks.ELEVATOR_DOOR.defaultState
            .with(ElevatorDoorBlock.AXIS, if (alongX) Direction.Axis.X else Direction.Axis.Z)
            .with(ElevatorDoorBlock.POSITIVE, coordinate > middle)
            .with(ElevatorDoorBlock.STAGE, 0)
    }

    private fun teleportToStart(player: ServerPlayerEntity, level: LevelDefinition) {
        val start = level.spawnPoints.firstOrNull { RespawnHooks.TAG in it.tags } ?: return
        val world = worldOf(player.server, level) ?: return
        player.teleport(world, start.position.x, start.position.y, start.position.z, start.yaw, start.pitch)
    }

    private fun doors(level: LevelDefinition): List<BlockBox> = level.zones.filter { DOOR_TAG in it.tags }.map { it.box }

    private fun worldOf(server: MinecraftServer, level: LevelDefinition): ServerWorld? =
        server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension)))

    private fun centre(box: BlockBox) = Triple((box.minX + box.maxX + 1) / 2.0, box.minY.toDouble(), (box.minZ + box.maxZ + 1) / 2.0)

    private inline fun forEachBlock(box: BlockBox, action: (BlockPos) -> Unit) {
        for (x in box.minX..box.maxX) for (y in box.minY..box.maxY) for (z in box.minZ..box.maxZ) action(BlockPos(x, y, z))
    }

    private inline fun sendMusic(player: ServerPlayerEntity, action: Byte, write: (PacketByteBuf) -> Unit) {
        if (!ServerPlayNetworking.canSend(player, MUSIC_PACKET_ID)) return
        val buf = PacketByteBufs.create()
        buf.writeByte(action.toInt())
        write(buf)
        ServerPlayNetworking.send(player, MUSIC_PACKET_ID, buf)
    }

    private fun state(server: MinecraftServer): IntroState =
        server.overworld.persistentStateManager.getOrCreate(IntroState::read, ::IntroState, "every_tunnel_intro")

    /** Players who have had the intro in this world. */
    class IntroState : PersistentState() {
        val done: MutableSet<UUID> = linkedSetOf()

        override fun writeNbt(nbt: NbtCompound): NbtCompound {
            nbt.put("done", NbtList().apply { done.forEach { add(NbtHelper.fromUuid(it)) } })
            return nbt
        }

        companion object {
            fun read(nbt: NbtCompound): IntroState = IntroState().apply {
                nbt.getList("done", NbtElement.INT_ARRAY_TYPE.toInt()).forEach { done += NbtHelper.toUuid(it) }
            }
        }
    }
}
