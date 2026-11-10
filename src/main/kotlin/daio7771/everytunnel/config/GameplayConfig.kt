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
    val food: FoodConfig = FoodConfig(),
    val flashlight: FlashlightConfig = FlashlightConfig(),
    val wrench: WrenchConfig = WrenchConfig(),
    val detector: DetectorConfig = DetectorConfig(),
    val hunter: HunterConfig = HunterConfig(),
    val cloud: CloudConfig = CloudConfig(),
    val junior: JuniorConfig = JuniorConfig(),
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

data class FoodConfig(
    /** Health points one food gives back (a player has 20: 8 is 4 hearts). */
    val heal: Double = 8.0,
    /** Seconds the use key is held to eat. */
    val eatSeconds: Double = 1.5,
)

/** Level 5's wrench, a heavy weapon (`platform.WrenchItem`). */
data class WrenchConfig(
    /** Health points a full blow takes (20 is a player's whole health). */
    val damage: Double = 7.0,
    /** Full blows a second: it is heavy. */
    val attacksPerSecond: Double = 1.0,
)

/** Level 5's radiation detector: it notices the Distortion Cloud (`core.detector.Detector`). */
data class DetectorConfig(
    /** Blocks within which it notices one, never through a wall. */
    val range: Double = 16.0,
    /** Closer than this the warning triangle blinks. */
    val dangerRange: Double = 8.0,
    /** Seconds between beeps at the edge of its range... */
    val slowestBeepSeconds: Double = 1.2,
    /** ...and right next to one. */
    val fastestBeepSeconds: Double = 0.1,
)

/** Level 5's Hunters, little ones in groups of three (`entities.HunterBrain`, `platform.HunterEntity`). */
data class HunterConfig(
    /** Their health points, against anything but the wrench (any blow of the wrench kills one). */
    val health: Double = 6.0,
    /** Health points one hit takes (1 is half a heart)... */
    val damage: Double = 1.0,
    /** ...at most once every this many seconds each, from at most this many blocks away. */
    val attackSeconds: Double = 1.0,
    val attackRange: Double = 1.4,
    /** Blocks they see, never through a wall, in a cone this many degrees wide... */
    val sightRange: Double = 16.0,
    val fieldOfView: Double = 140.0,
    /** ...and closer than this, whatever way they face. */
    val senseRange: Double = 3.0,
    /** Seconds without seeing (or being told of) the one they chase before they give up... */
    val lostSeconds: Double = 4.0,
    /** ...and stand looking round, before they walk back. */
    val searchSeconds: Double = 3.0,
    /** Blocks from their spawn point they wander, and seconds they stand between walks. */
    val wanderRadius: Double = 5.0,
    val wanderPauseSeconds: Double = 3.0,
    /** Pathfinding speeds (times their base speed). */
    val walkSpeed: Double = 0.7,
    val runSpeed: Double = 1.5,
)

/** Level 5's Distortion Cloud: lethal to look at (`entities.CloudClock`, `platform.DistortionCloudEntity`). */
data class CloudConfig(
    /** Seconds of looking its way (anywhere in front, with nothing in between) before it kills. */
    val graceSeconds: Double = 1.0,
    /** Seconds without anyone looking its way before it goes. */
    val leaveSeconds: Double = 6.0,
    /** One appears after a random time between these, spent in zones tagged `cloud`. */
    val minSeconds: Double = 120.0,
    val maxSeconds: Double = 240.0,
    /** The same in zones tagged `cloud_often` as well (Level 5's drain): sooner. */
    val oftenMinSeconds: Double = 60.0,
    val oftenMaxSeconds: Double = 120.0,
    /** Blocks ahead of the player where it appears, at the nearest and the farthest. */
    val nearest: Double = 8.0,
    val farthest: Double = 14.0,
    /** Closer than this (walking into it backwards), it kills whatever way one faces. */
    val touchRange: Double = 1.2,
)

/** Level 5's Distortion Junior: it never kills (`entities.JuniorClock`, `platform.DistortionJuniorEntity`). */
data class JuniorConfig(
    /** Blocks within which, with nothing in between, it shakes the camera and marks the player for the Hunters. */
    val range: Double = 8.0,
    /** Seconds of staring at its eye, without looking away, that end it. */
    val stareSeconds: Double = 4.0,
    /** Degrees off its eye the crosshair may be and still count as on it (more, very close). */
    val aimDegrees: Double = 5.0,
    /** How far the camera trembles (degrees) and how fast it drifts away (degrees a second). */
    val tremorDegrees: Double = 0.6,
    val driftDegreesPerSecond: Double = 14.0,
)

data class ThirstConfig(
    /** Maximum thirst points (full = not thirsty). */
    val max: Double = 100.0,
    /** Thirst lost per minute at normal activity (5: full lasts 20 minutes). */
    val drainPerMinute: Double = 5.0,
    /** Multiplier applied to the drain while sprinting. */
    val sprintDrainMultiplier: Double = 2.0,
    /** Health damage dealt when thirst is empty, once per [damageIntervalSeconds]. */
    val damageWhenEmpty: Double = 1.0,
    val damageIntervalSeconds: Double = 10.0,
    /** Thirst points one water bottle gives back. */
    val drinkRestore: Double = 40.0,
    /** Seconds spent drinking a water bottle (holding use). */
    val drinkSeconds: Double = 1.5,
)

data class FlashlightConfig(
    /** Seconds of light from a full battery. */
    val batteryMaxSeconds: Double = 180.0,
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
    /** Gulping a water bottle, once per drink. */
    val drinkRadius: Double = 4.0,
)

/** The Pale man's hearing, speeds and attack (see `entities.PaleManBrain`). */
data class PaleManConfig(
    /** Multiplies every noise radius when he listens (2 = hears everything twice as far). */
    val hearing: Double = 1.0,
    /** Each solid block between a noise and him multiplies its radius by this (1 = walls don't muffle). */
    val wallMuffle: Double = 0.6,
    /**
     * How loud a single noise must be to make him charge at once: 1 at its source, 0 at
     * the edge of its radius. 0.5 = sprinting within 7 blocks. A second noise while he
     * listens needs half of it to start a chase; a fainter one makes him walk there.
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
