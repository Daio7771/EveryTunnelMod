package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.cutscene.BlockSpec
import daio7771.everytunnel.core.cutscene.Cell
import daio7771.everytunnel.core.cutscene.PrologueScene
import daio7771.everytunnel.core.cutscene.RoomPlan
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.core.cutscene.Spot
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.network.PacketByteBuf
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Property
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.UUID

/**
 * The prologue of Chapter 2 (the opening cinematic, [PrologueScene]).
 *
 * It does not happen in a level: when it starts, this builds its room ([RoomPlan], from `tools/prologue/room.py`)
 * at [ORIGIN] (high over the world, far from every level) in the player's world, puts the scientist in it
 * ([ScientistEntity]), moves the player into the room (held there unharmed, not drawn: the client's camera is
 * the view) and tells the client to begin ([START_PACKET_ID], which carries where he stands). When it ends,
 * played through or skipped ([finish], the one way out), the scientist goes, the player is put back where they
 * were and the room is cleared out of the world. Leaving the world during it puts the player back too
 * ([abort]), so a saved game is never left inside the room.
 *
 * While it runs this also does what the scene's cues say ([PrologueScene.PLAN]): his hands begin to shake
 * ([ScientistEntity.TREMBLING]), the ceiling's lights flicker (the lamps swapped for plain ceiling and back, the
 * counter's and the tubes' own glow staying) and, in the long beats of it, he goes through [ScientistEntity.SICK] and
 * [ScientistEntity.STRETCHED] and, in the last beat, becomes a Pale man ([ScientistEntity.PALE]), turning a little
 * more towards the camera each time. The camera, the voice and the sound are the client's, on the same ticks.
 *
 * For now it is started by `/everytunnel prologue` and by the main menu's NEW GAME, Chapter 2 ([ChapterRequest],
 * [startFromMenu]: the world opens only for it, and the client leaves for the menu when it ends); the game's chapter 2
 * does not exist yet. Its state lives in memory only.
 */
object PrologueSceneHooks {

    val START_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "prologue_scene_start")
    val END_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "prologue_scene_end")
    val SKIP_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "prologue_scene_skip")

    /** The room's cell 0, 0, 0 (the corner of its air): high over the world's levels, a long way from them. */
    private val ORIGIN = BlockPos(2048, 200, 2048)

    /** The ceiling's lamps, and what takes their place while the lights are off. */
    private const val LAMP = "every_tunnel:lab_ceiling_light"
    private const val CEILING = "minecraft:white_concrete"

    /** Blocks are placed without shape updates (the plan gives every state) but with light and listeners. */
    private const val FLAGS = Block.NOTIFY_LISTENERS or Block.FORCE_STATE

    /** Where the player was, to put them back: [world], position and the way they looked. */
    private class Running(
        val player: UUID,
        val startTick: Int,
        val world: RegistryKey<World>,
        val from: Vec3d,
        val fromYaw: Float,
        val fromPitch: Float,
        val hold: Vec3d,
        val holdYaw: Float,
        val scientist: UUID?,
        val plan: RoomPlan,
        val stands: Vec3d,
        val lit: BooleanArray = BooleanArray(plan.lights.size) { true },
    )

    private var running: Running? = null

    private val states = mutableMapOf<String, BlockState>()

    fun register() {
        TickProfiler.endServerTick.register(::tick)
        ServerPlayNetworking.registerGlobalReceiver(SKIP_PACKET_ID) { server, player, _, _, _ ->
            server.execute { if (running?.player == player.uuid) finish(server, skipped = true) }
        }
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ -> running?.player != entity.uuid }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
            if (running?.player == handler.player.uuid) abort(server, handler.player)
        }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            running = null
            ChapterRequest.prologueOnly = false
        }
    }

    fun isRunning(player: ServerPlayerEntity): Boolean = running?.player == player.uuid

    /**
     * Starts the prologue for [player]: null if it did, else why not (for the command to say).
     */
    fun start(player: ServerPlayerEntity): String? {
        if (running != null) return "The prologue is already playing."
        if (!ResourceHooks.appliesTo(player)) return "This world has no levels: the prologue only plays in the mod's worlds."
        if (!ServerPlayNetworking.canSend(player, START_PACKET_ID)) return "The client cannot receive the prologue (is the mod on it?)."
        val plan = try {
            RoomPlan.load()
        } catch (e: RuntimeException) {
            EveryTunnel.LOGGER.error("Prologue: the room's plan could not be read", e)
            return "The room's plan could not be read: ${e.message}"
        }
        val world = player.serverWorld
        try {
            place(world, plan)
        } catch (e: RuntimeException) {
            EveryTunnel.LOGGER.error("Prologue: the room could not be built", e)
            clear(world, plan)
            return "The room could not be built: ${e.message}"
        }
        val stands = at(plan.scientist)
        val scientist = ModEntities.SCIENTIST.create(world)?.also {
            it.refreshPositionAndAngles(stands.x, stands.y, stands.z, plan.scientist.yaw, 0f)
            world.spawnEntity(it)
        }
        val hold = at(plan.viewer)
        running = Running(
            player.uuid, player.server.ticks, world.registryKey, player.pos, player.yaw, player.pitch,
            hold, plan.viewer.yaw, scientist?.uuid, plan, stands,
        )
        player.velocity = Vec3d.ZERO
        player.isSprinting = false
        player.teleport(world, hold.x, hold.y, hold.z, plan.viewer.yaw, 0f)
        val buf = PacketByteBufs.create()
        buf.writeDouble(stands.x)
        buf.writeDouble(stands.y)
        buf.writeDouble(stands.z)
        buf.writeFloat(plan.scientist.yaw)
        ServerPlayNetworking.send(player, START_PACKET_ID, buf)
        EveryTunnel.LOGGER.info("Prologue: begins ({} s), room at {}", PrologueScene.SCENE.cutscene.totalTicks / 20, ORIGIN.toShortString())
        return null
    }

    /**
     * The main menu asked only for the prologue ([ChapterRequest]) and [player] is in: it begins. If it cannot, the
     * request is dropped and the player stays in the world, told why.
     */
    fun startFromMenu(player: ServerPlayerEntity) {
        val why = start(player) ?: return
        EveryTunnel.LOGGER.warn("Prologue from the menu: {}", why)
        ChapterRequest.prologueOnly = false
        player.sendMessage(net.minecraft.text.Text.literal(why))
    }

    /** (x, y, z, yaw) of where the scientist stands. */
    fun readStart(buf: PacketByteBuf): DoubleArray =
        doubleArrayOf(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat().toDouble())

    private fun tick(server: MinecraftServer) {
        val scene = running ?: return
        val player = server.playerManager.getPlayer(scene.player)
        if (player == null) {
            running = null
            return
        }
        val t = server.ticks - scene.startTick
        if (t >= PrologueScene.SCENE.cutscene.totalTicks) {
            finish(server, skipped = false)
            return
        }
        server.getWorld(scene.world)?.let { cue(it, scene, t) }
        // Held there: the client takes their keys, this keeps them there whatever it does.
        if (player.pos.squaredDistanceTo(scene.hold) > 0.0025) {
            player.networkHandler.requestTeleport(scene.hold.x, scene.hold.y, scene.hold.z, scene.holdYaw, 0f)
        }
        player.velocity = Vec3d.ZERO
    }

    /** The scene's cues at tick [t]: the lights, and how far the scientist has gone. */
    private fun cue(world: ServerWorld, scene: Running, t: Int) {
        val cues = PrologueScene.PLAN.cues
        for ((i, light) in scene.plan.lights.withIndex()) {
            val on = cues.lightOn(t, i)
            if (on != scene.lit[i]) {
                scene.lit[i] = on
                world.setBlockState(at(light), state(if (on) LAMP else CEILING), FLAGS)
            }
        }
        val scientist = scene.scientist?.let { world.getEntity(it) } as? ScientistEntity ?: return
        val stage = cues.stageAt(t)
        if (stage <= scientist.stage) return
        scientist.stage = stage
        // Each stage turns him a little more from the bench towards the camera.
        val frame = SceneFrame(scene.stands.x, scene.stands.y, scene.stands.z, scene.plan.scientist.yaw.toDouble())
        scientist.refreshPositionAndAngles(scene.stands.x, scene.stands.y, scene.stands.z, PrologueScene.facing(frame, stage).toFloat(), 0f)
        // Dark ash and ink come off him at each stage, and burst out of him at the change.
        val count = when (stage) { ScientistEntity.SICK -> 10; ScientistEntity.STRETCHED -> 18; ScientistEntity.PALE -> 70; else -> 0 }
        if (count > 0) {
            val burst = stage == ScientistEntity.PALE
            val spread = if (burst) 0.45 else 0.3
            val speed = if (burst) 0.09 else 0.03
            world.spawnParticles(ParticleTypes.ASH, scene.stands.x, scene.stands.y + 1.0, scene.stands.z, count * 2, spread, 0.8, spread, speed)
            world.spawnParticles(ParticleTypes.SQUID_INK, scene.stands.x, scene.stands.y + 1.0, scene.stands.z, count, spread, 0.8, spread, speed)
            if (burst) world.spawnParticles(ParticleTypes.LARGE_SMOKE, scene.stands.x, scene.stands.y + 0.8, scene.stands.z, 25, 0.4, 0.6, 0.4, 0.04)
        }
        EveryTunnel.LOGGER.info("Prologue: the scientist is at stage {} (tick {})", stage, t)
    }

    /** The scene's end, played through or skipped: the scientist gone, the player back where they were, the room cleared. */
    private fun finish(server: MinecraftServer, skipped: Boolean) {
        val scene = running ?: return
        running = null
        val world = server.getWorld(scene.world)
        val player = server.playerManager.getPlayer(scene.player)
        if (world != null) {
            scene.scientist?.let { world.getEntity(it)?.discard() }
            player?.teleport(world, scene.from.x, scene.from.y, scene.from.z, scene.fromYaw, scene.fromPitch)
        }
        if (player != null && ServerPlayNetworking.canSend(player, END_PACKET_ID)) {
            val buf = PacketByteBufs.create()
            buf.writeBoolean(skipped)
            ServerPlayNetworking.send(player, END_PACKET_ID, buf)
        }
        if (world != null) clear(world, RoomPlan.load())
        EveryTunnel.LOGGER.info("Prologue: {}", if (skipped) "skipped" else "ends")
    }

    /** The player left the world during the scene: put back where they were (so the save is not in the room) and the room cleared. */
    private fun abort(server: MinecraftServer, player: ServerPlayerEntity) {
        val scene = running ?: return
        running = null
        val world = server.getWorld(scene.world) ?: return
        scene.scientist?.let { world.getEntity(it)?.discard() }
        player.refreshPositionAndAngles(scene.from.x, scene.from.y, scene.from.z, scene.fromYaw, scene.fromPitch)
        clear(world, RoomPlan.load())
        EveryTunnel.LOGGER.info("Prologue: dropped, the player left the world")
    }

    /** The room's cell [c] in the world. */
    private fun at(c: Cell) = BlockPos(ORIGIN.x + c.x, ORIGIN.y + c.y, ORIGIN.z + c.z)

    /** Where [spot] (the room's coordinates) is in the world. */
    private fun at(spot: Spot) = Vec3d(ORIGIN.x + spot.x, ORIGIN.y + spot.y, ORIGIN.z + spot.z)

    /** Builds the room: the fills in their order, then the single blocks. */
    private fun place(world: ServerWorld, plan: RoomPlan) {
        for (fill in plan.fills) {
            val state = state(fill.block)
            for (x in fill.from.x..fill.to.x) for (y in fill.from.y..fill.to.y) for (z in fill.from.z..fill.to.z) {
                world.setBlockState(at(Cell(x, y, z)), state, FLAGS)
            }
        }
        for (put in plan.puts) world.setBlockState(at(put.at), state(put.block), FLAGS)
    }

    /** Takes the whole room (its shell and what is in it) out of the world. */
    private fun clear(world: ServerWorld, plan: RoomPlan) {
        val (from, to) = plan.shell
        val air = Blocks.AIR.defaultState
        for (x in from.x..to.x) for (y in from.y..to.y) for (z in from.z..to.z) {
            val pos = at(Cell(x, y, z))
            if (!world.getBlockState(pos).isAir) world.setBlockState(pos, air, FLAGS)
        }
    }

    /** The state a plan's block names ("every_tunnel:lab_bench[facing=south]"); an unknown block or state fails. */
    private fun state(spec: String): BlockState = states.getOrPut(spec) {
        val parsed = BlockSpec.parse(spec)
        val id = Identifier(parsed.id)
        val block = Registries.BLOCK.get(id)
        require(block != Blocks.AIR || id.path == "air") { "the plan names a block that does not exist: ${parsed.id}" }
        var state = block.defaultState
        for ((name, value) in parsed.states) {
            val property = block.stateManager.getProperty(name)
                ?: throw IllegalArgumentException("${parsed.id} has no state '$name'")
            state = withValue(state, property, value, parsed.id)
        }
        state
    }

    private fun <T : Comparable<T>> withValue(state: BlockState, property: Property<T>, value: String, block: String): BlockState =
        state.with(property, property.parse(value).orElseThrow { IllegalArgumentException("$block: '$value' is not a value of '${property.name}'") })
}
