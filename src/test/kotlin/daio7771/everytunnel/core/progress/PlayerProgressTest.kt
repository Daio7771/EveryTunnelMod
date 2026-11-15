package daio7771.everytunnel.core.progress

import daio7771.everytunnel.core.ch2l1.Ch2L1State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerProgressTest {

    @Test
    fun `a piece counts once and belongs to its level`() {
        val progress = PlayerProgress()
        assertTrue(progress.addPiece("level_4", "ch1_l4_piece_bar"))
        assertFalse(progress.addPiece("level_4", "ch1_l4_piece_bar"))
        assertTrue(progress.hasPiece("level_4", "ch1_l4_piece_bar"))
        assertFalse(progress.hasPiece("level_3", "ch1_l4_piece_bar"))
        assertEquals(1, progress.pieceCount("level_4"))
        assertEquals(0, progress.pieceCount("level_3"))
    }

    @Test
    fun `notes and scenes say when they are new`() {
        val progress = PlayerProgress()
        assertFalse(progress.hasReadNote("ch1_l4_note_bar"))
        assertTrue(progress.markNoteRead("ch1_l4_note_bar"))
        assertFalse(progress.markNoteRead("ch1_l4_note_bar"))
        assertTrue(progress.hasReadNote("ch1_l4_note_bar"))
        assertTrue(progress.markSceneSeen("ch1_l4_scene_entry"))
        assertFalse(progress.markSceneSeen("ch1_l4_scene_entry"))
        assertTrue(progress.hasSeenScene("ch1_l4_scene_entry"))
    }

    @Test
    fun `the books never go below zero`() {
        val progress = PlayerProgress()
        progress.level3Books = -3
        assertEquals(0, progress.level3Books)
    }

    @Test
    fun `the copies handed out do not change the progress`() {
        val progress = PlayerProgress()
        progress.addPiece("level_4", "a")
        val pieces = progress.pieces
        progress.addPiece("level_4", "b")
        assertEquals(setOf("a"), pieces["level_4"])
    }

    @Test
    fun `clear forgets everything`() {
        val progress = PlayerProgress()
        progress.addPiece("level_4", "a")
        progress.markNoteRead("n")
        progress.markSceneSeen("s")
        progress.level3Books = 2
        progress.checkpoint = "c"
        progress.clear()
        assertTrue(progress.pieces.isEmpty())
        assertTrue(progress.notes.isEmpty())
        assertTrue(progress.scenes.isEmpty())
        assertEquals(0, progress.level3Books)
        assertNull(progress.checkpoint)
    }

    @Test
    fun `the time played and the deaths are never negative and a new game clears them`() {
        val progress = PlayerProgress()
        progress.playTicks = 1200
        progress.deaths = 3
        progress.playTicks -= 5000
        progress.deaths -= 10
        assertEquals(0, progress.playTicks)
        assertEquals(0, progress.deaths)
        progress.playTicks = 99
        progress.deaths = 4
        progress.clear()
        assertEquals(0, progress.playTicks)
        assertEquals(0, progress.deaths)
    }

    @Test
    fun `a player begins Chapter 2's Level 1 at the clock and a new game takes them back there`() {
        val progress = PlayerProgress()
        assertEquals(Ch2L1State.ACT1_CLOCKIN, progress.ch2l1State)
        progress.ch2l1State = Ch2L1State.ACT3_EXPLORE
        assertTrue("ACT3_EXPLORE" in progress.describe())
        progress.clear()
        assertEquals(Ch2L1State.ACT1_CLOCKIN, progress.ch2l1State)
    }

    @Test
    fun `playing Chapter 2's Level 1 is a flag that a new game clears`() {
        val progress = PlayerProgress()
        assertFalse(progress.ch2l1Active)
        progress.ch2l1Active = true
        assertTrue("active" in progress.describe())
        progress.clear()
        assertFalse(progress.ch2l1Active)
    }
}
