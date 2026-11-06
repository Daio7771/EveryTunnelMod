package daio7771.everytunnel.platform

import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.entities.Gaze
import daio7771.everytunnel.entities.JuniorClock
import net.minecraft.entity.EntityType
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.core.animation.AnimationController
import software.bernie.geckolib.core.animation.RawAnimation
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Distortion Junior (Level 5): a small Cloud, a ball of black smoke floating at face height where
 * it was put, with the same purple eye, bigger for its size. It never kills.
 *
 * A player within [daio7771.everytunnel.config.JuniorConfig.range] blocks of its eye, with nothing
 * solid in between, is troubled: their camera trembles and drifts away from it (the client's
 * `DistortionEffect` works that out itself), and the Hunters know where they are
 * ([DistortionHooks.mark]). Staring at its eye ([Gaze.onTarget]) for
 * [daio7771.everytunnel.config.JuniorConfig.stareSeconds] without looking away ends it ([JuniorClock]):
 * the eye cracks more and more ([stare], the textures' stages), then it bursts
 * (`entity.distortion_junior.die`) and is gone. The detector does not notice it.
 *
 * Model, animations and textures: `tools/entity/distortion.py` (GeckoLib).
 */
class DistortionJuniorEntity(type: EntityType<*>, world: World) : DistortionEntity(type, world) {

    private val config = ConfigManager.gameplay.junior
    private val clock = JuniorClock.of(config.stareSeconds)

    override val endTicks: Int = 20

    override val smokeChance: Float = 0.25f

    override fun serverTick(world: ServerWorld) {
        val now = world.server.ticks.toLong()
        val eye = eyeCentre()
        val near = watchers(world, config.range + 2).filter { it.eyePos.distanceTo(eye) <= config.range && clearSight(world, it.eyePos, eye) }
        near.minByOrNull { squaredDistanceTo(it) }?.let(::face)
        for (player in near) DistortionHooks.mark(player, now)
        val target = Position(eye.x, eye.y, eye.z)
        val starers = near.filter {
            Gaze.onTarget(Position(it.eyePos.x, it.eyePos.y, it.eyePos.z), it.yaw.toDouble(), it.pitch.toDouble(), target, EYE_RADIUS, config.aimDegrees)
        }.mapTo(HashSet()) { it.uuid }
        val tick = clock.tick(starers)
        stare = tick.stare.toFloat()
        if (tick.done) {
            ending = true
            world.playSound(null, eye.x, eye.y, eye.z, ModSounds.JUNIOR_DIE, SoundCategory.HOSTILE, 1.3f, 1f)
        }
    }

    /** The middle of its eye, a little in front of it, at the height the model puts it. */
    fun eyeCentre(): Vec3d {
        val yaw = Math.toRadians(yaw.toDouble())
        return Vec3d(x - sin(yaw) * EYE_FORWARD, y + EYE_HEIGHT, z + cos(yaw) * EYE_FORWARD)
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "main", 2) { state -> state.setAndContinue(if (ending) DIE else IDLE) })
    }

    companion object {
        /** `tools/entity/distortion.py`: its eye's middle, 25 units up and 3 forward; about 0.2 blocks across. */
        const val EYE_HEIGHT = 25.0 / 16
        const val EYE_FORWARD = 3.0 / 16
        const val EYE_RADIUS = 0.17

        val IDLE: RawAnimation = RawAnimation.begin().thenLoop("animation.distortion_junior.idle")
        val DIE: RawAnimation = RawAnimation.begin().thenPlayAndHold("animation.distortion_junior.die")
    }
}
