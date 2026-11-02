package daio7771.everytunnel.core.film

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import daio7771.everytunnel.util.JsonFields

/**
 * The film of Level 4's Cinema (`assets/every_tunnel/film/film.json`, written by `tools/film/film.py`):
 * how many frames, how fast. Plain Kotlin, unit tested. The server needs how long it runs, the client
 * which frame to show.
 */
data class FilmInfo(val fps: Int, val frames: Int) {

    /** Its length in game ticks (20 a second). */
    val ticks: Int
        get() = if (fps <= 0) 0 else frames * TICKS_PER_SECOND / fps

    /** The frame (1 to [frames]) at [ticks] since it started, or null once it has ended. */
    fun frameAt(ticks: Float): Int? {
        if (ticks < 0f || fps <= 0) return null
        val frame = (ticks * fps / TICKS_PER_SECOND).toInt() + 1
        return frame.takeIf { it <= frames }
    }

    companion object {
        private const val TICKS_PER_SECOND = 20

        /** The file's contents into a film, or null if it is unusable. */
        fun parse(text: String): FilmInfo? {
            val root = try {
                JsonParser.parseString(text)
            } catch (e: JsonParseException) {
                return null
            }
            if (!root.isJsonObject) return null
            val fields = JsonFields(root.asJsonObject, "")
            val fps = fields.int("fps", 0, min = 0)
            val frames = fields.int("frames", 0, min = 0)
            return if (fps > 0 && frames > 0) FilmInfo(fps, frames) else null
        }
    }
}
