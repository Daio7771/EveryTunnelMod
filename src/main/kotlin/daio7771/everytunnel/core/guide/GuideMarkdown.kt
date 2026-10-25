package daio7771.everytunnel.core.guide

/**
 * A page of the player guide (`docs/<lang>/guide/<id>.md`, shown by the main menu's GUIDE
 * screen, `client.screen.GuideScreen`), parsed into blocks the screen lays out.
 *
 * [title] is the text of the first `# ` heading, or [id] if there is none.
 */
data class GuidePage(val id: String, val title: String, val blocks: List<GuideBlock>)

/** One block of a guide page. */
sealed interface GuideBlock {
    /** `#`, `##` or `###`: [level] 1 to 3. */
    data class Heading(val level: Int, val spans: List<GuideSpan>) : GuideBlock

    data class Paragraph(val spans: List<GuideSpan>) : GuideBlock

    /** A list item: [number] is null for `- ` items, the item's number for `1. ` items. */
    data class ListItem(val number: Int?, val spans: List<GuideSpan>) : GuideBlock

    /** `---`: a separator line. */
    data object Rule : GuideBlock
}

/** A run of text with one style. */
data class GuideSpan(val text: String, val bold: Boolean = false, val italic: Boolean = false)

/**
 * Reads the small Markdown subset the guide pages are written in (see
 * `docs/en/contributing.md`, "Player guide pages"), so they read the same on GitHub and in game:
 *
 * - `# `, `## `, `### ` headings, one line each;
 * - paragraphs, separated by blank lines; the lines of a paragraph are joined with a space;
 * - `- ` list items and `1. ` numbered items; the lines after an item, up to a blank line or
 *   the next block, continue it (indented or not, as GitHub reads them);
 * - `**bold**` and `*italic*` inside any of them; a marker that is never closed is kept as text;
 * - `---` (three or more dashes alone on a line) as a separator.
 *
 * Anything else is read as text, never rejected: a page always shows. Plain Kotlin, unit tested.
 */
object GuideMarkdown {

    private val HEADING = Regex("""^(#{1,3})\s+(.*)$""")
    private val BULLET = Regex("""^[-*]\s+(.*)$""")
    private val NUMBERED = Regex("""^(\d{1,3})\.\s+(.*)$""")
    private val RULE = Regex("""^-{3,}\s*$""")

    fun parse(id: String, text: String): GuidePage {
        val blocks = mutableListOf<GuideBlock>()
        // The block being read: its kind and its lines so far.
        var open: ((String) -> GuideBlock)? = null
        val lines = mutableListOf<String>()

        fun close() {
            val build = open ?: return
            blocks += build(lines.joinToString(" ") { it.trim() }.trim())
            open = null
            lines.clear()
        }

        for (raw in text.lines()) {
            val line = raw.trimEnd()
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> close()
                RULE.matches(trimmed) -> {
                    close()
                    blocks += GuideBlock.Rule
                }
                HEADING.matches(trimmed) -> {
                    close()
                    val match = HEADING.matchEntire(trimmed)!!
                    blocks += GuideBlock.Heading(match.groupValues[1].length, spans(match.groupValues[2].trim()))
                }
                // A list item starts only at the line's start; indented lines continue the last one.
                !line.first().isWhitespace() && BULLET.matches(trimmed) -> {
                    close()
                    open = { GuideBlock.ListItem(null, spans(it)) }
                    lines += BULLET.matchEntire(trimmed)!!.groupValues[1]
                }
                !line.first().isWhitespace() && NUMBERED.matches(trimmed) -> {
                    close()
                    val match = NUMBERED.matchEntire(trimmed)!!
                    val number = match.groupValues[1].toInt()
                    open = { GuideBlock.ListItem(number, spans(it)) }
                    lines += match.groupValues[2]
                }
                else -> {
                    if (open == null) open = { GuideBlock.Paragraph(spans(it)) }
                    lines += trimmed
                }
            }
        }
        close()

        val title = blocks.firstNotNullOfOrNull { block ->
            (block as? GuideBlock.Heading)?.takeIf { it.level == 1 }?.spans?.joinToString("") { it.text }
        } ?: id
        return GuidePage(id, title, blocks)
    }

    /** Splits a line into styled runs at `**` (bold) and `*` (italic) markers that are closed later. */
    fun spans(text: String): List<GuideSpan> {
        val result = mutableListOf<GuideSpan>()
        val current = StringBuilder()
        var bold = false
        var italic = false
        var i = 0

        fun flush() {
            if (current.isNotEmpty()) result += GuideSpan(current.toString(), bold, italic)
            current.clear()
        }

        while (i < text.length) {
            if (text.startsWith("**", i)) {
                // Opens only if it closes later; closes whatever bold is open.
                if (bold || text.indexOf("**", i + 2) >= 0) {
                    flush()
                    bold = !bold
                    i += 2
                    continue
                }
            } else if (text[i] == '*') {
                if (italic || nextSingleStar(text, i + 1) >= 0) {
                    flush()
                    italic = !italic
                    i += 1
                    continue
                }
            }
            current.append(text[i])
            i++
        }
        flush()
        return mergeSameStyle(result)
    }

    /** The index of the next `*` from [from] that is not part of a `**`, or -1. */
    private fun nextSingleStar(text: String, from: Int): Int {
        var i = from
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                i += 2
                continue
            }
            if (text[i] == '*') return i
            i++
        }
        return -1
    }

    private fun mergeSameStyle(spans: List<GuideSpan>): List<GuideSpan> {
        val merged = mutableListOf<GuideSpan>()
        for (span in spans) {
            val last = merged.lastOrNull()
            if (last != null && last.bold == span.bold && last.italic == span.italic) {
                merged[merged.lastIndex] = last.copy(text = last.text + span.text)
            } else {
                merged += span
            }
        }
        return merged
    }
}
