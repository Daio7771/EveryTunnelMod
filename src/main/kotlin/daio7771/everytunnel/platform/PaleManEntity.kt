package daio7771.everytunnel.platform

import net.minecraft.entity.EntityType
import net.minecraft.entity.ai.goal.SwimGoal
import net.minecraft.entity.ai.goal.WanderAroundFarGoal
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.world.World
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.core.animation.AnimationController
import software.bernie.geckolib.core.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil

/** What the Pale man is doing, as far as his animations care (synced to clients). */
enum class PaleManMode { ROAM, LISTEN, CHASE }

/**
 * The Pale man (Level 2): tall, thin, blind, hunts by hearing.
 *
 * He cannot be hurt by the player (only `/kill` and the void remove him) and never
 * despawns. He is spawned once per `pale_man` spawn point of a level by
 * [PaleManSpawner]; [homeKey] remembers which one, so a killed Pale man can come back.
 *
 * Model, animations and texture: `tools/entity/pale_man.py` (GeckoLib). Which animation
 * plays follows [mode] and his movement: listen, run (chasing), walk, or idle.
 */
class PaleManEntity(type: EntityType<out HostileEntity>, world: World) : HostileEntity(type, world), GeoEntity {

    private val animations: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    /** "<level id>/<spawn point id>" of the spawn point he belongs to, or null if summoned by hand. */
    var homeKey: String? = null

    var mode: PaleManMode
        get() = PaleManMode.entries.getOrElse(dataTracker.get(MODE)) { PaleManMode.ROAM }
        set(value) = dataTracker.set(MODE, value.ordinal)

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(MODE, PaleManMode.ROAM.ordinal)
    }

    override fun initGoals() {
        goalSelector.add(0, SwimGoal(this))
        goalSelector.add(5, WanderAroundFarGoal(this, 0.6))
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "main", 5) { state ->
                when {
                    mode == PaleManMode.LISTEN -> state.setAndContinue(LISTEN)
                    state.isMoving && mode == PaleManMode.CHASE -> state.setAndContinue(RUN)
                    state.isMoving -> state.setAndContinue(WALK)
                    else -> state.setAndContinue(IDLE)
                }
            }.triggerableAnim("attack", ATTACK),
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animations

    override fun isInvulnerableTo(source: DamageSource): Boolean =
        !source.isOf(DamageTypes.OUT_OF_WORLD) && !source.isOf(DamageTypes.GENERIC_KILL)

    override fun canImmediatelyDespawn(distanceSquared: Double): Boolean = false

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        homeKey?.let { nbt.putString(HOME_KEY, it) }
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)
        homeKey = if (nbt.contains(HOME_KEY)) nbt.getString(HOME_KEY) else null
    }

    override fun remove(reason: RemovalReason) {
        // Killed or removed for good (not just unloaded with his chunk): his spawn point may spawn him again.
        if (!world.isClient && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
            homeKey?.let { PaleManSpawner.forget(it) }
        }
        super.remove(reason)
    }

    companion object {
        private val MODE: TrackedData<Int> = DataTracker.registerData(PaleManEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
        private const val HOME_KEY = "EveryTunnelHome"

        val IDLE: RawAnimation = RawAnimation.begin().thenLoop("animation.pale_man.idle")
        val LISTEN: RawAnimation = RawAnimation.begin().thenLoop("animation.pale_man.listen")
        val WALK: RawAnimation = RawAnimation.begin().thenLoop("animation.pale_man.walk")
        val RUN: RawAnimation = RawAnimation.begin().thenLoop("animation.pale_man.run")
        val ATTACK: RawAnimation = RawAnimation.begin().thenPlay("animation.pale_man.attack")

        fun createAttributes(): DefaultAttributeContainer.Builder = createHostileAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
    }
}
