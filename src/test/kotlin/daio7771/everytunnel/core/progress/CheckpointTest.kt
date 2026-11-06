package daio7771.everytunnel.core.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CheckpointTest {

    @Test
    fun `a level's start or a point inside it, written as text and read back`() {
        assertEquals(Checkpoint("level_4"), Checkpoint.parse("level_4"))
        assertEquals(Checkpoint("level_5", "checkpoint"), Checkpoint.parse("level_5/checkpoint"))
        assertEquals("level_4", Checkpoint("level_4").toString())
        assertEquals("level_5/checkpoint", Checkpoint("level_5", "checkpoint").toString())
        assertEquals(Checkpoint("level_5"), Checkpoint.parse("level_5/"))
    }

    @Test
    fun `nothing to go on from`() {
        assertNull(Checkpoint.parse(null))
        assertNull(Checkpoint.parse(""))
        assertNull(Checkpoint.parse("/checkpoint"))
    }
}
