package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.ch2l1.Ch2L1State
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Chapter 2's Level 1 as the rest of the mod sees it: the state machine ([Ch2L1State]) of each player, kept in
 * their progress ([ProgressService]) so it survives leaving the world, dying and a new session.
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

    private val listeners = CopyOnWriteArrayList<StateListener>()

    fun onStateChange(listener: StateListener) {
        listeners += listener
    }

    fun state(player: PlayerEntity): Ch2L1State = ProgressService.get().ch2L1State(player)

    /**
     * Puts the player in [to] and tells the listeners; [reason] only goes to the log. False, and nothing happens, if
     * they already are there.
     */
    fun setState(player: ServerPlayerEntity, to: Ch2L1State, reason: String): Boolean {
        val from = state(player)
        if (from == to) return false
        ProgressService.get().setCh2L1State(player, to)
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
}
