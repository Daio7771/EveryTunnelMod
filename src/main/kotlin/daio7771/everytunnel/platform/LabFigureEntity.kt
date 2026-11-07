package daio7771.everytunnel.platform

import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.entities.Gaze
import net.minecraft.entity.EntityType
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.MobEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import net.minecraft.world.World

/**
 * The figure seen far off from Level 5's Lobby ([LabFigureHooks]): a person's dark shape at the end of the
 * corridor, looking back. Once a player has looked at it for [SEEN_TICKS] (anywhere near the middle of
 * their view, with nothing in between), or comes within [NEAR] blocks, it turns and walks off round the
 * corner to [exit], and is gone. Nothing touches or hurts it; it is never saved. Drawn far further off than
 * a mob of its size would be.
 */
class LabFigureEntity(type: EntityType<out MobEntity>, world: World) : MobEntity(type, world) {

    /** Where it goes once seen, round the corner; null (summoned by hand): it stays. */
    var exit: Vec3d? = null

    private var seenTicks = 0
    private var leaving = false

    override fun tick() {
        super.tick()
        val world = world as? ServerWorld ?: return
        val exit = exit ?: return
        if (!leaving) {
            val watchers = world.players.filterIsInstance<ServerPlayerEntity>().filter { ResourceHooks.appliesTo(it) }
            if (watchers.any { squaredDistanceTo(it) < NEAR * NEAR }) seenTicks = SEEN_TICKS
            if (watchers.any(::watching)) seenTicks++
            if (seenTicks >= SEEN_TICKS) {
                leaving = true
                LabFigureHooks.seen(world.server)
            }
            return
        }
        val toward = Gaze.yawTowards(Position(x, y, z), Position(exit.x, exit.y, exit.z)).toFloat()
        yaw = toward
        bodyYaw = toward
        headYaw = toward
        if (Vec3d(x, exit.y, z).squaredDistanceTo(exit) < ARRIVED * ARRIVED) discard()
    }

    /** [player] has it near the middle of their view, with nothing in between. */
    private fun watching(player: ServerPlayerEntity): Boolean {
        if (squaredDistanceTo(player) > SIGHT * SIGHT) return false
        val head = Position(x, eyeY, z)
        val eye = player.eyePos
        if (Gaze.angleTo(Position(eye.x, eye.y, eye.z), player.yaw.toDouble(), player.pitch.toDouble(), head) > VIEW_DEGREES) return false
        return world.raycast(RaycastContext(eye, Vec3d(x, eyeY, z), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player)).type ==
            HitResult.Type.MISS
    }

    override fun travel(movementInput: Vec3d) {
        movementSpeed = SPEED
        super.travel(Vec3d(0.0, 0.0, if (leaving) 1.0 else 0.0))
    }

    override fun shouldRender(distance: Double): Boolean = distance < RENDER * RENDER

    override fun damage(source: DamageSource, amount: Float): Boolean = false

    override fun isPushable(): Boolean = false

    override fun cannotDespawn(): Boolean = true

    companion object {
        /** Ticks of being looked at before it goes, and how close a player may come before it goes anyway. */
        private const val SEEN_TICKS = 12
        private const val NEAR = 24.0
        /** How far it is seen from, and how far off the middle of the view it still counts as looked at. */
        private const val SIGHT = 96.0
        private const val VIEW_DEGREES = 30.0
        private const val RENDER = 128.0
        private const val ARRIVED = 0.7
        /** A slow walk, about 2 blocks a second. */
        private const val SPEED = 0.05f

        fun createAttributes(): DefaultAttributeContainer.Builder =
            createMobAttributes().add(EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED.toDouble())
    }
}
