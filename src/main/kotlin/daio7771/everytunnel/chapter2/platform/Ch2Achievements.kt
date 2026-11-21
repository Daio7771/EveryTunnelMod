package daio7771.everytunnel.chapter2.platform

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter2.core.Ch2Achievement
import daio7771.everytunnel.chapter2.core.StillGaze
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1DataLoader
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2DataLoader
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2Perf
import daio7771.everytunnel.chapter2.prologue.platform.ScientistEntity
import daio7771.everytunnel.common.core.achievement.AchievementRegistry
import daio7771.everytunnel.common.platform.AchievementHooks
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.util.UUID

/**
 * Chapter 2's achievements ([Ch2Achievement], the second tab of the ACHIEVEMENTS screen): registers them with the base game's registry
 * and earns each from the moment that is its own, through `AchievementHooks.earned` (the toast, once per game; the client keeps it for
 * good). Where each is caught:
 *
 * - Level 1: the time clock (a state change), the union's meeting left ([Ch2L1State.ACT2_MEETING] to the next), a guard's catch
 *   ([caught], called by the patrols), the basement recording's PLAY ([RECORDING_PACKET], sent by its window), Chamber 02's occupant looked
 *   at for 5 s without moving ([tick], [StillGaze]) and the coworker's two reminders ([followReminder], called as he says them).
 * - Level 2: no blow from the pump creature by the time the catacombs are reached ([creatureHit], counter `pump_hits`, reset whenever the
 *   level starts before them), five seconds alive after the Guard's death, every room of `level.json`'s `explore` walked into (counters
 *   `seen.<zone>`) and more than two pipes missed in one fight ([pipeMissed], counter `maw_misses`, reset when it begins).
 *
 * The counters are Level 2's own, so they follow its saved game. Server thread only.
 */
object Ch2Achievements {

    /** To the server: the sound event of a recording the player started on a computer of Level 1. */
    val RECORDING_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_recording_played")

    /** The basement terminal's backwards recording (`act3.json`). */
    private const val BASEMENT_RECORDING = "voice.basement_recording"

    private const val PUMP_HITS = "pump_hits"
    private const val MAW_MISSES = "maw_misses"
    private const val SEEN = "seen."
    private const val FOLLOW = "ach.follow."

    /** How many missed pipes are too many: more than this. */
    private const val MISSES_ALLOWED = 2

    private const val GAZE_TICKS = 100
    private const val GAZE_RANGE = 12.0
    private const val FAREWELL_TICKS = 100L
    private const val EXPLORE_EVERY = 5

    private val gazes = HashMap<UUID, StillGaze>()
    private val farewells = HashMap<UUID, Long>()

    fun register() {
        AchievementRegistry.register(Ch2Achievement.entries)

        Ch2L1.onStateChange { player, from, to ->
            if (from == Ch2L1State.ACT1_CLOCKIN && to > from) earn(player, Ch2Achievement.CLOCK_IN)
            if (from == Ch2L1State.ACT2_MEETING && to > from) earn(player, Ch2Achievement.ON_THE_SLY)
        }
        Ch2L2.onStateChange { player, from, to ->
            if (to == Ch2L2State.CATACOMBS && from < to && Ch2Progress.ch2L2Counter(player, PUMP_HITS) == 0) earn(player, Ch2Achievement.WHAT_WAS_THAT)
            if (to == Ch2L2State.GUARD_DEAD && from < to) farewells[player.uuid] = player.server.ticks + FAREWELL_TICKS
        }
        Ch2L2.onStart { player, state ->
            if (state <= Ch2L2State.PUMP_ROOM) Ch2Progress.setCh2L2Counter(player, PUMP_HITS, 0)
            Ch2Progress.setCh2L2Counter(player, MAW_MISSES, 0)
            farewells.remove(player.uuid)
        }
        Ch2L2Perf.register("achievements", ::tick)

        ServerPlayNetworking.registerGlobalReceiver(RECORDING_PACKET) { server, player, _, buf, _ ->
            val sound = buf.readString(64)
            server.execute { recordingPlayed(player, sound) }
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            gazes.remove(handler.player.uuid)
            farewells.remove(handler.player.uuid)
        }
    }

    fun earn(player: ServerPlayerEntity, achievement: Ch2Achievement) = AchievementHooks.earned(player, achievement)

    // --- Level 1 ---------------------------------------------------------------------------------------

    /** A guard on patrol caught [player]. */
    fun caught(player: ServerPlayerEntity) = earn(player, Ch2Achievement.BAD_STEALTH)

    /** The coworker said the reminder [line] to [player], who lags behind him; all of [reminders] heard earns the achievement. */
    fun followReminder(player: ServerPlayerEntity, line: String, reminders: List<String>) {
        Ch2Progress.setCh2L1Counter(player, FOLLOW + line, 1)
        if (reminders.isNotEmpty() && reminders.all { Ch2Progress.ch2L1Counter(player, FOLLOW + it) > 0 }) earn(player, Ch2Achievement.WASTE_OF_TIME)
    }

    private fun recordingPlayed(player: ServerPlayerEntity, sound: String) {
        if (sound != BASEMENT_RECORDING || !Ch2L1.active(player) || Ch2L1.state(player) < Ch2L1State.ACT3_BASEMENT) return
        earn(player, Ch2Achievement.STATIC)
    }

    // --- Level 2 ---------------------------------------------------------------------------------------

    /** The pump creature's blow landed on [player]. */
    fun creatureHit(player: ServerPlayerEntity) {
        Ch2Progress.setCh2L2Counter(player, PUMP_HITS, Ch2Progress.ch2L2Counter(player, PUMP_HITS) + 1)
    }

    /** Stone Maw's fight begins for [player]: no pipe missed yet. */
    fun fightBegan(player: ServerPlayerEntity) {
        Ch2Progress.setCh2L2Counter(player, MAW_MISSES, 0)
    }

    /** A pipe thrown by [player] missed Stone Maw. */
    fun pipeMissed(player: ServerPlayerEntity) {
        val misses = Ch2Progress.ch2L2Counter(player, MAW_MISSES) + 1
        Ch2Progress.setCh2L2Counter(player, MAW_MISSES, misses)
        if (misses > MISSES_ALLOWED) earn(player, Ch2Achievement.BAD_AIM)
    }

    // --- the clock -------------------------------------------------------------------------------------

    private fun tick(server: MinecraftServer) {
        val now = server.ticks.toLong()
        for (player in server.playerManager.playerList) {
            if (!player.isAlive || player.isSpectator) continue
            if (Ch2L1.active(player)) gaze(player)
            if (Ch2L2.active(player)) {
                if (now % EXPLORE_EVERY == 0L) explore(player)
                farewells[player.uuid]?.let { due ->
                    if (now >= due) {
                        farewells.remove(player.uuid)
                        if (Ch2L2.state(player) >= Ch2L2State.GUARD_DEAD) earn(player, Ch2Achievement.SAD_FAREWELL)
                    }
                }
            }
        }
    }

    /** Chamber 02's occupant looked at, still, for 5 s: the player is in the chamber's room with their eyes on him. */
    private fun gaze(player: ServerPlayerEntity) {
        val data = Ch2L1DataLoader.data
        val room = data.anchors.zone("aux_chamber")?.box
        var looking = false
        if (room != null && Ch2L1.state(player) >= Ch2L1State.ACT3_EXPLORE && room.contains(player.x, player.y, player.z)) {
            val eye = player.eyePos
            val look = player.getRotationVec(1f)
            val area = Box.of(player.pos, GAZE_RANGE * 2, GAZE_RANGE * 2, GAZE_RANGE * 2)
            // The Pale man is taller than his hitbox (1.8), so the look is judged by its angle to his chest, not by hitting the box.
            looking = player.serverWorld.getEntitiesByType(Ch2Entities.SCIENTIST, area) { it.stage == ScientistEntity.CONTAINED }.any {
                val to = Vec3d(it.x, it.y + GAZE_TARGET_HEIGHT, it.z).subtract(eye)
                val distance = to.length()
                distance <= GAZE_RANGE && distance > 0.1 && look.dotProduct(to.multiply(1.0 / distance)) >= GAZE_COS
            }
        }
        if (!looking && !gazes.containsKey(player.uuid)) return
        val gaze = gazes.getOrPut(player.uuid) { StillGaze(GAZE_TICKS) }
        if (gaze.tick(looking, player.x, player.y, player.z)) earn(player, Ch2Achievement.FAMILIAR_FACE)
    }

    /** Every room of the level's `explore` list walked into, whichever order: the ones seen are the counters `seen.<zone>`. */
    private fun explore(player: ServerPlayerEntity) {
        val data = Ch2L2DataLoader.data
        val rooms = data.level.explore
        if (rooms.isEmpty() || player.serverWorld.registryKey.value.toString() != data.anchors.dimension) return
        var changed = false
        for (zone in rooms) {
            if (Ch2Progress.ch2L2Counter(player, SEEN + zone) > 0) continue
            if (data.anchors.zone(zone)?.box?.contains(player.x, player.y, player.z) != true) continue
            Ch2Progress.setCh2L2Counter(player, SEEN + zone, 1)
            changed = true
        }
        if (changed && rooms.all { Ch2Progress.ch2L2Counter(player, SEEN + it) > 0 }) earn(player, Ch2Achievement.EXPLORER)
    }

    /** Where on him the look is judged (blocks over his feet), and how near the aim must be: the cosine of 16 degrees. */
    private const val GAZE_TARGET_HEIGHT = 1.5
    private val GAZE_COS = Math.cos(Math.toRadians(16.0))
}
