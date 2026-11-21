package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.chapter2.platform.Ch2Achievements
import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Creature
import daio7771.everytunnel.chapter2.level2.core.VibrationCreatureBrain
import daio7771.everytunnel.chapter2.level2.core.VibrationCreatureBrain.Kind
import daio7771.everytunnel.chapter2.level2.core.VibrationCreatureBrain.State
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.common.core.level.Position
import daio7771.everytunnel.common.systems.NoiseSource
import daio7771.everytunnel.common.systems.NoiseSystem
import net.minecraft.block.BlockState
import net.minecraft.entity.EntityType
import net.minecraft.entity.ai.goal.SwimGoal
import net.minecraft.entity.ai.pathing.PathNodeType
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.mob.PathAwareEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.core.animation.AnimationController
import software.bernie.geckolib.core.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import kotlin.math.sqrt

/**
 * The vibration creature of Chapter 2's Level 2 (phases 4 and 4.1): the Stone Maw, 3.5 blocks of cracked grey stone, no eyes, a mouth
 * of long teeth. It lives in the pump room, at rest by the pump ([Ch2L2Pump] puts it there), and feels the floor: every footstep, jump,
 * landing and thing touched by a player on the surfaces of `level.json`'s `creature.floor_zones` (sneaking is a soft vibration, running,
 * jumping and landing loud ones), and the pipe's landing ([Ch2L2Pump.pipeVibration]), as vibrations for [VibrationCreatureBrain], which
 * decides what it does. The pump masks the player's vibrations while it runs: each one carries whether it did when it was made. Its own
 * steps and the pump's sounds are never vibrations (it only reads [NoiseSystem]'s player noises), so it never reacts to itself.
 *
 * Moving: the brain's speeds are blocks a second, turned into the navigation's multiplier ([multiplier]); the path is asked again every
 * [REPATH_TICKS] and when the navigation stops; the way is [VibrationCreatureBrain.Nav.BLOCKED] when no path gets it moving or it makes no
 * progress for [PROGRESS_TICKS] (water is forbidden to it: a point in a channel is struck from its edge). It climbs full blocks.
 *
 * A blow hurts the player by exactly [Ch2L2Creature.damageFraction] of their maximum health (two kill): the health is lowered directly
 * (vanilla damage would be cut by difficulty and armor), a lethal one goes through vanilla damage so that the death sequence
 * ([daio7771.everytunnel.common.common.platform.DeathHooks]) takes it. It cannot be hurt (only `/kill` and the void remove it), is never saved and
 * never despawns. Model, animations and texture: `tools/entity/chapter2/vibration_creature.py` (GeckoLib); the animation follows its brain's
 * state, synced to clients ([brainState]).
 */
class VibrationCreatureEntity(type: EntityType<out PathAwareEntity>, world: World) : PathAwareEntity(type, world), GeoEntity {

    private val animations: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    /** Server side only. */
    val mind = VibrationCreatureBrain()
    private var lastVibrationScan = 0L
    private var stepClock = 0.0
    private var lastX = 0.0
    private var lastZ = 0.0

    private var pathTarget: Position? = null
    private var pathSpeed = 0.0
    private var navBlocked = false
    private var progressFrom: Vec3d = Vec3d.ZERO
    private var progressTicks = 0

    /** Its horizontal speed over the last ticks, in blocks a second (the debug readout). */
    var measuredSpeed = 0.0
        private set

    val brainState: State
        get() = State.entries.getOrElse(dataTracker.get(STATE)) { State.REST }

    /**
     * This is Stone Maw of the arena beyond the exit (phase 6.1): [Ch2L2Maw] runs it (its [StoneMawFight][daio7771.everytunnel.chapter2.level2.core.StoneMawFight]
     * decides, this body acts through the `fight*` functions below), and the pump room's logic ([Ch2L2Pump]) leaves it alone. Server only.
     */
    var inFight = false

    init {
        setPathfindingPenalty(PathNodeType.WATER, -1.0f)
        setPathfindingPenalty(PathNodeType.WATER_BORDER, -1.0f)
        setStepHeight(1.0f)
    }

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(STATE, State.REST.ordinal)
        dataTracker.startTracking(DEATH_POSE, 0)
    }

    override fun initGoals() {
        goalSelector.add(0, SwimGoal(this))
    }

    override fun mobTick() {
        val server = (world as? ServerWorld)?.server
        if (server == null) brainTick() else Ch2L2Perf.time(server, if (inFight) "maw brain" else "creature brain") { brainTick() }
    }

    private fun brainTick() {
        super.mobTick()
        val world = world as? ServerWorld ?: return
        if (inFight) {
            Ch2L2Maw.tickBody(this, world)
            footsteps()
            return
        }
        val data = Ch2L2DataLoader.data
        val config = data.level.creature
        val now = world.server.ticks.toLong()
        val home = data.anchors.point(config.home)?.position ?: Position(x, y, z)

        val before = mind.state
        val decision = mind.tick(
            VibrationCreatureBrain.Senses(
                now, Position(x, y, z), home, vibrations(world, now), nearestPlayer(world),
                if (navBlocked) VibrationCreatureBrain.Nav.BLOCKED else VibrationCreatureBrain.Nav.MOVING,
                Ch2L2Pump.introPending(),
            ),
            config,
        )
        if (decision.state != before) {
            EveryTunnel.LOGGER.info(
                "Vibration creature {} at ({}, {}, {}): {} -> {}, target {}", id, blockX, blockY, blockZ, before, decision.state, decision.target,
            )
        }
        dataTracker.set(STATE, decision.state.ordinal)

        val target = decision.target
        if (target == null || decision.speed <= 0.0) {
            stand()
            if (target != null) face(target)
        } else {
            steer(target, decision.speed)
        }
        if (decision.oriented) sound(Ch2Sounds.CH2L2_CREATURE_CLICK, 1.0f)
        if (decision.windup) sound(Ch2Sounds.CH2L2_CREATURE_WINDUP, 1.2f)
        if (decision.struck) {
            world.playSoundFromEntity(null, this, Ch2Sounds.CH2L2_CREATURE_STRIKE, SoundCategory.HOSTILE, 1.2f, 1.0f)
            decision.hit?.let { id -> (world.getPlayerByUuid(id) as? ServerPlayerEntity)?.let(::hit) }
        }
        if (decision.struckPipe) Ch2L2Pump.pipeStruck()
        footsteps()
    }

    /** The players' noises on the felt floors since the last tick (the arena's, for Stone Maw): the fight adds the clangs of the pipes. */
    internal fun feltNoises(world: ServerWorld, floors: List<daio7771.everytunnel.common.core.level.BlockBox>): List<VibrationCreatureBrain.Vibration> {
        val dimension = world.registryKey.value.toString()
        val out = ArrayList<VibrationCreatureBrain.Vibration>()
        for (noise in NoiseSystem.recent()) {
            if (noise.tick <= lastVibrationScan) break
            if (noise.dimension != dimension || noise.playerId == null) continue
            val kind = KINDS[noise.source] ?: continue
            if (floors.none { it.contains(noise.x, noise.y, noise.z) }) continue
            out += VibrationCreatureBrain.Vibration(Position(noise.x, noise.y, noise.z), noise.tick, VibrationCreatureBrain.Source.PLAYER, kind, playerId = noise.playerId)
        }
        lastVibrationScan = world.server.ticks.toLong() - 1
        return out
    }

    /** The players' noises on the felt floors since the last tick, and the pipe's landing. */
    private fun vibrations(world: ServerWorld, now: Long): List<VibrationCreatureBrain.Vibration> {
        val data = Ch2L2DataLoader.data
        val dimension = world.registryKey.value.toString()
        val floors = data.level.creature.floorZones.mapNotNull { data.anchors.zone(it)?.box }
        val out = ArrayList<VibrationCreatureBrain.Vibration>()
        for (noise in NoiseSystem.recent()) {
            if (noise.tick <= lastVibrationScan) break
            if (noise.dimension != dimension || noise.playerId == null) continue
            val kind = KINDS[noise.source] ?: continue
            if (floors.none { it.contains(noise.x, noise.y, noise.z) }) continue
            out += VibrationCreatureBrain.Vibration(
                Position(noise.x, noise.y, noise.z), noise.tick, VibrationCreatureBrain.Source.PLAYER, kind,
                masked = Ch2L2Pump.masks(noise.tick), playerId = noise.playerId,
            )
        }
        lastVibrationScan = now - 1
        Ch2L2Pump.pipeVibration()?.let { out += it }
        return out
    }

    private fun nearestPlayer(world: ServerWorld): VibrationCreatureBrain.NearbyPlayer? = nearestActivePlayer(world)?.let { VibrationCreatureBrain.NearbyPlayer(it.uuid, Math.hypot(it.x - x, it.z - z)) }

    /** The nearest player playing the level, alive and about its height. */
    internal fun nearestActivePlayer(world: ServerWorld): ServerPlayerEntity? = world.players
        .filterIsInstance<ServerPlayerEntity>()
        .filter { it.isAlive && !it.isSpectator && Ch2L2.active(it) && it.y - y in -2.0..4.0 }
        .minByOrNull { Math.hypot(it.x - x, it.z - z) }

    // --- moving -------------------------------------------------------------------------------------

    /** Goes to [target] at [blocksPerSecond]; finds out whether the way is blocked. */
    private fun steer(target: Position, blocksPerSecond: Double) {
        if (target != pathTarget || blocksPerSecond != pathSpeed) {
            pathTarget = target
            pathSpeed = blocksPerSecond
            navBlocked = false
            progressFrom = pos
            progressTicks = 0
            repath()
        } else if (age % REPATH_TICKS == 0 || navigation.isIdle) {
            repath()
        }
        if (++progressTicks >= PROGRESS_TICKS) {
            if (pos.subtract(progressFrom).horizontalLength() < MIN_PROGRESS) navBlocked = true
            progressFrom = pos
            progressTicks = 0
        }
    }

    private fun repath() {
        val target = pathTarget ?: return
        val moving = navigation.startMovingTo(target.x, target.y, target.z, multiplier(pathSpeed))
        // No path, or one that ends where it stands (the nearest it can get): it can come no closer.
        if (!moving || navigation.isIdle) navBlocked = true
    }

    private fun stand() {
        if (!navigation.isIdle) navigation.stop()
        pathTarget = null
        navBlocked = false
    }

    /** Turns its body and head towards [target] while it stands. */
    private fun face(target: Position) {
        lookControl.lookAt(target.x, target.y + 1.5, target.z)
        val wanted = (MathHelper.atan2(target.z - z, target.x - x) * MathHelper.DEGREES_PER_RADIAN).toFloat() - 90f
        val turned = MathHelper.stepUnwrappedAngleTowards(bodyYaw, wanted, TURN_PER_TICK)
        bodyYaw = turned
        yaw = turned
    }

    /** The navigation's multiplier for [blocksPerSecond]: a mob on plain ground moves at about 44 * s^2 blocks a second, s = attribute * multiplier. */
    private fun multiplier(blocksPerSecond: Double): Double = sqrt(blocksPerSecond / GROUND_FACTOR) / BASE_SPEED

    // --- the blow and its sounds ---------------------------------------------------------------------

    /** The blow connects: exactly the configured share of the player's maximum health (see the class). */
    private fun hit(player: ServerPlayerEntity) {
        val fraction = Ch2L2DataLoader.data.level.creature.damageFraction
        val loss = (player.maxHealth * fraction).toFloat()
        val lethal = player.health - loss <= 0.0f
        if (!inFight) Ch2Achievements.creatureHit(player)
        if (lethal) {
            player.damage(damageSources.mobAttack(this), LETHAL_DAMAGE)
        } else {
            player.health = player.health - loss
            player.world.sendEntityStatus(player, HURT_STATUS)
            val away = Vec3d(player.x - x, 0.0, player.z - z).normalize().multiply(KNOCKBACK)
            player.addVelocity(away.x, 0.25, away.z)
            player.velocityModified = true
        }
        EveryTunnel.LOGGER.info(
            "Vibration creature {} hits {} for {} of {}: {}", id, player.gameProfile.name, loss, player.maxHealth,
            if (lethal) "fatal" else "health ${player.health}",
        )
    }

    private fun sound(event: SoundEvent, volume: Float) =
        world.playSoundFromEntity(null, this, event, SoundCategory.HOSTILE, volume, 0.95f + random.nextFloat() * 0.1f)

    /** One heavy step in time with its gait while it moves (its own sound, never a vibration); soft while stalking. */
    private fun footsteps() {
        val moved = Math.hypot(x - lastX, z - lastZ)
        lastX = x
        lastZ = z
        measuredSpeed += (moved * 20.0 - measuredSpeed) / SPEED_SMOOTHING
        if (!isOnGround || moved < MOVING_PER_TICK) {
            stepClock = 0.0
            return
        }
        val interval = when (brainState) {
            State.CHARGE -> CHARGE_STEP_TICKS
            State.STALK -> STALK_STEP_TICKS
            else -> WALK_STEP_TICKS
        }
        if (stepClock == 0.0) stepClock = interval / 2.0
        stepClock += 1.0
        if (stepClock < interval) return
        stepClock -= interval
        val volume = if (brainState == State.STALK) 0.5f else 1.0f
        world.playSound(null, x, y, z, Ch2Sounds.CH2L2_CREATURE_STEP, SoundCategory.HOSTILE, volume, 0.9f + random.nextFloat() * 0.2f)
    }

    // --- Stone Maw's body (phase 6.1): what [Ch2L2Maw] asks of it ------------------------------------------------

    /** Goes to [target] at [blocksPerSecond] by the navigation; true if it cannot get closer. */
    internal fun fightSteer(target: Position, blocksPerSecond: Double): Boolean {
        steer(target, blocksPerSecond)
        return navBlocked
    }

    /** Stands still, facing [target] if given. */
    internal fun fightStand(target: Position?) {
        stand()
        if (target != null) face(target)
    }

    /** Charges in a straight line along [heading] (a unit vector, x and z) at [blocksPerSecond], with no pathfinding: it stops only at a wall ([horizontalCollision]). */
    internal fun fightRam(heading: Pair<Double, Double>, blocksPerSecond: Double) {
        if (!navigation.isIdle) navigation.stop()
        pathTarget = null
        val step = blocksPerSecond / 20.0
        setVelocity(heading.first * step, velocity.y, heading.second * step)
        velocityModified = true
        val turned = (MathHelper.atan2(heading.second, heading.first) * MathHelper.DEGREES_PER_RADIAN).toFloat() - 90f
        yaw = turned
        bodyYaw = turned
        headYaw = turned
    }

    /** The pose its brain state's animation shows. */
    internal fun fightPose(state: State) {
        if (dataTracker.get(STATE) != state.ordinal) dataTracker.set(STATE, state.ordinal)
    }

    /** Turns its body and head, a step a tick, to face [yaw] (Minecraft's degrees); it stays where it is. */
    internal fun fightTurn(yaw: Float) {
        stand()
        val turned = MathHelper.stepUnwrappedAngleTowards(bodyYaw, yaw, TURN_PER_TICK)
        bodyYaw = turned
        headYaw = turned
        this.yaw = turned
    }

    /** The death pose ([POSE_LIMP], [POSE_DYING], [POSE_CRUSHED]; 0 none). */
    internal fun fightDeathPose(pose: Int) {
        if (dataTracker.get(DEATH_POSE) != pose) dataTracker.set(DEATH_POSE, pose)
    }

    internal fun fightSound(event: SoundEvent, volume: Float) = sound(event, volume)

    internal fun fightHurt(player: ServerPlayerEntity) = hit(player)

    internal val fightNavBlocked: Boolean get() = navBlocked

    /** Back at [x], [y], [z] at rest as if just spawned (the encounter restarts). */
    fun returnHome(x: Double, y: Double, z: Double, yaw: Float) {
        stand()
        mind.reset()
        dataTracker.set(STATE, State.REST.ordinal)
        refreshPositionAndAngles(x, y, z, yaw, 0f)
        headYaw = yaw
        bodyYaw = yaw
        velocity = Vec3d.ZERO
        lastX = x
        lastZ = z
        stepClock = 0.0
    }

    override fun playStepSound(pos: BlockPos, state: BlockState) = Unit

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "main", TRANSITION_TICKS) { state ->
                val moving = state.isMoving
                state.setAndContinue(
                    when {
                        dataTracker.get(DEATH_POSE) == POSE_CRUSHED -> CRUSHED
                        dataTracker.get(DEATH_POSE) == POSE_DYING -> DYING
                        dataTracker.get(DEATH_POSE) == POSE_LIMP -> LIMP
                        else -> null
                    } ?: when (brainState) {
                        State.REST -> IDLE
                        State.STALK -> if (moving) STALK else LISTEN
                        State.ORIENT -> ORIENT
                        State.CHARGE -> if (moving) CHARGE else LISTEN
                        State.WINDUP -> STRIKE
                        State.RECOVER -> RECOVER
                        State.SEARCH -> if (moving) WALK else LISTEN
                        State.RETURN -> if (moving) WALK else IDLE
                    },
                )
            },
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animations

    override fun isInvulnerableTo(source: DamageSource): Boolean =
        !source.isOf(DamageTypes.OUT_OF_WORLD) && !source.isOf(DamageTypes.GENERIC_KILL)

    override fun canImmediatelyDespawn(distanceSquared: Double): Boolean = false

    override fun isPersistent(): Boolean = true

    override fun isPushable(): Boolean = false

    companion object {
        private val STATE: TrackedData<Int> = DataTracker.registerData(VibrationCreatureEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /**
         * Stone Maw's death pose (phases 6.1 and 6.2): 0 none, [POSE_LIMP] the wounded walk to the centre, [POSE_DYING] kneeling with its
         * hands flat on the floor and its head bowed, [POSE_CRUSHED] slammed flat by the first slab.
         */
        private val DEATH_POSE: TrackedData<Int> = DataTracker.registerData(VibrationCreatureEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
        const val POSE_DYING = 1
        const val POSE_CRUSHED = 2
        const val POSE_LIMP = 3

        /** What a player does on the floor that shakes it, and how hard. Breathing, sips and equipment are not felt. */
        private val KINDS = mapOf(
            NoiseSource.SNEAK to Kind.SOFT,
            NoiseSource.WALK to Kind.NORMAL,
            NoiseSource.INTERACT to Kind.NORMAL,
            NoiseSource.SPRINT to Kind.LOUD,
            NoiseSource.JUMP to Kind.LOUD,
            NoiseSource.LAND to Kind.LOUD,
        )

        /** Far above any maximum health: the lethal blow goes through vanilla damage (armor and difficulty cannot save the player). */
        private const val LETHAL_DAMAGE = 10_000f
        /** The entity status that plays the hurt animation and sound (vanilla's generic damage). */
        private const val HURT_STATUS: Byte = 2
        private const val KNOCKBACK = 0.5
        private const val MOVING_PER_TICK = 0.02
        private const val SPEED_SMOOTHING = 10.0

        private const val BASE_SPEED = 0.3
        /** Blocks a second per (attribute * multiplier)^2 on plain ground (vanilla friction 0.6: s^2 / 0.454 a tick). */
        private const val GROUND_FACTOR = 44.0
        private const val REPATH_TICKS = 10
        private const val PROGRESS_TICKS = 30
        private const val MIN_PROGRESS = 0.15
        private const val TURN_PER_TICK = 12f
        private const val TRANSITION_TICKS = 3

        /** Half a cycle of `tools/entity/chapter2/vibration_creature.py`'s walk (1.2 s), stalk (1.6 s) and charge (0.7 s), in ticks. */
        private const val WALK_STEP_TICKS = 12.0
        private const val STALK_STEP_TICKS = 16.0
        private const val CHARGE_STEP_TICKS = 7.0

        val IDLE: RawAnimation = RawAnimation.begin().thenLoop("animation.vibration_creature.idle")
        val ORIENT: RawAnimation = RawAnimation.begin().thenPlay("animation.vibration_creature.orient")
        val WALK: RawAnimation = RawAnimation.begin().thenLoop("animation.vibration_creature.walk")
        val STALK: RawAnimation = RawAnimation.begin().thenLoop("animation.vibration_creature.stalk")
        val CHARGE: RawAnimation = RawAnimation.begin().thenLoop("animation.vibration_creature.charge")
        val STRIKE: RawAnimation = RawAnimation.begin().thenPlay("animation.vibration_creature.strike")
        val RECOVER: RawAnimation = RawAnimation.begin().thenLoop("animation.vibration_creature.recover")
        val DYING: RawAnimation = RawAnimation.begin().thenPlayAndHold("animation.vibration_creature.dying")
        val CRUSHED: RawAnimation = RawAnimation.begin().thenPlayAndHold("animation.vibration_creature.crushed")
        val LIMP: RawAnimation = RawAnimation.begin().thenLoop("animation.vibration_creature.limp")
        val LISTEN: RawAnimation = RawAnimation.begin().thenLoop("animation.vibration_creature.listen")

        fun createAttributes(): DefaultAttributeContainer.Builder = MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, BASE_SPEED)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
    }
}
