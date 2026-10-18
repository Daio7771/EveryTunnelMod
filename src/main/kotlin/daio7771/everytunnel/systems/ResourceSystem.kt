package daio7771.everytunnel.systems

import daio7771.everytunnel.config.GameplayConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A player's stamina, thirst and flashlight battery. */
data class ResourceState(
    val stamina: Double,
    val thirst: Double,
    /** Seconds of flashlight light left in the battery. */
    val battery: Double,
    /** True while the flashlight is switched on. */
    val flashlightOn: Boolean = false,
    /** Out of stamina: no sprinting until stamina recovers (see `StaminaConfig.exhaustedRecoverPercent`). */
    val exhausted: Boolean = false,
    /** Seconds left before stamina starts regenerating. */
    val regenCooldown: Double = 0.0,
    /** Seconds spent with empty thirst since the last thirst damage. */
    val thirstDamageTimer: Double = 0.0,
) {
    companion object {
        fun full(config: GameplayConfig) =
            ResourceState(config.stamina.max, config.thirst.max, config.flashlight.batteryMaxSeconds)
    }
}

/** What the player did during one tick, and whether a flashlight is anywhere in their inventory. */
data class ResourceInput(val sprinting: Boolean, val jumps: Int, val hasFlashlight: Boolean = false)

/**
 * The state after one tick, the health damage to deal (0 if none), and whether the
 * flashlight switched itself off this tick because the battery ran out.
 */
data class ResourceStep(val state: ResourceState, val damage: Double, val batteryDied: Boolean = false)

/** Result of pressing the flashlight key. */
enum class FlashlightToggle { TURNED_ON, TURNED_OFF, NO_FLASHLIGHT, BATTERY_EMPTY }

/** Result of using a battery item. */
enum class BatteryInsert { REFILLED, ALREADY_FULL, NO_FLASHLIGHT }

/**
 * Stamina, thirst and flashlight battery simulation.
 *
 * - Stamina drains while sprinting and per jump, and regenerates after a short
 *   delay without use. At 0 the player is exhausted and cannot sprint until it
 *   recovers to a configured percentage.
 * - Thirst drains slowly all the time (faster while sprinting). When empty, the
 *   player takes damage at a fixed interval.
 * - The flashlight battery drains one second per second while the flashlight is
 *   on. At 0 the flashlight switches off; a battery item refills it. The
 *   flashlight also switches off when the player no longer carries one.
 *
 * [step], [toggleFlashlight], [insertBattery] and [withoutDrain] are pure functions
 * of the state, input and config; the per-player states live here too. Plain Kotlin
 * types only, so it is unit tested without Minecraft. Driven every server tick by
 * `platform.ResourceHooks`, which also saves the states with the world.
 */
object ResourceSystem {

    private val states = ConcurrentHashMap<UUID, ResourceState>()

    /** The player's state; full resources if the player has none yet. */
    fun stateOf(playerId: UUID, config: GameplayConfig): ResourceState =
        states[playerId] ?: ResourceState.full(config)

    fun set(playerId: UUID, state: ResourceState) {
        states[playerId] = state
    }

    /** Back to full resources (on respawn after death). */
    fun reset(playerId: UUID) {
        states.remove(playerId)
    }

    /** Advances the player's state by one tick and stores the result. */
    fun tick(playerId: UUID, input: ResourceInput, dtSeconds: Double, config: GameplayConfig): ResourceStep {
        val step = step(stateOf(playerId, config), input, dtSeconds, config)
        states[playerId] = step.state
        return step
    }

    /** All stored states, for saving. */
    fun all(): Map<UUID, ResourceState> = HashMap(states)

    /** Replaces all stored states (on world load). */
    fun replaceAll(loaded: Map<UUID, ResourceState>) {
        states.clear()
        states.putAll(loaded)
    }

    /** Forgets all players (on server stop). */
    fun clear() {
        states.clear()
    }

    fun step(state: ResourceState, input: ResourceInput, dtSeconds: Double, config: GameplayConfig): ResourceStep {
        val s = config.stamina
        val t = config.thirst
        val f = config.flashlight

        // Stamina
        var stamina = state.stamina.coerceAtMost(s.max)
        var cooldown = state.regenCooldown
        val used = input.sprinting || input.jumps > 0
        if (input.sprinting) stamina -= s.sprintDrainPerSecond * dtSeconds
        stamina -= s.jumpCost * input.jumps
        if (used) {
            cooldown = s.regenDelaySeconds
        } else if (cooldown > 0.0) {
            cooldown = (cooldown - dtSeconds).coerceAtLeast(0.0)
        } else {
            stamina += s.regenPerSecond * dtSeconds
        }
        stamina = stamina.coerceIn(0.0, s.max)

        val exhausted = when {
            stamina <= 0.0 -> true
            state.exhausted -> stamina < s.max * s.exhaustedRecoverPercent / 100.0
            else -> false
        }

        // Thirst
        val drainMultiplier = if (input.sprinting) t.sprintDrainMultiplier else 1.0
        val thirst = (state.thirst.coerceAtMost(t.max) - t.drainPerMinute / 60.0 * dtSeconds * drainMultiplier)
            .coerceAtLeast(0.0)
        var damageTimer = 0.0
        var damage = 0.0
        if (thirst <= 0.0) {
            damageTimer = state.thirstDamageTimer + dtSeconds
            if (damageTimer >= t.damageIntervalSeconds) {
                damageTimer -= t.damageIntervalSeconds
                damage = t.damageWhenEmpty
            }
        }

        // Flashlight battery
        var battery = state.battery.coerceIn(0.0, f.batteryMaxSeconds)
        var flashlightOn = state.flashlightOn && input.hasFlashlight
        var batteryDied = false
        if (flashlightOn) {
            battery = (battery - dtSeconds).coerceAtLeast(0.0)
            if (battery <= 0.0) {
                flashlightOn = false
                batteryDied = true
            }
        }

        val next = ResourceState(stamina, thirst, battery, flashlightOn, exhausted, cooldown, damageTimer)
        return ResourceStep(next, damage, batteryDied)
    }

    /**
     * The flashlight part of a tick for players the survival rules do not apply to
     * (Creative, worlds without levels): no drain, but it still switches off when the
     * player stops carrying a flashlight.
     */
    fun withoutDrain(state: ResourceState, hasFlashlight: Boolean): ResourceState =
        if (state.flashlightOn && !hasFlashlight) state.copy(flashlightOn = false) else state

    /** Flashlight key pressed: switches the flashlight on or off if possible. */
    fun toggleFlashlight(state: ResourceState, hasFlashlight: Boolean): Pair<ResourceState, FlashlightToggle> = when {
        state.flashlightOn -> state.copy(flashlightOn = false) to FlashlightToggle.TURNED_OFF
        !hasFlashlight -> state to FlashlightToggle.NO_FLASHLIGHT
        state.battery <= 0.0 -> state to FlashlightToggle.BATTERY_EMPTY
        else -> state.copy(flashlightOn = true) to FlashlightToggle.TURNED_ON
    }

    /** True if a battery item would add charge (the player carries a flashlight and the battery is not full). */
    fun canInsertBattery(state: ResourceState, hasFlashlight: Boolean, config: GameplayConfig): Boolean =
        hasFlashlight && state.battery < config.flashlight.batteryMaxSeconds

    /** Battery item used: refills the battery by `FlashlightConfig.batteryRefillPercent`. */
    fun insertBattery(state: ResourceState, hasFlashlight: Boolean, config: GameplayConfig): Pair<ResourceState, BatteryInsert> {
        val f = config.flashlight
        return when {
            !hasFlashlight -> state to BatteryInsert.NO_FLASHLIGHT
            state.battery >= f.batteryMaxSeconds -> state to BatteryInsert.ALREADY_FULL
            else -> {
                val battery = (state.battery + f.batteryMaxSeconds * f.batteryRefillPercent / 100.0)
                    .coerceAtMost(f.batteryMaxSeconds)
                state.copy(battery = battery) to BatteryInsert.REFILLED
            }
        }
    }
}
