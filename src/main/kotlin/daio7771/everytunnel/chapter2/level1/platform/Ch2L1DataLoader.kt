package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Data
import daio7771.everytunnel.chapter2.level1.core.Ch2L1DataParser
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Files
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Act2
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Act2Parser
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Act3
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Act3Parser
import daio7771.everytunnel.chapter2.level1.core.Ch2L1OfficeParser
import daio7771.everytunnel.chapter2.level1.core.Ch2L1OfficeSpec
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Patrols
import daio7771.everytunnel.chapter2.level1.core.Ch2L1PatrolsParser
import daio7771.everytunnel.chapter2.level1.core.Ch2L1NpcData
import daio7771.everytunnel.chapter2.level1.core.Ch2L1NpcsParser
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Speakers
import daio7771.everytunnel.chapter2.level1.core.Ch2L1SpeakersParser
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Tasks
import daio7771.everytunnel.chapter2.level1.core.Ch2L1TasksParser
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Wiring
import com.google.gson.JsonObject
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads Chapter 2's Level 1 data ([Ch2L1Data]) from disk and keeps the current copy, which every system of the level
 * reads through [data]. Each file comes from the config folder (`config/every_tunnel/ch2/l1/<name>.json`) if a usable
 * one is there, else from the jar's defaults (`data/every_tunnel/ch2/l1/`): editing a file in the config folder and
 * running `/et reload` changes the level without a rebuild or a restart. Read once at launch and on every reload;
 * what is wrong with a file is logged and answered to the command, and the level goes on with the copy that works.
 *
 * Not a datapack: it does not follow `/reload`, and it loads before any world does (the data is the level's own, not
 * the map's, though `anchors.json` is what the map generator will write).
 */
object Ch2L1DataLoader {

    /** The data as last read. Replaced as a whole, so a reader never sees half of a reload. */
    @Volatile
    var data: Ch2L1Data = Ch2L1Data.EMPTY
        private set

    /** The tasks' file ([Ch2L1Tasks]) as last read, parsed with [data]. */
    @Volatile
    var tasks: Ch2L1Tasks = Ch2L1Tasks.DEFAULT
        private set

    /** The loudspeakers' file ([Ch2L1Speakers]) as last read. */
    @Volatile
    var speakers: Ch2L1Speakers = Ch2L1SpeakersParser.parse(JsonObject()).speakers
        private set

    /** The factory's people ([Ch2L1NpcData], `npcs.json`) as last read. */
    @Volatile
    var npcs: Ch2L1NpcData = Ch2L1NpcData.EMPTY

    /** Act 2 (`cutscenes/meeting.json`: following the coworker and the meeting) as last read. */
    var act2: Ch2L1Act2 = Ch2L1Act2.DEFAULT
        private set

    /** Act 3 (`act3.json`: the auxiliary lab, the keypad, the computers, the office) as last read. */
    @Volatile
    var act3: Ch2L1Act3 = Ch2L1Act3.DEFAULT
        private set

    /** The office's cinematic (`cutscenes/office.json`) as last read. */
    @Volatile
    var office: Ch2L1OfficeSpec = Ch2L1OfficeSpec.DEFAULT
        private set

    /** Act 3's patrols (`patrols.json`: the guards' rounds and how they watch) as last read. */
    @Volatile
    var patrols: Ch2L1Patrols = Ch2L1Patrols.DEFAULT
        private set

    /** What a read found, for the log and for `/et reload`. */
    class Report(val data: Ch2L1Data, val problems: List<String>, val overrideFolder: Path) {
        /** The text of the report, one line each. */
        fun lines(): List<String> {
            val anchors = data.anchors
            val overridden = data.sources.filterValues { it == Ch2L1Data.Source.OVERRIDE }.keys
            return buildList {
                add(
                    "Chapter 2 Level 1 data loaded: ${anchors.points.size} anchors and ${anchors.zones.size} zones " +
                        "(${anchors.provisionalCount()} provisional).",
                )
                add(
                    if (overridden.isEmpty()) "No overrides: the jar's files are in use (put edited ones in $overrideFolder)."
                    else "Overrides in use: ${overridden.joinToString(", ")} (from $overrideFolder).",
                )
                addAll(problems)
            }
        }
    }

    /** Reads the files once, at launch. */
    fun register() {
        reload()
    }

    /** Reads every file again and makes the result current. */
    fun reload(): Report {
        val folder = FabricLoader.getInstance().configDir.resolve(Ch2L1Files.OVERRIDE_FOLDER)
        val readProblems = mutableListOf<String>()
        val overrides = linkedMapOf<String, String>()
        for (name in Ch2L1Files.ALL) {
            val file = folder.resolve("$name.json")
            if (!Files.isRegularFile(file)) continue
            try {
                overrides[name] = Files.readString(file)
            } catch (e: IOException) {
                readProblems += "$name.json (override): could not be read (${e.message}), trying the copy in the jar"
            }
        }
        val result = Ch2L1DataParser.parse(overrides, Ch2L1Files.readAllBundled())
        data = result.data
        val tasksResult = Ch2L1TasksParser.parse(result.data.document("tasks"))
        val speakersResult = Ch2L1SpeakersParser.parse(result.data.document("speakers"))
        val npcsResult = Ch2L1NpcsParser.parse(result.data.document("npcs"))
        val act2Result = Ch2L1Act2Parser.parse(result.data.document("cutscenes/meeting"))
        val act3Result = Ch2L1Act3Parser.parse(result.data.document("act3"))
        val patrolsResult = Ch2L1PatrolsParser.parse(result.data.document("patrols"))
        val officeResult = Ch2L1OfficeParser.parse(result.data.document("cutscenes/office"))
        tasks = tasksResult.tasks
        speakers = speakersResult.speakers
        npcs = npcsResult.data
        act2 = act2Result.act2
        act3 = act3Result.act3
        patrols = patrolsResult.patrols
        office = officeResult.spec
        val wiring = Ch2L1Wiring.problems(result.data.anchors, tasks, speakers, npcs, act2, act3, patrols, office)

        val report = Report(
            result.data,
            readProblems + result.problems + tasksResult.problems.map { "tasks.json: $it" } +
                speakersResult.problems.map { "speakers.json: $it" } + npcsResult.problems.map { "npcs.json: $it" } +
                act2Result.problems.map { "cutscenes/meeting.json: $it" } + act3Result.problems.map { "act3.json: $it" } +
                patrolsResult.problems.map { "patrols.json: $it" } + officeResult.problems.map { "cutscenes/office.json: $it" } + wiring,
            folder,
        )
        val log = EveryTunnel.LOGGER
        log.info(report.lines().first())
        report.problems.forEach { log.warn("Chapter 2 Level 1 data: {}", it) }
        return report
    }
}
