package daio7771.everytunnel.platform

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.damage.DamageSource
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.world.World
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.core.animation.AnimationController
import software.bernie.geckolib.core.animation.RawAnimation
import software.bernie.geckolib.core.`object`.PlayState
import software.bernie.geckolib.util.GeckoLibUtil

/**
 * The scientist of Chapter 2's prologue ([PrologueSceneHooks]), at his bench in the room built for the scene:
 * a man in a white coat, seen from behind, working with tubes. Life cycle: **passing** (see Contributing,
 * Creatures): [PrologueSceneHooks] spawns him when the scene begins and discards him when it ends; nothing is
 * recorded and he is never saved. Nothing touches him. Model, texture and animations: `tools/entity/scientist.py`
 * (GeckoLib); he works (`animation.scientist.work`) as long as he stands.
 */
class ScientistEntity(type: EntityType<*>, world: World) : Entity(type, world), GeoEntity {

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

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "main", 10) { state ->
            state.controller.setAnimation(RawAnimation.begin().thenLoop("animation.scientist.work"))
            PlayState.CONTINUE
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animations
}
