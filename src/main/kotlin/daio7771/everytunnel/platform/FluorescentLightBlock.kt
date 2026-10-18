package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.EnumProperty
import net.minecraft.state.property.Properties
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.Hand
import net.minecraft.util.StringIdentifiable
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.random.Random
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World

/** What a fluorescent light is doing. */
enum class LightMode(private val id: String) : StringIdentifiable {
    ON("on"),
    FLICKER("flicker"),
    OFF("off");

    override fun asString(): String = id

    fun next(): LightMode = entries[(ordinal + 1) % entries.size]
}

/**
 * Fluorescent ceiling light (placeholder model: a thin panel on the face it was placed
 * against; it can go on ceilings, walls and floors).
 *
 * - [MODE] `on`: light level 15 and a hum. `off`: dark and silent.
 *   `flicker`: mostly lit, but every few seconds it goes through a burst of rapid
 *   off/on switching with the flicker sound; the light really goes out (block light
 *   updates), so the room around it flickers too.
 * - In Creative, right-click cycles on -> flicker -> off (sneak to place a block
 *   against it instead). Turning on plays the starter sound, turning off the power-down.
 * - The hum is played by the client for the nearest lights (`client.effects.FluorescentHum`).
 *
 * [LIT] is what the light shows right now; [BURST] is true during a flicker burst.
 */
class FluorescentLightBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState
            .with(FACING, Direction.DOWN)
            .with(MODE, LightMode.ON)
            .with(LIT, true)
            .with(BURST, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, MODE, LIT, BURST)
    }

    /** Points away from the face it was placed against (placed under a ceiling it points down). */
    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(FACING, ctx.side)

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(
        state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult,
    ): ActionResult {
        if (!player.abilities.creativeMode) return ActionResult.PASS
        if (world.isClient) return ActionResult.SUCCESS
        val mode = state.get(MODE).next()
        world.setBlockState(pos, withMode(state, mode), NOTIFY_ALL)
        when (mode) {
            LightMode.ON -> play(world, pos, ModSounds.FLUORESCENT_START)
            LightMode.OFF -> play(world, pos, ModSounds.FLUORESCENT_OFF)
            LightMode.FLICKER -> world.scheduleBlockTick(pos, this, 20)
        }
        player.sendMessage(Text.translatable("message.every_tunnel.light_mode.${mode.asString()}"), true)
        return ActionResult.CONSUME
    }

    @Deprecated("Deprecated in Java")
    override fun onBlockAdded(state: BlockState, world: World, pos: BlockPos, oldState: BlockState, notify: Boolean) {
        // Also covers /setblock and pasted structures in flicker mode.
        if (!world.isClient && !oldState.isOf(this) && state.get(MODE) == LightMode.FLICKER) {
            world.scheduleBlockTick(pos, this, 20)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun scheduledTick(state: BlockState, world: ServerWorld, pos: BlockPos, random: Random) {
        if (state.get(MODE) != LightMode.FLICKER) return
        if (!state.get(BURST)) {
            if (random.nextFloat() < BURST_CHANCE) {
                world.setBlockState(pos, state.with(BURST, true).with(LIT, false), NOTIFY_LISTENERS)
                play(world, pos, ModSounds.FLUORESCENT_FLICKER)
                world.scheduleBlockTick(pos, this, 1 + random.nextInt(3))
            } else {
                world.scheduleBlockTick(pos, this, 40 + random.nextInt(120))
            }
            return
        }
        val turningOn = !state.get(LIT)
        if (turningOn && random.nextFloat() < BURST_END_CHANCE) {
            world.setBlockState(pos, state.with(LIT, true).with(BURST, false), NOTIFY_LISTENERS)
            world.scheduleBlockTick(pos, this, 60 + random.nextInt(200))
        } else {
            world.setBlockState(pos, state.with(LIT, turningOn), NOTIFY_LISTENERS)
            world.scheduleBlockTick(pos, this, 1 + random.nextInt(4))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun rotate(state: BlockState, rotation: BlockRotation): BlockState =
        state.with(FACING, rotation.rotate(state.get(FACING)))

    @Deprecated("Deprecated in Java")
    override fun mirror(state: BlockState, mirror: BlockMirror): BlockState =
        state.rotate(mirror.getRotation(state.get(FACING)))

    private fun play(world: World, pos: BlockPos, sound: SoundEvent) {
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 0.8f, 1.0f)
    }

    companion object {
        val FACING: net.minecraft.state.property.DirectionProperty = Properties.FACING
        val MODE: EnumProperty<LightMode> = EnumProperty.of("mode", LightMode::class.java)
        val LIT: BooleanProperty = Properties.LIT
        val BURST: BooleanProperty = BooleanProperty.of("burst")

        /** Chance per check (every 2-8 s) that a steady flickering light starts a burst. */
        private const val BURST_CHANCE = 0.35f
        /** Chance per switch-on that a burst ends (about 5 off/on cycles, ~1 s). */
        private const val BURST_END_CHANCE = 0.2f

        /** Light level by state; used in the block settings. */
        fun luminance(state: BlockState): Int = if (state.get(LIT)) 15 else 0

        fun withMode(state: BlockState, mode: LightMode): BlockState =
            state.with(MODE, mode).with(LIT, mode != LightMode.OFF).with(BURST, false)

        /** 12x12 panel, 2 pixels thick, against the face opposite to where it points. */
        private val SHAPES: Map<Direction, VoxelShape> = mapOf(
            Direction.DOWN to createCuboidShape(2.0, 14.0, 2.0, 14.0, 16.0, 14.0),
            Direction.UP to createCuboidShape(2.0, 0.0, 2.0, 14.0, 2.0, 14.0),
            Direction.NORTH to createCuboidShape(2.0, 2.0, 14.0, 14.0, 14.0, 16.0),
            Direction.SOUTH to createCuboidShape(2.0, 2.0, 0.0, 14.0, 14.0, 2.0),
            Direction.EAST to createCuboidShape(0.0, 2.0, 2.0, 2.0, 14.0, 14.0),
            Direction.WEST to createCuboidShape(14.0, 2.0, 2.0, 16.0, 14.0, 14.0),
        )
    }
}
