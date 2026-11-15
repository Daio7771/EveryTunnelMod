package daio7771.everytunnel.core.ch2l1

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * One cue of a voice line's subtitles: from [atMs] after the line begins it shows the sentences of the line's text
 * from [firstSentence] up to, not including, [endSentence] (null: to the end of the text, whatever its language).
 */
data class Ch2L1Cue(val atMs: Int, val firstSentence: Int, val endSentence: Int?)

/**
 * One voice line of Chapter 2's Level 1: its sound is `voice.<id>`; its text is the lang key [textKey] (the author's
 * English and Spanish, one key for the whole line); [speakerKey] is the lang key of the name that shows in grey before
 * it; [ms] is its length and [cues] are its subtitles, as `tools/sounds/ch2_l1_voices.py` writes them. Plain Kotlin.
 *
 * The cues are ranges of sentences, not texts, so that their timing is the same in every language and a line's words
 * are edited in the lang files alone: the client translates [textKey], splits it into sentences ([Ch2L1Sentences]) and
 * shows the range of the cue that is current ([textAt]).
 */
data class Ch2L1VoiceLine(val id: String, val textKey: String, val speakerKey: String, val ms: Int, val cues: List<Ch2L1Cue>) {

    /**
     * What to show [elapsedMs] after the line began, given its [translated] text: the sentences of the last cue that
     * has started; null before the first cue, or if the text has nothing in that range.
     */
    fun textAt(elapsedMs: Long, translated: String): String? {
        val cue = cues.lastOrNull { it.atMs <= elapsedMs } ?: return null
        val sentences = Ch2L1Sentences.split(translated)
        val first = cue.firstSentence.coerceAtMost(sentences.size)
        val end = (cue.endSentence ?: sentences.size).coerceIn(first, sentences.size)
        return sentences.subList(first, end).joinToString(" ").ifEmpty { null }
    }

    /** True once the voice has ended and the last cue has stayed [holdMs] longer. */
    fun isOver(elapsedMs: Long, holdMs: Long): Boolean = elapsedMs > ms + holdMs
}

/**
 * Where a text's sentences end: at a `.`, `?` or `!` followed by a space. The same rule as `tools/sounds/ch2_l1_voices.py`
 * (`voices.sentences`), which cuts the cues in the first place; the English and the Spanish text of a line must have the
 * same number of them (`Ch2L1VoicesTest`).
 */
object Ch2L1Sentences {
    private val END = Regex("(?<=[.?!])\\s+")

    fun split(text: String): List<String> = END.split(text.trim()).filter { it.isNotEmpty() }
}

/**
 * Every voice line of the level ([lines]), from `assets/every_tunnel/cutscene/ch2_l1_voices.json`: the coworker's and
 * the union's (`follow_*`, `meeting_*`) and Rowan's (`rowan_loyal_*`, from the loudspeakers; `rowan_office_*`). The
 * client shows the subtitles of whichever of them plays, however it was started (`client.hud.Ch2L1Subtitles`).
 */
data class Ch2L1Voices(val lines: List<Ch2L1VoiceLine>) {

    /** The line called [id], or null. */
    fun line(id: String): Ch2L1VoiceLine? = lines.firstOrNull { it.id == id }

    /** The line whose sound event is [soundPath] (`voice.follow_01`), or null if it is not one of the level's. */
    fun ofSound(soundPath: String): Ch2L1VoiceLine? =
        if (soundPath.startsWith(SOUND_PREFIX)) line(soundPath.removePrefix(SOUND_PREFIX)) else null

    companion object {
        const val RESOURCE = "assets/every_tunnel/cutscene/ch2_l1_voices.json"

        /** A line's sound event is this and its id. */
        const val SOUND_PREFIX = "voice."

        fun parse(json: String): Ch2L1Voices =
            Ch2L1Voices(JsonParser.parseString(json).asJsonObject.getAsJsonArray("lines").map { line(it.asJsonObject) })

        /** The file from the classpath (the mod's own resources). */
        fun load(): Ch2L1Voices {
            val stream = Ch2L1Voices::class.java.classLoader.getResourceAsStream(RESOURCE)
                ?: error("$RESOURCE is missing: run tools/sounds/ch2_l1_voices.py")
            return stream.use { parse(it.reader().readText()) }
        }

        private fun line(o: JsonObject) = Ch2L1VoiceLine(
            o["id"].asString,
            o["text"].asString,
            o["speaker"].asString,
            o["ms"].asInt,
            o.getAsJsonArray("cues").map {
                val cue = it.asJsonObject
                Ch2L1Cue(cue["at"].asInt, cue["from"].asInt, cue["to"]?.asInt)
            },
        )
    }
}

/**
 * The look of the factory's people ([daio7771.everytunnel.platform.FactoryNpcEntity]): one skin each, a PNG in
 * `textures/entity/npc/` named by its id. `rowan` is the author's own; the rest are made by `tools/entity/workers.py`:
 * the three who speak in the level (`coworker`, `worker_a`, `worker_b`) and three faces for the rest (`generic_*`).
 */
object Ch2L1Skins {
    const val ROWAN = "rowan"
    const val COWORKER = "coworker"
    const val WORKER_A = "worker_a"
    const val WORKER_B = "worker_b"

    val ALL: List<String> = listOf(ROWAN, COWORKER, WORKER_A, WORKER_B, "generic_1", "generic_2", "generic_3")

    /** What an unknown id draws as. */
    const val DEFAULT = "generic_1"

    fun orDefault(id: String): String = if (id in ALL) id else DEFAULT

    /** Where the skin's PNG is, under the mod's assets. */
    fun texturePath(id: String): String = "textures/entity/npc/${orDefault(id)}.png"
}
