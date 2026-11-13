package daio7771.everytunnel.core.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeftBehindTest {

    @Test
    fun `nothing is useless in the first two levels`() {
        assertTrue(LeftBehind.uselessFrom(1).isEmpty())
        assertTrue(LeftBehind.uselessFrom(2).isEmpty())
    }

    @Test
    fun `the keycard goes on reaching Level 3 and the master card on reaching Level 5`() {
        assertEquals(setOf("keycard"), LeftBehind.uselessFrom(3))
        assertEquals(setOf("keycard"), LeftBehind.uselessFrom(4))
        val level5 = LeftBehind.uselessFrom(5)
        assertTrue("keycard" in level5)
        assertEquals(setOf("keycard", "master_card_nw", "master_card_ne", "master_card_sw", "master_card_se", "master_card"), level5)
    }

    @Test
    fun `what is of use in every level is never left behind`() {
        val all = LeftBehind.uselessFrom(10)
        for (id in listOf("battery", "water_bottle", "food_plate", "food_popcorn", "food_snack", "wrench")) assertFalse(id in all, id)
    }

    @Test
    fun `later levels keep leaving behind what earlier ones did`() {
        assertEquals(LeftBehind.uselessFrom(5), LeftBehind.uselessFrom(9))
    }
}
