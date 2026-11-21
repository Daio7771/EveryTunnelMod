package daio7771.everytunnel.chapter2.core

import daio7771.everytunnel.common.core.achievement.AchievementDef
/**
 * Chapter 2's achievements, the second tab of the ACHIEVEMENTS screen, in the order it lists them (Level 1's, then Level 2's). Registered
 * with the base game's registry at start-up (`Chapter2`) and earned by `platform.Ch2Achievements`. The texts are the lang keys
 * `achievement.every_tunnel.<id>` and `.description` (`tools/lang/src/chapter2/`). [hidden]: the screen only says "Hidden achievement"
 * until it is earned. [icon]: `sprite:<n>`, the n-th column of `achievement_icons.png` (`tools/gui/common/achievement_icons.py`).
 */
enum class Ch2Achievement(override val id: String, sprite: Int, override val hidden: Boolean = false) : AchievementDef {
    /** Level 1: the time clock punched. */
    CLOCK_IN("clock_in", 7),

    /** Level 1: out of the union's meeting (watched or skipped). */
    ON_THE_SLY("on_the_sly", 8),

    /** Level 1: caught by a guard on patrol. */
    BAD_STEALTH("bad_stealth", 9),

    /** Level 1: PLAY pressed on the basement terminal's backwards recording. */
    STATIC("static", 10, hidden = true),

    /** Level 1: Chamber 02's occupant stared at, still, for [StillGaze] ticks. */
    FAMILIAR_FACE("familiar_face", 11, hidden = true),

    /** Level 1: both of the coworker's reminders heard (the follow lines 02 and 03), never following him. */
    WASTE_OF_TIME("waste_of_time", 12, hidden = true),

    /** Level 2: through the pump room with no blow from its creature. */
    WHAT_WAS_THAT("what_was_that", 13),

    /** Level 2: five seconds after the Observant Guard's death. */
    SAD_FAREWELL("sad_farewell", 14),

    /** Level 2: every room of the catacombs seen, the optional ones too. */
    EXPLORER("explorer", 15, hidden = true),

    /** Level 2: more than two pipes thrown at Stone Maw that missed. */
    BAD_AIM("bad_aim", 16, hidden = true);

    override val chapter: Int get() = 2
    override val puzzle: Boolean get() = false
    override val icon: String = "sprite:$sprite"
}
