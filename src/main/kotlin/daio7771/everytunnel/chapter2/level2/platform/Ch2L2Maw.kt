package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Flood
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Maw as MawRules
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import daio7771.everytunnel.chapter2.level2.core.MawCollapse
import daio7771.everytunnel.chapter2.level2.core.MawDeathScene
import daio7771.everytunnel.chapter2.level2.core.PipeSupply
import daio7771.everytunnel.chapter2.level2.core.StoneMawFight
import daio7771.everytunnel.chapter2.level2.core.VibrationCreatureBrain
import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.chapter2.platform.Ch2Items
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.platform.MovementRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.Block
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
import kotlin.random.Random

/**
 * Stone Maw's last stand, beyond the exit (Chapter 2 Level 2, phase 6.1), over `level.json`'s `maw` and the rules of
 * [StoneMawFight] / [PipeSupply]:
 *
 * - **The exit door** (`maw.exit_door`): reaching its zone from the station on plays its sound and, a moment later, opens the heavy
 *   door for good; the corridor leads to the arena.
 * - **The entrance** (state [Ch2L2State.MAW], set by walking into `zone_maw_middle`): the arena's door shuts behind the player, the grate
 *   over the pipe's mouth is torn off, Stone Maw ([VibrationCreatureEntity] in its fight mode, [VibrationCreatureEntity.inFight]) crawls
 *   out and rests at `maw.home`, and the first piece of pipe falls in front of the player.
 * - **The fight**: [tickBody] (called by the entity's tick) feeds [StoneMawFight] and acts on what it decides; the pieces are
 *   [ItemEntity]s that fall from the ceiling points ([supplyTick]; each landing is a loud vibration), picked up by walking over them
 *   and thrown with a right click ([PipePieceItem], [ThrownPipeEntity]): a hit on the creature is [pieceHit], anything else
 *   [pieceBroke] (a clang the creature goes to). The jump is blocked in the arena while it lasts ([MovementRules]: the server's rule
 *   here, the client's by [JUMP_PACKET]).
 * - **The death** (phase 6.2): the last hit has it limp to the centre ([VibrationCreatureEntity.POSE_LIMP]) and kneel with its hands flat
 *   on the floor ([VibrationCreatureEntity.POSE_DYING], facing the far wall) while a short cinematic ([Ch2L2GuardScenes.MAW_DEATH]) runs on
 *   the clock of [MawDeathScene]; the ceiling comes down on it ([collapse] spawns the pieces of [MawCollapse]'s plan as falling blocks:
 *   small bits, then big slabs, the first of which crushes it at `impactAt`: [crush], then the creature is gone under the burst), then
 *   [finishDeath] (one idempotent end, also what a skip runs): the creature gone, the mound of the plan left where it stood, the far door
 *   open, the state [Ch2L2State.MAW_DEAD].
 * - **The music** (phase 6.2): the Observant Guard's hunt music, recycled (`music.ch2l2.guard_chase`, 256 ticks, seamless), played again every
 *   [CHASE_MUSIC_TICKS] from the arena's door shutting until the killing hit, which cuts it dry (the death scene has only the stone's sound).
 * - **Starts** ([Ch2L2.onStart]: entering, a death, CONTINUE, `reset`): everything put as the state wants it (fight over and reset,
 *   the gates, the rubble, the pieces gone from the pockets).
 *
 * One fight at a time (the level is a single-player one). Server thread only.
 */
object Ch2L2Maw {

    /** To the client: whether the jump is blocked (the arena, while the fight lasts). */
    val JUMP_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l2_jump_block")

    private val data get() = Ch2L2DataLoader.data
    private val rules get() = data.level.maw

    private enum class Phase { IDLE, FIGHT, DYING, DEAD }

    private val fight = StoneMawFight()
    private val supply = PipeSupply()
    private var phase = Phase.IDLE
    private var fighter: UUID? = null
    private var entering = false
    private var enteringSince = 0L
    private var deathStart = 0L
    private var collapsed = false
    private var lastMode = StoneMawFight.Mode.BRAIN
    private var exitOpenAt: Long? = null
    private var burstAt: Long? = null
    private var musicStart: Long? = null

    /** The pieces that are falling, by entity id (their landing is a clang). */
    private val falling = HashSet<UUID>()
    private val clangs = ArrayList<VibrationCreatureBrain.Vibration>()
    private val jumpTold = HashSet<UUID>()

    /** The client's knowledge that its jump is blocked (set by [JUMP_PACKET]; client thread only). */
    @Volatile
    var clientJumpBlocked = false

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
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
    fun describe(): String = "maw: $phase hits left ${fight.hitsLeft} mode ${fight.mode} brain ${fight.brainState}"

    /** A hit on Stone Maw as if a piece had struck it (the debug command); false if it cannot be hit now. */
    fun debugHit(server: MinecraftServer): Boolean = phase == Phase.FIGHT && fight.hit()

    /** Stone Maw's death played at once (the debug command); false if it is not fighting. */
    fun debugKill(): Boolean {
        if (phase != Phase.FIGHT) return false
        fight.kill()
        return true
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
        if (phase == Phase.DYING) dying(server, world, now)
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
        clearArena(world, rubble = true)
        fight.reset(rules)
        supply.reset()
        phase = Phase.FIGHT
        fighter = player.uuid
        lastMode = StoneMawFight.Mode.BRAIN
        collapsed = false
        Ch2L2Build.setGate(world, rules.entryGate, "shut")
        data.anchors.point(rules.entryAnchor)?.position?.let { world.playSound(null, it.x, it.y, it.z, Ch2Sounds.CH2L2_FLOOD_GATE_SLAM, SoundCategory.BLOCKS, 1.5f, 1.0f) }
        burstAt = now + BURST_DELAY_TICKS
        musicStart = now
        firstPiece(world, player)
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
        if (phase == Phase.DEAD || phase == Phase.IDLE) {
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
        val d = fight.tick(StoneMawFight.Senses(now, self, home, centre, felt, target, navigation, wall = e.horizontalCollision), rules, creature)
        if (d.mode != lastMode) {
            EveryTunnel.LOGGER.info("Stone Maw: {} -> {} ({} hits left)", lastMode, d.mode, fight.hitsLeft)
            lastMode = d.mode
        }
        e.fightPose(d.state)
        if (phase == Phase.DYING && !crushed) deathPose(e, d.mode)
        when {
            d.straight -> e.fightRam(d.heading!!, d.speed)
            d.target == null || d.speed <= 0.0 -> {
                e.fightStand(d.target)
                if (d.mode == StoneMawFight.Mode.STUNNED) e.velocity = Vec3d(0.0, e.velocity.y, 0.0)
            }
            else -> e.fightSteer(d.target, d.speed)
        }
        if (d.oriented) e.fightSound(Ch2Sounds.CH2L2_CREATURE_CLICK, 1.0f)
        if (d.windup) e.fightSound(Ch2Sounds.CH2L2_CREATURE_WINDUP, 1.2f)
        if (d.struck) {
            e.fightSound(Ch2Sounds.CH2L2_CREATURE_STRIKE, 1.2f)
            d.hit?.let { id -> (world.getPlayerByUuid(id) as? ServerPlayerEntity)?.let(e::fightHurt) }
        }
        if (d.rammed) rammed(world, e)
        if (d.mode == StoneMawFight.Mode.STUNNED && e.age % STUN_SOUND_TICKS == 0) e.fightSound(Ch2Sounds.CH2L2_MAW_STUNNED, 0.9f)
        if (d.mode == StoneMawFight.Mode.WALK_TO_CENTRE && phase == Phase.FIGHT) beginDeath(world, now)
    }

    /** The wounded walk, then the kneel facing the far wall (the way the scene's camera frame looks), so that the side shots see it in profile. */
    private fun deathPose(e: VibrationCreatureEntity, mode: StoneMawFight.Mode) {
        when (mode) {
            StoneMawFight.Mode.WALK_TO_CENTRE -> e.fightDeathPose(VibrationCreatureEntity.POSE_LIMP)
            StoneMawFight.Mode.CROUCH, StoneMawFight.Mode.DEAD -> {
                e.fightDeathPose(VibrationCreatureEntity.POSE_DYING)
                data.anchors.point(rules.centre)?.let { e.fightTurn(it.yaw.toFloat()) }
            }
            else -> Unit
        }
    }

    private fun rammed(world: ServerWorld, e: VibrationCreatureEntity) {
        world.playSound(null, e.x, e.y, e.z, Ch2Sounds.CH2L2_MAW_WALL_RAM, SoundCategory.HOSTILE, 1.6f, 1.0f)
        val dust = BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE.defaultState)
        world.spawnParticles(dust, e.x, e.y + 1.5, e.z, 50, 1.0, 1.2, 1.0, 0.15)
        EveryTunnel.LOGGER.info("Stone Maw rams a wall at ({}, {}): stunned", e.blockX, e.blockZ)
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
        EveryTunnel.LOGGER.info("Stone Maw is hit: {} left (it was {})", fight.hitsLeft, fight.mode)
    }

    /** A thrown piece broke on a block: its clang, a loud vibration where it landed. */
    fun pieceBroke(piece: ThrownPipeEntity) {
        val world = piece.world as? ServerWorld ?: return
        world.playSound(null, piece.x, piece.y, piece.z, Ch2Sounds.CH2L2_MAW_PIPE_BREAK, SoundCategory.BLOCKS, 1.4f, 1.0f)
        world.spawnParticles(ParticleTypes.CRIT, piece.x, piece.y, piece.z, 12, 0.2, 0.2, 0.2, 0.2)
        if (phase == Phase.FIGHT) clang(piece.x, piece.y, piece.z, world.server.ticks.toLong())
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
        if (ServerPlayNetworking.canSend(player, JUMP_PACKET)) {
            val buf = PacketByteBufs.create()
            buf.writeBoolean(blocked)
            ServerPlayNetworking.send(player, JUMP_PACKET, buf)
        }
    }

    // --- the death ------------------------------------------------------------------------------------

    private fun beginDeath(world: ServerWorld, now: Long) {
        phase = Phase.DYING
        stopMusic(world.server)
        deathStart = now
        collapsed = false
        crushed = false
        crackSound = false
        smashAt = null
        coming.clear()
        takePieces(world)
        val player = fighter?.let { world.getPlayerByUuid(it) as? ServerPlayerEntity }
        val centre = data.anchors.point(rules.centre)
        if (player != null && centre != null) {
            val frame = SceneFrame(centre.position.x, centre.position.y, centre.position.z, centre.yaw.toDouble())
            Ch2L2GuardScenes.MAW_DEATH.start(player, frame)
        }
        EveryTunnel.LOGGER.info("Stone Maw takes its last hit: the death scene begins")
    }

    /** One piece of [MawCollapse]'s plan still to fall: it starts at server tick [at] and reaches the stone at [lands]. */
    private class Fall(val at: Long, val lands: Long, val piece: MawCollapse.Piece, var fell: Boolean = false)

    private val coming = ArrayList<Fall>()
    private var crackSound = false
    private var crushed = false
    private var smashAt: Long? = null

    private fun dying(server: MinecraftServer, world: ServerWorld, now: Long) {
        val phases = MawDeathScene.phases(rules)
        val t = (now - deathStart).toInt()
        // The stone speaks first: its cracking and groaning begin while the bits fall and the blow lands as the first slab hits it.
        if (!crackSound && t >= phases.impactAt - SOUND_LEAD_TICKS) {
            crackSound = true
            position(rules.centre)?.let { world.playSound(null, it.x, it.y + 2.0, it.z, Ch2Sounds.CH2L2_MAW_COLLAPSE, SoundCategory.BLOCKS, 2.0f, 1.0f) }
        }
        if (t >= phases.crouchAt && t < phases.collapseAt && t % CRACK_EVERY_TICKS == 0) crackDust(world, t - phases.crouchAt, phases.collapseAt - phases.crouchAt)
        if (!collapsed && t >= phases.collapseAt) collapse(world, now)
        if (collapsed) fall(world, now)
        if (collapsed && !crushed && t >= phases.impactAt) crush(world, now)
        smashAt?.let { at ->
            if (now >= at) {
                smashAt = null
                smash(world)
            }
        }
        if (t >= phases.settledAt) finishDeath(server)
    }

    /** Dust and grit sifting from the ceiling over the centre, more and wider as the crack grows ([progress] of [length]). */
    private fun crackDust(world: ServerWorld, progress: Int, length: Int) {
        val c = position(rules.centre) ?: return
        val grow = (progress.toDouble() / length.coerceAtLeast(1)).coerceIn(0.0, 1.0)
        val dust = BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.GRAY_CONCRETE_POWDER.defaultState)
        world.spawnParticles(dust, c.x, c.y + CEILING_OFFSET, c.z, 6 + (26 * grow).toInt(), 0.8 + 2.2 * grow, 0.2, 0.8 + 2.2 * grow, 0.0)
        if (grow > 0.35 && world.random.nextInt(3) == 0) {
            // A chip comes away and ticks on the floor.
            val chip = BlockStateParticleEffect(ParticleTypes.BLOCK, rubbleBlocks().random(Random(progress.toLong())))
            world.spawnParticles(chip, c.x + (world.random.nextDouble() - 0.5) * 4, c.y + CEILING_OFFSET - 0.5, c.z + (world.random.nextDouble() - 0.5) * 4, 3, 0.1, 0.1, 0.1, 0.02)
        }
    }

    /** The ceiling comes down as [MawCollapse]'s plan says: its pieces start at their delays ([fall]), and the creature is crushed by the first slab ([crush]). */
    private fun collapse(world: ServerWorld, now: Long) {
        collapsed = true
        val c = position(rules.centre) ?: return
        val plan = MawCollapse.plan(rules, MawDeathScene.COLLAPSE_SEED)
        val landings = MawCollapse.landings(plan, rules.slabStartSpeed)
        for ((i, piece) in plan.withIndex()) coming += Fall(now + piece.delayTicks, now + landings[i], piece)
        world.spawnParticles(BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE.defaultState), c.x, c.y + CEILING_OFFSET - 0.5, c.z, 80, 2.0, 0.3, 2.0, 0.15)
        EveryTunnel.LOGGER.info("Stone Maw: the ceiling comes down ({} pieces, {} of them slabs)", plan.size, plan.count { it.kind == MawCollapse.Kind.SLAB })
    }

    private fun fall(world: ServerWorld, now: Long) {
        val c = position(rules.centre) ?: return
        val blocks = rubbleBlocks()
        val top = BlockPos.ofFloored(c.x, c.y + MawCollapse.TOP_CELL, c.z)
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
            val x = c.x + 0.5 + f.piece.cells.map { it.first }.average()
            val z = c.z + 0.5 + f.piece.cells.map { it.second }.average()
            val state = blocks[Random(RUBBLE_SEED + f.at.toInt()).nextInt(blocks.size)]
            world.spawnParticles(BlockStateParticleEffect(ParticleTypes.BLOCK, state), x, c.y + 0.6, z, if (slab) 50 else 14, if (slab) 1.5 else 0.4, 0.2, if (slab) 1.5 else 0.4, if (slab) 0.12 else 0.06)
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, c.y + 0.8, z, if (slab) 6 else 1, if (slab) 1.2 else 0.3, 0.1, if (slab) 1.2 else 0.3, 0.02)
        }
    }

    /** The first slab reaches the creature: it is slammed flat ([VibrationCreatureEntity.POSE_CRUSHED], seen for a few ticks), then [smash] hides it. */
    private fun crush(world: ServerWorld, now: Long) {
        crushed = true
        creatures(world).forEach { it.fightDeathPose(VibrationCreatureEntity.POSE_CRUSHED) }
        smashAt = now + SMASH_DELAY_TICKS
    }

    /** The big burst of dust and chips over the centre, and the creature gone under it. */
    private fun smash(world: ServerWorld) {
        val c = position(rules.centre) ?: return
        val blocks = rubbleBlocks()
        for (state in blocks) {
            world.spawnParticles(BlockStateParticleEffect(ParticleTypes.BLOCK, state), c.x, c.y + 0.8, c.z, 60, 1.4, 0.5, 1.4, 0.25)
        }
        world.spawnParticles(BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.GRAY_CONCRETE_POWDER.defaultState), c.x, c.y + 1.0, c.z, 120, 1.6, 0.8, 1.6, 0.1)
        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 0.8, c.z, 24, 1.4, 0.4, 1.4, 0.06)
        world.spawnParticles(ParticleTypes.POOF, c.x, c.y + 0.8, c.z, 30, 1.4, 0.5, 1.4, 0.12)
        creatures(world).forEach { it.discard() }
    }

    /**
     * The one end of the fight, idempotent (the scene's end, its skip, `skip maw`): Stone Maw gone, the rubble on the spot, the pieces
     * gone, the far door open, the player in [Ch2L2State.MAW_DEAD].
     */
    fun finishDeath(server: MinecraftServer) {
        if (phase == Phase.DEAD) return
        phase = Phase.DEAD
        entering = false
        burstAt = null
        stopMusic(server)
        val world = levelWorld(server) ?: return
        creatures(world).forEach { it.discard() }
        takePieces(world)
        for (e in world.getEntitiesByType(net.minecraft.entity.EntityType.FALLING_BLOCK, arenaBox()) { true }) e.discard()
        placeRubble(world)
        if (!Ch2L2Build.gateIs(world, rules.farGate, "open")) {
            data.anchors.point(rules.farAnchor)?.position?.let { world.playSound(null, it.x, it.y, it.z, Ch2Sounds.CH2L2_EXIT_DOOR, SoundCategory.BLOCKS, 1.5f, 1.0f) }
        }
        Ch2L2Build.setGate(world, rules.farGate, "open")
        Ch2L2Build.setGate(world, rules.entryGate, "open")
        fighter?.let { id -> (world.getPlayerByUuid(id) as? ServerPlayerEntity)?.let { if (Ch2L2.state(it) < Ch2L2State.MAW_DEAD) Ch2L2.setState(it, Ch2L2State.MAW_DEAD, "Stone Maw is dead") } }
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: Stone Maw is dead; the far door opens")
    }

    // --- the level starts -------------------------------------------------------------------------------

    /** The arena as [state] wants it (see the class). */
    private fun started(player: ServerPlayerEntity, state: Ch2L2State) {
        val world = levelWorld(player.server) ?: return
        stopMusic(player.server)
        reset()
        takePieces(world)
        creatures(world).forEach { it.discard() }
        val past = state >= Ch2L2State.MAW_DEAD
        clearArena(world, rubble = !past)
        if (past) placeRubble(world)
        phase = if (past) Phase.DEAD else Phase.IDLE
        Ch2L2Build.setGate(world, rules.entryGate, if (past) "open" else "open")
        Ch2L2Build.setGate(world, rules.mouthGate, "grate")
        Ch2L2Build.setGate(world, rules.farGate, if (past) "open" else "shut")
        Ch2L2Build.setGate(world, rules.exitGate, if (state >= Ch2L2State.MAW) "open" else "shut")
    }

    /** All the state of a fight forgotten. */
    private fun reset() {
        musicStart = null
        phase = Phase.IDLE
        fighter = null
        entering = false
        burstAt = null
        exitOpenAt = null
        collapsed = false
        crushed = false
        crackSound = false
        coming.clear()
        smashAt = null
        lastMode = StoneMawFight.Mode.BRAIN
        falling.clear()
        clangs.clear()
        jumpTold.clear()
        fight.reset(rules)
        supply.reset()
    }

    private fun clearArena(world: ServerWorld, rubble: Boolean) {
        if (rubble) clearRubble(world)
    }

    // --- rubble -----------------------------------------------------------------------------------------------

    /** The mix of blocks the ceiling is made of when it falls. */
    private fun rubbleBlocks(): List<net.minecraft.block.BlockState> =
        rules.collapseBlocks.mapNotNull { Identifier.tryParse(it)?.let(Registries.BLOCK::get)?.defaultState }.ifEmpty { listOf(Blocks.GRAY_CONCRETE.defaultState) }

    /** The mound where the creature stood, at once (a skip, or the level started past its death): [MawCollapse.heights] of the plan, filling only air. */
    private fun placeRubble(world: ServerWorld) {
        val c = position(rules.centre) ?: return
        val blocks = rubbleBlocks()
        val floor = BlockPos.ofFloored(c.x, c.y, c.z)
        val heights = MawCollapse.heights(MawCollapse.plan(rules, MawDeathScene.COLLAPSE_SEED), rules.maxHeight)
        for ((cell, height) in heights) {
            for (dy in 0 until height) {
                val pos = floor.add(cell.first, dy, cell.second)
                if (world.getBlockState(pos).isAir) world.setBlockState(pos, blocks[Random(RUBBLE_SEED + pos.hashCode()).nextInt(blocks.size)], Block.NOTIFY_ALL)
            }
        }
    }

    private fun clearRubble(world: ServerWorld) {
        val c = position(rules.centre) ?: return
        val kinds = rubbleBlocks().map { it.block }
        val r = rules.collapseRadius
        val base = BlockPos.ofFloored(c.x, c.y, c.z)
        for (dx in -r..r) for (dz in -r..r) for (dy in 0..RUBBLE_HEIGHT) {
            val pos = base.add(dx, dy, dz)
            if (kinds.any { world.getBlockState(pos).isOf(it) }) world.setBlockState(pos, Blocks.AIR.defaultState, Block.NOTIFY_ALL)
        }
        for (e in world.getEntitiesByType(net.minecraft.entity.EntityType.FALLING_BLOCK, arenaBox()) { true }) e.discard()
    }

    // --- helpers ----------------------------------------------------------------------------------------------

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
    private const val EXIT_DOOR_TICKS = 70L
    private const val BURST_DELAY_TICKS = 25L
    private const val ENTERING_DEPTH = 3.0
    private const val ENTERED_DISTANCE = 1.5
    private const val ENTERING_LIMIT_TICKS = 200L
    private const val STUN_SOUND_TICKS = 20
    private const val CLANG_TICKS = 5L
    private const val PICKUP_DELAY_TICKS = 15
    private const val FIRST_PIECE_HEIGHT = 5.0
    private const val CRACK_EVERY_TICKS = 3
    /** Ticks of the crack sound before its blow (`event.ch2l2.maw_collapse`: the blow is at 1.62 s). */
    private const val SOUND_LEAD_TICKS = 32
    /** Ticks the crushed pose is seen before the burst hides it. */
    private const val SMASH_DELAY_TICKS = 3L
    private const val CEILING_OFFSET = 6.0
    private const val RUBBLE_SEED = 61
    /** At least as high as the parser lets `death.max_height` go: [clearRubble] clears this many layers. */
    private const val RUBBLE_HEIGHT = 5
}
