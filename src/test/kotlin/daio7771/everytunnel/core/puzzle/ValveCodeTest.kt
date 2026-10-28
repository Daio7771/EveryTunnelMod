package daio7771.everytunnel.core.puzzle

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValveCodeTest {

    @Test
    fun `a code has one digit per valve, each 0 to 9, and every digit comes up`() {
        val random = Random(3)
        val seen = mutableSetOf<Int>()
        repeat(200) {
            val code = ValveCode.random(random, 5)
            assertEquals(5, code.size)
            assertTrue(code.all { it in 0..9 })
            seen += code
        }
        assertEquals((0..9).toSet(), seen)
    }

    @Test
    fun `a turn adds one and nine goes back to zero`() {
        assertEquals(1, ValveCode.next(0))
        assertEquals(9, ValveCode.next(8))
        assertEquals(0, ValveCode.next(9))
    }

    @Test
    fun `solved only with every valve on its digit`() {
        val code = listOf(3, 0, 7, 9, 1)
        assertTrue(ValveCode.solved(code, listOf(3, 0, 7, 9, 1)))
        assertFalse(ValveCode.solved(code, listOf(3, 0, 7, 9, 2)))
        assertFalse(ValveCode.solved(code, listOf(3, 0, 7, 9, null)))
        assertFalse(ValveCode.solved(code, listOf(3, 0, 7, 9)))
    }
}
