package daio7771.everytunnel.common

import com.google.gson.JsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The game loads an element of a model with a rotation only if its angle is -45, -22.5, 0, 22.5 or 45; any other makes the whole
 * model fail and the block show as the purple and black of a missing one (Chapter 2's leaning broom did, with 13).
 */
class ModelRotationTest {

    private val models = File("src/main/resources/assets/every_tunnel/models")

    @Test
    fun `no element of any model has a rotation angle the game refuses`() {
        val allowed = setOf(-45.0, -22.5, 0.0, 22.5, 45.0)
        val files = models.walkTopDown().filter { it.extension == "json" }.toList()
        assertTrue(files.size > 100, "found ${files.size} models")
        val bad = mutableListOf<String>()
        for (file in files) {
            val elements = JsonParser.parseString(file.readText()).asJsonObject["elements"]?.asJsonArray ?: continue
            for (element in elements) {
                val angle = element.asJsonObject["rotation"]?.asJsonObject?.get("angle")?.asDouble ?: continue
                if (angle !in allowed) bad += "${file.name}: $angle"
            }
        }
        assertEquals(emptyList(), bad)
    }
}
