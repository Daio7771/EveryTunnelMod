package daio7771.everytunnel.core.puzzle.darts

/**
 * Where a dart landed on the Game room's board, in plain Kotlin (unit tested). The scoring part is a
 * standard board: 20 sectors, bull, doubles and triples. The numbers stand outside it, on a black
 * band that counts as off the board. `tools/blocks/generate.py` paints the same geometry, and
 * [SCORING_RADIUS_BLOCKS] must match its 38 pixels out of 48.
 */
object DartboardGeometry {

    enum class Ring { BULL, OUTER_BULL, SINGLE, TRIPLE, DOUBLE, MISS }

    data class Hit(val number: Int, val ring: Ring)

    /** The way the board's front looks. */
    enum class Facing { NORTH, SOUTH, EAST, WEST }

    /** The board is 3 blocks across; the wire of its doubles has 38/48 of that radius. */
    const val SCORING_RADIUS_BLOCKS = 1.5 * 38.0 / 48.0

    /** Clockwise from the top. */
    private val ORDER = intArrayOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)

    /** [x] and [y] in radii of the scoring part: (0, 0) its centre, x to the right, y up, as seen from the front. */
    fun resolve(x: Double, y: Double): Hit {
        val r = Math.hypot(x, y)
        if (r > 1.0) return Hit(0, Ring.MISS)
        if (r < 0.037) return Hit(50, Ring.BULL)
        if (r < 0.094) return Hit(25, Ring.OUTER_BULL)

        var angle = Math.toDegrees(Math.atan2(x, y)) // 0 = up, clockwise
        if (angle < 0) angle += 360.0
        val number = ORDER[((angle + 9.0) / 18.0).toInt() % 20]

        val ring = when {
            r in 0.582..0.629 -> Ring.TRIPLE
            r >= 0.953 -> Ring.DOUBLE
            else -> Ring.SINGLE
        }
        return Hit(number, ring)
    }

    /** A hit from a world position relative to the board's centre, in blocks. */
    fun resolveWorld(facing: Facing, dx: Double, dy: Double, dz: Double): Hit {
        // To the right of someone looking at the front.
        val right = when (facing) {
            Facing.NORTH -> -dx
            Facing.SOUTH -> dx
            Facing.EAST -> -dz
            Facing.WEST -> dz
        }
        return resolve(right / SCORING_RADIUS_BLOCKS, dy / SCORING_RADIUS_BLOCKS)
    }
}
