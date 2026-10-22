package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.state.StateManager
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World

/**
 * Loot lying on a floor, box or shelf: right-click picks up one [item] (a battery, a
 * water bottle) and the spot is empty. Drawn by the client as the item itself lying flat,
 * turned by [FACING] (`client.render.LootRenderer`); it has no model of its own and
 * nothing bumps into it.
 *
 * Levels place them at their spawn points tagged for loot ([LootSpawner]).
 */
class LootBlock(settings: Settings, private val itemOf: () -> Item) : BlockWithEntity(settings) {

    val item: Item get() = itemOf()

    init {
        defaultState = stateManager.defaultState.with(FACING, net.minecraft.util.math.Direction.NORTH)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(FACING, ctx.horizontalPlayerFacing.opposite)

    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = LootBlockEntity(pos, state)

    @Deprecated("Deprecated in Java")
    override fun getRenderType(state: BlockState): BlockRenderType = BlockRenderType.INVISIBLE

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape = SHAPE

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (!world.isClient) {
            val stack = ItemStack(item)
            if (!player.inventory.insertStack(stack)) player.dropItem(stack, false)
            world.removeBlock(pos, false)
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.3f, 1.2f + world.random.nextFloat() * 0.4f)
            if (item == ModItems.KEYCARD && player is ServerPlayerEntity) KeycardHooks.pickedUp(player, pos)
        }
        return ActionResult.success(world.isClient)
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        private val SHAPE: VoxelShape = createCuboidShape(3.0, 0.0, 3.0, 13.0, 3.0, 13.0)
    }
}

/** No data: which item it shows comes from its block. Exists so the client can draw the item. */
class LootBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlocks.LOOT_ENTITY, pos, state)
