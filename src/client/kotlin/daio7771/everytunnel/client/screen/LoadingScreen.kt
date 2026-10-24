package daio7771.everytunnel.client.screen

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.LoadingHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.DownloadingTerrainScreen
import net.minecraft.client.gui.screen.LevelLoadingScreen
import net.minecraft.client.gui.screen.MessageScreen
import net.minecraft.client.gui.screen.ProgressScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.gui.screen.world.SelectWorldScreen
import net.minecraft.client.resource.language.I18n
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.Util
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * The loading screen of a world with levels while the server prepares it
 * (`platform.LoadingHooks`). It shows nothing itself: [LoadingCover] draws it. It does not
 * pause the game (the server has to keep building) and Escape does not close it.
 */
class LoadingScreen : Screen(Text.translatable("loading.every_tunnel.title")) {

    override fun shouldPause(): Boolean = false

    override fun shouldCloseOnEsc(): Boolean = false

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        LoadingCover.draw(context, width, height)
    }
}

/**
 * One black loading screen from picking a world with levels until the player stands in the
 * elevator: a tip in the middle ([LoadingTips]) and "Loading..." below. Silent.
 *
 * 1. Opening: when a world is picked (`WorldListEntryMixin`, `QuickPlayMixin`) and one of
 *    its datapacks has Every Tunnel levels, vanilla's loading screens (reading the world,
 *    preparing the spawn area, loading terrain) and the resource reload splash are drawn
 *    over ([drawOver], from `ScreenMixin` and `SplashOverlayMixin`).
 * 2. Loading: the server says the world is being prepared ([LoadingHooks.STATE_LOADING]):
 *    [LoadingScreen] opens, and stays open until the end.
 * 3. Ready: the server has started the new game ([LoadingHooks.STATE_READY]). Once the
 *    terrain around the player is drawn (at least [MIN_READY_MS], at most [MAX_READY_MS]),
 * 4. Fading: the black fades out over [FADE_MS] onto the closed elevator; then the screen
 *    closes and the server is told ([LoadingHooks.DONE_PACKET_ID]), which starts the intro.
 *
 * The game is silent until the fade, then its sounds fade in with the picture
 * ([worldVolume], from `SoundSystemMixin`). Leaving the world starts it all over. Other
 * worlds are never touched.
 */
object LoadingCover {

    private enum class Phase { OFF, OPENING, LOADING, READY, FADING }

    private const val MIN_READY_MS = 750L
    /** After this long, the player's own spot being drawn is enough (the rest may keep streaming in). */
    private const val SETTLE_MS = 3000L
    private const val MAX_READY_MS = 8000L
    private const val FADE_MS = 1500L
    /** In the menus again this long after picking the world: the opening failed or was cancelled. */
    private const val MENU_GRACE_MS = 2000L
    /** In the world this long without hearing from the server: not a loading world after all. */
    private const val NO_SERVER_MS = 15000L

    /** The game's volume behind the loading screen: inaudible, but not zero (see `SoundSystemMixin`). */
    private const val QUIET = 0.0001f

    private const val TIP_COLOR = 0xBDBDBD
    private const val LOADING_COLOR = 0x6E6E6E
    private const val TIP_WIDTH = 300
    private const val TIP_LINE_GAP = 2
    /** Gap between the middle of the screen and "Loading..." below it. */
    private const val LOADING_BELOW = 26
    /** Above everything the covered screens draw. */
    private const val Z = 500f
    private const val LEVELS_DIR = "data/${EveryTunnel.MOD_ID}/levels"

    private var phase = Phase.OFF
    private var phaseStartMs = 0L

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(LoadingHooks.STATE_PACKET_ID) { client, _, buf, _ ->
            val state = buf.readByte()
            client.execute { onState(client, state) }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { enter(Phase.OFF) } }
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    /** A world is being opened from the menu or by quick play (from mixins). */
    @JvmStatic
    fun worldOpening(levelName: String) {
        val world = MinecraftClient.getInstance().levelStorage.savesDirectory.resolve(levelName)
        val hasLevels = hasLevels(world.resolve("datapacks"))
        enter(if (hasLevels) Phase.OPENING else Phase.OFF)
        if (hasLevels) {
            LoadingTips.restart()
            EveryTunnel.LOGGER.info("Opening {}: a world with levels, loading screen on", levelName)
        }
    }

    /** Draws over [screen] if it is one of vanilla's loading screens and the cover is on (from `ScreenMixin`). */
    @JvmStatic
    fun drawOver(screen: Screen, context: DrawContext) {
        if (phase == Phase.OFF || screen is LoadingScreen || !isVanillaLoading(screen)) return
        draw(context, screen.width, screen.height)
    }

    /** Draws over the resource reload splash while the cover is on (from `SplashOverlayMixin`). */
    @JvmStatic
    fun drawOverSplash(context: DrawContext) {
        if (phase == Phase.OFF) return
        draw(context, context.scaledWindowWidth, context.scaledWindowHeight)
    }

    /** True from picking a world with levels until the loading screen is gone. */
    val active: Boolean get() = phase != Phase.OFF

    /** What every sound's volume is multiplied by: nearly silent while loading, back up during the fade. */
    @JvmStatic
    fun worldVolume(): Float = when (phase) {
        Phase.OFF -> 1f
        Phase.FADING -> maxOf(QUIET, 1f - alpha())
        else -> QUIET
    }

    fun draw(context: DrawContext, width: Int, height: Int) {
        val alpha = alpha()
        if (alpha <= 0f) return
        val now = Util.getMeasuringTimeMs()
        val textRenderer = MinecraftClient.getInstance().textRenderer
        context.matrices.push()
        context.matrices.translate(0f, 0f, Z)
        context.fill(0, 0, width, height, argb(0x000000, alpha))

        val middle = height / 2
        LoadingTips.at(now)?.let { (tip, opacity) ->
            val lines = textRenderer.wrapLines(tip, minOf(TIP_WIDTH, width - 40))
            val lineHeight = textRenderer.fontHeight + TIP_LINE_GAP
            var y = middle - lines.size * lineHeight / 2
            for (line in lines) {
                drawCentered(context, textRenderer, line, width, y, TIP_COLOR, alpha * opacity)
                y += lineHeight
            }
        }
        val loading = I18n.translate("loading.every_tunnel.loading")
        val dots = ((now / 400) % 4).toInt()
        // From a fixed left edge, so the text does not move as the dots come and go.
        val x = (width - textRenderer.getWidth("$loading...")) / 2
        drawText(context, textRenderer, Text.literal(loading + ".".repeat(dots)).asOrderedText(), x, middle + LOADING_BELOW, LOADING_COLOR, alpha)
        context.matrices.pop()
    }

    private fun onState(client: MinecraftClient, state: Byte) {
        when (state) {
            LoadingHooks.STATE_LOADING -> {
                if (phase == Phase.OFF) LoadingTips.restart()
                enter(Phase.LOADING)
                keepOpen(client)
            }
            LoadingHooks.STATE_READY -> {
                if (phase == Phase.OFF) LoadingTips.restart()
                enter(Phase.READY)
                keepOpen(client)
            }
        }
    }

    private fun tick(client: MinecraftClient) {
        val elapsed = Util.getMeasuringTimeMs() - phaseStartMs
        when (phase) {
            Phase.OFF -> Unit
            Phase.OPENING -> {
                val screen = client.currentScreen
                val inMenus = client.world == null && (screen is TitleScreen || screen is SelectWorldScreen)
                if ((inMenus && elapsed > MENU_GRACE_MS) || (client.world != null && elapsed > NO_SERVER_MS)) enter(Phase.OFF)
            }
            Phase.LOADING -> keepOpen(client)
            Phase.READY -> {
                keepOpen(client)
                if (terrainReady(client, elapsed)) enter(Phase.FADING)
            }
            Phase.FADING -> {
                keepOpen(client)
                if (elapsed >= FADE_MS) finish(client)
            }
        }
    }

    private fun terrainReady(client: MinecraftClient, elapsed: Long): Boolean {
        if (elapsed >= MAX_READY_MS) return true
        if (elapsed < MIN_READY_MS) return false
        val player = client.player ?: return false
        val renderer = client.worldRenderer
        if (!renderer.isRenderingReady(player.blockPos)) return false
        return renderer.isTerrainRenderComplete || elapsed >= SETTLE_MS
    }

    private fun finish(client: MinecraftClient) {
        enter(Phase.OFF)
        if (client.currentScreen is LoadingScreen) client.setScreen(null)
        if (ClientPlayNetworking.canSend(LoadingHooks.DONE_PACKET_ID)) {
            ClientPlayNetworking.send(LoadingHooks.DONE_PACKET_ID, PacketByteBufs.empty())
        }
    }

    /** Puts the loading screen back if vanilla's terrain screen (or nothing) took its place. */
    private fun keepOpen(client: MinecraftClient) {
        if (client.world == null) return
        val screen = client.currentScreen
        if (screen == null || screen is DownloadingTerrainScreen) client.setScreen(LoadingScreen())
    }

    private fun enter(next: Phase) {
        phase = next
        phaseStartMs = Util.getMeasuringTimeMs()
    }

    private fun alpha(): Float = when (phase) {
        Phase.OFF -> 0f
        Phase.FADING -> (1f - (Util.getMeasuringTimeMs() - phaseStartMs).toFloat() / FADE_MS).coerceIn(0f, 1f)
        else -> 1f
    }

    private fun isVanillaLoading(screen: Screen): Boolean =
        screen is MessageScreen || screen is LevelLoadingScreen || screen is ProgressScreen || screen is DownloadingTerrainScreen

    /** True if a datapack in [datapacks] (a folder or a zip) has Every Tunnel level files. */
    private fun hasLevels(datapacks: Path): Boolean {
        if (!datapacks.isDirectory()) return false
        return try {
            datapacks.listDirectoryEntries().any { pack ->
                when {
                    pack.isDirectory() -> pack.resolve(LEVELS_DIR).isDirectory() &&
                        Files.list(pack.resolve(LEVELS_DIR)).use { files -> files.anyMatch { it.name.endsWith(".json") } }
                    pack.name.endsWith(".zip") -> ZipFile(pack.toFile()).use { zip ->
                        zip.stream().anyMatch { it.name.startsWith("$LEVELS_DIR/") && it.name.endsWith(".json") }
                    }
                    else -> false
                }
            }
        } catch (e: Exception) {
            EveryTunnel.LOGGER.warn("Could not look into {} for level files", datapacks, e)
            false
        }
    }

    private fun drawCentered(context: DrawContext, textRenderer: TextRenderer, text: OrderedText, width: Int, y: Int, rgb: Int, alpha: Float) {
        drawText(context, textRenderer, text, (width - textRenderer.getWidth(text)) / 2, y, rgb, alpha)
    }

    private fun drawText(context: DrawContext, textRenderer: TextRenderer, text: OrderedText, x: Int, y: Int, rgb: Int, alpha: Float) {
        // Text with almost no alpha is drawn opaque by the game: skip it instead.
        if (alpha * 255f < 4f) return
        context.drawText(textRenderer, text, x, y, argb(rgb, alpha), false)
    }

    private fun argb(rgb: Int, alpha: Float): Int = ((alpha * 255f).toInt().coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)
}
