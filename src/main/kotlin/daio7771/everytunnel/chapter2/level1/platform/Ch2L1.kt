package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.systems.ResourceSystem
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.concurrent.CopyOnWriteArrayList
import daio7771.everytunnel.chapter2.platform.Ch2Progress

/**
 * Chapter 2's Level 1 as the rest of the mod sees it: the state machine ([Ch2L1State]) of each player, kept in
 * their progress ([Ch2Progress]) so it survives leaving the world, dying and a new session.
 *
 * The level's rules follow the player, not the place: while a player is **active** ([active], a flag in their progress
 * that [enter] sets and [leave] clears) they have no flashlight, nothing hurts them, their sprint is free and makes no noise
 * for the AI, and the survival bars are gone ([SafeLevelHooks], [FlashlightHooks], [NoiseHooks] and the client's HUD ask).
 * Nothing about it depends on where they stand, so it works before the level's map exists; the chapter's new game and the
 * map's entrance will call [enter] later.
 *
 * Every change goes through [setState] (the level's own systems, `/et ch2 l1 state` and `skip`), which logs it and
 * tells the listeners, so a step can be reached by playing or by command and the same things happen either way. A
 * system of the level registers once with [onStateChange] and does what entering a state means (an objective, a
 * scene, a speaker line); this object knows none of that. A listener that throws is logged and does not stop the
 * others, as in [daio7771.everytunnel.world.LevelTriggers]. Server thread only.
 */
object Ch2L1 {

    /** Told after a player's state changed, from [from] to [to]. */
    fun interface StateListener {
        fun changed(player: ServerPlayerEntity, from: Ch2L1State, to: Ch2L1State)
    }

    /** The anchor a new hire starts at, in front of the time clock. */
    const val SPAWN_ANCHOR = "spawn_clockin"

    private val listeners = CopyOnWriteArrayList<StateListener>()

    fun register() {
        // Back at the clock-in step, the clocks show it: red again.
        onStateChange { player, _, to -> if (to == Ch2L1State.ACT1_CLOCKIN) TimeClockBlock.relockNear(player) }
    }

    fun onStateChange(listener: StateListener) {
        listeners += listener
    }

    fun state(player: PlayerEntity): Ch2L1State = Ch2Progress.ch2L1State(player)

    /**
     * Puts the player in [to] and tells the listeners; [reason] only goes to the log. False, and nothing happens, if
     * they already are there.
     */
    fun setState(player: ServerPlayerEntity, to: Ch2L1State, reason: String): Boolean {
        val from = state(player)
        if (from == to) return false
        Ch2Progress.setCh2L1State(player, to)
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: {} goes {} -> {} ({})", player.gameProfile.name, from, to, reason)
        for (listener in listeners) {
            try {
                listener.changed(player, from, to)
            } catch (e: Exception) {
                EveryTunnel.LOGGER.error("Chapter 2 Level 1: a state listener failed on {} -> {}", from, to, e)
            }
        }
        return true
    }

    /**
     * Moves the player on to the next state, as if they had done what ends the current one; null if they are at the
     * end. The states' own effects (objects, objectives, characters) are the listeners' job, so skipping and playing
     * leave the same state of the world.
     */
    fun skip(player: ServerPlayerEntity): Ch2L1State? {
        val next = state(player).next() ?: return null
        setState(player, next, "skip")
        return next
    }

    /** The player is playing the level: its rules apply to them. */
    fun active(player: PlayerEntity): Boolean = Ch2Progress.ch2L1Active(player)

    /**
     * Makes [player] a new hire of the factory: active, at the start of the level ([Ch2L1State.START]), their pockets
     * empty (they are another person, with none of Chapter 1's things: no flashlight, no battery), the clocks red, and
     * at the anchor [SPAWN_ANCHOR]. The level must stand in the world: if it does not (or was built from another plan)
     * it is built first ([Ch2L1Build], a second or two) and the player goes in when it is done. Null if it works or has
     * begun to, else why not; the player is told in chat either way.
     */
    fun enter(player: ServerPlayerEntity): String? {
        if (Ch2L1DataLoader.data.anchors.point(SPAWN_ANCHOR) == null) return "anchors.json has no anchor called $SPAWN_ANCHOR."
        val server = player.server
        val id = player.uuid
        val asked = Ch2L1Build.ask(server, rebuild = false) {
            server.playerManager.getPlayer(id)?.let(::enterNow)
        }
        when (asked) {
            Ch2L1Build.Asked.BUSY -> return "The level is being cleared: try again in a moment."
            Ch2L1Build.Asked.FAILED -> return "The level cannot be built: ${Ch2L1Build.lastProblem}"
            Ch2L1Build.Asked.STARTED, Ch2L1Build.Asked.JOINED ->
                player.sendMessage(Text.literal("Building the factory: you go in when it is done."), false)
            Ch2L1Build.Asked.BUILT -> {}
        }
        return null
    }

    /** The level stands: [player] becomes a new hire (see [enter]). */
    private fun enterNow(player: ServerPlayerEntity) {
        val anchors = Ch2L1DataLoader.data.anchors
        val start = anchors.point(SPAWN_ANCHOR)
        val world = Identifier.tryParse(anchors.dimension)?.let { player.server.getWorld(RegistryKey.of(RegistryKeys.WORLD, it)) }
        if (start == null || world == null) {
            player.sendMessage(Text.literal("The level has no $SPAWN_ANCHOR in ${anchors.dimension}."), false)
            return
        }
        Ch2Progress.setCh2L1Active(player, true)
        Ch2L1SpeakerHooks.forget(player)
        setState(player, Ch2L1State.START, "enter")
        player.inventory.clear()
        val resources = ResourceSystem.stateOf(player.uuid, ConfigManager.gameplay)
        if (resources.flashlightOn) ResourceSystem.set(player.uuid, ResourceSystem.switchOff(resources))
        player.stopRiding()
        player.teleport(world, start.position.x, start.position.y, start.position.z, start.yaw, start.pitch)
        TimeClockBlock.relockNear(player)
        Ch2L1Npcs.ensureAmbient(player.server)
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: {} enters at {}{}", player.gameProfile.name, SPAWN_ANCHOR, if (start.provisional) " (provisional)" else "")
        player.sendMessage(
            Text.literal("You are a new hire of the factory, at $SPAWN_ANCHOR, your pockets empty. The level's rules apply in Survival or Adventure (not Creative); /et ch2 l1 leave ends it."),
            false,
        )
    }

    /** [player] is out of the level: the survival rules of the rest of the game apply to them again. */
    fun leave(player: ServerPlayerEntity) {
        Ch2Progress.setCh2L1Active(player, false)
        Ch2L1Npcs.clear(player.server)
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: {} leaves", player.gameProfile.name)
    }
}
