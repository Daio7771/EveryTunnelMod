package daio7771.everytunnel.chapter1.platform

import daio7771.everytunnel.chapter1.core.WhisperTimer
import daio7771.everytunnel.common.core.LevelManager
import daio7771.everytunnel.common.core.PlayerLocationTracker
import daio7771.everytunnel.common.world.WorldRules
import daio7771.everytunnel.common.core.achievement.Achievement
import daio7771.everytunnel.common.platform.AchievementHooks
import daio7771.everytunnel.common.platform.DeathHooks
import daio7771.everytunnel.common.platform.IntroHooks
import daio7771.everytunnel.common.platform.ModSounds
import daio7771.everytunnel.common.platform.TickProfiler
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.Vec3d
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * Level 1's whisper easter egg: a player who stands still in the parking garage hears, close behind them, a whisper that says
 * nothing ([ModSounds.L1_WHISPER]), and earns [Achievement.WHISPER] with the first. The timing is [WhisperTimer] (five seconds still,
 * a minute between whispers); this only watches who is moving, where Level 1 is (never in the elevator), and plays it. Server thread only.
 */
object WhisperHooks {

    private const val LEVEL_ID = "level_1"

    /** How far behind the player, and how high, the whisper comes from. */
    private const val BEHIND = 1.2
    private const val HEIGHT = 1.6
    private const val VOLUME = 0.8f

    private class Watch(val timer: WhisperTimer = WhisperTimer(), var last: Vec3d? = null)

    private val watches = HashMap<UUID, Watch>()

    fun register() {
        TickProfiler.endServerTick.register(::tick)
    }

    private fun tick(server: MinecraftServer) {
        if (!WorldRules.active) return
        val seen = HashSet<UUID>()
        for (player in server.playerManager.playerList) {
            if (!inLevel(player)) continue
            seen += player.uuid
            val watch = watches.getOrPut(player.uuid) { Watch() }
            val now = player.pos
            val moved = watch.last?.let { it.squaredDistanceTo(now) > WhisperTimer.STILL_BLOCKS * WhisperTimer.STILL_BLOCKS } ?: true
            watch.last = now
            if (watch.timer.tick(moved)) whisper(player)
        }
        // Those who left the level (or the game) are forgotten.
        watches.keys.retainAll(seen)
    }

    private fun inLevel(player: ServerPlayerEntity): Boolean {
        if (!player.isAlive || player.isSpectator || DeathHooks.isDying(player)) return false
        if (LevelManager.levelAt(player.serverWorld.registryKey.value.toString(), player.x, player.y, player.z)?.id != LEVEL_ID) return false
        // Not in the elevator (the intro's start zone): the player waits there, and the count starts anew once they step out.
        return IntroHooks.START_TAG !in LevelManager.tagsOf(PlayerLocationTracker.locationOf(player.uuid).zones)
    }

    private fun whisper(player: ServerPlayerEntity) {
        val yaw = Math.toRadians(player.yaw.toDouble())
        val x = player.x + sin(yaw) * BEHIND
        val z = player.z - cos(yaw) * BEHIND
        player.serverWorld.playSound(null, x, player.y + HEIGHT, z, ModSounds.L1_WHISPER, SoundCategory.AMBIENT, VOLUME, 1f)
        AchievementHooks.earned(player, Achievement.WHISPER)
    }
}
