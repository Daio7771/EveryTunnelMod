package daio7771.everytunnel.client.screen

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.AchievementStore
import daio7771.everytunnel.client.effects.MenuMusic
import daio7771.everytunnel.client.menu.MenuScene
import daio7771.everytunnel.core.achievement.Achievement
import daio7771.everytunnel.core.menu.LogoRun
import daio7771.everytunnel.platform.ChapterRequest
import daio7771.everytunnel.platform.ContinueGame
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
import java.io.File
import kotlin.random.Random

/**
 * Every Tunnel's main menu, in place of vanilla's title screen (`MinecraftClientMixin`): the
 * logo and the options on the left over black, the version at the bottom, and behind it all
 * a parking hall whose lamps flicker now and then ([MenuScene]); the black fades out towards
 * the right ([drawShade]) to show it. Its music is [MenuMusic].
 *
 * - The menu is a chapter's: [chapter], 1 to begin with, gives it its scene and its music ([MenuScene.show],
 *   [MenuMusic.setChapter]). SELECT CHAPTER swaps the options for the chapters, CHAPTER 1, CHAPTER 2 and BACK, in
 *   the same menu: moving over one previews its scene and music through a short fade, choosing one makes it the
 *   menu's (the whole menu is then that chapter's, and stays so, for as long as the game runs, when the menu comes
 *   back after a world); BACK (or Escape) leaves it as it was. CHAPTER 2 carries a padlock until the achievement
 *   "Nothing in There" is earned.
 * - The way into the game depends on the state, one option and not two. In Chapter 1's menu, with a saved game
 *   ([ContinueGame]) it is CONTINUE, which goes on with it; without one it is NEW GAME, which opens Noname Complex
 *   (installed by `client.MapWorldInstaller`), behind the loading screen ([LoadingCover]). Starting over is
 *   Ctrl+Shift+D, explained in the guide: it erases the menu's chapter's saved game after asking ([EraseSaveScreen]), and NEW
 *   GAME is back. In Chapter 2's menu it is NEW GAME, which only warns ([SpoilerAlertScreen]) and lets it be played anyway
 *   while Chapter 2 is locked, and opens the world for that chapter ([ChapterRequest]), which comes back here at its end; or,
 *   with Chapter 2's own saved game ([ChapterRequest.markerName]), CONTINUE, which opens the world to go on with it. Each
 *   chapter's saved game is apart: erasing one leaves the other.
 * - OPTIONS opens vanilla's options (the language is changed there); GUIDE opens the player
 *   guide ([GuideScreen], whose last tab rolls the credits); ACHIEVEMENTS lists the
 *   achievements ([AchievementsScreen]); EXIT closes the game.
 * - Mouse (hover and click) or keyboard (arrows, Enter).
 * - Ctrl+Shift+V, not shown anywhere, swaps to vanilla's title screen (other worlds, mods,
 *   multiplayer) and back. Vanilla's stays until the player joins a world.
 *
 * The logo is chalk on a blackboard (`tools/gui/logo.py`), in two layers: `textures/gui/logo_rest.png`, the words and
 * the tunnel emblem, and `logo_run.png`, the R of EVERY and the U and N of TUNNEL. Now and then ([LogoRun]) the words
 * flicker out and those three letters, which read RUN, stay lit alone for a moment before the words come back. In
 * Chapter 2's menu `logo_chapter_2.png`, "CHAPTER 2", is drawn under the logo (and goes out with the words). Without
 * the textures, the name in big letters.
 */
class MainMenuScreen : Screen(Text.translatable("menu.every_tunnel.title")) {

    private enum class Option(val key: String) {
        NEW_GAME("menu.every_tunnel.new_game"),
        SELECT_CHAPTER("menu.every_tunnel.select_chapter"),
        CONTINUE("menu.every_tunnel.continue"),
        CHAPTER_1("menu.every_tunnel.chapter_1"),
        CHAPTER_2("menu.every_tunnel.chapter_2"),
        BACK("menu.every_tunnel.back"),
        OPTIONS("menu.every_tunnel.options"),
        GUIDE("menu.every_tunnel.guide"),
        ACHIEVEMENTS("menu.every_tunnel.achievements"),
        EXIT("menu.every_tunnel.exit");

        companion object {
            /** All but the way in, which depends on the saved game. */
            val REST = listOf(OPTIONS, GUIDE, ACHIEVEMENTS, EXIT)
        }
    }

    private val openedAtMs = Util.getMeasuringTimeMs()
    private val logoRun = LogoRun(Random.Default)
    private var selected = 0
    private var notice: Text? = null
    private var noticeUntilMs = 0L
    /** The chapters (1, 2) with a saved game to continue. */
    private var saved = emptySet<Int>()

    /** The chapter this menu is (1 or 2): its scene, its music and its way into the game. */
    private var chapter = rememberedChapter

    /** The chapters are showing instead of the options. */
    private var choosingChapter = false

    /** What the menu offers now: the chapters, or one way into the game (CONTINUE with the chapter's saved game, else NEW GAME), SELECT CHAPTER and the rest. */
    private val options: List<Option>
        get() = if (choosingChapter) listOf(Option.CHAPTER_1, Option.CHAPTER_2, Option.BACK)
        else listOf(if (chapter in saved) Option.CONTINUE else Option.NEW_GAME, Option.SELECT_CHAPTER) + Option.REST

    /** Chapter 2 is locked until the achievement "Nothing in There" is earned: it only warns. */
    private val chapterTwoLocked: Boolean get() = Achievement.NOTHING_THERE !in AchievementStore.unlocked

    override fun init() {
        saved = (1..2).filter { saveMarker(it)?.isFile == true }.toSet()
    }

    /** The file that makes [number]'s saved game one: Chapter 1's ([ContinueGame.MARKER]) or a later chapter's ([ChapterRequest.markerName]). */
    private fun saveMarker(number: Int): File? {
        val name = if (number == 1) ContinueGame.MARKER else ChapterRequest.markerName(number)
        return client?.levelStorage?.savesDirectory?.resolve(WORLD_FOLDER)?.resolve(name)?.toFile()
    }

    /** Forgets this menu's chapter's saved game: its marker is what makes it one, and a new game clears the rest. */
    private fun eraseSave() {
        saveMarker(chapter)?.delete()
        saved = saved - chapter
        EveryTunnel.LOGGER.info("Chapter {}'s saved game was erased from the menu", chapter)
    }

    override fun shouldCloseOnEsc(): Boolean = false

    override fun tick() {
        logoRun.tick()
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val layout = layout()
        // The scene first (it clears the depth buffer), or plain black without it.
        showSelected()
        if (MenuScene.render()) drawShade(context, layout) else context.fill(0, 0, width, height, 0xFF000000.toInt())
        // Changing scene goes through black.
        MenuScene.fade().takeIf { it > 0f }?.let { context.fill(0, 0, width, height, ((it * 255f).toInt().coerceIn(0, 255)) shl 24) }

        // Everything fades in from black when the menu first opens.
        val fade = ((Util.getMeasuringTimeMs() - openedAtMs) / FADE_IN_MS.toFloat()).coerceIn(0f, 1f)
        if (fade < 1f) context.fill(0, 0, width, height, ((1f - fade) * 255f).toInt() shl 24)
        if (fade * 255f < 4f) return
        val left = layout.left
        drawLogo(context, layout, fade)

        val top = layout.optionsTop
        hoveredOption(mouseX, mouseY, layout)?.let { selected = it }
        options.forEachIndexed { index, option ->
            val y = top + index * LINE_HEIGHT
            val chosen = index == selected
            val label = Text.translatable(option.key)
            context.matrices.push()
            context.matrices.translate(left.toFloat(), y.toFloat(), 0f)
            context.matrices.scale(OPTION_SCALE, OPTION_SCALE, 1f)
            if (chosen) context.drawText(textRenderer, Text.literal(">"), -12, 0, argb(SELECTED_COLOR, fade), false)
            val colour = if (chosen) SELECTED_COLOR else OPTION_COLOR
            context.drawText(textRenderer, label, 0, 0, argb(colour, fade), false)
            if (option == Option.CHAPTER_2 && chapterTwoLocked) padlock(context, textRenderer.getWidth(label) + 8, -1, argb(colour, fade))
            context.matrices.pop()
        }

        notice?.takeIf { Util.getMeasuringTimeMs() < noticeUntilMs }?.let {
            context.drawText(textRenderer, it, left, top + optionsHeight() + NOTICE_GAP, argb(NOTICE_COLOR, fade), false)
        }
        context.drawText(textRenderer, versionText, left, height - VERSION_FROM_BOTTOM, argb(VERSION_COLOR, fade), false)
    }

    /** The scene and the music of the chapter the cursor is on while the chapters show, else of the menu's chapter. */
    private fun showSelected() {
        val shown = when (if (choosingChapter) options.getOrNull(selected) else null) {
            Option.CHAPTER_1 -> 1
            Option.CHAPTER_2 -> 2
            else -> chapter
        }
        MenuScene.show(if (shown == 2) MenuScene.LAB else MenuScene.GARAGE)
        MenuMusic.setChapter(shown)
    }

    /** A small padlock, 8 wide and 11 high at ([x], [y]) (in the option's scaled units): the shackle's arch over a body. */
    private fun padlock(context: DrawContext, x: Int, y: Int, colour: Int) {
        context.fill(x + 1, y, x + 7, y + 1, colour)
        context.fill(x + 1, y + 1, x + 2, y + 5, colour)
        context.fill(x + 6, y + 1, x + 7, y + 5, colour)
        context.fill(x, y + 5, x + 8, y + 11, colour)
        context.fill(x + 3, y + 7, x + 5, y + 9, 0xFF0A0A0B.toInt())
    }

    /**
     * Where things go on this screen size. The options and the version line keep their size;
     * the logo shrinks (down to [MIN_LOGO_HEIGHT]) so nothing overlaps on short screens or
     * with a big GUI scale.
     */
    private data class Layout(val left: Int, val logoTop: Int, val logoHeight: Int, val subtitleHeight: Int, val optionsTop: Int)

    private fun layout(): Layout {
        val left = (width * LEFT).toInt()
        val gap = minOf(LOGO_GAP, height / 20 + 6)
        // Room left for the logo (and Chapter 2's line under it) above the options, and above the notice and version line.
        val below = gap + optionsHeight() + BOTTOM_RESERVE
        val subtitleShare = if (chapter == 2) LOGO_SUBTITLE_H / LOGO_TEXTURE_H.toFloat() else 0f
        val logoHeight = minOf(LOGO_HEIGHT, ((height - TOP_MARGIN - below) / (1f + subtitleShare)).toInt(), (width - left * 2) / 2)
            .coerceAtLeast(MIN_LOGO_HEIGHT)
        val subtitleHeight = (logoHeight * subtitleShare).toInt()
        val whole = logoHeight + subtitleHeight
        val logoTop = (height * LOGO_TOP).toInt().coerceAtMost(height - below - whole).coerceAtLeast(TOP_MARGIN)
        return Layout(left, logoTop, logoHeight, subtitleHeight, logoTop + whole + gap)
    }

    private fun optionsHeight(): Int = (options.size - 1) * LINE_HEIGHT + (9 * OPTION_SCALE).toInt()

    /**
     * Black over the scene: solid behind the logo and the options (at least [SOLID_SHARE] of
     * the width), then fading out (smoothly) until [CLEAR_SHARE] of the width, where the scene
     * shows fully.
     */
    private fun drawShade(context: DrawContext, layout: Layout) {
        val options = options.maxOf { (textRenderer.getWidth(Text.translatable(it.key)) * OPTION_SCALE).toInt() }
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
        if (client.resourceManager.getResource(LOGO_REST).isPresent && client.resourceManager.getResource(LOGO_RUN).isPresent) {
            val h = layout.logoHeight
            val w = h * 2
            val words = fade * logoRun.wordsLevel
            RenderSystem.enableBlend()
            // The words and the emblem, which the run puts out; then RUN, always lit, drawn once more while it glows.
            drawLayer(context, LOGO_REST, left, top, w, h, LOGO_TEXTURE_H, words)
            drawLayer(context, LOGO_RUN, left, top, w, h, LOGO_TEXTURE_H, fade)
            if (logoRun.glow > 0f) drawLayer(context, LOGO_RUN, left, top, w, h, LOGO_TEXTURE_H, fade * logoRun.glow * GLOW)
            if (layout.subtitleHeight > 0) drawLayer(context, LOGO_CHAPTER_2, left, top + h, w, layout.subtitleHeight, LOGO_SUBTITLE_H, words)
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

    /** One 512-wide layer of the logo at ([x], [y]), [w] by [h], as light as [alpha]; [textureHeight] is the texture's. */
    private fun drawLayer(context: DrawContext, texture: Identifier, x: Int, y: Int, w: Int, h: Int, textureHeight: Int, alpha: Float) {
        if (alpha < 0.01f) return
        context.setShaderColor(1f, 1f, 1f, alpha.coerceAtMost(1f))
        context.drawTexture(texture, x, y, w, h, 0f, 0f, LOGO_TEXTURE_W, textureHeight, LOGO_TEXTURE_W, textureHeight)
    }

    private fun hoveredOption(mouseX: Int, mouseY: Int, layout: Layout): Int? {
        val left = layout.left
        val top = layout.optionsTop
        options.forEachIndexed { index, option ->
            val y = top + index * LINE_HEIGHT
            val w = (textRenderer.getWidth(Text.translatable(option.key)) * OPTION_SCALE).toInt()
            if (mouseX in (left - 14)..(left + w) && mouseY in (y - 2)..(y + (9 * OPTION_SCALE).toInt() + 2)) return index
        }
        return null
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val index = hoveredOption(mouseX.toInt(), mouseY.toInt(), layout()) ?: return false
        selected = index
        choose(options[index])
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (isVanillaToggle(keyCode, modifiers)) {
            showVanilla()
            return true
        }
        if (keyCode == GLFW.GLFW_KEY_D && hasShiftDown() && hasControlDown() && chapter in saved && !choosingChapter) {
            client?.setScreen(EraseSaveScreen(this, "menu.every_tunnel.erase.warning", ::eraseSave))
            return true
        }
        when (keyCode) {
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> selected = (selected + options.size - 1) % options.size
            GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> selected = (selected + 1) % options.size
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> choose(options[selected])
            GLFW.GLFW_KEY_ESCAPE -> if (choosingChapter) chooseChapter(false) else return super.keyPressed(keyCode, scanCode, modifiers)
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }

    private fun choose(option: Option) {
        val client = client ?: return
        when (option) {
            Option.NEW_GAME -> when {
                chapter == 1 -> open(client, false)
                chapterTwoLocked -> client.setScreen(SpoilerAlertScreen(this) { open(client, continuing = false, number = 2) })
                else -> open(client, continuing = false, number = 2)
            }
            Option.SELECT_CHAPTER -> chooseChapter(true)
            Option.BACK -> chooseChapter(false)
            Option.CHAPTER_1 -> becomeChapter(1)
            Option.CHAPTER_2 -> becomeChapter(2)
            Option.CONTINUE -> open(client, continuing = true, number = chapter)
            Option.OPTIONS -> client.setScreen(OptionsScreen(this, client.options))
            Option.GUIDE -> client.setScreen(GuideScreen(this))
            Option.ACHIEVEMENTS -> client.setScreen(AchievementsScreen(this))
            Option.EXIT -> client.scheduleStop()
        }
    }

    /** Shows the chapters in place of the options (or the options again), the cursor on this chapter's (or on SELECT CHAPTER). */
    private fun chooseChapter(on: Boolean) {
        choosingChapter = on
        selected = if (on) chapter - 1 else options.indexOf(Option.SELECT_CHAPTER)
    }

    /** The menu is [number]'s from now on (its scene and music already showing): the options come back. */
    private fun becomeChapter(number: Int) {
        chapter = number
        rememberedChapter = number
        chooseChapter(false)
    }

    /** Opens the map world for chapter [number]: to begin it or, [continuing], to go on with its saved game. */
    private fun open(client: MinecraftClient, continuing: Boolean, number: Int = 1) {
        ContinueGame.requested = continuing && number == 1
        ChapterRequest.chapter = if (number == 1) 0 else number
        ChapterRequest.continuing = continuing && number != 1
        val world = client.levelStorage.savesDirectory.resolve(WORLD_FOLDER)
        if (!world.resolve("level.dat").toFile().isFile) {
            ChapterRequest.clear()
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
        private val LOGO_REST = Identifier(EveryTunnel.MOD_ID, "textures/gui/logo_rest.png")
        private val LOGO_RUN = Identifier(EveryTunnel.MOD_ID, "textures/gui/logo_run.png")
        private val LOGO_CHAPTER_2 = Identifier(EveryTunnel.MOD_ID, "textures/gui/logo_chapter_2.png")
        /** The textures' sizes: the two layers of the logo are 512 by 256, Chapter 2's line 512 by 64. */
        private const val LOGO_TEXTURE_W = 512
        private const val LOGO_TEXTURE_H = 256
        private const val LOGO_SUBTITLE_H = 64
        /** How much brighter RUN shines at the most, as a share of the alpha of a second pass over itself. */
        private const val GLOW = 0.55f
        /** The logo's height when there is room (it is twice as wide), and the least it shrinks to. */
        private const val LOGO_HEIGHT = 100
        private const val MIN_LOGO_HEIGHT = 24
        private const val WORLD_FOLDER = "noname_complex"

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

        /** The chapter the menu was last left as, for as long as the game runs (the menu is made again after each world). */
        private var rememberedChapter = 1

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
