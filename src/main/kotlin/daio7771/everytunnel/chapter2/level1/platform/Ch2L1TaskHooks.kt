package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.chapter2.platform.Ch2Items
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Bits
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Tasks
import daio7771.everytunnel.chapter2.level1.core.HoldTracker
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import java.util.UUID
import daio7771.everytunnel.platform.ModSounds
import daio7771.everytunnel.chapter2.platform.Ch2Progress

/**
 * Chapter 2 Level 1's three tasks of Act 1, which the blocks of [Ch2L1TaskBlocks] hand their uses to. What is where and how long
 * it takes is `tasks.json` ([Ch2L1Tasks]); how far each player has got is a few counters in their progress (the tubes taken and the
 * samples registered as bits, the dirt swept as bits, whether the broom was taken: [Ch2L1Tasks.TUBES] and the rest), so it
 * survives leaving the world and the client can show it (the objective panel's counts).
 *
 * - **Task 1** ([Ch2L1State.ACT1_TASK1]): click the three sample tubes on the bench (each goes, with a pip: 1/3, 2/3, 3/3), then the
 *   terminal, which opens NonameOS (`client.screen.NonameOsScreen`): a registry with a REGISTER button for each tube taken; the
 *   client asks for a registration when its bar fills ([OS_REGISTER_PACKET]), the server counts it and answers with the registry as it
 *   stands ([OS_OPEN_PACKET]); the last one ends the task.
 * - **Task 2** ([Ch2L1State.ACT1_TASK2]): a click held on the analyzer for `calibrate_seconds`: a bar fills ([HOLD_PACKET]) and a pip
 *   rises in pitch; looking away or letting go starts it over ([HoldTracker]). Then the LAB's reagents and gauge ([Ch2L1LabTaskHooks]).
 * - **Task 3** ([Ch2L1State.ACT1_TASK3]): the broom leans against a wall; taking it puts the item in the player's hand. A click
 *   held on each patch of dirt with the broom in hand for `sweep_seconds` sweeps it away; the last one ends the task, takes the
 *   broom back and goes on to [Ch2L1State.ACT2_FOLLOW].
 * - **The overtime** ([Ch2L1State.ACT3_BASEMENT], Rowan's three hours, past the sealed door): the same sweeping with the basement's broom
 *   and dirt (`tasks.json`'s `overtime`); the last patch locks the player in ([Ch2L1State.ACT3_LOCKED_IN], whose lights out, bolted
 *   door, manhole and ending are [Ch2L1EndingHooks]').
 *
 * A state change ([Ch2L1.onStateChange], by playing, `/et ch2 l1 state` or `skip`) brings the counters and the props of the world to what
 * that state means ([onState], [reconcile]): the tubes, the dirt and the broom stand where the player has not been yet and are gone
 * where they have, so a step can be reached by command with the same level as by playing. Server thread only.
 */
object Ch2L1TaskHooks {

    /** To the client: how far a held use has got (a float, 0 to 1) and the lang key of what it is called. */
    val HOLD_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_hold")

    /** To the client: NonameOS's registry as it stands (the tubes' codes, the bits taken and registered, whether it is over). */
    val OS_OPEN_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_os_open")

    /** To the server: the player asks to register the tube of this index (a var int). */
    val OS_REGISTER_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_os_register")

    /** To the server: the player left NonameOS (the screen closed, however it was). */
    val OS_CLOSED_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_os_closed")

    private const val KEY = "every_tunnel.ch2.l1."
    private val holds = HashMap<UUID, HoldTracker>()
    private val terminalOpen = HashSet<UUID>()

    private val progress get() = Ch2Progress
    private val tasks get() = Ch2L1DataLoader.tasks

    fun register() {
        Ch2L1.onStateChange(::onState)
        ServerPlayNetworking.registerGlobalReceiver(OS_REGISTER_PACKET) { server, player, _, buf, _ ->
            val index = buf.readVarInt()
            server.execute { registerSample(player, index) }
        }
        ServerPlayNetworking.registerGlobalReceiver(OS_CLOSED_PACKET) { server, player, _, _, _ ->
            server.execute {
                if (terminalOpen.remove(player.uuid)) Ch2L1SpeakerHooks.terminalClosed(player)
            }
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            holds.remove(handler.player.uuid)
            terminalOpen.remove(handler.player.uuid)
        }
    }

    /** The player has NonameOS open: nothing speaks to them while they do (see [Ch2L1SpeakerHooks]). */
    fun isTerminalOpen(player: ServerPlayerEntity): Boolean = player.uuid in terminalOpen

    // --- where things are -------------------------------------------------------------------------

    private fun cell(anchorId: String): BlockPos? =
        Ch2L1DataLoader.data.anchors.point(anchorId)?.position?.let { BlockPos.ofFloored(it.x, it.y, it.z) }

    private fun tubeCells(): List<BlockPos?> = tasks.tubes.map { cell(it.anchor) }

    /**
     * A sweeping task: the state it is played in, its broom's and its dirt's places, the counters of the patches swept and of the broom
     * taken, and the state its last patch leads to.
     */
    private data class Sweep(
        val state: Ch2L1State,
        val broomAnchor: String,
        val dirtPath: String,
        val swept: String,
        val broom: String,
        val next: Ch2L1State,
        val name: String,
    )

    /** Task 3, in the ring, and the overtime's, in the basement. */
    private fun sweeps() = listOf(
        Sweep(Ch2L1State.ACT1_TASK3, tasks.broomAnchor, tasks.dirtPath, Ch2L1Tasks.SWEPT, Ch2L1Tasks.BROOM, Ch2L1State.ACT2_FOLLOW, "task 3"),
        Sweep(
            Ch2L1State.ACT3_BASEMENT, tasks.basementBroomAnchor, tasks.basementDirtPath, Ch2L1Tasks.BASEMENT_SWEPT, Ch2L1Tasks.BASEMENT_BROOM,
            Ch2L1State.ACT3_LOCKED_IN, "the basement",
        ),
    )

    private fun dirtCells(sweep: Sweep): List<BlockPos> =
        Ch2L1DataLoader.data.anchors.path(sweep.dirtPath)?.points?.map { BlockPos.ofFloored(it.x, it.y, it.z) } ?: emptyList()

    private fun counter(player: ServerPlayerEntity, key: String) = progress.ch2L1Counter(player, key)

    private fun set(player: ServerPlayerEntity, key: String, value: Int) = progress.setCh2L1Counter(player, key, value)

    // --- the blocks' uses ---------------------------------------------------------------------------

    private fun notNow(player: ServerPlayerEntity): ActionResult {
        player.sendMessage(Text.translatable(KEY + "task.not_now"), true)
        return ActionResult.CONSUME
    }

    private fun blip(player: ServerPlayerEntity, pos: BlockPos, pitch: Float, volume: Float = 0.8f) {
        player.serverWorld.playSound(null, pos, Ch2Sounds.CH2L1_BLIP, SoundCategory.BLOCKS, volume, pitch)
    }

    /** A sample tube clicked: taken, counted, gone from the bench. */
    fun useTube(player: ServerPlayerEntity, pos: BlockPos): ActionResult {
        val index = tubeCells().indexOf(pos)
        if (index < 0) return ActionResult.PASS
        if (Ch2L1.state(player) != Ch2L1State.ACT1_TASK1) return notNow(player)
        val taken = counter(player, Ch2L1Tasks.TUBES)
        if (Ch2L1Bits.has(taken, index)) return ActionResult.CONSUME
        val now = Ch2L1Bits.with(taken, index)
        set(player, Ch2L1Tasks.TUBES, now)
        player.serverWorld.setBlockState(pos, Blocks.AIR.defaultState)
        val count = Ch2L1Bits.count(now)
        blip(player, pos, 0.9f + 0.15f * count)
        player.sendMessage(Text.translatable(KEY + "tube.taken", count, tasks.tubes.size), true)
        return ActionResult.CONSUME
    }

    /** The terminal clicked: NonameOS opens. */
    fun useTerminal(player: ServerPlayerEntity, pos: BlockPos): ActionResult {
        if (pos != cell(tasks.terminalAnchor)) return ActionResult.PASS
        if (Ch2L1.state(player) != Ch2L1State.ACT1_TASK1) return notNow(player)
        player.serverWorld.playSound(null, pos, ModSounds.TERMINAL_KEY, SoundCategory.BLOCKS, 0.6f, 1f)
        sendOs(player, done = false)
        return ActionResult.CONSUME
    }

    /** NonameOS's registry, as it stands, to the client (opening the screen if it is not open). */
    private fun sendOs(player: ServerPlayerEntity, done: Boolean) {
        if (!ServerPlayNetworking.canSend(player, OS_OPEN_PACKET)) return
        terminalOpen += player.uuid
        val buf = PacketByteBufs.create()
        buf.writeVarInt(tasks.tubes.size)
        tasks.tubes.forEach { buf.writeString(it.code) }
        buf.writeInt(counter(player, Ch2L1Tasks.TUBES))
        buf.writeInt(counter(player, Ch2L1Tasks.REGISTERED))
        buf.writeBoolean(done)
        buf.writeVarInt(tasks.registerTicks)
        ServerPlayNetworking.send(player, OS_OPEN_PACKET, buf)
    }

    /** The registry's REGISTER button of tube [index] has filled its bar. */
    private fun registerSample(player: ServerPlayerEntity, index: Int) {
        if (!Ch2L1.active(player) || Ch2L1.state(player) != Ch2L1State.ACT1_TASK1) return
        val total = tasks.tubes.size
        if (index !in 0 until total) return
        val registered = counter(player, Ch2L1Tasks.REGISTERED)
        if (!Ch2L1Bits.has(counter(player, Ch2L1Tasks.TUBES), index) || Ch2L1Bits.has(registered, index)) return
        val now = Ch2L1Bits.with(registered, index)
        set(player, Ch2L1Tasks.REGISTERED, now)
        blip(player, player.blockPos, 1.3f, 0.6f)
        val done = Ch2L1Bits.count(now) == total
        sendOs(player, done)
        if (done) Ch2L1.setState(player, Ch2L1State.ACT1_TASK2, "task 1 done")
    }

    /** The analyzer used: one more step of a held calibration. */
    fun useAnalyzer(player: ServerPlayerEntity, pos: BlockPos): ActionResult {
        if (pos != cell(tasks.analyzerAnchor)) return ActionResult.PASS
        if (Ch2L1.state(player) != Ch2L1State.ACT1_TASK2) return notNow(player)
        val fraction = fractionHeld(player, "analyzer", tasks.calibrateTicks, KEY + "hold.calibrating")
        if (fraction < 1f) {
            blip(player, pos, 0.7f + 0.9f * fraction, 0.5f)
            return ActionResult.CONSUME
        }
        player.serverWorld.playSound(null, pos, ModSounds.CARD_READER_ACCEPT, SoundCategory.BLOCKS, 1f, 1f)
        player.sendMessage(Text.translatable(KEY + "analyzer.done"), true)
        Ch2L1.setState(player, Ch2L1State.ACT1_REAGENTS, "task 2 done")
        return ActionResult.CONSUME
    }

    /** A broom clicked on its wall: it is in the player's hand. */
    fun takeBroom(player: ServerPlayerEntity, pos: BlockPos): ActionResult {
        val sweep = sweeps().firstOrNull { pos == cell(it.broomAnchor) } ?: return ActionResult.PASS
        if (Ch2L1.state(player) != sweep.state) return notNow(player)
        if (counter(player, sweep.broom) != 0) return ActionResult.CONSUME
        set(player, sweep.broom, 1)
        player.serverWorld.setBlockState(pos, Blocks.AIR.defaultState)
        val broom = ItemStack(Ch2Items.BROOM)
        if (player.mainHandStack.isEmpty) player.setStackInHand(Hand.MAIN_HAND, broom) else player.giveItemStack(broom)
        player.serverWorld.playSound(null, pos, Ch2Sounds.CH2L1_SWEEP, SoundCategory.BLOCKS, 0.6f, 1.2f)
        player.sendMessage(Text.translatable(KEY + "broom.taken"), true)
        return ActionResult.CONSUME
    }

    /** A patch of dirt used: with the broom in hand, one more step of sweeping it. */
    fun useDirt(player: ServerPlayerEntity, pos: BlockPos): ActionResult {
        val sweep = sweeps().firstOrNull { pos in dirtCells(it) } ?: return ActionResult.PASS
        val cells = dirtCells(sweep)
        val index = cells.indexOf(pos)
        if (Ch2L1.state(player) != sweep.state) return notNow(player)
        if (Ch2L1Bits.has(counter(player, sweep.swept), index)) return ActionResult.CONSUME
        if (!player.mainHandStack.isOf(Ch2Items.BROOM)) {
            player.sendMessage(Text.translatable(KEY + "broom.need"), true)
            return ActionResult.CONSUME
        }
        val world = player.serverWorld
        val fraction = fractionHeld(player, "dirt:${sweep.name}:$index", tasks.sweepTicks, KEY + "hold.sweeping")
        if (fraction < 1f) {
            if ((fraction * tasks.sweepTicks).toInt() % 8 < 4) {
                world.playSound(null, pos, Ch2Sounds.CH2L1_SWEEP, SoundCategory.BLOCKS, 0.7f, 0.9f + world.random.nextFloat() * 0.2f)
            }
            return ActionResult.CONSUME
        }
        val now = Ch2L1Bits.with(counter(player, sweep.swept), index)
        set(player, sweep.swept, now)
        world.setBlockState(pos, Blocks.AIR.defaultState)
        world.spawnParticles(ParticleTypes.POOF, pos.x + 0.5, pos.y + 0.15, pos.z + 0.5, 4, 0.25, 0.05, 0.25, 0.01)
        val total = cells.size
        val count = Ch2L1Bits.count(now)
        // No message: the objective panel counts the spots, and as the last one goes the coworker speaks, or the basement's lights go out.
        if (count < total) return ActionResult.CONSUME
        removeBroom(player)
        Ch2L1.setState(player, sweep.next, "${sweep.name} swept")
        return ActionResult.CONSUME
    }

    /**
     * A step of a held use of [on]: how far it has got, 0 to 1 (1: done, and the hold starts over). The bar goes to the client. The LAB's
     * reagents ([Ch2L1LabTaskHooks]) pour this way too.
     */
    internal fun fractionHeld(player: ServerPlayerEntity, on: String, needTicks: Int, label: String): Float {
        val tracker = holds.getOrPut(player.uuid) { HoldTracker() }
        val held = tracker.press(on, player.server.ticks.toLong())
        val done = held >= needTicks
        if (done) tracker.reset()
        sendHold(player, if (done) 1f else held.toFloat() / needTicks, label)
        return if (done) 1f else held.toFloat() / needTicks
    }

    private fun sendHold(player: ServerPlayerEntity, fraction: Float, label: String) {
        if (!ServerPlayNetworking.canSend(player, HOLD_PACKET)) return
        val buf = PacketByteBufs.create()
        buf.writeFloat(fraction)
        buf.writeString(label)
        ServerPlayNetworking.send(player, HOLD_PACKET, buf)
    }

    private fun removeBroom(player: ServerPlayerEntity) {
        val inventory = player.inventory
        for (slot in 0 until inventory.size()) {
            if (inventory.getStack(slot).isOf(Ch2Items.BROOM)) inventory.setStack(slot, ItemStack.EMPTY)
        }
    }

    // --- the state and the world ------------------------------------------------------------------

    /**
     * The counters and the props that [to] means. Going on, a task's counters are what the player did (or full, if the state was reached
     * by a command and they did nothing); going back to a task's state, or before it, they start over.
     */
    private fun onState(player: ServerPlayerEntity, from: Ch2L1State, to: Ch2L1State) {
        holds.remove(player.uuid)
        val rank = to.ordinal
        val rewound = from.ordinal > rank
        val task1 = Ch2L1State.ACT1_TASK1.ordinal
        val tubes = tasks.tubes.size
        when {
            rank > task1 -> {
                set(player, Ch2L1Tasks.TUBES, Ch2L1Bits.first(tubes))
                set(player, Ch2L1Tasks.REGISTERED, Ch2L1Bits.first(tubes))
            }
            rank < task1 || rewound -> {
                set(player, Ch2L1Tasks.TUBES, 0)
                set(player, Ch2L1Tasks.REGISTERED, 0)
            }
        }
        for (sweep in sweeps()) {
            val own = sweep.state.ordinal
            when {
                rank > own -> {
                    set(player, sweep.swept, Ch2L1Bits.first(dirtCells(sweep).size))
                    set(player, sweep.broom, 1)
                }
                rank < own || rewound -> {
                    set(player, sweep.swept, 0)
                    set(player, sweep.broom, 0)
                }
            }
        }
        if (sweeps().none { it.state == to }) removeBroom(player)
        reconcile(player)
    }

    /** The tubes, the dirt and the broom of the world as the player's state and counters say; nothing if the level is not built. */
    fun reconcile(player: ServerPlayerEntity) {
        if (!Ch2L1Build.isBuilt(player.server)) return
        val world = player.serverWorld
        val rank = Ch2L1.state(player).ordinal
        val taken = counter(player, Ch2L1Tasks.TUBES)
        tubeCells().forEachIndexed { i, pos -> pos?.let { put(world, it, rank <= Ch2L1State.ACT1_TASK1.ordinal && !Ch2L1Bits.has(taken, i)) } }
        for (sweep in sweeps()) {
            val swept = counter(player, sweep.swept)
            val own = sweep.state.ordinal
            dirtCells(sweep).forEachIndexed { i, pos -> put(world, pos, rank <= own && !Ch2L1Bits.has(swept, i)) }
            cell(sweep.broomAnchor)?.let { put(world, it, rank <= own && counter(player, sweep.broom) == 0) }
        }
    }

    private fun put(world: net.minecraft.server.world.ServerWorld, pos: BlockPos, present: Boolean) {
        val want = if (present) Ch2L1Build.planState(pos) ?: return else Blocks.AIR.defaultState
        if (world.getBlockState(pos) != want) world.setBlockState(pos, want, Block.NOTIFY_ALL)
    }

    /** `/et ch2 l1 tasks`: where the player is in the three tasks, one line each. */
    fun status(player: ServerPlayerEntity): List<String> {
        val taken = Ch2L1Bits.count(counter(player, Ch2L1Tasks.TUBES))
        val registered = Ch2L1Bits.count(counter(player, Ch2L1Tasks.REGISTERED))
        val (task3, basement) = sweeps()
        val swept = Ch2L1Bits.count(counter(player, task3.swept))
        val basementSwept = Ch2L1Bits.count(counter(player, basement.swept))
        return listOf(
            "State ${Ch2L1.state(player)}.",
            "Task 1: $taken/${tasks.tubes.size} tubes taken, $registered registered.",
            "Task 2: calibrates in ${tasks.calibrateTicks} ticks of a held click.",
            "Task 3: broom ${if (counter(player, Ch2L1Tasks.BROOM) != 0) "taken" else "not taken"}, $swept/${dirtCells(task3).size} patches swept.",
            "Overtime: broom ${if (counter(player, Ch2L1Tasks.BASEMENT_BROOM) != 0) "taken" else "not taken"}, " +
                "$basementSwept/${dirtCells(basement).size} patches swept in the basement.",
        )
    }

    /** `/et ch2 l1 tasks reset`: the tasks start over (the overtime's too), whatever the state. */
    fun reset(player: ServerPlayerEntity) {
        for (key in listOf(Ch2L1Tasks.TUBES, Ch2L1Tasks.REGISTERED, Ch2L1Tasks.SWEPT, Ch2L1Tasks.BROOM, Ch2L1Tasks.BASEMENT_SWEPT, Ch2L1Tasks.BASEMENT_BROOM)) {
            set(player, key, 0)
        }
        removeBroom(player)
        reconcile(player)
    }
}
