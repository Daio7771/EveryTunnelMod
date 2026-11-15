package daio7771.everytunnel.core.ch2l1

import daio7771.everytunnel.core.level.BlockBox
import daio7771.everytunnel.core.level.Position

/**
 * The named places of Chapter 2's Level 1, from `anchors.json` (see [Ch2L1AnchorsParser]): every system of the
 * level asks for a place by name (`spawn_clockin`, `door_anchor`, `speaker_lab`...) and never by coordinates, so
 * moving the map means editing one file. Plain Kotlin.
 *
 * An anchor is either a **point** (a position and a facing) or a **zone** (a box of blocks). Anything not yet
 * measured on the final map is `provisional`: it stands where it can be tried out and is meant to be replaced.
 */
data class Ch2L1Anchors(
    /** Dimension id of every anchor of the level, e.g. `minecraft:overworld`. */
    val dimension: String,
    val points: List<Anchor>,
    val zones: List<AnchorZone>,
) {
    /** The point called [id], or null. */
    fun point(id: String): Anchor? = points.firstOrNull { it.id == id }

    /** The zone called [id], or null. */
    fun zone(id: String): AnchorZone? = zones.firstOrNull { it.id == id }

    /** Every name, points first, as `/et ch2 l1 goto` suggests them. */
    fun names(): List<String> = points.map { it.id } + zones.map { it.id }

    /** How many anchors (points and zones) are still marked provisional. */
    fun provisionalCount(): Int = points.count { it.provisional } + zones.count { it.provisional }

    companion object {
        /** No anchors at all, what the level has before its file is read or when no copy of it is usable. */
        val EMPTY = Ch2L1Anchors("minecraft:overworld", emptyList(), emptyList())
    }
}

/** A named place to stand and face: [yaw] and [pitch] in degrees, as in a level's spawn points. */
data class Anchor(
    val id: String,
    val position: Position,
    val yaw: Float,
    val pitch: Float,
    val provisional: Boolean,
)

/** A named area (a corridor, the range of a loudspeaker). */
data class AnchorZone(
    val id: String,
    val box: BlockBox,
    val provisional: Boolean,
) {
    /** Where `goto` puts a player: the middle of the box, one block over its floor. */
    fun standingPoint(): Position = Position(
        (box.minX + box.maxX + 1) / 2.0,
        box.minY + 1.0,
        (box.minZ + box.maxZ + 1) / 2.0,
    )
}
