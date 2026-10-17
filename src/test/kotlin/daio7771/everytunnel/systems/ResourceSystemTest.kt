package daio7771.everytunnel.systems

import daio7771.everytunnel.config.GameplayConfig
import daio7771.everytunnel.config.StaminaConfig
import daio7771.everytunnel.config.ThirstConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceSystemTest {

    private val config = GameplayConfig(
        stamina = StaminaConfig(
            max = 100.0, sprintDrainPerSecond = 10.0, jumpCost = 5.0,
            regenPerSecond = 20.0, regenDelaySeconds = 1.0, exhaustedRecoverPercent = 30.0,
        ),
        thirst = ThirstConfig(
            max = 100.0, drainPerMinute = 60.0, sprintDrainMultiplier = 2.0,
            damageWhenEmpty = 2.0, damageIntervalSeconds = 5.0,
        ),
    )
    private val idle = ResourceInput(sprinting = false, jumps = 0)
    private val sprint = ResourceInput(sprinting = true, jumps = 0)

    /** Runs [seconds] of one-second steps. */
    private fun run(state: ResourceState, input: ResourceInput, seconds: Int): ResourceState {
        var s = state
        repeat(seconds) { s = ResourceSystem.step(s, input, 1.0, config).state }
        return s
    }

    @Test
    fun `sprinting and jumping drain stamina, regeneration waits for the delay`() {
        val sprinted = run(ResourceState.full(config), sprint, 3)
        assertEquals(70.0, sprinted.stamina, 1e-9)

        val jumped = ResourceSystem.step(sprinted, ResourceInput(false, 2), 1.0, config).state
        assertEquals(60.0, jumped.stamina, 1e-9)

        val waited = ResourceSystem.step(jumped, idle, 1.0, config).state
        assertEquals(60.0, waited.stamina, 1e-9, "no regeneration during the delay")
        val regenerated = ResourceSystem.step(waited, idle, 1.0, config).state
        assertEquals(80.0, regenerated.stamina, 1e-9)
        assertEquals(100.0, run(regenerated, idle, 10).stamina, 1e-9, "capped at max")
    }

    @Test
    fun `exhaustion starts at zero and ends at the recover percentage`() {
        val empty = run(ResourceState.full(config), sprint, 10)
        assertEquals(0.0, empty.stamina)
        assertTrue(empty.exhausted)

        // 1 s delay, then 20/s: 20 after 2 s (still exhausted), 40 after 3 s (>= 30%).
        assertTrue(run(empty, idle, 2).exhausted)
        assertFalse(run(empty, idle, 3).exhausted)
    }

    @Test
    fun `thirst drains faster while sprinting and deals damage at an interval when empty`() {
        assertEquals(90.0, run(ResourceState.full(config), idle, 10).thirst, 1e-9)
        assertEquals(80.0, run(ResourceState.full(config), sprint, 10).thirst, 1e-9)

        var state = ResourceState(stamina = 100.0, thirst = 0.0)
        val damage = mutableListOf<Double>()
        repeat(12) {
            val step = ResourceSystem.step(state, idle, 1.0, config)
            state = step.state
            damage += step.damage
        }
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0), damage)
    }

    @Test
    fun `values above a lowered max are clamped`() {
        val state = ResourceState(stamina = 500.0, thirst = 500.0)
        val step = ResourceSystem.step(state, idle, 0.0, config)
        assertEquals(100.0, step.state.stamina)
        assertEquals(100.0, step.state.thirst)
    }
}
