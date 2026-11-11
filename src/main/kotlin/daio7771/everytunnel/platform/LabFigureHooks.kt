package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.level.LevelDefinition
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import net.minecraft.world.PersistentState

/**
 * The figure seen far off from Level 5's Lobby ([LabFigureEntity]), once a game: when the level is built
 * ([levelBuilt], from [LabHooks]) it stands at the spawn point tagged [TAG], unless it was seen already
 * this game, and goes to the one tagged [EXIT_TAG] once seen. Whether it was seen is kept with the world
 * (`data/every_tunnel_lab_figure.dat`) until a new game ([newGame]).
 */
object LabFigureHooks {

    const val TAG = "lab_figure"
    const val EXIT_TAG = "lab_figure_exit"

    private var server: MinecraftServer? = null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register { server = null }
    }

    /** [level] has just been built: the figure stands at its end of the corridor, unless seen this game. */
    fun levelBuilt(server: MinecraftServer, level: LevelDefinition) {
        discard(server)
        if (state(server).seen) return
        val point = level.spawnPoints.firstOrNull { TAG in it.tags } ?: return
        val exit = level.spawnPoints.firstOrNull { EXIT_TAG in it.tags } ?: return
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return
        KeptEntities.spawn(world) { w ->
            ModEntities.LAB_FIGURE.create(w)?.apply {
                refreshPositionAndAngles(point.position.x, point.position.y, point.position.z, point.yaw, 0f)
                headYaw = point.yaw
                bodyYaw = point.yaw
                this.exit = Vec3d(exit.position.x, exit.position.y, exit.position.z)
            }
        } ?: return
        EveryTunnel.LOGGER.info("The figure stands at {} ({}, {}, {})", point.id, point.position.x, point.position.y, point.position.z)
    }

    /** It was seen: it goes, and does not come back this game. */
    fun seen(server: MinecraftServer) {
        val state = state(server)
        if (state.seen) return
        state.seen = true
        state.markDirty()
        EveryTunnel.LOGGER.info("The figure was seen")
    }

    /** A new game: it is there again the next time the level is built. */
    fun newGame() {
        val server = server ?: return
        discard(server)
        val state = state(server)
        state.seen = false
        state.markDirty()
    }

    private fun discard(server: MinecraftServer) {
        KeptEntities.forget { it is LabFigureEntity }
        for (world in server.worlds) {
            for (entity in world.iterateEntities().toList()) if (entity is LabFigureEntity) entity.discard()
        }
    }

    private fun state(server: MinecraftServer): SeenState =
        server.overworld.persistentStateManager.getOrCreate(SeenState::read, ::SeenState, "every_tunnel_lab_figure")

    class SeenState : PersistentState() {
        var seen = false

        override fun writeNbt(nbt: NbtCompound): NbtCompound {
            nbt.putBoolean("seen", seen)
            return nbt
        }

        companion object {
            fun read(nbt: NbtCompound): SeenState = SeenState().apply { seen = nbt.getBoolean("seen") }
        }
    }
}
