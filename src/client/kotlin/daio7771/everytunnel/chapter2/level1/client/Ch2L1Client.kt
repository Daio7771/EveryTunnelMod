package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.chapter2.level1.core.Ch2L1Bits
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Objectives
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Tasks
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1DataLoader
import net.minecraft.text.Text
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.platform.Ch2Progress
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
        return Ch2Progress.ch2L1Active(player)
    }

    /** The local player's state in the level; the start if there is no player. */
    fun state(): Ch2L1State {
        val player = MinecraftClient.getInstance().player ?: return Ch2L1State.START
        return Ch2Progress.ch2L1State(player)
    }

    /**
     * The lang key of the objective to show now, or null: not in the level, or the state has none. In Act 3's exploring it moves on with
     * what the player has done ([Ch2L1Act3.objective]: the auxiliary lab, the way into the office, searching it).
     */
    fun objectiveKey(): String? {
        if (!active()) return null
        val state = state()
        val player = MinecraftClient.getInstance().player
        if (state == Ch2L1State.ACT3_EXPLORE && player != null) {
            return Ch2L1Objectives.PREFIX + Ch2L1DataLoader.act3.objective { Ch2Progress.ch2L1Counter(player, it) }
        }
        return Ch2L1Objectives.keyFor(state)
    }

    /**
     * The objective as the panel writes it: its text, and where the player is in it where it has a count ("(1/3)": the tubes taken, then the
     * samples registered, in the first task; the dirt swept in the third). The player's counters come with their progress.
     */
    fun objectiveText(key: String): Text {
        val player = MinecraftClient.getInstance().player ?: return Text.translatable(key)
        val tasks = Ch2L1DataLoader.tasks
        val service = Ch2Progress
        val (done, total) = when (state()) {
            Ch2L1State.ACT1_TASK1 -> {
                val taken = Ch2L1Bits.count(service.ch2L1Counter(player, Ch2L1Tasks.TUBES))
                val count = if (taken < tasks.tubes.size) taken else Ch2L1Bits.count(service.ch2L1Counter(player, Ch2L1Tasks.REGISTERED))
                count to tasks.tubes.size
            }
            Ch2L1State.ACT1_TASK3 ->
                Ch2L1Bits.count(service.ch2L1Counter(player, Ch2L1Tasks.SWEPT)) to
                    (Ch2L1DataLoader.data.anchors.path(tasks.dirtPath)?.points?.size ?: 0)
            else -> 0 to 0
        }
        return if (total > 0) Text.translatable("every_tunnel.ch2.l1.objective.count", Text.translatable(key), done, total) else Text.translatable(key)
    }
}
