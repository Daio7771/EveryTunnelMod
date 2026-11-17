package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Bits
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Gauge
import daio7771.everytunnel.chapter2.level1.core.Ch2L1LabTasksSpec
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Tasks
import daio7771.everytunnel.chapter2.platform.Ch2Items
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.platform.ModSounds
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

/**
 * The LAB's two tasks, between calibrating the analyzer and sweeping (`tasks.json`'s `reagents` and `gauge`,
 * [Ch2L1LabTasksSpec]):
 *
 * - **The reagents** ([Ch2L1State.ACT1_REAGENTS]): the three bottles stand on the reagent cabinet ([ReagentBottleBlock]); a click takes one
 *   into the player's hand (its item, `factory_reagent_<colour>`); a click held on the mixing tank ([MixingTankBlock]) for `pour_seconds`
 *   pours it in (the bar of [Ch2L1TaskHooks.fractionHeld]), in any order. The third poured moves on to the gauge.
 * - **The gauge** ([Ch2L1State.ACT1_GAUGE]): the centrifuge ([CentrifugeBlock]) shows a speed drawn for this player ([Ch2L1Gauge.reading],
 *   [Ch2L1Tasks.GAUGE_RPM]; the client draws it on its display); the terminal beside it opens NonameOS's maintenance log
 *   ([GAUGE_OPEN_PACKET]), the speed typed comes back ([GAUGE_TRY_PACKET]) and is answered ([GAUGE_RESULT_PACKET]): right, it is logged
 *   and the last task, sweeping, begins; wrong, a buzz, and it can be typed again.
 *
 * As with the other tasks, a change of state ([onState]) brings the counters and the bottles of the world to what the state means. Server
 * thread only.
 */
object Ch2L1LabTaskHooks {

    /** To the client: the maintenance log opens (the terminal's position). */
    val GAUGE_OPEN_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_gauge_open")

    /** To the server: the speed typed at the terminal at this position. */
    val GAUGE_TRY_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_gauge_try")

    /** To the client: whether it was right. */
    val GAUGE_RESULT_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_gauge_result")

    private const val KEY = "every_tunnel.ch2.l1."
    private const val REACH = 8.0

    private val spec: Ch2L1LabTasksSpec get() = Ch2L1DataLoader.tasks.lab

    fun register() {
        Ch2L1.onStateChange(::onState)
        ServerPlayNetworking.registerGlobalReceiver(GAUGE_TRY_PACKET) { server, player, _, buf, _ ->
            val pos = buf.readBlockPos()
            val typed = buf.readString(Ch2L1Gauge.MAX_DIGITS)
            server.execute { tryReading(player, pos, typed) }
        }
    }

    // --- where things are -------------------------------------------------------------------------

    private fun cell(anchorId: String): BlockPos? =
        Ch2L1DataLoader.data.anchors.point(anchorId)?.position?.let { BlockPos.ofFloored(it.x, it.y, it.z) }

    private fun bottleCells(): List<BlockPos?> = spec.bottles.map { cell(it.anchor) }

    private fun counter(player: ServerPlayerEntity, key: String) = Ch2Progress.ch2L1Counter(player, key)

    private fun set(player: ServerPlayerEntity, key: String, value: Int) = Ch2Progress.setCh2L1Counter(player, key, value)

    private fun notNow(player: ServerPlayerEntity): ActionResult {
        player.sendMessage(Text.translatable(KEY + "task.not_now"), true)
        return ActionResult.CONSUME
    }

    private fun blip(player: ServerPlayerEntity, pos: BlockPos, pitch: Float, volume: Float = 0.8f) {
        player.serverWorld.playSound(null, pos, Ch2Sounds.CH2L1_BLIP, SoundCategory.BLOCKS, volume, pitch)
    }

    private fun reagentItem(index: Int) = Ch2Items.REAGENTS.getValue(spec.bottles[index].colour)

    // --- the reagents --------------------------------------------------------------------------

    /** A bottle on the cabinet clicked: taken, into the player's hand. */
    fun useBottle(player: ServerPlayerEntity, pos: BlockPos): ActionResult {
        val index = bottleCells().indexOf(pos)
        if (index < 0) return ActionResult.PASS
        if (Ch2L1.state(player) != Ch2L1State.ACT1_REAGENTS) return notNow(player)
        val taken = counter(player, Ch2L1Tasks.REAGENTS_TAKEN)
        if (Ch2L1Bits.has(taken, index)) return ActionResult.CONSUME
        set(player, Ch2L1Tasks.REAGENTS_TAKEN, Ch2L1Bits.with(taken, index))
        player.serverWorld.setBlockState(pos, Blocks.AIR.defaultState)
        val bottle = ItemStack(reagentItem(index))
        if (player.mainHandStack.isEmpty) player.setStackInHand(Hand.MAIN_HAND, bottle) else player.giveItemStack(bottle)
        blip(player, pos, 1.4f, 0.5f)
        return ActionResult.CONSUME
    }

    /** The mixing tank used: with a bottle in hand that is not poured yet, one more step of pouring it. */
    fun useTank(player: ServerPlayerEntity, pos: BlockPos): ActionResult {
        if (pos != cell(spec.tankAnchor)) return ActionResult.PASS
        if (Ch2L1.state(player) != Ch2L1State.ACT1_REAGENTS) return notNow(player)
        val poured = counter(player, Ch2L1Tasks.REAGENTS_POURED)
        val index = spec.bottles.indices.firstOrNull { player.mainHandStack.isOf(reagentItem(it)) && !Ch2L1Bits.has(poured, it) }
        if (index == null) {
            player.sendMessage(Text.translatable(KEY + "reagent.need"), true)
            return ActionResult.CONSUME
        }
        val world = player.serverWorld
        val fraction = Ch2L1TaskHooks.fractionHeld(player, "tank:$index", spec.pourTicks, KEY + "hold.pouring")
        if (fraction < 1f) {
            if ((fraction * spec.pourTicks).toInt() % 6 == 0) {
                world.playSound(null, pos, ModSounds.TERMINAL_KEY, SoundCategory.BLOCKS, 0.25f, 0.5f + fraction * 0.4f)
            }
            return ActionResult.CONSUME
        }
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY)
        val now = Ch2L1Bits.with(poured, index)
        set(player, Ch2L1Tasks.REAGENTS_POURED, now)
        world.spawnParticles(ParticleTypes.SPLASH, pos.x + 0.5, pos.y + 1.05, pos.z + 0.5, 8, 0.2, 0.05, 0.2, 0.05)
        blip(player, pos, 1.1f + 0.15f * Ch2L1Bits.count(now))
        if (Ch2L1Bits.count(now) == spec.bottles.size) Ch2L1.setState(player, Ch2L1State.ACT1_GAUGE, "reagents poured")
        return ActionResult.CONSUME
    }

    private fun removeBottles(player: ServerPlayerEntity) {
        val inventory = player.inventory
        for (slot in 0 until inventory.size()) {
            if (spec.bottles.indices.any { inventory.getStack(slot).isOf(reagentItem(it)) }) inventory.setStack(slot, ItemStack.EMPTY)
        }
    }

    // --- the gauge -----------------------------------------------------------------------------

    /** The centrifuge clicked: it says where its speed is logged. */
    fun useCentrifuge(player: ServerPlayerEntity, pos: BlockPos): ActionResult {
        if (pos != cell(spec.centrifugeAnchor)) return ActionResult.PASS
        if (Ch2L1.state(player) != Ch2L1State.ACT1_GAUGE) return notNow(player)
        player.sendMessage(Text.translatable(KEY + "gauge.hint"), true)
        return ActionResult.CONSUME
    }

    /** The terminal beside the centrifuge clicked: its maintenance log opens. PASS if it is another terminal. */
    fun useGaugeTerminal(player: ServerPlayerEntity, pos: BlockPos): ActionResult {
        if (pos != cell(spec.gaugeTerminalAnchor)) return ActionResult.PASS
        if (Ch2L1.state(player) != Ch2L1State.ACT1_GAUGE) return notNow(player)
        player.serverWorld.playSound(null, pos, ModSounds.TERMINAL_KEY, SoundCategory.BLOCKS, 0.6f, 1f)
        if (!ServerPlayNetworking.canSend(player, GAUGE_OPEN_PACKET)) return ActionResult.CONSUME
        val buf = PacketByteBufs.create()
        buf.writeBlockPos(pos)
        ServerPlayNetworking.send(player, GAUGE_OPEN_PACKET, buf)
        return ActionResult.CONSUME
    }

    /** A speed typed at the terminal at [pos]: the centrifuge's logs it and the sweeping begins; anything else buzzes. */
    private fun tryReading(player: ServerPlayerEntity, pos: BlockPos, typed: String) {
        if (!Ch2L1.active(player) || pos != cell(spec.gaugeTerminalAnchor)) return
        if (player.squaredDistanceTo(Vec3d.ofCenter(pos)) > REACH * REACH) return
        val ok = Ch2L1.state(player) == Ch2L1State.ACT1_GAUGE && Ch2L1Gauge.accepts(typed, counter(player, Ch2L1Tasks.GAUGE_RPM))
        if (ServerPlayNetworking.canSend(player, GAUGE_RESULT_PACKET)) {
            val result = PacketByteBufs.create()
            result.writeBoolean(ok)
            ServerPlayNetworking.send(player, GAUGE_RESULT_PACKET, result)
        }
        val world = player.serverWorld
        if (!ok) {
            world.playSound(null, pos, Ch2Sounds.CH2L1_KEYPAD_ERROR, SoundCategory.BLOCKS, 0.8f, 1f)
            return
        }
        world.playSound(null, pos, Ch2Sounds.CH2L1_KEYPAD_ACCEPT, SoundCategory.BLOCKS, 0.9f, 1f)
        Ch2L1.setState(player, Ch2L1State.ACT1_TASK3, "the centrifuge logged")
    }

    // --- the state and the world ----------------------------------------------------------------

    /**
     * The counters and the bottles that [to] means: past the reagents, every bottle taken and poured; at them or before (or coming back),
     * none. The gauge's speed is drawn on reaching it (if it is not yet), and forgotten before it.
     */
    private fun onState(player: ServerPlayerEntity, from: Ch2L1State, to: Ch2L1State) {
        val all = Ch2L1Bits.first(spec.bottles.size)
        when {
            to > Ch2L1State.ACT1_REAGENTS -> {
                set(player, Ch2L1Tasks.REAGENTS_TAKEN, all)
                set(player, Ch2L1Tasks.REAGENTS_POURED, all)
            }
            to < Ch2L1State.ACT1_REAGENTS || from > to -> {
                set(player, Ch2L1Tasks.REAGENTS_TAKEN, 0)
                set(player, Ch2L1Tasks.REAGENTS_POURED, 0)
            }
        }
        if (to != Ch2L1State.ACT1_REAGENTS) removeBottles(player)
        when {
            to < Ch2L1State.ACT1_GAUGE -> set(player, Ch2L1Tasks.GAUGE_RPM, 0)
            to == Ch2L1State.ACT1_GAUGE && counter(player, Ch2L1Tasks.GAUGE_RPM) == 0 ->
                set(player, Ch2L1Tasks.GAUGE_RPM, Ch2L1Gauge.reading(spec, player.random.nextInt()))
        }
        reconcile(player)
    }

    /** The bottles on the cabinet as the player's state and counters say; nothing if the level is not built. */
    fun reconcile(player: ServerPlayerEntity) {
        if (!Ch2L1Build.isBuilt(player.server)) return
        val world = player.serverWorld
        val rank = Ch2L1.state(player)
        val taken = counter(player, Ch2L1Tasks.REAGENTS_TAKEN)
        bottleCells().forEachIndexed { i, pos -> pos?.let { put(world, it, rank <= Ch2L1State.ACT1_REAGENTS && !Ch2L1Bits.has(taken, i)) } }
    }

    private fun put(world: ServerWorld, pos: BlockPos, present: Boolean) {
        val want = if (present) Ch2L1Build.planState(pos) ?: return else Blocks.AIR.defaultState
        if (world.getBlockState(pos) != want) world.setBlockState(pos, want, Block.NOTIFY_ALL)
    }

    /** `/et ch2 l1 tasks`: where the player is in the LAB's two tasks. */
    fun status(player: ServerPlayerEntity): List<String> = listOf(
        "Reagents: ${Ch2L1Bits.count(counter(player, Ch2L1Tasks.REAGENTS_TAKEN))}/${spec.bottles.size} taken, " +
            "${Ch2L1Bits.count(counter(player, Ch2L1Tasks.REAGENTS_POURED))} poured.",
        "Gauge: the centrifuge shows ${counter(player, Ch2L1Tasks.GAUGE_RPM).takeIf { it > 0 } ?: "nothing yet"}.",
    )

    /** `/et ch2 l1 tasks reset`: the LAB's two tasks start over. */
    fun reset(player: ServerPlayerEntity) {
        for (key in listOf(Ch2L1Tasks.REAGENTS_TAKEN, Ch2L1Tasks.REAGENTS_POURED, Ch2L1Tasks.GAUGE_RPM)) set(player, key, 0)
        removeBottles(player)
        if (Ch2L1.state(player) == Ch2L1State.ACT1_GAUGE) set(player, Ch2L1Tasks.GAUGE_RPM, Ch2L1Gauge.reading(spec, player.random.nextInt()))
        reconcile(player)
    }
}
