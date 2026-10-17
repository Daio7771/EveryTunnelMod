package daio7771.everytunnel.config

/**
 * All tunable gameplay values, loaded from `config/every_tunnel/gameplay.json`.
 *
 * Property names map to snake_case keys in the file (`sprintDrainPerSecond` ->
 * `sprint_drain_per_second`). The defaults below are placeholders to be tuned in
 * playtesting; they are also what gets written to a fresh config file.
 *
 * When adding a value: add the property with a default here, read it in
 * [GameplayConfigParser], and existing config files get the new key added
 * automatically on the next launch.
 */
data class GameplayConfig(
    /** Format version of the file, used for future migrations. */
    val configVersion: Int = CURRENT_VERSION,
    val stamina: StaminaConfig = StaminaConfig(),
    val thirst: ThirstConfig = ThirstConfig(),
    val flashlight: FlashlightConfig = FlashlightConfig(),
    val noise: NoiseConfig = NoiseConfig(),
) {
    companion object {
        const val CURRENT_VERSION: Int = 1
    }
}

data class StaminaConfig(
    /** Maximum stamina points. */
    val max: Double = 100.0,
    /** Stamina lost per second while sprinting. */
    val sprintDrainPerSecond: Double = 12.0,
    /** Stamina lost per jump. */
    val jumpCost: Double = 8.0,
    /** Stamina regained per second while not sprinting. */
    val regenPerSecond: Double = 15.0,
    /** Seconds after the last stamina use before regeneration starts. */
    val regenDelaySeconds: Double = 1.5,
    /**
     * When stamina runs out the player is exhausted and cannot sprint until stamina
     * has regenerated to this percentage of [max].
     */
    val exhaustedRecoverPercent: Double = 30.0,
)

data class ThirstConfig(
    /** Maximum thirst points (full = not thirsty). */
    val max: Double = 100.0,
    /** Thirst lost per minute at normal activity. */
    val drainPerMinute: Double = 2.0,
    /** Multiplier applied to the drain while sprinting. */
    val sprintDrainMultiplier: Double = 2.0,
    /** Health damage dealt when thirst is empty, once per [damageIntervalSeconds]. */
    val damageWhenEmpty: Double = 1.0,
    val damageIntervalSeconds: Double = 10.0,
)

data class FlashlightConfig(
    /** Seconds of light from a full battery. */
    val batteryMaxSeconds: Double = 300.0,
    /** Battery percentage below which the low battery warning starts. */
    val lowBatteryWarningPercent: Double = 20.0,
)

/** Radius in blocks at which each player action can be heard. */
data class NoiseConfig(
    val sneakRadius: Double = 2.0,
    val walkRadius: Double = 6.0,
    val sprintRadius: Double = 14.0,
    val jumpRadius: Double = 8.0,
    val landRadius: Double = 10.0,
)
