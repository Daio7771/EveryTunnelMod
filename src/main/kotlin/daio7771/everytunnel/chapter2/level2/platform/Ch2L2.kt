package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Anchor
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Checkpoint
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Event
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Snapshot
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.chapter2.platform.Ch2Return
import daio7771.everytunnel.chapter2.platform.MapBuild
import daio7771.everytunnel.chapter2.prologue.platform.PrologueSceneHooks
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.platform.ChapterRequest
import daio7771.everytunnel.platform.ChapterRestarts
import daio7771.everytunnel.platform.DeathHooks
import daio7771.everytunnel.platform.LightMode
import daio7771.everytunnel.systems.ResourceSystem
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Chapter 2's Level 2, the catacombs, as the rest of the mod sees it, as [daio7771.everytunnel.chapter2.level1.platform.Ch2L1] is Level
 * 1: each player's state ([Ch2L2State]), whether they are playing it ([active]: its rules apply to them, `Chapter2`), and Chapter 2's
 * saved game, all in their Chapter 2 progress ([Ch2Progress]).
 *
 * - **In** ([enter]): down Level 1's manhole, or `/et ch2 l2 enter`. The level is built if it is not, the player's pockets are emptied
 *   (Level 1's pass is no use down there; the flashlight is equipment, not an item, and stays), their stamina, thirst and battery are
 *   set as `level.json` says (`start`), the lamps as it says (`lights`), and they stand at [ENTRY_ANCHOR], where the first checkpoint
 *   is taken.
 * - **On**: walking into a zone of `level.json`'s `progress` moves the player on to its state, never back ([setState] tells the
 *   listeners, as playing and the commands do); walking into a checkpoint's zone ahead of the last one taken takes it: the state, the
 *   counters and what they carry are the saved game ([Ch2L2Snapshot]), and the world's folder gets Chapter 2's marker, so the menu
 *   offers CONTINUE ([ChapterRequest.setSaved]).
 * - **Dying** ([ChapterRestarts]): the base game's death, then the player stands at the last checkpoint, the state goes back to the one
 *   it had then, and the level is started again there ([onStart]).
 * - **CONTINUE** ([continueFromMenu]): the way back to Chapter 1 is kept ([Ch2Return]), and the player is at the last checkpoint with
 *   that checkpoint's state, counters and things, full of health and resources.
 * - **Out** ([leave]): leaving the world ([Ch2Return]); the saved game stays.
 *
 * Two kinds of listener: [onStateChange], a state left for another (by playing or by command), and [onStart], the level started at a
 * state (entering, a death, CONTINUE, `reset`): put the world as that state wants it, whatever was there. Server thread only.
 */
object Ch2L2 {

    /** Told after a player's state changed, from [from] to [to]. */
    fun interface StateListener {
        fun changed(player: ServerPlayerEntity, from: Ch2L2State, to: Ch2L2State)
    }

    /** Told when the level is (re)started for a player at [state]. */
    fun interface StartListener {
        fun started(player: ServerPlayerEntity, state: Ch2L2State)
    }

    /** Where a player arriving from Level 1 stands: the foot of the manhole's ladder. */
    const val ENTRY_ANCHOR = "sealed_entry"

    /** How often (ticks) where the players are is looked at. */
    private const val CHECK_EVERY = 5

    /** The screen's fade back from black on arriving or continuing. */
    private const val FADE_IN_SECONDS = 1.5f

    private val listeners = CopyOnWriteArrayList<StateListener>()
    private val starts = CopyOnWriteArrayList<StartListener>()

    private val data get() = Ch2L2DataLoader.data

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ChapterRestarts.register { player -> if (active(player)) restartFor(player) else null }
    }

    fun onStateChange(listener: StateListener) {
        listeners += listener
    }

    fun onStart(listener: StartListener) {
        starts += listener
    }

    fun state(player: PlayerEntity): Ch2L2State = Ch2Progress.ch2L2State(player)

    /** The player is playing the level: its rules apply to them. */
    fun active(player: PlayerEntity): Boolean = Ch2Progress.ch2L2Active(player)

    /** The checkpoint the player last took, or null. */
    fun checkpoint(player: PlayerEntity): Ch2L2Checkpoint? = data.level.checkpoint(Ch2Progress.ch2L2Snapshot(player)?.checkpoint)

    /** Puts the player in [to] and tells the listeners; [reason] only goes to the log. False, and nothing happens, if they already are. */
    fun setState(player: ServerPlayerEntity, to: Ch2L2State, reason: String): Boolean {
        val from = state(player)
        if (from == to) return false
        Ch2Progress.setCh2L2State(player, to)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: {} goes {} -> {} ({})", player.gameProfile.name, from, to, reason)
        for (listener in listeners) {
            try {
                listener.changed(player, from, to)
            } catch (e: Exception) {
                EveryTunnel.LOGGER.error("Chapter 2 Level 2: a state listener failed on {} -> {}", from, to, e)
            }
        }
        return true
    }

    /** Moves the player on to the next state, as if they had done what ends the current one; null at the end. */
    fun skip(player: ServerPlayerEntity): Ch2L2State? {
        val next = state(player).next() ?: return null
        setState(player, next, "skip")
        return next
    }

    /** The player past [event], as if they had lived it; false if they already are. */
    fun skip(player: ServerPlayerEntity, event: Ch2L2Event): Boolean =
        state(player) < event.after && setState(player, event.after, "skip ${event.id}")

    /**
     * [event] to live again: the state just before it, the player at the checkpoint that comes before it (which is taken), and the level
     * started there, so walking on plays it. Null if it worked, else why not.
     */
    fun reset(player: ServerPlayerEntity, event: Ch2L2Event): String? {
        val checkpoint = data.level.checkpointBefore(event) ?: return "level.json has no checkpoint before ${event.id}."
        val point = data.anchors.point(checkpoint.anchor) ?: return "anchors.json has no anchor ${checkpoint.anchor}."
        val world = levelWorld(player.server) ?: return "The anchors' dimension ${data.anchors.dimension} is not there."
        setState(player, event.before, "reset ${event.id}")
        teleport(player, world, point)
        save(player, checkpoint)
        started(player, world, lights = true)
        return null
    }

    // --- in and out -------------------------------------------------------------------------------

    /**
     * Makes [player] begin the level (see the class): it is built first if it must be (a few seconds), and they go in when it stands;
     * [fadeIn]: the screen, black from Level 1's descent, fades back once they are there. Null if it works or has begun to, else why not.
     */
    fun enter(player: ServerPlayerEntity, fadeIn: Boolean = false): String? {
        if (data.anchors.point(ENTRY_ANCHOR) == null) return "anchors.json has no anchor called $ENTRY_ANCHOR."
        if (levelWorld(player.server) == null) return "The anchors' dimension ${data.anchors.dimension} is not there."
        val server = player.server
        val id = player.uuid
        return built(server) { server.playerManager.getPlayer(id)?.let { enterNow(it, fadeIn) } }
    }

    private fun enterNow(player: ServerPlayerEntity, fadeIn: Boolean) {
        val world = levelWorld(player.server) ?: return
        val start = data.anchors.point(ENTRY_ANCHOR) ?: return
        val level = data.level
        Ch2Progress.clearCh2L2(player)
        Ch2Progress.setCh2L2Active(player, true)
        player.closeHandledScreen()
        player.inventory.clear()
        val config = ConfigManager.gameplay
        val resources = ResourceSystem.stateOf(player.uuid, config)
        ResourceSystem.set(
            player.uuid,
            resources.copy(
                stamina = level.startStamina * config.stamina.max,
                thirst = level.startThirst * config.thirst.max,
                battery = level.startBattery * config.flashlight.batteryMaxSeconds,
                exhausted = false,
            ),
        )
        teleport(player, world, start)
        save(player, level.checkpoints.first())
        started(player, world, lights = true)
        if (fadeIn) DeathHooks.sendFade(player, 0f, FADE_IN_SECONDS)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: {} enters at {}", player.gameProfile.name, ENTRY_ANCHOR)
    }

    /**
     * The menu's CONTINUE of Chapter 2: [player], in the world just opened, goes back to Level 2's last checkpoint (see the class). False,
     * and nothing happens, if there is no saved game.
     */
    fun continueFromMenu(player: ServerPlayerEntity): Boolean {
        val snapshot = Ch2Progress.ch2L2Snapshot(player) ?: return false
        Ch2Return.keep(player)
        DeathHooks.sendFade(player, 1f, 0f)
        val server = player.server
        val id = player.uuid
        val why = if (levelWorld(server) == null) "The anchors' dimension ${data.anchors.dimension} is not there."
        else built(server) { server.playerManager.getPlayer(id)?.let { resume(it, snapshot) } }
        if (why == null) return true
        EveryTunnel.LOGGER.warn("Chapter 2's CONTINUE: {}", why)
        Ch2Progress.setCh2Return(player, null)
        DeathHooks.sendFade(player, 0f, FADE_IN_SECONDS)
        player.sendMessage(Text.literal(why))
        return true
    }

    private fun resume(player: ServerPlayerEntity, snapshot: Ch2L2Snapshot) {
        val world = levelWorld(player.server) ?: return
        val checkpoint = data.level.checkpoint(snapshot.checkpoint) ?: data.level.checkpoints.first()
        val point = data.anchors.point(checkpoint.anchor) ?: data.anchors.point(ENTRY_ANCHOR) ?: return
        Ch2Progress.setCh2L2Active(player, true)
        Ch2Progress.setCh2L2State(player, snapshot.state)
        Ch2Progress.replaceCh2L2Counters(player, snapshot.counters)
        player.closeHandledScreen()
        player.inventory.clear()
        Ch2Progress.ch2L2Inventory(player)?.let { player.inventory.readNbt(it) }
        player.health = player.maxHealth
        ResourceSystem.reset(player.uuid)
        teleport(player, world, point)
        started(player, world, lights = true)
        DeathHooks.sendFade(player, 0f, FADE_IN_SECONDS)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: {} continues at {} ({})", player.gameProfile.name, checkpoint.id, snapshot.state)
    }

    /** [player] is out of the level: the rules of the rest of the game apply to them again. Their saved game stays. */
    fun leave(player: ServerPlayerEntity) {
        Ch2Progress.setCh2L2Active(player, false)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: {} leaves", player.gameProfile.name)
    }

    /** Level 2 and its saved game forgotten (Chapter 2 begun again from the menu). */
    fun forget(player: ServerPlayerEntity) {
        Ch2Progress.clearCh2L2(player)
        ChapterRequest.setSaved(player.server, PrologueSceneHooks.MENU_CHAPTER, false)
    }

    // --- checkpoints and deaths -------------------------------------------------------------------------

    /** Takes [checkpoint]: the saved game is the player's state, counters and things now, and the menu can continue it. */
    private fun save(player: ServerPlayerEntity, checkpoint: Ch2L2Checkpoint) {
        val snapshot = Ch2L2Snapshot(checkpoint.id, state(player), Ch2Progress.ch2L2Counters(player))
        Ch2Progress.setCh2L2Snapshot(player, snapshot, player.inventory.writeNbt(NbtList()))
        ChapterRequest.setSaved(player.server, PrologueSceneHooks.MENU_CHAPTER, true)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: {} reaches the checkpoint {} ({})", player.gameProfile.name, checkpoint.id, snapshot.state)
    }

    /** Where a death of [player] brings them back: the last checkpoint, its state again. */
    private fun restartFor(player: ServerPlayerEntity): ChapterRestarts.Restart {
        val point = checkpoint(player)?.let { data.anchors.point(it.anchor) } ?: data.anchors.point(ENTRY_ANCHOR)
        val world = Identifier.tryParse(data.anchors.dimension) ?: player.serverWorld.registryKey.value
        val reset: (ServerPlayerEntity) -> Unit = { p ->
            Ch2Progress.ch2L2Snapshot(p)?.let { Ch2Progress.setCh2L2State(p, it.state) }
            levelWorld(p.server)?.let { started(p, it, lights = false) }
            EveryTunnel.LOGGER.info("Chapter 2 Level 2: {} died, back at {} ({})", p.gameProfile.name, checkpoint(p)?.id, state(p))
        }
        if (point == null) return ChapterRestarts.Restart(world, player.x, player.y, player.z, player.yaw, player.pitch, reset = reset)
        val p = point.position
        return ChapterRestarts.Restart(world, p.x, p.y, p.z, point.yaw, point.pitch, reset = reset)
    }

    // --- the clock ---------------------------------------------------------------------------------

    private fun tick(server: MinecraftServer) {
        if (server.ticks % CHECK_EVERY != 0) return
        for (player in server.playerManager.playerList) {
            if (!active(player) || !player.isAlive || player.isSpectator || DeathHooks.isDying(player)) continue
            if (player.serverWorld.registryKey.value.toString() != data.anchors.dimension) continue
            val inZone = { id: String -> data.anchors.zone(id)?.box?.contains(player.x, player.y, player.z) == true }
            data.level.checkpointAhead(Ch2Progress.ch2L2Snapshot(player)?.checkpoint, inZone)?.let { save(player, it) }
            data.level.stateAhead(state(player), inZone)?.let { setState(player, it, "walked in") }
        }
    }

    // --- helpers ----------------------------------------------------------------------------------

    /** The level stood up (built if it must be), then [whenDone]; null, or why it cannot. */
    private fun built(server: MinecraftServer, whenDone: () -> Unit): String? = when (Ch2L2Build.ask(server, rebuild = false, whenDone)) {
        MapBuild.Asked.BUSY -> "The catacombs are being cleared: try again in a moment."
        MapBuild.Asked.FAILED -> "The catacombs cannot be built: ${Ch2L2Build.lastProblem}"
        MapBuild.Asked.STARTED, MapBuild.Asked.JOINED, MapBuild.Asked.BUILT -> null
    }

    /** The level started for [player] at their state: the lamps as `level.json` says ([lights]), then the start listeners. */
    private fun started(player: ServerPlayerEntity, world: ServerWorld, lights: Boolean) {
        if (lights) {
            for ((zone, mode) in data.level.lights) Ch2L2Build.zoneLights(world, zone, LightMode.valueOf(mode.uppercase()))
        }
        val state = state(player)
        for (listener in starts) {
            try {
                listener.started(player, state)
            } catch (e: Exception) {
                EveryTunnel.LOGGER.error("Chapter 2 Level 2: a start listener failed at {}", state, e)
            }
        }
    }

    private fun levelWorld(server: MinecraftServer): ServerWorld? =
        Identifier.tryParse(data.anchors.dimension)?.let { server.getWorld(RegistryKey.of(RegistryKeys.WORLD, it)) }

    private fun teleport(player: ServerPlayerEntity, world: ServerWorld, point: Anchor) {
        player.stopRiding()
        player.teleport(world, point.position.x, point.position.y, point.position.z, point.yaw, point.pitch)
        player.velocity = Vec3d.ZERO
        player.fallDistance = 0f
    }
}
