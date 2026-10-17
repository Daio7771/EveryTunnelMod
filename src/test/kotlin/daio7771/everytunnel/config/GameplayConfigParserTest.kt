package daio7771.everytunnel.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameplayConfigParserTest {

    @Test
    fun `defaults survive a write and read round trip without problems`() {
        val result = GameplayConfigParser.parse(GameplayConfigParser.toJson(GameplayConfig()))
        assertEquals(GameplayConfig(), result.config)
        assertEquals(emptyList(), result.problems)
        assertEquals(emptyList(), result.missingKeys)
    }

    @Test
    fun `out of range and wrong type values fall back to defaults`() {
        val result = GameplayConfigParser.parse(
            """{ "stamina": { "max": -5, "jump_cost": "lots" }, "flashlight": { "low_battery_warning_percent": 150 } }""",
        )
        val defaults = GameplayConfig()
        assertEquals(defaults.stamina.max, result.config.stamina.max)
        assertEquals(defaults.stamina.jumpCost, result.config.stamina.jumpCost)
        assertEquals(defaults.flashlight.lowBatteryWarningPercent, result.config.flashlight.lowBatteryWarningPercent)
        assertEquals(3, result.problems.size, result.problems.toString())
    }

    @Test
    fun `valid values are read and missing keys are reported`() {
        val result = GameplayConfigParser.parse("""{ "config_version": 1, "noise": { "sprint_radius": 20 } }""")
        assertEquals(20.0, result.config.noise.sprintRadius)
        assertEquals(emptyList(), result.problems)
        assertTrue("stamina" in result.missingKeys)
        assertTrue("noise.walk_radius" in result.missingKeys)
    }

    @Test
    fun `unknown keys are reported as likely typos`() {
        val result = GameplayConfigParser.parse("""{ "stamina": { "sprint_drian_per_second": 3 } }""")
        assertTrue(result.problems.any { "stamina.sprint_drian_per_second" in it }, result.problems.toString())
    }

    @Test
    fun `invalid json gives defaults and a syntax error`() {
        val result = GameplayConfigParser.parse("{ not json")
        assertTrue(result.syntaxError)
        assertEquals(GameplayConfig(), result.config)
    }
}
