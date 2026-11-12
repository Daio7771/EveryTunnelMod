package daio7771.everytunnel.core.cutscene

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** One cue of a voice line's subtitles: shown from [atMs] after the line begins, its text under the lang key [key]. */
data class VoiceCue(val atMs: Int, val key: String)

/** One voice line: its sound `voice.director.<id>`, the cinematic's [part] it belongs to (0: none), its length, its cues. */
data class VoiceLine(val id: String, val part: Int, val ms: Int, val cues: List<VoiceCue>)

/**
 * The director's voice lines ([lines], his speech in order) and the phrases he repeats afterwards ([loop]), as
 * `tools/sounds/voices.py` writes them into `assets/every_tunnel/cutscene/director.json`. Plain Kotlin.
 */
data class DirectorLines(val lines: List<VoiceLine>, val loop: List<VoiceLine>) {

    companion object {
        const val RESOURCE = "assets/every_tunnel/cutscene/director.json"

        fun parse(json: String): DirectorLines {
            val root = JsonParser.parseString(json).asJsonObject
            fun list(name: String) = root.getAsJsonArray(name).map { line(it.asJsonObject) }
            return DirectorLines(list("lines"), list("loop"))
        }

        /** The file from the classpath (the mod's own resources). */
        fun load(): DirectorLines {
            val stream = DirectorLines::class.java.classLoader.getResourceAsStream(RESOURCE)
                ?: error("$RESOURCE is missing: run tools/sounds/voices.py")
            return stream.use { parse(it.reader().readText()) }
        }

        private fun line(o: JsonObject) = VoiceLine(
            o["id"].asString,
            o["part"].asInt,
            o["ms"].asInt,
            o.getAsJsonArray("cues").map { VoiceCue(it.asJsonObject["at"].asInt, it.asJsonObject["text"].asString) },
        )
    }
}
