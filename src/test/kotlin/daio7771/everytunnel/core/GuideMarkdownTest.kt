package daio7771.everytunnel.core

import daio7771.everytunnel.core.guide.GuideBlock
import daio7771.everytunnel.core.guide.GuideMarkdown
import daio7771.everytunnel.core.guide.GuideSpan
import kotlin.test.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GuideMarkdownTest {

    @Test
    fun `headings, paragraphs and the title`() {
        val page = GuideMarkdown.parse(
            "01-how-to-play",
            """
            # How to play

            Welcome to the night shift. You are
            the new guard.

            ## Controls
            ### Small
            """.trimIndent(),
        )
        assertEquals("How to play", page.title)
        assertEquals(
            listOf(
                GuideBlock.Heading(1, listOf(GuideSpan("How to play"))),
                GuideBlock.Paragraph(listOf(GuideSpan("Welcome to the night shift. You are the new guard."))),
                GuideBlock.Heading(2, listOf(GuideSpan("Controls"))),
                GuideBlock.Heading(3, listOf(GuideSpan("Small"))),
            ),
            page.blocks,
        )
    }

    @Test
    fun `list items continue over indented and lazy lines`() {
        val page = GuideMarkdown.parse(
            "p",
            """
            - **Flashlight:** the key, F by default. You can change it in Options,
              Controls.
            - Second item
            continued without indent.
            1. First
            2. Second
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                GuideBlock.ListItem(
                    null,
                    listOf(GuideSpan("Flashlight:", bold = true), GuideSpan(" the key, F by default. You can change it in Options, Controls.")),
                ),
                GuideBlock.ListItem(null, listOf(GuideSpan("Second item continued without indent."))),
                GuideBlock.ListItem(1, listOf(GuideSpan("First"))),
                GuideBlock.ListItem(2, listOf(GuideSpan("Second"))),
            ),
            page.blocks,
        )
    }

    @Test
    fun `rules end blocks and the title falls back to the id`() {
        val page = GuideMarkdown.parse("02-noise", "Some text\n---\nMore")
        assertEquals("02-noise", page.title)
        assertEquals(
            listOf(
                GuideBlock.Paragraph(listOf(GuideSpan("Some text"))),
                GuideBlock.Rule,
                GuideBlock.Paragraph(listOf(GuideSpan("More"))),
            ),
            page.blocks,
        )
    }

    @Test
    fun `bold and italic, nested and unclosed`() {
        assertEquals(
            listOf(GuideSpan("See the "), GuideSpan("Technical data", italic = true), GuideSpan(" page.")),
            GuideMarkdown.spans("See the *Technical data* page."),
        )
        assertEquals(
            listOf(GuideSpan("a "), GuideSpan("b ", bold = true), GuideSpan("c", bold = true, italic = true), GuideSpan(" d", bold = true)),
            GuideMarkdown.spans("a **b *c* d**"),
        )
        assertEquals(listOf(GuideSpan("5 * 3 and **open")), GuideMarkdown.spans("5 * 3 and **open"))
    }

    /** The real pages (tests run from the repository root): titled, the same files in both languages, no stray markers. */
    @Test
    fun `the guide pages in docs`() {
        val languages = listOf("en", "es").associateWith { language ->
            File("docs/$language/guide").listFiles { file -> file.name.endsWith(".md") }.orEmpty().sortedBy { it.name }
        }
        assertEquals(languages.getValue("en").map { it.name }, languages.getValue("es").map { it.name })
        assertTrue(languages.getValue("en").isNotEmpty())
        for (file in languages.values.flatten()) {
            val page = GuideMarkdown.parse(file.nameWithoutExtension, file.readText())
            assertNotEquals(file.nameWithoutExtension, page.title, "$file has no # title")
            val text = page.blocks.flatMap { block ->
                when (block) {
                    is GuideBlock.Heading -> block.spans
                    is GuideBlock.Paragraph -> block.spans
                    is GuideBlock.ListItem -> block.spans
                    GuideBlock.Rule -> emptyList()
                }
            }.joinToString("") { it.text }
            assertFalse("*" in text || "[" in text || "`" in text || "|" in text, "$file uses Markdown the guide does not draw")
        }
    }

    @Test
    fun `windows line endings`() {
        val page = GuideMarkdown.parse("p", "# Title\r\n\r\nText\r\n")
        assertEquals("Title", page.title)
        assertEquals(GuideBlock.Paragraph(listOf(GuideSpan("Text"))), page.blocks.last())
    }
}
