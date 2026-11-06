package daio7771.everytunnel.platform

import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.entities.CloudClock
import daio7771.everytunnel.entities.Gaze
import net.minecraft.entity.EntityType
import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.core.animation.AnimationController
import software.bernie.geckolib.core.animation.RawAnimation
import kotlin.math.hypot

/**
 * The Distortion Cloud (Level 5): lethal to look at. It appears out of nothing, already whole, in
 * front of a player ([DistortionHooks.appear]), and stays where it is.
 *
 * A player who looks its way, that is, has it anywhere in front of them ([Gaze.inFront]: within 90
 * degrees either side, looking down does not help) with nothing solid between their eyes and some
 * part of it, dies once they have looked for [daio7771.everytunnel.config.CloudConfig.graceSeconds]
 * in a row ([CloudClock]); so does one who walks into it. Turning away and going back the way one came
 * is how to live: once nobody has looked its way for
 * [daio7771.everytunnel.config.CloudConfig.leaveSeconds], it goes (`entity.distortion_cloud.leave`).
 * The detector notices it (it is in the type tag `every_tunnel:radiation_sources`).
 *
 * Model, animations and texture: `tools/entity/distortion.py` (GeckoLib).
 */
class DistortionCloudEntity(type: EntityType<*>, world: World) : DistortionEntity(type, world) {

    private val config = ConfigManager.gameplay.cloud
    private val clock = CloudClock.of(config.graceSeconds, config.leaveSeconds)

    override val endTicks: Int = 24

    override fun serverTick(world: ServerWorld) {
        val players = watchers(world, WATCH_RANGE)
        players.minByOrNull { squaredDistanceTo(it) }?.let(::face)
        val lookers = players.filter { looksAt(world, it) }.mapTo(HashSet()) { it.uuid }
        val tick = clock.tick(lookers)
        stare = tick.stare.toFloat()
        for (player in players) {
            if (player.uuid in tick.kill || touches(player)) kill(player)
        }
        if (tick.leave) leave()
    }

    /** It is in front of [player], with nothing solid between their eyes and some part of it. */
    private fun looksAt(world: ServerWorld, player: ServerPlayerEntity): Boolean {
        if (!Gaze.inFront(Position(player.x, player.y, player.z), player.yaw.toDouble(), Position(x, y, z))) return false
        return SIGHT_HEIGHTS.any { h -> clearSight(world, player.eyePos, Vec3d(x, y + h, z)) }
    }

    private fun touches(player: Entity): Boolean =
        hypot(player.x - x, player.z - z) < config.touchRange && player.y < y + height && player.y + player.height > y

    private fun kill(player: ServerPlayerEntity) {
        world.playSound(null, player.x, player.y, player.z, ModSounds.CLOUD_KILL, SoundCategory.HOSTILE, 1.6f, 1f)
        player.damage(damageSources.indirectMagic(this, this), KILL_DAMAGE)
    }

    /** It goes: its last animation plays, with a sound, then it is gone. */
    fun leave() {
        if (ending) return
        ending = true
        world.playSound(null, x, y + 1.2, z, ModSounds.CLOUD_LEAVE, SoundCategory.HOSTILE, 1.2f, 1f)
    }

    override fun remove(reason: RemovalReason) {
        DistortionHooks.cloudGone(this)
        super.remove(reason)
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "main", 2) { state -> state.setAndContinue(if (ending) LEAVE else IDLE) })
    }

    companion object {
        /** Players farther than this do not count, nor keep it here. */
        private const val WATCH_RANGE = 48.0
        /** Heights over its feet any of which, seen, is seeing it: low, middle, its eye, high. */
        private val SIGHT_HEIGHTS = listOf(0.5, 1.2, 1.6, 2.1)
        /** Enough to kill anyone (dying is handled by DeathHooks). */
        private const val KILL_DAMAGE = 1000f

        val IDLE: RawAnimation = RawAnimation.begin().thenLoop("animation.distortion_cloud.idle")
        val LEAVE: RawAnimation = RawAnimation.begin().thenPlayAndHold("animation.distortion_cloud.leave")
    }
}
