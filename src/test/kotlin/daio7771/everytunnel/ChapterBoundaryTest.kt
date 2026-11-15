package daio7771.everytunnel

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The chapters stay apart: the base game (Chapter 1 and what every chapter shares, everything outside `daio7771.everytunnel.chapterN`)
 * never names a later chapter's code, so a new chapter or level never has to touch it; a later chapter reaches into the base game only
 * through its extension points (`PlayerRules`, `ChapterRequest`, `ProgressService.onReset`, `CutsceneView.Kind`) and its shared pieces.
 * A later chapter does not name another later chapter either. Checked on the sources, by their imports and qualified names.
 */
class ChapterBoundaryTest {

    private val roots = listOf("src/main/kotlin", "src/client/kotlin", "src/main/java").map(::File).filter { it.isDirectory }

    private val chapterPackage = Regex("""daio7771\.everytunnel\.(chapter\d+)\b""")

    /** The chapter a source file belongs to (`chapter2`), or null for the base game. */
    private fun chapterOf(file: File): String? =
        Regex("""daio7771[/\\]everytunnel[/\\](chapter\d+)[/\\]""").find(file.path)?.groupValues?.get(1)

    @Test
    fun `no source file names a chapter other than its own`() {
        val files = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList() }
        assertTrue(files.size > 100, "the sources were found")
        val crossings = files.flatMap { file ->
            val own = chapterOf(file)
            file.readLines().withIndex()
                .filter { (_, line) -> chapterPackage.findAll(line).any { it.groupValues[1] != own } }
                .map { (i, line) -> "${file.path}:${i + 1}: ${line.trim()}" }
        }
        assertEquals(emptyList(), crossings, "the base game (or a chapter) names another chapter's code")
    }

    @Test
    fun `chapter 2's code is all in its own package`() {
        val strays = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { chapterOf(it) == null && Regex("""^(Ch2|Prologue|FactoryNpc|Scientist)""").containsMatchIn(it.name) }
        assertEquals(emptyList(), strays.map { it.path })
    }
}
