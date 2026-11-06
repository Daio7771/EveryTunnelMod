package daio7771.everytunnel.platform

import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.entities.Gaze
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.util.GeckoLibUtil

/**
 * What the two Distortions of Level 5 ([DistortionCloudEntity], [DistortionJuniorEntity]) share: black
 * clouds with one purple eye, floating where they are. Nothing touches, pushes or hurts them; they
 * turn to face the nearest player. They are never saved ([DistortionHooks] brings them back with the
 * level).
 *
 * Synced to clients: [stare] (how near the longest look at it is to its end, 0 to 1: the glitch grows
 * with it, and the client's effects follow it) and [ending] (it is going: the model's last animation
 * plays for [endTicks], then it is gone). Clients smooth its turning ([renderYaw]) and let off black
 * smoke round it, a burst as it goes.
 */
abstract class DistortionEntity(type: EntityType<*>, world: World) : Entity(type, world), GeoEntity {

    private val animations: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    /** The level it belongs to, if it came with one ([DistortionHooks]). */
    var levelId: String? = null

    var stare: Float
        get() = dataTracker.get(STARE)
        protected set(value) = dataTracker.set(STARE, value)

    var ending: Boolean
        get() = dataTracker.get(ENDING)
        protected set(value) = dataTracker.set(ENDING, value)

    private var endingTicks = 0

    /** Client side: its yaw eased towards the synced one, for drawing. */
    private var renderYawNow = Float.NaN
    private var renderYawBefore = 0f
    private var endedSeen = false

    /** Ticks its last animation takes, before it is gone. */
    protected abstract val endTicks: Int

    init {
        noClip = true
        setNoGravity(true)
    }

    override fun initDataTracker() {
        dataTracker.startTracking(STARE, 0f)
        dataTracker.startTracking(ENDING, false)
    }

    override fun tick() {
        super.tick()
        val world = world
        if (world !is ServerWorld) {
            clientTick()
            return
        }
        if (ending) {
            if (++endingTicks >= endTicks) discard()
            return
        }
        serverTick(world)
    }

    /** One server tick, while it is not going. */
    protected abstract fun serverTick(world: ServerWorld)

    /** The players it can trouble: in Survival or Adventure, not dying, within [range] blocks. */
    protected fun watchers(world: ServerWorld, range: Double): List<ServerPlayerEntity> =
        world.players.filter { it is ServerPlayerEntity && ResourceHooks.appliesTo(it) && !DeathHooks.isDying(it) && squaredDistanceTo(it) <= range * range }

    protected fun face(player: ServerPlayerEntity) {
        yaw = Gaze.yawTowards(Position(x, y, z), Position(player.x, player.y, player.z)).toFloat()
    }

    protected fun clearSight(world: ServerWorld, from: Vec3d, to: Vec3d): Boolean =
        world.raycast(RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this)).type == HitResult.Type.MISS

    /** Smoke: now and then a puff of black round it ([smokeChance] a tick), a burst as it goes. */
    private fun clientTick() {
        if (renderYawNow.isNaN()) renderYawNow = yaw
        renderYawBefore = renderYawNow
        renderYawNow += MathHelper.wrapDegrees(yaw - renderYawNow) * TURN_EASING
        val puffs = when {
            ending && !endedSeen -> 24
            ending -> 0
            random.nextFloat() < smokeChance -> 1
            else -> 0
        }
        if (ending) endedSeen = true
        repeat(puffs) {
            val px = x + (random.nextDouble() - 0.5) * width
            val py = y + random.nextDouble() * height
            val pz = z + (random.nextDouble() - 0.5) * width
            val speed = if (ending) 0.12 else 0.02
            world.addParticle(
                ParticleTypes.SQUID_INK, px, py, pz,
                (random.nextDouble() - 0.5) * speed, random.nextDouble() * speed * 0.5, (random.nextDouble() - 0.5) * speed,
            )
        }
    }

    /** Chance of a puff of smoke each tick. */
    protected open val smokeChance: Float = 0.5f

    /** Its yaw for drawing, eased (client side). */
    fun renderYaw(partialTick: Float): Float =
        if (renderYawNow.isNaN()) yaw else MathHelper.lerpAngleDegrees(partialTick, renderYawBefore, renderYawNow)

    override fun damage(source: DamageSource, amount: Float): Boolean = false

    override fun canHit(): Boolean = false

    override fun isCollidable(): Boolean = false

    override fun isPushable(): Boolean = false

    override fun isAttackable(): Boolean = false

    override fun readCustomDataFromNbt(nbt: NbtCompound) = Unit

    override fun writeCustomDataToNbt(nbt: NbtCompound) = Unit

    override fun createSpawnPacket(): Packet<ClientPlayPacketListener> = EntitySpawnS2CPacket(this)

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animations

    companion object {
        private val STARE: TrackedData<Float> = DataTracker.registerData(DistortionEntity::class.java, TrackedDataHandlerRegistry.FLOAT)
        private val ENDING: TrackedData<Boolean> = DataTracker.registerData(DistortionEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)
        /** Share of the way to its synced yaw it turns each tick, when drawn. */
        private const val TURN_EASING = 0.25f
    }
}
