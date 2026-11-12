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
 * The director, at the top of Level 5: sitting in his chair behind his desk, facing his door. His scene
 * ([DirectorSceneHooks]) plays round him. Never moves for now; nothing touches him; never saved
 * ([DirectorHooks] puts him back with the level). Model and texture: `tools/entity/director.py` (GeckoLib); for
 * now a stand-in, a mannequin in chroma green.
 */
class DirectorEntity(type: EntityType<*>, world: World) : Entity(type, world), GeoEntity {

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

/** The director in his chair: at the spawn point tagged [TAG], each time Level 5 is built (kept: [KeptEntities]). */
object DirectorHooks {

    const val TAG = "director"

    fun levelBuilt(server: MinecraftServer, level: LevelDefinition) {
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return
        KeptEntities.forget { it is DirectorEntity }
        for (entity in world.iterateEntities().toList()) if (entity is DirectorEntity) entity.discard()
        for (point in level.spawnPoints.filter { TAG in it.tags }) {
            KeptEntities.spawn(world) { w ->
                ModEntities.DIRECTOR.create(w)?.apply { refreshPositionAndAngles(point.position.x, point.position.y, point.position.z, point.yaw, 0f) }
            }
        }
        DirectorSceneHooks.levelBuilt()
        EveryTunnel.LOGGER.info("Level 5: the director, in his chair")
    }
}
