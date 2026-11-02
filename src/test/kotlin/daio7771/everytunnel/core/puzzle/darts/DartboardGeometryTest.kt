package daio7771.everytunnel.core.puzzle.darts

import daio7771.everytunnel.core.puzzle.darts.DartboardGeometry.Facing
import daio7771.everytunnel.core.puzzle.darts.DartboardGeometry.Ring
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class DartboardGeometryTest {

    /** A point at [degrees] clockwise from the top, at radius [r]. */
    private fun at(degrees: Double, r: Double) =
        DartboardGeometry.resolve(r * sin(Math.toRadians(degrees)), r * cos(Math.toRadians(degrees)))

    @Test
    fun `the numbers go clockwise from 20`() {
        val order = listOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)
        for ((k, number) in order.withIndex()) assertEquals(number, at(k * 18.0, 0.4).number)
    }

    @Test
    fun `a sector ends halfway to the next`() {
        assertEquals(20, at(8.9, 0.4).number)
        assertEquals(1, at(9.1, 0.4).number)
        assertEquals(20, at(351.1, 0.4).number)
        assertEquals(5, at(350.9, 0.4).number)
    }

    @Test
    fun `the rings from the centre out`() {
        assertEquals(Ring.BULL, at(0.0, 0.02).ring)
        assertEquals(50, at(0.0, 0.02).number)
        assertEquals(Ring.OUTER_BULL, at(0.0, 0.05).ring)
        assertEquals(25, at(0.0, 0.05).number)
        assertEquals(Ring.SINGLE, at(0.0, 0.3).ring)
        assertEquals(Ring.TRIPLE, at(0.0, 0.6).ring)
        assertEquals(Ring.SINGLE, at(0.0, 0.8).ring)
        assertEquals(Ring.DOUBLE, at(0.0, 0.98).ring)
        assertEquals(Ring.MISS, at(0.0, 1.01).ring)
        assertEquals(0, at(0.0, 1.01).number)
    }

    @Test
    fun `doubles and triples still give their number`() {
        assertEquals(20, at(0.0, 0.6).number)
        assertEquals(5, at(342.0, 0.98).number)
    }

    @Test
    fun `right of the board is the same to anyone looking at it, in the 4 orientations`() {
        // Half a radius up and half to the right of 20 falls in sector 4 (45 degrees clockwise).
        val side = DartboardGeometry.SCORING_RADIUS_BLOCKS * 0.5
        assertEquals(4, DartboardGeometry.resolveWorld(Facing.SOUTH, side, side, 0.0).number)
        assertEquals(4, DartboardGeometry.resolveWorld(Facing.NORTH, -side, side, 0.0).number)
        assertEquals(4, DartboardGeometry.resolveWorld(Facing.WEST, 0.0, side, side).number)
        assertEquals(4, DartboardGeometry.resolveWorld(Facing.EAST, 0.0, side, -side).number)
    }

    @Test
    fun `the band with the numbers and the corners are off the board`() {
        assertEquals(Ring.MISS, DartboardGeometry.resolveWorld(Facing.SOUTH, 0.0, 1.3, 0.0).ring)
        assertEquals(Ring.MISS, DartboardGeometry.resolveWorld(Facing.SOUTH, 1.4, 1.4, 0.0).ring)
    }
}
