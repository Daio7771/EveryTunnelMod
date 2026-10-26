package daio7771.everytunnel.core.menu

import kotlin.test.Test
import kotlin.test.assertEquals

class BlockLightTest {

    @Test
    fun `light falls by one per block in open space`() {
        val light = BlockLight(20, 5, 5)
        light.setEmission(2, 2, 2, 13)
        light.compute()
        assertEquals(13, light.levelAt(2, 2, 2))
        assertEquals(12, light.levelAt(3, 2, 2))
        assertEquals(8, light.levelAt(7, 2, 2))
        assertEquals(6, light.levelAt(7, 3, 3), "five steps along x, one up, one across")
        assertEquals(0, light.levelAt(16, 2, 2))
        assertEquals(0, light.levelAt(-1, 2, 2), "outside the box")
    }

    @Test
    fun `an opaque wall stops light and it goes round through a gap`() {
        // A one block high slice: a wall at x = 2 from z = 0 to 3, a gap at z = 4.
        val light = BlockLight(5, 1, 5)
        for (z in 0..3) light.setOpacity(2, 0, z, 15)
        light.setEmission(0, 0, 0, 15)
        light.compute()
        assertEquals(0, light.levelAt(2, 0, 0), "inside the wall")
        assertEquals(15 - 6, light.levelAt(2, 0, 4), "in the gap: 2 + 4 steps")
        assertEquals(15 - 11, light.levelAt(3, 0, 0), "behind the wall: round through the gap, 2 + 4 + 1 + 4 steps")
    }

    @Test
    fun `a partly opaque block dims light more than air`() {
        val light = BlockLight(5, 1, 1)
        light.setOpacity(2, 0, 0, 3)
        light.setEmission(0, 0, 0, 12)
        light.compute()
        assertEquals(11, light.levelAt(1, 0, 0))
        assertEquals(8, light.levelAt(2, 0, 0))
        assertEquals(7, light.levelAt(3, 0, 0))
    }

    @Test
    fun `computing again after a light goes out clears it`() {
        val light = BlockLight(5, 1, 1)
        light.setEmission(0, 0, 0, 12)
        light.compute()
        light.setEmission(0, 0, 0, 0)
        light.compute()
        assertEquals(0, light.levelAt(2, 0, 0))
    }
}
