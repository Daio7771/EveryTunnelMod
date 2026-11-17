package daio7771.everytunnel.platform

import net.minecraft.server.network.ServerPlayerEntity

/**
 * What the main menu's NEW GAME modal asked for, read by the server and the client of the one process (the game is
 * singleplayer, as [ContinueGame.requested] is): the next opening of the world plays a later [chapter], not Chapter 1. The
 * world is opened without rebuilding the map or starting a new game ([LoadingHooks]); once the loading screen is gone, the
 * chapter's own start runs ([onLoaded], registered by the chapter), and the chapter decides what follows. It must leave the saved
 * game of Chapter 1, if there is one, as it found it. Set by the menu, cleared when the world closes.
 */
object ChapterRequest {

    /** The chapter whose opening was asked for; 0: none (the world opens for Chapter 1, as always). */
    @Volatile
    var chapter = 0

    /** A chapter was asked for instead of Chapter 1. */
    val chapterOnly: Boolean
        get() = chapter != 0

    private val starts = mutableMapOf<Int, (ServerPlayerEntity) -> Unit>()

    /** What [chapter] does once the world opened for it has loaded (its opening scene). */
    fun onLoaded(chapter: Int, start: (ServerPlayerEntity) -> Unit) {
        starts[chapter] = start
    }

    /** Runs the start of the chapter that was asked for, if it registered one. */
    fun loaded(player: ServerPlayerEntity) {
        starts[chapter]?.invoke(player)
    }
}
