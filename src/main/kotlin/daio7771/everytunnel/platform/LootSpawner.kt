package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.PersistentState

/**
 * Puts the loot of a level in place: a [LootBlock] at every spawn point tagged with a key
 * of [LOOT] (`battery`, `water`, `keycard`), the first time a player enters that level.
 *
 * Each spot is filled once per world: the world remembers it
 * (`data/every_tunnel_loot.dat`), so what was picked up stays picked up, also after
 * dying or reopening the world. A new game ([restock]) puts everything back.
 */
object LootSpawner {

    /** Spawn point tag -> the loot block put there. */
    val LOOT: Map<String, () -> Block> = mapOf(
        "battery" to { ModBlocks.LOOT_BATTERY },
        "water" to { ModBlocks.LOOT_WATER_BOTTLE },
        "keycard" to { ModBlocks.LOOT_KEYCARD },
    )

    private var server: MinecraftServer? = null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register { server = null }
        LevelTriggers.register { change ->
            val level = change.current.levelId?.let(LevelManager::get)
            val server = server
            if (change.levelChanged && level != null && server != null) place(server, level)
        }
    }

    /** Every level's loot back in place (a new game). */
    fun restock(server: MinecraftServer) {
        val state = state(server)
        state.placed.clear()
        state.markDirty()
        for (level in LevelManager.all()) place(server, level)
    }

    /** Loot spots ("level/point") filled in this game whose loot is gone (picked up), where the chunk is loaded. */
    fun taken(server: MinecraftServer): Set<String> {
        val placed = state(server).placed
        val taken = LinkedHashSet<String>()
        for (level in LevelManager.all()) {
            val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: continue
            for (point in level.spawnPoints) {
                if (point.tags.none { it in LOOT }) continue
                val key = "${level.id}/${point.id}"
                if (key !in placed) continue
                val pos = BlockPos.ofFloored(point.position.x, point.position.y, point.position.z)
                if (!world.chunkManager.isChunkLoaded(pos.x shr 4, pos.z shr 4)) continue
                if (world.getBlockState(pos).block !is LootBlock) taken += key
            }
        }
        return taken
    }

    /** A saved game goes on ([SaveGameHooks]): the loot picked up before is gone again. */
    fun takeAway(server: MinecraftServer, keys: Set<String>) {
        if (keys.isEmpty()) return
        val state = state(server)
        for (level in LevelManager.all()) {
            val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: continue
            for (point in level.spawnPoints) {
                val key = "${level.id}/${point.id}"
                if (key !in keys) continue
                val pos = BlockPos.ofFloored(point.position.x, point.position.y, point.position.z)
                if (world.getBlockState(pos).block is LootBlock) world.setBlockState(pos, Blocks.AIR.defaultState)
                state.placed += key
            }
        }
        state.markDirty()
    }

    private fun place(server: MinecraftServer, level: LevelDefinition) {
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return
        val state = state(server)
        var count = 0
        for (point in level.spawnPoints) {
            val block = point.tags.firstNotNullOfOrNull { LOOT[it] }?.invoke() ?: continue
            val key = "${level.id}/${point.id}"
            if (key in state.placed) continue
            val pos = BlockPos.ofFloored(point.position.x, point.position.y, point.position.z)
            val there = world.getBlockState(pos)
            if (there.isAir || there.isReplaceable || there.block is LootBlock) {
                world.setBlockState(pos, block.defaultState.with(LootBlock.FACING, Direction.fromRotation(point.yaw.toDouble())))
                count++
            } else {
                EveryTunnel.LOGGER.warn("Loot {} not placed: {} is taken by {}", key, pos, there)
            }
            state.placed += key
            state.markDirty()
        }
        if (count > 0) EveryTunnel.LOGGER.info("Loot placed in {}: {}", level.id, count)
    }

    private fun state(server: MinecraftServer): PlacedState =
        server.overworld.persistentStateManager.getOrCreate(PlacedState::read, ::PlacedState, "every_tunnel_loot")

    /** Loot spots ("level/point") already filled in this world. */
    class PlacedState : PersistentState() {
        val placed: MutableSet<String> = linkedSetOf()

        override fun writeNbt(nbt: NbtCompound): NbtCompound {
            nbt.put("placed", NbtList().apply { placed.forEach { add(NbtString.of(it)) } })
            return nbt
        }

        companion object {
            fun read(nbt: NbtCompound): PlacedState = PlacedState().apply {
                nbt.getList("placed", NbtElement.STRING_TYPE.toInt()).forEach { placed += it.asString() }
            }
        }
    }
}
