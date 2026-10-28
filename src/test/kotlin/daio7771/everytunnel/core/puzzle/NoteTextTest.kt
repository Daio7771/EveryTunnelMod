package daio7771.everytunnel.core.puzzle

import kotlin.test.Test
import kotlin.test.assertEquals

class NoteTextTest {

    private val words = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

    @Test
    fun `placeholders are filled as digits or words`() {
        val text = "Valve A: {A:word}\nValve B: {B} turns"
        assertEquals("Valve A: seven\nValve B: 0 turns", NoteText.fill(text, mapOf("A" to 7, "B" to 0)) { words[it] })
    }

    @Test
    fun `a placeholder without a value shows a question mark, other braces stay`() {
        assertEquals("Valve C: ? {not one", NoteText.fill("Valve C: {C:word} {not one", emptyMap()) { words[it] })
    }

    @Test
    fun `the first line is the title, blank lines split paragraphs`() {
        val (title, paragraphs) = NoteText.split("\n  PRESSURE LINE  \nLine one.\nLine two.\n\n\nValve A: seven\n")
        assertEquals("PRESSURE LINE", title)
        assertEquals(listOf("Line one.\nLine two.", "Valve A: seven"), paragraphs)
    }
}
