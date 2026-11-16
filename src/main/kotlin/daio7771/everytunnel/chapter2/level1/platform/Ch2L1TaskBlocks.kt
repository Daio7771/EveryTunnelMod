package daio7771.everytunnel.chapter2.level1.platform

import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import daio7771.everytunnel.platform.BlockShapes
import daio7771.everytunnel.platform.PropBlock

/**
 * The blocks of Chapter 2 Level 1's three tasks. Each does nothing but pass its use to [Ch2L1TaskHooks], which knows the
 * task, the player's state and what the block is for; a player who is not playing the level ([Ch2L1.active]) uses them
 * as props (nothing happens). On the client they answer with a swing, so that the game goes on repeating a held click,
 * which the tasks that take time (the analyzer, the dirt) rely on.
 */
private fun use(world: World, player: PlayerEntity, run: (ServerPlayerEntity) -> ActionResult): ActionResult {
    if (!Ch2L1.active(player)) return ActionResult.PASS
    if (world.isClient) return ActionResult.SUCCESS
    return run(player as ServerPlayerEntity)
}

/** One of task 1's sample tubes on the bench: taking it counts it ([Ch2L1TaskHooks.useTube]). */
class SampleTubeBlock(settings: Settings, boxes: List<BlockShapes.Box>) : PropBlock(settings, boxes) {

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult =
        use(world, player) { Ch2L1TaskHooks.useTube(it, pos) }
}

/**
 * A NonameOS terminal: task 1's, where the samples are registered ([Ch2L1TaskHooks.useTerminal]), or one of Act 3's computers, with its
 * files ([Ch2L1Act3Hooks.useTerminal]).
 */
class NonameTerminalBlock(settings: Settings, boxes: List<BlockShapes.Box>) : PropBlock(settings, boxes) {

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult =
        use(world, player) { p -> Ch2L1TaskHooks.useTerminal(p, pos).takeIf { it != ActionResult.PASS } ?: Ch2L1Act3Hooks.useTerminal(p, pos) }
}

/** The analyzer of task 2: held down on, it calibrates ([Ch2L1TaskHooks.useAnalyzer]). */
class AnalyzerBlock(settings: Settings, boxes: List<BlockShapes.Box>) : PropBlock(settings, boxes) {

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult =
        use(world, player) { Ch2L1TaskHooks.useAnalyzer(it, pos) }
}

/** The broom of task 3 against its wall: taking it puts the broom item in the player's hand ([Ch2L1TaskHooks.takeBroom]). */
class LeaningBroomBlock(settings: Settings, boxes: List<BlockShapes.Box>) : PropBlock(settings, boxes) {

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult =
        use(world, player) { Ch2L1TaskHooks.takeBroom(it, pos) }
}

/** A patch of dirt on the floor of task 3: swept with the broom, a click held on it ([Ch2L1TaskHooks.useDirt]). */
class DirtSpotBlock(settings: Settings, boxes: List<BlockShapes.Box>) : PropBlock(settings, boxes) {

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult =
        use(world, player) { Ch2L1TaskHooks.useDirt(it, pos) }
}
