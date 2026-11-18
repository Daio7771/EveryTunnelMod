package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.core.level.Position
import net.minecraft.block.BlockState
import net.minecraft.entity.EntityType
import net.minecraft.entity.ai.goal.SwimGoal
import net.minecraft.entity.ai.pathing.PathNodeType
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.mob.PathAwareEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.world.World
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.core.animation.AnimationController
import software.bernie.geckolib.core.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import kotlin.math.sqrt

/**
 * The Observant Guard of Chapter 2's Level 2 (phase 5): 2.5 blocks of a man in a dark suit and red bow tie whose cube head is split
 * open round one white point (the user's model, `tools/entity/observant_guard.py`). It breaks out of the vestibule's wall and hunts the
 * player through the vestibule, the service corridor and the maintenance room until the four traps kill it. **Everything it decides is
 * [Ch2L2Guard]'s** ([Ch2L2Guard.drive], every tick): this class only moves (a path to where it is told, at a speed in blocks a second),
 * turns, plays the animation it is told ([anim], synced to clients) and its steps. It cannot be hurt (only `/kill` and the void remove
 * it), is never saved and never despawns.
 */
class ObservantGuardEntity(type: EntityType<out PathAwareEntity>, world: World) : PathAwareEntity(type, world), GeoEntity {

    /** What it looks like doing: the animation each plays (`animation.observant_guard.*`). */
    enum class Anim { IDLE, WALK, CHASE, ATTACK, STUNNED, DEATH, PUNCH, POINT, CHEST }

    private val animations: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    private var stepClock = 0.0
    private var lastX = 0.0
    private var lastZ = 0.0
    private var pathSpeed = 0.0
    private var pathTarget: Position? = null

    var anim: Anim
        get() = Anim.entries.getOrElse(dataTracker.get(ANIM)) { Anim.IDLE }
        set(value) = dataTracker.set(ANIM, value.ordinal)

    init {
        setPathfindingPenalty(PathNodeType.WATER, -1.0f)
        setPathfindingPenalty(PathNodeType.WATER_BORDER, -1.0f)
        setStepHeight(1.0f)
    }

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(ANIM, Anim.IDLE.ordinal)
    }

    override fun initGoals() {
        goalSelector.add(0, SwimGoal(this))
    }

    override fun mobTick() {
        super.mobTick()
        val world = world as? ServerWorld ?: return
        Ch2L2Guard.drive(this, world, world.server.ticks.toLong())
        footsteps()
    }

    // --- moving (told by Ch2L2Guard) ---------------------------------------------------------------------

    /** Walks to [target] at [blocksPerSecond]; the path is asked again as it goes. */
    fun steerTo(target: Position, blocksPerSecond: Double) {
        if (target != pathTarget || blocksPerSecond != pathSpeed) {
            pathTarget = target
            pathSpeed = blocksPerSecond
            repath()
        } else if (age % REPATH_TICKS == 0 || navigation.isIdle) {
            repath()
        }
    }

    private fun repath() {
        val target = pathTarget ?: return
        navigation.startMovingTo(target.x, target.y, target.z, multiplier(pathSpeed))
    }

    fun stand() {
        if (!navigation.isIdle) navigation.stop()
        pathTarget = null
    }

    /** Rigid where it is: no momentum left (a stun, a windup, a death stop it dead, it does not slide on). */
    fun freeze() {
        setVelocity(0.0, velocity.y, 0.0)
        velocityModified = true
    }

    /** Turns its body and head towards [target] while it stands. */
    fun face(target: Position) {
        lookControl.lookAt(target.x, target.y + 1.6, target.z)
        val wanted = (MathHelper.atan2(target.z - z, target.x - x) * MathHelper.DEGREES_PER_RADIAN).toFloat() - 90f
        val turned = MathHelper.stepUnwrappedAngleTowards(bodyYaw, wanted, TURN_PER_TICK)
        bodyYaw = turned
        yaw = turned
    }

    /** The navigation's multiplier for [blocksPerSecond] (see the vibration creature: a mob on plain ground moves at about 44 * s^2 blocks a second). */
    private fun multiplier(blocksPerSecond: Double): Double = sqrt(blocksPerSecond / GROUND_FACTOR) / BASE_SPEED

    /** One stiff step in time with its gait while it moves. */
    private fun footsteps() {
        val moved = Math.hypot(x - lastX, z - lastZ)
        lastX = x
        lastZ = z
        if (!isOnGround || moved < MOVING_PER_TICK || (anim != Anim.WALK && anim != Anim.CHASE)) {
            stepClock = 0.0
            return
        }
        val interval = if (anim == Anim.CHASE) CHASE_STEP_TICKS else WALK_STEP_TICKS
        if (stepClock == 0.0) stepClock = interval / 2.0
        stepClock += 1.0
        if (stepClock < interval) return
        stepClock -= interval
        world.playSound(null, x, y, z, Ch2Sounds.CH2L2_GUARD_STEP, SoundCategory.HOSTILE, 1.0f, 0.92f + random.nextFloat() * 0.16f)
    }

    override fun playStepSound(pos: BlockPos, state: BlockState) = Unit

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "main", TRANSITION_TICKS) { state ->
                state.setAndContinue(
                    when (anim) {
                        Anim.IDLE -> IDLE
                        Anim.WALK -> WALK
                        Anim.CHASE -> CHASE
                        Anim.ATTACK -> ATTACK
                        Anim.STUNNED -> STUNNED
                        Anim.DEATH -> DEATH
                        Anim.PUNCH -> PUNCH
                        Anim.POINT -> POINT
                        Anim.CHEST -> CHEST
                    },
                )
            },
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animations

    override fun isInvulnerableTo(source: DamageSource): Boolean =
        !source.isOf(DamageTypes.OUT_OF_WORLD) && !source.isOf(DamageTypes.GENERIC_KILL)

    override fun canImmediatelyDespawn(distanceSquared: Double): Boolean = false

    override fun isPersistent(): Boolean = true

    override fun isPushable(): Boolean = false

    companion object {
        private val ANIM: TrackedData<Int> = DataTracker.registerData(ObservantGuardEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        private const val BASE_SPEED = 0.3
        /** Blocks a second per (attribute * multiplier)^2 on plain ground (vanilla friction 0.6: s^2 / 0.454 a tick). */
        private const val GROUND_FACTOR = 44.0
        private const val REPATH_TICKS = 5
        private const val TURN_PER_TICK = 14f
        private const val TRANSITION_TICKS = 3
        private const val MOVING_PER_TICK = 0.02

        /** Half a stride of `tools/entity/observant_guard.py`'s walk (1.4 s a stride) and chase (0.9 s), in ticks. */
        private const val WALK_STEP_TICKS = 14.0
        private const val CHASE_STEP_TICKS = 9.0

        val IDLE: RawAnimation = RawAnimation.begin().thenLoop("animation.observant_guard.idle")
        val WALK: RawAnimation = RawAnimation.begin().thenLoop("animation.observant_guard.walk")
        val CHASE: RawAnimation = RawAnimation.begin().thenLoop("animation.observant_guard.chase")
        val ATTACK: RawAnimation = RawAnimation.begin().thenPlay("animation.observant_guard.attack")
        val STUNNED: RawAnimation = RawAnimation.begin().thenLoop("animation.observant_guard.stunned")
        val PUNCH: RawAnimation = RawAnimation.begin().thenPlay("animation.observant_guard.punch")
        val POINT: RawAnimation = RawAnimation.begin().thenPlayAndHold("animation.observant_guard.point")
        val CHEST: RawAnimation = RawAnimation.begin().thenPlay("animation.observant_guard.chest")
        val DEATH: RawAnimation = RawAnimation.begin().thenPlayAndHold("animation.observant_guard.death")

        fun createAttributes(): DefaultAttributeContainer.Builder = MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, BASE_SPEED)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
    }
}
