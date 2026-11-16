package daio7771.everytunnel.chapter2.level1.core

import com.google.gson.JsonObject
import daio7771.everytunnel.util.JsonFields

/** A sound file on a NonameOS computer: the name it shows (the same in every language) and the sound event it plays from the computer. */
data class Ch2L1Recording(val name: String, val sound: String)

/**
 * A NonameOS computer of Act 3 ([Ch2L1Act3]): its block's anchor and the files it holds, each a document's id (`every_tunnel.ch2.l1.<id>`),
 * and its recordings, which only play: nothing counts them, and nothing waits on them. A [cipher] one (the basement's) shows its window's
 * own words (title, header, labels, buttons) as scrambled symbols; its files' names and texts are as their lang keys write them.
 */
data class Ch2L1Computer(
    val id: String,
    val anchor: String,
    val files: List<String>,
    val recordings: List<Ch2L1Recording> = emptyList(),
    val cipher: Boolean = false,
)

/**
 * The overtime, after Rowan's office (`act3.json`'s `overtime`): the sealed door opens at its reader ([reader]) with the unpaid overtime
 * pass, and slams shut once the player is [slamAfter] blocks past it in [zone] (the landing behind it), which starts the basement's task.
 * When the last dirt there is swept the lights of [basementZone] go out; [darkTicks] later the screen fades to black over [fadeTicks],
 * the lang key [text] stays on it for [textTicks], and the game goes back to the menu.
 */
data class Ch2L1Overtime(
    val reader: String = "sealed_reader",
    val zone: String = "sealed_corridor",
    val slamAfter: Double = 2.5,
    val basementZone: String = "basement",
    val darkTicks: Int = 60,
    val fadeTicks: Int = 50,
    val textTicks: Int = 120,
    val text: String = "every_tunnel.ch2.l1.ending.text",
) {
    companion object {
        /** The player's counter set when the level is finished (`ch2.l1.completed` in the plan): 1. */
        const val COMPLETED = "completed"
    }
}

/**
 * Act 3 of Chapter 2's Level 1, its first part: exploring the auxiliary lab, the authorized corridor and Rowan's office, from `act3.json`.
 * Plain Kotlin.
 *
 * The coworker's badge opens the auxiliary lab at the badge reader by its door ([readerAnchor], [labDoor]); its computer holds the
 * chamber's log and the containment authorization, which carries Rowan's [code]. The code opens the office at the keypad by its door
 * ([keypadAnchor], [officeDoor]); Rowan's computer holds the medical file and contract 14, and closing the second of its files
 * ([triggerComputer]) is when the office's cinematic will begin (phase 8). Beyond: a door onto nothing at the end of the authorized
 * corridor ([restrictedDoors]: "Restricted access"), the sealed zone's door ([sealedDoor]: shut until Rowan's card), the portrait over his
 * desk ([portraitAnchor]: its plaque read in the action bar) and the contained Pale man of chamber 02 ([chamberAnchor]).
 *
 * What the player has done is kept in their counters ([LAB_OPEN], [READ], [OFFICE_OPEN], [IN_OFFICE], [TRIGGERED], all starting with
 * [PREFIX]); [objective] and [guide] read them. The guide arrow shows only once the player has been on the same target for [stuckTicks].
 */
data class Ch2L1Act3(
    val readerAnchor: String = "aux_lab_reader",
    val labDoor: String = "aux_lab_door",
    val keypadAnchor: String = "office_keypad",
    val officeDoor: String = "door_anchor",
    val code: String = "4719",
    val computers: List<Ch2L1Computer> = DEFAULT_COMPUTERS,
    /** Reading this file (closing it) moves the objective on to the way into the office. */
    val codeFile: String = "authorization",
    /** Entering this zone moves the objective on to searching the office. */
    val officeZone: String = "office",
    val triggerComputer: String = "rowan",
    val restrictedDoors: List<String> = listOf("restricted_door_east"),
    val sealedDoor: String = "sealed_door",
    val portraitAnchor: String = "painting",
    val chamberAnchor: String = "chamber_02",
    val stuckTicks: Int = 1200,
    val overtime: Ch2L1Overtime = Ch2L1Overtime(),
) {
    /** Every file of every computer, in order: a file's place here is its bit in [READ]. */
    val allFiles: List<String> get() = computers.flatMap { it.files }

    fun computer(id: String): Ch2L1Computer? = computers.firstOrNull { it.id == id }

    /** The bit of [file] in [READ]; -1 if no computer has it. */
    fun fileBit(file: String): Int = allFiles.indexOf(file)

    fun isRead(file: String, counter: (String) -> Int): Boolean {
        val bit = fileBit(file)
        return bit >= 0 && Ch2L1Bits.has(counter(READ), bit)
    }

    /** Every file of [computer] has been read. */
    fun allRead(computer: Ch2L1Computer, counter: (String) -> Int): Boolean = computer.files.all { isRead(it, counter) }

    /** The objective's lang key suffix (`objective.<it>`) in [Ch2L1State.ACT3_EXPLORE]. */
    fun objective(counter: (String) -> Int): String = when {
        counter(IN_OFFICE) > 0 -> SEARCH_OFFICE
        isRead(codeFile, counter) -> EXPLORE_OFFICE
        else -> EXPLORE_LAB
    }

    /** The anchor the guide arrow points at in Act 3 (the reader, the lab's computer, the keypad, the office's door, Rowan's computer). */
    fun guide(counter: (String) -> Int): String? = when (objective(counter)) {
        EXPLORE_LAB -> if (counter(LAB_OPEN) == 0) readerAnchor else computers.firstOrNull { codeFile in it.files }?.anchor
        EXPLORE_OFFICE -> if (counter(OFFICE_OPEN) == 0) keypadAnchor else officeDoor
        else -> computer(triggerComputer)?.anchor
    }

    /** True if the code typed is Rowan's (digits only are compared). */
    fun accepts(typed: String): Boolean = typed.filter(Char::isDigit) == code

    companion object {
        const val PREFIX = "a3_"
        const val LAB_OPEN = PREFIX + "lab_open"
        const val READ = PREFIX + "read"
        const val OFFICE_OPEN = PREFIX + "office_open"
        const val IN_OFFICE = PREFIX + "in_office"
        const val TRIGGERED = PREFIX + "triggered"
        /** The overtime: the sealed door opened at its reader (it slams shut behind the player, [Ch2L1Overtime]). */
        const val SEALED_OPEN = PREFIX + "sealed_open"

        const val EXPLORE_LAB = "explore_lab"
        const val EXPLORE_OFFICE = "explore_office"
        const val SEARCH_OFFICE = "search_office"

        /** The longest code the keypad takes. */
        const val MAX_CODE = 8

        val DEFAULT_COMPUTERS = listOf(
            Ch2L1Computer("aux_lab", "aux_lab_computer", listOf("chamber_log", "authorization")),
            Ch2L1Computer("rowan", "rowan_computer", listOf("medical_file", "contract_14")),
            Ch2L1Computer(
                "basement", "basement_computer", listOf("basement_note"),
                listOf(Ch2L1Recording("?????????.mp3", "voice.basement_recording")), cipher = true,
            ),
        )
        val DEFAULT = Ch2L1Act3()
    }
}

/**
 * Reads `act3.json` (every key optional):
 *
 * ```json
 * { "reader": "aux_lab_reader", "lab_door": "aux_lab_door", "keypad": "office_keypad", "office_door": "door_anchor", "code": "4719",
 *   "computers": [ { "id": "aux_lab", "anchor": "aux_lab_computer", "files": ["chamber_log", "authorization"] },
 *                  { "id": "rowan", "anchor": "rowan_computer", "files": ["medical_file", "contract_14"] },
 *                  { "id": "basement", "anchor": "basement_computer", "files": ["basement_note"], "cipher": true,
 *                    "recordings": [ { "name": "?????????.mp3", "sound": "voice.basement_recording" } ] } ],
 *   "code_file": "authorization", "office_zone": "office", "trigger_computer": "rowan",
 *   "restricted_doors": ["restricted_door_east"], "sealed_door": "sealed_door", "portrait": "painting", "chamber": "chamber_02",
 *   "guide_after_seconds": 60,
 *   "overtime": { "reader": "sealed_reader", "zone": "sealed_corridor", "slam_after": 2.5, "basement_zone": "basement",
 *                 "dark_seconds": 3.0, "fade_seconds": 2.5, "text_seconds": 6.0, "text": "every_tunnel.ch2.l1.ending.text" } }
 * ```
 */
object Ch2L1Act3Parser {

    data class Result(val act3: Ch2L1Act3, val problems: List<String>)

    private fun parseOvertime(o: JsonFields, d: Ch2L1Overtime): Ch2L1Overtime {
        fun ticks(key: String, default: Int) = (o.double(key, default / 20.0, min = 0.0, max = 120.0) * 20).toInt()
        val overtime = Ch2L1Overtime(
            reader = o.string("reader", d.reader),
            zone = o.string("zone", d.zone),
            slamAfter = o.double("slam_after", d.slamAfter, min = 0.5, max = 20.0),
            basementZone = o.string("basement_zone", d.basementZone),
            darkTicks = ticks("dark_seconds", d.darkTicks),
            fadeTicks = ticks("fade_seconds", d.fadeTicks).coerceAtLeast(1),
            textTicks = ticks("text_seconds", d.textTicks),
            text = o.string("text", d.text),
        )
        o.reportUnknownKeys()
        return overtime
    }

    fun parse(root: JsonObject): Result {
        val f = JsonFields(root, "")
        val d = Ch2L1Act3.DEFAULT
        val computers = if (!f.has("computers")) d.computers else (f.array("computers")?.toList() ?: emptyList()).mapIndexedNotNull { i, e ->
            if (!e.isJsonObject) {
                f.problems += "computers[$i]: expected an object"
                return@mapIndexedNotNull null
            }
            val c = JsonFields(e.asJsonObject, "computers[$i]", f.problems)
            val id = c.requiredString("id")
            val anchor = c.requiredString("anchor")
            val files = c.stringList("files")
            val recordings = (c.array("recordings")?.toList() ?: emptyList()).mapIndexedNotNull { j, r ->
                if (!r.isJsonObject) {
                    f.problems += "computers[$i].recordings[$j]: expected an object"
                    return@mapIndexedNotNull null
                }
                val rf = JsonFields(r.asJsonObject, "computers[$i].recordings[$j]", f.problems)
                val name = rf.requiredString("name")
                val sound = rf.requiredString("sound")
                rf.reportUnknownKeys()
                if (name == null || sound == null) null else Ch2L1Recording(name, sound)
            }
            val cipher = c.boolean("cipher", false)
            c.reportUnknownKeys()
            if (id == null || anchor == null) null else Ch2L1Computer(id, anchor, files, recordings, cipher)
        }
        val act3 = Ch2L1Act3(
            readerAnchor = f.string("reader", d.readerAnchor),
            labDoor = f.string("lab_door", d.labDoor),
            keypadAnchor = f.string("keypad", d.keypadAnchor),
            officeDoor = f.string("office_door", d.officeDoor),
            code = f.string("code", d.code),
            computers = computers,
            codeFile = f.string("code_file", d.codeFile),
            officeZone = f.string("office_zone", d.officeZone),
            triggerComputer = f.string("trigger_computer", d.triggerComputer),
            restrictedDoors = if (f.has("restricted_doors")) f.stringList("restricted_doors") else d.restrictedDoors,
            sealedDoor = f.string("sealed_door", d.sealedDoor),
            portraitAnchor = f.string("portrait", d.portraitAnchor),
            chamberAnchor = f.string("chamber", d.chamberAnchor),
            stuckTicks = (f.double("guide_after_seconds", d.stuckTicks / 20.0, min = 0.0, max = 3600.0) * 20).toInt(),
            overtime = parseOvertime(f.child("overtime"), d.overtime),
        )
        f.reportUnknownKeys()
        val problems = f.problems.toMutableList()
        if (act3.code.isEmpty() || act3.code.length > Ch2L1Act3.MAX_CODE || !act3.code.all(Char::isDigit)) {
            problems += "code: \"${act3.code}\" must be 1 to ${Ch2L1Act3.MAX_CODE} digits"
        }
        if (act3.allFiles.size > 30) problems += "computers: ${act3.allFiles.size} files, at most 30"
        if (act3.allFiles.toSet().size != act3.allFiles.size) problems += "computers: a file is on two computers"
        if (act3.fileBit(act3.codeFile) < 0) problems += "code_file: no computer has \"${act3.codeFile}\""
        if (act3.computer(act3.triggerComputer) == null) problems += "trigger_computer: there is no computer \"${act3.triggerComputer}\""
        return Result(act3, problems)
    }
}
