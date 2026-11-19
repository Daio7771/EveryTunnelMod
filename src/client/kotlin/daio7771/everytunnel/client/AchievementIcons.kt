package daio7771.everytunnel.client

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.achievement.Achievement
import daio7771.everytunnel.core.achievement.AchievementDef
import daio7771.everytunnel.platform.ModBlocks
import daio7771.everytunnel.platform.ModItems
import net.minecraft.client.gui.DrawContext
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

/**
 * Each achievement's 16x16 icon, on its toast and on the ACHIEVEMENTS screen: mostly the thing it
 * is about, drawn as an item; the guard's badge, the skull, the Pale man's silhouette, the puzzle piece, the padlock and
 * the locked "?" are drawn for the mod (`achievement_icons.png`, `tools/gui/achievement_icons.py`). A later chapter's achievement
 * names an item as its [AchievementDef.icon] (`item:<namespace>:<path>`) or a column of the sheet (`sprite:<n>`).
 */
object AchievementIcons {

    private val SHEET = Identifier(EveryTunnel.MOD_ID, "textures/gui/achievement_icons.png")
    private const val SHEET_WIDTH = 272
    private const val LOCKED_U = 0

    /** The icons drawn for the mod (`tools/gui/achievement_icons.py`): where each is on the sheet. */
    private fun spriteU(achievement: Achievement): Int? = when (achievement) {
        Achievement.FIRST_DAY -> 16       // the guard's badge
        Achievement.OUCH -> 32            // a skull
        Achievement.TALL_MEN -> 48        // the Pale man's silhouette
        Achievement.BIG_BRAIN -> 64       // a puzzle piece
        Achievement.PRIVACY_POLICY -> 80  // a padlock
        Achievement.NOTHING_THERE -> 96   // the Director's mask
        else -> null
    }

    /** The others are the thing itself, as an item. */
    private fun item(achievement: Achievement): ItemStack? = when (achievement) {
        Achievement.FIRST_DAY, Achievement.OUCH, Achievement.TALL_MEN, Achievement.BIG_BRAIN, Achievement.PRIVACY_POLICY, Achievement.NOTHING_THERE -> null
        Achievement.STAY_HYDRATED -> ItemStack(ModItems.WATER_BOTTLE)
        Achievement.KEYCARD -> ItemStack(ModItems.KEYCARD)
        Achievement.VALVES -> ItemStack(ModBlocks.VALVE)
        Achievement.BOXES -> ItemStack(ModBlocks.PURPLE_BOX)
        Achievement.BOOKS -> ItemStack(ModBlocks.OFFICE_BOOK)
    }

    /** A later chapter's icon: the item its [AchievementDef.icon] names, or null (the "?" is drawn then). */
    private fun itemOf(achievement: AchievementDef): ItemStack? {
        val id = achievement.icon.removePrefix("item:").takeIf { achievement.icon.startsWith("item:") }?.let(Identifier::tryParse) ?: return null
        return Registries.ITEM.getOrEmpty(id).map(::ItemStack).orElse(null)
    }

    /** A later chapter's drawn icon: where it is on the sheet, or null. */
    private fun spriteOf(achievement: AchievementDef): Int? =
        achievement.icon.removePrefix("sprite:").takeIf { achievement.icon.startsWith("sprite:") }?.toIntOrNull()?.let { it * 16 }

    /** [achievement]'s icon at ([x], [y]), or the "?" if [locked]. */
    fun draw(context: DrawContext, achievement: AchievementDef, x: Int, y: Int, locked: Boolean = false) {
        val own = achievement as? Achievement
        val stack = if (locked) null else if (own != null) item(own) else itemOf(achievement)
        if (stack != null) {
            context.drawItemWithoutEntity(stack, x, y)
            return
        }
        val u = if (locked) LOCKED_U else if (own != null) spriteU(own) ?: LOCKED_U else spriteOf(achievement) ?: LOCKED_U
        RenderSystem.enableBlend()
        context.drawTexture(SHEET, x, y, u.toFloat(), 0f, 16, 16, SHEET_WIDTH, 16)
        RenderSystem.disableBlend()
    }
}
