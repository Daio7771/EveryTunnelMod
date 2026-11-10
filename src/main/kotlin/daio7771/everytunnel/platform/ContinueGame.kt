package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.core.progress.Checkpoint
import daio7771.everytunnel.core.save.LoungeSave
import daio7771.everytunnel.systems.ResourceSystem
import daio7771.everytunnel.world.LevelTriggers
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import net.minecraft.world.GameMode
import java.nio.file.Files
import java.nio.file.Path

/**
 * The one saved game, in the map world. There is a checkpoint at the start of each level: it notes which
 * level ([ProgressService.setCheckpoint]) and what the player carries. A file in the world's folder
 * ([MARKER]) tells the main menu there is something to continue, without opening the world.
 *
 * CONTINUE ([requested], set by the menu) takes the place of [NewGame.start] when the world opens: the world is
 * set up as for a new game, but the player is put at the checkpoint's level start, with what they carried then.
 *
 * Level 4 is kept as it was left. Leaving the world writes it down ([LoungeSave]). On CONTINUE it is built
 * again behind the loading screen ([LoungeHooks.beginAgain]), each puzzle takes its state back, and the player
 * appears in Reception with what they carry now, but what belongs to a puzzle in progress goes back to its
 * place. Taking the Master Key is undone: the lights come back and the key is back on its table, the big door
 * shut, so that moment can be lived again. Levels 1 to 3 start over from their start, as they do on dying.
 *
 * Level 5 starts over from its start too, but it does not stand when the world opens: on CONTINUE it is built
 * behind the loading screen ([LabHooks.beginAgain]) while the player waits in Level 4's diploma corridor, which
 * always stands.
 *
 * A level may also have restart points inside it (zones tagged [RESTART_TAG], each with a spawn point tagged the
 * same inside it: Level 5's Lab hall and, in the drain, its Junction). Walking in makes it the checkpoint, the same
 * way ([Checkpoint]: `level/point`): what the player carries is noted, and a death ([DeathHooks]) or CONTINUE
 * comes back there, not to the level's start. It is never taken back by an earlier one (of an earlier level, or
 * listed earlier in the level's file).
 *
 * Seeing the credits ([finished]) erases the saved game. Achievements are kept apart and stay.
 */
object ContinueGame {

    const val MARKER = "every_tunnel_continue"
    const val RESTART_TAG = "restart_point"
    private const val LEVEL_4 = "level_4"
    private const val LEVEL_5 = "level_5"

    /** The next opening of the world continues the saved game. Set by the main menu. */
    @Volatile
    var requested = false

    private var server: MinecraftServer? = null

    /** The player waiting behind the loading screen for a level built in play to stand again, or null. */
    private var waiting: ServerPlayerEntity? = null

    /** Which level [waiting] waits for. */
    private var waitingFor: String? = null

    /** Level 4 or Level 5 is being put back: the loading screen waits. */
    val waitingForBuild: Boolean
        get() = waiting != null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            waiting = null
            waitingFor = null
            requested = false
        }
        LevelTriggers.register { change ->
            if (!change.levelChanged) return@register
            val level = change.current.levelId?.let(LevelManager::get) ?: return@register
            val player = server?.playerManager?.getPlayer(change.playerId) ?: return@register
            if (LoadingHooks.isLoading(player) || !WorldRules.appliesTo(player.isCreative, player.isSpectator)) return@register
            reached(player, level)
        }
        LevelTriggers.register { change ->
            val key = change.enteredZones.firstOrNull { key ->
                LevelManager.get(key.levelId)?.zone(key.zoneId)?.tags?.contains(RESTART_TAG) == true
            } ?: return@register
            val level = LevelManager.get(key.levelId) ?: return@register
            val player = server?.playerManager?.getPlayer(change.playerId) ?: return@register
            if (LoadingHooks.isLoading(player) || !WorldRules.appliesTo(player.isCreative, player.isSpectator)) return@register
            val box = level.zone(key.zoneId)?.box ?: return@register
            val points = level.spawnPoints.filter { RESTART_TAG in it.tags }
            val point = points.firstOrNull { box.contains(it.position.x, it.position.y, it.position.z) } ?: points.firstOrNull() ?: return@register
            restartPointReached(player, level, point.id)
        }
        // Before the player's data is written, so this goes with it.
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> writeLounge(handler.player) }
    }

    /** A new game has just started in [level]: that is the first checkpoint, and it replaces any other. */
    fun newGame(player: ServerPlayerEntity, level: LevelDefinition?) {
        ProgressService.get().setLounge(player, null)
        if (level == null) {
            setMarker(player.server, false)
            return
        }
        checkpoint(player, level)
    }

    /** The credits: nothing to continue any more. */
    fun finished(player: ServerPlayerEntity) {
        val progress = ProgressService.get()
        progress.setCheckpoint(player, null)
        progress.setLounge(player, null)
        setMarker(player.server, false)
        EveryTunnel.LOGGER.info("{}: the saved game is finished", player.name.string)
    }

    private fun reached(player: ServerPlayerEntity, level: LevelDefinition) {
        val current = Checkpoint.parse(ProgressService.get().checkpoint(player))?.let { LevelManager.get(it.levelId) }
        if (current != null && current.order >= level.order) return
        checkpoint(player, level)
    }

    private fun restartPointReached(player: ServerPlayerEntity, level: LevelDefinition, pointId: String) {
        val progress = ProgressService.get()
        val current = Checkpoint.parse(progress.checkpoint(player))
        val currentLevel = current?.let { LevelManager.get(it.levelId) }
        if (current != null && currentLevel != null) {
            if (currentLevel.order > level.order || current == Checkpoint(level.id, pointId)) return
            val order = level.spawnPoints.filter { RESTART_TAG in it.tags }.map { it.id }
            if (current.levelId == level.id && order.indexOf(current.pointId) > order.indexOf(pointId)) return
        }
        progress.setCheckpoint(player, Checkpoint(level.id, pointId).toString())
        progress.saveInventory(player)
        setMarker(player.server, true)
        level.spawnPoint(pointId)?.let { point ->
            val dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))
            player.setSpawnPoint(dimension, BlockPos.ofFloored(point.position.x, point.position.y, point.position.z), point.yaw, true, false)
        }
        player.server.playerManager.saveAllPlayerData()
        EveryTunnel.LOGGER.info("{}: checkpoint at {}, {}", player.name.string, level.id, pointId)
    }

    private fun checkpoint(player: ServerPlayerEntity, level: LevelDefinition) {
        val progress = ProgressService.get()
        progress.setCheckpoint(player, level.id)
        progress.saveInventory(player)
        setMarker(player.server, true)
        player.server.playerManager.saveAllPlayerData()
        EveryTunnel.LOGGER.info("{}: checkpoint at {}", player.name.string, level.id)
    }

    private fun setMarker(server: MinecraftServer, on: Boolean) {
        val path: Path = server.getSavePath(WorldSavePath.ROOT).resolve(MARKER)
        try {
            if (on) Files.writeString(path, "") else Files.deleteIfExists(path)
        } catch (e: java.io.IOException) {
            EveryTunnel.LOGGER.warn("The saved game's marker could not be changed", e)
        }
    }

    /** Takes the place of [NewGame.start]. False if there is nothing to continue: then a new game it is. */
    fun start(player: ServerPlayerEntity): Boolean {
        val progress = ProgressService.get()
        val checkpoint = Checkpoint.parse(progress.checkpoint(player)) ?: return false
        val level = LevelManager.get(checkpoint.levelId) ?: return false
        val server = player.server
        NewGame.resetWorld(server)
        player.clearStatusEffects()
        player.extinguish()
        player.fallDistance = 0f
        player.health = player.maxHealth
        player.hungerManager.foodLevel = 20
        player.hungerManager.saturationLevel = 5f
        ResourceSystem.reset(player.uuid)
        player.changeGameMode(GameMode.SURVIVAL)
        EveryTunnel.LOGGER.info("{}: continuing from {}", player.name.string, level.id)
        if (level.id == LEVEL_4) {
            LoungeHooks.toCorridor(player)
            waiting = player
            waitingFor = LEVEL_4
            if (!LoungeHooks.beginAgain(server)) loungeBuilt(server)
            return true
        }
        if (level.id == LEVEL_5) {
            LoungeHooks.toCorridor(player)
            waiting = player
            waitingFor = LEVEL_5
            if (!LabHooks.beginAgain(server)) labBuilt(server)
            return true
        }
        progress.restoreInventory(player)
        progress.setLevel3Books(player, 0)
        toStart(player, level, checkpoint.pointId)
        IntroHooks.doorsOpenIfDone(player, level)
        return true
    }

    /** Level 4 stands again ([LoungeHooks]). */
    fun loungeBuilt(server: MinecraftServer) {
        if (waitingFor != LEVEL_4) return
        val player = waiting ?: return
        waiting = null
        waitingFor = null
        val level = LevelManager.get(LEVEL_4) ?: return
        val world = worldOf(server, level) ?: return
        val save = LoungeSave.fromText(ProgressService.get().lounge(player))
        if (save != null) {
            LoungePuzzleHooks.restore(save, server, player)
            DartHooks.restore(save, world, level, player)
            BreakerHooks.restore(save, world, level)
            SafeHooks.restore(save, world)
            LootSpawner.restoreTaken(save.lootTaken, world, level)
        }
        LoungeClosingHooks.undoKey(player)
        toStart(player, level)
        EveryTunnel.LOGGER.info("{}: Level 4 is back as it was", player.name.string)
    }

    /** Level 5 stands again ([LabHooks]): the player goes to its start, or the Lab hall, with what they carried then. */
    fun labBuilt(server: MinecraftServer) {
        if (waitingFor != LEVEL_5) return
        val player = waiting ?: return
        waiting = null
        waitingFor = null
        val level = LevelManager.get(LEVEL_5) ?: return
        val progress = ProgressService.get()
        progress.restoreInventory(player)
        toStart(player, level, Checkpoint.parse(progress.checkpoint(player))?.pointId)
        EveryTunnel.LOGGER.info("{}: Level 5 stands again", player.name.string)
    }

    private fun writeLounge(player: ServerPlayerEntity) {
        val progress = ProgressService.get()
        if (Checkpoint.parse(progress.checkpoint(player))?.levelId != LEVEL_4 || waiting != null) return
        val level = LevelManager.get(LEVEL_4) ?: return
        if (LoungeHooks.notBuiltYet(level)) return
        val world = worldOf(player.server, level) ?: return
        val save = LoungeSave()
        LoungePuzzleHooks.save(save)
        DartHooks.save(save)
        BreakerHooks.save(save, world, level)
        SafeHooks.save(save, world)
        save.lootTaken = LootSpawner.taken.toList()
        progress.setLounge(player, save.toText())
    }

    /** To the level's start, or to its spawn point [pointId] when it has one by that name. */
    private fun toStart(player: ServerPlayerEntity, level: LevelDefinition, pointId: String? = null) {
        val start = pointId?.let(level::spawnPoint) ?: level.spawnPoints.firstOrNull { RespawnHooks.TAG in it.tags } ?: return
        val world = worldOf(player.server, level) ?: return
        player.teleport(world, start.position.x, start.position.y, start.position.z, start.yaw, start.pitch)
    }

    private fun worldOf(server: MinecraftServer, level: LevelDefinition): ServerWorld? =
        server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension)))
}
