package daio7771.everytunnel.common

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The parts of the mod stay apart: `daio7771.everytunnel.common` (the engine and what every chapter shares), `chapter1` (the base game's
 * levels, one folder each) and `chapter2`. `common` never names a chapter's code, so a new chapter or level never has to touch it; a
 * chapter reaches into `common` only through its extension points (`PlayerRules`, `ChapterRequest`, `NewGame.addResetStep`,
 * `DeathHooks.listeners`, `LootBlock.takenListeners`, `NoteHooks`, `CutsceneView.Kind`...) and its shared pieces. A chapter does not
 * name another chapter either. Checked on the sources, by their imports and qualified names.
 */
class ChapterBoundaryTest {

    private val roots = listOf("src/main/kotlin", "src/client/kotlin", "src/main/java", "src/client/java").map(::File).filter { it.isDirectory }

    private val chapterPackage = Regex("""daio7771\.everytunnel\.(chapter\d+)\b""")

    /**
     * The files that may name a chapter although they are not in it (path relative to the repository, with the reason). Empty: every
     * link from `common` to a chapter was cut with an extension point.
     */
    private val allowedLinks = emptyMap<String, String>()

    /**
     * Code that was Chapter 1's and stays in `common`, by file name (without `.kt`), and why: it is used by Chapter 2, by the engine or
     * by an extension point that cannot name a chapter. Anything else named like a Chapter 1 level's code belongs under `chapter1`.
     */
    private val sharedByName = mapOf(
        "LabItems" to "the wrench item, registered with the shared items (ModItems) and given by loot",
        "DirectorBlocks" to "the paired doors, which Chapter 2's blocks use",
        "DirectorLines" to "the director's lines of the cinematic engine, which Chapter 2's scenes use",
        "DirectorScene" to "the director's scene timeline of the cinematic engine, which Chapter 2's scenes use",
        "ChemicalBlocks" to "the mixer and flask blocks, which Chapter 2's laboratory uses",
        "CardReaderBlock" to "the card reader, which Chapter 2's doors use",
        "CollapseHooks" to "the collapse, which Chapter 2's flood uses",
        "CollapseSchedule" to "the collapse's timing, which Chapter 2's flood uses",
        "CollapseScheduleTest" to "the collapse's timing, which Chapter 2's flood uses",
        "SafeLevelHooks" to "levels without risk (`safe`): a level file's flag, any chapter",
        "HunterBrain" to "the Hunters are spawned by DistortionHooks, which Chapter 2 uses",
        "HunterBrainTest" to "the Hunters are spawned by DistortionHooks, which Chapter 2 uses",
        "HunterEntity" to "the Hunters are spawned by DistortionHooks, which Chapter 2 uses",
        "HunterRenderer" to "the Hunters are spawned by DistortionHooks, which Chapter 2 uses",
        "LockerBlock" to "hiding in a locker is a shared mechanic (HidingHooks)",
        "LockerSeatEntity" to "hiding in a locker is a shared mechanic (HidingHooks)",
        "LockerView" to "hiding in a locker is a shared mechanic (HidingHooks)",
        "Detector" to "the detector's HUD layer must be drawn in order with the shared HUD",
        "DetectorTest" to "the detector's HUD layer must be drawn in order with the shared HUD",
        "DetectorEffect" to "the detector's HUD layer must be drawn in order with the shared HUD",
        "DetectorHud" to "the detector's HUD layer must be drawn in order with the shared HUD",
    )

    /** What names Chapter 1's levels' code (a file in `common` called so is either in [sharedByName] or has drifted back). */
    private val chapter1Names = Regex(
        """^(Lab[A-Z]|Lounge|Dart|Safe[A-Z]|Suite|Film|Breaker|Observer|PaleMan|Book[A-Z]|Box[A-Z]|Valve|Drain|DeepWater|FalseExit|Terminal|Subject|CloudGame|DeadGuard|Briefing|Keycard|Carry|Ending|FinalCredits|Congratulations|Director|Hunter|Chemical|Locker|Detector|Collapse|CardReader)""",
    )

    /** The part a source file belongs to (`chapter1`, `chapter2`), or null for `common`. */
    private fun chapterOf(file: File): String? =
        Regex("""daio7771[/\\]everytunnel[/\\](chapter\d+)[/\\]""").find(file.path)?.groupValues?.get(1)

    private fun sources(extension: Set<String>) =
        roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension in extension }.toList() }

    @Test
    fun `no source file names a chapter other than its own`() {
        val files = sources(setOf("kt", "java"))
        assertTrue(files.size > 100, "the sources were found")
        val crossings = files.filter { it.invariantSeparatorsPath !in allowedLinks }.flatMap { file ->
            val own = chapterOf(file)
            file.readLines().withIndex()
                .filter { (_, line) -> chapterPackage.findAll(line).any { it.groupValues[1] != own } }
                .map { (i, line) -> "${file.path}:${i + 1}: ${line.trim()}" }
        }
        assertEquals(emptyList(), crossings, "`common` (or a chapter) names another chapter's code")
    }

    @Test
    fun `chapter 2's code is all in its own package`() {
        val strays = sources(setOf("kt")).filter { chapterOf(it) != "chapter2" && Regex("""^(Ch2|Prologue|FactoryNpc|Scientist)""").containsMatchIn(it.name) }
        assertEquals(emptyList(), strays.map { it.path })
    }

    @Test
    fun `chapter 1's code does not drift back into common`() {
        val tests = File("src/test/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val drifted = (sources(setOf("kt")) + tests).filter { chapterOf(it) == null && !it.invariantSeparatorsPath.contains("/chapter") }
            .map { it.nameWithoutExtension }
            .filter { chapter1Names.containsMatchIn(it) && it !in sharedByName }
        assertEquals(emptyList(), drifted, "files named like a Chapter 1 level's code, in common without a reason in `sharedByName`")
    }
}
