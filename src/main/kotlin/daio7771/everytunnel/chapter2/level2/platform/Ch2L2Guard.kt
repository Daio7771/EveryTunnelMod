package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Guard as GuardConfig
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import daio7771.everytunnel.chapter2.level2.core.GuardFight
import daio7771.everytunnel.chapter2.level2.core.GuardIntro
import daio7771.everytunnel.chapter2.level2.core.GuardFight.Act
import daio7771.everytunnel.chapter2.level2.core.TrapBoard
import daio7771.everytunnel.chapter2.platform.Ch2Blocks
import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.common.core.cutscene.CameraPose
import daio7771.everytunnel.common.core.cutscene.SceneFrame
import daio7771.everytunnel.common.core.level.Position
import daio7771.everytunnel.common.platform.DeathHooks
import daio7771.everytunnel.common.platform.LightMode
import daio7771.everytunnel.common.platform.ModBlocks
import daio7771.everytunnel.common.platform.WallMessageBlock
import daio7771.everytunnel.common.platform.WallMessageBlockEntity
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.particle.BlockStateParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import java.util.UUID
import kotlin.math.floor
import kotlin.math.hypot

/**
 * The Observant Guard and its four traps (Chapter 2 Level 2, phase 5), over the data of `level.json`'s `guard` ([GuardConfig]) and the
 * clocks of [GuardFight] and [TrapBoard]:
 *
 * - **Coming out.** The player reaching the vestibule (state [Ch2L2State.GUARD_APPEARS]) begins the fight: the arena's four grates drop
 *   (`guard.barred`: the service corridor's far end, the maintenance room's east exit, the vestibule's south door and the grate to the
 *   collector, so that the only ways are back north, a dead end, and the trap room) and for `crack_seconds` the vestibule's lamps flicker
 *   and, the last second, die while the bricked-up patch (`guard.wall`) cracks: the warning. Then a skippable cinematic in first person ([Ch2L2GuardScenes.INTRO], through the player's own eyes; moves in [introMoves]: it punches the
 *   wall open from its nest, steps out, points at the player and beats its chest; it has no voice), the lamps back at the blow, and it hunts ([drive]): it
 *   always knows where the player is within `guard.arena`, walks straight at them and hurts them for `damage_fraction` of their health
 *   every blow. Control is only taken by the two cinematics.
 * - **The traps.** Each breaker ([TrapBreakerBlock]) is pulled ([pull]) to charge its plate for `rearm_seconds` and arm it; the Guard
 *   stepping on an armed plate (its feet in the plate's zone) is rolled once ([TrapBoard]): stunned for `stun_seconds` or, `fail_chance`
 *   of the time, a fizzle. Only a hunting Guard is stunned; the plates never touch the player. `stuns_to_kill` stuns start its
 *   dying: it falls to the floor and for `death_seconds` its body comes apart into a cloud and is gone, watched by a cinematic
 *   ([Ch2L2GuardScenes], skippable; skipping runs the finish at once).
 * - **The finish** ([finish]): one end, idempotent, run when the dying is over or by `/et ch2 l2 skip guard`: the Guard is gone, the
 *   fight is marked won, the grates rise, the wall stays broken and BOO! goes up in white over the hole, the state becomes
 *   [Ch2L2State.GUARD_DEAD] and the vestibule's checkpoint is taken with it (a death after it brings the player back to the vestibule
 *   with the hole, BOO! and the way open).
 * - **A start** (a death, CONTINUE, `reset`; [restore]): the wall whole, the Guard and its rubble gone, every trap spent and the grates up
 *   as the plan built them; from [Ch2L2State.GUARD_DEAD] on, the aftermath instead.
 *
 * Nothing here is saved: the fight is begun again by walking into the vestibule. Server thread only.
 */
object Ch2L2Guard {

    private val data get() = Ch2L2DataLoader.data
    private val config: GuardConfig get() = data.level.guard

    private var fight = GuardFight(config)
    private var board = TrapBoard(emptyList(), 60L, 0.25)
    private var builtFor: GuardConfig? = null

    private var guardId: UUID? = null
    private var fightPlayer: UUID? = null
    private var barsDown = false
    private var lastCrackTick = 0L
    private var lastStunSound = 0L
    private var lastAct: Act = Act.NONE
    private var deathSoundPlayed = false
    private var lightsOut = false
    private var punched = false
    private var musicOn = false
    private var musicNext = 0L

    private val watch = mutableSetOf<UUID>()

    private const val MIN_GUARD_Y = 3.0
    /** The Guard in its nest stands this far (blocks) in front of the nest's anchor, so that its fist reaches the wall. */
    private const val PUNCH_STEP = 0.9

    private const val OUT_SPEED = 1.9

    /** The player ends the intro looking at this height (blocks) over the Guard's feet: where the last shot looks, so that the camera comes back to it. */
    private const val LOOK_AT_HEAD = 1.8
    private const val OUT_DISTANCE = 1.4

    /** Level 5's fall (`music.fall`, 5 s, rising and ending in a hit), recycled: it plays as the warning begins and its hit lands as the Guard appears. */
    private val SCARE_MUSIC = Identifier(EveryTunnel.MOD_ID, "music.fall")

    /** The hunt's music (`music.ch2l2.guard_chase`, 256 ticks, seamless): played again every [CHASE_MUSIC_TICKS] from the Guard's appearing until its dying. */
    private val CHASE_MUSIC = Identifier(EveryTunnel.MOD_ID, "music.ch2l2.guard_chase")
    private const val CHASE_MUSIC_TICKS = 256L

    /** The sad song of the dying (`music.ch2l2.guard_lament`, 14 s, once): it starts with the dying and ends with it. */
    private val LAMENT_MUSIC = Identifier(EveryTunnel.MOD_ID, "music.ch2l2.guard_lament")
    /** The lamps are dead for the last second of the warning. */
    private const val DARK_TICKS = 20L
    private const val CRACK_EVERY = 8
    private const val STUN_SOUND_EVERY = 20
    private const val SPARK_EVERY = 6
    private const val KNOCKBACK = 0.5
    private const val LETHAL_DAMAGE = 10_000f
    private const val HURT_STATUS: Byte = 2
    private const val BLINK_TICKS = 5L
    private const val BOO_TEXT = "every_tunnel.ch2.l2.boo"
    private const val BOO_CHALK = "paint_white"
    private const val BOO_SIZE = 1.5f

    /** Cells (dx, dz) from the wall face where the burst leaves its rubble, as slabs on the floor. */
    private val RUBBLE = listOf(-2 to 0, -1 to 0, 1 to 0, -1 to 1, 0 to 1)

    fun register() {
        Ch2L2Perf.register("guard", ::tick)
        Ch2L2.onStart { player, state -> restore(player.server, state) }
        Ch2L2.onStateChange { player, from, to ->
            if (to >= Ch2L2State.GUARD_DEAD) finish(player.server, player)
            else if (to < Ch2L2State.GUARD_APPEARS && from >= Ch2L2State.GUARD_APPEARS) restore(player.server, to)
        }
    }

    /** The clocks follow the current configuration while no fight is on (a `/et reload` mid-fight keeps the old ones). */
    private fun sync() {
        val current = config
        if (builtFor === current) return
        if (builtFor != null && fight.phase != GuardFight.Phase.IDLE && !fight.finished) return
        builtFor = current
        fight = GuardFight(current)
        board = TrapBoard(current.plates.map { it.id }, Math.round(current.rearmSeconds * 20.0), current.failChance)
    }

    /** The fight is on: it has begun and not ended. */
    private val running: Boolean
        get() = fight.phase != GuardFight.Phase.IDLE && !fight.finished && fight.phase != GuardFight.Phase.DYING && fight.phase != GuardFight.Phase.DEAD

    // --- the clock ----------------------------------------------------------------------------------

    private fun tick(server: MinecraftServer) {
        val world = levelWorld(server) ?: return
        sync()
        val now = server.ticks.toLong()
        val players = eligible(world)
        if (fight.phase == GuardFight.Phase.IDLE && !fight.finished && players.isNotEmpty()) begin(world, players.first(), now)
        if (fight.phase == GuardFight.Phase.IDLE || fight.finished) {
            watchers(server, now)
            return
        }

        if (fight.tick(now)) beginIntro(world, players.firstOrNull(), now)
        if (fight.phase == GuardFight.Phase.INTRO) intro(world, now)
        if (fight.phase == GuardFight.Phase.CRACKING) cracking(world, now)
        if (fight.phase.ordinal >= GuardFight.Phase.INTRO.ordinal && fight.phase != GuardFight.Phase.DEAD && guard(world) == null) spawnGuard(world, if (fight.phase == GuardFight.Phase.INTRO) nestSpot() else outPoint())

        music(world, now)
        val guard = guard(world)
        traps(world, guard, now)
        if (fight.phase == GuardFight.Phase.STUNNED && guard != null) stunned(world, guard, now)
        if (fight.phase == GuardFight.Phase.DYING && guard != null) dying(world, guard, now)
        if (fight.dead && !Ch2L2GuardScenes.DEATH.running) finish(server, world.getPlayerByUuid(fightPlayer ?: UUID(0, 0)) as? ServerPlayerEntity ?: players.firstOrNull())
        watchers(server, now)
    }

    /** The players the Guard hunts: playing the level, alive, between the vestibule and its death, inside the arena. */
    private fun eligible(world: ServerWorld): List<ServerPlayerEntity> {
        val zones = config.arena.mapNotNull { data.anchors.zone(it)?.box }
        return world.players.filter {
            Ch2L2.active(it) && it.isAlive && !it.isSpectator && !DeathHooks.isDying(it) &&
                Ch2L2.state(it) in Ch2L2State.GUARD_APPEARS..Ch2L2State.GUARD_FIGHT && zones.any { z -> z.contains(it.x, it.y, it.z) }
        }
    }

    private fun begin(world: ServerWorld, player: ServerPlayerEntity, now: Long) {
        board.reset()
        setBreakers(world)
        fight.begin(now)
        fightPlayer = player.uuid
        lastCrackTick = now
        deathSoundPlayed = false
        lastAct = Act.NONE
        raiseBars(world, down = true)
        Ch2L2Build.zoneLights(world, config.lightsZone, LightMode.FLICKER)
        lightsOut = false
        play(player, SCARE_MUSIC)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: the Guard's fight begins for {}", player.gameProfile.name)
    }

    // --- the wall -------------------------------------------------------------------------------------

    /** The warning: the lamps flicker, then die for the last second (and the wall falls silent); the wall cracks and sheds dust meanwhile. */
    private fun cracking(world: ServerWorld, now: Long) {
        val left = fight.crackRemaining(now)
        if (!lightsOut && left <= DARK_TICKS) {
            lightsOut = true
            Ch2L2Build.zoneLights(world, config.lightsZone, LightMode.OFF)
        }
        if (lightsOut) return
        if (now - lastCrackTick < CRACK_EVERY) return
        lastCrackTick = now
        val face = point(config.wallFace) ?: return
        world.playSound(null, face.x, face.y, face.z, Ch2Sounds.CH2L2_WALL_CRACK, SoundCategory.BLOCKS, 1.0f, 0.85f + world.random.nextFloat() * 0.3f)
        val bricks = BlockStateParticleEffect(ParticleTypes.BLOCK, Ch2Blocks.CATACOMB_BRICKED.defaultState)
        world.spawnParticles(bricks, face.x, face.y, face.z + 0.2, 8, 1.6, 1.6, 0.1, 0.03)
        world.spawnParticles(ParticleTypes.POOF, face.x, face.y + 1.0, face.z + 0.3, 2, 1.4, 1.2, 0.1, 0.01)
    }

    /**
     * The warning is over: the Guard stands in its nest facing the wall, the cinematic begins (in first person: the player is held in it
     * and the camera is their eyes) and the choreography starts. The cinematic ends with the player turned to the Guard ([lookAtGuard]).
     */
    private fun beginIntro(world: ServerWorld, player: ServerPlayerEntity?, now: Long) {
        punched = false
        spawnGuard(world, nestSpot())
        val nest = point(config.nest)
        val target = player ?: fightPlayer?.let { world.getPlayerByUuid(it) as? ServerPlayerEntity }
        if (nest != null && target != null) {
            val eyes = doubleArrayOf(target.z - nest.z, nest.x - target.x, target.eyeY - nest.y)
            val frame = SceneFrame(nest.x, nest.y, nest.z, data.anchors.point(config.nest)?.yaw?.toDouble() ?: 0.0)
            Ch2L2GuardScenes.INTRO.start(target, frame, eyes, lookAtGuard(target), GuardIntro.POINT_TO.toInt() + 12)
        }
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: the Guard's intro begins")
    }

    /** (yaw, pitch) in degrees of [player]'s eyes looking at the Guard's head once it stands outside its hole; null if there is no such place. */
    private fun lookAtGuard(player: ServerPlayerEntity): DoubleArray? {
        val out = outPoint() ?: return null
        val pose = CameraPose.looking(player.x, player.eyeY, player.z, out.x, out.y + LOOK_AT_HEAD, out.z)
        return doubleArrayOf(pose.yaw, pose.pitch)
    }

    /** The choreography's sounds and the blow ([GuardIntro]): the fist lands at [GuardIntro.PUNCH_AT], the chest is beaten in time with its animation. */
    private fun intro(world: ServerWorld, now: Long) {
        val t = fight.introElapsed(now)
        if (!punched && t >= GuardIntro.PUNCH_AT) punch(world)
        val guard = guard(world) ?: return
        if (t in GuardIntro.chestBlows()) {
            world.playSoundFromEntity(null, guard, Ch2Sounds.CH2L2_GUARD_CHEST, SoundCategory.HOSTILE, 1.3f, 0.95f + world.random.nextFloat() * 0.1f)
            world.spawnParticles(ParticleTypes.POOF, guard.x, guard.y + 1.5, guard.z, 3, 0.3, 0.2, 0.3, 0.01)
        }
    }

    /** The fist hits the wall: the lamps are back, the patch bursts open with rubble, a hard sound and dust. */
    private fun punch(world: ServerWorld) {
        punched = true
        Ch2L2Build.zoneLights(world, config.lightsZone, LightMode.ON)
        lightsOut = false
        Ch2L2Build.setGate(world, config.wall, "broken")
        placeRubble(world, present = true)
        val face = point(config.wallFace) ?: return
        world.playSound(null, face.x, face.y, face.z, Ch2Sounds.CH2L2_WALL_BURST, SoundCategory.HOSTILE, 1.6f, 1.0f)
        val bricks = BlockStateParticleEffect(ParticleTypes.BLOCK, Ch2Blocks.CATACOMB_BRICKED.defaultState)
        world.spawnParticles(bricks, face.x, face.y, face.z + 0.6, 180, 1.8, 1.8, 0.7, 0.18)
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, face.x, face.y, face.z + 0.6, 30, 1.8, 1.8, 0.7, 0.04)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: the Guard punches the wall open")
    }

    /** The intro skipped (the cinematic's skip): the blow struck if it was not, the Guard just outside the hole facing the player, and on to the stare. */
    fun skipIntro(server: MinecraftServer) {
        val world = levelWorld(server) ?: return
        if (fight.phase != GuardFight.Phase.INTRO) return
        if (!punched) punch(world)
        val guard = guard(world)
        val out = outPoint()
        if (guard != null && out != null) {
            guard.refreshPositionAndAngles(out.x, out.y, out.z, guard.yaw, 0f)
            guard.stand()
            guard.freeze()
            eligible(world).firstOrNull()?.let {
                guard.face(Position(it.x, it.y, it.z))
                lookAtGuard(it)?.let { look -> it.networkHandler.requestTeleport(it.x, it.y, it.z, look[0].toFloat(), look[1].toFloat()) }
            }
        }
        fight.skipIntro(server.ticks.toLong())
    }

    /** Just in front of the Guard's nest anchor (its fist reaches the wall from here), looking south at it. */
    private fun nestSpot(): net.minecraft.util.math.Vec3d? = point(config.nest)?.let { net.minecraft.util.math.Vec3d(it.x, it.y, it.z + PUNCH_STEP) }

    /** Where the Guard stands outside the hole once it has stepped out. */
    private fun outPoint(): net.minecraft.util.math.Vec3d? {
        val face = point(config.wallFace) ?: return null
        val nest = point(config.nest) ?: return null
        return net.minecraft.util.math.Vec3d(face.x, nest.y, face.z + OUT_DISTANCE)
    }

    private fun placeRubble(world: ServerWorld, present: Boolean) {
        val face = point(config.wallFace) ?: return
        val nest = point(config.nest) ?: return
        val slab = Ch2Blocks.CATACOMB_BRICK_SLAB.defaultState
        for ((dx, dz) in RUBBLE) {
            val pos = BlockPos(floor(face.x).toInt() + dx, floor(nest.y).toInt(), floor(face.z).toInt() + dz)
            val here = world.getBlockState(pos)
            if (present) {
                if (here.isAir) world.setBlockState(pos, slab, Block.NOTIFY_ALL)
            } else if (here.isOf(Ch2Blocks.CATACOMB_BRICK_SLAB)) {
                world.setBlockState(pos, net.minecraft.block.Blocks.AIR.defaultState, Block.NOTIFY_ALL)
            }
        }
    }

    // --- the Guard -------------------------------------------------------------------------------------

    private fun guard(world: ServerWorld): ObservantGuardEntity? = guardId?.let { world.getEntity(it) as? ObservantGuardEntity }

    /** The Guard put in the world at [spot] (its nest's, else outside the hole), facing the nest's way (south). */
    private fun spawnGuard(world: ServerWorld, spot: net.minecraft.util.math.Vec3d?) {
        val nest = data.anchors.point(config.nest)
        if (spot == null || nest == null) {
            EveryTunnel.LOGGER.warn("Chapter 2 Level 2: no anchor {}: the Guard is not placed", config.nest)
            return
        }
        guard(world)?.discard()
        val guard = Ch2Entities.OBSERVANT_GUARD.create(world) ?: return
        guard.refreshPositionAndAngles(spot.x, spot.y, spot.z, nest.yaw, 0f)
        guard.headYaw = nest.yaw
        guard.bodyYaw = nest.yaw
        world.spawnEntity(guard)
        guardId = guard.uuid
    }

    /** Called every tick by the Guard itself ([ObservantGuardEntity.mobTick]): what it does now. */
    fun drive(guard: ObservantGuardEntity, world: ServerWorld, now: Long) = Ch2L2Perf.time(world.server, "guard brain") { driveNow(guard, world, now) }

    private fun driveNow(guard: ObservantGuardEntity, world: ServerWorld, now: Long) {
        if (guardId == null) guardId = guard.uuid
        if (guard.uuid != guardId || fight.finished) {
            if (guard.uuid != guardId) guard.discard()
            return
        }
        val target = eligible(world).minByOrNull { hypot(it.x - guard.x, it.z - guard.z) }
        val where = target?.let { Position(it.x, it.y, it.z) }
        val distance = target?.takeIf { Math.abs(it.y - guard.y) < MIN_GUARD_Y }?.let { hypot(it.x - guard.x, it.z - guard.z) }
        when (fight.phase) {
            GuardFight.Phase.INTRO -> introMoves(guard, world.server.ticks.toLong(), where)
            GuardFight.Phase.STARING -> {
                still(guard, ObservantGuardEntity.Anim.IDLE)
                where?.let(guard::face)
            }
            GuardFight.Phase.HUNTING -> hunt(guard, world, now, target, where, distance)
            GuardFight.Phase.STUNNED -> still(guard, ObservantGuardEntity.Anim.STUNNED)
            GuardFight.Phase.DYING -> still(guard, ObservantGuardEntity.Anim.DEATH)
            else -> still(guard, ObservantGuardEntity.Anim.IDLE)
        }
    }

    /**
     * The intro's moves ([GuardIntro]): it stands, punches (facing the wall), walks out of the hole, turns to the player, points at them and beats
     * its chest. What it does, it does at the player, whom the camera is.
     */
    private fun introMoves(guard: ObservantGuardEntity, now: Long, where: Position?) {
        val t = fight.introElapsed(now)
        val out = outPoint()
        when {
            t < GuardIntro.PUNCH_START -> still(guard, ObservantGuardEntity.Anim.IDLE)
            t < GuardIntro.WALK_FROM -> still(guard, ObservantGuardEntity.Anim.PUNCH)
            t < GuardIntro.WALK_TO && out != null && hypot(out.x - guard.x, out.z - guard.z) > 0.4 -> {
                guard.steerTo(Position(out.x, out.y, out.z), OUT_SPEED)
                guard.anim = ObservantGuardEntity.Anim.WALK
            }
            t < GuardIntro.TURN_TO -> {
                still(guard, ObservantGuardEntity.Anim.IDLE)
                where?.let(guard::face)
            }
            else -> {
                still(guard, if (t < GuardIntro.POINT_TO) ObservantGuardEntity.Anim.POINT else ObservantGuardEntity.Anim.CHEST)
                where?.let(guard::face)
            }
        }
    }

    private fun still(guard: ObservantGuardEntity, anim: ObservantGuardEntity.Anim) {
        guard.stand()
        guard.freeze()
        guard.anim = anim
    }

    private fun hunt(guard: ObservantGuardEntity, world: ServerWorld, now: Long, target: ServerPlayerEntity?, where: Position?, distance: Double?) {
        val act = fight.think(now, distance)
        when (act) {
            Act.MOVE -> if (where == null || distance == null) {
                still(guard, ObservantGuardEntity.Anim.IDLE)
            } else if (distance <= config.hitRange * 0.7) {
                still(guard, ObservantGuardEntity.Anim.IDLE)
                guard.face(where)
            } else {
                guard.steerTo(where, config.chaseSpeed)
                guard.anim = ObservantGuardEntity.Anim.CHASE
            }
            Act.WINDUP -> {
                guard.stand()
                guard.freeze()
                where?.let(guard::face)
                guard.anim = ObservantGuardEntity.Anim.ATTACK
                if (lastAct != Act.WINDUP) world.playSoundFromEntity(null, guard, Ch2Sounds.CH2L2_GUARD_WINDUP, SoundCategory.HOSTILE, 1.0f, 1.0f)
            }
            Act.HIT -> {
                world.playSoundFromEntity(null, guard, Ch2Sounds.CH2L2_GUARD_HIT, SoundCategory.HOSTILE, 1.2f, 1.0f)
                target?.let { hit(guard, it) }
            }
            Act.MISS -> still(guard, ObservantGuardEntity.Anim.IDLE)
            Act.NONE -> still(guard, ObservantGuardEntity.Anim.IDLE)
        }
        lastAct = act
    }

    /** The blow connects: exactly the configured share of the player's maximum health (two kill); a lethal one goes through vanilla damage so that the death sequence takes it. */
    private fun hit(guard: ObservantGuardEntity, player: ServerPlayerEntity) {
        val loss = (player.maxHealth * config.damageFraction).toFloat()
        val lethal = player.health - loss <= 0.0f
        if (lethal) {
            player.damage(guard.damageSources.mobAttack(guard), LETHAL_DAMAGE)
        } else {
            player.health = player.health - loss
            player.world.sendEntityStatus(player, HURT_STATUS)
            val away = net.minecraft.util.math.Vec3d(player.x - guard.x, 0.0, player.z - guard.z).normalize().multiply(KNOCKBACK)
            player.addVelocity(away.x, 0.25, away.z)
            player.velocityModified = true
        }
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: the Guard hits {} for {} of {}: {}", player.gameProfile.name, loss, player.maxHealth, if (lethal) "fatal" else "health ${player.health}")
    }

    // --- the traps -------------------------------------------------------------------------------------

    /** A breaker pulled by [player] ([TrapBreakerBlock]); only while the fight is on, and only a spent trap charges. */
    fun pull(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos, @Suppress("UNUSED_PARAMETER") state: BlockState) {
        sync()
        val plate = config.plates.firstOrNull { cell(it.breaker) == pos } ?: return
        val now = world.server.ticks.toLong()
        world.playSound(null, pos, Ch2Sounds.CH2L2_TRAP_CLACK, SoundCategory.BLOCKS, 0.9f, 1.0f)
        if (!running) return
        if (!board.pull(plate.id, now)) return
        world.playSound(null, pos, Ch2Sounds.CH2L2_TRAP_CHARGE, SoundCategory.BLOCKS, 0.9f, 1.0f)
        setBreakers(world, now)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: {} pulls the breaker of {}", player.gameProfile.name, plate.id)
    }

    private fun traps(world: ServerWorld, guard: ObservantGuardEntity?, now: Long) {
        for (id in board.tick(now)) {
            config.plates.firstOrNull { it.id == id }?.let { plate -> cell(plate.breaker)?.let { world.playSound(null, it, Ch2Sounds.CH2L2_TRAP_ARMED, SoundCategory.BLOCKS, 0.9f, 1.0f) } }
        }
        for (plate in config.plates) {
            val box = data.anchors.zone(plate.zone)?.box ?: continue
            val inside = guard != null && fight.alive && box.contains(guard.x, guard.y, guard.z)
            // A plate only does anything to a hunting Guard (not while it stares, is stunned or dies): the entry is then not consumed.
            val outcome = board.step(plate.id, inside, world.random.nextDouble(), stunActive = fight.phase != GuardFight.Phase.HUNTING)
            when (outcome) {
                TrapBoard.Outcome.STUN -> if (guard != null && fight.stun(now)) zap(world, guard, plate.id)
                TrapBoard.Outcome.FAIL -> if (guard != null) fizzle(world, guard, plate.id)
                TrapBoard.Outcome.NONE -> Unit
            }
            if (board.phase(plate.id) == TrapBoard.Phase.ARMED && now % SPARK_EVERY == 0L) {
                world.spawnParticles(
                    ParticleTypes.ELECTRIC_SPARK, (box.minX + box.maxX + 1) / 2.0, box.minY + 0.1, (box.minZ + box.maxZ + 1) / 2.0, 1,
                    (box.maxX - box.minX + 1) / 2.4, 0.0, (box.maxZ - box.minZ + 1) / 2.4, 0.0,
                )
            }
        }
        setBreakers(world, now)
    }

    private fun zap(world: ServerWorld, guard: ObservantGuardEntity, plateId: String) {
        world.playSound(null, guard.x, guard.y, guard.z, Ch2Sounds.CH2L2_TRAP_ZAP, SoundCategory.HOSTILE, 1.5f, 1.0f)
        world.spawnParticles(ParticleTypes.FLASH, guard.x, guard.y + 1.2, guard.z, 1, 0.0, 0.0, 0.0, 0.0)
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, guard.x, guard.y + 1.2, guard.z, 60, 0.6, 1.0, 0.6, 0.5)
        guard.stand()
        guard.freeze()
        if (fight.phase == GuardFight.Phase.DYING) {
            val watcher = fightPlayer?.let { world.getPlayerByUuid(it) as? ServerPlayerEntity }
            if (watcher != null) Ch2L2GuardScenes.DEATH.start(watcher, SceneFrame(guard.x, guard.y, guard.z, guard.bodyYaw.toDouble()))
        }
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: {} stuns the Guard ({} of {}): {}", plateId, fight.stuns, config.stunsToKill, fight.phase)
    }

    private fun fizzle(world: ServerWorld, guard: ObservantGuardEntity, plateId: String) {
        world.playSound(null, guard.x, guard.y, guard.z, Ch2Sounds.CH2L2_TRAP_FIZZLE, SoundCategory.HOSTILE, 1.1f, 1.0f)
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, guard.x, guard.y + 0.2, guard.z, 12, 0.4, 0.1, 0.4, 0.1)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: {} fizzles under the Guard", plateId)
    }

    private fun stunned(world: ServerWorld, guard: ObservantGuardEntity, now: Long) {
        if (now - lastStunSound >= STUN_SOUND_EVERY) {
            lastStunSound = now
            world.playSoundFromEntity(null, guard, Ch2Sounds.CH2L2_GUARD_STUN, SoundCategory.HOSTILE, 1.0f, 1.0f)
        }
        if (now % 3 == 0L) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, guard.x, guard.y + 1.3, guard.z, 3, 0.5, 0.9, 0.5, 0.15)
    }

    /** The Guard's gradual death: a cloud of black and violet specks thickening round its body, the model coming apart ([ObservantGuardEntity.Anim.DEATH]). */
    private fun dying(world: ServerWorld, guard: ObservantGuardEntity, now: Long) {
        if (!deathSoundPlayed) {
            deathSoundPlayed = true
            world.playSoundFromEntity(null, guard, Ch2Sounds.CH2L2_GUARD_DEATH, SoundCategory.HOSTILE, 1.4f, 1.0f)
            (fightPlayer?.let { world.getPlayerByUuid(it) } as? ServerPlayerEntity)?.let { play(it, LAMENT_MUSIC, louder = false) }
        }
        val progress = fight.dyingProgress(now)
        val count = 1 + (progress * 8).toInt()
        world.spawnParticles(ParticleTypes.SQUID_INK, guard.x, guard.y + 1.2, guard.z, count, 0.5, 1.0, 0.5, 0.02)
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, guard.x, guard.y + 1.2, guard.z, count * 2, 0.5, 1.0, 0.5, 0.05)
        if (now % 4 == 0L) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, guard.x, guard.y + 1.2, guard.z, 2, 0.7, 1.1, 0.7, 0.3)
    }

    /** Every breaker as its trap is: lever up, or down with the lamp blinking amber while it charges, steady green when armed. */
    private fun setBreakers(world: ServerWorld, now: Long = 0L) {
        for (plate in config.plates) {
            val pos = cell(plate.breaker) ?: continue
            val state = world.getBlockState(pos)
            if (!state.isOf(Ch2Blocks.TRAP_BREAKER)) continue
            val wanted = when (board.phase(plate.id)) {
                TrapBoard.Phase.SPENT -> TrapBreakerState.SPENT
                TrapBoard.Phase.CHARGING -> if ((now / BLINK_TICKS) % 2 == 0L) TrapBreakerState.CHARGING else TrapBreakerState.DIM
                TrapBoard.Phase.ARMED -> TrapBreakerState.ARMED
            }
            if (state.get(TrapBreakerBlock.STATE) != wanted) world.setBlockState(pos, state.with(TrapBreakerBlock.STATE, wanted), Block.NOTIFY_ALL)
        }
    }

    // --- the music ------------------------------------------------------------------------------------

    /** The hunt's music while the Guard hunts or is stunned: it starts as the Guard appears and is played again as it ends; it stops when the dying begins. */
    private fun music(world: ServerWorld, now: Long) {
        val player = fightPlayer?.let { world.getPlayerByUuid(it) as? ServerPlayerEntity }
        val hunting = (fight.phase == GuardFight.Phase.INTRO && punched) || fight.phase == GuardFight.Phase.STARING || fight.phase == GuardFight.Phase.HUNTING || fight.phase == GuardFight.Phase.STUNNED
        if (!hunting) {
            if (musicOn) stopMusic(world.server, scare = false)
            return
        }
        if (player == null) return
        if (!musicOn || now >= musicNext) {
            musicOn = true
            musicNext = now + CHASE_MUSIC_TICKS
            play(player, CHASE_MUSIC)
        }
    }

    /**
     * A music of the mod played to [player], following them (the sound is not in the registry: the packet carries it). The game caps a sound's
     * volume at 1, so [louder] plays it a second time at half volume at the same moment: about 3.5 dB more.
     */
    private fun play(player: ServerPlayerEntity, id: Identifier, louder: Boolean = true) {
        val seed = player.world.random.nextLong()
        val entry = RegistryEntry.of(SoundEvent.of(id))
        player.networkHandler.sendPacket(PlaySoundFromEntityS2CPacket(entry, SoundCategory.MUSIC, player, 1.0f, 1.0f, seed))
        if (louder) player.networkHandler.sendPacket(PlaySoundFromEntityS2CPacket(entry, SoundCategory.MUSIC, player, 0.5f, 1.0f, seed))
    }

    /** Both musics stopped for everybody ([scare] too: a death or a finish in the middle of the warning). */
    private fun stopMusic(server: MinecraftServer, scare: Boolean) {
        musicOn = false
        for (player in server.playerManager.playerList) {
            player.networkHandler.sendPacket(StopSoundS2CPacket(CHASE_MUSIC, SoundCategory.MUSIC))
            if (scare) player.networkHandler.sendPacket(StopSoundS2CPacket(LAMENT_MUSIC, SoundCategory.MUSIC))
            if (scare) player.networkHandler.sendPacket(StopSoundS2CPacket(SCARE_MUSIC, SoundCategory.MUSIC))
        }
    }

    // --- the ways, BOO! ---------------------------------------------------------------------------------

    /** The grates down ([down]) or up; their chains sound once for each that changes. */
    private fun raiseBars(world: ServerWorld, down: Boolean, state: Ch2L2State? = null) {
        if (down == barsDown && !down) return
        barsDown = down
        for (id in config.barred) {
            // The collector's grate belongs to the flood from its first stage on (phase 6).
            if (!down && id == "vestibule_gate" && state != null && state >= Ch2L2State.FLOOD_1) continue
            val before = world.getBlockState(Ch2L2Build.gateCentre(id)?.let { BlockPos.ofFloored(it) } ?: continue)
            Ch2L2Build.setGate(world, id, if (down) "grate" else "open")
            val after = world.getBlockState(BlockPos.ofFloored(Ch2L2Build.gateCentre(id) ?: continue))
            if (before != after) Ch2L2Build.gateCentre(id)?.let { world.playSound(null, it.x, it.y, it.z, Ch2Sounds.CH2L2_GRATE, SoundCategory.BLOCKS, 1.0f, 1.0f) }
        }
    }

    private fun booPos(): BlockPos? = cell(config.boo)

    private fun putBoo(world: ServerWorld, up: Boolean) {
        val pos = booPos() ?: return
        if (!up) {
            if (world.getBlockState(pos).isOf(ModBlocks.WALL_MESSAGE)) world.setBlockState(pos, net.minecraft.block.Blocks.AIR.defaultState, Block.NOTIFY_ALL)
            return
        }
        world.setBlockState(pos, ModBlocks.WALL_MESSAGE.defaultState.with(WallMessageBlock.FACING, Direction.SOUTH), Block.NOTIFY_ALL)
        val entity = world.getBlockEntity(pos) as? WallMessageBlockEntity ?: return
        val nbt = NbtCompound()
        nbt.putString("Text", BOO_TEXT)
        nbt.putFloat("Size", BOO_SIZE)
        nbt.putFloat("Rotation", 0f)
        nbt.putString("Trigger", "fixed")
        nbt.putString("Chalk", BOO_CHALK)
        entity.readNbt(nbt)
        entity.markDirty()
        world.updateListeners(pos, entity.cachedState, entity.cachedState, Block.NOTIFY_ALL)
    }

    // --- the finish and the start -----------------------------------------------------------------------------

    /**
     * The one end of the fight (see the class), idempotent and safe to run twice: the Guard gone, the grates up, the wall broken with
     * its rubble, BOO! up. [player]: whose state becomes [Ch2L2State.GUARD_DEAD] (with the vestibule's checkpoint) if it is not yet.
     */
    fun finish(server: MinecraftServer, player: ServerPlayerEntity?) {
        sync()
        if (!fight.finish()) return
        val world = levelWorld(server)
        stopMusic(server, scare = true)
        if (world != null) {
            guard(world)?.discard()
            guardId = null
            board.reset()
            setBreakers(world)
            val state = player?.let(Ch2L2::state) ?: Ch2L2State.GUARD_DEAD
            raiseBars(world, down = false, state)
            Ch2L2Build.setGate(world, config.wall, "broken")
            placeRubble(world, present = true)
            putBoo(world, up = true)
        }
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: the Guard is dead: the way is open, BOO! is up")
        if (player != null) {
            if (Ch2L2.state(player) < Ch2L2State.GUARD_DEAD) Ch2L2.setState(player, Ch2L2State.GUARD_DEAD, "the Guard is dead")
            if (Ch2L2.state(player) == Ch2L2State.GUARD_DEAD) Ch2L2.takeCheckpoint(player, "vestibule")
        }
    }

    /** The encounter as the level starts (or is put back) at [state]: see the class. */
    private fun restore(server: MinecraftServer, state: Ch2L2State) {
        sync()
        val world = levelWorld(server) ?: return
        stopMusic(server, scare = true)
        guard(world)?.discard()
        for (stray in world.iterateEntities().filterIsInstance<ObservantGuardEntity>().toList()) stray.discard()
        guardId = null
        fightPlayer = null
        fight.reset()
        board.reset()
        barsDown = true
        lightsOut = false
        Ch2L2Build.zoneLights(world, config.lightsZone, LightMode.ON)
        setBreakers(world)
        raiseBars(world, down = false, state)
        if (state >= Ch2L2State.GUARD_DEAD) {
            Ch2L2Build.setGate(world, config.wall, "broken")
            placeRubble(world, present = true)
            putBoo(world, up = true)
            fight.finish()
        } else {
            Ch2L2Build.setGate(world, config.wall, "whole")
            placeRubble(world, present = false)
            putBoo(world, up = false)
        }
    }

    // --- debug --------------------------------------------------------------------------------------

    /** Toggles the action bar's readout of the fight; returns whether it is on now. */
    fun toggleWatch(player: ServerPlayerEntity): Boolean = if (watch.remove(player.uuid)) false else watch.add(player.uuid)

    private fun watchers(server: MinecraftServer, now: Long) {
        if (watch.isEmpty()) return
        val traps = config.plates.joinToString(" ") { "${it.id.removePrefix("trap_")}:${board.phase(it.id).name.first()}" }
        val line = "guard: ${fight.phase} stuns ${fight.stuns}/${config.stunsToKill}${if (fight.finished) " (finished)" else ""}  |  traps $traps"
        for (player in server.playerManager.playerList) if (player.uuid in watch) player.sendMessage(Text.literal(line), true)
    }

    // --- helpers ------------------------------------------------------------------------------------

    private fun point(anchor: String): net.minecraft.util.math.Vec3d? = data.anchors.point(anchor)?.position?.let { net.minecraft.util.math.Vec3d(it.x, it.y, it.z) }

    private fun cell(anchor: String): BlockPos? = point(anchor)?.let { BlockPos.ofFloored(it) }

    private fun levelWorld(server: MinecraftServer): ServerWorld? =
        Identifier.tryParse(data.anchors.dimension)?.let { server.getWorld(RegistryKey.of(RegistryKeys.WORLD, it)) }
}
