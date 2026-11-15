package daio7771.everytunnel.chapter2.level1.core

import com.google.gson.JsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The texts the tasks, NonameOS and the hold bars show, in English and Spanish, with the same placeholders in both. */
class Ch2L1TaskTextsTest {

    private val lang = File("src/main/resources/assets/every_tunnel/lang")

    private fun load(name: String) = JsonParser.parseString(File(lang, "$name.json").readText()).asJsonObject.entrySet().associate { it.key to it.value.asString }

    private val keys = listOf(
        "objective.count", "task.not_now", "tube.taken", "analyzer.done", "broom.taken", "broom.need",
        "hold.calibrating", "hold.sweeping", "os.title", "os.version", "os.registry", "os.expected", "os.row.not_scanned", "os.row.scanned",
        "os.row.registering", "os.row.registered", "os.button.register", "os.progress", "os.done", "os.hint",
    ).map { "every_tunnel.ch2.l1.$it" } + listOf(
        "item.every_tunnel.broom", "block.every_tunnel.sample_tube", "block.every_tunnel.noname_terminal", "block.every_tunnel.analyzer",
        "block.every_tunnel.leaning_broom", "block.every_tunnel.dirt_spot", "block.every_tunnel.factory_glass", "block.every_tunnel.factory_door",
        "block.every_tunnel.factory_camera", "block.every_tunnel.predecessor_locker",
    )

    private fun placeholders(text: String) = Regex("%(\\d+\\$)?[sd]").findAll(text).map { it.value }.toList().sorted()

    @Test
    fun `every text exists in English and in Spanish, with the same placeholders`() {
        val en = load("en_us")
        val es = load("es_es")
        for (key in keys) {
            assertTrue(key in en, "no English text for $key")
            assertTrue(key in es, "no Spanish text for $key")
            assertEquals(placeholders(en.getValue(key)), placeholders(es.getValue(key)), "the placeholders of $key differ")
        }
    }

    @Test
    fun `the other Spanish locales are copies of es_es`() {
        val es = File(lang, "es_es.json").readText()
        for (name in listOf("es_ar", "es_cl", "es_ec", "es_mx", "es_uy", "es_ve")) assertEquals(es, File(lang, "$name.json").readText(), name)
    }
}
