package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.level.LevelDefinition
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
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.world.World
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.util.GeckoLibUtil

/**
 * A test subject in its sealed chamber, S-1 to S-4 (Level 5's subjects' corridor): a person turning, room by
 * room, into a Pale man ([stage] 1 to 4). Completely still; nothing touches it; never saved ([SubjectHooks]
 * puts it back with the level). Models and textures: `tools/entity/subject.py` (GeckoLib).
 */
class SubjectEntity(type: EntityType<*>, world: World) : Entity(type, world), GeoEntity {

    private val animations: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    var stage: Int
        get() = dataTracker.get(STAGE)
        set(value) = dataTracker.set(STAGE, value.coerceIn(1, STAGES))

    init {
        setNoGravity(true)
    }

    override fun initDataTracker() {
        dataTracker.startTracking(STAGE, 1)
    }

    override fun damage(source: DamageSource, amount: Float): Boolean = false

    override fun canHit(): Boolean = false

    override fun isCollidable(): Boolean = false

    override fun isPushable(): Boolean = false

    override fun readCustomDataFromNbt(nbt: NbtCompound) = Unit

    override fun writeCustomDataToNbt(nbt: NbtCompound) = Unit

    override fun createSpawnPacket(): Packet<ClientPlayPacketListener> = EntitySpawnS2CPacket(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) = Unit

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animations

    companion object {
        const val STAGES = 4
        private val STAGE: TrackedData<Int> = DataTracker.registerData(SubjectEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
    }
}

/** The subjects in their chambers: one at each spawn point tagged [TAG] (`subject_<stage>`), each time Level 5 is built. */
object SubjectHooks {

    const val TAG = "subject"

    fun levelBuilt(server: MinecraftServer, level: LevelDefinition) {
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return
        for (entity in world.iterateEntities().toList()) if (entity is SubjectEntity) entity.discard()
        for (point in level.spawnPoints.filter { TAG in it.tags }) {
            val subject = ModEntities.SUBJECT.create(world) ?: continue
            subject.stage = point.id.substringAfterLast('_').toIntOrNull() ?: 1
            subject.refreshPositionAndAngles(point.position.x, point.position.y, point.position.z, point.yaw, 0f)
            world.spawnEntity(subject)
        }
        EveryTunnel.LOGGER.info("Level 5's subjects in their chambers")
    }
}
