package daio7771.everytunnel.core.cutscene

/**
 * The final credits' timeline (`client.screen.FinalCreditsScreen`), in seconds from the start of the credits' song
 * ([LENGTH]), which comes in [SONG_AT] seconds after the black has turned into the credits: an intro ("THE END",
 * a little before the song, then "Every Tunnel" under it), four screens of [SCREEN] each (a
 * drawing on one side, a title typed out and its lines one by one on the other, the credits under them), the
 * tools in groups of [GROUP] each, "Thank you for playing", "Level 6 coming soon", and black. Each piece says how
 * opaque it is at a moment ([fade]). Plain Kotlin, unit tested.
 */
object FinalCredits {

    const val LENGTH = 94.0

    /** The credits open on black and silence; the song comes in this many seconds later. */
    const val SONG_AT = 2.0

    /** The intro: "THE END" from [END_IN] (before the song), "Every Tunnel" from [SUB_IN], both gone by [INTRO_END]. */
    const val END_IN = -1.5
    const val SUB_IN = 3.0
    const val INTRO_OUT = 5.0
    const val INTRO_END = 6.0

    const val SCREENS = 4
    const val SCREEN = 18.0

    /** Within a screen: the drawing fades in over [IMAGE_IN], the title types from [TITLE_AT], the lines come
     * between [LINES_FROM] and [LINES_TO], the credits at [CREDITS_AT], all fade out from [SCREEN_OUT]. */
    const val IMAGE_IN = 1.5
    const val TITLE_AT = 1.5
    const val LINES_FROM = 2.5
    const val LINES_TO = 11.0
    const val CREDITS_AT = 12.0
    const val SCREEN_OUT = 17.0

    /** The title types a letter this often. */
    const val TYPE_EVERY = 0.045

    const val TOOLS_AT = INTRO_END + SCREENS * SCREEN
    const val GROUP = 2.0
    const val GROUPS = 4
    const val THANKS_AT = TOOLS_AT + GROUPS * GROUP
    const val NEXT_AT = THANKS_AT + 2.0
    const val BLACK_AT = NEXT_AT + 4.0

    /** Short fades in and out of the pieces. */
    const val FADE = 0.5

    /** How opaque something shown from [from] to [to] is at [t]: fading in over [fadeIn], out over [fadeOut]. */
    fun fade(t: Double, from: Double, to: Double, fadeIn: Double = FADE, fadeOut: Double = FADE): Double {
        if (t < from || t >= to) return 0.0
        val rise = if (fadeIn <= 0.0) 1.0 else ((t - from) / fadeIn).coerceAtMost(1.0)
        val fall = if (fadeOut <= 0.0) 1.0 else ((to - t) / fadeOut).coerceAtMost(1.0)
        return minOf(rise, fall)
    }

    fun screenStart(i: Int): Double = INTRO_END + i * SCREEN

    /** The screen showing at [t] (0..3), or -1. */
    fun screenAt(t: Double): Int = if (t < INTRO_END || t >= TOOLS_AT) -1 else ((t - INTRO_END) / SCREEN).toInt()

    /** When line [k] of [n] shows, seconds into its screen. */
    fun lineAt(k: Int, n: Int): Double = LINES_FROM + k * (LINES_TO - LINES_FROM) / n

    /** How many letters of a title of [length] are typed [s] seconds into its screen. */
    fun typed(s: Double, length: Int): Int = ((s - TITLE_AT) / TYPE_EVERY).toInt().coerceIn(0, length)

    /** How opaque a piece of a screen shown from [from] (seconds into it) is, [s] seconds into it. */
    fun inScreen(s: Double, from: Double): Double = fade(s, from, SCREEN, FADE, SCREEN - SCREEN_OUT)

    /** The tools' group showing at [t] (0..3), or -1. */
    fun groupAt(t: Double): Int = if (t < TOOLS_AT || t >= THANKS_AT) -1 else ((t - TOOLS_AT) / GROUP).toInt()
}
