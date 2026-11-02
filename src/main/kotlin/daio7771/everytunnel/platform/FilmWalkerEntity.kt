package daio7771.everytunnel.platform

import net.minecraft.entity.EntityType
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.MobEntity
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/**
 * The figure in the Cinema's film ([FilmSetHooks]): he stands until [walking], then walks in a straight
 * line, east, at an even slow pace, looking ahead, until [END_X]. Nothing touches or turns him. He
 * exists only on the set, for filming, and is not saved.
 */
class FilmWalkerEntity(type: EntityType<out MobEntity>, world: World) : MobEntity(type, world) {

    var walking = false

    override fun tick() {
        super.tick()
        yaw = FACING_EAST
        bodyYaw = FACING_EAST
        headYaw = FACING_EAST
        pitch = 0f
        if (walking && x >= END_X) walking = false
    }

    override fun travel(movementInput: Vec3d) {
        movementSpeed = SPEED
        super.travel(Vec3d(0.0, 0.0, if (walking) 1.0 else 0.0))
    }

    override fun damage(source: DamageSource, amount: Float): Boolean = false

    override fun isPushable(): Boolean = false

    override fun cannotDespawn(): Boolean = true

    companion object {
        private const val FACING_EAST = -90f

        /** About 1.5 blocks a second on the floor (a player's walk, 0.1, is about 4.3). */
        private const val SPEED = 0.035f
        private const val END_X = 355.0

        fun createAttributes(): DefaultAttributeContainer.Builder =
            createMobAttributes().add(EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED.toDouble())
    }
}
