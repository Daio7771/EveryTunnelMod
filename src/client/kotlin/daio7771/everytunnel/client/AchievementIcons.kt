package daio7771.everytunnel.client

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.achievement.Achievement
import daio7771.everytunnel.platform.ModBlocks
import daio7771.everytunnel.platform.ModItems
import net.minecraft.client.gui.DrawContext
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Identifier

/**
 * Each achievement's 16x16 icon, on its toast and on the ACHIEVEMENTS screen: mostly the thing it
 * is about, drawn as an item; the guard's badge, the skull, the Pale man's silhouette and the
 * locked "?" are drawn for the mod (`achievement_icons.png`, `tools/gui/achievement_icons.py`).
 */
object AchievementIcons {

    private val SHEET = Identifier(EveryTunnel.MOD_ID, "textures/gui/achievement_icons.png")
    private const val SHEET_WIDTH = 64
    private const val LOCKED_U = 0

    /** The icons drawn for the mod (`tools/gui/achievement_icons.py`): where each is on the sheet. */
    private fun spriteU(achievement: Achievement): Int? = when (achievement) {
        Achievement.FIRST_DAY -> 16       // the guard's badge
        Achievement.OUCH -> 32            // a skull
        Achievement.TALL_MEN -> 48        // the Pale man's silhouette
        else -> null
    }

    /** The others are the thing itself, as an item. */
    private fun item(achievement: Achievement): ItemStack? = when (achievement) {
        Achievement.FIRST_DAY, Achievement.OUCH, Achievement.TALL_MEN -> null
        Achievement.STAY_HYDRATED -> ItemStack(ModItems.WATER_BOTTLE)
        Achievement.KEYCARD -> ItemStack(ModItems.KEYCARD)
        Achievement.VALVES -> ItemStack(ModBlocks.VALVE)
        // Stand-ins until their puzzles bring their own things (the purple box, the book).
        Achievement.BOXES -> ItemStack(ModBlocks.CARDBOARD_BOX)
        Achievement.BOOKS -> ItemStack(Items.BOOK)
    }

    /** [achievement]'s icon at ([x], [y]), or the "?" if [locked]. */
    fun draw(context: DrawContext, achievement: Achievement, x: Int, y: Int, locked: Boolean = false) {
        val stack = if (locked) null else item(achievement)
        if (stack != null) {
            context.drawItemWithoutEntity(stack, x, y)
            return
        }
        val u = if (locked) LOCKED_U else spriteU(achievement) ?: LOCKED_U
        RenderSystem.enableBlend()
        context.drawTexture(SHEET, x, y, u.toFloat(), 0f, 16, 16, SHEET_WIDTH, 16)
        RenderSystem.disableBlend()
    }
}
