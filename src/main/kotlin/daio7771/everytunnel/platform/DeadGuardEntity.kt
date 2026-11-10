package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.level.LevelDefinition
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.damage.DamageSource
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
 * The guard before you, dead in Level 5's drain (the Hideout): sitting on the floor against the wall, his head
 * on his chest, his dead flashlight and his note beside him. Never moves; nothing touches him; never saved
 * ([DeadGuardHooks] puts him back with the level). Model and texture: `tools/entity/dead_guard.py` (GeckoLib), on the player's uniform.
 */
class DeadGuardEntity(type: EntityType<*>, world: World) : Entity(type, world), GeoEntity {

    private val animations: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    init {
        setNoGravity(true)
    }

    override fun initDataTracker() = Unit

    override fun damage(source: DamageSource, amount: Float): Boolean = false

    override fun canHit(): Boolean = false

    override fun isCollidable(): Boolean = false

    override fun isPushable(): Boolean = false

    override fun readCustomDataFromNbt(nbt: NbtCompound) = Unit

    override fun writeCustomDataToNbt(nbt: NbtCompound) = Unit

    override fun createSpawnPacket(): Packet<ClientPlayPacketListener> = EntitySpawnS2CPacket(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) = Unit

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animations
}

/** The guard's body in the Hideout: at the spawn point tagged [TAG], each time Level 5 is built. */
object DeadGuardHooks {

    const val TAG = "dead_guard"

    fun levelBuilt(server: MinecraftServer, level: LevelDefinition) {
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return
        for (entity in world.iterateEntities().toList()) if (entity is DeadGuardEntity) entity.discard()
        for (point in level.spawnPoints.filter { TAG in it.tags }) {
            val guard = ModEntities.DEAD_GUARD.create(world) ?: continue
            guard.refreshPositionAndAngles(point.position.x, point.position.y, point.position.z, point.yaw, 0f)
            world.spawnEntity(guard)
        }
        EveryTunnel.LOGGER.info("Level 5: the guard before you, in the Hideout")
    }
}
