package daio7771.everytunnel.common.client

/**
 * The main menu's logo easter egg (`screen.MainMenuScreen`): once [set], the logo's second word reads FINAL instead of TUNNEL
 * (`textures/gui/logo_final.png`, `tools/gui/common/logo.py`) for as long as the game runs; it is a flag in memory, never saved.
 * Whoever knows when to set it (a chapter's client glue: Chapter 1's, on leaving Level 5 before the Director's office) sets it; the
 * menu draws it and earns the achievement. Client thread only.
 */
object MenuLogoOverride {

    var final: Boolean = false
        private set

    fun set() {
        final = true
    }

    /** Forgotten (tests). */
    internal fun clear() {
        final = false
    }
}
