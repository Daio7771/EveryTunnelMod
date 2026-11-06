package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.core.puzzle.BreakerCode
import daio7771.everytunnel.core.save.LoungeSave
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.IntProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World

/**
 * Level 4's Maintenance room: five levers ([BreakerLeverBlock]), a code drawn each game ([BreakerCode]),
 * a button ([BreakerButtonBlock]) and a counter ([BreakerCounterBlock]) saying how many are right. All
 * five give power to the Calm room's reader ([CalmReaderBlock.POWERED]). Places are the level's spawn
 * points tagged `puzzle` (`breaker_<index>`, `breaker_counter`, `calm_reader`).
 */
object BreakerHooks {

    private const val LEVEL_ID = "level_4"

    private var code = BreakerCode.draw()

    /** The reader has power. */
    var powered = false
        private set

    /** A new game, or the map rebuilt. */
    fun reset() {
        code = BreakerCode.draw()
        powered = false
        EveryTunnel.LOGGER.info("Level 4's breakers this game: {}", code.answer.joinToString("") { if (it) "1" else "0" })
    }

    fun press(player: ServerPlayerEntity, pos: BlockPos) {
        val world = player.serverWorld
        world.playSound(null, pos, ModSounds.BREAKER_BUTTON, SoundCategory.BLOCKS, 0.8f, 1f)
        if (powered) return
        val level = LevelManager.get(LEVEL_ID) ?: return
        if (world.registryKey != RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) return
        val levers = (0 until BreakerCode.LEVERS).map { i ->
            val lever = pointOf(level, "breaker_$i")?.let(world::getBlockState)
            lever != null && lever.isOf(ModBlocks.BREAKER_LEVER) && lever.get(BreakerLeverBlock.ON)
        }
        val count = code.rightCount(levers)
        pointOf(level, "breaker_counter")?.let { at ->
            val counter = world.getBlockState(at)
            if (counter.isOf(ModBlocks.BREAKER_COUNTER)) {
                world.setBlockState(at, counter.with(BreakerCounterBlock.COUNT, count).with(BreakerCounterBlock.LIT, true))
            }
        }
        if (code.solvedBy(levers)) powerOn(world, level, pos) else world.playSound(null, pos, ModSounds.BREAKER_COUNT, SoundCategory.BLOCKS, 0.8f, 1f)
    }

    /** For [ContinueGame]: the code, the levers as they stand, the counter, the reader. */
    fun save(save: LoungeSave, world: ServerWorld, level: LevelDefinition) {
        save.breakers = code.answer
        save.levers = (0 until BreakerCode.LEVERS).map { i ->
            val lever = pointOf(level, "breaker_$i")?.let(world::getBlockState)
            lever != null && lever.isOf(ModBlocks.BREAKER_LEVER) && lever.get(BreakerLeverBlock.ON)
        }
        val counter = pointOf(level, "breaker_counter")?.let(world::getBlockState)
        save.counter = if (counter != null && counter.isOf(ModBlocks.BREAKER_COUNTER) && counter.get(BreakerCounterBlock.LIT)) counter.get(BreakerCounterBlock.COUNT) else -1
        save.powered = powered
        val reader = pointOf(level, "calm_reader")?.let(world::getBlockState)
        save.readerUnlocked = reader != null && reader.isOf(ModBlocks.CALM_READER) && reader.get(CalmReaderBlock.UNLOCKED)
    }

    fun restore(save: LoungeSave, world: ServerWorld, level: LevelDefinition) {
        if (save.breakers.isEmpty()) return
        code = BreakerCode(save.breakers)
        powered = save.powered
        save.levers.forEachIndexed { i, on ->
            pointOf(level, "breaker_$i")?.let { at ->
                val lever = world.getBlockState(at)
                if (lever.isOf(ModBlocks.BREAKER_LEVER)) world.setBlockState(at, lever.with(BreakerLeverBlock.ON, on))
            }
        }
        if (save.counter >= 0) {
            pointOf(level, "breaker_counter")?.let { at ->
                val counter = world.getBlockState(at)
                if (counter.isOf(ModBlocks.BREAKER_COUNTER)) world.setBlockState(at, counter.with(BreakerCounterBlock.COUNT, save.counter).with(BreakerCounterBlock.LIT, true))
            }
        }
        pointOf(level, "calm_reader")?.let { at ->
            val reader = world.getBlockState(at)
            if (reader.isOf(ModBlocks.CALM_READER)) {
                world.setBlockState(at, reader.with(CalmReaderBlock.POWERED, save.powered).with(CalmReaderBlock.UNLOCKED, save.readerUnlocked))
                if (save.readerUnlocked) CardReaderBlock.setDoors(world, at, open = true)
            }
        }
    }

    private fun powerOn(world: ServerWorld, level: LevelDefinition, pos: BlockPos) {
        powered = true
        world.playSound(null, pos, ModSounds.BREAKER_POWER, SoundCategory.BLOCKS, 1f, 1f)
        pointOf(level, "calm_reader")?.let { at ->
            val reader = world.getBlockState(at)
            if (reader.isOf(ModBlocks.CALM_READER)) world.setBlockState(at, reader.with(CalmReaderBlock.POWERED, true))
        }
        EveryTunnel.LOGGER.info("Level 4's breakers are set: the reader has power")
    }

    private fun pointOf(level: LevelDefinition, id: String): BlockPos? =
        level.spawnPoint(id)?.let { BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) }
}

/** One of the Maintenance room's levers. Right-click flips it, until the power is on. */
class BreakerLeverBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(ON, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, ON)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        // Level 5 uses the same lever to open its locked doors.
        if (LabDoorHooks.pull(player as ServerPlayerEntity, world as ServerWorld, pos, state)) return ActionResult.CONSUME
        if (!BreakerHooks.powered) {
            world.setBlockState(pos, state.cycle(ON))
            world.playSound(null, pos, ModSounds.BREAKER_LEVER, SoundCategory.BLOCKS, 0.8f, if (state.get(ON)) 0.85f else 1f)
        }
        return ActionResult.CONSUME
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val ON: BooleanProperty = BooleanProperty.of("on")

        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(4, 1, 11, 12, 15, 16)))
    }
}

/** The button under the counter ([BreakerHooks.press]). */
class BreakerButtonBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        BreakerHooks.press(player as ServerPlayerEntity, pos)
        return ActionResult.CONSUME
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING

        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(4, 4, 13, 12, 12, 16)))
    }
}

/** The counter, blank until the button is first pressed ([LIT]), then [COUNT] levers right. */
class BreakerCounterBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(COUNT, 0).with(LIT, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, COUNT, LIT)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val COUNT: IntProperty = IntProperty.of("count", 0, BreakerCode.LEVERS)
        val LIT: BooleanProperty = BooleanProperty.of("lit")

        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(3, 3, 13, 13, 13, 16)))
    }
}
