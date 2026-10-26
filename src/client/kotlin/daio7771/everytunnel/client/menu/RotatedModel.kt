package daio7771.everytunnel.client.menu

import net.minecraft.block.BlockState
import net.minecraft.client.render.model.BakedModel
import net.minecraft.client.render.model.BakedQuad
import net.minecraft.client.render.model.json.ModelOverrideList
import net.minecraft.client.render.model.json.ModelTransformation
import net.minecraft.client.texture.Sprite
import net.minecraft.util.math.Direction
import net.minecraft.util.math.random.Random

/**
 * A baked model turned about the vertical axis by quarter turns, clockwise seen from above,
 * like a blockstate variant's `"y": 90` (north becomes east). The menu scene ([MenuScene])
 * names its own models for re-skinned blocks, and the model loader bakes those unturned.
 *
 * The quads are really turned (positions, normals and faces), not drawn through a turned
 * matrix, so face culling and smooth lighting look at the right neighbours.
 */
class RotatedModel(private val base: BakedModel, quarterTurns: Int) : BakedModel {

    private val turns = Math.floorMod(quarterTurns, 4)
    private val cache = HashMap<Direction?, List<BakedQuad>>()

    override fun getQuads(state: BlockState?, face: Direction?, random: Random): List<BakedQuad> {
        if (turns == 0) return base.getQuads(state, face, random)
        // The scene's models have one quad list per face, so they are turned once and kept.
        return cache.getOrPut(face) {
            base.getQuads(state, face?.let { turn(it, -turns) }, random).map(::turn)
        }
    }

    private fun turn(quad: BakedQuad): BakedQuad {
        val data = quad.vertexData.copyOf()
        val stride = data.size / 4
        for (v in 0 until 4) {
            val i = v * stride
            var x = Float.fromBits(data[i])
            var z = Float.fromBits(data[i + 2])
            val normal = data[i + NORMAL]
            var nx = normal.toByte().toInt()
            var nz = (normal shr 16).toByte().toInt()
            repeat(turns) {
                // A quarter turn clockwise seen from above, about the block's centre: (x, z) -> (1 - z, x).
                val px = 1f - z
                z = x
                x = px
                val qx = -nz
                nz = nx
                nx = qx
            }
            data[i] = x.toRawBits()
            data[i + 2] = z.toRawBits()
            // Packed normal: x, y, z, padding, a byte each; y and the padding stay.
            data[i + NORMAL] = (nx and 0xFF) or (normal and 0xFF00) or ((nz and 0xFF) shl 16) or (normal and 0xFF000000.toInt())
        }
        return BakedQuad(data, quad.colorIndex, turn(quad.face, turns), quad.sprite, quad.hasShade())
    }

    private fun turn(direction: Direction, quarterTurns: Int): Direction {
        if (direction.axis == Direction.Axis.Y) return direction
        var d = direction
        repeat(Math.floorMod(quarterTurns, 4)) { d = d.rotateYClockwise() }
        return d
    }

    override fun useAmbientOcclusion(): Boolean = base.useAmbientOcclusion()
    override fun hasDepth(): Boolean = base.hasDepth()
    override fun isSideLit(): Boolean = base.isSideLit()
    override fun isBuiltin(): Boolean = base.isBuiltin()
    override fun getParticleSprite(): Sprite = base.particleSprite
    override fun getTransformation(): ModelTransformation = base.transformation
    override fun getOverrides(): ModelOverrideList = base.overrides

    private companion object {
        /** The packed normal's place in a vertex of a baked quad (x, y, z, color, u, v, light, normal). */
        const val NORMAL = 7
    }
}
