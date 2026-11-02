package daio7771.everytunnel.core.puzzle

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BreakerCodeTest {

    @Test
    fun `it counts how many are right`() {
        val code = BreakerCode(listOf(true, true, false, false, true))
        assertEquals(2, code.rightCount(List(5) { false }))
        assertEquals(3, code.rightCount(List(5) { true }))
        assertEquals(5, code.rightCount(code.answer))
        assertTrue(code.solvedBy(code.answer))
        assertFalse(code.solvedBy(List(5) { true }))
    }

    @Test
    fun `a drawn code is never all down`() {
        val random = Random(1)
        repeat(500) {
            val code = BreakerCode.draw(random)
            assertEquals(BreakerCode.LEVERS, code.answer.size)
            assertTrue(code.answer.any { it })
        }
    }
}
