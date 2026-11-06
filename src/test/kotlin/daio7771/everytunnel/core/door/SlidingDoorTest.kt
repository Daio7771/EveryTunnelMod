package daio7771.everytunnel.core.door

import kotlin.test.Test
import kotlin.test.assertEquals

class SlidingDoorTest {

    @Test
    fun `a door four columns wide slides open from the middle, a quarter at a time`() {
        assertEquals(8, SlidingDoor.steps(4))
        assertEquals(listOf(4, 4, 4, 4), SlidingDoor.widths(4, 0))
        assertEquals(listOf(4, 3, 3, 4), SlidingDoor.widths(4, 1))
        assertEquals(listOf(4, 0, 0, 4), SlidingDoor.widths(4, 4))
        assertEquals(listOf(2, 0, 0, 2), SlidingDoor.widths(4, 6))
        assertEquals(listOf(0, 0, 0, 0), SlidingDoor.widths(4, 8))
    }

    @Test
    fun `a narrower door has one column a leaf`() {
        assertEquals(4, SlidingDoor.steps(2))
        assertEquals(listOf(4, 4), SlidingDoor.widths(2, 0))
        assertEquals(listOf(1, 1), SlidingDoor.widths(2, 3))
        assertEquals(listOf(0, 0), SlidingDoor.widths(2, 4))
    }
}
