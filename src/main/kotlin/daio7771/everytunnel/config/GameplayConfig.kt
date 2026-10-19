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
    val paleMan: PaleManConfig = PaleManConfig(),
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
    /**
     * Battery percentage below which the low battery warning starts: the HUD battery
     * row blinks and the beam flickers now and then.
     */
    val lowBatteryWarningPercent: Double = 20.0,
    /** Percentage of a full battery that one battery item restores. */
    val batteryRefillPercent: Double = 100.0,
    /** Seconds spent changing the battery (holding use with a battery item); 0 = instant. */
    val batteryChangeSeconds: Double = 1.5,
    /** How far the beam reaches, in blocks. */
    val beamRange: Double = 20.0,
    /** Width of the beam cone, in degrees (edge to edge, at most 120). */
    val beamWidthDegrees: Double = 40.0,
    /** Light level at the center of the beam, 0-15 (15 = torch). Dims along the beam. */
    val beamBrightness: Double = 15.0,
    /** Light level of the faint glow around the player while the flashlight is on, 0-15. */
    val glowBrightness: Double = 5.0,
)

/**
 * Radius in blocks at which each player action can be heard (see `systems.NoiseSystem`).
 * 0 makes that action silent.
 */
data class NoiseConfig(
    /** Per step (about one per block) while sneaking, walking or sprinting. */
    val sneakRadius: Double = 2.0,
    val walkRadius: Double = 6.0,
    val sprintRadius: Double = 14.0,
    val jumpRadius: Double = 8.0,
    /** Landing after a 2-block fall; half of it for tiny drops, up to twice for long falls. */
    val landRadius: Double = 10.0,
    /** Doors, trapdoors, fence gates, levers, buttons, chests and other containers. */
    val interactRadius: Double = 8.0,
    /** Flashlight clicks and changing the battery. */
    val equipmentRadius: Double = 4.0,
    /** Panting while out of stamina, once per second. */
    val exhaustedBreathingRadius: Double = 5.0,
)

/** The Pale man's hearing, speeds and attack (see `entities.PaleManBrain`). */
data class PaleManConfig(
    /** Multiplies every noise radius when he listens (2 = hears everything twice as far). */
    val hearing: Double = 1.0,
    /** Each solid block between a noise and him multiplies its radius by this (1 = walls don't muffle). */
    val wallMuffle: Double = 0.6,
    /**
     * How loud a single noise must be to make him charge at once: 1 at its source, 0 at
     * the edge of its radius. 0.5 = sprinting within 7 blocks.
     */
    val chaseLoudness: Double = 0.5,
    /** Seconds he stands still listening after a first noise, and searching after losing you. */
    val listenSeconds: Double = 2.0,
    val searchSeconds: Double = 4.0,
    /** Seconds he keeps walking towards a noise before giving up. */
    val investigateSeconds: Double = 20.0,
    /** Speed multipliers (1 = about 3 blocks/s): wandering, walking to a noise, chasing. */
    val roamSpeed: Double = 0.6,
    val investigateSpeed: Double = 0.8,
    val chaseSpeed: Double = 1.6,
    /** Damage per hit (a player has 20 health) and seconds between hits. */
    val attackDamage: Double = 20.0,
    val attackCooldownSeconds: Double = 1.0,
    /** Blocks within which he grabs a player while hunting; closer than [touchRange] he grabs anyone he bumps into. */
    val attackRange: Double = 2.0,
    val touchRange: Double = 1.0,
)
