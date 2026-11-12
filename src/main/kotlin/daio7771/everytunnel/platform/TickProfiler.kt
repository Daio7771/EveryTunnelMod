package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.profile.TickStats
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.util.Identifier

/**
 * Times the server's tick and each of the mod's tick hooks, to know what to optimise (F9). Every hook
 * registers through [endServerTick] instead of `ServerTickEvents.END_SERVER_TICK`: while the profiler is off
 * that costs one flag read per hook per tick. While it is on, the cost of each hook (named after the class
 * that registered it) goes to a [TickStats], and a line with the window's results is logged every
 * [WINDOW_TICKS] ticks (30 s): `[profile] 600 ticks | tick avg ... | top: ...`.
 *
 * Switched on by `/everytunnel profile on` (development worlds), or from the start by the system property
 * `every_tunnel.profile=true` (headless runs). `/everytunnel profile report` prints the current window.
 */
object TickProfiler {

    /** Where the mod's tick hooks register: the same as `ServerTickEvents.END_SERVER_TICK`, timed. */
    val endServerTick = Registrar()

    @Volatile
    var enabled: Boolean = System.getProperty("every_tunnel.profile") == "true"
        private set

    private val stats = TickStats()
    private var tickStart = 0L

    class Registrar {
        fun register(listener: ServerTickEvents.EndTick) {
            val name = Throwable().stackTrace[1].className.substringAfterLast('.').substringBefore('$')
            ServerTickEvents.END_SERVER_TICK.register { server ->
                if (!enabled) {
                    listener.onEndTick(server)
                } else {
                    val start = System.nanoTime()
                    listener.onEndTick(server)
                    stats.recordHook(name, System.nanoTime() - start)
                }
            }
        }
    }

    fun register() {
        val last = Identifier(EveryTunnel.MOD_ID, "profiler_last")
        ServerTickEvents.END_SERVER_TICK.addPhaseOrdering(Event.DEFAULT_PHASE, last)
        ServerTickEvents.START_SERVER_TICK.register { tickStart = System.nanoTime() }
        ServerTickEvents.END_SERVER_TICK.register(last) {
            if (enabled) {
                stats.recordTick(System.nanoTime() - tickStart)
                if (stats.ticks >= WINDOW_TICKS) flush()
            }
        }
        if (enabled) EveryTunnel.LOGGER.info("[profile] on (every {} ticks)", WINDOW_TICKS)
    }

    fun start() {
        stats.reset()
        enabled = true
        EveryTunnel.LOGGER.info("[profile] on (every {} ticks)", WINDOW_TICKS)
    }

    fun stop() {
        if (enabled && stats.ticks > 0) flush()
        enabled = false
        EveryTunnel.LOGGER.info("[profile] off")
    }

    /** The window so far, logged and returned, without closing it. */
    fun report(): String = stats.summary().also { EveryTunnel.LOGGER.info("[profile] {}", it) }

    private fun flush() {
        EveryTunnel.LOGGER.info("[profile] {}", stats.summary())
        stats.reset()
    }

    private const val WINDOW_TICKS = 600
}
