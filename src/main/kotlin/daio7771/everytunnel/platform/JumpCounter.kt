package daio7771.everytunnel.platform

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.stat.Stats
import java.util.UUID

/**
 * Jumps per player since the previous call, from the vanilla jump statistic (the
 * server counts every jump; there is no jump event). Each system that needs jumps
 * keeps its own counter. Server thread only.
 */
class JumpCounter {

    private val lastStat = HashMap<UUID, Int>()

    /** Jumps since the previous call for this player (0 on the first call). */
    fun jumpsSince(player: ServerPlayerEntity): Int {
        val count = player.statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.JUMP))
        val previous = lastStat.put(player.uuid, count) ?: return 0
        return (count - previous).coerceAtLeast(0)
    }

    fun forget(playerId: UUID) {
        lastStat.remove(playerId)
    }

    fun clear() {
        lastStat.clear()
    }
}
