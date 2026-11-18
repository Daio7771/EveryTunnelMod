package daio7771.everytunnel.chapter2

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1Act2Hooks
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1Act3Hooks
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1EndingHooks
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1OfficeHooks
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1PatrolHooks
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1Build
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1Commands
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1DataLoader
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1IntroHooks
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1KeyItems
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1LabTaskHooks
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1Npcs
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1SpeakerHooks
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1TaskHooks
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2Build
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2Commands
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2DataLoader
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2Pump
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2Spawns
import daio7771.everytunnel.chapter2.platform.Ch2Blocks
import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.chapter2.platform.Ch2Items
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.chapter2.platform.Ch2Return
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.chapter2.prologue.platform.PrologueCommand
import daio7771.everytunnel.chapter2.prologue.platform.PrologueSceneHooks
import daio7771.everytunnel.platform.ChapterRequest
import daio7771.everytunnel.platform.DeathHooks
import daio7771.everytunnel.platform.PlayerRules
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.fabricmc.api.ModInitializer

/**
 * Chapter 2's common (server + client) entrypoint, declared in `fabric.mod.json` after the base game's ([EveryTunnel], Chapter 1),
 * which runs first and never names this chapter. Everything of Chapter 2 is under `daio7771.everytunnel.chapter2`: the prologue
 * (`prologue`), the factory (`level1`), the catacombs (`level2`) and what the chapter shares (`core`, `platform`: its registries and its progress). It hooks
 * into the base game only through the base game's extension points: [PlayerRules] (what its levels change of the rules),
 * [ChapterRequest] (the menu's Chapter 2, NEW GAME or CONTINUE), `ChapterRestarts` (where a death brings the player back),
 * `ProgressService.onReset` (a new game) and the cutscene view's kinds (client).
 *
 * The chapter is played from the menu: NEW GAME is the prologue, then Level 1 in one go, then down its manhole into Level 2
 * ([Ch2L1EndingHooks.onFinished], [Ch2L2.enter]); CONTINUE goes back to Level 2's last checkpoint ([Ch2L2.continueFromMenu]).
 */
object Chapter2 : ModInitializer {

    /**
     * While playing Chapter 2's Level 1: no flashlight, no hiding, nothing hurts, silent steps, no status bars, and the player is the new
     * hire, a scientist in the factory's white coat (`textures/entity/new_hire.png`, by `tools/entity/workers.py`), not the night guard.
     */
    private val LEVEL1_RULES = PlayerRules.Rules(
        flashlight = false, hiding = false, safe = true, noise = false, statusBars = false,
        skin = Identifier(EveryTunnel.MOD_ID, "textures/entity/new_hire.png"),
    )

    /** After Rowan's office the LAB is dark, and he has handed the flashlight over with the pass: the same, but with the flashlight. */
    private val LEVEL1_OVERTIME_RULES = LEVEL1_RULES.copy(flashlight = true)

    /**
     * While playing Level 2, the catacombs: the base game's survival (hurt, thirst, battery, the bars), the flashlight and the noise of
     * the steps as `level.json` says (`rules`), and still the new hire.
     */
    private fun level2Rules(): PlayerRules.Rules {
        val level = Ch2L2DataLoader.data.level
        return PlayerRules.Rules(flashlight = level.flashlight, noise = level.noise, skin = LEVEL1_RULES.skin)
    }

    override fun onInitialize() {
        Ch2Items.register()
        Ch2Blocks.register()
        Ch2Entities.register()
        Ch2Sounds.register()
        Ch2Progress.register()
        PlayerRules.register { player ->
            when {
                Ch2L1.active(player) -> if (Ch2L1.state(player) >= Ch2L1State.ACT3_OVERTIME) LEVEL1_OVERTIME_RULES else LEVEL1_RULES
                Ch2L2.active(player) -> level2Rules()
                else -> null
            }
        }
        ChapterRequest.onLoaded(PrologueSceneHooks.MENU_CHAPTER, ::startFromMenu)
        PrologueSceneHooks.onEndedFromMenu(Ch2Return::enterFromPrologue)
        Ch2L1EndingHooks.onFinished(::downTheManhole)
        Ch2L1DataLoader.register()
        Ch2L1.register()
        Ch2L1Build.register()
        Ch2L1TaskHooks.register()
        Ch2L1LabTaskHooks.register()
        Ch2L1KeyItems.register()
        Ch2Return.register()
        Ch2L1Npcs.register()
        Ch2L1SpeakerHooks.register()
        Ch2L1IntroHooks.register()
        Ch2L1Act2Hooks.register()
        Ch2L1Act3Hooks.register()
        Ch2L1PatrolHooks.register()
        Ch2L1OfficeHooks.register()
        Ch2L1EndingHooks.register()
        PrologueSceneHooks.register()
        PrologueCommand.register()
        Ch2L1Commands.register()
        // Level 2, the catacombs (phase 2: the map, the way in from Level 1, survival, checkpoints, the saved game and the commands).
        Ch2L2DataLoader.register()
        Ch2L2Build.register()
        Ch2L2.register()
        Ch2L2Spawns.register()
        Ch2L2Pump.register()
        Ch2L2Commands.register()
        EveryTunnel.LOGGER.info("${EveryTunnel.MOD_NAME}: Chapter 2 loaded")
    }

    /**
     * The world opened for the menu's Chapter 2: CONTINUE goes back to Level 2's saved game; NEW GAME (or a CONTINUE with nothing to
     * continue) forgets it and plays the prologue.
     */
    private fun startFromMenu(player: ServerPlayerEntity) {
        if (ChapterRequest.continuing && Ch2L2.continueFromMenu(player)) return
        Ch2L2.forget(player)
        PrologueSceneHooks.startFromMenu(player)
    }

    /** Level 1 is over, the screen black at the foot of its manhole: on into Level 2. If it cannot be, the player stays, told why. */
    private fun downTheManhole(player: ServerPlayerEntity) {
        val why = Ch2L2.enter(player, fadeIn = true)
        if (why == null) {
            Ch2L1.leave(player)
            return
        }
        EveryTunnel.LOGGER.warn("Chapter 2: Level 2 after Level 1: {}", why)
        DeathHooks.sendFade(player, 0f, 1f)
        player.sendMessage(Text.literal(why))
    }
}
