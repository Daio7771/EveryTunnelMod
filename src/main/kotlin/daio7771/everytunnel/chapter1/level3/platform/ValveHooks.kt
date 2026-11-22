package daio7771.everytunnel.chapter1.level3.platform

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.common.core.LevelManager
import daio7771.everytunnel.common.core.level.LevelDefinition
import daio7771.everytunnel.common.core.achievement.Achievement
import daio7771.everytunnel.chapter1.level3.core.ValveCode
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.block.BlockState
import net.minecraft.block.DoorBlock
import net.minecraft.block.enums.DoorHinge
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import kotlin.random.Random
import daio7771.everytunnel.common.platform.AchievementHooks
import daio7771.everytunnel.common.platform.ModBlocks
import daio7771.everytunnel.common.platform.ModSounds
import daio7771.everytunnel.chapter1.platform.Ch1Blocks
import daio7771.everytunnel.common.platform.NoteHooks

/**
 * Level 3's valve puzzle in the world ([ValveCode] has its rules). The valves are the blocks at
 * the level's spawn points tagged [VALVE_TAG], each lettered A to E ([ValveBlock.LETTER]). A new
 * game draws a digit for each ([newGame]); the electrician's note tells them ([values], read by
 * [NoteHooks]). Every turn ([turned]) checks them all: once each shows its digit, the pipes
 * settle (`block.valve.release`), the valves stay open (stems out; they no longer turn), the
 * doors in the level's zones tagged [DOOR_TAG] open and everyone there earns "Under Pressure"
 * ([AchievementHooks]). Only a new game puts the valves back on 0
 * and shuts the doors (as the map's rebuild also builds them).
 *
 * The digits live in memory: every opening of the world starts a new game ([LoadingHooks]); in
 * Creative or Spectator, where none starts, they are drawn the first time they are needed.
 */
object ValveHooks {

    const val VALVE_TAG = "valve"
    const val DOOR_TAG = "valve_door"
    const val HIDDEN_DOOR_TAG = "hidden_door"

    /** Marcus Guard's note (the Observant Guard before he was one), in the closet behind the hidden door (`notes/<lang>/<id>.txt`; its spawn point's id). */
    const val GUARD_NOTE = "ch1_l3_note_guard"

    /** The blocks the hidden doorway's door stands where (what was there, to put back with a new game). */
    private val sealed = mutableMapOf<BlockPos, BlockState>()

    // Server thread only. Level id -> the digit of each valve, A first.
    private val codes = mutableMapOf<String, List<Int>>()

    fun register() {
        NoteHooks.addValues(::values)
        NoteHooks.addReadListener { player, id -> if (id == GUARD_NOTE) AchievementHooks.earned(player, Achievement.GUARD_NOTE) }
        ServerLifecycleEvents.SERVER_STOPPED.register { codes.clear() }
    }

    private fun valves(level: LevelDefinition): List<BlockPos> =
        level.spawnPoints.filter { VALVE_TAG in it.tags }.map { BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) }

    private fun world(server: MinecraftServer, level: LevelDefinition): ServerWorld? =
        server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension)))

    /** The level's digits, drawn now if this game has none yet. */
    fun code(level: LevelDefinition): List<Int> = codes.getOrPut(level.id) {
        ValveCode.random(Random.Default, ValveLetter.entries.size).also {
            EveryTunnel.LOGGER.info("Valve code for {}: {}", level.id, it.joinToString("-"))
        }
    }

    /** A note's placeholder values in [level]: "A" to "E" and their digits, if it has valves. */
    fun values(level: LevelDefinition): Map<String, Int> =
        if (valves(level).isEmpty()) emptyMap() else code(level).withIndex().associate { (i, digit) -> ValveLetter.entries[i].name to digit }

    /** A valve at [pos] was just turned: are they all right now? */
    fun turned(world: ServerWorld, pos: BlockPos) {
        val level = LevelManager.levelAt(world.registryKey.value.toString(), pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) ?: return
        val positions = valves(level)
        if (pos !in positions) return
        val code = code(level)
        val turns = MutableList<Int?>(code.size) { null }
        for (p in positions) {
            val state = world.getBlockState(p)
            if (state.isOf(Ch1Blocks.VALVE)) turns[state.get(ValveBlock.LETTER).ordinal] = state.get(ValveBlock.TURNS)
        }
        if (!ValveCode.solved(code, turns)) return
        for (p in positions) {
            val state = world.getBlockState(p)
            if (!state.isOf(Ch1Blocks.VALVE)) continue
            world.setBlockState(p, state.with(ValveBlock.OPEN, true))
            world.playSound(null, p, ModSounds.VALVE_RELEASE, SoundCategory.BLOCKS, 1f, 1f)
        }
        setDoors(world, level, open = true)
        revealHiddenDoors(world, level)
        AchievementHooks.puzzleSolved(world, Achievement.VALVES)
        EveryTunnel.LOGGER.info("Valves of {} set right: the doors open", level.id)
    }

    /** A new game: every valve back on 0 and shut, the doors shut, new digits. */
    fun newGame(server: MinecraftServer) {
        codes.clear()
        for (level in LevelManager.all()) {
            val positions = valves(level)
            if (positions.isEmpty()) continue
            val world = world(server, level) ?: continue
            for (p in positions) {
                val state = world.getBlockState(p)
                if (state.isOf(Ch1Blocks.VALVE)) world.setBlockState(p, state.with(ValveBlock.TURNS, 0).with(ValveBlock.OPEN, false))
            }
            setDoors(world, level, open = false)
            sealedAgain(world)
            code(level)
        }
    }

    /** The map was rebuilt (valves on 0, doors shut): a new game draws new digits. */
    fun levelsRebuilt() {
        codes.clear()
        sealed.clear()
    }

    /** The hidden doorway's door, in place of the wall; it stands open (the emergency door is iron: a hand cannot open it) (the lower and upper halves; the wall's third block stays as a lintel). */
    private fun revealHiddenDoors(world: ServerWorld, level: LevelDefinition) {
        for (point in level.spawnPoints.filter { HIDDEN_DOOR_TAG in it.tags }) {
            val lower = BlockPos.ofFloored(point.position.x, point.position.y, point.position.z)
            val facing = Direction.fromRotation(point.yaw.toDouble())
            for ((pos, half) in listOf(lower to DoubleBlockHalf.LOWER, lower.up() to DoubleBlockHalf.UPPER)) {
                sealed.putIfAbsent(pos, world.getBlockState(pos))
                world.setBlockState(
                    pos,
                    ModBlocks.EMERGENCY_DOOR.defaultState.with(DoorBlock.FACING, facing).with(DoorBlock.HALF, half)
                        .with(DoorBlock.HINGE, DoorHinge.LEFT).with(DoorBlock.OPEN, true).with(DoorBlock.POWERED, false),
                )
            }
        }
    }

    /** The wall back where the hidden door stood. */
    private fun sealedAgain(world: ServerWorld) {
        for ((pos, state) in sealed) world.setBlockState(pos, state)
        sealed.clear()
    }

    private fun setDoors(world: ServerWorld, level: LevelDefinition, open: Boolean) {
        for (zone in level.zones.filter { DOOR_TAG in it.tags }) {
            val box = zone.box
            for (pos in BlockPos.iterate(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
                val state = world.getBlockState(pos)
                val block = state.block
                if (block is DoorBlock && state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                    block.setOpen(null, world, state, pos.toImmutable(), open)
                }
            }
        }
    }
}
