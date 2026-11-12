package daio7771.everytunnel.core

import com.google.gson.JsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The translations against English, which is the reference ([en_us.json]): a Spanish key that English
 * lacks is a typo, a missing one falls back to English, and the placeholders and line breaks must match
 * (the game fills `{sneak}` and `%s`, and a wall plate counts its lines).
 */
class LangFilesTest {

    private val assets = File("src/main/resources/assets/every_tunnel")

    private fun lang(name: String): Map<String, String> =
        JsonParser.parseString(File(assets, "lang/$name.json").readText()).asJsonObject.entrySet()
            .associate { it.key to it.value.asString }

    private fun placeholders(text: String): List<String> =
        Regex("""\{[a-z_]+}|%\d?\$?s""").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun `Spanish has no key English lacks and keeps its placeholders and lines`() {
        val english = lang("en_us")
        val spanish = lang("es_es")
        assertEquals(emptyList(), spanish.keys.filter { it !in english }, "keys only in es_es")
        for ((key, text) in spanish) {
            val original = english.getValue(key)
            assertEquals(placeholders(original), placeholders(text), "placeholders of $key")
            assertEquals(original.count { it == '\n' }, text.count { it == '\n' }, "line breaks of $key")
        }
    }

    @Test
    fun `only the painted wall messages stay in English`() {
        val untranslated = lang("en_us").keys - lang("es_es").keys
        assertTrue(untranslated.all { it.startsWith("wall.every_tunnel.") }, "untranslated: $untranslated")
    }

    @Test
    fun `the other Spanish locales are copies of es_es`() {
        val source = File(assets, "lang/es_es.json").readText()
        for (name in listOf("es_ar", "es_cl", "es_ec", "es_mx", "es_uy", "es_ve")) {
            assertEquals(source, File(assets, "lang/$name.json").readText(), "$name differs: run tools/lang/spanish_variants.py")
        }
    }

    @Test
    fun `every English note has a Spanish one with the same placeholders`() {
        val english = File(assets, "notes/en").listFiles { f -> f.name.endsWith(".txt") }.orEmpty().sortedBy { it.name }
        assertTrue(english.isNotEmpty())
        for (note in english) {
            val spanish = File(assets, "notes/es/${note.name}")
            assertTrue(spanish.exists(), "no Spanish note for ${note.name}")
            val holders = { text: String -> Regex("""\{[A-Za-z0-9]+(:[a-z]+)?}""").findAll(text).map { it.value }.sorted().toList() }
            assertEquals(holders(note.readText()), holders(spanish.readText()), "placeholders of ${note.name}")
        }
    }
}
