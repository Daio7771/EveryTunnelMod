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
    /** Background sound while a player is in this level (a zone may have its own: [ambienceIn]). */
    val ambience: Ambience = Ambience.NONE,
    /** What a player gets on walking in from the previous level, or null for nothing. */
    val entry: LevelEntry? = null,
    /** The scene a new game starts with in this level (the elevator), or null for none. */
    val intro: LevelIntro? = null,
    /**
     * Function id (e.g. `garage:build`) that builds this level's blocks, run every time the
     * world opens, behind the loading screen; null if the level is built by hand. Levels
     * built together name the same function, which runs once.
     */
    val build: String? = null,
) {
    fun zone(id: String): Zone? = zones.firstOrNull { it.id == id }

    fun spawnPoint(id: String): SpawnPoint? = spawnPoints.firstOrNull { it.id == id }

    /**
     * The ambience for a player in the zones [zoneIds] of this level: that of the first zone
     * (in file order) among them with its own, else the level's.
     */
    fun ambienceIn(zoneIds: Set<String>): Ambience =
        zones.firstOrNull { it.ambience != null && it.id in zoneIds }?.ambience ?: ambience

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
    /** The zone's own ambience in place of the level's while a player is in it (see [LevelDefinition.ambienceIn]); null for none. */
    val ambience: Ambience? = null,
)

/** A named position (player start, enemy spawn, ...). [tags] describe its use. */
data class SpawnPoint(
    val id: String,
    val position: Position,
    val yaw: Float,
    val pitch: Float,
    val tags: Set<String>,
    /**
     * Id of a zone of the same level that whatever spawns here stays in, or null. A Pale man
     * never leaves it and ignores every noise and player outside it.
     */
    val zone: String? = null,
)

data class Position(val x: Double, val y: Double, val z: Double)

/**
 * The start of a new game in this level: the player waits [seconds] in the level's start
 * (a zone tagged `start`) behind closed doors (zones tagged `elevator_door`) while [music]
 * plays; then the doors open, the music distorts and fades, and the level's [LevelEntry]
 * title shows.
 */
data class LevelIntro(
    val seconds: Double,
    /** Sound event id of the song, or null for none. */
    val music: String?,
)

/**
 * Arriving from an earlier level (by [LevelDefinition.order]): a title on screen
 * ([title] large, [subtitle] under it; empty strings show nothing) and a sound in the
 * player's head. Shutters in zones tagged `shutter` close behind the player.
 */
data class LevelEntry(
    val title: String,
    val subtitle: String,
    /** Sound event id, e.g. `every_tunnel:event.level_transition`, or null for none. */
    val sound: String?,
)

/**
 * A level's background sound: a loop heard everywhere in the level (not positioned)
 * and sounds played now and then at random spots around the player. Sound ids are
 * Minecraft sound events, e.g. `every_tunnel:ambient.drip`.
 */
data class Ambience(
    /** Looped while the player is in the level, or null for none. */
    val loop: String?,
    val loopVolume: Double,
    val randomSounds: List<RandomSound>,
) {
    companion object {
        val NONE = Ambience(null, 1.0, emptyList())
    }
}

/** A sound played every [minInterval]-[maxInterval] seconds, [minDistance]-[maxDistance] blocks from the player. */
data class RandomSound(
    val sound: String,
    val minInterval: Double,
    val maxInterval: Double,
    val minDistance: Double,
    val maxDistance: Double,
    val volume: Double,
)

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
