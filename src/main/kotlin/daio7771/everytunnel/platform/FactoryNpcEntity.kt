package daio7771.everytunnel.platform

import daio7771.everytunnel.core.ch2l1.Ch2L1Angles
import daio7771.everytunnel.core.ch2l1.Ch2L1Gait
import daio7771.everytunnel.core.ch2l1.Ch2L1Gaze
import daio7771.everytunnel.core.ch2l1.Ch2L1Life
import daio7771.everytunnel.core.ch2l1.Ch2L1Point
import daio7771.everytunnel.core.ch2l1.Ch2L1Pose
import daio7771.everytunnel.core.ch2l1.Ch2L1RoutineRunner
import daio7771.everytunnel.core.ch2l1.Ch2L1Skins
import net.minecraft.entity.EntityType
import net.minecraft.entity.ai.control.BodyControl
import net.minecraft.entity.ai.control.LookControl
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.mob.MobEntity
import net.minecraft.predicate.entity.EntityPredicates
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.Random

/**
 * A person of Chapter 2's Level 1: Rowan, the coworker, the workers of the union and the faces that fill the factory.
 * One entity type for all of them; what tells them apart is the [skin] (one of [Ch2L1Skins.ALL], synced to the client,
 * which draws the player's model with that skin: `client.render.FactoryNpcRenderer`). Nothing touches them: they take
 * no damage, are not pushed and never despawn.
 *
 * A worker of the factory's ordinary life carries its [workerId], its [group] (the LAB's go away in phase 8) and a routine
 * ([Ch2L1RoutineRunner], from `npcs.json`): each tick on the server it asks the routine what to do and does it, walking
 * straight to the next point of its path (no pathfinding: it sets its own velocity, so its pace does not depend on any attribute),
 * standing, or typing at a station. Someone with no routine stands where they were put.
 *
 * How alive they look is [life] (`npcs.json`'s `life`, [Ch2L1Life]): they walk at their own [pace], easing in and braking, turning their
 * body a little each tick ([Ch2L1Gait]); their head looks about on its own ([Ch2L1Gaze]: around, at colleagues, at a player passing close).
 * The body faces [yaw] and the head [headYaw]: vanilla's body and look controls are switched off so that nothing else turns them.
 * While talking to someone ([talkingTo], paired by [Ch2L1Npcs]) the routine waits: they face each other and their [npcPose] says who talks
 * and who listens (synced: the client moves their arms and head).
 *
 * Life cycle: **kept** (see Contributing, Creatures): the type disables saving, the level puts them in through
 * [KeptEntities.spawn] ([Ch2L1Npcs]) and takes them out with [KeptEntities.forget] and `discard()`.
 */
class FactoryNpcEntity(type: EntityType<out MobEntity>, world: World) : MobEntity(type, world) {

    /** The skin's id, always one of [Ch2L1Skins.ALL]. */
    var skin: String
        get() = dataTracker.get(SKIN)
        set(value) = dataTracker.set(SKIN, Ch2L1Skins.orDefault(value))

    /** What the arms and head are doing (client). */
    var npcPose: Ch2L1Pose
        get() = Ch2L1Pose.of(dataTracker.get(POSE))
        set(value) {
            if (dataTracker.get(POSE) != value.ordinal) dataTracker.set(POSE, value.ordinal)
        }

    /** The id this person has in `npcs.json` (null: not one of its workers), and the group they belong to. */
    var workerId: String? = null
    var group: String? = null

    /** What they do, over and over; null: nothing. [pace] is how fast they walk, a multiple of a stroll's [WALK]. */
    var routine: Ch2L1RoutineRunner? = null
    var pace: Double = 1.0

    /** How alive they look, and the seed of their own little randomness (where they look). */
    var life: Ch2L1Life = Ch2L1Life.DEFAULT
    var seed: Long = 0L

    /** Who they are talking to now (null: no one), and the ticks before they may talk again. Server only, set by [Ch2L1Npcs]. */
    var talkingTo: FactoryNpcEntity? = null
    var talkCooldown = 0

    /** What they did on the last tick, for [Ch2L1Npcs] to tell who is free to talk. */
    var mode: Ch2L1Gaze.Mode = Ch2L1Gaze.Mode.STANDING
        private set

    private var gait: Ch2L1Gait? = null
    private var gaze: Ch2L1Gaze? = null

    init {
        lookControl = object : LookControl(this) {
            override fun tick() {}
        }
    }

    override fun createBodyControl(): BodyControl = object : BodyControl(this) {
        override fun tick() {
            bodyYaw = yaw
        }
    }

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(SKIN, Ch2L1Skins.DEFAULT)
        dataTracker.startTracking(POSE, Ch2L1Pose.NONE.ordinal)
    }

    override fun tick() {
        super.tick()
        if (world.isClient) return
        if (talkCooldown > 0) talkCooldown--
        val gait = gait ?: Ch2L1Gait(WALK * pace, life.accelTicks, life.turnDegrees).also { gait = it }
        val partner = talkingTo?.takeIf { !it.isRemoved }
        mode = when {
            partner != null -> {
                stand(gait, Ch2L1Angles.yawOf(partner.x - x, partner.z - z))
                Ch2L1Gaze.Mode.TALKING
            }
            routine != null -> follow(routine!!, gait)
            else -> {
                stand(gait, null)
                Ch2L1Gaze.Mode.STANDING
            }
        }
        look(partner)
    }

    private fun follow(runner: Ch2L1RoutineRunner, gait: Ch2L1Gait): Ch2L1Gaze.Mode =
        when (val command = runner.tick(x, y, z)) {
            is Ch2L1RoutineRunner.Command.MoveTo -> {
                npcPose = Ch2L1Pose.NONE
                if (command.snap) {
                    refreshPositionAndAngles(command.x, command.y, command.z, yaw, pitch)
                    velocity = Vec3d.ZERO
                    gait.stand(yaw, null)
                } else {
                    val dx = command.x - x
                    val dz = command.z - z
                    val step = gait.walk(yaw, dx, dz, if (command.last) Math.sqrt(dx * dx + dz * dz) else null)
                    velocity = Vec3d(step.vx, velocity.y, step.vz)
                    yaw = step.yaw
                }
                Ch2L1Gaze.Mode.WALKING
            }
            is Ch2L1RoutineRunner.Command.Stand -> {
                stand(gait, command.yaw)
                val facing = command.yaw == null || Math.abs(Ch2L1Angles.wrap(command.yaw - yaw)) < 25f
                npcPose = if (command.typing && facing) Ch2L1Pose.TYPING else Ch2L1Pose.NONE
                if (command.typing) Ch2L1Gaze.Mode.TYPING else Ch2L1Gaze.Mode.STANDING
            }
        }

    private fun stand(gait: Ch2L1Gait, face: Float?) {
        velocity = Vec3d(0.0, velocity.y, 0.0)
        yaw = gait.stand(yaw, face)
    }

    /** The head: where [Ch2L1Gaze] says, given the nearest player and the people around. */
    private fun look(partner: FactoryNpcEntity?) {
        val gaze = gaze ?: Ch2L1Gaze(life, Random(seed xor uuid.leastSignificantBits)).also { gaze = it }
        val eyes = Ch2L1Point(x, eyeY, z)
        val player = world.getClosestPlayer(x, eyeY, z, life.playerRange + 1.0, EntityPredicates.EXCEPT_SPECTATOR)
        gaze.tick(
            yaw, eyes, mode,
            player?.let { Ch2L1Point(it.x, it.eyeY, it.z) },
            partner?.let { Ch2L1Point(it.x, it.eyeY, it.z) },
        ) {
            world.getEntitiesByClass(FactoryNpcEntity::class.java, boundingBox.expand(AROUND)) { it !== this }
                .map { Ch2L1Point(it.x, it.eyeY, it.z) }
        }
        headYaw = gaze.headYaw
        pitch = gaze.pitch
    }

    override fun damage(source: DamageSource, amount: Float): Boolean = false

    override fun isPushable(): Boolean = false

    override fun cannotDespawn(): Boolean = true

    companion object {
        private val SKIN: TrackedData<String> = DataTracker.registerData(FactoryNpcEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val POSE: TrackedData<Int> = DataTracker.registerData(FactoryNpcEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /** Blocks a tick at `walk_speed` 1 and a pace of 1: a stroll of 1.3 blocks a second (a player walks 4.3). */
        const val WALK = 0.065

        /** How far around them they look for colleagues to glance at. */
        private const val AROUND = 8.0

        fun createAttributes(): DefaultAttributeContainer.Builder = createMobAttributes()
    }
}
