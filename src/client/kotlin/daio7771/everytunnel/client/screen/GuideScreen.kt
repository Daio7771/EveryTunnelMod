package daio7771.everytunnel.client.screen

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.guide.GuideBlock
import daio7771.everytunnel.core.guide.GuideMarkdown
import daio7771.everytunnel.core.guide.GuidePage
import daio7771.everytunnel.core.guide.GuideSpan
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.sound.SoundEvents
import net.minecraft.text.MutableText
import net.minecraft.text.OrderedText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.Language
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW
import java.io.IOException
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The player guide, opened by GUIDE in the main menu ([MainMenuScreen]): one sheet of worn white
 * paper in the middle of the black screen, scrolled with the mouse wheel or the keys, with paper
 * tabs on its right edge to jump between the sections.
 *
 * - The pages are `assets/every_tunnel/guide/<lang>/<page>.md`, copied from `docs/<lang>/guide/` by
 *   the build (`copyGuide` in build.gradle.kts), in file name order, and read with
 *   [GuideMarkdown]. The language follows Minecraft's: Spanish for any `es_*`, English
 *   otherwise (and English when a language has no pages).
 * - The first section is the contents: the guide's title and every page's title, which open it
 *   when clicked. Each page's tab shows its title (its first `#` heading).
 * - Changing section turns the page (vanilla's book page sound) and goes back to the top.
 * - Keys: up/down and page up/down scroll, Home/End, left/right or Tab change section, Esc (or
 *   BACK at the top left) goes back to the menu. The menu's music keeps playing ([MenuMusic]).
 *
 * The paper and its stains are `textures/gui/guide_paper.png` and `guide_stains.png`, from
 * `tools/gui/guide_paper.py`; the paper is tiled, so the sheet can be any length.
 */
class GuideScreen(private val parent: Screen?) : Screen(Text.translatable("screen.every_tunnel.guide.title")) {

    /** One line of text on the sheet: [x] from the text's left edge, [y] from the sheet's top. */
    private class Line(
        val text: OrderedText,
        val x: Int,
        val y: Int,
        val scale: Float,
        val color: Int,
        val width: Int,
        /** The section a click on this line opens, if any (the contents). */
        val link: Int? = null,
    )

    /** A thin rule across the sheet at [y]. */
    private class Rule(val y: Int)

    private class Laid(val lines: List<Line>, val rules: List<Rule>, val height: Int)

    /** Where everything goes on this screen size. */
    private data class Layout(
        val sheetLeft: Int,
        val sheetWidth: Int,
        val viewTop: Int,
        val viewBottom: Int,
        val tabWidth: Int,
    ) {
        val sheetRight: Int get() = sheetLeft + sheetWidth
        val textLeft: Int get() = sheetLeft + PAD_X
        val textWidth: Int get() = sheetWidth - PAD_X * 2
        val viewHeight: Int get() = viewBottom - viewTop
    }

    private var pages: List<GuidePage> = emptyList()
    private var section = 0
    private var layout = Layout(0, 0, 0, 0, 0)
    private var laid = Laid(emptyList(), emptyList(), 0)
    private var scrollTarget = 0.0
    private var scroll = 0.0
    private var lastFrameMs = Util.getMeasuringTimeMs()
    private val openedAtMs = Util.getMeasuringTimeMs()

    override fun init() {
        val client = client ?: return
        if (pages.isEmpty()) pages = loadPages(client)
        section = section.coerceIn(0, pages.size)
        layout = layoutFor()
        laid = layOut(section)
        scrollTarget = scrollTarget.coerceIn(0.0, maxScroll())
        scroll = scroll.coerceIn(0.0, maxScroll())
    }

    override fun shouldPause(): Boolean = false

    override fun close() {
        client?.setScreen(parent)
    }

    // --- Layout ------------------------------------------------------------------------

    private fun sectionLabel(index: Int): Text =
        if (index == 0) Text.translatable("screen.every_tunnel.guide.contents") else Text.literal(pages[index - 1].title)

    private fun layoutFor(): Layout {
        val labelWidth = (0..pages.size).maxOfOrNull { textRenderer.getWidth(sectionLabel(it)) } ?: 0
        val tabWidth = (labelWidth + TAB_PAD * 2 + TAB_OVERLAP).coerceIn(TAB_MIN, TAB_MAX)
        val backWidth = textRenderer.getWidth(backText) + MARGIN * 2
        val side = maxOf(backWidth, tabWidth + TAB_ACTIVE_OUT + MARGIN)
        val sheetWidth = minOf(SHEET_MAX, width - side * 2).coerceAtLeast(minOf(SHEET_MIN, width - MARGIN * 2))
        return Layout((width - sheetWidth) / 2, sheetWidth, MARGIN, height - MARGIN, tabWidth)
    }

    /** Lays out a section's text for the sheet's width. */
    private fun layOut(index: Int): Laid {
        val blocks: List<Pair<GuideBlock, Int?>> = if (index == 0) {
            listOf(GuideBlock.Heading(1, listOf(GuideSpan(title.string))) to null) +
                pages.mapIndexed { i, page -> GuideBlock.ListItem(i + 1, listOf(GuideSpan(page.title))) to i + 1 } +
                (if (pages.isEmpty()) listOf(GuideBlock.Paragraph(listOf(GuideSpan(emptyText.string))) to null) else emptyList())
        } else {
            pages[index - 1].blocks.map { it to null }
        }

        val width = layout.textWidth
        val lines = mutableListOf<Line>()
        val rules = mutableListOf<Rule>()
        var y = PAD_TOP
        var previous: GuideBlock? = null

        fun wrapped(text: Text, x: Int, available: Int, scale: Float, color: Int, link: Int?) {
            val step = (LINE_HEIGHT * scale).roundToInt()
            for (line in textRenderer.wrapLines(text, (available / scale).toInt().coerceAtLeast(20))) {
                lines += Line(line, x, y, scale, color, textRenderer.getWidth(line), link)
                y += step
            }
        }

        for ((block, link) in blocks) {
            // Space between blocks: more before headings, a little after the end of a list.
            if (previous != null) {
                y += when {
                    block is GuideBlock.Heading -> if (block.level == 1) GAP_H1 else GAP_HEADING
                    block is GuideBlock.ListItem && previous is GuideBlock.ListItem -> GAP_ITEM
                    else -> GAP_BLOCK
                }
            }
            when (block) {
                is GuideBlock.Heading -> {
                    val scale = if (block.level == 1) H1_SCALE else 1f
                    val text = styled(block.spans, bold = block.level >= 2, italic = block.level == 3)
                    wrapped(text, 0, width, scale, if (block.level == 3) INK_SOFT else INK_DARK, link)
                    if (block.level == 1) y += GAP_UNDER_TITLE
                }
                is GuideBlock.Paragraph -> wrapped(styled(block.spans), 0, width, 1f, INK, link)
                is GuideBlock.ListItem -> {
                    val marker = Text.literal(block.number?.let { "$it." } ?: "•")
                    val indent = if (block.number == null) BULLET_INDENT else textRenderer.getWidth(marker) + NUMBER_GAP
                    lines += Line(marker.asOrderedText(), 0, y, 1f, INK_SOFT, textRenderer.getWidth(marker), link)
                    wrapped(styled(block.spans), indent, width - indent, 1f, INK, link)
                }
                GuideBlock.Rule -> {
                    y += RULE_GAP
                    rules += Rule(y)
                    y += RULE_GAP
                }
            }
            previous = block
        }
        return Laid(lines, rules, y + PAD_BOTTOM)
    }

    private fun styled(spans: List<GuideSpan>, bold: Boolean = false, italic: Boolean = false): MutableText {
        val text = Text.empty()
        for (span in spans) {
            text.append(Text.literal(span.text).setStyle(Style.EMPTY.withBold(bold || span.bold).withItalic(italic || span.italic)))
        }
        return text
    }

    private fun sheetHeight(): Int = maxOf(laid.height, layout.viewHeight - SHEET_GAP * 2)

    private fun maxScroll(): Double = maxOf(0, sheetHeight() + SHEET_GAP * 2 - layout.viewHeight).toDouble()

    /** The sheet's top on screen right now. */
    private fun sheetTop(): Int = layout.viewTop + SHEET_GAP - scroll.roundToInt()

    // --- Drawing -------------------------------------------------------------------------

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val now = Util.getMeasuringTimeMs()
        val seconds = ((now - lastFrameMs) / 1000.0).coerceIn(0.0, 0.1)
        lastFrameMs = now
        scroll += (scrollTarget - scroll) * (1.0 - exp(-seconds * SCROLL_EASE))
        if (abs(scrollTarget - scroll) < 0.5) scroll = scrollTarget

        context.fill(0, 0, width, height, 0xFF000000.toInt())
        drawBack(context, mouseX, mouseY)
        drawTabs(context, mouseX, mouseY)
        drawSheet(context, mouseX, mouseY)
        drawScrollBar(context)

        // Everything comes out of the dark when the guide opens.
        val fade = ((now - openedAtMs) / FADE_IN_MS.toFloat()).coerceIn(0f, 1f)
        if (fade < 1f) context.fill(0, 0, width, height, ((1f - fade) * 255f).toInt() shl 24)
    }

    private fun drawBack(context: DrawContext, mouseX: Int, mouseY: Int) {
        val color = if (overBack(mouseX, mouseY)) BACK_HOVER else BACK_COLOR
        context.drawText(textRenderer, backText, MARGIN, MARGIN + 2, color, false)
    }

    private fun drawTabs(context: DrawContext, mouseX: Int, mouseY: Int) {
        val hovered = tabAt(mouseX, mouseY)
        for (index in 0..pages.size) {
            val (x, y, w) = tabRect(index)
            paper(context, x, y, w, TAB_HEIGHT, 0, index * TAB_HEIGHT)
            if (index != section) context.fill(x, y, x + w, y + TAB_HEIGHT, if (index == hovered) TAB_HOVER_SHADE else TAB_SHADE)
            outline(context, x, y, w, TAB_HEIGHT)
            val label = textRenderer.trimToWidth(sectionLabel(index), w - TAB_OVERLAP - TAB_PAD * 2 + 2)
            context.drawText(textRenderer, Language.getInstance().reorder(label), x + TAB_OVERLAP + TAB_PAD, y + (TAB_HEIGHT - 8) / 2, INK, false)
        }
    }

    /** x, y and width of a section's tab: it sticks out from under the sheet's right edge. */
    private fun tabRect(index: Int): Triple<Int, Int, Int> {
        val out = if (index == section) TAB_ACTIVE_OUT else 0
        val x = layout.sheetRight - TAB_OVERLAP
        val y = layout.viewTop + TAB_TOP + index * (TAB_HEIGHT + TAB_GAP)
        return Triple(x, y, layout.tabWidth + out)
    }

    private fun drawSheet(context: DrawContext, mouseX: Int, mouseY: Int) {
        val l = layout
        val top = sheetTop()
        val h = sheetHeight()
        context.enableScissor(0, l.viewTop, width, l.viewBottom)

        // A soft shadow, then the paper with its stains, edges and text.
        context.fill(l.sheetLeft + SHADOW, top + SHADOW, l.sheetRight + SHADOW, top + h + SHADOW, SHADOW_COLOR)
        paper(context, l.sheetLeft, top, l.sheetWidth, h, 0, 0)
        drawStains(context, top, h)
        edges(context, l.sheetLeft, top, l.sheetWidth, h)

        val hoveredLink = linkAt(mouseX, mouseY)
        for (rule in laid.rules) {
            context.fill(l.textLeft + l.textWidth / 4, top + rule.y, l.textLeft + l.textWidth * 3 / 4, top + rule.y + 1, RULE_COLOR)
        }
        for (line in laid.lines) {
            val y = top + line.y
            if (y + LINE_HEIGHT * line.scale < l.viewTop || y > l.viewBottom) continue
            val color = if (line.link != null && line.link == hoveredLink) LINK_HOVER else line.color
            context.matrices.push()
            context.matrices.translate((l.textLeft + line.x).toFloat(), y.toFloat(), 0f)
            context.matrices.scale(line.scale, line.scale, 1f)
            context.drawText(textRenderer, line.text, 0, 0, color, false)
            context.matrices.pop()
        }
        context.disableScissor()
    }

    private fun drawStains(context: DrawContext, top: Int, sheetHeight: Int) {
        // The same stains on the same page every time, a few per page.
        val random = Random(if (section == 0) STAIN_SEED else pages[section - 1].id.hashCode().toLong() xor STAIN_SEED)
        val count = 1 + random.nextInt(2)
        RenderSystem.enableBlend()
        context.setShaderColor(1f, 1f, 1f, STAIN_ALPHA)
        repeat(count) {
            val kind = random.nextInt(2)
            val x = layout.sheetLeft + 4 + random.nextInt((layout.sheetWidth - STAIN_SIZE - 8).coerceAtLeast(1))
            val y = top + 4 + random.nextInt((sheetHeight - STAIN_SIZE - 8).coerceAtLeast(1))
            context.drawTexture(STAINS, x, y, STAIN_SIZE, STAIN_SIZE, (kind * STAIN_SIZE).toFloat(), 0f, STAIN_SIZE, STAIN_SIZE, STAIN_SIZE * 2, STAIN_SIZE)
        }
        context.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }

    /** The paper texture tiled over a rectangle, starting at ([u], [v]) in the tile. */
    private fun paper(context: DrawContext, x: Int, y: Int, w: Int, h: Int, u: Int, v: Int) {
        context.drawTexture(PAPER, x, y, u.toFloat(), v.toFloat(), w, h, PAPER_SIZE, PAPER_SIZE)
    }

    /** Yellowed, slightly darker edges and a thin outline: old paper. */
    private fun edges(context: DrawContext, x: Int, y: Int, w: Int, h: Int) {
        EDGE_SHADES.forEachIndexed { i, shade ->
            context.fill(x + i, y + i, x + w - i, y + i + 1, shade)
            context.fill(x + i, y + h - i - 1, x + w - i, y + h - i, shade)
            context.fill(x + i, y + i + 1, x + i + 1, y + h - i - 1, shade)
            context.fill(x + w - i - 1, y + i + 1, x + w - i, y + h - i - 1, shade)
        }
        outline(context, x, y, w, h)
    }

    private fun outline(context: DrawContext, x: Int, y: Int, w: Int, h: Int) {
        context.drawBorder(x, y, w, h, OUTLINE)
    }

    private fun drawScrollBar(context: DrawContext) {
        val max = maxScroll()
        if (max <= 0.0) return
        val l = layout
        val track = l.viewHeight
        val thumb = (track * track / (track + max)).toInt().coerceAtLeast(12)
        val y = l.viewTop + ((track - thumb) * (scroll / max)).toInt()
        val x = l.sheetLeft - SCROLLBAR_GAP
        context.fill(x, l.viewTop, x + 1, l.viewBottom, SCROLL_TRACK)
        context.fill(x - 1, y, x + 2, y + thumb, SCROLL_THUMB)
    }

    // --- Input ---------------------------------------------------------------------------

    private fun overBack(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (MARGIN - 4)..(MARGIN + textRenderer.getWidth(backText) + 4) && mouseY in MARGIN..(MARGIN + 13)

    private fun tabAt(mouseX: Int, mouseY: Int): Int? = (0..pages.size).firstOrNull { index ->
        val (x, y, w) = tabRect(index)
        mouseX in (layout.sheetRight + 1)..(x + w) && mouseY in y until y + TAB_HEIGHT
    }

    private fun linkAt(mouseX: Int, mouseY: Int): Int? {
        if (mouseY !in layout.viewTop until layout.viewBottom) return null
        val top = sheetTop()
        return laid.lines.firstOrNull { line ->
            line.link != null &&
                mouseX in (layout.textLeft + line.x - 2)..(layout.textLeft + line.x + (line.width * line.scale).toInt() + 2) &&
                mouseY in (top + line.y - 1)..(top + line.y + (LINE_HEIGHT * line.scale).toInt())
        }?.link
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val x = mouseX.toInt()
        val y = mouseY.toInt()
        when {
            overBack(x, y) -> close()
            else -> {
                val target = tabAt(x, y) ?: linkAt(x, y) ?: return super.mouseClicked(mouseX, mouseY, button)
                open(target)
            }
        }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        scrollBy(-amount * WHEEL_STEP)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val page = (layout.viewHeight - PAGE_KEEP).toDouble()
        when (keyCode) {
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> scrollBy(-KEY_STEP)
            GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> scrollBy(KEY_STEP)
            GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-page)
            GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> scrollBy(page)
            GLFW.GLFW_KEY_HOME -> scrollBy(-maxScroll())
            GLFW.GLFW_KEY_END -> scrollBy(maxScroll())
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> open((section + pages.size) % (pages.size + 1))
            GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> open((section + 1) % (pages.size + 1))
            GLFW.GLFW_KEY_TAB -> open(
                if (modifiers and GLFW.GLFW_MOD_SHIFT != 0) (section + pages.size) % (pages.size + 1) else (section + 1) % (pages.size + 1),
            )
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }

    private fun scrollBy(amount: Double) {
        scrollTarget = (scrollTarget + amount).coerceIn(0.0, maxScroll())
    }

    /** Turns to a section, from its top. */
    private fun open(index: Int) {
        if (index == section) return
        section = index
        laid = layOut(index)
        scrollTarget = 0.0
        scroll = 0.0
        client?.soundManager?.play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1f))
    }

    companion object {
        private val PAPER = Identifier(EveryTunnel.MOD_ID, "textures/gui/guide_paper.png")
        private val STAINS = Identifier(EveryTunnel.MOD_ID, "textures/gui/guide_stains.png")
        private const val PAPER_SIZE = 64
        private const val STAIN_SIZE = 64
        private const val STAIN_ALPHA = 0.8f
        private const val STAIN_SEED = 0x5EA1L

        private val backText: Text get() = Text.translatable("screen.every_tunnel.guide.back").let { Text.literal("< ").append(it) }
        private val emptyText: Text get() = Text.translatable("screen.every_tunnel.guide.empty")

        /** Free space around everything, and the sheet's width range. */
        private const val MARGIN = 8
        private const val SHEET_MAX = 300
        private const val SHEET_MIN = 160
        /** Black above the sheet's top edge and below its bottom edge. */
        private const val SHEET_GAP = 6
        private const val SHADOW = 3
        private const val PAD_X = 18
        private const val PAD_TOP = 18
        private const val PAD_BOTTOM = 22

        /** Vanilla's font is 9 pixels tall; one more between lines. */
        private const val LINE_HEIGHT = 10
        private const val H1_SCALE = 2f
        private const val GAP_H1 = 12
        private const val GAP_UNDER_TITLE = 4
        private const val GAP_HEADING = 10
        private const val GAP_BLOCK = 6
        private const val GAP_ITEM = 3
        private const val RULE_GAP = 5
        private const val BULLET_INDENT = 9
        private const val NUMBER_GAP = 4

        private const val TAB_HEIGHT = 16
        private const val TAB_GAP = 3
        private const val TAB_TOP = 14
        private const val TAB_PAD = 5
        /** How far a tab reaches under the sheet, how far the open one sticks out further. */
        private const val TAB_OVERLAP = 4
        private const val TAB_ACTIVE_OUT = 4
        private const val TAB_MIN = 40
        private const val TAB_MAX = 104

        private const val SCROLLBAR_GAP = 7
        private const val WHEEL_STEP = 24.0
        private const val KEY_STEP = 20.0
        /** Lines kept on screen when paging. */
        private const val PAGE_KEEP = 30
        /** How fast the sheet catches up with the wheel (per second). */
        private const val SCROLL_EASE = 18.0
        private const val FADE_IN_MS = 400L

        // Ink on paper: dark, a little blue, never pure black. Links go red-brown when pointed at.
        private const val INK = 0xFF2C2B30.toInt()
        private const val INK_DARK = 0xFF1C1B20.toInt()
        private const val INK_SOFT = 0xFF55535A.toInt()
        private const val LINK_HOVER = 0xFF7A2A1E.toInt()
        private const val RULE_COLOR = 0x50303038
        private const val OUTLINE = 0xFF6F6A5E.toInt()
        /** From the edge inwards: yellowed, fading into the paper. */
        private val EDGE_SHADES = intArrayOf(0x40806838, 0x2A806838, 0x16806838)
        private const val SHADOW_COLOR = 0x60000000
        private const val TAB_SHADE = 0x30000000
        private const val TAB_HOVER_SHADE = 0x18000000
        private const val BACK_COLOR = 0xFF6E737A.toInt()
        private const val BACK_HOVER = 0xFFE8ECF0.toInt()
        private const val SCROLL_TRACK = 0xFF202226.toInt()
        private const val SCROLL_THUMB = 0xFF6E737A.toInt()

        /**
         * The guide pages in the game's language: Spanish for any `es_*` language, English
         * otherwise, and English when the language has no pages. Broken files are skipped.
         */
        fun loadPages(client: MinecraftClient): List<GuidePage> {
            val language = if (client.languageManager.language.startsWith("es_")) "es" else "en"
            val pages = load(client, language)
            return if (pages.isEmpty() && language != "en") load(client, "en") else pages
        }

        private fun load(client: MinecraftClient, language: String): List<GuidePage> =
            client.resourceManager.findResources("guide/$language") { it.path.endsWith(".md") }
                .filterKeys { it.namespace == EveryTunnel.MOD_ID }
                .entries.sortedBy { it.key.path }
                .mapNotNull { (id, resource) ->
                    try {
                        val name = id.path.substringAfterLast('/').removeSuffix(".md")
                        resource.reader.use { GuideMarkdown.parse(name, it.readText()) }
                    } catch (e: IOException) {
                        EveryTunnel.LOGGER.warn("Guide page {} could not be read", id, e)
                        null
                    }
                }
    }
}
