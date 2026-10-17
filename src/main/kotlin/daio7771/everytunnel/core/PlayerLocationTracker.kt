package daio7771.everytunnel.core

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A zone, identified by its level and its id inside that level. */
data class ZoneKey(val levelId: String, val zoneId: String)

/**
 * Where a player is: the current level (the first level by order that has a zone
 * containing the player, or null when outside every level) and every zone the
 * player is inside, from any level.
 */
data class PlayerLocation(val levelId: String?, val zones: Set<ZoneKey>) {
    companion object {
        val NOWHERE = PlayerLocation(null, emptySet())
    }
}

/** A change of [PlayerLocation] between two updates. */
data class LocationChange(
    val playerId: UUID,
    val previous: PlayerLocation,
    val current: PlayerLocation,
) {
    val levelChanged: Boolean get() = previous.levelId != current.levelId
    val enteredZones: Set<ZoneKey> get() = current.zones - previous.zones
    val exitedZones: Set<ZoneKey> get() = previous.zones - current.zones
}

/**
 * Remembers the [PlayerLocation] of each player and reports changes.
 *
 * Fed every server tick with the player's position (see
 * `platform.LevelTrackingHooks`). Plain Kotlin types only, so it is unit tested
 * without Minecraft. Zones are compared by id, so a datapack `/reload` that
 * removes or renames a zone shows up as leaving it.
 */
object PlayerLocationTracker {

    private val locations = ConcurrentHashMap<UUID, PlayerLocation>()

    fun locationOf(playerId: UUID): PlayerLocation = locations[playerId] ?: PlayerLocation.NOWHERE

    /** Updates the player's location and returns the change, or null if nothing changed. */
    fun update(playerId: UUID, dimension: String, x: Double, y: Double, z: Double): LocationChange? {
        val hits = LevelManager.zonesAt(dimension, x, y, z)
        val current = PlayerLocation(
            levelId = hits.firstOrNull()?.level?.id,
            zones = hits.mapTo(LinkedHashSet()) { ZoneKey(it.level.id, it.zone.id) },
        )
        val previous = locations.put(playerId, current) ?: PlayerLocation.NOWHERE
        return if (previous == current) null else LocationChange(playerId, previous, current)
    }

    /** Forgets a player (on disconnect). No change is reported. */
    fun remove(playerId: UUID) {
        locations.remove(playerId)
    }

    /** Forgets all players (on server stop). */
    fun clear() {
        locations.clear()
    }
}
