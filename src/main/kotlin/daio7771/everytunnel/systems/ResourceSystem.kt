package daio7771.everytunnel.systems

import daio7771.everytunnel.config.GameplayConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A player's stamina and thirst. */
data class ResourceState(
    val stamina: Double,
    val thirst: Double,
    /** Out of stamina: no sprinting until stamina recovers (see `StaminaConfig.exhaustedRecoverPercent`). */
    val exhausted: Boolean = false,
    /** Seconds left before stamina starts regenerating. */
    val regenCooldown: Double = 0.0,
    /** Seconds spent with empty thirst since the last thirst damage. */
    val thirstDamageTimer: Double = 0.0,
) {
    companion object {
        fun full(config: GameplayConfig) = ResourceState(config.stamina.max, config.thirst.max)
    }
}

/** What the player did during one tick. */
data class ResourceInput(val sprinting: Boolean, val jumps: Int)

/** The state after one tick, and the health damage to deal (0 if none). */
data class ResourceStep(val state: ResourceState, val damage: Double)

/**
 * Stamina and thirst simulation.
 *
 * - Stamina drains while sprinting and per jump, and regenerates after a short
 *   delay without use. At 0 the player is exhausted and cannot sprint until it
 *   recovers to a configured percentage.
 * - Thirst drains slowly all the time (faster while sprinting). When empty, the
 *   player takes damage at a fixed interval.
 *
 * [step] is a pure function of the state, input and config; the per-player states
 * live here too. Plain Kotlin types only, so it is unit tested without Minecraft.
 * Driven every server tick by `platform.ResourceHooks`, which also saves the
 * states with the world. The flashlight battery is added with the flashlight item.
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

        return ResourceStep(ResourceState(stamina, thirst, exhausted, cooldown, damageTimer), damage)
    }
}
