package daio7771.everytunnel.core.cutscene

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * The scientist's voice lines in Chapter 2's prologue ([lines], in the order he says them), as
 * `tools/sounds/prologue_voices.py` writes them into `assets/every_tunnel/cutscene/prologue.json`: each line's
 * sound is `voice.<id>`, its length and its subtitle cues. Plain Kotlin.
 */
data class PrologueLines(val lines: List<VoiceLine>) {

    /** The line called [id]; fails naming it if the file has none. */
    fun line(id: String): VoiceLine = lines.firstOrNull { it.id == id } ?: error("the prologue's voice lines have no $id")

    companion object {
        const val RESOURCE = "assets/every_tunnel/cutscene/prologue.json"

        fun parse(json: String): PrologueLines =
            PrologueLines(JsonParser.parseString(json).asJsonObject.getAsJsonArray("lines").map { line(it.asJsonObject) })

        /** The file from the classpath (the mod's own resources). */
        fun load(): PrologueLines {
            val stream = PrologueLines::class.java.classLoader.getResourceAsStream(RESOURCE)
                ?: error("$RESOURCE is missing: run tools/sounds/prologue_voices.py")
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
