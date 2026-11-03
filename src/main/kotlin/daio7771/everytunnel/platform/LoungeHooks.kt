package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.DoorBlock
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * From Level 3 to Level 4, in play. Level 4 stands where Level 3 stood, so it cannot be built
 * until the player has left:
 *
 * - Walking up to the door to B4 (a zone tagged [APPROACH_TAG]) opens it (the spawn point tagged
 *   [DOOR_TAG]).
 * - Walking into the diploma corridor behind it (a zone tagged [BUILD_TAG]) shuts it and starts the
 *   build: `garage:level4_begin` force-loads the area, and once the chunks are there
 *   (`/everytunnel loungeready`, [chunksReady]) one function `garage:level4_step_<n>` runs every
 *   [STEP_TICKS], in order, until there is no next: Level 3 goes and Level 4 goes up. Small steps,
 *   written by the map generator, so the game does not stall while the player walks the corridor.
 *   Then its loot goes out ([LootSpawner]) and the doors in zones tagged [LOUNGE_DOOR_TAG] open:
 *   the way into Reception.
 *
 * What stands is not saved: the map is rebuilt whenever the world opens ([levelsRebuilt]). A new
 * game in the same world (`/everytunnel newgame`) rebuilds it too, if any of this has begun.
 */
object LoungeHooks {

    const val APPROACH_TAG = "b4_door_open"
    const val BUILD_TAG = "lounge_build"
    const val DOOR_TAG = "b4_door"
    const val CORRIDOR_TAG = "b4_corridor"
    const val LOUNGE_DOOR_TAG = "lounge_door"

    /** The level built in play. */
    private const val LEVEL_ID = "level_4"
    private const val MAP_BUILD = "garage:build"
    private const val BEGIN = "garage:level4_begin"
    private const val STEP_PREFIX = "garage:level4_step_"

    /** Ticks between steps: a step is at most what a tick can do unnoticed (see the generator). */
    private const val STEP_TICKS = 2

    private enum class Phase { NOT_STARTED, WAITING_FOR_CHUNKS, BUILDING, BUILT }

    // Server thread only.
    private var phase = Phase.NOT_STARTED
    private var nextStep = 0
    private var startTick = 0L
    private var server: MinecraftServer? = null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            phase = Phase.NOT_STARTED
        }
        LevelTriggers.register { change ->
            val tags = change.enteredZones.flatMap { key -> LevelManager.get(key.levelId)?.zone(key.zoneId)?.tags ?: emptyList() }
            val server = server ?: return@register
            val player = server.playerManager.getPlayer(change.playerId) ?: return@register
            if (player.isSpectator || LoadingHooks.isLoading(player)) return@register
            if (APPROACH_TAG in tags && phase == Phase.NOT_STARTED) setDoor(server, open = true)
            if (BUILD_TAG in tags && phase == Phase.NOT_STARTED) begin(server)
        }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    private fun begin(server: MinecraftServer) {
        setDoor(server, open = false)
        phase = Phase.WAITING_FOR_CHUNKS
        startTick = server.ticks.toLong()
        if (!run(server, BEGIN)) {
            EveryTunnel.LOGGER.warn("Function {} not found: Level 4 is not built", BEGIN)
            phase = Phase.NOT_STARTED
        }
    }

    /** A game continued in Level 4: up it goes again, for [ContinueGame.loungeBuilt]. False if it cannot. */
    fun beginAgain(server: MinecraftServer): Boolean {
        if (phase != Phase.NOT_STARTED) return false
        begin(server)
        return phase != Phase.NOT_STARTED
    }

    /** `/everytunnel loungeready`: the chunks are loaded, the steps can start. */
    fun chunksReady() {
        if (phase != Phase.WAITING_FOR_CHUNKS) return
        phase = Phase.BUILDING
        nextStep = 0
    }

    private fun tick(server: MinecraftServer) {
        if (phase != Phase.BUILDING || server.ticks % STEP_TICKS != 0) return
        if (run(server, STEP_PREFIX + nextStep)) {
            nextStep++
            return
        }
        phase = Phase.BUILT
        LevelManager.get(LEVEL_ID)?.let { LootSpawner.place(server, it) }
        openLoungeDoors(server)
        ContinueGame.loungeBuilt(server)
        EveryTunnel.LOGGER.info(
            "Level 4 built in {} steps, {} s", nextStep, "%.1f".format(java.util.Locale.ROOT, (server.ticks - startTick) / 20.0),
        )
    }

    /** The map was rebuilt: Level 3 stands, with its door shut, and Level 4 does not. */
    fun levelsRebuilt() {
        phase = Phase.NOT_STARTED
        LoungePuzzleHooks.reset()
    }

    /** A new game in the same world: the door shut, and Level 3 back if it was erased. */
    fun newGame(server: MinecraftServer) {
        LoungePuzzleHooks.reset()
        if (phase == Phase.NOT_STARTED) {
            setDoor(server, open = false)
            return
        }
        phase = Phase.NOT_STARTED
        if (!run(server, MAP_BUILD)) EveryTunnel.LOGGER.warn("Function {} not found: the map is not rebuilt", MAP_BUILD)
    }

    /** Level 3 is gone, or going: Level 4's build has begun. */
    val levelThreeErased: Boolean
        get() = phase != Phase.NOT_STARTED

    /** [level] is the one built in play, and it does not stand yet. */
    fun notBuiltYet(level: LevelDefinition): Boolean = level.id == LEVEL_ID && phase != Phase.BUILT

    /** Puts [player] in the corridor, as walking in would. False if the level files have no such place. */
    fun toCorridor(player: ServerPlayerEntity): Boolean {
        val level = LevelManager.all().firstOrNull { l -> l.spawnPoints.any { CORRIDOR_TAG in it.tags } } ?: return false
        val point = level.spawnPoints.first { CORRIDOR_TAG in it.tags }
        val world = worldOf(player.server, level) ?: return false
        player.stopRiding()
        player.teleport(world, point.position.x, point.position.y, point.position.z, point.yaw, point.pitch)
        return true
    }

    private fun setDoor(server: MinecraftServer, open: Boolean) {
        for (level in LevelManager.all()) {
            val point = level.spawnPoints.firstOrNull { DOOR_TAG in it.tags } ?: continue
            val world = worldOf(server, level) ?: continue
            val pos = BlockPos.ofFloored(point.position.x, point.position.y, point.position.z)
            val state = world.getBlockState(pos)
            val block = state.block
            if (block is DoorBlock && state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                block.setOpen(null, world, state, pos, open)
            }
        }
    }

    private fun openLoungeDoors(server: MinecraftServer) {
        val level = LevelManager.get(LEVEL_ID) ?: return
        val world = worldOf(server, level) ?: return
        for (zone in level.zones.filter { LOUNGE_DOOR_TAG in it.tags }) {
            val box = zone.box
            for (pos in BlockPos.iterate(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
                val state = world.getBlockState(pos)
                val block = state.block
                if (block is DoorBlock && state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                    block.setOpen(null, world, state, pos.toImmutable(), true)
                }
            }
        }
    }

    private fun worldOf(server: MinecraftServer, level: LevelDefinition): ServerWorld? =
        server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension)))

    /** Runs a function silently. False if there is no such function. */
    private fun run(server: MinecraftServer, id: String): Boolean {
        val functions = server.commandFunctionManager
        val function = functions.getFunction(Identifier(id)).orElse(null) ?: return false
        functions.execute(function, server.commandSource.withSilent())
        return true
    }
}
