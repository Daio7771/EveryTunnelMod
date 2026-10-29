package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.core.achievement.Achievement
import daio7771.everytunnel.core.puzzle.ValveCode
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.block.DoorBlock
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import kotlin.random.Random

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

    // Server thread only. Level id -> the digit of each valve, A first.
    private val codes = mutableMapOf<String, List<Int>>()

    fun register() {
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
            if (state.isOf(ModBlocks.VALVE)) turns[state.get(ValveBlock.LETTER).ordinal] = state.get(ValveBlock.TURNS)
        }
        if (!ValveCode.solved(code, turns)) return
        for (p in positions) {
            val state = world.getBlockState(p)
            if (!state.isOf(ModBlocks.VALVE)) continue
            world.setBlockState(p, state.with(ValveBlock.OPEN, true))
            world.playSound(null, p, ModSounds.VALVE_RELEASE, SoundCategory.BLOCKS, 1f, 1f)
        }
        setDoors(world, level, open = true)
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
                if (state.isOf(ModBlocks.VALVE)) world.setBlockState(p, state.with(ValveBlock.TURNS, 0).with(ValveBlock.OPEN, false))
            }
            setDoors(world, level, open = false)
            code(level)
        }
    }

    /** This game's digits for [levelId], if drawn yet (for the save, [SaveGameHooks]). */
    fun drawnCode(levelId: String): List<Int>? = codes[levelId]

    /**
     * A saved game goes on ([SaveGameHooks]): [code] is [level]'s again and its valves are
     * turned as they were ([turns], A first). If that was right, they are open and locked and
     * the doors open, quietly.
     */
    fun restore(world: ServerWorld, level: LevelDefinition, code: List<Int>, turns: List<Int>) {
        codes[level.id] = code
        val solved = ValveCode.solved(code, turns)
        for (p in valves(level)) {
            val state = world.getBlockState(p)
            if (!state.isOf(ModBlocks.VALVE)) continue
            val t = turns.getOrNull(state.get(ValveBlock.LETTER).ordinal) ?: 0
            world.setBlockState(p, state.with(ValveBlock.TURNS, t.coerceIn(0, ValveCode.POSITIONS - 1)).with(ValveBlock.OPEN, solved))
        }
        if (solved) setDoors(world, level, open = true)
    }

    /** The map was rebuilt (valves on 0, doors shut): a new game draws new digits. */
    fun levelsRebuilt() {
        codes.clear()
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
