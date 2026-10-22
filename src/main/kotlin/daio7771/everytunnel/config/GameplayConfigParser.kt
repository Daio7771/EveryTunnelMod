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
            flashlight = flashlight(fields.child("flashlight"), defaults.flashlight),
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
