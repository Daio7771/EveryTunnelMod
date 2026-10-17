package daio7771.everytunnel.core

import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.core.level.Zone

/**
 * Registry of the levels defined by the loaded world's datapack.
 *
 * Filled by the datapack reload (see `platform.LevelReloadListener`) every time a
 * world loads or `/reload` runs; empty when the world has no level files.
 * Lookups are safe from any thread: the whole set is swapped at once.
 *
 * Which level/zone each player is in is tracked by [PlayerLocationTracker].
 */
object LevelManager {

    data class ZoneHit(val level: LevelDefinition, val zone: Zone)

    private class Snapshot(val ordered: List<LevelDefinition>) {
        val byId: Map<String, LevelDefinition> = ordered.associateBy { it.id }
    }

    @Volatile
    private var snapshot = Snapshot(emptyList())

    /**
     * Replaces all loaded levels. Returns problems that involve several levels
     * (e.g. two levels with the same `order`); the levels are still loaded.
     */
    fun replaceAll(levels: Collection<LevelDefinition>): List<String> {
        val problems = mutableListOf<String>()
        levels.groupBy { it.order }
            .filterValues { it.size > 1 }
            .forEach { (order, sameOrder) ->
                problems += "levels ${sameOrder.joinToString { it.id }} share order $order; they are sorted by id"
            }
        snapshot = Snapshot(levels.sortedWith(compareBy({ it.order }, { it.id })))
        return problems
    }

    /** All loaded levels in progression order. */
    fun all(): List<LevelDefinition> = snapshot.ordered

    fun get(id: String): LevelDefinition? = snapshot.byId[id]

    /** The first level (by order) with a zone containing the position, or null. */
    fun levelAt(dimension: String, x: Double, y: Double, z: Double): LevelDefinition? =
        zoneAt(dimension, x, y, z)?.level

    /**
     * Every zone containing the position, from all levels (levels by order, then
     * zones in file order). Zones may overlap, e.g. an `exit` zone inside a larger one.
     */
    fun zonesAt(dimension: String, x: Double, y: Double, z: Double): List<ZoneHit> {
        val hits = mutableListOf<ZoneHit>()
        for (level in snapshot.ordered) {
            if (level.dimension != dimension) continue
            for (zone in level.zones) {
                if (zone.box.contains(x, y, z)) hits += ZoneHit(level, zone)
            }
        }
        return hits
    }

    /** The first zone (levels by order, then zones in file order) containing the position, or null. */
    fun zoneAt(dimension: String, x: Double, y: Double, z: Double): ZoneHit? {
        for (level in snapshot.ordered) {
            val zone = level.zoneAt(dimension, x, y, z) ?: continue
            return ZoneHit(level, zone)
        }
        return null
    }
}
