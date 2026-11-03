package daio7771.everytunnel.core.wall

import daio7771.everytunnel.core.wall.WallMessageState.Trigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WallMessageTest {

    @Test
    fun `a fixed one is always there`() {
        val state = WallMessageState(Trigger.FIXED, true)
        state.update(100.0, 180.0, 1f)
        assertEquals(1f, state.opacity)
        assertFalse(state.changed)
    }

    @Test
    fun `on approach it fades in, and stays if once`() {
        val state = WallMessageState(Trigger.ON_APPROACH, true)
        state.update(20.0, 0.0, 1f)
        assertEquals(0f, state.opacity)
        state.update(5.0, 0.0, 3f)
        assertEquals(1f, state.opacity)
        state.update(50.0, 0.0, 3f)
        assertEquals(1f, state.opacity)
    }

    @Test
    fun `on approach, not once, it goes again`() {
        val state = WallMessageState(Trigger.ON_APPROACH, false)
        state.update(5.0, 0.0, 3f)
        state.update(50.0, 0.0, 3f)
        assertEquals(0f, state.opacity)
    }

    @Test
    fun `it only changes after being seen, once out of sight`() {
        val state = WallMessageState(Trigger.ON_LOOK_AWAY_CHANGE, true)
        state.update(5.0, 120.0, 0.1f)
        assertFalse(state.changed)
        state.update(5.0, 10.0, 0.1f)
        assertFalse(state.changed)
        state.update(5.0, 50.0, 0.1f)
        assertFalse(state.changed)
        state.update(5.0, 120.0, 0.1f)
        assertTrue(state.changed)
    }

    @Test
    fun `once it stays changed, otherwise it goes back`() {
        val once = WallMessageState(Trigger.ON_LOOK_AWAY_CHANGE, true)
        val again = WallMessageState(Trigger.ON_LOOK_AWAY_CHANGE, false)
        for (state in listOf(once, again)) {
            repeat(2) {
                state.update(5.0, 10.0, 0.1f)
                state.update(5.0, 120.0, 0.1f)
            }
        }
        assertTrue(once.changed)
        assertFalse(again.changed)
    }

    @Test
    fun `seen from too far does not count`() {
        val state = WallMessageState(Trigger.ON_LOOK_AWAY_CHANGE, true)
        state.update(40.0, 5.0, 0.1f)
        state.update(40.0, 120.0, 0.1f)
        assertFalse(state.changed)
    }

    @Test
    fun `the paint is the same for the same seed, and spaces stay`() {
        assertEquals(WallFont.paint("DON'T STAY", 3), WallFont.paint("DON'T STAY", 3))
        assertNotEquals(WallFont.paint("AAAAAAAA", 1)[0], WallFont.paint("AAAAAAAA", 1)[1])
        assertEquals(' ', WallFont.paint("A B", 1)[1])
        assertEquals(WallFont.paint("a", 1), WallFont.paint("A", 1))
    }
}
