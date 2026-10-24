package daio7771.everytunnel.client.screen

import daio7771.everytunnel.client.FlashlightControls
import net.minecraft.client.MinecraftClient
import net.minecraft.client.resource.language.I18n
import net.minecraft.text.Text

/**
 * The tips on the loading screen: `loading.every_tunnel.tip.1`, `.2`, ... in the language
 * file, as many as there are (numbered without gaps). They come in a random order without
 * repeats until all have been shown, each for [SHOW_MS], fading in and out over [FADE_MS].
 *
 * A tip may name keys as `{flashlight}`, `{sneak}`, `{sprint}`, `{jump}` and `{use}`: they
 * are replaced by the keys the player has bound (e.g. "F", "Left Shift", "Right Button").
 */
object LoadingTips {

    private const val KEY_PREFIX = "loading.every_tunnel.tip."
    private const val SHOW_MS = 7000L
    private const val FADE_MS = 400L

    private val deck = ArrayDeque<String>()
    private var current: String? = null
    private var shownAtMs = 0L

    /** A new loading screen: the next tip is a fresh random one. */
    fun restart() {
        deck.clear()
        current = null
    }

    /** The tip to show at [nowMs] and its opacity (0 to 1), or null if there are no tips. */
    fun at(nowMs: Long): Pair<Text, Float>? {
        if (current == null || nowMs - shownAtMs >= SHOW_MS) next(nowMs)
        val key = current ?: return null
        val age = nowMs - shownAtMs
        val opacity = when {
            age < FADE_MS -> age.toFloat() / FADE_MS
            SHOW_MS - age < FADE_MS -> (SHOW_MS - age).toFloat() / FADE_MS
            else -> 1f
        }.coerceIn(0f, 1f)
        return Text.literal(withKeys(I18n.translate(key))) to opacity
    }

    private fun next(nowMs: Long) {
        if (deck.isEmpty()) {
            val keys = allKeys().shuffled().toMutableList()
            // A new round never starts with the tip that was just shown.
            if (keys.size > 1 && keys.first() == current) keys.add(keys.removeAt(0))
            deck.addAll(keys)
        }
        current = deck.removeFirstOrNull()
        shownAtMs = nowMs
    }

    private fun allKeys(): List<String> =
        generateSequence(1) { it + 1 }.map { "$KEY_PREFIX$it" }.takeWhile(I18n::hasTranslation).toList()

    private fun withKeys(tip: String): String {
        val options = MinecraftClient.getInstance().options
        val keys = mapOf(
            "{flashlight}" to FlashlightControls.keyName(),
            "{sneak}" to options.sneakKey.boundKeyLocalizedText,
            "{sprint}" to options.sprintKey.boundKeyLocalizedText,
            "{jump}" to options.jumpKey.boundKeyLocalizedText,
            "{use}" to options.useKey.boundKeyLocalizedText,
        )
        var text = tip
        for ((placeholder, key) in keys) text = text.replace(placeholder, key.string)
        return text
    }
}
