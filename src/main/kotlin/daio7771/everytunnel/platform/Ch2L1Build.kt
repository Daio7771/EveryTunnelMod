package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.ch2l1.Ch2L1MapPlan
import daio7771.everytunnel.core.cutscene.BlockSpec
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
 * Puts Chapter 2's Level 1 in the world, and takes it out. The map is data ([Ch2L1MapPlan], from `tools/map/ch2l1/generate.py`):
 * a box to empty and boxes of one block each. It is built where the plan's origin says, in the dimension of the anchors, when
 * the level is first needed ([Ch2L1.enter] asks) and again whenever the plan changes (the world remembers the fingerprint of
 * the plan it was built from, `data/every_tunnel_ch2_l1.dat`, which `packMapWorld` leaves out like every other `every_tunnel_*.dat`).
 *
 * The work is spread over server ticks ([OPS_PER_TICK] cells each), with the chunks it touches kept loaded, so a build is a
 * second or two with no stutter. Blocks go in without shape updates (the plan gives every state) but with light and listeners,
 * as the prologue's room does. One job at a time: a build asked for while one runs joins it; a clear while a build runs is refused.
 * A world that closes mid-job drops it and lets the chunks go; it is built again next time, as its fingerprint was not saved.
 */
object Ch2L1Build {

    private const val FLAGS = Block.NOTIFY_LISTENERS or Block.FORCE_STATE
    private const val OPS_PER_TICK = 8000
    private const val SAVE_ID = "every_tunnel_ch2_l1"

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
        val plan: Ch2L1MapPlan,
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
    val plan: Ch2L1MapPlan by lazy { Ch2L1MapPlan.load() }

    fun register() {
        TickProfiler.endServerTick.register(::tick)
        ServerLifecycleEvents.SERVER_STOPPING.register { drop() }
        ServerLifecycleEvents.SERVER_STOPPED.register { states.clear() }
    }

    fun isBuilding(): Boolean = job != null

    /** The level stands in the world, built from the current plan. */
    fun isBuilt(server: MinecraftServer): Boolean = saved(server).fingerprint == plan.fingerprint

    /**
     * Builds the level if it is not built (or, with [rebuild], whatever there is), then calls [whenDone] on the server thread.
     * The world is the anchors' dimension; [Asked] says what happened.
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
            plan.fills.map { it.block }.toSet().forEach(::state)       // every block, before the world is touched
        } catch (e: IllegalArgumentException) {
            lastProblem = e.message
            EveryTunnel.LOGGER.error("Chapter 2 Level 1: the map plan cannot be built: {}", e.message)
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
        val dimension = Ch2L1DataLoader.data.anchors.dimension
        val world = Identifier.tryParse(dimension)?.let { server.getWorld(RegistryKey.of(RegistryKeys.WORLD, it)) } ?: server.overworld
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
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: {} ({} cells, {} chunks)", if (building) "building" else "clearing", total, chunks.size)
    }

    /** The cells to visit, in order: the box emptied, then (building) every block of every fill. */
    private fun operations(plan: Ch2L1MapPlan, building: Boolean): Iterator<Op> = iterator {
        val air = Blocks.AIR.defaultState
        val (a, b) = plan.clear
        for (y in a.y..b.y) for (z in a.z..b.z) for (x in a.x..b.x) {
            yield(Op(BlockPos(plan.worldX(x), plan.worldY(y), plan.worldZ(z)), air, true))
        }
        if (!building) return@iterator
        for (fill in plan.fills) {
            val state = state(fill.block)
            for (y in fill.from.y..fill.to.y) for (z in fill.from.z..fill.to.z) for (x in fill.from.x..fill.to.x) {
                yield(Op(BlockPos(plan.worldX(x), plan.worldY(y), plan.worldZ(z)), state, false))
            }
        }
    }

    private fun tick(server: MinecraftServer) {
        val current = job ?: return
        try {
            var left = OPS_PER_TICK
            while (left-- > 0 && current.ops.hasNext()) {
                val op = current.ops.next()
                current.done++
                if (op.onlyIfNotAir && current.world.getBlockState(op.pos).isAir) continue
                current.world.setBlockState(op.pos, op.state, FLAGS)
            }
        } catch (e: Exception) {
            // Whatever it was, the world is left as it is, not built, and the server goes on.
            EveryTunnel.LOGGER.error("Chapter 2 Level 1: the build failed after {} of {} cells", current.done, current.total, e)
            lastProblem = e.message ?: e.javaClass.simpleName
            release(current)
            job = null
            server.playerManager.broadcast(Text.literal("Chapter 2 Level 1: the build failed (see the log): $lastProblem"), false)
            return
        }
        if (current.ops.hasNext()) return
        finish(current)
    }

    private fun finish(finished: Job) {
        release(finished)
        job = null
        val saved = saved(finished.server)
        saved.fingerprint = if (finished.building) finished.plan.fingerprint else null
        saved.markDirty()
        EveryTunnel.LOGGER.info(
            "Chapter 2 Level 1: {} done in {} ms",
            if (finished.building) "built" else "cleared", System.currentTimeMillis() - finished.startedMs,
        )
        for (callback in finished.whenDone) {
            try {
                callback()
            } catch (e: Exception) {
                EveryTunnel.LOGGER.error("Chapter 2 Level 1: a callback after the build failed", e)
            }
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
    private fun state(spec: String): BlockState = states.getOrPut(spec) {
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
        server.overworld.persistentStateManager.getOrCreate(BuiltState::read, ::BuiltState, SAVE_ID)

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
}
