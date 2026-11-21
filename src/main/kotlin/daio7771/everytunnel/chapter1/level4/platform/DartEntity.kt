package daio7771.everytunnel.chapter1.level4.platform

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.ItemStack
import net.minecraft.sound.SoundEvent
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.world.World
import daio7771.everytunnel.common.platform.ModEntities
import daio7771.everytunnel.common.platform.ModItems
import daio7771.everytunnel.common.platform.ModSounds
import daio7771.everytunnel.chapter1.platform.Ch1Entities
import daio7771.everytunnel.chapter1.platform.Ch1Items

/**
 * A thrown dart ([Ch1Items.DART]). Built on vanilla's arrow, which already flies, sticks and shakes.
 * It stays where it lands until [DartHooks] takes it away, cannot be picked up, passes through
 * anyone, and is never saved ([Ch1Entities.DART]).
 */
class DartEntity : PersistentProjectileEntity {

    constructor(type: EntityType<DartEntity>, world: World) : super(type, world)

    constructor(world: World, owner: LivingEntity) : super(Ch1Entities.DART, owner, world)

    init {
        pickupType = PickupPermission.DISALLOWED
    }

    /** Stuck in something, not in the air. */
    val stuck: Boolean
        get() = inGround

    override fun asItemStack(): ItemStack = ItemStack(Ch1Items.DART)

    override fun canHit(entity: Entity): Boolean = false

    override fun getHitSound(): SoundEvent = ModSounds.DART_HIT

    /** Vanilla takes an arrow away after a minute in the ground. */
    override fun age() {}

    override fun onBlockHit(blockHitResult: BlockHitResult) {
        super.onBlockHit(blockHitResult)
        if (!world.isClient) DartHooks.landed(this, blockHitResult)
    }
}
