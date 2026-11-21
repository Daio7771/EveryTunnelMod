package daio7771.everytunnel.common.platform

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.common.core.LevelManager
import daio7771.everytunnel.common.core.level.LevelDefinition
import daio7771.everytunnel.common.core.progress.Checkpoint
import daio7771.everytunnel.common.core.progress.LeftBehind
import daio7771.everytunnel.common.systems.ResourceSystem
import daio7771.everytunnel.common.world.LevelTriggers
import daio7771.everytunnel.common.world.WorldRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
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
 * same inside it: Level 5's Lab hall and, in the drain, its Junction; or with no zone, reached by code: the
 * director's office once his scene is over, [restartPoint]). Walking in makes it the checkpoint, the same
 * way ([Checkpoint]: `level/point`): what the player carries is noted, and a death ([DeathHooks]) or CONTINUE
 * comes back there, not to the level's start. It is never taken back by an earlier one (of an earlier level, or
 * listed earlier in the level's file).
 *
 * A level's checkpoint is not made in a zone tagged [NO_CHECKPOINT_TAG] (Level 5's entry corridor, the Cloud's game,
 * and its frame): it waits for the first zone of the level without it (the airlock). Quitting in the corridor,
 * CONTINUE goes back to Level 4.
 *
 * Seeing the credits ([finished]) erases the saved game. Achievements are kept apart and stay.
 */
object ContinueGame {

    const val MARKER = "every_tunnel_continue"
    const val RESTART_TAG = "restart_point"
    const val NO_CHECKPOINT_TAG = "no_checkpoint"

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

    /**
     * A chapter's levels that are built in play (Chapter 1's Levels 4 and 5): CONTINUE builds them again behind the loading screen,
     * the player waiting ([wait]) until they stand ([finishWaiting]).
     */
    interface BuiltInPlay {
        /** The checkpoint [level] is one of its own: begin its build and return true (the player waits); false: not its. */
        fun resume(player: ServerPlayerEntity, level: LevelDefinition): Boolean

        /** The player leaves the world: what is to be kept of its levels is written down. */
        fun disconnected(player: ServerPlayerEntity)
    }

    val builtInPlay = java.util.concurrent.CopyOnWriteArrayList<BuiltInPlay>()

    /** [player] waits behind the loading screen for the level [levelId] to stand again. */
    fun wait(player: ServerPlayerEntity, levelId: String) {
        waiting = player
        waitingFor = levelId
    }

    /** The level [levelId] stands again: the player who waited for it (and no longer waits), or null if nobody did. */
    fun finishWaiting(levelId: String): ServerPlayerEntity? {
        if (waitingFor != levelId) return null
        val player = waiting ?: return null
        waiting = null
        waitingFor = null
        return player
    }

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            waiting = null
            waitingFor = null
            requested = false
        }
        LevelTriggers.register { change ->
            if (!change.levelChanged && change.enteredZones.isEmpty()) return@register
            val level = change.current.levelId?.let(LevelManager::get) ?: return@register
            if (NO_CHECKPOINT_TAG in LevelManager.tagsOf(change.current.zones)) return@register
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
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> for (levels in builtInPlay) levels.disconnected(handler.player) }
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

    /** [player] has passed [level]'s restart point [pointId] some other way than walking into its zone (the director's scene). */
    fun restartPoint(player: ServerPlayerEntity, level: LevelDefinition, pointId: String) = restartPointReached(player, level, pointId)

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
        leaveBehind(player, level)
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
        leaveBehind(player, level)
        progress.saveInventory(player)
        setMarker(player.server, true)
        player.server.playerManager.saveAllPlayerData()
        EveryTunnel.LOGGER.info("{}: checkpoint at {}", player.name.string, level.id)
    }

    /**
     * Takes out of [player]'s inventory what is of no use from [level] on ([LeftBehind]): the keycard from Level 3, the master
     * card from Level 5. Silently. Done before the inventory is saved for a checkpoint, and again on continuing a game saved before.
     */
    internal fun leaveBehind(player: ServerPlayerEntity, level: LevelDefinition) {
        val useless = LeftBehind.uselessFrom(level.order)
        if (useless.isEmpty()) return
        val inventory = player.inventory
        var taken = 0
        for (list in listOf(inventory.main, inventory.offHand, inventory.armor)) {
            for (i in list.indices) {
                val id = Registries.ITEM.getId(list[i].item)
                if (id.namespace == EveryTunnel.MOD_ID && id.path in useless) {
                    list[i] = ItemStack.EMPTY
                    taken++
                }
            }
        }
        if (taken == 0) return
        inventory.markDirty()
        EveryTunnel.LOGGER.info("{}: {} item(s) left behind on reaching {}", player.name.string, taken, level.id)
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
        leaveBehind(player, level)
        if (builtInPlay.any { it.resume(player, level) }) return true
        progress.restoreInventory(player)
        leaveBehind(player, level)
        progress.setLevel3Books(player, 0)
        toStart(player, level, checkpoint.pointId)
        IntroHooks.doorsOpenIfDone(player, level)
        return true
    }

    /** To the level's start, or to its spawn point [pointId] when it has one by that name. */
    internal fun toStart(player: ServerPlayerEntity, level: LevelDefinition, pointId: String? = null) {
        val start = pointId?.let(level::spawnPoint) ?: level.spawnPoints.firstOrNull { RespawnHooks.TAG in it.tags } ?: return
        val world = worldOf(player.server, level) ?: return
        player.teleport(world, start.position.x, start.position.y, start.position.z, start.yaw, start.pitch)
    }

    internal fun worldOf(server: MinecraftServer, level: LevelDefinition): ServerWorld? =
        server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension)))
}
