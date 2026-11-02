package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.core.puzzle.OrderPuzzle
import daio7771.everytunnel.core.puzzle.darts.DartboardGeometry
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.BlockState
import net.minecraft.entity.ItemEntity
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
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction

/**
 * Level 4's darts, in the Game room: the fourth piece of the master card.
 *
 * The tray ([DartTrayBlock]) gives 3 darts ([ModItems.DART], [DartEntity]). The board's plate asks for
 * three numbers in order, drawn each game, and only the number counts, not its ring. A wrong number, or anything that is
 * not the board, sends the darts back to the tray after [CLEAR_TICKS], so the player sees where it
 * landed; a dart dropped sends them back too, but costs nothing. After 3 failures the next number
 * glows ([OrderPuzzle.hint], [DartboardBlock.HINT]). The third hit opens the hatch under the board
 * with the piece inside ([DartHatchBlock]), leaves the darts on the board, and adds a line to the
 * score sheet ([noteShown]).
 *
 * The state is kept in memory, like the other puzzles of the level: every opening of the world is a
 * new game. Places are the level's spawn points tagged `puzzle` (`dartboard`, its middle part;
 * `dart_tray`; `dart_hatch`; `dart_plate`).
 */
object DartHooks {

    /** Drawn each game. The plate beside the board shows it ([sync]). */
    private fun drawSequence(): List<Int> = (1..20).shuffled().take(3)

    const val DART_COUNT = 3
    private const val LEVEL_ID = "level_4"
    private const val CLEAR_TICKS = 20
    private const val SYNC_TICKS = 10
    private const val SEARCH_BLOCKS = 32.0
    private const val SCORE_SHEET = "ch1_l4_note_score_sheet"

    private var puzzle = OrderPuzzle(drawSequence())
    private var server: MinecraftServer? = null

    /** Ticks until the darts stuck go back after a failure, or -1. */
    private var clearIn = -1

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
        puzzle = OrderPuzzle(drawSequence())
        clearIn = -1
        EveryTunnel.LOGGER.info("Level 4's darts this game: {}", puzzle.answer)
        val server = server ?: return
        if (!server.isRunning) return
        val (level, world) = place(server) ?: return
        if (pointOf(level, "dartboard") != null) discardDarts(level, world)
    }

    /** The note to show for a sheet read: winning writes the player on the score sheet. */
    fun noteShown(id: String): String = if (id == SCORE_SHEET && puzzle.solved) "${id}_won" else id

    fun useTray(player: ServerPlayerEntity, state: BlockState) {
        if (puzzle.solved) return
        val (level, world) = place(player.server) ?: return
        if (carrying(player)) {
            removeCarried(player)
            discardDarts(level, world)
            puzzle.restart()
        } else {
            if (!state.get(DartTrayBlock.HAS)) return
            if (!player.getStackInHand(Hand.MAIN_HAND).isEmpty) {
                player.sendMessage(Text.translatable("message.every_tunnel.hands_full"), true)
                return
            }
            puzzle.restart()
            player.setStackInHand(Hand.MAIN_HAND, ItemStack(ModItems.DART, DART_COUNT))
        }
        play(world, player.blockPos, SoundEvents.ENTITY_ITEM_PICKUP)
        sync(player.server)
    }

    /** A dart stuck in something. */
    fun landed(dart: DartEntity, hit: BlockHitResult) {
        if (puzzle.solved || clearIn >= 0) return
        val world = dart.world as? ServerWorld ?: return
        val server = world.server
        val (level, _) = place(server) ?: return
        val centre = pointOf(level, "dartboard") ?: return
        val number = if (world.getBlockState(hit.blockPos).block == ModBlocks.DARTBOARD) numberAt(world, centre, hit) else 0
        when (puzzle.attempt(number)) {
            OrderPuzzle.Result.RIGHT -> play(world, hit.blockPos, ModSounds.DART_RIGHT)
            OrderPuzzle.Result.WRONG -> {
                play(world, hit.blockPos, ModSounds.DART_WRONG)
                server.playerManager.playerList.forEach(::removeCarried)
                clearIn = CLEAR_TICKS
            }
            OrderPuzzle.Result.SOLVED -> {
                play(world, hit.blockPos, ModSounds.PUZZLE_SOLVED)
                openHatch(world, level)
                server.playerManager.playerList.forEach(::removeCarried)
                EveryTunnel.LOGGER.info("The darts of Level 4 are solved")
            }
        }
        sync(server)
    }

    fun takePiece(player: ServerPlayerEntity, pos: BlockPos, state: BlockState) {
        val stack = ItemStack(ModItems.CARD_SE)
        if (!player.inventory.insertStack(stack)) player.dropItem(stack, false)
        player.serverWorld.setBlockState(pos, state.with(DartHatchBlock.PIECE, false))
        PlaytestLog.pickedUp(player, ModItems.CARD_SE, pos)
        play(player.serverWorld, pos, SoundEvents.ENTITY_ITEM_PICKUP)
        LoungePuzzleHooks.pieceTaken(player, ModItems.CARD_SE)
    }

    private fun numberAt(world: ServerWorld, centre: BlockPos, hit: BlockHitResult): Int {
        val facing = when (world.getBlockState(hit.blockPos).get(DartboardBlock.FACING)) {
            Direction.NORTH -> DartboardGeometry.Facing.NORTH
            Direction.EAST -> DartboardGeometry.Facing.EAST
            Direction.WEST -> DartboardGeometry.Facing.WEST
            else -> DartboardGeometry.Facing.SOUTH
        }
        return DartboardGeometry.resolveWorld(
            facing, hit.pos.x - (centre.x + 0.5), hit.pos.y - (centre.y + 0.5), hit.pos.z - (centre.z + 0.5),
        ).number
    }

    private fun openHatch(world: ServerWorld, level: LevelDefinition) {
        val pos = pointOf(level, "dart_hatch") ?: return
        val state = world.getBlockState(pos)
        if (state.block != ModBlocks.DART_HATCH) return
        world.setBlockState(pos, state.with(DartHatchBlock.OPEN, true).with(DartHatchBlock.PIECE, true))
        play(world, pos, ModSounds.DART_HATCH_OPEN)
    }

    private fun tick(server: MinecraftServer) {
        if (clearIn >= 0 && clearIn-- == 0) {
            val (level, world) = place(server) ?: return
            discardDarts(level, world)
        }
        if (server.ticks % SYNC_TICKS == 0) sync(server)
    }

    /** The tray and the glow as things stand, and what got lost goes back. */
    private fun sync(server: MinecraftServer) {
        val (level, world) = place(server) ?: return
        val centre = pointOf(level, "dartboard") ?: return
        if (LoungeHooks.notBuiltYet(level)) return
        val area = Box(centre).expand(SEARCH_BLOCKS)
        val players = server.playerManager.playerList
        var thrown = world.getEntitiesByClass(DartEntity::class.java, area) { true }
        var carried = players.any(::carrying)

        // A dart on the floor would leave too few to finish: all back, without it counting.
        val dropped = world.getEntitiesByClass(ItemEntity::class.java, area) { it.stack.item == ModItems.DART }
        if (dropped.isNotEmpty() && !puzzle.solved) {
            dropped.forEach { it.discard() }
            players.forEach(::removeCarried)
            thrown.forEach { it.discard() }
            puzzle.restart()
            clearIn = -1
            thrown = emptyList()
            carried = false
        }
        // Darts on the board and none left (taken away by a command): nothing can finish this try.
        if (!puzzle.solved && clearIn < 0 && !carried && thrown.isNotEmpty() && thrown.all { it.stuck }) {
            thrown.forEach { it.discard() }
            puzzle.restart()
            thrown = emptyList()
        }

        val tray = pointOf(level, "dart_tray")
        if (tray != null) {
            val state = world.getBlockState(tray)
            if (state.block == ModBlocks.DART_TRAY) {
                val has = !puzzle.solved && !carried && thrown.isEmpty() && clearIn < 0
                if (state.get(DartTrayBlock.HAS) != has) world.setBlockState(tray, state.with(DartTrayBlock.HAS, has))
            }
        }

        pointOf(level, "dart_plate")?.let { at ->
            (world.getBlockEntity(at) as? WallTextBlockEntity)?.say(plateText(), WallTextBlockEntity.Style.BRASS)
        }

        val hint = puzzle.hint ?: 0
        val board = world.getBlockState(centre)
        if (board.block != ModBlocks.DARTBOARD) return
        val right = board.get(DartboardBlock.FACING).rotateYCounterclockwise()
        for (across in -1..1) {
            for (up in -1..1) {
                val pos = centre.offset(right, across).up(up)
                val part = world.getBlockState(pos)
                if (part.block == ModBlocks.DARTBOARD && part.get(DartboardBlock.HINT) != hint) {
                    world.setBlockState(pos, part.with(DartboardBlock.HINT, hint))
                }
            }
        }
    }

    private fun plateText(): String = "HOUSE GAME: " + puzzle.answer.joinToString(" - ")

    private fun discardDarts(level: LevelDefinition, world: ServerWorld) {
        val centre = pointOf(level, "dartboard") ?: return
        world.getEntitiesByClass(DartEntity::class.java, Box(centre).expand(SEARCH_BLOCKS)) { true }.forEach { it.discard() }
    }

    private fun carrying(player: ServerPlayerEntity): Boolean =
        (0 until player.inventory.size()).any { player.inventory.getStack(it).item == ModItems.DART }

    private fun removeCarried(player: ServerPlayerEntity) {
        val inventory = player.inventory
        for (slot in 0 until inventory.size()) {
            if (inventory.getStack(slot).item == ModItems.DART) inventory.setStack(slot, ItemStack.EMPTY)
        }
    }

    private fun place(server: MinecraftServer): Pair<LevelDefinition, ServerWorld>? {
        val level = LevelManager.get(LEVEL_ID) ?: return null
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return null
        return level to world
    }

    private fun pointOf(level: LevelDefinition, id: String): BlockPos? =
        level.spawnPoint(id)?.let { BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) }

    private fun play(world: ServerWorld, pos: BlockPos, sound: SoundEvent) {
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 0.7f, 1f)
    }
}
