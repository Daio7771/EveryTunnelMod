package daio7771.everytunnel.client

import daio7771.everytunnel.core.ch2l1.Ch2L1Objectives
import daio7771.everytunnel.core.ch2l1.Ch2L1State
import daio7771.everytunnel.platform.ProgressService
import net.minecraft.client.MinecraftClient

/**
 * What the client knows of Chapter 2's Level 1 for the local player: whether they are playing it and in which state. Read
 * from the player's progress (`platform.CardinalProgress`), which the server sends to that player alone on every change,
 * so there is no packet of its own.
 */
object Ch2L1Client {

    /** The local player is playing the level: its rules apply (no status bars here, an objective on the panel). */
    fun active(): Boolean {
        val player = MinecraftClient.getInstance().player ?: return false
        return ProgressService.get().ch2L1Active(player)
    }

    /** The local player's state in the level; the start if there is no player. */
    fun state(): Ch2L1State {
        val player = MinecraftClient.getInstance().player ?: return Ch2L1State.START
        return ProgressService.get().ch2L1State(player)
    }

    /** The lang key of the objective to show now, or null: not in the level, or the state has none. */
    fun objectiveKey(): String? = if (active()) Ch2L1Objectives.keyFor(state()) else null
}
