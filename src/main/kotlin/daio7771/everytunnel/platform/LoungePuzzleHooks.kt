package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.core.puzzle.OrderPuzzle
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.entity.ItemEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

/**
 * Level 4's Bar and Cinema puzzles, and the pieces of the master card.
 *
 * - **Bar:** four records wait on their racks ([PuzzleRackBlock]). One is carried at a time to the
 *   record player ([PuzzleDeviceBlock]). They go on in the menu's order; a wrong one sends them all
 *   back. Solved, the piece lies by the player.
 * - **Cinema:** four reels, by running time. The one of 94 minutes (the projection log says so) put
 *   into the projector runs for [PROJECTION_TICKS], then the piece lies by it. Another comes back out.
 * - **Hints:** after 3 failures the next right one glows ([OrderPuzzle.hint], [PuzzleRackBlock.HINT]).
 * - **The items stay in play:** a rack shows what nobody carries and is not on the device, so one
 *   dropped goes back to its rack.
 *
 * Racks, devices and the places for the pieces are the level's spawn points tagged `puzzle`
 * (`record_rack_<index>`, `record_player`, `piece_bar`...). A piece taken ([pieceTaken]; the third
 * lies in the pool, the fourth comes with the darts) is kept by [ProgressService]. Sounds are
 * vanilla's for now. What the film shows on the screen is still to come.
 */
object LoungePuzzleHooks {

    const val PIECE_LEVEL = "level_4"
    private const val LEVEL_ID = "level_4"
    private const val PROJECTION_TICKS = 100
    private const val SYNC_TICKS = 20

    /** One puzzle: its items, the block they are put into, where the piece lies when it is solved. */
    private class Puzzle(
        val items: List<Item>,
        val rackPrefix: String,
        val device: () -> Block,
        val pieceSpot: String,
        val pieceBlock: () -> Block,
        answer: List<Int>,
        val delayTicks: Int,
    ) {
        val order = OrderPuzzle(answer)
        var finishIn = -1
    }

    private val BAR = Puzzle(
        ModItems.RECORDS, "record_rack", { ModBlocks.RECORD_PLAYER }, "piece_bar", { ModBlocks.LOOT_CARD_NW }, listOf(0, 1, 2, 3), 0,
    )
    private val CINEMA = Puzzle(
        ModItems.REELS, "reel_rack", { ModBlocks.PROJECTOR }, "piece_cinema", { ModBlocks.LOOT_CARD_NE }, listOf(1), PROJECTION_TICKS,
    )
    private val PUZZLES = listOf(BAR, CINEMA)

    /** Each piece and its id in [ProgressService]. */
    private val PIECES: Map<Item, String> = mapOf(
        ModItems.CARD_NW to "ch1_l4_piece_bar", ModItems.CARD_NE to "ch1_l4_piece_cinema",
        ModItems.CARD_SW to "ch1_l4_piece_spa", ModItems.CARD_SE to "ch1_l4_piece_game",
    )

    private var server: MinecraftServer? = null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            reset()
        }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    /** A new game, or the map rebuilt. */
    fun reset() {
        for (puzzle in PUZZLES) {
            puzzle.order.reset()
            puzzle.finishIn = -1
        }
    }

    /** A piece was picked up: kept in the player's progress. */
    fun pieceTaken(player: ServerPlayerEntity, item: Item) {
        val piece = PIECES[item] ?: return
        ProgressService.get().addPiece(player, PIECE_LEVEL, piece)
    }

    fun useRack(player: ServerPlayerEntity, state: BlockState) {
        val puzzle = PUZZLES.firstOrNull { state.block == rackBlock(it) } ?: return
        if (puzzle.order.solved) return
        val carried = carried(player, puzzle)
        if (carried != null) {
            // Back on its own rack, whichever was clicked.
            carried.decrement(1)
        } else if (state.get(PuzzleRackBlock.HAS)) {
            if (!player.getStackInHand(Hand.MAIN_HAND).isEmpty) {
                player.sendMessage(Text.translatable("message.every_tunnel.hands_full"), true)
                return
            }
            val item = puzzle.items[state.get(PuzzleRackBlock.INDEX)]
            player.setStackInHand(Hand.MAIN_HAND, ItemStack(item))
            player.sendMessage(item.name, true)
        }
        play(player, SoundEvents.ITEM_BOOK_PAGE_TURN)
        sync(player.server)
    }

    fun useDevice(player: ServerPlayerEntity, state: BlockState) {
        val puzzle = PUZZLES.firstOrNull { state.block == it.device() } ?: return
        if (puzzle.order.solved) return
        val stack = player.getStackInHand(Hand.MAIN_HAND)
        val index = puzzle.items.indexOf(stack.item)
        if (index < 0) return
        stack.decrement(1)
        when (puzzle.order.attempt(index)) {
            OrderPuzzle.Result.RIGHT -> play(player, SoundEvents.BLOCK_NOTE_BLOCK_HARP.value())
            OrderPuzzle.Result.WRONG -> play(player, SoundEvents.BLOCK_GRINDSTONE_USE)
            OrderPuzzle.Result.SOLVED -> {
                play(player, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value())
                puzzle.finishIn = puzzle.delayTicks
                EveryTunnel.LOGGER.info("{} solved a puzzle of Level 4 ({})", player.name.string, puzzle.pieceSpot)
            }
        }
        sync(player.server)
    }

    private fun rackBlock(puzzle: Puzzle): Block = if (puzzle === BAR) ModBlocks.RECORD_RACK else ModBlocks.REEL_RACK

    private fun carried(player: ServerPlayerEntity, puzzle: Puzzle): ItemStack? {
        val inventory = player.inventory
        for (slot in 0 until inventory.size()) {
            val stack = inventory.getStack(slot)
            if (stack.item in puzzle.items) return stack
        }
        return null
    }

    private fun tick(server: MinecraftServer) {
        for (puzzle in PUZZLES) {
            if (puzzle.finishIn < 0) continue
            if (puzzle.finishIn-- == 0) putPiece(server, puzzle)
        }
        if (server.ticks % SYNC_TICKS == 0) sync(server)
    }

    private fun putPiece(server: MinecraftServer, puzzle: Puzzle) {
        val (level, world) = place(server) ?: return
        val pos = pointOf(level, puzzle.pieceSpot) ?: return
        world.setBlockState(pos, puzzle.pieceBlock().defaultState)
    }

    /** Racks and glow as things stand; dropped items go back (they are taken off the floor). */
    private fun sync(server: MinecraftServer) {
        val (level, world) = place(server) ?: return
        if (level.spawnPoints.none { it.id == "record_player" } || LoungeHooks.notBuiltYet(level)) return
        val players = server.playerManager.playerList
        for (puzzle in PUZZLES) {
            val device = pointOf(level, if (puzzle === BAR) "record_player" else "projector") ?: continue
            val dropped = world.getEntitiesByClass(ItemEntity::class.java, Box(device).expand(SEARCH_BLOCKS)) { it.stack.item in puzzle.items }
            dropped.forEach { it.discard() }
            val inHands = players.flatMap { player -> (0 until player.inventory.size()).map { player.inventory.getStack(it).item } }
            for ((index, item) in puzzle.items.withIndex()) {
                val pos = pointOf(level, "${puzzle.rackPrefix}_$index") ?: continue
                val state = world.getBlockState(pos)
                if (state.block != rackBlock(puzzle)) continue
                val on = !puzzle.order.solved && item !in inHands && index !in puzzle.order.placed
                val next = state.with(PuzzleRackBlock.HAS, on).with(PuzzleRackBlock.HINT, on && puzzle.order.hint == index)
                if (next != state) world.setBlockState(pos, next)
            }
        }
    }

    private fun place(server: MinecraftServer): Pair<LevelDefinition, ServerWorld>? {
        val level = LevelManager.get(LEVEL_ID) ?: return null
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return null
        return level to world
    }

    private fun pointOf(level: LevelDefinition, id: String): BlockPos? =
        level.spawnPoint(id)?.let { BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) }

    private fun play(player: ServerPlayerEntity, sound: SoundEvent) {
        player.serverWorld.playSound(null, player.x, player.y, player.z, sound, SoundCategory.BLOCKS, 0.7f, 1f)
    }

    private const val SEARCH_BLOCKS = 32.0
}
