package daio7771.everytunnel.client.hud

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.ClientResources
import daio7771.everytunnel.config.ConfigManager
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import kotlin.math.ceil

/**
 * The Every Tunnel status bars, drawn where the vanilla ones were (vanilla style,
 * 10 icons per bar, each icon full or half):
 *
 * ```
 *  stamina (top left)                battery (top right, only with a flashlight)
 *  health  (bottom left)             thirst  (bottom right)
 *  [                        hotbar                        ]
 * ```
 *
 * Shown while the survival resources apply (Survival/Adventure in a world with
 * levels); then `InGameHudMixin` hides vanilla health, hunger, armor, air and the
 * experience bar. Elsewhere the vanilla HUD is untouched.
 *
 * - Health uses the vanilla heart sprites; hearts shake when health is low.
 * - Stamina icons turn grey while exhausted.
 * - The battery row blinks red below `flashlight.low_battery_warning_percent`.
 *
 * Icons come from `textures/gui/hud_icons.png` (placeholder art). Layout of that
 * file, 9x9 icons at (u, v):
 * - v=0 stamina, v=9 thirst, v=18 battery
 * - u=0 empty container, u=9 full, u=18 half (left half only, drawn over the container)
 * - u=27/36/45: the same three as an alternate state (stamina: exhausted, battery: low)
 * Full and half icons only contain the filled pixels; the container is drawn first.
 */
object StatusHud {

    private val ICONS = Identifier(EveryTunnel.MOD_ID, "textures/gui/hud_icons.png")
    private const val ICONS_WIDTH = 64
    private const val ICONS_HEIGHT = 32

    private val VANILLA_ICONS = Identifier("textures/gui/icons.png")

    private const val ICON_SIZE = 9
    private const val ICON_SPACING = 8
    private const val ICONS_PER_BAR = 10

    private const val STAMINA_V = 0
    private const val THIRST_V = 9
    private const val BATTERY_V = 18
    private const val ALTERNATE_U = 27

    fun register() {
        HudRenderCallback.EVENT.register { context, _ -> render(context) }
    }

    /** True while our bars replace the vanilla ones. Called from `InGameHudMixin`. */
    @JvmStatic
    fun replacesVanillaBars(): Boolean = ClientResources.current.active

    private fun render(context: DrawContext) {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val resources = ClientResources.current
        if (!resources.active || player.isSpectator || client.interactionManager?.hasStatusBars() != true) return

        val left = context.scaledWindowWidth / 2 - 91
        val right = context.scaledWindowWidth / 2 + 91
        val bottomRow = context.scaledWindowHeight - 39
        val topRow = bottomRow - 10
        val time = player.age

        RenderSystem.enableBlend()

        // Health (vanilla hearts), bottom left.
        val lowHealth = player.health <= 4f
        drawHearts(context, left, bottomRow, player.health / player.maxHealth, client.world?.levelProperties?.isHardcore == true) { i ->
            if (lowHealth && shake(time, i)) 1 else 0
        }

        // Stamina, top left.
        drawBar(context, left, topRow, fraction(resources.stamina, resources.staminaMax), STAMINA_V, alternate = resources.exhausted, rightToLeft = false)

        // Thirst, bottom right.
        drawBar(context, right, bottomRow, fraction(resources.thirst, resources.thirstMax), THIRST_V, alternate = false, rightToLeft = true)

        // Flashlight battery, top right.
        if (resources.hasFlashlight) {
            val low = resources.batteryLow(ConfigManager.gameplay.flashlight.lowBatteryWarningPercent)
            val blinkOn = (time / 8) % 2 == 0
            drawBar(context, right, topRow, fraction(resources.battery, resources.batteryMax), BATTERY_V, alternate = low && blinkOn, rightToLeft = true)
        }

        RenderSystem.disableBlend()
    }

    /**
     * Draws one bar of 10 icons. Left bars start at [x] and grow to the right; right bars
     * end at [x] and grow to the left, like vanilla hunger.
     */
    private fun drawBar(context: DrawContext, x: Int, y: Int, fraction: Double, v: Int, alternate: Boolean, rightToLeft: Boolean) {
        val halves = halvesOf(fraction)
        val uBase = if (alternate) ALTERNATE_U else 0
        for (i in 0 until ICONS_PER_BAR) {
            val iconX = if (rightToLeft) x - i * ICON_SPACING - ICON_SIZE else x + i * ICON_SPACING
            icon(context, iconX, y, uBase, v)
            when {
                halves >= 2 * i + 2 -> icon(context, iconX, y, uBase + ICON_SIZE, v)
                halves == 2 * i + 1 -> icon(context, iconX, y, uBase + 2 * ICON_SIZE, v)
            }
        }
    }

    /** Vanilla hearts: container at u=16, full at u=52, half at u=61; hardcore hearts are 45 pixels lower. */
    private fun drawHearts(context: DrawContext, x: Int, y: Int, fraction: Float, hardcore: Boolean, yOffset: (Int) -> Int) {
        val halves = halvesOf(fraction.toDouble())
        val v = if (hardcore) 45 else 0
        for (i in 0 until ICONS_PER_BAR) {
            val iconX = x + i * ICON_SPACING
            val iconY = y + yOffset(i)
            context.drawTexture(VANILLA_ICONS, iconX, iconY, 16, v, ICON_SIZE, ICON_SIZE)
            when {
                halves >= 2 * i + 2 -> context.drawTexture(VANILLA_ICONS, iconX, iconY, 52, v, ICON_SIZE, ICON_SIZE)
                halves == 2 * i + 1 -> context.drawTexture(VANILLA_ICONS, iconX, iconY, 61, v, ICON_SIZE, ICON_SIZE)
            }
        }
    }

    private fun icon(context: DrawContext, x: Int, y: Int, u: Int, v: Int) {
        context.drawTexture(ICONS, x, y, u.toFloat(), v.toFloat(), ICON_SIZE, ICON_SIZE, ICONS_WIDTH, ICONS_HEIGHT)
    }

    private fun fraction(value: Float, max: Float): Double =
        if (max <= 0f) 0.0 else (value / max).toDouble().coerceIn(0.0, 1.0)

    /** Half icons to fill (0-20). Anything above zero shows at least half an icon. */
    private fun halvesOf(fraction: Double): Int =
        ceil(fraction.coerceIn(0.0, 1.0) * 2 * ICONS_PER_BAR - 1e-6).toInt().coerceIn(0, 2 * ICONS_PER_BAR)

    /** Pseudo-random per tick and icon, so low health hearts jitter like vanilla's. */
    private fun shake(time: Int, index: Int): Boolean = (((time * 31 + index * 17) * 1103515245 ushr 16) and 1) == 1
}
