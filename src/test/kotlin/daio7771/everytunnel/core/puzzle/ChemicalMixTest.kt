package daio7771.everytunnel.core.puzzle

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChemicalMixTest {

    @Test
    fun `the pair works in either tank`() {
        val mix = ChemicalMix(2, 4)
        assertTrue(mix.right(2, 4))
        assertTrue(mix.right(4, 2))
        assertFalse(mix.right(2, 3))
        assertFalse(mix.right(4, 4))
        assertFalse(mix.right(0, 2))
    }

    @Test
    fun `a drawn pair is two different reagents, the lower first`() {
        repeat(200) { seed ->
            val mix = ChemicalMix.draw(Random(seed))
            assertTrue(mix.first in 1..4 && mix.second in 1..4 && mix.first < mix.second)
        }
    }

    @Test
    fun `every pair comes up`() {
        val seen = (0 until 400).map { ChemicalMix.draw(Random(it)).let { m -> m.first to m.second } }.toSet()
        assertTrue(seen.size == 6)
    }

    @Test
    fun `not a pair is refused`() {
        assertFailsWith<IllegalArgumentException> { ChemicalMix(3, 3) }
        assertFailsWith<IllegalArgumentException> { ChemicalMix(0, 5) }
    }
}
