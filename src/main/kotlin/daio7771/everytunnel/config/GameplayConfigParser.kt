package daio7771.everytunnel.config

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import daio7771.everytunnel.util.JsonFields

/**
 * Converts between [GameplayConfig] and JSON text. No file IO here (see
 * [ConfigManager]), so it can be unit tested.
 *
 * Invalid values never stop loading: each one falls back to its default and is
 * reported in [Result.problems].
 */
object GameplayConfigParser {

    data class Result(
        val config: GameplayConfig,
        /** Readable descriptions of invalid or unknown values. */
        val problems: List<String>,
        /** JSON paths of keys that were absent and got their default. */
        val missingKeys: List<String>,
        /** True if the text was not valid JSON at all; [config] is then all defaults. */
        val syntaxError: Boolean = false,
    )

    private val GSON: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    fun toJson(config: GameplayConfig): String = GSON.toJson(config) + "\n"

    fun parse(text: String): Result {
        val root = try {
            JsonParser.parseString(text)
        } catch (e: JsonParseException) {
            return Result(GameplayConfig(), listOf("invalid JSON: ${e.message}"), emptyList(), syntaxError = true)
        }
        if (!root.isJsonObject) {
            return Result(GameplayConfig(), listOf("the file must contain a JSON object"), emptyList(), syntaxError = true)
        }

        val fields = JsonFields(root.asJsonObject, "")
        val defaults = GameplayConfig()

        val version = fields.int("config_version", GameplayConfig.CURRENT_VERSION, min = 1)
        if (version > GameplayConfig.CURRENT_VERSION) {
            fields.problems += "config_version $version is newer than this mod supports " +
                "(${GameplayConfig.CURRENT_VERSION}); values are read as far as possible"
        }

        val config = GameplayConfig(
            configVersion = GameplayConfig.CURRENT_VERSION,
            stamina = stamina(fields.child("stamina"), defaults.stamina),
            thirst = thirst(fields.child("thirst"), defaults.thirst),
            food = food(fields.child("food"), defaults.food),
            flashlight = flashlight(fields.child("flashlight"), defaults.flashlight),
            wrench = wrench(fields.child("wrench"), defaults.wrench),
            detector = detector(fields.child("detector"), defaults.detector),
            hunter = hunter(fields.child("hunter"), defaults.hunter),
            cloud = cloud(fields.child("cloud"), defaults.cloud),
            junior = junior(fields.child("junior"), defaults.junior),
            noise = noise(fields.child("noise"), defaults.noise),
            paleMan = paleMan(fields.child("pale_man"), defaults.paleMan),
        )
        fields.reportUnknownKeys()
        return Result(config, fields.problems.toList(), fields.missingKeys.toList())
    }

    private fun stamina(f: JsonFields, d: StaminaConfig) = StaminaConfig(
        max = f.double("max", d.max, min = 1.0),
        sprintDrainPerSecond = f.double("sprint_drain_per_second", d.sprintDrainPerSecond, min = 0.0),
        jumpCost = f.double("jump_cost", d.jumpCost, min = 0.0),
        regenPerSecond = f.double("regen_per_second", d.regenPerSecond, min = 0.0),
        regenDelaySeconds = f.double("regen_delay_seconds", d.regenDelaySeconds, min = 0.0),
        exhaustedRecoverPercent = f.double("exhausted_recover_percent", d.exhaustedRecoverPercent, min = 0.0, max = 100.0),
    ).also { f.reportUnknownKeys() }

    private fun food(f: JsonFields, d: FoodConfig) = FoodConfig(
        heal = f.double("heal", d.heal, min = 0.0),
        eatSeconds = f.double("eat_seconds", d.eatSeconds, min = 0.05, max = 10.0),
        energySeconds = f.double("energy_seconds", d.energySeconds, min = 0.0, max = 600.0),
    ).also { f.reportUnknownKeys() }

    private fun wrench(f: JsonFields, d: WrenchConfig) = WrenchConfig(
        damage = f.double("damage", d.damage, min = 0.0, max = 100.0),
        attacksPerSecond = f.double("attacks_per_second", d.attacksPerSecond, min = 0.1, max = 20.0),
    ).also { f.reportUnknownKeys() }

    private fun detector(f: JsonFields, d: DetectorConfig) = DetectorConfig(
        range = f.double("range", d.range, min = 1.0, max = 64.0),
        dangerRange = f.double("danger_range", d.dangerRange, min = 0.0, max = 64.0),
        slowestCountsPerSecond = f.double("slowest_counts_per_second", d.slowestCountsPerSecond, min = 0.1, max = 400.0),
        fastestCountsPerSecond = f.double("fastest_counts_per_second", d.fastestCountsPerSecond, min = 0.1, max = 400.0),
    ).also { f.reportUnknownKeys() }

    private fun hunter(f: JsonFields, d: HunterConfig) = HunterConfig(
        health = f.double("health", d.health, min = 1.0, max = 1000.0),
        damage = f.double("damage", d.damage, min = 0.0, max = 100.0),
        attackSeconds = f.double("attack_seconds", d.attackSeconds, min = 0.05, max = 10.0),
        attackRange = f.double("attack_range", d.attackRange, min = 0.5, max = 4.0),
        sightRange = f.double("sight_range", d.sightRange, min = 0.0, max = 64.0),
        fieldOfView = f.double("field_of_view", d.fieldOfView, min = 0.0, max = 360.0),
        senseRange = f.double("sense_range", d.senseRange, min = 0.0, max = 64.0),
        lostSeconds = f.double("lost_seconds", d.lostSeconds, min = 0.0, max = 60.0),
        searchSeconds = f.double("search_seconds", d.searchSeconds, min = 0.0, max = 60.0),
        wanderRadius = f.double("wander_radius", d.wanderRadius, min = 0.0, max = 32.0),
        wanderPauseSeconds = f.double("wander_pause_seconds", d.wanderPauseSeconds, min = 0.0, max = 60.0),
        walkSpeed = f.double("walk_speed", d.walkSpeed, min = 0.1, max = 5.0),
        runSpeed = f.double("run_speed", d.runSpeed, min = 0.1, max = 5.0),
    ).also { f.reportUnknownKeys() }

    private fun cloud(f: JsonFields, d: CloudConfig) = CloudConfig(
        graceSeconds = f.double("grace_seconds", d.graceSeconds, min = 0.05, max = 10.0),
        leaveSeconds = f.double("leave_seconds", d.leaveSeconds, min = 0.5, max = 120.0),
        minSeconds = f.double("min_seconds", d.minSeconds, min = 5.0, max = 3600.0),
        maxSeconds = f.double("max_seconds", d.maxSeconds, min = 5.0, max = 3600.0),
        oftenMinSeconds = f.double("often_min_seconds", d.oftenMinSeconds, min = 5.0, max = 3600.0),
        oftenMaxSeconds = f.double("often_max_seconds", d.oftenMaxSeconds, min = 5.0, max = 3600.0),
        nearest = f.double("nearest", d.nearest, min = 2.0, max = 32.0),
        farthest = f.double("farthest", d.farthest, min = 2.0, max = 32.0),
        touchRange = f.double("touch_range", d.touchRange, min = 0.0, max = 4.0),
        gameGraceSeconds = f.double("game_grace_seconds", d.gameGraceSeconds, min = 0.05, max = 10.0),
        gameBehindBlocks = f.double("game_behind_blocks", d.gameBehindBlocks, min = 2.0, max = 32.0),
        gameAheadBlocks = f.double("game_ahead_blocks", d.gameAheadBlocks, min = 2.0, max = 32.0),
        gameForwardMinSeconds = f.double("game_forward_min_seconds", d.gameForwardMinSeconds, min = 0.5, max = 120.0),
        gameForwardMaxSeconds = f.double("game_forward_max_seconds", d.gameForwardMaxSeconds, min = 0.5, max = 120.0),
        gameBackMinSeconds = f.double("game_back_min_seconds", d.gameBackMinSeconds, min = 0.5, max = 120.0),
        gameBackMaxSeconds = f.double("game_back_max_seconds", d.gameBackMaxSeconds, min = 0.5, max = 120.0),
        gameCreepStart = f.double("game_creep_start", d.gameCreepStart, min = 0.0, max = 10.0),
        gameCreepEnd = f.double("game_creep_end", d.gameCreepEnd, min = 0.0, max = 10.0),
        gameHurry = f.double("game_hurry", d.gameHurry, min = 0.0, max = 0.9),
        gameDarkLift = f.double("game_dark_lift", d.gameDarkLift, min = 0.0, max = 1.0),
    ).also { f.reportUnknownKeys() }

    private fun junior(f: JsonFields, d: JuniorConfig) = JuniorConfig(
        range = f.double("range", d.range, min = 1.0, max = 32.0),
        stareSeconds = f.double("stare_seconds", d.stareSeconds, min = 0.05, max = 60.0),
        aimDegrees = f.double("aim_degrees", d.aimDegrees, min = 0.5, max = 45.0),
        tremorDegrees = f.double("tremor_degrees", d.tremorDegrees, min = 0.0, max = 10.0),
        driftDegreesPerSecond = f.double("drift_degrees_per_second", d.driftDegreesPerSecond, min = 0.0, max = 180.0),
    ).also { f.reportUnknownKeys() }

    private fun thirst(f: JsonFields, d: ThirstConfig) = ThirstConfig(
        max = f.double("max", d.max, min = 1.0),
        drainPerMinute = f.double("drain_per_minute", d.drainPerMinute, min = 0.0),
        sprintDrainMultiplier = f.double("sprint_drain_multiplier", d.sprintDrainMultiplier, min = 0.0),
        damageWhenEmpty = f.double("damage_when_empty", d.damageWhenEmpty, min = 0.0),
        damageIntervalSeconds = f.double("damage_interval_seconds", d.damageIntervalSeconds, min = 0.1),
        drinkRestore = f.double("drink_restore", d.drinkRestore, min = 0.0),
        drinkSeconds = f.double("drink_seconds", d.drinkSeconds, min = 0.05, max = 10.0),
    ).also { f.reportUnknownKeys() }

    private fun flashlight(f: JsonFields, d: FlashlightConfig) = FlashlightConfig(
        batteryMaxSeconds = f.double("battery_max_seconds", d.batteryMaxSeconds, min = 1.0),
        lowBatteryWarningPercent = f.double("low_battery_warning_percent", d.lowBatteryWarningPercent, min = 0.0, max = 100.0),
        batteryRefillPercent = f.double("battery_refill_percent", d.batteryRefillPercent, min = 0.0, max = 100.0),
        batteryChangeSeconds = f.double("battery_change_seconds", d.batteryChangeSeconds, min = 0.0, max = 10.0),
        beamRange = f.double("beam_range", d.beamRange, min = 1.0, max = 64.0),
        beamWidthDegrees = f.double("beam_width_degrees", d.beamWidthDegrees, min = 1.0, max = 120.0),
        beamBrightness = f.double("beam_brightness", d.beamBrightness, min = 0.0, max = 15.0),
        glowBrightness = f.double("glow_brightness", d.glowBrightness, min = 0.0, max = 15.0),
    ).also { f.reportUnknownKeys() }

    private fun noise(f: JsonFields, d: NoiseConfig) = NoiseConfig(
        sneakRadius = f.double("sneak_radius", d.sneakRadius, min = 0.0),
        walkRadius = f.double("walk_radius", d.walkRadius, min = 0.0),
        sprintRadius = f.double("sprint_radius", d.sprintRadius, min = 0.0),
        jumpRadius = f.double("jump_radius", d.jumpRadius, min = 0.0),
        landRadius = f.double("land_radius", d.landRadius, min = 0.0),
        interactRadius = f.double("interact_radius", d.interactRadius, min = 0.0),
        equipmentRadius = f.double("equipment_radius", d.equipmentRadius, min = 0.0),
        exhaustedBreathingRadius = f.double("exhausted_breathing_radius", d.exhaustedBreathingRadius, min = 0.0),
        drinkRadius = f.double("drink_radius", d.drinkRadius, min = 0.0),
    ).also { f.reportUnknownKeys() }

    private fun paleMan(f: JsonFields, d: PaleManConfig) = PaleManConfig(
        hearing = f.double("hearing", d.hearing, min = 0.0, max = 10.0),
        wallMuffle = f.double("wall_muffle", d.wallMuffle, min = 0.0, max = 1.0),
        chaseLoudness = f.double("chase_loudness", d.chaseLoudness, min = 0.0, max = 1.0),
        listenSeconds = f.double("listen_seconds", d.listenSeconds, min = 0.0, max = 30.0),
        searchSeconds = f.double("search_seconds", d.searchSeconds, min = 0.0, max = 60.0),
        investigateSeconds = f.double("investigate_seconds", d.investigateSeconds, min = 1.0, max = 300.0),
        roamSpeed = f.double("roam_speed", d.roamSpeed, min = 0.0, max = 5.0),
        investigateSpeed = f.double("investigate_speed", d.investigateSpeed, min = 0.0, max = 5.0),
        chaseSpeed = f.double("chase_speed", d.chaseSpeed, min = 0.0, max = 5.0),
        attackDamage = f.double("attack_damage", d.attackDamage, min = 0.0),
        attackCooldownSeconds = f.double("attack_cooldown_seconds", d.attackCooldownSeconds, min = 0.05),
        attackRange = f.double("attack_range", d.attackRange, min = 0.5, max = 6.0),
        touchRange = f.double("touch_range", d.touchRange, min = 0.0, max = 6.0),
    ).also { f.reportUnknownKeys() }
}
