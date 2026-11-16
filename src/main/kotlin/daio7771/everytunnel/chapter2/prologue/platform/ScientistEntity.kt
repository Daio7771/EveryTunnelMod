package daio7771.everytunnel.chapter2.prologue.platform

import daio7771.everytunnel.chapter2.prologue.core.PrologueStages
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
 * (GeckoLib). His [stage] is the scene's to set (it is synced to the client, which draws him accordingly), one of
 * [PrologueStages]: [WORKING] (`animation.scientist.work`), [TREMBLING] (his hands, `animation.scientist.tremble`),
 * [SICK] (his skin pale, bent over his hands: `scientist_pale.png`, `animation.scientist.sick`), [STRETCHED] (his
 * limbs longer: `animation.scientist.stretch`) and [PALE]: he has become a Pale man, drawn with the Level 2 Pale
 * man's own model, texture and animations (`client.render.ScientistModel`), listening. Chapter 2 Level 1 puts him in its sealed
 * chamber 02 as [CONTAINED]: the Pale man again, not moving at all.
 */
class ScientistEntity(type: EntityType<*>, world: World) : Entity(type, world), GeoEntity {

    private val animations: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    /** The stage whose animation was last set (-1 none yet). */
    private var shown = -1

    /** The client's world time when he became a Pale man (-1: not yet); the renderer unfolds him from it ([unfolded]). */
    private var paleSince = -1L

    init {
        setNoGravity(true)
    }

    /** How far the change has gone: one of [PrologueStages]. */
    var stage: Int
        get() = dataTracker.get(STAGE)
        set(value) = dataTracker.set(STAGE, value)

    /**
     * How far he has risen from the crouch he appears in, 0 (curled up, smaller) to 1 (standing), [partialTick] into
     * this tick: he unfolds over [UNFOLD_TICKS] after becoming a Pale man (client only; 1 before, and when unknown).
     */
    fun unfolded(partialTick: Float): Float {
        if (paleSince < 0L) return 1f
        return ((world.time - paleSince + partialTick) / UNFOLD_TICKS).coerceIn(0f, 1f)
    }

    override fun initDataTracker() {
        dataTracker.startTracking(STAGE, WORKING)
    }

    override fun damage(source: DamageSource, amount: Float): Boolean = false

    override fun canHit(): Boolean = false

    override fun isCollidable(): Boolean = false

    override fun isPushable(): Boolean = false

    override fun readCustomDataFromNbt(nbt: NbtCompound) = Unit

    override fun writeCustomDataToNbt(nbt: NbtCompound) = Unit

    override fun createSpawnPacket(): Packet<ClientPlayPacketListener> = EntitySpawnS2CPacket(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "main", 10) { state ->
            val now = stage
            if (now == CONTAINED) {
                shown = now
                return@AnimationController PlayState.STOP
            }
            if (now != shown) {
                shown = now
                if (now == PALE && world.isClient) paleSince = world.time
                // The Pale man's animations are another model's: no blending from the man's pose into them.
                state.controller.transitionLength(when (now) { PALE -> 0; TREMBLING -> 14; else -> 10 })
                state.controller.forceAnimationReset()
                state.controller.setAnimation(RawAnimation.begin().thenLoop(animation(now)))
            }
            PlayState.CONTINUE
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animations

    companion object {
        const val WORKING = PrologueStages.WORKING
        const val TREMBLING = PrologueStages.TREMBLING
        const val SICK = PrologueStages.SICK
        const val STRETCHED = PrologueStages.STRETCHED
        const val PALE = PrologueStages.PALE
        const val CONTAINED = PrologueStages.CONTAINED

        /** How long he takes to rise to his full height as a Pale man. */
        const val UNFOLD_TICKS = 36

        private val STAGE: TrackedData<Int> = DataTracker.registerData(ScientistEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        private fun animation(stage: Int) = when (stage) {
            PALE -> "animation.pale_man.listen"
            STRETCHED -> "animation.scientist.stretch"
            SICK -> "animation.scientist.sick"
            TREMBLING -> "animation.scientist.tremble"
            else -> "animation.scientist.work"
        }
    }
}
