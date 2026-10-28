package daio7771.everytunnel.client.screen

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import java.util.Random

/**
 * Worn white paper on a black screen, shared by the player guide ([GuideScreen]) and the
 * ACHIEVEMENTS screen ([AchievementsScreen]): the tiled paper, its stains, yellowed edges and
 * outline, and the inks written on it.
 *
 * The paper and its stains are `textures/gui/guide_paper.png` and `guide_stains.png`, from
 * `tools/gui/guide_paper.py`; the paper is tiled, so a sheet can be any size.
 */
object Paper {

    private val PAPER = Identifier(EveryTunnel.MOD_ID, "textures/gui/guide_paper.png")
    private val STAINS = Identifier(EveryTunnel.MOD_ID, "textures/gui/guide_stains.png")
    private const val PAPER_SIZE = 64
    private const val STAIN_SIZE = 64
    private const val STAIN_ALPHA = 0.8f

    // Ink on paper: dark, a little blue, never pure black. Links go red-brown when pointed at.
    const val INK = 0xFF2C2B30.toInt()
    const val INK_DARK = 0xFF1C1B20.toInt()
    const val INK_SOFT = 0xFF55535A.toInt()
    const val LINK_HOVER = 0xFF7A2A1E.toInt()
    const val RULE_COLOR = 0x50303038
    const val SHADOW_COLOR = 0x60000000
    private const val OUTLINE = 0xFF6F6A5E.toInt()
    /** From the edge inwards: yellowed, fading into the paper. */
    private val EDGE_SHADES = intArrayOf(0x40806838, 0x2A806838, 0x16806838)

    /** The paper texture tiled over a rectangle, starting at ([u], [v]) in the tile. */
    fun tile(context: DrawContext, x: Int, y: Int, w: Int, h: Int, u: Int, v: Int) {
        context.drawTexture(PAPER, x, y, u.toFloat(), v.toFloat(), w, h, PAPER_SIZE, PAPER_SIZE)
    }

    /** Yellowed, slightly darker edges and a thin outline: old paper. */
    fun edges(context: DrawContext, x: Int, y: Int, w: Int, h: Int) {
        EDGE_SHADES.forEachIndexed { i, shade ->
            context.fill(x + i, y + i, x + w - i, y + i + 1, shade)
            context.fill(x + i, y + h - i - 1, x + w - i, y + h - i, shade)
            context.fill(x + i, y + i + 1, x + i + 1, y + h - i - 1, shade)
            context.fill(x + w - i - 1, y + i + 1, x + w - i, y + h - i - 1, shade)
        }
        outline(context, x, y, w, h)
    }

    fun outline(context: DrawContext, x: Int, y: Int, w: Int, h: Int) {
        context.drawBorder(x, y, w, h, OUTLINE)
    }

    /** One or two stains somewhere on a sheet, the same ones for the same [seed]. */
    fun stains(context: DrawContext, seed: Long, x: Int, y: Int, w: Int, h: Int) {
        val random = Random(seed)
        val count = 1 + random.nextInt(2)
        RenderSystem.enableBlend()
        context.setShaderColor(1f, 1f, 1f, STAIN_ALPHA)
        repeat(count) {
            val kind = random.nextInt(2)
            val sx = x + 4 + random.nextInt((w - STAIN_SIZE - 8).coerceAtLeast(1))
            val sy = y + 4 + random.nextInt((h - STAIN_SIZE - 8).coerceAtLeast(1))
            context.drawTexture(STAINS, sx, sy, STAIN_SIZE, STAIN_SIZE, (kind * STAIN_SIZE).toFloat(), 0f, STAIN_SIZE, STAIN_SIZE, STAIN_SIZE * 2, STAIN_SIZE)
        }
        context.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }
}
