package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.config.GameplayConfig
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.PlayerLocationTracker
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.systems.ResourceState
import daio7771.everytunnel.systems.ResourceSystem
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import java.util.Locale
import java.util.UUID

/**
 * Playtest log: a line starting with [PREFIX] for everything that matters when tuning the
 * demo, so a whole playthrough can be read back from `logs/latest.log`
 * (`grep '\[playtest\]' latest.log`).
 *
 * - When a run starts: the gameplay config in use and what each level holds (loot spots,
 *   Pale men).
 * - Every [STATUS_SECONDS] of play: thirst, stamina, battery, health, what the player
 *   carries, and where they are.
 * - As it happens: levels entered, loot picked up, drinks, battery changes and the battery
 *   running out, exhaustion and recovery, thirst below [LOW_THIRST_PERCENT] and empty, Pale
 *   men hearing the player and hitting them, deaths and their cause.
 * - The Pale men's way: a path that does not reach where he is going (once per state, with
 *   what blocks the way), and why each walk or chase towards a noise ends.
 * - A summary per level at the end of the demo, and when the player leaves or starts a new
 *   game halfway: time, deaths by cause, hits, loot, drinks, batteries, flashlight time,
 *   exhaustion, and what was left at the end.
 *
 * A run starts with a new game ([newGame]), or when the player is first seen in the world.
 * Times are play time since then, counted in server ticks, so a paused game does not
 * count. Only players the world rules apply to (Survival or Adventure in a world with
 * levels). Runs live in memory only: reopening the world starts a new one.
 */
object PlaytestLog {

    private const val PREFIX = "[playtest]"
    private const val STATUS_SECONDS = 30
    private const val STATUS_TICKS = STATUS_SECONDS * 20L
    private const val LOW_THIRST_PERCENT = 25.0
    /** Stats for play outside every level (between two levels, or before the first). */
    private const val NO_LEVEL = "(no level)"

    /** What happened in one level during a run. */
    private class LevelStats {
        var ticks = 0L
        var flashlightTicks = 0L
        var exhaustions = 0
        var batteriesPicked = 0
        var waterPicked = 0
        var drinks = 0
        var batteriesUsed = 0
        var batteriesRanOut = 0
        var heard = 0
        var hits = 0
        /** Cause -> deaths. */
        val deaths = LinkedHashMap<String, Int>()
    }

    private class Run {
        var ticks = 0L
        /** The last level the player was in; stays set between zones. */
        var levelId: String? = null
        val levels = LinkedHashMap<String, LevelStats>()
        var keycardTick: Long? = null
        /** The end of the demo was reached: no more lines until a new game. */
        var ended = false

        // Last seen, to log the changes.
        var exhausted = false
        var exhaustedTick = 0L
        var thirstLow = false
        var thirstEmpty = false

        fun stats(levelId: String? = this.levelId): LevelStats = levels.getOrPut(levelId ?: NO_LEVEL) { LevelStats() }
    }

    // Server thread only.
    private val runs = HashMap<UUID, Run>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = handler.player
            runs.remove(player.uuid)?.let { run -> if (!run.ended && run.ticks > 0) summary(player, run, "left the world") }
        }
        ServerLifecycleEvents.SERVER_STOPPED.register { runs.clear() }
    }

    // ---- Called by the game hooks ----

    /** A new game started for [player] ([NewGame.start]): a run halfway through is summed up first. */
    fun newGame(player: ServerPlayerEntity) {
        runs.remove(player.uuid)?.let { run -> if (!run.ended && run.ticks > 0) summary(player, run, "new game started") }
        val run = Run()
        runs[player.uuid] = run
        log(player, run, "new game")
        logSetup()
    }

    fun pickedUp(player: ServerPlayerEntity, item: Item, pos: BlockPos) {
        val run = runOf(player) ?: return
        val stats = run.stats()
        when (item) {
            ModItems.BATTERY -> stats.batteriesPicked++
            ModItems.WATER_BOTTLE -> stats.waterPicked++
            ModItems.KEYCARD -> run.keycardTick = run.ticks
        }
        log(player, run, "picked up ${nameOf(item)} at ${at(pos)}, now carries ${carried(player)}")
    }

    fun drank(player: ServerPlayerEntity, before: ResourceState, after: ResourceState) {
        val run = runOf(player) ?: return
        run.stats().drinks++
        val max = ConfigManager.gameplay.thirst.max
        log(player, run, "drank: thirst ${num(before.thirst)} -> ${num(after.thirst)}/${num(max)}, ${count(player, ModItems.WATER_BOTTLE)} water left")
    }

    fun batteryChanged(player: ServerPlayerEntity, before: ResourceState, after: ResourceState) {
        val run = runOf(player) ?: return
        run.stats().batteriesUsed++
        val max = ConfigManager.gameplay.flashlight.batteryMaxSeconds
        log(player, run, "changed the battery: ${num(before.battery)} -> ${num(after.battery)}/${num(max)} s, ${count(player, ModItems.BATTERY)} spare")
    }

    fun batteryRanOut(player: ServerPlayerEntity) {
        val run = runOf(player) ?: return
        run.stats().batteriesRanOut++
        log(player, run, "battery ran out, ${count(player, ModItems.BATTERY)} spare")
    }

    /** [paleMan] noticed the player (the "he heard you" stinger), now in [state]. */
    fun heardBy(player: ServerPlayerEntity, paleMan: PaleManEntity, state: String) {
        val run = runOf(player) ?: return
        run.stats().heard++
        log(player, run, "${describe(paleMan)} heard you at ${num(paleMan.distanceTo(player).toDouble(), 1)} blocks: $state")
    }

    /** [paleMan] hit the player for [damage]; [fatal] if it killed them. */
    fun hitBy(player: ServerPlayerEntity, paleMan: PaleManEntity, damage: Double, fatal: Boolean) {
        val run = runOf(player) ?: return
        run.stats().hits++
        val result = if (fatal) "fatal" else "health ${num(player.health.toDouble())}/${num(player.maxHealth.toDouble())}"
        log(player, run, "hit by ${describe(paleMan)} for ${num(damage)}: $result")
    }

    /** [paleMan]'s path towards [target] in [state] does not get there ([problem] says how). */
    fun paleManPath(paleMan: PaleManEntity, state: String, target: Position, problem: String) {
        EveryTunnel.LOGGER.info(
            "{} {} at {}, {} towards ({}, {}, {}): {}", PREFIX, describe(paleMan), at(paleMan.blockPos), state,
            num(target.x, 1), num(target.y, 1), num(target.z, 1), problem,
        )
    }

    /** [paleMan] stopped walking or running ([state]) towards a noise and searches; [reason] says why. */
    fun paleManGaveUp(paleMan: PaleManEntity, state: String, reason: String) {
        EveryTunnel.LOGGER.info("{} {} at {} ends his {}: {}", PREFIX, describe(paleMan), at(paleMan.blockPos), state, reason)
    }

    /** The player died in [levelId] and restarts it ([DeathHooks]). */
    fun died(player: ServerPlayerEntity, levelId: String, source: DamageSource) {
        val run = runOf(player) ?: return
        val stats = run.stats(levelId)
        val cause = causeOf(source)
        stats.deaths[cause] = (stats.deaths[cause] ?: 0) + 1
        val by = (source.attacker as? PaleManEntity)?.let { " (${describe(it)})" } ?: ""
        log(player, run, "died in $levelId: $cause$by | ${resources(player)}")
    }

    /** The player reached the end of the demo ([EndingHooks]). */
    fun endOfDemo(player: ServerPlayerEntity) {
        val run = runOf(player) ?: return
        summary(player, run, "end of demo")
        run.ended = true
    }

    // ---- Tick ----

    private fun tick(server: MinecraftServer) {
        val config = ConfigManager.gameplay
        for (player in server.playerManager.playerList) {
            if (!ResourceHooks.appliesTo(player)) continue
            val run = runOf(player) ?: continue
            run.ticks++
            val levelId = PlayerLocationTracker.locationOf(player.uuid).levelId
            if (levelId != null && levelId != run.levelId) enteredLevel(player, run, levelId)

            val state = ResourceSystem.stateOf(player.uuid, config)
            val stats = run.stats()
            stats.ticks++
            if (state.flashlightOn) stats.flashlightTicks++
            changes(player, run, state, config)
            if (run.ticks % STATUS_TICKS == 0L) log(player, run, "${resources(player)} | at ${at(player.blockPos)} in ${zones(player)}")
        }
    }

    /** Logs exhaustion and thirst changes. Quiet while dying: the restart refills everything. */
    private fun changes(player: ServerPlayerEntity, run: Run, state: ResourceState, config: GameplayConfig) {
        val quiet = DeathHooks.isDying(player)
        val stats = run.stats()

        if (state.exhausted != run.exhausted) {
            run.exhausted = state.exhausted
            if (state.exhausted) {
                stats.exhaustions++
                run.exhaustedTick = run.ticks
                if (!quiet) log(player, run, "exhausted: out of stamina")
            } else if (!quiet) {
                log(player, run, "no longer exhausted, after ${clock(run.ticks - run.exhaustedTick)}")
            }
        }

        val low = state.thirst < config.thirst.max * LOW_THIRST_PERCENT / 100.0
        if (low != run.thirstLow) {
            run.thirstLow = low
            if (!quiet) log(player, run, if (low) "thirst below ${num(LOW_THIRST_PERCENT)}%" else "thirst back above ${num(LOW_THIRST_PERCENT)}%")
        }
        val empty = state.thirst <= 0.0
        if (empty != run.thirstEmpty) {
            run.thirstEmpty = empty
            if (!quiet) log(player, run, if (empty) "thirst empty: losing health" else "thirst no longer empty")
        }
    }

    private fun enteredLevel(player: ServerPlayerEntity, run: Run, levelId: String) {
        val previous = run.levelId
        run.levelId = levelId
        val name = LevelManager.get(levelId)?.name ?: levelId
        if (previous == null) {
            log(player, run, "in $name ($levelId)")
        } else {
            log(player, run, "entered $name ($levelId), after ${clock(run.levels[previous]?.ticks ?: 0)} in $previous")
        }
    }

    // ---- Summary and setup ----

    private fun summary(player: ServerPlayerEntity, run: Run, why: String) {
        val config = ConfigManager.gameplay
        log(player, run, "summary ($why): ${clock(run.ticks)} played")
        for ((levelId, s) in run.levels) {
            val deaths = s.deaths.values.sum()
            val causes = if (deaths == 0) "" else " (" + s.deaths.entries.joinToString { "${it.key} ${it.value}" } + ")"
            log(
                player, run,
                "  $levelId: ${clock(s.ticks)} | deaths $deaths$causes | heard ${s.heard}, hits ${s.hits}" +
                    " | picked up ${s.batteriesPicked} battery, ${s.waterPicked} water | drank ${s.drinks}" +
                    " | batteries changed ${s.batteriesUsed}, ran out ${s.batteriesRanOut}" +
                    " | flashlight on ${clock(s.flashlightTicks)} | exhausted ${s.exhaustions}",
            )
        }
        run.keycardTick?.let { log(player, run, "  keycard taken at ${clock(it)}") }
        log(player, run, "  left: ${resources(player)}")
        // The values this run was played with, next to its results.
        log(player, run, "  battery lasts ${num(config.flashlight.batteryMaxSeconds)} s, thirst drains ${num(config.thirst.drainPerMinute, 1)}/min")
    }

    /** The config in use and what each level holds, so a log carries the values it was played with. */
    private fun logSetup() {
        EveryTunnel.LOGGER.info("{} config: {}", PREFIX, ConfigManager.gameplay)
        for (level in LevelManager.all().sortedBy { it.order }) {
            val loot = LootSpawner.LOOT.keys.joinToString { tag -> "${level.spawnPoints.count { tag in it.tags }} $tag" }
            val paleMen = level.spawnPoints.count { PaleManSpawner.TAG in it.tags }
            EveryTunnel.LOGGER.info("{} map: {} ({}): {}, {} Pale men", PREFIX, level.name, level.id, loot, paleMen)
        }
    }

    // ---- Helpers ----

    /** The player's run, started now if they have none; null once the demo ended (until a new game). */
    private fun runOf(player: ServerPlayerEntity): Run? {
        val run = runs.getOrPut(player.uuid) {
            Run().also { run ->
                log(player, run, "run started (first seen in the world)")
                logSetup()
            }
        }
        return if (run.ended) null else run
    }

    private fun resources(player: ServerPlayerEntity): String {
        val config = ConfigManager.gameplay
        val s = ResourceSystem.stateOf(player.uuid, config)
        val exhausted = if (s.exhausted) " (exhausted)" else ""
        val light = if (s.flashlightOn) " (on)" else ""
        return "thirst ${num(s.thirst)}/${num(config.thirst.max)}" +
            " | stamina ${num(s.stamina)}/${num(config.stamina.max)}$exhausted" +
            " | battery ${num(s.battery)}/${num(config.flashlight.batteryMaxSeconds)} s$light" +
            " | health ${num(player.health.toDouble())}/${num(player.maxHealth.toDouble())}" +
            " | carries ${carried(player)}"
    }

    private fun carried(player: ServerPlayerEntity): String {
        val keycard = if (count(player, ModItems.KEYCARD) > 0) ", keycard" else ""
        return "${count(player, ModItems.BATTERY)} battery, ${count(player, ModItems.WATER_BOTTLE)} water$keycard"
    }

    private fun count(player: ServerPlayerEntity, item: Item): Int = player.inventory.count(item)

    private fun zones(player: ServerPlayerEntity): String =
        PlayerLocationTracker.locationOf(player.uuid).zones.joinToString(", ") { it.zoneId }.ifEmpty { "no zone" }

    private fun describe(paleMan: PaleManEntity): String = "Pale man ${paleMan.id} (${paleMan.homeKey ?: "no home"})"

    private fun causeOf(source: DamageSource): String = when {
        source.attacker is PaleManEntity -> "Pale man"
        source.isOf(DamageTypes.DRY_OUT) -> "thirst"
        else -> source.name
    }

    private fun nameOf(item: Item): String = Registries.ITEM.getId(item).path

    private fun at(pos: BlockPos): String = "(${pos.x}, ${pos.y}, ${pos.z})"

    /** Minutes and seconds of play, e.g. `12:05`. */
    private fun clock(ticks: Long): String {
        val seconds = ticks / 20
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }

    /** A number with [decimals] decimals and a dot, whatever the system language. */
    private fun num(value: Double, decimals: Int = 0): String = String.format(Locale.ROOT, "%.${decimals}f", value)

    private fun log(player: ServerPlayerEntity, run: Run, message: String) {
        EveryTunnel.LOGGER.info("{} {} {}: {}", PREFIX, clock(run.ticks), player.name.string, message)
    }
}
