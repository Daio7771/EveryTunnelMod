package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.chapter2.platform.Ch2Items
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.projectile.thrown.ThrownItemEntity
import net.minecraft.item.Item
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.world.World

/**
 * A piece of pipe in flight (phase 6.1, [PipePieceItem]): vanilla's thrown item (it flies and falls), with the arc `maw.throw` sets
 * ([getGravity]) and a different end: it hits only Stone Maw in its fight (anyone else is passed through), counts as a hit on it
 * ([Ch2L2Maw.pieceHit]) and is gone; on anything else (a block, the floor) it breaks and clangs ([Ch2L2Maw.pieceBroke]) and is gone.
 * Never saved.
 */
class ThrownPipeEntity : ThrownItemEntity {

    constructor(type: EntityType<ThrownPipeEntity>, world: World) : super(type, world)

    constructor(world: World, owner: LivingEntity) : super(Ch2Entities.THROWN_PIPE, owner, world)

    override fun getDefaultItem(): Item = Ch2Items.PIPE_PIECE

    override fun getGravity(): Float = Ch2L2DataLoader.data.level.maw.throwGravity.toFloat()

    override fun canHit(entity: Entity): Boolean = entity is VibrationCreatureEntity && entity.inFight && super.canHit(entity)

    override fun onCollision(hitResult: HitResult) {
        if (world.isClient) return
        if (hitResult is EntityHitResult && hitResult.entity is VibrationCreatureEntity) {
            Ch2L2Maw.pieceHit(this, hitResult.entity as VibrationCreatureEntity)
        } else {
            Ch2L2Maw.pieceBroke(this)
        }
        discard()
    }
}
