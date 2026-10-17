package daio7771.everytunnel.core.level

import kotlin.math.max
import kotlin.math.min

/**
 * Data model for one level of the map, parsed from
 * `data/every_tunnel/levels/<id>.json` in the world's datapack (see [LevelParser]).
 * Plain Kotlin types only, no Minecraft classes.
 */
data class LevelDefinition(
    /** File name without `.json`, e.g. `level_1`. */
    val id: String,
    /** Readable name for logs and debug output. */
    val name: String,
    /** Progression order: Level 1 = 1, Level 2 = 2, ... */
    val order: Int,
    /** Dimension id, e.g. `minecraft:overworld`. */
    val dimension: String,
    val zones: List<Zone>,
    val spawnPoints: List<SpawnPoint>,
) {
    fun zone(id: String): Zone? = zones.firstOrNull { it.id == id }

    fun spawnPoint(id: String): SpawnPoint? = spawnPoints.firstOrNull { it.id == id }

    /** First zone of this level containing the position, or null. */
    fun zoneAt(dimension: String, x: Double, y: Double, z: Double): Zone? {
        if (dimension != this.dimension) return null
        return zones.firstOrNull { it.box.contains(x, y, z) }
    }
}

/** A named area of a level. [tags] mark special zones for later systems (e.g. `exit`). */
data class Zone(
    val id: String,
    val box: BlockBox,
    val tags: Set<String>,
)

/** A named position (player start, enemy spawn, ...). [tags] describe its use. */
data class SpawnPoint(
    val id: String,
    val position: Position,
    val yaw: Float,
    val pitch: Float,
    val tags: Set<String>,
)

data class Position(val x: Double, val y: Double, val z: Double)

/**
 * Box of whole blocks with both corners included, the same convention as `/fill`:
 * from [0, 64, 0] to [1, 64, 1] covers four blocks.
 */
data class BlockBox(
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxY: Int, val maxZ: Int,
) {
    init {
        require(minX <= maxX && minY <= maxY && minZ <= maxZ) { "min corner must not exceed max corner: $this" }
    }

    /** True if the point (e.g. an entity's position) is inside one of the box's blocks. */
    fun contains(x: Double, y: Double, z: Double): Boolean =
        x >= minX && x < maxX + 1.0 &&
            y >= minY && y < maxY + 1.0 &&
            z >= minZ && z < maxZ + 1.0

    companion object {
        /** Builds a box from two opposite corners given in any order. */
        fun ofCorners(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int): BlockBox =
            BlockBox(min(x1, x2), min(y1, y2), min(z1, z2), max(x1, x2), max(y1, y2), max(z1, z2))
    }
}
