package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.achievement.Achievement
import daio7771.everytunnel.core.film.FilmInfo
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.core.puzzle.OrderPuzzle
import daio7771.everytunnel.core.save.LoungeSave
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
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
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

/**
 * Level 4's Bar and Cinema puzzles, and the pieces of the master card.
 *
 * - **Bar:** four records wait on their racks ([PuzzleRackBlock]). One is carried at a time to the
 *   record player ([PuzzleDeviceBlock]). They go on in the menu's order, drawn each game ([noteValues]);
 *   a wrong one sends them all back. Solved, the piece lies by the player.
 * - **Cinema:** four reels, by running time. Another than the one the projection log names, drawn each
 *   game, comes back out. That one in the projector waits until someone stands in the auditorium,
 *   out of the booth: then the film runs on the screen, once (`client.effects.FilmPlayer`), and when
 *   it ends the piece lies by the projector. "Do not rewind."
 * - **Hints:** after 3 failures the next right one glows ([OrderPuzzle.hint], [PuzzleRackBlock.HINT]).
 * - **The items stay in play:** a rack shows what nobody carries and is not on the device, so one
 *   dropped goes back to its rack.
 *
 * Racks, devices and the places for the pieces are the level's spawn points tagged `puzzle`
 * (`record_rack_<index>`, `record_player`, `piece_bar`...). A piece taken ([pieceTaken]; the third
 * lies in the pool, the fourth comes with the darts, [DartHooks]) is kept by [ProgressService].
 */
object LoungePuzzleHooks {

    const val PIECE_LEVEL = "level_4"
    private const val LEVEL_ID = "level_4"
    /** A film that cannot be read still gives the piece, after this long. */
    private const val FALLBACK_FILM_TICKS = 100

    /** To the client: a film starts. Payload: screen corners x0, x1, y0, y1 and its z, the projector's x, y, z, the folder of its frames (empty: stop), whether it loops. */
    val FILM_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "film")
    private const val SYNC_TICKS = 20

    /** The running time of each reel, as their items say ([ModItems.REELS]). */
    private val REEL_MINUTES = listOf(87, 94, 102, 120)

    /** One puzzle: its items, the block they are put into, where the piece lies when it is solved. */
    private class Puzzle(
        val items: List<Item>,
        val rackPrefix: String,
        val device: () -> Block,
        val pieceSpot: String,
        val pieceBlock: () -> Block,
        private val draw: () -> List<Int>,
        val delayTicks: Int,
    ) {
        /** Drawn anew each game. */
        var order = OrderPuzzle(draw())
            private set
        var finishIn = -1

        fun redraw() {
            order = OrderPuzzle(draw())
            finishIn = -1
        }

        fun restore(answer: List<Int>, done: Int, failures: Int) {
            if (answer.isEmpty()) return
            order = OrderPuzzle(answer).also { it.restore(done, failures) }
        }
    }

    private val BAR = Puzzle(
        ModItems.RECORDS, "record_rack", { ModBlocks.RECORD_PLAYER }, "piece_bar", { ModBlocks.LOOT_CARD_NW }, { listOf(0, 1, 2, 3).shuffled() }, 0,
    )
    private val CINEMA = Puzzle(
        ModItems.REELS, "reel_rack", { ModBlocks.PROJECTOR }, "piece_cinema", { ModBlocks.LOOT_CARD_NE }, { listOf((0..3).random()) }, -1,
    )
    private val PUZZLES = listOf(BAR, CINEMA)

    /** Each piece and its id in [ProgressService]. */
    private val PIECES: Map<Item, String> = mapOf(
        ModItems.CARD_NW to "ch1_l4_piece_bar", ModItems.CARD_NE to "ch1_l4_piece_cinema",
        ModItems.CARD_SW to "ch1_l4_piece_spa", ModItems.CARD_SE to "ch1_l4_piece_game",
    )

    private var server: MinecraftServer? = null

    /** The film has been seen to its end. */
    private var filmSeen = false

    /** The right reel is in the projector, and nobody has come out to see it yet. */
    private var filmWaiting = false

    /** Ticks the film has left, or -1. For the level's music to wait (nothing plays in Level 4 yet). */
    private var filmTicks = -1

    val filmRunning: Boolean
        get() = filmTicks >= 0

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
        PUZZLES.forEach(Puzzle::redraw)
        EveryTunnel.LOGGER.info("Level 4 this game: records {}, reel {} minutes", BAR.order.answer, REEL_MINUTES[CINEMA.order.answer[0]])
        filmWaiting = false
        filmTicks = -1
        filmSeen = false
        DartHooks.reset()
        BreakerHooks.reset()
        SafeHooks.reset()
        LoungeClosingHooks.reset()
    }

    /** For the notes of [level]: the menu's lines in this game's order, and the film's length. */
    fun noteValues(level: LevelDefinition): Map<String, Int> {
        if (level.id != LEVEL_ID) return emptyMap()
        val lines = BAR.order.answer.withIndex().associate { (i, record) -> "R$i" to record }
        return lines + ("FILM" to REEL_MINUTES[CINEMA.order.answer[0]])
    }

    /** For [ContinueGame]. */
    fun save(save: LoungeSave) {
        save.records = BAR.order.answer
        save.recordsDone = BAR.order.done
        save.recordsFailures = BAR.order.failures
        save.reel = CINEMA.order.answer
        save.reelDone = CINEMA.order.done
        save.reelFailures = CINEMA.order.failures
        save.filmSeen = filmSeen
    }

    /** As it was left. What a solved puzzle left lying is put back; a film not seen to its end waits again. */
    fun restore(save: LoungeSave, server: MinecraftServer, player: ServerPlayerEntity) {
        BAR.restore(save.records, save.recordsDone, save.recordsFailures)
        CINEMA.restore(save.reel, save.reelDone, save.reelFailures)
        filmSeen = save.filmSeen
        val inventory = player.inventory
        for (slot in 0 until inventory.size()) {
            val item = inventory.getStack(slot).item
            if (item in ModItems.RECORDS || item in ModItems.REELS) inventory.setStack(slot, ItemStack.EMPTY)
        }
        val progress = ProgressService.get()
        if (BAR.order.solved && !progress.hasPiece(player, PIECE_LEVEL, "ch1_l4_piece_bar")) putPiece(server, BAR)
        if (CINEMA.order.solved) {
            if (!filmSeen) filmWaiting = true
            else if (!progress.hasPiece(player, PIECE_LEVEL, "ch1_l4_piece_cinema")) putPiece(server, CINEMA)
        }
    }

    /** A piece was picked up: kept in the player's progress. */
    fun pieceTaken(player: ServerPlayerEntity, item: Item) {
        val piece = PIECES[item] ?: return
        ProgressService.get().addPiece(player, PIECE_LEVEL, piece)
        AchievementHooks.earned(player, Achievement.BIG_BRAIN)
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
        play(player, if (puzzle === BAR) ModSounds.RECORD_HANDLE else ModSounds.REEL_HANDLE)
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
            OrderPuzzle.Result.RIGHT -> play(player, ModSounds.RECORD_RIGHT)
            OrderPuzzle.Result.WRONG -> play(player, if (puzzle === BAR) ModSounds.RECORD_WRONG else ModSounds.REEL_WRONG)
            OrderPuzzle.Result.SOLVED -> {
                play(player, ModSounds.PUZZLE_SOLVED)
                if (puzzle === CINEMA) filmWaiting = true else puzzle.finishIn = puzzle.delayTicks
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

    private fun filmInfo(): FilmInfo? =
        javaClass.classLoader.getResourceAsStream("assets/every_tunnel/film/film.json")?.use { FilmInfo.parse(it.reader().readText()) }

    /** Someone is in the auditorium: in the Cinema's zone, out of its booth's. */
    private fun someoneWatches(server: MinecraftServer, level: LevelDefinition): Boolean {
        val cinema = level.zone("ch1_l4_cinema")?.box ?: return false
        val booth = level.zone("ch1_l4_booth")?.box ?: return false
        return server.playerManager.playerList.any { cinema.contains(it.x, it.y, it.z) && !booth.contains(it.x, it.y, it.z) }
    }

    private fun startFilm(server: MinecraftServer, level: LevelDefinition) {
        filmWaiting = false
        val info = filmInfo()
        if (info == null) EveryTunnel.LOGGER.warn("No film to show (assets/every_tunnel/film/film.json)")
        filmTicks = info?.ticks ?: FALLBACK_FILM_TICKS
        EveryTunnel.LOGGER.info("The film runs, {} s", filmTicks / 20)
        sendFilm(server, level, "film", false)
    }

    /** The blackout: the eye on the screen, round and round. Empty [folder]: nothing any more. */
    fun sendFilm(server: MinecraftServer, level: LevelDefinition, folder: String, loop: Boolean) {
        val a = level.spawnPoint("film_screen_a")?.position ?: return
        val b = level.spawnPoint("film_screen_b")?.position ?: return
        val projector = level.spawnPoint("projector")?.position ?: return
        for (player in server.playerManager.playerList) {
            if (!ServerPlayNetworking.canSend(player, FILM_PACKET_ID)) continue
            val buf = PacketByteBufs.create()
            buf.writeDouble(minOf(a.x, b.x) - 0.5)
            buf.writeDouble(maxOf(a.x, b.x) + 0.5)
            buf.writeDouble(minOf(a.y, b.y))
            buf.writeDouble(maxOf(a.y, b.y) + 1.0)
            buf.writeDouble(a.z - 0.5)
            buf.writeDouble(projector.x)
            buf.writeDouble(projector.y)
            buf.writeDouble(projector.z)
            buf.writeString(folder)
            buf.writeBoolean(loop)
            ServerPlayNetworking.send(player, FILM_PACKET_ID, buf)
        }
    }

    private fun tick(server: MinecraftServer) {
        if (filmWaiting) {
            val level = LevelManager.get(LEVEL_ID)
            if (level != null && someoneWatches(server, level)) startFilm(server, level)
        }
        if (filmTicks >= 0 && filmTicks-- == 0) {
            filmSeen = true
            putPiece(server, CINEMA)
        }
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
