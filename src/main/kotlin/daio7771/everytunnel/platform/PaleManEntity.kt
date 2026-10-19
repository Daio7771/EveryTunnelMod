package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.entities.PaleManBrain
import daio7771.everytunnel.systems.NoiseSystem
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
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
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
 * He hunts by ear: every tick he hears the noises of `systems.NoiseSystem`, muffled by
 * the solid blocks between him and them, and [PaleManBrain] decides what he does
 * (roam, listen, walk to a noise, chase, search, hit). Settings: `pale_man` in the
 * gameplay config. A player whose noise starts a chase hears the "he heard you"
 * stinger (at most once every [STINGER_COOLDOWN_TICKS]).
 *
 * Model, animations and texture: `tools/entity/pale_man.py` (GeckoLib). Which animation
 * plays follows [mode] and his movement: listen, run (chasing), walk, or idle.
 */
class PaleManEntity(type: EntityType<out HostileEntity>, world: World) : HostileEntity(type, world), GeoEntity {

    private val animations: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    /** "<level id>/<spawn point id>" of the spawn point he belongs to, or null if summoned by hand. */
    var homeKey: String? = null

    /** Server side only. */
    val brain = PaleManBrain()
    private var pathTarget: Position? = null
    private val stingerTicks = HashMap<java.util.UUID, Long>()

    var mode: PaleManMode
        get() = PaleManMode.entries.getOrElse(dataTracker.get(MODE)) { PaleManMode.ROAM }
        set(value) = dataTracker.set(MODE, value.ordinal)

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(MODE, PaleManMode.ROAM.ordinal)
    }

    override fun initGoals() {
        goalSelector.add(0, SwimGoal(this))
        goalSelector.add(5, object : WanderAroundFarGoal(this, ConfigManager.gameplay.paleMan.roamSpeed) {
            override fun canStart() = brain.state == PaleManBrain.State.ROAM && super.canStart()
        })
    }

    override fun mobTick() {
        super.mobTick()
        val world = world as? ServerWorld ?: return
        val config = ConfigManager.gameplay.paleMan
        val now = world.server.ticks.toLong()
        val ears = Vec3d(x, eyeY, z)
        val heard = NoiseSystem.audibleAt(
            world.registryKey.value.toString(), x, y, z, now,
            hearing = config.hearing, maxAgeTicks = HEARING_WINDOW_TICKS,
        ).mapNotNull { noise ->
            val source = Vec3d(noise.x, noise.y + 1.0, noise.z)
            val loudness = PaleManBrain.loudness(
                source.distanceTo(ears), noise.radius, config.hearing, wallsBetween(source, ears), config.wallMuffle,
            )
            if (loudness > 0.0) PaleManBrain.Heard(Position(noise.x, noise.y, noise.z), loudness, noise.tick, noise.playerId) else null
        }
        val nearest = world.players
            .filter { it is ServerPlayerEntity && NoiseHooks.makesNoise(it) && it.y - y in -2.0..3.0 }
            .minByOrNull { squaredDistanceTo(it) }
            ?.let { PaleManBrain.NearbyPlayer(it.uuid, Math.sqrt(squaredDistanceTo(it))) }

        val before = brain.state
        val decision = brain.tick(
            PaleManBrain.Senses(now, Position(x, y, z), heard, nearest, navigation.isIdle), config,
        )
        if (decision.state != before) {
            EveryTunnel.LOGGER.info(
                "Pale man {} at ({}, {}, {}): {} -> {}, target {}, loudest noise {}", id,
                blockX, blockY, blockZ, before, decision.state, decision.target,
                heard.maxOfOrNull { it.loudness }?.let { "%.2f".format(it) } ?: "-",
            )
        }

        mode = when (decision.state) {
            PaleManBrain.State.LISTEN, PaleManBrain.State.SEARCH -> PaleManMode.LISTEN
            PaleManBrain.State.CHASE -> PaleManMode.CHASE
            else -> PaleManMode.ROAM
        }
        val target = decision.target
        when {
            decision.state == PaleManBrain.State.ROAM -> pathTarget = null
            target == null -> Unit
            decision.speed <= 0.0 -> {
                navigation.stop()
                pathTarget = null
                lookControl.lookAt(target.x, target.y + 1.0, target.z)
            }
            target != pathTarget -> {
                navigation.startMovingTo(target.x, target.y, target.z, decision.speed)
                pathTarget = target
            }
        }
        decision.attack?.let { id -> (world.getPlayerByUuid(id) as? ServerPlayerEntity)?.let(::hit) }
        decision.alerted?.let { id -> (world.getPlayerByUuid(id) as? ServerPlayerEntity)?.let { sting(it, now) } }
    }

    private fun hit(player: ServerPlayerEntity) {
        lookControl.lookAt(player)
        triggerAnim("main", "attack")
        player.damage(damageSources.mobAttack(this), ConfigManager.gameplay.paleMan.attackDamage.toFloat())
    }

    private fun sting(player: ServerPlayerEntity, now: Long) {
        val last = stingerTicks[player.uuid]
        if (last != null && now - last < STINGER_COOLDOWN_TICKS) return
        stingerTicks[player.uuid] = now
        player.playSound(ModSounds.STINGER_HEARD, SoundCategory.HOSTILE, 1f, 1f)
    }

    /** Solid full blocks crossed by the straight line from [from] to [to], at most [MAX_WALLS]. */
    private fun wallsBetween(from: Vec3d, to: Vec3d): Int {
        val steps = (from.distanceTo(to) * 2).toInt().coerceAtLeast(1)
        val crossed = HashSet<BlockPos>()
        for (i in 1 until steps) {
            val pos = BlockPos.ofFloored(from.lerp(to, i.toDouble() / steps))
            if (pos !in crossed && world.getBlockState(pos).isOpaqueFullCube(world, pos)) {
                crossed += pos
                if (crossed.size >= MAX_WALLS) break
            }
        }
        return crossed.size
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

    // Always persistent, also when summoned or hatched: vanilla wandering stops for good
    // in a mob that could despawn once no player has been near it for a few seconds.
    override fun isPersistent(): Boolean = true

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

        /** Noises are looked at for a couple of ticks; the brain uses each one once. */
        private const val HEARING_WINDOW_TICKS = 2L
        private const val MAX_WALLS = 4
        const val STINGER_COOLDOWN_TICKS = 30 * 20L

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
