package daio7771.everytunnel.platform

import daio7771.everytunnel.core.ch2l1.Ch2L1RoutineRunner
import daio7771.everytunnel.core.ch2l1.Ch2L1Skins
import net.minecraft.entity.EntityType
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.mob.MobEntity
import net.minecraft.world.World

/**
 * A person of Chapter 2's Level 1: Rowan, the coworker, the workers of the union and the faces that fill the factory.
 * One entity type for all of them; what tells them apart is the [skin] (one of [Ch2L1Skins.ALL], synced to the client,
 * which draws the player's model with that skin: `client.render.FactoryNpcRenderer`). Nothing touches them: they take
 * no damage, are not pushed and never despawn.
 *
 * A worker of the factory's ordinary life carries its [workerId], its [group] (the LAB's go away in phase 8) and a routine
 * ([Ch2L1RoutineRunner], from `npcs.json`): each tick on the server it asks the routine what to do and does it, walking
 * straight to the next point of its path (no pathfinding: the move control only), standing, or typing at a station
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
                if (command.snap) refreshPositionAndAngles(command.x, command.y, command.z, yaw, 0f)
                else moveControl.moveTo(command.x, command.y, command.z, walkSpeed)
            }
            is Ch2L1RoutineRunner.Command.Stand -> {
                if (typing != command.typing) typing = command.typing
                command.yaw?.let {
                    yaw = it
                    bodyYaw = it
                    headYaw = it
                }
            }
        }
    }

    override fun damage(source: DamageSource, amount: Float): Boolean = false

    override fun isPushable(): Boolean = false

    override fun cannotDespawn(): Boolean = true

    companion object {
        private val SKIN: TrackedData<String> = DataTracker.registerData(FactoryNpcEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val TYPING: TrackedData<Boolean> = DataTracker.registerData(FactoryNpcEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        /** Their walking speed at `walk_speed` 1: a leisurely stroll (the player's own is 0.1). */
        private const val STROLL = 0.05

        fun createAttributes(): DefaultAttributeContainer.Builder = createMobAttributes().add(EntityAttributes.GENERIC_MOVEMENT_SPEED, STROLL)
    }
}
