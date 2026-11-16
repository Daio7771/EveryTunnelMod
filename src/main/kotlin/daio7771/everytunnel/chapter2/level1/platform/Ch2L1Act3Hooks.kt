package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Act3
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Bits
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.chapter2.platform.Ch2Items
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.chapter2.prologue.platform.ScientistEntity
import daio7771.everytunnel.platform.CardReaderBlock
import daio7771.everytunnel.platform.ModBlocks
import daio7771.everytunnel.platform.ModSounds
import daio7771.everytunnel.platform.TickProfiler
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.DoorBlock
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Act 3 of Chapter 2's Level 1, its first part ([Ch2L1State.ACT3_EXPLORE], `act3.json`, [Ch2L1Act3]): sneaking into the auxiliary lab, the
 * authorized corridor and Rowan's office (no patrols yet: phase 7).
 *
 * - **The auxiliary lab**: its badge reader ([BadgeReaderBlock]) takes the coworker's badge: green, the accept sound, and the grey door
 *   opens and stays open ([Ch2L1Doors.set] with `locked`). Without the badge it buzzes.
 * - **The computers** ([NonameTerminalBlock], the ones `act3.json` names; task 1's is [Ch2L1TaskHooks]'): NonameOS opens on their files
 *   ([FILES_PACKET]); the client says when the player closes one ([FILE_CLOSED_PACKET]), and only then it counts as read. The authorization
 *   (`code_file`) moves the objective on to the way into the office; closing the second of Rowan's files ([Ch2L1Act3.triggerComputer]) is the
 *   office cinematic's moment ([onOfficeTrigger]: phase 8 begins it; until then a line in the log, and in a development run one in chat).
 * - **The keypad** ([KeypadBlock]): its screen sends the code typed ([KEYPAD_TRY_PACKET]); Rowan's opens the office's grey doors for good,
 *   anything else buzzes ([KEYPAD_RESULT_PACKET] tells the screen which).
 * - **The office**: stepping in moves the objective on to searching it ([Ch2L1Act3.IN_OFFICE]).
 * - **Doors that say no**: a click on the restricted door at the end of the authorized corridor, on the sealed zone's door before the
 *   overtime, or on a locked door that is still shut, says so in the action bar. The portrait's plaque is read the same way.
 * - **Chamber 02**: while someone plays the level, the scientist stands in it as the Pale man he became ([ScientistEntity.CONTAINED]), his
 *   back to the chamber's front; never saved, put back if lost.
 *
 * Going back before Act 3 (a new hire, `/et ch2 l1 state`) locks the doors again, turns the reader and the keypad red and forgets the
 * act's counters ([Ch2L1Act3.PREFIX]). Server thread only.
 */
object Ch2L1Act3Hooks {

    /** To the client: a computer's files (its id, then each file's id and whether it has been read). */
    val FILES_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_files")

    /** To the server: the player closed a file (the computer's id, the file's). */
    val FILE_CLOSED_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_file_closed")

    /** To the client: the keypad's screen, for the keypad at this position (and whether it is already open). */
    val KEYPAD_OPEN_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_keypad_open")

    /** To the server: the code typed on the keypad at a position. */
    val KEYPAD_TRY_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_keypad_try")

    /** To the client: whether the code was right. */
    val KEYPAD_RESULT_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_keypad_result")

    /** Told when a player has read the last of Rowan's files (phase 8: the office's cinematic). */
    fun interface OfficeTrigger {
        fun reached(player: ServerPlayerEntity)
    }

    private const val KEY = "every_tunnel.ch2.l1."

    /** How far from a keypad or a computer a player may be for what its screen sends to count. */
    private const val REACH = 8.0

    /** How near a door's anchor a clicked grey door is that door; how often the office and the chamber are looked at. */
    private const val DOOR_NEAR = 2.6
    private const val CHECK_TICKS = 10
    private const val CHAMBER_TICKS = 100

    private val triggers = CopyOnWriteArrayList<OfficeTrigger>()
    private var contained: ScientistEntity? = null

    private val act3 get() = Ch2L1DataLoader.act3

    fun register() {
        Ch2L1.onStateChange(::onState)
        TickProfiler.endServerTick.register(::tick)
        UseBlockCallback.EVENT.register { player, world, _, hit ->
            if (world.isClient || player !is ServerPlayerEntity || !Ch2L1.active(player)) return@register ActionResult.PASS
            val pos = hit.blockPos
            if (!world.getBlockState(pos).isOf(ModBlocks.EMERGENCY_DOOR)) return@register ActionResult.PASS
            doorClicked(player, pos)
        }
        ServerPlayNetworking.registerGlobalReceiver(FILE_CLOSED_PACKET) { server, player, _, buf, _ ->
            val computer = buf.readString(64)
            val file = buf.readString(64)
            server.execute { fileClosed(player, computer, file) }
        }
        ServerPlayNetworking.registerGlobalReceiver(KEYPAD_TRY_PACKET) { server, player, _, buf, _ ->
            val pos = buf.readBlockPos()
            val code = buf.readString(Ch2L1Act3.MAX_CODE)
            server.execute { tryCode(player, pos, code) }
        }
    }

    fun onOfficeTrigger(listener: OfficeTrigger) {
        triggers += listener
    }

    // --- where things are -------------------------------------------------------------------------

    private fun point(anchorId: String): Vec3d? = Ch2L1DataLoader.data.anchors.point(anchorId)?.position?.let { Vec3d(it.x, it.y, it.z) }

    private fun cell(anchorId: String): BlockPos? = point(anchorId)?.let { BlockPos.ofFloored(it) }

    private fun counter(player: ServerPlayerEntity, key: String) = Ch2Progress.ch2L1Counter(player, key)

    private fun set(player: ServerPlayerEntity, key: String, value: Int) = Ch2Progress.setCh2L1Counter(player, key, value)

    private fun inAct3(player: ServerPlayerEntity) = Ch2L1.state(player) >= Ch2L1State.ACT3_EXPLORE

    private fun say(player: ServerPlayerEntity, key: String) = player.sendMessage(Text.translatable(KEY + key), true)

    // --- the badge reader -------------------------------------------------------------------------

    /** The badge reader clicked: with the coworker's badge, in Act 3, it opens the auxiliary lab. */
    fun useReader(player: ServerPlayerEntity, pos: BlockPos): ActionResult {
        if (pos != cell(act3.readerAnchor)) return ActionResult.PASS
        val world = player.serverWorld
        val state = world.getBlockState(pos)
        if (state.get(CardReaderBlock.UNLOCKED)) return ActionResult.CONSUME
        if (!inAct3(player) || !player.inventory.containsAny { it.isOf(Ch2Items.LAB_BADGE_COPY) }) {
            world.playSound(null, pos, ModSounds.CARD_READER_DENY, SoundCategory.BLOCKS, 1f, 1f)
            say(player, "reader.denied")
            return ActionResult.CONSUME
        }
        world.setBlockState(pos, state.with(CardReaderBlock.UNLOCKED, true))
        world.playSound(null, pos, ModSounds.CARD_READER_ACCEPT, SoundCategory.BLOCKS, 1f, 1f)
        point(act3.labDoor)?.let { Ch2L1Doors.set(world, it, DOOR_NEAR, true, player, locked = true) }
        set(player, Ch2L1Act3.LAB_OPEN, 1)
        say(player, "reader.granted")
        return ActionResult.CONSUME
    }

    // --- the keypad ------------------------------------------------------------------------------

    /** The keypad clicked: its screen opens (already open, it only beeps). */
    fun useKeypad(player: ServerPlayerEntity, pos: BlockPos): ActionResult {
        if (pos != cell(act3.keypadAnchor)) return ActionResult.PASS
        val open = player.serverWorld.getBlockState(pos).get(CardReaderBlock.UNLOCKED)
        player.serverWorld.playSound(null, pos, Ch2Sounds.CH2L1_KEYPAD_KEY, SoundCategory.BLOCKS, 0.6f, 1f)
        if (open || !ServerPlayNetworking.canSend(player, KEYPAD_OPEN_PACKET)) return ActionResult.CONSUME
        val buf = PacketByteBufs.create()
        buf.writeBlockPos(pos)
        ServerPlayNetworking.send(player, KEYPAD_OPEN_PACKET, buf)
        return ActionResult.CONSUME
    }

    /** A code typed on the keypad at [pos]: Rowan's (in Act 3) opens the office; anything else buzzes. */
    private fun tryCode(player: ServerPlayerEntity, pos: BlockPos, code: String) {
        if (!Ch2L1.active(player) || pos != cell(act3.keypadAnchor) || !near(player, pos)) return
        val world = player.serverWorld
        val ok = inAct3(player) && act3.accepts(code)
        val result = PacketByteBufs.create()
        result.writeBoolean(ok)
        if (ServerPlayNetworking.canSend(player, KEYPAD_RESULT_PACKET)) ServerPlayNetworking.send(player, KEYPAD_RESULT_PACKET, result)
        if (!ok) {
            world.playSound(null, pos, Ch2Sounds.CH2L1_KEYPAD_ERROR, SoundCategory.BLOCKS, 0.8f, 1f)
            return
        }
        val state = world.getBlockState(pos)
        if (state.contains(CardReaderBlock.UNLOCKED)) world.setBlockState(pos, state.with(CardReaderBlock.UNLOCKED, true))
        world.playSound(null, pos, Ch2Sounds.CH2L1_KEYPAD_ACCEPT, SoundCategory.BLOCKS, 0.9f, 1f)
        point(act3.officeDoor)?.let { Ch2L1Doors.set(world, it, DOOR_NEAR, true, player, locked = true) }
        set(player, Ch2L1Act3.OFFICE_OPEN, 1)
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: {} opens the office with Rowan's code", player.gameProfile.name)
    }

    private fun near(player: ServerPlayerEntity, pos: BlockPos) = player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= REACH * REACH

    // --- the computers -------------------------------------------------------------------------

    /** A NonameOS computer of Act 3 clicked: its files open. PASS if it is not one of them (task 1's terminal). */
    fun useTerminal(player: ServerPlayerEntity, pos: BlockPos): ActionResult {
        val computer = act3.computers.firstOrNull { cell(it.anchor) == pos } ?: return ActionResult.PASS
        if (!inAct3(player)) {
            say(player, "task.not_now")
            return ActionResult.CONSUME
        }
        player.serverWorld.playSound(null, pos, ModSounds.TERMINAL_KEY, SoundCategory.BLOCKS, 0.6f, 1f)
        if (!ServerPlayNetworking.canSend(player, FILES_PACKET)) return ActionResult.CONSUME
        val buf = PacketByteBufs.create()
        buf.writeString(computer.id)
        buf.writeVarInt(computer.files.size)
        for (file in computer.files) {
            buf.writeString(file)
            buf.writeBoolean(act3.isRead(file) { counter(player, it) })
        }
        ServerPlayNetworking.send(player, FILES_PACKET, buf)
        return ActionResult.CONSUME
    }

    /** The player closed [file] of [computerId]: it is read; Rowan's last one is the office cinematic's moment. */
    private fun fileClosed(player: ServerPlayerEntity, computerId: String, file: String) {
        if (!Ch2L1.active(player) || !inAct3(player)) return
        val computer = act3.computer(computerId) ?: return
        val at = cell(computer.anchor) ?: return
        if (file !in computer.files || !near(player, at)) return
        val bit = act3.fileBit(file)
        set(player, Ch2L1Act3.READ, Ch2L1Bits.with(counter(player, Ch2L1Act3.READ), bit))
        val read: (String) -> Int = { counter(player, it) }
        if (computer.id != act3.triggerComputer || !act3.allRead(computer, read) || counter(player, Ch2L1Act3.TRIGGERED) > 0) return
        set(player, Ch2L1Act3.TRIGGERED, 1)
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: {} has read Rowan's files: the office cinematic's moment", player.gameProfile.name)
        if (FabricLoader.getInstance().isDevelopmentEnvironment) {
            player.sendMessage(Text.literal("[dev] Both of Rowan's files read: the office cinematic begins here (phase 8)."), false)
        }
        for (trigger in triggers) {
            try {
                trigger.reached(player)
            } catch (e: Exception) {
                EveryTunnel.LOGGER.error("Chapter 2 Level 1: an office trigger failed", e)
            }
        }
    }

    // --- the portrait and the doors that say no ---------------------------------------------------

    /** The portrait over Rowan's desk: its plaque, in the action bar. */
    fun usePortrait(player: ServerPlayerEntity): ActionResult {
        say(player, "painting_plaque")
        return ActionResult.CONSUME
    }

    /** A grey door clicked: the restricted one, the sealed zone's before the overtime, or a locked one still shut, says why. */
    private fun doorClicked(player: ServerPlayerEntity, pos: BlockPos): ActionResult {
        fun at(anchorId: String): Boolean {
            val p = point(anchorId) ?: return false
            val dx = pos.x + 0.5 - p.x
            val dz = pos.z + 0.5 - p.z
            return dx * dx + dz * dz <= DOOR_NEAR * DOOR_NEAR && pos.y in (p.y.toInt() - 1)..(p.y.toInt() + 2)
        }
        val key = when {
            act3.restrictedDoors.any(::at) -> "restricted_door"
            at(act3.sealedDoor) -> if (Ch2L1.state(player) < Ch2L1State.ACT3_OVERTIME) "sealed_door" else return ActionResult.PASS
            at(act3.labDoor) || at(act3.officeDoor) -> if (player.serverWorld.getBlockState(pos).get(DoorBlock.OPEN)) return ActionResult.PASS else "door_locked"
            else -> return ActionResult.PASS
        }
        say(player, key)
        return ActionResult.CONSUME
    }

    // --- the office and the chamber --------------------------------------------------------------

    private fun tick(server: MinecraftServer) {
        if (server.ticks % CHECK_TICKS == 0) {
            val zone = Ch2L1DataLoader.data.anchors.zone(act3.officeZone)
            for (player in server.playerManager.playerList) {
                if (zone == null || !Ch2L1.active(player) || Ch2L1.state(player) != Ch2L1State.ACT3_EXPLORE) continue
                if (counter(player, Ch2L1Act3.IN_OFFICE) == 0 && zone.box.contains(player.x, player.y, player.z)) {
                    set(player, Ch2L1Act3.IN_OFFICE, 1)
                }
            }
        }
        if (server.ticks % CHAMBER_TICKS == 0) ensureContained(server)
    }

    /** Chamber 02's occupant is there while someone plays the level and its chunk is loaded; gone when nobody does. */
    private fun ensureContained(server: MinecraftServer) {
        val world = Ch2L1Npcs.world(server) ?: return
        val anchor = Ch2L1DataLoader.data.anchors.point(act3.chamberAnchor)
        val playing = anchor != null && Ch2L1Build.isBuilt(server) &&
            server.playerManager.playerList.any { it.serverWorld == world && Ch2L1.active(it) }
        val current = contained?.takeIf { it.isAlive && it.world == world }
        if (!playing) {
            current?.discard()
            contained = null
            return
        }
        val p = anchor!!.position
        if (current != null && current.squaredDistanceTo(p.x, p.y, p.z) < 1.0) return
        current?.discard()
        if (!world.isChunkLoaded(BlockPos.ofFloored(p.x, p.y, p.z))) {
            contained = null
            return
        }
        // Any other one left round the anchor (a reload of the data, a lost reference) goes first.
        world.getEntitiesByType(Ch2Entities.SCIENTIST, Box.of(Vec3d(p.x, p.y, p.z), 6.0, 6.0, 6.0)) { true }.forEach { it.discard() }
        val entity = ScientistEntity(Ch2Entities.SCIENTIST, world)
        entity.stage = ScientistEntity.CONTAINED
        entity.refreshPositionAndAngles(p.x, p.y, p.z, anchor.yaw, 0f)
        entity.headYaw = anchor.yaw
        world.spawnEntity(entity)
        contained = entity
    }

    /** The level is left: chamber 02's occupant goes. */
    fun clear() {
        contained?.discard()
        contained = null
    }

    // --- going back --------------------------------------------------------------------------------

    private fun onState(player: ServerPlayerEntity, from: Ch2L1State, to: Ch2L1State) {
        if (to >= Ch2L1State.ACT3_EXPLORE || (from < Ch2L1State.ACT3_EXPLORE && to != Ch2L1State.START)) return
        relock(player)
    }

    /** The auxiliary lab and the office shut, the reader and the keypad red, the act's counters forgotten. */
    fun relock(player: ServerPlayerEntity) {
        val world = player.serverWorld
        for (door in listOf(act3.labDoor, act3.officeDoor)) point(door)?.let { Ch2L1Doors.set(world, it, DOOR_NEAR, false, locked = true) }
        for (lock in listOf(act3.readerAnchor, act3.keypadAnchor)) {
            val pos = cell(lock) ?: continue
            val state = world.getBlockState(pos)
            if (state.contains(CardReaderBlock.UNLOCKED) && state.get(CardReaderBlock.UNLOCKED)) world.setBlockState(pos, state.with(CardReaderBlock.UNLOCKED, false))
        }
        Ch2Progress.clearCh2L1Counters(player, Ch2L1Act3.PREFIX)
    }
}
