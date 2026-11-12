package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.cutscene.Action
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
import software.bernie.geckolib.core.animation.AnimationController
import software.bernie.geckolib.core.animation.RawAnimation
import software.bernie.geckolib.core.`object`.PlayState
import software.bernie.geckolib.util.GeckoLibUtil

/**
 * The director, at the top of Level 5: sitting in his chair behind his desk, facing his door, in his tuxedo and
 * the Cinema film's mask. His scene ([DirectorSceneHooks]) plays round him: what he does then comes from the
 * client's scene ([poseSource], set by `client.cutscene.CutsceneView`), and once he has laid his mask on the
 * desk the server marks it ([maskOff]) for good. Nothing touches him; never saved ([DirectorHooks] puts him
 * back with the level). Model, texture and animations: `tools/entity/director.py` (GeckoLib).
 */
class DirectorEntity(type: EntityType<*>, world: World) : Entity(type, world), GeoEntity {

    /** What he does at a moment of his scene; [action] null: breathing in his chair. */
    data class Pose(val action: Action?, val gaze: Double, val maskOff: Boolean)

    private val animations: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    /** The action his animation was last set for (its index; -1 none yet, -2 breathing). */
    private var playing = -1

    init {
        setNoGravity(true)
    }

    var maskOff: Boolean
        get() = dataTracker.get(MASK_OFF)
        set(value) = dataTracker.set(MASK_OFF, value)

    /** How much he looks at the player outside his scene (the server's, [DirectorLoopHooks]: while he says a phrase). */
    var look: Float
        get() = dataTracker.get(LOOK)
        set(value) = dataTracker.set(LOOK, value)

    /** [look], eased on the client so his head turns rather than jumps. */
    private var lookShown = 1.0

    /**
     * His pose now: from the scene while it plays; else breathing, looking at the player while the mask is on, and
     * after it as much as [look] says.
     */
    fun pose(): Pose = poseSource?.invoke() ?: Pose(null, if (maskOff) lookShown else 1.0, maskOff)

    override fun tick() {
        super.tick()
        if (world.isClient) lookShown += (look - lookShown) * LOOK_EASE
    }

    override fun initDataTracker() {
        dataTracker.startTracking(MASK_OFF, false)
        dataTracker.startTracking(LOOK, 0f)
    }

    override fun damage(source: DamageSource, amount: Float): Boolean = false

    override fun canHit(): Boolean = false

    override fun isCollidable(): Boolean = false

    override fun isPushable(): Boolean = false

    override fun readCustomDataFromNbt(nbt: NbtCompound) = Unit

    override fun writeCustomDataToNbt(nbt: NbtCompound) = Unit

    override fun createSpawnPacket(): Packet<ClientPlayPacketListener> = EntitySpawnS2CPacket(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "main", 20) { state ->
            val action = pose().action
            val key = action?.index ?: -2
            if (key != playing) {
                playing = key
                state.controller.transitionLength(action?.blendTicks ?: 20)
                state.controller.forceAnimationReset()
                state.controller.setAnimation(raw(action))
            }
            PlayState.CONTINUE
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animations

    companion object {
        private val MASK_OFF: TrackedData<Boolean> = DataTracker.registerData(DirectorEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)
        private val LOOK: TrackedData<Float> = DataTracker.registerData(DirectorEntity::class.java, TrackedDataHandlerRegistry.FLOAT)

        /** Each tick his eyes go this share of the way to where [look] says. */
        private const val LOOK_EASE = 0.08

        /** The client's scene, while it plays (null otherwise). */
        @JvmStatic
        var poseSource: (() -> Pose?)? = null

        private fun anim(name: String) = "animation.director.$name"

        /** Breathing; a gesture and back to breathing; the mask off and held there; a pose held (standing, pointing). */
        private fun raw(action: Action?): RawAnimation = when {
            action == null || action.name == "idle" -> RawAnimation.begin().thenLoop(anim("idle"))
            action.name == "mask_off" -> RawAnimation.begin().thenPlayAndHold(anim("mask_off"))
            action.loops -> RawAnimation.begin().thenLoop(anim(action.name))
            else -> RawAnimation.begin().thenPlay(anim(action.name)).thenLoop(anim("idle"))
        }
    }
}

/** The director in his chair: at the spawn point tagged [TAG], each time Level 5 is built (kept: [KeptEntities]). */
object DirectorHooks {

    const val TAG = "director"

    fun levelBuilt(server: MinecraftServer, level: LevelDefinition) {
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return
        DirectorSceneHooks.levelBuilt(server)
        KeptEntities.forget { it is DirectorEntity }
        for (entity in world.iterateEntities().toList()) if (entity is DirectorEntity) entity.discard()
        for (point in level.spawnPoints.filter { TAG in it.tags }) {
            KeptEntities.spawn(world) { w ->
                ModEntities.DIRECTOR.create(w)?.apply {
                    refreshPositionAndAngles(point.position.x, point.position.y, point.position.z, point.yaw, 0f)
                    maskOff = DirectorSceneHooks.maskIsDown()
                }
            }
        }
        EveryTunnel.LOGGER.info("Level 5: the director, in his chair")
    }
}
