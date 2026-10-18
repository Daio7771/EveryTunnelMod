package daio7771.everytunnel.systems

import daio7771.everytunnel.config.FlashlightConfig
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
        flashlight = FlashlightConfig(batteryMaxSeconds = 10.0, batteryRefillPercent = 50.0),
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

        var state = ResourceState(stamina = 100.0, thirst = 0.0, battery = 10.0)
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
        val state = ResourceState(stamina = 500.0, thirst = 500.0, battery = 500.0)
        val step = ResourceSystem.step(state, idle, 0.0, config)
        assertEquals(100.0, step.state.stamina)
        assertEquals(100.0, step.state.thirst)
        assertEquals(10.0, step.state.battery)
    }

    @Test
    fun `the flashlight toggles while there is charge`() {
        val full = ResourceState.full(config)
        val (on, turnedOn) = ResourceSystem.toggleFlashlight(full)
        assertEquals(FlashlightToggle.TURNED_ON, turnedOn)
        assertTrue(on.flashlightOn)

        val (off, turnedOff) = ResourceSystem.toggleFlashlight(on)
        assertEquals(FlashlightToggle.TURNED_OFF, turnedOff)
        assertFalse(off.flashlightOn)

        val (stillOff, result) = ResourceSystem.toggleFlashlight(full.copy(battery = 0.0))
        assertEquals(FlashlightToggle.BATTERY_EMPTY, result)
        assertFalse(stillOff.flashlightOn)
    }

    @Test
    fun `the battery drains only while on and switches the flashlight off when empty`() {
        val on = ResourceState.full(config).copy(flashlightOn = true)
        assertEquals(10.0, run(on.copy(flashlightOn = false), idle, 5).battery, 1e-9, "no drain while off")

        val drained = run(on, idle, 4)
        assertEquals(6.0, drained.battery, 1e-9)
        assertTrue(drained.flashlightOn)

        var state = drained
        val died = mutableListOf<Boolean>()
        repeat(7) {
            val step = ResourceSystem.step(state, idle, 1.0, config)
            state = step.state
            died += step.batteryDied
        }
        // 6 s left: empty during the 6th second, already off in the 7th.
        assertEquals(listOf(false, false, false, false, false, true, false), died)
        assertEquals(0.0, state.battery)
        assertFalse(state.flashlightOn)
    }

    @Test
    fun `switching off from outside keeps the charge`() {
        val on = ResourceState.full(config).copy(flashlightOn = true, battery = 7.0)
        val off = ResourceSystem.switchOff(on)
        assertFalse(off.flashlightOn)
        assertEquals(7.0, off.battery)
        assertEquals(off, ResourceSystem.switchOff(off))
    }

    @Test
    fun `a battery item refills by the configured percentage up to full`() {
        val empty = ResourceState.full(config).copy(battery = 0.0)
        val (half, refilled) = ResourceSystem.insertBattery(empty, config)
        assertEquals(BatteryInsert.REFILLED, refilled)
        assertEquals(5.0, half.battery, 1e-9)

        val (full, _) = ResourceSystem.insertBattery(half.copy(battery = 8.0), config)
        assertEquals(10.0, full.battery, 1e-9, "capped at max")
        assertFalse(ResourceSystem.canInsertBattery(full, config))
        assertEquals(BatteryInsert.ALREADY_FULL, ResourceSystem.insertBattery(full, config).second)
    }
}
