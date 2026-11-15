package daio7771.everytunnel.platform

import daio7771.everytunnel.core.ch2l1.Ch2L1Skins
import net.minecraft.entity.EntityType
import net.minecraft.entity.attribute.DefaultAttributeContainer
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
 * Life cycle: **kept** (see Contributing, Creatures): the type disables saving, the level puts them in through
 * [KeptEntities.spawn] and takes them out with [KeptEntities.forget] and `discard()`. For now they only stand where
 * `/et ch2 l1 npc` puts them; their routines come with the level's life (a later phase).
 */
class FactoryNpcEntity(type: EntityType<out MobEntity>, world: World) : MobEntity(type, world) {

    /** The skin's id, always one of [Ch2L1Skins.ALL]. */
    var skin: String
        get() = dataTracker.get(SKIN)
        set(value) = dataTracker.set(SKIN, Ch2L1Skins.orDefault(value))

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(SKIN, Ch2L1Skins.DEFAULT)
    }

    override fun damage(source: DamageSource, amount: Float): Boolean = false

    override fun isPushable(): Boolean = false

    override fun cannotDespawn(): Boolean = true

    companion object {
        private val SKIN: TrackedData<String> = DataTracker.registerData(FactoryNpcEntity::class.java, TrackedDataHandlerRegistry.STRING)

        fun createAttributes(): DefaultAttributeContainer.Builder = createMobAttributes()
    }
}
