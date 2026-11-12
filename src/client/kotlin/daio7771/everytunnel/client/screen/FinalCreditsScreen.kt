package daio7771.everytunnel.client.screen

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.cutscene.FinalCredits
import daio7771.everytunnel.core.ending.RunSummary
import daio7771.everytunnel.platform.EndingHooks
import daio7771.everytunnel.platform.FalseExitHooks
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.MessageScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.text.OrderedText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import kotlin.math.pow

/**
 * The end of the game, after the fall down Level 5's false exit (`platform.FalseExitHooks`, `platform.EndingHooks`):
 * the final credits on black, timed to their song ([FinalCredits], 94 s). "THE END", a moment before the song comes
 * in, then "Every Tunnel"; four
 * screens, each with one of the author's drawings (`tools/gui/credits_art.py`) on one side, left and right in
 * turn, and on the other its title typed out and its lines one by one, the credits under both, dimmer and
 * smaller; the tools, a group at a time; "Thank you for playing"; "Level 6 coming soon" in the archive's purple;
 * black, and back to the title screen (the world saved; the saved game is already erased). Esc at any moment
 * fades the song and the screen out and goes back the same way. Its texts are the lang keys
 * `every_tunnel.credits.*` (lines split at "\n"), in Minecraft's font. Nothing is placed by fixed sizes: a longer
 * translation wraps its title, shrinks its text and lifts its credits ([layoutOf]) instead of running into them or
 * off the window's edges.
 *
 * When they end (or are skipped) the congratulations window opens over the menu ([CongratulationsScreen]), with the
 * numbers of the game that came in the ending packet.
 *
 * Also here: the fall's music, in the player's head ([FalseExitHooks.FALL_PACKET_ID]).
 */
class FinalCreditsScreen(private val summary: RunSummary = RunSummary.EMPTY) : Screen(Text.translatable("every_tunnel.credits.the_end")) {

    private val openedAt = Util.getMeasuringTimeMs()
    private var quitting = false
    private var skippedAt = 0L
    private val music = CreditsMusic()

    private var songStarted = false

    /** Seconds since the credits' song began (negative before it: the credits open on black and silence). */
    private fun now(): Double = (Util.getMeasuringTimeMs() - openedAt) / 1000.0 - FinalCredits.SONG_AT

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, BLACK)
        val t = now()
        intro(context, t)
        FinalCredits.screenAt(t).takeIf { it >= 0 }?.let { screen(context, it, t - FinalCredits.screenStart(it)) }
        FinalCredits.groupAt(t).takeIf { it >= 0 }?.let { group ->
            val g = FinalCredits.TOOLS_AT + group * FinalCredits.GROUP
            lines(context, Text.translatable(TOOLS[group]).string.split("\n"), height / 2 - 18, FinalCredits.fade(t, g, g + FinalCredits.GROUP, 0.4, 0.4), GREY, WHITE)
        }
        val thanks = FinalCredits.fade(t, FinalCredits.THANKS_AT, FinalCredits.NEXT_AT, 0.4, 0.4)
        centred(context, Text.translatable("every_tunnel.credits.thanks"), height / 2 - 4, thanks, WHITE, 1f)
        val next = FinalCredits.fade(t, FinalCredits.NEXT_AT, FinalCredits.BLACK_AT, 0.8, 1.2)
        centred(context, Text.translatable("every_tunnel.credits.coming_soon"), height / 2 - 4, next, PURPLE, 1f)
        if (skippedAt != 0L) {
            val black = ((Util.getMeasuringTimeMs() - skippedAt) / SKIP_MS.toFloat()).coerceIn(0f, 1f)
            context.fill(0, 0, width, height, ((black * 255).toInt() shl 24))
        }
    }

    private fun intro(context: DrawContext, t: Double) {
        val out = FinalCredits.INTRO_END - FinalCredits.INTRO_OUT
        centred(context, title, height / 2 - 30, FinalCredits.fade(t, FinalCredits.END_IN, FinalCredits.INTRO_END, 1.0, out), WHITE, 3f)
        val sub = Text.translatable("every_tunnel.credits.the_end_sub")
        centred(context, sub, height / 2 + 8, FinalCredits.fade(t, FinalCredits.SUB_IN, FinalCredits.INTRO_END, 1.0, out), GREY, 1.5f)
    }

    /** Screen [i], [s] seconds in: its drawing on one side, its title and lines on the other, its credits under. */
    private fun screen(context: DrawContext, i: Int, s: Double) {
        val (name, w, h) = ART[i]
        val left = i % 2 == 0
        val margin = (width * 0.06).toInt()
        val half = width / 2
        val boxW = half - margin * 3 / 2
        val boxH = (height * 0.62).toInt()
        val scale = minOf(boxW.toDouble() / w, boxH.toDouble() / h)
        val dw = (w * scale).toInt()
        val dh = (h * scale).toInt()
        val ix = if (left) margin + (boxW - dw) / 2 else half + margin / 2 + (boxW - dw) / 2
        val iy = (height * 0.08).toInt() + (boxH - dh) / 2
        val image = if (s < FinalCredits.IMAGE_IN) s / FinalCredits.IMAGE_IN else FinalCredits.inScreen(s, 0.0)
        if (image > 0.01) {
            RenderSystem.enableBlend()
            context.setShaderColor(1f, 1f, 1f, image.toFloat())
            context.drawTexture(Identifier(EveryTunnel.MOD_ID, "textures/gui/credits/$name.png"), ix, iy, dw, dh, 0f, 0f, w, h, w, h)
            context.setShaderColor(1f, 1f, 1f, 1f)
            RenderSystem.disableBlend()
        }

        val tx = if (left) half + margin / 2 else margin
        val layout = layouts.getOrPut(i) { layoutOf(name, boxW, margin) }
        alpha(FinalCredits.inScreen(s, FinalCredits.TITLE_AT))?.let { a ->
            var typed = FinalCredits.typed(s, layout.headingLength)
            context.matrices.push()
            context.matrices.scale(layout.headingScale, layout.headingScale, 1f)
            for ((k, line) in layout.heading.withIndex()) {
                if (typed <= 0) break
                context.drawText(textRenderer, line.take(typed), (tx / layout.headingScale).toInt(), ((layout.headingTop + k * layout.headingStep) / layout.headingScale).toInt(), a or WHITE, true)
                typed -= line.length + 1
            }
            context.matrices.pop()
        }
        val bodyScale = layout.bodyScale
        var y = layout.bodyTop / bodyScale
        context.matrices.push()
        context.matrices.scale(bodyScale, bodyScale, 1f)
        for ((k, paragraph) in layout.paragraphs.withIndex()) {
            val a = alpha(FinalCredits.inScreen(s, FinalCredits.lineAt(k, layout.paragraphs.size)))
            for (line in paragraph) {
                if (a != null) context.drawText(textRenderer, line, (tx / bodyScale).toInt(), y.toInt(), a or LIGHT, true)
                y += textRenderer.fontHeight + 2
            }
            y += 5
        }
        context.matrices.pop()

        val a = FinalCredits.inScreen(s, FinalCredits.CREDITS_AT)
        for ((k, line) in layout.credits.withIndex()) {
            centred(context, line, layout.creditsTop + k * layout.creditsStep, a, GREY, CREDITS_SCALE)
        }
    }

    /**
     * Where everything of screen [name] goes, worked out once per size of the window (cleared in [init]) from its
     * texts in the player's language, which may be longer than English: the title is wrapped to the [column]
     * and typed over its lines; the credits are wrapped to the window and kept clear of its edges, rising when they
     * take more lines; and the text between is shrunk (down to [MIN_BODY_SCALE]) until it ends above the credits.
     */
    private fun layoutOf(name: String, column: Int, margin: Int): Layout {
        val heading = Text.translatable("every_tunnel.credits.$name").string
        // The biggest of the title scales that keeps it to two lines (else the smallest, wrapped as it must).
        var headingScale = TITLE_SCALES.first()
        var headingLines = emptyList<String>()
        for (candidate in TITLE_SCALES) {
            headingScale = candidate
            headingLines = textRenderer.textHandler.wrapLines(heading, (column / candidate).toInt(), Style.EMPTY).map { it.string }
            if (headingLines.size <= 2) break
        }
        val headingStep = (textRenderer.fontHeight * headingScale).toInt() + 2
        val headingTop = (height * 0.14).toInt()
        val bodyTop = headingTop + headingLines.size * headingStep + 8

        val creditsStep = ((textRenderer.fontHeight + 2) * CREDITS_SCALE).toInt()
        val credits = Text.translatable("every_tunnel.credits.$name.credits").string.split("\n")
            .flatMap { textRenderer.wrapLines(Text.literal(it), ((width - margin * 2) / CREDITS_SCALE).toInt()) }
        val creditsTop = minOf((height * 0.80).toInt(), (height * 0.90).toInt() - credits.size * creditsStep)

        val texts = Text.translatable("every_tunnel.credits.$name.text").string.split("\n")
        var scale = 1f
        var paragraphs = texts.map { textRenderer.wrapLines(Text.literal(it), column) }
        while (scale > MIN_BODY_SCALE && bodyTop + bodyHeight(paragraphs) * scale > creditsTop - BODY_GAP) {
            scale = (scale - 0.05f).coerceAtLeast(MIN_BODY_SCALE)
            paragraphs = texts.map { textRenderer.wrapLines(Text.literal(it), (column / scale).toInt()) }
        }
        return Layout(headingLines, headingScale, heading.length, headingTop, headingStep, bodyTop, scale, paragraphs, credits, creditsTop, creditsStep)
    }

    /** The height of the wrapped [paragraphs] at scale 1. */
    private fun bodyHeight(paragraphs: List<List<OrderedText>>): Int =
        paragraphs.sumOf { it.size * (textRenderer.fontHeight + 2) + 5 }

    private class Layout(
        val heading: List<String>,
        val headingScale: Float,
        val headingLength: Int,
        val headingTop: Int,
        val headingStep: Int,
        val bodyTop: Int,
        val bodyScale: Float,
        val paragraphs: List<List<OrderedText>>,
        val credits: List<OrderedText>,
        val creditsTop: Int,
        val creditsStep: Int,
    )

    private val layouts = HashMap<Int, Layout>()

    override fun init() {
        layouts.clear()
    }

    /** [texts] centred from [top]: the first in [first]'s colour, the rest in [rest]'s. A line too long for the window wraps. */
    private fun lines(context: DrawContext, texts: List<String>, top: Int, opacity: Double, first: Int, rest: Int) {
        var y = top
        for ((k, text) in texts.withIndex()) {
            for (line in textRenderer.wrapLines(Text.literal(text), (width * 0.88).toInt())) {
                centred(context, line, y, opacity, if (k == 0) first else rest, 1f)
                y += textRenderer.fontHeight + 3
            }
            if (k == 0) y += 4
        }
    }

    private fun centred(context: DrawContext, text: Text, y: Int, opacity: Double, colour: Int, scale: Float) =
        centred(context, text.asOrderedText(), y, opacity, colour, scale)

    private fun centred(context: DrawContext, text: OrderedText, y: Int, opacity: Double, colour: Int, scale: Float) {
        val a = alpha(opacity) ?: return
        context.matrices.push()
        context.matrices.scale(scale, scale, 1f)
        context.drawCenteredTextWithShadow(textRenderer, text, (width / 2f / scale).toInt(), (y / scale).toInt(), a or colour)
        context.matrices.pop()
    }

    /** [opacity] as a colour's alpha, or null while too faint to draw (text alpha under 4 draws opaque). */
    private fun alpha(opacity: Double): Int? {
        val a = (opacity.coerceIn(0.0, 1.0) * 255).toInt()
        return if (a < 8) null else a shl 24
    }

    override fun tick() {
        if (!songStarted && skippedAt == 0L && now() >= 0.0) {
            songStarted = true
            client?.soundManager?.play(music)
        }
        if (now() >= FinalCredits.LENGTH) quitToTitle()
        if (skippedAt != 0L && Util.getMeasuringTimeMs() - skippedAt >= SKIP_MS) quitToTitle()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == ESCAPE && skippedAt == 0L) {
            skippedAt = Util.getMeasuringTimeMs()
            music.fadeOut()
        }
        return true
    }

    override fun shouldCloseOnEsc(): Boolean = false

    override fun shouldPause(): Boolean = false

    /** Like the pause menu's "Save and Quit to Title". */
    private fun quitToTitle() {
        if (quitting) return
        quitting = true
        val client = client ?: MinecraftClient.getInstance()
        client.soundManager.stop(music)
        val singleplayer = client.isInSingleplayer
        client.world?.disconnect()
        if (singleplayer) client.disconnect(MessageScreen(Text.translatable("menu.savingLevel"))) else client.disconnect()
        client.setScreen(TitleScreen())
        // The title screen is the main menu now: the congratulations window goes over it.
        client.setScreen(CongratulationsScreen(client.currentScreen, summary, award = true))
    }

    /**
     * The credits' song, in the head, faded in gently at its start (evenly in decibels, from [FADE_FROM_DB], over
     * [FADE_IN_TICKS]) and out on a skip.
     */
    private class CreditsMusic : MovingSoundInstance(SoundEvent.of(SONG), SoundCategory.MUSIC, SoundInstance.createRandom()) {
        private var ticks = 0
        private var fadingFrom = -1

        init {
            relative = true
            attenuationType = SoundInstance.AttenuationType.NONE
            volume = 0.001f
        }

        fun fadeOut() {
            if (fadingFrom < 0) fadingFrom = ticks
        }

        override fun tick() {
            ticks++
            val progress = (ticks / FADE_IN_TICKS.toFloat()).coerceAtMost(1f)
            val rise = if (progress >= 1f) 1f else 10f.pow(FADE_FROM_DB * (1f - progress) / 20f)
            val fall = if (fadingFrom < 0) 1f else 1f - (ticks - fadingFrom) / FADE_OUT_TICKS.toFloat()
            volume = (rise * fall).coerceIn(0.001f, 1f)
            if (fall <= 0f) setDone()
        }
    }

    companion object {
        private const val ESCAPE = 256
        /** The title's scales, biggest first: it takes the first that fits in two lines. */
        private val TITLE_SCALES = listOf(1.5f, 1.35f, 1.2f)
        private const val CREDITS_SCALE = 0.85f
        /** The text of a screen shrinks to this at most to end above its credits; the gap kept between them. */
        private const val MIN_BODY_SCALE = 0.7f
        private const val BODY_GAP = 6
        private const val SKIP_MS = 1000L
        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFF
        private const val LIGHT = 0xD8DCE0
        private const val GREY = 0x8A9098

        /** The archive drawing's purple (sampled by `tools/gui/credits_art.py`). */
        private const val PURPLE = 0xB176DC

        private const val FADE_IN_TICKS = 120
        private const val FADE_FROM_DB = -40f
        private const val FADE_OUT_TICKS = 20

        private val SONG = Identifier(EveryTunnel.MOD_ID, "music.credits")
        private val FALL_MUSIC = Identifier(EveryTunnel.MOD_ID, "music.fall")

        /** The four screens' drawings: (name, width, height of its texture). */
        private val ART = listOf(Triple("guard", 1024, 768), Triple("terminal", 1024, 1024), Triple("observer", 1024, 1024), Triple("archive", 1024, 768))
        private val TOOLS = listOf("tools_engine", "tools_libraries", "tools_dev", "tools_compat").map { "every_tunnel.credits.$it" }

        /** The ending packet's numbers: the time played in ticks and the deaths (nothing in a packet from an older server). */
        private fun readSummary(buf: net.minecraft.network.PacketByteBuf): RunSummary =
            if (buf.readableBytes() >= 9) RunSummary(buf.readLong(), buf.readVarInt()) else RunSummary.EMPTY

        fun register() {
            ClientPlayNetworking.registerGlobalReceiver(EndingHooks.ENDING_PACKET_ID) { client, _, buf, _ ->
                val summary = readSummary(buf)
                client.execute { client.setScreen(FinalCreditsScreen(summary)) }
            }
            // From `/everytunnel congrats` (development): the window over what is on screen, earning nothing.
            ClientPlayNetworking.registerGlobalReceiver(EndingHooks.CONGRATS_PACKET_ID) { client, _, buf, _ ->
                val summary = readSummary(buf)
                client.execute { client.setScreen(CongratulationsScreen(client.currentScreen, summary, award = false)) }
            }
            ClientPlayNetworking.registerGlobalReceiver(FalseExitHooks.FALL_PACKET_ID) { client, _, _, _ ->
                client.execute {
                    client.soundManager.play(
                        PositionedSoundInstance(
                            FALL_MUSIC, SoundCategory.MUSIC, 1f, 1f, SoundInstance.createRandom(),
                            false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true,
                        ),
                    )
                }
            }
        }
    }
}
