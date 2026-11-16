package daio7771.everytunnel.chapter2

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1Act2Hooks
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1Act3Hooks
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1PatrolHooks
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1Build
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1Commands
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1DataLoader
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1IntroHooks
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1Npcs
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1SpeakerHooks
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1TaskHooks
import daio7771.everytunnel.chapter2.platform.Ch2Blocks
import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.chapter2.platform.Ch2Items
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.chapter2.prologue.platform.PrologueCommand
import daio7771.everytunnel.chapter2.prologue.platform.PrologueSceneHooks
import daio7771.everytunnel.platform.ChapterRequest
import daio7771.everytunnel.platform.PlayerRules
import net.fabricmc.api.ModInitializer

/**
 * Chapter 2's common (server + client) entrypoint, declared in `fabric.mod.json` after the base game's ([EveryTunnel], Chapter 1),
 * which runs first and never names this chapter. Everything of Chapter 2 is under `daio7771.everytunnel.chapter2`: the prologue
 * (`prologue`), the factory (`level1`) and what the chapter shares (`core`, `platform`: its registries and its progress). It hooks
 * into the base game only through the base game's extension points: [PlayerRules] (what its levels change of the rules),
 * [ChapterRequest] (the menu's Chapter 2), `ProgressService.onReset` (a new game) and the cutscene view's kinds (client).
 */
object Chapter2 : ModInitializer {

    /** While playing Chapter 2's Level 1: no flashlight, no hiding, nothing hurts, silent steps, no status bars. */
    private val LEVEL1_RULES = PlayerRules.Rules(flashlight = false, hiding = false, safe = true, noise = false, statusBars = false)

    override fun onInitialize() {
        Ch2Items.register()
        Ch2Blocks.register()
        Ch2Entities.register()
        Ch2Sounds.register()
        Ch2Progress.register()
        PlayerRules.register { player -> if (Ch2L1.active(player)) LEVEL1_RULES else null }
        ChapterRequest.onLoaded(2, PrologueSceneHooks::startFromMenu)
        Ch2L1DataLoader.register()
        Ch2L1.register()
        Ch2L1Build.register()
        Ch2L1TaskHooks.register()
        Ch2L1Npcs.register()
        Ch2L1SpeakerHooks.register()
        Ch2L1IntroHooks.register()
        Ch2L1Act2Hooks.register()
        Ch2L1Act3Hooks.register()
        Ch2L1PatrolHooks.register()
        PrologueSceneHooks.register()
        PrologueCommand.register()
        Ch2L1Commands.register()
        EveryTunnel.LOGGER.info("${EveryTunnel.MOD_NAME}: Chapter 2 loaded")
    }
}
