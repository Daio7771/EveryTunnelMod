package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.chapter2.level1.core.Ch2L1Angles
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Gait
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Gaze
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Life
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Point
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Pose
import daio7771.everytunnel.chapter2.level1.core.Ch2L1RoutineRunner
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Skins
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
 * A scene can take them over ([direction], set by `Ch2L1Act2Hooks`: the coworker leading the player, the meeting): then the routine
 * waits and they walk where it says (opening the doors in their way), face and look where it says, in the pose it says.
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
        set(value) {
            if (field != value) gait = null
            field = value
        }

    /** How alive they look, and the seed of their own little randomness (where they look). */
    var life: Ch2L1Life = Ch2L1Life.DEFAULT
    var seed: Long = 0L

    /** Who they are talking to now (null: no one), and the ticks before they may talk again. Server only, set by [Ch2L1Npcs]. */
    var talkingTo: FactoryNpcEntity? = null
    var talkCooldown = 0

    /** What a scene has them do instead of their own life; null: their own life. Server only. */
    var direction: Direction? = null

    /** What a scene asks of someone (see [direction]); it changes it as the scene goes on. */
    class Direction {
        /** Where they walk to (null: they stand), and whether they stop there (they brake on the way). */
        var walkTo: Vec3d? = null
        var stopThere = true

        /** The way the body faces while they stand; null: towards [lookAt] when that is far off to a side. */
        var face: Float? = null

        /** Where they look (someone's eyes); null: about, on their own. */
        var lookAt: Vec3d? = null
        var pose: Ch2L1Pose = Ch2L1Pose.NONE

        /** True once they have got to [walkTo]. */
        var arrived = false
    }

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
        direction?.let {
            mode = directed(it, gait)
            look(it.lookAt?.let { at -> Ch2L1Point(at.x, at.y, at.z) })
            return
        }
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
        look(partner?.let { Ch2L1Point(it.x, it.eyeY, it.z) })
    }

    /** A tick of what a scene asks ([Direction]). */
    private fun directed(d: Direction, gait: Ch2L1Gait): Ch2L1Gaze.Mode {
        val to = d.walkTo
        if (to != null) {
            val dx = to.x - x
            val dz = to.z - z
            val distance = Math.sqrt(dx * dx + dz * dz)
            if (distance > ARRIVE) {
                d.arrived = false
                val step = gait.walk(yaw, dx, dz, if (d.stopThere) distance else null)
                velocity = Vec3d(step.vx, velocity.y, step.vz)
                yaw = step.yaw
                npcPose = Ch2L1Pose.NONE
                if (age % 5 == 0) Ch2L1Doors.openNear(this)
                return Ch2L1Gaze.Mode.WALKING
            }
            d.arrived = true
        }
        val face = d.face ?: d.lookAt?.let { at ->
            val toward = Ch2L1Angles.yawOf(at.x - x, at.z - z)
            if (Math.abs(Ch2L1Angles.wrap(toward - yaw)) > TURN_TO_LOOK) toward else null
        }
        stand(gait, face)
        npcPose = d.pose
        return if (d.lookAt != null) Ch2L1Gaze.Mode.TALKING else Ch2L1Gaze.Mode.STANDING
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

    /** The head: where [Ch2L1Gaze] says, given the nearest player, the people around and whom they talk to or look at ([partner]'s eyes). */
    private fun look(partner: Ch2L1Point?) {
        val gaze = gaze ?: Ch2L1Gaze(life, Random(seed xor uuid.leastSignificantBits)).also { gaze = it }
        val eyes = Ch2L1Point(x, eyeY, z)
        val player = world.getClosestPlayer(x, eyeY, z, life.playerRange + 1.0, EntityPredicates.EXCEPT_SPECTATOR)
        gaze.tick(
            yaw, eyes, mode,
            player?.let { Ch2L1Point(it.x, it.eyeY, it.z) },
            partner,
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

        /** Within this of where a scene sends them (along the floor) they are there. */
        private const val ARRIVE = 0.3

        /** Standing, they turn their body to what they look at when it is more than this far off to a side, degrees. */
        private const val TURN_TO_LOOK = 50f

        /** How far around them they look for colleagues to glance at. */
        private const val AROUND = 8.0

        fun createAttributes(): DefaultAttributeContainer.Builder = createMobAttributes()
    }
}
