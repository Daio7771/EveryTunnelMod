package daio7771.everytunnel.chapter2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.core.MapPlan
import daio7771.everytunnel.chapter2.prologue.core.BlockSpec
import daio7771.everytunnel.platform.TickProfiler
import daio7771.everytunnel.platform.WallMessageBlockEntity
import daio7771.everytunnel.platform.WallTextBlockEntity
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Property
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.PersistentState

/**
 * Puts one of Chapter 2's levels in the world, and takes it out (made for Level 1 as `Ch2L1Build`, shared since Level 2). The map is
 * data ([MapPlan], from the level's generator under `tools/map/`): a box to empty and boxes of blocks. It is built where the plan's
 * origin says, in the dimension [dimension] gives (the level's anchors'), when the level is first needed and again whenever the plan
 * changes (the world remembers the fingerprint of the plan it was built from, `data/<saveId>.dat`, which `packMapWorld` leaves out like
 * every other `every_tunnel_*.dat`). Each level keeps one instance ([daio7771.everytunnel.chapter2.level1.platform.Ch2L1Build],
 * [daio7771.everytunnel.chapter2.level2.platform.Ch2L2Build]), registered once.
 *
 * The work is spread over server ticks, with the chunks it touches kept loaded, so a build is a second or a few with no stutter: each
 * tick writes up to [WRITES_PER_TICK] cells, and a cell the clear finds empty already costs an eighth of a write (a big level's box is
 * mostly empty). Blocks go in without shape updates (the plan gives every state) but with light and listeners, as the prologue's room
 * does. One job at a time: a build asked for while one runs joins it; a clear while a build runs is refused. A world that closes
 * mid-job drops it and lets the chunks go; it is built again next time, as its fingerprint was not saved.
 */
class MapBuild(
    /** What the log and the chat call it: "Chapter 2 Level 1". */
    private val name: String,
    /** The world's saved data that remembers the fingerprint ("every_tunnel_ch2_l1"). */
    private val saveId: String,
    /** The plan on the classpath ("data/every_tunnel/ch2/l1/map.json"). */
    private val resource: String,
    /** The dimension to build in (an id such as "minecraft:overworld"), asked when a job starts. */
    private val dimension: () -> String,
) {

    /** What asking for a build came to. */
    enum class Asked {
        /** Already built from this plan: nothing to do (the callback has been called). */
        BUILT,

        /** A job was started. */
        STARTED,

        /** A build was already running; the callback waits for it. */
        JOINED,

        /** A clear is running: nothing was started. */
        BUSY,

        /** The plan names a block or a state the game does not know ([lastProblem] says which): nothing was touched. */
        FAILED,
    }

    /** Why the last [Asked.FAILED] failed. */
    var lastProblem: String? = null
        private set

    private class Op(val pos: BlockPos, val state: BlockState, val onlyIfNotAir: Boolean)

    private class Job(
        val server: MinecraftServer,
        val world: ServerWorld,
        val building: Boolean,
        val plan: MapPlan,
        val chunks: List<ChunkPos>,
        val ops: Iterator<Op>,
        val total: Long,
        val startedMs: Long,
        val whenDone: MutableList<() -> Unit>,
    ) {
        var done = 0L
    }

    private val states = HashMap<String, BlockState>()
    private var job: Job? = null

    /** The plan, read once. */
    val plan: MapPlan by lazy { MapPlan.load(resource) }

    fun register() {
        TickProfiler.endServerTick.register(::tick)
        ServerLifecycleEvents.SERVER_STOPPING.register { drop() }
        ServerLifecycleEvents.SERVER_STOPPED.register { states.clear() }
    }

    fun isBuilding(): Boolean = job != null

    /** The level stands in the world, built from the current plan. */
    fun isBuilt(server: MinecraftServer): Boolean = saved(server).fingerprint == plan.fingerprint

    /** The world the level is built in. */
    fun world(server: MinecraftServer): ServerWorld =
        Identifier.tryParse(dimension())?.let { server.getWorld(RegistryKey.of(RegistryKeys.WORLD, it)) } ?: server.overworld

    /** The block the plan puts at the world cell [pos], or null where no fill of the plan reaches (or its block is unknown). */
    fun planState(pos: BlockPos): BlockState? {
        val x = pos.x - plan.origin.x
        val y = pos.y - plan.origin.y
        val z = pos.z - plan.origin.z
        val fill = plan.fills.lastOrNull { x in it.from.x..it.to.x && y in it.from.y..it.to.y && z in it.from.z..it.to.z } ?: return null
        return try {
            state(fill.blockAt(x, y, z))
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Builds the level if it is not built (or, with [rebuild], whatever there is), then calls [whenDone] on the server thread.
     * [Asked] says what happened.
     */
    fun ask(server: MinecraftServer, rebuild: Boolean, whenDone: () -> Unit): Asked {
        job?.let {
            if (!it.building) return Asked.BUSY
            it.whenDone += whenDone
            return Asked.JOINED
        }
        if (!rebuild && isBuilt(server)) {
            whenDone()
            return Asked.BUILT
        }
        try {
            plan.allBlocks().forEach(::state)       // every block, before the world is touched
        } catch (e: IllegalArgumentException) {
            lastProblem = e.message
            EveryTunnel.LOGGER.error("{}: the map plan cannot be built: {}", name, e.message)
            return Asked.FAILED
        }
        start(server, building = true, whenDone)
        return Asked.STARTED
    }

    /** Empties everything the plan built; the world forgets it was built. [Asked.BUSY] if a job runs. */
    fun clear(server: MinecraftServer, whenDone: () -> Unit): Asked {
        if (job != null) return Asked.BUSY
        start(server, building = false, whenDone)
        return Asked.STARTED
    }

    private fun start(server: MinecraftServer, building: Boolean, whenDone: () -> Unit) {
        val world = world(server)
        val plan = plan
        val (a, b) = plan.clear
        val chunks = ((plan.worldX(a.x) shr 4)..(plan.worldX(b.x) shr 4)).flatMap { cx ->
            ((plan.worldZ(a.z) shr 4)..(plan.worldZ(b.z) shr 4)).map { cz -> ChunkPos(cx, cz) }
        }
        chunks.forEach { world.setChunkForced(it.x, it.z, true) }
        val total = plan.clearedCells() + if (building) plan.filledCells() else 0L
        job = Job(server, world, building, plan, chunks, operations(plan, building), total, System.currentTimeMillis(), mutableListOf(whenDone))
        saved(server).fingerprint = null
        saved(server).markDirty()
        EveryTunnel.LOGGER.info("{}: {} ({} cells, {} chunks)", name, if (building) "building" else "clearing", total, chunks.size)
    }

    /** The cells to visit, in order: the box emptied, then (building) every block of every fill. */
    private fun operations(plan: MapPlan, building: Boolean): Iterator<Op> = iterator {
        val air = Blocks.AIR.defaultState
        val (a, b) = plan.clear
        for (y in a.y..b.y) for (z in a.z..b.z) for (x in a.x..b.x) {
            yield(Op(BlockPos(plan.worldX(x), plan.worldY(y), plan.worldZ(z)), air, true))
        }
        if (!building) return@iterator
        for (fill in plan.fills) {
            val single = if (fill.mix.size == 1) state(fill.block) else null
            for (y in fill.from.y..fill.to.y) for (z in fill.from.z..fill.to.z) for (x in fill.from.x..fill.to.x) {
                val state = single ?: state(fill.blockAt(x, y, z))
                yield(Op(BlockPos(plan.worldX(x), plan.worldY(y), plan.worldZ(z)), state, false))
            }
        }
    }

    private fun tick(server: MinecraftServer) {
        val current = job ?: return
        try {
            var left = WRITES_PER_TICK * READS_PER_WRITE
            while (left > 0 && current.ops.hasNext()) {
                val op = current.ops.next()
                current.done++
                if (op.onlyIfNotAir && current.world.getBlockState(op.pos).isAir) {
                    left -= 1
                    continue
                }
                current.world.setBlockState(op.pos, op.state, FLAGS)
                left -= READS_PER_WRITE
            }
        } catch (e: Exception) {
            // Whatever it was, the world is left as it is, not built, and the server goes on.
            EveryTunnel.LOGGER.error("{}: the build failed after {} of {} cells", name, current.done, current.total, e)
            lastProblem = e.message ?: e.javaClass.simpleName
            release(current)
            job = null
            server.playerManager.broadcast(Text.literal("$name: the build failed (see the log): $lastProblem"), false)
            return
        }
        if (current.ops.hasNext()) return
        finish(current)
    }

    private fun finish(finished: Job) {
        if (finished.building) writeTexts(finished.world, finished.plan)
        release(finished)
        job = null
        val saved = saved(finished.server)
        saved.fingerprint = if (finished.building) finished.plan.fingerprint else null
        saved.markDirty()
        EveryTunnel.LOGGER.info(
            "{}: {} done in {} ms", name, if (finished.building) "built" else "cleared", System.currentTimeMillis() - finished.startedMs,
        )
        for (callback in finished.whenDone) {
            try {
                callback()
            } catch (e: Exception) {
                EveryTunnel.LOGGER.error("{}: a callback after the build failed", name, e)
            }
        }
    }

    /** What each of the plan's `wall_text` and `wall_message` blocks says ([MapPlan.texts], [MapPlan.messages]). */
    private fun writeTexts(world: ServerWorld, plan: MapPlan) {
        for (text in plan.texts) {
            val pos = BlockPos(plan.worldX(text.at.x), plan.worldY(text.at.y), plan.worldZ(text.at.z))
            val entity = world.getBlockEntity(pos) as? WallTextBlockEntity ?: continue
            val nbt = NbtCompound()
            nbt.putString("Text", text.text)
            nbt.putString("Style", text.style)
            nbt.putFloat("Offset", text.offset)
            entity.readNbt(nbt)
            entity.markDirty()
            world.updateListeners(pos, entity.cachedState, entity.cachedState, Block.NOTIFY_ALL)
        }
        for (message in plan.messages) {
            val pos = BlockPos(plan.worldX(message.at.x), plan.worldY(message.at.y), plan.worldZ(message.at.z))
            val entity = world.getBlockEntity(pos) as? WallMessageBlockEntity ?: continue
            val nbt = NbtCompound()
            nbt.putString("Text", message.text)
            nbt.putFloat("Size", message.size)
            nbt.putFloat("Rotation", message.rotation)
            nbt.putString("Trigger", "fixed")
            nbt.putString("Chalk", message.chalk)
            entity.readNbt(nbt)
            entity.markDirty()
            world.updateListeners(pos, entity.cachedState, entity.cachedState, Block.NOTIFY_ALL)
        }
    }

    /** The world is closing: the job is dropped and its chunks let go (they would stay forced for good otherwise). */
    private fun drop() {
        job?.let(::release)
        job = null
    }

    private fun release(finished: Job) {
        finished.chunks.forEach { finished.world.setChunkForced(it.x, it.z, false) }
    }

    // --- blocks --------------------------------------------------------------------------------------

    /** The state a plan's block names ("every_tunnel:time_clock[facing=north,unlocked=false]"); an unknown block or state fails. */
    fun state(spec: String): BlockState = states.getOrPut(spec) {
        val parsed = BlockSpec.parse(spec)
        val id = Identifier(parsed.id)
        val block = Registries.BLOCK.get(id)
        require(block != Blocks.AIR || id.path == "air") { "the map plan names a block that does not exist: ${parsed.id}" }
        var state = block.defaultState
        for ((name, value) in parsed.states) {
            val property = block.stateManager.getProperty(name) ?: throw IllegalArgumentException("${parsed.id} has no state '$name'")
            state = withValue(state, property, value, parsed.id)
        }
        state
    }

    private fun <T : Comparable<T>> withValue(state: BlockState, property: Property<T>, value: String, block: String): BlockState =
        state.with(property, property.parse(value).orElseThrow { IllegalArgumentException("$block: '$value' is not a value of '${property.name}'") })

    // --- what the world remembers ------------------------------------------------------------------

    private fun saved(server: MinecraftServer): BuiltState =
        server.overworld.persistentStateManager.getOrCreate(BuiltState::read, ::BuiltState, saveId)

    /** The fingerprint of the plan the world was built from, or null: not built. */
    private class BuiltState : PersistentState() {
        var fingerprint: Int? = null

        override fun writeNbt(nbt: NbtCompound): NbtCompound {
            fingerprint?.let { nbt.putInt("fingerprint", it) }
            return nbt
        }

        companion object {
            fun read(nbt: NbtCompound): BuiltState = BuiltState().also { if (nbt.contains("fingerprint")) it.fingerprint = nbt.getInt("fingerprint") }
        }
    }

    companion object {
        private const val FLAGS = Block.NOTIFY_LISTENERS or Block.FORCE_STATE

        /** Cells written each tick (Level 1's pace: its whole build is a second or two). */
        const val WRITES_PER_TICK = 8000

        /** A cell the clear finds empty costs this many times less than one written. */
        const val READS_PER_WRITE = 8
    }
}
