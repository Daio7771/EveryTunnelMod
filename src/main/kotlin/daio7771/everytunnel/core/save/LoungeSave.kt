package daio7771.everytunnel.core.save

import com.google.gson.Gson
import com.google.gson.JsonParseException

/**
 * Level 4 as the player left it, for CONTINUE (`platform.ContinueGame`): each game's answers, how far each
 * puzzle got, the safes, the power and the loot taken. The level itself is rebuilt from nothing, so this is
 * all that remains of it. Kept as text in `core.progress.PlayerProgress`. Plain Kotlin, unit tested.
 */
data class LoungeSave(
    var records: List<Int> = emptyList(),
    var recordsDone: Int = 0,
    var recordsFailures: Int = 0,
    var reel: List<Int> = emptyList(),
    var reelDone: Int = 0,
    var reelFailures: Int = 0,
    var filmSeen: Boolean = false,
    var darts: List<Int> = emptyList(),
    var dartsDone: Int = 0,
    var dartsFailures: Int = 0,
    var breakers: List<Boolean> = emptyList(),
    var levers: List<Boolean> = emptyList(),
    /** What the counter shows, or -1 when blank. */
    var counter: Int = -1,
    var powered: Boolean = false,
    var readerUnlocked: Boolean = false,
    var safes: List<Safe> = emptyList(),
    /** The loot taken, as `level/point`. */
    var lootTaken: List<String> = emptyList(),
) {

    data class Safe(val x: Int, val y: Int, val z: Int, val combination: List<Int>, val failures: Int, val open: Boolean)

    fun toText(): String = GSON.toJson(this)

    companion object {
        private val GSON = Gson()

        /** The text back into a save, or null if it cannot be read. */
        fun fromText(text: String?): LoungeSave? {
            if (text.isNullOrEmpty()) return null
            return try {
                GSON.fromJson(text, LoungeSave::class.java)
            } catch (e: JsonParseException) {
                null
            }
        }
    }
}
