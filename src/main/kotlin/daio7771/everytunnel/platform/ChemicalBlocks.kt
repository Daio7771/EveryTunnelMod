package daio7771.everytunnel.platform

import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.IntProperty
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * One of the two tanks of Level 5's mixer, in the chemical room ([ChemicalHooks]). Empty ([REAGENT] 0),
 * it takes a reagent flask with a right click (`block.mixer.fill`): its window shows the colour. A full
 * one takes nothing more; the mixer empties both when it runs.
 */
class MixerTankBlock(settings: Settings, boxes: List<BlockShapes.Box>) : PropBlock(settings, boxes) {

    init {
        defaultState = defaultState.with(REAGENT, 0)
    }

    override fun appendProperties(builder: StateManager.Builder<net.minecraft.block.Block, BlockState>) {
        super.appendProperties(builder)
        builder.add(REAGENT)
    }

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        val stack = player.getStackInHand(hand)
        val reagent = ModItems.REAGENTS.indexOf(stack.item) + 1
        if (reagent == 0 || state.get(REAGENT) != 0) return ActionResult.PASS
        if (world.isClient) return ActionResult.SUCCESS
        world.setBlockState(pos, state.with(REAGENT, reagent))
        if (!player.abilities.creativeMode) stack.decrement(1)
        world.playSound(null, pos, ModSounds.MIXER_FILL, SoundCategory.BLOCKS, 0.9f, 1f)
        return ActionResult.CONSUME
    }

    companion object {
        val REAGENT: IntProperty = IntProperty.of("reagent", 0, 4)
    }
}

/** Level 5's mixer, between its two tanks: its red button runs it ([ChemicalHooks.press]). */
class MixerBlock(settings: Settings, boxes: List<BlockShapes.Box>) : PropBlock(settings, boxes) {

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        ChemicalHooks.press(world as ServerWorld, pos, player as ServerPlayerEntity)
        return ActionResult.CONSUME
    }
}

/** A red alarm light on a wall ([ON]: lit and turning; the chemical room's alarm, [ChemicalHooks]). */
class AlarmLightBlock(settings: Settings, boxes: List<BlockShapes.Box>) : PropBlock(settings, boxes) {

    init {
        defaultState = defaultState.with(ON, false)
    }

    override fun appendProperties(builder: StateManager.Builder<net.minecraft.block.Block, BlockState>) {
        super.appendProperties(builder)
        builder.add(ON)
    }

    companion object {
        val ON: BooleanProperty = BooleanProperty.of("on")
    }
}
