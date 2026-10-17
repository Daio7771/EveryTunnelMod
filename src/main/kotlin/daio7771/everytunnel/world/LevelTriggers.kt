package daio7771.everytunnel.world

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LocationChange
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Zone enter/exit notifications for gameplay systems.
 *
 * Systems that react to the player's position (level ambience, enemy activation,
 * level transitions, ...) register a [Listener] once at startup. Listeners run on
 * the server thread, once per change. A failing listener is logged and does not
 * stop the others.
 */
object LevelTriggers {

    fun interface Listener {
        fun onLocationChange(change: LocationChange)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    fun register(listener: Listener) {
        listeners += listener
    }

    fun fire(change: LocationChange) {
        for (listener in listeners) {
            try {
                listener.onLocationChange(change)
            } catch (e: Exception) {
                EveryTunnel.LOGGER.error("Level trigger listener {} failed for {}", listener, change, e)
            }
        }
    }
}
