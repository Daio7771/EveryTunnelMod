package daio7771.everytunnel.core.puzzle

import kotlin.random.Random

/**
 * Level 5's chemical puzzle: of the four reagents (R-1 to R-4, numbered 1 to 4), one pair, [first] and
 * [second], makes the solvent that eats the drain's grate; the Office's computer says which. Any order
 * in the mixer's two tanks. Plain Kotlin, unit tested.
 */
class ChemicalMix(val first: Int, val second: Int) {

    init {
        require(first in REAGENTS && second in REAGENTS && first != second) { "not a pair of two reagents: $first, $second" }
    }

    /** The two tanks hold [a] and [b]: is it the solvent? */
    fun right(a: Int, b: Int): Boolean = (a == first && b == second) || (a == second && b == first)

    companion object {
        val REAGENTS = 1..4

        /** A new game's pair, in order (the lower first). */
        fun draw(random: Random = Random.Default): ChemicalMix {
            val pair = REAGENTS.shuffled(random).take(2).sorted()
            return ChemicalMix(pair[0], pair[1])
        }
    }
}
