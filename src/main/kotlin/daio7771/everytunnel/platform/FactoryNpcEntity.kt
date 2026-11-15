package daio7771.everytunnel.platform

import daio7771.everytunnel.core.ch2l1.Ch2L1RoutineRunner
import daio7771.everytunnel.core.ch2l1.Ch2L1Skins
import net.minecraft.entity.EntityType
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.mob.MobEntity
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A person of Chapter 2's Level 1: Rowan, the coworker, the workers of the union and the faces that fill the factory.
 * One entity type for all of them; what tells them apart is the [skin] (one of [Ch2L1Skins.ALL], synced to the client,
 * which draws the player's model with that skin: `client.render.FactoryNpcRenderer`). Nothing touches them: they take
 * no damage, are not pushed and never despawn.
 *
 * A worker of the factory's ordinary life carries its [workerId], its [group] (the LAB's go away in phase 8) and a routine
 * ([Ch2L1RoutineRunner], from `npcs.json`): each tick on the server it asks the routine what to do and does it, walking
 * straight to the next point of its path (no pathfinding: it sets its own velocity, [WALK] blocks a tick, and turns to face the way it goes,
 * so its pace does not depend on any attribute), standing, or typing at a station
 * ([typing], synced: the client puts their arms out). Someone with no routine stands where they were put.
 *
 * Life cycle: **kept** (see Contributing, Creatures): the type disables saving, the level puts them in through
 * [KeptEntities.spawn] ([Ch2L1Npcs]) and takes them out with [KeptEntities.forget] and `discard()`.
 */
class FactoryNpcEntity(type: EntityType<out MobEntity>, world: World) : MobEntity(type, world) {

    /** The skin's id, always one of [Ch2L1Skins.ALL]. */
    var skin: String
        get() = dataTracker.get(SKIN)
        set(value) = dataTracker.set(SKIN, Ch2L1Skins.orDefault(value))

    /** Typing at a station: arms out in front (client). */
    var typing: Boolean
        get() = dataTracker.get(TYPING)
        set(value) = dataTracker.set(TYPING, value)

    /** The id this person has in `npcs.json` (null: not one of its workers), and the group they belong to. */
    var workerId: String? = null
    var group: String? = null

    /** What they do, over and over; null: nothing. [walkSpeed] multiplies how fast they walk. */
    var routine: Ch2L1RoutineRunner? = null
    var walkSpeed: Double = 1.0

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(SKIN, Ch2L1Skins.DEFAULT)
        dataTracker.startTracking(TYPING, false)
    }

    override fun tick() {
        super.tick()
        if (!world.isClient) routine?.let(::follow)
    }

    private fun follow(runner: Ch2L1RoutineRunner) {
        when (val command = runner.tick(x, y, z)) {
            is Ch2L1RoutineRunner.Command.MoveTo -> {
                typing = false
                if (command.snap) {
                    refreshPositionAndAngles(command.x, command.y, command.z, yaw, 0f)
                    velocity = Vec3d.ZERO
                } else {
                    walkToward(command.x, command.z)
                }
            }
            is Ch2L1RoutineRunner.Command.Stand -> {
                velocity = Vec3d(0.0, velocity.y, 0.0)
                if (typing != command.typing) typing = command.typing
                command.yaw?.let {
                    yaw = it
                    bodyYaw = it
                    headYaw = it
                }
            }
        }
    }

    /** A step toward ([x], [z]): the velocity for the next tick, and a turn (at most [TURN] degrees) to face the way they go. */
    private fun walkToward(x: Double, z: Double) {
        val dx = x - this.x
        val dz = z - this.z
        val distance = sqrt(dx * dx + dz * dz)
        if (distance < 1.0e-4) return
        val step = min(WALK * walkSpeed, distance)
        velocity = Vec3d(dx / distance * step, velocity.y, dz / distance * step)
        val heading = (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)).toFloat() - 90f
        yaw += MathHelper.wrapDegrees(heading - yaw).coerceIn(-TURN, TURN)
        bodyYaw = yaw
        headYaw = yaw
    }

    override fun damage(source: DamageSource, amount: Float): Boolean = false

    override fun isPushable(): Boolean = false

    override fun cannotDespawn(): Boolean = true

    companion object {
        private val SKIN: TrackedData<String> = DataTracker.registerData(FactoryNpcEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val TYPING: TrackedData<Boolean> = DataTracker.registerData(FactoryNpcEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        /** Blocks a tick at `walk_speed` 1: a stroll of 1.3 blocks a second (a player walks 4.3). */
        private const val WALK = 0.065

        /** The most they turn in a tick, degrees. */
        private const val TURN = 20f

        fun createAttributes(): DefaultAttributeContainer.Builder = createMobAttributes()
    }
}
