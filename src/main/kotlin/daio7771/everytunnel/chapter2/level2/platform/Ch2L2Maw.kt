package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.chapter2.platform.Ch2Achievements
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Flood
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Maw as MawRules
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import daio7771.everytunnel.chapter2.level2.core.MawCollapse
import daio7771.everytunnel.chapter2.level2.core.MawEscape
import daio7771.everytunnel.chapter2.level2.core.PipeSupply
import daio7771.everytunnel.chapter2.level2.core.StoneMawFight
import daio7771.everytunnel.chapter2.level2.core.VibrationCreatureBrain
import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.chapter2.platform.Ch2Items
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.platform.CollapseHooks
import daio7771.everytunnel.platform.DeathHooks
import daio7771.everytunnel.platform.MovementRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.FallingBlockEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.particle.BlockStateParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket
import net.minecraft.registry.Registries
import net.minecraft.registry.entry.RegistryEntry
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
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.util.UUID
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Stone Maw's last stand, beyond the exit (Chapter 2 Level 2, phase 6.1, remade in phase 9), over `level.json`'s `maw` and the rules of
 * [StoneMawFight] / [PipeSupply] / [MawEscape]:
 *
 * - **The exit door** (`maw.exit_door`): reaching its zone from the station on plays its sound and, a moment later, opens the heavy
 *   door for good; the corridor leads to the arena.
 * - **The entrance** (state [Ch2L2State.MAW], set by walking into `zone_maw_middle`): the arena's door shuts behind the player, the grate
 *   over the pipe's mouth is torn off, Stone Maw ([VibrationCreatureEntity] in its fight mode, [VibrationCreatureEntity.inFight]) crawls
 *   out and rests at `maw.home`, and the first piece of pipe falls in front of the player.
 * - **The fight**: [tickBody] (called by the entity's tick) feeds [StoneMawFight] and acts on what it decides; the pieces are
 *   [ItemEntity]s that fall from the ceiling points ([supplyTick]; each landing is a loud vibration), picked up by walking over them
 *   and thrown with a right click ([PipePieceItem], [ThrownPipeEntity]): a hit on the creature is [pieceHit] (it charges the player: the
 *   pipe never kills), anything else [pieceBroke] (a clang the creature goes to). The jump is blocked in the arena while it lasts
 *   ([MovementRules]: the server's rule here, the client's by [JUMP_PACKET]).
 * - **The pillars**: three 2 x 2 columns of the arena (`maw.pillars`, built by the map whole). A charge that ends against one ([pillarNear]
 *   tells which) damages it: it cracks ([crackPillar]: other bricks, dust and pebbles falling at the crack), and at the second it breaks
 *   ([breakPillar]: its blocks fall apart and a stump of rubble stays, and pieces of the ceiling fall round it, each one announced
 *   on the floor and hurting whoever it lands on: [Rock]). Each ram into a pillar shakes the camera and thuds deeper than a wall's.
 * - **The death and the escape** (phase 9): when the third pillar breaks the ceiling comes down on Stone Maw, live, in first person
 *   ([collapse]: [MawCollapse]'s plan centred on it; the creature is crushed by the first slab), the state goes to [Ch2L2State.MAW_ESCAPE]
 *   (its checkpoint saves the pillars and where the mound is: CONTINUE puts the arena back and [startEscape]s again), the far door opens and
 *   the arena caves in for `maw.escape.seconds` ([MawEscape]: pieces fall everywhere, announced as the others; rumble and shaking). The
 *   player who leaves through the far door has it slam behind them and is in [Ch2L2State.MAW_DEAD] ([finishDeath]: one idempotent end, also
 *   what `skip maw` and the debug `kill` run); the one who is still in the arena when the time is up dies.
 * - **The music** (phase 6.2): the Observant Guard's hunt music, recycled (`music.ch2l2.guard_chase`, 256 ticks, seamless), played again every
 *   [CHASE_MUSIC_TICKS] from the arena's door shutting until the last pillar breaks, which cuts it dry.
 * - **Starts** ([Ch2L2.onStart]: entering, a death, CONTINUE, `reset`): everything put as the state wants it (fight over and reset,
 *   the pillars whole or broken, the gates, the rubble, the pieces gone from the pockets).
 *
 * One fight at a time (the level is a single-player one). Server thread only.
 */
object Ch2L2Maw {

    /** To the client: whether the jump is blocked (the arena, while the fight lasts). */
    val JUMP_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l2_jump_block")

    private val data get() = Ch2L2DataLoader.data
    private val rules get() = data.level.maw

    private enum class Phase { IDLE, FIGHT, ESCAPE, DEAD }

    /** A piece of the ceiling coming down on the cell ([x], [z]): announced at [warnAt], dropped at [dropAt], landing at [landAt] (server ticks). */
    private class Rock(val warnAt: Long, val dropAt: Long, val landAt: Long, val x: Int, val z: Int, var warned: Boolean = false, var dropped: Boolean = false)

    private val fight = StoneMawFight()
    private val supply = PipeSupply()
    private var phase = Phase.IDLE
    private var fighter: UUID? = null
    private var entering = false
    private var enteringSince = 0L
    private var lastMode = StoneMawFight.Mode.BRAIN
    private var exitOpenAt: Long? = null
    private var burstAt: Long? = null
    private var musicStart: Long? = null
    private var escapeStart = 0L
    private var escapeShaken = 0L
    private var moundPlaced = false

    /** How each pillar looks now: 0 whole, 1 cracked, 2 broken. */
    private val shown = IntArray(MAX_PILLARS)

    /** The pieces that are falling, by entity id (their landing is a clang). */
    private val falling = HashSet<UUID>()
    private val clangs = ArrayList<VibrationCreatureBrain.Vibration>()
    private val jumpTold = HashSet<UUID>()
    private val rocks = ArrayList<Rock>()

    /** The client's knowledge that its jump is blocked (set by [JUMP_PACKET]; client thread only). */
    @Volatile
    var clientJumpBlocked = false

    fun register() {
        Ch2L2Perf.register("maw", ::tick)
        Ch2L2.onStart { player, state -> started(player, state) }
        Ch2L2.onStateChange { player, _, to -> if (to >= Ch2L2State.MAW_DEAD) finishDeath(player.server) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> jumpTold.remove(handler.player.uuid) }
        ServerLifecycleEvents.SERVER_STOPPED.register { reset() }
        MovementRules.addJumpRule { entity ->
            entity is net.minecraft.entity.player.PlayerEntity && (if (entity.world.isClient) clientJumpBlocked else entity is ServerPlayerEntity && jumpBlocked(entity))
        }
    }

    // --- what the others ask -----------------------------------------------------------------------

    /** The player may not jump now: playing the level, in the arena, with the fight on. */
    fun jumpBlocked(player: ServerPlayerEntity): Boolean {
        if (!Ch2L2.active(player) || Ch2L2.state(player) != Ch2L2State.MAW) return false
        val arena = data.anchors.zone(rules.arenaZone)?.box ?: return false
        return arena.contains(player.x, player.y, player.z)
    }

    /** Stone Maw is alive and fighting (the debug readout). */
    fun describe(): String = "maw: $phase pillars broken ${fight.broken} (damage ${rules.pillars.indices.map { fight.pillarDamage(it) }}) mode ${fight.mode} brain ${fight.brainState}"

    /** A hit on Stone Maw as if a piece had struck it (the debug command); false if it cannot be hit now. */
    fun debugHit(server: MinecraftServer): Boolean = phase == Phase.FIGHT && fight.hit()

    /** The pillars broken at once and the ceiling down on Stone Maw (the debug command); false if it is not fighting. */
    fun debugKill(server: MinecraftServer): Boolean {
        if (phase != Phase.FIGHT) return false
        val world = levelWorld(server) ?: return false
        val before = BooleanArray(rules.pillars.size) { fight.isBroken(it) }
        val outcome = fight.breakAll() ?: return false
        for (i in before.indices) if (!before[i] && i != outcome.index) breakPillar(world, i, debris = false)
        applyOutcome(world, outcome, server.ticks.toLong(), creatures(world).firstOrNull())
        return true
    }

    /** Pillar [number] (1-based) damaged once as a ram would (the debug command); the reply says what happened, null if nothing could. */
    fun debugPillar(server: MinecraftServer, number: Int): String? {
        if (phase != Phase.FIGHT) return null
        val world = levelWorld(server) ?: return null
        val outcome = fight.damagePillar(number - 1) ?: return null
        applyOutcome(world, outcome, server.ticks.toLong(), creatures(world).firstOrNull())
        return if (outcome.collapse) "the last pillar breaks" else if (outcome.broken) "pillar $number breaks" else "pillar $number cracks"
    }

    // --- the clock ---------------------------------------------------------------------------------

    private fun tick(server: MinecraftServer) {
        val world = levelWorld(server) ?: return
        val now = server.ticks.toLong()
        for (player in world.players) {
            if (!Ch2L2.active(player) || !player.isAlive || player.isSpectator) continue
            val state = Ch2L2.state(player)
            exitDoor(world, player, state, now)
            if (state == Ch2L2State.MAW && phase == Phase.IDLE && inZone(player, rules.middleZone)) begin(player, world, now)
            tellJump(player)
            if (phase == Phase.FIGHT) hint(player)
        }
        exitOpenAt?.let { at ->
            if (now >= at) {
                exitOpenAt = null
                Ch2L2Build.setGate(world, rules.exitGate, "open")
            }
        }
        burstAt?.let { at ->
            if (now >= at) {
                burstAt = null
                burst(world, now)
            }
        }
        clangs.removeAll { now - it.tick > CLANG_TICKS }
        if (phase == Phase.FIGHT) supplyTick(world, now)
        if (phase == Phase.FIGHT) music(world, now)
        if (phase == Phase.FIGHT) crackedPillars(world, now)
        if (phase == Phase.ESCAPE) escaping(server, world, now)
        if (phase == Phase.ESCAPE || coming.isNotEmpty()) collapsing(world, now)
        if (rocks.isNotEmpty()) rocks(world, now)
    }

    // --- the exit door -------------------------------------------------------------------------------

    private fun exitDoor(world: ServerWorld, player: ServerPlayerEntity, state: Ch2L2State, now: Long) {
        if (!Ch2L2Flood.exitDoorAllowed(state) || exitOpenAt != null || !inZone(player, rules.exitZone)) return
        if (Ch2L2Build.gateIs(world, rules.exitGate, "open")) return
        data.anchors.point(rules.exitAnchor)?.position?.let { world.playSound(null, it.x, it.y, it.z, Ch2Sounds.CH2L2_EXIT_DOOR, SoundCategory.BLOCKS, 1.5f, 1.0f) }
        exitOpenAt = now + EXIT_DOOR_TICKS
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: {} reaches the exit door: it opens", player.gameProfile.name)
    }

    // --- the entrance --------------------------------------------------------------------------------

    private fun begin(player: ServerPlayerEntity, world: ServerWorld, now: Long) {
        clearArena(world)
        // clearArena takes the pillars' bricks with the rubble: they are put whole again.
        for (i in rules.pillars.indices) setPillar(world, i, 0)
        fight.reset(rules)
        supply.reset()
        phase = Phase.FIGHT
        fighter = player.uuid
        lastMode = StoneMawFight.Mode.BRAIN
        Ch2L2Build.setGate(world, rules.entryGate, "shut")
        data.anchors.point(rules.entryAnchor)?.position?.let { world.playSound(null, it.x, it.y, it.z, Ch2Sounds.CH2L2_FLOOD_GATE_SLAM, SoundCategory.BLOCKS, 1.5f, 1.0f) }
        burstAt = now + BURST_DELAY_TICKS
        musicStart = now
        firstPiece(world, player)
        Ch2Achievements.fightBegan(player)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: {} enters the arena: the fight begins", player.gameProfile.name)
    }

    /** The grate off the pipe's mouth, and Stone Maw out of it. */
    private fun burst(world: ServerWorld, now: Long) {
        if (phase != Phase.FIGHT) return
        Ch2L2Build.setGate(world, rules.mouthGate, "open")
        val mouth = data.anchors.point(rules.mouthAnchor)?.position
        val home = data.anchors.point(rules.home)
        if (mouth != null) world.playSound(null, mouth.x, mouth.y, mouth.z, Ch2Sounds.CH2L2_MAW_BURST, SoundCategory.HOSTILE, 1.6f, 1.0f)
        if (home == null || mouth == null) return
        val creature = Ch2Entities.VIBRATION_CREATURE.create(world) ?: return
        creature.inFight = true
        creature.refreshPositionAndAngles(mouth.x, home.position.y, mouth.z - ENTERING_DEPTH, 0f, 0f)
        creature.headYaw = 0f
        creature.bodyYaw = 0f
        world.spawnEntity(creature)
        entering = true
        enteringSince = now
        world.spawnParticles(
            BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE.defaultState), mouth.x, mouth.y, mouth.z + 1.0, 60, 1.2, 1.2, 0.6, 0.1,
        )
    }

    // --- Stone Maw's body -------------------------------------------------------------------------------

    /** One tick of Stone Maw ([VibrationCreatureEntity.mobTick]): the fight decides, the body acts. */
    fun tickBody(e: VibrationCreatureEntity, world: ServerWorld) {
        val now = world.server.ticks.toLong()
        val creature = data.level.creature
        val home = position(rules.home) ?: Position(e.x, e.y, e.z)
        val centre = position(rules.centre) ?: home
        val self = Position(e.x, e.y, e.z)
        if (phase == Phase.DEAD || phase == Phase.IDLE || phase == Phase.ESCAPE) {
            e.fightStand(null)
            return
        }
        if (entering) {
            e.fightPose(VibrationCreatureBrain.State.SEARCH)
            e.fightSteer(home, creature.walkSpeed)
            if (VibrationCreatureBrain.horizontalDistance(self, home) <= ENTERED_DISTANCE || now - enteringSince > ENTERING_LIMIT_TICKS) {
                entering = false
                e.fightStand(null)
            }
            return
        }
        val player = e.nearestActivePlayer(world)
        val target = player?.let { StoneMawFight.Target(it.uuid, Position(it.x, it.y, it.z), Math.hypot(it.x - e.x, it.z - e.z)) }
        val floors = listOfNotNull(data.anchors.zone(rules.arenaZone)?.box)
        val felt = e.feltNoises(world, floors) + clangs
        val navigation = if (e.fightNavBlocked) VibrationCreatureBrain.Nav.BLOCKED else VibrationCreatureBrain.Nav.MOVING
        val wall = e.horizontalCollision
        val d = fight.tick(
            StoneMawFight.Senses(now, self, home, centre, felt, target, navigation, wall = wall, pillar = if (wall) pillarNear(e) else null), rules, creature,
        )
        if (d.mode != lastMode) {
            EveryTunnel.LOGGER.info("Stone Maw: {} -> {} ({} pillars broken)", lastMode, d.mode, fight.broken)
            lastMode = d.mode
        }
        e.fightPose(d.state)
        when {
            d.straight -> e.fightRam(d.heading!!, d.speed)
            d.target == null || d.speed <= 0.0 -> {
                e.fightStand(d.target)
                if (d.mode == StoneMawFight.Mode.STUNNED || d.mode == StoneMawFight.Mode.DEAD) e.velocity = Vec3d(0.0, e.velocity.y, 0.0)
            }
            else -> e.fightSteer(d.target, d.speed)
        }
        if (d.oriented) e.fightSound(Ch2Sounds.CH2L2_CREATURE_CLICK, 1.0f)
        if (d.windup) e.fightSound(Ch2Sounds.CH2L2_CREATURE_WINDUP, 1.2f)
        if (d.struck) {
            e.fightSound(Ch2Sounds.CH2L2_CREATURE_STRIKE, 1.2f)
            d.hit?.let { id -> (world.getPlayerByUuid(id) as? ServerPlayerEntity)?.let(e::fightHurt) }
        }
        if (d.rammed) rammed(world, e, d.pillarHit)
        if (d.pillarHit != null) {
            applyOutcome(world, StoneMawFight.PillarOutcome(d.pillarHit, fight.pillarDamage(d.pillarHit), d.pillarBroken, d.collapse), now, e)
        }
        if (d.mode == StoneMawFight.Mode.STUNNED && e.age % STUN_SOUND_TICKS == 0) e.fightSound(Ch2Sounds.CH2L2_MAW_STUNNED, 0.9f)
    }

    /** The pillar the creature has run into: the nearest whose face is within `maw.pillars.hit_radius` of it (a broken one too: the fight tells a stump from a pillar). */
    private fun pillarNear(e: VibrationCreatureEntity): Int? {
        var best: Int? = null
        var bestDistance = Double.MAX_VALUE
        for ((i, g) in pillars().withIndex()) {
            val dx = maxOf(g.minX - e.x, 0.0, e.x - (g.minX + 2.0))
            val dz = maxOf(g.minZ - e.z, 0.0, e.z - (g.minZ + 2.0))
            val distance = hypot(dx, dz)
            if (distance <= rules.pillarHitRadius && distance < bestDistance) {
                best = i
                bestDistance = distance
            }
        }
        return best
    }

    private fun rammed(world: ServerWorld, e: VibrationCreatureEntity, pillar: Int?) {
        if (pillar != null) {
            world.playSound(null, e.x, e.y, e.z, Ch2Sounds.CH2L2_MAW_PILLAR_RAM, SoundCategory.HOSTILE, 2.0f, 1.0f)
            shake(world, Vec3d(e.x, e.y, e.z), 0.9, 16)
            EveryTunnel.LOGGER.info("Stone Maw rams pillar {} at ({}, {})", pillar + 1, e.blockX, e.blockZ)
        } else {
            world.playSound(null, e.x, e.y, e.z, Ch2Sounds.CH2L2_MAW_WALL_RAM, SoundCategory.HOSTILE, 1.6f, 1.0f)
            EveryTunnel.LOGGER.info("Stone Maw rams a wall at ({}, {}): stunned", e.blockX, e.blockZ)
        }
        val dust = BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE.defaultState)
        world.spawnParticles(dust, e.x, e.y + 1.5, e.z, 50, 1.0, 1.2, 1.0, 0.15)
    }

    // --- the pieces of pipe ---------------------------------------------------------------------------------

    /** A thrown piece struck Stone Maw: a hit if it can be hit. */
    fun pieceHit(piece: ThrownPipeEntity, creature: VibrationCreatureEntity) {
        val world = piece.world as? ServerWorld ?: return
        if (phase != Phase.FIGHT || !fight.hit()) return
        world.playSound(null, piece.x, piece.y, piece.z, Ch2Sounds.CH2L2_MAW_HIT, SoundCategory.HOSTILE, 1.6f, 0.95f + world.random.nextFloat() * 0.1f)
        world.spawnParticles(
            BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE.defaultState), piece.x, piece.y, piece.z, 25, 0.3, 0.3, 0.3, 0.12,
        )
        EveryTunnel.LOGGER.info("Stone Maw is hit ({} hits, it was {})", fight.hits, fight.mode)
    }

    /** A thrown piece broke on a block: its clang, a loud vibration where it landed. */
    fun pieceBroke(piece: ThrownPipeEntity) {
        val world = piece.world as? ServerWorld ?: return
        world.playSound(null, piece.x, piece.y, piece.z, Ch2Sounds.CH2L2_MAW_PIPE_BREAK, SoundCategory.BLOCKS, 1.4f, 1.0f)
        world.spawnParticles(ParticleTypes.CRIT, piece.x, piece.y, piece.z, 12, 0.2, 0.2, 0.2, 0.2)
        if (phase == Phase.FIGHT) {
            (piece.owner as? ServerPlayerEntity)?.let(Ch2Achievements::pipeMissed)
            clang(piece.x, piece.y, piece.z, world.server.ticks.toLong())
        }
    }

    private fun clang(x: Double, y: Double, z: Double, now: Long) {
        clangs += VibrationCreatureBrain.Vibration(Position(x, y, z), now, VibrationCreatureBrain.Source.PIPE, VibrationCreatureBrain.Kind.LOUD)
    }

    private fun supplyTick(world: ServerWorld, now: Long) {
        // A piece that has landed clangs.
        val iterator = falling.iterator()
        while (iterator.hasNext()) {
            val piece = world.getEntity(iterator.next()) as? ItemEntity
            if (piece == null || piece.isRemoved) {
                iterator.remove()
            } else if (piece.isOnGround) {
                iterator.remove()
                world.playSound(null, piece.x, piece.y, piece.z, Ch2Sounds.CH2L2_PIPE_FALL, SoundCategory.BLOCKS, 1.5f, 1.0f)
                world.spawnParticles(BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE.defaultState), piece.x, piece.y + 0.2, piece.z, 14, 0.3, 0.1, 0.3, 0.08)
                clang(piece.x, piece.y, piece.z, now)
            }
        }
        val points = rules.pipePoints.mapNotNull { position(it) }
        val player = fighter?.let { world.getPlayerByUuid(it) }
        val creature = world.getEntitiesByType(Ch2Entities.VIBRATION_CREATURE, arenaBox()) { it.inFight && !it.isRemoved }.firstOrNull()
        val pick = supply.tick(
            now, piecesIn(world), points, creature?.let { Position(it.x, it.y, it.z) }, player?.let { Position(it.x, it.y, it.z) }, rules,
        ) ?: return
        val from = points[pick]
        dropPiece(world, from.x, from.y, from.z)
    }

    /** A piece falls from (x, y, z): its crumble and groan as it comes away, dust, and its clang when it lands. */
    private fun dropPiece(world: ServerWorld, x: Double, y: Double, z: Double) {
        val piece = ItemEntity(world, x, y, z, ItemStack(Ch2Items.PIPE_PIECE))
        piece.setVelocity(0.0, 0.0, 0.0)
        piece.setPickupDelay(PICKUP_DELAY_TICKS)
        piece.setNeverDespawn()
        world.spawnEntity(piece)
        falling += piece.uuid
        world.playSound(null, x, y, z, Ch2Sounds.CH2L2_MAW_PIPE_FALL, SoundCategory.BLOCKS, 1.3f, 1.0f)
        world.spawnParticles(BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.GRAY_CONCRETE_POWDER.defaultState), x, y + 0.5, z, 20, 0.3, 0.1, 0.3, 0.0)
    }

    /** The first piece falls [MawRules.firstPieceOffset] blocks in front of the player, from the height of the ceiling points. */
    private fun firstPiece(world: ServerWorld, player: ServerPlayerEntity) {
        val height = rules.pipePoints.firstNotNullOfOrNull { position(it) }?.y ?: (player.y + FIRST_PIECE_HEIGHT)
        val look = Vec3d.fromPolar(0f, player.yaw).multiply(rules.firstPieceOffset)
        dropPiece(world, player.x + look.x, height, player.z + look.z)
        supply.dropped()
    }

    /** Pieces there are now: lying or falling in the arena, in flight, or in a pocket. */
    private fun piecesIn(world: ServerWorld): Int {
        val box = arenaBox()
        var n = world.getEntitiesByType(net.minecraft.entity.EntityType.ITEM, box) { it.stack.isOf(Ch2Items.PIPE_PIECE) }.sumOf { it.stack.count }
        n += world.getEntitiesByType(Ch2Entities.THROWN_PIPE, box) { !it.isRemoved }.size
        for (player in world.players) n += heldPieces(player)
        return n
    }

    private fun heldPieces(player: ServerPlayerEntity): Int {
        var n = 0
        for (slot in 0 until player.inventory.size()) if (player.inventory.getStack(slot).isOf(Ch2Items.PIPE_PIECE)) n += player.inventory.getStack(slot).count
        return n
    }

    private fun takePieces(world: ServerWorld) {
        for (player in world.players) {
            for (slot in 0 until player.inventory.size()) if (player.inventory.getStack(slot).isOf(Ch2Items.PIPE_PIECE)) player.inventory.removeStack(slot)
        }
        for (e in world.getEntitiesByType(net.minecraft.entity.EntityType.ITEM, arenaBox()) { it.stack.isOf(Ch2Items.PIPE_PIECE) }) e.discard()
        for (e in world.getEntitiesByType(Ch2Entities.THROWN_PIPE, arenaBox()) { true }) e.discard()
        falling.clear()
    }

    /** The one-time hint on picking up the first piece. */
    private fun hint(player: ServerPlayerEntity) {
        if (Ch2Progress.ch2L2Counter(player, HINT_KEY) > 0 || heldPieces(player) == 0) return
        Ch2Progress.setCh2L2Counter(player, HINT_KEY, 1)
        player.sendMessage(Text.translatable("every_tunnel.ch2.l2.maw.hint_throw"), true)
    }

    // --- the music ------------------------------------------------------------------------------------

    /**
     * The hunt's music (the Guard's: `music.ch2l2.guard_chase`) is played to the fighter every [CHASE_MUSIC_TICKS] from the fight's start. The game
     * caps a sound's volume at 1, so it is played a second time at half volume at the same moment: about 3.5 dB more (as the Guard's is).
     */
    private fun music(world: ServerWorld, now: Long) {
        val start = musicStart ?: return
        if ((now - start) % CHASE_MUSIC_TICKS != 0L) return
        val player = fighter?.let { world.getPlayerByUuid(it) as? ServerPlayerEntity } ?: return
        val entry = RegistryEntry.of(SoundEvent.of(CHASE_MUSIC))
        val seed = world.random.nextLong()
        player.networkHandler.sendPacket(PlaySoundFromEntityS2CPacket(entry, SoundCategory.MUSIC, player, 1.0f, 1.0f, seed))
        player.networkHandler.sendPacket(PlaySoundFromEntityS2CPacket(entry, SoundCategory.MUSIC, player, 0.5f, 1.0f, seed))
    }

    /** The music cut, for everybody. */
    private fun stopMusic(server: MinecraftServer) {
        if (musicStart == null) return
        musicStart = null
        for (player in server.playerManager.playerList) player.networkHandler.sendPacket(StopSoundS2CPacket(CHASE_MUSIC, SoundCategory.MUSIC))
    }

    // --- the jump -------------------------------------------------------------------------------------

    private fun tellJump(player: ServerPlayerEntity) {
        val blocked = jumpBlocked(player)
        if (blocked == (player.uuid in jumpTold)) return
        if (blocked) jumpTold += player.uuid else jumpTold -= player.uuid
        sendJump(player, blocked)
    }

    private fun sendJump(player: ServerPlayerEntity, blocked: Boolean) {
        if (ServerPlayNetworking.canSend(player, JUMP_PACKET)) {
            val buf = PacketByteBufs.create()
            buf.writeBoolean(blocked)
            ServerPlayNetworking.send(player, JUMP_PACKET, buf)
        }
    }

    // --- the pillars ------------------------------------------------------------------------------------

    /** A pillar's footprint: the 2 x 2 cells from ([minX], [minZ]); [centre] its middle (the anchor, at half its height). */
    private class PillarGeo(val minX: Int, val minZ: Int, val centre: Vec3d)

    private fun pillars(): List<PillarGeo> = rules.pillars.mapNotNull { id ->
        position(id)?.let { PillarGeo(floor(it.x).toInt() - 1, floor(it.z).toInt() - 1, Vec3d(it.x, floorY() + PILLAR_HEIGHT / 2.0, it.z)) }
    }

    private fun floorY(): Int = floor(position(rules.home)?.y ?: 0.0).toInt()

    private fun block(id: String): BlockState = Registries.BLOCK.get(Identifier(id)).defaultState

    /** Pillar [i] as it looks at [stage]: 0 whole, 1 cracked (other bricks over its middle), 2 broken (a stump of rubble two high, nothing above). */
    private fun setPillar(world: ServerWorld, i: Int, stage: Int) {
        val g = pillars().getOrNull(i) ?: return
        val whole = block("every_tunnel:catacomb_brick")
        val cracked = block("every_tunnel:catacomb_brick_cracked")
        val damp = block("every_tunnel:catacomb_brick_damp")
        val y0 = floorY()
        for (dx in 0..1) for (dz in 0..1) for (dy in 0 until PILLAR_HEIGHT) {
            val state = when (stage) {
                0 -> whole
                1 -> when {
                    dy in 2..4 && (dx + dz + dy) % 2 == 0 -> cracked
                    dy == 3 -> damp
                    (dx + dz * 2 + dy * 3) % 4 == 0 -> cracked
                    else -> whole
                }
                else -> if (dy < STUMP_HEIGHT) cracked else Blocks.AIR.defaultState
            }
            val pos = BlockPos(g.minX + dx, y0 + dy, g.minZ + dz)
            if (world.getBlockState(pos) != state) world.setBlockState(pos, state, Block.NOTIFY_LISTENERS)
        }
        if (i in shown.indices) shown[i] = stage
    }

    /** What a blow to a pillar did, in the world: it cracks, or breaks (and the ceiling comes down if it was the last). */
    private fun applyOutcome(world: ServerWorld, o: StoneMawFight.PillarOutcome, now: Long, creature: VibrationCreatureEntity?) {
        if (o.broken) breakPillar(world, o.index, debris = !o.collapse) else crackPillar(world, o.index)
        if (o.collapse) beginCollapse(world, now, creature, o.index)
    }

    private fun crackPillar(world: ServerWorld, index: Int) {
        val g = pillars().getOrNull(index) ?: return
        setPillar(world, index, 1)
        world.playSound(null, g.centre.x, g.centre.y, g.centre.z, Ch2Sounds.CH2L2_MAW_PILLAR_CRACK, SoundCategory.BLOCKS, 1.8f, 1.0f)
        world.spawnParticles(BlockStateParticleEffect(ParticleTypes.BLOCK, block("every_tunnel:catacomb_brick_cracked")), g.centre.x, g.centre.y, g.centre.z, 40, 0.7, 1.2, 0.7, 0.12)
        world.spawnParticles(BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.GRAY_CONCRETE_POWDER.defaultState), g.centre.x, g.centre.y + 1.0, g.centre.z, 40, 0.8, 1.5, 0.8, 0.0)
    }

    /** Dust and pebbles keep falling at the crack of a cracked pillar. */
    private fun crackedPillars(world: ServerWorld, now: Long) {
        if (now % CRACK_EVERY_TICKS != 0L) return
        for ((i, g) in pillars().withIndex()) {
            if (fight.pillarDamage(i) != 1 || fight.isBroken(i)) continue
            world.spawnParticles(BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.GRAY_CONCRETE_POWDER.defaultState), g.centre.x, g.centre.y + 0.5, g.centre.z, 3, 0.8, 0.3, 0.8, 0.0)
            world.spawnParticles(BlockStateParticleEffect(ParticleTypes.BLOCK, block("every_tunnel:catacomb_brick_cracked")), g.centre.x, g.centre.y, g.centre.z, 1, 0.9, 0.2, 0.9, 0.0)
        }
    }

    /** A pillar giving way: its blocks fall apart, a stump stays, the stone speaks, the camera shakes and (if [debris]) the ceiling round it starts to come away. */
    private fun breakPillar(world: ServerWorld, index: Int, debris: Boolean) {
        val g = pillars().getOrNull(index) ?: return
        val y0 = floorY()
        val random = Random(index * 31 + world.server.ticks)
        for (dx in 0..1) for (dz in 0..1) for (dy in STUMP_HEIGHT until PILLAR_HEIGHT) {
            val pos = BlockPos(g.minX + dx, y0 + dy, g.minZ + dz)
            val state = world.getBlockState(pos)
            if (state.isAir) continue
            val piece = FallingBlockEntity.spawnFromBlock(world, pos, state)
            piece.dropItem = false
            piece.setHurtEntities(0f, 0)
            piece.setDestroyedOnLanding()
            piece.setVelocity((random.nextDouble() - 0.5) * 0.3, 0.05 + random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.3)
            piece.velocityModified = true
        }
        setPillar(world, index, 2)
        world.playSound(null, g.centre.x, g.centre.y, g.centre.z, Ch2Sounds.CH2L2_MAW_PILLAR_BREAK, SoundCategory.BLOCKS, 2.2f, 1.0f)
        world.spawnParticles(BlockStateParticleEffect(ParticleTypes.BLOCK, block("every_tunnel:catacomb_brick")), g.centre.x, g.centre.y, g.centre.z, 90, 0.9, 1.8, 0.9, 0.2)
        world.spawnParticles(BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.GRAY_CONCRETE_POWDER.defaultState), g.centre.x, g.centre.y, g.centre.z, 160, 1.4, 2.0, 1.4, 0.05)
        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, g.centre.x, y0 + 1.0, g.centre.z, 20, 1.2, 0.4, 1.2, 0.04)
        shake(world, g.centre, 1.0, 30)
        if (debris) scatterDebris(world, g, world.server.ticks.toLong(), random)
        EveryTunnel.LOGGER.info("Stone Maw: pillar {} breaks", index + 1)
    }

    // --- the ceiling's pieces ------------------------------------------------------------------------------

    /** [MawRules.debris] pieces of the ceiling over the next `spread_ticks`, round the pillar ([g]) that broke. */
    private fun scatterDebris(world: ServerWorld, g: PillarGeo, now: Long, random: Random) {
        val d = rules.debris
        for (k in 0 until d.count) {
            val angle = random.nextDouble() * Math.PI * 2
            val distance = 1.6 + random.nextDouble() * (d.radius - 1.6).coerceAtLeast(0.5)
            val offset = (k * d.spreadTicks / d.count.coerceAtLeast(1)) + random.nextInt(0, 4)
            addRock(world, Math.floor(g.centre.x + Math.cos(angle) * distance).toInt(), Math.floor(g.centre.z + Math.sin(angle) * distance).toInt(), now + offset + d.warnTicks)
        }
    }

    private fun addRock(world: ServerWorld, x: Int, z: Int, landAt: Long) {
        if (!world.getBlockState(BlockPos(x, floorY(), z)).isAir) return
        val fall = MawCollapse.fallTicks((ROCK_START_ABOVE_FLOOR).toDouble(), 0.0)
        val warnAt = landAt - rules.debris.warnTicks
        rocks += Rock(warnAt, maxOf(warnAt, landAt - fall), landAt, x, z)
    }

    /** The announcements (dust on the floor, a small crumble), the falling pieces and what they hurt where they land. */
    private fun rocks(world: ServerWorld, now: Long) {
        val y = floorY()
        val iterator = rocks.iterator()
        while (iterator.hasNext()) {
            val r = iterator.next()
            if (now < r.warnAt) continue
            if (!r.warned) {
                r.warned = true
                world.playSound(null, r.x + 0.5, y + 1.0, r.z + 0.5, Ch2Sounds.CH2L2_MAW_DEBRIS_WARN, SoundCategory.BLOCKS, 1.2f, 0.9f + world.random.nextFloat() * 0.2f)
            }
            if (now % WARN_PARTICLE_TICKS == 0L) {
                world.spawnParticles(BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE_POWDER.defaultState), r.x + 0.5, y + 0.15, r.z + 0.5, 6, 0.4, 0.05, 0.4, 0.02)
                world.spawnParticles(BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.GRAY_CONCRETE_POWDER.defaultState), r.x + 0.5, y + PILLAR_HEIGHT - 0.5, r.z + 0.5, 3, 0.35, 0.1, 0.35, 0.0)
            }
            if (!r.dropped && now >= r.dropAt) {
                r.dropped = true
                val blocks = rubbleBlocks()
                val pos = BlockPos(r.x, y + ROCK_START_ABOVE_FLOOR, r.z)
                val piece = FallingBlockEntity.spawnFromBlock(world, pos, blocks[Random(r.x * 31 + r.z).nextInt(blocks.size)])
                piece.dropItem = false
                piece.setHurtEntities(0f, 0)
                piece.setDestroyedOnLanding()
            }
            if (now >= r.landAt) {
                iterator.remove()
                land(world, r)
            }
        }
    }

    private fun land(world: ServerWorld, r: Rock) {
        val y = floorY()
        world.spawnParticles(BlockStateParticleEffect(ParticleTypes.BLOCK, rubbleBlocks().random()), r.x + 0.5, y + 0.4, r.z + 0.5, 22, 0.35, 0.15, 0.35, 0.1)
        world.spawnParticles(ParticleTypes.POOF, r.x + 0.5, y + 0.3, r.z + 0.5, 4, 0.3, 0.1, 0.3, 0.02)
        val box = Box(r.x - 0.2, y - 0.5, r.z - 0.2, r.x + 1.2, y + 3.0, r.z + 1.2)
        for (p in world.getEntitiesByClass(ServerPlayerEntity::class.java, box) { it.isAlive && !it.isSpectator && !DeathHooks.isDying(it) }) {
            p.damage(world.damageSources.fallingBlock(null), (rules.debris.damageFraction * p.maxHealth).toFloat())
        }
    }

    // --- the death and the escape ------------------------------------------------------------------------------

    /** One piece of [MawCollapse]'s plan still to fall: it starts at server tick [at] and reaches the stone at [lands]. */
    private class Fall(val at: Long, val lands: Long, val piece: MawCollapse.Piece, var fell: Boolean = false)

    private val coming = ArrayList<Fall>()
    private var crushAt: Long? = null
    private var smashAt: Long? = null
    private var moundX = 0
    private var moundZ = 0
    private var lost = false

    /**
     * The last pillar is down: the ceiling comes down on Stone Maw where it stands (its stunned body against the pillar; [MawCollapse]'s plan
     * centred on it), the music is cut dry, the state goes to [Ch2L2State.MAW_ESCAPE] and its checkpoint is taken (with the pillar and the
     * mound in the counters, so that CONTINUE puts the arena back), the far door opens and the arena starts caving in.
     */
    private fun beginCollapse(world: ServerWorld, now: Long, creature: VibrationCreatureEntity?, lastPillar: Int) {
        if (phase == Phase.ESCAPE) return
        phase = Phase.ESCAPE
        stopMusic(world.server)
        takePieces(world)
        val g = pillars().getOrNull(lastPillar)
        val at = creature?.let { Vec3d(it.x, it.y, it.z) } ?: defaultMound(lastPillar)
        moundX = floor(at.x).toInt()
        moundZ = floor(at.z).toInt()
        moundPlaced = false
        val plan = MawCollapse.plan(rules, MawCollapse.SEED)
        val landings = MawCollapse.landings(plan, rules.slabStartSpeed)
        coming.clear()
        for ((i, piece) in plan.withIndex()) coming += Fall(now + piece.delayTicks, now + landings[i], piece)
        crushAt = now + rules.firstSlabDelayTicks + MawCollapse.fallTicks(MawCollapse.TOP_CELL - rules.crushHeight, rules.slabStartSpeed)
        // Whoever stands where the first slabs come down is thrown clear of them (and hurt a little: it is the ceiling).
        for (p in world.players) {
            val dx = p.x - at.x
            val dz = p.z - at.z
            val distance = hypot(dx, dz)
            if (!p.isAlive || distance > rules.collapseRadius + 1.5) continue
            val away = if (distance < 0.1) Vec3d(1.0, 0.0, 0.0) else Vec3d(dx / distance, 0.0, dz / distance)
            p.addVelocity(away.x * 0.9, 0.35, away.z * 0.9)
            p.velocityModified = true
            p.damage(world.damageSources.fallingBlock(null), (rules.debris.damageFraction * p.maxHealth).toFloat())
        }
        world.playSound(null, at.x, at.y + 2.0, at.z, Ch2Sounds.CH2L2_MAW_COLLAPSE, SoundCategory.BLOCKS, 2.5f, 1.0f)
        world.spawnParticles(BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE.defaultState), at.x, floorY() + CEILING_OFFSET - 0.5, at.z, 80, 2.0, 0.3, 2.0, 0.15)
        val player = fighter?.let { world.getPlayerByUuid(it) as? ServerPlayerEntity }
        if (player != null) {
            Ch2Progress.setCh2L2Counter(player, LAST_PILLAR_KEY, lastPillar)
            Ch2Progress.setCh2L2Counter(player, MOUND_X_KEY, moundX)
            Ch2Progress.setCh2L2Counter(player, MOUND_Z_KEY, moundZ)
            Ch2L2.setState(player, Ch2L2State.MAW_ESCAPE, "the last pillar breaks")
            Ch2L2.takeCheckpoint(player, ESCAPE_CHECKPOINT)
        }
        openFarDoor(world)
        startEscape(world, now)
        EveryTunnel.LOGGER.info("Stone Maw: the ceiling comes down on it at ({}, {}): the escape begins ({} pieces)", moundX, moundZ, plan.size)
    }

    /** The far door opens (its sound, once). */
    private fun openFarDoor(world: ServerWorld) {
        Ch2L2Build.setGate(world, rules.entryGate, "open")
        if (!Ch2L2Build.gateIs(world, rules.farGate, "open")) {
            data.anchors.point(rules.farAnchor)?.position?.let { world.playSound(null, it.x, it.y, it.z, Ch2Sounds.CH2L2_EXIT_DOOR, SoundCategory.BLOCKS, 1.5f, 1.0f) }
        }
        Ch2L2Build.setGate(world, rules.farGate, "open")
    }

    /** The arena starts caving in: the rumble, the shaking and the plan of the falling pieces ([MawEscape]). */
    private fun startEscape(world: ServerWorld, now: Long) {
        escapeStart = now
        escapeShaken = now
        lost = false
        rocks.clear()
        val arena = data.anchors.zone(rules.arenaZone)?.box ?: return
        val door = data.anchors.point(rules.farAnchor)?.position
        val plan = MawEscape.plan(rules, MawEscape.Arena(arena.minX, arena.minZ, arena.maxX, arena.maxZ, arena.minX, floor(door?.z ?: (arena.minZ + arena.maxZ) / 2.0).toInt()), ESCAPE_SEED)
        for (d in plan) addRock(world, d.x, d.z, now + d.tick + rules.debris.warnTicks)
        val mid = Vec3d((arena.minX + arena.maxX + 1) / 2.0, floorY() + 2.0, (arena.minZ + arena.maxZ + 1) / 2.0)
        world.playSound(null, mid.x, mid.y, mid.z, Ch2Sounds.CH2L2_MAW_ESCAPE_RUMBLE, SoundCategory.BLOCKS, 2.5f, 1.0f)
        shake(world, mid, 0.9, 40)
    }

    /** The escape's clock: the shaking, the way out (the far door slams behind who is through) and the end of the time. */
    private fun escaping(server: MinecraftServer, world: ServerWorld, now: Long) {
        val player = fighter?.let { world.getPlayerByUuid(it) as? ServerPlayerEntity } ?: return
        if (!player.isAlive || DeathHooks.isDying(player)) return
        if (now - escapeShaken >= ESCAPE_SHAKE_EVERY_TICKS) {
            escapeShaken = now
            val arena = data.anchors.zone(rules.arenaZone)?.box
            if (arena != null) shake(world, Vec3d((arena.minX + arena.maxX + 1) / 2.0, floorY() + 2.0, (arena.minZ + arena.maxZ + 1) / 2.0), 0.3, 14)
        }
        val door = data.anchors.point(rules.farAnchor)?.position
        if (door != null && player.x < door.x - THROUGH_DOOR_MARGIN && Math.abs(player.y - door.y) < 4.0) {
            finishDeath(server)
            return
        }
        val inArena = inZone(player, rules.escape.zone)
        if (!lost && MawEscape.isLost(rules, (now - escapeStart).toInt(), inArena)) {
            lost = true
            world.playSound(null, player.x, player.y, player.z, Ch2Sounds.CH2L2_MAW_COLLAPSE, SoundCategory.BLOCKS, 2.5f, 1.0f)
            world.spawnParticles(BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE.defaultState), player.x, player.y + 2.0, player.z, 120, 1.5, 1.0, 1.5, 0.2)
            player.damage(world.damageSources.fallingBlock(null), Float.MAX_VALUE)
            EveryTunnel.LOGGER.info("Chapter 2 Level 2: {} is still in the arena as it caves in", player.gameProfile.name)
        }
    }

    /** The slabs of [MawCollapse]'s plan falling, the creature crushed by the first and gone under the burst. */
    private fun collapsing(world: ServerWorld, now: Long) {
        if (coming.isNotEmpty()) fall(world, now)
        crushAt?.let { at ->
            if (now >= at) {
                crushAt = null
                creatures(world).forEach { it.fightDeathPose(VibrationCreatureEntity.POSE_CRUSHED) }
                smashAt = now + SMASH_DELAY_TICKS
            }
        }
        smashAt?.let { at ->
            if (now >= at) {
                smashAt = null
                smash(world)
            }
        }
        if (coming.isNotEmpty() && coming.all { it.fell && now > it.lands }) coming.clear()
    }

    private fun fall(world: ServerWorld, now: Long) {
        val y = floorY()
        val blocks = rubbleBlocks()
        val top = BlockPos(moundX, y + MawCollapse.TOP_CELL, moundZ)
        for (f in coming) {
            if (f.fell || now < f.at) continue
            f.fell = true
            // A slab falls as one piece: all its blocks leave the ceiling in this tick, thrown downwards.
            for ((dx, dz) in f.piece.cells) {
                val pos = top.add(dx, 0, dz)
                if (!world.getBlockState(pos).isAir) continue
                val block = FallingBlockEntity.spawnFromBlock(world, pos, blocks[Random(RUBBLE_SEED + pos.hashCode()).nextInt(blocks.size)])
                block.dropItem = false
                block.setHurtEntities(0f, 0)
                block.setVelocity(0.0, -rules.slabStartSpeed, 0.0)
                block.velocityModified = true
            }
            val slab = f.piece.kind == MawCollapse.Kind.SLAB
            val dust = BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.GRAY_CONCRETE_POWDER.defaultState)
            world.spawnParticles(dust, top.x + 0.5, top.y + 0.8, top.z + 0.5, if (slab) 40 else 6, if (slab) 2.0 else 0.3, 0.2, if (slab) 2.0 else 0.3, 0.0)
        }
        // The landing: a puff of dust and chips where each piece reaches the stone, bigger for a slab.
        for (f in coming) {
            if (!f.fell || now != f.lands) continue
            val slab = f.piece.kind == MawCollapse.Kind.SLAB
            val x = moundX + 0.5 + f.piece.cells.map { it.first }.average()
            val z = moundZ + 0.5 + f.piece.cells.map { it.second }.average()
            val state = blocks[Random(RUBBLE_SEED + f.at.toInt()).nextInt(blocks.size)]
            world.spawnParticles(BlockStateParticleEffect(ParticleTypes.BLOCK, state), x, y + 0.6, z, if (slab) 50 else 14, if (slab) 1.5 else 0.4, 0.2, if (slab) 1.5 else 0.4, if (slab) 0.12 else 0.06)
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.8, z, if (slab) 6 else 1, if (slab) 1.2 else 0.3, 0.1, if (slab) 1.2 else 0.3, 0.02)
            if (slab) shake(world, Vec3d(x, y.toDouble(), z), 0.6, 10)
        }
    }

    /** The big burst of dust and chips over the creature, and the creature gone under it. */
    private fun smash(world: ServerWorld) {
        val y = floorY()
        val x = moundX + 0.5
        val z = moundZ + 0.5
        for (state in rubbleBlocks()) {
            world.spawnParticles(BlockStateParticleEffect(ParticleTypes.BLOCK, state), x, y + 0.8, z, 60, 1.4, 0.5, 1.4, 0.25)
        }
        world.spawnParticles(BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.GRAY_CONCRETE_POWDER.defaultState), x, y + 1.0, z, 120, 1.6, 0.8, 1.6, 0.1)
        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.8, z, 24, 1.4, 0.4, 1.4, 0.06)
        world.spawnParticles(ParticleTypes.POOF, x, y + 0.8, z, 30, 1.4, 0.5, 1.4, 0.12)
        creatures(world).forEach { it.discard() }
        shake(world, Vec3d(x, y.toDouble(), z), 1.0, 24)
    }

    /**
     * The one end of the fight, idempotent (the escape's: the far door slamming behind the player; `skip maw`; the debug `kill` once it ends):
     * Stone Maw gone, the pillars stumps, the mound on the spot, the pieces gone, the player in [Ch2L2State.MAW_DEAD]. The far door slams shut if the
     * player is past it (the arena is gone for good), else it is left open so that a skip can walk through.
     */
    fun finishDeath(server: MinecraftServer) {
        if (phase == Phase.DEAD) return
        phase = Phase.DEAD
        entering = false
        burstAt = null
        stopMusic(server)
        stopRumble(server)
        val world = levelWorld(server) ?: return
        creatures(world).forEach { it.discard() }
        takePieces(world)
        rocks.clear()
        coming.clear()
        crushAt = null
        smashAt = null
        for (e in world.getEntitiesByType(net.minecraft.entity.EntityType.FALLING_BLOCK, arenaBox()) { true }) e.discard()
        for (i in rules.pillars.indices) setPillar(world, i, 2)
        if (!moundPlaced) placeMound(world, moundX.takeIf { moundX != 0 } ?: floor(defaultMound(DEFAULT_LAST_PILLAR).x).toInt(), moundZ.takeIf { moundZ != 0 } ?: floor(defaultMound(DEFAULT_LAST_PILLAR).z).toInt())
        Ch2L2Build.setGate(world, rules.entryGate, "open")
        closeFarDoorBehind(world, slam = true)
        fighter?.let { id -> (world.getPlayerByUuid(id) as? ServerPlayerEntity)?.let { if (Ch2L2.state(it) < Ch2L2State.MAW_DEAD) Ch2L2.setState(it, Ch2L2State.MAW_DEAD, "Stone Maw is dead") } }
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: Stone Maw is dead; the arena is behind")
    }

    /** The far door shut if a player of the level is past it (west of it), open if none is. */
    private fun closeFarDoorBehind(world: ServerWorld, slam: Boolean) {
        val door = data.anchors.point(rules.farAnchor)?.position ?: return
        val behind = world.players.any { Ch2L2.active(it) && it.isAlive && it.x < door.x - 0.3 }
        if (behind && !Ch2L2Build.gateIs(world, rules.farGate, "shut")) {
            if (slam) {
                world.playSound(null, door.x, door.y, door.z, Ch2Sounds.CH2L2_FLOOD_GATE_SLAM, SoundCategory.BLOCKS, 2.0f, 1.0f)
                shake(world, Vec3d(door.x, door.y, door.z), 0.7, 14)
            }
            Ch2L2Build.setGate(world, rules.farGate, "shut")
        } else if (!behind) {
            Ch2L2Build.setGate(world, rules.farGate, "open")
        }
    }

    private fun stopRumble(server: MinecraftServer) {
        for (player in server.playerManager.playerList) player.networkHandler.sendPacket(StopSoundS2CPacket(Ch2Sounds.CH2L2_MAW_ESCAPE_RUMBLE.id, SoundCategory.BLOCKS))
    }

    // --- the level starts -------------------------------------------------------------------------------

    /** The arena as [state] wants it (see the class). */
    private fun started(player: ServerPlayerEntity, state: Ch2L2State) {
        val world = levelWorld(player.server) ?: return
        stopMusic(player.server)
        stopRumble(player.server)
        // A death in the arena: the client still thinks the jump is blocked and reset() forgets it was told, so it is told again.
        for (p in player.server.playerManager.playerList) if (p.uuid in jumpTold) sendJump(p, false)
        reset()
        takePieces(world)
        creatures(world).forEach { it.discard() }
        clearArena(world)
        val last = Ch2Progress.ch2L2Counter(player, LAST_PILLAR_KEY).takeIf { it in rules.pillars.indices } ?: DEFAULT_LAST_PILLAR
        val spot = defaultMound(last)
        moundX = Ch2Progress.ch2L2Counter(player, MOUND_X_KEY).takeIf { it != 0 } ?: floor(spot.x).toInt()
        moundZ = Ch2Progress.ch2L2Counter(player, MOUND_Z_KEY).takeIf { it != 0 } ?: floor(spot.z).toInt()
        val past = state >= Ch2L2State.MAW_ESCAPE
        for (i in rules.pillars.indices) setPillar(world, i, if (past) 2 else 0)
        if (past) placeMound(world, moundX, moundZ)
        phase = when {
            state >= Ch2L2State.MAW_DEAD -> Phase.DEAD
            state == Ch2L2State.MAW_ESCAPE -> Phase.ESCAPE
            else -> Phase.IDLE
        }
        Ch2L2Build.setGate(world, rules.entryGate, "open")
        Ch2L2Build.setGate(world, rules.mouthGate, if (past) "open" else "grate")
        Ch2L2Build.setGate(world, rules.exitGate, if (state >= Ch2L2State.MAW) "open" else "shut")
        when {
            state >= Ch2L2State.MAW_DEAD -> Ch2L2Build.setGate(world, rules.farGate, if (data.anchors.point(rules.farAnchor)?.position?.let { player.x < it.x } == true) "shut" else "open")
            state == Ch2L2State.MAW_ESCAPE -> {
                fighter = player.uuid
                Ch2L2Build.setGate(world, rules.farGate, "open")
                startEscape(world, player.server.ticks.toLong())
                EveryTunnel.LOGGER.info("Chapter 2 Level 2: the escape begins again for {}", player.gameProfile.name)
            }
            else -> Ch2L2Build.setGate(world, rules.farGate, "shut")
        }
    }

    /** All the state of a fight forgotten. */
    private fun reset() {
        musicStart = null
        phase = Phase.IDLE
        fighter = null
        entering = false
        burstAt = null
        exitOpenAt = null
        coming.clear()
        crushAt = null
        smashAt = null
        rocks.clear()
        moundPlaced = false
        lost = false
        lastMode = StoneMawFight.Mode.BRAIN
        falling.clear()
        clangs.clear()
        jumpTold.clear()
        shown.fill(0)
        fight.reset(rules)
        supply.reset()
    }

    // --- rubble -----------------------------------------------------------------------------------------------

    /** The mix of blocks the ceiling is made of when it falls. */
    private fun rubbleBlocks(): List<BlockState> =
        rules.collapseBlocks.mapNotNull { Identifier.tryParse(it)?.let(Registries.BLOCK::get)?.defaultState }.ifEmpty { listOf(Blocks.GRAY_CONCRETE.defaultState) }

    /** Where Stone Maw would be against pillar [index] with nothing to tell (CONTINUE with no counters, a skip): two blocks from it towards the arena's centre. */
    private fun defaultMound(index: Int): Vec3d {
        val g = pillars().getOrNull(index) ?: return Vec3d(0.0, floorY().toDouble(), 0.0)
        val centre = position(rules.centre) ?: return Vec3d(g.centre.x, floorY().toDouble(), g.centre.z)
        val dx = centre.x - g.centre.x
        val dz = centre.z - g.centre.z
        val length = hypot(dx, dz).coerceAtLeast(1e-6)
        return Vec3d(g.centre.x + dx / length * 2.5, floorY().toDouble(), g.centre.z + dz / length * 2.5)
    }

    /** The mound where the creature fell, at once (a skip, or the level started past its death): [MawCollapse.heights] of the plan, filling only air and never where a player stands. */
    private fun placeMound(world: ServerWorld, cx: Int, cz: Int) {
        moundPlaced = true
        val blocks = rubbleBlocks()
        val floor = BlockPos(cx, floorY(), cz)
        val heights = MawCollapse.heights(MawCollapse.plan(rules, MawCollapse.SEED), rules.maxHeight)
        for ((cell, height) in heights) {
            for (dy in 0 until height) {
                val pos = floor.add(cell.first, dy, cell.second)
                if (world.players.any { Math.abs(it.x - (pos.x + 0.5)) < 1.2 && Math.abs(it.z - (pos.z + 0.5)) < 1.2 && it.y - pos.y in -1.0..3.0 }) continue
                if (world.getBlockState(pos).isAir) world.setBlockState(pos, blocks[Random(RUBBLE_SEED + pos.hashCode()).nextInt(blocks.size)], Block.NOTIFY_ALL)
            }
        }
    }

    /** The arena's air free of everything a fight leaves: the rubble kinds (pillars and stumps are set again after), the falling blocks. */
    private fun clearArena(world: ServerWorld) {
        val box = data.anchors.zone(rules.arenaZone)?.box ?: return
        val kinds = rubbleBlocks().map { it.block } + block("every_tunnel:catacomb_brick").block
        val y0 = floorY()
        for (x in box.minX..box.maxX) for (z in box.minZ..box.maxZ) for (dy in 0 until PILLAR_HEIGHT) {
            val pos = BlockPos(x, y0 + dy, z)
            val state = world.getBlockState(pos)
            if (!state.isAir && kinds.any { state.isOf(it) }) world.setBlockState(pos, Blocks.AIR.defaultState, Block.NOTIFY_LISTENERS)
        }
        for (e in world.getEntitiesByType(net.minecraft.entity.EntityType.FALLING_BLOCK, arenaBox()) { true }) e.discard()
    }

    // --- helpers ----------------------------------------------------------------------------------------------

    /** A shake of the camera of everyone in the level ([strength] 1 is the strongest; weaker the farther they are from [at]). */
    internal fun shake(world: ServerWorld, at: Vec3d, strength: Double, ticks: Int) {
        for (p in world.players) {
            if (!ServerPlayNetworking.canSend(p, CollapseHooks.SHAKE_PACKET_ID)) continue
            val distance = p.pos.distanceTo(at)
            val s = (strength * (1.0 - distance / SHAKE_RANGE)).coerceAtLeast(strength * 0.25)
            val buf = PacketByteBufs.create()
            buf.writeFloat(s.toFloat())
            buf.writeVarInt(ticks)
            ServerPlayNetworking.send(p, CollapseHooks.SHAKE_PACKET_ID, buf)
        }
    }

    private fun creatures(world: ServerWorld) = world.iterateEntities().filterIsInstance<VibrationCreatureEntity>().filter { it.inFight }.toList()

    private fun inZone(player: ServerPlayerEntity, zone: String) = data.anchors.zone(zone)?.box?.contains(player.x, player.y, player.z) == true

    private fun position(anchor: String): Position? = data.anchors.point(anchor)?.position

    private fun arenaBox(): Box {
        val b = data.anchors.zone(rules.arenaZone)?.box ?: return Box(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        return Box(b.minX.toDouble(), b.minY.toDouble(), b.minZ.toDouble(), b.maxX + 1.0, b.maxY + 1.0, b.maxZ + 1.0)
    }

    private fun levelWorld(server: MinecraftServer): ServerWorld? =
        Identifier.tryParse(data.anchors.dimension)?.let { server.getWorld(RegistryKey.of(RegistryKeys.WORLD, it)) }

    private val CHASE_MUSIC = Identifier(EveryTunnel.MOD_ID, "music.ch2l2.guard_chase")
    private const val CHASE_MUSIC_TICKS = 256L
    private const val HINT_KEY = "maw_hint"
    private const val LAST_PILLAR_KEY = "maw_last_pillar"
    private const val MOUND_X_KEY = "maw_mound_x"
    private const val MOUND_Z_KEY = "maw_mound_z"
    private const val ESCAPE_CHECKPOINT = "maw_escape"
    private const val EXIT_DOOR_TICKS = 70L
    private const val BURST_DELAY_TICKS = 25L
    private const val ENTERING_DEPTH = 3.0
    private const val ENTERED_DISTANCE = 1.5
    private const val ENTERING_LIMIT_TICKS = 200L
    private const val STUN_SOUND_TICKS = 20
    private const val CLANG_TICKS = 5L
    private const val PICKUP_DELAY_TICKS = 15
    private const val FIRST_PIECE_HEIGHT = 5.0

    /** Blocks high a pillar is (floor to ceiling), how many of them stay as the stump of a broken one. */
    private const val PILLAR_HEIGHT = 7
    private const val STUMP_HEIGHT = 2

    /** A piece of ceiling starts this many blocks over the floor: the last cell of air under the lamps' row, so that a falling piece never takes a lamp's cell. */
    private const val ROCK_START_ABOVE_FLOOR = 5
    private const val MAX_PILLARS = 8
    private const val DEFAULT_LAST_PILLAR = 2
    private const val CRACK_EVERY_TICKS = 6L
    private const val WARN_PARTICLE_TICKS = 3L
    private const val ESCAPE_SEED = 17
    private const val ESCAPE_SHAKE_EVERY_TICKS = 10L
    private const val SHAKE_RANGE = 40.0

    /** Blocks past the far door's cell the player's feet must be for the door to slam behind them. */
    private const val THROUGH_DOOR_MARGIN = 0.8

    /** Ticks the crushed pose is seen before the burst hides it. */
    private const val SMASH_DELAY_TICKS = 3L
    private const val CEILING_OFFSET = 6.0
    private const val RUBBLE_SEED = 61
}
