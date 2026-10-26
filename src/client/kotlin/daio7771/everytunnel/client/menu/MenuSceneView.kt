package daio7771.everytunnel.client.menu

import daio7771.everytunnel.core.menu.BlockLight
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntity
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.BlockRenderView
import net.minecraft.world.BlockView
import net.minecraft.world.LightType
import net.minecraft.world.biome.ColorResolver
import net.minecraft.world.chunk.ChunkProvider
import net.minecraft.world.chunk.light.LightSourceView
import net.minecraft.world.chunk.light.LightingProvider

/**
 * The menu scene's blocks as a world for Minecraft's block renderer ([MenuScene]): block
 * states by position, air elsewhere, no block entities, no sky light, and block light from
 * [light] (the scene's own [BlockLight], with [origin] as its cell 0, 0, 0). Faces are shaded
 * like the overworld's.
 */
class MenuSceneView(
    private val blocks: Map<BlockPos, BlockState>,
    private val light: BlockLight,
    private val origin: BlockPos,
) : BlockRenderView {

    private val noLightEngine: LightingProvider by lazy {
        val view = this
        LightingProvider(object : ChunkProvider {
            override fun getChunk(chunkX: Int, chunkZ: Int): LightSourceView? = null
            override fun getWorld(): BlockView = view
        }, false, false)
    }

    override fun getBlockState(pos: BlockPos): BlockState = blocks[pos] ?: Blocks.AIR.defaultState

    override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState

    override fun getBlockEntity(pos: BlockPos): BlockEntity? = null

    override fun getLightLevel(type: LightType, pos: BlockPos): Int =
        if (type == LightType.SKY) 0 else light.levelAt(pos.x - origin.x, pos.y - origin.y, pos.z - origin.z)

    override fun getBaseLightLevel(pos: BlockPos, ambientDarkness: Int): Int = getLightLevel(LightType.BLOCK, pos)

    override fun isSkyVisible(pos: BlockPos): Boolean = false

    // Never asked for light, since the methods above answer instead; an engine with no light.
    override fun getLightingProvider(): LightingProvider = noLightEngine

    override fun getBrightness(direction: Direction, shaded: Boolean): Float = when {
        !shaded -> 1f
        direction == Direction.DOWN -> 0.5f
        direction == Direction.UP -> 1f
        direction.axis == Direction.Axis.Z -> 0.8f
        else -> 0.6f
    }

    // No biomes: tinted blocks (none in the scene) come out untinted.
    override fun getColor(pos: BlockPos, colorResolver: ColorResolver): Int = 0xFFFFFF

    override fun getHeight(): Int = 384

    override fun getBottomY(): Int = -64
}
