package daio7771.everytunnel.client.screen

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.MessageScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.gui.screen.option.OptionsScreen
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

/**
 * Every Tunnel's main menu, in place of vanilla's title screen (`MinecraftClientMixin`):
 * black, the logo and the options on the left, the version at the bottom, and, when the
 * mouse is left still, something in the dark on the right ([MenuFigure]). Its music is [MenuMusic].
 *
 * - BEGIN opens Noname Complex (installed by `client.MapWorldInstaller`), behind the loading
 *   screen ([LoadingCover]).
 * - OPTIONS opens vanilla's options (the language is changed there); GUIDE opens the player
 *   guide ([GuideScreen]); CREDITS rolls the credits
 *   ([EndOfDemoScreen] without "End of demo"); EXIT closes the game.
 * - Mouse (hover and click) or keyboard (arrows, Enter).
 * - Ctrl+Shift+V, not shown anywhere, swaps to vanilla's title screen (other worlds, mods,
 *   multiplayer) and back. Vanilla's stays until the player joins a world.
 *
 * The logo is `textures/gui/logo.png` (`tools/gui/logo.py`); without that texture, the name
 * in big letters.
 */
class MainMenuScreen : Screen(Text.translatable("menu.every_tunnel.title")) {

    private enum class Option(val key: String) {
        BEGIN("menu.every_tunnel.begin"),
        OPTIONS("menu.every_tunnel.options"),
        GUIDE("menu.every_tunnel.guide"),
        CREDITS("menu.every_tunnel.credits"),
        EXIT("menu.every_tunnel.exit"),
    }

    private val openedAtMs = Util.getMeasuringTimeMs()
    private var selected = 0
    private var notice: Text? = null
    private var noticeUntilMs = 0L

    override fun shouldCloseOnEsc(): Boolean = false

    // Opening the menu counts as activity: he never waits there already.
    override fun init() {
        figure.activity()
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0xFF000000.toInt())
        figure.render(context, width, height)

        // Everything fades in from black when the menu first opens.
        val fade = ((Util.getMeasuringTimeMs() - openedAtMs) / FADE_IN_MS.toFloat()).coerceIn(0f, 1f)
        if (fade * 255f < 4f) return
        val layout = layout()
        val left = layout.left
        drawLogo(context, layout, fade)

        val top = layout.optionsTop
        hoveredOption(mouseX, mouseY, layout)?.let { selected = it }
        Option.entries.forEachIndexed { index, option ->
            val y = top + index * LINE_HEIGHT
            val chosen = index == selected
            val label = Text.translatable(option.key)
            context.matrices.push()
            context.matrices.translate(left.toFloat(), y.toFloat(), 0f)
            context.matrices.scale(OPTION_SCALE, OPTION_SCALE, 1f)
            if (chosen) context.drawText(textRenderer, Text.literal(">"), -12, 0, argb(SELECTED_COLOR, fade), false)
            context.drawText(textRenderer, label, 0, 0, argb(if (chosen) SELECTED_COLOR else OPTION_COLOR, fade), false)
            context.matrices.pop()
        }

        notice?.takeIf { Util.getMeasuringTimeMs() < noticeUntilMs }?.let {
            context.drawText(textRenderer, it, left, top + optionsHeight() + NOTICE_GAP, argb(NOTICE_COLOR, fade), false)
        }
        context.drawText(textRenderer, versionText, left, height - VERSION_FROM_BOTTOM, argb(VERSION_COLOR, fade), false)
    }

    /**
     * Where things go on this screen size. The options and the version line keep their size;
     * the logo shrinks (down to [MIN_LOGO_HEIGHT]) so nothing overlaps on short screens or
     * with a big GUI scale.
     */
    private data class Layout(val left: Int, val logoTop: Int, val logoHeight: Int, val optionsTop: Int)

    private fun layout(): Layout {
        val left = (width * LEFT).toInt()
        val gap = minOf(LOGO_GAP, height / 20 + 6)
        // Room left for the logo above the options, and above the notice and version line.
        val below = gap + optionsHeight() + BOTTOM_RESERVE
        val logoHeight = minOf(LOGO_HEIGHT, height - TOP_MARGIN - below, (width - left * 2) / 2).coerceAtLeast(MIN_LOGO_HEIGHT)
        val logoTop = (height * LOGO_TOP).toInt().coerceAtMost(height - below - logoHeight).coerceAtLeast(TOP_MARGIN)
        return Layout(left, logoTop, logoHeight, logoTop + logoHeight + gap)
    }

    private fun optionsHeight(): Int = (Option.entries.size - 1) * LINE_HEIGHT + (9 * OPTION_SCALE).toInt()

    private fun drawLogo(context: DrawContext, layout: Layout, fade: Float) {
        val client = client ?: return
        val left = layout.left
        val top = layout.logoTop
        if (client.resourceManager.getResource(LOGO).isPresent) {
            val h = layout.logoHeight
            val w = h * 2
            context.setShaderColor(1f, 1f, 1f, fade)
            RenderSystem.enableBlend()
            context.drawTexture(LOGO, left, top, w, h, 0f, 0f, LOGO_TEXTURE_W, LOGO_TEXTURE_H, LOGO_TEXTURE_W, LOGO_TEXTURE_H)
            RenderSystem.disableBlend()
            context.setShaderColor(1f, 1f, 1f, 1f)
            return
        }
        // No logo yet: the name in big letters, on two lines.
        context.matrices.push()
        context.matrices.translate(left.toFloat(), top.toFloat(), 0f)
        val scale = layout.logoHeight / 20f
        context.matrices.scale(scale, scale, 1f)
        context.drawText(textRenderer, Text.literal("EVERY"), 0, 0, argb(LOGO_COLOR, fade), false)
        context.drawText(textRenderer, Text.literal("TUNNEL"), 0, 10, argb(LOGO_COLOR, fade), false)
        context.matrices.pop()
    }

    private fun hoveredOption(mouseX: Int, mouseY: Int, layout: Layout): Int? {
        val left = layout.left
        val top = layout.optionsTop
        Option.entries.forEachIndexed { index, option ->
            val y = top + index * LINE_HEIGHT
            val w = (textRenderer.getWidth(Text.translatable(option.key)) * OPTION_SCALE).toInt()
            if (mouseX in (left - 14)..(left + w) && mouseY in (y - 2)..(y + (9 * OPTION_SCALE).toInt() + 2)) return index
        }
        return null
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        figure.activity()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        figure.activity()
        val index = hoveredOption(mouseX.toInt(), mouseY.toInt(), layout()) ?: return false
        selected = index
        choose(Option.entries[index])
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (isVanillaToggle(keyCode, modifiers)) {
            showVanilla()
            return true
        }
        when (keyCode) {
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> selected = (selected + Option.entries.size - 1) % Option.entries.size
            GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> selected = (selected + 1) % Option.entries.size
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> choose(Option.entries[selected])
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }

    private fun choose(option: Option) {
        val client = client ?: return
        when (option) {
            Option.BEGIN -> begin(client)
            Option.OPTIONS -> client.setScreen(OptionsScreen(this, client.options))
            Option.GUIDE -> client.setScreen(GuideScreen(this))
            Option.CREDITS -> client.setScreen(EndOfDemoScreen.credits(this))
            Option.EXIT -> client.scheduleStop()
        }
    }

    private fun begin(client: MinecraftClient) {
        val world = client.levelStorage.savesDirectory.resolve(WORLD_FOLDER)
        if (!world.resolve("level.dat").toFile().isFile) {
            notice = Text.translatable("menu.every_tunnel.world_missing")
            noticeUntilMs = Util.getMeasuringTimeMs() + 5000L
            EveryTunnel.LOGGER.warn("BEGIN: no map world at {}", world)
            return
        }
        // As vanilla does when a world is picked in the list (and LoadingCover covers it).
        LoadingCover.worldOpening(WORLD_FOLDER)
        client.setScreenAndRender(MessageScreen(Text.translatable("selectWorld.data_read")))
        client.createIntegratedServerLoader().start(this, WORLD_FOLDER)
    }

    companion object {
        private val LOGO = Identifier(EveryTunnel.MOD_ID, "textures/gui/logo.png")
        private const val LOGO_TEXTURE_W = 256
        private const val LOGO_TEXTURE_H = 128
        /** The logo's height when there is room (it is twice as wide), and the least it shrinks to. */
        private const val LOGO_HEIGHT = 100
        private const val MIN_LOGO_HEIGHT = 24
        private const val WORLD_FOLDER = "noname_complex"

        private const val FADE_IN_MS = 1500L
        /** Left margin and the logo's top, as shares of the screen. */
        private const val LEFT = 0.08f
        private const val LOGO_TOP = 0.14f
        private const val LOGO_GAP = 28
        private const val TOP_MARGIN = 8
        /** Kept free under the options: the notice line and the version line. */
        private const val BOTTOM_RESERVE = 42
        private const val NOTICE_GAP = 6
        private const val VERSION_FROM_BOTTOM = 16
        private const val OPTION_SCALE = 1.5f
        private const val LINE_HEIGHT = 20

        private const val LOGO_COLOR = 0xD8DCE0
        private const val OPTION_COLOR = 0x6E737A
        private const val SELECTED_COLOR = 0xE8ECF0
        private const val VERSION_COLOR = 0x3A3E44
        private const val NOTICE_COLOR = 0xA05050

        /** True while vanilla's title screen was asked for (Ctrl+Shift+V), until the player joins a world. */
        @JvmStatic
        var vanillaShown = false
            private set

        /** Shared, so he does not stand in the same place again when the menu is reopened. */
        private val figure = MenuFigure()

        private val versionText: Text by lazy {
            val version = FabricLoader.getInstance().getModContainer(EveryTunnel.MOD_ID)
                .map { it.metadata.version.friendlyString }.orElse("?")
            Text.translatable("menu.every_tunnel.version", version)
        }

        /** What a screen being opened becomes (from `MinecraftClientMixin`): ours instead of vanilla's title screen. */
        @JvmStatic
        fun replace(screen: Screen?): Screen? =
            if (screen is TitleScreen && !vanillaShown) MainMenuScreen() else screen

        fun register() {
            ClientPlayConnectionEvents.JOIN.register { _, _, _ -> vanillaShown = false }
            // In vanilla's title screen the same keys bring ours back.
            ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
                if (screen is TitleScreen) {
                    ScreenKeyboardEvents.afterKeyPress(screen).register { _, key, _, modifiers ->
                        if (isVanillaToggle(key, modifiers)) {
                            vanillaShown = false
                            MinecraftClient.getInstance().setScreen(MainMenuScreen())
                        }
                    }
                }
            }
        }

        private fun showVanilla() {
            vanillaShown = true
            MinecraftClient.getInstance().setScreen(TitleScreen())
        }

        private fun isVanillaToggle(keyCode: Int, modifiers: Int): Boolean =
            keyCode == GLFW.GLFW_KEY_V &&
                modifiers and GLFW.GLFW_MOD_CONTROL != 0 && modifiers and GLFW.GLFW_MOD_SHIFT != 0

        private fun argb(rgb: Int, alpha: Float): Int = ((alpha * 255f).toInt().coerceIn(4, 255) shl 24) or rgb
    }
}
