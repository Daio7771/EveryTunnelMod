package daio7771.everytunnel.common.platform

import daio7771.everytunnel.common.EveryTunnel
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.WorldSavePath
import java.io.IOException
import java.nio.file.Files

/**
 * What the main menu's NEW GAME modal asked for, read by the server and the client of the one process (the game is
 * singleplayer, as [ContinueGame.requested] is): the next opening of the world plays a later [chapter], not Chapter 1, from its
 * beginning or, [continuing], from that chapter's own saved game. The world is opened without rebuilding the map or starting a new
 * game ([LoadingHooks]); once the loading screen is gone, the chapter's own start runs ([onLoaded], registered by the chapter), and
 * the chapter decides what follows. It must leave the saved game of Chapter 1, if there is one, as it found it. Set by the menu,
 * cleared ([clear]) when the world closes.
 *
 * A chapter's saved game is the chapter's business; only a file in the world's folder ([markerName], written by the chapter with
 * [setSaved]) tells the menu, without opening the world, that there is one to continue, as [ContinueGame.MARKER] does for Chapter 1.
 */
object ChapterRequest {

    /** The chapter whose opening was asked for; 0: none (the world opens for Chapter 1, as always). */
    @Volatile
    var chapter = 0

    /** The menu asked to continue [chapter]'s saved game, not to begin it. */
    @Volatile
    var continuing = false

    /** A chapter was asked for instead of Chapter 1. */
    val chapterOnly: Boolean
        get() = chapter != 0

    private val starts = mutableMapOf<Int, (ServerPlayerEntity) -> Unit>()

    /** What [chapter] does once the world opened for it has loaded (its opening scene, or its saved game: [continuing]). */
    fun onLoaded(chapter: Int, start: (ServerPlayerEntity) -> Unit) {
        starts[chapter] = start
    }

    /** Runs the start of the chapter that was asked for, if it registered one. */
    fun loaded(player: ServerPlayerEntity) {
        starts[chapter]?.invoke(player)
    }

    /** Nothing asked for any more: the next opening is Chapter 1's. */
    fun clear() {
        chapter = 0
        continuing = false
    }

    /** The file in the world's folder that says [chapter] has a saved game to continue. */
    fun markerName(chapter: Int): String = "every_tunnel_chapter${chapter}_continue"

    /** Writes or deletes [chapter]'s marker ([markerName]) in [server]'s world folder. */
    fun setSaved(server: MinecraftServer, chapter: Int, saved: Boolean) {
        val path = server.getSavePath(WorldSavePath.ROOT).resolve(markerName(chapter))
        try {
            if (saved) Files.writeString(path, "") else Files.deleteIfExists(path)
        } catch (e: IOException) {
            EveryTunnel.LOGGER.warn("Chapter {}'s saved game's marker could not be changed", chapter, e)
        }
    }
}
