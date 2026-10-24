package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.BlockBox
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.entities.PaleManBrain
import daio7771.everytunnel.systems.NoiseSystem
import net.minecraft.block.BlockState
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
import net.minecraft.registry.Registries
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
import java.util.Locale

/** What the Pale man is doing, as far as his animations care. */
enum class PaleManMode { ROAM, LISTEN, CHASE }

/**
 * The Pale man (Level 2): tall, thin, blind, hunts by hearing.
 *
 * He cannot be hurt by the player (only `/kill` and the void remove him) and never
 * despawns. He is spawned once per `pale_man` spawn point of a level by
 * [PaleManSpawner]; [homeKey] remembers which one, so a killed Pale man can come back.
 *
 * If his spawn point names a `zone`, that is his territory ([territory]): he wanders only
 * inside it and ignores every noise and player outside it, so he never leaves it.
 *
 * He hunts by ear: every tick he hears the noises of `systems.NoiseSystem`, muffled by
 * the solid blocks between him and them, and [PaleManBrain] decides what he does
 * (roam, listen, walk to a noise, chase, search, hit). Settings: `pale_man` in the
 * gameplay config. A player whose noise starts a chase hears the "he heard you"
 * stinger (at most once every [STINGER_COOLDOWN_TICKS]), and one who survives his hit
 * hears ear ringing. His state is synced to clients ([brainState]), which pick the
 * music from it (`client.effects.TensionMusic`).
 *
 * His own sounds come from here: bare footsteps in time with his walk and run
 * animations (not vanilla's distance-based block steps), and breathing, one breath
 * after another, except while he listens or searches: then he holds his breath.
 *
 * Model, animations and texture: `tools/entity/pale_man.py` (GeckoLib). Which animation
 * plays follows [mode] and his movement: listen, run (chasing), walk, or idle.
 */
class PaleManEntity(type: EntityType<out HostileEntity>, world: World) : HostileEntity(type, world), GeoEntity {

    private val animations: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    /** "<level id>/<spawn point id>" of the spawn point he belongs to, or null if summoned by hand. */
    var homeKey: String? = null

    /** The world's new-game count he has seen ([PaleManSpawner.newGames]); -1 = not yet. */
    private var newGamesSeen = -1

    /** Server side only. */
    val brain = PaleManBrain()
    private var pathTarget: Position? = null
    /** The state in which a path that falls short was last reported ([checkPath]), or null. */
    private var pathProblemState: PaleManBrain.State? = null
    private val stingerTicks = HashMap<java.util.UUID, Long>()

    /** Sound timers, server side: ticks into the current step, ticks until the next breath. */
    private var stepClock = 0.0
    private var untilBreath = 0
    private var lastX = 0.0
    private var lastZ = 0.0

    /** What his brain is doing; set on the server, synced to clients. */
    var brainState: PaleManBrain.State
        get() = PaleManBrain.State.entries.getOrElse(dataTracker.get(STATE)) { PaleManBrain.State.ROAM }
        private set(value) = dataTracker.set(STATE, value.ordinal)

    val mode: PaleManMode
        get() = when (brainState) {
            PaleManBrain.State.LISTEN, PaleManBrain.State.SEARCH -> PaleManMode.LISTEN
            PaleManBrain.State.CHASE -> PaleManMode.CHASE
            else -> PaleManMode.ROAM
        }

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(STATE, PaleManBrain.State.ROAM.ordinal)
    }

    override fun initGoals() {
        goalSelector.add(0, SwimGoal(this))
        goalSelector.add(5, object : WanderAroundFarGoal(this, ConfigManager.gameplay.paleMan.roamSpeed) {
            override fun canStart() = brain.state == PaleManBrain.State.ROAM && super.canStart()

            override fun getWanderTarget(): Vec3d? {
                val territory = territory() ?: return super.getWanderTarget()
                repeat(WANDER_TRIES) {
                    val target = super.getWanderTarget()
                    if (target != null && territory.contains(target.x, target.y, target.z)) return target
                }
                return null
            }
        })
    }

    override fun mobTick() {
        super.mobTick()
        val world = world as? ServerWorld ?: return
        val newGames = PaleManSpawner.newGames(world.server)
        if (newGames != newGamesSeen) {
            // A new game started since he last looked (or he was just spawned): back home.
            if (newGamesSeen >= 0) goHome()
            newGamesSeen = newGames
        }
        val config = ConfigManager.gameplay.paleMan
        val now = world.server.ticks.toLong()
        val ears = Vec3d(x, eyeY, z)
        val territory = territory()
        val heard = NoiseSystem.audibleAt(
            world.registryKey.value.toString(), x, y, z, now,
            hearing = config.hearing, maxAgeTicks = HEARING_WINDOW_TICKS,
        ).filter { territory == null || territory.contains(it.x, it.y, it.z) }.mapNotNull { noise ->
            val source = Vec3d(noise.x, noise.y + 1.0, noise.z)
            val loudness = PaleManBrain.loudness(
                source.distanceTo(ears), noise.radius, config.hearing, wallsBetween(source, ears), config.wallMuffle,
            )
            if (loudness > 0.0) PaleManBrain.Heard(Position(noise.x, noise.y, noise.z), loudness, noise.tick, noise.playerId) else null
        }
        val nearest = world.players
            .filter { it is ServerPlayerEntity && NoiseHooks.makesNoise(it) && it.y - y in -2.0..3.0 }
            .filter { territory == null || territory.contains(it.x, it.y, it.z) }
            .minByOrNull { squaredDistanceTo(it) }
            ?.let { PaleManBrain.NearbyPlayer(it.uuid, Math.sqrt(squaredDistanceTo(it))) }

        val before = brain.state
        val targetBefore = brain.target
        val navigationIdle = navigation.isIdle
        val decision = brain.tick(
            PaleManBrain.Senses(now, Position(x, y, z), heard, nearest, navigationIdle), config,
        )
        if (decision.state != before) {
            EveryTunnel.LOGGER.info(
                "Pale man {} at ({}, {}, {}): {} -> {}, target {}, loudest noise {}", id,
                blockX, blockY, blockZ, before, decision.state, decision.target,
                heard.maxOfOrNull { it.loudness }?.let { "%.2f".format(it) } ?: "-",
            )
            pathProblemState = null
            if (decision.state == PaleManBrain.State.SEARCH && targetBefore != null &&
                (before == PaleManBrain.State.CHASE || before == PaleManBrain.State.INVESTIGATE)
            ) {
                PlaytestLog.paleManGaveUp(this, before.name, whyStopped(targetBefore, navigationIdle))
            }
        }

        brainState = decision.state
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
                checkPath(decision.state, target)
            }
        }
        decision.attack?.let { id -> (world.getPlayerByUuid(id) as? ServerPlayerEntity)?.let(::hit) }
        decision.alerted?.let { id -> (world.getPlayerByUuid(id) as? ServerPlayerEntity)?.let { sting(it, now) } }
        footsteps()
        breathe()
    }

    /**
     * His territory: the zone named by his spawn point (`zone` in the level file), or null
     * if he has none (summoned by hand, or a spawn point without a zone). Looked up each
     * time, so a `/reload` of the level files applies at once.
     */
    fun territory(): BlockBox? {
        val key = homeKey ?: return null
        val level = LevelManager.get(key.substringBefore('/')) ?: return null
        if (level.dimension != world.registryKey.value.toString()) return null
        val zoneId = level.spawnPoint(key.substringAfter('/'))?.zone ?: return null
        return level.zone(zoneId)?.box
    }

    /**
     * Playtest log, once per state: the path to [target] does not get there. Says where it
     * ends and what stands in the next block towards the target, from the floor up to his
     * head (he is 2.8 blocks tall, so he needs three free blocks).
     */
    private fun checkPath(state: PaleManBrain.State, target: Position) {
        val path = navigation.currentPath
        if (path != null && path.reachesTarget()) return
        if (pathProblemState == state) return
        pathProblemState = state
        val end = path?.end?.blockPos ?: blockPos
        val short = PaleManBrain.horizontalDistance(Position(end.x + 0.5, end.y.toDouble(), end.z + 0.5), target)
        val problem = if (path == null) "no path at all" else "the path stops %.1f blocks short, at (%d, %d, %d)".format(
            Locale.ROOT, short, end.x, end.y, end.z,
        )
        PlaytestLog.paleManPath(this, state.name, target, "$problem; next block towards it: ${blocksTowards(end, target)}")
    }

    /** The blocks from the floor (y - 1) up to y + 3 one step from [from] towards [target]. */
    private fun blocksTowards(from: BlockPos, target: Position): String {
        val dx = target.x - (from.x + 0.5)
        val dz = target.z - (from.z + 0.5)
        val step = if (Math.abs(dx) >= Math.abs(dz)) BlockPos(Math.signum(dx).toInt(), 0, 0) else BlockPos(0, 0, Math.signum(dz).toInt())
        val next = from.add(step)
        return (-1..3).joinToString(", ") { dy ->
            val state = world.getBlockState(next.up(dy))
            val name = Registries.BLOCK.getId(state.block).path
            val label = if (dy < 0) "floor" else "y+$dy"
            if (state.isAir) "$label air" else "$label $name"
        }
    }

    /** Why a walk or a chase towards [target] ended in a search: there, stuck, or out of time. */
    private fun whyStopped(target: Position, navigationIdle: Boolean): String {
        val distance = PaleManBrain.horizontalDistance(Position(x, y, z), target)
        return when {
            distance <= PaleManBrain.ARRIVE_DISTANCE -> "reached the noise"
            navigationIdle -> "stopped %.1f blocks short of the noise (no way on)".format(Locale.ROOT, distance)
            else -> "ran out of time %.1f blocks from the noise".format(Locale.ROOT, distance)
        }
    }

    /** [returnHome] to his spawn point, if he has one. */
    private fun goHome() {
        val key = homeKey ?: return
        val point = LevelManager.get(key.substringBefore('/'))?.spawnPoint(key.substringAfter('/')) ?: return
        returnHome(point.position.x, point.position.y, point.position.z, point.yaw)
    }

    /** Back at his spawn point, wandering, as if just spawned (a player died and restarts the level). */
    fun returnHome(x: Double, y: Double, z: Double, yaw: Float) {
        navigation.stop()
        pathTarget = null
        brain.reset()
        brainState = PaleManBrain.State.ROAM
        refreshPositionAndAngles(x, y, z, yaw, 0f)
        headYaw = yaw
        velocity = Vec3d.ZERO
        lastX = x
        lastZ = z
        stepClock = 0.0
        EveryTunnel.LOGGER.info("Pale man {} back home at {} ({}, {}, {})", id, homeKey, x, y, z)
    }

    private fun hit(player: ServerPlayerEntity) {
        lookControl.lookAt(player)
        triggerAnim("main", "attack")
        val damage = ConfigManager.gameplay.paleMan.attackDamage
        val hurt = player.damage(damageSources.mobAttack(this), damage.toFloat())
        if (hurt) PlaytestLog.hitBy(player, this, damage, fatal = DeathHooks.isDying(player))
        if (hurt && player.isAlive) player.playSound(ModSounds.TINNITUS, SoundCategory.PLAYERS, 1f, 1f)
    }

    /**
     * One bare footstep every half walk or run cycle while he moves (two per cycle of the
     * animations in `tools/entity/pale_man.py`). Running steps are heavier and carry further.
     */
    private fun footsteps() {
        val moved = Math.hypot(x - lastX, z - lastZ)
        lastX = x
        lastZ = z
        if (!isOnGround || moved < MOVING_PER_TICK) {
            stepClock = 0.0
            return
        }
        val running = brainState == PaleManBrain.State.CHASE
        val interval = if (running) RUN_STEP_TICKS else WALK_STEP_TICKS
        // The first step lands half a step after he sets off.
        if (stepClock == 0.0) stepClock = interval / 2.0
        stepClock += 1.0
        if (stepClock < interval) return
        stepClock -= interval
        world.playSound(
            null, x, y, z, if (running) ModSounds.PALE_MAN_STEP_RUN else ModSounds.PALE_MAN_STEP, SoundCategory.HOSTILE,
            if (running) 1.0f else 0.8f, 0.9f + random.nextFloat() * 0.2f,
        )
    }

    /** A breath (3 to 4 s), a short pause, the next one; none start while he listens or searches. */
    private fun breathe() {
        if (--untilBreath > 0) return
        if (mode == PaleManMode.LISTEN) {
            untilBreath = 10
            return
        }
        world.playSoundFromEntity(null, this, ModSounds.PALE_MAN_BREATHING, SoundCategory.HOSTILE, 1.0f, 0.95f + random.nextFloat() * 0.1f)
        untilBreath = BREATH_TICKS + random.nextInt(BREATH_PAUSE_TICKS)
    }

    // His steps are played by footsteps(), in time with his animations.
    override fun playStepSound(pos: BlockPos, state: BlockState) = Unit

    private fun sting(player: ServerPlayerEntity, now: Long) {
        val last = stingerTicks[player.uuid]
        if (last != null && now - last < STINGER_COOLDOWN_TICKS) return
        stingerTicks[player.uuid] = now
        player.playSound(ModSounds.STINGER_HEARD, SoundCategory.HOSTILE, 1f, 1f)
        PlaytestLog.heardBy(player, this, brain.state.name)
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
        nbt.putInt(NEW_GAMES_KEY, newGamesSeen)
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)
        homeKey = if (nbt.contains(HOME_KEY)) nbt.getString(HOME_KEY) else null
        newGamesSeen = if (nbt.contains(NEW_GAMES_KEY)) nbt.getInt(NEW_GAMES_KEY) else -1
    }

    override fun remove(reason: RemovalReason) {
        // Killed or removed for good (not just unloaded with his chunk): his spawn point may spawn him again.
        if (!world.isClient && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
            homeKey?.let { PaleManSpawner.forget(it) }
        }
        super.remove(reason)
    }

    companion object {
        private val STATE: TrackedData<Int> = DataTracker.registerData(PaleManEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
        private const val HOME_KEY = "EveryTunnelHome"
        private const val NEW_GAMES_KEY = "EveryTunnelNewGames"

        /** Noises are looked at for a couple of ticks; the brain uses each one once. */
        private const val HEARING_WINDOW_TICKS = 2L
        private const val MAX_WALLS = 4
        /** Random wander targets tried before giving up when none falls inside his territory. */
        private const val WANDER_TRIES = 10
        const val STINGER_COOLDOWN_TICKS = 30 * 20L

        /** Half a walk cycle (1.6 s) and half a run cycle (0.55 s), in ticks. */
        private const val WALK_STEP_TICKS = 16.0
        private const val RUN_STEP_TICKS = 5.5
        /** Horizontal blocks per tick below which he counts as standing still. */
        private const val MOVING_PER_TICK = 0.02
        /** The longest breath recording is 3.8 s; then a pause of up to 1.5 s. */
        private const val BREATH_TICKS = 78
        private const val BREATH_PAUSE_TICKS = 30

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
