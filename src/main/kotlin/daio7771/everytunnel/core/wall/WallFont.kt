package daio7771.everytunnel.core.wall

/**
 * The painted font's characters (`tools/gui/wall_font.py` draws it): each letter has [VARIANTS] drawings, in a
 * private range of Unicode, so that the same letter is never quite the same twice. [paint] turns a text into
 * those, always the same way for the same [seed]. Plain Kotlin, unit tested.
 */
object WallFont {

    /** In the order of the font's picture. Keep with the tool's ALPHABET. */
    const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'.,?!-"
    const val VARIANTS = 3
    private const val FIRST = 0xE000

    fun paint(text: String, seed: Int): String = buildString {
        text.forEachIndexed { i, char ->
            val letter = ALPHABET.indexOf(char.uppercaseChar())
            if (letter < 0) {
                append(' ')
            } else {
                val variant = Math.floorMod(seed * 31 + i * 17 + letter * 7, VARIANTS)
                append((FIRST + letter * VARIANTS + variant).toChar())
            }
        }
    }
}
