package daio7771.everytunnel.chapter1.level4.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DartsEggTest {

    @Test
    fun `the three other pieces and every safe open qualify`() {
        assertTrue(DartsEgg.applies(piecesTaken = 3, safesOpen = 2, safesTotal = 2))
    }

    @Test
    fun `a missing piece or a safe still shut does not`() {
        assertFalse(DartsEgg.applies(piecesTaken = 2, safesOpen = 2, safesTotal = 2))
        assertFalse(DartsEgg.applies(piecesTaken = 3, safesOpen = 1, safesTotal = 2))
        assertFalse(DartsEgg.applies(piecesTaken = 0, safesOpen = 0, safesTotal = 2))
    }

    @Test
    fun `a level without safes never qualifies`() {
        assertFalse(DartsEgg.applies(piecesTaken = 3, safesOpen = 0, safesTotal = 0))
    }

    @Test
    fun `the sequence is the last three guards`() {
        assertEquals(listOf(12, 13, 14), DartsEgg.SEQUENCE)
    }
}
