package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.BlockBox
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.entities.ObserverBrain
import daio7771.everytunnel.entities.ObserverSettings
import daio7771.everytunnel.systems.ResourceSystem
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EntityType
import net.minecraft.entity.ai.goal.SwimGoal
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
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.core.animation.AnimationController
import software.bernie.geckolib.core.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Observer (Level 3's observant guard): a tall guard in black with a blank grey head. He
 * sees; he does not hear footsteps.
 *
 * He cannot be hurt by the player and never despawns. [ObserverHooks] spawns him once at the
 * level's spawn point tagged `observant_guard` ([homeKey] remembers which); a new game, or the
 * player dying, sends him back there. His home is the zone tagged [HOME_TAG] (the library):
 * he patrols it and goes back to it after anything else.
 *
 * Every tick he looks ([sight]): a player in Survival or Adventure, not hidden in a locker,
 * within his cone ([ObserverSettings.fieldOfView] round where his head faces) and his range
 * (shorter sneaking, longer with the flashlight on), with nothing solid in between, is seen;
 * closer than [ObserverSettings.senseRange] he notices them whatever way he faces. A player
 * he sees get into a locker stays known ([witnessed]) until they come out. Boxes put on the
 * shelf reach him through [ObserverHooks.alertsSince]. [ObserverBrain] decides; this moves him
 * (pathfinding at the brain's pace), turns his head (looking round), and catches: a caught
 * player dies; one in a locker is pulled out first ([HidingHooks.pullOut]).
 *
 * His state is synced to clients ([brainState]) for his animations (and his music).
 * Model, animations and texture: `tools/entity/observer.py` (GeckoLib); 2.5 blocks tall, his
 * hitbox 1.9 like the player's room, so his paths fit wherever the player's do.
 */
class ObserverEntity(type: EntityType<out HostileEntity>, world: World) : HostileEntity(type, world), GeoEntity {

    private val animations: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    /** "<level id>/<spawn point id>" of the spawn point he belongs to, or null if summoned by hand. */
    var homeKey: String? = null

    private var newGamesSeen = -1
    private val settings = ObserverSettings()
    val brain = ObserverBrain(settings)
    private var pathTarget: Position? = null
    private var lastAlertTick = Long.MAX_VALUE
    /** Server side: when he last saw each player, and the lockers he saw players get into. */
    private val lastSeen = HashMap<UUID, Long>()
    /** The run being measured ([measureRun]). */
    private var runTicks = 0
    private var runBlocks = 0.0
    private var runPace = ObserverBrain.Pace.RUN
    private var lastX = 0.0
    private var lastZ = 0.0
    private val witnessed = HashMap<UUID, BlockPos>()

    var brainState: ObserverBrain.State
        get() = ObserverBrain.State.entries.getOrElse(dataTracker.get(STATE)) { ObserverBrain.State.PATROL }
        private set(value) = dataTracker.set(STATE, value.ordinal)

    /** Running (not walking), as far as his animation cares; set on the server, synced to clients. */
    var running: Boolean
        get() = dataTracker.get(RUNNING)
        private set(value) = dataTracker.set(RUNNING, value)

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(STATE, ObserverBrain.State.PATROL.ordinal)
        dataTracker.startTracking(RUNNING, false)
    }

    override fun initGoals() {
        goalSelector.add(0, SwimGoal(this))
    }

    override fun getActiveEyeHeight(pose: EntityPose, dimensions: EntityDimensions): Float = EYE_HEIGHT

    override fun mobTick() {
        super.mobTick()
        val world = world as? ServerWorld ?: return
        val newGames = PaleManSpawner.newGames(world.server)
        if (newGames != newGamesSeen) {
            if (newGamesSeen >= 0) goHome()
            newGamesSeen = newGames
        }
        val now = world.server.ticks.toLong()
        if (lastAlertTick == Long.MAX_VALUE) lastAlertTick = now
        val home = home()
        val alert = ObserverHooks.alertsSince(world, lastAlertTick).lastOrNull()
        if (alert != null) lastAlertTick = alert.tick

        val seen = sight(world, now)
        val hidden = witnessedHidden(world)
        val decision = brain.tick(
            ObserverBrain.Senses(
                now, Position(x, y, z), home?.contains(x, y + 0.5, z) == true, home?.let(::patrolPoint),
                seen, hidden, alert?.position, navigation.isIdle,
            ),
        )
        if (decision.state != brainState) {
            EveryTunnel.LOGGER.info("Observer {} at ({}, {}, {}): {} -> {}, target {}", id, blockX, blockY, blockZ,
                brainState, decision.state, decision.target)
        }
        brainState = decision.state
        val wasRunning = running
        running = decision.pace == ObserverBrain.Pace.RUN || decision.pace == ObserverBrain.Pace.ALERT_RUN
        measureRun(wasRunning, decision.pace)
        move(decision, seen)
        decision.catch?.let { (world.getPlayerByUuid(it) as? ServerPlayerEntity)?.let(::catch) }
        decision.grab?.let { id ->
            witnessed.remove(id)
            (world.getPlayerByUuid(id) as? ServerPlayerEntity)?.let { player ->
                HidingHooks.pullOut(player)
                catch(player)
            }
        }
    }

    /** The log, when he stops running: how far and how fast he really ran (to tune his speeds). */
    private fun measureRun(wasRunning: Boolean, pace: ObserverBrain.Pace) {
        // mobTick runs before this tick's move, so the step measured is the last tick's.
        val step = Math.hypot(x - lastX, z - lastZ)
        lastX = x
        lastZ = z
        if (running) {
            if (!wasRunning) {
                runTicks = 0
                runBlocks = 0.0
                runPace = pace
            }
            runTicks++
            if (wasRunning) runBlocks += step
        } else if (wasRunning && runTicks >= 20) {
            EveryTunnel.LOGGER.info("[observer] ran %.1f blocks in %.1f s: %.1f blocks/s (%s)".format(
                java.util.Locale.ROOT, runBlocks, runTicks / 20.0, runBlocks / (runTicks / 20.0), runPace))
        }
    }

    private fun move(decision: ObserverBrain.Decision, seen: ObserverBrain.Seen?) {
        val target = decision.target
        val speed = when (decision.pace) {
            ObserverBrain.Pace.STAND -> 0.0
            ObserverBrain.Pace.WALK -> settings.walkSpeed
            ObserverBrain.Pace.RUN -> settings.runSpeed
            ObserverBrain.Pace.ALERT_RUN -> settings.alertSpeed
        }
        if (target == null || speed <= 0.0) {
            navigation.stop()
            pathTarget = null
        } else if (target != pathTarget || navigation.isIdle) {
            navigation.startMovingTo(target.x, target.y, target.z, speed)
            pathTarget = target
        }
        when {
            seen != null -> lookControl.lookAt(seen.position.x, seen.position.y + 1.5, seen.position.z, 30f, 30f)
            decision.lookOffset != null -> {
                val yaw = Math.toRadians((bodyYaw + decision.lookOffset).toDouble())
                lookControl.lookAt(x - sin(yaw) * 6, eyeY, z + cos(yaw) * 6, LOOK_TURN_PER_TICK, 30f)
            }
        }
    }

    /** The nearest player he sees this tick, or null. Remembers when he last saw each one. */
    private fun sight(world: ServerWorld, now: Long): ObserverBrain.Seen? {
        val eye = Vec3d(x, eyeY, z)
        var best: ObserverBrain.Seen? = null
        for (player in world.players) {
            if (player !is ServerPlayerEntity || !ResourceHooks.appliesTo(player) || DeathHooks.isDying(player)) continue
            if (HidingHooks.isHidden(player)) continue
            val distance = sqrt(squaredDistanceTo(player))
            val flashlight = ResourceSystem.stateOf(player.uuid, ConfigManager.gameplay).flashlightOn
            val range = when {
                flashlight -> settings.flashlightSightRange
                player.isSneaking -> settings.sneakSightRange
                else -> settings.sightRange
            }
            if (distance > range) continue
            if (distance > settings.senseRange) {
                val toPlayer = Math.toDegrees(atan2(-(player.x - x), player.z - z))
                if (abs(MathHelper.wrapDegrees(toPlayer - headYaw)) > settings.fieldOfView / 2) continue
            }
            if (!clearSight(world, eye, player.eyePos) && !clearSight(world, eye, player.pos.add(0.0, 0.9, 0.0))) continue
            lastSeen[player.uuid] = now
            if (best == null || distance < best.distance) best = ObserverBrain.Seen(player.uuid, Position(player.x, player.y, player.z), distance)
        }
        // Someone he was looking at a moment ago is now in a locker: he saw them get in.
        for (player in world.players) {
            if (player.uuid in witnessed || !HidingHooks.isHidden(player)) continue
            val last = lastSeen[player.uuid] ?: continue
            if (now - last <= WITNESS_TICKS) {
                witnessed[player.uuid] = player.blockPos
                EveryTunnel.LOGGER.info("Observer {} saw {} get into the locker at {}", id, player.name.string, player.blockPos)
            }
        }
        return best
    }

    /** A player he saw get into a locker, still in it; forgets the ones who came out. */
    private fun witnessedHidden(world: ServerWorld): ObserverBrain.Hidden? {
        witnessed.entries.removeIf { (id, _) -> world.getPlayerByUuid(id)?.let { !HidingHooks.isHidden(it) } ?: true }
        val (id, locker) = witnessed.entries.minByOrNull { (_, pos) -> squaredDistanceTo(Vec3d.ofBottomCenter(pos)) }?.toPair() ?: return null
        return ObserverBrain.Hidden(id, Position(locker.x + 0.5, locker.y.toDouble(), locker.z + 0.5),
            sqrt(squaredDistanceTo(Vec3d.ofBottomCenter(locker))))
    }

    private fun clearSight(world: ServerWorld, from: Vec3d, to: Vec3d): Boolean =
        world.raycast(RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this)).type == HitResult.Type.MISS

    /** His home: the level's zone tagged [HOME_TAG], or null (summoned by hand somewhere else). */
    private fun home(): BlockBox? {
        val level = homeKey?.let { LevelManager.get(it.substringBefore('/')) }
            ?: LevelManager.levelAt(world.registryKey.value.toString(), x, y, z) ?: return null
        return level.zones.firstOrNull { HOME_TAG in it.tags }?.box
    }

    /** A random standing place on his home's floor, at his spawn point's height (a few tries). */
    private fun patrolPoint(home: BlockBox): Position? {
        val floorY = homeKey?.let { key -> LevelManager.get(key.substringBefore('/'))?.spawnPoint(key.substringAfter('/'))?.position?.y }
            ?: home.minY.toDouble()
        repeat(PATROL_TRIES) {
            val px = home.minX + random.nextInt(home.maxX - home.minX + 1)
            val pz = home.minZ + random.nextInt(home.maxZ - home.minZ + 1)
            val feet = BlockPos(px, floorY.toInt(), pz)
            if (world.getBlockState(feet).isAir && world.getBlockState(feet.up()).isAir && !world.getBlockState(feet.down()).isAir) {
                return Position(px + 0.5, floorY, pz + 0.5)
            }
        }
        return null
    }

    private fun catch(player: ServerPlayerEntity) {
        lookControl.lookAt(player)
        triggerAnim("main", "attack")
        EveryTunnel.LOGGER.info("Observer {} caught {}", id, player.name.string)
        player.damage(damageSources.mobAttack(this), CATCH_DAMAGE)
    }

    private fun goHome() {
        val key = homeKey ?: return
        val point = LevelManager.get(key.substringBefore('/'))?.spawnPoint(key.substringAfter('/')) ?: return
        returnHome(point.position.x, point.position.y, point.position.z, point.yaw)
    }

    /** Back at his spawn point, patrolling, as if just spawned (a new game, or the player died). */
    fun returnHome(x: Double, y: Double, z: Double, yaw: Float) {
        navigation.stop()
        pathTarget = null
        brain.reset()
        brainState = ObserverBrain.State.PATROL
        running = false
        lastSeen.clear()
        witnessed.clear()
        refreshPositionAndAngles(x, y, z, yaw, 0f)
        headYaw = yaw
        velocity = Vec3d.ZERO
        EveryTunnel.LOGGER.info("Observer {} back home at {} ({}, {}, {})", id, homeKey, x, y, z)
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "main", 4) { state ->
                when {
                    state.isMoving && running -> state.setAndContinue(RUN)
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

    override fun isPersistent(): Boolean = true

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        homeKey?.let { nbt.putString(HOME_KEY, it) }
        nbt.putInt(NEW_GAMES_KEY, newGamesSeen)
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)
        homeKey = if (nbt.contains(HOME_KEY)) nbt.getString(HOME_KEY) else null
        newGamesSeen = if (nbt.contains(NEW_GAMES_KEY)) nbt.getInt(NEW_GAMES_KEY) else -1
    }

    override fun remove(reason: RemovalReason) {
        if (!world.isClient && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
            homeKey?.let { ObserverHooks.forget(it) }
        }
        super.remove(reason)
    }

    companion object {
        private val STATE: TrackedData<Int> = DataTracker.registerData(ObserverEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
        private val RUNNING: TrackedData<Boolean> = DataTracker.registerData(ObserverEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)
        private const val HOME_KEY = "EveryTunnelHome"
        private const val NEW_GAMES_KEY = "EveryTunnelNewGames"

        /** The zone he patrols and goes back to. */
        const val HOME_TAG = "observer_home"
        /** His hitbox ([ModEntities.OBSERVER]); the model stands 2.5 (`tools/entity/observer.py`). */
        const val HITBOX_HEIGHT = 1.9f
        /** His eyes are in the model's blank head, well above the hitbox. */
        private const val EYE_HEIGHT = 2.2f
        /** Seen this recently before getting into a locker counts as seen getting in. */
        private const val WITNESS_TICKS = 10L
        private const val PATROL_TRIES = 12
        /** Degrees per tick his head turns while he looks round. */
        private const val LOOK_TURN_PER_TICK = 6f
        /** Enough to kill anyone (dying is handled by DeathHooks). */
        private const val CATCH_DAMAGE = 1000f

        val IDLE: RawAnimation = RawAnimation.begin().thenLoop("animation.observer.idle")
        val WALK: RawAnimation = RawAnimation.begin().thenLoop("animation.observer.walk")
        val RUN: RawAnimation = RawAnimation.begin().thenLoop("animation.observer.run")
        val ATTACK: RawAnimation = RawAnimation.begin().thenPlay("animation.observer.attack")

        fun createAttributes(): DefaultAttributeContainer.Builder = createHostileAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96.0)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
    }
}
