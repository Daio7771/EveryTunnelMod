package daio7771.everytunnel.client.screen

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.menu.MenuScene
import daio7771.everytunnel.platform.SaveGameHooks
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
import net.minecraft.nbt.NbtIo
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.nio.file.Path

/**
 * Every Tunnel's main menu, in place of vanilla's title screen (`MinecraftClientMixin`): the
 * logo and the options on the left over black, the version at the bottom, and behind it all
 * a parking hall whose lamps flicker now and then ([MenuScene]); the black fades out towards
 * the right ([drawShade]) to show it. Its music is [MenuMusic].
 *
 * - CONTINUE (only with a game saved, `data/every_tunnel_progress.dat` in the world, see
 *   `platform.SaveGameHooks`) opens Noname Complex (installed by `client.MapWorldInstaller`),
 *   behind the loading screen ([LoadingCover]): the game goes on at the furthest level reached.
 * - NEW GAME deletes that save and opens it: the game starts over in the elevator. With a game
 *   saved it asks first, in place of the options ("Start over?", NO picked to begin with).
 * - OPTIONS opens vanilla's options (the language is changed there); GUIDE opens the player
 *   guide ([GuideScreen], whose last tab rolls the credits); ACHIEVEMENTS lists the
 *   achievements ([AchievementsScreen]); EXIT closes the game.
 * - Mouse (hover and click) or keyboard (arrows, Enter).
 * - Ctrl+Shift+V, not shown anywhere, swaps to vanilla's title screen (other worlds, mods,
 *   multiplayer) and back. Vanilla's stays until the player joins a world.
 *
 * The logo is `textures/gui/logo.png` (`tools/gui/logo.py`); without that texture, the name
 * in big letters.
 */
class MainMenuScreen : Screen(Text.translatable("menu.every_tunnel.title")) {

    private enum class Option(val key: String) {
        CONTINUE("menu.every_tunnel.continue"),
        NEW_GAME("menu.every_tunnel.new_game"),
        OPTIONS("menu.every_tunnel.options"),
        GUIDE("menu.every_tunnel.guide"),
        ACHIEVEMENTS("menu.every_tunnel.achievements"),
        EXIT("menu.every_tunnel.exit"),
        START_OVER("menu.every_tunnel.start_over"),
        KEEP("menu.every_tunnel.keep_game"),
    }

    private val openedAtMs = Util.getMeasuringTimeMs()
    private var selected = 0
    /** A game is saved in the map world (read as the screen opens). */
    private var saved = false
    /** NEW GAME was picked with a game saved: asking first. */
    private var confirming = false

    /** The options shown now, top to bottom. */
    private val shown: List<Option>
        get() = when {
            confirming -> listOf(Option.START_OVER, Option.KEEP)
            saved -> listOf(Option.CONTINUE, Option.NEW_GAME, Option.OPTIONS, Option.GUIDE, Option.ACHIEVEMENTS, Option.EXIT)
            else -> listOf(Option.NEW_GAME, Option.OPTIONS, Option.GUIDE, Option.ACHIEVEMENTS, Option.EXIT)
        }

    override fun init() {
        super.init()
        saved = client?.let(::hasSavedGame) == true
        confirming = false
        selected = selected.coerceIn(0, shown.size - 1)
    }
    private var notice: Text? = null
    private var noticeUntilMs = 0L

    override fun shouldCloseOnEsc(): Boolean = false

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val layout = layout()
        // The scene first (it clears the depth buffer), or plain black without it.
        if (MenuScene.render()) drawShade(context, layout) else context.fill(0, 0, width, height, 0xFF000000.toInt())

        // Everything fades in from black when the menu first opens.
        val fade = ((Util.getMeasuringTimeMs() - openedAtMs) / FADE_IN_MS.toFloat()).coerceIn(0f, 1f)
        if (fade < 1f) context.fill(0, 0, width, height, ((1f - fade) * 255f).toInt() shl 24)
        if (fade * 255f < 4f) return
        val left = layout.left
        drawLogo(context, layout, fade)

        val top = optionsTop(layout)
        hoveredOption(mouseX, mouseY, layout)?.let { selected = it }
        if (confirming) {
            context.drawText(textRenderer, Text.translatable("menu.every_tunnel.start_over_question"), left, layout.optionsTop, argb(QUESTION_COLOR, fade), false)
        }
        shown.forEachIndexed { index, option ->
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
            context.drawText(textRenderer, it, left, layout.optionsTop + optionsHeight() + NOTICE_GAP, argb(NOTICE_COLOR, fade), false)
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

    /** The room the options take: the longest list, so the menu does not move when it changes. */
    private fun optionsHeight(): Int = (MOST_OPTIONS - 1) * LINE_HEIGHT + (9 * OPTION_SCALE).toInt()

    /** Where the first option goes: one line lower while asking, under the question. */
    private fun optionsTop(layout: Layout): Int = layout.optionsTop + if (confirming) QUESTION_HEIGHT else 0

    /**
     * Black over the scene: solid behind the logo and the options (at least [SOLID_SHARE] of
     * the width), then fading out (smoothly) until [CLEAR_SHARE] of the width, where the scene
     * shows fully.
     */
    private fun drawShade(context: DrawContext, layout: Layout) {
        val options = maxOf(
            Option.entries.maxOf { (textRenderer.getWidth(Text.translatable(it.key)) * OPTION_SCALE).toInt() },
            textRenderer.getWidth(Text.translatable("menu.every_tunnel.start_over_question")),
        )
        val menuRight = layout.left + maxOf(layout.logoHeight * 2, options) + SHADE_MARGIN
        val solid = maxOf((width * SOLID_SHARE).toInt(), menuRight).coerceAtMost(width)
        val clear = maxOf((width * CLEAR_SHARE).toInt(), solid + (width * MIN_FADE_SHARE).toInt()).coerceAtMost(width)
        context.fill(0, 0, solid, height, 0xFF000000.toInt())
        for (x in solid until clear) {
            val t = 1f - (x - solid + 0.5f) / (clear - solid)
            val alpha = t * t * (3f - 2f * t)
            context.fill(x, 0, x + 1, height, (alpha * 255f).toInt() shl 24)
        }
    }

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
        val top = optionsTop(layout)
        shown.forEachIndexed { index, option ->
            val y = top + index * LINE_HEIGHT
            val w = (textRenderer.getWidth(Text.translatable(option.key)) * OPTION_SCALE).toInt()
            if (mouseX in (left - 14)..(left + w) && mouseY in (y - 2)..(y + (9 * OPTION_SCALE).toInt() + 2)) return index
        }
        return null
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val index = hoveredOption(mouseX.toInt(), mouseY.toInt(), layout()) ?: return false
        selected = index
        choose(shown[index])
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (isVanillaToggle(keyCode, modifiers)) {
            showVanilla()
            return true
        }
        when (keyCode) {
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> selected = (selected + shown.size - 1) % shown.size
            GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> selected = (selected + 1) % shown.size
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> choose(shown[selected])
            GLFW.GLFW_KEY_ESCAPE -> if (confirming) choose(Option.KEEP) else return super.keyPressed(keyCode, scanCode, modifiers)
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }

    private fun choose(option: Option) {
        val client = client ?: return
        when (option) {
            Option.CONTINUE -> begin(client)
            Option.NEW_GAME -> if (saved) {
                confirming = true
                selected = shown.indexOf(Option.KEEP)
            } else {
                newGame(client)
            }
            Option.START_OVER -> newGame(client)
            Option.KEEP -> {
                confirming = false
                selected = shown.indexOf(Option.NEW_GAME)
            }
            Option.OPTIONS -> client.setScreen(OptionsScreen(this, client.options))
            Option.GUIDE -> client.setScreen(GuideScreen(this))
            Option.ACHIEVEMENTS -> client.setScreen(AchievementsScreen(this))
            Option.EXIT -> client.scheduleStop()
        }
    }

    /** The saved game deleted (if any), then the world opens: the server starts a new game. */
    private fun newGame(client: MinecraftClient) {
        val save = saveFile(client)
        try {
            Files.deleteIfExists(save)
        } catch (e: Exception) {
            EveryTunnel.LOGGER.error("NEW GAME: could not delete the saved game {}", save, e)
            notice = Text.translatable("menu.every_tunnel.save_not_deleted")
            noticeUntilMs = Util.getMeasuringTimeMs() + 5000L
            confirming = false
            return
        }
        begin(client)
    }

    private fun begin(client: MinecraftClient) {
        val world = client.levelStorage.savesDirectory.resolve(WORLD_FOLDER)
        if (!world.resolve("level.dat").toFile().isFile) {
            notice = Text.translatable("menu.every_tunnel.world_missing")
            noticeUntilMs = Util.getMeasuringTimeMs() + 5000L
            EveryTunnel.LOGGER.warn("No map world at {}", world)
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
        /** The most options shown at once (with a game saved). */
        private const val MOST_OPTIONS = 6
        /** The question's line above the options while asking. */
        private const val QUESTION_HEIGHT = 16

        private const val FADE_IN_MS = 1500L
        /** The black over the scene ([drawShade]): solid to here at least, clear from here, as shares of the width. */
        private const val SOLID_SHARE = 0.30f
        private const val CLEAR_SHARE = 0.65f
        private const val MIN_FADE_SHARE = 0.15f
        /** Black kept past the logo's or the widest option's right edge. */
        private const val SHADE_MARGIN = 4
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
        private const val QUESTION_COLOR = 0xB8BCC2

        private fun saveFile(client: MinecraftClient): Path =
            client.levelStorage.savesDirectory.resolve(WORLD_FOLDER).resolve("data").resolve("${SaveGameHooks.SAVE_ID}.dat")

        /** A game in progress is saved in the map world (`platform.SaveGameHooks`). */
        private fun hasSavedGame(client: MinecraftClient): Boolean {
            val file = saveFile(client)
            if (!Files.isRegularFile(file)) return false
            return try {
                NbtIo.readCompressed(file.toFile()).getCompound("data").getBoolean("active")
            } catch (e: Exception) {
                EveryTunnel.LOGGER.warn("Could not read the saved game {}", file, e)
                false
            }
        }

        /** True while vanilla's title screen was asked for (Ctrl+Shift+V), until the player joins a world. */
        @JvmStatic
        var vanillaShown = false
            private set

        private val versionText: Text by lazy {
            val version = FabricLoader.getInstance().getModContainer(EveryTunnel.MOD_ID)
                .map { it.metadata.version.friendlyString }.orElse("?")
            Text.translatable("menu.every_tunnel.version", version)
        }

        /**
         * What a screen being opened becomes (from `MinecraftClientMixin`): ours instead of
         * vanilla's title screen. No screen with no world open is the title screen too (vanilla
         * makes one then, as when Escape closes the options).
         */
        @JvmStatic
        fun replace(screen: Screen?): Screen? {
            val title = screen is TitleScreen || (screen == null && MinecraftClient.getInstance().world == null)
            return if (title && !vanillaShown) MainMenuScreen() else screen
        }

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
