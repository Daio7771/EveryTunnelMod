package daio7771.everytunnel.core.puzzle

/**
 * The text of a note read in game (`client.screen.NoteScreen`), in plain Kotlin (unit tested).
 * The first line is the note's title; blank lines separate paragraphs. A placeholder is filled
 * from [fill]'s values: `{A}` gives the value as digits, `{A:word}` as a word ("seven"); one
 * without a value shows `?`.
 */
object NoteText {

    private val PLACEHOLDER = Regex("""\{([A-Za-z0-9_]+)(?::(word))?\}""")

    fun fill(text: String, values: Map<String, Int>, word: (Int) -> String): String =
        PLACEHOLDER.replace(text) { match ->
            val value = values[match.groupValues[1]] ?: return@replace "?"
            if (match.groupValues[2] == "word") word(value) else value.toString()
        }

    /** (title, paragraphs): the first non-blank line, then the rest split at blank lines. */
    fun split(text: String): Pair<String, List<String>> {
        val lines = text.replace("\r", "").lines().dropWhile { it.isBlank() }
        val title = lines.firstOrNull()?.trim() ?: ""
        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        for (line in lines.drop(1)) {
            if (line.isBlank()) {
                if (current.isNotEmpty()) paragraphs += current.toString()
                current.clear()
            } else {
                if (current.isNotEmpty()) current.append('\n')
                current.append(line.trim())
            }
        }
        if (current.isNotEmpty()) paragraphs += current.toString()
        return title to paragraphs
    }
}
