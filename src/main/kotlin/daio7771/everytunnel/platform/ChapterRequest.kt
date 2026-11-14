package daio7771.everytunnel.platform

/**
 * What the main menu's NEW GAME modal asked for, read by the server and the client of the one process (the game is
 * singleplayer, as [ContinueGame.requested] is): the next opening of the world only plays Chapter 2's prologue
 * ([PrologueSceneHooks]). The world is opened without rebuilding the map or starting a new game ([LoadingHooks]), the
 * prologue plays once the loading screen is gone, and when it ends the client leaves the world for the menu
 * (`client.cutscene.CutsceneView`). The saved game of Chapter 1, if there is one, is not touched. Set by the menu,
 * cleared when the server stops.
 */
object ChapterRequest {

    @Volatile
    var prologueOnly = false
}
