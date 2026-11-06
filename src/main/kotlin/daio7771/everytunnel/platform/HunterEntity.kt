package daio7771.everytunnel.platform

import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.entities.HunterBrain
import net.minecraft.entity.EntityType
import net.minecraft.entity.ai.goal.SwimGoal
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvent
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.core.animation.AnimationController
import software.bernie.geckolib.core.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * A Hunter (Level 5): one of the little ones, black, in a lab coat, with blank white eyes. They come
 * in groups of three ([DistortionHooks] spawns them, all three round one spawn point, their [group]).
 * One blow of the wrench kills one ([daio7771.everytunnel.config.HunterConfig.health]); they hit for
 * half a heart a second each.
 *
 * Every tick one looks ([sight]): a player in Survival or Adventure, not dying nor hidden in a
 * locker, within its range and cone (or close by, whatever way it faces), with nothing solid in
 * between, is seen, and the whole group is told ([DistortionHooks.sighted]). A player a Distortion
 * Junior marks is known to every Hunter about ([DistortionHooks.toldOf]). [HunterBrain] decides;
 * this moves it (pathfinding), turns it to face whom it sees, and hits. It shrieks when a chase
 * starts.
 *
 * Its state is synced to clients ([running]) for the animations. Never saved, like the level it
 * belongs to: a new build, or the player dying, puts the group back ([DistortionHooks]).
 * Model, animations and texture: `tools/entity/hunter.py` (GeckoLib).
 */
class HunterEntity(type: EntityType<out HostileEntity>, world: World) : HostileEntity(type, world), GeoEntity {

    private val animations: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)
    private val config = ConfigManager.gameplay.hunter
    val brain = HunterBrain(config)

    /** Its group's spawn point (or where it was summoned), and the group's key ("level/point"). */
    var home: Position? = null
    var group: String = ""
    /** The level it belongs to, if it came with one ([DistortionHooks]). */
    var levelId: String? = null

    private var pathTarget: Position? = null
    private var wanderPoint: Position? = null
    private var wanderPointTick = 0L

    /** Running (not walking), as far as its animation cares; set on the server, synced to clients. */
    var running: Boolean
        get() = dataTracker.get(RUNNING)
        private set(value) = dataTracker.set(RUNNING, value)

    init {
        experiencePoints = 0
        getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)?.baseValue = config.health
        health = config.health.toFloat()
    }

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(RUNNING, false)
    }

    override fun initGoals() {
        goalSelector.add(0, SwimGoal(this))
    }

    override fun mobTick() {
        super.mobTick()
        val world = world as? ServerWorld ?: return
        val now = world.server.ticks.toLong()
        val home = home ?: Position(x, y, z).also { home = it }
        val seen = sight(world)
        if (seen != null) DistortionHooks.sighted(group, seen, now)
        val told = if (seen == null) DistortionHooks.toldOf(this, now) else null
        val decision = brain.tick(
            HunterBrain.Senses(now, Position(x, y, z), home, wanderPoint(world, home, now), seen, told, navigation.isIdle),
        )
        running = decision.pace == HunterBrain.Pace.RUN
        if (decision.charged) playSound(ModSounds.HUNTER_SHRIEK, 1.4f, 0.9f + random.nextFloat() * 0.25f)
        move(decision)
        (seen ?: told)?.let { lookControl.lookAt(it.position.x, it.position.y + 1.5, it.position.z, 40f, 40f) }
        decision.attack?.let { id -> (world.getPlayerByUuid(id) as? ServerPlayerEntity)?.let(::hit) }
    }

    private fun move(decision: HunterBrain.Decision) {
        val target = decision.target
        val speed = when (decision.pace) {
            HunterBrain.Pace.STAND -> 0.0
            HunterBrain.Pace.WALK -> config.walkSpeed
            HunterBrain.Pace.RUN -> config.runSpeed
        }
        if (target == null || speed <= 0.0) {
            navigation.stop()
            pathTarget = null
        } else if (target != pathTarget || navigation.isIdle) {
            navigation.startMovingTo(target.x, target.y, target.z, speed)
            pathTarget = target
        }
    }

    /** The nearest player it sees this tick, or null. */
    private fun sight(world: ServerWorld): HunterBrain.Contact? {
        val eye = Vec3d(x, eyeY, z)
        var best: HunterBrain.Contact? = null
        for (player in world.players) {
            if (player !is ServerPlayerEntity || !ResourceHooks.appliesTo(player) || DeathHooks.isDying(player)) continue
            if (HidingHooks.isHidden(player)) continue
            val distance = sqrt(squaredDistanceTo(player))
            if (distance > config.sightRange) continue
            if (distance > config.senseRange) {
                val toPlayer = Math.toDegrees(atan2(-(player.x - x), player.z - z))
                if (abs(MathHelper.wrapDegrees(toPlayer - headYaw)) > config.fieldOfView / 2) continue
            }
            if (!clearSight(world, eye, player.eyePos) && !clearSight(world, eye, player.pos.add(0.0, 0.9, 0.0))) continue
            if (best == null || distance < best.distance) best = HunterBrain.Contact(player.uuid, Position(player.x, player.y, player.z), distance)
        }
        return best
    }

    private fun clearSight(world: ServerWorld, from: Vec3d, to: Vec3d): Boolean =
        world.raycast(RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this)).type == HitResult.Type.MISS

    /** A random standing place near [home], looked for only while it wanders and at most twice a second. */
    private fun wanderPoint(world: ServerWorld, home: Position, now: Long): Position? {
        if (brain.state != HunterBrain.State.WANDER || brain.target != null) return null
        if (wanderPoint != null && now - wanderPointTick < WANDER_POINT_TICKS) return wanderPoint
        wanderPointTick = now
        val radius = config.wanderRadius
        wanderPoint = null
        repeat(WANDER_TRIES) {
            val px = home.x + (random.nextDouble() * 2 - 1) * radius
            val pz = home.z + (random.nextDouble() * 2 - 1) * radius
            val feet = BlockPos.ofFloored(px, home.y, pz)
            if (world.getBlockState(feet).getCollisionShape(world, feet).isEmpty &&
                world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty &&
                !world.getBlockState(feet.down()).getCollisionShape(world, feet.down()).isEmpty
            ) {
                wanderPoint = Position(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5)
                return wanderPoint
            }
        }
        return null
    }

    private fun hit(player: ServerPlayerEntity) {
        triggerAnim("main", "attack")
        playSound(ModSounds.HUNTER_ATTACK, 1f, 0.9f + random.nextFloat() * 0.2f)
        player.damage(damageSources.mobAttack(this), config.damage.toFloat())
    }

    /** Back at [home], wandering, as if just spawned. */
    fun returnHome() {
        val home = home ?: return
        navigation.stop()
        pathTarget = null
        brain.reset()
        running = false
        refreshPositionAndAngles(home.x, home.y, home.z, yaw, 0f)
        velocity = Vec3d.ZERO
    }

    override fun getAmbientSound(): SoundEvent? = if (brain.state == HunterBrain.State.WANDER) ModSounds.HUNTER_AMBIENT else null

    override fun getMinAmbientSoundDelay(): Int = AMBIENT_DELAY_TICKS

    override fun getHurtSound(source: DamageSource): SoundEvent = ModSounds.HUNTER_DEATH

    override fun getDeathSound(): SoundEvent = ModSounds.HUNTER_DEATH

    override fun isDisallowedInPeaceful(): Boolean = false

    override fun canImmediatelyDespawn(distanceSquared: Double): Boolean = false

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "main", 3) { state ->
                when {
                    state.isMoving && running -> state.setAndContinue(RUN)
                    state.isMoving -> state.setAndContinue(WALK)
                    else -> state.setAndContinue(IDLE)
                }
            }.triggerableAnim("attack", ATTACK),
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animations

    companion object {
        private val RUNNING: TrackedData<Boolean> = DataTracker.registerData(HunterEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)
        private const val WANDER_TRIES = 8
        private const val WANDER_POINT_TICKS = 10L
        private const val AMBIENT_DELAY_TICKS = 160

        val IDLE: RawAnimation = RawAnimation.begin().thenLoop("animation.hunter.idle")
        val WALK: RawAnimation = RawAnimation.begin().thenLoop("animation.hunter.walk")
        val RUN: RawAnimation = RawAnimation.begin().thenLoop("animation.hunter.run")
        val ATTACK: RawAnimation = RawAnimation.begin().thenPlay("animation.hunter.attack")

        fun createAttributes(): DefaultAttributeContainer.Builder = createHostileAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 6.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96.0)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0)
    }
}
