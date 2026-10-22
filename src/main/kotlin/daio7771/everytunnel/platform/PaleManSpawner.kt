package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.world.PersistentState

/**
 * Puts a Pale man at every spawn point tagged `pale_man` of a level, the first time a
 * player enters that level.
 *
 * Each spawn point spawns once per world: the world remembers it
 * (`data/every_tunnel_pale_men.dat`), so leaving and entering again, or reopening the
 * world, never adds a second one, even while he is far away in an unloaded chunk. When
 * he is killed or removed (`/kill`), his spawn point is forgotten and spawns him again
 * the next time a player enters the level.
 */
object PaleManSpawner {

    const val TAG = "pale_man"

    private var server: MinecraftServer? = null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register { server = null }
        LevelTriggers.register { change ->
            val levelId = change.current.levelId
            if (change.levelChanged && levelId != null) spawnFor(levelId)
        }
    }

    private fun spawnFor(levelId: String) {
        val server = server ?: return
        val level = LevelManager.get(levelId) ?: return
        val points = level.spawnPoints.filter { TAG in it.tags }
        if (points.isEmpty()) return
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return
        val state = state(server)
        for (point in points) {
            val key = "$levelId/${point.id}"
            if (key in state.spawned) continue
            val paleMan = ModEntities.PALE_MAN.create(world) ?: continue
            paleMan.refreshPositionAndAngles(point.position.x, point.position.y, point.position.z, point.yaw, 0f)
            paleMan.headYaw = point.yaw
            paleMan.homeKey = key
            paleMan.setPersistent()
            if (world.spawnEntity(paleMan)) {
                state.spawned += key
                state.markDirty()
                EveryTunnel.LOGGER.info("Pale man spawned at {} ({}, {}, {})", key, point.position.x, point.position.y, point.position.z)
            }
        }
    }

    /**
     * How many new games this world has had (`/everytunnel newgame`). A Pale man that has
     * seen fewer goes back to his spawn point ([PaleManEntity]), also one that was in an
     * unloaded chunk when the new game started.
     */
    fun newGames(server: MinecraftServer): Int = state(server).newGames

    fun startNewGame(server: MinecraftServer) {
        val state = state(server)
        state.newGames++
        state.markDirty()
    }

    /** The spawn point [key] has no Pale man any more. */
    fun forget(key: String) {
        val state = state(server ?: return)
        if (state.spawned.remove(key)) state.markDirty()
    }

    private fun state(server: MinecraftServer): SpawnedState =
        server.overworld.persistentStateManager.getOrCreate(SpawnedState::read, ::SpawnedState, "every_tunnel_pale_men")

    /** Spawn points ("level/point") that already have their Pale man in this world. */
    class SpawnedState : PersistentState() {
        val spawned: MutableSet<String> = linkedSetOf()
        var newGames = 0

        override fun writeNbt(nbt: NbtCompound): NbtCompound {
            nbt.put("spawned", NbtList().apply { spawned.forEach { add(NbtString.of(it)) } })
            nbt.putInt("new_games", newGames)
            return nbt
        }

        companion object {
            fun read(nbt: NbtCompound): SpawnedState = SpawnedState().apply {
                nbt.getList("spawned", NbtElement.STRING_TYPE.toInt()).forEach { spawned += it.asString() }
                newGames = nbt.getInt("new_games")
            }
        }
    }
}
