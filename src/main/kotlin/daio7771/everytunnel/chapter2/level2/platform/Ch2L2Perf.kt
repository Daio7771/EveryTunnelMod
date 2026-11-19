package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.chapter2.level2.core.TickBudget
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer

/**
 * The clock behind `/et ch2 l2 perf`: Level 2's tick handlers ([register]) and the entities' brains ([time]) are timed with
 * `System.nanoTime()` into a [TickBudget] of the last 100 ticks. Server thread only.
 */
object Ch2L2Perf {

    val budget = TickBudget()

    /** Registers [handler] on the server's tick, timed as [name]. */
    fun register(name: String, handler: (MinecraftServer) -> Unit) {
        ServerTickEvents.END_SERVER_TICK.register { server -> time(server, name) { handler(server) } }
    }

    /** Runs [block], its time counted under [name] in this server tick. */
    inline fun <T> time(server: MinecraftServer, name: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            budget.add(server.ticks.toLong(), name, System.nanoTime() - start)
        }
    }
}
