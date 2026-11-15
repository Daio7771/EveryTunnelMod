package daio7771.everytunnel.core.ch2l1

import com.google.gson.JsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ch2L1ObjectivesTest {

    private fun lang(name: String): Map<String, String> =
        JsonParser.parseString(File("src/main/resources/assets/every_tunnel/lang/$name.json").readText()).asJsonObject.entrySet()
            .associate { it.key to it.value.asString }

    @Test
    fun `the steps with something to do have an objective and the cinematics and the end have none`() {
        assertEquals("every_tunnel.ch2.l1.objective.clockin", Ch2L1Objectives.keyFor(Ch2L1State.ACT1_CLOCKIN))
        assertEquals("every_tunnel.ch2.l1.objective.task1", Ch2L1Objectives.keyFor(Ch2L1State.ACT1_TASK1))
        assertEquals("every_tunnel.ch2.l1.objective.follow", Ch2L1Objectives.keyFor(Ch2L1State.ACT2_FOLLOW))
        assertEquals("every_tunnel.ch2.l1.objective.explore_lab", Ch2L1Objectives.keyFor(Ch2L1State.ACT3_EXPLORE))
        assertEquals("every_tunnel.ch2.l1.objective.overtime", Ch2L1Objectives.keyFor(Ch2L1State.ACT3_OVERTIME))
        assertNull(Ch2L1Objectives.keyFor(Ch2L1State.ACT2_MEETING))
        assertNull(Ch2L1Objectives.keyFor(Ch2L1State.ACT3_CUTSCENE))
        assertNull(Ch2L1Objectives.keyFor(Ch2L1State.DONE))
    }

    @Test
    fun `every objective, and the panel's title, has its text in both languages`() {
        val english = lang("en_us")
        val spanish = lang("es_es")
        val keys = Ch2L1State.entries.mapNotNull { Ch2L1Objectives.keyFor(it) } + Ch2L1Objectives.TITLE_KEY
        for (key in keys) {
            assertNotNull(english[key], "$key is missing from en_us.json")
            assertNotNull(spanish[key], "$key is missing from es_es.json")
            assertTrue(english.getValue(key).isNotBlank() && spanish.getValue(key).isNotBlank(), "$key is empty")
        }
    }

    @Test
    fun `no two steps share an objective`() {
        val keys = Ch2L1State.entries.mapNotNull { Ch2L1Objectives.keyFor(it) }
        assertEquals(keys.size, keys.toSet().size)
    }
}
