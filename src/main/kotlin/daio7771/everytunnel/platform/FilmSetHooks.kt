package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import net.minecraft.entity.SpawnReason
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.GameMode
import java.util.UUID

/**
 * The set for filming the Cinema's film, away from the levels: a parking corridor in perfect order
 * (`garage:film_set`, `FILM_SET` in the map generator), and a figure who walks along it when told
 * ([FilmWalkerEntity]). For dev runs only: `/everytunnel film set|walk|back`. Whoever asks is taken
 * there in Spectator and chooses the camera (F1 hides the HUD).
 */
object FilmSetHooks {

    private const val BUILD = "garage:film_set"

    // Where the figure starts, on the corridor's centre line, and where to look from at first. As FILM_SET says.
    private const val START_X = 304.5
    private const val START_Y = 65.0
    private const val START_Z = 304.0
    private const val SEARCH = 80.0

    private var requester: UUID? = null

    fun begin(player: ServerPlayerEntity) {
        requester = player.uuid
        val function = player.server.commandFunctionManager.getFunction(Identifier(BUILD)).orElse(null)
        if (function == null) {
            EveryTunnel.LOGGER.warn("Function {} not found: run tools/map/map_generator.py", BUILD)
            return
        }
        player.changeGameMode(GameMode.SPECTATOR)
        player.server.commandFunctionManager.execute(function, player.server.commandSource.withSilent())
    }

    /** `/everytunnel filmready`: the corridor stands. */
    fun ready(server: MinecraftServer) {
        val world = server.overworld
        place(world)
        val player = requester?.let(server.playerManager::getPlayer) ?: return
        player.teleport(world, START_X + 30.0, START_Y + 1.6, START_Z, 90f, 0f)
    }

    /** The figure sets off. False if he is not there. */
    fun walk(server: MinecraftServer): Boolean {
        val walker = walkers(server.overworld).firstOrNull() ?: return false
        walker.walking = true
        return true
    }

    /** The figure back at the start, for another take. */
    fun back(server: MinecraftServer) = place(server.overworld)

    private fun place(world: ServerWorld) {
        walkers(world).forEach { it.discard() }
        val walker = ModEntities.FILM_WALKER.create(world) ?: return
        walker.refreshPositionAndAngles(START_X, START_Y, START_Z, -90f, 0f)
        walker.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(START_X, START_Y, START_Z)), SpawnReason.COMMAND, null, null)
        world.spawnEntity(walker)
    }

    private fun walkers(world: ServerWorld): List<FilmWalkerEntity> =
        world.getEntitiesByClass(FilmWalkerEntity::class.java, Box.of(net.minecraft.util.math.Vec3d(START_X + 25.0, START_Y, START_Z), SEARCH, 20.0, 20.0)) { true }
}
